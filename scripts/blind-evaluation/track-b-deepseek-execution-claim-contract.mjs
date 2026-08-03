const CLAIM_FIELDS = new Set([
  "schemaVersion",
  "status",
  "dispatchSha256",
  "modelInputSha256",
  "providerCallSetSha256",
  "providerCallCount",
  "egressAuthorizationSha256",
  "egressChallengeSha256",
  "endpointHost",
  "model",
  "resolverVersion",
  "pinnedAddressSetSha256",
  "claimedAt"
]);

export const TRACK_B_DEEPSEEK_RESOLVER_VERSION =
  "request-scoped-pinned-dns-v2";

const HOLDOUT_CLAIM_SCHEMA =
  "task-eval-002-track-b-deepseek-execution-claim-v2";
const SUCCESSOR_CLAIM_SCHEMA =
  "task-eval-003-track-b-successor-deepseek-execution-claim-v1";
const FINAL_CLAIM_SCHEMA =
  "task-eval-004-track-b-final-deepseek-execution-claim-v1";
const FIFTH_CLAIM_SCHEMA =
  "task-eval-005-track-b-fifth-deepseek-execution-claim-v2";

export function buildTrackBDeepSeekExecutionClaim(values) {
  return buildTrackBExecutionClaim(values, HOLDOUT_CLAIM_SCHEMA);
}

export function buildTrackBSuccessorDeepSeekExecutionClaim(values) {
  return buildTrackBExecutionClaim(values, SUCCESSOR_CLAIM_SCHEMA);
}

export function buildTrackBFinalDeepSeekExecutionClaim(values) {
  return buildTrackBExecutionClaim(values, FINAL_CLAIM_SCHEMA);
}

export function buildTrackBFifthDeepSeekExecutionClaim(values) {
  return buildTrackBExecutionClaim(values, FIFTH_CLAIM_SCHEMA);
}

function buildTrackBExecutionClaim(values, schemaVersion) {
  return validateTrackBExecutionClaim({
    schemaVersion,
    status: "ONE_TIME_EXECUTION_CLAIMED",
    dispatchSha256: values.dispatchSha256,
    modelInputSha256: values.modelInputSha256,
    providerCallSetSha256: values.providerCallSetSha256,
    providerCallCount: values.providerCallCount,
    egressAuthorizationSha256: values.egressAuthorizationSha256,
    egressChallengeSha256: values.egressChallengeSha256,
    endpointHost: "api.deepseek.com",
    model: "deepseek-v4-pro",
    resolverVersion: TRACK_B_DEEPSEEK_RESOLVER_VERSION,
    pinnedAddressSetSha256: values.pinnedAddressSetSha256,
    claimedAt: values.claimedAt
  }, {}, schemaVersion);
}

export function validateTrackBDeepSeekExecutionClaim(
  value,
  expected = {}
) {
  return validateTrackBExecutionClaim(
    value,
    expected,
    HOLDOUT_CLAIM_SCHEMA
  );
}

export function validateTrackBSuccessorDeepSeekExecutionClaim(
  value,
  expected = {}
) {
  return validateTrackBExecutionClaim(
    value,
    expected,
    SUCCESSOR_CLAIM_SCHEMA
  );
}

export function validateTrackBFinalDeepSeekExecutionClaim(
  value,
  expected = {}
) {
  return validateTrackBExecutionClaim(
    value,
    expected,
    FINAL_CLAIM_SCHEMA
  );
}

export function validateTrackBFifthDeepSeekExecutionClaim(
  value,
  expected = {}
) {
  return validateTrackBExecutionClaim(
    value,
    expected,
    FIFTH_CLAIM_SCHEMA
  );
}

function validateTrackBExecutionClaim(value, expected, schemaVersion) {
  if (
    value === null ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    Object.keys(value).length !== CLAIM_FIELDS.size ||
    Object.keys(value).some((key) => !CLAIM_FIELDS.has(key)) ||
    value.schemaVersion !== schemaVersion ||
    value.status !== "ONE_TIME_EXECUTION_CLAIMED" ||
    !isSha256(value.dispatchSha256) ||
    !isSha256(value.modelInputSha256) ||
    !isSha256(value.providerCallSetSha256) ||
    !Number.isSafeInteger(value.providerCallCount) ||
    value.providerCallCount <= 0 ||
    !isSha256(value.egressAuthorizationSha256) ||
    !isSha256(value.egressChallengeSha256) ||
    value.endpointHost !== "api.deepseek.com" ||
    value.model !== "deepseek-v4-pro" ||
    value.resolverVersion !== TRACK_B_DEEPSEEK_RESOLVER_VERSION ||
    !isSha256(value.pinnedAddressSetSha256) ||
    !isCanonicalIso(value.claimedAt)
  ) {
    throw new Error("TRACK_B_DEEPSEEK_EXECUTION_CLAIM_INVALID");
  }
  for (const field of [
    "dispatchSha256",
    "modelInputSha256",
    "providerCallSetSha256",
    "providerCallCount",
    "egressAuthorizationSha256",
    "egressChallengeSha256",
    "pinnedAddressSetSha256"
  ]) {
    if (field in expected && value[field] !== expected[field]) {
      throw new Error(
        "TRACK_B_DEEPSEEK_EXECUTION_CLAIM_BINDING_MISMATCH"
      );
    }
  }
  if (
    "startedAt" in expected &&
    (!isCanonicalIso(expected.startedAt) ||
      Date.parse(value.claimedAt) < Date.parse(expected.startedAt))
  ) {
    throw new Error("TRACK_B_DEEPSEEK_EXECUTION_CLAIM_TIME_INVALID");
  }
  if (
    "completedAt" in expected &&
    (!isCanonicalIso(expected.completedAt) ||
      Date.parse(value.claimedAt) > Date.parse(expected.completedAt))
  ) {
    throw new Error("TRACK_B_DEEPSEEK_EXECUTION_CLAIM_TIME_INVALID");
  }
  if (
    "startedAt" in expected &&
    "completedAt" in expected &&
    Date.parse(expected.completedAt) < Date.parse(expected.startedAt)
  ) {
    throw new Error("TRACK_B_DEEPSEEK_EXECUTION_CLAIM_TIME_INVALID");
  }
  return value;
}

const isSha256 = (value) =>
  typeof value === "string" && /^[a-f0-9]{64}$/.test(value);

const isCanonicalIso = (value) =>
  typeof value === "string" &&
  !Number.isNaN(Date.parse(value)) &&
  new Date(value).toISOString() === value;
