import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  jsonBytes,
  sha256
} from "./track-b-holdout-contract.mjs";

const OUTPUT_ROOT =
  "apps/api-server/src/test/resources/track-b-successor-v1";

export function buildTrackBSuccessorSourceArtifacts() {
  const source = {
    schemaVersion:
      "task-eval-003-track-b-successor-source-signals-v1",
    source: "INDEPENDENT_DEIDENTIFIED_SYNTHETIC_RUNTIME_SIGNALS",
    containsProductionContractText: false,
    groundTruthIncluded: false,
    admissionFieldsIncluded: false,
    ruleSetVersion: "v20260802.successor.1",
    identities: {
      MEDIUM: {
        taskId: "task-eval-003-track-b-successor-medium",
        executionId: "execution-track-b-successor-medium-v1"
      },
      CONFLICTED: {
        taskId: "task-eval-003-track-b-successor-conflicted",
        executionId: "execution-track-b-successor-conflicted-v1"
      },
      CONTROL: {
        taskId: "task-eval-003-track-b-successor-control",
        executionId: "execution-track-b-successor-control-v1"
      }
    },
    caseCount: 12,
    cases: [
      mediumCase({
        caseId: "TBS-MED-001",
        family: "PARTY_FIELDS",
        reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
        candidateRole: "PARTY_A",
        slotKey: "party_a",
        occurrences: [
          blockOccurrence({
            candidateValue: "邵宁",
            blockId: "tbs-med-001-b01",
            evidenceText: "甲方经办联系人：邵宁，负责资料传递。",
            sectionPath: ["联络信息"]
          }),
          blockOccurrence({
            candidateValue: "晴川精密制造有限公司",
            blockId: "tbs-med-001-b02",
            evidenceText: "发包人（甲方）：晴川精密制造有限公司。",
            sectionPath: ["合同主体"]
          })
        ]
      }),
      mediumCase({
        caseId: "TBS-MED-002",
        family: "PARTY_FIELDS",
        reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
        candidateRole: "PARTY_B",
        slotKey: "party_b",
        occurrences: [
          blockOccurrence({
            candidateValue: "峦境机电工程有限公司",
            blockId: "tbs-med-002-b01",
            evidenceText: "承揽人（乙方）：峦境机电工程有限公司。",
            sectionPath: ["合同主体"]
          }),
          blockOccurrence({
            candidateValue: "峦境资金结算部",
            blockId: "tbs-med-002-b02",
            evidenceText: "收款户名：峦境资金结算部。",
            sectionPath: ["结算账户"]
          })
        ]
      }),
      mediumCase({
        caseId: "TBS-MED-003",
        family: "AMOUNT_TAX",
        reviewPointCode: "CONTRACT_TOTAL_AMOUNT_CONSISTENCY",
        candidateRole: "CONTRACT_TOTAL_AMOUNT",
        slotKey: "contract_total_amount",
        occurrences: [
          cellOccurrence({
            candidateValue: "538640",
            blockId: "tbs-med-003-table-01",
            evidenceText: "合同含税总金额（元）538640",
            previewElementRef: "table:tbs_med_003/row:1/cell:2",
            sectionPath: ["合同价款表"]
          }),
          cellOccurrence({
            candidateValue: "560000",
            blockId: "tbs-med-003-table-01",
            evidenceText: "项目预算控制额（元）560000",
            previewElementRef: "table:tbs_med_003/row:2/cell:2",
            sectionPath: ["合同价款表"]
          })
        ]
      }),
      mediumCase({
        caseId: "TBS-MED-004",
        family: "PAYMENT_TERMS",
        reviewPointCode: "PREPAYMENT_RATIO_CONSISTENCY",
        candidateRole: "PREPAYMENT_RATIO",
        slotKey: "prepayment_ratio",
        occurrences: [
          blockOccurrence({
            candidateValue: "14",
            blockId: "tbs-med-004-b01",
            evidenceText: "合同签署后支付合同价的14%作为预付款。",
            sectionPath: ["付款条件"]
          }),
          blockOccurrence({
            candidateValue: "7",
            blockId: "tbs-med-004-b02",
            evidenceText: "履约担保比例为合同价的7%。",
            sectionPath: ["履约担保"]
          })
        ]
      }),
      mediumCase({
        caseId: "TBS-MED-005",
        family: "PAYMENT_TERMS",
        reviewPointCode: "WARRANTY_RETENTION_RATIO_CONSISTENCY",
        candidateRole: "WARRANTY_RETENTION_RATIO",
        slotKey: "warranty_retention_ratio",
        occurrences: [
          blockOccurrence({
            candidateValue: "2",
            blockId: "tbs-med-005-b01",
            evidenceText: "审定结算价的2%作为质量保证金。",
            sectionPath: ["质量保证"]
          }),
          blockOccurrence({
            candidateValue: "9",
            blockId: "tbs-med-005-b02",
            evidenceText: "延期违约金累计上限为合同价的9%。",
            sectionPath: ["违约责任"]
          })
        ]
      }),
      conflictedCase({
        caseId: "TBS-CON-001",
        family: "PARTY_FIELDS",
        reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
        candidateRole: "PARTY_B",
        slotKey: "party_b",
        occurrences: [
          blockOccurrence({
            candidateValue: "栖岸监理咨询有限公司",
            blockId: "tbs-con-001-b01",
            evidenceText:
              "承包人（乙方）：青屿设备安装有限公司；监理协调单位：栖岸监理咨询有限公司。",
            sectionPath: ["合同主体"],
            confidence: "CONFLICTED"
          }),
          blockOccurrence({
            candidateValue: "青屿设备安装有限公司",
            blockId: "tbs-con-001-b01",
            evidenceText:
              "承包人（乙方）：青屿设备安装有限公司；监理协调单位：栖岸监理咨询有限公司。",
            sectionPath: ["合同主体"],
            confidence: "CONFLICTED"
          })
        ]
      }),
      conflictedCase({
        caseId: "TBS-CON-002",
        family: "AMOUNT_TAX",
        reviewPointCode: "TAX_AMOUNT_FORMULA_CONSISTENCY",
        candidateRole: "TAX_AMOUNT",
        slotKey: "tax_amount",
        occurrences: [
          cellOccurrence({
            candidateValue: "468000",
            blockId: "tbs-con-002-table-01",
            evidenceText: "不含税金额468000元，税额42120元，价税合计510120元。",
            previewElementRef: "table:tbs_con_002/row:1/cell:1",
            sectionPath: ["价税明细"],
            confidence: "CONFLICTED"
          }),
          cellOccurrence({
            candidateValue: "42120",
            blockId: "tbs-con-002-table-01",
            evidenceText: "不含税金额468000元，税额42120元，价税合计510120元。",
            previewElementRef: "table:tbs_con_002/row:1/cell:2",
            sectionPath: ["价税明细"],
            confidence: "CONFLICTED"
          }),
          cellOccurrence({
            candidateValue: "510120",
            blockId: "tbs-con-002-table-01",
            evidenceText: "不含税金额468000元，税额42120元，价税合计510120元。",
            previewElementRef: "table:tbs_con_002/row:1/cell:3",
            sectionPath: ["价税明细"],
            confidence: "CONFLICTED"
          })
        ]
      }),
      conflictedCase({
        caseId: "TBS-CON-003",
        family: "PAYMENT_TERMS",
        reviewPointCode: "PROGRESS_PAYMENT_RATIO_CONSISTENCY",
        candidateRole: "PROGRESS_PAYMENT_RATIO",
        slotKey: "progress_payment_ratio",
        occurrences: [
          blockOccurrence({
            candidateValue: "74",
            blockId: "tbs-con-003-b01",
            evidenceText: "月度进度款按核定完成产值的74%支付。",
            sectionPath: ["工程款支付"],
            confidence: "CONFLICTED"
          }),
          blockOccurrence({
            candidateValue: "86",
            blockId: "tbs-con-003-b02",
            evidenceText: "完工验收后累计支付至合同价的86%。",
            sectionPath: ["工程款支付"],
            confidence: "CONFLICTED"
          }),
          blockOccurrence({
            candidateValue: "101",
            blockId: "tbs-con-003-b03",
            evidenceText: "另附税务登记说明，不参与付款比例比较。",
            sectionPath: ["附件说明"],
            regionType: "APPENDIX",
            confidence: "CONFLICTED"
          })
        ]
      }),
      conflictedCase({
        caseId: "TBS-CON-004",
        family: "PAYMENT_TERMS",
        reviewPointCode: "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY",
        candidateRole: "SETTLEMENT_PAYMENT_RATIO",
        slotKey: "settlement_payment_ratio",
        occurrences: [
          blockOccurrence({
            candidateValue: "89",
            blockId: "tbs-con-004-b01",
            evidenceText: "结算审定后累计支付至结算价的89%，余款11%按保修约定处理。",
            sectionPath: ["结算支付"],
            confidence: "CONFLICTED"
          }),
          blockOccurrence({
            candidateValue: "11",
            blockId: "tbs-con-004-b01",
            evidenceText: "结算审定后累计支付至结算价的89%，余款11%按保修约定处理。",
            sectionPath: ["结算支付"],
            confidence: "CONFLICTED"
          })
        ]
      }),
      highControl(),
      invalidControl(),
      noAnchorControl()
    ]
  };

  const proposals = {
    schemaVersion:
      "task-eval-003-track-b-successor-proposed-decisions-v1",
    status: "CODEX_PROPOSAL_NOT_HUMAN_GROUND_TRUTH",
    draftedBy: "CODEX",
    humanGroundTruthEstablished: false,
    entryCount: 12,
    entries: [
      proposal("TBS-MED-001", "OCC-002", "tbs-med-001-b02", "PARTY_A", "发包人标签直接标识合同甲方，联系人不是合同主体。"),
      proposal("TBS-MED-002", "OCC-001", "tbs-med-002-b01", "PARTY_B", "承揽人（乙方）是合同主体，收款户名不是乙方主体字段。"),
      proposal("TBS-MED-003", "OCC-001", "tbs-med-003-table-01", "CONTRACT_TOTAL_AMOUNT", "合同含税总金额对应合同总价，预算控制额不属于合同总价。"),
      proposal("TBS-MED-004", "OCC-001", "tbs-med-004-b01", "PREPAYMENT_RATIO", "预付款条款中的14%对应目标角色，7%属于履约担保。"),
      proposal("TBS-MED-005", "OCC-001", "tbs-med-005-b01", "WARRANTY_RETENTION_RATIO", "质量保证金2%对应质保金比例，9%属于违约金上限。"),
      proposal("TBS-CON-001", "OCC-002", "tbs-con-001-b01", "PARTY_B", "承包人（乙方）标签直接指向青屿设备安装有限公司。"),
      proposal("TBS-CON-002", "OCC-002", "tbs-con-002-table-01", "TAX_AMOUNT", "税额标签直接对应42120，而非不含税金额或价税合计。"),
      proposal("TBS-CON-003", "OCC-001", "tbs-con-003-b01", "PROGRESS_PAYMENT_RATIO", "月度进度款74%对应目标角色；86%是完工累计比例，附件说明不参与比较。"),
      proposal("TBS-CON-004", "OCC-001", "tbs-con-004-b01", "SETTLEMENT_PAYMENT_RATIO", "结算审定后累计支付89%对应结算支付比例，11%是余款。"),
      abstentionProposal("TBS-CTL-HIGH-001", "DETERMINISTIC_HIGH_ZERO_CALL", "确定性 HIGH 必须零调用。"),
      abstentionProposal("TBS-CTL-INVALID-001", "BUNDLE_INVALID", "TABLE_CELL identity 不合法，bundle 必须 fail closed。"),
      abstentionProposal("TBS-CTL-NOANCHOR-001", "RELIABLE_ANCHOR_MISSING", "TOC context 不构成可靠业务证据 anchor。")
    ]
  };

  assert.equal(source.cases.length, source.caseCount);
  assert.equal(proposals.entries.length, proposals.entryCount);
  return { source, proposals };
}

