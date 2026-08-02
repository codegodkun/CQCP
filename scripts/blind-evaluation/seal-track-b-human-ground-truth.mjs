import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const repoRoot = resolve(process.argv[2] ?? ".");
const confirmationArg = process.argv[3];
const mode = process.argv[4] ?? "create";
if (!confirmationArg) {
  throw new Error(
    "Usage: node seal-track-b-human-ground-truth.mjs " +
      "<repo-root> <confirmation-path> [create|verify]"
  );
}

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
const sealRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/human-ground-truth.json";
const confirmationPath = resolve(repoRoot, confirmationArg);
const corpusPath = resolve(repoRoot, corpusRelativePath);
const draftPath = resolve(repoRoot, draftRelativePath);
const reviewDocumentPath = resolve(repoRoot, reviewDocumentRelativePath);
const challengePath = resolve(repoRoot, challengeRelativePath);
const sealPath = resolve(repoRoot, sealRelativePath);
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

const corpusBytes = await readFile(corpusPath);
const draftBytes = await readFile(draftPath);
const reviewDocumentBytes = await readFile(reviewDocumentPath);
const challengeBytes = await readFile(challengePath);
const confirmationBytes = await readFile(confirmationPath);
const corpus = JSON.parse(corpusBytes.toString("utf8"));
const draft = JSON.parse(draftBytes.toString("utf8"));
const challenge = JSON.parse(challengeBytes.toString("utf8"));
const confirmation = JSON.parse(confirmationBytes.toString("utf8"));

assert.equal(
  corpus.schemaVersion,
  "task-eval-002-track-b-admission-corpus-v1"
);
assert.equal(corpus.groundTruthIncluded, false);
assert.equal(corpus.packetCount, 18);
assert.equal(
  draft.schemaVersion,
  "task-eval-002-track-b-human-review-draft-v1"
);
assert.equal(draft.status, "PENDING_HUMAN_CONFIRMATION_NOT_GROUND_TRUTH");
assert.equal(draft.humanGroundTruthEstablished, false);
assert.equal(
  draft.corpusSha256,
  sha256(corpusBytes),
  "Draft corpusSha256 does not match current corpus"
);
assert.equal(draft.entries?.length, 18);
assert.ok(
  draft.entries.every((entry) => entry.humanDecision === null),
  "Draft must remain unmodified and non-authoritative"
);

assert.equal(
  challenge.schemaVersion,
  "task-eval-002-track-b-human-challenge-v1"
);
assert.equal(
  challenge.status,
  "AWAITING_EXPLICIT_HUMAN_DECISIONS"
);
assert.equal(challenge.corpusSha256, sha256(corpusBytes));
assert.equal(challenge.humanReviewDraftSha256, sha256(draftBytes));
assert.equal(
  challenge.humanReviewDocumentSha256,
  sha256(reviewDocumentBytes),
  "challenge humanReviewDocumentSha256 does not match current Markdown"
);
assert.equal(challenge.requiredDecisionCount, 18);
assert.equal(challenge.proposedAnswersAreNonAuthoritative, true);
assert.deepEqual(
  Object.keys(confirmation).sort(),
  [
    "schemaVersion",
    "confirmed",
    "challengeNonce",
    "challengeSha256",
    "corpusSha256",
    "humanReviewDraftSha256",
    "humanReviewDocumentSha256",
    "decisionsSha256",
    "decisions",
    "confirmedBy",
    "confirmedAt",
    "confirmationSource",
    "confirmationStatement"
  ].sort(),
  "Confirmation fields are invalid"
);
assert.equal(
  confirmation.schemaVersion,
  "task-eval-002-track-b-human-confirmation-v1"
);
assert.equal(confirmation.confirmed, true);
assert.equal(confirmation.challengeNonce, challenge.nonce);
assert.equal(confirmation.challengeSha256, sha256(challengeBytes));
assert.equal(confirmation.corpusSha256, sha256(corpusBytes));
assert.equal(confirmation.humanReviewDraftSha256, sha256(draftBytes));
assert.equal(
  confirmation.humanReviewDocumentSha256,
  sha256(reviewDocumentBytes),
  "humanReviewDocumentSha256 does not match the reviewed Markdown"
);
assert.ok(Array.isArray(confirmation.decisions));
assert.equal(confirmation.decisions.length, 18);
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
assert.equal(seenCaseIds.size, 18);
assert.equal(seenPacketIds.size, 18);
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
const confirmedAtMillis = Date.parse(confirmation.confirmedAt);
assert.ok(
  isCanonicalIso(confirmation.confirmedAt),
  "confirmedAt must be canonical ISO-8601"
);
assert.ok(
  confirmedAtMillis >= Date.parse(challenge.createdAt),
  "confirmedAt cannot precede the human challenge"
);
assert.ok(
  confirmedAtMillis <= Date.parse(challenge.expiresAt),
  "confirmedAt cannot be after challenge expiry"
);
assert.ok(
  confirmedAtMillis <= Date.now() + 300_000,
  "confirmedAt cannot be in the future"
);
assert.equal(typeof confirmation.confirmationStatement, "string");
assert.ok(
  confirmation.confirmationStatement.includes(challenge.nonce) &&
    confirmation.confirmationStatement.includes(
      challenge.corpusSha256
    ),
  "confirmationStatement must bind the challenge nonce and corpus SHA"
);

