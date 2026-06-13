# TASK-021 Result URL 查询接口最小实现

状态：已实现，待提交
类型：A 类核心链路后端开发
优先级：P0
负责人：Codex
创建日期：2026-06-13

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`tasks/TEMPLATE_ROUTER.md`

## 背景

`TASK-019` 已完成 `ReviewResultSnapshot` 最小合成。

`TASK-020` 已完成 `Task Execution` 最小状态机。

`TASK-021` 的目标是提供最小 Result URL 查询接口，让外部可以通过任务标识查询已生成的审核结果快照。

## 任务目标

实现一个最小只读查询接口，用于根据任务标识查询 `ReviewResultSnapshot`。

该接口只读取已有结果，不触发审核，不重新执行状态机，不修改任务状态。

## 任务性质

A 类核心链路后端开发，由 Codex 主控。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/MVP_TASK_MAP.md`
* `tasks/TEMPLATE_ROUTER.md`
* 本任务文件
* `tasks/active/TASK-019-result-composer-review-result-snapshot.md`
* `tasks/active/TASK-020-task-execution-state-machine.md`

### Optional Context

* `docs/backend.md`
* `docs/database.md`
* `docs/context-management.md`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ResultComposer.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java`

### Out of Scope

* 前端页面
* 任务创建接口
* 任务重新执行
* 异步调度
* 鉴权系统
* 权限模型
* 结果下载
* 数据库 schema 变更
* `PRD.md`
* `docs/ARCHITECTURE.md`

## 后续实现目标

提供最小只读结果查询接口。

建议接口形态，仅作为父任务阶段建议，不在本轮实现：

* `GET /api/v1/tasks/{taskId}/result`

返回内容应复用或映射 `ReviewResultSnapshot` 的最小字段。

建议成功/失败语义：

* 任务存在且已有结果：返回 `200 + snapshot`
* 任务不存在：返回 `404`
* 任务存在但尚未形成结果：返回 `409` 或 `404`

说明：

* `409` 还是 `404`，必须由 Codex 在后续实现前根据现有代码风格、错误响应风格和接口边界再确认。
* 父任务阶段只冻结建议边界，不落任何 Controller、Service、Repository 或测试实现代码。

## 明确不做

* 不做任务创建接口
* 不做任务重新执行
* 不做异步调度
* 不做鉴权系统
* 不做权限模型
* 不做前端页面
* 不做结果下载
* 不做数据库 schema 变更，除非建档后确认当前仓库已存在 schema 完全无法支持，并必须先暂停报告
* 不修改 `PRD.md`
* 不修改 `docs/ARCHITECTURE.md`

## 实现前必须检查

在 `TASK-021` 后续实现前，Codex 必须先检查：

* 当前是否已有 `Task / Execution / Snapshot` 相关实体或内存结构
* 当前 Controller 路由命名风格
* 当前错误响应风格
* 当前测试组织方式
* 是否已有 V1 API 包结构
* `TASK-019 ResultComposer` 的输出结构
* `TASK-020 TaskExecutionStateMachine` 的结果保存方式或内存态承接方式

## 预期涉及文件

当前仅建档，预计后续实现阶段才可能涉及：

* `tasks/active/TASK-021-result-url-query-api.md`
* `apps/api-server/src/main/java/com/cqcp/apiserver/...`
* `apps/api-server/src/test/java/com/cqcp/apiserver/...`
* `CURRENT_CONTEXT.md`
* `changelog/2026-06.md`

## 验收标准

后续实现阶段至少满足：

* 查询已有成功结果返回 `200`
* 查询不存在任务返回明确错误
* 查询尚未完成任务不触发执行
* 查询接口不改变 execution 状态
* 不新增数据库迁移
* 不触碰前端
* Docker Compose 标准环境保持可用
* 最小定向测试通过

## 执行顺序

1. 第一阶段：父任务建档，冻结边界
2. 第二阶段：Codex 自行实现最小后端查询接口
3. 第三阶段：定向测试
4. 第四阶段：Docker Compose 环境检查
5. 第五阶段：提交收口
6. 第六阶段：再判断是否拆给 Claude Code / DeepSeek 局部执行

当前只做第一阶段。

## 暂停条件

* 发现需要数据库迁移
* 发现需要修改 `PRD.md` 或 `docs/ARCHITECTURE.md`
* Docker Compose 环境异常
* 发现需要引入新的执行状态机能力，而不是单纯只读查询
* 任务范围扩展到前端、下载、鉴权或权限系统

## 文档更新要求

* `CURRENT_CONTEXT.md`：本轮更新为“TASK-021 已完成最小后端实现与验证，待提交收口”
* `changelog/2026-06.md`：记录 TASK-021 实现与验证事实
* `tasks/MVP_TASK_MAP.md`：仅同步 `TASK-021` 状态，不改变任务路线
* 本任务文件：写入本轮实现结果与验证结果

## 本轮实现结果

* 新增最小只读查询接口：`GET /api/v1/tasks/{taskId}/result`
* 新增最小 Spring MVC 入口与错误处理：
  * `TaskResultQueryController`
  * `TaskResultQueryService`
  * `TaskResultQueryExceptionHandler`
* 新增最小内存态结果承接层 `InMemoryTaskResultStore`：
  * 实现 `TaskExecutionPersistence`
  * 提供只读查询所需的 `TaskResultStore`
  * 复用 `TASK-020` 既有 `saveExecution / appendStageLog / saveSnapshot` 承接方式
* 查询语义确定为：
  * 任务存在且已有结果 -> `200`
  * 任务不存在 -> `404`
  * 任务存在但尚无结果 -> `409`
* 本轮未修改 `ResultComposer` 业务合成逻辑，未修改 `TaskExecutionStateMachine` 核心状态迁移逻辑。

## 本轮测试与验证

* 定向测试：
  * `gradle test --tests "*TaskResultQueryServiceTest" --tests "*TaskResultQueryControllerTest"` -> 通过
* 回归测试：
  * `gradle test --tests "*MinimalReviewEngineTest" --tests "*ResultComposerTest" --tests "*TaskExecutionStateMachineTest" --tests "*TaskResultQueryServiceTest" --tests "*TaskResultQueryControllerTest"` -> 通过
* Docker Compose 标准环境检查：
  * `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example ps` -> 3 个服务均为 `Up`
  * `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example exec postgres pg_isready -U cqcp -d cqcp` -> `accepting connections`

## 边界检查结论

* 未修改数据库迁移。
* 未修改 `PRD.md`。
* 未修改 `docs/ARCHITECTURE.md`。
* 未触碰前端。
* 未修改 Docker 配置。
* 查询接口只读，不触发审核，不重新执行状态机，不改变 execution 状态，不写 stage log。
