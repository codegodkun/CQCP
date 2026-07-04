# TASK-EVAL-001：Parser-backed 证据重合度评测基线

状态：REBASELINED / Active / 不归档 / 不进入 TASK-028

类型：A 类质量评测父任务 / Codex 主控

优先级：P0

负责人：Codex

创建日期：2026-06-20

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`PRD.md`、`docs/ARCHITECTURE.md`、`decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`、`decisions/ADR-015-evidence-slot-source-anchor-governance.md`

## 背景

`TASK-025` 已建立 parser-backed fixture 级主链路验收，`TASK-026` 已完成最小 `CandidateResolver` 五档置信度治理，`TASK-027` 已完成 ADR-015 边界内的最小 `EvidenceSlot / SourceAnchor` 主实现。

当前回归能够验证 `PointStatus`、`candidateValue`、单个 `blockId` 和 `evidenceSummary`，但尚未形成可比较的证据定位质量基线。若缺少 block / table-row / cell level overlap 指标，后续 parser、候选召回、归属规则或模型辅助变化即使让最终状态保持不变，也无法判断证据定位是否真实提升或退化。

本任务建立最小离线评测基线，只评价证据定位与候选归属质量，不改变生产审核语义。

## Review Intake Decision

2026-06-20 实现前 Review Intake Decision：`NEEDS-SPLIT`。

已确认：

* block-level overlap 已具备实现条件。
* parser 内部已有部分 `tableId + rowIndex` 信息，table row 具备潜在实现条件。
* 当前 `PointEvidence / SourceAnchorSummary / ResultComposer / TaskResultQuery / PersistentTaskResultStore` 的稳定可见契约仍主要只有 block-level anchor。
* 当前 cell anchor 缺少稳定 `cellIndex`，不得通过 `candidateValue` 搜索 cells 伪造来源归属。
* 在 row/cell anchor 无法通过真实结果链路稳定观察前，不得直接进入本父任务的完整 overlap evaluator 实现。

拆分决定：

1. 先执行 `TASK-EVAL-001-A`：SourceAnchor row/cell observability 前置任务。
2. `TASK-EVAL-001-A` 完成并经 Codex 验收后，再启动 `TASK-EVAL-001-B`：evidence overlap baseline。
3. Git 历史显示 `TASK-EVAL-001-B` 后续对应 commit 为 `672d97f695756249a871da53ad2821eb5146997f`；据用户提供的外部报告摘要，提交前独立复核流程曾缺失，后续形成了事后独立只读复核和定向测试复跑报告，原始凭证待父任务归档前核验。
4. 本父任务原 DoD 不降级，仍要求 block / table-row / cell、4 正向 + 4 负向/冲突及完整 overlap 指标。

### Rebaseline 前收口判断

2026-07-04 文档治理收口判断曾为：`NO-GO TO ARCHIVE / 继续暂停 / 需重新定界`。该判断是触发后续 rebaseline 的前置状态，不再作为当前最终状态文字。

本判断仅固化当前项目状态，不新增业务代码，不修改测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。

判断依据：

* 旧 `GO TO ARCHIVE WITH CONDITIONS` 口径已按用户要求回滚，不再作为当前归档依据。
* DoD #12 仍未通过、未补足；`TASK-EVAL-001-A` 与 `TASK-EVAL-001-B` 的历史 commit / push 授权记录无法完整核实。
* 事后独立复核、定向测试 `30/30 PASS` 与已 push 内容不被本判断追溯否定，但这些补偿证据不能替代提交前门禁，也不能支撑父任务归档。
* Step 2 原始逐条认领报告未入库；此前“重新覆盖审计”未找到可追溯原始报告记录，不能支撑归档判断。

后续要求：

* `TASK-EVAL-001` 保持 active，不移动到 `tasks/done/`。
* 不得恢复“条件归档”或其他绕过 DoD #12 的归档口径。
* 如需继续推进，必须重新定界：明确新的任务边界、验收断言、证据来源、授权记录要求和归档门禁。该要求已在下方 `Rebaseline Decision` 中处理。
* 不自动解除 `TASK-028`、`TASK-031` 或 `TASK-032` 门禁，不进入 Step 3，不起草、冻结或派发 `TASK_SPEC`。

### 重新定界 Review Intake Decision

2026-07-04 Codex Review Intake Decision：`NO-GO TO ARCHIVE / SPLIT GOVERNANCE DEBT`。

