import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  buildTrackBProviderRecoveryAdmissionArtifacts
} from "./track-b-provider-recovery-admission-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

async function loadFixture() {
  return {
    corpusBytes: await readFile(
      resolve(repoRoot, "outputs/task-eval-006/track-b-recovery-v1/corpus.json")
    ),
    humanGroundTruthSealBytes: await readFile(
      resolve(
        repoRoot,
        "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json"
      )
    ),
    promptBytes: await readFile(
      resolve(
        repoRoot,
        "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt"
      )
    ),
    grantBytes: await readFile(
      resolve(
        repoRoot,
        "scripts/blind-evaluation/mvp002-standing-egress-grant.json"
      )
    ),
    createdAt: "2026-08-03T12:00:00.000Z"
  };
}

test("freezes nine eligible calls and three explicit zero-call controls", async () => {
  const artifacts = buildTrackBProviderRecoveryAdmissionArtifacts(
    await loadFixture()
  );

  assert.equal(artifacts.modelInput.packetCount, 9);
  assert.equal(artifacts.callSet.callCount, 9);
  assert.equal(artifacts.callSet.inputCount, 9);
  assert.equal(artifacts.dispatch.zeroCallControlCount, 3);
  assert.equal(artifacts.dispatch.providerCallCount, 9);
  assert.equal(artifacts.dispatch.networkCallPerformed, false);
  assert.equal(artifacts.receipt.callCount, 9);
  assert.equal(artifacts.controls.length, 3);
  assert.ok(
    artifacts.controls.every(
      (control) => control.modelCallAllowed === false
    )
  );
  assert.equal(
    new Set(artifacts.callSet.calls.map((call) => call.packetId)).size,
    9
  );
});

test("formal model payload is v3-only and excludes labels, ground truth, and verdicts", async () => {
  const artifacts = buildTrackBProviderRecoveryAdmissionArtifacts(
    await loadFixture()
  );
  const serialized = artifacts.calls
    .map((call) => call.modelInputBytes.toString("utf8"))
    .join("\n");

  for (const forbidden of [
    "taskId",
    "executionId",
    "sampleId",
    "confidenceClass",
    "coverageStatus",
    "diagnosticCode",
    "reasonCodes",
    "modelCallAllowed",
    "AMBIGUOUS",
    "CONFLICTED",
    "humanDecision",
    "proposedExpected",
    "groundTruth",
    "finding",
    "verdict"
  ]) {
    assert.equal(serialized.includes(forbidden), false, forbidden);
  }
  assert.equal(artifacts.modelInput.groundTruthIncluded, false);
  assert.equal(artifacts.modelInput.findingOrVerdictIncluded, false);
  assert.equal(
    artifacts.dispatch.humanGroundTruthExcludedFromPayload,
    true
  );
});

test("human seal predates dispatch and every hash binding is exact", async () => {
  const fixture = await loadFixture();
  const artifacts = buildTrackBProviderRecoveryAdmissionArtifacts(fixture);
  const seal = JSON.parse(fixture.humanGroundTruthSealBytes.toString("utf8"));

  assert.ok(Date.parse(seal.confirmedAt) < Date.parse(fixture.createdAt));
  assert.equal(
    artifacts.dispatch.modelInputSha256,
    artifacts.hashes.modelInputSha256
  );
  assert.equal(
    artifacts.dispatch.providerCallSetSha256,
    artifacts.hashes.callSetSha256
  );
  assert.equal(
    artifacts.receipt.dispatchSha256,
    artifacts.hashes.dispatchSha256
  );
  assert.equal(
    artifacts.receipt.actualInputSha256,
    artifacts.hashes.modelInputSha256
  );
});
