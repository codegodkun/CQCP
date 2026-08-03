import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  mkdtemp,
  readFile,
  rename,
  rm,
  writeFile
} from "node:fs/promises";
import { dirname, resolve, sep } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import {
  evaluateTrackBFifthAdmission,
  evaluateTrackBFinalAdmission,
  evaluateTrackBHoldoutAdmission,
  evaluateTrackBSuccessorAdmission
} from "./track-b-holdout-admission-evaluator.mjs";
import { sha256 } from "./track-b-holdout-contract.mjs";
import { TRACK_B_HOLDOUT_MODEL } from "./track-b-holdout-constants.mjs";
import {
  validateTrackBFifthModelInput,
  validateTrackBFifthOpinionPayload,
  validateTrackBFinalModelInput,
  validateTrackBFinalOpinionPayload,
  validateTrackBHoldoutModelInput,
  validateTrackBHoldoutOpinionPayload,
  validateTrackBSuccessorModelInput,
  validateTrackBSuccessorOpinionPayload
} from "./track-b-holdout-opinion-contract.mjs";
import {
  buildTrackBFifthProviderCalls,
  buildTrackBFinalProviderCalls,
  buildTrackBHoldoutProviderCalls,
  buildTrackBSuccessorProviderCalls
} from "./track-b-holdout-provider-request-contract.mjs";
import {
  runTrackBFifthDeepSeek,
  runTrackBFinalDeepSeek,
  runTrackBHoldoutDeepSeek,
  runTrackBSuccessorDeepSeek
} from "./track-b-holdout-deepseek-runner.mjs";
import {
  parseJsonBytesRejectDuplicateKeys
} from "./strict-json.mjs";
import {
  validateTrackBFifthDeepSeekExecutionClaim,
  validateTrackBFinalDeepSeekExecutionClaim,
  validateTrackBDeepSeekExecutionClaim,
  validateTrackBSuccessorDeepSeekExecutionClaim
} from "./track-b-deepseek-execution-claim-contract.mjs";

const pathsFor = (
  outputRoot,
  prompt = "scripts/blind-evaluation/track-b-holdout-opinion-prompt.txt"
) => {
  const runRoot = `${outputRoot}/run-v1`;
  return Object.freeze({
    corpus: `${outputRoot}/corpus.json`,
    groundTruth: `${outputRoot}/human-ground-truth.json`,
    input: `${runRoot}/model-input.json`,
    callSet: `${runRoot}/provider-call-set.json`,
    dispatch: `${runRoot}/dispatch.json`,
    derivedReceipt:
      `${runRoot}/derived-egress-authorization-receipt.json`,
    grant: "scripts/blind-evaluation/mvp002-standing-egress-grant.json",
    prompt,
    codex: `${runRoot}/codex-opinion.json`,
    codexReceipt: `${runRoot}/codex-execution-receipt.json`,
    deepSeek: `${runRoot}/deepseek-opinion.json`,
    deepSeekClaim: `${runRoot}/deepseek-execution-claim.json`,
    report: `${runRoot}/unblind/admission-evaluation.json`,
    seal: `${runRoot}/unblind/admission-seal.json`
  });
};

const HOLDOUT_OUTPUT_ROOT =
  "outputs/task-eval-002/track-b-holdout-v1";
const HOLDOUT_PROFILE = Object.freeze({
  profileArg: "holdout",
  outputRoot: HOLDOUT_OUTPUT_ROOT,
  paths: pathsFor(HOLDOUT_OUTPUT_ROOT),
  reportSchema: "task-eval-002-track-b-holdout-unblind-report-v1",
  sealSchema: "task-eval-002-track-b-holdout-admission-seal-v1",
  opinionSchema: "task-eval-002-track-b-holdout-model-opinion-v1",
  codexReceiptSchema:
    "task-eval-002-track-b-holdout-codex-execution-receipt-v1",
  maxResolverAttempts: 1,
  validateModelInput: validateTrackBHoldoutModelInput,
  validateOpinionPayload: validateTrackBHoldoutOpinionPayload,
  buildProviderCalls: buildTrackBHoldoutProviderCalls,
  runDeepSeek: runTrackBHoldoutDeepSeek,
  validateClaim: validateTrackBDeepSeekExecutionClaim,
  evaluateAdmission: evaluateTrackBHoldoutAdmission
});

