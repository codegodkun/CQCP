import {
  TRACK_B_FINAL_TRACK,
  TRACK_B_FIFTH_TRACK,
  TRACK_B_HOLDOUT_COUNTS,
  TRACK_B_HOLDOUT_TRACK,
  TRACK_B_SUCCESSOR_TRACK
} from "./track-b-holdout-constants.mjs";
import { parseJsonRejectDuplicateKeys } from "./strict-json.mjs";

export const TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA =
  "task-eval-002-track-b-holdout-model-input-v1";
export const TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA =
  "task-eval-003-track-b-successor-model-input-v1";
export const TRACK_B_FINAL_MODEL_INPUT_SCHEMA =
  "task-eval-004-track-b-final-model-input-v1";
export const TRACK_B_FIFTH_MODEL_INPUT_SCHEMA =
  "task-eval-005-track-b-fifth-model-input-v2";
export const TRACK_B_HOLDOUT_PROVIDER_INPUT_SCHEMA =
  "ROLE_CANDIDATE_ANCHOR_ABSTENTION_INPUT_V1";
export const TRACK_B_HOLDOUT_ABSTENTION_REASONS = Object.freeze([
  "NOT_ENOUGH_EVIDENCE",
  "CONFLICT_UNRESOLVED",
  "ROLE_NOT_COVERED"
]);

const ROOT_FIELDS = new Set(["opinions"]);
const MODEL_INPUT_FIELDS = new Set([
  "schemaVersion",
  "track",
  "sourceCorpusSha256",
  "groundTruthIncluded",
  "packetCount",
  "packets"
]);
const PROVIDER_INPUT_FIELDS = new Set([
  "schemaVersion",
  "track",
  "taskId",
  "executionId",
  "family",
  "requestedRoles",
  "packetCount",
  "packets"
]);
const CORPUS_FIELDS = new Set([
  "schemaVersion",
  "status",
  "generatedAt",
  "source",
  "sourceSignalsPath",
  "sourceSignalsSha256",
  "containsProductionContractText",
  "groundTruthIncluded",
  "packetCount",
  "eligiblePacketCount",
  "mediumEligibleCount",
  "conflictedEligibleCount",
  "zeroCallControlCount",
  "packets"
]);
const PACKET_FIELDS = new Set([
  "schemaVersion",
  "packetId",
  "taskId",
  "executionId",
  "sampleId",
  "ruleSetVersion",
  "family",
  "reviewPointCode",
  "candidateRole",
  "candidateOccurrences",
  "coverageSignals",
  "budget",
  "admission",
  "requiredOutput"
]);
const OCCURRENCE_FIELDS = new Set([
  "occurrenceId",
  "candidateValue",
  "evidenceText",
  "sourceAnchor"
]);
const ANCHOR_FIELDS = new Set([
  "blockId",
  "locationLevel",
  "previewElementRef",
  "sectionPath",
  "regionType",
  "contextType",
  "reliable"
]);
const COVERAGE_FIELDS = new Set([
  "slotKey",
  "required",
  "critical",
  "coverageStatus",
  "diagnosticCode",
  "reliableAnchor"
]);
const BUDGET_FIELDS = new Set([
  "maxEvidenceChars",
  "usedEvidenceChars",
  "complete",
  "truncated"
]);
const ADMISSION_FIELDS = new Set([
  "modelCallAllowed",
  "status",
  "reasonCodes",
  "requiredBlockIds"
]);
const REQUIRED_OUTPUT_FIELDS = new Set([
  "contract",
  "fields",
  "instruction"
]);
const OPINION_FIELDS = new Set([
  "packetId",
  "suggestedRole",
  "selectedOccurrenceIds",
  "selectedAnchorBlockIds",
  "abstain",
  "abstentionReason"
]);
const HOLDOUT_IDENTITIES = Object.freeze({
  MEDIUM: Object.freeze({
    taskId: "task-eval-002-track-b-holdout-medium",
    executionId: "execution-track-b-holdout-medium-v1"
  }),
  CONFLICTED: Object.freeze({
    taskId: "task-eval-002-track-b-holdout-conflicted",
    executionId: "execution-track-b-holdout-conflicted-v1"
  }),
  CONTROL: Object.freeze({
    taskId: "task-eval-002-track-b-holdout-control",
    executionId: "execution-track-b-holdout-control-v1"
  })
});
const HOLDOUT_CONTROL_EXPECTATIONS = new Map([
  [
    "TBH-CTL-HIGH-001",
    {
      reason: "DETERMINISTIC_HIGH_ZERO_CALL",
      occurrenceCount: 1,
      reliableAnchor: true,
      contextType: "NORMAL"
    }
  ],
  [
    "TBH-CTL-INVALID-001",
    {
      reason: "BUNDLE_INVALID",
      occurrenceCount: 1,
      reliableAnchor: false,
      contextType: "NORMAL"
    }
  ],
  [
    "TBH-CTL-NOANCHOR-001",
    {
      reason: "RELIABLE_ANCHOR_MISSING",
      occurrenceCount: 1,
      reliableAnchor: true,
      contextType: "TOC"
    }
  ]
]);

