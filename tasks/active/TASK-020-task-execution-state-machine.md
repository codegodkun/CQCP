# TASK-020 Task Execution 最小状态机

状态：已完成
类型：A 类核心链路后端开发
优先级：P0
负责人：Codex
创建日期：2026-06-13
完成日期：2026-06-13

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`tasks/TEMPLATE_ROUTER.md`

## 背景

`TASK-019` 已完成正式最小 `ResultComposer + ReviewResultSnapshot` 合成，`INFRA-001` 已完成 Docker Compose 唯一标准开发环境收口。下一步需要把 `Task / Execution / Stage` 串成最小可执行闭环，但不能扩成完整调度系统。

## 任务目标

补齐 `Task / Execution / Stage` 的最小状态迁移、阶段日志，以及与 `ReviewResultSnapshot` 的衔接，形成最小串行执行闭环。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/MVP_TASK_MAP.md`
* `tasks/TEMPLATE_ROUTER.md`
* `tasks/active/TASK-019-result-composer-review-result-snapshot.md`

### Optional Context

* `docs/backend.md`
* `docs/database.md`
* `docs/context-management.md`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ResultComposer.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`

### Out of Scope

* 前端页面
* 真实 Word 解析
* AI 调优包导出
* 异步队列和完整调度系统
* 多租户、权限系统、并发执行扩展
* 前端 5 个 vulnerabilities 处理

## 本轮实现

### 新增代码

* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java`

实现内容：

* 新增最小 `TaskExecutionStateMachine`
* 新增最小执行输入/输出与持久化抽象：
  * `TaskExecutionRequest`
  * `ReviewTaskRecord`
  * `TaskExecutionRecord`
  * `TaskStageLogEntry`
  * `TaskExecutionRunResult`
  * `TaskExecutionPersistence`
* 新增最小 `ExecutionStatus`，字段和值对齐 V1 schema
* 串行执行路径固定为：
  * `CREATED -> REVIEWING_RULES -> COMPOSING -> SUCCESS / PARTIAL_SUCCESS / FAILED`
* 在 `REVIEWING_RULES`、`COMPOSING` 两个阶段记录最小 stage log：
  * `STARTED`
  * `COMPLETED`
  * `FAILED`
* 直接复用现有 `MinimalReviewEngine`
* 直接复用现有 `ResultComposer`
* 终态 execution 禁止重复执行
* 失败路径会写入失败状态和失败 stage log 后再抛出异常

### 新增测试

* `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachineTest.java`

覆盖场景：

* 成功路径：生成正式 `ReviewResultSnapshot`，并记录完整最小阶段日志
* 部分成功路径：`SYS-*` diagnostics 继续只留在 `diagnostics`，不进入业务 `findings`
* 失败路径：review 阶段抛错时，execution 进入 `FAILED`，并记录失败 stage log
* 终态保护：`SUCCESS` execution 不允许重复执行

## V1 schema 复用判断

本轮未修改数据库迁移 SQL。

复用方式：

* `execution.status` 复用 V1 已冻结状态集合
* `execution.current_stage` 复用 V1 已冻结阶段/终态口径
* `task_stage_log` 复用 V1 已冻结最小字段语义：
  * `stage_name`
  * `attempt`
  * `event_type`
  * `summary_status`
  * `business_reason`
  * `duration_ms`
  * `detail_payload`
* `review_result_snapshot` 继续复用 `TASK-019` 已冻结正式最小快照结构

本轮仅实现最小内存态持久化抽象，不引入真实数据库 adapter；后续如需数据库接入，应在后续任务内继续推进。

## 明确不做

* 不做完整任务调度系统
* 不做异步队列
* 不做并发执行扩展
* 不做多租户
* 不做权限系统
* 不做前端页面
* 不做真实 Word 解析
* 不做 AI 调优包导出
* 不处理前端 5 个 vulnerabilities
* 不重复 `TASK-019`
* 不修改 `PRD.md`
* 不修改 `docs/ARCHITECTURE.md`
* 不修改数据库迁移 SQL

## 验证结果

### Docker Compose 环境

* `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example ps`：通过
* `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example exec postgres pg_isready -U cqcp -d cqcp`：返回 `accepting connections`

### 后端定向测试

* `gradle test --tests "*TaskExecutionStateMachineTest"`：通过
* `gradle test --tests "*MinimalReviewEngineTest" --tests "*ResultComposerTest" --tests "*TaskExecutionStateMachineTest"`：通过

## 结果结论

`TASK-020` 已在冻结边界内完成最小串行执行闭环，未触发 ADR，未触碰前端、PRD、ARCHITECTURE 或数据库迁移 SQL。

下一优先任务为：`TASK-021 Result URL 查询接口最小实现`。
