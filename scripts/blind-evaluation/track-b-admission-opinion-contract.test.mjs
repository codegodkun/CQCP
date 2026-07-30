import assert from "node:assert/strict";
import test from "node:test";

import {
  validateTrackBAdmissionModelInput,
  validateTrackBAdmissionOpinionPayload,
  validateTrackBDeepSeekEnvelope
} from "./track-b-admission-opinion-contract.mjs";

const packet = (index, suffix, role, occurrences) => ({
  schemaVersion: "task036-runtime-evidence-packet-v1",
  packetId: `EP-${suffix.padStart(64, "0")}`,
  taskId:
    index < 8
      ? "task-eval-002-track-b-admission-medium"
      : "task-eval-002-track-b-admission-conflicted",
  executionId:
    index < 8
      ? "execution-track-b-admission-medium-v2"
      : "execution-track-b-admission-conflicted-v2",
  sampleId: `TB-${index < 8 ? "MED" : "CON"}-${String(index + 1).padStart(3, "0")}`,
  ruleSetVersion: "v20260729.admission.1",
  family: "TEST_FAMILY",
  reviewPointCode: `TEST_POINT_${index + 1}`,
  candidateRole: role,
  candidateOccurrences: occurrences,
  coverageSignals: [
    {
      slotKey: `slot_${index + 1}`,
      required: true,
      critical: true,
      coverageStatus: index < 8 ? "LOW_CONFIDENCE" : "AMBIGUOUS",
      diagnosticCode:
        index < 8
          ? "SYS_EVIDENCE_MEDIUM_CONFIDENCE"
          : "SYS_ROLE_CONFLICT",
      reliableAnchor: true
    }
  ],
  budget: {
    maxEvidenceChars: 4096,
    usedEvidenceChars: 20,
    complete: true,
    truncated: false
  },
  admission: {
    modelCallAllowed: true,
    status: "ELIGIBLE",
    reasonCodes: [
      index < 8
        ? "ELIGIBLE_MEDIUM_AMBIGUITY"
        : "ELIGIBLE_CONFLICT_LOCAL_CONTEXT"
    ]
  },
  requiredOutput: {
    contract: "ROLE_CANDIDATE_ANCHOR_ABSTENTION_V1",
    fields: [
      "suggestedRole",
      "selectedOccurrenceIds",
      "selectedAnchorBlockIds",
      "abstain",
      "abstentionReason"
    ],
    instruction:
      "Only assess supplied role/candidate/anchor evidence. " +
      "Do not produce final business adjudication."
  }
});
const occurrence = (id, blockId) => ({
  occurrenceId: id,
  candidateValue: `value-${id}`,
  evidenceText: `evidence-${id}`,
  sourceAnchor: {
    blockId,
    locationLevel: "BLOCK_LEVEL",
    previewElementRef: null,
    sectionPath: ["脱敏合同片段"],
    regionType: "BODY",
    contextType: null,
    reliable: true
  }
});
const packets = Array.from({ length: 15 }, (_, index) =>
  packet(
    index,
    (index + 1).toString(16),
    `ROLE_${index + 1}`,
    [
      occurrence("OCC-001", `block-${index + 1}-a`),
      occurrence("OCC-002", `block-${index + 1}-b`)
    ]
  )
);
const input = {
  schemaVersion: "task-eval-002-track-b-admission-model-input-v1",
  track: "TRACK_B_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION",
  sourceCorpusSha256: "a".repeat(64),
  groundTruthIncluded: false,
  packetCount: packets.length,
  packets
};
const payload = {
  opinions: packets.map((item) => ({
    packetId: item.packetId,
    suggestedRole: item.candidateRole,
    selectedOccurrenceIds: ["OCC-001"],
    selectedAnchorBlockIds: [
      item.candidateOccurrences[0].sourceAnchor.blockId
    ],
    abstain: false,
    abstentionReason: null
  }))
};

test("Track B model input and strict opinion contract accept exact local selections", () => {
  assert.equal(validateTrackBAdmissionModelInput(input), input);
  assert.equal(validateTrackBAdmissionOpinionPayload(input, payload), payload);
  const envelope = {
    choices: [
      {
        finish_reason: "stop",
        message: {
          content: JSON.stringify(payload),
          reasoning_content: "must be ignored"
        }
      }
    ]
  };
  assert.deepEqual(validateTrackBDeepSeekEnvelope(input, envelope), payload);
});

test("Track B opinion contract rejects forged anchors, extra fields, and incomplete coverage", () => {
  const forgedAnchor = structuredClone(payload);
  forgedAnchor.opinions[0].selectedAnchorBlockIds = ["forged"];
  assert.throws(
    () => validateTrackBAdmissionOpinionPayload(input, forgedAnchor),
    /must exactly match/
  );

  const extra = structuredClone(payload);
  extra.opinions[0].finding = "forbidden";
  assert.throws(
    () => validateTrackBAdmissionOpinionPayload(input, extra),
    /fields are invalid/
  );

  const incomplete = structuredClone(payload);
  incomplete.opinions.pop();
  assert.throws(
    () => validateTrackBAdmissionOpinionPayload(input, incomplete),
    /cover every packet/
  );
});

test("Track B abstention is strict and DeepSeek completion fails closed", () => {
  const abstained = structuredClone(payload);
  abstained.opinions[0] = {
    packetId: packets[0].packetId,
    suggestedRole: null,
    selectedOccurrenceIds: [],
    selectedAnchorBlockIds: [],
    abstain: true,
    abstentionReason: "CONFLICT_UNRESOLVED"
  };
  assert.equal(
    validateTrackBAdmissionOpinionPayload(input, abstained),
    abstained
  );
  const unknownReason = structuredClone(abstained);
  unknownReason.opinions[0].abstentionReason =
    "LOCAL_EVIDENCE_AMBIGUOUS";
  assert.throws(
    () => validateTrackBAdmissionOpinionPayload(input, unknownReason),
    /invalid abstention/
  );

  for (const finishReason of [
    "length",
    "content_filter",
    "tool_calls",
    "insufficient_system_resource"
  ]) {
    assert.throws(
      () =>
        validateTrackBDeepSeekEnvelope(input, {
          choices: [
            {
              finish_reason: finishReason,
              message: { content: "{}" }
            }
          ]
        }),
      (error) => error.admissionCode === "FINISH_REASON_NOT_STOP",
      finishReason
    );
  }

  assert.throws(
    () =>
      validateTrackBDeepSeekEnvelope(input, {
        choices: [
          {
            finish_reason: "stop",
            message: {
              content:
                `{"opinions":${JSON.stringify(payload.opinions)},` +
                `"opin\\u0069ons":[]}`
            }
          }
        ]
      }),
    (error) => error.admissionCode === "CONTENT_NOT_JSON"
  );
});
