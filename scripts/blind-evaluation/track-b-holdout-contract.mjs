import assert from "node:assert/strict";
import { createHash } from "node:crypto";

export const TRACK_B_HOLDOUT_COUNTS = Object.freeze({
  total: 12,
  eligible: 9,
  medium: 5,
  conflicted: 4,
  zeroCallControls: 3
});

export const TRACK_B_HOLDOUT_OUTPUT_CONTRACT = Object.freeze({
  contract: "ROLE_CANDIDATE_ANCHOR_ABSTENTION_V1",
  fields: Object.freeze([
    "suggestedRole",
    "selectedOccurrenceIds",
    "selectedAnchorBlockIds",
    "abstain",
    "abstentionReason"
  ]),
  instruction:
    "Only assess supplied role/candidate/anchor evidence. " +
    "Do not produce final business adjudication."
});

const HOLDOUT_PROFILE = Object.freeze({
  sourceSchema:
    "task-eval-002-track-b-holdout-source-signals-v1",
  proposalSchema:
    "task-eval-002-track-b-holdout-proposed-decisions-v1",
  corpusSchema: "task-eval-002-track-b-holdout-corpus-v1",
  draftSchema:
    "task-eval-002-track-b-holdout-human-review-draft-v1",
  outputRoot: "outputs/task-eval-002/track-b-holdout-v1",
  taskPattern: /^task-eval-002-track-b-holdout-/,
  executionPattern: /^execution-track-b-holdout-/,
  casePattern: /^TBH-(MED|CON|CTL)-/,
  seamCaseId: "TBH-CON-003",
  seamRequiredBlockIds: ["tbh-con-003-b01", "tbh-con-003-b02"],
  seamExcludedBlockId: "tbh-con-003-b03",
  reviewTitle:
    "TASK-EVAL-002 Track B 12-packet holdout 人工确认表"
});

