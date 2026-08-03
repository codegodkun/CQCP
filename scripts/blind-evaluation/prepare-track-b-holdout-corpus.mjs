import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

import {
  buildTrackBHoldoutArtifacts,
  jsonBytes,
  sha256
} from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const generatedAt = process.argv[3];
if (!generatedAt) {
  throw new Error(
    "Usage: node prepare-track-b-holdout-corpus.mjs " +
      "<repo-root> <generated-at>"
  );
}

const sourceRelativePath =
  "apps/api-server/src/test/resources/track-b-holdout-v1/" +
  "source-signals.json";
const proposalsRelativePath =
  "apps/api-server/src/test/resources/track-b-holdout-v1/" +
  "proposed-decisions.json";
const outputRelativePath =
  "outputs/task-eval-002/track-b-holdout-v1";
const sourceBytes = await readRelative(sourceRelativePath);
const proposalsBytes = await readRelative(proposalsRelativePath);
const artifacts = buildTrackBHoldoutArtifacts({
  source: JSON.parse(sourceBytes.toString("utf8")),
  sourcePath: sourceRelativePath,
  sourceSha256: sha256(sourceBytes),
  proposals: JSON.parse(proposalsBytes.toString("utf8")),
  proposalsPath: proposalsRelativePath,
  proposalsSha256: sha256(proposalsBytes),
  generatedAt
});
const outputRoot = resolve(
  repoRoot,
  ...outputRelativePath.split("/")
);
await mkdir(dirname(outputRoot), { recursive: true });
try {
  await mkdir(outputRoot);
} catch (error) {
  if (error?.code === "EEXIST") {
    throw new Error("Track B holdout output is immutable and already exists");
  }
  throw error;
}
const corpusBytes = jsonBytes(artifacts.corpus);
const draftBytes = jsonBytes(artifacts.draft);
const reviewBytes = Buffer.from(artifacts.reviewMarkdown, "utf8");
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
const manifest = {
  schemaVersion: "task-eval-002-track-b-holdout-preseal-manifest-v1",
  status: "AWAITING_HUMAN_REVIEW",
  generatedAt,
  sourceSignalsPath: sourceRelativePath,
  sourceSignalsSha256: sha256(sourceBytes),
  proposedDecisionsPath: proposalsRelativePath,
  proposedDecisionsSha256: sha256(proposalsBytes),
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
  modelInputCreated: false,
  networkCallAllowed: false
};
const manifestBytes = jsonBytes(manifest);
await writeFile(
  resolve(outputRoot, "preseal-manifest.json"),
  manifestBytes,
  { flag: "wx" }
);
assert.equal(manifest.packetCount, 12);
process.stdout.write(
  `${JSON.stringify({
    status: manifest.status,
    corpusSha256: manifest.corpusSha256,
    humanReviewDraftSha256: manifest.humanReviewDraftSha256,
    humanReviewDocumentSha256:
      manifest.humanReviewDocumentSha256,
    presealManifestSha256: sha256(manifestBytes),
    packetCount: manifest.packetCount,
    eligiblePacketCount: manifest.eligiblePacketCount,
    zeroCallControlCount: manifest.zeroCallControlCount,
    modelInputCreated: false,
    networkCallAllowed: false
  })}\n`
);

function readRelative(relativePath) {
  return readFile(resolve(repoRoot, ...relativePath.split("/")));
}
