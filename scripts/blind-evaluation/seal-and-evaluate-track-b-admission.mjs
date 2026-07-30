import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import {
  access,
  readFile,
  writeFile
} from "node:fs/promises";
import { resolve } from "node:path";

import {
  validateTrackBAdmissionCorpus,
  validateTrackBAdmissionModelInput,
  validateTrackBAdmissionOpinionPayload
} from "./track-b-admission-opinion-contract.mjs";
import {
  validateTrackBEgressAuthorization
} from "./track-b-admission-authorization.mjs";
import {
  TRACK_B_ADMISSION_COUNTS,
  TRACK_B_ADMISSION_TRUSTED_PROMPT_SHA256
} from "./track-b-admission-constants.mjs";
import {
  buildTrackBProviderCallSetArtifact,
  buildTrackBProviderCalls
} from "./track-b-provider-request-contract.mjs";
import {
  validateTrackBDeepSeekExecutionClaim
} from "./track-b-deepseek-execution-claim-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const mode = process.argv[3];
const sealedAtArg = process.argv[4];
if (!["seal", "verify", "unblind"].includes(mode)) {
  throw new Error(
    "Usage: node seal-and-evaluate-track-b-admission.mjs " +
      "<repo-root> <seal|verify|unblind> [sealed-at]"
  );
}
if (
  mode === "seal" &&
  (!sealedAtArg || Number.isNaN(Date.parse(sealedAtArg)))
) {
  throw new Error("seal mode requires a valid ISO sealed-at timestamp");
}

const runRoot = "outputs/task-eval-002/track-b-admission-run-v3";
const dispatchRelativePath = `${runRoot}/dispatch.json`;
const dispatchHashRelativePath = `${runRoot}/dispatch.sha256`;
const codexRelativePath = `${runRoot}/codex-opinion.json`;
const codexReceiptRelativePath =
  `${runRoot}/codex-execution-receipt.json`;
const deepSeekRelativePath = `${runRoot}/deepseek-opinion.json`;
const deepSeekClaimRelativePath =
  `${runRoot}/deepseek-execution-claim.json`;
const sealRelativePath = `${runRoot}/opinion-seal.json`;
const reportRelativePath = `${runRoot}/admission-report.json`;
const admissionRelativePath = "outputs/task-eval-002/track-b-admission.json";
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const readRelative = async (relativePath) =>
  readFile(resolve(repoRoot, ...relativePath.split("/")));

const dispatchBytes = await readRelative(dispatchRelativePath);
const dispatchSha256 = sha256(dispatchBytes);
const expectedDispatchSha256 = (
  await readRelative(dispatchHashRelativePath)
)
  .toString("utf8")
  .trim()
  .split(/\s+/)[0];
assert.equal(dispatchSha256, expectedDispatchSha256, "Dispatch hash changed");
const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
assert.equal(
  dispatch.status,
  "FROZEN_AFTER_HUMAN_CONFIRMATION_BEFORE_MODEL_EXECUTION"
);
const inputBytes = await readRelative(dispatch.modelInputPath);
assert.equal(sha256(inputBytes), dispatch.modelInputSha256);
const input = validateTrackBAdmissionModelInput(
  JSON.parse(inputBytes.toString("utf8"))
);
const promptBytes = await readRelative(dispatch.promptPath);
assert.equal(
  sha256(promptBytes),
  TRACK_B_ADMISSION_TRUSTED_PROMPT_SHA256
);
const providerCalls = buildTrackBProviderCalls(input, promptBytes);
const expectedProviderCallSet =
  buildTrackBProviderCallSetArtifact(input, promptBytes);
const providerCallSetBytes = await readRelative(
  dispatch.providerCallSetPath
);
assert.equal(
  sha256(providerCallSetBytes),
  dispatch.providerCallSetSha256
);
assert.deepEqual(
  JSON.parse(providerCallSetBytes.toString("utf8")),
  expectedProviderCallSet
);
const codexBytes = await readRelative(codexRelativePath);
const codexReceiptBytes = await readRelative(codexReceiptRelativePath);
const deepSeekBytes = await readRelative(deepSeekRelativePath);
const deepSeekClaimBytes = await readRelative(deepSeekClaimRelativePath);
const codex = JSON.parse(codexBytes.toString("utf8"));
const codexReceipt = JSON.parse(codexReceiptBytes.toString("utf8"));
const deepSeek = JSON.parse(deepSeekBytes.toString("utf8"));

