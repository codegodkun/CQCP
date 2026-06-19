# TASK-025：Parser / Candidate / Evidence 主链路接入

状态：已完成并归档
类型：A 类主链路父任务 / 后端实现
优先级：P0
负责人：Codex
创建日期：2026-06-17
最近更新：2026-06-19

来源：
- `CURRENT_CONTEXT.md`
- `tasks/MVP_TASK_MAP.md`
- `docs/ARCHITECTURE.md`
- `docs/backend.md`
- `docs/ai-review.md`
- `decisions/ADR-005-first-review-points-selection.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`

## Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `docs/backend.md`
- `docs/ai-review.md`
- `decisions/ADR-005-first-review-points-selection.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`

## Optional Context

- `tasks/MVP_TASK_MAP.md`
- `changelog/2026-06.md`

## Out of Scope

- 不新增 controller / service / API
- 不修改数据库迁移 SQL
- 不修改 Docker 配置
- 不修改 `PRD.md`
- 不修改 `docs/ARCHITECTURE.md`
- 不进入完整 `EvidenceSlot / SourceAnchor` 生命周期治理
- 不接入 Gemma
- 不在本任务内继续开放式补充新的表达变体规则

## 背景

`TASK-025` 原始目标是把 parser 产物最小接入审核主链路。到本轮为止，主链路接线已经完成，实际缺口不再是“能否接入”，而是“如何收口验收并阻断继续无限补规则”。

因此本任务现阶段被重新定义为：
- fixture 级验收收口任务
- 用来确认当前 parser-backed 主链路在 4 正 4 负 fixture 上是否达到可接受的最小稳定性
- 不再承担 `CandidateResolver` / `EvidenceSlot` 的完整机制治理目标

## 当前实现事实

已完成：
- 新增 `ParserBackedReviewInputPreparer`
- `TaskExecutionStateMachine` 已接入
  - `PARSING`
  - `INDEXING`
  - `PLANNING`
  - `BUILDING_EVIDENCE`
  - `REVIEWING_RULES`
  - `COMPOSING`
- parser-backed 正向样例 `CQCP-MVP-DOCX-001`、`002`、`003`、`004` 当前可完成预期 evidence 绑定
- 新增 `MinimalCandidateResolver`
  - `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN`
  - 非 `HIGH` 不再进入确定性裁判
- 新增 parser evidence 严格回归测试
  - 校验 `PointStatus`
  - 校验 `candidateValue`
  - 校验 `blockId`
  - 校验 `evidenceSummary` 包含值和关键词
- 已修复 `paymentClauseBlocks()` 死代码 bug
  - `request != null` 条件写反，导致付款方式切片永远失效
  - 月度 / 节点付款切片已恢复执行
  - 同步修正切片起始命中词与 `sliceBlocks()` 起始块被排除的问题
- 新增真实冲突 fixture `CQCP-MVP-DOCX-001-progress-conflict.docx`
  - 在同一 `A模式：按月形象进度付款` block 内同时放入 `70%` 与 `75%`
  - 经真实 parser 主链路触发 `CONFLICTED`
  - 已固化到 `ParserBackedReviewInputPreparerEvidenceTest`

收口结论：
- `TASK-025` 已达到 fixture 级验收 DoD
- `TASK-026` 已完成并归档到 `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
- `TASK-027` / `TASK-028` / `TASK-032` 仍未启动

## Definition of Done

`TASK-025` 的完成条件固定为：

4 个正向 fixture + 4 个负向 fixture 全部通过以下校验：
- `PointStatus`
- `candidateValue`
- `blockId`
- `evidenceSummary` 包含值和关键词

补充约束：
- `TASK-025` 不再以“继续补表达变体规则”作为开放式完成条件
- 若后续出现新的合同表达变体，不回流到 `TASK-025` 持续补规则
- 必须新开轻量任务，并先判断缺口属于：
  - 规则覆盖缺口
  - 最小 `CandidateResolver` 能力缺口
  - 完整 `EvidenceSlot` 治理缺口

## 验收口径

- 若真实歧义导致历史 `PASS -> NOT_CONCLUDED`，只要诊断与证据链一致，则记为“机制修正成功”，不是默认失败
- 这类状态变化必须单列记录在：
  - `CURRENT_CONTEXT.md`
  - `changelog/2026-06.md`
  - 本任务文件
- 不允许为了把某个点“修回 PASS”而默认新增启发式或 regex

## 依赖与边界

上游依赖：
- `TASK-018`
- `TASK-019`
- `TASK-020`
- `TASK-021`
- `TASK-022`

下游拆分：
- `TASK-026`：最小 `CandidateResolver` / 置信度分级 / 同 role 竞争检测
- `TASK-027`：完整 `EvidenceSlot / SourceAnchor` 正式治理
- `TASK-028`：Gemma `MEDIUM` 档辅助通道
- `TASK-032`：按 `ReviewPointFamily` 拆分 `ParserBackedReviewInputPreparer`

## 本轮验证

已通过：
- `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"`
- `gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"`
- `gradle test --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest"`
- `gradle test --tests "com.cqcp.apiserver.reviewengine.TaskResultQueryServiceTest"`

