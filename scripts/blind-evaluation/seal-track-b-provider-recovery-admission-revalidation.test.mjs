import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  buildTrackBProviderRecoveryRevalidatedAdmission
} from "./seal-track-b-provider-recovery-admission-revalidation.mjs";
import { CODEX_REVALIDATION_ROOT } from
  "./track-b-provider-recovery-codex-revalidation-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const runRoot = "outputs/task-eval-006/track-b-recovery-v1/run-v1";
const read = (relative) =>
  readFile(resolve(repoRoot, ...relative.split("/")));
const bytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

async function fixture() {
  return {
    modelInputBytes: await read(`${runRoot}/model-input.json`),
    callSetBytes: await read(`${runRoot}/provider-call-set.json`),
    dispatchBytes: await read(`${runRoot}/dispatch.json`),
    receiptBytes: await read(`${runRoot}/derived-egress-receipt.json`),
    humanGroundTruthBytes: await read(
      "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json"
    ),
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
    deepSeekClaimBytes: await read(`${runRoot}/deepseek-execution-claim.json`),
    deepSeekOpinionBytes: await read(`${runRoot}/deepseek-opinion.json`),
    previousAdmissionSealBytes: await read(`${runRoot}/admission-seal.json`)
  };
}

test("revalidates admission with fresh Codex chronology and no DeepSeek rerun", async () => {
  const result = buildTrackBProviderRecoveryRevalidatedAdmission({
    ...(await fixture()),
    completedAt: "2026-08-03T15:30:00.000Z"
  });
  assert.equal(result.report.codexMetrics.schema, "9/9");
  assert.equal(result.report.deepSeekMetrics.schema, "9/9");
  assert.equal(result.report.deepSeekEvidenceReused, true);
  assert.equal(result.report.deepSeekNetworkCallRepeated, false);
  assert.equal(
    result.seal.status,
    "SEALED_GO_TRACK_B_RECOVERY_EVALUATION_REVALIDATED_PENDING_AUDIT"
  );
  assert.equal(result.seal.modelEvaluationPassed, true);
  assert.equal(result.seal.providerAdmissionEstablished, false);
  assert.equal(result.seal.admissionDecisionPendingAudit, true);
  assert.equal(result.seal.a0A1A2ImplementationAllowed, false);
});

test("fails closed when reused DeepSeek bytes do not match the prior seal", async () => {
  for (const field of [
    "deepSeekExecutionClaimSha256",
    "deepSeekOpinionSha256"
  ]) {
    const input = await fixture();
    const previousSeal = JSON.parse(input.previousAdmissionSealBytes);
    previousSeal[field] = "0".repeat(64);
    assert.throws(
      () =>
        buildTrackBProviderRecoveryRevalidatedAdmission({
          ...input,
          previousAdmissionSealBytes: bytes(previousSeal),
          completedAt: "2026-08-03T15:30:00.000Z"
        }),
      /previous seal DeepSeek .* SHA must match/
    );
  }
});
