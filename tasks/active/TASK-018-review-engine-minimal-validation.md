# TASK-018：Review Engine 最小验证闭环
状态：已完成
类型：后端最小实现 / 确定性裁判验证 / MVP 闭环推进

优先级：P0
负责人：待确认
创建日期：2026-06-12

来源：`CURRENT_CONTEXT.md` 当前下一步、`PRD.md` 主链路、`TASK-016-mvp-development-validation` 收口结论、`ADR-005-first-review-points-selection`

## 背景

当前仓库已经完成 `TASK-006` 脚手架环境验证、`TASK-015` Flyway V1 基线、`TASK-017` 首批 expected fixtures bootstrap，以及 `TASK-016` 的 4 个开发前验证最小闭环。  
但项目仍未真正跑通 `9 个审核点 -> 点级结果 -> 结果摘要 -> ReviewResultSnapshot 上游输入` 的确定性主链路。

`PRD.md` 和 `docs/ARCHITECTURE.md` 都明确要求：

- 主审核链路是 `Contract -> Parser -> CandidateResolver -> EvidenceSlot -> Review Engine -> Finding -> ReviewResultSnapshot`
- 后端负责结构化比对、公式裁判和最终确定性裁判
- 证据不足不得生成业务 finding
- `SYS-*` 必须与业务 finding 分流，并映射为业务化 `NOT_CONCLUDED`

因此，下一步优先级不应是完整管理台页面，而应先落地最小确定性 `Review Engine`，使现有样例夹具能够产生真实点级审核结果，并为后续 `Snapshot`、执行状态机、API 和管理台详情提供真实结果来源。

## 目标

* 基于现有 4 份 `expected/*.json` 和已冻结的 9 个 core review point，落地最小确定性 `Review Engine`。
* 产出可被后续 `Result Composer / Snapshot / 管理台摘要 / 普通结果页` 消费的内存对象层结果。
* 跑通最小点级状态矩阵：`PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`。
* 验证 `SYS-* -> NOT_CONCLUDED` 分流边界。
* 验证证据不足、候选冲突或适用性不匹配时，不生成业务 finding。

## 非目标

* 不实现真实 Word 正式抽取。
* 不实现完整 `CandidateResolver`。
* 不实现真实模型调用或 Gemma/DeepSeek 接入。
* 不实现完整 `EvidenceBundle / EvidencePacket`。
* 不实现真实任务 API。
* 不实现完整管理台页面或普通结果页。
* 不扩展样例数量，不新增第 5 份 `.docx`。
* 不进入 Pilot / Production Readiness。

## 输入

* 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`PRD.md`
* 相关架构章节：`docs/ARCHITECTURE.md` 中主链路、EvidenceSlot、Review Engine、Result Composer、ReviewResultSnapshot 相关章节
* 相关 ADR：`ADR-002`、`ADR-003`、`ADR-005`、`ADR-012`
* 上游任务：`TASK-016-mvp-development-validation`、`TASK-017-expected-fixtures-bootstrap`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/active/TASK-018-review-engine-minimal-validation.md`
* `PRD.md`
* `docs/ARCHITECTURE.md`
* `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
* `decisions/ADR-003-task-execution-snapshot-model.md`
* `decisions/ADR-005-first-review-points-selection.md`
* `decisions/ADR-012-domain-model-freeze.md`
* `tasks/active/TASK-017-expected-fixtures-bootstrap.md`

### Optional Context

* `docs/backend.md`
* `docs/database.md`
* `docs/ai-review.md`
* `decisions/ADR-013-v1-core-schema-bootstrap.md`
* `tasks/active/TASK-016-mvp-development-validation.md`
* `packages/test-fixtures/README.md`
* `packages/test-fixtures/expected/*.json`

### Out of Scope

* Word parser 正式字段抽取
* 复杂 CandidateResolver
* Gemma / DeepSeek / 公网兼容 provider 真实调用
* 完整 EvidenceBundle / PointEvidenceOverlay
* 真实 Task / Execution API
* 完整管理台页面与复杂路由
* 普通结果页
* 样例扩容

## 范围

### 包含

* 定义 `Review Engine` 最小输入对象边界：
  - 结构化字段输入
  - expected fixture 中的 `goldenExpected`
  - expected fixture 中的 `negativeCandidates`
  - 最小候选证据摘要对象
  - 9 个 frozen review point
* 定义并实现最小输出对象边界：
  - `PointReviewResult[]`
  - `ReviewSummary`
  - `ReviewCompleteness`
  - `ReviewResultSnapshot` 草案对象
  - `PointDiagnostic[]`
* 用 4 份现有 expected fixtures 跑通回归测试。
* 落地最小确定性裁判规则。
* 为后续 `TASK-019` 的 `Result Composer + Snapshot` 提供稳定上游结果结构。

### 不包含

* 不把 parser Spike 直接升级为正式 parser。
* 不把 expected fixtures 直接等同于最终数据库快照。
* 不在本任务中引入任务状态机、异步 worker 或 API 入口。
* 不在本任务中落地 Flyway schema 扩展、Repository、Mapper 或 Controller。

## 约束