assert.deepEqual(
  Object.keys(codex).sort(),
  [
    "schemaVersion",
    "status",
    "track",
    "evaluator",
    "agentTask",
    "agentId",
    "inputSha256",
    "dispatchSha256",
    "executionReceiptSha256",
    "startedAt",
    "completedAt",
    "opinions"
  ].sort()
);
assert.equal(
  codex.schemaVersion,
  "task-eval-002-track-b-admission-model-opinion-v1"
);
assert.equal(codex.status, "ACCEPTED");
assert.equal(codex.track, input.track);
assert.equal(codex.evaluator, "codex-blind-agent");
assert.equal(codex.agentTask, dispatch.codexAgentTask);
assert.equal(typeof codex.agentId, "string");
assert.ok(codex.agentId.trim());
assert.equal(codex.inputSha256, dispatch.modelInputSha256);
assert.equal(codex.dispatchSha256, dispatchSha256);
assert.equal(
  codex.executionReceiptSha256,
  sha256(codexReceiptBytes)
);
assert.deepEqual(
  Object.keys(codexReceipt).sort(),
  [
    "schemaVersion",
    "source",
    "forkTurns",
    "agentTask",
    "agentId",
    "modelInputSha256",
    "dispatchSha256",
    "rawAgentOutputPath",
    "rawAgentOutputSha256",
    "startedAt",
    "completedAt",
    "transcriptCrossCheckRequired"
  ].sort()
);
assert.equal(
  codexReceipt.schemaVersion,
  "task-eval-002-track-b-admission-codex-execution-receipt-v1"
);
assert.equal(codexReceipt.source, "CODEX_COLLABORATION_TOOL");
assert.equal(codexReceipt.forkTurns, "none");
assert.equal(codexReceipt.agentTask, codex.agentTask);
assert.equal(codexReceipt.agentId, codex.agentId);
assert.equal(codexReceipt.modelInputSha256, codex.inputSha256);
assert.equal(codexReceipt.dispatchSha256, codex.dispatchSha256);
assert.equal(codexReceipt.startedAt, codex.startedAt);
assert.equal(codexReceipt.completedAt, codex.completedAt);
assert.equal(codexReceipt.transcriptCrossCheckRequired, true);
const rawCodexOutputBytes = await readRelative(
  codexReceipt.rawAgentOutputPath
);
assert.equal(
  sha256(rawCodexOutputBytes),
  codexReceipt.rawAgentOutputSha256
);
assert.deepEqual(
  JSON.parse(rawCodexOutputBytes.toString("utf8")),
  { opinions: codex.opinions }
);
validateTrackBAdmissionOpinionPayload(input, { opinions: codex.opinions });

