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
  validateTrackBAdmissionModelInput,
  validateTrackBDeepSeekEnvelope
} from "./track-b-admission-opinion-contract.mjs";
import {
  validateTrackBEgressAuthorization
} from "./track-b-admission-authorization.mjs";
import {
  TRACK_B_ADMISSION_COUNTS,
  TRACK_B_ADMISSION_TRUSTED_PROMPT_SHA256
} from "./track-b-admission-constants.mjs";
import {
  TRACK_B_PROVIDER_CALL_COUNT,
  TRACK_B_PROVIDER_REQUEST_BUILDER_VERSION,
  buildTrackBProviderCallSetArtifact,
  buildTrackBProviderCalls
} from "./track-b-provider-request-contract.mjs";
import {
  parseJsonRejectDuplicateKeys,
} from "./strict-json.mjs";
import {
  assertDeepSeekTlsEnvironment,
  postDeepSeekJson,
  resolveAndValidateDeepSeekAddresses
} from "./deepseek-secure-transport.mjs";
import {
  readStableJsonDirectChild
} from "./stable-capability-file.mjs";
import {
  TRACK_B_DEEPSEEK_RESOLVER_VERSION,
  buildTrackBDeepSeekExecutionClaim
} from "./track-b-deepseek-execution-claim-contract.mjs";

export class TrackBRunnerExit extends Error {
  constructor(exitCode) {
    super(`TRACK_B_RUNNER_EXIT_${exitCode}`);
    this.exitCode = exitCode;
  }
}