const SUCCESSOR_PROFILE = Object.freeze({
  sourceSchema:
    "task-eval-003-track-b-successor-source-signals-v1",
  proposalSchema:
    "task-eval-003-track-b-successor-proposed-decisions-v1",
  corpusSchema: "task-eval-003-track-b-successor-corpus-v1",
  draftSchema:
    "task-eval-003-track-b-successor-human-review-draft-v1",
  outputRoot: "outputs/task-eval-003/track-b-successor-v1",
  taskPattern: /^task-eval-003-track-b-successor-/,
  executionPattern: /^execution-track-b-successor-/,
  casePattern: /^TBS-(MED|CON|CTL)-/,
  seamCaseId: "TBS-CON-003",
  seamRequiredBlockIds: ["tbs-con-003-b01", "tbs-con-003-b02"],
  seamExcludedBlockId: "tbs-con-003-b03",
  reviewTitle:
    "TASK-EVAL-003 Track B successor 12-packet 人工确认表"
});
const FINAL_PROFILE = Object.freeze({
  sourceSchema:
    "task-eval-004-track-b-final-source-signals-v1",
  proposalSchema:
    "task-eval-004-track-b-final-proposed-decisions-v1",
  corpusSchema: "task-eval-004-track-b-final-corpus-v1",
  draftSchema:
    "task-eval-004-track-b-final-human-review-draft-v1",
  outputRoot: "outputs/task-eval-004/track-b-final-v1",
  taskPattern: /^task-eval-004-track-b-final-/,
  executionPattern: /^execution-track-b-final-/,
  casePattern: /^TBF-(MED|CON|CTL)-/,
  seamCaseId: "TBF-CON-003",
  seamRequiredBlockIds: ["tbf-con-003-b01", "tbf-con-003-b02"],
  seamExcludedBlockId: "tbf-con-003-b03",
  reviewTitle:
    "TASK-EVAL-004 Track B final 12-packet 人工确认表"
});
const FIFTH_PROFILE = Object.freeze({
  sourceSchema:
    "task-eval-005-track-b-fifth-source-signals-v1",
  proposalSchema:
    "task-eval-005-track-b-fifth-proposed-decisions-v1",
  corpusSchema: "task-eval-005-track-b-fifth-corpus-v1",
  draftSchema:
    "task-eval-005-track-b-fifth-human-review-draft-v1",
  outputRoot: "outputs/task-eval-005/track-b-fifth-v1",
  taskPattern: /^task-eval-005-track-b-fifth-/,
  executionPattern: /^execution-track-b-fifth-/,
  casePattern: /^TB5-(MED|CON|CTL)-/,
  seamCaseId: "TB5-CON-003",
  seamRequiredBlockIds: ["tb5-con-003-b01", "tb5-con-003-b02"],
  seamExcludedBlockId: "tb5-con-003-b03",
  reviewTitle:
    "TASK-EVAL-005 Track B fifth 12-packet 人工确认表"
});
const RECOVERY_PROFILE = Object.freeze({
  sourceSchema:
    "task-eval-006-track-b-recovery-source-signals-v1",
  proposalSchema:
    "task-eval-006-track-b-recovery-proposed-decisions-v1",
  corpusSchema: "task-eval-006-track-b-recovery-corpus-v1",
  draftSchema:
    "task-eval-006-track-b-recovery-human-review-draft-v1",
  outputRoot: "outputs/task-eval-006/track-b-recovery-v1",
  taskPattern: /^task-eval-006-track-b-recovery-/,
  executionPattern: /^execution-track-b-recovery-/,
  casePattern: /^TB6-(MED|CON|CTL)-/,
  seamCaseId: "TB6-CON-003",
  seamRequiredBlockIds: ["tb6-con-003-b01", "tb6-con-003-b02"],
  seamExcludedBlockId: "tb6-con-003-b03",
  reviewTitle:
    "TASK-EVAL-006 Track B recovery 12-packet 人工确认表"
});
const PACKET_SCHEMA = "task036-runtime-evidence-packet-v1";
const FORBIDDEN_CONTEXTS = new Set([
  "DELETED",
  "VOIDED",
  "TOC",
  "HEADER_FOOTER"
]);
const FAMILY_BY_POINT = new Map([
  ["PARTY_A_NAME_CONSISTENCY", "PARTY_FIELDS"],
  ["PARTY_B_NAME_CONSISTENCY", "PARTY_FIELDS"],
  ["CONTRACT_TOTAL_AMOUNT_CONSISTENCY", "AMOUNT_TAX"],
  ["TAX_AMOUNT_FORMULA_CONSISTENCY", "AMOUNT_TAX"],
  ["PREPAYMENT_RATIO_CONSISTENCY", "PAYMENT_TERMS"],
  ["PROGRESS_PAYMENT_RATIO_CONSISTENCY", "PAYMENT_TERMS"],
  ["COMPLETION_PAYMENT_RATIO_CONSISTENCY", "PAYMENT_TERMS"],
  ["SETTLEMENT_PAYMENT_RATIO_CONSISTENCY", "PAYMENT_TERMS"],
  ["WARRANTY_RETENTION_RATIO_CONSISTENCY", "PAYMENT_TERMS"]
]);

export function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

export function jsonBytes(value) {
  return Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");
}