本轮按 GOAL-2 证据恢复结果进入“证据不可恢复 / 不可完整核实”分支：`TASK-EVAL-001-A` 与 `TASK-EVAL-001-B` 的历史 commit / push 授权链无法恢复到可完整核实状态，不能作为父任务归档 DoD #12 的通过证据。

DoD 支撑边界：

* DoD #1 至 #11：仍可沿用既有独立审计记录作为“已独立确认”的子断言摘要，但这些断言不足以单独支撑父任务归档。
* DoD #12：不可支撑；历史授权链缺口未通过、未补足，且当前没有可恢复的完整原始授权证据。
* 事后独立复核、定向测试 `30/30 PASS` 与已 merge / push 内容：不被本判断追溯否定，但只可作为补偿性质量证据，不可替代提交前授权门禁。

处理决定：

1. `TASK-EVAL-001` 保持 active / 继续暂停，不归档。
2. 不恢复旧 `GO TO ARCHIVE WITH CONDITIONS` 口径，也不创建新的条件归档例外。
3. 将 A/B 历史授权链不可完整核实问题从本父任务拆出为治理债务任务：`tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`。
4. `TASK-GOV-005` 只记录和治理历史授权证据债务，不写业务代码，不追溯否定已 merge 内容，但明确阻止其成为后续绕过 commit / push 明确授权门禁的先例。
5. `TASK-028`、`TASK-031`、`TASK-032` 继续禁止抢跑；如需重新推进 `TASK-EVAL-001`，必须先重新冻结任务边界、验收断言、证据来源和归档门禁。

### GOAL-5 / GOAL-6 / GOAL-7 只读复判

2026-07-04 Codex 只读复判结论：

* GOAL-5：`TASK-EVAL-001` 最终处理方式为 `ARCHIVE BLOCKED`，继续 active / paused。当前不采用 `ARCHIVE WITH EXPLICIT DEBT SPLIT`，因为 `TASK-GOV-005` 只拆出了历史授权链治理债务，不等于用户接受父任务归档例外；如后续仍要推进父任务，必须按 `REBASELINE REQUIRED` 重新定义父任务边界、DoD、证据来源和归档门禁。该要求已在本文件 `Rebaseline Decision` 中执行。
* GOAL-6：`TASK-028` Readiness Gate 为 `NO-GO`。`TASK-EVAL-001` 当前收口结果稳定为 blocked，但这不是可进入 `TASK-028` 的完成态；本轮仍不是实现授权，也不是 `TASK_SPEC` 派发授权。
* GOAL-7：`DEBT-001-03` parser provenance / `SourceAnchor` 与 `DEBT-001-05` real DOCX `TABLE_CELL` 分别保留为未授权债务。前者必须单独定界 provenance 数据契约与真实 parser block / candidate 透传边界；后者必须先取得独立人工 anchor 标注，不得由 parser 输出倒填 expected。

### Rebaseline Decision

2026-07-04 Codex 按用户要求执行 `REBASELINE REQUIRED`，结论为：`TASK-EVAL-001` 不再按原父任务 DoD 伪装为 12/12 全通过，也不恢复旧 `GO TO ARCHIVE WITH CONDITIONS` 口径；本任务进入 rebaseline 后的 active 状态。

本次 rebaseline 只重定界父任务文档、DoD 和归档门禁，不写业务代码，不修改测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、PRD、架构文档或 ADR。

#### Rebaseline 后任务定位

`TASK-EVAL-001` 从“准备按原 DoD 归档的 evidence overlap baseline 父任务”调整为：

* 记录 parser-backed evidence overlap baseline 的历史实现结果和可复用质量信号。
* 明确原 DoD #12 未通过、未补足，且该缺口不能由事后复核或治理债务拆出替代。
* 将 A/B 历史 commit / push 授权链不可完整核实问题保留为 `TASK-GOV-005` 治理债务。
* 保留真实 DOCX positive baseline `TABLE_CELL` 覆盖不足为后续 `DEBT-001-05` / 人工 anchor 标注前置问题。
* 在未满足 rebaseline 后归档门禁前，不归档本父任务，不解除 `TASK-028` / `TASK-031` / `TASK-032` 门禁。

#### Rebaseline 后 DoD

本父任务后续不得再声明原 DoD 全量通过。rebaseline 后仅允许使用以下 DoD 作为后续收口判断依据：

