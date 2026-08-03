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

test("revalidates admission with fresh Codex chronology and no DeepSeek rerun", async () => {
  const result = buildTrackBProviderRecoveryRevalidatedAdmission({
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
    previousAdmissionSealBytes: await read(`${runRoot}/admission-seal.json`),
    completedAt: "2026-08-03T15:30:00.000Z"
  });
  assert.equal(result.report.codexMetrics.schema, "9/9");
  assert.equal(result.report.deepSeekMetrics.schema, "9/9");
  assert.equal(result.report.deepSeekEvidenceReused, true);
  assert.equal(result.report.deepSeekNetworkCallRepeated, false);
  assert.equal(
    result.seal.status,
    "SEALED_GO_TRACK_B_RECOVERY_ADMISSION_REVALIDATED"
  );
  assert.equal(result.seal.a0A1A2ImplementationAllowed, false);
});