export function buildTrackBHoldoutArtifacts({
  source,
  sourcePath,
  sourceSha256,
  proposals,
  proposalsPath,
  proposalsSha256,
  generatedAt,
  profile = HOLDOUT_PROFILE
}) {
  validateSource(source, profile);
  validateProposalsShape(proposals, profile);
  const generatedAtIso = canonicalIso(generatedAt, "generatedAt");
  const packets = source.cases.map((item) => packetFor(source, item));
  const corpus = {
    schemaVersion: profile.corpusSchema,
    status: "DRAFT_PENDING_HUMAN_GROUND_TRUTH_CONFIRMATION",
    generatedAt: generatedAtIso,
    source: source.source,
    sourceSignalsPath: sourcePath,
    sourceSignalsSha256: sourceSha256,
    containsProductionContractText: false,
    groundTruthIncluded: false,
    packetCount: packets.length,
    eligiblePacketCount: packets.filter(
      (packet) => packet.admission.modelCallAllowed
    ).length,
    mediumEligibleCount: packets.filter(
      (packet) =>
        packet.admission.modelCallAllowed &&
        packet.admission.reasonCodes[0] ===
          "ELIGIBLE_MEDIUM_AMBIGUITY"
    ).length,
    conflictedEligibleCount: packets.filter(
      (packet) =>
        packet.admission.modelCallAllowed &&
        packet.admission.reasonCodes[0] ===
          "ELIGIBLE_CONFLICT_LOCAL_CONTEXT"
    ).length,
    zeroCallControlCount: packets.filter(
      (packet) => !packet.admission.modelCallAllowed
    ).length,
    packets
  };
  validateTrackBCorpus(corpus, profile);
  const corpusSha256 = sha256(jsonBytes(corpus));
  const proposalByCaseId = new Map(
    proposals.entries.map((entry) => [entry.caseId, entry])
  );
  const entries = packets.map((packet) => {
    const proposal = proposalByCaseId.get(packet.sampleId);
    assert.ok(proposal, `Missing proposal for ${packet.sampleId}`);
    proposalByCaseId.delete(packet.sampleId);
    validateProposalAgainstPacket(proposal, packet);
    return {
      caseId: packet.sampleId,
      packetId: packet.packetId,
      confidenceClass: source.cases.find(
        (item) => item.caseId === packet.sampleId
      ).confidence,
      humanDecision: null,
      proposedRationale: proposal.proposedRationale,
      proposedExpected: proposal.proposedExpected
    };
  });
  assert.equal(proposalByCaseId.size, 0, "Unknown proposal caseId");
  const draft = {
    schemaVersion: profile.draftSchema,
    status: "PENDING_HUMAN_CONFIRMATION_NOT_GROUND_TRUTH",
    draftedBy: "CODEX",
    humanGroundTruthEstablished: false,
    corpusPath: `${profile.outputRoot}/corpus.json`,
    corpusSha256,
    proposedDecisionsPath: proposalsPath,
    proposedDecisionsSha256: proposalsSha256,
    confirmationInstruction:
      "人工逐项确认 proposedExpected；确认前不得作为 ground truth，" +
      "不得提供给任何盲评模型。",
    entries
  };
  const reviewMarkdown = renderReviewMarkdown(draft, corpus, profile);
  return { corpus, draft, reviewMarkdown };
}

export function validateTrackBHoldoutCorpus(corpus) {
  return validateTrackBCorpus(corpus, HOLDOUT_PROFILE);
}

export function buildTrackBSuccessorArtifacts(args) {
  return buildTrackBHoldoutArtifacts({
    ...args,
    profile: SUCCESSOR_PROFILE
  });
}

export function validateTrackBSuccessorCorpus(corpus) {
  return validateTrackBCorpus(corpus, SUCCESSOR_PROFILE);
}

export function buildTrackBFinalArtifacts(args) {
  return buildTrackBHoldoutArtifacts({
    ...args,
    profile: FINAL_PROFILE
  });
}

export function validateTrackBFinalCorpus(corpus) {
  return validateTrackBCorpus(corpus, FINAL_PROFILE);
}

export function buildTrackBFifthArtifacts(args) {
  return buildTrackBHoldoutArtifacts({
    ...args,
    profile: FIFTH_PROFILE
  });
}

export function validateTrackBFifthCorpus(corpus) {
  return validateTrackBCorpus(corpus, FIFTH_PROFILE);
}

export function buildTrackBRecoveryArtifacts(args) {
  return buildTrackBHoldoutArtifacts({
    ...args,
    profile: RECOVERY_PROFILE
  });
}

export function validateTrackBRecoveryCorpus(corpus) {
  return validateTrackBCorpus(corpus, RECOVERY_PROFILE);
}

