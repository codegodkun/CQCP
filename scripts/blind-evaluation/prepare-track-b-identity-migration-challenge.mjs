import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const [repoArg, createdAtArg, expiresAtArg, nonce] =
  process.argv.slice(2);
if (
  !repoArg ||
  !isCanonicalIso(createdAtArg) ||
  !isCanonicalIso(expiresAtArg) ||
  !/^TBM-[a-f0-9]{32}$/.test(nonce ?? "")
) {
  throw new Error(
    "Usage: node prepare-track-b-identity-migration-challenge.mjs " +
      "<repo-root> <created-at> <expires-at> <TBM-nonce>"
  );
}
if (
  Date.parse(expiresAtArg) <= Date.parse(createdAtArg) ||
  Date.parse(createdAtArg) > Date.now() + 300_000
) {
  throw new Error("TRACK_B_IDENTITY_MIGRATION_TIME_INVALID");
}

const repoRoot = resolve(repoArg);
const oldCorpusPath =
  "outputs/task-eval-002/track-b-admission-corpus-v1/corpus.json";
const oldGroundTruthPath =
  "outputs/task-eval-002/track-b-admission-corpus-v1/human-ground-truth.json";
const newCorpusPath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/corpus.json";
const newDraftPath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/human-review-draft.json";
const newReviewPath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/human-ground-truth-review.md";
const outputPath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/" +
  "identity-migration-challenge.json";
const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const canonicalBytes = (value) =>
  Buffer.from(JSON.stringify(value), "utf8");
const readRelative = async (relativePath) =>
  readFile(resolve(repoRoot, ...relativePath.split("/")));

const [
  oldCorpusBytes,
  oldGroundTruthBytes,
  newCorpusBytes,
  newDraftBytes,
  newReviewBytes
] = await Promise.all([
  readRelative(oldCorpusPath),
  readRelative(oldGroundTruthPath),
  readRelative(newCorpusPath),
  readRelative(newDraftPath),
  readRelative(newReviewPath)
]);
const oldCorpus = JSON.parse(oldCorpusBytes.toString("utf8"));
const oldGroundTruth = JSON.parse(
  oldGroundTruthBytes.toString("utf8")
);
const newCorpus = JSON.parse(newCorpusBytes.toString("utf8"));
const newDraft = JSON.parse(newDraftBytes.toString("utf8"));

assert.equal(
  oldGroundTruth.status,
  "ACCEPTED_HUMAN_GROUND_TRUTH"
);
assert.equal(oldGroundTruth.corpusSha256, sha256(oldCorpusBytes));
assert.equal(oldCorpus.packetCount, 18);
assert.equal(newCorpus.packetCount, 18);
assert.equal(oldGroundTruth.entryCount, 18);
assert.equal(newDraft.entries.length, 18);

const oldPackets = uniqueBy(
  oldCorpus.packets,
  (packet) => packet.sampleId,
  "OLD_PACKET_CASE_DUPLICATED"
);
const newPackets = uniqueBy(
  newCorpus.packets,
  (packet) => packet.sampleId,
  "NEW_PACKET_CASE_DUPLICATED"
);
const oldExpected = uniqueBy(
  oldGroundTruth.entries,
  (entry) => entry.caseId,
  "OLD_EXPECTED_CASE_DUPLICATED"
);
const newExpected = uniqueBy(
  newDraft.entries,
  (entry) => entry.caseId,
  "NEW_EXPECTED_CASE_DUPLICATED"
);

const mappings = [];
for (const caseId of [...oldPackets.keys()].sort()) {
  const oldPacket = oldPackets.get(caseId);
  const newPacket = newPackets.get(caseId);
  const oldDecision = oldExpected.get(caseId);
  const newDecision = newExpected.get(caseId);
  if (!newPacket || !oldDecision || !newDecision) {
    throw new Error(`TRACK_B_IDENTITY_MIGRATION_CASE_MISSING:${caseId}`);
  }
  const oldSemanticBytes = canonicalBytes(
    withoutRuntimeIdentity(oldPacket)
  );
  const newSemanticBytes = canonicalBytes(
    withoutRuntimeIdentity(newPacket)
  );
  const oldExpectedBytes = canonicalBytes(oldDecision.expected);
  const newExpectedBytes = canonicalBytes(
    newDecision.proposedExpected
  );
  if (!oldSemanticBytes.equals(newSemanticBytes)) {
    throw new Error(
      `TRACK_B_IDENTITY_MIGRATION_SEMANTIC_DRIFT:${caseId}`
    );
  }
  if (!oldExpectedBytes.equals(newExpectedBytes)) {
    throw new Error(
      `TRACK_B_IDENTITY_MIGRATION_EXPECTED_DRIFT:${caseId}`
    );
  }
  mappings.push({
    caseId,
    oldPacketId: oldPacket.packetId,
    newPacketId: newPacket.packetId,
    oldTaskId: oldPacket.taskId,
    oldExecutionId: oldPacket.executionId,
    newTaskId: newPacket.taskId,
    newExecutionId: newPacket.executionId,
    semanticSha256: sha256(oldSemanticBytes),
    expectedSha256: sha256(oldExpectedBytes)
  });
}
if (
  mappings.length !== 18 ||
  newPackets.size !== 18 ||
  oldExpected.size !== 18 ||
  newExpected.size !== 18 ||
  new Set(mappings.map((entry) => entry.oldPacketId)).size !== 18 ||
  new Set(mappings.map((entry) => entry.newPacketId)).size !== 18
) {
  throw new Error("TRACK_B_IDENTITY_MIGRATION_COUNT_INVALID");
}

