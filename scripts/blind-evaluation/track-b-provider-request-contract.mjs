import crypto from "node:crypto";

import {
  validateTrackBAdmissionModelInput,
  validateTrackBProviderModelInput
} from "./track-b-admission-opinion-contract.mjs";
import {
  TRACK_B_ADMISSION_TRUSTED_PROMPT_SHA256
} from "./track-b-admission-constants.mjs";

export const TRACK_B_PROVIDER_MODEL = "deepseek-v4-pro";
export const TRACK_B_PROVIDER_MAX_OUTPUT_TOKENS = 1500;
export const TRACK_B_PROVIDER_CALL_COUNT = 6;
export const TRACK_B_PROVIDER_MAX_CALLS_PER_TASK = 4;
export const TRACK_B_PROVIDER_INPUT_SCHEMA =
  "ROLE_CANDIDATE_ANCHOR_ABSTENTION_INPUT_V1";
export const TRACK_B_PROVIDER_REQUEST_BUILDER_VERSION =
  "track-b-provider-request-builder-v3";

const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");

export function buildTrackBProviderCalls(masterInput, promptBytes) {
  validateTrackBAdmissionModelInput(masterInput);
  if (!Buffer.isBuffer(promptBytes)) {
    throw new Error("TRACK_B_PROMPT_BYTES_REQUIRED");
  }
  const promptSha256 = sha256(promptBytes);
  if (promptSha256 !== TRACK_B_ADMISSION_TRUSTED_PROMPT_SHA256) {
    throw new Error("TRACK_B_PROMPT_HASH_MISMATCH");
  }

  const groups = new Map();
  for (const packet of masterInput.packets) {
    const key = JSON.stringify([
      packet.taskId,
      packet.executionId,
      packet.family
    ]);
    const group = groups.get(key) ?? [];
    group.push(packet);
    groups.set(key, group);
  }

  const calls = [];
  for (const packets of groups.values()) {
    const orderedPackets = [...packets].sort((left, right) =>
      left.candidateRole < right.candidateRole
        ? -1
        : left.candidateRole > right.candidateRole
          ? 1
          : 0
    );
    const first = orderedPackets[0];
    if (
      orderedPackets.some(
        (packet) =>
          packet.admission.reasonCodes[0] !==
          first.admission.reasonCodes[0]
      )
    ) {
      throw new Error("TRACK_B_PROVIDER_ADMISSION_CLASS_MIXED");
    }
    const input = validateTrackBProviderModelInput({
      schemaVersion: TRACK_B_PROVIDER_INPUT_SCHEMA,
      track: masterInput.track,
      taskId: first.taskId,
      executionId: first.executionId,
      family: first.family,
      requestedRoles: orderedPackets.map(
        (packet) => packet.candidateRole
      ),
      packetCount: orderedPackets.length,
      packets: orderedPackets
    });
    const modelInputBytes = Buffer.from(JSON.stringify(input), "utf8");
    const outboundRequest = {
      model: TRACK_B_PROVIDER_MODEL,
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
      max_tokens: TRACK_B_PROVIDER_MAX_OUTPUT_TOKENS
    };
    const outboundRequestBytes = Buffer.from(
      JSON.stringify(outboundRequest),
      "utf8"
    );
    const modelInputSha256 = sha256(modelInputBytes);
    calls.push({
      callId: `MC-${modelInputSha256}`,
      admissionClass: first.admission.reasonCodes[0],
      input,
      modelInputBytes,
      modelInputSha256,
      outboundRequest,
      outboundRequestBytes,
      outboundRequestSha256: sha256(outboundRequestBytes)
    });
  }
  if (
    calls.length !== TRACK_B_PROVIDER_CALL_COUNT ||
    calls.reduce((total, call) => total + call.input.packetCount, 0) !==
      masterInput.packetCount
  ) {
    throw new Error("TRACK_B_PROVIDER_CALL_PARTITION_INVALID");
  }
  const callsPerTask = new Map();
  const taskFamilyIdentities = new Set();
  for (const call of calls) {
    const taskIdentity = JSON.stringify([
      call.input.taskId,
      call.input.executionId
    ]);
    callsPerTask.set(
      taskIdentity,
      (callsPerTask.get(taskIdentity) ?? 0) + 1
    );
    const taskFamilyIdentity = JSON.stringify([
      call.input.taskId,
      call.input.executionId,
      call.input.family
    ]);
    if (taskFamilyIdentities.has(taskFamilyIdentity)) {
      throw new Error("TRACK_B_PROVIDER_TASK_FAMILY_DUPLICATED");
    }
    taskFamilyIdentities.add(taskFamilyIdentity);
  }
  if (
    callsPerTask.size !== 2 ||
    [...callsPerTask.values()].some(
      (count) =>
        count !== 3 || count > TRACK_B_PROVIDER_MAX_CALLS_PER_TASK
    )
  ) {
    throw new Error("TRACK_B_PROVIDER_TASK_BUDGET_INVALID");
  }
  return calls;
}

export function buildTrackBProviderCallSetArtifact(
  masterInput,
  promptBytes
) {
  const calls = buildTrackBProviderCalls(masterInput, promptBytes);
  return {
    schemaVersion: "task-eval-002-track-b-provider-call-set-v1",
    status: "FROZEN_BEFORE_EGRESS_AUTHORIZATION",
    requestBuilderVersion: TRACK_B_PROVIDER_REQUEST_BUILDER_VERSION,
    model: TRACK_B_PROVIDER_MODEL,
    promptSha256: sha256(promptBytes),
    maxOutputTokens: TRACK_B_PROVIDER_MAX_OUTPUT_TOKENS,
    callCount: calls.length,
    eligiblePacketCount: calls.reduce(
      (total, call) => total + call.input.packetCount,
      0
    ),
    calls: calls.map((call) => ({
      callId: call.callId,
      admissionClass: call.admissionClass,
      taskId: call.input.taskId,
      executionId: call.input.executionId,
      family: call.input.family,
      requestedRoles: call.input.requestedRoles,
      packetIds: call.input.packets.map((packet) => packet.packetId),
      modelInputSha256: call.modelInputSha256,
      outboundRequestSha256: call.outboundRequestSha256
    }))
  };
}