function validateTrackBCorpus(corpus, profile) {
  assert.equal(corpus.schemaVersion, profile.corpusSchema);
  assert.equal(corpus.containsProductionContractText, false);
  assert.equal(corpus.groundTruthIncluded, false);
  assert.equal(corpus.packetCount, TRACK_B_HOLDOUT_COUNTS.total);
  assert.equal(
    corpus.eligiblePacketCount,
    TRACK_B_HOLDOUT_COUNTS.eligible
  );
  assert.equal(
    corpus.mediumEligibleCount,
    TRACK_B_HOLDOUT_COUNTS.medium
  );
  assert.equal(
    corpus.conflictedEligibleCount,
    TRACK_B_HOLDOUT_COUNTS.conflicted
  );
  assert.equal(
    corpus.zeroCallControlCount,
    TRACK_B_HOLDOUT_COUNTS.zeroCallControls
  );
  assert.equal(corpus.packets.length, TRACK_B_HOLDOUT_COUNTS.total);
  assert.equal(
    new Set(corpus.packets.map((packet) => packet.packetId)).size,
    TRACK_B_HOLDOUT_COUNTS.total
  );
  const reasons = [];
  for (const packet of corpus.packets) {
    assert.equal(packet.schemaVersion, PACKET_SCHEMA);
    assert.match(packet.packetId, /^EP-[a-f0-9]{64}$/);
    assert.match(packet.taskId, profile.taskPattern);
    assert.match(packet.executionId, profile.executionPattern);
    assert.ok(!packet.taskId.includes("admission"));
    assert.deepEqual(
      packet.requiredOutput,
      TRACK_B_HOLDOUT_OUTPUT_CONTRACT
    );
    assert.equal(packet.admission.reasonCodes.length, 1);
    assert.equal(
      packet.admission.modelCallAllowed,
      packet.admission.status === "ELIGIBLE"
    );
    assert.equal(
      packet.admission.modelCallAllowed,
      packet.admission.requiredBlockIds.length > 0
    );
    assert.equal(
      new Set(packet.admission.requiredBlockIds).size,
      packet.admission.requiredBlockIds.length
    );
    if (!packet.admission.modelCallAllowed) {
      reasons.push(packet.admission.reasonCodes[0]);
    }
  }
  assert.deepEqual(reasons.sort(), [
    "BUNDLE_INVALID",
    "DETERMINISTIC_HIGH_ZERO_CALL",
    "RELIABLE_ANCHOR_MISSING"
  ]);
  const seamCase = corpus.packets.find(
    (packet) => packet.sampleId === profile.seamCaseId
  );
  assert.deepEqual(
    seamCase.admission.requiredBlockIds,
    profile.seamRequiredBlockIds
  );
  assert.ok(
    seamCase.candidateOccurrences.some(
      (candidate) =>
        candidate.sourceAnchor.blockId === profile.seamExcludedBlockId
    )
  );
  assertNoForbiddenModelKeys(corpus);
  return corpus;
}

function validateSource(source, profile) {
  assert.equal(source.schemaVersion, profile.sourceSchema);
  assert.equal(
    source.source,
    "INDEPENDENT_DEIDENTIFIED_SYNTHETIC_RUNTIME_SIGNALS"
  );
  assert.equal(source.containsProductionContractText, false);
  assert.equal(source.groundTruthIncluded, false);
  assert.equal(source.admissionFieldsIncluded, false);
  assert.equal(source.caseCount, TRACK_B_HOLDOUT_COUNTS.total);
  assert.equal(source.cases.length, TRACK_B_HOLDOUT_COUNTS.total);
  assert.equal(
    new Set(source.cases.map((item) => item.caseId)).size,
    source.cases.length
  );
  for (const identityClass of ["MEDIUM", "CONFLICTED", "CONTROL"]) {
    const identity = source.identities[identityClass];
    assert.match(identity.taskId, profile.taskPattern);
    assert.match(identity.executionId, profile.executionPattern);
  }
  for (const item of source.cases) {
    assert.match(item.caseId, profile.casePattern);
    assert.equal(FAMILY_BY_POINT.get(item.reviewPointCode), item.family);
    assert.ok(source.identities[item.identityClass]);
    assert.ok(Array.isArray(item.occurrences));
    assert.ok(item.occurrences.length > 0);
    assert.ok(Number.isInteger(item.maxEvidenceChars));
    assert.ok(item.maxEvidenceChars > 0);
    for (const occurrence of item.occurrences) {
      assert.equal(typeof occurrence.candidateValue, "string");
      assert.ok(occurrence.candidateValue.length > 0);
      assert.equal(typeof occurrence.blockId, "string");
      assert.ok(occurrence.blockId.length > 0);
      assert.equal(typeof occurrence.evidenceText, "string");
      assert.ok(occurrence.evidenceText.length > 0);
    }
  }
  assertNoForbiddenSourceKeys(source);
}

