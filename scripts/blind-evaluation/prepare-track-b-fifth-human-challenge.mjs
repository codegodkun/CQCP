import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  jsonBytes,
  sha256,
  validateTrackBFifthCorpus
} from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const createdAt = canonicalIso(process.argv[3], "createdAt");
const expiresAt = canonicalIso(process.argv[4], "expiresAt");
const nonce = process.argv[5];
assert.match(nonce ?? "", /^TB51-[a-f0-9]{32}$/);
assert.ok(Date.parse(expiresAt) > Date.parse(createdAt));
assert.ok(Date.parse(expiresAt) - Date.parse(createdAt) <= 172_800_000);

const root = "outputs/task-eval-005/track-b-fifth-v1";
const paths = {
  corpus: `${root}/corpus.json`,
  draft: `${root}/human-review-draft.json`,
  review: `${root}/human-ground-truth-review.md`,
  manifest: `${root}/preseal-manifest.json`,
  challenge: `${root}/human-confirmation-challenge.json`
};
const [corpusBytes, draftBytes, reviewBytes, manifestBytes] =
  await Promise.all([
    read(paths.corpus),
    read(paths.draft),
    read(paths.review),
    read(paths.manifest)
  ]);
const corpus = validateTrackBFifthCorpus(
  JSON.parse(corpusBytes.toString("utf8"))
);
const draft = JSON.parse(draftBytes.toString("utf8"));
const manifest = JSON.parse(manifestBytes.toString("utf8"));
assert.equal(draft.status, "PENDING_HUMAN_CONFIRMATION_NOT_GROUND_TRUTH");
assert.equal(draft.humanGroundTruthEstablished, false);
assert.equal(draft.entries.length, 12);
assert.ok(draft.entries.every((entry) => entry.humanDecision === null));
assert.equal(manifest.status, "AWAITING_HUMAN_REVIEW");
assert.equal(manifest.stabilityFreezeSha256.length, 64);
assert.equal(manifest.corpusSha256, sha256(corpusBytes));
assert.equal(manifest.humanReviewDraftSha256, sha256(draftBytes));
assert.equal(manifest.humanReviewDocumentSha256, sha256(reviewBytes));
assert.equal(manifest.disjointness.identityOverlapCount, 0);
assert.equal(manifest.disjointness.candidateValueOverlapCount, 0);
assert.equal(manifest.disjointness.evidenceTextOverlapCount, 0);
assert.equal(manifest.modelInputCreated, false);
assert.equal(manifest.evaluatorAccessAllowed, false);
assert.equal(manifest.admissionNetworkCallAllowed, false);
assert.equal(manifest.taskEval004Reused, false);
assert.equal(manifest.sixthCorpusAllowed, false);

const challenge = {
  schemaVersion: "task-eval-005-track-b-fifth-human-challenge-v1",
  status: "AWAITING_EXPLICIT_HUMAN_DECISIONS",
  nonce,
  createdAt,
  expiresAt,
  stabilityFreezePath: manifest.stabilityFreezePath,
  stabilityFreezeSha256: manifest.stabilityFreezeSha256,
  selectedPromptSha256: manifest.selectedPromptSha256,
  selectedSchemaVersion: manifest.selectedSchemaVersion,
  corpusPath: paths.corpus,
  corpusSha256: sha256(corpusBytes),
  humanReviewDraftPath: paths.draft,
  humanReviewDraftSha256: sha256(draftBytes),
  humanReviewDocumentPath: paths.review,
  humanReviewDocumentSha256: sha256(reviewBytes),
  presealManifestPath: paths.manifest,
  presealManifestSha256: sha256(manifestBytes),
  requiredDecisionCount: corpus.packetCount,
  confirmationSourceRequired: "CODEX_THREAD_USER_CONFIRMATION",
  proposedAnswersAreNonAuthoritative: true,
  priorClaimsReused: false,
  modelInputCreated: false,
  evaluatorAccessAllowed: false,
  admissionNetworkCallAllowed: false,
  sixthCorpusAllowed: false,
  instruction:
    "人工确认必须绑定本 challenge/corpus/review SHA 并逐项提供12条 decisions；" +
    "可接受、修改或拒答。确认前不得创建 model input、访问 evaluator 或联网。"
};
const bytes = jsonBytes(challenge);
await writeFile(relative(paths.challenge), bytes, { flag: "wx" });
process.stdout.write(`${JSON.stringify({
  status: challenge.status,
  nonce,
  challengeSha256: sha256(bytes),
  corpusSha256: challenge.corpusSha256,
  humanReviewDraftSha256: challenge.humanReviewDraftSha256,
  humanReviewDocumentSha256: challenge.humanReviewDocumentSha256,
  presealManifestSha256: challenge.presealManifestSha256,
  stabilityFreezeSha256: challenge.stabilityFreezeSha256,
  requiredDecisionCount: challenge.requiredDecisionCount,
  expiresAt,
  modelInputCreated: false,
  evaluatorAccessAllowed: false,
  admissionNetworkCallAllowed: false
})}\n`);

function relative(path) {
  return resolve(repoRoot, ...path.split("/"));
}
function read(path) {
  return readFile(relative(path));
}
function canonicalIso(value, field) {
  assert.equal(typeof value, "string", `${field} missing`);
  assert.equal(new Date(value).toISOString(), value);
  return value;
}