* 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
* 只完成当前任务包定义的最小确定性审核结果生成范围。
* 不顺手实现 `TASK-019`、`TASK-020`、`TASK-021`、`TASK-022`、`TASK-023` 或 `TASK-024`。
* 所有点级状态必须遵循 `ADR-002` 已冻结契约。
* `SYS-*` 只能进入诊断层，必须映射为业务化 `NOT_CONCLUDED`。
* `PASS / WARNING / ERROR` 必须建立在可靠证据之上；证据不足不得生成业务 finding。
* `SKIPPED` 只用于明确不适用，不得把“不适用”伪装成 `NOT_CONCLUDED`。
* 不得引入真实模型调用，不得让模型直接决定最终点级状态。
* 不得新增超出 ADR 冻结边界的业务功能。
* 若发现需要改变 `ReviewResultSnapshot`、`Finding`、`EvidenceSlot`、`CandidateResolver` 或主审核链路边界，必须先评估是否需要 ADR。

## 最小规则范围

本任务只覆盖首批 9 个 frozen review point：

* `PARTY_A_NAME_CONSISTENCY`
* `PARTY_B_NAME_CONSISTENCY`
* `CONTRACT_TOTAL_AMOUNT_CONSISTENCY`
* `TAX_AMOUNT_FORMULA_CONSISTENCY`
* `PREPAYMENT_RATIO_CONSISTENCY`
* `PROGRESS_PAYMENT_RATIO_CONSISTENCY`
* `COMPLETION_PAYMENT_RATIO_CONSISTENCY`
* `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY`
* `WARRANTY_RETENTION_RATIO_CONSISTENCY`

最小裁判范围只覆盖以下规则：

* 甲方名称一致性
* 乙方名称一致性
* 合同含税总金额一致性
* 税额强校验：`contractTotalAmount = taxExcludedAmount + taxAmount`
* 税额弱校验：`taxAmount = taxExcludedAmount × taxRate`
* 五个月度付款比例一致性
* `paymentMethod=MILESTONE` 时四个月度专属点输出 `SKIPPED`

## 最小输入/输出契约要求

### 最小输入

* 结构化字段输入至少覆盖首批 17 个字段中的本任务相关字段子集
* expected fixture 中的 `goldenExpected`
* expected fixture 中的 `negativeCandidates`
* 最小候选证据对象，至少保留：
  - `reviewPointCode`
  - `candidateRole`
  - `candidateValue`
  - `sourceOrigin`
  - `sourceExtractionMode`
  - `contextType`
  - `blockId` 或同等定位标识
  - `confidence`
  - 业务解释所需的最小摘要

### 最小输出

* `PointReviewResult[]`：
  - `reviewPointCode`
  - `pointStatus`
  - `businessMessage`
  - `findingSeverity` 或同等业务风险级别占位
  - `sourceAnchors` 摘要
  - `notConcludedReason` 或 `skippedReason`
* `ReviewSummary`：
  - `plannedPointCount`
  - `passCount`
  - `warningCount`
  - `errorCount`
  - `notConcludedCount`
  - `skippedCount`
* `ReviewCompleteness`：
  - 至少能表达 concluded coverage 与未结论覆盖不足
* `ReviewResultSnapshot` 草案对象：
  - 仅要求形成内存对象层草案结构
  - 不要求在本任务内持久化
* `PointDiagnostic[]`：
  - 至少覆盖点级 `SYS-*`、证据缺失、适用性原因和最小裁判解释

## 通过标准

* 4 份现有 expected fixtures 可执行。
* 9 个 core review point 全覆盖。
* `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED` 均有测试覆盖。
* `SYS-*` 与业务 finding 分流正确。
* 证据不足不生成业务 finding。
* 每个审核点都有状态、业务消息、诊断或证据摘要。
* 后端 `gradle test` 和 `gradle build` 通过。

## 交付物

* `Review Engine` 最小输入/输出对象
* 最小确定性裁判实现
* 基于 4 份 expected fixtures 的回归测试
* `ReviewResultSnapshot` 草案对象结构
* `PointDiagnostic[]` 最小诊断产物
* 更新后的项目记忆和任务执行记录

## 验收标准

* 能基于现有 expected fixtures 产出最小点级正式结果，而不是静态假数据。
* 能覆盖 9 个审核点的最小确定性裁判。
* `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED` 的触发边界在测试中可解释。
* `SYS-*` 不进入业务 finding 统计。
* `MILESTONE` 下四个月度专属点为 `SKIPPED`，而不是 `NOT_CONCLUDED`。
* 税额弱校验只允许生成 `WARNING`，不得单独生成 `ERROR`。
* 本任务完成后，后续 `TASK-019` 可以直接消费 `Review Engine` 结果做 `Snapshot` 合成。

## 测试与验证

* 使用 4 份现有 `expected/*.json` 做回归测试。
* 至少验证以下场景：
  - 主体一致与不一致
  - 合同总金额一致与不一致
  - 税额强校验通过与失败
  - 税额弱校验 `WARNING`
  - 月度付款比例一致与不一致
  - `MILESTONE` 下四个月度专属点 `SKIPPED`
  - 证据不足或候选冲突时 `NOT_CONCLUDED`
  - `SYS-* -> NOT_CONCLUDED`