const SUCCESSOR_IDENTITIES = Object.freeze({
  MEDIUM: Object.freeze({
    taskId: "task-eval-003-track-b-successor-medium",
    executionId: "execution-track-b-successor-medium-v1"
  }),
  CONFLICTED: Object.freeze({
    taskId: "task-eval-003-track-b-successor-conflicted",
    executionId: "execution-track-b-successor-conflicted-v1"
  }),
  CONTROL: Object.freeze({
    taskId: "task-eval-003-track-b-successor-control",
    executionId: "execution-track-b-successor-control-v1"
  })
});
const SUCCESSOR_CONTROL_EXPECTATIONS = new Map([
  [
    "TBS-CTL-HIGH-001",
    {
      reason: "DETERMINISTIC_HIGH_ZERO_CALL",
      occurrenceCount: 1,
      reliableAnchor: true,
      contextType: "NORMAL"
    }
  ],
  [
    "TBS-CTL-INVALID-001",
    {
      reason: "BUNDLE_INVALID",
      occurrenceCount: 1,
      reliableAnchor: false,
      contextType: "NORMAL"
    }
  ],
  [
    "TBS-CTL-NOANCHOR-001",
    {
      reason: "RELIABLE_ANCHOR_MISSING",
      occurrenceCount: 1,
      reliableAnchor: true,
      contextType: "TOC"
    }
  ]
]);

const FINAL_IDENTITIES = Object.freeze({
  MEDIUM: Object.freeze({
    taskId: "task-eval-004-track-b-final-medium",
    executionId: "execution-track-b-final-medium-v1"
  }),
  CONFLICTED: Object.freeze({
    taskId: "task-eval-004-track-b-final-conflicted",
    executionId: "execution-track-b-final-conflicted-v1"
  }),
  CONTROL: Object.freeze({
    taskId: "task-eval-004-track-b-final-control",
    executionId: "execution-track-b-final-control-v1"
  })
});
const FINAL_CONTROL_EXPECTATIONS = new Map([
  [
    "TBF-CTL-HIGH-001",
    {
      reason: "DETERMINISTIC_HIGH_ZERO_CALL",
      occurrenceCount: 1,
      reliableAnchor: true,
      contextType: "NORMAL"
    }
  ],
  [
    "TBF-CTL-INVALID-001",
    {
      reason: "BUNDLE_INVALID",
      occurrenceCount: 1,
      reliableAnchor: false,
      contextType: "NORMAL"
    }
  ],
  [
    "TBF-CTL-NOANCHOR-001",
    {
      reason: "RELIABLE_ANCHOR_MISSING",
      occurrenceCount: 1,
      reliableAnchor: true,
      contextType: "TOC"
    }
  ]
]);

const FIFTH_IDENTITIES = Object.freeze({
  MEDIUM: Object.freeze({
    taskId: "task-eval-005-track-b-fifth-medium",
    executionId: "execution-track-b-fifth-medium-v1"
  }),
  CONFLICTED: Object.freeze({
    taskId: "task-eval-005-track-b-fifth-conflicted",
    executionId: "execution-track-b-fifth-conflicted-v1"
  }),
  CONTROL: Object.freeze({
    taskId: "task-eval-005-track-b-fifth-control",
    executionId: "execution-track-b-fifth-control-v1"
  })
});
const FIFTH_CONTROL_EXPECTATIONS = new Map([
  ["TB5-CTL-HIGH-001", {
    reason: "DETERMINISTIC_HIGH_ZERO_CALL",
    occurrenceCount: 1,
    reliableAnchor: true,
    contextType: "NORMAL"
  }],
  ["TB5-CTL-INVALID-001", {
    reason: "BUNDLE_INVALID",
    occurrenceCount: 1,
    reliableAnchor: false,
    contextType: "NORMAL"
  }],
  ["TB5-CTL-NOANCHOR-001", {
    reason: "RELIABLE_ANCHOR_MISSING",
    occurrenceCount: 1,
    reliableAnchor: true,
    contextType: "TOC"
  }]
]);

const HOLDOUT_PROFILE = Object.freeze({
  corpusSchema: "task-eval-002-track-b-holdout-corpus-v1",
  sourceSignalsPath:
    "apps/api-server/src/test/resources/track-b-holdout-v1/" +
    "source-signals.json",
  modelInputSchema: TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
  track: TRACK_B_HOLDOUT_TRACK,
  ruleSetVersion: "v20260802.holdout.1",
  samplePrefix: "TBH",
  identities: HOLDOUT_IDENTITIES,
  controlExpectations: HOLDOUT_CONTROL_EXPECTATIONS
});

