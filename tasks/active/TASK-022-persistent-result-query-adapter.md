# TASK-022 Persistent Result Query Adapter 最小持久化查询适配层

状态：已完成并提交  
类型：A 类核心链路后端开发  
优先级：P0  
负责人：Codex

关联：
- `CURRENT_CONTEXT.md`
- `tasks/MVP_TASK_MAP.md`
- `tasks/TEMPLATE_ROUTER.md`

## 背景

`TASK-021` 已提供 `GET /api/v1/tasks/{taskId}/result` 查询接口，但当时结果读取仍基于 `InMemoryTaskResultStore`。  
MVP 需要把公开结果查询从内存态切到数据库持久化快照，保证服务重启后仍可按 `taskId` 查询结果。

`TASK-022` 的目标是在不修改 `TASK-019 ResultComposer`、`TASK-020 TaskExecutionStateMachine`、`TASK-021 API 语义` 的前提下，补上最小持久化查询适配层。

## 目标

1. 为 `GET /api/v1/tasks/{taskId}/result` 接入数据库持久化查询。
2. 复用既有 `ReviewResultSnapshot` 结构，不扩展公开返回契约。
3. 保持 `200 / 404 / 409` 语义不变：
   - `200`：存在结果快照
   - `404`：任务不存在
   - `409`：任务存在但尚无结果
4. 不改执行状态机，不触发重跑，不补 stage log，不修改 execution 状态。

## Task Context

### Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `tasks/MVP_TASK_MAP.md`
- `tasks/TEMPLATE_ROUTER.md`
- `tasks/active/TASK-021-result-url-query-api.md`

### Optional Context

- `docs/backend.md`
- `docs/database.md`
- `docs/context-management.md`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/InMemoryTaskResultStore.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskResultQueryService.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java`

### Out of Scope

- 前端页面实现
- 公开结果页展示改造
- 管理台诊断详情
- `PRD.md`
- `docs/ARCHITECTURE.md`
- Docker 配置
- `TASK-019 ResultComposer`
- `TASK-020 TaskExecutionStateMachine`
- `TASK-021` 的公开接口语义

## 明确不做

- 不修改 V1 schema 表结构
- 不新增迁移 SQL
- 不引入新 repository 抽象层
- 不修改 `ReviewResultSnapshot` 契约
- 不引入 execution 回放或补写
- 不写 stage log
- 不创建 `TASK_SPEC`
- 不分配 Claude Code / DeepSeek

## 实现前探查结论

1. V1 schema 已包含 `task`、`execution`、`task_stage_log`、`review_result_snapshot`。
2. `review_result_snapshot` 已能保存结果 JSON，适合直接作为公开查询来源。
3. `TASK-021` 已固定 `GET /api/v1/tasks/{taskId}/result` 的 200/404/409 语义，不应改动。
4. 后端当前技术栈允许使用 `JdbcTemplate + ObjectMapper` 做最小查询适配，无需引入额外持久化框架。

## 本轮实现结果

已完成以下实现：

- 新增 `PersistentTaskResultStore`
  - 基于 `JdbcTemplate + ObjectMapper`
  - 查询 `task` 与 `review_result_snapshot`
  - 以 `task_id + superseded_by_execution_id IS NULL + created_at DESC LIMIT 1` 选取当前有效结果
- 将 `GET /api/v1/tasks/{taskId}/result` 从 `InMemoryTaskResultStore` 切换到持久化查询实现
- 保持 `TASK-021` 定义的返回语义不变：
  - 有结果返回 `200`
  - 任务不存在返回 `404`
  - 任务存在但无结果返回 `409`
- 未修改 `ResultComposer`
- 未修改 `TaskExecutionStateMachine`
- 未新增 stage log 写入
- 未修改 execution 状态

## 涉及模块

- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/PersistentTaskResultStore.java`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/PersistentTaskResultStoreTest.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskResultQueryService.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskResultQueryController.java`

## 测试与验证

已完成验证：

- `gradle test --tests "*PersistentTaskResultStoreTest" --tests "*TaskResultQueryServiceTest" --tests "*TaskResultQueryControllerTest"`
- `gradle test --tests "*MinimalReviewEngineTest" --tests "*ResultComposerTest" --tests "*TaskExecutionStateMachineTest" --tests "*TaskResultQueryServiceTest" --tests "*TaskResultQueryControllerTest" --tests "*PersistentTaskResultStoreTest"`
- `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example ps`
- `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example exec postgres pg_isready -U cqcp -d cqcp`

## 边界检查结论

- 未修改后端核心审核链路行为
- 未修改数据库迁移 SQL
- 未修改 `PRD.md`
- 未修改 `docs/ARCHITECTURE.md`
- 未修改 Docker 配置
- 未进入 `TASK-023`
- 未进入 `TASK-024`
- 未进入 `TASK-031`

## 提交记录

- 父任务建档 commit：`dde34dd`
- 实现 commit：`1a206d7`

## 遗留与风险

- 公开结果页后续展示仍依赖 `TASK-023` 消费当前查询结果。
- 如后续发现公开页或管理台缺少必要字段，应单独评估是否进入 `TASK-031`，不能在 `TASK-022` 内扩边。
