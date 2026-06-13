# TASK-019：Result Composer + ReviewResultSnapshot 最小合成

状态：已完成

类型：父任务 / A 类 Codex 主控 / 后端结果合成

优先级：P0

负责人：Codex

创建日期：2026-06-12

来源：`CURRENT_CONTEXT.md` 下一步主线、`tasks/MVP_TASK_MAP.md`、`PRD.md` 结果页与快照要求、`docs/ARCHITECTURE.md` 主审核链路、`ADR-002`、`ADR-003`、`ADR-012`、`ADR-013`、`TASK-018-review-engine-minimal-validation`

## 背景

`TASK-018` 已经基于 4 份 `expected fixtures` 和首批 9 个 frozen review point 产出最小确定性 `Review Engine` 结果，当前已具备：

* `PointReviewResult[]`
* `ReviewSummary`
* `ReviewCompleteness`
* `ReviewResultSnapshotDraft`
* `PointDiagnostic[]`

但这些对象仍停留在 `Review Engine` 内部草案层，尚未被正式定义为后续可消费的 `ReviewResultSnapshot` 合成边界。  

`PRD.md`、`docs/ARCHITECTURE.md`、`ADR-002`、`ADR-003`、`ADR-012`、`ADR-013` 已经共同冻结以下事实：

* 正式结果必须以不可变 `ReviewResultSnapshot` 作为读取事实来源。
* 普通结果页、管理台、外部 API 不能各自解释点级状态。
* `SYS-*` 必须保留在诊断层，并映射为业务化 `NOT_CONCLUDED`。
* `ReviewResultSnapshot` 必须绑定 `taskId + executionId`，并携带最小版本引用与结构化字段快照。
* 现有数据库基线已具备 `review_result_snapshot` 表，不允许在本任务中再扩表或改迁移。

因此，`TASK-019` 的目标不是扩展新能力，而是把 `TASK-018` 的点级结果草案合成为正式最小 `ReviewResultSnapshot` 对象边界，为后续 `TASK-020` 状态机、`TASK-021` 结果查询接口、普通结果页和管理台详情提供稳定输入。

## 目标

* 冻结 `Result Composer` 的最小职责边界。
* 明确 `Result Composer` 消费的输入对象集合。
* 明确正式最小 `ReviewResultSnapshot` 的输出字段边界。
* 定义 `TASK-018` 草案对象到正式快照对象的映射口径。
* 明确哪些字段进入普通结果读取面，哪些只保留在诊断层。
* 明确本任务与 `TASK-020`、`TASK-021` 的边界，避免把状态机、接口或真实主链路接入混入本任务。
* 为后续实现阶段提供可直接落地的父任务约束和验收标准。

## 非目标

* 不创建或修改数据库迁移。
* 不进入 `TASK-020` 的状态机实现。
* 不进入 `TASK-021` 的 Result URL 查询接口实现。
* 不把 fixture 驱动输入升级为真实 parser / candidate / evidence 主链路。
* 不扩展 `Review Engine` 审核规则范围。
* 不实现完整 Repository / Controller / 外部 API。
* 不扩展普通结果页或管理台页面。
* 不生成 `TASK_SPEC`，不启动 `Claude Code + DeepSeek`。

## 输入

* 相关文档：
  * `AGENTS.md`
  * `CURRENT_CONTEXT.md`
  * `PRD.md`
  * `docs/ARCHITECTURE.md`
  * `tasks/MVP_TASK_MAP.md`
  * `tasks/TEMPLATE_ROUTER.md`
* 相关 ADR：
  * `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
  * `decisions/ADR-003-task-execution-snapshot-model.md`
  * `decisions/ADR-012-domain-model-freeze.md`
  * `decisions/ADR-013-v1-core-schema-bootstrap.md`
* 上游任务：
  * `tasks/active/TASK-018-review-engine-minimal-validation.md`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `PRD.md`
* `docs/ARCHITECTURE.md`
* `tasks/MVP_TASK_MAP.md`
* `tasks/TEMPLATE_ROUTER.md`
* `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
* `decisions/ADR-003-task-execution-snapshot-model.md`
* `decisions/ADR-012-domain-model-freeze.md`
* `decisions/ADR-013-v1-core-schema-bootstrap.md`
* `tasks/active/TASK-018-review-engine-minimal-validation.md`

### Optional Context

