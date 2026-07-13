# TASK-DATA-001：MVP E2E 人工 anchor 准备

状态：Active / 人工 anchor 准备完成 / 63 条逐出处明细已接受 / TASK_SPEC-DATA-001-A 实现已接受 / 独立审计 GO / 已 commit 并 push / PR #28 checks passed / 未授权合并

类型：Data / Evaluation / Codex 主控任务

优先级：P0

负责人：Codex 总控；人工 anchor 标准答案必须由业务方、data owner 或人工标注者确认

创建日期：2026-07-09

来源：用户授权、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`

## 背景

`TASK-033` 已冻结 MVP 端到端样本验收规格，并明确后续正式验收前需要 `TASK-DATA-001` 准备真实 DOCX `TABLE_CELL` 人工 anchor 标准答案。

当前 `TASK-EVAL-001` 独立审计后状态为 `NO-GO TO ARCHIVE / KEEP ACTIVE`：DoD #12 固定未通过、不可补足。该任务当时的 parser-backed expected / anchor 只证明一致性，不证明独立人工 ground truth。此后 `TASK-DATA-001` 已独立形成并接受真实 DOCX 的人工 `BLOCK` / `TABLE_CELL` ground truth；尚未完成的是 fixture / expected JSON / 测试转换和完整 MVP E2E 验证。

本任务只做人工 anchor 准备与标注规则冻结，不实现 parser、不修改 fixture、不生成 expected JSON、不进入 `TASK-028` / `TASK-031` / `TASK-032`。

## Review Intake Decision

Decision：`GO TO TASK-DATA-001 TASK BUILD / NO IMPLEMENTATION AUTHORIZATION`

理由：

* `TASK-033` 已把真实 DOCX `TABLE_CELL` 人工 anchor 标准答案准备明确分流到 `TASK-DATA-001`。
* `TASK-EVAL-001` 已停止围绕归档反复补文档，后续实际推进方向合并为 `TASK-DATA-001` / MVP E2E 人工 anchor 准备。
* 当前缺口是 data / evaluation ground truth 准备，不是代码修复任务。
* 本任务不解除 `TASK-028` / `TASK-031` / `TASK-032` 门禁。

## 目标

* 冻结真实 DOCX `TABLE_CELL` 人工 anchor 标注规则。
* 从 `packages/test-fixtures/README.md` 已登记样本池中选定 2-3 份 MVP E2E 候选样本，并说明选择理由。
* 明确每份样本的 data owner / 人工确认状态；无法确认时标记为“待确认”。
* 设计人工 anchor 标准答案记录格式，确保标准答案独立于 parser / AI 输出。
* 明确人工答案后续如何转换为 fixture / expected JSON / 测试，但本任务不执行转换。

## 非目标

* 不写业务代码。
* 不改测试代码。
* 不改 DOCX 样本。
* 不改 `packages/test-fixtures/expected/*.json`。
* 不修改 `cqcp-mvp-sample-matrix.xlsx`。
* 不修改 parser、`CandidateResolver`、`EvidenceSlot`、`SourceAnchor` 或 Review Engine。
* 不运行完整 MVP E2E 验收。
* 不归档 `TASK-EVAL-001`，不补足 DoD #12。
* 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 不把 parser / AI 输出转换成人工 ground truth。

边界例外记录：上述“不改 DOCX 样本”约束适用于 Codex 执行动作。人工核对期间，用户直接修正了 `CQCP-MVP-DOCX-003`，Codex 未编辑该文件；为保证已接受 XLSX 可由同一份原始合同重建，本任务提交基线必须保留该用户侧修正，且不得隐藏或回退。具体差异和哈希见“用户侧 DOCX 003 修正例外”。

## 输入

* 相关文档：
  * `AGENTS.md`
  * `CURRENT_CONTEXT.md`
  * `tasks/MVP_TASK_MAP.md`
  * `docs/VERIFY.md`
  * `packages/test-fixtures/README.md`
* 上游任务：
  * `tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`
  * `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
  * `tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`
* 候选样本池：
  * `CQCP-MVP-DOCX-001`：工程采购合同缩减版
  * `CQCP-MVP-DOCX-002`：产品采购合同缩减版
  * `CQCP-MVP-DOCX-003`：工程采购合同缩减版
  * `CQCP-MVP-DOCX-004`：产品采购合同缩减版

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/MVP_TASK_MAP.md`
* `docs/VERIFY.md`
* `packages/test-fixtures/README.md`
* `tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`

### Optional Context

* `tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`
* `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
* `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* `docs/ARCHITECTURE.md` 中样本集、质量验证和 EvidenceSlot 相关章节

### Out of Scope

* 业务代码、测试代码、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR、PRD。
* `TASK-EVAL-001` 归档或 DoD #12 补足。
* `TASK-028` / `TASK-031` / `TASK-032` Review Intake、规格冻结或实现。
* parser provenance / `SourceAnchor` 实现。

## 范围

### 包含

* 冻结人工 anchor 标注规则：
  * 标注者不得参考 parser actual 输出、AI 输出或被测系统运行结果来确定 ground truth。
  * 标注记录必须能说明合同原文位置、所在表格/行/单元格、人工判断理由和待确认项。
  * 对 `TABLE_CELL`，必须记录可人工复核的表格上下文，而不是只记录 parser 产生的 `blockId / rowIndex / cellIndex`。
* 选定 2-3 份候选样本：
  * 至少 1 份工程采购合同。
  * 至少 1 份产品采购合同。
  * 若选择第 3 份，优先选择含明显表格结构、能暴露证据定位风险的样本。
* 冻结人工标注输出格式，建议最小字段：
  * `sampleId`
  * `docxPath`
  * `contractType`
  * `reviewPointCode`
  * `expectedCandidateValue`
  * `anchorGranularity`
  * `humanAnchorText`
  * `humanLocationDescription`
  * `tableContext`
  * `rowContext`
  * `cellContext`
  * `groundTruthSource`
  * `dataOwner`
  * `independenceStatement`
  * `status`
  * `notes`
* 输出后续转换边界：
  * 人工标注完成后，需另行冻结 `TASK_SPEC` 才能转换为 fixture / expected JSON / 测试。
  * 转换任务必须说明 expected 值来源，避免循环验证。

### 不包含

* 不实际生成或修改 `expected/*.json`。
* 不实际新增自动化测试。
* 不解析 DOCX、不中途修 parser、不跑完整 E2E。
* 不把候选样本选择写成 data owner 已确认，除非已有明确人工确认。

## 人工 anchor 标注规则冻结

### 独立性规则

1. 人工 anchor 标准答案只能来自人工直接阅读 DOCX 原文、业务方确认材料或 data owner 明确确认，不得参考 parser actual 输出、AI 输出、被测系统运行结果、已有 `expected/*.json` 中的 `expectedCanonicalAnchors[]` 或测试日志来确定 ground truth。
2. `packages/test-fixtures/cqcp-mvp-sample-matrix.xlsx` 可作为业务字段候选值来源参考，但本任务不修改该文件；真实 DOCX `TABLE_CELL` anchor 的位置判断仍必须由人工独立标注。
3. 标注者若在标注过程中接触过 parser / AI / actual 输出，必须在 `notes` 中说明，并将该条 `status` 标记为 `NEEDS_REVIEW`，不得直接作为人工 ground truth。
4. 无法从 DOCX 原文中人工复核的位置，不得补写为 `TABLE_CELL`；应标记为 `UNAVAILABLE` 或 `NEEDS_REVIEW`，并记录待确认原因。

### TABLE_CELL 标注规则

1. `anchorGranularity = TABLE_CELL` 时，`humanAnchorText` 必须是人工在 DOCX 可见单元格中识别到的原文片段或完整单元格文本，不得填写 parser 生成的 canonical key。
2. `humanLocationDescription` 必须描述人工可复核的位置，例如章节、页内附近标题、表格名称或表格前后文本；不得只写 `blockId / rowIndex / cellIndex`。
3. `tableContext` 必须记录表格可人工识别的上下文，例如表格标题、表格前后段落、表头字段或表格用途；如无标题，应记录邻近文本和人工定位方式。
4. `rowContext` 必须记录行级上下文，例如行标题、序号、项目名称、金额字段所在行、同一行关键文本；如果行标题缺失，应记录能区分该行的邻近单元格文本。
5. `cellContext` 必须记录列名、字段名、相邻单元格或该单元格在表格中的人工定位说明；同一 table 或同一 row 不得宽松替代 cell 命中。
6. 人工标注可以记录 `parserCanonicalKeyCandidate` 作为后续转换时的“待映射字段”，但该字段不得作为 ground truth 来源；本任务模板默认不把该字段列为最小必填字段。

### 多出处拆分规则

1. 同一审核点在 DOCX 中出现多次时，审核点汇总行只表达预期候选值和人工汇总，不直接作为后续 anchor 转换输入；每个可见出处必须在“anchor明细待确认”中单独一行记录。
2. 每条明细使用唯一 `occurrenceNo`，分别记录该出处的 `observedCandidateValue`、`anchorGranularity`、原文、页码和结构上下文；不得用顿号或换行把多个出处合并成一个 anchor。
3. 同一审核点的不同出处可以分别是 `BLOCK` 或 `TABLE_CELL`，必须按 Word 实际可见结构逐条标注，不能因审核点相同而统一粒度。
4. `comparisonResult` 由人工逐条填写为 `MATCH`、`MISMATCH` 或 `NEEDS_REVIEW`；任一应纳入审核范围的出处为 `MISMATCH`，该审核点即存在不一致。
5. Codex 根据 DOCX 和人工汇总生成的拆分明细只能标记为 `AI_ASSISTED_SPLIT_FROM_MANUAL_SUMMARY_NOT_GROUND_TRUTH / NEEDS_REVIEW`。只有人工逐条核对后，才可改为 `MANUAL_DOCX_REVIEW / ANNOTATED_PENDING_REVIEW`。

### 状态枚举

| `status` | 含义 |
|---|---|
| `PENDING_OWNER_CONFIRMATION` | 样本或标注尚待 data owner / 业务方确认可用于项目验证。 |
| `PENDING_HUMAN_ANNOTATION` | 已选为候选样本，但人工 anchor 标注尚未完成。 |
| `ANNOTATED_PENDING_REVIEW` | 人工已完成标注，但尚未复核独立性或样本可用性。 |
| `ACCEPTED_HUMAN_GROUND_TRUTH` | data owner / 人工复核已确认，可作为后续转换输入。 |
| `NEEDS_REVIEW` | 标注位置、字段值、独立性或样本可用性存在疑问。 |
| `REJECTED` | 样本或标注不应进入后续 fixture / expected JSON / 测试转换。 |

## 候选样本选择记录

本轮从 `packages/test-fixtures/README.md` 已登记样本池中选定 3 份候选样本。`CQCP-MVP-DOCX-001` / `002` / `003` 共 27 条审核点汇总已由 `ZK` 填写；为避免多出处被合并，Codex 又直接读取当前 DOCX，将三份合同拆为 63 条逐出处明细。2026-07-12，`ZK` 确认 H 列观察值和 J-O 列定位信息，63 条比较结果均为 `MATCH`，其中 6 条合同标题/前言名称明确标记为“排除”；明细来源和状态已更新为 `MANUAL_DOCX_REVIEW / ACCEPTED_HUMAN_GROUND_TRUTH`。27 条汇总行仅供审核点总览，不作为单条 anchor 转换输入。旧“AI预提取草稿”工作表已删除。

| sampleId | docxPath | contractType | 选择理由 | data owner | 人工确认状态 | 本轮结论 |
|---|---|---|---|---|---|---|
| `CQCP-MVP-DOCX-001` | `packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx` | 工程采购合同缩减版 | 覆盖工程采购合同，满足至少 1 份工程采购候选样本要求；可用于人工标注工程类审核点的原文定位。 | `ZK` | 样本可用、脱敏合规；22 条逐出处明细已接受；`ACCEPTED_HUMAN_GROUND_TRUTH` | 选定为候选样本；可作为后续转换任务输入。 |
| `CQCP-MVP-DOCX-002` | `packages/test-fixtures/docx/2、达利安造船厂四方城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx` | 产品采购合同缩减版 | 覆盖产品采购合同，满足至少 1 份产品采购候选样本要求；用于与工程采购合同形成合同类型差异覆盖。 | `ZK` | 样本可用、脱敏合规；19 条逐出处明细已接受；`ACCEPTED_HUMAN_GROUND_TRUTH` | 选定为候选样本；可作为后续转换任务输入。 |
| `CQCP-MVP-DOCX-003` | `packages/test-fixtures/docx/3、星辰建设集团南山科技园项目二标段土建总承包工程合同_缩减版.docx` | 工程采购合同缩减版 | 封面甲乙方和签约通知书金额位于可见表格单元格，可补充 `TABLE_CELL` 人工定位。当前 DOCX 已显示“二标段”和总价 `12800 / 壹万贰仟捌佰元整`。 | `ZK` | 样本可用、脱敏合规；22 条逐出处明细已接受；`ACCEPTED_HUMAN_GROUND_TRUTH` | 选定为第 3 份候选；可作为后续转换任务输入。 |

`CQCP-MVP-DOCX-004` 本轮不选：DOCX 可见结构中未发现表格，无法补充 `TABLE_CELL` 盲区；其预付款 15%、结算款 100% / 97% 并存、质量保函 3% 等内容可作为后续付款比例冲突样本线索，但不占用本任务最多 3 份候选中的最后一个名额。

## 人工标注字段模板

人工 anchor 标准答案已确认使用 `outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx` 记录；该 XLSX 是后续转换的唯一人工源。最小字段如下：

| 字段 | 必填 | 说明 |
|---|---|---|
| `sampleId` | 是 | 使用 `packages/test-fixtures/README.md` 登记 ID。 |
| `docxPath` | 是 | DOCX 仓库路径，不得指向临时副本。 |
| `contractType` | 是 | 工程采购合同缩减版 / 产品采购合同缩减版；无法确认时写 `待确认`。 |
| `reviewPointCode` | 是 | 对应审核点代码；无法确认时写 `待确认`，不得自行补全架构未定义代码。 |
| `expectedCandidateValue` | 是 | 人工识别的候选值；来源需在 `groundTruthSource` 说明。 |
| `anchorGranularity` | 是 | `BLOCK` / `TABLE_ROW` / `TABLE_CELL` / `UNAVAILABLE`。 |
| `humanAnchorText` | 是 | 人工从 DOCX 原文识别的定位文本。 |
| `humanLocationDescription` | 是 | 人工可复核的位置描述，不得只写 parser id。 |
| `tableContext` | 条件必填 | `TABLE_ROW` / `TABLE_CELL` 必填；记录表格标题、表头、前后段落或表格用途。 |
| `rowContext` | 条件必填 | `TABLE_ROW` / `TABLE_CELL` 必填；记录行标题、序号、项目名或同一行关键文本。 |
| `cellContext` | 条件必填 | `TABLE_CELL` 必填；记录列名、字段名、相邻单元格或人工定位说明。 |
| `groundTruthSource` | 是 | 例如 `MANUAL_DOCX_REVIEW`、`DATA_OWNER_CONFIRMED`、`BUSINESS_REVIEW_CONFIRMED`；不得写 parser / AI / actual 输出。 |
| `dataOwner` | 是 | data owner / 业务确认人；未知时写 `待确认`。 |
| `independenceStatement` | 是 | 说明该条标注是否独立于 parser / AI / 被测系统 actual 输出。 |
| `status` | 是 | 使用本任务冻结的状态枚举。 |
| `notes` | 否 | 记录待确认项、争议点、人工复核说明和不得转换原因。 |

示例空模板：

| sampleId | docxPath | contractType | reviewPointCode | expectedCandidateValue | anchorGranularity | humanAnchorText | humanLocationDescription | tableContext | rowContext | cellContext | groundTruthSource | dataOwner | independenceStatement | status | notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | `TABLE_CELL` | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | `MANUAL_DOCX_REVIEW` | 待确认 | 未参考 parser / AI / 被测系统 actual 输出 | `PENDING_HUMAN_ANNOTATION` | 待填写 |

## 后续 fixture / expected JSON / 测试转换边界

1. 本任务不生成、不修改 `packages/test-fixtures/expected/*.json`，不修改 `cqcp-mvp-sample-matrix.xlsx`，不新增测试，不运行完整 MVP E2E。
2. 人工标注完成后，必须另行冻结关联本父任务的 `TASK_SPEC`，才能把人工答案转换为 fixture / expected JSON / 测试。
3. 后续转换 `TASK_SPEC` 必须逐项说明 expected 值来源：哪些来自人工 DOCX 标注，哪些来自 matrix 业务字段，哪些只是 parser-backed 一致性基线。
4. 若 expected 值依赖 parser / AI / 被测系统 actual 输出，只能声明一致性或回归稳定性，不得声明独立人工正确性。
5. 后续转换任务必须保留人工标注记录与 generated expected JSON 的可追溯映射；转换过程不得用 actual 输出反向修正人工 anchor。
6. 只有当 `status = ACCEPTED_HUMAN_GROUND_TRUTH` 且 data owner / 人工复核证据已记录时，才可把该条人工标注作为真实 DOCX `TABLE_CELL` expected anchor 的来源。

## 约束

* 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
* 必须保持 `TASK-EVAL-001` active，不归档。
* 必须保持 `TASK-028` / `TASK-031` / `TASK-032` `NO-GO`。
* 人工 anchor 标准答案必须独立于 parser / AI / 被测系统 actual 输出。
* 不得使用 `CURRENT_CONTEXT.md` 自述替代人工标注证据、console 输出、commit 或后续测试证据。
* 不确定事项统一标记为“待确认”。

## 交付物

* `TASK-DATA-001` 任务文件。
* 人工 anchor 标注规则。
* 2-3 份候选样本选择记录。
* 人工标注输出字段模板。
* 后续 fixture / expected JSON / 测试转换边界说明。
* `CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-07.md` 项目记忆写回。

## 验收标准

1. 本任务明确 `TASK-DATA-001` 是 data / evaluation ground truth 准备任务，不是业务代码实现任务。
2. 本任务明确人工 anchor ground truth 不得由 parser / AI / 被测系统 actual 输出倒填。
3. 本任务至少选定 2 份候选 DOCX 样本，并覆盖工程采购与产品采购；如未最终确认样本可用性，必须标记“待确认”。
4. 本任务定义 `TABLE_CELL` 人工 anchor 的最小标注字段。
5. 本任务说明人工答案后续转换为 fixture / expected JSON / 测试必须另行冻结 `TASK_SPEC`。
6. 本任务不修改代码、测试、fixture、expected JSON、workflow、ADR、PRD、OpenAPI、Docker 或数据库迁移。
7. 本任务不归档 `TASK-EVAL-001`，不补足 DoD #12，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
8. `CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-07.md` 已记录任务建档和边界。
9. `git diff --check` 通过。
10. `git status --short` 只包含本任务允许的 XLSX、任务与项目记忆文档，以及已正式记录的用户侧 DOCX 003 修正例外。

## 测试与验证

本任务为文档建档和人工标注准备，不运行业务测试或完整 E2E。最低验证：

```powershell
git diff --check
git status --short
git diff --name-status
```

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是，记录 `TASK-DATA-001` 已建档及当前边界。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：是，记录任务地图层面的顺序和门禁。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要新增或更新 ADR：否。本任务不改变架构、审核链路、模型职责、EvidenceSlot、ReviewPointFamily 或 CandidateResolver。

## Next Task Handoff

本任务已完成 Codex 可执行的人工 anchor 准备：标注规则已冻结，3 份候选样本已选定，27 条审核点人工汇总已保留，63 条逐出处明细已按当前 DOCX 结构生成。

`ZK` 已完成“anchor明细待确认”工作表的逐条确认和最终接受，63 条均为 `MATCH`，6 条标题/前言名称已排除，来源和状态已更新为 `MANUAL_DOCX_REVIEW / ACCEPTED_HUMAN_GROUND_TRUTH`。

转换执行规格已冻结为 `tasks/active/TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md`。2026-07-13 第二修订版编码前规格映射计划已获 Codex `GO`；仅允许执行者在隔离 worktree 修改 §0.3 文件、运行两个定向测试并填写实现报告，不得 commit 或 push。

## 风险

* 若标注者参考 parser / AI 输出定义 ground truth，会形成循环验证。
* 若 data owner 未确认样本可用于项目验证，不得写成已确认事实。
* 若在本任务中顺手改 expected JSON 或测试，会越过数据准备与实现转换边界。

## 待确认

* 已确认：`CQCP-MVP-DOCX-001` 与 `CQCP-MVP-DOCX-002` 可用于项目验证；data owner / 确认人为 `zk`；脱敏合规已确认。
* 已确认：补选 `CQCP-MVP-DOCX-003` 作为第 3 份候选，其表格结构可补充 `TABLE_CELL` 人工定位；AI 预提取草稿不属于 ground truth。
* 已确认：人工标注输出使用 `outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx`。
* 已确认：`CQCP-MVP-DOCX-003` 样本可用、脱敏合规，data owner 为 `ZK`；三份合同的 63 条逐出处明细已由 `ZK` 接受为 `ACCEPTED_HUMAN_GROUND_TRUTH`。
* 已确认：后续由 `TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md` 承接 fixture / expected JSON / 测试转换；编码前计划已获 GO，仅授权 §0.3 实现并等待 Codex 后续审查。

## 完成记录

### 用户侧 DOCX 003 修正例外

* 文件：`packages/test-fixtures/docx/3、星辰建设集团南山科技园项目二标段土建总承包工程合同_缩减版.docx`。
* 责任边界：该文件由用户在人工核对过程中直接修正，Codex 未修改 DOCX；本任务仅审查并将用户修正纳入可重建基线。
* 相对提交前 `HEAD` 的三处可见文本变化：合同金额大写由 `捌仟捌佰肆拾捌元整` 改为 `壹万贰仟捌佰元整`；签约通知表由 `三标段` 改为 `二标段`；《阳光合作协议》前言由 `星辰建设集团南山科技园项目三标段` 改为 `星辰建设集团南山科技园项目二标段`。
* Word 保存同时改变 9 个 OOXML 部件：`docProps/app.xml`、`docProps/core.xml`、`word/document.xml`、`word/endnotes.xml`、`word/footer4.xml`、`word/footer5.xml`、`word/footnotes.xml`、`word/settings.xml`、`word/styles.xml`；除上述三处可见文本外，未发现其他可见文本变化。
* 当前文件 SHA-256：`48BB0FC63A0182BAFF5D7BD3E38F3A4409F68E7C371E5D24C02970C6362BF7B3`；提交前 `HEAD` blob：`de8984717a6fdb4385087542d5327c8da327e363`；当前 blob：`12d3909f82ca8aa10af866daee6cf5ac7f9de0a0`。
* 人工 XLSX SHA-256：`3DC0DABE37DF2556B5F48B68600A0E24F092373FBD96B1E65B696FF0B9AEBA0B`。该哈希用于锁定本轮 63 条已接受 ground truth 的源文件版本。

* 初始建档与规则冻结日期：2026-07-09；人工 ground truth 最终接受日期：2026-07-12。
* 变更文件：`outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx`、`packages/test-fixtures/docx/3、星辰建设集团南山科技园项目二标段土建总承包工程合同_缩减版.docx`（用户侧修正例外）、`tasks/active/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md`、`tasks/active/TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md`、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-07.md`。
* 验证结果：本任务仅运行文档边界验证命令；结果以交付摘要中的 `git status --short`、`git diff --name-status`、`git diff --stat`、`git diff --check` 为准。
* 补充执行日期：2026-07-12；`001` / `002` / `003` 共 63 条逐出处明细已由 `ZK` 接受为 `ACCEPTED_HUMAN_GROUND_TRUTH`；`004` 本轮不选。
* 补充执行日期：2026-07-12；`TASK_SPEC-DATA-001-A` 已创建并冻结；首次编码前基线核对为 `NO-GO`，计划请求未派发，不构成实现授权。
* 提交前审计日期：2026-07-13；独立只读审计首次与第二次均因状态记录问题给出 `NO-GO`，阻塞修正后最终结论为 `GO`，允许提交范围精确为本节列出的 7 个路径。
* 可重建基线：commit `a2e9c085cd4a073d7f9dcba55cf891ace3d556da`；隔离分支 / worktree：`codex/task-data-001-a-human-anchor-conversion` / `C:\Users\1\Documents\CQCP-worktrees\task-data-001-a`。
* 编码前计划审查：2026-07-13 已收到 Claude Code / DeepSeek §0.2 计划；Codex Decision 为 `NO-GO / CODING-PLAN REVISION REQUIRED / NO IMPLEMENTATION AUTHORIZATION`。需按 `TASK_SPEC-DATA-001-A` §10.1 六项要求修订后重审。
* 修订版计划复审：前述六项已响应，但仍需区分 16 个 XLSX 直接字段与 2 个 notes 派生字段，并禁止把直接字段空字符串转为 `null`；Decision 为 `NO-GO / CODING-PLAN REVISION ROUND 2 REQUIRED / NO IMPLEMENTATION AUTHORIZATION`。
* 第二修订版计划复审：前两轮八项要求均已覆盖；Codex 只读确认 `occurrenceNo` 为 `001-PA-01` 等字符串追溯 ID，并冻结为 JSON string。Decision 为 `GO / CODING-PLAN ACCEPTED WITH BINDING CLARIFICATION / IMPLEMENTATION AUTHORIZED WITHIN §0.3 / NO COMMIT OR PUSH`。
* 首轮实现 Review Intake：8 个实现路径均在允许范围，当前 fixture / expected 内容与人工 XLSX 一致，两个定向测试通过，独立 agent 对范围、独立性和 baseline 给出 `GO`；但新测试未有效锁定 JSON 类型、occurrence 顺序、全部 required columns、精确 18-key schema 和完整 positive-case baseline。Codex Decision 为 `NO-GO / TEST CONTRACT REVISION REQUIRED / DATA ARTIFACTS RETAINED / NO COMMIT OR PUSH`。
* test-only revision 再审：四项 blocking findings 均已解除；Codex 重跑定向测试为 10/10 与 4/4，独立 agent delta 审计 `GO`，无 blocking finding。Codex Decision 为 `GO / IMPLEMENTATION ACCEPTED / AWAITING CODEX COMMIT AUTHORIZATION / NO PUSH`。
* 提交、push 与 PR：用户明确授权后，Codex 精确提交 8 个实现路径为 `32b4c414349118b9225a65fa08eb4e3466f82a2e`（`test: add accepted human anchor fixtures`），并 push 到 `origin/codex/task-data-001-a-human-anchor-conversion`；PR #28 三项 checks 已通过，未获合并授权。
* 备注：Codex 本任务不修改代码、测试、DOCX 样本、expected JSON、`cqcp-mvp-sample-matrix.xlsx`、parser、`CandidateResolver`、`EvidenceSlot`、`SourceAnchor` 或 Review Engine；用户侧 DOCX 003 修正按上述例外保留。不运行完整 MVP E2E；不归档 `TASK-EVAL-001`，不补足 DoD #12，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
