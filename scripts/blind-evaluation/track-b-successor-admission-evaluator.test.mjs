import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import {
  evaluateTrackBSuccessorAdmission
} from "./track-b-holdout-admission-evaluator.mjs";
import { sha256 } from "./track-b-holdout-contract.mjs";

const outputRoot = new URL(
  "../../outputs/task-eval-003/track-b-successor-v1/",
  import.meta.url
);
const corpusBytes = await readFile(new URL("corpus.json", outputRoot));
const corpus = JSON.parse(corpusBytes.toString("utf8"));
const draft = JSON.parse(
  await readFile(new URL("human-review-draft.json", outputRoot), "utf8")
);
const groundTruth = {
  schemaVersion:
    "task-eval-003-track-b-successor-human-ground-truth-v1",
  status: "ACCEPTED_HUMAN_GROUND_TRUTH",
  corpusSha256: sha256(corpusBytes),
  entryCount: 12,
  entries: draft.entries.map((entry) => ({
    caseId: entry.caseId,
    packetId: entry.packetId,
    expected: entry.proposedExpected
  })),
  modelInputCreated: false,
  networkCallAllowedByThisSeal: false
};
const eligible = corpus.packets.filter(
  (packet) => packet.admission.modelCallAllowed
);
const exactOpinions = groundTruth.entries
  .filter((entry) =>
    eligible.some((packet) => packet.packetId === entry.packetId)
  )
  .map((entry) => ({ packetId: entry.packetId, ...entry.expected }));

test("successor admission requires both evaluators and all controls to be exact", () => {
  const evaluation = evaluateTrackBSuccessorAdmission({
    corpus,
    corpusSha256: sha256(corpusBytes),
    humanGroundTruth: groundTruth,
    codexPayload: { opinions: exactOpinions },
    deepSeekPayload: { opinions: exactOpinions }
  });
  assert.equal(
    evaluation.schemaVersion,
    "task-eval-003-track-b-successor-admission-evaluation-v1"
  );
  assert.equal(evaluation.allGatesPassed, true);
  assert.equal(
    evaluation.providerAdmission,
    "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE"
  );
  assert.equal(evaluation.controls.length, 3);
  assert.ok(evaluation.evaluators.every((item) => item.allDimensions100Percent));
});

test("one valid but wrong successor selection keeps admission not established", () => {
  const firstPacket = eligible[0];
  const expected = exactOpinions.find(
    (opinion) => opinion.packetId === firstPacket.packetId
  );
  const alternative = firstPacket.candidateOccurrences.find(
    (candidate) =>
      !expected.selectedOccurrenceIds.includes(candidate.occurrenceId)
  );
  assert.ok(alternative);
  const changed = exactOpinions.map((opinion) =>
    opinion.packetId === firstPacket.packetId
      ? {
          ...opinion,
          selectedOccurrenceIds: [alternative.occurrenceId],
          selectedAnchorBlockIds: [alternative.sourceAnchor.blockId]
        }
      : opinion
  );
  const evaluation = evaluateTrackBSuccessorAdmission({
    corpus,
    corpusSha256: sha256(corpusBytes),
    humanGroundTruth: groundTruth,
    codexPayload: { opinions: exactOpinions },
    deepSeekPayload: { opinions: changed }
  });
  assert.equal(evaluation.allGatesPassed, false);
  assert.equal(evaluation.providerAdmission, "NOT_ESTABLISHED");
  assert.equal(evaluation.status, "NO_GO_MODEL_MISMATCH");
  assert.equal(evaluation.evaluators[1].mismatchCount, 1);
});
