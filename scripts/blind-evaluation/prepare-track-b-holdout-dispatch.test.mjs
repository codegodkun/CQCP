import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import {
  cp,
  mkdir,
  mkdtemp,
  readFile,
  rm,
  stat,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { sha256 } from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const prepareScript = resolve(
  repoRoot,
  "scripts/blind-evaluation/prepare-track-b-holdout-corpus.mjs"
);
const challengeScript = resolve(
  repoRoot,
  "scripts/blind-evaluation/prepare-track-b-holdout-human-challenge.mjs"
);
const sealScript = resolve(
  repoRoot,
  "scripts/blind-evaluation/seal-track-b-holdout-human-ground-truth.mjs"
);
const dispatchScript = resolve(
  repoRoot,
  "scripts/blind-evaluation/prepare-track-b-holdout-dispatch.mjs"
);

test("creates no model input before seal, then freezes a 9x1 dispatch", async () => {
  const root = await mkdtemp(resolve(tmpdir(), "cqcp-tbh-dispatch-"));
  try {
    await copyFixture(root);
    const now = new Date(Date.now() - 60_000);
    const createdAt = now.toISOString();
    const dispatchAt = new Date(now.getTime() + 60_000).toISOString();
    const expiresAt = new Date(now.getTime() + 3_600_000).toISOString();
    const nonce = `TBH2-${randomBytes(16).toString("hex")}`;
    assert.equal(run(prepareScript, [root, createdAt]).status, 0);
    assert.equal(
      run(challengeScript, [root, createdAt, expiresAt, nonce]).status,
      0
    );

    const premature = run(dispatchScript, [root, dispatchAt]);
    assert.notEqual(premature.status, 0);
    assert.match(premature.stderr, /human-ground-truth\.json|ENOENT/);
    await assert.rejects(
      () => stat(resolve(root, "outputs/task-eval-002/track-b-holdout-v1/run-v1")),
      /ENOENT/
    );

    const outputRoot = resolve(
      root,
      "outputs/task-eval-002/track-b-holdout-v1"
    );
    const challengeBytes = await readFile(
      resolve(outputRoot, "human-confirmation-challenge.json")
    );
    const challenge = JSON.parse(challengeBytes.toString("utf8"));
    const draft = JSON.parse(
      await readFile(resolve(outputRoot, "human-review-draft.json"), "utf8")
    );
    const decisions = draft.entries.map((entry) => ({
      caseId: entry.caseId,
      packetId: entry.packetId,
      expected: entry.proposedExpected
    }));
    const confirmation = {
      schemaVersion:
        "task-eval-002-track-b-holdout-human-confirmation-v1",
      confirmed: true,
      challengeNonce: challenge.nonce,
      challengeSha256: sha256(challengeBytes),
      corpusSha256: challenge.corpusSha256,
      humanReviewDraftSha256: challenge.humanReviewDraftSha256,
      humanReviewDocumentSha256:
        challenge.humanReviewDocumentSha256,
      presealManifestSha256: challenge.presealManifestSha256,
      decisionsSha256: sha256(
        Buffer.from(JSON.stringify(decisions), "utf8")
      ),
      decisions,
      confirmedBy: "Project Owner Test",
      confirmedAt: new Date(now.getTime() + 30_000).toISOString(),
      confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
      confirmationStatement:
        `Test confirmation ${challenge.nonce} ` +
        `${challenge.corpusSha256}`
    };
    await writeFile(
      resolve(root, "confirmation.json"),
      `${JSON.stringify(confirmation, null, 2)}\n`,
      "utf8"
    );
    const sealed = run(sealScript, [
      root,
      "confirmation.json",
      "create"
    ]);
    assert.equal(sealed.status, 0, sealed.stderr);

    const outOfOrder = run(dispatchScript, [root, createdAt]);
    assert.notEqual(outOfOrder.status, 0);
    assert.match(outOfOrder.stderr, /DISPATCH_PRECEDES_HUMAN_CONFIRMATION/);
    await assert.rejects(
      () => stat(resolve(outputRoot, "run-v1")),
      /ENOENT/
    );

    const dispatched = run(dispatchScript, [root, dispatchAt]);
    assert.equal(dispatched.status, 0, dispatched.stderr);
    const result = JSON.parse(dispatched.stdout);
    assert.equal(result.providerCallCount, 9);
    assert.equal(result.eligiblePacketCount, 9);
    assert.equal(result.zeroCallControlCount, 3);
    assert.equal(result.networkCallPerformed, false);

    const runRoot = resolve(outputRoot, "run-v1");
    const modelInputBytes = await readFile(resolve(runRoot, "model-input.json"));
    const callSetBytes = await readFile(
      resolve(runRoot, "provider-call-set.json")
    );
    const dispatchBytes = await readFile(resolve(runRoot, "dispatch.json"));
    const receiptBytes = await readFile(
      resolve(runRoot, "derived-egress-authorization-receipt.json")
    );
    const modelInput = JSON.parse(modelInputBytes.toString("utf8"));
    const callSet = JSON.parse(callSetBytes.toString("utf8"));
    const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
    const receipt = JSON.parse(receiptBytes.toString("utf8"));
    assert.equal(modelInput.packetCount, 9);
    assert.equal(callSet.callCount, 9);
    assert.ok(callSet.calls.every((call) => typeof call.packetId === "string"));
    assert.equal(
      new Set(callSet.calls.map((call) => call.packetId)).size,
      9
    );
    assert.equal(dispatch.modelInputSha256, sha256(modelInputBytes));
    assert.equal(dispatch.providerCallSetSha256, sha256(callSetBytes));
    assert.equal(receipt.dispatchSha256, sha256(dispatchBytes));
    assert.equal(receipt.actualInputSha256, sha256(modelInputBytes));
    assert.equal(receipt.providerCallSetSha256, sha256(callSetBytes));
    assert.equal(receipt.outboundRequestSha256s.length, 9);
    assert.equal(new Set(receipt.outboundRequestSha256s).size, 9);
    assert.equal(receipt.networkCallPerformed, false);
    assert.equal(receipt.secretOrRawKeyIncluded, false);

    const serializedInput = modelInputBytes.toString("utf8").toLowerCase();
    for (const forbidden of [
      "project owner test",
      "proposedexpected",
      "humandecision",
      "cqcpactual",
      "cqcpexpected",
      "finding",
      "verdict"
    ]) {
      assert.ok(!serializedInput.includes(forbidden));
    }
    assert.ok(
      modelInput.packets.every(
        (packet) => packet.admission.modelCallAllowed === true
      )
    );
    const second = run(dispatchScript, [root, dispatchAt]);
    assert.notEqual(second.status, 0);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

async function copyFixture(root) {
  const fixtureRoot = resolve(
    root,
    "apps/api-server/src/test/resources/track-b-holdout-v1"
  );
  const scriptRoot = resolve(root, "scripts/blind-evaluation");
  await mkdir(fixtureRoot, { recursive: true });
  await mkdir(scriptRoot, { recursive: true });
  for (const name of ["source-signals.json", "proposed-decisions.json"]) {
    await cp(
      resolve(
        repoRoot,
        "apps/api-server/src/test/resources/track-b-holdout-v1",
        name
      ),
      resolve(fixtureRoot, name)
    );
  }
  for (const name of [
    "track-b-holdout-opinion-prompt.txt",
    "mvp002-standing-egress-grant.json"
  ]) {
    await cp(
      resolve(repoRoot, "scripts/blind-evaluation", name),
      resolve(scriptRoot, name)
    );
  }
}

function run(script, args) {
  return spawnSync(process.execPath, [script, ...args], {
    cwd: repoRoot,
    encoding: "utf8"
  });
}
