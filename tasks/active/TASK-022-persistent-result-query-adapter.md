# TASK-022 Persistent Result Query Adapter 最小持久化查询适配层

状态：已建档
类型：A 类核心链路后端开发
优先级：P0
负责人：Codex
创建日期：2026-06-13

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`tasks/TEMPLATE_ROUTER.md`

## 背景

`TASK-021` 已提供 `GET /api/v1/tasks/{taskId}/result` 最小只读查询接口。

当前 `TASK-021` 的结果承接仍是 `InMemoryTaskResultStore`，只适合 MVP 内存态闭环，不是最终持久化结果查询实现。

`TASK-022` 的目标不是新增接口，而是把查询接口后面的结果来源，从内存态承接层推进到真实数据库 query adapter，优先面向已有 V1 schema 的 `review_result_snapshot / execution / task` 语义。

## 任务目标

本轮只做父任务建档，不写代码。

后续实现目标：

1. 增加最小持久化结果查询适配层。
2. 让 `TASK-021` 已有查询接口可以从持久化结果来源读取 `ReviewResultSnapshot` 或最小可返回结果。
3. 保持 `GET /api/v1/tasks/{taskId}/result` 接口路径和外部语义不变。
4. 不触发审核。
5. 不重跑 `TaskExecutionStateMachine`。
6. 不改变 execution 状态。
7. 不写 stage log。
8. 尽量不改数据库 schema。

## 任务性质

A 类核心链路后端开发，由 Codex 主控。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/MVP_TASK_MAP.md`
* `tasks/TEMPLATE_ROUTER.md`
* 本任务文件
* `tasks/active/TASK-021-result-url-query-api.md`

### Optional Context

* `docs/backend.md`
* `docs/database.md`
* `docs/context-management.md`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/InMemoryTaskResultStore.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskResultQueryService.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java`

### Out of Scope

* 前端页面
* 新任务创建接口
* 任务重新执行
* 异步调度系统
* 鉴权 / 权限模型
* 结果下载
* `PRD.md`
* `docs/ARCHITECTURE.md`
* Docker 配置

## 明确不做

* 不新增前端页面
* 不新增任务创建接口
* 不做异步调度系统
* 不做鉴权 / 权限模型
* 不做结果下载
* 不修改 `PRD.md`
* 不修改 `docs/ARCHITECTURE.md`
* 不改 Docker 配置
* 不重构 `TASK-019 ResultComposer`
* 不重构 `TASK-020 TaskExecutionStateMachine`
* 不改 `TASK-021` 对外接口路径
* 不引入复杂 repository 架构
* 不做大范围数据库模型改造

如果实现前发现现有 schema 无法支持最小持久化查询，应暂停报告，不得直接修改迁移。

## 实现前必须探查的问题

1. 当前 V1 schema 中 `task / execution / review_result_snapshot / task_stage_log` 的真实字段。
2. 当前项目是否已有 JPA / JDBC / Repository 使用方式。
3. 当前测试是否适合使用 Testcontainers、SpringBootTest、JdbcTemplate，或只做更薄的适配层单测。
4. `ReviewResultSnapshot` 当前是否可直接序列化 / 反序列化。
5. `TASK-021 InMemoryTaskResultStore` 如何替换或抽象为持久化查询来源。
6. 是否需要保留内存态作为测试替身。
7. 是否需要数据库迁移；默认目标是不需要。

## 预期涉及模块

* `reviewengine`
* `task / execution / review_result_snapshot` 查询承接层
* 持久化查询适配层
* 后端定向测试

## 验收标准

后续实现阶段至少满足：

* 已有结果可以从持久化来源查询并返回 `200`
* 不存在 `taskId` 返回 `404`
* `task` 存在但没有 result snapshot 返回 `409` 或沿用 `TASK-021` 已定语义
* 查询不触发审核
* 查询不改变 execution 状态
* 查询不写 stage log
* `GET /api/v1/tasks/{taskId}/result` 对外路径不变
* `TASK-021` 既有 Controller 测试不被破坏
* 新增最小持久化查询适配层测试
* Docker Compose 标准环境检查通过
* 不新增数据库迁移，除非先暂停并获得确认

## 执行顺序

1. 第一阶段：父任务建档，冻结边界。
2. 第二阶段：Codex 探查现有 V1 schema 与后端持久化访问风格。
3. 第三阶段：Codex 实现最小持久化查询适配层。
4. 第四阶段：定向测试与必要回归。
5. 第五阶段：Docker Compose 环境检查。
6. 第六阶段：提交收口。
7. 第七阶段：再判断是否适合拆给 Claude Code / DeepSeek 局部执行。

当前只做第一阶段。

## 暂停条件

* 发现需要数据库迁移
* 发现需要修改 `PRD.md` 或 `docs/ARCHITECTURE.md`
* Docker Compose 环境异常
* 发现必须引入异步队列或完整调度系统
* 任务范围扩展到前端或真实 Word 解析
* 需要 Claude Code / DeepSeek 介入父任务

## 文档更新要求

* `CURRENT_CONTEXT.md`：更新为“TASK-021 已完成并提交；当前主线切换为 TASK-022 父任务建档”
* `changelog/2026-06.md`：记录 TASK-022 建档事实，以及 TASK-021 完成态状态一致性修正
* `tasks/MVP_TASK_MAP.md`：同步 TASK-021 状态与 TASK-022 任务编号/任务路线
* 如有必要，修正 `tasks/active/TASK-021-result-url-query-api.md` 中“待提交”表述
