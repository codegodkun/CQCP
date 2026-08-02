import assert from "node:assert/strict";

const AUTHORIZATION_FIELDS = new Set([
  "schemaVersion",
  "authorized",
  "scope",
  "purpose",
  "challengePath",
  "challengeSha256",
  "challengeNonce",
  "modelInputSha256",
  "providerCallSetSha256",
  "providerRequestBuilderVersion",
  "providerCallCount",
  "dispatchSha256",
  "endpointHost",
  "model",
  "authorizedInputCount",
  "excludedZeroCallControlCount",
  "zeroCallControlsExcluded",
  "humanGroundTruthExcludedFromPayload",
  "findingOrVerdictExcludedFromPayload",
  "approvedBy",
  "approvedAt",
  "expiresAt",
  "confirmationSource",
  "confirmationStatement",
  "oneTimeExecution"
]);
const CHALLENGE_FIELDS = new Set([
  "schemaVersion",
  "status",
  "nonce",
  "createdAt",
  "expiresAt",
  "purpose",
  "dispatchPath",
  "dispatchSha256",
  "modelInputPath",
  "modelInputSha256",
  "providerCallSetPath",
  "providerCallSetSha256",
  "providerRequestBuilderVersion",
  "providerCallCount",
  "outboundRequestSha256s",
  "endpointHost",
  "model",
  "authorizedInputCount",
  "excludedZeroCallControlCount",
  "zeroCallControlsExcluded",
  "humanGroundTruthExcludedFromPayload",
  "findingOrVerdictExcludedFromPayload",
  "instruction"
]);

