import { createHash } from "node:crypto";

import {
  TRACK_B_HOLDOUT_COUNTS,
  TRACK_B_HOLDOUT_MODEL,
  TRACK_B_HOLDOUT_TRUSTED_PROMPT_SHA256
} from "./track-b-holdout-constants.mjs";
import {
  TRACK_B_FINAL_MODEL_INPUT_SCHEMA,
  TRACK_B_FIFTH_MODEL_INPUT_SCHEMA,
  TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
  TRACK_B_HOLDOUT_PROVIDER_INPUT_SCHEMA,
  TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA,
  validateTrackBFinalModelInput,
  validateTrackBFinalProviderInput,
  validateTrackBFifthModelInput,
  validateTrackBFifthProviderInput,
  validateTrackBHoldoutModelInput,
  validateTrackBHoldoutProviderInput,
  validateTrackBSuccessorModelInput,
  validateTrackBSuccessorProviderInput
} from "./track-b-holdout-opinion-contract.mjs";

export const TRACK_B_HOLDOUT_PROVIDER_MAX_OUTPUT_TOKENS = 1500;
export const TRACK_B_HOLDOUT_PROVIDER_CALL_COUNT =
  TRACK_B_HOLDOUT_COUNTS.providerCalls;
export const TRACK_B_HOLDOUT_PROVIDER_REQUEST_BUILDER_VERSION =
  "track-b-holdout-provider-request-builder-v1";
export const TRACK_B_SUCCESSOR_PROVIDER_REQUEST_BUILDER_VERSION =
  "track-b-successor-provider-request-builder-v1";
export const TRACK_B_FINAL_PROVIDER_REQUEST_BUILDER_VERSION =
  "track-b-final-provider-request-builder-v1";

const HOLDOUT_PROFILE = Object.freeze({
  modelInputSchema: TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
  callSetSchema:
    "task-eval-002-track-b-holdout-provider-call-set-v1",
  callPrefix: "TBH-MC",
  requestBuilderVersion:
    TRACK_B_HOLDOUT_PROVIDER_REQUEST_BUILDER_VERSION,
  validateModelInput: validateTrackBHoldoutModelInput,
  validateProviderInput: validateTrackBHoldoutProviderInput,
  trustedPromptSha256: TRACK_B_HOLDOUT_TRUSTED_PROMPT_SHA256
});

const SUCCESSOR_PROFILE = Object.freeze({
  modelInputSchema: TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA,
  callSetSchema:
    "task-eval-003-track-b-successor-provider-call-set-v1",
  callPrefix: "TBS-MC",
  requestBuilderVersion:
    TRACK_B_SUCCESSOR_PROVIDER_REQUEST_BUILDER_VERSION,
  validateModelInput: validateTrackBSuccessorModelInput,
  validateProviderInput: validateTrackBSuccessorProviderInput,
  trustedPromptSha256: TRACK_B_HOLDOUT_TRUSTED_PROMPT_SHA256
});

const FINAL_PROFILE = Object.freeze({
  modelInputSchema: TRACK_B_FINAL_MODEL_INPUT_SCHEMA,
  callSetSchema:
    "task-eval-004-track-b-final-provider-call-set-v1",
  callPrefix: "TBF-MC",
  requestBuilderVersion:
    TRACK_B_FINAL_PROVIDER_REQUEST_BUILDER_VERSION,
  validateModelInput: validateTrackBFinalModelInput,
  validateProviderInput: validateTrackBFinalProviderInput,
  trustedPromptSha256: TRACK_B_HOLDOUT_TRUSTED_PROMPT_SHA256
});

export const TRACK_B_FIFTH_PROVIDER_REQUEST_BUILDER_VERSION =
  "track-b-single-packet-provider-request-builder-v2";
export const TRACK_B_FIFTH_TRUSTED_PROMPT_SHA256 =
  "6a29b74fc890c3492e1658c08144a875325dbf11acd9012883d80e1e9d0f1bcc";
const FIFTH_PROFILE = Object.freeze({
  modelInputSchema: TRACK_B_FIFTH_MODEL_INPUT_SCHEMA,
  callSetSchema:
    "task-eval-005-track-b-fifth-provider-call-set-v2",
  callPrefix: "TB5-MC",
  requestBuilderVersion:
    TRACK_B_FIFTH_PROVIDER_REQUEST_BUILDER_VERSION,
  validateModelInput: validateTrackBFifthModelInput,
  validateProviderInput: validateTrackBFifthProviderInput,
  trustedPromptSha256: TRACK_B_FIFTH_TRUSTED_PROMPT_SHA256
});

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

export function buildTrackBHoldoutProviderCalls(
  masterInput,
  promptBytes
) {
  return buildTrackBProviderCalls(masterInput, promptBytes, HOLDOUT_PROFILE);
}

export function buildTrackBSuccessorProviderCalls(
  masterInput,
  promptBytes
) {
  return buildTrackBProviderCalls(
    masterInput,
    promptBytes,
    SUCCESSOR_PROFILE
  );
}

export function buildTrackBFinalProviderCalls(
  masterInput,
  promptBytes
) {
  return buildTrackBProviderCalls(masterInput, promptBytes, FINAL_PROFILE);
}

export function buildTrackBFifthProviderCalls(
  masterInput,
  promptBytes
) {
  return buildTrackBProviderCalls(masterInput, promptBytes, FIFTH_PROFILE);
}