export async function runTrackBAdmission({
  argv = process.argv.slice(2),
  environment = process.env,
  dependencies = {}
} = {}) {
const now = dependencies.now ?? (() => new Date());
const resolveAddresses =
  dependencies.resolveAddresses ?? resolveAndValidateDeepSeekAddresses;
const transport = dependencies.transport ?? postDeepSeekJson;
const afterClaim = dependencies.afterClaim ?? (async () => {});
const beforeSend = dependencies.beforeSend ?? (async () => {});
const afterResponse = dependencies.afterResponse ?? (async () => {});
const stop = (exitCode) => {
  throw new TrackBRunnerExit(exitCode);
};
const [
  repoArg,
  inputArg,
  outputArg,
  dispatchArg,
  authorizationArg,
  mode = "run"
] = argv;
if (
  !repoArg ||
  !inputArg ||
  !outputArg ||
  !dispatchArg ||
  !authorizationArg
) {
  throw new Error(
    "Usage: node track-b-admission-deepseek-runner.mjs " +
      "<repo-root> <model-input> <output> <dispatch> <authorization> [preflight]"
  );
}
if (!["run", "preflight"].includes(mode)) {
  throw new Error("mode must be run or preflight");
}
if (mode === "run") {
  assertDeepSeekTlsEnvironment(environment);
}

const repoRoot = resolve(repoArg);
const inputPath = resolve(inputArg);
const outputPath = resolve(outputArg);
const dispatchPath = resolve(dispatchArg);
const authorizationPath = resolve(authorizationArg);
const startedAt = now().toISOString();
const stageTimeoutAt = new Date(
  Date.parse(startedAt) + 7 * 60_000,
).toISOString();
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const inputBytes = await readFile(inputPath);
const inputSha256 = sha256(inputBytes);
const input = validateTrackBAdmissionModelInput(
  JSON.parse(inputBytes.toString("utf8"))
);
const dispatchBytes = await readFile(dispatchPath);
const dispatchSha256 = sha256(dispatchBytes);
const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
const resolveRelative = (relativePath) =>
  resolve(repoRoot, ...relativePath.split("/"));
const scriptRoot = dirname(fileURLToPath(import.meta.url));
if (
  mode === "run" &&
  outputPath !==
    resolve(
      repoRoot,
      "outputs/task-eval-002/track-b-admission-run-v3/" +
        "deepseek-opinion.json"
    )
) {
  throw new Error(
    "Production DeepSeek run output must be the immutable opinion path"
  );
}

const block = async (code, networkAttempted = false, detailCode, metadata) => {
  const blockedOutputPath =
    mode === "run"
      ? resolve(
          dirname(outputPath),
          "blocked",
          `deepseek-blocked-${startedAt.replaceAll(":", "-")}-${process.pid}.json`
        )
      : outputPath;
  await mkdir(dirname(blockedOutputPath), { recursive: true });
  await writeFile(
    blockedOutputPath,
    `${JSON.stringify(
      {
        schemaVersion:
          "task-eval-002-track-b-admission-deepseek-run-v1",
        status: "BLOCKED",
        code,
        ...(detailCode ? { detailCode } : {}),
        ...(metadata ?? {}),
        evaluator: "deepseek-v4-pro",
        inputSha256,
        dispatchSha256,
        networkAttempted,
        blockedOutputPath:
          mode === "run"
            ? blockedOutputPath
                .slice(repoRoot.length + 1)
                .replaceAll("\\", "/")
            : null,
        startedAt,
        completedAt: now().toISOString()
      },
      null,
      2
    )}\n`,
    { encoding: "utf8", flag: "wx" }
  );
};

const DISPATCH_FIELDS = new Set([
  "schemaVersion",
  "status",
  "createdAt",
  "track",
  "forkTurns",
  "historicalContextIncluded",
  "codexAgentTask",
  "corpusPath",
  "corpusSha256",
  "humanGroundTruthSealPath",
  "humanGroundTruthSealSha256",
  "humanGroundTruthExcludedFromModelInput",
  "promptPath",
  "promptSha256",
  "modelInputPath",
  "modelInputSha256",
  "providerCallSetPath",
  "providerCallSetSha256",
  "providerRequestBuilderVersion",
  "providerCallCount",
  "egressAuthorizationChallengePath",
  "egressAuthorizationPath",
  "egressAuthorizationRequired",
  "eligiblePacketCount",
  "zeroCallControlCount",
  "unblindProhibitedUntilBothModelOpinionsSealed"
]);
if (
  Object.keys(dispatch).length !== DISPATCH_FIELDS.size ||
  Object.keys(dispatch).some((key) => !DISPATCH_FIELDS.has(key)) ||
  dispatch.schemaVersion !==
    "task-eval-002-track-b-admission-dispatch-v1" ||
  dispatch.status !==
    "FROZEN_AFTER_HUMAN_CONFIRMATION_BEFORE_MODEL_EXECUTION" ||
  dispatch.modelInputSha256 !== inputSha256 ||
  dispatch.track !== input.track ||
  dispatch.forkTurns !== "none" ||
  dispatch.historicalContextIncluded !== false ||
  !/^\/root\/[a-z0-9_]+$/.test(dispatch.codexAgentTask) ||
  dispatch.corpusPath !==
    "outputs/task-eval-002/track-b-admission-corpus-v2/corpus.json" ||
  dispatch.humanGroundTruthSealPath !==
    "outputs/task-eval-002/track-b-admission-corpus-v2/" +
      "human-ground-truth.json" ||
  dispatch.humanGroundTruthExcludedFromModelInput !== true ||
  dispatch.promptPath !==
    "scripts/blind-evaluation/track-b-admission-opinion-prompt.txt" ||
  dispatch.modelInputPath !==
    "outputs/task-eval-002/track-b-admission-run-v3/model-input.json" ||
  dispatch.providerCallSetPath !==
    "outputs/task-eval-002/track-b-admission-run-v3/provider-call-set.json" ||
  dispatch.providerRequestBuilderVersion !==
    TRACK_B_PROVIDER_REQUEST_BUILDER_VERSION ||
  dispatch.providerCallCount !== TRACK_B_PROVIDER_CALL_COUNT ||
  dispatch.egressAuthorizationChallengePath !==
    "outputs/task-eval-002/track-b-admission-run-v3/" +
      "egress-authorization-challenge.json" ||
  dispatch.egressAuthorizationPath !==
    "outputs/task-eval-002/track-b-admission-run-v3/" +
      "egress-authorization.json" ||
  dispatch.egressAuthorizationRequired !== true ||
  dispatch.eligiblePacketCount !== TRACK_B_ADMISSION_COUNTS.eligible ||
  dispatch.zeroCallControlCount !==
    TRACK_B_ADMISSION_COUNTS.zeroCallControls ||
  dispatch.unblindProhibitedUntilBothModelOpinionsSealed !== true
) {
  await block("DISPATCH_INVALID");
  stop(3);
}
if (resolveRelative(dispatch.modelInputPath) !== inputPath) {
  await block("DISPATCH_INVALID");
  stop(3);
}
if (resolveRelative(dispatch.egressAuthorizationPath) !== authorizationPath) {
  await block("DISPATCH_INVALID");
  stop(3);
}
const promptPath = resolveRelative(dispatch.promptPath);
const promptBytes = await readFile(promptPath);
if (
  sha256(promptBytes) !== dispatch.promptSha256 ||
  dispatch.promptSha256 !== TRACK_B_ADMISSION_TRUSTED_PROMPT_SHA256
) {
  await block("DISPATCH_INVALID");
  stop(3);
}
const humanSealPath = resolveRelative(dispatch.humanGroundTruthSealPath);
let humanSeal;
try {
  const humanSealBytes = await readFile(humanSealPath);
  humanSeal = JSON.parse(humanSealBytes.toString("utf8"));
  assert.equal(
    sha256(humanSealBytes),
    dispatch.humanGroundTruthSealSha256
  );
  assert.equal(humanSeal.status, "ACCEPTED_HUMAN_GROUND_TRUTH");
  const identityMigrated =
    humanSeal.schemaVersion ===
      "task-eval-002-track-b-human-ground-truth-v2" &&
    humanSeal.confirmationKind === "IDENTITY_ONLY_CARRY_FORWARD" &&
    humanSeal.externalEgressAuthorized === false;
  const fullyReviewedV2Corpus =
    humanSeal.schemaVersion ===
      "task-eval-002-track-b-human-ground-truth-v1" &&
    typeof humanSeal.confirmationPath === "string";
  assert.ok(identityMigrated || fullyReviewedV2Corpus);
  assert.equal(humanSeal.entryCount, TRACK_B_ADMISSION_COUNTS.total);
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
        ? humanSeal.identityMigrationConfirmationPath
        : humanSeal.confirmationPath,
      "verify"
    ],
    { cwd: repoRoot, encoding: "utf8" }
  );
  assert.equal(
    verification.status,
    0,
    verification.stderr || "Human ground truth verification failed"
  );
} catch {
  await block("HUMAN_GROUND_TRUTH_SEAL_INVALID");
  stop(3);
}
const corpusPath = resolveRelative(dispatch.corpusPath);
try {
  const corpusBytes = await readFile(corpusPath);
  assert.equal(sha256(corpusBytes), dispatch.corpusSha256);
  assert.equal(input.sourceCorpusSha256, dispatch.corpusSha256);
  const validated = validateTrackBAdmissionCorpus(
    JSON.parse(corpusBytes.toString("utf8")),
    dispatch.corpusSha256
  );
  assert.deepEqual(input.packets, validated.eligiblePackets);
} catch {
  await block("CORPUS_OR_MODEL_INPUT_INVALID");
  stop(3);
}

