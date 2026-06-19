# TASK-026：最小 CandidateResolver 置信度治理

状态：已完成并归档

类型：A 类主链路任务 / 后端实现

优先级：P0

负责人：Codex

创建日期：2026-06-19

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`

## 背景

`TASK-025` 已完成 parser-backed evidence 主链路最小接入，并通过 `ADR-014` 固定 `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN` 五档置信度与 `HIGH` admission gate。

补充核查发现，真实 parser 主链路虽已通过 fixture 证明 `CONFLICTED` 可达，但 `MEDIUM / LOW` 仍只停留在 `MinimalCandidateResolverTest` 隔离单测覆盖。原因不是缺少测试，而是 `ParserBackedReviewInputPreparer` 当前候选构造大多直接写成全信号候选，或把“命中 role 文本”和“block 归属可靠”绑定为同一个布尔值。

## 目标

* 调整 parser-backed 主链路 candidate 信号建模，让 `MEDIUM / LOW` 能通过真实 `parse -> index -> plan -> build` 链路触发。
* 保持 `ADR-014` 的 admission gate 不变：只有 `HIGH` 可进入确定性裁判。
* 用真实 `.docx` fixture 固化 `MEDIUM / LOW` 回归路径。

## 非目标

* 不接入 Gemma。
* 不实现完整 `EvidenceSlot / SourceAnchor` 生命周期治理。
* 不拆分 `ParserBackedReviewInputPreparer`。
* 不扩大 `TASK-025` 的 4 正 4 负 golden fixture 验收矩阵。
* 不更新 `docs/ARCHITECTURE.md`。

## 输入

* 相关文档：`docs/backend.md`、`docs/ai-review.md`
* 相关 ADR：`decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
* 上游任务：`tasks/active/TASK-025-parser-candidate-evidence-mainline-integration.md`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `docs/backend.md`
* `docs/ai-review.md`
* `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java`

### Optional Context

* `tasks/MVP_TASK_MAP.md`
* `changelog/2026-06.md`
* `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`
* `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachineTest.java`

### Out of Scope

* 不新增 API / controller / service。
* 不修改数据库迁移。
* 不修改 Docker 配置。
* 不引入外部模型或公网调用。
* 不新增任意审核点。
* 不进行 `ReviewPointFamily` 物理拆分类重构。

## 范围

### 包含

* 调整 `ParserBackedReviewInputPreparer` 的候选信号标注。
* 保留 `MinimalCandidateResolver` 五档判定语义。
* 新增 `MEDIUM / LOW` 真实 parser 主链路 fixture。
* 增加端到端 evidence admission 回归测试。

### 不包含

* 不改变点级最终裁判规则。
* 不改变 `SYS-*` 到 `NOT_CONCLUDED` 的映射。
* 不改变 `TASK-025` 的 fixture 验收口径。

## 约束

* 只有 `HIGH` 可以进入确定性裁判并产出业务 `PASS / ERROR / WARNING`。
* `MEDIUM / LOW / CONFLICTED / UNKNOWN` 必须停在 `NOT_CONCLUDED`。
* 候选不足或归属不清时不得静默回灌全文。
* 新增 fixture 只用于置信度治理，不加入 golden fixture 主矩阵。
* 本任务默认不需要新增 ADR；若改变五档定义、admission gate 或 CandidateResolver 职责边界，则必须先补 ADR。

## 交付物

* `ParserBackedReviewInputPreparer` candidate 信号建模调整。
* `ParserBackedReviewInputPreparerEvidenceTest` 新增 `MEDIUM / LOW` 主链路断言。
* `packages/test-fixtures/docx/CQCP-MVP-DOCX-001-progress-medium.docx`
* `packages/test-fixtures/docx/CQCP-MVP-DOCX-001-progress-low.docx`
* 项目记忆写回。

## 验收标准

* 真实 parser 主链路可以触发 `MEDIUM`：
  * `EvidenceStatus.AMBIGUOUS`
  * `confidence = MEDIUM`
  * `diagnosticCode = SYS_EVIDENCE_MEDIUM_CONFIDENCE`
  * `notConcludedReason = EVIDENCE_AMBIGUOUS`
* 真实 parser 主链路可以触发 `LOW`：
  * `EvidenceStatus.AMBIGUOUS`
  * `confidence = LOW`
  * `diagnosticCode = SYS_EVIDENCE_LOW_CONFIDENCE`
  * `notConcludedReason = EVIDENCE_AMBIGUOUS`
* 既有 `CONFLICTED` fixture 继续通过。
* 既有正向 / 负向 fixture 回归不被破坏。

## 测试与验证

已执行：

* `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"`
* `gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"`
* `gradle test --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest"`

参考项：

* 全量 `gradle test` 仍失败于既有 `CqcpApiServerApplicationTests` PostgreSQL host 解析失败，不作为本任务失败结论。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是。
* 是否需要更新 `docs/*.md`：否。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要新增或更新 ADR：否，除非实现改变 `ADR-014` 决策边界。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：仅记录 `TASK-026` active task 文件已建立与当前状态。

## Next Task Handoff

* 任务完成后必须判断是否存在明确下一任务。
* `TASK-032` 仍只登记为后续重构任务，不在本任务执行。
* 若没有新的明确 active task，不输出可复制代码块。

## 风险

* `ParserBackedReviewInputPreparer` 仍是单体类，候选信号治理会继续受到局部规则耦合影响。
* 本任务只治理最小候选信号，不代表完整 `EvidenceSlot / SourceAnchor` 生命周期完成。
* 新增 fixture 是治理样本，不证明位置切片机制已在所有合同表达中稳定有效。

## 待确认

* 待确认：`TASK-032` 是否在 `TASK-026` 完成后立即启动。
* 待确认：`TASK-027` 是否需要为正式 `EvidenceSlot / SourceAnchor` 设计新增 ADR。

## 完成记录

* 完成日期：2026-06-19。
* 变更文件：
  * `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java`
  * `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`
  * `packages/test-fixtures/docx/CQCP-MVP-DOCX-001-progress-medium.docx`
  * `packages/test-fixtures/docx/CQCP-MVP-DOCX-001-progress-low.docx`
  * `CURRENT_CONTEXT.md`
  * `tasks/MVP_TASK_MAP.md`
  * `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
  * `changelog/2026-06.md`
* 测试结果：
  * `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest" --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest" --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest"`：通过
  * `gradle test`：失败于既有 `CqcpApiServerApplicationTests.contextLoads` PostgreSQL host 解析问题
* 遗留问题：
  * `UNKNOWN` 仍仅由 resolver 隔离单测覆盖，未新增真实 parser 主链路 fixture。
  * 完整 `EvidenceSlot / SourceAnchor` 生命周期仍留给 `TASK-027`。
  * `ParserBackedReviewInputPreparer` 物理拆分仍留给 `TASK-032`。
* 备注：本任务包按已确认计划补建；实现方向未改变 `ADR-014` 的五档定义和 admission gate。
