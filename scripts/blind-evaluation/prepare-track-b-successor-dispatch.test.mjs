import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { sha256 } from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const script = (name) =>
  resolve(repoRoot, "scripts/blind-evaluation", name);

test("successor dispatch remains absent before seal and freezes exact 9x1 calls", async () => {
  const root = await mkdtemp(resolve(tmpdir(), "cqcp-tbs-dispatch-"));
  try {
    await copyFixture(root);
    const now = new Date(Date.now() - 60_000);
    const createdAt = now.toISOString();
    const dispatchAt = new Date(now.getTime() + 60_000).toISOString();
    const expiresAt = new Date(now.getTime() + 3_600_000).toISOString();
    const nonce = `TBS1-${randomBytes(16).toString("hex")}`;
    assertRun(
      run(script("prepare-track-b-successor-corpus.mjs"), [root, createdAt])
    );
    assertRun(
      run(script("prepare-track-b-successor-human-challenge.mjs"), [
        root,
        createdAt,
        expiresAt,
        nonce
      ])
    );
    const runRoot = resolve(
      root,
      "outputs/task-eval-003/track-b-successor-v1/run-v1"
    );
    const premature = run(
      script("prepare-track-b-successor-dispatch.mjs"),
      [root, dispatchAt]
    );
    assert.notEqual(premature.status, 0);
    await assert.rejects(() => stat(runRoot), /ENOENT/);

    const outputRoot = resolve(runRoot, "..");
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
        "task-eval-003-track-b-successor-human-confirmation-v1",
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
        `Test confirmation ${challenge.nonce} ${challenge.corpusSha256}`
    };
    await writeFile(
      resolve(root, "confirmation.json"),
      `${JSON.stringify(confirmation, null, 2)}\n`,
      "utf8"
    );
    assertRun(
      run(script("seal-track-b-successor-human-ground-truth.mjs"), [
        root,
        "confirmation.json",
        "create"
      ])
    );
    const outOfOrder = run(
      script("prepare-track-b-successor-dispatch.mjs"),
      [root, createdAt]
    );
    assert.notEqual(outOfOrder.status, 0);
    assert.match(outOfOrder.stderr, /DISPATCH_PRECEDES_HUMAN_CONFIRMATION/);
    await assert.rejects(() => stat(runRoot), /ENOENT/);
    const dispatched = run(
      script("prepare-track-b-successor-dispatch.mjs"),
      [root, dispatchAt]
    );
    assertRun(dispatched);

    const modelInputBytes = await readFile(resolve(runRoot, "model-input.json"));
    const callSetBytes = await readFile(
      resolve(runRoot, "provider-call-set.json")
    );
    const dispatchBytes = await readFile(resolve(runRoot, "dispatch.json"));
    const receiptBytes = await readFile(
      resolve(runRoot, "derived-egress-authorization-receipt.json")
    );
    const input = JSON.parse(modelInputBytes.toString("utf8"));
    const callSet = JSON.parse(callSetBytes.toString("utf8"));
    const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
    const receipt = JSON.parse(receiptBytes.toString("utf8"));
    assert.equal(
      input.schemaVersion,
      "task-eval-003-track-b-successor-model-input-v1"
    );
    assert.equal(input.packetCount, 9);
    assert.ok(input.packets.every((packet) => packet.sampleId.startsWith("TBS-")));
    assert.equal(
      callSet.schemaVersion,
      "task-eval-003-track-b-successor-provider-call-set-v1"
    );
    assert.equal(callSet.callCount, 9);
    assert.ok(callSet.calls.every((call) => call.callId.startsWith("TBS-MC-")));
    assert.equal(
      dispatch.schemaVersion,
      "task-eval-003-track-b-successor-dispatch-v1"
    );
    assert.equal(dispatch.modelInputSha256, sha256(modelInputBytes));
    assert.equal(dispatch.providerCallSetSha256, sha256(callSetBytes));
    assert.equal(receipt.dispatchSha256, sha256(dispatchBytes));
    assert.equal(receipt.actualInputSha256, sha256(modelInputBytes));
    assert.equal(receipt.providerCallSetSha256, sha256(callSetBytes));
    assert.equal(receipt.callCount, 9);
    assert.equal(receipt.inputCount, 9);
    assert.equal(receipt.excludedZeroCallControlCount, 3);
    assert.equal(receipt.networkCallPerformed, false);
    assert.equal(receipt.secretOrRawKeyIncluded, false);

    const serialized = modelInputBytes.toString("utf8").toLowerCase();
    for (const forbidden of [
      "project owner test",
      "proposedexpected",
      "humandecision",
      "finding",
      "verdict"
    ]) {
      assert.ok(!serialized.includes(forbidden));
    }
    const duplicate = run(
      script("prepare-track-b-successor-dispatch.mjs"),
      [root, dispatchAt]
    );
    assert.notEqual(duplicate.status, 0);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

async function copyFixture(root) {
  const copies = [
    [
      "apps/api-server/src/test/resources/track-b-successor-v1",
      ["source-signals.json", "proposed-decisions.json"]
    ],
    ["apps/api-server/src/test/resources/track-b-admission-corpus-v2", ["corpus.json"]],
    ["outputs/task-eval-002/track-b-holdout-v1", ["corpus.json"]],
    [
      "scripts/blind-evaluation",
      ["track-b-holdout-opinion-prompt.txt", "mvp002-standing-egress-grant.json"]
    ]
  ];
  for (const [relativeRoot, files] of copies) {
    const targetRoot = resolve(root, relativeRoot);
    await mkdir(targetRoot, { recursive: true });
    for (const file of files) {
      await cp(resolve(repoRoot, relativeRoot, file), resolve(targetRoot, file));
    }
  }
}

function run(command, args) {
  return spawnSync(process.execPath, [command, ...args], {
    cwd: repoRoot,
    encoding: "utf8"
  });
}

function assertRun(result) {
  assert.equal(result.status, 0, result.stderr);
}
