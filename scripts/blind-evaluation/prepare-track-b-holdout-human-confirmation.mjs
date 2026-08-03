import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  jsonBytes,
  sha256
} from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const expectedNonce = process.argv[3];
const expectedChallengeSha256 = process.argv[4];
const expectedCorpusSha256 = process.argv[5];
const expectedReviewSha256 = process.argv[6];
const confirmedAt = canonicalIso(process.argv[7], "confirmedAt");
const profileArg = process.argv[8] ?? "holdout";

if (
  !expectedNonce ||
  !expectedChallengeSha256 ||
  !expectedCorpusSha256 ||
  !expectedReviewSha256
) {
  throw new Error(
    "Usage: node prepare-track-b-holdout-human-confirmation.mjs " +
      "<repo-root> <nonce> <challenge-sha256> <corpus-sha256> " +
      "<review-sha256> <confirmed-at>"
  );
}

const profiles = Object.freeze({
  holdout: Object.freeze({
    outputRoot: "outputs/task-eval-002/track-b-holdout-v1",
    confirmationSchema:
      "task-eval-002-track-b-holdout-human-confirmation-v1"
  }),
  successor: Object.freeze({
    outputRoot: "outputs/task-eval-003/track-b-successor-v1",
    confirmationSchema:
      "task-eval-003-track-b-successor-human-confirmation-v1"
  }),
  final: Object.freeze({
    outputRoot: "outputs/task-eval-004/track-b-final-v1",
    confirmationSchema:
      "task-eval-004-track-b-final-human-confirmation-v1"
  })
});
const profile = profiles[profileArg];
if (!profile) throw new Error(`Unknown profile: ${profileArg}`);
const outputRoot = resolve(repoRoot, ...profile.outputRoot.split("/"));
const challengeBytes = await readFile(
  resolve(outputRoot, "human-confirmation-challenge.json")
);
const challenge = JSON.parse(challengeBytes.toString("utf8"));
const draft = JSON.parse(
  await readFile(resolve(outputRoot, "human-review-draft.json"), "utf8")
);

assert.equal(challenge.nonce, expectedNonce);
assert.equal(sha256(challengeBytes), expectedChallengeSha256);
assert.equal(challenge.corpusSha256, expectedCorpusSha256);
assert.equal(challenge.humanReviewDocumentSha256, expectedReviewSha256);
assert.equal(challenge.requiredDecisionCount, 12);
assert.equal(draft.corpusSha256, expectedCorpusSha256);
assert.equal(draft.entries.length, 12);
assert.ok(draft.entries.every((entry) => entry.humanDecision === null));
assert.ok(Date.parse(confirmedAt) >= Date.parse(challenge.createdAt));
assert.ok(Date.parse(confirmedAt) <= Date.parse(challenge.expiresAt));
assert.ok(Date.parse(confirmedAt) <= Date.now() + 300_000);

const decisions = draft.entries.map((entry) => ({
  caseId: entry.caseId,
  packetId: entry.packetId,
  expected: entry.proposedExpected
}));
const confirmation = {
  schemaVersion: profile.confirmationSchema,
  confirmed: true,
  challengeNonce: challenge.nonce,
  challengeSha256: sha256(challengeBytes),
  corpusSha256: challenge.corpusSha256,
  humanReviewDraftSha256: challenge.humanReviewDraftSha256,
  humanReviewDocumentSha256:
    challenge.humanReviewDocumentSha256,
  presealManifestSha256: challenge.presealManifestSha256,
  decisionsSha256: sha256(
    Buffer.from(JSON.stringify(decisions), "utf8")
  ),
  decisions,
  confirmedBy: "项目负责人",
  confirmedAt,
  confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
  confirmationStatement:
    `我已人工审阅并确认 challenge ${challenge.nonce}，` +
    `challenge SHA ${sha256(challengeBytes)}，` +
    `corpus SHA ${challenge.corpusSha256}，` +
    `review SHA ${challenge.humanReviewDocumentSha256}，` +
    "同意12条 proposedExpected全部作为我的人工 decisions，无修改。"
};

await writeFile(
  resolve(outputRoot, "human-confirmation.json"),
  jsonBytes(confirmation),
  { flag: "wx" }
);

process.stdout.write(
  `${JSON.stringify({
    status: "HUMAN_CONFIRMATION_RECORDED",
    challengeNonce: confirmation.challengeNonce,
    challengeSha256: confirmation.challengeSha256,
    corpusSha256: confirmation.corpusSha256,
    reviewSha256: confirmation.humanReviewDocumentSha256,
    decisionCount: confirmation.decisions.length,
    decisionsSha256: confirmation.decisionsSha256,
    confirmedAt
  })}\n`
);

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