1. 原 DoD #1 至 #11 可保留为“既有独立确认摘要”，但必须标注其证据来源为历史审计 / 复核摘要，不得替代原始 console、commit、PR 或独立审计报告。
2. 原 DoD #12 固定为未通过、未补足；该项不再作为可被事后补写文档修复的完成项。
3. `TASK-EVAL-001-A` / `TASK-EVAL-001-B` 的实现结果可作为历史技术质量信号和后续任务参考，不作为父任务归档通过证据。
4. `TASK-GOV-005` 只治理历史授权证据债务，不补足本父任务 DoD #12，不授权业务实现，不覆盖 `DEBT-001-03` 或 `DEBT-001-05`。
5. 真实 DOCX positive baseline `TABLE_CELL` 覆盖仍不得宣称已验证；如后续需要解决，必须先取得独立人工 anchor 标注并另行定界。
6. 本父任务后续若申请归档，必须先经过独立 agent 只读审计，并由 Codex 单独给出 Review Intake Decision；不得把本 rebaseline 文档本身当作归档证据。
7. 任何后续 commit / push 仍需单独取得用户明确授权；历史授权链缺口不得成为例外先例。

#### Rebaseline 后归档门禁

本任务当前不归档。后续只有同时满足以下条件，才允许重新进入归档判断：

* 用户明确要求对 rebaseline 后的 `TASK-EVAL-001` 发起归档判断。
* 独立 agent 对 rebaseline 后任务文件、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`TASK-GOV-005` 和相关历史证据做只读审计。
* Codex 单独 Review Intake 确认：归档判断没有恢复旧 DoD、没有把 DoD #12 写成通过、没有把 `TASK-GOV-005` 当作父任务完成证据。
* 项目记忆明确保留 `TASK-028` / `TASK-031` / `TASK-032` 门禁，除非另有独立任务解除。

#### 2026-07-04 归档前审计启动前收口判断

Codex Review Intake Decision：

```text
NO-GO TO ARCHIVE / INDEPENDENT AUDIT REQUIRED / BASELINE NOT CLEAN
```

触发原因：

* 用户已明确要求先处理 `TASK-EVAL-001` 的归档前审计 / 收口判断，满足“发起归档判断”的人工触发条件。
* 归档门禁仍要求独立 agent 对 rebaseline 后任务文件、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`TASK-GOV-005` 和相关历史证据做只读审计；本轮 Codex 只做预审与收口判断，不能替代独立审计。
* 当前主工作区不是干净审计基线，`git status --short` 显示仍存在已修改文件和未跟踪治理文件；这些差异需分批处理，不能作为干净归档审计基线。
* `origin/master...HEAD` 为 `0 0`，本地分支与远端分支提交指针对齐；上述差异属于未提交工作区状态，不得被当作已入库归档证据。
* `TASK-GOV-005` 只能作为历史授权证据债务记录，不能补足本任务 DoD #12，也不能支撑本父任务归档。

收口判断：

* 当前不得归档 `TASK-EVAL-001`。
* 当前不得恢复 `GO TO ARCHIVE WITH CONDITIONS` 或创建新的条件归档例外。
* 当前不得把 `TASK-GOV-005`、事后独立复核或 `30/30 PASS` 写成本父任务 DoD #12 已通过。
* 当前不得进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 若后续继续申请归档，必须先由独立 agent 在明确的只读审计基线上完成归档前审计；审计报告返回后，Codex 再单独给出 Review Intake Decision。

建议独立只读审计输入：

```text
26/07/04 23:38:20
本窗口是 TASK-EVAL-001 归档前独立只读审计窗口，不依赖历史聊天记录作为事实来源。若聊天内容与仓库文件冲突，以仓库文件为准。

请在只读模式下审计 CQCP 仓库的 TASK-EVAL-001 是否可进入归档判断。不得修改文件、不得 stage、不得 commit、不得 push、不得 merge、不得配置 GitHub。

开始前读取：
- AGENTS.md
- CURRENT_CONTEXT.md
- tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md
- tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md
- tasks/MVP_TASK_MAP.md
- docs/context-management.md
- docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md

必须执行并报告原始输出摘要：
- git status --short
- git status -sb
- git rev-list --left-right --count origin/master...HEAD
- git log --oneline -8
- git diff --name-status

审计问题：
1. rebaseline 后的 TASK-EVAL-001 是否仍明确 DoD #12 未通过、未补足？
2. 是否存在把 TASK-GOV-005、事后独立复核或 30/30 PASS 当作 DoD #12 通过证据的表述？
3. 是否存在恢复 GO TO ARCHIVE WITH CONDITIONS 或条件归档例外的表述？
4. CURRENT_CONTEXT.md、tasks/MVP_TASK_MAP.md、TASK-EVAL-001、TASK-GOV-005 是否一致保留 TASK-028 / TASK-031 / TASK-032 门禁？
5. 当前工作区是否适合作为归档判断基线？如不适合，请说明具体文件和原因。
6. 是否发现任何会阻止 TASK-EVAL-001 归档判断的缺失证据、循环验证、未记录债务或完成态夸大？

输出格式：
- Decision: GO / NEEDS-FIX / NO-GO
- Blocking findings:
- Non-blocking findings:
- Evidence reviewed:
- Commands run:
- Final recommendation:
```

