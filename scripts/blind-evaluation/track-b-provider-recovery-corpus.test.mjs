import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  buildTrackBRecoveryArtifacts,
  jsonBytes,
  sha256,
  validateTrackBRecoveryCorpus
} from "./track-b-holdout-contract.mjs";
import {
  buildTrackBProviderRecoverySourceArtifacts
} from "./prepare-track-b-provider-recovery-source.mjs";
import {
  validateTrackBProviderRecoveryIndependence
} from "./track-b-provider-recovery-corpus-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

test("builds a sixth 5 MEDIUM + 4 CONFLICTED + 3 zero-call corpus with new identities", () => {
  const { source, proposals } =
    buildTrackBProviderRecoverySourceArtifacts();
  const artifacts = buildTrackBRecoveryArtifacts({
    source,
    sourcePath:
      "apps/api-server/src/test/resources/track-b-recovery-v1/source-signals.json",
    sourceSha256: sha256(jsonBytes(source)),
    proposals,
    proposalsPath:
      "apps/api-server/src/test/resources/track-b-recovery-v1/proposed-decisions.json",
    proposalsSha256: sha256(jsonBytes(proposals)),
    generatedAt: "2026-08-03T13:00:00.000Z"
  });
  const corpus = validateTrackBRecoveryCorpus(artifacts.corpus);

  assert.equal(corpus.packetCount, 12);
  assert.equal(corpus.mediumEligibleCount, 5);
  assert.equal(corpus.conflictedEligibleCount, 4);
  assert.equal(corpus.zeroCallControlCount, 3);
  assert.equal(
    corpus.packets.filter((packet) => packet.admission.modelCallAllowed)
      .length,
    9
  );
  assert.ok(corpus.packets.every((packet) => packet.sampleId.startsWith("TB6-")));
  assert.ok(
    corpus.packets.every((packet) => packet.taskId.startsWith("task-eval-006-"))
  );
  assert.equal(proposals.entries.length, 12);
  assert.ok(proposals.entries.every((entry) => entry.proposedExpected));
});

test("is disjoint from all five prior corpora and both diagnostic input sets", async () => {
  const { source, proposals } =
    buildTrackBProviderRecoverySourceArtifacts();
  const corpus = buildTrackBRecoveryArtifacts({
    source,
    sourcePath: "source.json",
    sourceSha256: sha256(jsonBytes(source)),
    proposals,
    proposalsPath: "proposals.json",
    proposalsSha256: sha256(jsonBytes(proposals)),
    generatedAt: "2026-08-03T13:00:00.000Z"
  }).corpus;
  const readJson = async (relative) =>
    JSON.parse(
      await readFile(resolve(repoRoot, ...relative.split("/")), "utf8")
    );
  const priorCorpora = await Promise.all(
    [
      "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json",
      "outputs/task-eval-002/track-b-holdout-v1/corpus.json",
      "outputs/task-eval-003/track-b-successor-v1/corpus.json",
      "outputs/task-eval-004/track-b-final-v1/corpus.json",
      "outputs/task-eval-005/track-b-fifth-v1/corpus.json"
    ].map(readJson)
  );
  const diagnosticInputs = await Promise.all(
    [
      "outputs/task-eval-005/schema-diagnostic-v1/baseline/diagnostic-input.json",
      "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/model-input.json"
    ].map(readJson)
  );

  assert.deepEqual(
    validateTrackBProviderRecoveryIndependence({
      recoveryCorpus: corpus,
      priorCorpora,
      diagnosticInputs
    }),
    {
      comparedPriorCorpusCount: 5,
      diagnosticInputCount: 2,
      identityOverlapCount: 0,
      candidateValueOverlapCount: 0,
      evidenceTextOverlapCount: 0
    }
  );
});