const SUCCESSOR_PROFILE = Object.freeze({
  corpusSchema: "task-eval-003-track-b-successor-corpus-v1",
  sourceSignalsPath:
    "apps/api-server/src/test/resources/track-b-successor-v1/" +
    "source-signals.json",
  modelInputSchema: TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA,
  track: TRACK_B_SUCCESSOR_TRACK,
  ruleSetVersion: "v20260802.successor.1",
  samplePrefix: "TBS",
  identities: SUCCESSOR_IDENTITIES,
  controlExpectations: SUCCESSOR_CONTROL_EXPECTATIONS
});

const FINAL_PROFILE = Object.freeze({
  corpusSchema: "task-eval-004-track-b-final-corpus-v1",
  sourceSignalsPath:
    "apps/api-server/src/test/resources/track-b-final-v1/" +
    "source-signals.json",
  modelInputSchema: TRACK_B_FINAL_MODEL_INPUT_SCHEMA,
  track: TRACK_B_FINAL_TRACK,
  ruleSetVersion: "v20260803.final.1",
  samplePrefix: "TBF",
  identities: FINAL_IDENTITIES,
  controlExpectations: FINAL_CONTROL_EXPECTATIONS
});

const FIFTH_PROFILE = Object.freeze({
  corpusSchema: "task-eval-005-track-b-fifth-corpus-v1",
  sourceSignalsPath:
    "apps/api-server/src/test/resources/track-b-fifth-v1/" +
    "source-signals.json",
  modelInputSchema: TRACK_B_FIFTH_MODEL_INPUT_SCHEMA,
  track: TRACK_B_FIFTH_TRACK,
  ruleSetVersion: "v20260803.fifth.1",
  samplePrefix: "TB5",
  identities: FIFTH_IDENTITIES,
  controlExpectations: FIFTH_CONTROL_EXPECTATIONS
});

export function validateTrackBHoldoutCorpusForModel(
  corpus,
  sourceCorpusSha256
) {
  return validateTrackBCorpusForModel(
    corpus,
    sourceCorpusSha256,
    HOLDOUT_PROFILE
  );
}

export function validateTrackBSuccessorCorpusForModel(
  corpus,
  sourceCorpusSha256
) {
  return validateTrackBCorpusForModel(
    corpus,
    sourceCorpusSha256,
    SUCCESSOR_PROFILE
  );
}

export function validateTrackBFinalCorpusForModel(
  corpus,
  sourceCorpusSha256
) {
  return validateTrackBCorpusForModel(
    corpus,
    sourceCorpusSha256,
    FINAL_PROFILE
  );
}

export function validateTrackBFifthCorpusForModel(
  corpus,
  sourceCorpusSha256
) {
  return validateTrackBCorpusForModel(
    corpus,
    sourceCorpusSha256,
    FIFTH_PROFILE
  );
}

function validateTrackBCorpusForModel(
  corpus,
  sourceCorpusSha256,
  profile
) {
  requireObject(corpus, "corpus");
  requireExactKeys(corpus, CORPUS_FIELDS, "corpus");
  if (
    corpus.schemaVersion !== profile.corpusSchema ||
    corpus.status !==
      "DRAFT_PENDING_HUMAN_GROUND_TRUTH_CONFIRMATION" ||
    !isCanonicalIso(corpus.generatedAt) ||
    corpus.source !==
      "INDEPENDENT_DEIDENTIFIED_SYNTHETIC_RUNTIME_SIGNALS" ||
    corpus.sourceSignalsPath !== profile.sourceSignalsPath ||
    !isSha256(corpus.sourceSignalsSha256) ||
    corpus.containsProductionContractText !== false ||
    corpus.groundTruthIncluded !== false ||
    corpus.packetCount !== TRACK_B_HOLDOUT_COUNTS.total ||
    corpus.eligiblePacketCount !== TRACK_B_HOLDOUT_COUNTS.eligible ||
    corpus.mediumEligibleCount !== TRACK_B_HOLDOUT_COUNTS.medium ||
    corpus.conflictedEligibleCount !==
      TRACK_B_HOLDOUT_COUNTS.conflicted ||
    corpus.zeroCallControlCount !==
      TRACK_B_HOLDOUT_COUNTS.zeroCallControls ||
    !Array.isArray(corpus.packets) ||
    corpus.packets.length !== TRACK_B_HOLDOUT_COUNTS.total ||
    !isSha256(sourceCorpusSha256)
  ) {
    throw new Error("TRACK_B_HOLDOUT_CORPUS_IDENTITY_INVALID");
  }
  const packetIds = new Set();
  const sampleIds = new Set();
  for (const packet of corpus.packets) {
    validateCommonPacket(packet);
    if (
      packetIds.has(packet.packetId) ||
      sampleIds.has(packet.sampleId)
    ) {
      throw new Error("TRACK_B_HOLDOUT_PACKET_ID_DUPLICATED");
    }
    packetIds.add(packet.packetId);
    sampleIds.add(packet.sampleId);
  }
  const eligiblePackets = corpus.packets.filter(
    (packet) => packet.admission.modelCallAllowed
  );
  const zeroCallPackets = corpus.packets.filter(
    (packet) => !packet.admission.modelCallAllowed
  );
  const modelInput = validateTrackBModelInput({
    schemaVersion: profile.modelInputSchema,
    track: profile.track,
    sourceCorpusSha256,
    groundTruthIncluded: false,
    packetCount: eligiblePackets.length,
    packets: eligiblePackets
  }, profile);
  if (zeroCallPackets.length !== TRACK_B_HOLDOUT_COUNTS.zeroCallControls) {
    throw new Error("TRACK_B_HOLDOUT_CONTROL_COUNT_INVALID");
  }
  for (const packet of zeroCallPackets) {
    validateZeroCallPacket(packet, profile);
  }
  return { eligiblePackets: modelInput.packets, zeroCallPackets };
}

