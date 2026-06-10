# ADR-003: Task Execution Snapshot Model

Status: Accepted

日期：2026-06-09

说明：本 ADR 冻结 `Task / Execution / ReviewResultSnapshot` 子模型；领域模型总览见 `ADR-012-domain-model-freeze.md`。

## 背景

架构文档要求 `taskId` 标识业务任务，`executionId` 标识一次具体审核执行，结果读取不可变 `ReviewResultSnapshot`。合同类型修正、预算升级、人工重跑或规则集重跑都不能覆盖旧执行记录。该模型会影响数据库、API、结果 URL、管理台历史查看和后续 correction execution。

## 适用范围

- 影响模块：后端、数据库、前端结果页、SAP/OA API、部署运维。
- 影响阶段：V1 MVP / Pilot。
- 是否影响外部 API：是。
- 是否影响数据库或版本快照：是。
- 是否影响模型、规则、prompt、正则或证据选择：间接影响，快照需绑定相关版本。

## 决策

V1 MVP 采用 `Task / Execution / ReviewResultSnapshot` 三层模型。

### Task

`Task` 标识一次业务审核请求，是外部系统、管理台或评测入口创建的稳定聚合。

`Task` 负责保存：

- `taskId`
- 创建来源与 caller 摘要
- 合同基础元数据
- 结构化字段输入快照
- result URL 基础入口
- 当前 latest execution 的查询入口

`Task` 不负责保存最终点级结果，不覆盖历史 execution，不维护一套与 execution 竞争的审核状态。

### Execution

`Execution` 标识同一 `Task` 下的一次具体审核执行。

`Execution` 负责保存：

- `executionId`
- 所属 `taskId`
- execution 状态机与当前 stage
- stage lease、heartbeat、attempt、timeout 和阶段日志
- 本次执行绑定的 `RuleSetVersion`、`ContractTypeProfile`、`ReviewBudgetProfile`、`ModelProfile`、parser / prompt / schema / pattern / field lexicon / EvidenceSelector 等版本引用

合同类型修正、预算升级、人工重跑、规则集重跑、模型升级或 parser 升级必须创建新的 `executionId`，不得覆盖旧 execution。

同一 `taskId` 下同时只能有一个 non-terminal execution。

MVP 普通执行上限采用：

```text
maxExecutionsPerTask = 3
```

超过上限时拒绝创建新的普通 execution，返回 `409 EXECUTION_LIMIT_REACHED`。系统不得覆盖或删除最旧 execution。管理员 recovery execution 可不占普通配额，但必须有审计记录。

### ReviewResultSnapshot

`ReviewResultSnapshot` 是 execution 生成的不可变正式结果快照，按 `taskId + executionId` 唯一绑定。

最小字段：

- `taskId`
- `executionId`
- `supersededByExecutionId?`
- `supersededReason?`
- `status`
- `summary`
- `reviewCompleteness`
- `pointResults`
- `findings`
- `diagnostics`
- `sourceAnchors`
- `structuredFieldsSnapshot`
- `enabledReviewPointsSnapshot`
- `disabledReviewPointsSnapshot`
- `contractTypeProfileVersion`
- `ruleSetVersion`
- `reviewBudgetProfileVersion`
- `modelProfileVersion`
- `parserVersion`
- `promptVersion`
- `schemaVersion`
- `patternLibraryVersion`
- `fieldLexiconVersion`
- `evidenceSelectorVersion`
- `createdAt`

`ReviewResultSnapshot` 一旦生成，不得被后续规则、模型、parser、prompt、配置或字段定义变化覆盖。普通结果页和历史结果查询必须读取快照。

### Result URL

普通结果 URL 形式：

```text
/review/results/{taskId}?executionId={executionId}
```

- 带 `executionId` 时展示指定历史 execution 的快照。
- 不带 `executionId` 时选择最新未被 superseded 的 terminal execution。
- 如无 terminal execution，可展示最新 non-terminal execution 的处理中状态。

### Superseded

`supersededReason` MVP 枚举：

- `TYPE_CORRECTION`
- `BUDGET_UPGRADE`
- `MANUAL_RERUN`
- `RULESET_RERUN`
- `MODEL_UPGRADE`
- `PARSER_UPGRADE`
- `ADMIN_RECOVERY`

被 superseded 的快照仍可通过 `executionId` 查询，不得删除或覆盖。

## 备选方案

- 方案 A：保留 `Task / Execution / ReviewResultSnapshot` 三层模型，并使用不可变快照。
- 方案 B：只使用单层 Task 状态，重跑覆盖旧结果。
- 方案 C：先使用 Task + Result，后续再迁移到 Execution。

## 选择理由

- 三层模型符合 `docs/ARCHITECTURE.md` 对异步任务、历史结果、不可变快照和 correction rerun 的约束。
- `Task` 与 `Execution` 分离后，同一业务请求可以保留多次执行历史，不需要覆盖旧结果。
- `ReviewResultSnapshot` 不可变可以保证普通结果 URL、管理台历史查看和外部 API 审计读取同一事实来源。
- `maxExecutionsPerTask=3` 能覆盖 MVP 的合同类型修正、预算升级和人工重跑，同时限制异常重复执行。

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

- 回滚方式：在数据库实现前通过新 ADR 或本 ADR 修订。
- 数据迁移影响：Accepted 后若改成其他模型，需要迁移 task、execution、snapshot 和 result URL。
- 历史快照兼容性：Accepted 后必须保证历史快照不因规则、模型、parser 或 prompt 变化而改变。

## 验证方式

- 用普通任务、合同类型修正、预算升级、同 task 并发 execution、历史结果查询五个场景检查模型。
- 用结果 URL 规则检查不带 `executionId` 和带 `executionId` 的展示行为。

## 后续动作

- 与 `TASK-002-entry-order-decision` 联动，确认入口返回字段。
- 与后续数据库 ERD 和迁移任务联动，定义最小表结构。
- 与 `TASK-013` 最小 OpenAPI 契约联动，定义任务创建、execution 查询和结果 URL 边界。

## 关联

- 相关任务：`TASK-002-entry-order-decision`、`TASK-006-scaffold-only-after-adr`、`TASK-012-domain-model-freeze`
- 相关文档：`CURRENT_CONTEXT.md`、`ROADMAP.md`、`docs/backend.md`、`docs/database.md`、`docs/sap.md`
- 相关 ADR：`ADR-012-domain-model-freeze`

## 待确认

- 完整 ERD、表命名、字段类型和索引策略。
- correction execution 的完整 API 与权限策略。
- `ReviewResultSnapshot` 中 JSONB 与关系字段的拆分方式。