async function main() {
  const repoRoot = resolve(process.argv[2] ?? ".");
  const outputRoot = resolve(repoRoot, ...OUTPUT_ROOT.split("/"));
  await mkdir(outputRoot, { recursive: false });
  const { source, proposals } = buildTrackBSuccessorSourceArtifacts();
  const sourceBytes = jsonBytes(source);
  const proposalBytes = jsonBytes(proposals);
  await writeFile(resolve(outputRoot, "source-signals.json"), sourceBytes, {
    flag: "wx"
  });
  await writeFile(
    resolve(outputRoot, "proposed-decisions.json"),
    proposalBytes,
    { flag: "wx" }
  );
  process.stdout.write(
    `${JSON.stringify({
      status: "TRACK_B_SUCCESSOR_SOURCE_FROZEN",
      sourceSha256: sha256(sourceBytes),
      proposedDecisionsSha256: sha256(proposalBytes),
      caseCount: source.caseCount,
      groundTruthEstablished: false,
      modelInputCreated: false,
      networkCallAllowed: false
    })}\n`
  );
}

function mediumCase({
  caseId,
  family,
  reviewPointCode,
  candidateRole,
  slotKey,
  occurrences
}) {
  return baseCase({
    caseId,
    identityClass: "MEDIUM",
    family,
    reviewPointCode,
    candidateRole,
    confidence: "MEDIUM",
    slotKey,
    coverageStatus: "LOW_CONFIDENCE",
    diagnosticCode: "SYS_EVIDENCE_MEDIUM_CONFIDENCE",
    occurrences
  });
}