#### Rebaseline 后禁止事项

* 不恢复 `GO TO ARCHIVE WITH CONDITIONS`。
* 不创建“显式债务拆分即可归档”的新例外。
* 不把 `TASK-GOV-005`、事后独立复核或 `30/30 PASS` 写成本父任务原 DoD #12 已通过。
* 不进入 `TASK-028`、`TASK-031`、`TASK-032`。
* 不起草、冻结或派发 `TASK_SPEC`。
* 不修改业务代码、测试、fixture、expected JSON 或生产链路。

### 归档前 Review Intake Decision

2026-06-23 父任务归档前 Review Intake Decision：`GO TO ARCHIVE WITH CONDITIONS`。
【2026-06-XX 回滚批注】以上为历史记录，该决定已按用户要求回滚，不再作为当前归档状态依据。

该决定仅允许在 `TASK-EVAL-001` 父任务 DoD 范围内带治理债务条件归档，不代表 12/12 DoD 全部通过：
【2026-06-XX 回滚批注】以上为历史记录，该决定已按用户要求回滚，不再作为当前归档状态依据。

* DoD #1 至 #11 已由独立 agent 在 `origin/master` clean clone 上重新核验。
* DoD #12 未通过、未补足：`TASK-EVAL-001-A` commit `4bac2f438389f83e5ec6338558aaee94a6fe4464` 与 `TASK-EVAL-001-B` commit `672d97f695756249a871da53ad2821eb5146997f` 的历史 commit / push 授权记录无法完整核实。
* 该缺口作为历史流程治理债务永久保留，不追溯否定已 push 内容、事后独立审计结论或定向测试 `30/30 PASS`。
* 该例外不得成为后续绕过 commit / push 明确授权门禁的先例；本归档 diff 的 commit 与 push 必须分别重新取得用户明确授权。
* Step 2 原始逐条认领报告未入库，作为治理债务保留；父任务归档判断依据为归档前独立审计对本父任务相关关键断言的重新覆盖，不得表述为原始 Step 2 报告已入库。
【回滚批注】经独立核实，上述“重新覆盖审计”未找到可追溯的原始报告记录，该依据不能支撑归档判断。
* 在 Step 2 原始逐条认领报告真正产出、DoD #12 真正解决之前，不得再次进入任何形式的归档状态，包括“条件归档”这类自定义状态。
* 提交前独立复核曾缺失；事后复核与独立重跑只作为补偿证据，不能追溯性等同于提交前复核。

## 目标

* 建立 parser-backed fixtures 的 block / table-row / cell level evidence overlap 最低评测基线。
* 对 expected anchor 与实际 `SourceAnchor` 做可重复的 recall、precision、required hit 和失败归因计算。
* 阻止“`candidateValue` 正确但证据定位错误”被误判为评测通过。
* 为后续 parser、候选召回、`CandidateResolver`、模型辅助或检索方案比较提供统一基线。

## 非目标

* 不改变生产审核语义。
* 不改变 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED` 状态定义。
* 不改变 `SYS-*` 与业务 Finding 分流。
* 不改变 `EvidenceSlot` admission 规则。
* 不改变 `CandidateResolver` 五档定义或 `HIGH` admission gate。
* 不进入字符级 span 强制评分。
* 不进入 `TASK-028`、`TASK-031` 或 `TASK-032`。
* 不引入新模型，不比较 Gemma / Qwen。
* 不引入 BM25、Docling、Ragas、Outlines、OpenTelemetry 或 Label Studio。
* 不替换 parser。
* 不修改 `PRD.md` 或 `docs/ARCHITECTURE.md`。
* 不派发 Claude Code / DeepSeek。

## 输入

* 相关文档：
  * `PRD.md`
  * `docs/ARCHITECTURE.md`
  * `CURRENT_CONTEXT.md`
  * `tasks/MVP_TASK_MAP.md`
* 相关 ADR：
  * `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
  * `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* 上游任务：
  * `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
  * `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
  * `tasks/done/TASK-027-evidence-slot-source-anchor-governance.md`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/MVP_TASK_MAP.md`