export function validateTrackBEgressAuthorization({
  authorization,
  challenge,
  challengeSha256,
  inputSha256,
  dispatchSha256,
  dispatchCreatedAt,
  validationTime
}) {
  requireObject(authorization);
  requireObject(challenge);
  assert.deepEqual(
    Object.keys(authorization).sort(),
    [...AUTHORIZATION_FIELDS].sort(),
    "Track B egress authorization fields are invalid"
  );
  assert.equal(
    authorization.schemaVersion,
    "task-eval-002-track-b-egress-authorization-v2"
  );
  assert.equal(authorization.authorized, true);
  assert.equal(
    authorization.scope,
    "DEEPSEEK_TRACK_B_ADMISSION_EGRESS"
  );
  assert.equal(
    authorization.purpose,
    "TASK-EVAL-002_TRACK_B_ADMISSION_ONLY"
  );
  assert.deepEqual(
    Object.keys(challenge).sort(),
    [...CHALLENGE_FIELDS].sort(),
    "Track B egress challenge fields are invalid"
  );
  assert.equal(
    challenge.schemaVersion,
    "task-eval-002-track-b-egress-challenge-v2"
  );
  assert.equal(
    challenge.status,
    "AWAITING_EXACT_USER_EGRESS_AUTHORIZATION"
  );
  assert.match(challenge.nonce, /^TBE-[a-f0-9]{32}$/);
  assert.match(challengeSha256, /^[a-f0-9]{64}$/);
  assert.equal(
    challenge.dispatchPath,
    "outputs/task-eval-002/track-b-admission-run-v3/dispatch.json"
  );
  assert.equal(
    challenge.modelInputPath,
    "outputs/task-eval-002/track-b-admission-run-v3/model-input.json"
  );
  assert.equal(
    challenge.providerCallSetPath,
    "outputs/task-eval-002/track-b-admission-run-v3/provider-call-set.json"
  );
  assert.equal(
    challenge.instruction,
    "授权必须在 Codex 对话中逐字绑定 nonce、dispatchSha256、" +
      "modelInputSha256、providerCallSetSha256、endpointHost、model、" +
      "6 calls/15 inputs/3 controls 范围；仅允许一次正式执行。"
  );
  assert.equal(
    authorization.challengePath,
    "outputs/task-eval-002/track-b-admission-run-v3/" +
      "egress-authorization-challenge.json"
  );
  assert.equal(authorization.challengeSha256, challengeSha256);
  assert.equal(authorization.challengeNonce, challenge.nonce);
  assert.equal(challenge.purpose, authorization.purpose);
  assert.equal(challenge.dispatchSha256, dispatchSha256);
  assert.equal(challenge.modelInputSha256, inputSha256);
  assert.equal(authorization.modelInputSha256, inputSha256);
  assert.equal(
    authorization.providerCallSetSha256,
    challenge.providerCallSetSha256
  );
  assert.equal(
    authorization.providerRequestBuilderVersion,
    challenge.providerRequestBuilderVersion
  );
  assert.equal(
    authorization.providerCallCount,
    challenge.providerCallCount
  );
  assert.match(challenge.providerCallSetSha256, /^[a-f0-9]{64}$/);
  assert.equal(
    challenge.providerRequestBuilderVersion,
    "track-b-provider-request-builder-v3"
  );
  assert.equal(challenge.providerCallCount, 6);
  assert.ok(Array.isArray(challenge.outboundRequestSha256s));
  assert.equal(challenge.outboundRequestSha256s.length, 6);
  assert.equal(
    new Set(challenge.outboundRequestSha256s).size,
    challenge.outboundRequestSha256s.length
  );
  assert.ok(
    challenge.outboundRequestSha256s.every((hash) =>
      /^[a-f0-9]{64}$/.test(hash)
    )
  );
  assert.equal(authorization.dispatchSha256, dispatchSha256);
  assert.equal(authorization.endpointHost, "api.deepseek.com");
  assert.equal(authorization.model, "deepseek-v4-pro");
  assert.equal(authorization.authorizedInputCount, 15);
  assert.equal(authorization.excludedZeroCallControlCount, 3);
  assert.equal(authorization.zeroCallControlsExcluded, true);
  assert.equal(authorization.humanGroundTruthExcludedFromPayload, true);
  assert.equal(authorization.findingOrVerdictExcludedFromPayload, true);
  assert.equal(authorization.oneTimeExecution, true);
  assert.equal(
    authorization.confirmationSource,
    "CODEX_THREAD_USER_CONFIRMATION"
  );
  assert.equal(typeof authorization.confirmationStatement, "string");
  for (const required of [
    challenge.nonce,
    dispatchSha256,
    inputSha256,
    challenge.providerCallSetSha256,
    "api.deepseek.com",
    "deepseek-v4-pro",
    "6",
    "15",
    "3"
  ]) {
    assert.ok(
      authorization.confirmationStatement.includes(required),
      `confirmationStatement must include ${required}`
    );
  }
  assert.equal(challenge.endpointHost, authorization.endpointHost);
  assert.equal(challenge.model, authorization.model);
  assert.equal(
    challenge.authorizedInputCount,
    authorization.authorizedInputCount
  );
  assert.equal(
    challenge.excludedZeroCallControlCount,
    authorization.excludedZeroCallControlCount
  );
  assert.equal(challenge.zeroCallControlsExcluded, true);
  assert.equal(challenge.humanGroundTruthExcludedFromPayload, true);
  assert.equal(challenge.findingOrVerdictExcludedFromPayload, true);
  assert.equal(typeof authorization.approvedBy, "string");
  assert.ok(authorization.approvedBy.trim());
  assert.doesNotMatch(
    authorization.approvedBy,
    /(codex|agent|model|artificial intelligence|\bai\b)/i,
    "AI agents cannot authorize external egress"
  );

  assert.ok(
    isCanonicalIso(dispatchCreatedAt),
    "dispatchCreatedAt must be canonical ISO-8601"
  );
  assert.ok(
    isCanonicalIso(authorization.approvedAt),
    "approvedAt must be canonical ISO-8601"
  );
  assert.ok(
    isCanonicalIso(authorization.expiresAt),
    "expiresAt must be canonical ISO-8601"
  );
  const dispatchCreatedAtMillis = Date.parse(dispatchCreatedAt);
  assert.ok(
    isCanonicalIso(challenge.createdAt),
    "challenge.createdAt must be canonical ISO-8601"
  );
  assert.ok(
    isCanonicalIso(challenge.expiresAt),
    "challenge.expiresAt must be canonical ISO-8601"
  );
  const challengeCreatedAtMillis = Date.parse(challenge.createdAt);
  const challengeExpiresAtMillis = Date.parse(challenge.expiresAt);
  const approvedAtMillis = Date.parse(authorization.approvedAt);
  const expiresAtMillis = Date.parse(authorization.expiresAt);
  const validationTimeMillis =
    validationTime instanceof Date
      ? validationTime.getTime()
      : Date.parse(validationTime);
  if (!(validationTime instanceof Date)) {
    assert.ok(
      isCanonicalIso(validationTime),
      "validationTime must be canonical ISO-8601"
    );
  }
  assert.ok(
    Number.isFinite(dispatchCreatedAtMillis),
    "dispatchCreatedAt must be ISO-8601"
  );
  assert.ok(
    Number.isFinite(approvedAtMillis),
    "approvedAt must be ISO-8601"
  );
  assert.ok(
    Number.isFinite(expiresAtMillis),
    "expiresAt must be ISO-8601"
  );
  assert.ok(
    Number.isFinite(validationTimeMillis),
    "validationTime must be ISO-8601"
  );
  assert.ok(
    approvedAtMillis >= dispatchCreatedAtMillis,
    "egress approval cannot precede the bound dispatch"
  );
  assert.ok(
    challengeCreatedAtMillis >= dispatchCreatedAtMillis,
    "egress challenge cannot precede the bound dispatch"
  );
  assert.ok(
    challengeExpiresAtMillis > challengeCreatedAtMillis &&
      challengeExpiresAtMillis - challengeCreatedAtMillis <= 86_400_000,
    "egress challenge validity is invalid"
  );
  assert.ok(
    approvedAtMillis >= challengeCreatedAtMillis,
    "egress approval cannot precede the bound challenge"
  );
  assert.ok(
    approvedAtMillis <= validationTimeMillis,
    "egress approval cannot be in the future"
  );
  assert.ok(
    expiresAtMillis > approvedAtMillis,
    "egress authorization must expire after approval"
  );
  assert.ok(
    validationTimeMillis < expiresAtMillis,
    "egress authorization is expired"
  );
  assert.ok(
    validationTimeMillis < challengeExpiresAtMillis,
    "egress challenge is expired"
  );
  assert.ok(
    expiresAtMillis <= challengeExpiresAtMillis,
    "egress authorization cannot outlive the challenge"
  );
  assert.ok(
    expiresAtMillis - approvedAtMillis <= 86_400_000,
    "egress authorization cannot be valid for more than 24 hours"
  );

  return {
    scope: authorization.scope,
    purpose: authorization.purpose,
    endpointHost: authorization.endpointHost,
    model: authorization.model,
    providerCallSetSha256: authorization.providerCallSetSha256,
    providerRequestBuilderVersion:
      authorization.providerRequestBuilderVersion,
    providerCallCount: authorization.providerCallCount,
    authorizedInputCount: authorization.authorizedInputCount,
    excludedZeroCallControlCount:
      authorization.excludedZeroCallControlCount,
    challengePath: authorization.challengePath,
    challengeSha256: authorization.challengeSha256,
    challengeNonce: authorization.challengeNonce,
    oneTimeExecution: authorization.oneTimeExecution,
    approvedBy: authorization.approvedBy,
    approvedAt: new Date(authorization.approvedAt).toISOString(),
    expiresAt: new Date(authorization.expiresAt).toISOString(),
    confirmationSource: authorization.confirmationSource
  };
}

function requireObject(value) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Track B egress authorization must be an object");
  }
}

function isCanonicalIso(value) {
  if (
    typeof value !== "string" ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(value)
  ) {
    return false;
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) && new Date(parsed).toISOString() === value;
}