export function validateTrackBHoldoutModelInput(input) {
  return validateTrackBModelInput(input, HOLDOUT_PROFILE);
}

export function validateTrackBSuccessorModelInput(input) {
  return validateTrackBModelInput(input, SUCCESSOR_PROFILE);
}

export function validateTrackBFinalModelInput(input) {
  return validateTrackBModelInput(input, FINAL_PROFILE);
}

export function validateTrackBFifthModelInput(input) {
  return validateTrackBModelInput(input, FIFTH_PROFILE);
}

function validateTrackBModelInput(input, profile) {
  requireObject(input, "input");
  requireExactKeys(input, MODEL_INPUT_FIELDS, "input");
  if (
    input.schemaVersion !== profile.modelInputSchema ||
    input.track !== profile.track ||
    !isSha256(input.sourceCorpusSha256) ||
    input.groundTruthIncluded !== false ||
    input.packetCount !== TRACK_B_HOLDOUT_COUNTS.eligible ||
    !Array.isArray(input.packets) ||
    input.packets.length !== TRACK_B_HOLDOUT_COUNTS.eligible
  ) {
    throw new Error("TRACK_B_HOLDOUT_MODEL_INPUT_IDENTITY_INVALID");
  }
  rejectForbiddenContext(input, true);
  const medium = [];
  const conflicted = [];
  for (const packet of input.packets) {
    validateEligiblePacket(packet, true, profile);
    if (packet.sampleId.startsWith(`${profile.samplePrefix}-MED-`)) {
      medium.push(packet);
    } else if (
      packet.sampleId.startsWith(`${profile.samplePrefix}-CON-`)
    ) {
      conflicted.push(packet);
    } else {
      throw new Error("TRACK_B_HOLDOUT_ELIGIBLE_IDENTITY_INVALID");
    }
  }
  if (
    medium.length !== TRACK_B_HOLDOUT_COUNTS.medium ||
    conflicted.length !== TRACK_B_HOLDOUT_COUNTS.conflicted
  ) {
    throw new Error("TRACK_B_HOLDOUT_ELIGIBLE_COUNT_INVALID");
  }
  return input;
}

export function validateTrackBHoldoutProviderInput(input) {
  return validateTrackBProviderInput(input, HOLDOUT_PROFILE);
}

export function validateTrackBSuccessorProviderInput(input) {
  return validateTrackBProviderInput(input, SUCCESSOR_PROFILE);
}

export function validateTrackBFinalProviderInput(input) {
  return validateTrackBProviderInput(input, FINAL_PROFILE);
}

export function validateTrackBFifthProviderInput(input) {
  return validateTrackBProviderInput(input, FIFTH_PROFILE);
}

function validateTrackBProviderInput(input, profile) {
  requireObject(input, "providerInput");
  requireExactKeys(input, PROVIDER_INPUT_FIELDS, "providerInput");
  if (
    input.schemaVersion !== TRACK_B_HOLDOUT_PROVIDER_INPUT_SCHEMA ||
    input.track !== profile.track ||
    typeof input.taskId !== "string" ||
    !input.taskId ||
    typeof input.executionId !== "string" ||
    !input.executionId ||
    typeof input.family !== "string" ||
    !input.family ||
    !Array.isArray(input.requestedRoles) ||
    input.requestedRoles.length === 0 ||
    new Set(input.requestedRoles).size !== input.requestedRoles.length ||
    input.requestedRoles.some((role) => typeof role !== "string" || !role) ||
    !Array.isArray(input.packets) ||
    input.packets.length === 0 ||
    input.packetCount !== 1 ||
    input.packets.length !== 1
  ) {
    throw new Error("TRACK_B_HOLDOUT_PROVIDER_INPUT_IDENTITY_INVALID");
  }
  rejectForbiddenContext(input, false);
  for (const packet of input.packets) {
    validateEligiblePacket(packet, true, profile);
  }
  if (
    input.packets.some(
      (packet) =>
        packet.taskId !== input.taskId ||
        packet.executionId !== input.executionId ||
        packet.family !== input.family
    ) ||
    input.requestedRoles.length !== 1 ||
    input.requestedRoles[0] !== input.packets[0].candidateRole
  ) {
    throw new Error("TRACK_B_HOLDOUT_PROVIDER_INPUT_GROUP_INVALID");
  }
  return input;
}