function validateProposalsShape(proposals, profile) {
  assert.equal(proposals.schemaVersion, profile.proposalSchema);
  assert.equal(
    proposals.status,
    "CODEX_PROPOSAL_NOT_HUMAN_GROUND_TRUTH"
  );
  assert.equal(proposals.draftedBy, "CODEX");
  assert.equal(proposals.humanGroundTruthEstablished, false);
  assert.equal(proposals.entryCount, TRACK_B_HOLDOUT_COUNTS.total);
  assert.equal(proposals.entries.length, TRACK_B_HOLDOUT_COUNTS.total);
  assert.equal(
    new Set(proposals.entries.map((entry) => entry.caseId)).size,
    proposals.entries.length
  );
}

function validateProposalAgainstPacket(proposal, packet) {
  assert.equal(typeof proposal.proposedRationale, "string");
  assert.ok(proposal.proposedRationale.trim());
  const expected = proposal.proposedExpected;
  assert.deepEqual(Object.keys(expected).sort(), [
    "abstain",
    "abstentionReason",
    "selectedAnchorBlockIds",
    "selectedOccurrenceIds",
    "suggestedRole"
  ]);
  assert.equal(typeof expected.abstain, "boolean");
  assert.ok(Array.isArray(expected.selectedOccurrenceIds));
  assert.ok(Array.isArray(expected.selectedAnchorBlockIds));
  const occurrenceById = new Map(
    packet.candidateOccurrences.map((candidate) => [
      candidate.occurrenceId,
      candidate
    ])
  );
  assert.ok(
    expected.selectedOccurrenceIds.every((occurrenceId) =>
      occurrenceById.has(occurrenceId)
    )
  );
  const anchors = [
    ...new Set(
      expected.selectedOccurrenceIds.map(
        (occurrenceId) =>
          occurrenceById.get(occurrenceId).sourceAnchor.blockId
      )
    )
  ];
  assert.deepEqual(expected.selectedAnchorBlockIds, anchors);
  if (packet.admission.modelCallAllowed) {
    assert.equal(expected.abstain, false);
    assert.equal(expected.suggestedRole, packet.candidateRole);
    assert.ok(expected.selectedOccurrenceIds.length > 0);
    assert.equal(expected.abstentionReason, null);
  } else {
    assert.equal(expected.abstain, true);
    assert.equal(expected.suggestedRole, null);
    assert.deepEqual(expected.selectedOccurrenceIds, []);
    assert.deepEqual(expected.selectedAnchorBlockIds, []);
    assert.equal(
      expected.abstentionReason,
      packet.admission.reasonCodes[0]
    );
  }
}

function packetFor(source, item) {
  const identity = source.identities[item.identityClass];
  const candidates = item.occurrences.map((occurrence, index) => ({
    occurrenceId: `OCC-${String(index + 1).padStart(3, "0")}`,
    candidateValue: occurrence.candidateValue,
    evidenceText: occurrence.evidenceText,
    sourceAnchor: {
      blockId: occurrence.blockId,
      locationLevel: occurrence.locationLevel,
      previewElementRef: occurrence.previewElementRef,
      sectionPath: occurrence.sectionPath,
      regionType: occurrence.regionType,
      contextType: occurrence.contextType,
      reliable: reliableAnchor(occurrence)
    }
  }));
  const usedEvidenceChars = candidates.reduce(
    (total, candidate) =>
      total + [...candidate.evidenceText].length,
    0
  );
  const budgetComplete =
    usedEvidenceChars <= item.maxEvidenceChars &&
    item.slotCoverage.coverageStatus !== "BUDGET_TRUNCATED";
  const bundleValid =
    item.evidenceStatus !== "SYSTEM_FAILURE" &&
    item.slotCoverage !== null &&
    candidates.every((candidate) => candidate.sourceAnchor.reliable);
  const admission = evaluateAdmission(
    item,
    candidates,
    bundleValid,
    budgetComplete
  );
  const canonical = [
    PACKET_SCHEMA,
    identity.taskId,
    identity.executionId,
    item.caseId,
    source.ruleSetVersion,
    item.reviewPointCode,
    item.candidateRole,
    ...candidates.flatMap((candidate) => [
      candidate.candidateValue,
      candidate.sourceAnchor.blockId,
      candidate.sourceAnchor.previewElementRef ?? ""
    ])
  ].join("|");
  return {
    schemaVersion: PACKET_SCHEMA,
    packetId: `EP-${sha256(canonical)}`,
    taskId: identity.taskId,
    executionId: identity.executionId,
    sampleId: item.caseId,
    ruleSetVersion: source.ruleSetVersion,
    family: item.family,
    reviewPointCode: item.reviewPointCode,
    candidateRole: item.candidateRole,
    candidateOccurrences: candidates,
    coverageSignals: [item.slotCoverage],
    budget: {
      maxEvidenceChars: item.maxEvidenceChars,
      usedEvidenceChars,
      complete: budgetComplete,
      truncated: !budgetComplete
    },
    admission,
    requiredOutput: TRACK_B_HOLDOUT_OUTPUT_CONTRACT
  };
}