const SUCCESSOR_OUTPUT_ROOT =
  "outputs/task-eval-003/track-b-successor-v1";
const SUCCESSOR_PROFILE = Object.freeze({
  profileArg: "successor",
  outputRoot: SUCCESSOR_OUTPUT_ROOT,
  paths: pathsFor(SUCCESSOR_OUTPUT_ROOT),
  reportSchema: "task-eval-003-track-b-successor-unblind-report-v1",
  sealSchema: "task-eval-003-track-b-successor-admission-seal-v1",
  opinionSchema: "task-eval-003-track-b-successor-model-opinion-v1",
  codexReceiptSchema:
    "task-eval-003-track-b-successor-codex-execution-receipt-v1",
  maxResolverAttempts: 2,
  validateModelInput: validateTrackBSuccessorModelInput,
  validateOpinionPayload: validateTrackBSuccessorOpinionPayload,
  buildProviderCalls: buildTrackBSuccessorProviderCalls,
  runDeepSeek: runTrackBSuccessorDeepSeek,
  validateClaim: validateTrackBSuccessorDeepSeekExecutionClaim,
  evaluateAdmission: evaluateTrackBSuccessorAdmission
});

const FINAL_OUTPUT_ROOT =
  "outputs/task-eval-004/track-b-final-v1";
const FINAL_PROFILE = Object.freeze({
  profileArg: "final",
  outputRoot: FINAL_OUTPUT_ROOT,
  paths: pathsFor(FINAL_OUTPUT_ROOT),
  reportSchema: "task-eval-004-track-b-final-unblind-report-v1",
  sealSchema: "task-eval-004-track-b-final-admission-seal-v1",
  opinionSchema: "task-eval-004-track-b-final-model-opinion-v1",
  codexReceiptSchema:
    "task-eval-004-track-b-final-codex-execution-receipt-v1",
  maxResolverAttempts: 2,
  validateModelInput: validateTrackBFinalModelInput,
  validateOpinionPayload: validateTrackBFinalOpinionPayload,
  buildProviderCalls: buildTrackBFinalProviderCalls,
  runDeepSeek: runTrackBFinalDeepSeek,
  validateClaim: validateTrackBFinalDeepSeekExecutionClaim,
  evaluateAdmission: evaluateTrackBFinalAdmission
});

const FIFTH_OUTPUT_ROOT =
  "outputs/task-eval-005/track-b-fifth-v1";
const FIFTH_PROFILE = Object.freeze({
  profileArg: "fifth",
  outputRoot: FIFTH_OUTPUT_ROOT,
  paths: pathsFor(
    FIFTH_OUTPUT_ROOT,
    "scripts/blind-evaluation/track-b-schema-diagnostic-prompt-v2.txt"
  ),
  reportSchema: "task-eval-005-track-b-fifth-unblind-report-v2",
  sealSchema: "task-eval-005-track-b-fifth-admission-seal-v2",
  opinionSchema: "task-eval-005-track-b-fifth-model-opinion-v2",
  codexReceiptSchema:
    "task-eval-005-track-b-fifth-codex-execution-receipt-v2",
  maxResolverAttempts: 2,
  validateModelInput: validateTrackBFifthModelInput,
  validateOpinionPayload: validateTrackBFifthOpinionPayload,
  buildProviderCalls: buildTrackBFifthProviderCalls,
  runDeepSeek: runTrackBFifthDeepSeek,
  validateClaim: validateTrackBFifthDeepSeekExecutionClaim,
  evaluateAdmission: evaluateTrackBFifthAdmission
});

