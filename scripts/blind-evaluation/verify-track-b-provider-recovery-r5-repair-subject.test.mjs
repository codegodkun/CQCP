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
  assert.equal(result.verification.metrics.node, "56/56");
  assert.equal(result.verification.metrics.javaRecoveryRuntimeSeam, "1/1");
  assert.equal(result.verification.deepSeekNetworkCallRepeated, false);
  assert.equal(
    result.verification.providerAdmission,
    "PENDING_THREE_PARTY_AUDIT_AND_CI"
  );
  assert.equal(result.verification.previousFailedFreezePreserved, true);
  assert.equal(result.verification.failedAuditRoundsPreserved.length, 5);
  assert.equal(
    result.verification.failedAuditRoundsPreserved[0].codeArchitectureVerdict,
    "NO_GO"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[1].subjectIdentity,
    "a9daf62f4644dadd3834a3ef1e92408bc96c4d974009e1f6020b3f3f24e397ad"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[1].ccAuditVerdict,
    "NO_GO"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[1].failureClassification,
    "AUDIT_PROJECTION_TRANSPORT"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[2].subjectIdentity,
    "47d84299f9f5002cc80c5aa20481452cdf6f27f83699fce2793c836aaf4852d3"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[2].testSecurityAuditVerdict,
    "NO_GO"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[2].failureClassification,
    "CC_AUDIT_PACKAGE_CONTENT_ISOLATION"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[2].ccAuditVerdict,
    "GO_INVALIDATED_BY_PACKAGE_ISOLATION_P0"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[3].subjectIdentity,
    "a85e96f58532a8a9b807e72cb3ca0666e011e72321cf87a6685d28c59db1b50d"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[3].ccAuditVerdict,
    "NO_GO"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[3].ccAuditP2,
    1
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[3].failureClassification,
    "GOVERNANCE_DOCUMENT_FRESHNESS"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[4].subjectIdentity,
    "e566c8d2e987b2508c24083f1307765c3150722e9a39f66cdb5c7c08f14433a2"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[4].ccAuditVerdict,
    "GO_INVALIDATED_BY_CODE_ARCHITECTURE_P2"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[4].codeArchitectureAuditVerdict,
    "NO_GO"
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[4].codeArchitectureAuditP2,
    1
  );
  assert.equal(
    result.verification.failedAuditRoundsPreserved[4].failureClassification,
    "GOVERNANCE_TASK_STATE_CONTRADICTION"
  );
  assert.equal(result.verification.a0A1A2ImplementationAllowed, false);
  assert.equal(result.consoleManifest.failedHarnessEvidence.length, 3);
});
