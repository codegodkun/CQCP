import assert from "node:assert/strict";

import {
  TRACK_B_FIFTH_TRACK,
  TRACK_B_FINAL_TRACK,
  TRACK_B_HOLDOUT_COUNTS,
  TRACK_B_HOLDOUT_TRACK,
  TRACK_B_SUCCESSOR_TRACK
} from "./track-b-holdout-constants.mjs";
import {
  TRACK_B_FIFTH_MODEL_INPUT_SCHEMA,
  TRACK_B_FINAL_MODEL_INPUT_SCHEMA,
  TRACK_B_HOLDOUT_ABSTENTION_REASONS,
  TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
  TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA,
  validateTrackBFifthCorpusForModel,
  validateTrackBFifthModelInput,
  validateTrackBFifthOpinionPayload,
  validateTrackBFinalCorpusForModel,
  validateTrackBFinalModelInput,
  validateTrackBFinalOpinionPayload,
  validateTrackBHoldoutCorpusForModel,
  validateTrackBHoldoutModelInput,
  validateTrackBHoldoutOpinionPayload,
  validateTrackBSuccessorCorpusForModel,
  validateTrackBSuccessorModelInput,
  validateTrackBSuccessorOpinionPayload
} from "./track-b-holdout-opinion-contract.mjs";

const HOLDOUT_PROFILE = Object.freeze({
  modelInputSchema: TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
  groundTruthSchema:
    "task-eval-002-track-b-holdout-human-ground-truth-v1",
  evaluationSchema:
    "task-eval-002-track-b-holdout-admission-evaluation-v1",
  track: TRACK_B_HOLDOUT_TRACK,
  validateCorpusForModel: validateTrackBHoldoutCorpusForModel,
  validateModelInput: validateTrackBHoldoutModelInput,
  validateOpinionPayload: validateTrackBHoldoutOpinionPayload
});

const SUCCESSOR_PROFILE = Object.freeze({
  modelInputSchema: TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA,
  groundTruthSchema:
    "task-eval-003-track-b-successor-human-ground-truth-v1",
  evaluationSchema:
    "task-eval-003-track-b-successor-admission-evaluation-v1",
  track: TRACK_B_SUCCESSOR_TRACK,
  validateCorpusForModel: validateTrackBSuccessorCorpusForModel,
  validateModelInput: validateTrackBSuccessorModelInput,
  validateOpinionPayload: validateTrackBSuccessorOpinionPayload
});

const FINAL_PROFILE = Object.freeze({
  modelInputSchema: TRACK_B_FINAL_MODEL_INPUT_SCHEMA,
  groundTruthSchema:
    "task-eval-004-track-b-final-human-ground-truth-v1",
  evaluationSchema:
    "task-eval-004-track-b-final-admission-evaluation-v1",
  track: TRACK_B_FINAL_TRACK,
  validateCorpusForModel: validateTrackBFinalCorpusForModel,
  validateModelInput: validateTrackBFinalModelInput,
  validateOpinionPayload: validateTrackBFinalOpinionPayload
});

const FIFTH_PROFILE = Object.freeze({
  modelInputSchema: TRACK_B_FIFTH_MODEL_INPUT_SCHEMA,
  groundTruthSchema:
    "task-eval-005-track-b-fifth-human-ground-truth-v1",
  evaluationSchema:
    "task-eval-005-track-b-fifth-admission-evaluation-v2",
  track: TRACK_B_FIFTH_TRACK,
  validateCorpusForModel: validateTrackBFifthCorpusForModel,
  validateModelInput: validateTrackBFifthModelInput,
  validateOpinionPayload: validateTrackBFifthOpinionPayload
});

export function evaluateTrackBHoldoutAdmission({
  corpus,
  corpusSha256,
  humanGroundTruth,
  codexPayload,
  deepSeekPayload
}) {
  return evaluateTrackBAdmission({
    corpus,
    corpusSha256,
    humanGroundTruth,
    codexPayload,
    deepSeekPayload,
    profile: HOLDOUT_PROFILE
  });
}