export function validateTrackBHoldoutOpinionPayload(input, payload) {
  return validateTrackBOpinionPayload(input, payload, HOLDOUT_PROFILE);
}

export function validateTrackBSuccessorOpinionPayload(input, payload) {
  return validateTrackBOpinionPayload(input, payload, SUCCESSOR_PROFILE);
}

export function validateTrackBFinalOpinionPayload(input, payload) {
  return validateTrackBOpinionPayload(input, payload, FINAL_PROFILE);
}

export function validateTrackBFifthOpinionPayload(input, payload) {
  return validateTrackBOpinionPayload(input, payload, FIFTH_PROFILE);
}

function validateTrackBOpinionPayload(input, payload, profile) {
  const validatedInput =
    input?.schemaVersion === TRACK_B_HOLDOUT_PROVIDER_INPUT_SCHEMA
      ? validateTrackBProviderInput(input, profile)
      : validateTrackBModelInput(input, profile);
  requireObject(payload, "payload");
  requireExactKeys(payload, ROOT_FIELDS, "payload");
  if (
    !Array.isArray(payload.opinions) ||
    payload.opinions.length !== validatedInput.packets.length
  ) {
    throw new Error("TRACK_B_HOLDOUT_OPINION_COVERAGE_INVALID");
  }
  const packetById = new Map(
    validatedInput.packets.map((packet) => [packet.packetId, packet])
  );
  const seen = new Set();
  for (const [index, opinion] of payload.opinions.entries()) {
    const path = `opinions[${index}]`;
    requireObject(opinion, path);
    requireExactKeys(opinion, OPINION_FIELDS, path);
    const packet = packetById.get(opinion.packetId);
    if (!packet || seen.has(opinion.packetId)) {
      throw new Error(`${path}.packetId is unknown or duplicated`);
    }
    seen.add(opinion.packetId);
    validateOpinion(opinion, packet, path);
  }
  return payload;
}

export function validateTrackBHoldoutDeepSeekEnvelope(input, envelope) {
  return validateTrackBDeepSeekEnvelope(input, envelope, HOLDOUT_PROFILE);
}

export function validateTrackBSuccessorDeepSeekEnvelope(input, envelope) {
  return validateTrackBDeepSeekEnvelope(input, envelope, SUCCESSOR_PROFILE);
}

export function validateTrackBFinalDeepSeekEnvelope(input, envelope) {
  return validateTrackBDeepSeekEnvelope(input, envelope, FINAL_PROFILE);
}

export function validateTrackBFifthDeepSeekEnvelope(input, envelope) {
  return validateTrackBDeepSeekEnvelope(input, envelope, FIFTH_PROFILE);
}

function validateTrackBDeepSeekEnvelope(input, envelope, profile) {
  const choice = envelope?.choices?.[0];
  if (envelope?.choices?.length !== 1) {
    throw opinionAdmissionError("CHOICE_COUNT_INVALID");
  }
  if (choice?.finish_reason !== "stop") {
    throw opinionAdmissionError("FINISH_REASON_NOT_STOP");
  }
  if (
    typeof choice?.message?.content !== "string" ||
    !choice.message.content.trim()
  ) {
    throw opinionAdmissionError("CONTENT_EMPTY");
  }
  let payload;
  try {
    payload = parseJsonRejectDuplicateKeys(choice.message.content);
  } catch {
    throw opinionAdmissionError("CONTENT_NOT_JSON");
  }
  try {
    return validateTrackBOpinionPayload(input, payload, profile);
  } catch {
    throw opinionAdmissionError("OPINION_SCHEMA_INVALID");
  }
}

