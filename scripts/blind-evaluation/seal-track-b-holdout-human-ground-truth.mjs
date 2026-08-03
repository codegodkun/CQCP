import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  jsonBytes,
  sha256,
  validateTrackBFifthCorpus,
  validateTrackBFinalCorpus,
  validateTrackBHoldoutCorpus,
  validateTrackBSuccessorCorpus
} from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const confirmationArg = process.argv[3];
const mode = process.argv[4] ?? "create";
const profileArg = process.argv[5] ?? "holdout";
if (!confirmationArg) {
  throw new Error(
    "Usage: node seal-track-b-holdout-human-ground-truth.mjs " +
      "<repo-root> <confirmation-path> [create|verify]"
  );
}

const profiles = Object.freeze({
  holdout: Object.freeze({
    outputRoot: "outputs/task-eval-002/track-b-holdout-v1",
    challengeSchema:
      "task-eval-002-track-b-holdout-human-challenge-v1",
    confirmationSchema:
      "task-eval-002-track-b-holdout-human-confirmation-v1",
    sealSchema:
      "task-eval-002-track-b-holdout-human-ground-truth-v1",
    validateCorpus: validateTrackBHoldoutCorpus
  }),
  successor: Object.freeze({
    outputRoot: "outputs/task-eval-003/track-b-successor-v1",
    challengeSchema:
      "task-eval-003-track-b-successor-human-challenge-v1",
    confirmationSchema:
      "task-eval-003-track-b-successor-human-confirmation-v1",
    sealSchema:
      "task-eval-003-track-b-successor-human-ground-truth-v1",
    validateCorpus: validateTrackBSuccessorCorpus
  }),
  final: Object.freeze({
    outputRoot: "outputs/task-eval-004/track-b-final-v1",
    challengeSchema:
      "task-eval-004-track-b-final-human-challenge-v1",
    confirmationSchema:
      "task-eval-004-track-b-final-human-confirmation-v1",
    sealSchema:
      "task-eval-004-track-b-final-human-ground-truth-v1",
    validateCorpus: validateTrackBFinalCorpus
  }),
  fifth: Object.freeze({
    outputRoot: "outputs/task-eval-005/track-b-fifth-v1",
    challengeSchema:
      "task-eval-005-track-b-fifth-human-challenge-v1",
    confirmationSchema:
      "task-eval-005-track-b-fifth-human-confirmation-v1",
    sealSchema:
      "task-eval-005-track-b-fifth-human-ground-truth-v1",
    validateCorpus: validateTrackBFifthCorpus
  })
});
const profile = profiles[profileArg];
if (!profile) throw new Error(`Unknown profile: ${profileArg}`);
const outputRoot = profile.outputRoot;
const corpusRelativePath = `${outputRoot}/corpus.json`;
const draftRelativePath = `${outputRoot}/human-review-draft.json`;
const reviewRelativePath =
  `${outputRoot}/human-ground-truth-review.md`;
const manifestRelativePath = `${outputRoot}/preseal-manifest.json`;
const challengeRelativePath =
  `${outputRoot}/human-confirmation-challenge.json`;
