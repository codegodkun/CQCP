import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { jsonBytes, sha256 } from "./track-b-holdout-contract.mjs";
import {
  buildTrackBSuccessorSourceArtifacts
} from "./prepare-track-b-successor-source.mjs";

const OUTPUT_ROOT =
  "apps/api-server/src/test/resources/track-b-final-v1";

export function buildTrackBFinalSourceArtifacts() {
  const template = buildTrackBSuccessorSourceArtifacts();
  const source = structuredClone(template.source);
  source.schemaVersion = "task-eval-004-track-b-final-source-signals-v1";
  source.ruleSetVersion = "v20260803.final.1";
  source.identities = {
    MEDIUM: {
      taskId: "task-eval-004-track-b-final-medium",
      executionId: "execution-track-b-final-medium-v1"
    },
    CONFLICTED: {
      taskId: "task-eval-004-track-b-final-conflicted",
      executionId: "execution-track-b-final-conflicted-v1"
    },
    CONTROL: {
      taskId: "task-eval-004-track-b-final-control",
      executionId: "execution-track-b-final-control-v1"
    }
  };

  const specifications = finalCaseSpecifications();
  assert.equal(specifications.length, source.cases.length);
  source.cases = source.cases.map((item, index) =>
    applySpecification(item, specifications[index])
  );

  const proposals = {
    schemaVersion: "task-eval-004-track-b-final-proposed-decisions-v1",
    status: "CODEX_PROPOSAL_NOT_HUMAN_GROUND_TRUTH",
    draftedBy: "CODEX",
    humanGroundTruthEstablished: false,
    entryCount: 12,
    entries: [
      proposal(
        "TBF-MED-001",
        "OCC-002",
        "tbf-med-001-b02",
        "PARTY_A",
        "委托方（甲方）标签直接标识合同甲方，现场联系人不是合同主体。"
      ),
      proposal(
        "TBF-MED-002",
        "OCC-001",
        "tbf-med-002-b01",
        "PARTY_B",
        "承包方（乙方）是合同主体，结算中心只是收款信息。"
      ),
      proposal(
        "TBF-MED-003",
        "OCC-001",
        "tbf-med-003-table-01",
        "CONTRACT_TOTAL_AMOUNT",
        "签约含税价对应合同总金额，采购控制价不是签约金额。"
      ),
      proposal(
        "TBF-MED-004",
        "OCC-001",
        "tbf-med-004-b01",
        "PREPAYMENT_RATIO",
        "启动预付款13.5%对应目标角色，6.5%属于履约担保。"
      ),
      proposal(
        "TBF-MED-005",
        "OCC-001",
        "tbf-med-005-b01",
        "WARRANTY_RETENTION_RATIO",
        "质保金2.75%对应目标角色，8.25%是逾期责任上限。"
      ),
      proposal(
        "TBF-CON-001",
        "OCC-002",
        "tbf-con-001-b01",
        "PARTY_A",
        "发包人（甲方）标签直接指向牧星能源装备有限公司。"
      ),
      proposal(
        "TBF-CON-002",
        "OCC-002",
        "tbf-con-002-table-01",
        "TAX_AMOUNT",
        "税额标签直接对应66168，而非不含税金额或价税合计。"
      ),
      proposal(
        "TBF-CON-003",
        "OCC-001",
        "tbf-con-003-b01",
        "PROGRESS_PAYMENT_RATIO",
        "月度计量款73.5%对应进度款；87.5%是验收累计比例，附件编号不参与。"
      ),
      proposal(
        "TBF-CON-004",
        "OCC-001",
        "tbf-con-004-b01",
        "SETTLEMENT_PAYMENT_RATIO",
        "结算确认后累计支付91.25%对应结算支付比例，8.75%是保留余款。"
      ),
      abstentionProposal(
        "TBF-CTL-HIGH-001",
        "DETERMINISTIC_HIGH_ZERO_CALL",
        "确定性 HIGH 必须零调用。"
      ),
      abstentionProposal(
        "TBF-CTL-INVALID-001",
        "BUNDLE_INVALID",
        "TABLE_CELL identity 不合法，bundle 必须 fail closed。"
      ),
      abstentionProposal(
        "TBF-CTL-NOANCHOR-001",
        "RELIABLE_ANCHOR_MISSING",
        "TOC context 不构成可靠业务证据 anchor。"
      )
    ]
  };

  assert.equal(source.cases.length, source.caseCount);
  assert.equal(proposals.entries.length, proposals.entryCount);
  return { source, proposals };
}

