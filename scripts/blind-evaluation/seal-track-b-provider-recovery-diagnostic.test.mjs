import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  buildTrackBProviderRecoveryDiagnosticEvaluation
} from "./seal-track-b-provider-recovery-diagnostic.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const runRoot =
  "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1";

test("seals 4/4 recovery only when every semantic dimension matches the prior human decisions", async () => {
  const read = (relative) =>
    readFile(resolve(repoRoot, ...relative.split("/")));
  const result = buildTrackBProviderRecoveryDiagnosticEvaluation({
    modelInputBytes: await read(`${runRoot}/model-input.json`),
    callSetBytes: await read(`${runRoot}/provider-call-set.json`),
    dispatchBytes: await read(`${runRoot}/dispatch.json`),
    receiptBytes: await read(`${runRoot}/derived-egress-receipt.json`),
    claimBytes: await read(`${runRoot}/deepseek-execution-claim.json`),
    opinionBytes: await read(`${runRoot}/deepseek-opinion.json`),
    humanGroundTruthBytes: await read(
      "outputs/task-eval-005/track-b-fifth-v1/human-ground-truth.json"
    ),
    completedAt: "2026-08-03T12:40:00.000Z"
  });

  assert.equal(result.report.status, "GO_4_OF_4");
  assert.deepEqual(result.report.metrics, {
    schema: "4/4",
    reliableAnchor: "4/4",
    role: "4/4",
    candidate: "4/4",
    anchor: "4/4",
    abstention: "4/4"
  });
  assert.equal(result.report.formalAdmissionAffected, false);
  assert.equal(result.seal.status, "SEALED_GO_RECOVERY_DIAGNOSTIC");
  assert.equal(result.seal.sixthCorpusCreationAllowed, true);
  assert.equal(result.seal.providerAdmissionEstablished, false);
  assert.equal(result.seal.providerA0A1A2Allowed, false);
  assert.match(result.hashes.reportSha256, /^[a-f0-9]{64}$/);
  assert.match(result.hashes.sealSha256, /^[a-f0-9]{64}$/);
});
