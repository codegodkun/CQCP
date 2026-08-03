import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  buildTrackBRecoveryArtifacts,
  jsonBytes,
  sha256
} from "./track-b-holdout-contract.mjs";
import {
  validateTrackBProviderRecoveryIndependence
} from "./track-b-provider-recovery-corpus-contract.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

const SOURCE_PATH =
  "apps/api-server/src/test/resources/track-b-recovery-v1/source-signals.json";
const PROPOSALS_PATH =
  "apps/api-server/src/test/resources/track-b-recovery-v1/proposed-decisions.json";
const PRIOR_CORPUS_PATHS = [
  "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json",
  "outputs/task-eval-002/track-b-holdout-v1/corpus.json",
  "outputs/task-eval-003/track-b-successor-v1/corpus.json",
  "outputs/task-eval-004/track-b-final-v1/corpus.json",
  "outputs/task-eval-005/track-b-fifth-v1/corpus.json"
];
const DIAGNOSTIC_INPUT_PATHS = [
  "outputs/task-eval-005/schema-diagnostic-v1/baseline/diagnostic-input.json",
  "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/model-input.json"
];
const DIAGNOSTIC_SEAL_PATH =
  "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/diagnostic-seal.json";
const OUTPUT_ROOT_PATH = "outputs/task-eval-006/track-b-recovery-v1";

export async function prepareTrackBProviderRecoveryCorpus({
  repoRoot,
  generatedAt
}) {
  assert.equal(new Date(generatedAt).toISOString(), generatedAt);
  const root = resolve(repoRoot);
  const absolute = (path) => resolve(root, ...path.split("/"));
  const read = (path) => readFile(absolute(path));
  const [
    sourceBytes,
    proposalsBytes,
    diagnosticSealBytes,
    ...comparisonBytes
  ] = await Promise.all([
    read(SOURCE_PATH),
    read(PROPOSALS_PATH),
    read(DIAGNOSTIC_SEAL_PATH),
    ...PRIOR_CORPUS_PATHS.map(read),
    ...DIAGNOSTIC_INPUT_PATHS.map(read)
  ]);
  const diagnosticSeal = parseJsonBytesRejectDuplicateKeys(
    diagnosticSealBytes
  );
  assert.equal(
    diagnosticSeal.status,
    "SEALED_GO_RECOVERY_DIAGNOSTIC"
  );
  assert.equal(diagnosticSeal.sixthCorpusCreationAllowed, true);
  assert.equal(diagnosticSeal.providerAdmissionEstablished, false);

  const artifacts = buildTrackBRecoveryArtifacts({
    source: parseJsonBytesRejectDuplicateKeys(sourceBytes),
    sourcePath: SOURCE_PATH,
    sourceSha256: sha256(sourceBytes),
    proposals: parseJsonBytesRejectDuplicateKeys(proposalsBytes),
    proposalsPath: PROPOSALS_PATH,
    proposalsSha256: sha256(proposalsBytes),
    generatedAt
  });
  const priorBytes = comparisonBytes.slice(0, PRIOR_CORPUS_PATHS.length);
  const diagnosticBytes = comparisonBytes.slice(PRIOR_CORPUS_PATHS.length);
  const disjointness = validateTrackBProviderRecoveryIndependence({
    recoveryCorpus: artifacts.corpus,
    priorCorpora: priorBytes.map(parseJsonBytesRejectDuplicateKeys),
    diagnosticInputs: diagnosticBytes.map(parseJsonBytesRejectDuplicateKeys)
  });

  const corpusBytes = jsonBytes(artifacts.corpus);
  const draftBytes = jsonBytes(artifacts.draft);
  const reviewBytes = Buffer.from(artifacts.reviewMarkdown, "utf8");
  const manifest = {
    schemaVersion: "task-eval-006-track-b-recovery-preseal-manifest-v1",
    status: "AWAITING_HUMAN_REVIEW",
    generatedAt,
    diagnosticSealPath: DIAGNOSTIC_SEAL_PATH,
    diagnosticSealSha256: sha256(diagnosticSealBytes),
    selectedPromptPath:
      "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt",
    selectedPromptSha256:
      "5cb29a5ddc9644e15afba8afc67079d805ff41ae4472cf93997a1d89ac302601",
    selectedProjectionSchema:
      "task-eval-006-model-facing-evidence-packet-v3",
    selectedOpinionSchema:
      "track-b-role-candidate-anchor-abstention-v3",
    sourceSignalsPath: SOURCE_PATH,
    sourceSignalsSha256: sha256(sourceBytes),
    proposedDecisionsPath: PROPOSALS_PATH,
    proposedDecisionsSha256: sha256(proposalsBytes),
    priorCorpusPaths: PRIOR_CORPUS_PATHS,
    priorCorpusSha256s: priorBytes.map(sha256),
    diagnosticInputPaths: DIAGNOSTIC_INPUT_PATHS,
    diagnosticInputSha256s: diagnosticBytes.map(sha256),
    disjointness,
    corpusPath: `${OUTPUT_ROOT_PATH}/corpus.json`,
    corpusSha256: sha256(corpusBytes),
    humanReviewDraftPath: `${OUTPUT_ROOT_PATH}/human-review-draft.json`,
    humanReviewDraftSha256: sha256(draftBytes),
    humanReviewDocumentPath:
      `${OUTPUT_ROOT_PATH}/human-ground-truth-review.md`,
    humanReviewDocumentSha256: sha256(reviewBytes),
    packetCount: artifacts.corpus.packetCount,
    eligiblePacketCount: artifacts.corpus.eligiblePacketCount,
    zeroCallControlCount: artifacts.corpus.zeroCallControlCount,
    modelInputCreated: false,
    evaluatorAccessAllowed: false,
    admissionNetworkCallAllowed: false,
    providerAdmissionEstablished: false,
    providerA0A1A2Allowed: false
  };
  const outputRoot = absolute(OUTPUT_ROOT_PATH);
  await mkdir(outputRoot, { recursive: true });
  for (const [name, bytes] of [
    ["corpus.json", corpusBytes],
    ["human-review-draft.json", draftBytes],
    ["human-ground-truth-review.md", reviewBytes],
    ["preseal-manifest.json", jsonBytes(manifest)]
  ]) {
    await writeFile(resolve(outputRoot, name), bytes, { flag: "wx" });
  }
  return {
    status: manifest.status,
    sourceSignalsSha256: manifest.sourceSignalsSha256,
    proposedDecisionsSha256: manifest.proposedDecisionsSha256,
    corpusSha256: manifest.corpusSha256,
    humanReviewDraftSha256: manifest.humanReviewDraftSha256,
    humanReviewDocumentSha256: manifest.humanReviewDocumentSha256,
    presealManifestSha256: sha256(jsonBytes(manifest)),
    diagnosticSealSha256: manifest.diagnosticSealSha256,
    disjointness,
    packetCount: manifest.packetCount,
    eligiblePacketCount: manifest.eligiblePacketCount,
    zeroCallControlCount: manifest.zeroCallControlCount,
    modelInputCreated: false,
    evaluatorAccessAllowed: false,
    admissionNetworkCallAllowed: false
  };
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repoRoot = ".", generatedAt = new Date().toISOString()] =
    process.argv.slice(2);
  const result = await prepareTrackBProviderRecoveryCorpus({
    repoRoot,
    generatedAt
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
