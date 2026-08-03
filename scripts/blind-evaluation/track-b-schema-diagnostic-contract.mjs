import assert from "node:assert/strict";
import { createHash } from "node:crypto";

import { parseJsonRejectDuplicateKeys } from "./strict-json.mjs";

export const TRACK_B_SCHEMA_DIAGNOSTIC_MODEL = "deepseek-v4-pro";
export const TRACK_B_SCHEMA_DIAGNOSTIC_CALLS_PER_PASS = 12;
export const TRACK_B_SCHEMA_DIAGNOSTIC_MAX_CALLS = 60;
export const TRACK_B_SCHEMA_DIAGNOSTIC_MAX_OUTPUT_TOKENS = 1500;
export const TRACK_B_SCHEMA_DIAGNOSTIC_INPUT_SCHEMA =
  "task-eval-005-track-b-schema-diagnostic-input-v1";
export const TRACK_B_SCHEMA_DIAGNOSTIC_PROVIDER_INPUT_SCHEMA =
  "task-eval-005-track-b-schema-diagnostic-provider-input-v1";

export const TRACK_B_SCHEMA_DIAGNOSTIC_CLASSES = Object.freeze([
  "ACCEPTED",
  "ENVELOPE_INVALID",
  "FINISH_REASON_NOT_STOP",
  "CONTENT_EMPTY",
  "CONTENT_NOT_JSON",
  "ROOT_FIELDS_INVALID",
  "OPINION_COUNT_INVALID",
  "OPINION_FIELDS_INVALID",
  "FIELD_TYPE_OR_ENUM_INVALID",
  "PACKET_COVERAGE_INVALID",
  "OCCURRENCE_OR_ANCHOR_INVALID",
  "ABSTENTION_CONTRACT_INVALID",
  "UNKNOWN_VALIDATION_FAILURE"
]);

const ROOT_FIELDS = new Set(["opinions"]);
const OPINION_FIELDS = new Set([
  "packetId",
  "suggestedRole",
  "selectedOccurrenceIds",
  "selectedAnchorBlockIds",
  "abstain",
  "abstentionReason"
]);
const ABSTENTION_REASONS = new Set([
  "NOT_ENOUGH_EVIDENCE",
  "CONFLICT_UNRESOLVED",
  "ROLE_NOT_COVERED"
]);

export const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

export const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

export function buildTrackBSchemaDiagnosticInput(templateInput) {
  assert.equal(templateInput?.packets?.length, 9);
  const packets = Array.from(
    { length: TRACK_B_SCHEMA_DIAGNOSTIC_CALLS_PER_PASS },
    (_, index) => transformPacket(templateInput.packets[index % 9], index)
  );
  return validateTrackBSchemaDiagnosticInput({
    schemaVersion: TRACK_B_SCHEMA_DIAGNOSTIC_INPUT_SCHEMA,
    track: "TRACK_B_SCHEMA_STABILITY_DIAGNOSTIC_ONLY",
    purpose: "PROVIDER_VALIDATION",
    dataClass: "SYNTHETIC_CORPUS",
    formalAdmissionAffected: false,
    groundTruthIncluded: false,
    findingOrVerdictIncluded: false,
    packetCount: packets.length,
    packets
  });
}

