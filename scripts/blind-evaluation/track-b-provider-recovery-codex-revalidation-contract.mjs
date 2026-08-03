import assert from "node:assert/strict";
import { createHash } from "node:crypto";

import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";
import {
  validateTrackBProviderRecoveryOpinionPayload
} from "./track-b-provider-recovery-opinion-contract.mjs";

export const CODEX_REVALIDATION_ROOT =
  "outputs/task-eval-006/track-b-recovery-v1/run-v1/codex-revalidation-v1";

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const validIso = (value) =>
  typeof value === "string" &&
  Number.isFinite(Date.parse(value)) &&
  new Date(value).toISOString() === value;
const validTimestamp = (value) =>
  typeof value === "string" && Number.isFinite(Date.parse(value));

export function buildCodexRevalidationClaim({
  claimId,
  claimedAt,
  isolatedDirectory,
  humanGroundTruthBytes,
  modelInputBytes,
  providerCallSetBytes,
  dispatchBytes,
  promptBytes,
  readinessInstructionBytes,
  evaluationInstructionBytes
}) {
  assert.match(claimId, /^TBCX-[a-f0-9]{32}$/);
  assert.ok(validIso(claimedAt));
  assert.equal(typeof isolatedDirectory, "string");
  assert.ok(isolatedDirectory.length > 3);
  const human = parseJsonBytesRejectDuplicateKeys(humanGroundTruthBytes);
  const modelInput = parseJsonBytesRejectDuplicateKeys(modelInputBytes);
  assert.equal(human.status, "ACCEPTED_HUMAN_GROUND_TRUTH");
  assert.ok(validIso(human.confirmedAt));
  assert.ok(Date.parse(claimedAt) >= Date.parse(human.confirmedAt));
  assert.equal(modelInput.groundTruthIncluded, false);
  assert.equal(modelInput.cqcpActualOrExpectedIncluded, false);
  assert.equal(modelInput.findingOrVerdictIncluded, false);
  assert.equal(modelInput.packetCount, 9);

  return {
    schemaVersion:
      "task-eval-006-fresh-codex-blind-revalidation-claim-v1",
    status: "CLAIMED_FRESH_BLIND_REVALIDATION",
    claimId,
    claimedAt,
    humanGroundTruthConfirmedAt: human.confirmedAt,
    humanGroundTruthSealSha256: sha256(humanGroundTruthBytes),
    modelInputSha256: sha256(modelInputBytes),
    providerCallSetSha256: sha256(providerCallSetBytes),
    dispatchSha256: sha256(dispatchBytes),
    readinessInstructionSha256: sha256(readinessInstructionBytes),
    evaluationInstructionSha256: sha256(evaluationInstructionBytes),
    isolatedDirectory: isolatedDirectory.replaceAll("\\", "/"),
    allowlistedEvaluationInputs: [
      {
        fileName: "model-input.json",
        size: modelInputBytes.length,
        sha256: sha256(modelInputBytes)
      },
      {
        fileName: "prompt-v3.txt",
        size: promptBytes.length,
        sha256: sha256(promptBytes)
      }
    ],
    allowlistedControlFiles: [
      "execution-claim.json",
      "launch-receipt.json"
    ],
    excludedInputClasses: [
      "HUMAN_GROUND_TRUTH_CONTENT",
      "CQCP_ACTUAL_OR_EXPECTED",
      "FINDING_OR_VERDICT",
      "PRIOR_CODEX_OPINION",
      "DEEPSEEK_OPINION",
      "PROJECT_MEMORY_OR_CHAT_HISTORY"
    ],
    evaluatorRequirements: {
      evaluator: "codex-subagent-gpt-5.6-sol-xhigh",
      model: "gpt-5.6-sol",
      reasoningEffort: "xhigh",
      forkTurns: "none",
      readinessBeforeFileAccess: true,
      allowedInputOnly: true,
      humanGroundTruthRead: false,
      findingOrVerdictProduced: false
    },
    opinionCount: 9,
    networkCallAllowed: false,
    deepSeekRerunAllowed: false
  };
}

