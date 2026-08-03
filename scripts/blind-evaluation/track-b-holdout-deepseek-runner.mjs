import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  assertDeepSeekTlsEnvironment,
  postDeepSeekJson,
  resolveAndValidateDeepSeekAddresses
} from "./deepseek-secure-transport.mjs";
import {
  TRACK_B_FIFTH_TRACK,
  TRACK_B_FINAL_TRACK,
  TRACK_B_HOLDOUT_COUNTS,
  TRACK_B_HOLDOUT_ENDPOINT_HOST,
  TRACK_B_HOLDOUT_MODEL,
  TRACK_B_HOLDOUT_TRACK,
  TRACK_B_SUCCESSOR_TRACK
} from "./track-b-holdout-constants.mjs";
import {
  validateMvp002StandingEgressGrant
} from "./mvp002-standing-egress-grant.mjs";
import {
  validateTrackBFifthDeepSeekEnvelope,
  validateTrackBFifthModelInput,
  validateTrackBFinalDeepSeekEnvelope,
  validateTrackBFinalModelInput,
  validateTrackBHoldoutDeepSeekEnvelope,
  validateTrackBHoldoutModelInput,
  validateTrackBSuccessorDeepSeekEnvelope,
  validateTrackBSuccessorModelInput
} from "./track-b-holdout-opinion-contract.mjs";
import {
  buildTrackBFifthProviderCallSetArtifact,
  buildTrackBFifthProviderCalls,
  buildTrackBFinalProviderCallSetArtifact,
  buildTrackBFinalProviderCalls,
  buildTrackBHoldoutProviderCallSetArtifact,
  buildTrackBHoldoutProviderCalls,
  buildTrackBSuccessorProviderCallSetArtifact,
  buildTrackBSuccessorProviderCalls
} from "./track-b-holdout-provider-request-contract.mjs";
import {
  parseJsonBytesRejectDuplicateKeys
} from "./strict-json.mjs";
import {
  TRACK_B_DEEPSEEK_RESOLVER_VERSION,
  buildTrackBFifthDeepSeekExecutionClaim,
  buildTrackBFinalDeepSeekExecutionClaim,
  buildTrackBDeepSeekExecutionClaim,
  buildTrackBSuccessorDeepSeekExecutionClaim
} from "./track-b-deepseek-execution-claim-contract.mjs";

const pathsFor = (
  runRoot,
  prompt = "scripts/blind-evaluation/track-b-holdout-opinion-prompt.txt"
) => Object.freeze({
  input: `${runRoot}/model-input.json`,
  callSet: `${runRoot}/provider-call-set.json`,
  dispatch: `${runRoot}/dispatch.json`,
  receipt: `${runRoot}/derived-egress-authorization-receipt.json`,
  claim: `${runRoot}/deepseek-execution-claim.json`,
  output: `${runRoot}/deepseek-opinion.json`,
  prompt,
  grant: "scripts/blind-evaluation/mvp002-standing-egress-grant.json"
});

const HOLDOUT_PROFILE = Object.freeze({
  paths: pathsFor("outputs/task-eval-002/track-b-holdout-v1/run-v1"),
  outputRoot: "outputs/task-eval-002/track-b-holdout-v1",
  dispatchSchema: "task-eval-002-track-b-holdout-dispatch-v1",
  blockedSchema: "task-eval-002-track-b-holdout-deepseek-run-v1",
  opinionSchema: "task-eval-002-track-b-holdout-model-opinion-v1",
  track: TRACK_B_HOLDOUT_TRACK,
  safeResolverAttempts: 1,
  validateModelInput: validateTrackBHoldoutModelInput,
  validateEnvelope: validateTrackBHoldoutDeepSeekEnvelope,
  buildProviderCalls: buildTrackBHoldoutProviderCalls,
  buildProviderCallSet: buildTrackBHoldoutProviderCallSetArtifact,
  buildExecutionClaim: buildTrackBDeepSeekExecutionClaim
});