function conflictedCase({
  caseId,
  family,
  reviewPointCode,
  candidateRole,
  slotKey,
  occurrences
}) {
  return baseCase({
    caseId,
    identityClass: "CONFLICTED",
    family,
    reviewPointCode,
    candidateRole,
    confidence: "CONFLICTED",
    slotKey,
    coverageStatus: "AMBIGUOUS",
    diagnosticCode: "SYS_ROLE_CONFLICT",
    occurrences
  });
}

function baseCase({
  caseId,
  identityClass,
  family,
  reviewPointCode,
  candidateRole,
  confidence,
  slotKey,
  coverageStatus,
  diagnosticCode,
  occurrences
}) {
  return {
    caseId,
    identityClass,
    family,
    reviewPointCode,
    candidateRole,
    confidence,
    evidenceStatus: "AMBIGUOUS",
    slotCoverage: {
      slotKey,
      required: true,
      critical: true,
      coverageStatus,
      diagnosticCode,
      reliableAnchor: true
    },
    assistPolicy: {
      resolverPolicy: "GEMMA_IF_AMBIGUOUS",
      modelAssistMode: "AMBIGUITY_RESOLUTION",
      executionStrategy: "LLM_EXTRACT_THEN_RULE"
    },
    maxEvidenceChars: 4096,
    occurrences
  };
}

