import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  mkdir,
  mkdtemp,
  readFile,
  rename,
  rm,
  writeFile
} from "node:fs/promises";
import { dirname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

import {
  TRACK_B_FINAL_TRACK,
  TRACK_B_FIFTH_TRACK,
  TRACK_B_HOLDOUT_COUNTS,
  TRACK_B_HOLDOUT_ENDPOINT_HOST,
  TRACK_B_HOLDOUT_MODEL,
  TRACK_B_HOLDOUT_TRACK,
  TRACK_B_SUCCESSOR_TRACK
} from "./track-b-holdout-constants.mjs";
import {
  jsonBytes,
  sha256
} from "./track-b-holdout-contract.mjs";
import {
  TRACK_B_FINAL_MODEL_INPUT_SCHEMA,
  TRACK_B_FIFTH_MODEL_INPUT_SCHEMA,
  TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
  TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA,
  validateTrackBFinalCorpusForModel,
  validateTrackBFinalModelInput,
  validateTrackBFifthCorpusForModel,
  validateTrackBFifthModelInput,
  validateTrackBHoldoutCorpusForModel,
  validateTrackBHoldoutModelInput,
  validateTrackBSuccessorCorpusForModel,
  validateTrackBSuccessorModelInput
} from "./track-b-holdout-opinion-contract.mjs";
import {
  buildTrackBFinalProviderCallSetArtifact,
  buildTrackBFifthProviderCallSetArtifact,
  buildTrackBHoldoutProviderCallSetArtifact,
  buildTrackBSuccessorProviderCallSetArtifact
} from "./track-b-holdout-provider-request-contract.mjs";
import {
  loadAndValidateMvp002StandingEgressGrant
} from "./mvp002-standing-egress-grant.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const createdAt = canonicalIso(
  process.argv[3] ?? new Date().toISOString(),
  "createdAt"
);
const profileArg = process.argv[4] ?? "holdout";
const scriptRoot = dirname(fileURLToPath(import.meta.url));
const profiles = Object.freeze({
  holdout: Object.freeze({
    outputRoot: "outputs/task-eval-002/track-b-holdout-v1",
    humanSealSchema:
      "task-eval-002-track-b-holdout-human-ground-truth-v1",
    modelInputSchema: TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
    track: TRACK_B_HOLDOUT_TRACK,
    dispatchSchema: "task-eval-002-track-b-holdout-dispatch-v1",
    validateCorpusForModel: validateTrackBHoldoutCorpusForModel,
    validateModelInput: validateTrackBHoldoutModelInput,
    buildProviderCallSet: buildTrackBHoldoutProviderCallSetArtifact
  }),
  successor: Object.freeze({
    outputRoot: "outputs/task-eval-003/track-b-successor-v1",
    humanSealSchema:
      "task-eval-003-track-b-successor-human-ground-truth-v1",
    modelInputSchema: TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA,
    track: TRACK_B_SUCCESSOR_TRACK,
    dispatchSchema: "task-eval-003-track-b-successor-dispatch-v1",
    validateCorpusForModel: validateTrackBSuccessorCorpusForModel,
    validateModelInput: validateTrackBSuccessorModelInput,
    buildProviderCallSet: buildTrackBSuccessorProviderCallSetArtifact
  }),
  final: Object.freeze({
    outputRoot: "outputs/task-eval-004/track-b-final-v1",
    humanSealSchema:
      "task-eval-004-track-b-final-human-ground-truth-v1",
    modelInputSchema: TRACK_B_FINAL_MODEL_INPUT_SCHEMA,
    track: TRACK_B_FINAL_TRACK,
    dispatchSchema: "task-eval-004-track-b-final-dispatch-v1",
    validateCorpusForModel: validateTrackBFinalCorpusForModel,
    validateModelInput: validateTrackBFinalModelInput,
    buildProviderCallSet: buildTrackBFinalProviderCallSetArtifact,
    promptPath:
      "scripts/blind-evaluation/track-b-holdout-opinion-prompt.txt"
  }),
  fifth: Object.freeze({
    outputRoot: "outputs/task-eval-005/track-b-fifth-v1",
    humanSealSchema:
      "task-eval-005-track-b-fifth-human-ground-truth-v1",
    modelInputSchema: TRACK_B_FIFTH_MODEL_INPUT_SCHEMA,
    track: TRACK_B_FIFTH_TRACK,
    dispatchSchema: "task-eval-005-track-b-fifth-dispatch-v2",
    validateCorpusForModel: validateTrackBFifthCorpusForModel,
    validateModelInput: validateTrackBFifthModelInput,
    buildProviderCallSet: buildTrackBFifthProviderCallSetArtifact,
    promptPath:
      "scripts/blind-evaluation/track-b-schema-diagnostic-prompt-v2.txt"
  })
});
const profile = profiles[profileArg];
if (!profile) throw new Error(`Unknown profile: ${profileArg}`);
const outputRootRelative = profile.outputRoot;
const runRootRelative = `${outputRootRelative}/run-v1`;
const corpusRelativePath = `${outputRootRelative}/corpus.json`;
const sealRelativePath = `${outputRootRelative}/human-ground-truth.json`;
const promptRelativePath = profile.promptPath ??
  "scripts/blind-evaluation/track-b-holdout-opinion-prompt.txt";
