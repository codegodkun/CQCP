import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { isAbsolute, relative, resolve } from "node:path";

const [repoArg, confirmationArg, mode = "create"] =
  process.argv.slice(2);
if (!repoArg || !confirmationArg || !["create", "verify"].includes(mode)) {
  throw new Error(
    "Usage: node seal-track-b-identity-migration.mjs " +
      "<repo-root> <identity-confirmation-path> <create|verify>"
  );
}
const repoRoot = resolve(repoArg);
const confirmationPath = resolve(confirmationArg);
const confirmationRelativePath = relativePathWithinRepo(
  repoRoot,
  confirmationPath
);
const challengeRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/" +
  "identity-migration-challenge.json";
const oldCorpusRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v1/corpus.json";
const oldGroundTruthRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v1/human-ground-truth.json";
const newCorpusRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/corpus.json";
const newDraftRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/human-review-draft.json";
const newReviewRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/" +
  "human-ground-truth-review.md";
const sealRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/" +
  "human-ground-truth.json";
const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const canonicalBytes = (value) =>
  Buffer.from(JSON.stringify(value), "utf8");
const readRelative = async (relativePath) =>
  readFile(resolve(repoRoot, ...relativePath.split("/")));

const [
  confirmationBytes,
  challengeBytes,
  oldCorpusBytes,
  oldGroundTruthBytes,
  newCorpusBytes,
  newDraftBytes,
  newReviewBytes
] = await Promise.all([
  readFile(confirmationPath),
  readRelative(challengeRelativePath),
  readRelative(oldCorpusRelativePath),
  readRelative(oldGroundTruthRelativePath),
  readRelative(newCorpusRelativePath),
  readRelative(newDraftRelativePath),
  readRelative(newReviewRelativePath)
]);
const confirmation = JSON.parse(confirmationBytes.toString("utf8"));
const challenge = JSON.parse(challengeBytes.toString("utf8"));
const oldCorpus = JSON.parse(oldCorpusBytes.toString("utf8"));
const oldGroundTruth = JSON.parse(
  oldGroundTruthBytes.toString("utf8")
);
const newCorpus = JSON.parse(newCorpusBytes.toString("utf8"));
const newDraft = JSON.parse(newDraftBytes.toString("utf8"));

assert.deepEqual(
  Object.keys(confirmation).sort(),
  [
    "schemaVersion",
    "confirmed",
    "challengeNonce",
    "challengeSha256",
    "oldCorpusSha256",
    "newCorpusSha256",
    "oldGroundTruthSha256",
    "expectedDecisionSetSha256",
    "decision",
    "confirmedBy",
    "confirmedAt",
    "confirmationSource",
    "confirmationStatement",
    "externalEgressAuthorized"
  ].sort()
);
assert.equal(
  confirmation.schemaVersion,
  "task-eval-002-track-b-identity-migration-confirmation-v1"
);
assert.equal(confirmation.confirmed, true);
assert.equal(
  confirmation.decision,
  "CONFIRM_IDENTITY_ONLY_CARRY_FORWARD"
);
assert.equal(
  confirmation.confirmationSource,
  "CODEX_THREAD_USER_CONFIRMATION"
);
assert.equal(confirmation.externalEgressAuthorized, false);
assert.equal(confirmation.confirmedBy, "CQCP_PROJECT_OWNER");
assert.doesNotMatch(
  confirmation.confirmedBy,
  /(codex|agent|model|artificial intelligence|\bai\b)/i
);
assert.equal(
  challenge.schemaVersion,
  "task-eval-002-track-b-identity-migration-challenge-v1"
);
assert.equal(
  challenge.status,
  "PENDING_HUMAN_IDENTITY_ONLY_CONFIRMATION"
);
assert.equal(challenge.externalEgressAuthorized, false);
assert.equal(confirmation.challengeNonce, challenge.nonce);
assert.equal(confirmation.challengeSha256, sha256(challengeBytes));
assert.equal(confirmation.oldCorpusSha256, sha256(oldCorpusBytes));
assert.equal(confirmation.newCorpusSha256, sha256(newCorpusBytes));
assert.equal(
  confirmation.oldGroundTruthSha256,
  sha256(oldGroundTruthBytes)
);
assert.equal(
  confirmation.expectedDecisionSetSha256,
  challenge.expectedDecisionSetSha256
);
assert.equal(challenge.oldCorpusSha256, sha256(oldCorpusBytes));
assert.equal(challenge.newCorpusSha256, sha256(newCorpusBytes));
assert.equal(
  challenge.oldGroundTruthSha256,
  sha256(oldGroundTruthBytes)
);
assert.ok(isCanonicalIso(confirmation.confirmedAt));
assert.ok(
  Date.parse(confirmation.confirmedAt) >= Date.parse(challenge.createdAt)
);
assert.ok(
  Date.parse(confirmation.confirmedAt) <= Date.parse(challenge.expiresAt)
);
assert.ok(Date.parse(confirmation.confirmedAt) <= Date.now() + 300_000);
for (const required of [
  challenge.nonce,
  confirmation.challengeSha256,
  confirmation.oldCorpusSha256,
  confirmation.newCorpusSha256,
  "不授权公网外发"
]) {
  assert.ok(confirmation.confirmationStatement.includes(required));
}

