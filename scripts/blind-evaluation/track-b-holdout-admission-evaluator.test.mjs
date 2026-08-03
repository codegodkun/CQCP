import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { sha256 } from "./track-b-holdout-contract.mjs";
import { evaluateTrackBHoldoutAdmission } from "./track-b-holdout-admission-evaluator.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

async function fixture() {
  const corpusBytes = await readFile(
    resolve(repoRoot, "outputs/task-eval-002/track-b-holdout-v1/corpus.json")
  );
  const corpus = JSON.parse(corpusBytes.toString("utf8"));
  const proposals = JSON.parse(
    await readFile(
      resolve(
        repoRoot,
        "apps/api-server/src/test/resources/track-b-holdout-v1/" +
          "proposed-decisions.json"
      ),
      "utf8"
    )
  );
  const proposalByCase = new Map(
    proposals.entries.map((entry) => [entry.caseId, entry.proposedExpected])
  );
  const entries = corpus.packets.map((packet) => ({
    caseId: packet.sampleId,
    packetId: packet.packetId,
    expected: proposalByCase.get(packet.sampleId)
  }));
  const groundTruth = {
    schemaVersion: "task-eval-002-track-b-holdout-human-ground-truth-v1",
    status: "ACCEPTED_HUMAN_GROUND_TRUTH",
    corpusSha256: sha256(corpusBytes),
    entryCount: entries.length,
    entries,
    modelInputCreated: false,
    networkCallAllowedByThisSeal: false
  };
  const eligible = corpus.packets.filter(
    (packet) => packet.admission.modelCallAllowed
  );
  const payload = {
    opinions: eligible.map((packet) => ({
      packetId: packet.packetId,
      ...proposalByCase.get(packet.sampleId)
    }))
  };
  return {
    corpus,
    corpusSha256: sha256(corpusBytes),
    groundTruth,
    payload
  };
}

test("establishes admission only when both evaluators and controls are all exact", async () => {
  const data = await fixture();
  const result = evaluateTrackBHoldoutAdmission({
    corpus: data.corpus,
    corpusSha256: data.corpusSha256,
    humanGroundTruth: data.groundTruth,
    codexPayload: structuredClone(data.payload),
    deepSeekPayload: structuredClone(data.payload)
  });
  assert.equal(result.status, "GO_ALL_ADMISSION_DIMENSIONS_100_PERCENT");
  assert.equal(
    result.providerAdmission,
    "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE"
  );
  assert.equal(result.groundTruthCompatible, true);
  assert.equal(result.controlsPassed, true);
  assert.equal(result.evaluators.length, 2);
  assert.ok(
    result.evaluators.every(
      (entry) =>
        entry.allDimensions100Percent &&
        Object.values(entry.percentages).every((value) => value === 100)
    )
  );
});

test("a structurally valid wrong candidate fixes admission at NOT_ESTABLISHED", async () => {
  const data = await fixture();
  const wrong = structuredClone(data.payload);
  let changed = false;
  for (const opinion of wrong.opinions) {
    const packet = data.corpus.packets.find(
      (candidate) => candidate.packetId === opinion.packetId
    );
    const alternative = packet.candidateOccurrences.find(
      (candidate) =>
        packet.admission.requiredBlockIds.includes(
          candidate.sourceAnchor.blockId
        ) &&
        !opinion.selectedOccurrenceIds.includes(candidate.occurrenceId)
    );
    if (!alternative) continue;
    opinion.selectedOccurrenceIds = [alternative.occurrenceId];
    opinion.selectedAnchorBlockIds = [alternative.sourceAnchor.blockId];
    changed = true;
    break;
  }
  assert.equal(changed, true, "test corpus needs a valid alternative candidate");
  const result = evaluateTrackBHoldoutAdmission({
    corpus: data.corpus,
    corpusSha256: data.corpusSha256,
    humanGroundTruth: data.groundTruth,
    codexPayload: structuredClone(data.payload),
    deepSeekPayload: wrong
  });
  assert.equal(result.status, "NO_GO_MODEL_MISMATCH");
  assert.equal(result.providerAdmission, "NOT_ESTABLISHED");
  assert.equal(result.allGatesPassed, false);
  const deepSeek = result.evaluators.find(
    (entry) => entry.evaluator === "DEEPSEEK_V4_PRO"
  );
  assert.equal(deepSeek.allDimensions100Percent, false);
  assert.equal(deepSeek.mismatchCount, 1);
  assert.ok(deepSeek.percentages.candidatePercent < 100);
  assert.ok(deepSeek.percentages.anchorPercent < 100);
});
