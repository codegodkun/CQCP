import assert from "node:assert/strict";
import { createHash } from "node:crypto";

import { validateMvp002StandingEgressGrant } from
  "./mvp002-standing-egress-grant.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";
import { validateTrackBRecoveryCorpus } from
  "./track-b-holdout-contract.mjs";
import {
  TRACK_B_PROVIDER_RECOVERY_MODEL,
  TRACK_B_PROVIDER_RECOVERY_PROMPT_SHA256,
  TRACK_B_PROVIDER_RECOVERY_REQUEST_BUILDER_VERSION,
  buildTrackBProviderRecoveryCall
} from "./track-b-provider-recovery-request-contract.mjs";

export const TRACK_B_PROVIDER_RECOVERY_ADMISSION_OUTPUT_ROOT =
  "outputs/task-eval-006/track-b-recovery-v1/run-v1";
export const TRACK_B_PROVIDER_RECOVERY_ADMISSION_CALL_COUNT = 9;
export const TRACK_B_PROVIDER_RECOVERY_ZERO_CALL_COUNT = 3;

const PATHS = Object.freeze({
  corpus: "outputs/task-eval-006/track-b-recovery-v1/corpus.json",
  humanSeal:
    "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json",
  input: `${TRACK_B_PROVIDER_RECOVERY_ADMISSION_OUTPUT_ROOT}/model-input.json`,
  callSet:
    `${TRACK_B_PROVIDER_RECOVERY_ADMISSION_OUTPUT_ROOT}/provider-call-set.json`,
  dispatch: `${TRACK_B_PROVIDER_RECOVERY_ADMISSION_OUTPUT_ROOT}/dispatch.json`,
  receipt:
    `${TRACK_B_PROVIDER_RECOVERY_ADMISSION_OUTPUT_ROOT}/derived-egress-receipt.json`,
  grant: "scripts/blind-evaluation/mvp002-standing-egress-grant.json",
  prompt:
    "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt"
});

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

export function buildTrackBProviderRecoveryAdmissionArtifacts({
  corpusBytes,
  humanGroundTruthSealBytes,
  promptBytes,
  grantBytes,
  createdAt
}) {
  assert.ok(Buffer.isBuffer(corpusBytes));
  assert.ok(Buffer.isBuffer(humanGroundTruthSealBytes));
  assert.ok(Buffer.isBuffer(promptBytes));
  assert.ok(Buffer.isBuffer(grantBytes));
  assert.equal(new Date(createdAt).toISOString(), createdAt);

  const grant = validateMvp002StandingEgressGrant(
    parseJsonBytesRejectDuplicateKeys(grantBytes)
  );
  assert.ok(grant.scope.allowedPurposes.includes("TRACK_B_EVALUATION"));
  assert.ok(grant.scope.allowedDataClasses.includes("SYNTHETIC_CORPUS"));
  assert.ok(grant.provider.allowedModels.includes(TRACK_B_PROVIDER_RECOVERY_MODEL));

  const corpus = validateTrackBRecoveryCorpus(
    parseJsonBytesRejectDuplicateKeys(corpusBytes)
  );
  const humanSeal = parseJsonBytesRejectDuplicateKeys(
    humanGroundTruthSealBytes
  );
  assert.equal(
    humanSeal.schemaVersion,
    "task-eval-006-track-b-recovery-human-ground-truth-v1"
  );
  assert.equal(humanSeal.status, "ACCEPTED_HUMAN_GROUND_TRUTH");
  assert.equal(humanSeal.corpusSha256, sha256(corpusBytes));
  assert.equal(humanSeal.entryCount, 12);
  assert.equal(humanSeal.modelInputCreated, false);
  assert.equal(humanSeal.networkCallAllowedByThisSeal, false);
  assert.ok(Date.parse(humanSeal.confirmedAt) < Date.parse(createdAt));

  const eligiblePackets = corpus.packets.filter(
    (packet) =>
      packet.admission?.status === "ELIGIBLE" &&
      packet.admission?.modelCallAllowed === true
  );
  const controls = corpus.packets
    .filter((packet) => packet.admission?.modelCallAllowed !== true)
    .map((packet) => ({
      packetId: packet.packetId,
      caseId: packet.sampleId,
      status: packet.admission.status,
      modelCallAllowed: false,
      reasonCodes: [...packet.admission.reasonCodes]
    }));
  assert.equal(
    eligiblePackets.length,
    TRACK_B_PROVIDER_RECOVERY_ADMISSION_CALL_COUNT
  );
  assert.equal(controls.length, TRACK_B_PROVIDER_RECOVERY_ZERO_CALL_COUNT);
  assert.ok(controls.every((control) => control.status === "ZERO_CALL_REQUIRED"));

  const calls = eligiblePackets.map((packet) =>
    buildTrackBProviderRecoveryCall(packet, promptBytes)
  );
  assert.equal(new Set(calls.map((call) => call.callId)).size, calls.length);

  const modelInput = {
    schemaVersion: "task-eval-006-track-b-recovery-model-input-v1",
    status: "FROZEN_BLIND_FORMAL_ADMISSION_INPUT",
    purpose: "TRACK_B_EVALUATION",
    dataClass: "SYNTHETIC_CORPUS",
    groundTruthIncluded: false,
    cqcpActualOrExpectedIncluded: false,
    findingOrVerdictIncluded: false,
    packetCount: calls.length,
    packets: calls.map((call) => call.input)
  };
  const modelInputSha256 = sha256(jsonBytes(modelInput));

  const callSet = {
    schemaVersion: "task-eval-006-track-b-recovery-call-set-v3",
    status: "FROZEN_READY_FOR_FORMAL_ADMISSION",
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
  const humanSealSha256 = sha256(humanGroundTruthSealBytes);

  const dispatch = {
    schemaVersion: "task-eval-006-track-b-recovery-dispatch-v1",
    status: "FROZEN_READY_FOR_FORMAL_ADMISSION",
    createdAt,
    milestoneId: "MILESTONE-MVP-002",
    taskId: "TASK-EVAL-006",
    purpose: "TRACK_B_EVALUATION",
    dataClass: "SYNTHETIC_CORPUS",
    formalAdmissionAffected: true,
    priorFailedClaimReused: false,
    corpusPath: PATHS.corpus,
    corpusSha256: sha256(corpusBytes),
    humanGroundTruthSealPath: PATHS.humanSeal,
    humanGroundTruthSealSha256: humanSealSha256,
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
    zeroCallControlCount: controls.length,
    zeroCallControls: controls,
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
    purpose: "TRACK_B_EVALUATION",
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
    zeroCallControlCount: controls.length,
    formalAdmissionAffected: true,
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
    controls,
    hashes: {
      modelInputSha256,
      callSetSha256,
      dispatchSha256,
      receiptSha256: sha256(jsonBytes(receipt)),
      standingGrantSha256: grantSha256,
      humanGroundTruthSealSha256: humanSealSha256
    },
    paths: PATHS
  };
}

export const serializeTrackBProviderRecoveryAdmissionArtifact = jsonBytes;
