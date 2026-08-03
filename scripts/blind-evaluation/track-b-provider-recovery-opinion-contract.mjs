import { parseJsonRejectDuplicateKeys } from "./strict-json.mjs";
import { validateModelFacingEvidencePacket } from
  "./track-b-provider-recovery-projection.mjs";

export const TRACK_B_PROVIDER_RECOVERY_OPINION_SCHEMA =
  "track-b-role-candidate-anchor-abstention-v3";

const ROOT_FIELDS = ["opinions"];
const OPINION_FIELDS = [
  "packetId",
  "suggestedRole",
  "selectedOccurrenceIds",
  "selectedAnchorBlockIds",
  "abstain",
  "abstentionReason"
];
const ABSTENTION_REASONS = new Set([
  "NOT_ENOUGH_EVIDENCE",
  "CONFLICT_UNRESOLVED",
  "ROLE_NOT_COVERED"
]);

export function validateTrackBProviderRecoveryOpinionPayload(input, payload) {
  validateModelFacingEvidencePacket(input);
  requireExactKeys(payload, ROOT_FIELDS, "payload");
  if (!Array.isArray(payload.opinions) || payload.opinions.length !== 1) {
    throw new Error("TRACK_B_RECOVERY_OPINION_COVERAGE_INVALID");
  }
  const opinion = payload.opinions[0];
  requireExactKeys(opinion, OPINION_FIELDS, "opinion");
  if (opinion.packetId !== input.packetId) {
    throw new Error("TRACK_B_RECOVERY_PACKET_ID_INVALID");
  }
  if (
    !Array.isArray(opinion.selectedOccurrenceIds) ||
    !Array.isArray(opinion.selectedAnchorBlockIds) ||
    opinion.selectedOccurrenceIds.some((value) => typeof value !== "string") ||
    opinion.selectedAnchorBlockIds.some((value) => typeof value !== "string")
  ) {
    throw new Error("TRACK_B_RECOVERY_SELECTION_TYPE_INVALID");
  }

  const occurrenceById = new Map(
    input.candidateOccurrences.map((item) => [item.occurrenceId, item])
  );
  if (
    new Set(opinion.selectedOccurrenceIds).size !==
      opinion.selectedOccurrenceIds.length ||
    opinion.selectedOccurrenceIds.some((id) => !occurrenceById.has(id))
  ) {
    throw new Error("TRACK_B_RECOVERY_OCCURRENCE_SELECTION_INVALID");
  }
  const expectedAnchors = [
    ...new Set(
      opinion.selectedOccurrenceIds.map(
        (id) => occurrenceById.get(id).anchor.blockId
      )
    )
  ];
  if (
    JSON.stringify(opinion.selectedAnchorBlockIds) !==
    JSON.stringify(expectedAnchors)
  ) {
    throw new Error("TRACK_B_RECOVERY_ANCHOR_SELECTION_INVALID");
  }

  if (opinion.abstain === true) {
    if (
      opinion.suggestedRole !== null ||
      opinion.selectedOccurrenceIds.length !== 0 ||
      opinion.selectedAnchorBlockIds.length !== 0 ||
      !ABSTENTION_REASONS.has(opinion.abstentionReason)
    ) {
      throw new Error("TRACK_B_RECOVERY_ABSTENTION_INVALID");
    }
  } else if (
    opinion.abstain !== false ||
    opinion.suggestedRole !== input.requestedRole ||
    opinion.selectedOccurrenceIds.length === 0 ||
    opinion.selectedAnchorBlockIds.length === 0 ||
    opinion.abstentionReason !== null
  ) {
    throw new Error("TRACK_B_RECOVERY_POSITIVE_SELECTION_INVALID");
  }
  return payload;
}

export function validateTrackBProviderRecoveryDeepSeekEnvelope(input, envelope) {
  if (!Array.isArray(envelope?.choices) || envelope.choices.length !== 1) {
    throw new Error("TRACK_B_RECOVERY_CHOICE_COUNT_INVALID");
  }
  const choice = envelope.choices[0];
  if (choice.finish_reason !== "stop") {
    throw new Error("TRACK_B_RECOVERY_FINISH_REASON_NOT_STOP");
  }
  if (
    typeof choice.message?.content !== "string" ||
    !choice.message.content.trim()
  ) {
    throw new Error("TRACK_B_RECOVERY_CONTENT_EMPTY");
  }
  let payload;
  try {
    payload = parseJsonRejectDuplicateKeys(choice.message.content);
  } catch {
    throw new Error("TRACK_B_RECOVERY_CONTENT_NOT_STRICT_JSON");
  }
  return validateTrackBProviderRecoveryOpinionPayload(input, payload);
}

function requireExactKeys(value, expected, path) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${path} must be an object`);
  }
  const actual = Object.keys(value).sort();
  const required = [...expected].sort();
  if (JSON.stringify(actual) !== JSON.stringify(required)) {
    throw new Error(`${path} fields invalid`);
  }
}
