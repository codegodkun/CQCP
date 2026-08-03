import assert from "node:assert/strict";

import {
  validateTrackBFinalCorpus,
  validateTrackBSuccessorCorpus
} from "./track-b-holdout-contract.mjs";

export function validateTrackBSuccessorIndependence({
  successorCorpus,
  historicalCorpus,
  failedHoldoutCorpus
}) {
  validateTrackBSuccessorCorpus(successorCorpus);
  assert.equal(
    successorCorpus.source,
    "INDEPENDENT_DEIDENTIFIED_SYNTHETIC_RUNTIME_SIGNALS"
  );
  assert.equal(successorCorpus.containsProductionContractText, false);
  assert.equal(successorCorpus.groundTruthIncluded, false);

  const successor = identitySets(successorCorpus);
  const prior = mergeIdentitySets(
    identitySets(historicalCorpus),
    identitySets(failedHoldoutCorpus)
  );
  for (const key of Object.keys(successor)) {
    const overlap = [...successor[key]].filter((value) =>
      prior[key].has(value)
    );
    assert.deepEqual(
      overlap,
      [],
      `TRACK_B_SUCCESSOR_${key.toUpperCase()}_OVERLAP`
    );
  }
  assert.equal(
    successor.candidateValues.size,
    [...successorCorpus.packets]
      .flatMap((packet) => packet.candidateOccurrences).length,
    "Successor candidate values must be unique"
  );
  return {
    successorPacketCount: successor.packetIds.size,
    priorPacketCount: prior.packetIds.size,
    successorCandidateValueCount: successor.candidateValues.size,
    priorCandidateValueCount: prior.candidateValues.size,
    successorEvidenceTextCount: successor.evidenceTexts.size,
    priorEvidenceTextCount: prior.evidenceTexts.size,
    identityOverlapCount: 0,
    candidateValueOverlapCount: 0,
    evidenceTextOverlapCount: 0
  };
}

export function validateTrackBFinalIndependence({
  finalCorpus,
  historicalCorpus,
  failedHoldoutCorpus,
  failedSuccessorCorpus
}) {
  validateTrackBFinalCorpus(finalCorpus);
  assert.equal(
    finalCorpus.source,
    "INDEPENDENT_DEIDENTIFIED_SYNTHETIC_RUNTIME_SIGNALS"
  );
  assert.equal(finalCorpus.containsProductionContractText, false);
  assert.equal(finalCorpus.groundTruthIncluded, false);

  const current = identitySets(finalCorpus);
  const prior = mergeIdentitySets(
    identitySets(historicalCorpus),
    identitySets(failedHoldoutCorpus),
    identitySets(failedSuccessorCorpus)
  );
  for (const key of Object.keys(current)) {
    const overlap = [...current[key]].filter((value) =>
      prior[key].has(value)
    );
    assert.deepEqual(
      overlap,
      [],
      `TRACK_B_FINAL_${key.toUpperCase()}_OVERLAP`
    );
  }
  assert.equal(
    current.candidateValues.size,
    [...finalCorpus.packets]
      .flatMap((packet) => packet.candidateOccurrences).length,
    "Final candidate values must be unique"
  );
  return {
    finalPacketCount: current.packetIds.size,
    priorPacketCount: prior.packetIds.size,
    finalCandidateValueCount: current.candidateValues.size,
    priorCandidateValueCount: prior.candidateValues.size,
    finalEvidenceTextCount: current.evidenceTexts.size,
    priorEvidenceTextCount: prior.evidenceTexts.size,
    identityOverlapCount: 0,
    candidateValueOverlapCount: 0,
    evidenceTextOverlapCount: 0
  };
}

function identitySets(corpus) {
  assert.ok(Array.isArray(corpus?.packets));
  const candidateOccurrences = corpus.packets.flatMap(
    (packet) => packet.candidateOccurrences ?? []
  );
  return {
    packetIds: new Set(corpus.packets.map((packet) => packet.packetId)),
    sampleIds: new Set(corpus.packets.map((packet) => packet.sampleId)),
    taskIds: new Set(corpus.packets.map((packet) => packet.taskId)),
    executionIds: new Set(
      corpus.packets.map((packet) => packet.executionId)
    ),
    candidateValues: new Set(
      candidateOccurrences.map((candidate) => candidate.candidateValue)
    ),
    evidenceTexts: new Set(
      candidateOccurrences.map((candidate) => candidate.evidenceText)
    )
  };
}

function mergeIdentitySets(...sets) {
  const [first] = sets;
  return Object.fromEntries(
    Object.keys(first).map((key) => [
      key,
      new Set(sets.flatMap((set) => [...set[key]]))
    ])
  );
}