function buildTrackBProviderCalls(masterInput, promptBytes, profile) {
  profile.validateModelInput(masterInput);
  if (!Buffer.isBuffer(promptBytes)) {
    throw new Error("TRACK_B_HOLDOUT_PROMPT_BYTES_REQUIRED");
  }
  const promptSha256 = sha256(promptBytes);
  if (promptSha256 !== profile.trustedPromptSha256) {
    throw new Error("TRACK_B_HOLDOUT_PROMPT_HASH_MISMATCH");
  }

  const calls = masterInput.packets.map((packet) => {
    const input = profile.validateProviderInput({
      schemaVersion: TRACK_B_HOLDOUT_PROVIDER_INPUT_SCHEMA,
      track: masterInput.track,
      taskId: packet.taskId,
      executionId: packet.executionId,
      family: packet.family,
      requestedRoles: [packet.candidateRole],
      packetCount: 1,
      packets: [packet]
    });
    const modelInputBytes = Buffer.from(JSON.stringify(input), "utf8");
    const modelInputSha256 = sha256(modelInputBytes);
    const outboundRequest = {
      model: TRACK_B_HOLDOUT_MODEL,
      thinking: { type: "disabled" },
      messages: [
        {
          role: "system",
          content: promptBytes.toString("utf8")
        },
        {
          role: "user",
          content: modelInputBytes.toString("utf8")
        }
      ],
      response_format: { type: "json_object" },
      stream: false,
      max_tokens: TRACK_B_HOLDOUT_PROVIDER_MAX_OUTPUT_TOKENS
    };
    const outboundRequestBytes = Buffer.from(
      JSON.stringify(outboundRequest),
      "utf8"
    );
    return {
      callId: `${profile.callPrefix}-${modelInputSha256}`,
      sampleId: packet.sampleId,
      input,
      modelInputBytes,
      modelInputSha256,
      outboundRequest,
      outboundRequestBytes,
      outboundRequestSha256: sha256(outboundRequestBytes)
    };
  });

  if (
    calls.length !== TRACK_B_HOLDOUT_PROVIDER_CALL_COUNT ||
    calls.some((call) => call.input.packetCount !== 1) ||
    new Set(calls.map((call) => call.callId)).size !== calls.length ||
    new Set(
      calls.flatMap((call) =>
        call.input.packets.map((packet) => packet.packetId)
      )
    ).size !== TRACK_B_HOLDOUT_COUNTS.eligible
  ) {
    throw new Error("TRACK_B_HOLDOUT_PROVIDER_CALL_PARTITION_INVALID");
  }
  const callsPerExecution = new Map();
  for (const call of calls) {
    const key = `${call.input.taskId}\0${call.input.executionId}`;
    callsPerExecution.set(key, (callsPerExecution.get(key) ?? 0) + 1);
  }
  const counts = [...callsPerExecution.values()].sort((a, b) => a - b);
  if (
    callsPerExecution.size !== 2 ||
    JSON.stringify(counts) !== JSON.stringify([4, 5])
  ) {
    throw new Error("TRACK_B_HOLDOUT_PROVIDER_EXECUTION_BUDGET_INVALID");
  }
  return calls;
}

export function buildTrackBHoldoutProviderCallSetArtifact(
  masterInput,
  promptBytes
) {
  return buildTrackBProviderCallSetArtifact(
    masterInput,
    promptBytes,
    HOLDOUT_PROFILE
  );
}

export function buildTrackBSuccessorProviderCallSetArtifact(
  masterInput,
  promptBytes
) {
  return buildTrackBProviderCallSetArtifact(
    masterInput,
    promptBytes,
    SUCCESSOR_PROFILE
  );
}

export function buildTrackBFinalProviderCallSetArtifact(
  masterInput,
  promptBytes
) {
  return buildTrackBProviderCallSetArtifact(
    masterInput,
    promptBytes,
    FINAL_PROFILE
  );
}

export function buildTrackBFifthProviderCallSetArtifact(
  masterInput,
  promptBytes
) {
  return buildTrackBProviderCallSetArtifact(
    masterInput,
    promptBytes,
    FIFTH_PROFILE
  );
}

function buildTrackBProviderCallSetArtifact(
  masterInput,
  promptBytes,
  profile
) {
  if (masterInput.schemaVersion !== profile.modelInputSchema) {
    throw new Error("TRACK_B_HOLDOUT_MASTER_INPUT_SCHEMA_INVALID");
  }
  const calls = buildTrackBProviderCalls(masterInput, promptBytes, profile);
  const masterModelInputBytes = Buffer.from(
    `${JSON.stringify(masterInput, null, 2)}\n`,
    "utf8"
  );
  return {
    schemaVersion: profile.callSetSchema,
    status: "OFFLINE_PLAN_REQUIRES_HUMAN_GROUND_TRUTH_SEAL",
    requestBuilderVersion: profile.requestBuilderVersion,
    model: TRACK_B_HOLDOUT_MODEL,
    promptSha256: sha256(promptBytes),
    masterModelInputSha256: sha256(masterModelInputBytes),
    maxOutputTokens: TRACK_B_HOLDOUT_PROVIDER_MAX_OUTPUT_TOKENS,
    callCount: calls.length,
    eligiblePacketCount: calls.length,
    calls: calls.map((call) => ({
      callId: call.callId,
      sampleId: call.sampleId,
      taskId: call.input.taskId,
      executionId: call.input.executionId,
      family: call.input.family,
      requestedRole: call.input.requestedRoles[0],
      packetId: call.input.packets[0].packetId,
      modelInputSha256: call.modelInputSha256,
      outboundRequestSha256: call.outboundRequestSha256
    }))
  };
}