assert.deepEqual(
  Object.keys(deepSeek).sort(),
  [
    "schemaVersion",
    "status",
    "track",
    "evaluator",
    "inputSha256",
    "dispatchSha256",
    "authorizationSha256",
    "authorization",
    "networkAttempted",
    "executionMode",
    "executionClaimSha256",
    "resolverVersion",
    "pinnedAddressSetSha256",
    "providerCallSetSha256",
    "providerCallCount",
    "providerResponses",
    "finishReason",
    "startedAt",
    "completedAt",
    "opinions"
  ].sort()
);
assert.equal(
  deepSeek.schemaVersion,
  "task-eval-002-track-b-admission-model-opinion-v2"
);
assert.equal(deepSeek.status, "ACCEPTED");
assert.equal(deepSeek.track, input.track);
assert.equal(deepSeek.evaluator, "deepseek-v4-pro");
assert.equal(deepSeek.inputSha256, dispatch.modelInputSha256);
assert.equal(deepSeek.dispatchSha256, dispatchSha256);
const authorizationBytes = await readRelative(
  dispatch.egressAuthorizationPath
);
const egressChallengeBytes = await readRelative(
  dispatch.egressAuthorizationChallengePath
);
const egressChallenge = JSON.parse(
  egressChallengeBytes.toString("utf8")
);
assert.equal(
  deepSeek.authorizationSha256,
  sha256(authorizationBytes),
  "Track B egress authorization changed"
);
const authorizationEvidence = validateTrackBEgressAuthorization({
  authorization: JSON.parse(authorizationBytes.toString("utf8")),
  challenge: egressChallenge,
  challengeSha256: sha256(egressChallengeBytes),
  inputSha256: dispatch.modelInputSha256,
  dispatchSha256,
  dispatchCreatedAt: dispatch.createdAt,
  validationTime: deepSeek.startedAt
});
assert.deepEqual(
  deepSeek.authorization,
  authorizationEvidence,
  "DeepSeek opinion authorization evidence is invalid"
);
assert.equal(deepSeek.networkAttempted, true);
assert.equal(deepSeek.executionMode, "run");
assert.equal(
  deepSeek.executionClaimSha256,
  sha256(deepSeekClaimBytes)
);
const deepSeekClaim = JSON.parse(deepSeekClaimBytes.toString("utf8"));
validateTrackBDeepSeekExecutionClaim(deepSeekClaim, {
  dispatchSha256,
  modelInputSha256: dispatch.modelInputSha256,
  providerCallSetSha256: dispatch.providerCallSetSha256,
  providerCallCount: providerCalls.length,
  egressAuthorizationSha256: sha256(authorizationBytes),
  egressChallengeSha256: sha256(egressChallengeBytes),
  pinnedAddressSetSha256: deepSeek.pinnedAddressSetSha256,
  startedAt: deepSeek.startedAt,
  completedAt: deepSeek.completedAt
});
assert.equal(deepSeek.resolverVersion, deepSeekClaim.resolverVersion);
assert.equal(
  deepSeek.pinnedAddressSetSha256,
  deepSeekClaim.pinnedAddressSetSha256
);
assert.equal(
  deepSeek.providerCallSetSha256,
  dispatch.providerCallSetSha256
);
assert.equal(deepSeek.providerCallCount, providerCalls.length);
assert.ok(Array.isArray(deepSeek.providerResponses));
assert.equal(deepSeek.providerResponses.length, providerCalls.length);
for (const [index, receipt] of deepSeek.providerResponses.entries()) {
  assert.deepEqual(
    Object.keys(receipt).sort(),
    [
      "callId",
      "receiptStatus",
      "finishReason"
    ].sort()
  );
  assert.equal(receipt.callId, providerCalls[index].callId);
  assert.equal(receipt.receiptStatus, "STRICT_SCHEMA_ACCEPTED");
  assert.equal(receipt.finishReason, "stop");
}
assert.equal(deepSeek.finishReason, "stop");
validateTrackBAdmissionOpinionPayload(input, {
  opinions: deepSeek.opinions
});

const dispatchCreatedAt = Date.parse(dispatch.createdAt);
for (const opinion of [codex, deepSeek]) {
  const startedAt = Date.parse(opinion.startedAt);
  const completedAt = Date.parse(opinion.completedAt);
  assert.ok(Number.isFinite(startedAt) && Number.isFinite(completedAt));
  assert.ok(startedAt >= dispatchCreatedAt);
  assert.ok(completedAt >= startedAt);
  assert.ok(completedAt <= Date.now() + 300_000);
}