const SUCCESSOR_PROFILE = Object.freeze({
  paths: pathsFor("outputs/task-eval-003/track-b-successor-v1/run-v1"),
  outputRoot: "outputs/task-eval-003/track-b-successor-v1",
  dispatchSchema: "task-eval-003-track-b-successor-dispatch-v1",
  blockedSchema: "task-eval-003-track-b-successor-deepseek-run-v1",
  opinionSchema: "task-eval-003-track-b-successor-model-opinion-v1",
  track: TRACK_B_SUCCESSOR_TRACK,
  safeResolverAttempts: 2,
  validateModelInput: validateTrackBSuccessorModelInput,
  validateEnvelope: validateTrackBSuccessorDeepSeekEnvelope,
  buildProviderCalls: buildTrackBSuccessorProviderCalls,
  buildProviderCallSet: buildTrackBSuccessorProviderCallSetArtifact,
  buildExecutionClaim: buildTrackBSuccessorDeepSeekExecutionClaim
});

const FINAL_PROFILE = Object.freeze({
  paths: pathsFor("outputs/task-eval-004/track-b-final-v1/run-v1"),
  outputRoot: "outputs/task-eval-004/track-b-final-v1",
  dispatchSchema: "task-eval-004-track-b-final-dispatch-v1",
  blockedSchema: "task-eval-004-track-b-final-deepseek-run-v1",
  opinionSchema: "task-eval-004-track-b-final-model-opinion-v1",
  track: TRACK_B_FINAL_TRACK,
  safeResolverAttempts: 2,
  validateModelInput: validateTrackBFinalModelInput,
  validateEnvelope: validateTrackBFinalDeepSeekEnvelope,
  buildProviderCalls: buildTrackBFinalProviderCalls,
  buildProviderCallSet: buildTrackBFinalProviderCallSetArtifact,
  buildExecutionClaim: buildTrackBFinalDeepSeekExecutionClaim
});

const FIFTH_PROFILE = Object.freeze({
  paths: pathsFor(
    "outputs/task-eval-005/track-b-fifth-v1/run-v1",
    "scripts/blind-evaluation/track-b-schema-diagnostic-prompt-v2.txt"
  ),
  outputRoot: "outputs/task-eval-005/track-b-fifth-v1",
  dispatchSchema: "task-eval-005-track-b-fifth-dispatch-v2",
  blockedSchema: "task-eval-005-track-b-fifth-deepseek-run-v2",
  opinionSchema: "task-eval-005-track-b-fifth-model-opinion-v2",
  track: TRACK_B_FIFTH_TRACK,
  safeResolverAttempts: 2,
  validateModelInput: validateTrackBFifthModelInput,
  validateEnvelope: validateTrackBFifthDeepSeekEnvelope,
  buildProviderCalls: buildTrackBFifthProviderCalls,
  buildProviderCallSet: buildTrackBFifthProviderCallSetArtifact,
  buildExecutionClaim: buildTrackBFifthDeepSeekExecutionClaim
});

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

export class TrackBHoldoutRunnerExit extends Error {
  constructor(exitCode) {
    super(`TRACK_B_HOLDOUT_RUNNER_EXIT_${exitCode}`);
    this.exitCode = exitCode;
  }
}

export async function runTrackBHoldoutDeepSeek(options = {}) {
  return runTrackBDeepSeek({ ...options, profile: HOLDOUT_PROFILE });
}

export async function runTrackBSuccessorDeepSeek(options = {}) {
  return runTrackBDeepSeek({ ...options, profile: SUCCESSOR_PROFILE });
}

export async function runTrackBFinalDeepSeek(options = {}) {
  return runTrackBDeepSeek({ ...options, profile: FINAL_PROFILE });
}

export async function runTrackBFifthDeepSeek(options = {}) {
  return runTrackBDeepSeek({ ...options, profile: FIFTH_PROFILE });
}

