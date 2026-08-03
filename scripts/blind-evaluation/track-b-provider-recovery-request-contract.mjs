import { createHash } from "node:crypto";

import {
  projectRuntimeEvidencePacketForModel
} from "./track-b-provider-recovery-projection.mjs";

export const TRACK_B_PROVIDER_RECOVERY_MODEL = "deepseek-v4-pro";
export const TRACK_B_PROVIDER_RECOVERY_MAX_OUTPUT_TOKENS = 1500;
export const TRACK_B_PROVIDER_RECOVERY_REQUEST_BUILDER_VERSION =
  "track-b-provider-recovery-request-builder-v3";
export const TRACK_B_PROVIDER_RECOVERY_PROMPT_SHA256 =
  "5cb29a5ddc9644e15afba8afc67079d805ff41ae4472cf93997a1d89ac302601";

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

export function buildTrackBProviderRecoveryCall(runtimePacket, promptBytes) {
  if (!Buffer.isBuffer(promptBytes)) {
    throw new Error("TRACK_B_RECOVERY_PROMPT_BYTES_REQUIRED");
  }
  if (sha256(promptBytes) !== TRACK_B_PROVIDER_RECOVERY_PROMPT_SHA256) {
    throw new Error("TRACK_B_RECOVERY_PROMPT_HASH_MISMATCH");
  }

  const input = projectRuntimeEvidencePacketForModel(runtimePacket);
  const modelInputBytes = Buffer.from(JSON.stringify(input), "utf8");
  const outboundRequest = {
    model: TRACK_B_PROVIDER_RECOVERY_MODEL,
    thinking: { type: "disabled" },
    messages: [
      { role: "system", content: promptBytes.toString("utf8") },
      { role: "user", content: modelInputBytes.toString("utf8") }
    ],
    response_format: { type: "json_object" },
    stream: false,
    max_tokens: TRACK_B_PROVIDER_RECOVERY_MAX_OUTPUT_TOKENS
  };
  const outboundRequestBytes = Buffer.from(
    JSON.stringify(outboundRequest),
    "utf8"
  );
  const modelInputSha256 = sha256(modelInputBytes);

  return {
    callId: `TBR-MC-${modelInputSha256}`,
    input,
    modelInputBytes,
    modelInputSha256,
    outboundRequest,
    outboundRequestBytes,
    outboundRequestSha256: sha256(outboundRequestBytes)
  };
}