const sealRelativePath = `${outputRoot}/human-ground-truth.json`;
const corpusBytes = await readRelative(corpusRelativePath);
const draftBytes = await readRelative(draftRelativePath);
const reviewBytes = await readRelative(reviewRelativePath);
const manifestBytes = await readRelative(manifestRelativePath);
const challengeBytes = await readRelative(challengeRelativePath);
const confirmationBytes = await readFile(
  resolve(repoRoot, confirmationArg)
);
const corpus = profile.validateCorpus(
  JSON.parse(corpusBytes.toString("utf8"))
);
const draft = JSON.parse(draftBytes.toString("utf8"));
const challenge = JSON.parse(challengeBytes.toString("utf8"));
const confirmation = JSON.parse(confirmationBytes.toString("utf8"));
assert.equal(
  draft.status,
  "PENDING_HUMAN_CONFIRMATION_NOT_GROUND_TRUTH"
);
assert.equal(draft.humanGroundTruthEstablished, false);
assert.equal(draft.corpusSha256, sha256(corpusBytes));
assert.ok(draft.entries.every((entry) => entry.humanDecision === null));
assert.equal(
  challenge.schemaVersion,
  profile.challengeSchema
);
assert.equal(
  challenge.status,
  "AWAITING_EXPLICIT_HUMAN_DECISIONS"
);
assert.equal(challenge.corpusSha256, sha256(corpusBytes));
assert.equal(challenge.humanReviewDraftSha256, sha256(draftBytes));
assert.equal(challenge.humanReviewDocumentSha256, sha256(reviewBytes));
assert.equal(challenge.presealManifestSha256, sha256(manifestBytes));
assert.equal(challenge.requiredDecisionCount, 12);
assert.equal(challenge.proposedAnswersAreNonAuthoritative, true);
assert.deepEqual(Object.keys(confirmation).sort(), [
  "challengeNonce",
  "challengeSha256",
  "confirmationSource",
  "confirmationStatement",
  "confirmed",
  "confirmedAt",
  "confirmedBy",
  "corpusSha256",
  "decisions",
  "decisionsSha256",
  "humanReviewDraftSha256",
  "humanReviewDocumentSha256",
  "presealManifestSha256",
  "schemaVersion"
].sort());
assert.equal(
  confirmation.schemaVersion,
  profile.confirmationSchema
);
assert.equal(confirmation.confirmed, true);
assert.equal(confirmation.challengeNonce, challenge.nonce);
assert.equal(confirmation.challengeSha256, sha256(challengeBytes));
assert.equal(confirmation.corpusSha256, sha256(corpusBytes));
assert.equal(
  confirmation.humanReviewDraftSha256,
  sha256(draftBytes)
);
assert.equal(
  confirmation.humanReviewDocumentSha256,
  sha256(reviewBytes)
);
assert.equal(
  confirmation.presealManifestSha256,
  sha256(manifestBytes)
);
assert.ok(Array.isArray(confirmation.decisions));
assert.equal(confirmation.decisions.length, 12);
assert.equal(
  confirmation.decisionsSha256,
  sha256(Buffer.from(JSON.stringify(confirmation.decisions), "utf8"))
);
const packetByCaseId = new Map(
  corpus.packets.map((packet) => [packet.sampleId, packet])
);
const seenCaseIds = new Set();
const seenPacketIds = new Set();
for (const decision of confirmation.decisions) {
  validateDecision(
    decision,
    packetByCaseId,
    seenCaseIds,
    seenPacketIds
  );
}
assert.equal(seenCaseIds.size, 12);
assert.equal(seenPacketIds.size, 12);
assert.equal(
  confirmation.confirmationSource,
  "CODEX_THREAD_USER_CONFIRMATION"
);
assert.equal(typeof confirmation.confirmedBy, "string");
assert.ok(confirmation.confirmedBy.trim());
assert.doesNotMatch(
  confirmation.confirmedBy,
  /(codex|agent|model|artificial intelligence|\bai\b)/i,
  "AI agents cannot establish human ground truth"
);
const confirmedAt = canonicalIso(
  confirmation.confirmedAt,
  "confirmedAt"
);
assert.ok(Date.parse(confirmedAt) >= Date.parse(challenge.createdAt));
assert.ok(Date.parse(confirmedAt) <= Date.parse(challenge.expiresAt));
assert.ok(Date.parse(confirmedAt) <= Date.now() + 300_000);
assert.equal(typeof confirmation.confirmationStatement, "string");
assert.ok(
  confirmation.confirmationStatement.includes(challenge.nonce) &&
    confirmation.confirmationStatement.includes(
      challenge.corpusSha256
    )
);
const seal = {
  schemaVersion: profile.sealSchema,
  status: "ACCEPTED_HUMAN_GROUND_TRUTH",
  confirmedBy: confirmation.confirmedBy,
  confirmedAt,
  confirmationSource: confirmation.confirmationSource,
  corpusPath: corpusRelativePath,
  corpusSha256: sha256(corpusBytes),
  humanReviewDraftPath: draftRelativePath,
  humanReviewDraftSha256: sha256(draftBytes),
  humanReviewDocumentPath: reviewRelativePath,
  humanReviewDocumentSha256: sha256(reviewBytes),
  presealManifestPath: manifestRelativePath,
  presealManifestSha256: sha256(manifestBytes),
  challengePath: challengeRelativePath,
  challengeSha256: sha256(challengeBytes),
  challengeNonce: challenge.nonce,
  confirmationPath: confirmationArg.replaceAll("\\", "/"),
  confirmationSha256: sha256(confirmationBytes),
  decisionsSha256: confirmation.decisionsSha256,
  entryCount: confirmation.decisions.length,
  entries: confirmation.decisions,
  modelInputCreated: false,
  networkCallAllowedByThisSeal: false
};
const sealPath = resolve(
  repoRoot,
  ...sealRelativePath.split("/")
);
if (mode === "create") {
  await writeFile(sealPath, jsonBytes(seal), { flag: "wx" });
  process.stdout.write(
    `${JSON.stringify({
      status: seal.status,
      corpusSha256: seal.corpusSha256,
      entryCount: seal.entryCount,
      modelInputCreated: false,
      networkCallAllowedByThisSeal: false
    })}\n`
  );
} else if (mode === "verify") {
  const sealed = JSON.parse(await readFile(sealPath, "utf8"));
  assert.deepEqual(sealed, seal, "Human ground-truth seal changed");
  process.stdout.write(
    `${JSON.stringify({
      status: "HUMAN_GROUND_TRUTH_VERIFIED",
      corpusSha256: seal.corpusSha256,
      entryCount: seal.entryCount,
      sealSha256: sha256(await readFile(sealPath))
    })}\n`
  );
} else {
  throw new Error(`Unknown mode: ${mode}`);
}