let providerCalls;
try {
  providerCalls = buildTrackBProviderCalls(input, promptBytes);
  const expectedCallSet = buildTrackBProviderCallSetArtifact(
    input,
    promptBytes
  );
  const expectedCallSetBytes = Buffer.from(
    `${JSON.stringify(expectedCallSet, null, 2)}\n`,
    "utf8"
  );
  const callSetBytes = await readFile(
    resolveRelative(dispatch.providerCallSetPath)
  );
  assert.equal(
    sha256(callSetBytes),
    dispatch.providerCallSetSha256
  );
  assert.equal(
    sha256(expectedCallSetBytes),
    dispatch.providerCallSetSha256
  );
  assert.deepEqual(
    JSON.parse(callSetBytes.toString("utf8")),
    expectedCallSet
  );
} catch {
  await block("PROVIDER_CALL_SET_INVALID");
  stop(3);
}

let challenge;
let challengeSha256;
try {
  const { bytes: challengeBytes, value } =
    readStableJsonDirectChild({
      repoRoot,
      relativePath: dispatch.egressAuthorizationChallengePath,
      requiredRoot:
        "outputs/task-eval-002/track-b-admission-run-v3/"
    });
  challengeSha256 = sha256(challengeBytes);
  challenge = value;
} catch {
  await block("EXTERNAL_EGRESS_CHALLENGE_INVALID");
  stop(3);
}
let authorization;
let authorizationBytes;
try {
  const stableAuthorization = readStableJsonDirectChild({
    repoRoot,
    relativePath: dispatch.egressAuthorizationPath,
    requiredRoot:
      "outputs/task-eval-002/track-b-admission-run-v3/"
  });
  authorizationBytes = stableAuthorization.bytes;
  authorization = stableAuthorization.value;
} catch {
  await block("EXTERNAL_EGRESS_NOT_AUTHORIZED");
  if (mode === "preflight") stop(0);
  stop(3);
}
let authorizationEvidence;
try {
  authorizationEvidence = validateTrackBEgressAuthorization({
    authorization,
    challenge,
    challengeSha256,
    inputSha256,
    dispatchSha256,
    dispatchCreatedAt: dispatch.createdAt,
    validationTime: startedAt
  });
  assert.equal(
    challenge.providerCallSetSha256,
    dispatch.providerCallSetSha256
  );
  assert.equal(
    challenge.providerRequestBuilderVersion,
    dispatch.providerRequestBuilderVersion
  );
  assert.equal(challenge.providerCallCount, providerCalls.length);
  assert.deepEqual(
    challenge.outboundRequestSha256s,
    providerCalls.map((call) => call.outboundRequestSha256)
  );
} catch {
  await block("EXTERNAL_EGRESS_NOT_AUTHORIZED");
  if (mode === "preflight") stop(0);
  stop(3);
}
if (mode === "preflight") {
  await block("PREFLIGHT_AUTHORIZED_NO_NETWORK");
  stop(0);
}

