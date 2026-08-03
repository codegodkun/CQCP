import assert from "node:assert/strict";
import { createHash } from "node:crypto";

import { validateMvp002StandingEgressGrant } from
  "./mvp002-standing-egress-grant.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";
import {
  TRACK_B_PROVIDER_RECOVERY_MODEL,
  TRACK_B_PROVIDER_RECOVERY_PROMPT_SHA256,
  TRACK_B_PROVIDER_RECOVERY_REQUEST_BUILDER_VERSION,
  buildTrackBProviderRecoveryCall
} from "./track-b-provider-recovery-request-contract.mjs";

export const TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT =
  "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1";
export const TRACK_B_PROVIDER_RECOVERY_DIAGNOSTIC_CALL_COUNT = 4;

const PATHS = Object.freeze({
  input: `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/model-input.json`,
  callSet: `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/provider-call-set.json`,
  dispatch: `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/dispatch.json`,
  receipt: `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/derived-egress-receipt.json`,
  grant: "scripts/blind-evaluation/mvp002-standing-egress-grant.json",
  prompt:
    "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt"
});

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

export function buildTrackBProviderRecoveryDiagnosticArtifacts({
  fifthInputBytes,
  promptBytes,
  grantBytes,
  createdAt
}) {
  assert.ok(Buffer.isBuffer(fifthInputBytes));
  assert.ok(Buffer.isBuffer(promptBytes));
  assert.ok(Buffer.isBuffer(grantBytes));
  assert.equal(new Date(createdAt).toISOString(), createdAt);

  const grant = validateMvp002StandingEgressGrant(
    parseJsonBytesRejectDuplicateKeys(grantBytes)
  );
  assert.ok(grant.scope.allowedPurposes.includes("PROVIDER_VALIDATION"));
  assert.ok(grant.scope.allowedDataClasses.includes("SYNTHETIC_CORPUS"));
  assert.ok(grant.provider.allowedModels.includes(TRACK_B_PROVIDER_RECOVERY_MODEL));

  const fifthInput = parseJsonBytesRejectDuplicateKeys(fifthInputBytes);
  const runtimePackets = fifthInput.packets.filter((packet) =>
    packet.sampleId?.startsWith("TB5-CON-")
  );
  assert.equal(
    runtimePackets.length,
    TRACK_B_PROVIDER_RECOVERY_DIAGNOSTIC_CALL_COUNT
  );

  const calls = runtimePackets.map((packet) =>
    buildTrackBProviderRecoveryCall(packet, promptBytes)
  );
  assert.equal(new Set(calls.map((call) => call.callId)).size, calls.length);

  const modelInput = {
    schemaVersion: "task-eval-006-provider-recovery-diagnostic-input-v1",
    status: "FROZEN_NON_ADMISSION_DIAGNOSTIC_INPUT",
    purpose: "PROVIDER_VALIDATION",
    dataClass: "SYNTHETIC_CORPUS",
    formalAdmissionAffected: false,
    groundTruthIncluded: false,
    findingOrVerdictIncluded: false,
    sourceFifthModelInputSha256: sha256(fifthInputBytes),
    packetCount: calls.length,
    packets: calls.map((call) => call.input)
  };
  const modelInputSha256 = sha256(jsonBytes(modelInput));

  const callSet = {
    schemaVersion: "task-eval-006-provider-recovery-call-set-v3",
    status: "FROZEN_READY_FOR_NON_ADMISSION_DIAGNOSTIC",
    requestBuilderVersion:
      TRACK_B_PROVIDER_RECOVERY_REQUEST_BUILDER_VERSION,
    model: TRACK_B_PROVIDER_RECOVERY_MODEL,
    promptSha256: TRACK_B_PROVIDER_RECOVERY_PROMPT_SHA256,
    masterModelInputSha256: modelInputSha256,
    callCount: calls.length,
    inputCount: calls.length,
    calls: calls.map((call) => ({
      callId: call.callId,
      packetId: call.input.packetId,
      modelInputSha256: call.modelInputSha256,
      outboundRequestSha256: call.outboundRequestSha256
    }))
  };
  const callSetSha256 = sha256(jsonBytes(callSet));
  const grantSha256 = sha256(grantBytes);

  const dispatch = {
    schemaVersion: "task-eval-006-provider-recovery-dispatch-v1",
    status: "FROZEN_READY_FOR_NON_ADMISSION_DIAGNOSTIC",
    createdAt,
    milestoneId: "MILESTONE-MVP-002",
    taskId: "TASK-EVAL-006",
    purpose: "PROVIDER_VALIDATION",
    dataClass: "SYNTHETIC_CORPUS",
    formalAdmissionAffected: false,
    sameTaskEval005ClaimRestored: false,
    modelInputPath: PATHS.input,
    modelInputSha256,
    providerCallSetPath: PATHS.callSet,
    providerCallSetSha256: callSetSha256,
    standingGrantPath: PATHS.grant,
    standingGrantSha256: grantSha256,
    promptPath: PATHS.prompt,
    promptSha256: TRACK_B_PROVIDER_RECOVERY_PROMPT_SHA256,
    derivedEgressReceiptPath: PATHS.receipt,
    endpointOrigin: "https://api.deepseek.com:443",
    endpointHost: "api.deepseek.com",
    model: TRACK_B_PROVIDER_RECOVERY_MODEL,
    providerCallCount: calls.length,
    inputCount: calls.length,
    humanGroundTruthExcludedFromPayload: true,
    cqcpActualOrExpectedExcludedFromPayload: true,
    findingOrVerdictExcludedFromPayload: true,
    secretOrRawKeyIncluded: false,
    networkCallPerformed: false
  };
  const dispatchSha256 = sha256(jsonBytes(dispatch));

  const receipt = {
    schemaVersion: "mvp002-derived-egress-authorization-receipt-v1",
    status: "DERIVED_FROM_ACTIVE_STANDING_GRANT_BEFORE_NETWORK",
    createdAt,
    milestoneId: "MILESTONE-MVP-002",
    taskId: "TASK-EVAL-006",
    purpose: "PROVIDER_VALIDATION",
    standingGrantPath: PATHS.grant,
    standingGrantSha256: grantSha256,
    dispatchPath: PATHS.dispatch,
    dispatchSha256,
    actualInputPath: PATHS.input,
    actualInputSha256: modelInputSha256,
    providerCallSetPath: PATHS.callSet,
    providerCallSetSha256: callSetSha256,
    outboundRequestSha256s: calls.map(
      (call) => call.outboundRequestSha256
    ),
    endpointOrigin: "https://api.deepseek.com:443",
    model: TRACK_B_PROVIDER_RECOVERY_MODEL,
    callCount: calls.length,
    inputCount: calls.length,
    formalAdmissionAffected: false,
    humanGroundTruthExcludedFromPayload: true,
    cqcpActualOrExpectedExcludedFromPayload: true,
    findingOrVerdictExcludedFromPayload: true,
    secretOrRawKeyIncluded: false,
    networkCallPerformed: false
  };

  return {
    modelInput,
    callSet,
    dispatch,
    receipt,
    calls,
    hashes: {
      modelInputSha256,
      callSetSha256,
      dispatchSha256,
      receiptSha256: sha256(jsonBytes(receipt)),
      standingGrantSha256: grantSha256
    },
    paths: PATHS
  };
}

export const serializeTrackBProviderRecoveryArtifact = jsonBytes;