function evaluateAdmission(item, candidates, bundleValid, budgetComplete) {
  if (!bundleValid) return ineligible("BUNDLE_INVALID");
  if (!budgetComplete) return ineligible("BUDGET_INCOMPLETE");
  if (!item.slotCoverage.required && !item.slotCoverage.critical) {
    return ineligible("REQUIRED_SLOT_UNAVAILABLE");
  }
  if (candidates.length === 0 || item.confidence === "UNKNOWN") {
    return ineligible("NO_CANDIDATE");
  }
  if (item.assistPolicy.modelAssistMode === "NONE") {
    return ineligible(
      item.confidence === "HIGH"
        ? "DETERMINISTIC_HIGH_ZERO_CALL"
        : "MODEL_ASSIST_POLICY_NONE"
    );
  }
  if (item.confidence === "MEDIUM") {
    const anchored = candidates.filter(hasReliableAnchor);
    return anchored.length === 0
      ? ineligible("RELIABLE_ANCHOR_MISSING")
      : eligible("ELIGIBLE_MEDIUM_AMBIGUITY", anchored);
  }
  if (item.confidence === "CONFLICTED") {
    const distinctValues = new Set(
      candidates.map((candidate) => candidate.candidateValue)
    ).size;
    const comparable = candidates.filter(
      (candidate) =>
        hasReliableAnchor(candidate) &&
        localRelation(candidate) !== "NONE" &&
        distinctValues > 1
    );
    if (
      comparable.length < 2 ||
      new Set(comparable.map((candidate) => candidate.candidateValue))
          .size < 2
    ) {
      return ineligible("CONFLICT_NOT_LOCALLY_COMPARABLE");
    }
    return eligible("ELIGIBLE_CONFLICT_LOCAL_CONTEXT", comparable);
  }
  throw new Error(`Unsupported holdout confidence: ${item.confidence}`);
}

function eligible(reason, candidates) {
  return {
    modelCallAllowed: true,
    status: "ELIGIBLE",
    reasonCodes: [reason],
    requiredBlockIds: [
      ...new Set(
        candidates.map((candidate) => candidate.sourceAnchor.blockId)
      )
    ]
  };
}

function ineligible(reason) {
  return {
    modelCallAllowed: false,
    status: "ZERO_CALL_REQUIRED",
    reasonCodes: [reason],
    requiredBlockIds: []
  };
}

function reliableAnchor(occurrence) {
  if (!occurrence.blockId?.trim()) return false;
  if (occurrence.locationLevel === "TABLE_CELL") {
    return /^table:[^/]+\/row:[0-9]+\/cell:[0-9]+$/.test(
      occurrence.previewElementRef ?? ""
    );
  }
  return (
    occurrence.locationLevel === "BLOCK_LEVEL" &&
    (occurrence.previewElementRef === null ||
      occurrence.previewElementRef === "" ||
      occurrence.previewElementRef === `block:${occurrence.blockId}`)
  );
}

function hasReliableAnchor(candidate) {
  return (
    candidate.sourceAnchor.reliable &&
    !FORBIDDEN_CONTEXTS.has(candidate.sourceAnchor.contextType ?? "")
  );
}

function localRelation(candidate) {
  if (candidate.sourceAnchor.locationLevel === "TABLE_CELL") {
    return "SAME_TABLE_ROW";
  }
  return candidate.evidenceText.includes(candidate.candidateValue)
    ? "SAME_SENTENCE"
    : "NONE";
}

