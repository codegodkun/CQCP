import assert from "node:assert/strict";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  buildTrackBProviderRecoveryVerification
} from "./verify-track-b-provider-recovery-subject.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

test("builds PASS only from the sealed admission and all successful bounded consoles", async () => {
  const result = await buildTrackBProviderRecoveryVerification({
    repoRoot,
    completedAt: "2026-08-03T14:00:00.000Z"
  });
  assert.equal(result.verification.status, "PASS");
  assert.equal(
    result.verification.providerAdmission,
    "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE"
  );
  assert.equal(result.verification.networkCallPerformedByVerification, false);
  assert.equal(result.consoleManifest.runs.length, 6);
  assert.ok(result.consoleManifest.runs.every((run) => run.exitCode === 0));
  assert.equal(result.verification.metrics.node, "70/70");
  assert.equal(result.verification.metrics.javaRuntimeSeam, "1/1");
  assert.equal(result.verification.metrics.codexAdmission, "9/9");
  assert.equal(result.verification.metrics.deepSeekAdmission, "9/9");
  assert.equal(result.verification.metrics.zeroCallControls, "3/3");
  assert.equal(result.verification.metrics.actualSecretLeakFiles, 0);
  assert.equal(result.verification.metrics.forbiddenProviderPayloadFiles, 0);
  assert.equal(result.verification.metrics.filesContainingCR, 0);
  assert.ok(result.verification.evidence.length >= 12);
});