最终收口验证（2026-06-19）：
- `gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"`：通过
- `gradle test --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest"`：通过
- `gradle test --tests "com.cqcp.apiserver.reviewengine.TaskResultQueryServiceTest"`：通过
- `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"`：通过
- `gradle test`：失败于既有 `CqcpApiServerApplicationTests.contextLoads` PostgreSQL host 解析问题，不作为 `TASK-025` 失败结论

fixture 覆盖结论：
- 4 个正向 fixture：`CQCP-MVP-DOCX-001`、`CQCP-MVP-DOCX-002`、`CQCP-MVP-DOCX-003`、`CQCP-MVP-DOCX-004`
- 负向 fixture 矩阵：4 个 expected fixture 文件中的 `negativeCandidates` 全量展开校验，当前共 14 个负向矩阵行
- `TaskExecutionStateMachineTest` 校验真实 parser-backed execution 的 `PointStatus`
- `ParserBackedReviewInputPreparerEvidenceTest` 校验 `candidateValue`、`blockId`、`evidenceSummary` 包含关键值与关键词

本轮新增核实：
- 阶段一真实日志验证已完成
  - 曾在 `resolveFromCandidates()` 内临时打印 `reviewPointCode + candidateRole + confidenceLevel + candidateCount`
  - 运行 `gradle test --tests "*ParserBackedReviewInputPreparerEvidenceTest*" --info`
  - 日志确认后已移除临时输出并复跑通过
- 新增真实 parser 主链路冲突回归
  - fixture：`packages/test-fixtures/docx/CQCP-MVP-DOCX-001-progress-conflict.docx`
  - 预期：`EvidenceStatus.AMBIGUOUS`
  - 置信度：`CONFLICTED`
  - 诊断码：`SYS_ROLE_CONFLICT`
- 修复前后状态矩阵已逐点对比
  - 仅修复 `request == null` 后，`CQCP-MVP-DOCX-002` / `004` 的月度付款相关点曾回退为 `NOT_CONCLUDED`
  - 复核原文后确认不是真实歧义，而是切片实现仍有第二层缺陷
  - 继续修正后，4 正 + 4 负 fixture 的 9 个 review point 最终 `PointStatus` 与修复前一致，无净变化

参考项：
- 全量 `gradle test` 仍会失败于既有数据库连接阻塞，不作为本轮代码失败结论

## 风险

- 当前最小 resolver 只解决 evidence admission 与置信度造假问题，未覆盖完整 `EvidenceSlot` 生命周期
- `TASK-026` 已独立完成并归档；本任务不继续扩展 `CandidateResolver`
- `ParserBackedReviewInputPreparer` 仍是单体类，需由 `TASK-032` 继续处理
- 付款条款切片目前仍依赖文本标记与块边界，后续若 parser block 粒度变化，需在 `TASK-032` / `TASK-027` 中继续结构化治理
- 位置切片（`paymentClauseBlocks` 按 `MONTHLY` / `MILESTONE` 分段）在当前 4 正 4 负 fixture 上对最终判定结果无可观测影响，实际候选范围限定主要依赖内容关键词过滤（`isRatioRoleBlock` / `isExpectedRatioValue`）；该点需在 `TASK-026` / `TASK-027` 后续治理时重新评估，不应假设位置切片已被样本验证有效
- 真实 parser 主链路已通过 `TASK-026` 补充覆盖 `MEDIUM / LOW / CONFLICTED`；`UNKNOWN` 仍仅由 `MinimalCandidateResolverTest` 隔离单测覆盖

## 完成记录

- 完成日期：2026-06-19
- 本轮收口修改文件：
  - `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`
  - `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java`
  - `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachineTest.java`
  - `CURRENT_CONTEXT.md`
  - `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
  - `tasks/MVP_TASK_MAP.md`
  - `changelog/2026-06.md`