export function evaluateTrackBSuccessorAdmission({
  corpus,
  corpusSha256,
  humanGroundTruth,
  codexPayload,
  deepSeekPayload
}) {
  return evaluateTrackBAdmission({
    corpus,
    corpusSha256,
    humanGroundTruth,
    codexPayload,
    deepSeekPayload,
    profile: SUCCESSOR_PROFILE
  });
}

export function evaluateTrackBFinalAdmission({
  corpus,
  corpusSha256,
  humanGroundTruth,
  codexPayload,
  deepSeekPayload
}) {
  return evaluateTrackBAdmission({
    corpus,
    corpusSha256,
    humanGroundTruth,
    codexPayload,
    deepSeekPayload,
    profile: FINAL_PROFILE
  });
}

export function evaluateTrackBFifthAdmission({
  corpus,
  corpusSha256,
  humanGroundTruth,
  codexPayload,
  deepSeekPayload
}) {
  return evaluateTrackBAdmission({
    corpus,
    corpusSha256,
    humanGroundTruth,
    codexPayload,
    deepSeekPayload,
    profile: FIFTH_PROFILE
  });
}

function evaluateTrackBAdmission({
  corpus,
  corpusSha256,
  humanGroundTruth,
  codexPayload,
  deepSeekPayload,
  profile
}) {
  const { eligiblePackets, zeroCallPackets } =
    profile.validateCorpusForModel(corpus, corpusSha256);
  const modelInput = profile.validateModelInput({
    schemaVersion: profile.modelInputSchema,
    track: profile.track,
    sourceCorpusSha256: corpusSha256,
    groundTruthIncluded: false,
    packetCount: eligiblePackets.length,
    packets: eligiblePackets
  });
  const groundTruth = validateHumanGroundTruth(
    humanGroundTruth,
    corpus,
    corpusSha256,
    profile
  );
  const codex = profile.validateOpinionPayload(
    modelInput,
    codexPayload
  );
  const deepSeek = profile.validateOpinionPayload(
    modelInput,
    deepSeekPayload
  );
  const expectedByPacketId = new Map(
    groundTruth.entries.map((entry) => [entry.packetId, entry.expected])
  );
  const packetById = new Map(
    corpus.packets.map((packet) => [packet.packetId, packet])
  );
  const groundTruthCompatibility = eligiblePackets.map((packet) => {
    const expected = expectedByPacketId.get(packet.packetId);
    const occurrenceById = new Map(
      packet.candidateOccurrences.map((candidate) => [
        candidate.occurrenceId,
        candidate
      ])
    );
    const anchorsReliable = expected.selectedOccurrenceIds.every(
      (occurrenceId) =>
        occurrenceById.get(occurrenceId)?.sourceAnchor?.reliable === true
    );
    const anchorsWithinAdmission = expected.selectedAnchorBlockIds.every(
      (blockId) => packet.admission.requiredBlockIds.includes(blockId)
    );
    const abstentionRepresentable =
      !expected.abstain ||
      TRACK_B_HOLDOUT_ABSTENTION_REASONS.includes(
        expected.abstentionReason
      );
    return {
      packetId: packet.packetId,
      sampleId: packet.sampleId,
      compatible:
        anchorsReliable &&
        anchorsWithinAdmission &&
        abstentionRepresentable,
      anchorsReliable,
      anchorsWithinAdmission,
      abstentionRepresentable
    };
  });
  const controls = zeroCallPackets.map((packet) => {
    const expected = expectedByPacketId.get(packet.packetId);
    const correct =
      expected.abstain === true &&
      expected.suggestedRole === null &&
      expected.selectedOccurrenceIds.length === 0 &&
      expected.selectedAnchorBlockIds.length === 0 &&
      expected.abstentionReason === packet.admission.reasonCodes[0];
    return {
      packetId: packet.packetId,
      sampleId: packet.sampleId,
      reason: packet.admission.reasonCodes[0],
      modelCallAllowed: false,
      humanDecisionMatchesZeroCall: correct
    };
  });
  const evaluators = [
    scoreEvaluator("CODEX", codex, expectedByPacketId, packetById),
    scoreEvaluator(
      "DEEPSEEK_V4_PRO",
      deepSeek,
      expectedByPacketId,
      packetById
    )
  ];
  const groundTruthCompatible = groundTruthCompatibility.every(
    (entry) => entry.compatible
  );
  const controlsPassed = controls.every(
    (entry) =>
      entry.modelCallAllowed === false &&
      entry.humanDecisionMatchesZeroCall
  );
  const evaluatorGatesPassed = evaluators.every(
    (entry) => entry.allDimensions100Percent
  );
  const allGatesPassed =
    groundTruthCompatible && controlsPassed && evaluatorGatesPassed;
  return {
    schemaVersion: profile.evaluationSchema,
    status: allGatesPassed
      ? "GO_ALL_ADMISSION_DIMENSIONS_100_PERCENT"
      : "NO_GO_MODEL_MISMATCH",
    providerAdmission: allGatesPassed
      ? "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE"
      : "NOT_ESTABLISHED",
    track: profile.track,
    corpusSha256,
    threshold: {
      schemaPercent: 100,
      reliableAnchorPercent: 100,
      rolePercent: 100,
      candidatePercent: 100,
      anchorPercent: 100,
      abstentionPercent: 100,
      deterministicHighOverrideAllowed: false,
      modelMayProduceFindingOrVerdict: false
    },
    counts: {
      totalPacketCount: TRACK_B_HOLDOUT_COUNTS.total,
      eligiblePacketCount: TRACK_B_HOLDOUT_COUNTS.eligible,
      zeroCallControlCount: TRACK_B_HOLDOUT_COUNTS.zeroCallControls
    },
    groundTruthCompatible,
    groundTruthCompatibility,
    controlsPassed,
    controls,
    evaluatorGatesPassed,
    evaluators,
    allGatesPassed
  };
}

