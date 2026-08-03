import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  jsonBytes,
  sha256,
  validateTrackBRecoveryCorpus
} from "./track-b-holdout-contract.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

export async function prepareTrackBProviderRecoveryHumanChallenge({
  repoRoot,
  createdAt,
  expiresAt,
  nonce
}) {
  assert.equal(new Date(createdAt).toISOString(), createdAt);
  assert.equal(new Date(expiresAt).toISOString(), expiresAt);
  assert.ok(Date.parse(expiresAt) > Date.parse(createdAt));
  assert.ok(Date.parse(expiresAt) - Date.parse(createdAt) <= 172_800_000);
  assert.match(nonce, /^TB61-[a-f0-9]{32}$/);

  const root = resolve(repoRoot);
  const outputRoot = "outputs/task-eval-006/track-b-recovery-v1";
  const paths = {
    corpus: `${outputRoot}/corpus.json`,
    draft: `${outputRoot}/human-review-draft.json`,
    review: `${outputRoot}/human-ground-truth-review.md`,
    manifest: `${outputRoot}/preseal-manifest.json`,
    challenge: `${outputRoot}/human-confirmation-challenge.json`
  };
  const read = (path) => readFile(resolve(root, ...path.split("/")));
  const [corpusBytes, draftBytes, reviewBytes, manifestBytes] =
    await Promise.all([
      read(paths.corpus),
      read(paths.draft),
      read(paths.review),
      read(paths.manifest)
    ]);
  const corpus = validateTrackBRecoveryCorpus(
    parseJsonBytesRejectDuplicateKeys(corpusBytes)
  );
  const draft = parseJsonBytesRejectDuplicateKeys(draftBytes);
  const manifest = parseJsonBytesRejectDuplicateKeys(manifestBytes);
  assert.equal(draft.status, "PENDING_HUMAN_CONFIRMATION_NOT_GROUND_TRUTH");
  assert.equal(draft.humanGroundTruthEstablished, false);
  assert.ok(draft.entries.every((entry) => entry.humanDecision === null));
  assert.equal(manifest.status, "AWAITING_HUMAN_REVIEW");
  assert.deepEqual(manifest.disjointness, {
    comparedPriorCorpusCount: 5,
    diagnosticInputCount: 2,
    identityOverlapCount: 0,
    candidateValueOverlapCount: 0,
    evidenceTextOverlapCount: 0
  });

  const challenge = {
    schemaVersion: "task-eval-006-track-b-recovery-human-challenge-v1",
    status: "AWAITING_EXPLICIT_HUMAN_DECISIONS",
    nonce,
    createdAt,
    expiresAt,
    diagnosticSealPath: manifest.diagnosticSealPath,
    diagnosticSealSha256: manifest.diagnosticSealSha256,
    selectedPromptSha256: manifest.selectedPromptSha256,
    selectedProjectionSchema: manifest.selectedProjectionSchema,
    selectedOpinionSchema: manifest.selectedOpinionSchema,
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
    providerAdmissionEstablished: false,
    instruction:
      "人工确认必须绑定本 challenge/corpus/review SHA 并逐项提供12条 decisions；" +
      "可接受、修改或拒答。确认前不得创建 model input、访问 evaluator 或联网。"
  };
  const bytes = jsonBytes(challenge);
  await writeFile(resolve(root, ...paths.challenge.split("/")), bytes, {
    flag: "wx"
  });
  return {
    status: challenge.status,
    nonce,
    challengeSha256: sha256(bytes),
    corpusSha256: challenge.corpusSha256,
    humanReviewDraftSha256: challenge.humanReviewDraftSha256,
    humanReviewDocumentSha256: challenge.humanReviewDocumentSha256,
    presealManifestSha256: challenge.presealManifestSha256,
    diagnosticSealSha256: challenge.diagnosticSealSha256,
    requiredDecisionCount: challenge.requiredDecisionCount,
    expiresAt,
    modelInputCreated: false,
    evaluatorAccessAllowed: false,
    admissionNetworkCallAllowed: false
  };
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [
    repoRoot = ".",
    createdAt,
    expiresAt,
    nonce
  ] = process.argv.slice(2);
  const result = await prepareTrackBProviderRecoveryHumanChallenge({
    repoRoot,
    createdAt,
    expiresAt,
    nonce
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
