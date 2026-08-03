import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

import {
  buildTrackBFifthArtifacts,
  jsonBytes,
  sha256,
  validateTrackBFifthCorpus
} from "./track-b-holdout-contract.mjs";
import {
  validateTrackBFifthIndependence
} from "./track-b-fifth-contract.mjs";
import {
  buildTrackBFifthSourceArtifacts
} from "./prepare-track-b-fifth-source.mjs";

test("fifth source builds 5 MEDIUM, 4 CONFLICTED and 3 zero-call controls", () => {
  const { source, proposals } = buildTrackBFifthSourceArtifacts();
  const sourceBytes = jsonBytes(source);
  const proposalsBytes = jsonBytes(proposals);
  const artifacts = buildTrackBFifthArtifacts({
    source,
    sourcePath:
      "apps/api-server/src/test/resources/track-b-fifth-v1/source-signals.json",
    sourceSha256: sha256(sourceBytes),
    proposals,
    proposalsPath:
      "apps/api-server/src/test/resources/track-b-fifth-v1/proposed-decisions.json",
    proposalsSha256: sha256(proposalsBytes),
    generatedAt: "2026-08-03T07:00:00.000Z"
  });
  validateTrackBFifthCorpus(artifacts.corpus);
  assert.equal(artifacts.corpus.mediumEligibleCount, 5);
  assert.equal(artifacts.corpus.conflictedEligibleCount, 4);
  assert.equal(artifacts.corpus.zeroCallControlCount, 3);
  assert.equal(artifacts.draft.humanGroundTruthEstablished, false);
});

test("fifth corpus is disjoint from four prior corpora and diagnostic input", async () => {
  const root = resolve(process.cwd());
  const { source, proposals } = buildTrackBFifthSourceArtifacts();
  const artifacts = buildTrackBFifthArtifacts({
    source,
    sourcePath:
      "apps/api-server/src/test/resources/track-b-fifth-v1/source-signals.json",
    sourceSha256: sha256(jsonBytes(source)),
    proposals,
    proposalsPath:
      "apps/api-server/src/test/resources/track-b-fifth-v1/proposed-decisions.json",
    proposalsSha256: sha256(jsonBytes(proposals)),
    generatedAt: "2026-08-03T07:00:00.000Z"
  });
  const priorCorpora = await Promise.all([
    "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json",
    "outputs/task-eval-002/track-b-holdout-v1/corpus.json",
    "outputs/task-eval-003/track-b-successor-v1/corpus.json",
    "outputs/task-eval-004/track-b-final-v1/corpus.json"
  ].map((path) => json(root, path)));
  const diagnosticInput = await json(
    root,
    "outputs/task-eval-005/schema-diagnostic-v1/baseline/diagnostic-input.json"
  );
  assert.deepEqual(validateTrackBFifthIndependence({
    fifthCorpus: artifacts.corpus,
    priorCorpora,
    diagnosticInput
  }), {
    comparedPriorCorpusCount: 4,
    diagnosticInputCompared: true,
    identityOverlapCount: 0,
    candidateValueOverlapCount: 0,
    evidenceTextOverlapCount: 0
  });
});

async function json(root, path) {
  return JSON.parse((await readFile(resolve(
    root,
    ...path.split("/")
  ))).toString("utf8"));
}
