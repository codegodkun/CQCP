import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import test from "node:test";

import {
  buildCodexRevalidationClaim,
  buildCodexRevalidationCompletionReceipt,
  buildCodexRevalidationLaunchReceipt,
  validateCodexRevalidationEvidence
} from "./track-b-provider-recovery-codex-revalidation-contract.mjs";

const bytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");
const hash = (character) => character.repeat(64);
const packet = {
  schemaVersion: "task-eval-006-model-facing-evidence-packet-v3",
  packetId: `EP-${hash("a")}`,
  family: "PARTY_FIELDS",
  reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
  requestedRole: "PARTY_A",
  candidateOccurrences: [
    {
      occurrenceId: "OCC-001",
      value: "synthetic",
      evidence: "current operative party",
      anchor: {
        blockId: "b-1",
        locationLevel: "BLOCK_LEVEL",
        previewElementRef: null,
        sectionPath: ["synthetic"],
        regionType: "BODY",
        contextType: "NORMAL",
        reliable: true
      }
    }
  ],
  budget: {
    maxEvidenceChars: 4096,
    usedEvidenceChars: 23,
    complete: true,
    truncated: false
  },
  requiredOutput: {
    contract: "ROLE_CANDIDATE_ANCHOR_ABSTENTION_V3",
    fields: [
      "suggestedRole",
      "selectedOccurrenceIds",
      "selectedAnchorBlockIds",
      "abstain",
      "abstentionReason"
    ],
    instruction: "Only assess supplied evidence."
  }
};

function fixture() {
  const humanBytes = bytes({
    status: "ACCEPTED_HUMAN_GROUND_TRUTH",
    confirmedAt: "2026-08-03T10:00:00.000Z"
  });
  const modelInputBytes = bytes({
    groundTruthIncluded: false,
    cqcpActualOrExpectedIncluded: false,
    findingOrVerdictIncluded: false,
    packetCount: 9,
    packets: Array.from({ length: 9 }, (_, index) => ({
      ...packet,
      packetId: `EP-${String(index + 1).repeat(64).slice(0, 64)}`
    }))
  });
  const claim = buildCodexRevalidationClaim({
    claimId: `TBCX-${"1".repeat(32)}`,
    claimedAt: "2026-08-03T10:01:00.000Z",
    isolatedDirectory: "C:/tmp/isolated",
    humanGroundTruthBytes: humanBytes,
    modelInputBytes,
    providerCallSetBytes: bytes({ callCount: 9 }),
    dispatchBytes: bytes({ providerCallCount: 9 }),
    promptBytes: Buffer.from("prompt", "utf8"),
    readinessInstructionBytes: Buffer.from("ready", "utf8"),
    evaluationInstructionBytes: Buffer.from("evaluate", "utf8")
  });
  const claimBytes = bytes(claim);
  const launch = buildCodexRevalidationLaunchReceipt({
    claimBytes,
    agentId: "12345678-1234-1234-1234-123456789abc",
    canonicalTaskName: "/root/recovery_blind_revalidation",
    readinessInstructionSha256: claim.readinessInstructionSha256,
    readinessResponseBytes: bytes({
      status: "READY_NO_FILES_READ",
      readyAt: "2026-08-03T10:02:00.0000000Z",
      filesRead: 0
    }),
    recordedAt: "2026-08-03T10:03:00.000Z"
  });
  const launchBytes = bytes(launch);
  const opinionBytes = bytes({
    schemaVersion: "task-eval-006-track-b-recovery-codex-opinion-v4",
    status: "ACCEPTED_FRESH_BLIND_REVALIDATION_OPINION",
    evaluator: "codex-subagent-gpt-5.6-sol-xhigh",
    agentId: launch.agentId,
    canonicalTaskName: launch.canonicalTaskName,
    executionClaimSha256: claimBytesSha(claimBytes),
    launchReceiptSha256: claimBytesSha(launchBytes),
    evaluationInstructionSha256: claim.evaluationInstructionSha256,
    modelInputSha256: claim.modelInputSha256,
    freshContext: true,
    forkTurns: "none",
    allowedInputOnly: true,
    humanGroundTruthRead: false,
    priorModelOpinionRead: false,
    projectMemoryOrChatHistoryRead: false,
    findingOrVerdictProduced: false,
    startedAt: "2026-08-03T10:04:00.000Z",
    completedAt: "2026-08-03T10:05:00.000Z",
    opinions: JSON.parse(modelInputBytes).packets.map((item) => ({
      packetId: item.packetId,
      suggestedRole: "PARTY_A",
      selectedOccurrenceIds: ["OCC-001"],
      selectedAnchorBlockIds: ["b-1"],
      abstain: false,
      abstentionReason: null
    }))
  });
  const completion = buildCodexRevalidationCompletionReceipt({
    modelInputBytes,
    humanGroundTruthBytes: humanBytes,
    claimBytes,
    launchReceiptBytes: launchBytes,
    opinionBytes,
    receivedAt: "2026-08-03T10:06:00.000Z"
  });
  return {
    modelInputBytes,
    humanBytes,
    claimBytes,
    launchBytes,
    opinionBytes,
    completionBytes: bytes(completion)
  };
}

function claimBytesSha(value) {
  return createHash("sha256").update(value).digest("hex");
}

test("binds human seal before claim, fresh readiness, launch, opinion, and completion", () => {
  const value = fixture();
  const result = validateCodexRevalidationEvidence({
    modelInputBytes: value.modelInputBytes,
    humanGroundTruthBytes: value.humanBytes,
    claimBytes: value.claimBytes,
    launchReceiptBytes: value.launchBytes,
    opinionBytes: value.opinionBytes,
    completionReceiptBytes: value.completionBytes
  });
  assert.equal(result.opinion.opinions.length, 9);
  assert.equal(result.launch.filesReadBeforeReady, 0);
});

test("fails closed when evaluation starts before the launch receipt", () => {
  const value = fixture();
  const opinion = JSON.parse(value.opinionBytes);
  opinion.startedAt = "2026-08-03T10:02:30.000Z";
  assert.throws(
    () =>
      buildCodexRevalidationCompletionReceipt({
        modelInputBytes: value.modelInputBytes,
        humanGroundTruthBytes: value.humanBytes,
        claimBytes: value.claimBytes,
        launchReceiptBytes: value.launchBytes,
        opinionBytes: bytes(opinion),
        receivedAt: "2026-08-03T10:06:00.000Z"
      }),
    /opinion\.startedAt/
  );
});