const sealCore = {
  schemaVersion: "task-eval-002-track-b-admission-opinion-seal-v1",
  status: "BOTH_BLIND_MODEL_OPINIONS_SEALED_BEFORE_UNBLIND",
  dispatchPath: dispatchRelativePath,
  dispatchSha256,
  modelInputPath: dispatch.modelInputPath,
  modelInputSha256: dispatch.modelInputSha256,
  providerCallSetPath: dispatch.providerCallSetPath,
  providerCallSetSha256: dispatch.providerCallSetSha256,
  providerRequestBuilderVersion:
    dispatch.providerRequestBuilderVersion,
  providerCallCount: dispatch.providerCallCount,
  outboundRequestSha256s: providerCalls.map(
    (call) => call.outboundRequestSha256
  ),
  promptPath: dispatch.promptPath,
  promptSha256: dispatch.promptSha256,
  humanGroundTruthSealSha256: dispatch.humanGroundTruthSealSha256,
  humanGroundTruthReadDuringSeal: false,
  egressAuthorizationPath: dispatch.egressAuthorizationPath,
  egressAuthorizationSha256: sha256(authorizationBytes),
  egressAuthorization: authorizationEvidence,
  egressAuthorizationChallengePath:
    dispatch.egressAuthorizationChallengePath,
  egressAuthorizationChallengeSha256: sha256(egressChallengeBytes),
  deepSeekExecutionClaimPath: deepSeekClaimRelativePath,
  deepSeekExecutionClaimSha256: sha256(deepSeekClaimBytes),
  eligiblePacketCount: TRACK_B_ADMISSION_COUNTS.eligible,
  zeroCallControlCount: TRACK_B_ADMISSION_COUNTS.zeroCallControls,
  opinions: [
    {
      evaluator: codex.evaluator,
      path: codexRelativePath,
      sha256: sha256(codexBytes),
      executionReceiptPath: codexReceiptRelativePath,
      executionReceiptSha256: sha256(codexReceiptBytes),
      opinionCount: codex.opinions.length
    },
    {
      evaluator: deepSeek.evaluator,
      path: deepSeekRelativePath,
      sha256: sha256(deepSeekBytes),
      opinionCount: deepSeek.opinions.length
    }
  ]
};
const sealPath = resolve(repoRoot, ...sealRelativePath.split("/"));

if (mode === "seal") {
  try {
    await access(resolve(repoRoot, ...reportRelativePath.split("/")));
    throw new Error("Admission report already exists before opinion sealing");
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
  }
  const sealedAt = new Date(sealedAtArg).toISOString();
  assert.ok(
    Date.parse(sealedAt) >=
      Math.max(Date.parse(codex.completedAt), Date.parse(deepSeek.completedAt))
  );
  assert.ok(
    Date.parse(sealedAt) <= Date.now() + 300_000,
    "sealedAt cannot be in the future"
  );
  assert.equal(
    dispatch.promptSha256,
    TRACK_B_ADMISSION_TRUSTED_PROMPT_SHA256
  );
  const seal = { ...sealCore, sealedAt };
  await writeFile(
    sealPath,
    `${JSON.stringify(seal, null, 2)}\n`,
    { encoding: "utf8", flag: "wx" }
  );
  process.stdout.write(
    `${JSON.stringify({
      status: seal.status,
      opinionCount: seal.opinions.reduce(
        (total, opinion) => total + opinion.opinionCount,
        0
      ),
      sealSha256: sha256(await readFile(sealPath))
    })}\n`
  );
  process.exit(0);
}

const sealBytes = await readFile(sealPath);
const seal = JSON.parse(sealBytes.toString("utf8"));
assert.deepEqual(
  { ...seal, sealedAt: undefined },
  { ...sealCore, sealedAt: undefined },
  "Track B admission opinion seal changed"
);
assert.ok(
  Date.parse(seal.sealedAt) >=
    Math.max(Date.parse(codex.completedAt), Date.parse(deepSeek.completedAt))
);
assert.ok(Date.parse(seal.sealedAt) <= Date.now() + 300_000);
if (mode === "verify") {
  process.stdout.write(
    `${JSON.stringify({
      status: "TRACK_B_ADMISSION_OPINION_SEAL_VERIFIED",
      sealSha256: sha256(sealBytes)
    })}\n`
  );
  process.exit(0);
}

