import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  buildTrackBProviderRecoveryDiagnosticArtifacts
} from "./track-b-provider-recovery-diagnostic-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

async function loadFixture() {
  return {
    fifthInputBytes: await readFile(
      resolve(
        repoRoot,
        "outputs/task-eval-005/track-b-fifth-v1/run-v1/model-input.json"
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

test("freezes exactly four projected diagnostic calls and a standing-grant receipt before network", async () => {
  const artifacts = buildTrackBProviderRecoveryDiagnosticArtifacts(
    await loadFixture()
  );

  assert.equal(artifacts.modelInput.packetCount, 4);
  assert.equal(artifacts.callSet.callCount, 4);
  assert.equal(artifacts.dispatch.networkCallPerformed, false);
  assert.equal(artifacts.dispatch.formalAdmissionAffected, false);
  assert.equal(artifacts.receipt.callCount, 4);
  assert.equal(artifacts.receipt.inputCount, 4);
  assert.equal(
    artifacts.receipt.status,
    "DERIVED_FROM_ACTIVE_STANDING_GRANT_BEFORE_NETWORK"
  );
  assert.equal(
    artifacts.receipt.dispatchSha256,
    artifacts.hashes.dispatchSha256
  );
  assert.equal(
    artifacts.receipt.actualInputSha256,
    artifacts.hashes.modelInputSha256
  );
  assert.equal(
    artifacts.receipt.providerCallSetSha256,
    artifacts.hashes.callSetSha256
  );
  assert.equal(
    new Set(artifacts.callSet.calls.map((call) => call.packetId)).size,
    4
  );

  const providerInputs = artifacts.calls.map((call) =>
    call.modelInputBytes.toString("utf8")
  );
  for (const serialized of providerInputs) {
    for (const forbidden of [
      "taskId",
      "executionId",
      "sampleId",
      "coverageStatus",
      "diagnosticCode",
      "reasonCodes",
      "modelCallAllowed",
      "AMBIGUOUS",
      "CONFLICTED",
      "SYS_ROLE_CONFLICT",
      "ELIGIBLE_CONFLICT_LOCAL_CONTEXT",
      "humanDecision",
      "groundTruth",
      "finding",
      "verdict"
    ]) {
      assert.equal(serialized.includes(forbidden), false, forbidden);
    }
  }
});