export async function sealAndEvaluateTrackBHoldout({
  repoRoot: repoArg,
  evaluatedAt: evaluatedAtArg,
  mode = "create"
}) {
  return sealAndEvaluateTrackB({
    repoRoot: repoArg,
    evaluatedAt: evaluatedAtArg,
    profile: HOLDOUT_PROFILE,
    mode
  });
}

export async function sealAndEvaluateTrackBSuccessor({
  repoRoot: repoArg,
  evaluatedAt: evaluatedAtArg,
  mode = "create"
}) {
  return sealAndEvaluateTrackB({
    repoRoot: repoArg,
    evaluatedAt: evaluatedAtArg,
    profile: SUCCESSOR_PROFILE,
    mode
  });
}

export async function sealAndEvaluateTrackBFinal({
  repoRoot: repoArg,
  evaluatedAt: evaluatedAtArg,
  mode = "create"
}) {
  return sealAndEvaluateTrackB({
    repoRoot: repoArg,
    evaluatedAt: evaluatedAtArg,
    profile: FINAL_PROFILE,
    mode
  });
}

export async function sealAndEvaluateTrackBFifth({
  repoRoot: repoArg,
  evaluatedAt: evaluatedAtArg,
  mode = "create"
}) {
  return sealAndEvaluateTrackB({
    repoRoot: repoArg,
    evaluatedAt: evaluatedAtArg,
    profile: FIFTH_PROFILE,
    mode
  });
}