const humanGroundTruthBytes = await readRelative(
  dispatch.humanGroundTruthSealPath
);
assert.equal(
  sha256(humanGroundTruthBytes),
  dispatch.humanGroundTruthSealSha256,
  "Human ground truth seal changed"
);
const humanGroundTruth = JSON.parse(
  humanGroundTruthBytes.toString("utf8")
);
assert.equal(
  humanGroundTruth.status,
  "ACCEPTED_HUMAN_GROUND_TRUTH"
);
assert.equal(
  humanGroundTruth.entryCount,
  TRACK_B_ADMISSION_COUNTS.total
);
const corpusBytes = await readRelative(dispatch.corpusPath);
assert.equal(sha256(corpusBytes), dispatch.corpusSha256);
assert.equal(humanGroundTruth.corpusSha256, dispatch.corpusSha256);
const corpus = JSON.parse(corpusBytes.toString("utf8"));
const validatedCorpus = validateTrackBAdmissionCorpus(
  corpus,
  dispatch.corpusSha256
);
assert.equal(
  validatedCorpus.eligiblePackets.length,
  TRACK_B_ADMISSION_COUNTS.eligible
);
assert.equal(
  validatedCorpus.zeroCallPackets.length,
  TRACK_B_ADMISSION_COUNTS.zeroCallControls
);
assert.equal(
  new Set(humanGroundTruth.entries.map((entry) => entry.caseId)).size,
  TRACK_B_ADMISSION_COUNTS.total
);
assert.equal(
  new Set(humanGroundTruth.entries.map((entry) => entry.packetId)).size,
  TRACK_B_ADMISSION_COUNTS.total
);
assert.deepEqual(
  new Set(humanGroundTruth.entries.map((entry) => entry.packetId)),
  new Set(corpus.packets.map((packet) => packet.packetId))
);
const expectedByPacket = new Map(
  humanGroundTruth.entries.map((entry) => [entry.packetId, entry.expected])
);
const codexByPacket = new Map(
  codex.opinions.map((opinion) => [opinion.packetId, opinion])
);
const deepSeekByPacket = new Map(
  deepSeek.opinions.map((opinion) => [opinion.packetId, opinion])
);
const comparisons = [];
let zeroCallControlMatches = 0;
let codexEligibleMatches = 0;
let deepSeekEligibleMatches = 0;
for (const packet of corpus.packets) {
  const expected = expectedByPacket.get(packet.packetId);
  assert.ok(expected, `Missing human expected: ${packet.packetId}`);
  if (packet.admission.modelCallAllowed) {
    const codexOpinion = codexByPacket.get(packet.packetId);
    const deepSeekOpinion = deepSeekByPacket.get(packet.packetId);
    assert.ok(codexOpinion && deepSeekOpinion);
    const codexMatches = sameOutcome(codexOpinion, expected);
    const deepSeekMatches = sameOutcome(deepSeekOpinion, expected);
    if (codexMatches) codexEligibleMatches += 1;
    if (deepSeekMatches) deepSeekEligibleMatches += 1;
    comparisons.push({
      packetId: packet.packetId,
      caseId: packet.sampleId,
      executionSource: "MODEL_ELIGIBLE",
      codexMatchesHuman: codexMatches,
      deepSeekMatchesHuman: deepSeekMatches,
      policyControlMatchesHuman: null
    });
  } else {
    assert.ok(!codexByPacket.has(packet.packetId));
    assert.ok(!deepSeekByPacket.has(packet.packetId));
    const policyOutcome = {
      suggestedRole: null,
      selectedOccurrenceIds: [],
      selectedAnchorBlockIds: [],
      abstain: true,
      abstentionReason: packet.admission.reasonCodes[0]
    };
    const policyMatches = sameOutcome(policyOutcome, expected);
    if (policyMatches) zeroCallControlMatches += 1;
    comparisons.push({
      packetId: packet.packetId,
      caseId: packet.sampleId,
      executionSource: "POLICY_ZERO_CALL_NOT_SENT_TO_MODEL",
      codexMatchesHuman: null,
      deepSeekMatchesHuman: null,
      policyControlMatchesHuman: policyMatches
    });
  }
}
const allExact =
  codexEligibleMatches === TRACK_B_ADMISSION_COUNTS.eligible &&
  deepSeekEligibleMatches === TRACK_B_ADMISSION_COUNTS.eligible &&
  zeroCallControlMatches === TRACK_B_ADMISSION_COUNTS.zeroCallControls;
