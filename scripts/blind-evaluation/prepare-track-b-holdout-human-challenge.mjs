import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  jsonBytes,
  sha256,
  validateTrackBHoldoutCorpus
} from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const createdAt = canonicalIso(process.argv[3], "createdAt");
const expiresAt = canonicalIso(process.argv[4], "expiresAt");
const nonce = process.argv[5];
assert.match(
  nonce ?? "",
  /^TBH2-[a-f0-9]{32}$/,
  "challenge nonce must be an opaque TBH2 identifier"
);
assert.ok(Date.parse(expiresAt) > Date.parse(createdAt));
assert.ok(
  Date.parse(expiresAt) - Date.parse(createdAt) <= 172_800_000,
  "human confirmation challenge cannot be valid for more than 48 hours"
);

const outputRoot = "outputs/task-eval-002/track-b-holdout-v1";
const corpusRelativePath = `${outputRoot}/corpus.json`;
const draftRelativePath = `${outputRoot}/human-review-draft.json`;
const reviewRelativePath =
  `${outputRoot}/human-ground-truth-review.md`;
const manifestRelativePath = `${outputRoot}/preseal-manifest.json`;
const challengeRelativePath =
  `${outputRoot}/human-confirmation-challenge.json`;
const corpusBytes = await readRelative(corpusRelativePath);
const draftBytes = await readRelative(draftRelativePath);
const reviewBytes = await readRelative(reviewRelativePath);
const manifestBytes = await readRelative(manifestRelativePath);
const corpus = validateTrackBHoldoutCorpus(
  JSON.parse(corpusBytes.toString("utf8"))
);
const draft = JSON.parse(draftBytes.toString("utf8"));
const manifest = JSON.parse(manifestBytes.toString("utf8"));
assert.equal(
  draft.status,
  "PENDING_HUMAN_CONFIRMATION_NOT_GROUND_TRUTH"
);
assert.equal(draft.humanGroundTruthEstablished, false);
assert.equal(draft.entries.length, 12);
assert.ok(draft.entries.every((entry) => entry.humanDecision === null));
assert.equal(draft.corpusSha256, sha256(corpusBytes));
assert.equal(manifest.corpusSha256, sha256(corpusBytes));
assert.equal(manifest.humanReviewDraftSha256, sha256(draftBytes));
assert.equal(manifest.humanReviewDocumentSha256, sha256(reviewBytes));
assert.equal(manifest.modelInputCreated, false);
assert.equal(manifest.networkCallAllowed, false);
const challenge = {
  schemaVersion:
    "task-eval-002-track-b-holdout-human-challenge-v1",
  status: "AWAITING_EXPLICIT_HUMAN_DECISIONS",
  nonce,
  createdAt,
  expiresAt,
  corpusPath: corpusRelativePath,
  corpusSha256: sha256(corpusBytes),
  humanReviewDraftPath: draftRelativePath,
  humanReviewDraftSha256: sha256(draftBytes),
  humanReviewDocumentPath: reviewRelativePath,
  humanReviewDocumentSha256: sha256(reviewBytes),
  presealManifestPath: manifestRelativePath,
  presealManifestSha256: sha256(manifestBytes),
  requiredDecisionCount: corpus.packetCount,
  confirmationSourceRequired: "CODEX_THREAD_USER_CONFIRMATION",
  proposedAnswersAreNonAuthoritative: true,
  modelInputCreated: false,
  networkCallAllowed: false,
  instruction:
    "人工确认必须逐项提供12条 decisions；可接受、修改或拒答。" +
    "确认前不得创建 model input 或发起任何模型调用。"
};
const challengeBytes = jsonBytes(challenge);
await writeFile(
  resolve(repoRoot, ...challengeRelativePath.split("/")),
  challengeBytes,
  { flag: "wx" }
);
process.stdout.write(
  `${JSON.stringify({
    status: challenge.status,
    nonce,
    corpusSha256: challenge.corpusSha256,
    humanReviewDraftSha256: challenge.humanReviewDraftSha256,
    humanReviewDocumentSha256:
      challenge.humanReviewDocumentSha256,
    presealManifestSha256: challenge.presealManifestSha256,
    challengeSha256: sha256(challengeBytes),
    requiredDecisionCount: challenge.requiredDecisionCount,
    expiresAt,
    modelInputCreated: false,
    networkCallAllowed: false
  })}\n`
);

function readRelative(relativePath) {
  return readFile(resolve(repoRoot, ...relativePath.split("/")));
}

function canonicalIso(value, field) {
  assert.equal(typeof value, "string", `${field} must be a string`);
  assert.match(
    value,
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/,
    `${field} must be canonical ISO-8601`
  );
  const parsed = Date.parse(value);
  assert.ok(Number.isFinite(parsed), `${field} is invalid`);
  assert.equal(new Date(parsed).toISOString(), value);
  return value;
}