export function buildCodexRevalidationLaunchReceipt({
  claimBytes,
  agentId,
  canonicalTaskName,
  readinessInstructionSha256,
  readinessResponseBytes,
  recordedAt
}) {
  const claim = parseJsonBytesRejectDuplicateKeys(claimBytes);
  const readiness = parseJsonBytesRejectDuplicateKeys(readinessResponseBytes);
  assert.equal(claim.status, "CLAIMED_FRESH_BLIND_REVALIDATION");
  assert.match(agentId, /^(?:[a-f0-9-]{16,}|\/root\/[a-z0-9_]+)$/);
  assert.match(canonicalTaskName, /^\/root\/[a-z0-9_]+$/);
  if (agentId.startsWith("/root/")) assert.equal(agentId, canonicalTaskName);
  assert.match(readinessInstructionSha256, /^[a-f0-9]{64}$/);
  assert.equal(
    readinessInstructionSha256,
    claim.readinessInstructionSha256
  );
  assert.equal(readiness.status, "READY_NO_FILES_READ");
  assert.equal(readiness.filesRead, 0);
  assert.ok(Number.isFinite(Date.parse(readiness.readyAt)));
  const readyAt = new Date(readiness.readyAt).toISOString();
  assert.ok(validIso(recordedAt));
  assert.ok(Date.parse(readyAt) >= Date.parse(claim.claimedAt));
  assert.ok(Date.parse(recordedAt) >= Date.parse(readyAt));
  return {
    schemaVersion:
      "task-eval-006-fresh-codex-blind-revalidation-launch-receipt-v1",
    status: "FRESH_AGENT_READY_NO_FILES_READ",
    claimId: claim.claimId,
    executionClaimSha256: sha256(claimBytes),
    agentId,
    canonicalTaskName,
    evaluator: "codex-subagent-gpt-5.6-sol-xhigh",
    model: "gpt-5.6-sol",
    reasoningEffort: "xhigh",
    forkTurns: "none",
    readinessInstructionSha256,
    readinessResponseSha256: sha256(readinessResponseBytes),
    readyAtReported: readiness.readyAt,
    readyAt,
    recordedAt,
    filesReadBeforeReady: 0,
    inheritedConversationTurns: 0
  };
}

