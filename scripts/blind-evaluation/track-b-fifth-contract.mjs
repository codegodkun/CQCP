import assert from "node:assert/strict";

import {
  validateTrackBFifthCorpus
} from "./track-b-holdout-contract.mjs";
import {
  validateTrackBSchemaDiagnosticInput
} from "./track-b-schema-diagnostic-contract.mjs";

export function validateTrackBFifthIndependence({
  fifthCorpus,
  priorCorpora,
  diagnosticInput
}) {
  validateTrackBFifthCorpus(fifthCorpus);
  assert.equal(priorCorpora.length, 4);
  validateTrackBSchemaDiagnosticInput(diagnosticInput);
  const current = projection(fifthCorpus.packets);
  const comparison = [
    ...priorCorpora.map((corpus) => projection(corpus.packets)),
    projection(diagnosticInput.packets)
  ];
  const prior = {
    identities: new Set(comparison.flatMap((item) => [...item.identities])),
    values: new Set(comparison.flatMap((item) => [...item.values])),
    evidence: new Set(comparison.flatMap((item) => [...item.evidence]))
  };
  const result = {
    comparedPriorCorpusCount: priorCorpora.length,
    diagnosticInputCompared: true,
    identityOverlapCount: overlap(current.identities, prior.identities),
    candidateValueOverlapCount: overlap(current.values, prior.values),
    evidenceTextOverlapCount: overlap(current.evidence, prior.evidence)
  };
  assert.equal(result.identityOverlapCount, 0);
  assert.equal(result.candidateValueOverlapCount, 0);
  assert.equal(result.evidenceTextOverlapCount, 0);
  return result;
}

function projection(packets) {
  const identities = new Set();
  const values = new Set();
  const evidence = new Set();
  for (const packet of packets ?? []) {
    for (const value of [
      packet.packetId,
      packet.taskId,
      packet.executionId,
      packet.sampleId
    ]) {
      identities.add(value);
    }
    for (const occurrence of packet.candidateOccurrences ?? []) {
      values.add(occurrence.candidateValue);
      evidence.add(occurrence.evidenceText);
    }
  }
  identities.delete(null);
  identities.delete(undefined);
  return { identities, values, evidence };
}

const overlap = (left, right) =>
  [...left].filter((item) => right.has(item)).length;