const oldDecisionProjection = [...oldExpected.values()]
  .sort((left, right) => left.caseId.localeCompare(right.caseId))
  .map((entry) => ({ caseId: entry.caseId, expected: entry.expected }));
const newDecisionProjection = [...newExpected.values()]
  .sort((left, right) => left.caseId.localeCompare(right.caseId))
  .map((entry) => ({
    caseId: entry.caseId,
    expected: entry.proposedExpected
  }));
const oldDecisionBytes = canonicalBytes(oldDecisionProjection);
const newDecisionBytes = canonicalBytes(newDecisionProjection);
assert.ok(
  oldDecisionBytes.equals(newDecisionBytes),
  "TRACK_B_IDENTITY_MIGRATION_DECISION_SET_DRIFT"
);

const challenge = {
  schemaVersion:
    "task-eval-002-track-b-identity-migration-challenge-v1",
  status: "PENDING_HUMAN_IDENTITY_ONLY_CONFIRMATION",
  nonce,
  createdAt: createdAtArg,
  expiresAt: expiresAtArg,
  migrationScope:
    "TASK_EXECUTION_PACKET_ID_ONLY_SEMANTICS_AND_EXPECTED_UNCHANGED",
  oldCorpusPath,
  oldCorpusSha256: sha256(oldCorpusBytes),
  oldGroundTruthPath,
  oldGroundTruthSha256: sha256(oldGroundTruthBytes),
  oldConfirmedBy: oldGroundTruth.confirmedBy,
  oldConfirmedAt: oldGroundTruth.confirmedAt,
  newCorpusPath,
  newCorpusSha256: sha256(newCorpusBytes),
  newHumanReviewDraftPath: newDraftPath,
  newHumanReviewDraftSha256: sha256(newDraftBytes),
  newHumanReviewDocumentPath: newReviewPath,
  newHumanReviewDocumentSha256: sha256(newReviewBytes),
  entryCount: mappings.length,
  semanticMatchCount: mappings.length,
  expectedMatchCount: mappings.length,
  expectedDecisionSetSha256: sha256(oldDecisionBytes),
  mappings,
  confirmationRequired: true,
  externalEgressAuthorized: false,
  instruction:
    "仅确认 taskId/executionId/packetId 迁移；18 条语义与人工 expected 均未变化。该确认不授权公网外发。"
};
const challengeBytes = Buffer.from(
  `${JSON.stringify(challenge, null, 2)}\n`,
  "utf8"
);
await writeFile(resolve(repoRoot, ...outputPath.split("/")), challengeBytes, {
  flag: "wx"
});
process.stdout.write(
  `${JSON.stringify({
    status: challenge.status,
    nonce,
    challengePath: outputPath,
    challengeSha256: sha256(challengeBytes),
    oldCorpusSha256: challenge.oldCorpusSha256,
    newCorpusSha256: challenge.newCorpusSha256,
    oldGroundTruthSha256: challenge.oldGroundTruthSha256,
    expectedDecisionSetSha256: challenge.expectedDecisionSetSha256,
    entryCount: challenge.entryCount,
    semanticMatchCount: challenge.semanticMatchCount,
    expectedMatchCount: challenge.expectedMatchCount
  })}\n`
);

function withoutRuntimeIdentity(packet) {
  const {
    packetId: _packetId,
    taskId: _taskId,
    executionId: _executionId,
    ...semantic
  } = packet;
  return semantic;
}

function uniqueBy(values, keyOf, errorCode) {
  const result = new Map();
  for (const value of values) {
    const key = keyOf(value);
    if (typeof key !== "string" || !key || result.has(key)) {
      throw new Error(errorCode);
    }
    result.set(key, value);
  }
  return result;
}

function isCanonicalIso(value) {
  if (typeof value !== "string" || Number.isNaN(Date.parse(value))) {
    return false;
  }
  return new Date(value).toISOString() === value;
}