function finalCaseSpecifications() {
  return [
    spec("TBF-MED-001", [
      occurrence(
        "陆遥",
        "tbf-med-001-b01",
        "甲方现场联系人：陆遥，负责进场协调。",
        ["项目联络"]
      ),
      occurrence(
        "砺川智能装备有限公司",
        "tbf-med-001-b02",
        "委托方（甲方）：砺川智能装备有限公司。",
        ["签约主体"]
      )
    ]),
    spec("TBF-MED-002", [
      occurrence(
        "朔浦机电安装有限公司",
        "tbf-med-002-b01",
        "承包方（乙方）：朔浦机电安装有限公司。",
        ["签约主体"]
      ),
      occurrence(
        "朔浦结算中心",
        "tbf-med-002-b02",
        "收款账户户名：朔浦结算中心。",
        ["付款账户"]
      )
    ]),
    spec("TBF-MED-003", [
      cellOccurrence(
        "684730",
        "tbf-med-003-table-01",
        "签约含税价（元）684730",
        "table:tbf_med_003/row:1/cell:2",
        ["签约价款表"]
      ),
      cellOccurrence(
        "711800",
        "tbf-med-003-table-01",
        "采购控制价（元）711800",
        "table:tbf_med_003/row:2/cell:2",
        ["签约价款表"]
      )
    ]),
    spec("TBF-MED-004", [
      occurrence(
        "13.5",
        "tbf-med-004-b01",
        "合同生效后支付签约价的13.5%作为启动预付款。",
        ["资金支付"]
      ),
      occurrence(
        "6.5",
        "tbf-med-004-b02",
        "履约保函金额为签约价的6.5%。",
        ["履约保障"]
      )
    ]),
    spec("TBF-MED-005", [
      occurrence(
        "2.75",
        "tbf-med-005-b01",
        "最终结算价的2.75%留作工程质量保证金。",
        ["保修与质保"]
      ),
      occurrence(
        "8.25",
        "tbf-med-005-b02",
        "逾期责任累计最高为签约价的8.25%。",
        ["责任限制"]
      )
    ]),
    spec(
      "TBF-CON-001",
      [
        occurrence(
          "临川项目管理有限公司",
          "tbf-con-001-b01",
          "发包人（甲方）：牧星能源装备有限公司；全过程咨询单位：临川项目管理有限公司。",
          ["合同当事人"],
          { confidence: "CONFLICTED" }
        ),
        occurrence(
          "牧星能源装备有限公司",
          "tbf-con-001-b01",
          "发包人（甲方）：牧星能源装备有限公司；全过程咨询单位：临川项目管理有限公司。",
          ["合同当事人"],
          { confidence: "CONFLICTED" }
        )
      ],
      {
        family: "PARTY_FIELDS",
        reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
        candidateRole: "PARTY_A",
        slotKey: "party_a"
      }
    ),
    spec("TBF-CON-002", [
      cellOccurrence(
        "735200",
        "tbf-con-002-table-01",
        "未税金额735200元，税额66168元，含税合计801368元。",
        "table:tbf_con_002/row:1/cell:1",
        ["税价构成"],
        "CONFLICTED"
      ),
      cellOccurrence(
        "66168",
        "tbf-con-002-table-01",
        "未税金额735200元，税额66168元，含税合计801368元。",
        "table:tbf_con_002/row:1/cell:2",
        ["税价构成"],
        "CONFLICTED"
      ),
      cellOccurrence(
        "801368",
        "tbf-con-002-table-01",
        "未税金额735200元，税额66168元，含税合计801368元。",
        "table:tbf_con_002/row:1/cell:3",
        ["税价构成"],
        "CONFLICTED"
      )
    ]),
    spec("TBF-CON-003", [
      occurrence(
        "73.5",
        "tbf-con-003-b01",
        "月度计量款按确认完成量的73.5%支付。",
        ["计量付款"],
        { confidence: "CONFLICTED" }
      ),
      occurrence(
        "87.5",
        "tbf-con-003-b02",
        "阶段验收后累计支付至签约价的87.5%。",
        ["计量付款"],
        { confidence: "CONFLICTED" }
      ),
      occurrence(
        "103.5",
        "tbf-con-003-b03",
        "附件中的备案序号仅作资料索引，不属于付款比例。",
        ["资料附件"],
        { regionType: "APPENDIX", confidence: "CONFLICTED" }
      )
    ]),
    spec("TBF-CON-004", [
      occurrence(
        "91.25",
        "tbf-con-004-b01",
        "结算确认后累计支付至结算价的91.25%，其余8.75%按保修约定处理。",
        ["结算付款"],
        { confidence: "CONFLICTED" }
      ),
      occurrence(
        "8.75",
        "tbf-con-004-b01",
        "结算确认后累计支付至结算价的91.25%，其余8.75%按保修约定处理。",
        ["结算付款"],
        { confidence: "CONFLICTED" }
      )
    ]),
    spec("TBF-CTL-HIGH-001", [
      occurrence(
        "623450",
        "tbf-ctl-high-001-b01",
        "合同含税总金额明确为623450元。",
        ["合同金额"],
        { confidence: "HIGH" }
      )
    ]),
    spec("TBF-CTL-INVALID-001", [
      cellOccurrence(
        "16.5",
        "tbf-ctl-invalid-001-table-01",
        "预付款比例16.5%",
        "table:tbf_ctl_invalid/row:x/cell:2",
        ["支付比例表"]
      )
    ]),
    spec("TBF-CTL-NOANCHOR-001", [
      occurrence(
        "鹭湾设施维护有限公司",
        "tbf-ctl-noanchor-001-b01",
        "目录条目：鹭湾设施维护有限公司。",
        ["目录"],
        { contextType: "TOC" }
      )
    ])
  ];
}

