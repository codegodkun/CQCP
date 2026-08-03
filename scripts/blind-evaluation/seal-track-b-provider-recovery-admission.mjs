import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";
import {
  validateTrackBProviderRecoveryOpinionPayload
} from "./track-b-provider-recovery-opinion-contract.mjs";

const RUN_ROOT = "outputs/task-eval-006/track-b-recovery-v1/run-v1";
const HUMAN_PATH =
  "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json";
const REPORT_PATH = `${RUN_ROOT}/admission-report.json`;
const SEAL_PATH = `${RUN_ROOT}/admission-seal.json`;
const DIMENSIONS = [
  ["schema", "schemaMatched"],
  ["reliableAnchor", "reliableAnchorMatched"],
  ["role", "roleMatched"],
  ["candidate", "candidateMatched"],
  ["anchor", "anchorMatched"],
  ["abstention", "abstentionMatched"]
];

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

export function buildTrackBProviderRecoveryAdmissionEvaluation({
  modelInputBytes,
  callSetBytes,
  dispatchBytes,
  receiptBytes,
  humanGroundTruthBytes,
  codexOpinionBytes,
  deepSeekClaimBytes,
  deepSeekOpinionBytes,
  completedAt
}) {
  assert.equal(new Date(completedAt).toISOString(), completedAt);
  const modelInput = parseJsonBytesRejectDuplicateKeys(modelInputBytes);
  const callSet = parseJsonBytesRejectDuplicateKeys(callSetBytes);
  const dispatch = parseJsonBytesRejectDuplicateKeys(dispatchBytes);
  const receipt = parseJsonBytesRejectDuplicateKeys(receiptBytes);
  const human = parseJsonBytesRejectDuplicateKeys(humanGroundTruthBytes);
  const codex = parseJsonBytesRejectDuplicateKeys(codexOpinionBytes);
  const claim = parseJsonBytesRejectDuplicateKeys(deepSeekClaimBytes);
  const deepSeek = parseJsonBytesRejectDuplicateKeys(deepSeekOpinionBytes);
  const hashes = {
    modelInputSha256: sha256(modelInputBytes),
    callSetSha256: sha256(callSetBytes),
    dispatchSha256: sha256(dispatchBytes),
    receiptSha256: sha256(receiptBytes),
    humanGroundTruthSha256: sha256(humanGroundTruthBytes),
    codexOpinionSha256: sha256(codexOpinionBytes),
    deepSeekClaimSha256: sha256(deepSeekClaimBytes),
    deepSeekOpinionSha256: sha256(deepSeekOpinionBytes)
  };

  assert.equal(modelInput.packetCount, 9);
  assert.equal(modelInput.packets.length, 9);
  assert.equal(callSet.callCount, 9);
  assert.equal(callSet.inputCount, 9);
  assert.equal(callSet.masterModelInputSha256, hashes.modelInputSha256);
  assert.equal(dispatch.providerCallCount, 9);
  assert.equal(dispatch.zeroCallControlCount, 3);
  assert.equal(dispatch.modelInputSha256, hashes.modelInputSha256);
  assert.equal(dispatch.providerCallSetSha256, hashes.callSetSha256);
  assert.equal(
    dispatch.humanGroundTruthSealSha256,
    hashes.humanGroundTruthSha256
  );
  assert.equal(receipt.callCount, 9);
  assert.equal(receipt.zeroCallControlCount, 3);
  assert.equal(receipt.actualInputSha256, hashes.modelInputSha256);
  assert.equal(receipt.providerCallSetSha256, hashes.callSetSha256);
  assert.equal(receipt.dispatchSha256, hashes.dispatchSha256);
  assert.equal(human.status, "ACCEPTED_HUMAN_GROUND_TRUTH");
  assert.equal(human.entryCount, 12);
  assert.equal(claim.status, "CLAIMED_ONE_TIME_FORMAL_ADMISSION");
  assert.equal(claim.providerCallCount, 9);
  assert.equal(claim.zeroCallControlCount, 3);
  assert.equal(claim.modelInputSha256, hashes.modelInputSha256);
  assert.equal(claim.providerCallSetSha256, hashes.callSetSha256);
  assert.equal(claim.dispatchSha256, hashes.dispatchSha256);
  assert.equal(claim.derivedReceiptSha256, hashes.receiptSha256);
  assert.equal(
    claim.humanGroundTruthSealSha256,
    hashes.humanGroundTruthSha256
  );
  assert.equal(codex.modelInputSha256, hashes.modelInputSha256);
  if (codex.schemaVersion === "task-eval-006-track-b-recovery-codex-opinion-v4") {
    assert.equal(codex.executionClaimSha256?.length, 64);
    assert.equal(codex.launchReceiptSha256?.length, 64);
  } else {
    assert.equal(codex.providerCallSetSha256, hashes.callSetSha256);
    assert.equal(codex.dispatchSha256, hashes.dispatchSha256);
  }
  assert.equal(codex.blindInputOnly ?? codex.allowedInputOnly, true);
  assert.equal(codex.humanGroundTruthRead, false);
  assert.equal(codex.findingOrVerdictProduced, false);
  assert.equal(deepSeek.providerCallCount, 9);
  assert.equal(deepSeek.zeroCallControlCount, 3);
  assert.equal(deepSeek.modelInputSha256, hashes.modelInputSha256);
  assert.equal(deepSeek.providerCallSetSha256, hashes.callSetSha256);
  assert.equal(deepSeek.dispatchSha256, hashes.dispatchSha256);
  assert.equal(deepSeek.derivedReceiptSha256, hashes.receiptSha256);
  assert.equal(deepSeek.executionClaimSha256, hashes.deepSeekClaimSha256);
  assert.equal(deepSeek.rawResponsePersisted, false);
  assert.equal(deepSeek.reasoningContentPersisted, false);
  assert.equal(deepSeek.automaticRetryPerformed, false);
  assert.equal(deepSeek.providerReceipts.length, 9);
  assert.ok(
    deepSeek.providerReceipts.every(
      (entry) =>
        entry.receiptStatus === "STRICT_SCHEMA_ACCEPTED" &&
        entry.finishReason === "stop"
    )
  );

  const groundTruthByPacket = new Map(
    human.entries.map((entry) => [entry.packetId, entry])
  );
  const codexResults = evaluate(
    modelInput.packets,
    codex.opinions,
    groundTruthByPacket
  );
  const deepSeekResults = evaluate(
    modelInput.packets,
    deepSeek.opinions,
    groundTruthByPacket
  );
  const codexMetrics = metrics(codexResults);
  const deepSeekMetrics = metrics(deepSeekResults);

  const controlResults = dispatch.zeroCallControls.map((control) => {
    const truth = groundTruthByPacket.get(control.packetId);
    assert.ok(truth, `missing control truth ${control.packetId}`);
    return {
      caseId: control.caseId,
      packetId: control.packetId,
      zeroCallRequired: control.modelCallAllowed === false,
      excludedFromProviderCallSet:
        !callSet.calls.some((call) => call.packetId === control.packetId),
      excludedFromCodexOpinion:
        !codex.opinions.some((item) => item.packetId === control.packetId),
      excludedFromDeepSeekOpinion:
        !deepSeek.opinions.some((item) => item.packetId === control.packetId),
      abstentionMatched:
        truth.expected.abstain === true &&
        truth.expected.abstentionReason === control.reasonCodes[0]
    };
  });
  const passingControls = controlResults.filter((result) =>
    Object.entries(result)
      .filter(([key]) => !["caseId", "packetId"].includes(key))
      .every(([, value]) => value === true)
  ).length;
  const zeroCallControls = `${passingControls}/3`;
  const allNine = (value) =>
    Object.values(value).every((entry) => entry === "9/9");
  const go =
    allNine(codexMetrics) &&
    allNine(deepSeekMetrics) &&
    zeroCallControls === "3/3";

  const report = {
    schemaVersion: "task-eval-006-track-b-recovery-admission-report-v1",
    status: go ? "GO_9_OF_9_PLUS_3_ZERO_CALL" : "NO_GO_MODEL_MISMATCH",
    completedAt,
    purpose: "TRACK_B_EVALUATION",
    evaluatorPolicy: "CODEX_AND_DEEPSEEK_ALL_DIMENSIONS_100_PERCENT",
    modelInputSha256: hashes.modelInputSha256,
    providerCallSetSha256: hashes.callSetSha256,
    dispatchSha256: hashes.dispatchSha256,
    derivedReceiptSha256: hashes.receiptSha256,
    humanGroundTruthSha256: hashes.humanGroundTruthSha256,
    codexOpinionSha256: hashes.codexOpinionSha256,
    deepSeekExecutionClaimSha256: hashes.deepSeekClaimSha256,
    deepSeekOpinionSha256: hashes.deepSeekOpinionSha256,
    providerCallCount: 9,
    zeroCallControlCount: 3,
    automaticRetryPerformed: false,
    rawResponsePersisted: false,
    reasoningContentPersisted: false,
    codexMetrics,
    deepSeekMetrics,
    zeroCallControls,
    codexResults,
    deepSeekResults,
    controlResults,
    modelProducedFindingOrVerdict: false,
    providerAdmissionEstablished: go,
    verificationAndAuditAllowed: go,
    a0A1A2ImplementationAllowed: false
  };
  const reportBytes = jsonBytes(report);
  const seal = {
    schemaVersion: "task-eval-006-track-b-recovery-admission-seal-v1",
    status: go
      ? "SEALED_GO_TRACK_B_RECOVERY_ADMISSION"
      : "SEALED_NO_GO_TRACK_B_RECOVERY_ADMISSION",
    sealedAt: completedAt,
    reportPath: REPORT_PATH,
    reportSha256: sha256(reportBytes),
    modelInputSha256: hashes.modelInputSha256,
    providerCallSetSha256: hashes.callSetSha256,
    dispatchSha256: hashes.dispatchSha256,
    derivedReceiptSha256: hashes.receiptSha256,
    humanGroundTruthSha256: hashes.humanGroundTruthSha256,
    codexOpinionSha256: hashes.codexOpinionSha256,
    deepSeekExecutionClaimSha256: hashes.deepSeekClaimSha256,
    deepSeekOpinionSha256: hashes.deepSeekOpinionSha256,
    sameAdmissionRetryAllowed: false,
    seventhCorpusAllowed: false,
    providerAdmissionEstablished: go,
    verificationAndAuditAllowed: go,
    a0A1A2ImplementationAllowed: false
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

function evaluate(packets, opinions, groundTruthByPacket) {
  assert.equal(opinions.length, 9);
  const opinionByPacket = new Map(
    opinions.map((entry) => [entry.packetId, entry])
  );
  assert.equal(opinionByPacket.size, 9);
  return packets.map((packet) => {
    const actual = opinionByPacket.get(packet.packetId);
    const truth = groundTruthByPacket.get(packet.packetId);
    assert.ok(actual, `missing opinion for ${packet.packetId}`);
    assert.ok(truth, `missing human decision for ${packet.packetId}`);
    validateTrackBProviderRecoveryOpinionPayload(packet, {
      opinions: [actual]
    });
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
        JSON.stringify(truth.expected.selectedOccurrenceIds),
      anchorMatched:
        JSON.stringify(actual.selectedAnchorBlockIds) ===
        JSON.stringify(truth.expected.selectedAnchorBlockIds),
      abstentionMatched:
        actual.abstain === truth.expected.abstain &&
        actual.abstentionReason === truth.expected.abstentionReason
    };
  });
}

function metrics(results) {
  return Object.fromEntries(
    DIMENSIONS.map(([name, field]) => [
      name,
      `${results.filter((item) => item[field]).length}/9`
    ])
  );
}

export async function sealTrackBProviderRecoveryAdmission({
  repoRoot,
  completedAt,
  mode = "create"
}) {
  const root = resolve(repoRoot);
  const read = (relative) => readFile(resolve(root, ...relative.split("/")));
  const result = buildTrackBProviderRecoveryAdmissionEvaluation({
    modelInputBytes: await read(`${RUN_ROOT}/model-input.json`),
    callSetBytes: await read(`${RUN_ROOT}/provider-call-set.json`),
    dispatchBytes: await read(`${RUN_ROOT}/dispatch.json`),
    receiptBytes: await read(`${RUN_ROOT}/derived-egress-receipt.json`),
    humanGroundTruthBytes: await read(HUMAN_PATH),
    codexOpinionBytes: await read(`${RUN_ROOT}/codex-opinion.json`),
    deepSeekClaimBytes: await read(
      `${RUN_ROOT}/deepseek-execution-claim.json`
    ),
    deepSeekOpinionBytes: await read(`${RUN_ROOT}/deepseek-opinion.json`),
    completedAt
  });
  for (const [path, value] of [
    [REPORT_PATH, result.report],
    [SEAL_PATH, result.seal]
  ]) {
    const absolute = resolve(root, ...path.split("/"));
    if (mode === "create") {
      await mkdir(dirname(absolute), { recursive: true });
      await writeFile(absolute, jsonBytes(value), { flag: "wx" });
    } else if (mode === "verify") {
      assert.equal(
        Buffer.compare(await readFile(absolute), jsonBytes(value)),
        0,
        `${path} changed after sealing`
      );
    } else {
      throw new Error(`Unknown mode: ${mode}`);
    }
  }
  return {
    status: result.seal.status,
    ...result.hashes,
    providerAdmissionEstablished:
      result.seal.providerAdmissionEstablished,
    verificationAndAuditAllowed:
      result.seal.verificationAndAuditAllowed
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [
    repoRoot = ".",
    completedAt = new Date().toISOString(),
    mode = "create"
  ] =
    process.argv.slice(2);
  const result = await sealTrackBProviderRecoveryAdmission({
    repoRoot,
    completedAt,
    mode
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
