import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  buildTrackBFinalArtifacts,
  jsonBytes,
  sha256
} from "./track-b-holdout-contract.mjs";
import {
  validateTrackBFinalIndependence
} from "./track-b-successor-contract.mjs";
import {
  validateTaskEval004ConnectivityEvidence
} from "./task-eval-004-connectivity-gate.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const generatedAt = canonicalIso(process.argv[3], "generatedAt");
const sourceRelativePath =
  "apps/api-server/src/test/resources/track-b-final-v1/" +
  "source-signals.json";
const proposalsRelativePath =
  "apps/api-server/src/test/resources/track-b-final-v1/" +
  "proposed-decisions.json";
const historicalCorpusRelativePath =
  "apps/api-server/src/test/resources/track-b-admission-corpus-v2/" +
  "corpus.json";
const failedCorpusRelativePath =
  "outputs/task-eval-002/track-b-holdout-v1/corpus.json";
const failedSuccessorCorpusRelativePath =
  "outputs/task-eval-003/track-b-successor-v1/corpus.json";
const connectivityRelativePath =
  "outputs/task-eval-004/track-b-final-v1/connectivity/" +
  "connectivity-evidence.json";
const outputRelativePath =
  "outputs/task-eval-004/track-b-final-v1";

const sourceBytes = await readRelative(sourceRelativePath);
const proposalsBytes = await readRelative(proposalsRelativePath);
const historicalCorpusBytes = await readRelative(
  historicalCorpusRelativePath
);
const failedCorpusBytes = await readRelative(failedCorpusRelativePath);
const failedSuccessorCorpusBytes = await readRelative(
  failedSuccessorCorpusRelativePath
);
const connectivityBytes = await readRelative(connectivityRelativePath);
const connectivity = validateTaskEval004ConnectivityEvidence(
  JSON.parse(connectivityBytes.toString("utf8"))
);
assert.equal(connectivity.formalAdmissionAffected, false);

const artifacts = buildTrackBFinalArtifacts({
  source: JSON.parse(sourceBytes.toString("utf8")),
  sourcePath: sourceRelativePath,
  sourceSha256: sha256(sourceBytes),
  proposals: JSON.parse(proposalsBytes.toString("utf8")),
  proposalsPath: proposalsRelativePath,
  proposalsSha256: sha256(proposalsBytes),
  generatedAt
});
const disjointness = validateTrackBFinalIndependence({
  finalCorpus: artifacts.corpus,
  historicalCorpus: JSON.parse(historicalCorpusBytes.toString("utf8")),
  failedHoldoutCorpus: JSON.parse(failedCorpusBytes.toString("utf8")),
  failedSuccessorCorpus: JSON.parse(
    failedSuccessorCorpusBytes.toString("utf8")
  )
});
const outputRoot = resolve(repoRoot, ...outputRelativePath.split("/"));
await mkdir(outputRoot, { recursive: true });

const corpusBytes = jsonBytes(artifacts.corpus);
const draftBytes = jsonBytes(artifacts.draft);
const reviewBytes = Buffer.from(artifacts.reviewMarkdown, "utf8");
const manifest = {
  schemaVersion: "task-eval-004-track-b-final-preseal-manifest-v1",
  status: "AWAITING_HUMAN_REVIEW",
  generatedAt,
  connectivityEvidencePath: connectivityRelativePath,
  connectivityEvidenceSha256: sha256(connectivityBytes),
  connectivityStatus: connectivity.status,
  connectivityFormalAdmissionAffected: false,
  sourceSignalsPath: sourceRelativePath,
  sourceSignalsSha256: sha256(sourceBytes),
  proposedDecisionsPath: proposalsRelativePath,
  proposedDecisionsSha256: sha256(proposalsBytes),
  historicalCorpusPath: historicalCorpusRelativePath,
  historicalCorpusSha256: sha256(historicalCorpusBytes),
  failedHoldoutCorpusPath: failedCorpusRelativePath,
  failedHoldoutCorpusSha256: sha256(failedCorpusBytes),
  failedSuccessorCorpusPath: failedSuccessorCorpusRelativePath,
  failedSuccessorCorpusSha256: sha256(failedSuccessorCorpusBytes),
  disjointness,
  corpusPath: `${outputRelativePath}/corpus.json`,
  corpusSha256: sha256(corpusBytes),
  humanReviewDraftPath:
    `${outputRelativePath}/human-review-draft.json`,
  humanReviewDraftSha256: sha256(draftBytes),
  humanReviewDocumentPath:
    `${outputRelativePath}/human-ground-truth-review.md`,
  humanReviewDocumentSha256: sha256(reviewBytes),
  packetCount: artifacts.corpus.packetCount,
  eligiblePacketCount: artifacts.corpus.eligiblePacketCount,
  zeroCallControlCount: artifacts.corpus.zeroCallControlCount,
  priorFailedClaimsReused: false,
  modelInputCreated: false,
  admissionNetworkCallAllowed: false
};
const manifestBytes = jsonBytes(manifest);

await writeFile(resolve(outputRoot, "corpus.json"), corpusBytes, {
  flag: "wx"
});
await writeFile(
  resolve(outputRoot, "human-review-draft.json"),
  draftBytes,
  { flag: "wx" }
);
await writeFile(
  resolve(outputRoot, "human-ground-truth-review.md"),
  reviewBytes,
  { flag: "wx" }
);
await writeFile(
  resolve(outputRoot, "preseal-manifest.json"),
  manifestBytes,
  { flag: "wx" }
);

assert.equal(manifest.packetCount, 12);
assert.equal(manifest.disjointness.identityOverlapCount, 0);
process.stdout.write(
  `${JSON.stringify({
    status: manifest.status,
    connectivityEvidenceSha256: manifest.connectivityEvidenceSha256,
    corpusSha256: manifest.corpusSha256,
    humanReviewDraftSha256: manifest.humanReviewDraftSha256,
    humanReviewDocumentSha256:
      manifest.humanReviewDocumentSha256,
    presealManifestSha256: sha256(manifestBytes),
    packetCount: manifest.packetCount,
    eligiblePacketCount: manifest.eligiblePacketCount,
    zeroCallControlCount: manifest.zeroCallControlCount,
    disjointness: manifest.disjointness,
    modelInputCreated: false,
    admissionNetworkCallAllowed: false
  })}\n`
);

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