* `docs/backend.md`
* `docs/database.md`
* `docs/ai-review.md`
* `tasks/active/TASK-016-mvp-development-validation.md`
* `tasks/active/TASK-017-expected-fixtures-bootstrap.md`

### Out of Scope

* `TASK-020` Task Execution 最小状态机
* `TASK-021` Result URL 查询接口最小实现
* `TASK-022` 普通结果页最小展示
* `TASK-023` 管理台任务详情最小诊断
* `TASK-024` 及之后的真实 parser / candidate / evidence 主链路接入
* 数据库迁移变更
* Claude Code + DeepSeek 子任务拆分与执行

## 范围

### 包含

* 定义 `Result Composer` 的最小职责：
  * 接收一次 execution 的结果合成输入。
  * 生成正式最小 `ReviewResultSnapshot` 内存对象。
  * 明确诊断层、业务层和快照层的字段归属。
* 冻结最小输入边界：
  * `taskId`
  * `executionId`
  * `structuredFieldsSnapshot`
  * `enabledReviewPointsSnapshot`
  * `disabledReviewPointsSnapshot`
  * `Review Engine` 产出的 `PointReviewResult[]`
  * `ReviewSummary`
  * `ReviewCompleteness`
  * `PointDiagnostic[]`
  * 最小 `sourceAnchors`
  * 本次 execution 绑定的版本引用摘要
* 冻结最小输出边界：
  * `ReviewResultSnapshot.status`
  * `summary`
  * `reviewCompleteness`
  * `pointResults`
  * `findings`
  * `diagnostics`
  * `sourceAnchors`
  * `structuredFieldsSnapshot`
  * `enabledReviewPointsSnapshot`
  * `disabledReviewPointsSnapshot`
  * 最小版本引用字段
  * `createdAt`
* 定义点级结果到 `findings` 的最小映射规则：
  * 只有 `ERROR / WARNING` 进入业务 `findings`
  * `PASS / NOT_CONCLUDED / SKIPPED` 不进入业务风险统计
* 定义 snapshot 顶层 `status` 的最小合成口径：
  * 保持与 `ADR-003` 的 execution/snapshot 模型兼容
  * 仅在本任务中冻结“结果快照层如何表达完成结果”，不扩展到完整 execution 状态机
* 明确本任务是否需要 to-issues 草案拆分。

### 不包含

* 不创建新表、索引、迁移或 schema 变更。
* 不补完整 persistence adapter 之外的数据库设计。
* 不引入真实结果 URL 路由与公开接口。
* 不定义新的审核点、状态枚举或业务 finding 类型。
* 不修改 `ADR-002 / ADR-003 / ADR-012 / ADR-013` 已接受边界。

## Result Composer 最小输入/输出定义

### 最小输入

`Result Composer` 最小消费输入应来自“单次 execution 已完成审核结果合成前”的稳定对象集合：

* `taskId`
* `executionId`
* `structuredFieldsSnapshot`
* `enabledReviewPointsSnapshot`
* `disabledReviewPointsSnapshot`
* `PointReviewResult[]`
* `ReviewSummary`
* `ReviewCompleteness`
* `PointDiagnostic[]`
* `sourceAnchors`
* `contractTypeProfileVersion`
* `ruleSetVersion`
* `reviewBudgetProfileVersion`
* `modelProfileVersion`
* `parserVersion`
* `promptVersion`
* `schemaVersion`
* `patternLibraryVersion`
* `fieldLexiconVersion`
* `evidenceSelectorVersion`
* `createdAt`

约束：

* 本任务不要求真实 parser / candidate / evidence 主链路已经接入。
* 本任务默认上游仍可以消费 `TASK-018` 的最小草案对象，但需要在本任务中把“草案输入”升级为“正式 composer 输入口径”。
* 若发现 `TASK-018` 输出无法覆盖 `ADR-003 / ADR-012 / ADR-013` 要求的快照字段，只记录为“待确认”或实现阶段补齐项，不自行扩展架构。

### 最小输出

`Result Composer` 最小产出应为正式最小 `ReviewResultSnapshot` 内存对象，至少包含：

