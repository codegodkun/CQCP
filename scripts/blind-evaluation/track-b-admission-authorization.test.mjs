import assert from "node:assert/strict";
import test from "node:test";

import {
  validateTrackBEgressAuthorization
} from "./track-b-admission-authorization.mjs";

const dispatchCreatedAt = "2026-07-29T10:00:00.000Z";
const validationTime = "2026-07-29T10:10:00.000Z";
const challengeSha256 = "c".repeat(64);
const challenge = () => ({
  schemaVersion: "task-eval-002-track-b-egress-challenge-v2",
  status: "AWAITING_EXACT_USER_EGRESS_AUTHORIZATION",
  nonce: "TBE-5123456789abcdef0123456789abcdef",
  createdAt: "2026-07-29T10:01:00.000Z",
  expiresAt: "2026-07-29T12:00:00.000Z",
  purpose: "TASK-EVAL-002_TRACK_B_ADMISSION_ONLY",
  dispatchPath:
    "outputs/task-eval-002/track-b-admission-run-v3/dispatch.json",
  dispatchSha256: "b".repeat(64),
  modelInputPath:
    "outputs/task-eval-002/track-b-admission-run-v3/model-input.json",
  modelInputSha256: "a".repeat(64),
  providerCallSetPath:
    "outputs/task-eval-002/track-b-admission-run-v3/provider-call-set.json",
  providerCallSetSha256: "d".repeat(64),
  providerRequestBuilderVersion: "track-b-provider-request-builder-v3",
  providerCallCount: 6,
  outboundRequestSha256s: Array.from(
    { length: 6 },
    (_, index) => index.toString(16).padStart(64, "0")
  ),
  endpointHost: "api.deepseek.com",
  model: "deepseek-v4-pro",
  authorizedInputCount: 15,
  excludedZeroCallControlCount: 3,
  zeroCallControlsExcluded: true,
  humanGroundTruthExcludedFromPayload: true,
  findingOrVerdictExcludedFromPayload: true,
  instruction:
    "授权必须在 Codex 对话中逐字绑定 nonce、dispatchSha256、" +
    "modelInputSha256、providerCallSetSha256、endpointHost、model、" +
    "6 calls/15 inputs/3 controls 范围；仅允许一次正式执行。"
});
const authorization = () => ({
  schemaVersion: "task-eval-002-track-b-egress-authorization-v2",
  authorized: true,
  scope: "DEEPSEEK_TRACK_B_ADMISSION_EGRESS",
  purpose: "TASK-EVAL-002_TRACK_B_ADMISSION_ONLY",
  challengePath:
    "outputs/task-eval-002/track-b-admission-run-v3/" +
    "egress-authorization-challenge.json",
  challengeSha256,
  challengeNonce: "TBE-5123456789abcdef0123456789abcdef",
  modelInputSha256: "a".repeat(64),
  providerCallSetSha256: "d".repeat(64),
  providerRequestBuilderVersion: "track-b-provider-request-builder-v3",
  providerCallCount: 6,
  dispatchSha256: "b".repeat(64),
  endpointHost: "api.deepseek.com",
  model: "deepseek-v4-pro",
  authorizedInputCount: 15,
  excludedZeroCallControlCount: 3,
  zeroCallControlsExcluded: true,
  humanGroundTruthExcludedFromPayload: true,
  findingOrVerdictExcludedFromPayload: true,
  oneTimeExecution: true,
  approvedBy: "CQCP_PROJECT_OWNER",
  approvedAt: "2026-07-29T10:05:00.000Z",
  expiresAt: "2026-07-29T11:05:00.000Z",
  confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
  confirmationStatement:
    `确认 TBE-5123456789abcdef0123456789abcdef ` +
    `${"b".repeat(64)} ${"a".repeat(64)} ${"d".repeat(64)} ` +
    "api.deepseek.com deepseek-v4-pro 6 15 3"
});