const grantRelativePath =
  "scripts/blind-evaluation/mvp002-standing-egress-grant.json";
const modelInputRelativePath = `${runRootRelative}/model-input.json`;
const callSetRelativePath = `${runRootRelative}/provider-call-set.json`;
const dispatchRelativePath = `${runRootRelative}/dispatch.json`;
const receiptRelativePath =
  `${runRootRelative}/derived-egress-authorization-receipt.json`;

const corpusBytes = await readRelative(corpusRelativePath);
const sealBytes = await readRelative(sealRelativePath);
const promptBytes = await readRelative(promptRelativePath);
const seal = JSON.parse(sealBytes.toString("utf8"));
assert.equal(
  seal.schemaVersion,
  profile.humanSealSchema
);
assert.equal(seal.status, "ACCEPTED_HUMAN_GROUND_TRUTH");
assert.equal(seal.corpusPath, corpusRelativePath);
assert.equal(seal.corpusSha256, sha256(corpusBytes));
assert.equal(seal.entryCount, TRACK_B_HOLDOUT_COUNTS.total);
assert.equal(seal.modelInputCreated, false);
assert.equal(seal.networkCallAllowedByThisSeal, false);
assert.equal(typeof seal.confirmationPath, "string");
assert.ok(seal.confirmationPath);
assert.equal(typeof seal.confirmedAt, "string");
assert.ok(
  Date.parse(createdAt) >= Date.parse(canonicalIso(seal.confirmedAt, "confirmedAt")),
  "TRACK_B_HOLDOUT_DISPATCH_PRECEDES_HUMAN_CONFIRMATION"
);

const sealVerifier = resolve(
  scriptRoot,
  "seal-track-b-holdout-human-ground-truth.mjs"
);
const verified = spawnSync(
  process.execPath,
  [sealVerifier, repoRoot, seal.confirmationPath, "verify", profileArg],
  { cwd: repoRoot, encoding: "utf8" }
);
if (verified.status !== 0) {
  throw new Error(
    `TRACK_B_HOLDOUT_HUMAN_SEAL_INVALID: ${verified.stderr}`
  );
}

const grantPath = resolveRelative(grantRelativePath);
const { bytes: grantBytes, grant } =
  await loadAndValidateMvp002StandingEgressGrant(grantPath);
assert.ok(grant.scope.allowedPurposes.includes("TRACK_B_EVALUATION"));
assert.ok(grant.provider.allowedModels.includes(TRACK_B_HOLDOUT_MODEL));
assert.equal(
  grant.provider.endpointOrigin,
  "https://api.deepseek.com:443"
);

const corpus = JSON.parse(corpusBytes.toString("utf8"));
const { eligiblePackets, zeroCallPackets } =
  profile.validateCorpusForModel(corpus, sha256(corpusBytes));
const modelInput = profile.validateModelInput({
  schemaVersion: profile.modelInputSchema,
  track: profile.track,
  sourceCorpusSha256: sha256(corpusBytes),
  groundTruthIncluded: false,
  packetCount: eligiblePackets.length,
  packets: eligiblePackets
});
const modelInputBytes = jsonBytes(modelInput);
const callSet = {
  ...profile.buildProviderCallSet(modelInput, promptBytes),
  status: "FROZEN_AFTER_HUMAN_GROUND_TRUTH_SEAL",
  humanGroundTruthSealSha256: sha256(sealBytes),
  standingGrantSha256: sha256(grantBytes)
};
assert.equal(
  callSet.masterModelInputSha256,
  sha256(modelInputBytes),
  "provider call set must bind the actual model-input bytes"
);
const callSetBytes = jsonBytes(callSet);
const dispatch = {
  schemaVersion: profile.dispatchSchema,
  status: "FROZEN_READY_FOR_BLIND_EVALUATORS",
  createdAt,
  corpusPath: corpusRelativePath,
  corpusSha256: sha256(corpusBytes),
  humanGroundTruthSealPath: sealRelativePath,
  humanGroundTruthSealSha256: sha256(sealBytes),
  standingGrantPath: grantRelativePath,
  standingGrantSha256: sha256(grantBytes),
  promptPath: promptRelativePath,
  promptSha256: sha256(promptBytes),
  modelInputPath: modelInputRelativePath,
  modelInputSha256: sha256(modelInputBytes),
  providerCallSetPath: callSetRelativePath,
  providerCallSetSha256: sha256(callSetBytes),
  derivedEgressReceiptPath: receiptRelativePath,
  endpointOrigin: grant.provider.endpointOrigin,
  endpointHost: TRACK_B_HOLDOUT_ENDPOINT_HOST,
  model: TRACK_B_HOLDOUT_MODEL,
  providerCallCount: callSet.callCount,
  eligiblePacketCount: eligiblePackets.length,
  zeroCallControlCount: zeroCallPackets.length,
  zeroCallControlsExcluded: true,
  humanGroundTruthExcludedFromPayload: true,
  cqcpActualOrExpectedExcludedFromPayload: true,
  findingOrVerdictExcludedFromPayload: true,
  networkCallPerformed: false
};
const dispatchBytes = jsonBytes(dispatch);
const receipt = {
  schemaVersion: "mvp002-derived-egress-authorization-receipt-v1",
  status: "DERIVED_FROM_ACTIVE_STANDING_GRANT_BEFORE_NETWORK",
  createdAt,
  milestoneId: grant.milestoneId,
  purpose: "TRACK_B_EVALUATION",
  standingGrantPath: grantRelativePath,
  standingGrantSha256: sha256(grantBytes),
  dispatchPath: dispatchRelativePath,
  dispatchSha256: sha256(dispatchBytes),
  actualInputPath: modelInputRelativePath,
  actualInputSha256: sha256(modelInputBytes),
  providerCallSetPath: callSetRelativePath,
  providerCallSetSha256: sha256(callSetBytes),
  outboundRequestSha256s: callSet.calls.map(
    (call) => call.outboundRequestSha256
  ),
  endpointOrigin: grant.provider.endpointOrigin,
  model: TRACK_B_HOLDOUT_MODEL,
  callCount: callSet.callCount,
  inputCount: eligiblePackets.length,
  excludedZeroCallControlCount: zeroCallPackets.length,
  humanGroundTruthExcludedFromPayload: true,
  cqcpActualOrExpectedExcludedFromPayload: true,
  findingOrVerdictExcludedFromPayload: true,
  secretOrRawKeyIncluded: false,
  networkCallPerformed: false
};
validateDerivedReceipt(receipt, grant.derivedReceipt.requiredBindings);
const receiptBytes = jsonBytes(receipt);

