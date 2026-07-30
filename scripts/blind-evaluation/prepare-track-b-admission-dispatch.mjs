import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  mkdir,
  readFile,
  writeFile
} from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import {
  validateTrackBAdmissionCorpus,
  validateTrackBAdmissionModelInput
} from "./track-b-admission-opinion-contract.mjs";
import {
  TRACK_B_ADMISSION_COUNTS,
  TRACK_B_ADMISSION_TRUSTED_PROMPT_SHA256
} from "./track-b-admission-constants.mjs";
import {
  TRACK_B_PROVIDER_CALL_COUNT,
  TRACK_B_PROVIDER_REQUEST_BUILDER_VERSION,
  buildTrackBProviderCallSetArtifact
} from "./track-b-provider-request-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const createdAt = process.argv[3];
const codexAgentTask = process.argv[4];
if (
  !createdAt ||
  Number.isNaN(Date.parse(createdAt)) ||
  !/^\/root\/[a-z0-9_]+$/.test(codexAgentTask ?? "")
) {
  throw new Error(
    "Usage: node prepare-track-b-admission-dispatch.mjs " +
      "<repo-root> <created-at> </root/codex-agent-task>"
  );
}

const corpusRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/corpus.json";
const groundTruthRelativePath =
  "outputs/task-eval-002/track-b-admission-corpus-v2/human-ground-truth.json";
const promptRelativePath =
  "scripts/blind-evaluation/track-b-admission-opinion-prompt.txt";
const runRootRelativePath =
  "outputs/task-eval-002/track-b-admission-run-v3";
const modelInputRelativePath = `${runRootRelativePath}/model-input.json`;
const providerCallSetRelativePath =
  `${runRootRelativePath}/provider-call-set.json`;
const dispatchRelativePath = `${runRootRelativePath}/dispatch.json`;
const dispatchHashRelativePath = `${runRootRelativePath}/dispatch.sha256`;
const authorizationRelativePath =
  `${runRootRelativePath}/egress-authorization.json`;
const authorizationChallengeRelativePath =
  `${runRootRelativePath}/egress-authorization-challenge.json`;
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const scriptRoot = dirname(fileURLToPath(import.meta.url));

const groundTruthPath = resolve(repoRoot, groundTruthRelativePath);
let groundTruth;
try {
  groundTruth = JSON.parse(await readFile(groundTruthPath, "utf8"));
} catch {
  throw new Error("HUMAN_GROUND_TRUTH_NOT_SEALED");
}
assert.equal(groundTruth.status, "ACCEPTED_HUMAN_GROUND_TRUTH");
const identityMigrated =
  groundTruth.schemaVersion ===
    "task-eval-002-track-b-human-ground-truth-v2" &&
  groundTruth.confirmationKind === "IDENTITY_ONLY_CARRY_FORWARD" &&
  groundTruth.externalEgressAuthorized === false;
const fullyReviewedV2Corpus =
  groundTruth.schemaVersion ===
    "task-eval-002-track-b-human-ground-truth-v1" &&
  typeof groundTruth.confirmationPath === "string";
assert.ok(identityMigrated || fullyReviewedV2Corpus);
const verification = spawnSync(
  process.execPath,
  [
    resolve(
      scriptRoot,
      identityMigrated
        ? "seal-track-b-identity-migration.mjs"
        : "seal-track-b-human-ground-truth.mjs"
    ),
    repoRoot,
    identityMigrated
      ? groundTruth.identityMigrationConfirmationPath
      : groundTruth.confirmationPath,
    "verify"
  ],
  { cwd: repoRoot, encoding: "utf8" }
);
assert.equal(
  verification.status,
  0,
  `HUMAN_GROUND_TRUTH_VERIFICATION_FAILED: ${verification.stderr}`
);

const corpusPath = resolve(repoRoot, corpusRelativePath);
const corpusBytes = await readFile(corpusPath);
const corpus = JSON.parse(corpusBytes.toString("utf8"));
assert.equal(groundTruth.corpusSha256, sha256(corpusBytes));
const { eligiblePackets, zeroCallPackets } =
  validateTrackBAdmissionCorpus(corpus, sha256(corpusBytes));