* `taskId`
* `executionId`
* `supersededByExecutionId?`
* `supersededReason?`
* `status`
* `summary`
* `reviewCompleteness`
* `pointResults`
* `findings`
* `diagnostics`
* `sourceAnchors`
* `structuredFieldsSnapshot`
* `enabledReviewPointsSnapshot`
* `disabledReviewPointsSnapshot`
* `contractTypeProfileVersion`
* `ruleSetVersion`
* `reviewBudgetProfileVersion`
* `modelProfileVersion`
* `parserVersion`
* `promptVersion`
* `schemaVersion`
* `patternLibraryVersion`
* `fieldLexiconVersion`
* `evidenceSelectorVersion`
* `createdAt`

输出约束：

* `pointResults` 必须保持 `ADR-002` 的五态固定集：
  * `PASS`
  * `WARNING`
  * `ERROR`
  * `NOT_CONCLUDED`
  * `SKIPPED`
* `diagnostics` 保留 `SYS-*` 与点级诊断摘要，但不得把技术码抬升为业务 finding。
* `findings` 只从 `ERROR / WARNING` 点级正式结果衍生。
* `summary` 与 `reviewCompleteness` 必须与 `pointResults` 自洽，不允许重复统计或出现相互冲突口径。

## 最小映射规则

* `PointReviewResult.pointStatus=ERROR` -> 进入 `pointResults`，并生成业务 `finding`
* `PointReviewResult.pointStatus=WARNING` -> 进入 `pointResults`，并生成业务 `finding`
* `PointReviewResult.pointStatus=PASS` -> 进入 `pointResults`，不生成 `finding`
* `PointReviewResult.pointStatus=NOT_CONCLUDED` -> 进入 `pointResults`，不生成 `finding`
* `PointReviewResult.pointStatus=SKIPPED` -> 进入 `pointResults`，不生成 `finding`
* `PointDiagnostic` 中的 `SYS-*`、缺证据、歧义、适用性原因进入 `diagnostics`
* `summary` 的风险统计严格遵循 `ADR-002`
* `ReviewResultSnapshot.status` 在本任务中只冻结“终态结果快照如何表达”的最小规则，不越权设计 execution 运行中状态

## 约束

* 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
* 只完成 `Result Composer + ReviewResultSnapshot` 最小合成范围。
* 不顺手实现 `TASK-020`、`TASK-021` 或真实页面。
* 不得修改 `apps/`、`packages/`、`deploy/` 下业务代码于本父任务创建阶段。
* 不得修改数据库迁移。
* 不得修改 `PRD.md` 或 `docs/ARCHITECTURE.md`。
* 若发现需要改变 `ReviewResultSnapshot` 主字段边界、状态契约或快照职责，必须先判断是否需要 ADR。
* 若发现 `ADR-002 / ADR-003 / ADR-012 / ADR-013` 之间存在缺口，只记录“待确认”，不自行补全架构空白。
* 不得把 `SYS-*` 写成业务 finding。
* 不得把证据不足写成 `PASS / WARNING / ERROR`。

## 交付物

* `tasks/active/TASK-019-result-composer-review-result-snapshot.md` 父任务定义
* `Result Composer` 最小输入边界说明
* 正式最小 `ReviewResultSnapshot` 输出边界说明
* 点级结果到 snapshot 的映射规则
* 是否需要 to-issues 的明确结论
* 实现阶段验收标准

## 验收标准

* 父任务文档能明确回答 `Result Composer` 消费什么输入。
* 父任务文档能明确回答正式最小 `ReviewResultSnapshot` 至少产出什么字段。
* 父任务文档能明确回答 `PointReviewResult[] / ReviewSummary / ReviewCompleteness / PointDiagnostic[]` 如何进入正式 snapshot。
* 父任务文档能明确区分：
  * 业务结果层
  * 诊断层
  * 快照层
  * 状态机层
  * API 层
* 父任务文档能明确本任务不直接生成 `TASK_SPEC`，不启动 `Claude Code + DeepSeek`。
* 父任务文档能明确本任务当前是否需要 to-issues。

## 测试与验证

本父任务创建阶段不写业务代码。  
实现阶段至少应验证：

* `TASK-018` 的最小 `Review Engine` 输出可被 composer 稳定消费。
* 合成后的 `ReviewResultSnapshot` 满足 `ADR-002 / ADR-003 / ADR-012 / ADR-013` 的最小边界。
* `ERROR / WARNING` 正确进入 `findings`。
* `NOT_CONCLUDED / SKIPPED / PASS` 不进入业务风险统计。
* 快照顶层字段、摘要统计和点级结果互相自洽。
* 若落最小持久化，应仅使用现有 `review_result_snapshot` 表边界，不新增迁移。

