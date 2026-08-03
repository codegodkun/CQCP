import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { resolve } from "node:path";

import {
  buildTrackBHoldoutArtifacts,
  buildTrackBSuccessorArtifacts,
  jsonBytes,
  sha256,
  validateTrackBSuccessorCorpus
} from "./track-b-holdout-contract.mjs";
import {
  buildTrackBSuccessorSourceArtifacts
} from "./prepare-track-b-successor-source.mjs";
import {
  validateTrackBSuccessorIndependence
} from "./track-b-successor-contract.mjs";

const repoRoot = resolve(".");
const generatedAt = "2026-08-02T16:00:00.000Z";

test("successor builds 9 eligible and 3 disjoint controls", async () => {
  const { artifacts, historicalCorpus, failedHoldoutCorpus } =
    await fixture();
  validateTrackBSuccessorCorpus(artifacts.corpus);
  assert.equal(artifacts.corpus.packetCount, 12);
  assert.equal(artifacts.corpus.eligiblePacketCount, 9);
  assert.equal(artifacts.corpus.zeroCallControlCount, 3);
  assert.equal(
    artifacts.draft.corpusPath,
    "outputs/task-eval-003/track-b-successor-v1/corpus.json"
  );
  assert.match(
    artifacts.reviewMarkdown,
    /^# TASK-EVAL-003 Track B successor 12-packet 人工确认表/
  );
  const result = validateTrackBSuccessorIndependence({
    successorCorpus: artifacts.corpus,
    historicalCorpus,
    failedHoldoutCorpus
  });
  assert.deepEqual(
    {
      identityOverlapCount: result.identityOverlapCount,
      candidateValueOverlapCount: result.candidateValueOverlapCount,
      evidenceTextOverlapCount: result.evidenceTextOverlapCount
    },
    {
      identityOverlapCount: 0,
      candidateValueOverlapCount: 0,
      evidenceTextOverlapCount: 0
    }
  );
});

test("successor rejects historical candidate value reuse", async () => {
  const { artifacts, historicalCorpus, failedHoldoutCorpus } =
    await fixture();
  const changed = structuredClone(artifacts.corpus);
  changed.packets[0].candidateOccurrences[0].candidateValue = "650000";
  assert.throws(
    () =>
      validateTrackBSuccessorIndependence({
        successorCorpus: changed,
        historicalCorpus,
        failedHoldoutCorpus
      }),
    /CANDIDATEVALUES_OVERLAP/
  );
});

test("successor rejects failed holdout packet identity reuse", async () => {
  const { artifacts, historicalCorpus, failedHoldoutCorpus } =
    await fixture();
  const changed = structuredClone(artifacts.corpus);
  changed.packets[0].packetId = failedHoldoutCorpus.packets[0].packetId;
  assert.throws(
    () =>
      validateTrackBSuccessorIndependence({
        successorCorpus: changed,
        historicalCorpus,
        failedHoldoutCorpus
      }),
    /PACKETIDS_OVERLAP/
  );
});

async function fixture() {
  const sourceArtifacts = buildTrackBSuccessorSourceArtifacts();
  const sourceBytes = jsonBytes(sourceArtifacts.source);
  const proposalBytes = jsonBytes(sourceArtifacts.proposals);
  const artifacts = buildTrackBSuccessorArtifacts({
    source: sourceArtifacts.source,
    sourcePath:
      "apps/api-server/src/test/resources/track-b-successor-v1/" +
      "source-signals.json",
    sourceSha256: sha256(sourceBytes),
    proposals: sourceArtifacts.proposals,
    proposalsPath:
      "apps/api-server/src/test/resources/track-b-successor-v1/" +
      "proposed-decisions.json",
    proposalsSha256: sha256(proposalBytes),
    generatedAt
  });
  const historicalCorpus = JSON.parse(
    await readFile(
      resolve(
        repoRoot,
        "apps/api-server/src/test/resources/" +
          "track-b-admission-corpus-v2/corpus.json"
      ),
      "utf8"
    )
  );
  const holdoutSourceBytes = await readFile(
    resolve(
      repoRoot,
      "apps/api-server/src/test/resources/track-b-holdout-v1/" +
        "source-signals.json"
    )
  );
  const holdoutProposalBytes = await readFile(
    resolve(
      repoRoot,
      "apps/api-server/src/test/resources/track-b-holdout-v1/" +
        "proposed-decisions.json"
    )
  );
  const failedHoldoutCorpus = buildTrackBHoldoutArtifacts({
    source: JSON.parse(holdoutSourceBytes.toString("utf8")),
    sourcePath:
      "apps/api-server/src/test/resources/track-b-holdout-v1/" +
      "source-signals.json",
    sourceSha256: sha256(holdoutSourceBytes),
    proposals: JSON.parse(holdoutProposalBytes.toString("utf8")),
    proposalsPath:
      "apps/api-server/src/test/resources/track-b-holdout-v1/" +
      "proposed-decisions.json",
    proposalsSha256: sha256(holdoutProposalBytes),
    generatedAt
  }).corpus;
  return { artifacts, historicalCorpus, failedHoldoutCorpus };
}