assert.equal(
  groundTruth.entryCount,
  TRACK_B_ADMISSION_COUNTS.total
);
assert.equal(
  new Set(groundTruth.entries.map((entry) => entry.packetId)).size,
  TRACK_B_ADMISSION_COUNTS.total
);
assert.deepEqual(
  new Set(groundTruth.entries.map((entry) => entry.packetId)),
  new Set(corpus.packets.map((packet) => packet.packetId))
);
assert.ok(
  Date.parse(createdAt) >= Date.parse(groundTruth.confirmedAt),
  "Dispatch cannot precede human ground-truth confirmation"
);
assert.ok(
  Date.parse(createdAt) <= Date.now() + 300_000,
  "Dispatch cannot be created in the future"
);

const modelInput = validateTrackBAdmissionModelInput({
  schemaVersion: "task-eval-002-track-b-admission-model-input-v1",
  track: "TRACK_B_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION",
  sourceCorpusSha256: sha256(corpusBytes),
  groundTruthIncluded: false,
  packetCount: eligiblePackets.length,
  packets: eligiblePackets
});
const modelInputBytes = Buffer.from(
  `${JSON.stringify(modelInput, null, 2)}\n`,
  "utf8"
);
const promptBytes = await readFile(resolve(repoRoot, promptRelativePath));
assert.equal(
  sha256(promptBytes),
  TRACK_B_ADMISSION_TRUSTED_PROMPT_SHA256,
  "Track B admission prompt is not the trusted reviewed prompt"
);
const groundTruthBytes = await readFile(groundTruthPath);
const providerCallSet = buildTrackBProviderCallSetArtifact(
  modelInput,
  promptBytes
);
assert.equal(providerCallSet.callCount, TRACK_B_PROVIDER_CALL_COUNT);
const providerCallSetBytes = Buffer.from(
  `${JSON.stringify(providerCallSet, null, 2)}\n`,
  "utf8"
);
const dispatch = {
  schemaVersion: "task-eval-002-track-b-admission-dispatch-v1",
  status: "FROZEN_AFTER_HUMAN_CONFIRMATION_BEFORE_MODEL_EXECUTION",
  createdAt: new Date(createdAt).toISOString(),
  track: modelInput.track,
  forkTurns: "none",
  historicalContextIncluded: false,
  codexAgentTask,
  corpusPath: corpusRelativePath,
  corpusSha256: sha256(corpusBytes),
  humanGroundTruthSealPath: groundTruthRelativePath,
  humanGroundTruthSealSha256: sha256(groundTruthBytes),
  humanGroundTruthExcludedFromModelInput: true,
  promptPath: promptRelativePath,
  promptSha256: sha256(promptBytes),
  modelInputPath: modelInputRelativePath,
  modelInputSha256: sha256(modelInputBytes),
  providerCallSetPath: providerCallSetRelativePath,
  providerCallSetSha256: sha256(providerCallSetBytes),
  providerRequestBuilderVersion:
    TRACK_B_PROVIDER_REQUEST_BUILDER_VERSION,
  providerCallCount: providerCallSet.callCount,
  egressAuthorizationChallengePath:
    authorizationChallengeRelativePath,
  egressAuthorizationPath: authorizationRelativePath,
  egressAuthorizationRequired: true,
  eligiblePacketCount: eligiblePackets.length,
  zeroCallControlCount: zeroCallPackets.length,
  unblindProhibitedUntilBothModelOpinionsSealed: true
};
const dispatchBytes = Buffer.from(
  `${JSON.stringify(dispatch, null, 2)}\n`,
  "utf8"
);
const dispatchPath = resolve(repoRoot, dispatchRelativePath);
const runRootPath = resolve(repoRoot, runRootRelativePath);
await mkdir(dirname(runRootPath), { recursive: true });
try {
  await mkdir(runRootPath);
} catch (error) {
  if (error?.code === "EEXIST") {
    throw new Error(
      "Track B admission dispatch is immutable and already exists"
    );
  }
  throw error;
}
await writeFile(
  resolve(repoRoot, modelInputRelativePath),
  modelInputBytes,
  { flag: "wx" }
);
await writeFile(
  resolve(repoRoot, providerCallSetRelativePath),
  providerCallSetBytes,
  { flag: "wx" }
);
await writeFile(dispatchPath, dispatchBytes, { flag: "wx" });
await writeFile(
  resolve(repoRoot, dispatchHashRelativePath),
  `${sha256(dispatchBytes)}  dispatch.json\n`,
  { encoding: "utf8", flag: "wx" }
);
process.stdout.write(
  `${JSON.stringify({
    status: dispatch.status,
    dispatchSha256: sha256(dispatchBytes),
    modelInputSha256: dispatch.modelInputSha256,
    eligiblePacketCount: dispatch.eligiblePacketCount,
    zeroCallControlCount: dispatch.zeroCallControlCount
  })}\n`
);