function blockOccurrence({
  candidateValue,
  blockId,
  evidenceText,
  sectionPath,
  regionType = "BODY",
  contextType = "NORMAL",
  confidence = "MEDIUM"
}) {
  return {
    candidateValue,
    blockId,
    evidenceText,
    sectionPath,
    regionType,
    contextType,
    confidence,
    locationLevel: "BLOCK_LEVEL",
    previewElementRef: null
  };
}

function cellOccurrence({
  candidateValue,
  blockId,
  evidenceText,
  previewElementRef,
  sectionPath,
  confidence = "MEDIUM"
}) {
  return {
    candidateValue,
    blockId,
    evidenceText,
    sectionPath,
    regionType: "BODY",
    contextType: "NORMAL",
    confidence,
    locationLevel: "TABLE_CELL",
    previewElementRef
  };
}

function highControl() {
  return {
    caseId: "TBS-CTL-HIGH-001",
    identityClass: "CONTROL",
    family: "AMOUNT_TAX",
    reviewPointCode: "CONTRACT_TOTAL_AMOUNT_CONSISTENCY",
    candidateRole: "CONTRACT_TOTAL_AMOUNT",
    confidence: "HIGH",
    evidenceStatus: "CONFIRMED",
    slotCoverage: {
      slotKey: "contract_total_amount",
      required: true,
      critical: true,
      coverageStatus: "SATISFIED",
      diagnosticCode: null,
      reliableAnchor: true
    },
    assistPolicy: {
      resolverPolicy: "DETERMINISTIC_ONLY",
      modelAssistMode: "NONE",
      executionStrategy: "DETERMINISTIC"
    },
    maxEvidenceChars: 4096,
    occurrences: [
      blockOccurrence({
        candidateValue: "612340",
        blockId: "tbs-ctl-high-001-b01",
        evidenceText: "合同含税总价为612340元。",
        sectionPath: ["合同价款"],
        confidence: "HIGH"
      })
    ]
  };
}