async function runTrackBDeepSeek({
  argv = process.argv.slice(2),
  environment = process.env,
  dependencies = {},
  profile
} = {}) {
  const [repoArg, mode = "run"] = argv;
  if (!repoArg || !["run", "preflight"].includes(mode)) {
    throw new Error(
      "Usage: node track-b-holdout-deepseek-runner.mjs " +
        "<repo-root> [run|preflight]"
    );
  }
  const now = dependencies.now ?? (() => new Date());
  const resolveAddresses =
    dependencies.resolveAddresses ?? resolveAndValidateDeepSeekAddresses;
  const transport = dependencies.transport ?? postDeepSeekJson;
  const afterClaim = dependencies.afterClaim ?? (async () => {});
  const beforeSend = dependencies.beforeSend ?? (async () => {});
  const afterResponse = dependencies.afterResponse ?? (async () => {});
  const stop = (exitCode) => {
    throw new TrackBHoldoutRunnerExit(exitCode);
  };
  const repoRoot = resolve(repoArg);
  const paths = profile.paths;
  const startedAt = now().toISOString();
  const stageDeadlineAt = new Date(
    Date.parse(startedAt) + 12 * 60_000
  ).toISOString();
  const resolveRelative = (relativePath) =>
    resolve(repoRoot, ...relativePath.split("/"));

  const inputEvidence = await readJson(paths.input);
  const callSetEvidence = await readJson(paths.callSet);
  const dispatchEvidence = await readJson(paths.dispatch);
  const receiptEvidence = await readJson(paths.receipt);
  const grantEvidence = await readJson(paths.grant);
  const promptBytes = await readFile(resolveRelative(paths.prompt));
  const input = profile.validateModelInput(inputEvidence.value);
  const grant = validateMvp002StandingEgressGrant(grantEvidence.value);
  const calls = profile.buildProviderCalls(input, promptBytes);
  const expectedCallSet = {
    ...profile.buildProviderCallSet(input, promptBytes),
    status: "FROZEN_AFTER_HUMAN_GROUND_TRUTH_SEAL",
    humanGroundTruthSealSha256:
      callSetEvidence.value.humanGroundTruthSealSha256,
    standingGrantSha256: sha256(grantEvidence.bytes)
  };
  assert.match(
    expectedCallSet.humanGroundTruthSealSha256,
    /^[a-f0-9]{64}$/
  );
  assert.deepEqual(
    callSetEvidence.value,
    expectedCallSet,
    "TRACK_B_HOLDOUT_CALL_SET_CHANGED"
  );
  const dispatch = dispatchEvidence.value;
  validateDispatch({
    dispatch,
    corpusSha256: input.sourceCorpusSha256,
    inputSha256: sha256(inputEvidence.bytes),
    callSetSha256: sha256(callSetEvidence.bytes),
    grantSha256: sha256(grantEvidence.bytes),
    promptSha256: sha256(promptBytes),
    humanGroundTruthSealSha256:
      expectedCallSet.humanGroundTruthSealSha256,
    profile
  });
  assert.ok(
    Date.parse(startedAt) >= Date.parse(dispatch.createdAt),
    "TRACK_B_HOLDOUT_RUN_PRECEDES_DISPATCH"
  );
  const derivedReceipt = receiptEvidence.value;
  validateDerivedReceipt({
    receipt: derivedReceipt,
    dispatch,
    dispatchSha256: sha256(dispatchEvidence.bytes),
    inputSha256: sha256(inputEvidence.bytes),
    callSetSha256: sha256(callSetEvidence.bytes),
    grantSha256: sha256(grantEvidence.bytes),
    requestSha256s: calls.map((call) => call.outboundRequestSha256),
    paths
  });
  if (mode === "preflight") {
    return {
      status: "PREFLIGHT_VALIDATED_NO_NETWORK",
      modelInputSha256: sha256(inputEvidence.bytes),
      dispatchSha256: sha256(dispatchEvidence.bytes),
      providerCallSetSha256: sha256(callSetEvidence.bytes),
      derivedReceiptSha256: sha256(receiptEvidence.bytes),
      providerCallCount: calls.length,
      networkAttempted: false
    };
  }

  assertDeepSeekTlsEnvironment(environment);
  const outputPath = resolveRelative(paths.output);
  const claimPath = resolveRelative(paths.claim);
  const block = async (
    code,
    networkAttempted = false,
    detailCode,
    metadata = {}
  ) => {
    const {
      automaticRetryPerformed = false,
      ...blockMetadata
    } = metadata;
    const blockedPath = resolve(
      dirname(outputPath),
      "blocked",
      `deepseek-blocked-${startedAt.replaceAll(":", "-")}-${process.pid}.json`
    );
    await mkdir(dirname(blockedPath), { recursive: true });
    await writeFile(
      blockedPath,
      `${JSON.stringify({
        schemaVersion: profile.blockedSchema,
        status: "BLOCKED",
        code,
        ...(detailCode ? { detailCode } : {}),
        ...blockMetadata,
        evaluator: TRACK_B_HOLDOUT_MODEL,
        modelInputSha256: sha256(inputEvidence.bytes),
        dispatchSha256: sha256(dispatchEvidence.bytes),
        providerCallSetSha256: sha256(callSetEvidence.bytes),
        derivedReceiptSha256: sha256(receiptEvidence.bytes),
        networkAttempted,
        automaticRetryPerformed,
        startedAt,
        completedAt: now().toISOString()
      }, null, 2)}\n`,
      { encoding: "utf8", flag: "wx" }
    );
  };

  const secret = environment.DEEPSEEK_API_KEY;
  if (typeof secret !== "string" || !secret.trim()) {
    await block("SECRET_MISSING");
    stop(4);
  }
  let pinnedAddresses;
  let resolverAttemptCount = 0;
  while (
    !pinnedAddresses &&
    resolverAttemptCount < profile.safeResolverAttempts
  ) {
    resolverAttemptCount += 1;
    try {
      pinnedAddresses = await resolveAddresses({
        deadlineAt: stageDeadlineAt,
        timeoutMs: 60_000
      });
    } catch {
      if (resolverAttemptCount >= profile.safeResolverAttempts) {
        const safePreSendRetryCount = resolverAttemptCount - 1;
        await block(
          "ENDPOINT_DNS_REJECTED",
          false,
          safePreSendRetryCount > 0
            ? "SAFE_PRE_SEND_DNS_RETRY_EXHAUSTED"
            : undefined,
          {
            resolverAttemptCount,
            safePreSendRetryCount,
            automaticRetryPerformed: safePreSendRetryCount > 0
          }
        );
        stop(4);
      }
    }
  }
  const safePreSendRetryCount = resolverAttemptCount - 1;
  const pinnedAddressSetSha256 = sha256(
    Buffer.from(JSON.stringify(pinnedAddresses), "utf8")
  );
  const claim = profile.buildExecutionClaim({
    dispatchSha256: sha256(dispatchEvidence.bytes),
    modelInputSha256: sha256(inputEvidence.bytes),
    providerCallSetSha256: sha256(callSetEvidence.bytes),
    providerCallCount: calls.length,
    egressAuthorizationSha256: sha256(grantEvidence.bytes),
    egressChallengeSha256: sha256(receiptEvidence.bytes),
    pinnedAddressSetSha256,
    claimedAt: now().toISOString()
  });
  const claimBytes = Buffer.from(
    `${JSON.stringify(claim, null, 2)}\n`,
    "utf8"
  );
  try {
    await writeFile(claimPath, claimBytes, { flag: "wx" });
  } catch (error) {
    if (error?.code === "EEXIST") {
      throw new Error("Track B holdout DeepSeek execution already claimed");
    }
    throw error;
  }
  await afterClaim({ claimPath, claim, claimSha256: sha256(claimBytes) });

  const acceptedOpinions = [];
  const providerResponses = [];
  for (const [callIndex, call] of calls.entries()) {
    const metadata = {
      failedCallId: call.callId,
      failedCallIndex: callIndex,
      completedCallCount: providerResponses.length
    };
    let response;
    let networkAttempted = false;
    try {
      await beforeSend({ call, callIndex });
      networkAttempted = true;
      response = await transport({
        body: call.outboundRequestBytes,
        secret,
        addresses: pinnedAddresses,
        timeoutMs: 60_000,
        deadlineAt: stageDeadlineAt,
        environment
      });
      await afterResponse({ call, callIndex, response });
    } catch (error) {
      await block(
        error?.code === "RESPONSE_TOO_LARGE"
          ? "SCHEMA_INVALID_OR_EMPTY"
          : "NETWORK_OR_TIMEOUT",
        networkAttempted,
        error?.code === "RESPONSE_TOO_LARGE"
          ? "RESPONSE_TOO_LARGE"
          : "UNKNOWN_SIDE_EFFECT_NO_RETRY",
        metadata
      );
      stop(5);
    }
    if (response.status < 200 || response.status >= 300) {
      await block(
        response.status === 401 || response.status === 403
          ? "AUTHENTICATION_FAILED"
          : response.status === 429
            ? "RATE_LIMITED"
            : response.status >= 500
              ? "UPSTREAM_5XX"
              : "UPSTREAM_REJECTED",
        true,
        undefined,
        metadata
      );
      stop(6);
    }
    if (response.contentTypeClass !== "APPLICATION_JSON") {
      await block(
        "SCHEMA_INVALID_OR_EMPTY",
        true,
        "RESPONSE_MEDIA_TYPE_INVALID",
        metadata
      );
      stop(7);
    }
    let envelope;
    try {
      envelope = parseJsonBytesRejectDuplicateKeys(response.body);
    } catch {
      await block(
        "SCHEMA_INVALID_OR_EMPTY",
        true,
        "RESPONSE_BODY_NOT_JSON",
        metadata
      );
      stop(7);
    }
    if (
      typeof envelope.id !== "string" ||
      !/^[A-Za-z0-9._:-]{1,128}$/.test(envelope.id) ||
      envelope.model !== TRACK_B_HOLDOUT_MODEL ||
      !Number.isInteger(envelope.created)
    ) {
      await block(
        "SCHEMA_INVALID_OR_EMPTY",
        true,
        "PROVIDER_RECEIPT_INVALID",
        metadata
      );
      stop(7);
    }
    let accepted;
    try {
      accepted = profile.validateEnvelope(
        call.input,
        envelope
      );
    } catch (error) {
      await block(
        "SCHEMA_INVALID_OR_EMPTY",
        true,
        error?.admissionCode ?? "UNKNOWN_VALIDATION_FAILURE",
        metadata
      );
      stop(7);
    }
    acceptedOpinions.push(...accepted.opinions);
    providerResponses.push({
      callId: call.callId,
      packetId: call.input.packets[0].packetId,
      modelInputSha256: call.modelInputSha256,
      outboundRequestSha256: call.outboundRequestSha256,
      receiptStatus: "STRICT_SCHEMA_ACCEPTED",
      finishReason: "stop",
      completedAt: now().toISOString()
    });
  }
  const opinionByPacketId = new Map(
    acceptedOpinions.map((opinion) => [opinion.packetId, opinion])
  );
  if (
    opinionByPacketId.size !== input.packetCount ||
    input.packets.some((packet) => !opinionByPacketId.has(packet.packetId))
  ) {
    await block(
      "SCHEMA_INVALID_OR_EMPTY",
      true,
      "COMBINED_PACKET_COVERAGE_INVALID",
      { completedCallCount: providerResponses.length }
    );
    stop(7);
  }
  const output = {
    schemaVersion: profile.opinionSchema,
    status: "ACCEPTED",
    track: profile.track,
    evaluator: TRACK_B_HOLDOUT_MODEL,
    modelInputSha256: sha256(inputEvidence.bytes),
    dispatchSha256: sha256(dispatchEvidence.bytes),
    providerCallSetSha256: sha256(callSetEvidence.bytes),
    standingGrantSha256: sha256(grantEvidence.bytes),
    derivedReceiptSha256: sha256(receiptEvidence.bytes),
    executionClaimSha256: sha256(claimBytes),
    resolverVersion: TRACK_B_DEEPSEEK_RESOLVER_VERSION,
    pinnedAddressSetSha256,
    providerCallCount: calls.length,
    networkAttempted: true,
    resolverAttemptCount,
    safePreSendRetryCount,
    automaticRetryPerformed: safePreSendRetryCount > 0,
    rawResponsePersisted: false,
    reasoningContentPersisted: false,
    providerResponses,
    finishReason: "stop",
    startedAt,
    completedAt: now().toISOString(),
    opinions: input.packets.map((packet) =>
      opinionByPacketId.get(packet.packetId)
    )
  };
  await writeFile(
    outputPath,
    `${JSON.stringify(output, null, 2)}\n`,
    { encoding: "utf8", flag: "wx" }
  );
  return {
    status: output.status,
    providerCallCount: output.providerCallCount,
    opinionCount: output.opinions.length,
    networkAttempted: true,
    automaticRetryPerformed: output.automaticRetryPerformed,
    outputSha256: sha256(await readFile(outputPath))
  };

  async function readJson(relativePath) {
    const bytes = await readFile(resolveRelative(relativePath));
    return {
      bytes,
      value: parseJsonBytesRejectDuplicateKeys(bytes)
    };
  }
}

