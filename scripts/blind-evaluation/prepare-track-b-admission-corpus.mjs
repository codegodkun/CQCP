import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const repoRoot = resolve(process.argv[2] ?? ".");
const generatedAt = process.argv[3];
if (!generatedAt || Number.isNaN(Date.parse(generatedAt))) {
  throw new Error("A valid ISO generation timestamp is required");
}

const schemaVersion = "task036-runtime-evidence-packet-v1";
const ruleSetVersion = "v20260729.admission.1";
const outputRoot = resolve(
  repoRoot,
  "outputs/task-eval-002/track-b-admission-corpus-v2"
);
const sha256 = (value) =>
  createHash("sha256").update(value).digest("hex");

const anchor = (blockId, options = {}) => ({
  blockId,
  locationLevel: options.previewElementRef ? "TABLE_CELL" : "BLOCK_LEVEL",
  previewElementRef: options.previewElementRef ?? null,
  sectionPath: [options.section ?? "脱敏合同片段"],
  regionType: "BODY",
  contextType: null,
  reliable: options.reliable ?? true
});
const occurrence = (occurrenceId, candidateValue, evidenceText, sourceAnchor) => ({
  occurrenceId,
  candidateValue,
  evidenceText,
  sourceAnchor
});

const cases = [
  {
    caseId: "TB-MED-001",
    family: "PARTY_FIELDS",
    reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
    candidateRole: "PARTY_A",
    confidence: "MEDIUM",
    occurrences: [
      occurrence("OCC-001", "林某", "甲方项目经理：林某。", anchor("tb-med-001-b01")),
      occurrence(
        "OCC-002",
        "青岚设备有限公司",
        "采购人（甲方）：青岚设备有限公司。",
        anchor("tb-med-001-b02")
      )
    ],
    selectedOccurrenceIds: ["OCC-002"],
    rationale: "甲方角色应归属合同主体公司，而不是甲方项目经理个人。"
  },
  {
    caseId: "TB-MED-002",
    family: "PARTY_FIELDS",
    reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
    candidateRole: "PARTY_B",
    confidence: "MEDIUM",
    occurrences: [
      occurrence(
        "OCC-001",
        "远舟技术有限公司",
        "服务方（以下简称乙方）：远舟技术有限公司。",
        anchor("tb-med-002-b01")
      ),
      occurrence(
        "OCC-002",
        "远舟财务中心",
        "收款账户名称：远舟财务中心。",
        anchor("tb-med-002-b02")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "乙方角色应取明确的服务方合同主体，不取收款账户名称。"
  },
  {
    caseId: "TB-MED-003",
    family: "AMOUNT_TAX",
    reviewPointCode: "CONTRACT_TOTAL_AMOUNT_CONSISTENCY",
    candidateRole: "CONTRACT_TOTAL_AMOUNT",
    confidence: "MEDIUM",
    occurrences: [
      occurrence(
        "OCC-001",
        "708500",
        "本合同含税总价为人民币708500元。",
        anchor("tb-med-003-b01")
      ),
      occurrence(
        "OCC-002",
        "750000",
        "项目预算控制上限为人民币750000元。",
        anchor("tb-med-003-b02")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "合同总金额应取明确的含税总价，不取预算控制上限。"
  },
  {
    caseId: "TB-MED-004",
    family: "AMOUNT_TAX",
    reviewPointCode: "TAX_AMOUNT_FORMULA_CONSISTENCY",
    candidateRole: "TAX_AMOUNT",
    confidence: "MEDIUM",
    occurrences: [
      occurrence(
        "OCC-001",
        "58500",
        "其中增值税税额为58500元。",
        anchor("tb-med-004-b01")
      ),
      occurrence(
        "OCC-002",
        "708500",
        "价税合计708500元。",
        anchor("tb-med-004-b02")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "税额角色应取明确标注的增值税税额，不取价税合计。"
  },
  {
    caseId: "TB-MED-005",
    family: "PAYMENT_TERMS",
    reviewPointCode: "PREPAYMENT_RATIO_CONSISTENCY",
    candidateRole: "PREPAYMENT_RATIO",
    confidence: "MEDIUM",
    occurrences: [
      occurrence(
        "OCC-001",
        "10",
        "合同生效后支付合同价的10%作为预付款。",
        anchor("tb-med-005-b01")
      ),
      occurrence(
        "OCC-002",
        "5",
        "履约保证金为合同价的5%。",
        anchor("tb-med-005-b02")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "预付款比例应取10%，不得把履约保证金比例归入该角色。"
  },
  {
    caseId: "TB-MED-006",
    family: "PAYMENT_TERMS",
    reviewPointCode: "PROGRESS_PAYMENT_RATIO_CONSISTENCY",
    candidateRole: "PROGRESS_PAYMENT_RATIO",
    confidence: "MEDIUM",
    occurrences: [
      occurrence(
        "OCC-001",
        "75",
        "月度进度款按已审核产值的75%支付。",
        anchor("tb-med-006-b01")
      ),
      occurrence(
        "OCC-002",
        "100",
        "乙方应开具当期产值100%的增值税发票。",
        anchor("tb-med-006-b02")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "进度付款比例是75%，发票开具比例100%不是付款比例。"
  },
  {
    caseId: "TB-MED-007",
    family: "PAYMENT_TERMS",
    reviewPointCode: "COMPLETION_PAYMENT_RATIO_CONSISTENCY",
    candidateRole: "COMPLETION_PAYMENT_RATIO",
    confidence: "MEDIUM",
    occurrences: [
      occurrence(
        "OCC-001",
        "85",
        "竣工验收合格后累计支付至合同价的85%。",
        anchor("tb-med-007-b01")
      ),
      occurrence(
        "OCC-002",
        "75",
        "施工期间进度款累计支付至已审核产值的75%。",
        anchor("tb-med-007-b02")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "竣工款比例应取竣工验收后的85%，不取施工期进度款。"
  },
  {
    caseId: "TB-MED-008",
    family: "PAYMENT_TERMS",
    reviewPointCode: "WARRANTY_RETENTION_RATIO_CONSISTENCY",
    candidateRole: "WARRANTY_RETENTION_RATIO",
    confidence: "MEDIUM",
    occurrences: [
      occurrence(
        "OCC-001",
        "3",
        "结算价的3%作为质量保证金。",
        anchor("tb-med-008-b01")
      ),
      occurrence(
        "OCC-002",
        "5",
        "发生重大质量违约时按合同价的5%承担违约金。",
        anchor("tb-med-008-b02")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "质保金比例是3%，质量违约金比例不是质保金。"
  },
  {
    caseId: "TB-CON-001",
    family: "PARTY_FIELDS",
    reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
    candidateRole: "PARTY_A",
    confidence: "CONFLICTED",
    occurrences: [
      occurrence(
        "OCC-001",
        "青岚设备有限公司",
        "采购人（甲方）：青岚设备有限公司；项目管理单位：河谷咨询有限公司。",
        anchor("tb-con-001-b01")
      ),
      occurrence(
        "OCC-002",
        "河谷咨询有限公司",
        "采购人（甲方）：青岚设备有限公司；项目管理单位：河谷咨询有限公司。",
        anchor("tb-con-001-b01")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "同句冲突候选中，明确带甲方标签的是青岚设备有限公司。"
  },
  {
    caseId: "TB-CON-002",
    family: "PARTY_FIELDS",
    reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
    candidateRole: "PARTY_B",
    confidence: "CONFLICTED",
    occurrences: [
      occurrence(
        "OCC-001",
        "青岚设备有限公司",
        "甲方：青岚设备有限公司；乙方：远舟技术有限公司。",
        anchor("tb-con-002-b01")
      ),
      occurrence(
        "OCC-002",
        "远舟技术有限公司",
        "甲方：青岚设备有限公司；乙方：远舟技术有限公司。",
        anchor("tb-con-002-b01")
      )
    ],
    selectedOccurrenceIds: ["OCC-002"],
    rationale: "同句候选中，乙方标签直接指向远舟技术有限公司。"
  },
  {
    caseId: "TB-CON-003",
    family: "AMOUNT_TAX",
    reviewPointCode: "CONTRACT_TOTAL_AMOUNT_CONSISTENCY",
    candidateRole: "CONTRACT_TOTAL_AMOUNT",
    confidence: "CONFLICTED",
    occurrences: [
      occurrence(
        "OCC-001",
        "650000",
        "不含税价650000元，税额58500元，含税总价708500元。",
        anchor("tb-con-003-b01")
      ),
      occurrence(
        "OCC-002",
        "58500",
        "不含税价650000元，税额58500元，含税总价708500元。",
        anchor("tb-con-003-b01")
      ),
      occurrence(
        "OCC-003",
        "708500",
        "不含税价650000元，税额58500元，含税总价708500元。",
        anchor("tb-con-003-b01")
      )
    ],
    selectedOccurrenceIds: ["OCC-003"],
    rationale: "合同总金额角色应选择明确标注的含税总价708500元。"
  },
  {
    caseId: "TB-CON-004",
    family: "AMOUNT_TAX",
    reviewPointCode: "TAX_AMOUNT_FORMULA_CONSISTENCY",
    candidateRole: "TAX_AMOUNT",
    confidence: "CONFLICTED",
    occurrences: [
      occurrence(
        "OCC-001",
        "650000",
        "不含税价650000元，税额58500元，价税合计708500元。",
        anchor("tb-con-004-b01")
      ),
      occurrence(
        "OCC-002",
        "58500",
        "不含税价650000元，税额58500元，价税合计708500元。",
        anchor("tb-con-004-b01")
      ),
      occurrence(
        "OCC-003",
        "708500",
        "不含税价650000元，税额58500元，价税合计708500元。",
        anchor("tb-con-004-b01")
      )
    ],
    selectedOccurrenceIds: ["OCC-002"],
    rationale: "税额角色应选择明确标注的58500元。"
  },
  {
    caseId: "TB-CON-005",
    family: "PAYMENT_TERMS",
    reviewPointCode: "PROGRESS_PAYMENT_RATIO_CONSISTENCY",
    candidateRole: "PROGRESS_PAYMENT_RATIO",
    confidence: "CONFLICTED",
    occurrences: [
      occurrence(
        "OCC-001",
        "75",
        "月度进度款支付75%；竣工验收后累计支付至85%。",
        anchor("tb-con-005-b01")
      ),
      occurrence(
        "OCC-002",
        "85",
        "月度进度款支付75%；竣工验收后累计支付至85%。",
        anchor("tb-con-005-b01")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "进度付款角色应取月度进度款75%，不取竣工款85%。"
  },
  {
    caseId: "TB-CON-006",
    family: "PAYMENT_TERMS",
    reviewPointCode: "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY",
    candidateRole: "SETTLEMENT_PAYMENT_RATIO",
    confidence: "CONFLICTED",
    occurrences: [
      occurrence(
        "OCC-001",
        "97",
        "结算审核完成后累计支付至97%，剩余3%作为质保金。",
        anchor("tb-con-006-b01")
      ),
      occurrence(
        "OCC-002",
        "3",
        "结算审核完成后累计支付至97%，剩余3%作为质保金。",
        anchor("tb-con-006-b01")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "结算付款比例是累计支付至97%，3%属于质保金。"
  },
  {
    caseId: "TB-CON-007",
    family: "PAYMENT_TERMS",
    reviewPointCode: "PREPAYMENT_RATIO_CONSISTENCY",
    candidateRole: "PREPAYMENT_RATIO",
    confidence: "CONFLICTED",
    occurrences: [
      occurrence(
        "OCC-001",
        "10",
        "预付款为合同价的10%，履约保证金为合同价的5%。",
        anchor("tb-con-007-b01")
      ),
      occurrence(
        "OCC-002",
        "5",
        "预付款为合同价的10%，履约保证金为合同价的5%。",
        anchor("tb-con-007-b01")
      )
    ],
    selectedOccurrenceIds: ["OCC-001"],
    rationale: "预付款标签直接对应10%，5%属于履约保证金。"
  },
  {
    caseId: "TB-CTL-HIGH-001",
    family: "AMOUNT_TAX",
    reviewPointCode: "CONTRACT_TOTAL_AMOUNT_CONSISTENCY",
    candidateRole: "CONTRACT_TOTAL_AMOUNT",
    confidence: "HIGH",
    occurrences: [
      occurrence(
        "OCC-001",
        "708500",
        "合同含税总价：708500元。",
        anchor("tb-ctl-high-001-b01")
      )
    ],
    admissionReason: "DETERMINISTIC_HIGH_ZERO_CALL",
    selectedOccurrenceIds: [],
    rationale: "deterministic HIGH 必须零调用，模型不得覆盖。"
  },
  {
    caseId: "TB-CTL-NONE-001",
    family: "PAYMENT_TERMS",
    reviewPointCode: "WARRANTY_RETENTION_RATIO_CONSISTENCY",
    candidateRole: "WARRANTY_RETENTION_RATIO",
    confidence: "UNKNOWN",
    occurrences: [],
    admissionReason: "NO_CANDIDATE",
    selectedOccurrenceIds: [],
    rationale: "没有候选时必须零调用并拒答。"
  },
  {
    caseId: "TB-CTL-BUDGET-001",
    family: "PAYMENT_TERMS",
    reviewPointCode: "COMPLETION_PAYMENT_RATIO_CONSISTENCY",
    candidateRole: "COMPLETION_PAYMENT_RATIO",
    confidence: "MEDIUM",
    budgetComplete: false,
    occurrences: [
      occurrence(
        "OCC-001",
        "85",
        "竣工验收合格后累计支付至合同价的85%，但本 packet 被标记为预算截断。",
        anchor("tb-ctl-budget-001-b01")
      )
    ],
    admissionReason: "BUDGET_INCOMPLETE",
    selectedOccurrenceIds: [],
    rationale: "预算不完整时即使候选和 anchor 存在，也必须零调用。"
  }
];

function packetFor(item) {
  const sampleId = item.caseId;
  const identityClass = item.admissionReason
    ? "control"
    : item.confidence === "CONFLICTED"
      ? "conflicted"
      : "medium";
  const taskId =
    `task-eval-002-track-b-admission-${identityClass}`;
  const executionId =
    `execution-track-b-admission-${identityClass}-v2`;
  const canonical = [
    schemaVersion,
    taskId,
    executionId,
    sampleId,
    ruleSetVersion,
    item.reviewPointCode,
    item.candidateRole,
    ...item.occurrences.flatMap((candidate) => [
      candidate.candidateValue,
      candidate.sourceAnchor.blockId,
      candidate.sourceAnchor.previewElementRef ?? ""
    ])
  ].join("|");
  const eligible = !item.admissionReason;
  const reasonCode =
    item.admissionReason ??
    (item.confidence === "CONFLICTED"
      ? "ELIGIBLE_CONFLICT_LOCAL_CONTEXT"
      : "ELIGIBLE_MEDIUM_AMBIGUITY");
  const usedEvidenceChars = item.occurrences.reduce(
    (total, candidate) => total + candidate.evidenceText.length,
    0
  );
  const budgetIncomplete = item.budgetComplete === false;
  const slotKey = item.candidateRole.toLowerCase();
  const coverageStatus =
    item.confidence === "CONFLICTED"
      ? "AMBIGUOUS"
      : item.confidence === "UNKNOWN"
        ? "MISSING"
        : item.confidence === "HIGH"
          ? "SATISFIED"
          : "LOW_CONFIDENCE";
  return {
    schemaVersion,
    packetId: `EP-${sha256(canonical)}`,
    taskId,
    executionId,
    sampleId,
    ruleSetVersion,
    family: item.family,
    reviewPointCode: item.reviewPointCode,
    candidateRole: item.candidateRole,
    candidateOccurrences: item.occurrences,
    coverageSignals: [
      {
        slotKey,
        required: true,
        critical: true,
        coverageStatus,
        diagnosticCode:
          item.confidence === "CONFLICTED"
            ? "SYS_ROLE_CONFLICT"
            : item.confidence === "UNKNOWN"
              ? "SYS_INDEX_INCOMPLETE"
              : item.confidence === "HIGH"
                ? null
                : "SYS_EVIDENCE_MEDIUM_CONFIDENCE",
        reliableAnchor:
          item.occurrences.length > 0 &&
          item.occurrences.every((candidate) => candidate.sourceAnchor.reliable)
      }
    ],
    budget: {
      maxEvidenceChars: budgetIncomplete ? 20 : 4096,
      usedEvidenceChars,
      complete: !budgetIncomplete,
      truncated: budgetIncomplete
    },
    admission: {
      modelCallAllowed: eligible,
      status: eligible ? "ELIGIBLE" : "ZERO_CALL_REQUIRED",
      reasonCodes: [reasonCode]
    },
    requiredOutput: {
      contract: "ROLE_CANDIDATE_ANCHOR_ABSTENTION_V1",
      fields: [
        "suggestedRole",
        "selectedOccurrenceIds",
        "selectedAnchorBlockIds",
        "abstain",
        "abstentionReason"
      ],
      instruction:
        "Only assess supplied role/candidate/anchor evidence. Do not produce final business adjudication."
    }
  };
}

const packets = cases.map(packetFor);
const valueType = (reviewPointCode) => {
  switch (reviewPointCode) {
    case "PARTY_A_NAME_CONSISTENCY":
    case "PARTY_B_NAME_CONSISTENCY":
      return "TEXT";
    case "CONTRACT_TOTAL_AMOUNT_CONSISTENCY":
    case "TAX_AMOUNT_FORMULA_CONSISTENCY":
      return "CNY_DECIMAL";
    default:
      return "PERCENTAGE_POINT";
  }
};
const eligibilitySourceSignals = {
  schemaVersion: "task-eval-002-track-b-eligibility-source-signals-v1",
  source: "INDEPENDENT_PRE_ADMISSION_RUNTIME_SIGNALS",
  admissionFieldsIncluded: false,
  inputCount: cases.length,
  inputs: cases.map((item, index) => {
    const packet = packets[index];
    const deterministicHigh = item.confidence === "HIGH";
    const distinctValues = new Set(
      item.occurrences.map((candidate) => candidate.candidateValue)
    ).size;
    return {
      sampleId: item.caseId,
      packetId: packet.packetId,
      confidenceLevel: item.confidence,
      slotPolicy: {
        required: true,
        critical: true
      },
      resolverPolicy: deterministicHigh
        ? "DETERMINISTIC_ONLY"
        : "GEMMA_IF_AMBIGUOUS",
      modelAssistMode: deterministicHigh
        ? "NONE"
        : "AMBIGUITY_RESOLUTION",
      executionStrategy: deterministicHigh
        ? "DETERMINISTIC"
        : "LLM_EXTRACT_THEN_RULE",
      bundleValid:
        item.occurrences.length === 0 ||
        item.occurrences.every(
          (candidate) => candidate.sourceAnchor.reliable === true
        ),
      budgetComplete: item.budgetComplete !== false,
      candidateSignals: item.occurrences.map((candidate) => ({
        occurrenceId: candidate.occurrenceId,
        valueType: valueType(item.reviewPointCode),
        localContextRelation: candidate.sourceAnchor.previewElementRef
          ? "SAME_TABLE_ROW"
          : candidate.evidenceText.includes(candidate.candidateValue)
            ? "SAME_SENTENCE"
            : "NONE",
        conflictDimension:
          distinctValues > 1 ? "VALUE" : "NONE",
        roleLabelSignal: true,
        valueFormatSignal: true,
        strongExcluded: !candidate.sourceAnchor.reliable
      }))
    };
  })
};
const corpus = {
  schemaVersion: "task-eval-002-track-b-admission-corpus-v1",
  status: "DRAFT_PENDING_HUMAN_GROUND_TRUTH_CONFIRMATION",
  generatedAt: new Date(generatedAt).toISOString(),
  source: "INDEPENDENT_DEIDENTIFIED_SYNTHETIC_MINIMAL_EVIDENCE_PACKETS",
  containsProductionContractText: false,
  groundTruthIncluded: false,
  packetCount: packets.length,
  eligiblePacketCount: packets.filter(
    (packet) => packet.admission.modelCallAllowed
  ).length,
  mediumEligibleCount: cases.filter(
    (item) => item.confidence === "MEDIUM" && !item.admissionReason
  ).length,
  conflictedEligibleCount: cases.filter(
    (item) => item.confidence === "CONFLICTED" && !item.admissionReason
  ).length,
  zeroCallControlCount: cases.filter((item) => item.admissionReason).length,
  packets
};
const corpusBytes = `${JSON.stringify(corpus, null, 2)}\n`;
const corpusSha256 = sha256(corpusBytes);

const draft = {
  schemaVersion: "task-eval-002-track-b-human-review-draft-v1",
  status: "PENDING_HUMAN_CONFIRMATION_NOT_GROUND_TRUTH",
  draftedBy: "CODEX",
  humanGroundTruthEstablished: false,
  corpusPath:
    "outputs/task-eval-002/track-b-admission-corpus-v2/corpus.json",
  corpusSha256,
  confirmationInstruction:
    "人工逐项确认 proposedExpected；确认前本文件不得作为 ground truth、不得提供给盲评模型。",
  entries: cases.map((item, index) => {
    const packet = packets[index];
    const abstain = !packet.admission.modelCallAllowed;
    const selectedAnchorBlockIds = [
      ...new Set(
        packet.candidateOccurrences
          .filter((candidate) =>
            item.selectedOccurrenceIds.includes(candidate.occurrenceId)
          )
          .map((candidate) => candidate.sourceAnchor.blockId)
      )
    ];
    return {
      caseId: item.caseId,
      packetId: packet.packetId,
      confidenceClass: item.confidence,
      humanDecision: null,
      proposedRationale: item.rationale,
      proposedExpected: {
        suggestedRole: abstain ? null : item.candidateRole,
        selectedOccurrenceIds: item.selectedOccurrenceIds,
        selectedAnchorBlockIds,
        abstain,
        abstentionReason: abstain
          ? packet.admission.reasonCodes[0]
          : null
      }
    };
  })
};
const reviewRows = draft.entries
  .map((entry) => {
    const expected = entry.proposedExpected;
    const selection =
      expected.selectedOccurrenceIds.length > 0
        ? expected.selectedOccurrenceIds.join(", ")
        : "无（拒答）";
    return (
      `| ${entry.caseId} | ${entry.confidenceClass} | ` +
      `${selection} | ${entry.proposedRationale} | 待确认 |`
    );
  })
  .join("\n");
const markdownCell = (value) =>
  String(value ?? "—")
    .replaceAll("\r", " ")
    .replaceAll("\n", " ")
    .replaceAll("|", "\\|");
const reviewDetails = draft.entries
  .map((entry, index) => {
    const packet = packets[index];
    const candidates =
      packet.candidateOccurrences.length === 0
        ? "无候选（控制样本）。"
        : `| occurrenceId | candidateValue | evidenceText | SourceAnchor |\n` +
          `|---|---|---|---|\n` +
          packet.candidateOccurrences
            .map(
              (candidate) =>
                `| ${markdownCell(candidate.occurrenceId)} | ` +
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
- 待确认角色：\`${expected.suggestedRole ?? "无（拒答）"}\`
- 待确认 occurrence：\`${expected.selectedOccurrenceIds.join(", ") || "无"}\`
- 待确认 anchor：\`${expected.selectedAnchorBlockIds.join(", ") || "无"}\`
- 待确认 abstention：\`${expected.abstain}\`${expected.abstentionReason
      ? `（\`${expected.abstentionReason}\`）`
      : ""}
- 建议理由：${entry.proposedRationale}

${candidates}
`;
  })
  .join("\n");
const reviewMarkdown = `# TASK-EVAL-002 Track B 人工 ground truth 确认表

状态：**待人工确认；当前不是 ground truth**

语料 SHA-256：\`${corpusSha256}\`

确认范围：18 个独立、脱敏、最小 EvidencePacket；其中 15 个为可调用
MEDIUM/CONFLICTED 歧义，3 个为 HIGH/no-candidate/budget fail-closed 控制。

请逐项核对候选原文、角色与建议选择。只有项目负责人明确确认后，Codex 才会把
确认结果封存为 ground truth 并派发盲态 admission 评测。

| Case | 类别 | 建议选择 | 建议理由 | 人工决定 |
|---|---|---|---|---|
${reviewRows}

## 候选原文与 SourceAnchor 逐项核对

${reviewDetails}
`;

await mkdir(outputRoot, { recursive: true });
await writeFile(resolve(outputRoot, "corpus.json"), corpusBytes, "utf8");
await writeFile(
  resolve(outputRoot, "eligibility-source-signals.json"),
  `${JSON.stringify(eligibilitySourceSignals, null, 2)}\n`,
  "utf8"
);
await writeFile(
  resolve(outputRoot, "human-review-draft.json"),
  `${JSON.stringify(draft, null, 2)}\n`,
  "utf8"
);
await writeFile(
  resolve(outputRoot, "human-ground-truth-review.md"),
  reviewMarkdown,
  "utf8"
);
process.stdout.write(
  `${JSON.stringify({
    status: corpus.status,
    corpusSha256,
    packetCount: corpus.packetCount,
    eligiblePacketCount: corpus.eligiblePacketCount,
    mediumEligibleCount: corpus.mediumEligibleCount,
    conflictedEligibleCount: corpus.conflictedEligibleCount,
    zeroCallControlCount: corpus.zeroCallControlCount
  })}\n`
);