* 验证命令以 `apps/api-server` 下的 `gradle test`、`gradle build` 为准。

## 文档更新要求

* 必须更新 `CURRENT_CONTEXT.md`
* 必须更新 `changelog/2026-06.md`
* 必须更新本任务完成记录
* 是否需要更新 `docs/backend.md`：待确认
* 是否需要更新 `docs/ai-review.md`：待确认
* 是否需要新增或更新 ADR：原则上否；仅当发现需要修改主链路、快照边界或状态契约时再评估

## Next Task Handoff

* 本任务若完成，明确下一任务应优先切换到 `TASK-019-result-composer-and-snapshot-minimal-persistence` 或同等编号任务。
* 若 `Review Engine` 最小输入契约与 expected fixtures 存在缺口，应先记录为后续任务或本任务内最小夹具补充，不得隐式扩大为 parser 正式实现。

## 风险

* 现有 expected fixtures 可能更偏向 parser 验证，未必天然覆盖全部点级状态与诊断边界。
* 若最小输入对象边界定义不稳，后续 `TASK-019` 快照结构可能被迫返工。
* 若把证据不足、适用性不匹配和系统失败混写成同一种未结论，会破坏 `ADR-002` 状态契约。
* 若过早把本任务做成复杂规则引擎，会偏离“MVP 最小确定性闭环”的范围控制。

## 待确认

* 现有 4 份 expected fixtures 是否已完整覆盖 9 个审核点与全部状态矩阵；若未覆盖，是否允许只补充夹具断言字段而不新增样例文件。
* `ReviewResultSnapshot` 草案对象在本任务中是否直接复用未来正式命名，还是先使用内部 draft 类型。
* `ReviewCompleteness` 的最小字段集在本任务中冻结到什么粒度。

## 2026-06-12 最小实现推进记录

### 本轮完成

* 新增 `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`，在独立 `reviewengine` 包内落地最小输入/输出对象与确定性裁判逻辑，包含：
  * `ReviewEngineInput`
  * `PointEvidence`
  * `PointReviewResult`
  * `ReviewSummary`
  * `ReviewCompleteness`
  * `ReviewResultSnapshotDraft`
  * `ReviewEngineResult`
* 冻结首批 9 个审核点的最小裁判逻辑：
  * 甲方名称一致性
  * 乙方名称一致性
  * 合同含税总金额一致性
  * 税额强校验
  * 税额弱校验
  * 五个月度付款比例一致性
  * `paymentMethod=MILESTONE` 下四个月度专属点 `SKIPPED`
* 明确 `SYS-*`、证据缺失、证据歧义与系统失败均通过 `PointDiagnostic[]` 进入诊断层，并映射为业务化 `NOT_CONCLUDED`。
* 新增 `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngineTest.java`，基于现有 4 份 expected fixtures 和 fixture 内 negative candidates 覆盖：
  * 4 份 golden fixture 全部 9 点 `PASS`
  * 主体不一致 -> `ERROR`
  * 税额弱校验偏差 -> `WARNING`
  * `MILESTONE` 下四个月度专属点 -> `SKIPPED`
  * `SYS-* -> NOT_CONCLUDED`
  * 证据歧义 -> `NOT_CONCLUDED`

### 本轮实现边界

* 本轮未接入真实 parser 正式抽取；`goldenExpected` 仅作为“合同侧已解析可靠证据”的最小基线输入。
* 本轮未实现真实 `CandidateResolver / EvidenceSlot / EvidenceBundle / PointEvidenceOverlay`。
* 本轮 source anchor 为最小摘要对象，可支撑后续 Snapshot / 结果解释对象衔接，但不等同正式原文定位链路。
* 本轮未修改数据库 schema、Repository、Mapper、Controller、任务状态机或真实任务 API。

## 完成记录

* 完成日期：2026-06-12
* 变更文件：
  * `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`
  * `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngineTest.java`
  * `tasks/active/TASK-018-review-engine-minimal-validation.md`
  * `CURRENT_CONTEXT.md`
  * `changelog/2026-06.md`
* 测试结果：
  * 定向验证：`gradle test --tests com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest` -> `BUILD SUCCESSFUL`
  * 全量后端测试：`gradle test` -> `BUILD SUCCESSFUL in 6s`
  * 后端构建：`gradle build` -> `BUILD SUCCESSFUL in 1s`
* 遗留问题：
  * 当前 `Review Engine` 仍基于 fixture `goldenExpected` 充当最小合同侧证据基线，尚未与正式 parser/candidate 链路接通。
  * 当前 `ReviewResultSnapshotDraft` 为内存草案对象，尚未进入 `TASK-019` 的持久化与正式结果合成。
  * 当前 source anchor 仍是最小摘要对象，尚未绑定真实 block/row/cell 级原文定位产物。
* 备注：
  * 本任务未触发 ADR；未改变主审核链路、快照边界或状态契约。
  * 后续应优先进入 `TASK-019 Result Composer + Snapshot`，而不是扩展 parser、模型调用或管理台页面。