function validateDispatch({
  dispatch,
  corpusSha256,
  inputSha256,
  callSetSha256,
  grantSha256,
  promptSha256,
  humanGroundTruthSealSha256,
  profile
}) {
  const paths = profile.paths;
  assert.deepEqual(Object.keys(dispatch).sort(), [
    "schemaVersion",
    "status",
    "createdAt",
    "corpusPath",
    "corpusSha256",
    "humanGroundTruthSealPath",
    "humanGroundTruthSealSha256",
    "standingGrantPath",
    "standingGrantSha256",
    "promptPath",
    "promptSha256",
    "modelInputPath",
    "modelInputSha256",
    "providerCallSetPath",
    "providerCallSetSha256",
    "derivedEgressReceiptPath",
    "endpointOrigin",
    "endpointHost",
    "model",
    "providerCallCount",
    "eligiblePacketCount",
    "zeroCallControlCount",
    "zeroCallControlsExcluded",
    "humanGroundTruthExcludedFromPayload",
    "cqcpActualOrExpectedExcludedFromPayload",
    "findingOrVerdictExcludedFromPayload",
    "networkCallPerformed"
  ].sort());
  assert.equal(dispatch.schemaVersion, profile.dispatchSchema);
  assert.equal(dispatch.status, "FROZEN_READY_FOR_BLIND_EVALUATORS");
  assert.ok(isCanonicalIso(dispatch.createdAt));
  assert.equal(
    dispatch.corpusPath,
    `${profile.outputRoot}/corpus.json`
  );
  assert.equal(dispatch.corpusSha256, corpusSha256);
  assert.equal(
    dispatch.humanGroundTruthSealPath,
    `${profile.outputRoot}/human-ground-truth.json`
  );
  assert.equal(dispatch.humanGroundTruthSealSha256, humanGroundTruthSealSha256);
  assert.equal(dispatch.standingGrantPath, paths.grant);
  assert.equal(dispatch.standingGrantSha256, grantSha256);
  assert.equal(dispatch.promptPath, paths.prompt);
  assert.equal(dispatch.promptSha256, promptSha256);
  assert.equal(dispatch.modelInputPath, paths.input);
  assert.equal(dispatch.modelInputSha256, inputSha256);
  assert.equal(dispatch.providerCallSetPath, paths.callSet);
  assert.equal(dispatch.providerCallSetSha256, callSetSha256);
  assert.equal(dispatch.derivedEgressReceiptPath, paths.receipt);
  assert.equal(dispatch.endpointOrigin, "https://api.deepseek.com:443");
  assert.equal(dispatch.endpointHost, TRACK_B_HOLDOUT_ENDPOINT_HOST);
  assert.equal(dispatch.model, TRACK_B_HOLDOUT_MODEL);
  assert.equal(dispatch.providerCallCount, TRACK_B_HOLDOUT_COUNTS.providerCalls);
  assert.equal(dispatch.eligiblePacketCount, TRACK_B_HOLDOUT_COUNTS.eligible);
  assert.equal(dispatch.zeroCallControlCount, TRACK_B_HOLDOUT_COUNTS.zeroCallControls);
  assert.equal(dispatch.zeroCallControlsExcluded, true);
  assert.equal(dispatch.humanGroundTruthExcludedFromPayload, true);
  assert.equal(dispatch.cqcpActualOrExpectedExcludedFromPayload, true);
  assert.equal(dispatch.findingOrVerdictExcludedFromPayload, true);
  assert.equal(dispatch.networkCallPerformed, false);
}

