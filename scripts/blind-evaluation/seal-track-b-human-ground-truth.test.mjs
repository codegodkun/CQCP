import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  mkdir,
  mkdtemp,
  readFile,
  rm,
  writeFile
} from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const generatorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-admission-corpus.mjs"
);
const sealPath = path.resolve(
  scriptRoot,
  "seal-track-b-human-ground-truth.mjs"
);
const challengeGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-human-challenge.mjs"
);
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

test("human confirmation is hash-bound, immutable, and required before ground truth sealing", async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), "cqcp-track-b-human-"));
  try {
    let result = spawnSync(
      process.execPath,
      [generatorPath, root, "2026-07-29T07:00:00.000Z"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    result = spawnSync(
      process.execPath,
      [
        challengeGeneratorPath,
        root,
        "2026-07-29T07:05:00.000Z",
        "2026-07-30T07:05:00.000Z",
        "TBH-0123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const outputRoot = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-corpus-v2"
    );
    const corpusBytes = await readFile(path.join(outputRoot, "corpus.json"));
    const draftBytes = await readFile(
      path.join(outputRoot, "human-review-draft.json")
    );
    const reviewDocumentPath = path.join(
      outputRoot,
      "human-ground-truth-review.md"
    );
    const reviewDocumentBytes = await readFile(reviewDocumentPath);
    const challengeBytes = await readFile(
      path.join(outputRoot, "human-confirmation-challenge.json")
    );
    const challenge = JSON.parse(challengeBytes.toString("utf8"));
    const draft = JSON.parse(draftBytes.toString("utf8"));
    const decisions = draft.entries.map((entry) => ({
      caseId: entry.caseId,
      packetId: entry.packetId,
      expected: entry.proposedExpected
    }));
    decisions[0] = {
      ...decisions[0],
      expected: {
        suggestedRole: null,
        selectedOccurrenceIds: [],
        selectedAnchorBlockIds: [],
        abstain: true,
        abstentionReason: "HUMAN_NOT_ENOUGH_EVIDENCE"
      }
    };
    const confirmationRelativePath =
      "outputs/task-eval-002/track-b-admission-corpus-v2/" +
      "human-confirmation.json";
    const confirmationPath = path.join(root, confirmationRelativePath);
    await mkdir(path.dirname(confirmationPath), { recursive: true });
    await writeFile(
      confirmationPath,
      `${JSON.stringify(
        {
          schemaVersion: "task-eval-002-track-b-human-confirmation-v1",
          confirmed: true,
          challengeNonce: challenge.nonce,
          challengeSha256: sha256(challengeBytes),
          corpusSha256: sha256(corpusBytes),
          humanReviewDraftSha256: sha256(draftBytes),
          humanReviewDocumentSha256: sha256(reviewDocumentBytes),
          decisionsSha256: sha256(
            Buffer.from(JSON.stringify(decisions), "utf8")
          ),
          decisions,
          confirmedBy: "TEST_HUMAN_REVIEWER",
          confirmedAt: "2026-07-29T07:10:00.000Z",
          confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
          confirmationStatement:
            `确认 ${challenge.nonce} / ${challenge.corpusSha256}`
        },
        null,
        2
      )}\n`,
      "utf8"
    );

    result = spawnSync(
      process.execPath,
      [sealPath, root, confirmationRelativePath, "create"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const groundTruth = JSON.parse(
      await readFile(path.join(outputRoot, "human-ground-truth.json"), "utf8")
    );
    assert.equal(groundTruth.status, "ACCEPTED_HUMAN_GROUND_TRUTH");
    assert.equal(groundTruth.entryCount, 18);
    assert.equal(groundTruth.entries[0].expected.abstain, true);
    assert.equal(
      groundTruth.entries[0].expected.abstentionReason,
      "HUMAN_NOT_ENOUGH_EVIDENCE"
    );

    result = spawnSync(
      process.execPath,
      [sealPath, root, confirmationRelativePath, "verify"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);

    await writeFile(
      reviewDocumentPath,
      Buffer.concat([reviewDocumentBytes, Buffer.from("\nTAMPERED\n")])
    );
    const reviewTamperVerification = spawnSync(
      process.execPath,
      [sealPath, root, confirmationRelativePath, "verify"],
      { encoding: "utf8" }
    );
    assert.notEqual(reviewTamperVerification.status, 0);
    assert.match(
      reviewTamperVerification.stderr,
      /humanReviewDocumentSha256/
    );
    await writeFile(reviewDocumentPath, reviewDocumentBytes);

    const secondCreate = spawnSync(
      process.execPath,
      [sealPath, root, confirmationRelativePath, "create"],
      { encoding: "utf8" }
    );
    assert.notEqual(secondCreate.status, 0);
    assert.match(secondCreate.stderr, /immutable and already exists/);

    const corpus = JSON.parse(corpusBytes.toString("utf8"));
    corpus.packets[0].candidateRole = "TAMPERED";
    await writeFile(
      path.join(outputRoot, "corpus.json"),
      `${JSON.stringify(corpus, null, 2)}\n`
    );
    const tamperVerification = spawnSync(
      process.execPath,
      [sealPath, root, confirmationRelativePath, "verify"],
      { encoding: "utf8" }
    );
    assert.notEqual(tamperVerification.status, 0);
    assert.match(tamperVerification.stderr, /corpusSha256/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