## to-issues 判断

当前结论：本父任务创建阶段不需要先执行 to-issues 草案拆分。

原因：

* `TASK-019` 仍处于 A 类 Codex 主控阶段。
* 当前首要目标是先冻结快照边界，而不是并行分发局部实现。
* 在正式实现前，尚不适合把本任务提前切成 `TASK_SPEC`。

后续触发条件：

* 如果实现阶段确认需要分成“composer 对象定义 / snapshot mapper / persistence adapter / 结果读取 view mapper”等相对独立子块，可在父任务执行中途再补 to-issues 草案。
* 即使后续出现 to-issues，也只输出草案，不直接下放 `TASK_SPEC`，除非边界已冻结且局部块明确降级为 B 类任务。

## 文档更新要求

* 本父任务创建阶段不强制更新 `CURRENT_CONTEXT.md`
* 本父任务创建阶段不强制更新 `changelog/2026-06.md`
* 实现完成后必须更新：
  * `CURRENT_CONTEXT.md`
  * `changelog/2026-06.md`
  * 本任务完成记录
* 是否需要更新 `docs/backend.md`：待确认
* 是否需要新增或更新 ADR：待确认

## 风险

* `TASK-018` 的 `ReviewResultSnapshotDraft` 可能与 `ADR-003 / ADR-012` 最小字段要求仍有缺口。
* 如果本任务把 execution 状态、结果 API、页面读取逻辑一起卷入，会导致任务边界失控。
* 如果在实现阶段把 `diagnostics` 与 `findings` 混写，会直接破坏 `ADR-002`。
* 如果最小版本引用边界定义不稳，后续 `TASK-020 / TASK-021` 会被迫返工。

## 待确认

* `ReviewResultSnapshot.status` 在纯终态快照层的最小枚举和 `Execution.status` 的映射口径，是否已有现成实现边界可直接复用，还是需要在实现阶段补充明确。
* `diagnostics` 在正式最小 snapshot 中保留到什么粒度，才能同时满足 `ADR-002` 与 `ADR-013`，且不提前侵入管理台专用细节。
* `sourceAnchors` 在本任务中是否直接复用 `TASK-018` 最小摘要对象，还是需要仅冻结字段名而不冻结内部结构。
* 是否在 `TASK-019` 实现阶段同时落最小持久化 adapter；若落地，也必须限定为“使用现有表边界”，不得演变为 schema 设计任务。

## Next Task Handoff

* 本任务完成父任务创建后，下一步可直接进入 `TASK-019` 实现阶段。
* 本任务未要求 to-issues，也未授权生成 `TASK_SPEC`。

## 完成记录

* 完成日期：2026-06-13
* 变更文件：
  * `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ResultComposer.java`
  * `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`
  * `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ResultComposerTest.java`
  * `tasks/active/TASK-019-result-composer-review-result-snapshot.md`
  * `CURRENT_CONTEXT.md`
  * `changelog/2026-06.md`
* 测试结果：
  * 定向验证：`gradle test --tests com.cqcp.apiserver.reviewengine.ResultComposerTest --tests com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest` -> 通过
  * 全量后端构建：`gradle build` 未通过；失败点为 `CqcpApiServerApplicationTests` 启动阶段无法连接 PostgreSQL，执行时 `docker ps` 为空，确认是当前本地测试库容器未运行，不是 `TASK-019` 新增 composer 代码编译失败
* 遗留问题：
  * 本轮完成的是正式最小 `ReviewResultSnapshot` 内存对象与 `Result Composer`，尚未进入数据库持久化 adapter、结果读取 API 或状态机衔接
  * `ReviewResultSnapshot.status` 仍采用最小终态口径：`SUCCESS / PARTIAL_SUCCESS`；更完整的 execution 运行态与失败态衔接留给 `TASK-020`
* 备注：
  * 本轮未触发 ADR；未修改数据库迁移、接口契约或主审核链路边界
  * 当前 `.reviewengine` 包内仍保留 `TASK-018` 的 `ReviewResultSnapshotDraft`，作为最小验证草案对象；正式读取面已切换为 `ResultComposer` 产出的 `ReviewResultSnapshot`