const outputRoot = resolveRelative(outputRootRelative);
const finalRunRoot = resolveRelative(runRootRelative);
await mkdir(outputRoot, { recursive: true });
const tempRunRoot = await mkdtemp(resolve(outputRoot, ".run-v1-tmp-"));
if (!tempRunRoot.startsWith(`${outputRoot}${sep}`)) {
  throw new Error("TRACK_B_HOLDOUT_TEMP_PATH_ESCAPED");
}
try {
  await writeFile(resolve(tempRunRoot, "model-input.json"), modelInputBytes, {
    flag: "wx"
  });
  await writeFile(resolve(tempRunRoot, "provider-call-set.json"), callSetBytes, {
    flag: "wx"
  });
  await writeFile(resolve(tempRunRoot, "dispatch.json"), dispatchBytes, {
    flag: "wx"
  });
  await writeFile(
    resolve(tempRunRoot, "derived-egress-authorization-receipt.json"),
    receiptBytes,
    { flag: "wx" }
  );
  await rename(tempRunRoot, finalRunRoot);
} catch (error) {
  await rm(tempRunRoot, { recursive: true, force: true });
  throw error;
}

process.stdout.write(`${JSON.stringify({
  status: dispatch.status,
  modelInputSha256: dispatch.modelInputSha256,
  providerCallSetSha256: dispatch.providerCallSetSha256,
  dispatchSha256: sha256(dispatchBytes),
  derivedReceiptSha256: sha256(receiptBytes),
  providerCallCount: dispatch.providerCallCount,
  eligiblePacketCount: dispatch.eligiblePacketCount,
  zeroCallControlCount: dispatch.zeroCallControlCount,
  networkCallPerformed: false
})}\n`);

function validateDerivedReceipt(value, requiredBindings) {
  for (const field of requiredBindings) {
    if (field === "createdAt") assert.equal(value.createdAt, createdAt);
    else if (field === "actualInputSha256") {
      assert.equal(value.actualInputSha256, sha256(modelInputBytes));
    } else if (field === "dispatchSha256") {
      assert.equal(value.dispatchSha256, sha256(dispatchBytes));
    } else if (field === "providerCallSetSha256") {
      assert.equal(value.providerCallSetSha256, sha256(callSetBytes));
    } else if (field === "outboundRequestSha256s") {
      assert.equal(value.outboundRequestSha256s.length, callSet.callCount);
      assert.equal(
        new Set(value.outboundRequestSha256s).size,
        callSet.callCount
      );
    } else if (field === "endpointOrigin") {
      assert.equal(value.endpointOrigin, grant.provider.endpointOrigin);
    } else if (field === "model") {
      assert.equal(value.model, TRACK_B_HOLDOUT_MODEL);
    } else if (field === "callCount") {
      assert.equal(value.callCount, TRACK_B_HOLDOUT_COUNTS.providerCalls);
    } else if (field === "inputCount") {
      assert.equal(value.inputCount, TRACK_B_HOLDOUT_COUNTS.eligible);
    } else {
      throw new Error(`Unsupported standing grant binding: ${field}`);
    }
  }
}

function readRelative(relativePath) {
  return readFile(resolveRelative(relativePath));
}

function resolveRelative(relativePath) {
  const resolved = resolve(repoRoot, ...relativePath.split("/"));
  if (!resolved.startsWith(`${repoRoot}${sep}`)) {
    throw new Error("TRACK_B_HOLDOUT_PATH_ESCAPED");
  }
  return resolved;
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
