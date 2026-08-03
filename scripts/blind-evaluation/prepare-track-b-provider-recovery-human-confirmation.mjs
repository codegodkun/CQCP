import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import { jsonBytes, sha256 } from "./track-b-holdout-contract.mjs";

const [
  repoArg = ".",
  expectedNonce,
  expectedChallengeSha256,
  expectedCorpusSha256,
  expectedReviewDraftSha256,
  confirmedAtArg
] = process.argv.slice(2);
const repoRoot = resolve(repoArg);
const confirmedAt = canonicalIso(confirmedAtArg);
const outputRoot = resolve(
  repoRoot,
  "outputs/task-eval-006/track-b-recovery-v1"
);
const challengeBytes = await readFile(
  resolve(outputRoot, "human-confirmation-challenge.json")
);
const draftBytes = await readFile(
  resolve(outputRoot, "human-review-draft.json")
);
const challenge = JSON.parse(challengeBytes.toString("utf8"));
const draft = JSON.parse(draftBytes.toString("utf8"));
assert.equal(challenge.nonce, expectedNonce);
assert.equal(sha256(challengeBytes), expectedChallengeSha256);
assert.equal(challenge.corpusSha256, expectedCorpusSha256);
assert.equal(challenge.humanReviewDraftSha256, expectedReviewDraftSha256);
assert.equal(sha256(draftBytes), expectedReviewDraftSha256);
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
  schemaVersion: "task-eval-006-track-b-recovery-human-confirmation-v1",
  confirmed: true,
  challengeNonce: challenge.nonce,
  challengeSha256: sha256(challengeBytes),
  corpusSha256: challenge.corpusSha256,
  humanReviewDraftSha256: challenge.humanReviewDraftSha256,
  humanReviewDocumentSha256: challenge.humanReviewDocumentSha256,
  presealManifestSha256: challenge.presealManifestSha256,
  decisionsSha256: sha256(Buffer.from(JSON.stringify(decisions), "utf8")),
  decisions,
  confirmedBy: "项目负责人",
  confirmedAt,
  confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
  confirmationStatement:
    `我已人工审阅并确认 challenge ${challenge.nonce}，` +
    `challenge SHA ${sha256(challengeBytes)}，` +
    `corpus SHA ${challenge.corpusSha256}，` +
    `review SHA ${challenge.humanReviewDraftSha256}，` +
    "同意12条 proposedExpected 全部作为我的人工 decisions，无修改。"
};
const bytes = jsonBytes(confirmation);
await writeFile(resolve(outputRoot, "human-confirmation.json"), bytes, {
  flag: "wx"
});
process.stdout.write(`${JSON.stringify({
  status: "HUMAN_CONFIRMATION_RECORDED",
  challengeNonce: confirmation.challengeNonce,
  challengeSha256: confirmation.challengeSha256,
  corpusSha256: confirmation.corpusSha256,
  reviewSha256: confirmation.humanReviewDraftSha256,
  decisionCount: confirmation.decisions.length,
  decisionsSha256: confirmation.decisionsSha256,
  confirmationSha256: sha256(bytes),
  confirmedAt
})}\n`);

function canonicalIso(value) {
  assert.equal(typeof value, "string");
  assert.equal(new Date(value).toISOString(), value);
  return value;
}
