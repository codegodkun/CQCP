import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  validateTrackBAdmissionModelInput
} from "./track-b-admission-opinion-contract.mjs";
import {
  TRACK_B_ADMISSION_COUNTS
} from "./track-b-admission-constants.mjs";
import {
  TRACK_B_PROVIDER_CALL_COUNT,
  TRACK_B_PROVIDER_REQUEST_BUILDER_VERSION,
  buildTrackBProviderCallSetArtifact
} from "./track-b-provider-request-contract.mjs";

const [repoArg, createdAtArg, expiresAtArg, nonce] =
  process.argv.slice(2);
if (
  !repoArg ||
  !isCanonicalIso(createdAtArg) ||
  !isCanonicalIso(expiresAtArg) ||
  !/^TBE-[a-f0-9]{32}$/.test(nonce ?? "")
) {
  throw new Error(
    "Usage: node prepare-track-b-egress-challenge.mjs " +
      "<repo-root> <created-at> <expires-at> <TBE-nonce>"
  );
}
const createdAt = new Date(createdAtArg).toISOString();
const expiresAt = new Date(expiresAtArg).toISOString();
assert.ok(Date.parse(expiresAt) > Date.parse(createdAt));
assert.ok(
  Date.parse(expiresAt) - Date.parse(createdAt) <= 86_400_000,
  "Track B egress challenge cannot be valid for more than 24 hours"
);
assert.ok(Date.parse(createdAt) <= Date.now() + 300_000);

const repoRoot = resolve(repoArg);
const runRoot = "outputs/task-eval-002/track-b-admission-run-v3";
const dispatchRelativePath = `${runRoot}/dispatch.json`;
const dispatchHashRelativePath = `${runRoot}/dispatch.sha256`;
const challengeRelativePath =
  `${runRoot}/egress-authorization-challenge.json`;
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const readRelative = (relativePath) =>
  readFile(resolve(repoRoot, ...relativePath.split("/")));

const dispatchBytes = await readRelative(dispatchRelativePath);
const dispatchSha256 = sha256(dispatchBytes);
const dispatchHash = (
  await readRelative(dispatchHashRelativePath)
)
  .toString("utf8")
  .trim()
  .split(/\s+/)[0];
assert.equal(dispatchSha256, dispatchHash);
const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
assert.equal(
  dispatch.status,
  "FROZEN_AFTER_HUMAN_CONFIRMATION_BEFORE_MODEL_EXECUTION"
);
assert.equal(
  dispatch.egressAuthorizationChallengePath,
  challengeRelativePath
);
assert.ok(Date.parse(createdAt) >= Date.parse(dispatch.createdAt));
const inputBytes = await readRelative(dispatch.modelInputPath);
assert.equal(sha256(inputBytes), dispatch.modelInputSha256);
const input = validateTrackBAdmissionModelInput(
  JSON.parse(inputBytes.toString("utf8"))
);
assert.equal(input.packetCount, TRACK_B_ADMISSION_COUNTS.eligible);
const promptBytes = await readRelative(dispatch.promptPath);
const expectedCallSet = buildTrackBProviderCallSetArtifact(
  input,
  promptBytes
);
const expectedCallSetBytes = Buffer.from(
  `${JSON.stringify(expectedCallSet, null, 2)}\n`,
  "utf8"
);
const callSetBytes = await readRelative(dispatch.providerCallSetPath);
assert.deepEqual(
  JSON.parse(callSetBytes.toString("utf8")),
  expectedCallSet
);
assert.equal(sha256(callSetBytes), dispatch.providerCallSetSha256);
assert.equal(sha256(expectedCallSetBytes), dispatch.providerCallSetSha256);
assert.equal(
  dispatch.providerRequestBuilderVersion,
  TRACK_B_PROVIDER_REQUEST_BUILDER_VERSION
);
assert.equal(dispatch.providerCallCount, TRACK_B_PROVIDER_CALL_COUNT);

const challenge = {
  schemaVersion: "task-eval-002-track-b-egress-challenge-v2",
  status: "AWAITING_EXACT_USER_EGRESS_AUTHORIZATION",
  nonce,
  createdAt,
  expiresAt,
  purpose: "TASK-EVAL-002_TRACK_B_ADMISSION_ONLY",
  dispatchPath: dispatchRelativePath,
  dispatchSha256,
  modelInputPath: dispatch.modelInputPath,
  modelInputSha256: dispatch.modelInputSha256,
  providerCallSetPath: dispatch.providerCallSetPath,
  providerCallSetSha256: dispatch.providerCallSetSha256,
  providerRequestBuilderVersion:
    dispatch.providerRequestBuilderVersion,
  providerCallCount: dispatch.providerCallCount,
  outboundRequestSha256s: expectedCallSet.calls.map(
    (call) => call.outboundRequestSha256
  ),
  endpointHost: "api.deepseek.com",
  model: "deepseek-v4-pro",
  authorizedInputCount: TRACK_B_ADMISSION_COUNTS.eligible,
  excludedZeroCallControlCount:
    TRACK_B_ADMISSION_COUNTS.zeroCallControls,
  zeroCallControlsExcluded: true,
  humanGroundTruthExcludedFromPayload: true,
  findingOrVerdictExcludedFromPayload: true,
  instruction:
    "授权必须在 Codex 对话中逐字绑定 nonce、dispatchSha256、" +
    "modelInputSha256、providerCallSetSha256、endpointHost、model、" +
    "6 calls/15 inputs/3 controls 范围；仅允许一次正式执行。"
};
const challengePath = resolve(
  repoRoot,
  ...challengeRelativePath.split("/")
);
await writeFile(
  challengePath,
  `${JSON.stringify(challenge, null, 2)}\n`,
  { encoding: "utf8", flag: "wx" }
);
process.stdout.write(
  `${JSON.stringify({
    status: challenge.status,
    nonce,
    dispatchSha256,
    modelInputSha256: dispatch.modelInputSha256,
    providerCallSetSha256: dispatch.providerCallSetSha256,
    providerCallCount: dispatch.providerCallCount,
    challengeSha256: sha256(await readFile(challengePath)),
    expiresAt
  })}\n`
);

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