function transformPacket(source, index) {
  const ordinal = String(index + 1).padStart(3, "0");
  const occurrences = source.candidateOccurrences.map((item, itemIndex) => {
    const occurrenceOrdinal = String(itemIndex + 1).padStart(3, "0");
    const candidateValue =
      `诊断代号-${ordinal}-${occurrenceOrdinal}-${source.candidateRole}`;
    const blockId = `tbsd-${ordinal}-block-${occurrenceOrdinal}`;
    const tableCell = item.sourceAnchor.locationLevel === "TABLE_CELL";
    return {
      occurrenceId: `DG-OCC-${ordinal}-${occurrenceOrdinal}`,
      candidateValue,
      evidenceText:
        `合成 schema 诊断样本 ${ordinal}，候选 ${occurrenceOrdinal}：` +
        `角色 ${source.candidateRole} 的候选代号为「${candidateValue}」。`,
      sourceAnchor: {
        blockId,
        locationLevel: tableCell ? "TABLE_CELL" : "BLOCK_LEVEL",
        previewElementRef: tableCell
          ? `table:tbsd_${ordinal}/row:${itemIndex + 1}/cell:2`
          : null,
        sectionPath: [`合成诊断-${ordinal}`],
        regionType: "BODY",
        contextType: "NORMAL",
        reliable: true
      }
    };
  });
  const packetSeed = {
    ordinal,
    family: source.family,
    role: source.candidateRole,
    occurrences
  };
  return {
    schemaVersion: "task036-runtime-evidence-packet-v1",
    packetId: `EP-${sha256(Buffer.from(JSON.stringify(packetSeed), "utf8"))}`,
    taskId: "task-eval-005-schema-diagnostic",
    executionId: "execution-track-b-schema-diagnostic-v1",
    sampleId: `TBSD-${ordinal}`,
    ruleSetVersion: "v20260803.schema-diagnostic.1",
    family: source.family,
    reviewPointCode: source.reviewPointCode,
    candidateRole: source.candidateRole,
    candidateOccurrences: occurrences,
    coverageSignals: [{
      slotKey: source.coverageSignals[0].slotKey,
      required: true,
      critical: true,
      coverageStatus: index % 2 === 0 ? "LOW_CONFIDENCE" : "AMBIGUOUS",
      diagnosticCode: "SYS_SCHEMA_DIAGNOSTIC_SYNTHETIC",
      reliableAnchor: true
    }],
    budget: {
      maxEvidenceChars: 4096,
      usedEvidenceChars: occurrences.reduce(
        (sum, item) => sum + item.evidenceText.length,
        0
      ),
      complete: true,
      truncated: false
    },
    admission: {
      modelCallAllowed: true,
      status: "ELIGIBLE",
      reasonCodes: [
        index % 2 === 0
          ? "ELIGIBLE_MEDIUM_AMBIGUITY"
          : "ELIGIBLE_CONFLICT_LOCAL_CONTEXT"
      ],
      requiredBlockIds: occurrences.map(
        (item) => item.sourceAnchor.blockId
      )
    },
    requiredOutput: structuredClone(source.requiredOutput)
  };
}

export function validateTrackBSchemaDiagnosticInput(input) {
  assert.deepEqual(Object.keys(input), [
    "schemaVersion",
    "track",
    "purpose",
    "dataClass",
    "formalAdmissionAffected",
    "groundTruthIncluded",
    "findingOrVerdictIncluded",
    "packetCount",
    "packets"
  ]);
  assert.equal(input.schemaVersion, TRACK_B_SCHEMA_DIAGNOSTIC_INPUT_SCHEMA);
  assert.equal(input.track, "TRACK_B_SCHEMA_STABILITY_DIAGNOSTIC_ONLY");
  assert.equal(input.purpose, "PROVIDER_VALIDATION");
  assert.equal(input.dataClass, "SYNTHETIC_CORPUS");
  assert.equal(input.formalAdmissionAffected, false);
  assert.equal(input.groundTruthIncluded, false);
  assert.equal(input.findingOrVerdictIncluded, false);
  assert.equal(input.packetCount, TRACK_B_SCHEMA_DIAGNOSTIC_CALLS_PER_PASS);
  assert.equal(input.packets.length, input.packetCount);
  assert.equal(new Set(input.packets.map((item) => item.packetId)).size, 12);
  for (const packet of input.packets) {
    assert.equal(packet.admission.modelCallAllowed, true);
    assert.equal(packet.admission.status, "ELIGIBLE");
    assert.ok(packet.candidateOccurrences.length > 0);
    assert.ok(packet.candidateOccurrences.every(
      (item) => item.sourceAnchor.reliable === true
    ));
  }
  rejectForbiddenSerialized(input);
  return input;
}