const oldPacketByCase = uniqueBy(
  oldCorpus.packets,
  (packet) => packet.sampleId
);
const newPacketByCase = uniqueBy(
  newCorpus.packets,
  (packet) => packet.sampleId
);
const oldDecisionByCase = uniqueBy(
  oldGroundTruth.entries,
  (entry) => entry.caseId
);
const newDraftByCase = uniqueBy(
  newDraft.entries,
  (entry) => entry.caseId
);
const mappingByCase = uniqueBy(
  challenge.mappings,
  (entry) => entry.caseId
);
const entries = [];
for (const caseId of [...oldPacketByCase.keys()].sort()) {
  const oldPacket = oldPacketByCase.get(caseId);
  const newPacket = newPacketByCase.get(caseId);
  const oldDecision = oldDecisionByCase.get(caseId);
  const newDraftEntry = newDraftByCase.get(caseId);
  const mapping = mappingByCase.get(caseId);
  assert.ok(newPacket && oldDecision && newDraftEntry && mapping);
  const oldSemanticBytes = canonicalBytes(
    withoutRuntimeIdentity(oldPacket)
  );
  const newSemanticBytes = canonicalBytes(
    withoutRuntimeIdentity(newPacket)
  );
  const expectedBytes = canonicalBytes(oldDecision.expected);
  assert.ok(oldSemanticBytes.equals(newSemanticBytes));
  assert.ok(
    expectedBytes.equals(
      canonicalBytes(newDraftEntry.proposedExpected)
    )
  );
  assert.deepEqual(mapping, {
    caseId,
    oldPacketId: oldPacket.packetId,
    newPacketId: newPacket.packetId,
    oldTaskId: oldPacket.taskId,
    oldExecutionId: oldPacket.executionId,
    newTaskId: newPacket.taskId,
    newExecutionId: newPacket.executionId,
    semanticSha256: sha256(oldSemanticBytes),
    expectedSha256: sha256(expectedBytes)
  });
  entries.push({
    caseId,
    packetId: newPacket.packetId,
    expected: oldDecision.expected
  });
}
assert.equal(entries.length, 18);
const expectedDecisionProjection = entries.map(({ caseId, expected }) => ({
  caseId,
  expected
}));
assert.equal(
  sha256(canonicalBytes(expectedDecisionProjection)),
  challenge.expectedDecisionSetSha256
);

const seal = {
  schemaVersion:
    "task-eval-002-track-b-human-ground-truth-v2",
  status: "ACCEPTED_HUMAN_GROUND_TRUTH",
  confirmationKind: "IDENTITY_ONLY_CARRY_FORWARD",
  confirmedBy: confirmation.confirmedBy,
  confirmedAt: confirmation.confirmedAt,
  confirmationSource: confirmation.confirmationSource,
  oldCorpusPath: oldCorpusRelativePath,
  oldCorpusSha256: sha256(oldCorpusBytes),
  oldGroundTruthPath: oldGroundTruthRelativePath,
  oldGroundTruthSha256: sha256(oldGroundTruthBytes),
  corpusPath: newCorpusRelativePath,
  corpusSha256: sha256(newCorpusBytes),
  humanReviewDraftPath: newDraftRelativePath,
  humanReviewDraftSha256: sha256(newDraftBytes),
  humanReviewDocumentPath: newReviewRelativePath,
  humanReviewDocumentSha256: sha256(newReviewBytes),
  identityMigrationChallengePath: challengeRelativePath,
  identityMigrationChallengeSha256: sha256(challengeBytes),
  identityMigrationChallengeNonce: challenge.nonce,
  identityMigrationConfirmationPath: confirmationRelativePath,
  identityMigrationConfirmationSha256: sha256(confirmationBytes),
  expectedDecisionSetSha256: challenge.expectedDecisionSetSha256,
  decisionsSha256: sha256(canonicalBytes(entries)),
  entryCount: entries.length,
  externalEgressAuthorized: false,
  entries
};
const sealPath = resolve(
  repoRoot,
  ...sealRelativePath.split("/")
);
const sealBytes = Buffer.from(
  `${JSON.stringify(seal, null, 2)}\n`,
  "utf8"
);
if (mode === "create") {
  await writeFile(sealPath, sealBytes, { flag: "wx" });
} else {
  assert.ok(
    (await readFile(sealPath)).equals(sealBytes),
    "TRACK_B_IDENTITY_MIGRATION_SEAL_CHANGED"
  );
}
process.stdout.write(
  `${JSON.stringify({
    status:
      mode === "create"
        ? "ACCEPTED_HUMAN_GROUND_TRUTH"
        : "HUMAN_GROUND_TRUTH_VERIFIED",
    sealPath: sealRelativePath,
    sealSha256: sha256(sealBytes),
    corpusSha256: seal.corpusSha256,
    oldGroundTruthSha256: seal.oldGroundTruthSha256,
    entryCount: seal.entryCount,
    externalEgressAuthorized: seal.externalEgressAuthorized
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

function uniqueBy(values, keyOf) {
  assert.ok(Array.isArray(values));
  const result = new Map();
  for (const value of values) {
    const key = keyOf(value);
    assert.equal(typeof key, "string");
    assert.ok(key && !result.has(key));
    result.set(key, value);
  }
  return result;
}

function relativePathWithinRepo(root, candidate) {
  const value = relative(root, candidate);
  if (
    value === "" ||
    value === ".." ||
    value.startsWith(`..\\`) ||
    value.startsWith("../") ||
    isAbsolute(value)
  ) {
    throw new Error("CONFIRMATION_PATH_OUTSIDE_REPOSITORY");
  }
  return value.replaceAll("\\", "/");
}

function isCanonicalIso(value) {
  return (
    typeof value === "string" &&
    !Number.isNaN(Date.parse(value)) &&
    new Date(value).toISOString() === value
  );
}
