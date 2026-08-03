export const TRACK_B_PROVIDER_RECOVERY_PACKET_SCHEMA =
  "task-eval-006-model-facing-evidence-packet-v3";
export const TRACK_B_PROVIDER_RECOVERY_OUTPUT_CONTRACT =
  "ROLE_CANDIDATE_ANCHOR_ABSTENTION_V3";

const PACKET_FIELDS = [
  "schemaVersion",
  "packetId",
  "family",
  "reviewPointCode",
  "requestedRole",
  "candidateOccurrences",
  "budget",
  "requiredOutput"
];
const OCCURRENCE_FIELDS = ["occurrenceId", "value", "evidence", "anchor"];
const ANCHOR_FIELDS = [
  "blockId",
  "locationLevel",
  "previewElementRef",
  "sectionPath",
  "regionType",
  "contextType",
  "reliable"
];
const BUDGET_FIELDS = [
  "maxEvidenceChars",
  "usedEvidenceChars",
  "complete",
  "truncated"
];
const REQUIRED_OUTPUT_FIELDS = ["contract", "fields", "instruction"];
const OUTPUT_FIELDS = [
  "suggestedRole",
  "selectedOccurrenceIds",
  "selectedAnchorBlockIds",
  "abstain",
  "abstentionReason"
];

export function projectRuntimeEvidencePacketForModel(runtimePacket) {
  if (!runtimePacket || typeof runtimePacket !== "object") {
    throw new Error("TRACK_B_RECOVERY_RUNTIME_PACKET_REQUIRED");
  }
  if (
    runtimePacket.admission?.modelCallAllowed !== true ||
    runtimePacket.admission?.status !== "ELIGIBLE"
  ) {
    throw new Error("TRACK_B_RECOVERY_PACKET_NOT_ELIGIBLE");
  }
  if (
    !Array.isArray(runtimePacket.candidateOccurrences) ||
    runtimePacket.candidateOccurrences.length === 0
  ) {
    throw new Error("TRACK_B_RECOVERY_CANDIDATES_REQUIRED");
  }
  if (
    runtimePacket.candidateOccurrences.some(
      (occurrence) => occurrence.sourceAnchor?.reliable !== true
    )
  ) {
    throw new Error("TRACK_B_RECOVERY_ANCHOR_UNRELIABLE");
  }
  if (
    runtimePacket.budget?.complete !== true ||
    runtimePacket.budget?.truncated !== false
  ) {
    throw new Error("TRACK_B_RECOVERY_BUDGET_INCOMPLETE");
  }

  return validateModelFacingEvidencePacket({
    schemaVersion: TRACK_B_PROVIDER_RECOVERY_PACKET_SCHEMA,
    packetId: runtimePacket.packetId,
    family: runtimePacket.family,
    reviewPointCode: runtimePacket.reviewPointCode,
    requestedRole: runtimePacket.candidateRole,
    candidateOccurrences: runtimePacket.candidateOccurrences.map(
      (occurrence) => ({
        occurrenceId: occurrence.occurrenceId,
        value: occurrence.candidateValue,
        evidence: occurrence.evidenceText,
        anchor: structuredClone(occurrence.sourceAnchor)
      })
    ),
    budget: structuredClone(runtimePacket.budget),
    requiredOutput: {
      contract: TRACK_B_PROVIDER_RECOVERY_OUTPUT_CONTRACT,
      fields: [...OUTPUT_FIELDS],
      instruction:
        "Only assess supplied role/candidate/anchor evidence. Do not produce final business adjudication."
    }
  });
}

export function validateModelFacingEvidencePacket(input) {
  requireExactKeys(input, PACKET_FIELDS);
  if (
    input.schemaVersion !== TRACK_B_PROVIDER_RECOVERY_PACKET_SCHEMA ||
    !isNonEmptyString(input.packetId) ||
    !isNonEmptyString(input.family) ||
    !isNonEmptyString(input.reviewPointCode) ||
    !isNonEmptyString(input.requestedRole) ||
    !Array.isArray(input.candidateOccurrences) ||
    input.candidateOccurrences.length === 0
  ) {
    throw new Error("TRACK_B_RECOVERY_MODEL_INPUT_IDENTITY_INVALID");
  }
  const occurrenceIds = new Set();
  for (const occurrence of input.candidateOccurrences) {
    requireExactKeys(occurrence, OCCURRENCE_FIELDS);
    requireExactKeys(occurrence.anchor, ANCHOR_FIELDS);
    if (
      !isNonEmptyString(occurrence.occurrenceId) ||
      occurrenceIds.has(occurrence.occurrenceId) ||
      !isNonEmptyString(occurrence.value) ||
      !isNonEmptyString(occurrence.evidence) ||
      !isNonEmptyString(occurrence.anchor.blockId) ||
      !isNonEmptyString(occurrence.anchor.locationLevel) ||
      (occurrence.anchor.previewElementRef !== null &&
        !isNonEmptyString(occurrence.anchor.previewElementRef)) ||
      !Array.isArray(occurrence.anchor.sectionPath) ||
      occurrence.anchor.sectionPath.some((item) => !isNonEmptyString(item)) ||
      !isNonEmptyString(occurrence.anchor.regionType) ||
      !isNonEmptyString(occurrence.anchor.contextType) ||
      occurrence.anchor.reliable !== true
    ) {
      throw new Error("TRACK_B_RECOVERY_MODEL_INPUT_OCCURRENCE_INVALID");
    }
    occurrenceIds.add(occurrence.occurrenceId);
  }

  requireExactKeys(input.budget, BUDGET_FIELDS);
  if (
    !Number.isInteger(input.budget.maxEvidenceChars) ||
    !Number.isInteger(input.budget.usedEvidenceChars) ||
    input.budget.maxEvidenceChars < input.budget.usedEvidenceChars ||
    input.budget.usedEvidenceChars < 0 ||
    input.budget.complete !== true ||
    input.budget.truncated !== false
  ) {
    throw new Error("TRACK_B_RECOVERY_MODEL_INPUT_BUDGET_INVALID");
  }

  requireExactKeys(input.requiredOutput, REQUIRED_OUTPUT_FIELDS);
  if (
    input.requiredOutput.contract !==
      TRACK_B_PROVIDER_RECOVERY_OUTPUT_CONTRACT ||
    JSON.stringify(input.requiredOutput.fields) !==
      JSON.stringify(OUTPUT_FIELDS) ||
    !isNonEmptyString(input.requiredOutput.instruction)
  ) {
    throw new Error("TRACK_B_RECOVERY_MODEL_INPUT_OUTPUT_INVALID");
  }
  return input;
}

function requireExactKeys(value, expected) {
  if (
    !value ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    JSON.stringify(Object.keys(value)) !== JSON.stringify(expected)
  ) {
    throw new Error("TRACK_B_RECOVERY_MODEL_INPUT_FIELDS_INVALID");
  }
}

function isNonEmptyString(value) {
  return typeof value === "string" && value.length > 0;
}