function invalidControl() {
  return {
    ...mediumCase({
      caseId: "TBS-CTL-INVALID-001",
      family: "PAYMENT_TERMS",
      reviewPointCode: "PREPAYMENT_RATIO_CONSISTENCY",
      candidateRole: "PREPAYMENT_RATIO",
      slotKey: "prepayment_ratio",
      occurrences: [
        cellOccurrence({
          candidateValue: "17",
          blockId: "tbs-ctl-invalid-001-table-01",
          evidenceText: "预付款比例17%",
          previewElementRef: "table:tbs_ctl_invalid/row:x/cell:2",
          sectionPath: ["付款表"]
        })
      ]
    }),
    identityClass: "CONTROL",
    slotCoverage: {
      slotKey: "prepayment_ratio",
      required: true,
      critical: true,
      coverageStatus: "MISSING",
      diagnosticCode: "SYS_INDEX_INCOMPLETE",
      reliableAnchor: false
    }
  };
}

function noAnchorControl() {
  return {
    ...mediumCase({
      caseId: "TBS-CTL-NOANCHOR-001",
      family: "PARTY_FIELDS",
      reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
      candidateRole: "PARTY_B",
      slotKey: "party_b",
      occurrences: [
        blockOccurrence({
          candidateValue: "汀澜运维服务有限公司",
          blockId: "tbs-ctl-noanchor-001-b01",
          evidenceText: "目录索引：汀澜运维服务有限公司。",
          sectionPath: ["目录"],
          contextType: "TOC"
        })
      ]
    }),
    identityClass: "CONTROL",
    slotCoverage: {
      slotKey: "party_b",
      required: true,
      critical: true,
      coverageStatus: "MISSING",
      diagnosticCode: "SYS_INDEX_INCOMPLETE",
      reliableAnchor: false
    }
  };
}

function proposal(
  caseId,
  occurrenceId,
  anchorBlockId,
  suggestedRole,
  rationale
) {
  return {
    caseId,
    proposedRationale: rationale,
    proposedExpected: {
      suggestedRole,
      selectedOccurrenceIds: [occurrenceId],
      selectedAnchorBlockIds: [anchorBlockId],
      abstain: false,
      abstentionReason: null
    }
  };
}

function abstentionProposal(caseId, reason, rationale) {
  return {
    caseId,
    proposedRationale: rationale,
    proposedExpected: {
      suggestedRole: null,
      selectedOccurrenceIds: [],
      selectedAnchorBlockIds: [],
      abstain: true,
      abstentionReason: reason
    }
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  await main();
}
