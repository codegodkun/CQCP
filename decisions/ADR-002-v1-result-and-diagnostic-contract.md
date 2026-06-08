# ADR-002: V1 Result and Diagnostic Contract

Status: Proposed

日期：2026-06-07

## 背景

项目核心约束要求业务 finding 与 `SYS-*` 系统诊断分流。外部 API、普通结果页、管理台和 ReviewResultSnapshot 都需要一致解释 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`，否则后端、前端和外部系统可能各自解释状态。

## 适用范围

- 影响模块：后端、前端、SAP/OA API、数据库快照、AI 审核。
- 影响阶段：V1 MVP / Pilot。
- 是否影响外部 API：是。
- 是否影响数据库或版本快照：是。
- 是否影响模型、规则、prompt、正则或证据选择：是，影响模型输出进入裁判前的诊断映射。

## 决策

待确认。

本 ADR 需要确认：

- 点级状态枚举：`PASS`、`WARNING`、`ERROR`、`NOT_CONCLUDED`、`SKIPPED`。
- 内部 `SYS-*` 如何映射为 `NOT_CONCLUDED`。
- 普通结果页可见字段。
- 管理台可见内部诊断字段。
- 外部 API 不应依赖的 diagnostic-only 字段。
- `PROVEN_REQUIRED_CLAUSE_ABSENT` 与普通 `EVIDENCE_NOT_FOUND` 的区分。
- `businessMessage` 是否只由后端固定模板生成。

## 备选方案

- 方案 A：外部 API 和普通结果页只暴露业务化状态与原因，管理台保留完整 `SYS-*` 诊断。
- 方案 B：外部 API 也暴露内部 `SYS-*` 技术码。
- 方案 C：普通结果页、管理台和外部 API 使用完全不同状态模型。

## 选择理由

- 待确认。

## 影响

### 正向影响

- 防止系统诊断被误当作业务风险。
- 让结果页围绕“审核点 -> 证据 -> 原文定位”解释。
- 降低 SAP/OA 依赖内部技术码的风险。

### 代价与风险

- 如果普通结果页隐藏过多细节，人工可能难以理解未结论原因。
- 如果外部 API 暴露过多内部诊断，后续演进会被调用方绑定。

## 不做什么

- 不让模型直接生成最终业务结论。
- 不把 `SYS-*` 计入业务风险统计。
- 不在普通结果页暴露 prompt、raw output、endpoint、stack trace 或 admin logs。
- 不把 `NOT_CONCLUDED` 解释为合同无风险。

## 回滚与迁移

- 回滚方式：在 scaffold 和 API 发布前修改 Proposed 内容。
- 数据迁移影响：若 ReviewResultSnapshot 已落库，状态契约变更需要迁移或兼容映射。
- 历史快照兼容性：Accepted 后必须保持历史快照可解释。

## 验证方式

- 用首批审核点检查每种状态是否有明确触发场景。
- 用普通结果页、管理台和外部 API 三个视角审查字段可见性。
- 用 `SYS-*` 示例检查是否都能映射为业务化 `NOT_CONCLUDED`。

## 后续动作

- 与 `TASK-003-first-review-points-selection` 联动，验证首批审核点状态。
- 为后续 OpenAPI 和结果页任务提供状态契约。

## 关联

- 相关任务：`TASK-002-entry-order-decision`、`TASK-003-first-review-points-selection`
- 相关文档：`CURRENT_CONTEXT.md`、`ROADMAP.md`、`docs/frontend.md`、`docs/backend.md`、`docs/ai-review.md`、`docs/sap.md`
- 相关版本：待确认。

## 待确认

- 外部 API 是否返回 `suggestedTypes[]`，以及 caller policy 默认值。
- 普通结果页是否展示 `criticalSlotCoverageRate` 百分比或仅展示高/中/低。
- `requiresHigherBudget` 是否进入第一轮 MVP。
- `notConcludedDetail` 在 MVP 中是否只供管理台使用。