export function buildTrackBSchemaDiagnosticCalls(input, promptBytes) {
  validateTrackBSchemaDiagnosticInput(input);
  assert.ok(Buffer.isBuffer(promptBytes));
  return input.packets.map((packet, index) => {
    const providerInput = {
      schemaVersion: TRACK_B_SCHEMA_DIAGNOSTIC_PROVIDER_INPUT_SCHEMA,
      track: input.track,
      purpose: input.purpose,
      packetCount: 1,
      packets: [packet]
    };
    const providerInputBytes = Buffer.from(
      JSON.stringify(providerInput),
      "utf8"
    );
    const outboundRequest = {
      model: TRACK_B_SCHEMA_DIAGNOSTIC_MODEL,
      thinking: { type: "disabled" },
      messages: [
        { role: "system", content: promptBytes.toString("utf8") },
        { role: "user", content: providerInputBytes.toString("utf8") }
      ],
      response_format: { type: "json_object" },
      stream: false,
      max_tokens: TRACK_B_SCHEMA_DIAGNOSTIC_MAX_OUTPUT_TOKENS
    };
    const outboundRequestBytes = Buffer.from(
      JSON.stringify(outboundRequest),
      "utf8"
    );
    return {
      callIndex: index,
      callId: `TBSD-CALL-${sha256(providerInputBytes)}`,
      packetId: packet.packetId,
      input: providerInput,
      modelInputSha256: sha256(providerInputBytes),
      outboundRequestBytes,
      outboundRequestSha256: sha256(outboundRequestBytes)
    };
  });
}

export function classifyTrackBSchemaDiagnosticEnvelope(input, envelope) {
  try {
    if (
      !envelope ||
      typeof envelope !== "object" ||
      Array.isArray(envelope) ||
      !Array.isArray(envelope.choices) ||
      envelope.choices.length !== 1 ||
      envelope.model !== TRACK_B_SCHEMA_DIAGNOSTIC_MODEL
    ) {
      return "ENVELOPE_INVALID";
    }
    const choice = envelope.choices[0];
    if (choice?.finish_reason !== "stop") {
      return "FINISH_REASON_NOT_STOP";
    }
    const content = choice?.message?.content;
    if (typeof content !== "string" || !content.trim()) {
      return "CONTENT_EMPTY";
    }
    let payload;
    try {
      payload = parseJsonRejectDuplicateKeys(content);
    } catch {
      return "CONTENT_NOT_JSON";
    }
    if (!isObject(payload) || !hasExactKeys(payload, ROOT_FIELDS)) {
      return "ROOT_FIELDS_INVALID";
    }
    if (!Array.isArray(payload.opinions) || payload.opinions.length !== 1) {
      return "OPINION_COUNT_INVALID";
    }
    const opinion = payload.opinions[0];
    if (!isObject(opinion) || !hasExactKeys(opinion, OPINION_FIELDS)) {
      return "OPINION_FIELDS_INVALID";
    }
    if (
      typeof opinion.packetId !== "string" ||
      !Array.isArray(opinion.selectedOccurrenceIds) ||
      !opinion.selectedOccurrenceIds.every((item) => typeof item === "string") ||
      !Array.isArray(opinion.selectedAnchorBlockIds) ||
      !opinion.selectedAnchorBlockIds.every((item) => typeof item === "string") ||
      typeof opinion.abstain !== "boolean" ||
      ![null, input.packets[0].candidateRole].includes(opinion.suggestedRole) ||
      ![null, ...ABSTENTION_REASONS].includes(opinion.abstentionReason)
    ) {
      return "FIELD_TYPE_OR_ENUM_INVALID";
    }
    const packet = input.packets[0];
    if (opinion.packetId !== packet.packetId) {
      return "PACKET_COVERAGE_INVALID";
    }
    const occurrenceById = new Map(packet.candidateOccurrences.map(
      (item) => [item.occurrenceId, item]
    ));
    if (
      new Set(opinion.selectedOccurrenceIds).size !==
        opinion.selectedOccurrenceIds.length ||
      opinion.selectedOccurrenceIds.some((id) => !occurrenceById.has(id))
    ) {
      return "OCCURRENCE_OR_ANCHOR_INVALID";
    }
    const expectedBlocks = [...new Set(opinion.selectedOccurrenceIds.map(
      (id) => occurrenceById.get(id).sourceAnchor.blockId
    ))];
    if (
      JSON.stringify(expectedBlocks) !==
        JSON.stringify(opinion.selectedAnchorBlockIds) ||
      expectedBlocks.some(
        (id) => !packet.admission.requiredBlockIds.includes(id)
      )
    ) {
      return "OCCURRENCE_OR_ANCHOR_INVALID";
    }
    if (opinion.abstain) {
      if (
        opinion.suggestedRole !== null ||
        opinion.selectedOccurrenceIds.length !== 0 ||
        opinion.selectedAnchorBlockIds.length !== 0 ||
        !ABSTENTION_REASONS.has(opinion.abstentionReason)
      ) {
        return "ABSTENTION_CONTRACT_INVALID";
      }
    } else if (
      opinion.suggestedRole !== packet.candidateRole ||
      opinion.selectedOccurrenceIds.length === 0 ||
      opinion.selectedAnchorBlockIds.length === 0 ||
      opinion.abstentionReason !== null
    ) {
      return "ABSTENTION_CONTRACT_INVALID";
    }
    return "ACCEPTED";
  } catch {
    return "UNKNOWN_VALIDATION_FAILURE";
  }
}

