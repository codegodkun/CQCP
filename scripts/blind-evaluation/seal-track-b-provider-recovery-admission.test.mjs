import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  buildTrackBProviderRecoveryAdmissionEvaluation
} from "./seal-track-b-provider-recovery-admission.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const runRoot = "outputs/task-eval-006/track-b-recovery-v1/run-v1";
const read = (relative) =>
  readFile(resolve(repoRoot, ...relative.split("/")));

test("seals GO only when both blind evaluators are 9/9 on all dimensions and controls are 3/3 zero-call", async () => {
  const result = buildTrackBProviderRecoveryAdmissionEvaluation({
    modelInputBytes: await read(`${runRoot}/model-input.json`),
    callSetBytes: await read(`${runRoot}/provider-call-set.json`),
    dispatchBytes: await read(`${runRoot}/dispatch.json`),
    receiptBytes: await read(`${runRoot}/derived-egress-receipt.json`),
    humanGroundTruthBytes: await read(
      "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json"
    ),
    codexOpinionBytes: await read(`${runRoot}/codex-opinion.json`),
    deepSeekClaimBytes: await read(
      `${runRoot}/deepseek-execution-claim.json`
    ),
    deepSeekOpinionBytes: await read(`${runRoot}/deepseek-opinion.json`),
    completedAt: "2026-08-03T13:00:00.000Z"
  });

  assert.equal(result.report.status, "GO_9_OF_9_PLUS_3_ZERO_CALL");
  assert.deepEqual(result.report.codexMetrics, {
    schema: "9/9",
    reliableAnchor: "9/9",
    role: "9/9",
    candidate: "9/9",
    anchor: "9/9",
    abstention: "9/9"
  });
  assert.deepEqual(result.report.deepSeekMetrics, result.report.codexMetrics);
  assert.equal(result.report.zeroCallControls, "3/3");
  assert.equal(result.seal.status, "SEALED_GO_TRACK_B_RECOVERY_ADMISSION");
  assert.equal(result.seal.providerAdmissionEstablished, true);
  assert.equal(result.seal.a0A1A2ImplementationAllowed, false);
  assert.equal(result.seal.verificationAndAuditAllowed, true);
});

test("one semantic mismatch deterministically seals NO_GO", async () => {
  const deepSeek = JSON.parse(
    await read(`${runRoot}/deepseek-opinion.json`)
  );
  deepSeek.opinions[0].selectedOccurrenceIds = ["OCC-001"];
  const input = JSON.parse(await read(`${runRoot}/model-input.json`));
  deepSeek.opinions[0].selectedAnchorBlockIds = [
    input.packets[0].candidateOccurrences[0].anchor.blockId
  ];
  const bytes = Buffer.from(`${JSON.stringify(deepSeek, null, 2)}\n`, "utf8");
  const result = buildTrackBProviderRecoveryAdmissionEvaluation({
    modelInputBytes: await read(`${runRoot}/model-input.json`),
    callSetBytes: await read(`${runRoot}/provider-call-set.json`),
    dispatchBytes: await read(`${runRoot}/dispatch.json`),
    receiptBytes: await read(`${runRoot}/derived-egress-receipt.json`),
    humanGroundTruthBytes: await read(
      "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json"
    ),
    codexOpinionBytes: await read(`${runRoot}/codex-opinion.json`),
    deepSeekClaimBytes: await read(
      `${runRoot}/deepseek-execution-claim.json`
    ),
    deepSeekOpinionBytes: bytes,
    completedAt: "2026-08-03T13:00:00.000Z",
    allowOpinionHashDriftForTest: true
  });
  assert.equal(result.report.status, "NO_GO_MODEL_MISMATCH");
  assert.equal(result.seal.providerAdmissionEstablished, false);
  assert.equal(result.seal.verificationAndAuditAllowed, false);
});