* `PRD.md` 中 `Candidate Index And Resolver`、`EvidenceSlot And EvidencePacket`、`Result Composition`、`Result Page`、MVP 验收相关章节
* `docs/ARCHITECTURE.md` 中 `Evidence ranking`、`CandidateIndex 与 CandidateResolver 边界`、`EvidenceSlot Preflight`、`SourceAnchor`、质量评测与治理相关章节
* `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
* `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
* `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
* `tasks/done/TASK-027-evidence-slot-source-anchor-governance.md`

### Optional Context

* `docs/backend.md`
* `docs/ai-review.md`
* `packages/test-fixtures/README.md`
* `changelog/2026-06.md`

### Out of Scope

* 生产代码、API、数据库迁移、OpenAPI、前端、Docker、Compose 或依赖修改。
* 新审核点、新合同类型、新 `CandidateRole`、新 `ReviewPointFamily`。
* `EvidenceBundle` 平台化、模型接入、检索方案引入或 parser 替换。
* 业务 Finding 正确率、最终状态准确率或模型能力排名。

## 范围

### 包含

* 为现有 parser-backed fixture 定义 expected evidence anchors。
* 实现 test-only overlap 计算与断言。
* 覆盖 block 与 table row/cell 两类 anchor。
* 覆盖至少 4 个正向 fixture 和 4 个负向 / 冲突 fixture。
* 输出可定位的失败原因，不只输出布尔结果。

### 不包含

* 修改主代码以迎合评测。
* 修改 fixture 合同正文来提高分数。
* 用最终 `PointStatus` 代替 anchor overlap。
* 用 `candidateValue` 相同代替来源归属正确。
* 把评测结果直接作为生产规则发布或自动优化依据。

## 授权文件范围

实现阶段只允许修改以下范围：

* `packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json`
* `packages/test-fixtures/expected/CQCP-MVP-DOCX-002.json`
* `packages/test-fixtures/expected/CQCP-MVP-DOCX-003.json`
* `packages/test-fixtures/expected/CQCP-MVP-DOCX-004.json`
* `packages/test-fixtures/README.md`
* `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`
* `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/` 下新增的 test-only evaluation helper 或测试文件
* 本任务文件、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-06.md`

明确禁止：

* `apps/api-server/src/main/**`
* `packages/test-fixtures/docx/**`
* `PRD.md`
* `docs/ARCHITECTURE.md`
* `decisions/**`

如果现有测试可见输出无法表达 table row/cell anchor，必须停止并回交 Codex 重新定界，不得直接修改生产模型或 parser。

## 最低评测模型

### Anchor 标识

评测必须使用稳定、可比较的 canonical anchor key：

```text
BLOCK:<blockId>
TABLE_ROW:<blockId>:<rowIndex>
TABLE_CELL:<blockId>:<rowIndex>:<cellIndex>
```

block 级最低字段：

```text
expectedBlockIds
actualSourceAnchor.blockIds
```

table row/cell 级 expected 数据可使用与当前 fixture schema 兼容的附加字段，但最终必须转换为 canonical anchor key 后参与相同 overlap 计算。

### 最低公式

对单个 fixture + review point：

```text
expected = expected canonical anchor keys
actual = actual SourceAnchor canonical anchor keys
matched = expected ∩ actual

expectedRecall = |matched| / |expected|
actualPrecision = |matched| / |actual|
missingExpectedBlocks = expected - actual
unexpectedMatchedBlocks = actual - expected
requiredHit = 1 when missingExpectedBlocks is empty, otherwise 0
```

集合为空时：

* 正向 case 的 `expected` 不允许为空。
* 正向 case 的 `actual` 为空时，`expectedRecall=0`、`actualPrecision=0`。
* 负向 case 不以空 expected 集合计算正向 recall/precision，而是验证实际结果未错误命中禁止 anchor、未错误通过 admission，并记录失败归因。

跨基线集合：

```text
requiredHitRate = Σ requiredHit / positiveEvaluationCaseCount
```

最低通过门槛：

* 正向 case：`expectedRecall = 1.0`、`actualPrecision = 1.0`、`requiredHit = 1`。
* 基线集合：`requiredHitRate = 1.0`。
* 若确有允许的补充 anchor，必须在 expected 数据中显式声明，不能在计算时静默忽略。
* `candidateValue` 正确但 canonical anchor 不匹配时，评测必须失败。

