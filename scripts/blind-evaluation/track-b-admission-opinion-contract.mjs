import {
  parseJsonRejectDuplicateKeys,
} from "./strict-json.mjs";

const ROOT_FIELDS = new Set(["opinions"]);
const MODEL_INPUT_FIELDS = new Set([
  "schemaVersion",
  "track",
  "sourceCorpusSha256",
  "groundTruthIncluded",
  "packetCount",
  "packets"
]);
const PROVIDER_MODEL_INPUT_FIELDS = new Set([
  "schemaVersion",
  "track",
  "taskId",
  "executionId",
  "family",
  "requestedRoles",
  "packetCount",
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
  "reasonCodes"
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
export const TRACK_B_ABSTENTION_REASONS = Object.freeze([
  "NOT_ENOUGH_EVIDENCE",
  "CONFLICT_UNRESOLVED",
  "ROLE_NOT_COVERED"
]);
const CORPUS_FIELDS = new Set([
  "schemaVersion",
  "status",
  "generatedAt",
  "source",
  "containsProductionContractText",
  "groundTruthIncluded",
  "packetCount",
  "eligiblePacketCount",
  "mediumEligibleCount",
  "conflictedEligibleCount",
  "zeroCallControlCount",
  "packets"
]);
const ZERO_CALL_EXPECTATIONS = new Map([
  [
    "TB-CTL-HIGH-001",
    {
      reason: "DETERMINISTIC_HIGH_ZERO_CALL",
      occurrenceCount: 1,
      budgetComplete: true,
      budgetTruncated: false
    }
  ],
  [
    "TB-CTL-NONE-001",
    {
      reason: "NO_CANDIDATE",
      occurrenceCount: 0,
      budgetComplete: true,
      budgetTruncated: false
    }
  ],
  [
    "TB-CTL-BUDGET-001",
    {
      reason: "BUDGET_INCOMPLETE",
      occurrenceCount: 1,
      budgetComplete: false,
      budgetTruncated: true
    }
  ]
]);
const TRACK_B_RUNTIME_IDENTITIES = Object.freeze({
  MEDIUM: Object.freeze({
    taskId: "task-eval-002-track-b-admission-medium",
    executionId: "execution-track-b-admission-medium-v2"
  }),
  CONFLICTED: Object.freeze({
    taskId: "task-eval-002-track-b-admission-conflicted",
    executionId: "execution-track-b-admission-conflicted-v2"
  }),
  CONTROL: Object.freeze({
    taskId: "task-eval-002-track-b-admission-control",
    executionId: "execution-track-b-admission-control-v2"
  })
});

export function validateTrackBAdmissionCorpus(corpus, sourceCorpusSha256) {
  requireObject(corpus, "corpus");
  requireExactKeys(corpus, CORPUS_FIELDS, "corpus");
  if (
    corpus.schemaVersion !==
      "task-eval-002-track-b-admission-corpus-v1" ||
    corpus.status !==
      "DRAFT_PENDING_HUMAN_GROUND_TRUTH_CONFIRMATION" ||
    Number.isNaN(Date.parse(corpus.generatedAt)) ||
    corpus.source !==
      "INDEPENDENT_DEIDENTIFIED_SYNTHETIC_MINIMAL_EVIDENCE_PACKETS" ||
    corpus.containsProductionContractText !== false ||
    corpus.groundTruthIncluded !== false ||
    corpus.packetCount !== 18 ||
    corpus.eligiblePacketCount !== 15 ||
    corpus.mediumEligibleCount !== 8 ||
    corpus.conflictedEligibleCount !== 7 ||
    corpus.zeroCallControlCount !== 3 ||
    !Array.isArray(corpus.packets) ||
    corpus.packets.length !== 18
  ) {
    throw new Error("Track B admission corpus identity is invalid");
  }

  const eligiblePackets = corpus.packets.filter(
    (packet) => packet?.admission?.modelCallAllowed === true
  );
  const zeroCallPackets = corpus.packets.filter(
    (packet) => packet?.admission?.modelCallAllowed === false
  );
  validateTrackBAdmissionModelInput({
    schemaVersion: "task-eval-002-track-b-admission-model-input-v1",
    track: "TRACK_B_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION",
    sourceCorpusSha256,
    groundTruthIncluded: false,
    packetCount: eligiblePackets.length,
    packets: eligiblePackets
  });

  if (
    zeroCallPackets.length !== 3 ||
    new Set(corpus.packets.map((packet) => packet.packetId)).size !== 18 ||
    new Set(corpus.packets.map((packet) => packet.sampleId)).size !== 18
  ) {
    throw new Error("Track B admission corpus counts or identities changed");
  }
  for (const packet of zeroCallPackets) {
    requireObject(packet, "zeroCallPacket");
    requireExactKeys(packet, PACKET_FIELDS, "zeroCallPacket");
    const expected = ZERO_CALL_EXPECTATIONS.get(packet.sampleId);
    if (
      !expected ||
      packet.schemaVersion !== "task036-runtime-evidence-packet-v1" ||
      !/^EP-[a-f0-9]{64}$/.test(packet.packetId) ||
      packet.taskId !== TRACK_B_RUNTIME_IDENTITIES.CONTROL.taskId ||
      packet.executionId !==
        TRACK_B_RUNTIME_IDENTITIES.CONTROL.executionId ||
      packet.ruleSetVersion !== "v20260729.admission.1" ||
      packet.admission?.status !== "ZERO_CALL_REQUIRED" ||
      JSON.stringify(packet.admission?.reasonCodes) !==
        JSON.stringify([expected.reason]) ||
      packet.candidateOccurrences?.length !== expected.occurrenceCount ||
      packet.budget?.complete !== expected.budgetComplete ||
      packet.budget?.truncated !== expected.budgetTruncated
    ) {
      throw new Error(
        `Track B zero-call control is invalid: ${packet.sampleId}`
      );
    }
    validateCommonPacketShape(packet);
  }
  if (
    [...ZERO_CALL_EXPECTATIONS.keys()].some(
      (sampleId) =>
        !zeroCallPackets.some((packet) => packet.sampleId === sampleId)
    )
  ) {
    throw new Error("Track B admission zero-call control set changed");
  }
  return { corpus, eligiblePackets, zeroCallPackets };
}

export function validateTrackBAdmissionModelInput(input) {
  requireObject(input, "input");
  requireExactKeys(input, MODEL_INPUT_FIELDS, "input");
  if (
    input.schemaVersion !==
      "task-eval-002-track-b-admission-model-input-v1" ||
    input.track !==
      "TRACK_B_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION" ||
    !/^[a-f0-9]{64}$/.test(input.sourceCorpusSha256) ||
    input.groundTruthIncluded !== false ||
    !Array.isArray(input.packets) ||
    input.packets.length !== 15 ||
    input.packetCount !== input.packets.length
  ) {
    throw new Error("Track B admission model input identity is invalid");
  }
  const serialized = JSON.stringify(input);
  if (
    /(ground.?truth|human.?decision|proposed.?expected|cqcp.?actual|finding|verdict)/i.test(
      serialized.replace('"groundTruthIncluded":false', "")
    )
  ) {
    throw new Error("Track B admission model input contains forbidden context");
  }
  validateEligiblePackets(input.packets, {
    requireAdmissionCorpusIdentity: true
  });
  return input;
}

export function validateTrackBProviderModelInput(input) {
  requireObject(input, "providerInput");
  requireExactKeys(
    input,
    PROVIDER_MODEL_INPUT_FIELDS,
    "providerInput"
  );
  if (
    input.schemaVersion !==
      "ROLE_CANDIDATE_ANCHOR_ABSTENTION_INPUT_V1" ||
    input.track !==
      "TRACK_B_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION" ||
    typeof input.taskId !== "string" ||
    !input.taskId ||
    typeof input.executionId !== "string" ||
    !input.executionId ||
    typeof input.family !== "string" ||
    !input.family ||
    !Array.isArray(input.requestedRoles) ||
    input.requestedRoles.length === 0 ||
    input.requestedRoles.some(
      (role) => typeof role !== "string" || !role
    ) ||
    new Set(input.requestedRoles).size !== input.requestedRoles.length ||
    !Array.isArray(input.packets) ||
    input.packets.length === 0 ||
    input.packetCount !== input.packets.length
  ) {
    throw new Error("Track B provider model input identity is invalid");
  }
  const serialized = JSON.stringify(input);
  if (
    /(ground.?truth|human.?decision|proposed.?expected|cqcp.?actual|finding|verdict)/i.test(
      serialized
    )
  ) {
    throw new Error("Track B provider model input contains forbidden context");
  }
  validateEligiblePackets(input.packets);
  if (
    input.packets.some(
      (packet) =>
        packet.taskId !== input.taskId ||
        packet.executionId !== input.executionId ||
        packet.family !== input.family
    ) ||
    JSON.stringify(input.requestedRoles) !==
      JSON.stringify(input.packets.map((packet) => packet.candidateRole))
  ) {
    throw new Error("Track B provider model input grouping is invalid");
  }
  return input;
}

function validateEligiblePackets(
  packets,
  { requireAdmissionCorpusIdentity = false } = {}
) {
  const packetIds = new Set();
  const sampleIds = new Set();
  for (const packet of packets) {
    requireObject(packet, "packet");
    requireExactKeys(packet, PACKET_FIELDS, "packet");
    if (
      packet.schemaVersion !== "task036-runtime-evidence-packet-v1" ||
      typeof packet.packetId !== "string" ||
      !/^EP-[a-f0-9]{64}$/.test(packet.packetId) ||
      packetIds.has(packet.packetId) ||
      typeof packet.taskId !== "string" ||
      !packet.taskId ||
      typeof packet.executionId !== "string" ||
      !packet.executionId ||
      typeof packet.sampleId !== "string" ||
      !packet.sampleId ||
      sampleIds.has(packet.sampleId) ||
      typeof packet.ruleSetVersion !== "string" ||
      !packet.ruleSetVersion ||
      typeof packet.family !== "string" ||
      !packet.family ||
      typeof packet.reviewPointCode !== "string" ||
      !packet.reviewPointCode ||
      typeof packet.candidateRole !== "string" ||
      !packet.candidateRole ||
      packet.admission?.modelCallAllowed !== true ||
      packet.admission?.status !== "ELIGIBLE" ||
      !Array.isArray(packet.candidateOccurrences) ||
      packet.candidateOccurrences.length === 0 ||
      packet.candidateOccurrences.some(
        (candidate) =>
          candidate?.sourceAnchor?.reliable !== true ||
          typeof candidate?.sourceAnchor?.blockId !== "string" ||
          !candidate.sourceAnchor.blockId
      )
    ) {
      throw new Error("Track B admission packet is invalid");
    }
    if (
      requireAdmissionCorpusIdentity &&
      (packet.taskId !==
          (packet.sampleId.startsWith("TB-MED-")
            ? TRACK_B_RUNTIME_IDENTITIES.MEDIUM.taskId
            : TRACK_B_RUNTIME_IDENTITIES.CONFLICTED.taskId) ||
        packet.executionId !==
          (packet.sampleId.startsWith("TB-MED-")
            ? TRACK_B_RUNTIME_IDENTITIES.MEDIUM.executionId
            : TRACK_B_RUNTIME_IDENTITIES.CONFLICTED.executionId) ||
        !/^TB-(MED|CON)-\d{3}$/.test(packet.sampleId) ||
        packet.ruleSetVersion !== "v20260729.admission.1")
    ) {
      throw new Error("Track B admission packet identity is invalid");
    }
    packetIds.add(packet.packetId);
    sampleIds.add(packet.sampleId);
    validatePacketDetails(packet);
  }
}

function validatePacketDetails(packet) {
  validateCommonPacketShape(packet);
  if (
    !Array.isArray(packet.coverageSignals) ||
    packet.coverageSignals.length === 0
  ) {
    throw new Error("packet.coverageSignals is invalid");
  }
  for (const [index, signal] of packet.coverageSignals.entries()) {
    const signalPath = `packet.coverageSignals[${index}]`;
    if (
      signal.required !== true ||
      signal.critical !== true ||
      !["LOW_CONFIDENCE", "AMBIGUOUS"].includes(
        signal.coverageStatus
      ) ||
      signal.reliableAnchor !== true ||
      typeof signal.diagnosticCode !== "string" ||
      !signal.diagnosticCode
    ) {
      throw new Error(`${signalPath} is invalid`);
    }
  }
  if (
    !Number.isInteger(packet.budget.maxEvidenceChars) ||
    !Number.isInteger(packet.budget.usedEvidenceChars) ||
    packet.budget.maxEvidenceChars <= 0 ||
    packet.budget.usedEvidenceChars < 0 ||
    packet.budget.usedEvidenceChars > packet.budget.maxEvidenceChars ||
    packet.budget.complete !== true ||
    packet.budget.truncated !== false
  ) {
    throw new Error("packet.budget is invalid");
  }
  if (
    packet.admission.modelCallAllowed !== true ||
    packet.admission.status !== "ELIGIBLE" ||
    !Array.isArray(packet.admission.reasonCodes) ||
    packet.admission.reasonCodes.length !== 1 ||
    ![
      "ELIGIBLE_MEDIUM_AMBIGUITY",
      "ELIGIBLE_CONFLICT_LOCAL_CONTEXT"
    ].includes(packet.admission.reasonCodes[0])
  ) {
    throw new Error("packet.admission is invalid");
  }
}

function validateCommonPacketShape(packet) {
  const occurrenceIds = new Set();
  for (const [index, occurrence] of packet.candidateOccurrences.entries()) {
    const occurrencePath = `packet.candidateOccurrences[${index}]`;
    requireObject(occurrence, occurrencePath);
    requireExactKeys(occurrence, OCCURRENCE_FIELDS, occurrencePath);
    assertString(occurrence.occurrenceId, `${occurrencePath}.occurrenceId`);
    assertString(
      occurrence.candidateValue,
      `${occurrencePath}.candidateValue`
    );
    assertString(occurrence.evidenceText, `${occurrencePath}.evidenceText`);
    if (occurrenceIds.has(occurrence.occurrenceId)) {
      throw new Error(`${occurrencePath}.occurrenceId is duplicated`);
    }
    occurrenceIds.add(occurrence.occurrenceId);
    const anchor = occurrence.sourceAnchor;
    requireObject(anchor, `${occurrencePath}.sourceAnchor`);
    requireExactKeys(
      anchor,
      ANCHOR_FIELDS,
      `${occurrencePath}.sourceAnchor`
    );
    assertString(anchor.blockId, `${occurrencePath}.sourceAnchor.blockId`);
    if (
      anchor.locationLevel !== "BLOCK_LEVEL" ||
      (anchor.previewElementRef !== null &&
        typeof anchor.previewElementRef !== "string") ||
      !Array.isArray(anchor.sectionPath) ||
      anchor.sectionPath.some((part) => typeof part !== "string") ||
      anchor.regionType !== "BODY" ||
      (anchor.contextType !== null &&
        typeof anchor.contextType !== "string") ||
      anchor.reliable !== true
    ) {
      throw new Error(`${occurrencePath}.sourceAnchor is invalid`);
    }
  }

  if (!Array.isArray(packet.coverageSignals) || packet.coverageSignals.length === 0) {
    throw new Error("packet.coverageSignals is invalid");
  }
  for (const [index, signal] of packet.coverageSignals.entries()) {
    const signalPath = `packet.coverageSignals[${index}]`;
    requireObject(signal, signalPath);
    requireExactKeys(signal, COVERAGE_FIELDS, signalPath);
    assertString(signal.slotKey, `${signalPath}.slotKey`);
    if (
      typeof signal.slotKey !== "string" ||
      !signal.slotKey ||
      typeof signal.required !== "boolean" ||
      typeof signal.critical !== "boolean" ||
      typeof signal.coverageStatus !== "string" ||
      (signal.diagnosticCode !== null &&
        typeof signal.diagnosticCode !== "string") ||
      typeof signal.reliableAnchor !== "boolean"
    ) {
      throw new Error(`${signalPath} is invalid`);
    }
  }

  requireObject(packet.budget, "packet.budget");
  requireExactKeys(packet.budget, BUDGET_FIELDS, "packet.budget");
  if (
    !Number.isInteger(packet.budget.maxEvidenceChars) ||
    !Number.isInteger(packet.budget.usedEvidenceChars) ||
    packet.budget.maxEvidenceChars <= 0 ||
    packet.budget.usedEvidenceChars < 0 ||
    typeof packet.budget.complete !== "boolean" ||
    typeof packet.budget.truncated !== "boolean"
  ) {
    throw new Error("packet.budget is invalid");
  }

  requireObject(packet.admission, "packet.admission");
  requireExactKeys(packet.admission, ADMISSION_FIELDS, "packet.admission");
  if (
    typeof packet.admission.modelCallAllowed !== "boolean" ||
    typeof packet.admission.status !== "string" ||
    !Array.isArray(packet.admission.reasonCodes) ||
    packet.admission.reasonCodes.length !== 1 ||
    typeof packet.admission.reasonCodes[0] !== "string"
  ) {
    throw new Error("packet.admission is invalid");
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
    throw new Error("packet.requiredOutput is invalid");
  }
}

export function validateTrackBAdmissionOpinionPayload(input, payload) {
  const validatedInput =
    input?.schemaVersion ===
    "ROLE_CANDIDATE_ANCHOR_ABSTENTION_INPUT_V1"
      ? validateTrackBProviderModelInput(input)
      : validateTrackBAdmissionModelInput(input);
  requireObject(payload, "payload");
  requireExactKeys(payload, ROOT_FIELDS, "payload");
  if (!Array.isArray(payload.opinions)) {
    throw new Error("opinions must be an array");
  }
  if (payload.opinions.length !== validatedInput.packets.length) {
    throw new Error("opinions must cover every packet exactly once");
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
    if (typeof opinion.abstain !== "boolean") {
      throw new Error(`${path}.abstain must be boolean`);
    }
    requireStringArray(
      opinion.selectedOccurrenceIds,
      `${path}.selectedOccurrenceIds`
    );
    requireStringArray(
      opinion.selectedAnchorBlockIds,
      `${path}.selectedAnchorBlockIds`
    );
    if (
      opinion.abstentionReason !== null &&
      typeof opinion.abstentionReason !== "string"
    ) {
      throw new Error(`${path}.abstentionReason must be string or null`);
    }
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
      throw new Error(`${path}.selectedOccurrenceIds is invalid`);
    }
    const expectedAnchors = [
      ...new Set(
        opinion.selectedOccurrenceIds.map(
          (occurrenceId) =>
            occurrenceById.get(occurrenceId).sourceAnchor.blockId
        )
      )
    ];
    if (
      JSON.stringify(opinion.selectedAnchorBlockIds) !==
      JSON.stringify(expectedAnchors)
    ) {
      throw new Error(
        `${path}.selectedAnchorBlockIds must exactly match selected occurrences`
      );
    }
    if (opinion.abstain) {
      if (
        opinion.suggestedRole !== null ||
        opinion.selectedOccurrenceIds.length !== 0 ||
        opinion.selectedAnchorBlockIds.length !== 0 ||
        !TRACK_B_ABSTENTION_REASONS.includes(opinion.abstentionReason)
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
  return payload;
}

export function validateTrackBDeepSeekEnvelope(input, envelope) {
  const choice = envelope?.choices?.[0];
  if (envelope?.choices?.length !== 1) {
    throw admissionError("CHOICE_COUNT_INVALID");
  }
  if (choice?.finish_reason !== "stop") {
    throw admissionError("FINISH_REASON_NOT_STOP");
  }
  if (
    typeof choice?.message?.content !== "string" ||
    !choice.message.content.trim()
  ) {
    throw admissionError("CONTENT_EMPTY");
  }
  let payload;
  try {
    payload = parseJsonRejectDuplicateKeys(choice.message.content);
  } catch {
    throw admissionError("CONTENT_NOT_JSON");
  }
  try {
    return validateTrackBAdmissionOpinionPayload(input, payload);
  } catch {
    throw admissionError("OPINION_SCHEMA_INVALID");
  }
}

function admissionError(admissionCode) {
  const error = new Error("Track B DeepSeek completion was not admitted");
  error.admissionCode = admissionCode;
  return error;
}

function requireObject(value, path) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${path} must be an object`);
  }
}

function requireExactKeys(value, allowed, path) {
  const keys = Object.keys(value);
  if (
    keys.length !== allowed.size ||
    keys.some((key) => !allowed.has(key))
  ) {
    throw new Error(`${path} fields are invalid`);
  }
}

function requireStringArray(value, path) {
  if (
    !Array.isArray(value) ||
    value.some((item) => typeof item !== "string")
  ) {
    throw new Error(`${path} must be a string array`);
  }
}

function assertString(value, path) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${path} must be a non-empty string`);
  }
}