function scoreEvaluator(
  evaluator,
  payload,
  expectedByPacketId,
  packetById
) {
  const dimensions = {
    schema: 0,
    reliableAnchor: 0,
    role: 0,
    candidate: 0,
    anchor: 0,
    abstention: 0
  };
  const mismatches = [];
  for (const opinion of payload.opinions) {
    const expected = expectedByPacketId.get(opinion.packetId);
    const packet = packetById.get(opinion.packetId);
    assert.ok(expected && packet);
    dimensions.schema += 1;
    const occurrenceById = new Map(
      packet.candidateOccurrences.map((candidate) => [
        candidate.occurrenceId,
        candidate
      ])
    );
    const reliable = opinion.selectedOccurrenceIds.every(
      (occurrenceId) =>
        occurrenceById.get(occurrenceId)?.sourceAnchor?.reliable === true
    );
    const role = opinion.suggestedRole === expected.suggestedRole;
    const candidate = arraysEqual(
      opinion.selectedOccurrenceIds,
      expected.selectedOccurrenceIds
    );
    const anchor = arraysEqual(
      opinion.selectedAnchorBlockIds,
      expected.selectedAnchorBlockIds
    );
    const abstention =
      opinion.abstain === expected.abstain &&
      opinion.abstentionReason === expected.abstentionReason;
    if (reliable) dimensions.reliableAnchor += 1;
    if (role) dimensions.role += 1;
    if (candidate) dimensions.candidate += 1;
    if (anchor) dimensions.anchor += 1;
    if (abstention) dimensions.abstention += 1;
    const failedDimensions = Object.entries({
      reliableAnchor: reliable,
      role,
      candidate,
      anchor,
      abstention
    })
      .filter(([, passed]) => !passed)
      .map(([name]) => name);
    if (failedDimensions.length > 0) {
      mismatches.push({
        packetId: opinion.packetId,
        sampleId: packet.sampleId,
        failedDimensions
      });
    }
  }
  const total = TRACK_B_HOLDOUT_COUNTS.eligible;
  const percentages = Object.fromEntries(
    Object.entries(dimensions).map(([name, count]) => [
      `${name}Percent`,
      (count * 100) / total
    ])
  );
  return {
    evaluator,
    opinionCount: payload.opinions.length,
    correctCounts: dimensions,
    percentages,
    mismatchCount: mismatches.length,
    mismatches,
    allDimensions100Percent: Object.values(percentages).every(
      (value) => value === 100
    )
  };
}

