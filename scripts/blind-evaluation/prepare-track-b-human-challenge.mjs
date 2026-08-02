import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import {
  mkdir,
  readFile,
  writeFile
} from "node:fs/promises";
import { dirname, resolve } from "node:path";

const repoRoot = resolve(process.argv[2] ?? ".");
const createdAtArg = process.argv[3];
const expiresAtArg = process.argv[4];
const nonce = process.argv[5];
if (
  !createdAtArg ||
  !expiresAtArg ||
  !nonce ||
  !isCanonicalIso(createdAtArg) ||
  !isCanonicalIso(expiresAtArg)
) {
  throw new Error(
    "Usage: node prepare-track-b-human-challenge.mjs " +
      "<repo-root> <created-at> <expires-at> <nonce>"
  );
}
assert.match(
  nonce,
  /^TBH-[a-f0-9]{32}$/,
  "challenge nonce must be an opaque TBH identifier"
);
const createdAt = new Date(createdAtArg).toISOString();
const expiresAt = new Date(expiresAtArg).toISOString();
assert.ok(Date.parse(expiresAt) > Date.parse(createdAt));
assert.ok(
  Date.parse(expiresAt) - Date.parse(createdAt) <= 172_800_000,
  "human confirmation challenge cannot be valid for more than 48 hours"
);

const corpusRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/corpus.json";
const draftRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/human-review-draft.json";
const reviewDocumentRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/" +
  "human-ground-truth-review.md";
const challengeRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/" +
  "human-confirmation-challenge.json";
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const readRelative = (relativePath) =>
  readFile(resolve(repoRoot, ...relativePath.split("/")));
const corpusBytes = await readRelative(corpusRelativePath);
const draftBytes = await readRelative(draftRelativePath);
const reviewDocumentBytes = await readRelative(reviewDocumentRelativePath);
const corpus = JSON.parse(corpusBytes.toString("utf8"));
const draft = JSON.parse(draftBytes.toString("utf8"));
assert.equal(
  corpus.schemaVersion,
  "task-eval-002-track-b-admission-corpus-v1"
);
assert.equal(corpus.packetCount, 18);
assert.equal(corpus.groundTruthIncluded, false);
assert.equal(
  draft.status,
  "PENDING_HUMAN_CONFIRMATION_NOT_GROUND_TRUTH"
);
assert.equal(draft.entries.length, 18);
assert.ok(draft.entries.every((entry) => entry.humanDecision === null));

const challenge = {
  schemaVersion: "task-eval-002-track-b-human-challenge-v1",
  status: "AWAITING_EXPLICIT_HUMAN_DECISIONS",
  nonce,
  createdAt,
  expiresAt,
  corpusPath: corpusRelativePath,
  corpusSha256: sha256(corpusBytes),
  humanReviewDraftPath: draftRelativePath,
  humanReviewDraftSha256: sha256(draftBytes),
  humanReviewDocumentPath: reviewDocumentRelativePath,
  humanReviewDocumentSha256: sha256(reviewDocumentBytes),
  requiredDecisionCount: 18,
  confirmationSourceRequired: "CODEX_THREAD_USER_CONFIRMATION",
  proposedAnswersAreNonAuthoritative: true,
  instruction:
    "人工确认必须逐项提供18条 decisions；可接受、修改或拒答，seal 不得从 Codex proposedExpected 复制答案。"
};
const challengePath = resolve(repoRoot, challengeRelativePath);
await mkdir(dirname(challengePath), { recursive: true });
await writeFile(
  challengePath,
  `${JSON.stringify(challenge, null, 2)}\n`,
  { encoding: "utf8", flag: "wx" }
);
process.stdout.write(
  `${JSON.stringify({
    status: challenge.status,
    nonce,
    corpusSha256: challenge.corpusSha256,
    humanReviewDocumentSha256: challenge.humanReviewDocumentSha256,
    challengeSha256: sha256(await readFile(challengePath)),
    expiresAt
  })}\n`
);

function isCanonicalIso(value) {
  if (
    typeof value !== "string" ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(value)
  ) {
    return false;
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) && new Date(parsed).toISOString() === value;
}