const sealCore = {
  schemaVersion: "task-eval-002-track-b-human-ground-truth-v1",
  status: "ACCEPTED_HUMAN_GROUND_TRUTH",
  confirmedBy: confirmation.confirmedBy,
  confirmedAt: new Date(confirmation.confirmedAt).toISOString(),
  confirmationSource: confirmation.confirmationSource,
  corpusPath: corpusRelativePath,
  corpusSha256: sha256(corpusBytes),
  humanReviewDraftPath: draftRelativePath,
  humanReviewDraftSha256: sha256(draftBytes),
  humanReviewDocumentPath: reviewDocumentRelativePath,
  humanReviewDocumentSha256: sha256(reviewDocumentBytes),
  challengePath: challengeRelativePath,
  challengeSha256: sha256(challengeBytes),
  challengeNonce: challenge.nonce,
  confirmationPath: confirmationArg.replaceAll("\\", "/"),
  confirmationSha256: sha256(confirmationBytes),
  decisionsSha256: confirmation.decisionsSha256,
  entryCount: confirmation.decisions.length,
  entries: confirmation.decisions
};

if (mode === "create") {
  try {
    await writeFile(
      sealPath,
      `${JSON.stringify(sealCore, null, 2)}\n`,
      { encoding: "utf8", flag: "wx" }
    );
  } catch (error) {
    if (error?.code === "EEXIST") {
      throw new Error(
        "Human ground truth seal is immutable and already exists"
      );
    }
    throw error;
  }
  process.stdout.write(
    `${JSON.stringify({
      status: sealCore.status,
      corpusSha256: sealCore.corpusSha256,
      entryCount: sealCore.entryCount
    })}\n`
  );
} else if (mode === "verify") {
  const sealed = JSON.parse(await readFile(sealPath, "utf8"));
  assert.deepEqual(sealed, sealCore, "Human ground truth seal changed");
  process.stdout.write(
    `${JSON.stringify({
      status: "HUMAN_GROUND_TRUTH_VERIFIED",
      corpusSha256: sealCore.corpusSha256,
      entryCount: sealCore.entryCount,
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
  assert.deepEqual(
    Object.keys(decision).sort(),
    ["caseId", "packetId", "expected"].sort()
  );
  assert.equal(typeof decision.caseId, "string");
  assert.equal(typeof decision.packetId, "string");
  assert.ok(!seenCaseIds.has(decision.caseId));
  assert.ok(!seenPacketIds.has(decision.packetId));
  const packet = packetByCaseId.get(decision.caseId);
  assert.ok(packet, `Unknown caseId: ${decision.caseId}`);
  assert.equal(decision.packetId, packet.packetId);
  seenCaseIds.add(decision.caseId);
  seenPacketIds.add(decision.packetId);

  const expected = decision.expected;
  assert.ok(expected && typeof expected === "object");
  assert.deepEqual(
    Object.keys(expected).sort(),
    [
      "suggestedRole",
      "selectedOccurrenceIds",
      "selectedAnchorBlockIds",
      "abstain",
      "abstentionReason"
    ].sort()
  );
  assert.equal(typeof expected.abstain, "boolean");
  assert.ok(Array.isArray(expected.selectedOccurrenceIds));
  assert.ok(Array.isArray(expected.selectedAnchorBlockIds));
  assert.ok(
    expected.selectedOccurrenceIds.every(
      (occurrenceId) => typeof occurrenceId === "string"
    )
  );
  assert.ok(
    expected.selectedAnchorBlockIds.every(
      (blockId) => typeof blockId === "string"
    )
  );
  assert.equal(
    new Set(expected.selectedOccurrenceIds).size,
    expected.selectedOccurrenceIds.length
  );
  const occurrenceById = new Map(
    packet.candidateOccurrences.map((occurrence) => [
      occurrence.occurrenceId,
      occurrence
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
    assert.ok(expected.selectedAnchorBlockIds.length > 0);
    assert.equal(expected.abstentionReason, null);
  }

  if (packet.admission.modelCallAllowed === false) {
    assert.equal(expected.abstain, true);
    assert.equal(
      expected.abstentionReason,
      packet.admission.reasonCodes[0]
    );
  }
}

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