function validateEligiblePacket(packet, exactIdentity, profile) {
  validateCommonPacket(packet);
  const medium = packet.sampleId.startsWith(
    `${profile.samplePrefix}-MED-`
  );
  const conflicted = packet.sampleId.startsWith(
    `${profile.samplePrefix}-CON-`
  );
  const identity = medium
    ? profile.identities.MEDIUM
    : conflicted
      ? profile.identities.CONFLICTED
      : null;
  const reason = medium
    ? "ELIGIBLE_MEDIUM_AMBIGUITY"
    : "ELIGIBLE_CONFLICT_LOCAL_CONTEXT";
  if (
    !identity ||
    packet.admission.modelCallAllowed !== true ||
    packet.admission.status !== "ELIGIBLE" ||
    JSON.stringify(packet.admission.reasonCodes) !==
      JSON.stringify([reason]) ||
    packet.admission.requiredBlockIds.length === 0 ||
    packet.coverageSignals.some(
      (signal) =>
        signal.required !== true ||
        signal.critical !== true ||
        signal.reliableAnchor !== true ||
        !["LOW_CONFIDENCE", "AMBIGUOUS"].includes(
          signal.coverageStatus
        ) ||
        typeof signal.diagnosticCode !== "string" ||
        !signal.diagnosticCode
    ) ||
    packet.budget.complete !== true ||
    packet.budget.truncated !== false ||
    packet.budget.usedEvidenceChars > packet.budget.maxEvidenceChars ||
    packet.candidateOccurrences.some(
      (candidate) => candidate.sourceAnchor.reliable !== true
    )
  ) {
    throw new Error("TRACK_B_HOLDOUT_ELIGIBLE_PACKET_INVALID");
  }
  if (
    exactIdentity &&
    (packet.taskId !== identity.taskId ||
      packet.executionId !== identity.executionId ||
      packet.ruleSetVersion !== profile.ruleSetVersion ||
      !new RegExp(`^${profile.samplePrefix}-(MED|CON)-\\d{3}$`).test(
        packet.sampleId
      ))
  ) {
    throw new Error("TRACK_B_HOLDOUT_ELIGIBLE_IDENTITY_INVALID");
  }
  const requiredBlockIds = recomputeRequiredBlocks(packet, profile);
  if (
    JSON.stringify(packet.admission.requiredBlockIds) !==
    JSON.stringify(requiredBlockIds)
  ) {
    throw new Error("TRACK_B_HOLDOUT_REQUIRED_BLOCKS_INVALID");
  }
}

function validateZeroCallPacket(packet, profile) {
  const expected = profile.controlExpectations.get(packet.sampleId);
  const first = packet.candidateOccurrences[0];
  if (
    !expected ||
    packet.taskId !== profile.identities.CONTROL.taskId ||
    packet.executionId !== profile.identities.CONTROL.executionId ||
    packet.ruleSetVersion !== profile.ruleSetVersion ||
    packet.admission.modelCallAllowed !== false ||
    packet.admission.status !== "ZERO_CALL_REQUIRED" ||
    JSON.stringify(packet.admission.reasonCodes) !==
      JSON.stringify([expected.reason]) ||
    packet.admission.requiredBlockIds.length !== 0 ||
    packet.candidateOccurrences.length !== expected.occurrenceCount ||
    first?.sourceAnchor?.reliable !== expected.reliableAnchor ||
    first?.sourceAnchor?.contextType !== expected.contextType ||
    packet.budget.complete !== true ||
    packet.budget.truncated !== false
  ) {
    throw new Error("TRACK_B_HOLDOUT_ZERO_CALL_PACKET_INVALID");
  }
}

