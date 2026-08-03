import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import {
  cp,
  mkdtemp,
  mkdir,
  readFile,
  rm,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { sha256 } from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../.."
);
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

test("requires a hash-bound human confirmation before creating the seal", async () => {
  const tempRoot = await mkdtemp(resolve(tmpdir(), "cqcp-tbh2-"));
  try {
    const fixtureRoot = resolve(
      tempRoot,
      "apps/api-server/src/test/resources/track-b-holdout-v1"
    );
    await mkdir(fixtureRoot, { recursive: true });
    await cp(
      resolve(
        repoRoot,
        "apps/api-server/src/test/resources/track-b-holdout-v1/source-signals.json"
      ),
      resolve(fixtureRoot, "source-signals.json")
    );
    await cp(
      resolve(
        repoRoot,
        "apps/api-server/src/test/resources/track-b-holdout-v1/proposed-decisions.json"
      ),
      resolve(fixtureRoot, "proposed-decisions.json")
    );
    const now = new Date(Date.now() - 60_000);
    const createdAt = now.toISOString();
    const expiresAt = new Date(now.getTime() + 3_600_000).toISOString();
    const nonce = `TBH2-${randomBytes(16).toString("hex")}`;
    const prepared = run(prepareScript, [tempRoot, createdAt]);
    assert.equal(prepared.status, 0, prepared.stderr);
    const preparedOutput = JSON.parse(prepared.stdout);
    assert.equal(preparedOutput.modelInputCreated, false);
    assert.equal(preparedOutput.networkCallAllowed, false);
    const duplicate = run(prepareScript, [tempRoot, createdAt]);
    assert.notEqual(duplicate.status, 0);
    assert.match(duplicate.stderr, /immutable and already exists/);
    const challenged = run(challengeScript, [
      tempRoot,
      createdAt,
      expiresAt,
      nonce
    ]);
    assert.equal(challenged.status, 0, challenged.stderr);
    const challengeOutput = JSON.parse(challenged.stdout);
    assert.equal(challengeOutput.requiredDecisionCount, 12);
    assert.equal(challengeOutput.modelInputCreated, false);
    assert.equal(challengeOutput.networkCallAllowed, false);
    const outputRoot = resolve(
      tempRoot,
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
      confirmedAt: new Date(now.getTime() + 60_000).toISOString(),
      confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
      confirmationStatement:
        `Test confirmation ${challenge.nonce} ` +
        `${challenge.corpusSha256}`
    };
    const confirmationPath = resolve(tempRoot, "confirmation.json");
    await writeFile(
      confirmationPath,
      `${JSON.stringify(confirmation, null, 2)}\n`,
      "utf8"
    );
    const sealed = run(sealScript, [
      tempRoot,
      "confirmation.json",
      "create"
    ]);
    assert.equal(sealed.status, 0, sealed.stderr);
    const seal = JSON.parse(
      await readFile(resolve(outputRoot, "human-ground-truth.json"), "utf8")
    );
    assert.equal(seal.status, "ACCEPTED_HUMAN_GROUND_TRUTH");
    assert.equal(seal.entryCount, 12);
    assert.equal(seal.modelInputCreated, false);
    assert.equal(seal.networkCallAllowedByThisSeal, false);
    const verified = run(sealScript, [
      tempRoot,
      "confirmation.json",
      "verify"
    ]);
    assert.equal(verified.status, 0, verified.stderr);
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
});

function run(script, args) {
  return spawnSync(process.execPath, [script, ...args], {
    cwd: repoRoot,
    encoding: "utf8"
  });
}
