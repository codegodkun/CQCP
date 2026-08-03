import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { cp, mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { sha256 } from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const prepareScript = resolve(
  repoRoot,
  "scripts/blind-evaluation/prepare-track-b-successor-corpus.mjs"
);
const challengeScript = resolve(
  repoRoot,
  "scripts/blind-evaluation/prepare-track-b-successor-human-challenge.mjs"
);
const sealScript = resolve(
  repoRoot,
  "scripts/blind-evaluation/seal-track-b-successor-human-ground-truth.mjs"
);
const confirmationScript = resolve(
  repoRoot,
  "scripts/blind-evaluation/prepare-track-b-successor-human-confirmation.mjs"
);

test("successor seal requires its exact challenge-bound human confirmation", async () => {
  const tempRoot = await mkdtemp(resolve(tmpdir(), "cqcp-tbs1-"));
  try {
    const fixtureRoot = resolve(
      tempRoot,
      "apps/api-server/src/test/resources/track-b-successor-v1"
    );
    await mkdir(fixtureRoot, { recursive: true });
    for (const file of ["source-signals.json", "proposed-decisions.json"]) {
      await cp(
        resolve(
          repoRoot,
          "apps/api-server/src/test/resources/track-b-successor-v1",
          file
        ),
        resolve(fixtureRoot, file)
      );
    }
    const historicalRoot = resolve(
      tempRoot,
      "apps/api-server/src/test/resources/track-b-admission-corpus-v2"
    );
    await mkdir(historicalRoot, { recursive: true });
    await cp(
      resolve(
        repoRoot,
        "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json"
      ),
      resolve(historicalRoot, "corpus.json")
    );
    const failedRoot = resolve(
      tempRoot,
      "outputs/task-eval-002/track-b-holdout-v1"
    );
    await mkdir(failedRoot, { recursive: true });
    await cp(
      resolve(repoRoot, "outputs/task-eval-002/track-b-holdout-v1/corpus.json"),
      resolve(failedRoot, "corpus.json")
    );
    const now = new Date(Date.now() - 60_000);
    const createdAt = now.toISOString();
    const expiresAt = new Date(now.getTime() + 3_600_000).toISOString();
    const nonce = `TBS1-${randomBytes(16).toString("hex")}`;
    assertRun(run(prepareScript, [tempRoot, createdAt]));
    assertRun(
      run(challengeScript, [tempRoot, createdAt, expiresAt, nonce])
    );
    const outputRoot = resolve(
      tempRoot,
      "outputs/task-eval-003/track-b-successor-v1"
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
    const confirmedAt = new Date(now.getTime() + 60_000).toISOString();
    assertRun(
      run(confirmationScript, [
        tempRoot,
        challenge.nonce,
        sha256(challengeBytes),
        challenge.corpusSha256,
        challenge.humanReviewDocumentSha256,
        confirmedAt
      ])
    );
    const confirmationRelativePath =
      "outputs/task-eval-003/track-b-successor-v1/human-confirmation.json";
    const confirmationPath = resolve(tempRoot, confirmationRelativePath);
    const confirmation = JSON.parse(
      await readFile(confirmationPath, "utf8")
    );
    assert.deepEqual(confirmation.decisions, decisions);
    assert.equal(
      confirmation.decisionsSha256,
      sha256(Buffer.from(JSON.stringify(decisions), "utf8"))
    );
    assertRun(
      run(sealScript, [tempRoot, confirmationRelativePath, "create"])
    );
    const seal = JSON.parse(
      await readFile(resolve(outputRoot, "human-ground-truth.json"), "utf8")
    );
    assert.equal(
      seal.schemaVersion,
      "task-eval-003-track-b-successor-human-ground-truth-v1"
    );
    assert.equal(seal.entryCount, 12);
    assert.equal(seal.modelInputCreated, false);
    assert.equal(seal.networkCallAllowedByThisSeal, false);
    assertRun(
      run(sealScript, [tempRoot, confirmationRelativePath, "verify"])
    );

    confirmation.challengeNonce = "TBS1-tampered";
    await writeFile(
      confirmationPath,
      `${JSON.stringify(confirmation, null, 2)}\n`,
      "utf8"
    );
    const tampered = run(sealScript, [
      tempRoot,
      confirmationRelativePath,
      "verify"
    ]);
    assert.notEqual(tampered.status, 0);
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

function assertRun(result) {
  assert.equal(result.status, 0, result.stderr);
}