### 失败归因

每个失败 case 必须提供非空 `attributionFailureReason`，最低枚举：

```text
EXPECTED_ANCHOR_MISSING
UNEXPECTED_ANCHOR_SELECTED
WRONG_BLOCK_ATTRIBUTION
WRONG_TABLE_ROW_ATTRIBUTION
WRONG_TABLE_CELL_ATTRIBUTION
CONFLICTING_CANDIDATE_ADMITTED
ATTRIBUTION_AMBIGUOUS
SOURCE_ANCHOR_UNAVAILABLE
```

## Fixture 矩阵

### 正向

至少 4 个正向 fixture，优先复用：

* `CQCP-MVP-DOCX-001`
* `CQCP-MVP-DOCX-002`
* `CQCP-MVP-DOCX-003`
* `CQCP-MVP-DOCX-004`

正向 case 必须验证：

* expected required evidence anchor 被命中。
* 实际 anchor 未吸附到无关 block / row / cell。
* `candidateValue` 与来源归属同时正确。
* 至少一个 case 覆盖 block anchor。
* 至少一个 case 覆盖 table row/cell anchor。

### 负向 / 冲突

至少 4 个负向 / 冲突 case，必须覆盖：

* 错误 block。
* 无关 block。
* 冲突候选。
* 候选值可匹配但归属不清。

负向 case 必须验证：

* 禁止 anchor 不会被判为通过。
* `CONFLICTED / MEDIUM / LOW / UNKNOWN` 不会因值相同被当作正确归属。
* 错误 row/cell 不会因同表或同 block 被宽松判为通过。
* 失败时生成明确 `attributionFailureReason`。

## 约束

* 评测只验证证据定位质量，不改变业务 Finding 语义。
* 生产状态、admission、resolver 和 result composition 行为必须保持不变。
* 不得通过修改 DOCX fixture 正文来让评测通过。
* 不得把字符级 span 设为 MVP 强制门槛。
* 不得静默忽略 unexpected anchor。
* 不得把 table row/cell 退化为“同一 table block 即通过”。
* 后续任何模型、检索或 parser 优化必须使用同一基线比较前后结果，且不得只报告最终状态变化。

## 交付物

* expected fixture 中的 anchor expectation 定义。
* test-only evidence overlap evaluator。
* block 与 table row/cell overlap 自动化测试。
* 至少 4 正 + 4 负/冲突 case 的评测结果。
* 本任务文件完成记录与项目记忆写回。

## 验收标准

1. 父任务文档明确目标、非目标、授权文件范围和验收标准。
2. 已实现并验证 `expectedBlockIds`、`actualSourceAnchor.blockIds`、`expectedRecall`、`actualPrecision`、`requiredHitRate`、`missingExpectedBlocks`、`unexpectedMatchedBlocks`、`attributionFailureReason`。
3. 至少覆盖 4 个正向 fixture 和 4 个负向 / 冲突 fixture。
4. 正向 fixture 的 expected required evidence anchor 全部被命中。
5. `candidateValue` 正确但 `SourceAnchor` 错误时评测失败。
6. 错误 block、无关 block、冲突候选和归属不清不会被判为通过。
7. 自动化测试覆盖 block 与 table row/cell 两类 anchor。
8. 文档明确评测只验证证据定位质量，不改变业务 Finding 语义。
9. 生产代码、测试外依赖、DOCX fixture、PRD 和架构文档保持不变。
10. 在 Docker Compose 标准环境或项目认可的既有测试方式下完成验证。
11. 阶段结束时 `git status --short` 必须干净。
12. commit / push 必须单独取得用户确认。

## 测试与验证

实现阶段必须执行：

* overlap evaluator 定向测试。
* `ParserBackedReviewInputPreparerEvidenceTest` 回归。
* `TaskExecutionStateMachineTest` 回归，确认最终业务状态语义未变化。
* `git diff --check`。
* `git status --short`。

测试命令以仓库届时认可的 Gradle / Docker Compose 标准方式为准；若标准命令仍受既有 PostgreSQL host 问题阻塞，必须区分目标测试结果与既有环境阻塞，不得伪报全量通过。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：是，记录当前优先级与 `TASK-028` 依赖。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要更新 `PRD.md`：否。
* 是否需要更新 `docs/ARCHITECTURE.md`：否。
* 是否需要新增或更新 ADR：否；若实现要求改变 `EvidenceSlot`、`CandidateResolver`、`SourceAnchor` 或生产审核链路，必须停止并另行提出 ADR。