function validateDerivedReceipt({
  receipt,
  dispatch,
  dispatchSha256,
  inputSha256,
  callSetSha256,
  grantSha256,
  requestSha256s,
  paths
}) {
  assert.deepEqual(Object.keys(receipt).sort(), [
    "schemaVersion",
    "status",
    "createdAt",
    "milestoneId",
    "purpose",
    "standingGrantPath",
    "standingGrantSha256",
    "dispatchPath",
    "dispatchSha256",
    "actualInputPath",
    "actualInputSha256",
    "providerCallSetPath",
    "providerCallSetSha256",
    "outboundRequestSha256s",
    "endpointOrigin",
    "model",
    "callCount",
    "inputCount",
    "excludedZeroCallControlCount",
    "humanGroundTruthExcludedFromPayload",
    "cqcpActualOrExpectedExcludedFromPayload",
    "findingOrVerdictExcludedFromPayload",
    "secretOrRawKeyIncluded",
    "networkCallPerformed"
  ].sort());
  assert.equal(
    receipt.schemaVersion,
    "mvp002-derived-egress-authorization-receipt-v1"
  );
  assert.equal(
    receipt.status,
    "DERIVED_FROM_ACTIVE_STANDING_GRANT_BEFORE_NETWORK"
  );
  assert.equal(receipt.createdAt, dispatch.createdAt);
  assert.equal(receipt.milestoneId, "MILESTONE-MVP-002");
  assert.equal(receipt.purpose, "TRACK_B_EVALUATION");
  assert.equal(receipt.standingGrantPath, paths.grant);
  assert.equal(receipt.standingGrantSha256, grantSha256);
  assert.equal(receipt.dispatchPath, paths.dispatch);
  assert.equal(receipt.dispatchSha256, dispatchSha256);
  assert.equal(receipt.actualInputPath, paths.input);
  assert.equal(receipt.actualInputSha256, inputSha256);
  assert.equal(receipt.providerCallSetPath, paths.callSet);
  assert.equal(receipt.providerCallSetSha256, callSetSha256);
  assert.deepEqual(receipt.outboundRequestSha256s, requestSha256s);
  assert.equal(receipt.endpointOrigin, "https://api.deepseek.com:443");
  assert.equal(receipt.model, TRACK_B_HOLDOUT_MODEL);
  assert.equal(receipt.callCount, TRACK_B_HOLDOUT_COUNTS.providerCalls);
  assert.equal(receipt.inputCount, TRACK_B_HOLDOUT_COUNTS.eligible);
  assert.equal(
    receipt.excludedZeroCallControlCount,
    TRACK_B_HOLDOUT_COUNTS.zeroCallControls
  );
  assert.equal(receipt.humanGroundTruthExcludedFromPayload, true);
  assert.equal(receipt.cqcpActualOrExpectedExcludedFromPayload, true);
  assert.equal(receipt.findingOrVerdictExcludedFromPayload, true);
  assert.equal(receipt.secretOrRawKeyIncluded, false);
  assert.equal(receipt.networkCallPerformed, false);
}

const isCanonicalIso = (value) =>
  typeof value === "string" &&
  !Number.isNaN(Date.parse(value)) &&
  new Date(value).toISOString() === value;

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  try {
    const result = await runTrackBHoldoutDeepSeek();
    process.stdout.write(`${JSON.stringify(result)}\n`);
  } catch (error) {
    if (error instanceof TrackBHoldoutRunnerExit) {
      process.exitCode = error.exitCode;
    } else {
      throw error;
    }
  }
}
