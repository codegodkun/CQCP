# CQCP MVP Test Fixtures

本目录存放 CQCP MVP 第一批测试样例，用于 Word parser、结构化字段抽取、审核点判断、证据定位，以及 `expected/*.json` 夹具维护。

## 当前作用

`cqcp-mvp-sample-matrix.xlsx` 当前承担以下职责：

1. 记录 4 份合同样例的 `GOLDEN_EXPECTED` 正确数据。
2. 记录 `NEGATIVE_CANDIDATE` 错误候选数据。
3. 说明哪些错误属于单点错误，哪些会联动触发公式错误。
4. 作为后续生成 `expected/*.json` 的来源基础。

## 文件说明

- `docx/`：4 份 `.docx` 合同样例文件。
- `cqcp-mvp-sample-matrix.xlsx`：样例矩阵，承载正确数据、错误候选数据与联动说明。
- `expected/`：第一批 expected result JSON 文件目录。

## 当前样例

| sampleId | 文件 | 说明 |
|---|---|---|
| CQCP-MVP-DOCX-001 | 1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx | 工程采购合同缩减版 |
| CQCP-MVP-DOCX-002 | 2、达利安造船厂四方城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx | 产品采购合同缩减版 |
| CQCP-MVP-DOCX-003 | 3、星辰建设集团南山科技园项目二标段土建总承包工程合同_缩减版.docx | 工程采购合同缩减版 |
| CQCP-MVP-DOCX-004 | 4、东海船舶工业集团滨海城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx | 产品采购合同缩减版 |

## Expected 夹具

当前已生成：

- `expected/CQCP-MVP-DOCX-001.json`
- `expected/CQCP-MVP-DOCX-002.json`
- `expected/CQCP-MVP-DOCX-003.json`
- `expected/CQCP-MVP-DOCX-004.json`

每份 `expected/*.json` 当前包含：

- `sampleId`
- `sourceDocx`
- `matrixWorkbook`
- `matrixSheet`
- `goldenExpected`
- `negativeCandidates`
- `goldenExpected.evidenceEvaluation`

其中：

- `goldenExpected` 保存该样例的 `GOLDEN_EXPECTED` 基线。
- `negativeCandidates` 保存该样例的 `NEGATIVE_CANDIDATE` 候选数据。
- `goldenExpected.evidenceEvaluation.positiveCases[]` 保存 test-only evidence overlap baseline：
  - `caseId`
  - `reviewPointCode`
  - `expectedCandidateValue`
  - `expectedCanonicalAnchors[]`
- 每个 negative candidate 同时保存：
  - `allDiffs`
  - `singlePointDiffs`
  - `formulaLinkedEffects`

## 映射关系

- 4 份 `.docx` 样例与 4 份 `expected/*.json` 一一对应。
- `cqcp-mvp-sample-matrix.xlsx` 的 `合同清单` sheet 是当前唯一来源表。
- `GOLDEN_EXPECTED` 行写入对应样例的 `goldenExpected`。
- `NEGATIVE_CANDIDATE` 行按合同文件名聚合后写入同一样例的 `negativeCandidates[]`。
- matrix 中“同时导致税额公式不一致”的描述当前写入 `formulaLinkedEffects`；“金额数据连环错误”当前写入 `DATA_CASCADE`。

## Evidence Anchor Canonical Key

证据定位评测统一使用：

```text
BLOCK:<blockId>
TABLE_ROW:<blockId>:<rowIndex>
TABLE_CELL:<blockId>:<rowIndex>:<cellIndex>
```

约束：

- expected anchor 由人工冻结，不得在测试运行时从 actual 结果反向生成。
- `candidateValue` 正确但 canonical anchor 不匹配时，评测必须失败。
- 同一 table、同一 block 或同一 row 不得宽松替代 cell 命中。
- 字符级 span 不属于当前 MVP overlap baseline。
- `negativeCandidates[]` 继续用于结构化输入与业务状态回归，不直接等价为 wrong-anchor 样本。

## 人工 Anchor Ground Truth Fixture

`human-anchors/` 目录存放独立于 parser / AI 输出的人工 DOCX anchor 标准答案 fixture。

### 来源

- 唯一来源：`outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx`，sheet `anchor明细待确认`。
- 63 条逐出处明细已由 data owner `ZK` 逐条接受为 `ACCEPTED_HUMAN_GROUND_TRUTH`，`groundTruthSource = MANUAL_DOCX_REVIEW`。
- 标注过程未参考 parser / AI / 被测系统 actual 输出；所有位置信息均由人工直接阅读 DOCX 确定。

### 数量

| sampleId | acceptedOccurrenceCount | includedOccurrenceCount | excludedOccurrenceCount |
|---|---|---|---|
| CQCP-MVP-DOCX-001 | 22 | 20 | 2 |
| CQCP-MVP-DOCX-002 | 19 | 17 | 2 |
| CQCP-MVP-DOCX-003 | 22 | 20 | 2 |
| **合计** | **63** | **57** | **6** |

6 条排除项为合同标题或前言中的合同名称，不纳入甲乙方名称一致性判断，但保留完整 occurrence 记录用于追溯。

### 独立性边界

- human-anchor fixture 不包含 `blockId`、`rowIndex`、`cellIndex`、`previewElementRef`、`expectedCanonicalAnchors` 或任何 parser actual 派生字段。
- 现有 `expected/*.json` 中的 `expectedCanonicalAnchors[]` 继续作为 parser-backed 回归基线，不声明为本次人工 ground truth。
- 人工 anchor 不直接参与 `EvidenceOverlapEvaluator` 的 canonical key 匹配；人工位置描述 → parser canonical key 的 bridge 任务不在当前范围。

### 文件清单

- `human-anchors/CQCP-MVP-DOCX-001.json`
- `human-anchors/CQCP-MVP-DOCX-002.json`
- `human-anchors/CQCP-MVP-DOCX-003.json`

### expected JSON 引用

每份 `expected/CQCP-MVP-DOCX-00{1,2,3}.json` 的 `goldenExpected` 下新增 `humanAnchorGroundTruth` 引用对象，包含 schema 版本、fixture 路径、来源工作簿/工作表、data owner、状态和各数量，不复制 63 条 occurrence 明细。

## 使用边界

- CQCP 不负责判断样例是否完成脱敏合规处理。
- 样例进入本目录前，应由业务方或 data owner 判断其是否可用于项目验证。
- 当前 `expected/*.json` 只表达第一批 MVP 测试夹具基线，不等于最终 parser / engine 运行态输出格式。