function validateHumanGroundTruth(
  groundTruth,
  corpus,
  corpusSha256,
  profile
) {
  assert.equal(
    groundTruth.schemaVersion,
    profile.groundTruthSchema
  );
  assert.equal(groundTruth.status, "ACCEPTED_HUMAN_GROUND_TRUTH");
  assert.equal(groundTruth.corpusSha256, corpusSha256);
  assert.equal(groundTruth.entryCount, TRACK_B_HOLDOUT_COUNTS.total);
  assert.equal(groundTruth.modelInputCreated, false);
  assert.equal(groundTruth.networkCallAllowedByThisSeal, false);
  assert.ok(Array.isArray(groundTruth.entries));
  assert.equal(groundTruth.entries.length, TRACK_B_HOLDOUT_COUNTS.total);
  const packetById = new Map(
    corpus.packets.map((packet) => [packet.packetId, packet])
  );
  const seenPacketIds = new Set();
  const seenCaseIds = new Set();
  for (const entry of groundTruth.entries) {
    const packet = packetById.get(entry.packetId);
    assert.ok(packet);
    assert.equal(entry.caseId, packet.sampleId);
    assert.ok(!seenPacketIds.has(entry.packetId));
    assert.ok(!seenCaseIds.has(entry.caseId));
    seenPacketIds.add(entry.packetId);
    seenCaseIds.add(entry.caseId);
    validateExpected(entry.expected, packet);
  }
  assert.equal(seenPacketIds.size, TRACK_B_HOLDOUT_COUNTS.total);
  return groundTruth;
}

function validateExpected(expected, packet) {
  assert.deepEqual(Object.keys(expected).sort(), [
    "suggestedRole",
    "selectedOccurrenceIds",
    "selectedAnchorBlockIds",
    "abstain",
    "abstentionReason"
  ].sort());
  assert.equal(typeof expected.abstain, "boolean");
  assert.ok(Array.isArray(expected.selectedOccurrenceIds));
  assert.ok(Array.isArray(expected.selectedAnchorBlockIds));
  const occurrenceById = new Map(
    packet.candidateOccurrences.map((candidate) => [
      candidate.occurrenceId,
      candidate
    ])
  );
  assert.ok(
    expected.selectedOccurrenceIds.every((occurrenceId) =>
      occurrenceById.has(occurrenceId)
    )
  );
  const anchors = [
    ...new Set(
      expected.selectedOccurrenceIds.map(
        (occurrenceId) =>
          occurrenceById.get(occurrenceId).sourceAnchor.blockId
      )
    )
  ];
  assert.deepEqual(expected.selectedAnchorBlockIds, anchors);
  if (expected.abstain) {
    assert.equal(expected.suggestedRole, null);
    assert.deepEqual(expected.selectedOccurrenceIds, []);
    assert.deepEqual(expected.selectedAnchorBlockIds, []);
    assert.equal(typeof expected.abstentionReason, "string");
    assert.ok(expected.abstentionReason);
  } else {
    assert.equal(expected.suggestedRole, packet.candidateRole);
    assert.ok(expected.selectedOccurrenceIds.length > 0);
    assert.equal(expected.abstentionReason, null);
  }
}

function arraysEqual(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}
