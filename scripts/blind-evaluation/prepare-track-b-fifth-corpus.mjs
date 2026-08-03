import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  buildTrackBFifthArtifacts,
  jsonBytes,
  sha256
} from "./track-b-holdout-contract.mjs";
import {
  validateTrackBFifthIndependence
} from "./track-b-fifth-contract.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const generatedAt = canonicalIso(process.argv[3]);
const sourcePath =
  "apps/api-server/src/test/resources/track-b-fifth-v1/source-signals.json";
const proposalsPath =
  "apps/api-server/src/test/resources/track-b-fifth-v1/proposed-decisions.json";
const priorCorpusPaths = [
  "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json",
  "outputs/task-eval-002/track-b-holdout-v1/corpus.json",
  "outputs/task-eval-003/track-b-successor-v1/corpus.json",
  "outputs/task-eval-004/track-b-final-v1/corpus.json"
];
const diagnosticInputPath =
  "outputs/task-eval-005/schema-diagnostic-v1/baseline/diagnostic-input.json";
const stabilityFreezePath =
  "outputs/task-eval-005/schema-diagnostic-v1/stability-freeze.json";
const outputRootPath = "outputs/task-eval-005/track-b-fifth-v1";

const [
  sourceBytes,
  proposalsBytes,
  diagnosticBytes,
  stabilityBytes,
  ...priorBytes
] = await Promise.all([
  read(sourcePath),
  read(proposalsPath),
  read(diagnosticInputPath),
  read(stabilityFreezePath),
  ...priorCorpusPaths.map(read)
]);
const stability = parseJsonBytesRejectDuplicateKeys(stabilityBytes);
assert.equal(stability.status, "GO_24_OF_24_STRICT_SCHEMA");
assert.equal(stability.fifthCorpusCreationAllowed, true);
assert.equal(stability.selectedVersionAccepted, 24);
assert.equal(stability.selectedVersionRejected, 0);
assert.equal(stability.providerA0A1A2Allowed, false);

const artifacts = buildTrackBFifthArtifacts({
  source: parseJsonBytesRejectDuplicateKeys(sourceBytes),
  sourcePath,
  sourceSha256: sha256(sourceBytes),
  proposals: parseJsonBytesRejectDuplicateKeys(proposalsBytes),
  proposalsPath,
  proposalsSha256: sha256(proposalsBytes),
  generatedAt
});
const disjointness = validateTrackBFifthIndependence({
  fifthCorpus: artifacts.corpus,
  priorCorpora: priorBytes.map(parseJsonBytesRejectDuplicateKeys),
  diagnosticInput: parseJsonBytesRejectDuplicateKeys(diagnosticBytes)
});
const outputRoot = relative(outputRootPath);
await mkdir(outputRoot, { recursive: true });
const corpusBytes = jsonBytes(artifacts.corpus);
const draftBytes = jsonBytes(artifacts.draft);
const reviewBytes = Buffer.from(artifacts.reviewMarkdown, "utf8");
const manifest = {
  schemaVersion: "task-eval-005-track-b-fifth-preseal-manifest-v1",
  status: "AWAITING_HUMAN_REVIEW",
  generatedAt,
  stabilityFreezePath,
  stabilityFreezeSha256: sha256(stabilityBytes),
  selectedPromptPath: stability.selectedPromptPath,
  selectedPromptSha256: stability.selectedPromptSha256,
  selectedSchemaVersion: stability.selectedSchemaVersion,
  sourceSignalsPath: sourcePath,
  sourceSignalsSha256: sha256(sourceBytes),
  proposedDecisionsPath: proposalsPath,
  proposedDecisionsSha256: sha256(proposalsBytes),
  priorCorpusPaths,
  priorCorpusSha256s: priorBytes.map(sha256),
  diagnosticInputPath,
  diagnosticInputSha256: sha256(diagnosticBytes),
  disjointness,
  corpusPath: `${outputRootPath}/corpus.json`,
  corpusSha256: sha256(corpusBytes),
  humanReviewDraftPath: `${outputRootPath}/human-review-draft.json`,
  humanReviewDraftSha256: sha256(draftBytes),
  humanReviewDocumentPath:
    `${outputRootPath}/human-ground-truth-review.md`,
  humanReviewDocumentSha256: sha256(reviewBytes),
  packetCount: artifacts.corpus.packetCount,
  eligiblePacketCount: artifacts.corpus.eligiblePacketCount,
  zeroCallControlCount: artifacts.corpus.zeroCallControlCount,
  modelInputCreated: false,
  evaluatorAccessAllowed: false,
  admissionNetworkCallAllowed: false,
  taskEval004Reused: false,
  sixthCorpusAllowed: false,
  providerA0A1A2Allowed: false
};
const manifestBytes = jsonBytes(manifest);
await write("corpus.json", corpusBytes);
await write("human-review-draft.json", draftBytes);
await write("human-ground-truth-review.md", reviewBytes);
await write("preseal-manifest.json", manifestBytes);
process.stdout.write(`${JSON.stringify({
  status: manifest.status,
  sourceSignalsSha256: manifest.sourceSignalsSha256,
  proposedDecisionsSha256: manifest.proposedDecisionsSha256,
  corpusSha256: manifest.corpusSha256,
  humanReviewDraftSha256: manifest.humanReviewDraftSha256,
  humanReviewDocumentSha256: manifest.humanReviewDocumentSha256,
  presealManifestSha256: sha256(manifestBytes),
  disjointness,
  packetCount: manifest.packetCount,
  eligiblePacketCount: manifest.eligiblePacketCount,
  zeroCallControlCount: manifest.zeroCallControlCount,
  modelInputCreated: false,
  evaluatorAccessAllowed: false,
  admissionNetworkCallAllowed: false
})}\n`);

function relative(path) {
  return resolve(repoRoot, ...path.split("/"));
}
function read(path) {
  return readFile(relative(path));
}
function write(name, bytes) {
  return writeFile(resolve(outputRoot, name), bytes, { flag: "wx" });
}
function canonicalIso(value) {
  assert.equal(typeof value, "string");
  assert.equal(new Date(value).toISOString(), value);
  return value;
}