function validateDecision(
  decision,
  packetByCaseId,
  seenCaseIds,
  seenPacketIds
) {
  assert.deepEqual(Object.keys(decision).sort(), [
    "caseId",
    "expected",
    "packetId"
  ]);
  assert.ok(!seenCaseIds.has(decision.caseId));
  assert.ok(!seenPacketIds.has(decision.packetId));
  const packet = packetByCaseId.get(decision.caseId);
  assert.ok(packet, `Unknown caseId: ${decision.caseId}`);
  assert.equal(decision.packetId, packet.packetId);
  seenCaseIds.add(decision.caseId);
  seenPacketIds.add(decision.packetId);
  const expected = decision.expected;
  assert.deepEqual(Object.keys(expected).sort(), [
    "abstain",
    "abstentionReason",
    "selectedAnchorBlockIds",
    "selectedOccurrenceIds",
    "suggestedRole"
  ]);
  const occurrenceById = new Map(
    packet.candidateOccurrences.map((candidate) => [
      candidate.occurrenceId,
      candidate
    ])
  );
  assert.ok(
    expected.selectedOccurrenceIds.every((occurrenceId) =>
      occurrenceById.has(occurrenceId)
    )
  );
  const derivedAnchors = [
    ...new Set(
      expected.selectedOccurrenceIds.map(
        (occurrenceId) =>
          occurrenceById.get(occurrenceId).sourceAnchor.blockId
      )
    )
  ];
  assert.deepEqual(expected.selectedAnchorBlockIds, derivedAnchors);
  if (expected.abstain) {
    assert.equal(expected.suggestedRole, null);
    assert.deepEqual(expected.selectedOccurrenceIds, []);
    assert.deepEqual(expected.selectedAnchorBlockIds, []);
    assert.equal(typeof expected.abstentionReason, "string");
    assert.ok(expected.abstentionReason.trim());
  } else {
    assert.equal(expected.suggestedRole, packet.candidateRole);
    assert.ok(expected.selectedOccurrenceIds.length > 0);
    assert.equal(expected.abstentionReason, null);
  }
  if (!packet.admission.modelCallAllowed) {
    assert.equal(expected.abstain, true);
    assert.equal(
      expected.abstentionReason,
      packet.admission.reasonCodes[0]
    );
  }
}

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