export function validateCodexRevalidationEvidence({
  modelInputBytes,
  humanGroundTruthBytes,
  claimBytes,
  launchReceiptBytes,
  opinionBytes,
  completionReceiptBytes
}) {
  const input = parseJsonBytesRejectDuplicateKeys(modelInputBytes);
  const human = parseJsonBytesRejectDuplicateKeys(humanGroundTruthBytes);
  const claim = parseJsonBytesRejectDuplicateKeys(claimBytes);
  const launch = parseJsonBytesRejectDuplicateKeys(launchReceiptBytes);
  const opinion = parseJsonBytesRejectDuplicateKeys(opinionBytes);
  const completion = parseJsonBytesRejectDuplicateKeys(completionReceiptBytes);
  assert.equal(claim.humanGroundTruthSealSha256, sha256(humanGroundTruthBytes));
  assert.equal(claim.modelInputSha256, sha256(modelInputBytes));
  assert.ok(Date.parse(claim.claimedAt) >= Date.parse(human.confirmedAt));
  assert.equal(launch.executionClaimSha256, sha256(claimBytes));
  assert.equal(launch.claimId, claim.claimId);
  assert.equal(launch.status, "FRESH_AGENT_READY_NO_FILES_READ");
  assert.match(launch.readinessResponseSha256, /^[a-f0-9]{64}$/);
  assert.equal(launch.filesReadBeforeReady, 0);
  assert.equal(launch.inheritedConversationTurns, 0);
  assert.equal(launch.model, "gpt-5.6-sol");
  assert.equal(launch.reasoningEffort, "xhigh");
  assert.equal(launch.forkTurns, "none");
  assert.equal(
    opinion.schemaVersion,
    "task-eval-006-track-b-recovery-codex-opinion-v4"
  );
  assert.equal(opinion.status, "ACCEPTED_FRESH_BLIND_REVALIDATION_OPINION");
  assert.equal(opinion.executionClaimSha256, sha256(claimBytes));
  assert.equal(opinion.launchReceiptSha256, sha256(launchReceiptBytes));
  assert.equal(
    opinion.evaluationInstructionSha256,
    claim.evaluationInstructionSha256
  );
  assert.equal(opinion.agentId, launch.agentId);
  assert.equal(opinion.canonicalTaskName, launch.canonicalTaskName);
  assert.equal(opinion.modelInputSha256, sha256(modelInputBytes));
  assert.equal(opinion.freshContext, true);
  assert.equal(opinion.forkTurns, "none");
  assert.equal(opinion.allowedInputOnly, true);
  assert.equal(opinion.humanGroundTruthRead, false);
  assert.equal(opinion.priorModelOpinionRead, false);
  assert.equal(opinion.projectMemoryOrChatHistoryRead, false);
  assert.equal(opinion.findingOrVerdictProduced, false);
  assert.ok(validTimestamp(opinion.startedAt));
  assert.ok(validTimestamp(opinion.completedAt));
  assert.ok(Date.parse(opinion.startedAt) >= Date.parse(launch.recordedAt));
  assert.ok(Date.parse(opinion.completedAt) >= Date.parse(opinion.startedAt));
  assert.equal(opinion.opinions.length, input.packetCount);
  const byPacket = new Map(
    opinion.opinions.map((entry) => [entry.packetId, entry])
  );
  assert.equal(byPacket.size, input.packetCount);
  for (const packet of input.packets) {
    validateTrackBProviderRecoveryOpinionPayload(packet, {
      opinions: [byPacket.get(packet.packetId)]
    });
  }
  assert.equal(
    completion.schemaVersion,
    "task-eval-006-fresh-codex-blind-revalidation-completion-receipt-v1"
  );
  assert.equal(completion.status, "SEALED_FRESH_BLIND_REVALIDATION_OUTPUT");
  assert.equal(completion.executionClaimSha256, sha256(claimBytes));
  assert.equal(completion.launchReceiptSha256, sha256(launchReceiptBytes));
  assert.equal(completion.opinionSha256, sha256(opinionBytes));
  assert.equal(completion.agentId, launch.agentId);
  assert.equal(completion.opinionCount, 9);
  assert.ok(validIso(completion.receivedAt));
  assert.ok(Date.parse(completion.receivedAt) >= Date.parse(opinion.completedAt));
  return { claim, launch, opinion, completion };
}

export function buildCodexRevalidationCompletionReceipt({
  modelInputBytes,
  humanGroundTruthBytes,
  claimBytes,
  launchReceiptBytes,
  opinionBytes,
  receivedAt
}) {
  const launch = parseJsonBytesRejectDuplicateKeys(launchReceiptBytes);
  const opinion = parseJsonBytesRejectDuplicateKeys(opinionBytes);
  assert.ok(validIso(receivedAt));
  const receipt = {
    schemaVersion:
      "task-eval-006-fresh-codex-blind-revalidation-completion-receipt-v1",
    status: "SEALED_FRESH_BLIND_REVALIDATION_OUTPUT",
    receivedAt,
    executionClaimSha256: sha256(claimBytes),
    launchReceiptSha256: sha256(launchReceiptBytes),
    opinionSha256: sha256(opinionBytes),
    agentId: launch.agentId,
    canonicalTaskName: launch.canonicalTaskName,
    modelInputSha256: sha256(modelInputBytes),
    humanGroundTruthSealSha256: sha256(humanGroundTruthBytes),
    opinionCount: opinion.opinions?.length,
    networkCallPerformed: false,
    deepSeekRerunPerformed: false
  };
  validateCodexRevalidationEvidence({
    modelInputBytes,
    humanGroundTruthBytes,
    claimBytes,
    launchReceiptBytes,
    opinionBytes,
    completionReceiptBytes: Buffer.from(
      `${JSON.stringify(receipt, null, 2)}\n`,
      "utf8"
    )
  });
  return receipt;
}

export const codexRevalidationSha256 = sha256;