const secret = environment.DEEPSEEK_API_KEY;
if (!secret?.trim()) {
  await block("SECRET_MISSING");
  stop(4);
}

let pinnedAddresses;
try {
  pinnedAddresses = await resolveAddresses({
    deadlineAt: stageTimeoutAt,
    timeoutMs: 60_000,
  });
} catch {
  await block("ENDPOINT_DNS_REJECTED");
  stop(4);
}
const pinnedAddressSetSha256 = sha256(
  Buffer.from(JSON.stringify(pinnedAddresses), "utf8")
);

let executionClaimSha256 = null;
let executionClaim = null;
if (mode === "run") {
  const claimPath = resolve(
    repoRoot,
    "outputs/task-eval-002/track-b-admission-run-v3/" +
      "deepseek-execution-claim.json"
  );
  executionClaim = buildTrackBDeepSeekExecutionClaim({
    dispatchSha256,
    modelInputSha256: inputSha256,
    providerCallSetSha256: dispatch.providerCallSetSha256,
    providerCallCount: providerCalls.length,
    egressAuthorizationSha256: sha256(authorizationBytes),
    egressChallengeSha256: challengeSha256,
    pinnedAddressSetSha256,
    claimedAt: now().toISOString()
  });
  const claimBytes = Buffer.from(
    `${JSON.stringify(executionClaim, null, 2)}\n`,
    "utf8"
  );
  try {
    await writeFile(claimPath, claimBytes, { flag: "wx" });
  } catch (error) {
    if (error?.code === "EEXIST") {
      throw new Error(
        "Track B DeepSeek authorization was already claimed"
      );
    }
    throw error;
  }
  executionClaimSha256 = sha256(claimBytes);
  await afterClaim({
    claimPath,
    claim: executionClaim,
    executionClaimSha256
  });
}
const providerResponses = [];
const acceptedOpinions = [];
for (const [callIndex, call] of providerCalls.entries()) {
  const failureMetadata = {
    failedCallId: call.callId,
    failedCallIndex: callIndex,
    completedCallCount: providerResponses.length
  };
  let response;
  try {
    await beforeSend({ call, callIndex });
    response = await transport({
      body: call.outboundRequestBytes,
      secret,
      addresses: pinnedAddresses,
      timeoutMs: 60_000,
      deadlineAt: stageTimeoutAt,
      environment
    });
    await afterResponse({ call, callIndex, response });
  } catch (error) {
    await block(
      error?.code === "RESPONSE_TOO_LARGE"
        ? "SCHEMA_INVALID_OR_EMPTY"
        : "NETWORK_OR_TIMEOUT",
      true,
      error?.code === "RESPONSE_TOO_LARGE"
        ? "RESPONSE_TOO_LARGE"
        : undefined,
      failureMetadata
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
      failureMetadata
    );
    stop(6);
  }

  let responseText;
  try {
    if (response.contentTypeClass !== "APPLICATION_JSON") {
      await block(
        "SCHEMA_INVALID_OR_EMPTY",
        true,
        "RESPONSE_MEDIA_TYPE_INVALID",
        {
          ...failureMetadata,
          responseMediaTypeClass:
            ["UNSPECIFIED", "NON_JSON"].includes(
              response.contentTypeClass
            )
              ? response.contentTypeClass
              : "INVALID_CLASS"
        }
      );
      stop(7);
    }
    responseText = new TextDecoder("utf-8", { fatal: true }).decode(
      response.body
    );
  } catch (error) {
    await block(
      "SCHEMA_INVALID_OR_EMPTY",
      true,
      error?.code === "RESPONSE_TOO_LARGE"
        ? "RESPONSE_TOO_LARGE"
        : "RESPONSE_BODY_READ_FAILED",
      failureMetadata
    );
    stop(7);
  }
  if (Buffer.byteLength(responseText, "utf8") > 1_048_576) {
    await block(
      "SCHEMA_INVALID_OR_EMPTY",
      true,
      "RESPONSE_TOO_LARGE",
      {
        ...failureMetadata,
        responseLength: Buffer.byteLength(responseText, "utf8")
      }
    );
    stop(7);
  }
  let envelope;
  try {
    envelope = parseJsonRejectDuplicateKeys(responseText);
  } catch {
    const trimmed = responseText.trimStart();
    await block(
      "SCHEMA_INVALID_OR_EMPTY",
      true,
      "RESPONSE_BODY_NOT_JSON",
      {
        ...failureMetadata,
        responseMediaTypeClass: "APPLICATION_JSON",
        responseShape:
          trimmed.length === 0
            ? "EMPTY"
            : trimmed.startsWith("<")
              ? "HTML_LIKE"
              : trimmed.startsWith("{")
                ? "JSON_OBJECT_PREFIX"
                : trimmed.startsWith("[")
                  ? "JSON_ARRAY_PREFIX"
                  : "OTHER",
        responseLength: responseText.length
      }
    );
    stop(7);
  }
  if (
    typeof envelope.id !== "string" ||
    !/^[A-Za-z0-9._:-]{1,128}$/.test(envelope.id) ||
    envelope.model !== "deepseek-v4-pro" ||
    !Number.isInteger(envelope.created)
  ) {
    await block(
      "SCHEMA_INVALID_OR_EMPTY",
      true,
      "PROVIDER_RECEIPT_INVALID",
      failureMetadata
    );
    stop(7);
  }

  let accepted;
  try {
    accepted = validateTrackBDeepSeekEnvelope(call.input, envelope);
  } catch (error) {
    await block(
      "SCHEMA_INVALID_OR_EMPTY",
      true,
      error?.admissionCode ?? "UNKNOWN_VALIDATION_FAILURE",
      failureMetadata
    );
    stop(7);
  }
  providerResponses.push({
    callId: call.callId,
    receiptStatus: "STRICT_SCHEMA_ACCEPTED",
    finishReason: "stop"
  });
  acceptedOpinions.push(...accepted.opinions);
}
const acceptedByPacketId = new Map(
  acceptedOpinions.map((opinion) => [opinion.packetId, opinion])
);
if (
  acceptedByPacketId.size !== input.packetCount ||
  input.packets.some((packet) => !acceptedByPacketId.has(packet.packetId))
) {
  await block(
    "SCHEMA_INVALID_OR_EMPTY",
    true,
    "COMBINED_PACKET_COVERAGE_INVALID"
  );
  stop(7);
}
const orderedOpinions = input.packets.map((packet) =>
  acceptedByPacketId.get(packet.packetId)
);
const completedAt = now().toISOString();
await mkdir(dirname(outputPath), { recursive: true });
await writeFile(
  outputPath,
  `${JSON.stringify(
    {
      schemaVersion:
        "task-eval-002-track-b-admission-model-opinion-v2",
      status: "ACCEPTED",
      track: input.track,
      evaluator: "deepseek-v4-pro",
      inputSha256,
      dispatchSha256,
      authorizationSha256: sha256(
        authorizationBytes
      ),
      authorization: authorizationEvidence,
      networkAttempted: true,
      executionMode: "run",
      executionClaimSha256,
      resolverVersion: TRACK_B_DEEPSEEK_RESOLVER_VERSION,
      pinnedAddressSetSha256,
      providerCallSetSha256: dispatch.providerCallSetSha256,
      providerCallCount: providerCalls.length,
      providerResponses,
      finishReason: "stop",
      startedAt,
      completedAt,
      opinions: orderedOpinions
    },
    null,
    2
  )}\n`,
  { encoding: "utf8", flag: "wx" }
);
return { exitCode: 0, outputPath };
}

const invokedAsMain =
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invokedAsMain) {
  try {
    await runTrackBAdmission();
  } catch (error) {
    if (error instanceof TrackBRunnerExit) {
      process.exitCode = error.exitCode;
    } else {
      throw error;
    }
  }
}
