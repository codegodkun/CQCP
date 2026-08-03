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
  TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT,
  buildTrackBProviderRecoveryDiagnosticArtifacts,
  serializeTrackBProviderRecoveryArtifact
} from "./track-b-provider-recovery-diagnostic-contract.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";
import {
  validateTrackBProviderRecoveryDeepSeekEnvelope
} from "./track-b-provider-recovery-opinion-contract.mjs";
import {
  TRACK_B_PROVIDER_RECOVERY_MODEL
} from "./track-b-provider-recovery-request-contract.mjs";

const PATHS = Object.freeze({
  source:
    "outputs/task-eval-005/track-b-fifth-v1/run-v1/model-input.json",
  prompt:
    "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt",
  grant: "scripts/blind-evaluation/mvp002-standing-egress-grant.json",
  input: `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/model-input.json`,
  callSet: `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/provider-call-set.json`,
  dispatch: `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/dispatch.json`,
  receipt:
    `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/derived-egress-receipt.json`,
  claim:
    `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/deepseek-execution-claim.json`,
  output:
    `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/deepseek-opinion.json`,
  terminal:
    `${TRACK_B_PROVIDER_RECOVERY_OUTPUT_ROOT}/deepseek-terminal.json`
});

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

export async function runTrackBProviderRecoveryDiagnostic(options = {}) {
  const {
    argv = process.argv.slice(2),
    environment = process.env,
    dependencies = {}
  } = options;
  const [repoArg, mode = "run"] = argv;
  if (!repoArg || !["run", "preflight"].includes(mode)) {
    throw new Error(
      "Usage: node track-b-provider-recovery-runner.mjs " +
        "<repo-root> [run|preflight]"
    );
  }
  const repoRoot = resolve(repoArg);
  const fromRepo = (path) => resolve(repoRoot, ...path.split("/"));
  const readEvidence = async (path) => {
    const bytes = await readFile(fromRepo(path));
    return { bytes, value: parseJsonBytesRejectDuplicateKeys(bytes) };
  };

  const [sourceBytes, promptBytes, grantBytes] = await Promise.all([
    readFile(fromRepo(PATHS.source)),
    readFile(fromRepo(PATHS.prompt)),
    readFile(fromRepo(PATHS.grant))
  ]);
  const dispatchEvidence = await readEvidence(PATHS.dispatch);
  const expected = buildTrackBProviderRecoveryDiagnosticArtifacts({
    fifthInputBytes: sourceBytes,
    promptBytes,
    grantBytes,
    createdAt: dispatchEvidence.value.createdAt
  });
  const evidence = {
    input: await readEvidence(PATHS.input),
    callSet: await readEvidence(PATHS.callSet),
    dispatch: dispatchEvidence,
    receipt: await readEvidence(PATHS.receipt)
  };
  for (const [name, expectedValue] of [
    ["input", expected.modelInput],
    ["callSet", expected.callSet],
    ["dispatch", expected.dispatch],
    ["receipt", expected.receipt]
  ]) {
    assert.deepEqual(
      evidence[name].value,
      expectedValue,
      `TRACK_B_RECOVERY_${name.toUpperCase()}_DRIFT`
    );
    assert.equal(
      Buffer.compare(
        evidence[name].bytes,
        serializeTrackBProviderRecoveryArtifact(expectedValue)
      ),
      0,
      `TRACK_B_RECOVERY_${name.toUpperCase()}_BYTES_DRIFT`
    );
  }

  if (mode === "preflight") {
    return {
      status: "PREFLIGHT_VALIDATED_NO_NETWORK",
      modelInputSha256: sha256(evidence.input.bytes),
      providerCallSetSha256: sha256(evidence.callSet.bytes),
      dispatchSha256: sha256(evidence.dispatch.bytes),
      derivedReceiptSha256: sha256(evidence.receipt.bytes),
      providerCallCount: expected.calls.length,
      networkAttempted: false
    };
  }

  assertDeepSeekTlsEnvironment(environment);
  const secret = environment.DEEPSEEK_API_KEY;
  if (typeof secret !== "string" || !secret.trim()) {
    throw new Error("TRACK_B_RECOVERY_SECRET_MISSING");
  }
  const now = dependencies.now ?? (() => new Date());
  const resolveAddresses =
    dependencies.resolveAddresses ?? resolveAndValidateDeepSeekAddresses;
  const transport = dependencies.transport ?? postDeepSeekJson;
  const beforeSend = dependencies.beforeSend ?? (async () => {});
  const afterClaim = dependencies.afterClaim ?? (async () => {});
  const startedAt = now().toISOString();
  const stageDeadlineAt = new Date(
    Date.parse(startedAt) + 8 * 60_000
  ).toISOString();
  const addresses = await resolveAddresses({
    timeoutMs: 60_000,
    deadlineAt: stageDeadlineAt
  });
  const pinnedAddressSetSha256 = sha256(
    Buffer.from(JSON.stringify(addresses), "utf8")
  );

  const claim = {
    schemaVersion: "task-eval-006-provider-recovery-execution-claim-v1",
    status: "CLAIMED_ONE_TIME_NON_ADMISSION_DIAGNOSTIC",
    claimedAt: now().toISOString(),
    modelInputSha256: sha256(evidence.input.bytes),
    providerCallSetSha256: sha256(evidence.callSet.bytes),
    dispatchSha256: sha256(evidence.dispatch.bytes),
    derivedReceiptSha256: sha256(evidence.receipt.bytes),
    model: TRACK_B_PROVIDER_RECOVERY_MODEL,
    providerCallCount: expected.calls.length,
    formalAdmissionAffected: false,
    automaticRetryAllowedAfterHttpStart: false,
    pinnedAddressSetSha256
  };
  const claimBytes = serializeTrackBProviderRecoveryArtifact(claim);
  const claimPath = fromRepo(PATHS.claim);
  await mkdir(dirname(claimPath), { recursive: true });
  try {
    await writeFile(claimPath, claimBytes, { flag: "wx" });
  } catch (error) {
    if (error?.code === "EEXIST") {
      throw new Error("TRACK_B_RECOVERY_EXECUTION_ALREADY_CLAIMED");
    }
    throw error;
  }
  const claimSha256 = sha256(claimBytes);
  await afterClaim({ claim, claimSha256, claimPath });

  const terminal = async (code, detailCode, metadata = {}) => {
    const value = {
      schemaVersion: "task-eval-006-provider-recovery-terminal-v1",
      status: "BLOCKED",
      code,
      ...(detailCode ? { detailCode } : {}),
      ...metadata,
      evaluator: TRACK_B_PROVIDER_RECOVERY_MODEL,
      modelInputSha256: sha256(evidence.input.bytes),
      providerCallSetSha256: sha256(evidence.callSet.bytes),
      dispatchSha256: sha256(evidence.dispatch.bytes),
      derivedReceiptSha256: sha256(evidence.receipt.bytes),
      executionClaimSha256: claimSha256,
      automaticRetryPerformed: false,
      startedAt,
      completedAt: now().toISOString()
    };
    await writeFile(
      fromRepo(PATHS.terminal),
      serializeTrackBProviderRecoveryArtifact(value),
      { flag: "wx" }
    );
  };

  const acceptedOpinions = [];
  const providerReceipts = [];
  for (const [callIndex, call] of expected.calls.entries()) {
    const metadata = {
      failedCallId: call.callId,
      failedCallIndex: callIndex,
      completedCallCount: providerReceipts.length,
      networkAttempted: true
    };
    let response;
    try {
      await beforeSend({ call, callIndex });
      response = await transport({
        body: call.outboundRequestBytes,
        secret,
        addresses,
        timeoutMs: 60_000,
        deadlineAt: stageDeadlineAt,
        environment
      });
    } catch (error) {
      await terminal(
        error?.code === "RESPONSE_TOO_LARGE"
          ? "SCHEMA_INVALID_OR_EMPTY"
          : "NETWORK_OR_TIMEOUT",
        error?.code === "RESPONSE_TOO_LARGE"
          ? "RESPONSE_TOO_LARGE"
          : "UNKNOWN_SIDE_EFFECT_NO_RETRY",
        metadata
      );
      throw new TrackBProviderRecoveryRunnerExit(5);
    }
    if (response.status < 200 || response.status >= 300) {
      await terminal(
        response.status === 401 || response.status === 403
          ? "AUTHENTICATION_FAILED"
          : response.status === 429
            ? "RATE_LIMITED"
            : response.status >= 500
              ? "UPSTREAM_5XX"
              : "UPSTREAM_REJECTED",
        undefined,
        metadata
      );
      throw new TrackBProviderRecoveryRunnerExit(6);
    }
    if (response.contentTypeClass !== "APPLICATION_JSON") {
      await terminal(
        "SCHEMA_INVALID_OR_EMPTY",
        "RESPONSE_MEDIA_TYPE_INVALID",
        metadata
      );
      throw new TrackBProviderRecoveryRunnerExit(7);
    }
    let envelope;
    try {
      envelope = parseJsonBytesRejectDuplicateKeys(response.body);
    } catch {
      await terminal(
        "SCHEMA_INVALID_OR_EMPTY",
        "RESPONSE_BODY_NOT_JSON",
        metadata
      );
      throw new TrackBProviderRecoveryRunnerExit(7);
    }
    if (
      typeof envelope.id !== "string" ||
      !/^[A-Za-z0-9._:-]{1,128}$/.test(envelope.id) ||
      envelope.model !== TRACK_B_PROVIDER_RECOVERY_MODEL ||
      !Number.isInteger(envelope.created)
    ) {
      await terminal(
        "SCHEMA_INVALID_OR_EMPTY",
        "PROVIDER_RECEIPT_INVALID",
        metadata
      );
      throw new TrackBProviderRecoveryRunnerExit(7);
    }
    let payload;
    try {
      payload = validateTrackBProviderRecoveryDeepSeekEnvelope(
        call.input,
        envelope
      );
    } catch (error) {
      await terminal(
        "SCHEMA_INVALID_OR_EMPTY",
        error?.message ?? "OPINION_SCHEMA_INVALID",
        metadata
      );
      throw new TrackBProviderRecoveryRunnerExit(7);
    }
    acceptedOpinions.push(...payload.opinions);
    providerReceipts.push({
      callId: call.callId,
      packetId: call.input.packetId,
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
  assert.equal(opinionByPacketId.size, expected.calls.length);
  const output = {
    schemaVersion: "task-eval-006-provider-recovery-model-opinion-v3",
    status: "ACCEPTED_NON_ADMISSION_DIAGNOSTIC",
    purpose: "PROVIDER_VALIDATION",
    evaluator: TRACK_B_PROVIDER_RECOVERY_MODEL,
    modelInputSha256: sha256(evidence.input.bytes),
    providerCallSetSha256: sha256(evidence.callSet.bytes),
    dispatchSha256: sha256(evidence.dispatch.bytes),
    standingGrantSha256: expected.hashes.standingGrantSha256,
    derivedReceiptSha256: sha256(evidence.receipt.bytes),
    executionClaimSha256: claimSha256,
    pinnedAddressSetSha256,
    providerCallCount: expected.calls.length,
    networkAttempted: true,
    automaticRetryPerformed: false,
    rawResponsePersisted: false,
    reasoningContentPersisted: false,
    providerReceipts,
    startedAt,
    completedAt: now().toISOString(),
    opinions: expected.calls.map((call) =>
      opinionByPacketId.get(call.input.packetId)
    )
  };
  const outputPath = fromRepo(PATHS.output);
  await writeFile(
    outputPath,
    serializeTrackBProviderRecoveryArtifact(output),
    { flag: "wx" }
  );
  return {
    status: output.status,
    providerCallCount: output.providerCallCount,
    opinionCount: output.opinions.length,
    networkAttempted: true,
    automaticRetryPerformed: false,
    outputSha256: sha256(await readFile(outputPath))
  };
}

export class TrackBProviderRecoveryRunnerExit extends Error {
  constructor(exitCode) {
    super(`TRACK_B_PROVIDER_RECOVERY_RUNNER_EXIT_${exitCode}`);
    this.exitCode = exitCode;
  }
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  try {
    const result = await runTrackBProviderRecoveryDiagnostic();
    process.stdout.write(`${JSON.stringify(result)}\n`);
  } catch (error) {
    if (error instanceof TrackBProviderRecoveryRunnerExit) {
      process.exitCode = error.exitCode;
    } else {
      throw error;
    }
  }
}
