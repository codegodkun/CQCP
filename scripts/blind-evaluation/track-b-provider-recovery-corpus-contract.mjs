import assert from "node:assert/strict";

import { validateTrackBRecoveryCorpus } from
  "./track-b-holdout-contract.mjs";

export function validateTrackBProviderRecoveryIndependence({
  recoveryCorpus,
  priorCorpora,
  diagnosticInputs
}) {
  validateTrackBRecoveryCorpus(recoveryCorpus);
  assert.equal(priorCorpora.length, 5);
  assert.equal(diagnosticInputs.length, 2);

  const current = projection(recoveryCorpus.packets);
  const priorProjections = [
    ...priorCorpora.map((corpus) => projection(corpus.packets)),
    ...diagnosticInputs.map((input) => projection(input.packets))
  ];
  const prior = {
    identities: new Set(
      priorProjections.flatMap((item) => [...item.identities])
    ),
    values: new Set(
      priorProjections.flatMap((item) => [...item.values])
    ),
    evidence: new Set(
      priorProjections.flatMap((item) => [...item.evidence])
    )
  };
  const result = {
    comparedPriorCorpusCount: priorCorpora.length,
    diagnosticInputCount: diagnosticInputs.length,
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
      if (value !== null && value !== undefined) identities.add(value);
    }
    for (const occurrence of packet.candidateOccurrences ?? []) {
      const candidateValue = occurrence.candidateValue ?? occurrence.value;
      const evidenceText = occurrence.evidenceText ?? occurrence.evidence;
      if (candidateValue !== undefined) values.add(candidateValue);
      if (evidenceText !== undefined) evidence.add(evidenceText);
    }
  }
  return { identities, values, evidence };
}

const overlap = (left, right) =>
  [...left].filter((item) => right.has(item)).length;
