import assert from "node:assert/strict";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  buildTrackBProviderRecoveryR5RepairVerification
} from "./verify-track-b-provider-recovery-r5-repair-subject.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

test("builds PASS only from fresh chronology, executed recovery seam, and reused DeepSeek evidence", async () => {
  const result = await buildTrackBProviderRecoveryR5RepairVerification({
    repoRoot,
    completedAt: "2026-08-03T16:00:00.000Z"
  });
  assert.equal(result.verification.status, "PASS");
  assert.equal(result.verification.metrics.node, "54/54");
  assert.equal(result.verification.metrics.javaRecoveryRuntimeSeam, "1/1");
  assert.equal(result.verification.deepSeekNetworkCallRepeated, false);
  assert.equal(result.verification.previousFailedFreezePreserved, true);
  assert.equal(result.verification.a0A1A2ImplementationAllowed, false);
  assert.equal(result.consoleManifest.failedHarnessEvidence.length, 3);
});