const report = {
  schemaVersion: "task-eval-002-track-b-admission-report-v1",
  status: allExact ? "ADMITTED" : "NO_GO_MODEL_MISMATCH",
  providerAdmission: allExact ? "ESTABLISHED" : "NOT_ESTABLISHED",
  modelOutputTerminology: "模型意见",
  opinionSealPath: sealRelativePath,
  opinionSealSha256: sha256(sealBytes),
  humanGroundTruthSealPath: dispatch.humanGroundTruthSealPath,
  humanGroundTruthSealSha256: dispatch.humanGroundTruthSealSha256,
  egressAuthorizationPath: dispatch.egressAuthorizationPath,
  egressAuthorizationSha256: sha256(authorizationBytes),
  egressAuthorization: authorizationEvidence,
  egressAuthorizationChallengePath:
    dispatch.egressAuthorizationChallengePath,
  egressAuthorizationChallengeSha256: sha256(egressChallengeBytes),
  deepSeekExecutionClaimPath: deepSeekClaimRelativePath,
  deepSeekExecutionClaimSha256: sha256(deepSeekClaimBytes),
  corpusPath: dispatch.corpusPath,
  corpusSha256: dispatch.corpusSha256,
  providerCallSetPath: dispatch.providerCallSetPath,
  providerCallSetSha256: dispatch.providerCallSetSha256,
  providerRequestBuilderVersion:
    dispatch.providerRequestBuilderVersion,
  providerCallCount: dispatch.providerCallCount,
  outboundRequestSha256s: providerCalls.map(
    (call) => call.outboundRequestSha256
  ),
  metrics: {
    eligiblePacketCount: TRACK_B_ADMISSION_COUNTS.eligible,
    zeroCallControlCount: TRACK_B_ADMISSION_COUNTS.zeroCallControls,
    strictSchemaAcceptedPercent: 100,
    reliableAnchorPercent: 100,
    codexEligibleMatchesHuman: codexEligibleMatches,
    deepSeekEligibleMatchesHuman: deepSeekEligibleMatches,
    zeroCallControlsMatchHuman: zeroCallControlMatches,
    deterministicHighSentToModel: false,
    findingOrVerdictProduced: false
  },
  comparisons
};
await writeFile(
  resolve(repoRoot, ...reportRelativePath.split("/")),
  `${JSON.stringify(report, null, 2)}\n`,
  { encoding: "utf8", flag: "wx" }
);
const admission = {
  schemaVersion: "task-eval-002-track-b-admission-v3",
  status: report.status,
  modelCallsAllowed: allExact,
  task034CandidateAnchorGate: "R7_PASS",
  runtimeIsomorphicEvidencePacketSeam: "IMPLEMENTED_AND_TESTED",
  providerAdmission: report.providerAdmission,
  admissionReportPath: reportRelativePath,
  admissionReportSha256: sha256(
    await readRelative(reportRelativePath)
  ),
  blockingReasons: allExact
    ? []
    : ["Codex and DeepSeek model opinions must match every human answer."]
};
await writeFile(
  resolve(repoRoot, ...admissionRelativePath.split("/")),
  `${JSON.stringify(admission, null, 2)}\n`,
  "utf8"
);
process.stdout.write(
  `${JSON.stringify({
    status: report.status,
    providerAdmission: report.providerAdmission,
    codexEligibleMatches,
    deepSeekEligibleMatches,
    zeroCallControlMatches
  })}\n`
);
if (!allExact) process.exitCode = 2;

function sameOutcome(actual, expected) {
  return (
    actual.suggestedRole === expected.suggestedRole &&
    JSON.stringify(actual.selectedOccurrenceIds) ===
      JSON.stringify(expected.selectedOccurrenceIds) &&
    JSON.stringify(actual.selectedAnchorBlockIds) ===
      JSON.stringify(expected.selectedAnchorBlockIds) &&
    actual.abstain === expected.abstain &&
    actual.abstentionReason === expected.abstentionReason
  );
}
