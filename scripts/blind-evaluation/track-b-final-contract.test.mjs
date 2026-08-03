import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

import {
  buildTrackBFinalArtifacts,
  jsonBytes,
  sha256,
  validateTrackBFinalCorpus
} from "./track-b-holdout-contract.mjs";
import {
  buildTrackBFinalSourceArtifacts
} from "./prepare-track-b-final-source.mjs";
import {
  validateTrackBFinalIndependence
} from "./track-b-successor-contract.mjs";

const repoRoot = resolve(".");

test("final source builds 12 runtime packets and is disjoint from all prior corpora", async () => {
  const { source, proposals } = buildTrackBFinalSourceArtifacts();
  assert.equal(source.caseCount, 12);
  assert.equal(source.groundTruthIncluded, false);
  assert.equal(proposals.humanGroundTruthEstablished, false);
  const sourceBytes = jsonBytes(source);
  const proposalBytes = jsonBytes(proposals);
  const artifacts = buildTrackBFinalArtifacts({
    source,
    sourcePath:
      "apps/api-server/src/test/resources/track-b-final-v1/" +
      "source-signals.json",
    sourceSha256: sha256(sourceBytes),
    proposals,
    proposalsPath:
      "apps/api-server/src/test/resources/track-b-final-v1/" +
      "proposed-decisions.json",
    proposalsSha256: sha256(proposalBytes),
    generatedAt: "2026-08-03T04:00:00.000Z"
  });
  const corpus = validateTrackBFinalCorpus(artifacts.corpus);
  assert.equal(corpus.packetCount, 12);
  assert.equal(corpus.eligiblePacketCount, 9);
  assert.equal(corpus.mediumEligibleCount, 5);
  assert.equal(corpus.conflictedEligibleCount, 4);
  assert.equal(corpus.zeroCallControlCount, 3);
  assert.deepEqual(
    corpus.packets
      .filter((packet) => packet.admission.modelCallAllowed)
      .map((packet) => packet.admission.requiredBlockIds),
    [
      ["tbf-med-001-b01", "tbf-med-001-b02"],
      ["tbf-med-002-b01", "tbf-med-002-b02"],
      ["tbf-med-003-table-01"],
      ["tbf-med-004-b01", "tbf-med-004-b02"],
      ["tbf-med-005-b01", "tbf-med-005-b02"],
      ["tbf-con-001-b01"],
      ["tbf-con-002-table-01"],
      ["tbf-con-003-b01", "tbf-con-003-b02"],
      ["tbf-con-004-b01"]
    ]
  );
  assert.deepEqual(
    corpus.packets
      .filter((packet) => !packet.admission.modelCallAllowed)
      .map((packet) => packet.admission.reasonCodes[0])
      .sort(),
    [
      "BUNDLE_INVALID",
      "DETERMINISTIC_HIGH_ZERO_CALL",
      "RELIABLE_ANCHOR_MISSING"
    ]
  );
  const disjointness = validateTrackBFinalIndependence({
    finalCorpus: corpus,
    historicalCorpus: await readJson(
      "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json"
    ),
    failedHoldoutCorpus: await readJson(
      "outputs/task-eval-002/track-b-holdout-v1/corpus.json"
    ),
    failedSuccessorCorpus: await readJson(
      "outputs/task-eval-003/track-b-successor-v1/corpus.json"
    )
  });
  assert.equal(disjointness.identityOverlapCount, 0);
  assert.equal(disjointness.candidateValueOverlapCount, 0);
  assert.equal(disjointness.evidenceTextOverlapCount, 0);
  assert.equal(disjointness.finalCandidateValueCount, 23);
});

async function readJson(relativePath) {
  return JSON.parse(
    await readFile(resolve(repoRoot, ...relativePath.split("/")), "utf8")
  );
}