function renderReviewMarkdown(draft, corpus, profile) {
  const packetByCaseId = new Map(
    corpus.packets.map((packet) => [packet.sampleId, packet])
  );
  const rows = draft.entries
    .map((entry) => {
      const expected = entry.proposedExpected;
      const selected =
        expected.selectedOccurrenceIds.join(", ") || "无（拒答）";
      return (
        `| ${entry.caseId} | ${entry.confidenceClass} | ` +
        `${selected} | ${markdownCell(entry.proposedRationale)} | 待确认 |`
      );
    })
    .join("\n");
  const details = draft.entries
    .map((entry) => {
      const packet = packetByCaseId.get(entry.caseId);
      const candidates = packet.candidateOccurrences
        .map(
          (candidate) =>
            `| ${candidate.occurrenceId} | ` +
            `${markdownCell(candidate.candidateValue)} | ` +
            `${markdownCell(candidate.evidenceText)} | ` +
            `${markdownCell(candidate.sourceAnchor.blockId)}` +
            `${candidate.sourceAnchor.previewElementRef
              ? ` / ${markdownCell(candidate.sourceAnchor.previewElementRef)}`
              : ""} |`
        )
        .join("\n");
      const expected = entry.proposedExpected;
      return `### ${entry.caseId}

- 类别：\`${entry.confidenceClass}\`
- 新 packetId：\`${packet.packetId}\`
- 建议角色：\`${expected.suggestedRole ?? "无（拒答）"}\`
- 建议 occurrence：\`${expected.selectedOccurrenceIds.join(", ") || "无"}\`
- 建议 anchor：\`${expected.selectedAnchorBlockIds.join(", ") || "无"}\`
- 建议 abstention：\`${expected.abstain}\`${expected.abstentionReason
        ? `（\`${expected.abstentionReason}\`）`
        : ""}
- 建议理由：${entry.proposedRationale}

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
${candidates}
`;
    })
    .join("\n");
  return `# ${profile.reviewTitle}

状态：**待人工确认；当前不是 ground truth**

语料 SHA-256：\`${draft.corpusSha256}\`

确认范围：12 个新建、合成、最小 EvidencePacket；9 个为可调用
MEDIUM/CONFLICTED 歧义，3 个分别验证 deterministic HIGH、invalid bundle 与
无可靠 anchor 的 zero-call。

请逐项核对候选原文、角色、选择和 abstention。只有项目负责人明确确认后，
Codex 才会封存人工 ground truth。确认前不得创建 model input、不得派发 Codex
blind evaluator，也不得发起任何模型调用或 DeepSeek 网络调用。

| Case | 类别 | 建议选择 | 建议理由 | 人工决定 |
|---|---|---|---|---|
${rows}

## 候选原文与 SourceAnchor 逐项核对

${details}`;
}

function markdownCell(value) {
  return String(value ?? "—")
    .replaceAll("\r", " ")
    .replaceAll("\n", " ")
    .replaceAll("|", "\\|");
}

function assertNoForbiddenSourceKeys(value) {
  walkKeys(value, (key) => {
    assert.ok(
      !new Set([
        "admission",
        "packetId",
        "reasonCodes",
        "requiredBlockIds",
        "modelCallAllowed",
        "proposedExpected",
        "humanDecision",
        "expected",
        "actual",
        "finding",
        "verdict"
      ]).has(key),
      `Source signals contain forbidden derived key: ${key}`
    );
  });
}

function assertNoForbiddenModelKeys(value) {
  walkKeys(value, (key) => {
    assert.ok(
      !new Set([
        "proposedExpected",
        "humanDecision",
        "expected",
        "actual",
        "finding",
        "verdict"
      ]).has(key),
      `Model corpus contains forbidden key: ${key}`
    );
  });
}

function walkKeys(value, callback) {
  if (Array.isArray(value)) {
    value.forEach((entry) => walkKeys(entry, callback));
    return;
  }
  if (!value || typeof value !== "object") return;
  for (const [key, child] of Object.entries(value)) {
    callback(key);
    walkKeys(child, callback);
  }
}

function canonicalIso(value, field) {
  assert.equal(typeof value, "string", `${field} must be a string`);
  assert.match(
    value,
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/,
    `${field} must be canonical ISO-8601`
  );
  const parsed = Date.parse(value);
  assert.ok(Number.isFinite(parsed), `${field} is invalid`);
  assert.equal(new Date(parsed).toISOString(), value);
  return value;
}
