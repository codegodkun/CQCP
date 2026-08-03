import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";
import {
  validateTrackBProviderRecoveryOpinionPayload
} from "./track-b-provider-recovery-opinion-contract.mjs";

const RUN_ROOT =
  "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1";
const REPORT_PATH = `${RUN_ROOT}/diagnostic-report.json`;
const SEAL_PATH = `${RUN_ROOT}/diagnostic-seal.json`;

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

export function buildTrackBProviderRecoveryDiagnosticEvaluation({
  modelInputBytes,
  callSetBytes,
  dispatchBytes,
  receiptBytes,
  claimBytes,
  opinionBytes,
  humanGroundTruthBytes,
  completedAt
}) {
  assert.equal(new Date(completedAt).toISOString(), completedAt);
  const modelInput = parseJsonBytesRejectDuplicateKeys(modelInputBytes);
  const callSet = parseJsonBytesRejectDuplicateKeys(callSetBytes);
  const dispatch = parseJsonBytesRejectDuplicateKeys(dispatchBytes);
  const receipt = parseJsonBytesRejectDuplicateKeys(receiptBytes);
  const claim = parseJsonBytesRejectDuplicateKeys(claimBytes);
  const opinion = parseJsonBytesRejectDuplicateKeys(opinionBytes);
  const human = parseJsonBytesRejectDuplicateKeys(humanGroundTruthBytes);
  const hashes = {
    modelInputSha256: sha256(modelInputBytes),
    callSetSha256: sha256(callSetBytes),
    dispatchSha256: sha256(dispatchBytes),
    receiptSha256: sha256(receiptBytes),
    claimSha256: sha256(claimBytes),
    opinionSha256: sha256(opinionBytes),
    humanGroundTruthSha256: sha256(humanGroundTruthBytes)
  };

  assert.equal(modelInput.packetCount, 4);
  assert.equal(callSet.callCount, 4);
  assert.equal(dispatch.providerCallCount, 4);
  assert.equal(receipt.callCount, 4);
  assert.equal(claim.providerCallCount, 4);
  assert.equal(opinion.providerCallCount, 4);
  assert.equal(opinion.opinions.length, 4);
  assert.equal(opinion.modelInputSha256, hashes.modelInputSha256);
  assert.equal(opinion.providerCallSetSha256, hashes.callSetSha256);
  assert.equal(opinion.dispatchSha256, hashes.dispatchSha256);
  assert.equal(opinion.derivedReceiptSha256, hashes.receiptSha256);
  assert.equal(opinion.executionClaimSha256, hashes.claimSha256);
  assert.equal(claim.modelInputSha256, hashes.modelInputSha256);
  assert.equal(claim.providerCallSetSha256, hashes.callSetSha256);
  assert.equal(claim.dispatchSha256, hashes.dispatchSha256);
  assert.equal(claim.derivedReceiptSha256, hashes.receiptSha256);
  assert.equal(opinion.rawResponsePersisted, false);
  assert.equal(opinion.reasoningContentPersisted, false);
  assert.equal(opinion.automaticRetryPerformed, false);

  const groundTruthByPacket = new Map(
    human.entries.map((entry) => [entry.packetId, entry])
  );
  const opinionByPacket = new Map(
    opinion.opinions.map((entry) => [entry.packetId, entry])
  );
  const results = modelInput.packets.map((packet) => {
    const actual = opinionByPacket.get(packet.packetId);
    const truth = groundTruthByPacket.get(packet.packetId);
    assert.ok(actual, `missing opinion for ${packet.packetId}`);
    assert.ok(truth, `missing human decision for ${packet.packetId}`);
    validateTrackBProviderRecoveryOpinionPayload(packet, {
      opinions: [actual]
    });
    const expectedOccurrences = Array.isArray(
      truth.expected.selectedOccurrenceIds
    )
      ? truth.expected.selectedOccurrenceIds
      : truth.expected.selectedOccurrenceIds
        ? [truth.expected.selectedOccurrenceIds]
        : [];
    const expectedAnchors = Array.isArray(
      truth.expected.selectedAnchorBlockIds
    )
      ? truth.expected.selectedAnchorBlockIds
      : truth.expected.selectedAnchorBlockIds
        ? [truth.expected.selectedAnchorBlockIds]
        : [];
    return {
      caseId: truth.caseId,
      packetId: packet.packetId,
      schemaMatched: true,
      reliableAnchorMatched: actual.selectedAnchorBlockIds.every(
        (blockId) =>
          packet.candidateOccurrences.some(
            (candidate) =>
              candidate.anchor.blockId === blockId &&
              candidate.anchor.reliable === true
          )
      ),
      roleMatched: actual.suggestedRole === truth.expected.suggestedRole,
      candidateMatched:
        JSON.stringify(actual.selectedOccurrenceIds) ===
        JSON.stringify(expectedOccurrences),
      anchorMatched:
        JSON.stringify(actual.selectedAnchorBlockIds) ===
        JSON.stringify(expectedAnchors),
      abstentionMatched:
        actual.abstain === truth.expected.abstain &&
        actual.abstentionReason === truth.expected.abstentionReason
    };
  });
  const dimensions = [
    ["schema", "schemaMatched"],
    ["reliableAnchor", "reliableAnchorMatched"],
    ["role", "roleMatched"],
    ["candidate", "candidateMatched"],
    ["anchor", "anchorMatched"],
    ["abstention", "abstentionMatched"]
  ];
  const metrics = Object.fromEntries(
    dimensions.map(([name, field]) => [
      name,
      `${results.filter((item) => item[field]).length}/4`
    ])
  );
  const go = Object.values(metrics).every((value) => value === "4/4");

  const report = {
    schemaVersion: "task-eval-006-provider-recovery-diagnostic-report-v1",
    status: go ? "GO_4_OF_4" : "NO_GO_MODEL_MISMATCH",
    completedAt,
    purpose: "PROVIDER_VALIDATION",
    formalAdmissionAffected: false,
    sourceTaskEval005ClaimRestored: false,
    evaluator: opinion.evaluator,
    modelInputSha256: hashes.modelInputSha256,
    providerCallSetSha256: hashes.callSetSha256,
    dispatchSha256: hashes.dispatchSha256,
    derivedReceiptSha256: hashes.receiptSha256,
    executionClaimSha256: hashes.claimSha256,
    opinionSha256: hashes.opinionSha256,
    humanGroundTruthSha256: hashes.humanGroundTruthSha256,
    providerCallCount: 4,
    automaticRetryPerformed: false,
    rawResponsePersisted: false,
    reasoningContentPersisted: false,
    metrics,
    results,
    sixthCorpusCreationAllowed: go,
    providerAdmissionEstablished: false,
    providerA0A1A2Allowed: false
  };
  const reportBytes = jsonBytes(report);
  const seal = {
    schemaVersion: "task-eval-006-provider-recovery-diagnostic-seal-v1",
    status: go
      ? "SEALED_GO_RECOVERY_DIAGNOSTIC"
      : "SEALED_NO_GO_RECOVERY_DIAGNOSTIC",
    sealedAt: completedAt,
    reportPath: REPORT_PATH,
    reportSha256: sha256(reportBytes),
    modelInputSha256: hashes.modelInputSha256,
    providerCallSetSha256: hashes.callSetSha256,
    dispatchSha256: hashes.dispatchSha256,
    derivedReceiptSha256: hashes.receiptSha256,
    executionClaimSha256: hashes.claimSha256,
    opinionSha256: hashes.opinionSha256,
    humanGroundTruthSha256: hashes.humanGroundTruthSha256,
    sameDiagnosticRetryAllowed: false,
    sixthCorpusCreationAllowed: go,
    providerAdmissionEstablished: false,
    providerA0A1A2Allowed: false
  };
  return {
    report,
    seal,
    hashes: {
      ...hashes,
      reportSha256: sha256(reportBytes),
      sealSha256: sha256(jsonBytes(seal))
    }
  };
}

