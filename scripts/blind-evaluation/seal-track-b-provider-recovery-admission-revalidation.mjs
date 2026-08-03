import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  buildCodexRevalidationCompletionReceipt,
  CODEX_REVALIDATION_ROOT,
  validateCodexRevalidationEvidence
} from "./track-b-provider-recovery-codex-revalidation-contract.mjs";
import {
  buildTrackBProviderRecoveryAdmissionEvaluation
} from "./seal-track-b-provider-recovery-admission.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

const RUN_ROOT = "outputs/task-eval-006/track-b-recovery-v1/run-v1";
const HUMAN_PATH =
  "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json";
const REPORT_PATH = `${CODEX_REVALIDATION_ROOT}/admission-report-v2.json`;
const SEAL_PATH = `${CODEX_REVALIDATION_ROOT}/admission-seal-v2.json`;
const PREVIOUS_SEAL_PATH = `${RUN_ROOT}/admission-seal.json`;

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

export function buildTrackBProviderRecoveryRevalidatedAdmission({
  modelInputBytes,
  callSetBytes,
  dispatchBytes,
  receiptBytes,
  humanGroundTruthBytes,
  codexExecutionClaimBytes,
  codexLaunchReceiptBytes,
  codexOpinionBytes,
  codexCompletionReceiptBytes,
  deepSeekClaimBytes,
  deepSeekOpinionBytes,
  previousAdmissionSealBytes,
  completedAt
}) {
  assert.equal(new Date(completedAt).toISOString(), completedAt);
  const chronology = validateCodexRevalidationEvidence({
    modelInputBytes,
    humanGroundTruthBytes,
    claimBytes: codexExecutionClaimBytes,
    launchReceiptBytes: codexLaunchReceiptBytes,
    opinionBytes: codexOpinionBytes,
    completionReceiptBytes: codexCompletionReceiptBytes
  });
  assert.ok(
    Date.parse(completedAt) >= Date.parse(chronology.completion.receivedAt)
  );
  const previousSeal = parseJsonBytesRejectDuplicateKeys(
    previousAdmissionSealBytes
  );
  assert.equal(previousSeal.status, "SEALED_GO_TRACK_B_RECOVERY_ADMISSION");
  const base = buildTrackBProviderRecoveryAdmissionEvaluation({
    modelInputBytes,
    callSetBytes,
    dispatchBytes,
    receiptBytes,
    humanGroundTruthBytes,
    codexOpinionBytes,
    deepSeekClaimBytes,
    deepSeekOpinionBytes,
    completedAt
  });
  assert.equal(base.report.status, "GO_9_OF_9_PLUS_3_ZERO_CALL");
  const report = {
    ...base.report,
    schemaVersion:
      "task-eval-006-track-b-recovery-admission-report-v2",
    evaluatorPolicy:
      "FRESH_ISOLATED_CODEX_REVALIDATION_AND_REUSED_SEALED_DEEPSEEK_ALL_DIMENSIONS_100_PERCENT",
    previousAdmissionSealPath: PREVIOUS_SEAL_PATH,
    previousAdmissionSealSha256: sha256(previousAdmissionSealBytes),
    codexExecutionClaimSha256: sha256(codexExecutionClaimBytes),
    codexLaunchReceiptSha256: sha256(codexLaunchReceiptBytes),
    codexCompletionReceiptSha256: sha256(codexCompletionReceiptBytes),
    codexChronology: {
      humanGroundTruthConfirmedAt:
        chronology.claim.humanGroundTruthConfirmedAt,
      executionClaimedAt: chronology.claim.claimedAt,
      freshAgentReadyAt: chronology.launch.readyAt,
      launchReceiptRecordedAt: chronology.launch.recordedAt,
      evaluatorStartedAt: chronology.opinion.startedAt,
      evaluatorCompletedAt: chronology.opinion.completedAt,
      completionReceiptReceivedAt: chronology.completion.receivedAt
    },
    codexIsolation: {
      isolatedDirectory: chronology.claim.isolatedDirectory,
      forkTurns: chronology.launch.forkTurns,
      inheritedConversationTurns: chronology.launch.inheritedConversationTurns,
      filesReadBeforeReady: chronology.launch.filesReadBeforeReady,
      allowedInputOnly: chronology.opinion.allowedInputOnly,
      humanGroundTruthRead: chronology.opinion.humanGroundTruthRead,
      priorModelOpinionRead: chronology.opinion.priorModelOpinionRead,
      projectMemoryOrChatHistoryRead:
        chronology.opinion.projectMemoryOrChatHistoryRead
    },
    deepSeekEvidenceReused: true,
    deepSeekNetworkCallRepeated: false,
    deepSeekClaimRepeated: false
  };
  const reportBytes = jsonBytes(report);
  const seal = {
    ...base.seal,
    schemaVersion: "task-eval-006-track-b-recovery-admission-seal-v2",
    status: "SEALED_GO_TRACK_B_RECOVERY_ADMISSION_REVALIDATED",
    reportPath: REPORT_PATH,
    reportSha256: sha256(reportBytes),
    previousAdmissionSealPath: PREVIOUS_SEAL_PATH,
    previousAdmissionSealSha256: sha256(previousAdmissionSealBytes),
    codexExecutionClaimSha256: sha256(codexExecutionClaimBytes),
    codexLaunchReceiptSha256: sha256(codexLaunchReceiptBytes),
    codexCompletionReceiptSha256: sha256(codexCompletionReceiptBytes),
    deepSeekEvidenceReused: true,
    deepSeekNetworkCallRepeated: false,
    a0A1A2ImplementationAllowed: false
  };
  return {
    report,
    seal,
    hashes: {
      reportSha256: sha256(reportBytes),
      sealSha256: sha256(jsonBytes(seal)),
      codexOpinionSha256: sha256(codexOpinionBytes),
      deepSeekOpinionSha256: sha256(deepSeekOpinionBytes)
    }
  };
}
export async function sealTrackBProviderRecoveryRevalidatedAdmission({
  repoRoot: repoArg,
  completedAt,
  mode = "create"
}) {
  const root = resolve(repoArg);
  const read = (relative) =>
    readFile(resolve(root, ...relative.split("/")));
  const result = buildTrackBProviderRecoveryRevalidatedAdmission({
    modelInputBytes: await read(`${RUN_ROOT}/model-input.json`),
    callSetBytes: await read(`${RUN_ROOT}/provider-call-set.json`),
    dispatchBytes: await read(`${RUN_ROOT}/dispatch.json`),
    receiptBytes: await read(`${RUN_ROOT}/derived-egress-receipt.json`),
    humanGroundTruthBytes: await read(HUMAN_PATH),
    codexExecutionClaimBytes: await read(
      `${CODEX_REVALIDATION_ROOT}/execution-claim.json`
    ),
    codexLaunchReceiptBytes: await read(
      `${CODEX_REVALIDATION_ROOT}/launch-receipt.json`
    ),
    codexOpinionBytes: await read(
      `${CODEX_REVALIDATION_ROOT}/codex-opinion.json`
    ),
    codexCompletionReceiptBytes: await read(
      `${CODEX_REVALIDATION_ROOT}/completion-receipt.json`
    ),
    deepSeekClaimBytes: await read(`${RUN_ROOT}/deepseek-execution-claim.json`),
    deepSeekOpinionBytes: await read(`${RUN_ROOT}/deepseek-opinion.json`),
    previousAdmissionSealBytes: await read(PREVIOUS_SEAL_PATH),
    completedAt
  });
  for (const [relative, value] of [
    [REPORT_PATH, result.report],
    [SEAL_PATH, result.seal]
  ]) {
    const path = resolve(root, ...relative.split("/"));
    const expected = jsonBytes(value);
    if (mode === "create") {
      await writeFile(path, expected, { flag: "wx" });
    } else if (mode === "verify") {
      assert.equal(Buffer.compare(await readFile(path), expected), 0);
    } else {
      throw new Error(`Unknown mode: ${mode}`);
    }
  }
  return {
    status: result.seal.status,
    ...result.hashes,
    deepSeekNetworkCallRepeated: false,
    providerAdmissionEstablished: result.seal.providerAdmissionEstablished
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repoRoot = ".", completedAt = new Date().toISOString(), mode = "create"] =
    process.argv.slice(2);
  process.stdout.write(`${JSON.stringify(
    await sealTrackBProviderRecoveryRevalidatedAdmission({
      repoRoot,
      completedAt,
      mode
    })
  )}\n`);
}
