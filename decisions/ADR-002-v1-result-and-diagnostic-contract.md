# ADR-002: V1 Result and Diagnostic Contract

Status: Accepted

日期：2026-06-10

## 背景

项目核心约束要求业务 finding 与 `SYS-*` 系统诊断分流。外部 API、普通结果页、管理台和 `ReviewResultSnapshot` 都需要一致解释 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`，否则后端、前端和外部系统会各自解释状态，破坏结果可读性、外部契约稳定性和历史快照一致性。

`TASK-003`、`TASK-005`、`TASK-012` 和 `TASK-014` 之后，相关文档已经实质冻结了状态集、可见性边界、预算诊断边界和快照职责，但 ADR-002 仍停留在 `Proposed`。本 ADR 用于把这些已确认事实收拢为正式决策，解除 `TASK-006-scaffold-only-after-adr` 的门禁。

## 适用范围

- 影响模块：后端、前端、SAP/OA API、数据库快照、AI 审核。
- 影响阶段：V1 MVP / Pilot。
- 是否影响外部 API：是。
- 是否影响数据库或版本快照：是。
- 是否影响模型、规则、prompt、正则或证据选择：是，影响模型输出进入后端裁判前后的诊断映射和结果可见性。

## 决策

### 1. 点级正式状态固定为五类

V1 MVP 点级正式状态枚举固定为：

- `PASS`
- `WARNING`
- `ERROR`
- `NOT_CONCLUDED`
- `SKIPPED`

约束：

- `ERROR / WARNING` 是业务 finding。
- `PASS` 是明确通过，不计入风险数量。
- `NOT_CONCLUDED` 表示系统没有足够可靠依据形成业务结论，不是业务风险，也不是无风险结论。
- `SKIPPED` 表示审核点不适用，不映射为系统失败。

### 2. 内部 `SYS-*` 必须映射为业务化 `NOT_CONCLUDED`

内部 `SYS-*` 继续保留为技术诊断，不直接作为外部 API 或普通结果页的正式状态。

映射规则：

- `SYS-*` -> 点级 `NOT_CONCLUDED`
- 同时输出业务化 `notConcludedReason`
- 同时由后端生成固定模板 `businessMessage`

MVP 公开层最小 `notConcludedReason` 固定为：

- `PARSE_LOW_CONFIDENCE`
- `EVIDENCE_NOT_FOUND`
- `EVIDENCE_AMBIGUOUS`
- `MODEL_UNAVAILABLE`
- `MODEL_BUDGET_EXCEEDED`
- `INTERNAL_RULE_ERROR`

内部 `SYS-*` 技术码只保留在管理台任务详情、评测报告和 AI 调优包，不得成为外部系统流程控制契约。

### 3. 普通结果页只展示业务化结果，不展示完整技术诊断

普通结果页必须围绕“审核点 -> 证据 -> 原文定位”解释。

普通结果页默认展示进入 `ReviewExecutionPlan` 的全部点级结果，包括：

- `PASS`
- `ERROR`
- `WARNING`
- `NOT_CONCLUDED`
- `SKIPPED`

普通结果页必须：

- 展示证据定位和业务解释。
- 对 `NOT_CONCLUDED` 展示业务化原因和人工核对提示。
- 对 `SKIPPED` 展示适用性原因。
- 默认展示业务化“关键证据覆盖：高 / 中 / 低”。

普通结果页不得展示：

- 完整 `SYS-*` 技术码
- 完整 prompt
- raw output
- endpoint secret
- stack trace
- admin logs
- `PointDiagnostic` 技术详情
- `requiresHigherBudget`
- `recommendedBudgetProfile`
- `notConcludedDetail`

`criticalSlotCoverageRate` 的原始百分比不作为普通结果页默认主展示，只允许放在展开详情或管理台。

### 4. 管理台保留完整诊断摘要，但仍受敏感信息边界约束

管理台任务详情、评测报告和 AI 调优包可以展示：

- 完整内部 `SYS-*` 诊断码
- 模型调用状态
- timeout / unavailable / schema validation 摘要
- 预算诊断
- `requiresHigherBudget`
- `recommendedBudgetProfile`
- `notConcludedDetail`
- `PointDiagnostic` / `ExecutionSummary` 摘要

管理台仍不得展示或返回：

- 完整 prompt
- 完整模型 raw output
- endpoint secret
- stack trace
- 完整合同敏感调试包
- 密钥

### 5. 外部 API 不得依赖 diagnostic-only 字段

外部 API 顶层状态固定为有限集合：

- `QUEUED`
- `PROCESSING`
- `SUCCESS`
- `PARTIAL_SUCCESS`
- `FAILED`

外部 API 只暴露业务化点级状态、摘要和原因，不得依赖以下 diagnostic-only 字段：

- 完整 `SYS-*` 技术码
- `PointDiagnostic`
- `ExecutionSummary`
- `notConcludedDetail`
- `requiresHigherBudget`
- `recommendedBudgetProfile`
- 完整模型原始输出或调试信息

`notConcludedDetail` 在 MVP 仅供管理台、评测报告和 AI 调优包使用，不进入普通结果页和外部 API 流程控制契约。

### 6. `PROVEN_REQUIRED_CLAUSE_ABSENT` 必须与普通缺证据区分

系统不得把所有“没找到”都解释为合同本身缺少条款。

区分规则：

- `PROVEN_REQUIRED_CLAUSE_ABSENT`：缺少必备条款本身是业务风险，且 applicability、解析、索引、覆盖证明完整，可生成 `WARNING / ERROR`
- `EVIDENCE_NOT_FOUND`：证据不足或索引未形成可靠覆盖，输出 `NOT_CONCLUDED`

生成 `PROVEN_REQUIRED_CLAUSE_ABSENT` 时必须附 `coverageProofSummary`，不得伪造“未找到证据列表”作为正向证据。

### 7. `businessMessage` 由后端固定模板生成

`businessMessage` 必须由平台后端根据固定模板生成，不由模型生成，也不允许规则集写任意自由文本。

原因：

- 避免模型或规则文案漂移污染正式快照。
- 保持普通结果页、外部 API 和管理台解释口径一致。
- 保持历史快照可复现、可审计。

### 8. 业务风险统计口径固定

结果摘要必须至少统计：

- `plannedPointCount`
- `passCount`
- `errorCount`
- `warningCount`
- `notConcludedCount`
- `skippedCount`

统计规则：

- `ERROR / WARNING` 计入业务风险数量。
- `PASS / NOT_CONCLUDED / SKIPPED / SYS-*` 不计入业务风险数量。
- 被配置停用且未进入执行计划的审核点不进入 `plannedPointCount` 分母。
- 进入执行计划后因适用性被判定为 `SKIPPED` 的审核点进入分母。

## 备选方案

- 方案 A：外部 API 和普通结果页只暴露业务化状态与原因，管理台保留完整 `SYS-*` 诊断。采用。
- 方案 B：外部 API 也暴露内部 `SYS-*` 技术码。不采用，会把外部调用方绑定到内部实现。
- 方案 C：普通结果页、管理台和外部 API 使用完全不同状态模型。不采用，会破坏快照一致性和沟通成本。

## 选择理由

- 已有 PRD、后端、前端、SAP/OA 和 OpenAPI 契约都基于方案 A 演进，继续维持最稳。
- 可以把系统能力不足与业务风险清晰分流，避免把模型、预算、解析或索引问题误当成合同风险。
- 可以稳定普通结果页和外部 API 的公开面，同时保留管理台诊断能力。
- 与 `ADR-012` 的不可变快照边界、`TASK-014` 的最小公开契约和 `AI Advice != Production Change` 治理边界一致。

## 影响

### 正向影响

- 防止系统诊断被误当作业务风险。
- 让结果页围绕“审核点 -> 证据 -> 原文定位”解释。
- 降低 SAP/OA 依赖内部技术码的风险。
- 为 `TASK-006` 脚手架、后续 Flyway 和验证任务提供稳定结果契约。

### 代价与风险

- 普通结果页隐藏完整技术诊断后，复杂未结论场景需要依赖管理台排查。
- 一旦后续想扩大公开字段范围，必须保持对现有外部契约兼容。

## 不做什么

- 不让模型直接生成最终业务结论。
- 不把 `SYS-*` 计入业务风险统计。
- 不在普通结果页或外部 API 暴露 prompt、raw output、endpoint、stack trace 或 admin logs。
- 不把 `NOT_CONCLUDED` 解释为合同无风险。
- 不把 `PointDiagnostic` 暴露为普通结果页默认对象。

## 回滚与迁移

- 回滚方式：在正式 scaffold 和业务 API 发布前，允许通过新的 ADR 修订可见性边界。
- 数据迁移影响：若 `ReviewResultSnapshot` 已落库，状态契约变更需要兼容历史快照解释，不能覆盖历史含义。
- 历史快照兼容性：Accepted 后，`PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED` 和业务风险统计口径必须保持可解释。

## 验证方式

- 用首批 9 个审核点验证每种状态是否有明确触发场景。
- 用普通结果页、管理台和外部 API 三个视角核对字段可见性。
- 用 `SYS-*` 示例验证是否都映射为业务化 `NOT_CONCLUDED`。
- 用 `PROVEN_REQUIRED_CLAUSE_ABSENT` 与普通 `EVIDENCE_NOT_FOUND` 场景验证缺证据归因边界。
- 用 `TASK-014` 产出的最小 OpenAPI 契约核对公开字段范围。

## 后续动作

- 解除 `TASK-006-scaffold-only-after-adr` 中 `ADR-002` 的 Proposed 门禁。
- 后续 scaffold、Flyway 和验证任务按本 ADR 的状态/诊断边界实现。
- 若未来需要扩大外部 API 可见字段或开放 correction / budget 诊断，需要单独任务和必要 ADR。

## 关联

- 相关任务：`TASK-002-entry-order-decision`、`TASK-003-first-review-points-selection`、`TASK-006-scaffold-only-after-adr`、`TASK-014-minimal-openapi-contract`
- 相关文档：`PRD.md`、`CURRENT_CONTEXT.md`、`docs/frontend.md`、`docs/backend.md`、`docs/ai-review.md`、`docs/sap.md`
- 相关 ADR：`ADR-005-first-review-points-selection`、`ADR-006-model-profile-switching-and-public-provider-scope`、`ADR-008-definition-term-index`、`ADR-012-domain-model-freeze`

## 待确认

- caller policy 白名单下 `suggestedTypes[]` 的最终返回策略和具体集成方名单。
- `PointDiagnostic` 在未来管理台 API 与 TuningPacket API 中的物理字段拆分。
- Pilot / Production Readiness 阶段的受控敏感诊断导出审批矩阵。