function validateCommonPacket(packet) {
  requireObject(packet, "packet");
  requireExactKeys(packet, PACKET_FIELDS, "packet");
  if (
    packet.schemaVersion !== "task036-runtime-evidence-packet-v1" ||
    !/^EP-[a-f0-9]{64}$/.test(packet.packetId) ||
    typeof packet.taskId !== "string" ||
    !packet.taskId ||
    typeof packet.executionId !== "string" ||
    !packet.executionId ||
    typeof packet.sampleId !== "string" ||
    !packet.sampleId ||
    typeof packet.ruleSetVersion !== "string" ||
    !packet.ruleSetVersion ||
    typeof packet.family !== "string" ||
    !packet.family ||
    typeof packet.reviewPointCode !== "string" ||
    !packet.reviewPointCode ||
    typeof packet.candidateRole !== "string" ||
    !packet.candidateRole ||
    !Array.isArray(packet.candidateOccurrences) ||
    packet.candidateOccurrences.length === 0
  ) {
    throw new Error("TRACK_B_HOLDOUT_PACKET_SHAPE_INVALID");
  }
  const occurrenceIds = new Set();
  for (const [index, occurrence] of packet.candidateOccurrences.entries()) {
    const path = `packet.candidateOccurrences[${index}]`;
    requireObject(occurrence, path);
    requireExactKeys(occurrence, OCCURRENCE_FIELDS, path);
    assertNonEmptyString(occurrence.occurrenceId, `${path}.occurrenceId`);
    assertNonEmptyString(occurrence.candidateValue, `${path}.candidateValue`);
    assertNonEmptyString(occurrence.evidenceText, `${path}.evidenceText`);
    if (occurrenceIds.has(occurrence.occurrenceId)) {
      throw new Error(`${path}.occurrenceId duplicated`);
    }
    occurrenceIds.add(occurrence.occurrenceId);
    validateAnchor(occurrence.sourceAnchor, `${path}.sourceAnchor`);
  }
  if (!Array.isArray(packet.coverageSignals) || packet.coverageSignals.length === 0) {
    throw new Error("TRACK_B_HOLDOUT_COVERAGE_INVALID");
  }
  for (const [index, signal] of packet.coverageSignals.entries()) {
    const path = `packet.coverageSignals[${index}]`;
    requireObject(signal, path);
    requireExactKeys(signal, COVERAGE_FIELDS, path);
    assertNonEmptyString(signal.slotKey, `${path}.slotKey`);
    if (
      typeof signal.required !== "boolean" ||
      typeof signal.critical !== "boolean" ||
      typeof signal.coverageStatus !== "string" ||
      (signal.diagnosticCode !== null &&
        typeof signal.diagnosticCode !== "string") ||
      typeof signal.reliableAnchor !== "boolean"
    ) {
      throw new Error(`${path} invalid`);
    }
  }
  requireObject(packet.budget, "packet.budget");
  requireExactKeys(packet.budget, BUDGET_FIELDS, "packet.budget");
  if (
    !Number.isInteger(packet.budget.maxEvidenceChars) ||
    packet.budget.maxEvidenceChars <= 0 ||
    !Number.isInteger(packet.budget.usedEvidenceChars) ||
    packet.budget.usedEvidenceChars < 0 ||
    typeof packet.budget.complete !== "boolean" ||
    typeof packet.budget.truncated !== "boolean" ||
    packet.budget.complete === packet.budget.truncated
  ) {
    throw new Error("TRACK_B_HOLDOUT_BUDGET_INVALID");
  }
  requireObject(packet.admission, "packet.admission");
  requireExactKeys(packet.admission, ADMISSION_FIELDS, "packet.admission");
  if (
    typeof packet.admission.modelCallAllowed !== "boolean" ||
    typeof packet.admission.status !== "string" ||
    !Array.isArray(packet.admission.reasonCodes) ||
    packet.admission.reasonCodes.length !== 1 ||
    typeof packet.admission.reasonCodes[0] !== "string" ||
    !Array.isArray(packet.admission.requiredBlockIds) ||
    packet.admission.requiredBlockIds.some(
      (blockId) => typeof blockId !== "string" || !blockId
    ) ||
    new Set(packet.admission.requiredBlockIds).size !==
      packet.admission.requiredBlockIds.length
  ) {
    throw new Error("TRACK_B_HOLDOUT_ADMISSION_INVALID");
  }
  requireObject(packet.requiredOutput, "packet.requiredOutput");
  requireExactKeys(
    packet.requiredOutput,
    REQUIRED_OUTPUT_FIELDS,
    "packet.requiredOutput"
  );
  if (
    packet.requiredOutput.contract !==
      "ROLE_CANDIDATE_ANCHOR_ABSTENTION_V1" ||
    JSON.stringify(packet.requiredOutput.fields) !==
      JSON.stringify([
        "suggestedRole",
        "selectedOccurrenceIds",
        "selectedAnchorBlockIds",
        "abstain",
        "abstentionReason"
      ]) ||
    packet.requiredOutput.instruction !==
      "Only assess supplied role/candidate/anchor evidence. " +
        "Do not produce final business adjudication."
  ) {
    throw new Error("TRACK_B_HOLDOUT_REQUIRED_OUTPUT_INVALID");
  }
}

function validateAnchor(anchor, path) {
  requireObject(anchor, path);
  requireExactKeys(anchor, ANCHOR_FIELDS, path);
  assertNonEmptyString(anchor.blockId, `${path}.blockId`);
  if (
    !["BLOCK_LEVEL", "TABLE_CELL"].includes(anchor.locationLevel) ||
    (anchor.previewElementRef !== null &&
      typeof anchor.previewElementRef !== "string") ||
    !Array.isArray(anchor.sectionPath) ||
    anchor.sectionPath.some((part) => typeof part !== "string") ||
    !["BODY", "APPENDIX"].includes(anchor.regionType) ||
    !["NORMAL", "TOC"].includes(anchor.contextType) ||
    typeof anchor.reliable !== "boolean"
  ) {
    throw new Error(`${path} invalid`);
  }
  const structurallyReliable =
    anchor.locationLevel === "TABLE_CELL"
      ? /^table:[^/]+\/row:[0-9]+\/cell:[0-9]+$/.test(
          anchor.previewElementRef ?? ""
        )
      : anchor.previewElementRef === null ||
        anchor.previewElementRef === "" ||
        anchor.previewElementRef === `block:${anchor.blockId}`;
  if (anchor.reliable !== structurallyReliable) {
    throw new Error(`${path}.reliable mismatches identity`);
  }
}