export function collectTrackBSchemaDiagnosticDisjointness(
  diagnosticInput,
  priorCorpora
) {
  const current = collectProjection(diagnosticInput.packets);
  const prior = priorCorpora.map((item) => collectProjection(item.packets));
  const priorUnion = {
    identities: new Set(prior.flatMap((item) => [...item.identities])),
    values: new Set(prior.flatMap((item) => [...item.values])),
    evidence: new Set(prior.flatMap((item) => [...item.evidence]))
  };
  return {
    priorCorpusCount: priorCorpora.length,
    identityOverlapCount: overlap(current.identities, priorUnion.identities),
    candidateValueOverlapCount: overlap(current.values, priorUnion.values),
    evidenceTextOverlapCount: overlap(current.evidence, priorUnion.evidence)
  };
}

function collectProjection(packets) {
  const identities = new Set();
  const values = new Set();
  const evidence = new Set();
  for (const packet of packets ?? []) {
    identities.add(packet.packetId);
    identities.add(packet.taskId);
    identities.add(packet.executionId);
    identities.add(packet.sampleId);
    for (const item of packet.candidateOccurrences ?? []) {
      identities.add(item.occurrenceId);
      identities.add(item.sourceAnchor?.blockId);
      values.add(item.candidateValue);
      evidence.add(item.evidenceText);
    }
  }
  return { identities, values, evidence };
}

const overlap = (left, right) =>
  [...left].filter((item) => right.has(item)).length;

const isObject = (value) =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const hasExactKeys = (value, expected) => {
  const keys = Object.keys(value);
  return keys.length === expected.size && keys.every((key) => expected.has(key));
};

function rejectForbiddenSerialized(value) {
  const serialized = JSON.stringify(value);
  if (
    /(human.?ground.?truth|human.?decision|proposed.?expected|cqcp.?actual|cqcp.?expected|final.?finding|final.?verdict|reasoning_content|authorization)/i.test(
      serialized
    )
  ) {
    throw new Error("TRACK_B_SCHEMA_DIAGNOSTIC_FORBIDDEN_CONTENT");
  }
}