function spec(caseId, occurrences, overrides = {}) {
  return { caseId, occurrences, ...overrides };
}

function occurrence(
  candidateValue,
  blockId,
  evidenceText,
  sectionPath,
  {
    regionType = "BODY",
    contextType = "NORMAL",
    confidence
  } = {}
) {
  return {
    candidateValue,
    blockId,
    evidenceText,
    sectionPath,
    regionType,
    contextType,
    ...(confidence ? { confidence } : {}),
    locationLevel: "BLOCK_LEVEL",
    previewElementRef: null
  };
}

function cellOccurrence(
  candidateValue,
  blockId,
  evidenceText,
  previewElementRef,
  sectionPath,
  confidence
) {
  return {
    candidateValue,
    blockId,
    evidenceText,
    sectionPath,
    regionType: "BODY",
    contextType: "NORMAL",
    ...(confidence ? { confidence } : {}),
    locationLevel: "TABLE_CELL",
    previewElementRef
  };
}

function applySpecification(templateCase, specification) {
  const item = structuredClone(templateCase);
  item.caseId = specification.caseId;
  item.family = specification.family ?? item.family;
  item.reviewPointCode =
    specification.reviewPointCode ?? item.reviewPointCode;
  item.candidateRole = specification.candidateRole ?? item.candidateRole;
  item.slotCoverage.slotKey =
    specification.slotKey ?? item.slotCoverage.slotKey;
  assert.equal(item.occurrences.length, specification.occurrences.length);
  item.occurrences = item.occurrences.map((oldOccurrence, index) => ({
    ...oldOccurrence,
    ...specification.occurrences[index]
  }));
  return item;
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

async function main() {
  const repoRoot = resolve(process.argv[2] ?? ".");
  const outputRoot = resolve(repoRoot, ...OUTPUT_ROOT.split("/"));
  await mkdir(outputRoot, { recursive: false });
  const { source, proposals } = buildTrackBFinalSourceArtifacts();
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
      status: "TRACK_B_FINAL_SOURCE_FROZEN",
      sourceSha256: sha256(sourceBytes),
      proposedDecisionsSha256: sha256(proposalBytes),
      caseCount: source.caseCount,
      groundTruthEstablished: false,
      modelInputCreated: false,
      networkCallAllowed: false
    })}\n`
  );
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  await main();
}