## Next Task Handoff

* `TASK-EVAL-001-A` 已完成并 push，提交为 `4bac2f4`。
* Git 历史显示 `TASK-EVAL-001-B` 对应 commit 为 `672d97f695756249a871da53ad2821eb5146997f`；据用户提供的独立 agent 事后复核报告摘要，提交前独立复核流程曾缺失，复核建议为 `ACCEPT WITH CONDITIONS`。
* 据用户提供的独立 agent 定向测试复跑报告摘要，基线为 `CQCP_AUDIT` clean clone、HEAD `829796f2a18a87f1155eea96ed991a5fd0748b99`，四组定向测试合计 `30/30 PASS`，无 failure、error 或 skipped，测试前后工作区干净。凭证应以独立 agent 原始报告和 console 输出为准，本任务文件仅记录摘要，不作为完成凭证。
* B 已被接纳为 `ACCEPT WITH CONDITIONS — TEST EVIDENCE SATISFIED`；父任务归档前独立审计随后给出 `GO WITH CONDITIONS`，Codex Review Intake Decision 为 `GO TO ARCHIVE WITH CONDITIONS`。
【2026-06-XX 回滚批注】以上为历史记录，该决定已按用户要求回滚，不再作为当前归档状态依据。
* 父任务条件归档不自动解除 `TASK-028`、`TASK-031` 或 `TASK-032` 门禁。
【回滚批注】当前状态已变更为暂停归档，但上述门禁仍保持不解除。
* 本任务未派发 Claude Code / DeepSeek。

## 评测结果解释边界

* `TASK-EVAL-001-B` 报告的 `expectedRecall=1.0`、`actualPrecision=1.0`、`requiredHit=1` 和集合 `requiredHitRate=1.0` 是 evaluator 基于当前 expected / actual canonical anchor 集合得出的真实计算结果。
* expected anchor 中的 blockId、rowIndex 和 cellIndex 使用 parser 内部稳定标识，candidateValue 来源于独立登记的 matrix；当前结果只证明 parser-backed 输出与 expected JSON 的一致性和回归稳定性。
* 上述 1.0 / 1.0 / 1 不单独证明 parser anchor 位置客观正确，不得表述为独立人工标注准确率。
* evaluator 支持 TABLE_CELL canonical key，test-only / mock 覆盖已存在；当前四份真实主 DOCX 覆盖 BLOCK 与 TABLE_ROW，真实 DOCX positive baseline TABLE_CELL 覆盖仍未完成。
* 按父任务原 DoD 文本，自动化测试支持 TABLE_CELL canonical key 曾被视为满足 cell 覆盖要求；rebaseline 后不得再用该解释支撑父任务归档。真实 DOCX positive baseline `TABLE_CELL` 覆盖仍为后续人工 anchor 标注前置问题。
* 不得宣称真实 DOCX TABLE_CELL 已验证。该缺口继续由 `TASK-DEBT-001` 和后续人工 anchor 标注任务追踪，并防止 parser 输出倒填 expected。
* B 的事后条件接纳、定向测试复跑和父任务归档前独立审计只构成补偿性质量证据；这些补偿不能追溯性等同于提交前独立复核，也不能支撑 rebaseline 后父任务归档。
* 五条已确认问题的标准记录见 `tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`。

## 风险

* expected canonical key 绑定当前 parser 的稳定 block 顺序；后续 parser 结构变化若改变 blockId，必须通过基线评审更新，不得运行时自动重写 expected。
* 四份主 DOCX 当前覆盖 BLOCK 与 TABLE_ROW；TABLE_CELL 由 test-only parser-backed case 覆盖。后续如要求真实主 fixture 覆盖 cell，必须单独授权新增样本，不得修改现有 DOCX 迎合评测。
* expected anchor 标注若不完整，会把合法补充证据误报为 precision 下降；因此所有允许 anchor 必须显式登记，不得静默忽略 unexpected anchor。
* 基线规模较小，只用于回归门禁，不得宣称代表真实合同总体质量。

## 待确认

* 已确认：现有 parser 可在不改变 ADR-015 语义的前提下，通过真实 cell ordinal 与 joined-text range 稳定表达 cell anchor。
* 已确认：跨 cell 或无法唯一映射的 matcher 命中降级为 row anchor，不降低父任务 cell DoD。

## 完成记录

