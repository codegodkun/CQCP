import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";
import { resolve } from "node:path";

import {
  buildTrackBSuccessorArtifacts,
  jsonBytes,
  sha256,
  validateTrackBSuccessorCorpus
} from "./track-b-holdout-contract.mjs";
import {
  buildTrackBSuccessorSourceArtifacts
} from "./prepare-track-b-successor-source.mjs";

const repoRoot = resolve(".");
const resourceRoot = resolve(
  repoRoot,
  "apps/api-server/src/test/resources/track-b-successor-v1"
);
const outputRoot = resolve(
  repoRoot,
  "outputs/task-eval-003/track-b-successor-v1"
);

test("successor preseal is reproducible and remains model-dark", async () => {
  const sourceBytes = await readFile(
    resolve(resourceRoot, "source-signals.json")
  );
  const proposalsBytes = await readFile(
    resolve(resourceRoot, "proposed-decisions.json")
  );
  const source = JSON.parse(sourceBytes.toString("utf8"));
  const proposals = JSON.parse(proposalsBytes.toString("utf8"));
  const expectedSource = buildTrackBSuccessorSourceArtifacts();
  assert.deepEqual(source, expectedSource.source);
  assert.deepEqual(proposals, expectedSource.proposals);

  const corpusBytes = await readFile(resolve(outputRoot, "corpus.json"));
  const draftBytes = await readFile(
    resolve(outputRoot, "human-review-draft.json")
  );
  const reviewBytes = await readFile(
    resolve(outputRoot, "human-ground-truth-review.md")
  );
  const manifestBytes = await readFile(
    resolve(outputRoot, "preseal-manifest.json")
  );
  const challengeBytes = await readFile(
    resolve(outputRoot, "human-confirmation-challenge.json")
  );
  const corpus = validateTrackBSuccessorCorpus(
    JSON.parse(corpusBytes.toString("utf8"))
  );
  const rebuilt = buildTrackBSuccessorArtifacts({
    source,
    sourcePath:
      "apps/api-server/src/test/resources/track-b-successor-v1/" +
      "source-signals.json",
    sourceSha256: sha256(sourceBytes),
    proposals,
    proposalsPath:
      "apps/api-server/src/test/resources/track-b-successor-v1/" +
      "proposed-decisions.json",
    proposalsSha256: sha256(proposalsBytes),
    generatedAt: corpus.generatedAt
  });
  assert.deepEqual(JSON.parse(corpusBytes.toString("utf8")), rebuilt.corpus);
  assert.deepEqual(JSON.parse(draftBytes.toString("utf8")), rebuilt.draft);
  assert.deepEqual(reviewBytes, Buffer.from(rebuilt.reviewMarkdown, "utf8"));

  const manifest = JSON.parse(manifestBytes.toString("utf8"));
  assert.equal(manifest.corpusSha256, sha256(corpusBytes));
  assert.equal(manifest.humanReviewDraftSha256, sha256(draftBytes));
  assert.equal(manifest.humanReviewDocumentSha256, sha256(reviewBytes));
  assert.equal(manifest.disjointness.identityOverlapCount, 0);
  assert.equal(manifest.disjointness.candidateValueOverlapCount, 0);
  assert.equal(manifest.disjointness.evidenceTextOverlapCount, 0);
  assert.equal(manifest.priorFailedClaimReused, false);
  assert.equal(manifest.modelInputCreated, false);
  assert.equal(manifest.networkCallAllowed, false);

  const challenge = JSON.parse(challengeBytes.toString("utf8"));
  assert.equal(challenge.corpusSha256, sha256(corpusBytes));
  assert.equal(challenge.humanReviewDraftSha256, sha256(draftBytes));
  assert.equal(challenge.humanReviewDocumentSha256, sha256(reviewBytes));
  assert.equal(challenge.presealManifestSha256, sha256(manifestBytes));
  assert.equal(challenge.requiredDecisionCount, 12);
  assert.equal(challenge.proposedAnswersAreNonAuthoritative, true);
  assert.equal(challenge.priorFailedClaimReused, false);
  assert.equal(challenge.modelInputCreated, false);
  assert.equal(challenge.networkCallAllowed, false);
  assert.ok(Date.parse(challenge.createdAt) >= Date.parse(corpus.generatedAt));
  assert.ok(Date.parse(challenge.expiresAt) > Date.parse(challenge.createdAt));
  assert.ok(
    Date.parse(challenge.expiresAt) - Date.parse(challenge.createdAt) <=
      172_800_000
  );

  for (const forbiddenPath of [
    "human-confirmation.json",
    "human-ground-truth.json",
    "run-v1/model-input.json",
    "run-v1/dispatch.json",
    "run-v1/deepseek-execution-claim.json"
  ]) {
    await assert.rejects(
      access(resolve(outputRoot, ...forbiddenPath.split("/")))
    );
  }
  assert.equal(jsonBytes(rebuilt.corpus).equals(corpusBytes), true);
});