function validateOpinion(opinion, packet, path) {
  if (typeof opinion.abstain !== "boolean") {
    throw new Error(`${path}.abstain invalid`);
  }
  requireStringArray(
    opinion.selectedOccurrenceIds,
    `${path}.selectedOccurrenceIds`
  );
  requireStringArray(
    opinion.selectedAnchorBlockIds,
    `${path}.selectedAnchorBlockIds`
  );
  const occurrenceById = new Map(
    packet.candidateOccurrences.map((candidate) => [
      candidate.occurrenceId,
      candidate
    ])
  );
  if (
    new Set(opinion.selectedOccurrenceIds).size !==
      opinion.selectedOccurrenceIds.length ||
    opinion.selectedOccurrenceIds.some(
      (occurrenceId) => !occurrenceById.has(occurrenceId)
    )
  ) {
    throw new Error(`${path}.selectedOccurrenceIds invalid`);
  }
  const selected = opinion.selectedOccurrenceIds.map((occurrenceId) =>
    occurrenceById.get(occurrenceId)
  );
  const expectedAnchors = [
    ...new Set(
      selected.map((candidate) => candidate.sourceAnchor.blockId)
    )
  ];
  if (
    JSON.stringify(opinion.selectedAnchorBlockIds) !==
      JSON.stringify(expectedAnchors) ||
    expectedAnchors.some(
      (blockId) => !packet.admission.requiredBlockIds.includes(blockId)
    )
  ) {
    throw new Error(`${path}.selectedAnchorBlockIds invalid`);
  }
  if (opinion.abstain) {
    if (
      opinion.suggestedRole !== null ||
      opinion.selectedOccurrenceIds.length !== 0 ||
      opinion.selectedAnchorBlockIds.length !== 0 ||
      !TRACK_B_HOLDOUT_ABSTENTION_REASONS.includes(
        opinion.abstentionReason
      )
    ) {
      throw new Error(`${path} invalid abstention`);
    }
  } else if (
    opinion.suggestedRole !== packet.candidateRole ||
    opinion.selectedOccurrenceIds.length === 0 ||
    opinion.selectedAnchorBlockIds.length === 0 ||
    opinion.abstentionReason !== null
  ) {
    throw new Error(`${path} invalid positive selection`);
  }
}

function recomputeRequiredBlocks(packet, profile) {
  const distinctValues = new Set(
    packet.candidateOccurrences.map((candidate) => candidate.candidateValue)
  ).size;
  const medium = packet.sampleId.startsWith(
    `${profile.samplePrefix}-MED-`
  );
  const candidates = packet.candidateOccurrences.filter((candidate) => {
    const anchor = candidate.sourceAnchor;
    const allowedContext = !["TOC", "DELETED", "VOIDED", "HEADER_FOOTER"].includes(
      anchor.contextType
    );
    if (!anchor.reliable || !allowedContext) return false;
    if (medium) return true;
    const local =
      anchor.locationLevel === "TABLE_CELL" ||
      candidate.evidenceText.includes(candidate.candidateValue);
    return local && distinctValues > 1;
  });
  return [
    ...new Set(candidates.map((candidate) => candidate.sourceAnchor.blockId))
  ];
}

function rejectForbiddenContext(value, allowGroundTruthFlag) {
  let serialized = JSON.stringify(value);
  if (allowGroundTruthFlag) {
    serialized = serialized.replace('"groundTruthIncluded":false', "");
  }
  if (
    /(ground.?truth|human.?decision|proposed.?expected|cqcp.?actual|cqcp.?expected|finding|verdict)/i.test(
      serialized
    )
  ) {
    throw new Error("TRACK_B_HOLDOUT_FORBIDDEN_CONTEXT");
  }
}

function opinionAdmissionError(code) {
  const error = new Error("Track B holdout completion was not admitted");
  error.admissionCode = code;
  return error;
}

function requireObject(value, path) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${path} must be an object`);
  }
}

function requireExactKeys(value, allowed, path) {
  const keys = Object.keys(value);
  if (keys.length !== allowed.size || keys.some((key) => !allowed.has(key))) {
    throw new Error(`${path} fields invalid`);
  }
}

function requireStringArray(value, path) {
  if (!Array.isArray(value) || value.some((item) => typeof item !== "string")) {
    throw new Error(`${path} must be a string array`);
  }
}

function assertNonEmptyString(value, path) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${path} must be a non-empty string`);
  }
}

const isSha256 = (value) =>
  typeof value === "string" && /^[a-f0-9]{64}$/.test(value);

const isCanonicalIso = (value) =>
  typeof value === "string" &&
  !Number.isNaN(Date.parse(value)) &&
  new Date(value).toISOString() === value;