* 完成日期：2026-06-21。
* 变更文件：四份 `packages/test-fixtures/expected/*.json`、`packages/test-fixtures/README.md`、三个 test-only evaluator / baseline 文件及项目记忆文件。
* 测试结果：
  * 4 个真实正向 fixture：`expectedRecall=1.0`、`actualPrecision=1.0`、`requiredHit=1`
  * 集合 `requiredHitRate=1.0`
  * BLOCK / TABLE_ROW / TABLE_CELL canonical key 均有自动化覆盖
  * 真实 `CONFLICTED / MEDIUM / LOW` 与注入 wrong block / row / cell / unexpected / unavailable anchor 均不会误判通过
  * `ParserBackedReviewInputPreparerEvidenceTest` 与 `TaskExecutionStateMachineTest` 回归通过
* 事后复核摘要：据用户提供的独立 agent 报告，commit `672d97f` 的复核建议为 `ACCEPT WITH CONDITIONS`、定向复跑为 `30/30 PASS`；据 Codex Review Intake 摘要，接纳判断为 `ACCEPT WITH CONDITIONS — TEST EVIDENCE SATISFIED`。这些摘要不替代原始报告和 console 输出。
* 父任务归档前独立审计：
  * 审计基线为 GitHub `origin/master` clean clone，`HEAD = origin/master = 719699d`，工作区干净。
  * 12 条 DoD 中 #1 至 #11 已确认；DoD #12 未通过、未补足，A/B 历史 commit / push 授权记录无法完整核实。
  * 独立 agent 重新运行四组定向测试并捕获 JUnit XML，合计 `30/30 PASS`。
* 遗留问题：
  * A/B 历史 commit / push 授权记录无法完整核实，作为历史流程治理债务永久保留。
  * Step 2 原始逐条认领报告未入库。
  * 提交前独立复核曾缺失，事后复核与独立重跑不能追溯性等同于提交前复核。
  * 真实 DOCX positive baseline TABLE_CELL 覆盖仍为 0，由 `TASK-DEBT-001` 或后续人工 anchor 标注任务追踪。
* 备注：未修改生产代码、DOCX fixture、OpenAPI、数据库、Docker/Compose、前端、PRD、架构文档或 ADR；未改变 Finding、EvidenceSlot admission、CandidateResolver gate 或业务状态语义。

## Rebaseline 后持续边界

本次状态表示 `TASK-EVAL-001` 当前为 `REBASELINED / Active / 不归档 / 不进入 TASK-028`。本任务不再等待通过补写文档恢复原 DoD，也不把 Step 2 原始逐条认领报告或 DoD #12 作为可由本轮补足的完成项：

* 不代表五类问题整改已完成。
* 不代表角色分离机制已完全恢复。
* 不代表真实 DOCX `TABLE_CELL` 已验证。
* 不代表 expected anchor 已具备独立人工 ground truth 正确性。
* expected anchor 仍依赖 parser 内部 `blockId / rowIndex / cellIndex`，当前结果只证明 expected 与 parser-backed 输出的一致性和回归稳定性。
* 不自动解除 `TASK-028`、`TASK-031` 或 `TASK-032` 门禁。
* 不进入 Step 3，不起草、冻结或派发 `TASK_SPEC`。
* 后续任何归档、commit 或 push 均必须重新取得用户明确授权。

## 后续治理缺口

* 当前 v3 门禁仍依赖文档规则、Codex 遵守、用户判断和独立 agent 审计，尚未通过 GitHub branch protection / required status checks 形成机制化硬门禁，当前门禁不具备 GitHub 机制强制能力。
* 后续建议单独建立 `TASK-GOV-004`，评估 CI、Code Review Agent、Spec & Docs Review Agent required checks；review agent 判决以 GitHub Check Run 或 Commit Status 发布；required checks 指定可信 GitHub App / source；default branch 未满足 required checks 时禁止 merge；管理员 bypass 关闭或单独审计。
* `TASK-GOV-004` 尚未创建、未 active、未批准、未实施，required checks 未配置、branch protection 未生效；该机制化治理不纳入本次 `TASK-EVAL-001-B` 文档修正范围。本轮不修改 CI、GitHub Actions、branch protection 或仓库设置。

## 当前持续门禁

* 不进入 `TASK-028`、`TASK-031` 或 `TASK-032`。
* 不进入 Step 3，不起草或派发 `resolveTextEvidence` TASK_SPEC。
* 不提交新的 `TASK-EVAL-001-B` 代码、测试、fixture 或 expected JSON 变更。
* 本次归档文档 diff 不得在未经用户分别明确授权的情况下 commit 或 push。
