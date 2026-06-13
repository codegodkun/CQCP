# CURRENT_CONTEXT.md

更新日期：2026-06-13

## 1. 当前阶段

CQCP 已完成 MVP 主链路的前置基线、最小 Review Engine、正式 `ReviewResultSnapshot` 合成、`TASK-020 Task Execution 最小状态机`、`TASK-021 Result URL 查询接口最小实现`，以及 `TASK-022 Persistent Result Query Adapter 最小持久化查询适配层` 的最小实现与验证。

当前项目已具备：

* Docker Compose 唯一标准开发/验证/测试环境；
* 基于 V1 schema 的 `task / execution / task_stage_log / review_result_snapshot` 最小契约；
* 串行执行 `MinimalReviewEngine -> ResultComposer -> ReviewResultSnapshot` 的最小闭环；
* 只读 `GET /api/v1/tasks/{taskId}/result` 最小结果查询接口；
* 基于真实 PostgreSQL V1 schema 的最小持久化结果查询适配层。

`TASK-021` 已完成并提交，完成态 commit hash：`c5e4ddd`。
`TASK-022` 已完成最小实现与验证，当前处于待提交收口状态，尚未进入 `TASK-023`。

## 2. 当前关键结论

* MVP 第一批仍只支持中文 `.docx` 工程采购合同。
* 首批 9 个 core review point 已冻结，`SYS-*` 继续只保留在 diagnostics，不抬升为业务 finding。
* `TASK-019` 已完成正式最小 `ReviewResultSnapshot` 合成边界：
  * `ERROR / WARNING` 进入业务 `findings`
  * `PASS / NOT_CONCLUDED / SKIPPED` 不进入业务风险统计
  * `SYS-*` 仅保留在 `diagnostics`
* `TASK-020` 已完成最小串行执行闭环：
  * 最小 execution 状态流转：`CREATED -> REVIEWING_RULES -> COMPOSING -> SUCCESS / PARTIAL_SUCCESS / FAILED`
  * 最小 stage 日志记录：`REVIEWING_RULES`、`COMPOSING` 的 `STARTED / COMPLETED / FAILED`
  * 串行调用现有 `MinimalReviewEngine`
  * 调用现有 `ResultComposer` 生成正式 `ReviewResultSnapshot`
  * 终态 execution 禁止重复执行
  * 失败路径会落 execution 失败状态和失败 stage log
* `TASK-021` 已完成最小后端只读结果查询接口：
  * 新增 `GET /api/v1/tasks/{taskId}/result`
  * 查询接口只读，不触发审核、不重跑状态机、不修改 execution 状态、不写 stage log
* `TASK-022` 已将 `TaskResultStore` 的默认结果来源从内存态承接层推进到持久化查询适配层：
  * 新增 `PersistentTaskResultStore`
  * 采用 `JdbcTemplate + ObjectMapper` 直接读取 `task` 与 `review_result_snapshot`
  * 不修改 `TASK-021` 对外接口路径与 `200 / 404 / 409` 语义
  * 不修改 `TASK-019 ResultComposer` 核心逻辑
  * 不修改 `TASK-020 TaskExecutionStateMachine` 核心逻辑
  * 不新增数据库迁移
* `InMemoryTaskResultStore` 仍保留为 MVP 最小闭环测试替身，不再作为默认持久化查询实现。
* CQCP 当前唯一标准环境仍为 Docker Compose：
  * admin-web: `http://localhost:15173`
  * api-server health: `http://localhost:18080/actuator/health`
  * PostgreSQL: `localhost:54329`

## 3. 当前活跃任务

* `TASK-022 Persistent Result Query Adapter 最小持久化查询适配层` 已完成实现与验证，待提交收口：
  * 默认查询来源已切换为真实数据库 query adapter
  * 仍保持只读查询边界，不触发审核、不重跑状态机、不改变 execution 状态、不写 stage log
  * 当前未进入 `TASK-023` 普通结果页最小展示

## 4. 已完成任务

* `TASK-006` 纯技术脚手架与环境验证
* `TASK-015` Flyway V1 核心 schema 基线
* `TASK-016` MVP 开发前四个最小验证闭环
* `TASK-017` 首批 expected fixtures bootstrap
* `TASK-018` 最小 Review Engine 验证闭环
* `TASK-019` Result Composer + ReviewResultSnapshot 最小合成
* `TASK-020` Task Execution 最小状态机
* `TASK-021` Result URL 查询接口最小实现
* `INFRA-001` Docker 唯一标准开发环境收口

## 5. 当前阻塞项

* 无新的主线阻塞项。
* 在 Codex 默认 sandbox 下执行部分 Docker `exec` 命令时，仍可能因为本机 Docker pipe 权限受限而需要提权；这属于执行环境限制，不是当前主线代码阻塞。
* 前端镜像构建时发现的 5 个依赖 vulnerabilities 仍仅作为后续候选事项记录，本轮未处理。

## 6. 待确认事项

* `TASK-020` 当前仅完成最小内存态持久化抽象；是否需要进一步替换为真实数据库持久化 adapter，待后续任务明确，不在本轮扩展。
* `TASK-022` 当前采用 `JdbcTemplate + ObjectMapper` 的最小持久化查询适配层；后续是否需要沉淀为更正式的只读 query module 或局部 TASK_SPEC，待后续任务判断。

## 7. 下一步顺序

1. 先完成 `TASK-022 Persistent Result Query Adapter 最小持久化查询适配层` 的提交收口，不扩展到 `TASK-023`。
2. `TASK-022` 提交完成后，再进入 `TASK-023` 普通结果页最小展示父任务建档或边界确认。
3. 后续再推进管理台任务详情最小诊断与更长链路的 parser / candidate / evidence 接入。

## 8. 当前禁止推进

* 不提前进入异步队列、完整调度系统、并发执行扩展或多租户。
* 不修改 `PRD.md`、`docs/ARCHITECTURE.md` 或数据库迁移 SQL，除非后续任务明确触发并先行确认。
* 不处理前端 5 个 vulnerabilities，除非单独建任务。
* 不把非 Docker 启动方式重新写回为标准开发/验收路径。
* 在 `TASK-022` 提交收口前，不进入 `TASK-023`。

## 9. 长期记忆索引

* `PRD.md`：产品范围与 MVP 冻结基线
* `docs/ARCHITECTURE.md`：当前生效架构约束
* `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
* `decisions/ADR-003-task-execution-snapshot-model.md`
* `decisions/ADR-012-domain-model-freeze.md`
* `decisions/ADR-013-v1-core-schema-bootstrap.md`
* `tasks/active/TASK-020-task-execution-state-machine.md`
* `tasks/active/TASK-021-result-url-query-api.md`
* `tasks/active/TASK-022-persistent-result-query-adapter.md`
* `changelog/2026-06.md`
