# TASK-020：Task Execution 最小状态机

状态：待开始
类型：A 类核心链路后端开发

优先级：P0
负责人：Codex
创建日期：2026-06-13

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`tasks/TEMPLATE_ROUTER.md`

## 背景

`TASK-019` 已完成正式最小 `ResultComposer + ReviewResultSnapshot` 合成，`INFRA-001` 已完成 Docker Compose 唯一标准开发环境收口。当前下一优先主线任务为补齐 `Task / Execution / Stage` 的最小状态迁移、阶段日志，以及与 `ReviewResultSnapshot` 的衔接，形成最小串行执行闭环。

根据 `tasks/MVP_TASK_MAP.md`，`TASK-020` 属于 A 类核心链路任务，涉及 execution 状态机边界，必须由 Codex 主控，不得直接派发给 Claude Code / DeepSeek。

## 任务目标

补齐 `Task / Execution / Stage` 的最小状态迁移、阶段日志，以及与 `ReviewResultSnapshot` 的衔接，形成最小串行执行闭环。

## 任务性质

A 类核心链路后端开发，由 Codex 主控。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/MVP_TASK_MAP.md`
* `tasks/TEMPLATE_ROUTER.md`
* 本任务包
* `tasks/active/TASK-019-result-composer-review-result-snapshot.md`

### Optional Context

* `docs/backend.md`
* `docs/database.md`
* `docs/context-management.md`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ResultComposer.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`

### Out of Scope

* 前端页面。
* 真实 Word 解析。
* AI 调优包导出。
* 异步队列和完整调度系统。
* 多租户、权限系统、并发执行扩展。
* 前端 5 个 vulnerabilities 处理。

## 最小实现范围

* 最小 `Execution` 状态流转。
* 最小 `Stage` 日志记录。
* 串行调用现有 `MinimalReviewEngine`。
* 调用已有 `ResultComposer` 生成 `ReviewResultSnapshot`。
* 覆盖成功、失败、终态不可重复执行等最小测试。
* 优先复用现有 V1 schema。

## 明确不做

* 不做完整任务调度系统。
* 不做异步队列。
* 不做并发执行扩展。
* 不做多租户。
* 不做权限系统。
* 不做前端页面。
* 不做真实 Word 解析。
* 不做 AI 调优包导出。
* 不处理前端 5 个 vulnerabilities。
* 不重复 `TASK-019`。
* 不修改 `PRD.md`。
* 不修改 `docs/ARCHITECTURE.md`。
* 不修改数据库迁移 SQL，除非实现前明确发现 V1 schema 无法承载，并先暂停确认。

## 预计涉及模块

* `task`
* `execution`
* `stage`
* `reviewengine`
* `ReviewResultSnapshot`
* `ResultComposer`
* `MinimalReviewEngine`
* `Admin diagnostics` 相邻模块
* 后端测试

## 禁止修改范围

* `TASK-019` 已完成实现。
* `INFRA-001` 已完成配置。
* `PRD.md`
* `docs/ARCHITECTURE.md`
* 数据库迁移 SQL
* 前端 vulnerabilities
* 前端页面

## 数据库判断

* 目前预判不需要数据库迁移。
* `TASK-020` 应优先复用 `TASK-015` 已冻结的 V1 schema。
* 如果实现前发现 schema 不足，必须暂停并报告，不得直接修改迁移 SQL。

## Claude Code / DeepSeek 判断

* 当前不需要拆 `TASK_SPEC`。
* `TASK-020` 父任务由 Codex 主控。
* Claude Code / DeepSeek 不得直接接父 `TASK`。
* 只有 Codex 后续拆出边界冻结的 `TASK_SPEC` 后，才允许局部执行。

## 预计涉及文件

* `tasks/active/TASK-020-task-execution-state-machine.md`
* `apps/api-server/src/main/java/com/cqcp/apiserver/...`
* `apps/api-server/src/test/java/com/cqcp/apiserver/...`
* `CURRENT_CONTEXT.md`
* `changelog/2026-06.md`

## 验收标准

* Docker Compose 标准环境保持可用。
* 后端 `TASK-020` 定向测试通过。
* 成功路径能产生最小 `ReviewResultSnapshot`。
* 失败路径能记录失败状态和阶段日志。
* 终态 `execution` 不允许重复执行。
* `SYS-* diagnostics` 不抬升为业务 finding。
* 不引入数据库迁移。
* 不引入前端页面。
* Git 工作区收口干净。

## 验收命令

* `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example ps`
* `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example exec postgres pg_isready -U cqcp -d cqcp`
* 后端定向测试命令根据实际测试类命名确定，例如：
  `gradle test --tests "*TaskExecution*"`

## 暂停条件

* 发现需要数据库迁移。
* 发现需要修改 `PRD.md` 或 `docs/ARCHITECTURE.md`。
* Docker Compose 环境异常。
* 需要引入异步队列或完整调度系统。
* 任务范围扩展到前端或真实 Word 解析。
* 需要 Claude Code / DeepSeek 介入父任务。

## 文档更新要求

* `CURRENT_CONTEXT.md`：任务完成后更新。
* `changelog/2026-06.md`：任务完成后更新。
* 本任务包：任务完成后更新。
* ADR：当前预判不需要；如实现中触发架构、数据库、审核链路、模型职责或治理边界变化，必须先暂停并补 ADR。

## 备注

* 本轮仅创建父任务文件并冻结边界，不进入代码实现。