export async function sealTrackBProviderRecoveryDiagnostic({
  repoRoot,
  completedAt
}) {
  const root = resolve(repoRoot);
  const read = (relative) => readFile(resolve(root, ...relative.split("/")));
  const result = buildTrackBProviderRecoveryDiagnosticEvaluation({
    modelInputBytes: await read(`${RUN_ROOT}/model-input.json`),
    callSetBytes: await read(`${RUN_ROOT}/provider-call-set.json`),
    dispatchBytes: await read(`${RUN_ROOT}/dispatch.json`),
    receiptBytes: await read(`${RUN_ROOT}/derived-egress-receipt.json`),
    claimBytes: await read(`${RUN_ROOT}/deepseek-execution-claim.json`),
    opinionBytes: await read(`${RUN_ROOT}/deepseek-opinion.json`),
    humanGroundTruthBytes: await read(
      "outputs/task-eval-005/track-b-fifth-v1/human-ground-truth.json"
    ),
    completedAt
  });
  for (const [path, value] of [
    [REPORT_PATH, result.report],
    [SEAL_PATH, result.seal]
  ]) {
    const absolute = resolve(root, ...path.split("/"));
    await mkdir(dirname(absolute), { recursive: true });
    await writeFile(absolute, jsonBytes(value), { flag: "wx" });
  }
  return {
    status: result.seal.status,
    ...result.hashes,
    sixthCorpusCreationAllowed: result.seal.sixthCorpusCreationAllowed,
    providerAdmissionEstablished: false
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repoRoot = ".", completedAt = new Date().toISOString()] =
    process.argv.slice(2);
  const result = await sealTrackBProviderRecoveryDiagnostic({
    repoRoot,
    completedAt
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