async function sealAndEvaluateTrackB({
  repoRoot: repoArg,
  evaluatedAt: evaluatedAtArg,
  profile,
  mode
}) {
  const repoRoot = resolve(repoArg);
  assert.ok(["create", "verify"].includes(mode), `Unknown mode: ${mode}`);
  const paths = profile.paths;
  const runRoot = `${profile.outputRoot}/run-v1`;
  const resolveRelative = (relativePath) => {
    const resolved = resolve(repoRoot, ...relativePath.split("/"));
    if (!resolved.startsWith(`${repoRoot}${sep}`)) {
      throw new Error("TRACK_B_HOLDOUT_UNBLIND_PATH_ESCAPED");
    }
    return resolved;
  };
  const readJson = async (relativePath) => {
    const bytes = await readFile(resolveRelative(relativePath));
    return { bytes, value: parseJsonBytesRejectDuplicateKeys(bytes) };
  };
  const existingSealEvidence = mode === "verify"
    ? await readJson(paths.seal)
    : null;
  const evaluatedAt = canonicalIso(
    mode === "verify"
      ? existingSealEvidence.value.sealedAt
      : evaluatedAtArg,
    "evaluatedAt"
  );

  const preflight = await profile.runDeepSeek({
    argv: [repoRoot, "preflight"],
    environment: {}
  });
  assert.equal(preflight.status, "PREFLIGHT_VALIDATED_NO_NETWORK");
  assert.equal(preflight.networkAttempted, false);

  const inputEvidence = await readJson(paths.input);
  const dispatchEvidence = await readJson(paths.dispatch);
  const callSetEvidence = await readJson(paths.callSet);
  const derivedReceiptEvidence = await readJson(paths.derivedReceipt);
  const grantEvidence = await readJson(paths.grant);
  const corpusEvidence = await readJson(paths.corpus);
  const promptBytes = await readFile(resolveRelative(paths.prompt));
  const input = profile.validateModelInput(inputEvidence.value);
  const calls = profile.buildProviderCalls(input, promptBytes);
  const codexEvidence = await readJson(paths.codex);
  const codexReceiptEvidence = await readJson(paths.codexReceipt);
  const deepSeekEvidence = await readJson(paths.deepSeek);
  const deepSeekClaimEvidence = await readJson(paths.deepSeekClaim);

  await validateCodexEvidence({
    codex: codexEvidence.value,
    receipt: codexReceiptEvidence.value,
    receiptBytes: codexReceiptEvidence.bytes,
    input,
    inputSha256: sha256(inputEvidence.bytes),
    dispatchSha256: sha256(dispatchEvidence.bytes),
    callSetSha256: sha256(callSetEvidence.bytes),
    evaluatedAt,
    repoRoot,
    resolveRelative,
    profile,
    paths,
    runRoot
  });
  validateDeepSeekEvidence({
    deepSeek: deepSeekEvidence.value,
    claim: deepSeekClaimEvidence.value,
    claimBytes: deepSeekClaimEvidence.bytes,
    calls,
    input,
    inputSha256: sha256(inputEvidence.bytes),
    dispatchSha256: sha256(dispatchEvidence.bytes),
    callSetSha256: sha256(callSetEvidence.bytes),
    grantSha256: sha256(grantEvidence.bytes),
    derivedReceiptSha256: sha256(derivedReceiptEvidence.bytes),
    evaluatedAt,
    profile
  });

  // Ground truth is intentionally not opened until both blind opinions and
  // their execution evidence have been verified above.
  const groundTruthEvidence = await readJson(paths.groundTruth);
  const groundTruth = groundTruthEvidence.value;
  const verifier = resolve(
    dirname(fileURLToPath(import.meta.url)),
    "seal-track-b-holdout-human-ground-truth.mjs"
  );
  assert.equal(typeof groundTruth.confirmationPath, "string");
  const verifiedGroundTruth = spawnSync(
    process.execPath,
    [
      verifier,
      repoRoot,
      groundTruth.confirmationPath,
      "verify",
      profile.profileArg
    ],
    { cwd: repoRoot, encoding: "utf8" }
  );
  assert.equal(
    verifiedGroundTruth.status,
    0,
    verifiedGroundTruth.stderr
  );
  const evaluation = profile.evaluateAdmission({
    corpus: corpusEvidence.value,
    corpusSha256: sha256(corpusEvidence.bytes),
    humanGroundTruth: groundTruth,
    codexPayload: { opinions: codexEvidence.value.opinions },
    deepSeekPayload: { opinions: deepSeekEvidence.value.opinions }
  });
  const report = {
    schemaVersion: profile.reportSchema,
    status: evaluation.status,
    evaluatedAt,
    blindOpinionsVerifiedBeforeGroundTruthRead: true,
    sameHoldoutRetryAllowed: false,
    corpusSha256: sha256(corpusEvidence.bytes),
    humanGroundTruthSha256: sha256(groundTruthEvidence.bytes),
    modelInputSha256: sha256(inputEvidence.bytes),
    dispatchSha256: sha256(dispatchEvidence.bytes),
    providerCallSetSha256: sha256(callSetEvidence.bytes),
    codexOpinionSha256: sha256(codexEvidence.bytes),
    deepSeekOpinionSha256: sha256(deepSeekEvidence.bytes),
    evaluation
  };
  const reportBytes = Buffer.from(
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
  const seal = {
    schemaVersion: profile.sealSchema,
    status: evaluation.allGatesPassed
      ? "SEALED_GO_ALL_ADMISSION_DIMENSIONS_100_PERCENT"
      : "SEALED_NO_GO_MODEL_MISMATCH",
    sealedAt: evaluatedAt,
    providerAdmission: evaluation.providerAdmission,
    reportPath: paths.report,
    reportSha256: sha256(reportBytes),
    corpusPath: paths.corpus,
    corpusSha256: sha256(corpusEvidence.bytes),
    humanGroundTruthPath: paths.groundTruth,
    humanGroundTruthSha256: sha256(groundTruthEvidence.bytes),
    modelInputPath: paths.input,
    modelInputSha256: sha256(inputEvidence.bytes),
    dispatchPath: paths.dispatch,
    dispatchSha256: sha256(dispatchEvidence.bytes),
    providerCallSetPath: paths.callSet,
    providerCallSetSha256: sha256(callSetEvidence.bytes),
    codexOpinionPath: paths.codex,
    codexOpinionSha256: sha256(codexEvidence.bytes),
    codexReceiptPath: paths.codexReceipt,
    codexReceiptSha256: sha256(codexReceiptEvidence.bytes),
    deepSeekOpinionPath: paths.deepSeek,
    deepSeekOpinionSha256: sha256(deepSeekEvidence.bytes),
    deepSeekClaimPath: paths.deepSeekClaim,
    deepSeekClaimSha256: sha256(deepSeekClaimEvidence.bytes),
    standingGrantPath: paths.grant,
    standingGrantSha256: sha256(grantEvidence.bytes),
    derivedEgressReceiptPath: paths.derivedReceipt,
    derivedEgressReceiptSha256: sha256(derivedReceiptEvidence.bytes),
    blindOpinionsVerifiedBeforeGroundTruthRead: true,
    sameHoldoutRetryAllowed: false,
    independentThreePartyAuditRequired: true
  };
  const sealBytes = Buffer.from(
    `${JSON.stringify(seal, null, 2)}\n`,
    "utf8"
  );
  if (mode === "verify") {
    const existingReportBytes = await readFile(resolveRelative(paths.report));
    assert.deepEqual(
      existingReportBytes,
      reportBytes,
      "TRACK_B_HOLDOUT_UNBLIND_REPORT_CHANGED"
    );
    assert.deepEqual(
      existingSealEvidence.bytes,
      sealBytes,
      "TRACK_B_HOLDOUT_ADMISSION_SEAL_CHANGED"
    );
    return {
      status: "ADMISSION_SEAL_VERIFIED",
      sealedStatus: seal.status,
      providerAdmission: seal.providerAdmission,
      reportSha256: seal.reportSha256,
      sealSha256: sha256(sealBytes),
      sameHoldoutRetryAllowed: false
    };
  }
  const finalRoot = resolveRelative(`${runRoot}/unblind`);
  const parentRoot = resolveRelative(runRoot);
  const tempRoot = await mkdtemp(resolve(parentRoot, ".unblind-tmp-"));
  if (!tempRoot.startsWith(`${parentRoot}${sep}`)) {
    throw new Error("TRACK_B_HOLDOUT_UNBLIND_TEMP_PATH_ESCAPED");
  }
  try {
    await writeFile(resolve(tempRoot, "admission-evaluation.json"), reportBytes, {
      flag: "wx"
    });
    await writeFile(resolve(tempRoot, "admission-seal.json"), sealBytes, {
      flag: "wx"
    });
    await rename(tempRoot, finalRoot);
  } catch (error) {
    await rm(tempRoot, { recursive: true, force: true });
    throw error;
  }
  return {
    status: seal.status,
    providerAdmission: seal.providerAdmission,
    reportSha256: seal.reportSha256,
    sealSha256: sha256(sealBytes),
    sameHoldoutRetryAllowed: false
  };
}

function validateCodexEvidence({
  codex,
  receipt,
  receiptBytes,
  input,
  inputSha256,
  dispatchSha256,
  callSetSha256,
  evaluatedAt,
  resolveRelative,
  profile,
  paths,
  runRoot
}) {
  requireExactKeys(codex, [
    "schemaVersion",
    "status",
    "track",
    "evaluator",
    "forkTurns",
    "historicalContextIncluded",
    "agentTask",
    "agentId",
    "modelInputSha256",
    "dispatchSha256",
    "providerCallSetSha256",
    "executionReceiptPath",
    "executionReceiptSha256",
    "startedAt",
    "completedAt",
    "opinions"
  ], "codex opinion");
  requireExactKeys(receipt, [
    "schemaVersion",
    "source",
    "forkTurns",
    "historicalContextIncluded",
    "agentTask",
    "agentId",
    "modelInputSha256",
    "dispatchSha256",
    "providerCallSetSha256",
    "rawAgentOutputPath",
    "rawAgentOutputSha256",
    "startedAt",
    "completedAt",
    "transcriptCrossCheckRequired"
  ], "codex receipt");
  assert.equal(codex.schemaVersion, profile.opinionSchema);
  assert.equal(codex.status, "ACCEPTED");
  assert.equal(codex.track, input.track);
  assert.equal(codex.evaluator, "codex-blind-subagent");
  assert.equal(codex.forkTurns, "none");
  assert.equal(codex.historicalContextIncluded, false);
  assert.equal(codex.modelInputSha256, inputSha256);
  assert.equal(codex.dispatchSha256, dispatchSha256);
  assert.equal(codex.providerCallSetSha256, callSetSha256);
  assert.equal(codex.executionReceiptPath, paths.codexReceipt);
  assert.equal(codex.executionReceiptSha256, sha256(receiptBytes));
  assert.ok(Date.parse(codex.startedAt) <= Date.parse(codex.completedAt));
  assert.ok(Date.parse(codex.completedAt) <= Date.parse(evaluatedAt));
  profile.validateOpinionPayload(input, { opinions: codex.opinions });
  assert.equal(
    receipt.schemaVersion,
    profile.codexReceiptSchema
  );
  assert.equal(receipt.source, "CODEX_COLLABORATION_TOOL");
  assert.equal(receipt.forkTurns, "none");
  assert.equal(receipt.historicalContextIncluded, false);
  assert.equal(receipt.agentTask, codex.agentTask);
  assert.equal(receipt.agentId, codex.agentId);
  assert.equal(receipt.modelInputSha256, inputSha256);
  assert.equal(receipt.dispatchSha256, dispatchSha256);
  assert.equal(receipt.providerCallSetSha256, callSetSha256);
  assert.equal(receipt.startedAt, codex.startedAt);
  assert.equal(receipt.completedAt, codex.completedAt);
  assert.equal(receipt.transcriptCrossCheckRequired, true);
  assert.ok(receipt.rawAgentOutputPath.startsWith(`${runRoot}/`));
  const rawPath = resolveRelative(receipt.rawAgentOutputPath);
  return readFile(rawPath).then((bytes) => {
    assert.equal(receipt.rawAgentOutputSha256, sha256(bytes));
  });
}

function validateDeepSeekEvidence({
  deepSeek,
  claim,
  claimBytes,
  calls,
  input,
  inputSha256,
  dispatchSha256,
  callSetSha256,
  grantSha256,
  derivedReceiptSha256,
  evaluatedAt,
  profile
}) {
  requireExactKeys(deepSeek, [
    "schemaVersion",
    "status",
    "track",
    "evaluator",
    "modelInputSha256",
    "dispatchSha256",
    "providerCallSetSha256",
    "standingGrantSha256",
    "derivedReceiptSha256",
    "executionClaimSha256",
    "resolverVersion",
    "pinnedAddressSetSha256",
    "providerCallCount",
    "networkAttempted",
    "resolverAttemptCount",
    "safePreSendRetryCount",
    "automaticRetryPerformed",
    "rawResponsePersisted",
    "reasoningContentPersisted",
    "providerResponses",
    "finishReason",
    "startedAt",
    "completedAt",
    "opinions"
  ], "DeepSeek opinion");
  assert.equal(deepSeek.schemaVersion, profile.opinionSchema);
  assert.equal(deepSeek.status, "ACCEPTED");
  assert.equal(deepSeek.track, input.track);
  assert.equal(deepSeek.evaluator, TRACK_B_HOLDOUT_MODEL);
  assert.equal(deepSeek.modelInputSha256, inputSha256);
  assert.equal(deepSeek.dispatchSha256, dispatchSha256);
  assert.equal(deepSeek.providerCallSetSha256, callSetSha256);
  assert.equal(deepSeek.standingGrantSha256, grantSha256);
  assert.equal(deepSeek.derivedReceiptSha256, derivedReceiptSha256);
  assert.equal(deepSeek.executionClaimSha256, sha256(claimBytes));
  assert.equal(deepSeek.providerCallCount, calls.length);
  assert.equal(deepSeek.networkAttempted, true);
  assert.ok(
    Number.isInteger(deepSeek.resolverAttemptCount) &&
      deepSeek.resolverAttemptCount >= 1 &&
      deepSeek.resolverAttemptCount <= profile.maxResolverAttempts
  );
  assert.equal(
    deepSeek.safePreSendRetryCount,
    deepSeek.resolverAttemptCount - 1
  );
  assert.equal(
    deepSeek.automaticRetryPerformed,
    deepSeek.safePreSendRetryCount > 0
  );
  assert.equal(deepSeek.rawResponsePersisted, false);
  assert.equal(deepSeek.reasoningContentPersisted, false);
  assert.equal(deepSeek.finishReason, "stop");
  assert.ok(Date.parse(deepSeek.startedAt) <= Date.parse(deepSeek.completedAt));
  assert.ok(Date.parse(deepSeek.completedAt) <= Date.parse(evaluatedAt));
  assert.equal(deepSeek.providerResponses.length, calls.length);
  for (const [index, receipt] of deepSeek.providerResponses.entries()) {
    const call = calls[index];
    requireExactKeys(receipt, [
      "callId",
      "packetId",
      "modelInputSha256",
      "outboundRequestSha256",
      "receiptStatus",
      "finishReason",
      "completedAt"
    ], `DeepSeek receipt ${index}`);
    assert.equal(receipt.callId, call.callId);
    assert.equal(receipt.packetId, call.input.packets[0].packetId);
    assert.equal(receipt.modelInputSha256, call.modelInputSha256);
    assert.equal(receipt.outboundRequestSha256, call.outboundRequestSha256);
    assert.equal(receipt.receiptStatus, "STRICT_SCHEMA_ACCEPTED");
    assert.equal(receipt.finishReason, "stop");
    assert.ok(Date.parse(receipt.completedAt) <= Date.parse(deepSeek.completedAt));
  }
  profile.validateOpinionPayload(input, { opinions: deepSeek.opinions });
  profile.validateClaim(claim, {
    dispatchSha256,
    modelInputSha256: inputSha256,
    providerCallSetSha256: callSetSha256,
    providerCallCount: calls.length,
    egressAuthorizationSha256: grantSha256,
    egressChallengeSha256: derivedReceiptSha256,
    pinnedAddressSetSha256: deepSeek.pinnedAddressSetSha256,
    startedAt: deepSeek.startedAt,
    completedAt: deepSeek.completedAt
  });
}

function canonicalIso(value, field) {
  assert.equal(typeof value, "string", `${field} must be a string`);
  const parsed = Date.parse(value);
  assert.ok(Number.isFinite(parsed), `${field} is invalid`);
  assert.equal(new Date(parsed).toISOString(), value);
  return value;
}

function requireExactKeys(value, expected, label) {
  assert.ok(value !== null && typeof value === "object" && !Array.isArray(value));
  assert.deepEqual(
    Object.keys(value).sort(),
    [...expected].sort(),
    `${label} fields changed`
  );
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repoArg, evaluatedAtArg = new Date().toISOString(), mode = "create"] =
    process.argv.slice(2);
  const result = await sealAndEvaluateTrackBHoldout({
    repoRoot: repoArg,
    evaluatedAt: evaluatedAtArg,
    mode
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
  if (result.providerAdmission === "NOT_ESTABLISHED") {
    process.exitCode = 2;
  }
}
