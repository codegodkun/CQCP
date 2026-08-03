import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

const EXACT = Object.freeze({
  allowedPurposes: [
    "TRACK_A_EVALUATION",
    "TRACK_B_EVALUATION",
    "CONNECTIVITY_TEST",
    "EVALUATION_SHADOW",
    "PROVIDER_VALIDATION",
    "READ_ONLY_INDEPENDENT_AUDIT"
  ],
  allowedDataClasses: [
    "USER_CONFIRMED_DEIDENTIFIED_EVALUATION",
    "SYNTHETIC_CORPUS",
    "FROZEN_AUDIT_MATERIAL"
  ],
  forbiddenDataClasses: [
    "UNREDACTED_PRODUCTION_CONTRACT",
    "HUMAN_GROUND_TRUTH",
    "CQCP_ACTUAL_OR_EXPECTED",
    "FINAL_FINDING_OR_VERDICT",
    "SECRET_OR_RAW_KEY",
    "RAW_PROVIDER_RESPONSE",
    "REASONING_CONTENT"
  ],
  models: ["deepseek-v4-pro", "deepseek-v4-flash"],
  zeroCallClasses: [
    "DETERMINISTIC_HIGH",
    "INVALID_BUNDLE",
    "NO_RELIABLE_ANCHOR",
    "NO_ELIGIBLE_CANDIDATE",
    "BUDGET_INCOMPLETE"
  ],
  receiptBindings: [
    "actualInputSha256",
    "dispatchSha256",
    "providerCallSetSha256",
    "outboundRequestSha256s",
    "endpointOrigin",
    "model",
    "callCount",
    "inputCount",
    "createdAt"
  ]
});

export async function loadAndValidateMvp002StandingEgressGrant(path) {
  const bytes = await readFile(path);
  return {
    bytes,
    grant: validateMvp002StandingEgressGrant(
      parseJsonBytesRejectDuplicateKeys(bytes)
    )
  };
}

export function validateMvp002StandingEgressGrant(grant) {
  assert.deepEqual(Object.keys(grant).sort(), [
    "schemaVersion",
    "status",
    "milestoneId",
    "approvalSource",
    "approvedByRole",
    "revoked",
    "scope",
    "provider",
    "transport",
    "eligibility",
    "secret",
    "derivedReceipt"
  ].sort());
  assert.equal(grant.schemaVersion, "mvp002-standing-egress-grant-v1");
  assert.equal(
    grant.status,
    "ACTIVE_UNTIL_MILESTONE_COMPLETION_OR_USER_REVOCATION"
  );
  assert.equal(grant.milestoneId, "MILESTONE-MVP-002");
  assert.equal(grant.approvalSource, "CODEX_THREAD_USER_CONFIRMATION");
  assert.equal(grant.approvedByRole, "PROJECT_OWNER");
  assert.equal(grant.revoked, false);

  assert.deepEqual(Object.keys(grant.scope).sort(), [
    "allowedPurposes",
    "allowedDataClasses",
    "forbiddenDataClasses",
    "deidentificationResponsibility",
    "programmaticRedactionPlatformRequired"
  ].sort());
  assert.deepEqual(grant.scope.allowedPurposes, EXACT.allowedPurposes);
  assert.deepEqual(grant.scope.allowedDataClasses, EXACT.allowedDataClasses);
  assert.deepEqual(
    grant.scope.forbiddenDataClasses,
    EXACT.forbiddenDataClasses
  );
  assert.equal(
    grant.scope.deidentificationResponsibility,
    "USER_CONFIRMS_INPUT_SCOPE"
  );
  assert.equal(grant.scope.programmaticRedactionPlatformRequired, false);

  assert.deepEqual(Object.keys(grant.provider).sort(), [
    "endpointOrigin",
    "allowedModels",
    "redirectAllowed",
    "proxyAllowed",
    "privateAddressAllowed",
    "ccAuditModel"
  ].sort());
  assert.equal(grant.provider.endpointOrigin, "https://api.deepseek.com:443");
  assert.deepEqual(grant.provider.allowedModels, EXACT.models);
  assert.equal(grant.provider.redirectAllowed, false);
  assert.equal(grant.provider.proxyAllowed, false);
  assert.equal(grant.provider.privateAddressAllowed, false);
  assert.equal(grant.provider.ccAuditModel, "deepseek-v4-flash");

  assert.deepEqual(Object.keys(grant.transport).sort(), [
    "strictJsonRequired",
    "nonStreamingRequired",
    "reasoningPersistenceAllowed",
    "rawResponsePersistenceAllowed",
    "unknownSideEffectAutomaticRetryAllowed"
  ].sort());
  assert.equal(grant.transport.strictJsonRequired, true);
  assert.equal(grant.transport.nonStreamingRequired, true);
  assert.equal(grant.transport.reasoningPersistenceAllowed, false);
  assert.equal(grant.transport.rawResponsePersistenceAllowed, false);
  assert.equal(
    grant.transport.unknownSideEffectAutomaticRetryAllowed,
    false
  );

  assert.deepEqual(Object.keys(grant.eligibility).sort(), [
    "zeroCallClasses",
    "modelMayChangeFindingOrVerdict"
  ].sort());
  assert.deepEqual(
    grant.eligibility.zeroCallClasses,
    EXACT.zeroCallClasses
  );
  assert.equal(grant.eligibility.modelMayChangeFindingOrVerdict, false);

  assert.deepEqual(Object.keys(grant.secret).sort(), [
    "source",
    "processMemoryOnly",
    "repositoryPersistenceAllowed",
    "artifactPersistenceAllowed",
    "logPersistenceAllowed"
  ].sort());
  assert.equal(
    grant.secret.source,
    "PREVIOUSLY_DESIGNATED_DESKTOP_SECRET_REFERENCE"
  );
  assert.equal(grant.secret.processMemoryOnly, true);
  assert.equal(grant.secret.repositoryPersistenceAllowed, false);
  assert.equal(grant.secret.artifactPersistenceAllowed, false);
  assert.equal(grant.secret.logPersistenceAllowed, false);

  assert.deepEqual(Object.keys(grant.derivedReceipt).sort(), [
    "requiredPerExecution",
    "perRunChatConfirmationRequired",
    "requiredBindings"
  ].sort());
  assert.equal(grant.derivedReceipt.requiredPerExecution, true);
  assert.equal(grant.derivedReceipt.perRunChatConfirmationRequired, false);
  assert.deepEqual(
    grant.derivedReceipt.requiredBindings,
    EXACT.receiptBindings
  );
  return grant;
}
