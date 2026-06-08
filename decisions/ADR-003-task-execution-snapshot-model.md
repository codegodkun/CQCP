# ADR-003: Task Execution Snapshot Model

Status: Proposed

日期：2026-06-07

## 背景

架构文档要求 `taskId` 标识业务任务，`executionId` 标识一次具体审核执行，结果读取不可变 `ReviewResultSnapshot`。合同类型修正、预算升级、人工重跑或规则集重跑都不能覆盖旧执行记录。该模型会影响数据库、API、结果 URL、管理台历史查看和后续 correction execution。

## 适用范围

- 影响模块：后端、数据库、前端结果页、SAP/OA API、部署运维。
- 影响阶段：V1 MVP / Pilot。
- 是否影响外部 API：是。
- 是否影响数据库或版本快照：是。
- 是否影响模型、规则、prompt、正则或证据选择：间接影响，快照需绑定相关版本。

## 决策

待确认。

本 ADR 需要确认：

- `Task` 与 `Execution` 的职责边界。
- 同一 `taskId` 下 non-terminal execution 的并发限制。
- `ReviewResultSnapshot` 的最小字段。
- `supersededByExecutionId` 与 `supersededReason` 的 MVP 边界。
- 不带 `executionId` 的结果 URL 如何解析最新未 superseded execution。
- `maxExecutionsPerTask` 是否采用当前文档建议值。

## 备选方案

- 方案 A：保留 `Task / Execution / ReviewResultSnapshot` 三层模型，并使用不可变快照。
- 方案 B：只使用单层 Task 状态，重跑覆盖旧结果。
- 方案 C：先使用 Task + Result，后续再迁移到 Execution。

## 选择理由

- 待确认。

## 影响

### 正向影响

- 支持历史结果可追溯。
- 支持合同类型修正和预算升级后不覆盖旧结果。
- 让结果 URL、外部 API 和管理台历史查询使用同一模型。

### 代价与风险

- 三层模型比单层 Task 更复杂。
- 如果 MVP 暂不支持 correction execution，也需要避免未来迁移成本过高。

## 不做什么

- 不覆盖旧 execution 快照。
- 不删除最旧 execution 以规避数量限制。
- 不让 task 和 execution 维护互相竞争的审核状态。
- 不在本 ADR 中设计完整 ERD。

## 回滚与迁移

- 回滚方式：在数据库实现前修改 Proposed 内容。
- 数据迁移影响：Accepted 后若改成其他模型，需要迁移 task、execution、snapshot 和 result URL。
- 历史快照兼容性：Accepted 后必须保证历史快照不因规则、模型、parser 或 prompt 变化而改变。

## 验证方式

- 用普通任务、合同类型修正、预算升级、同 task 并发 execution、历史结果查询五个场景检查模型。
- 用结果 URL 规则检查不带 `executionId` 和带 `executionId` 的展示行为。

## 后续动作

- 与 `TASK-002-entry-order-decision` 联动，确认入口返回字段。
- 与后续数据库 ERD 和迁移任务联动，定义最小表结构。

## 关联

- 相关任务：`TASK-002-entry-order-decision`、`TASK-006-scaffold-only-after-adr`
- 相关文档：`CURRENT_CONTEXT.md`、`ROADMAP.md`、`docs/backend.md`、`docs/database.md`、`docs/sap.md`
- 相关版本：待确认。

## 待确认

- MVP 是否支持 correction execution。
- `maxExecutionsPerTask` 是否为 3。
- `supersededReason` MVP 枚举范围。
- Result URL 是否允许未登录访问。
- ReviewResultSnapshot 最小字段是否包含全部版本引用。