const validate = (value, overrides = {}) =>
  validateTrackBEgressAuthorization({
    authorization: value,
    challenge: challenge(),
    challengeSha256,
    inputSha256: "a".repeat(64),
    dispatchSha256: "b".repeat(64),
    dispatchCreatedAt,
    validationTime,
    ...overrides
  });

test("Track B egress authorization binds exact payload, scope and exclusions", () => {
  assert.deepEqual(validate(authorization()), {
    scope: "DEEPSEEK_TRACK_B_ADMISSION_EGRESS",
    purpose: "TASK-EVAL-002_TRACK_B_ADMISSION_ONLY",
    endpointHost: "api.deepseek.com",
    model: "deepseek-v4-pro",
    providerCallSetSha256: "d".repeat(64),
    providerRequestBuilderVersion: "track-b-provider-request-builder-v3",
    providerCallCount: 6,
    authorizedInputCount: 15,
    excludedZeroCallControlCount: 3,
    challengePath:
      "outputs/task-eval-002/track-b-admission-run-v3/" +
      "egress-authorization-challenge.json",
    challengeSha256,
    challengeNonce: "TBE-5123456789abcdef0123456789abcdef",
    oneTimeExecution: true,
    approvedBy: "CQCP_PROJECT_OWNER",
    approvedAt: "2026-07-29T10:05:00.000Z",
    expiresAt: "2026-07-29T11:05:00.000Z",
    confirmationSource: "CODEX_THREAD_USER_CONFIRMATION"
  });
});

for (const [name, mutate] of [
  ["extra field", (value) => { value.extra = true; }],
  ["wrong input hash", (value) => { value.modelInputSha256 = "c".repeat(64); }],
  ["wrong dispatch hash", (value) => { value.dispatchSha256 = "c".repeat(64); }],
  ["wrong host", (value) => { value.endpointHost = "proxy.example"; }],
  ["wrong model", (value) => { value.model = "deepseek-v4-flash"; }],
  ["wrong purpose", (value) => { value.purpose = "GENERAL_EVALUATION"; }],
  ["wrong input count", (value) => { value.authorizedInputCount = 18; }],
  ["controls included", (value) => { value.zeroCallControlsExcluded = false; }],
  ["wrong control count", (value) => { value.excludedZeroCallControlCount = 0; }],
  ["ground truth included", (value) => { value.humanGroundTruthExcludedFromPayload = false; }],
  ["finding allowed", (value) => { value.findingOrVerdictExcludedFromPayload = false; }],
  ["replay enabled", (value) => { value.oneTimeExecution = false; }],
  ["wrong challenge", (value) => { value.challengeNonce = "TBE-6123456789abcdef0123456789abcdef"; }],
  ["AI approver", (value) => { value.approvedBy = "Codex agent"; }],
  ["wrong source", (value) => { value.confirmationSource = "LOCAL_FILE"; }],
  ["approval before dispatch", (value) => { value.approvedAt = "2026-07-29T09:59:59.000Z"; }],
  ["future approval", (value) => { value.approvedAt = "2026-07-29T10:10:01.000Z"; }],
  ["expiry before approval", (value) => { value.expiresAt = "2026-07-29T10:04:59.000Z"; }],
  ["expired", (value) => { value.expiresAt = "2026-07-29T10:10:00.000Z"; }]
]) {
  test(`Track B egress authorization rejects ${name}`, () => {
    const value = authorization();
    mutate(value);
    assert.throws(() => validate(value));
  });
}

test("Track B egress authorization rejects a changed challenge hash", () => {
  assert.throws(() =>
    validate(authorization(), { challengeSha256: "d".repeat(64) })
  );
});

test("Track B egress authorization rejects an expired challenge", () => {
  const expiredChallenge = challenge();
  expiredChallenge.expiresAt = "2026-07-29T10:09:59.000Z";
  assert.throws(() =>
    validate(authorization(), { challenge: expiredChallenge })
  );
});
