# CURRENT_CONTEXT.md

更新日期：2026-06-13

## 1. 当前阶段

CQCP 已完成 MVP 主链路的前置基线、最小 Review Engine、正式 `ReviewResultSnapshot` 合成，以及 `TASK-020 Task Execution 最小状态机`。当前项目已具备：

* Docker Compose 唯一标准开发/验证/测试环境；
* 基于 V1 schema 的 `task / execution / task_stage_log / review_result_snapshot` 最小契约；
* 串行执行 `MinimalReviewEngine -> ResultComposer -> ReviewResultSnapshot` 的最小闭环。

当前优先主线任务为 `TASK-021 Result URL 查询接口最小实现`，最小后端实现与验证已完成，待提交收口。

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
* `TASK-020` 本轮未引入数据库迁移；实现优先复用了 `TASK-015` 冻结的 V1 schema 字段语义。
* CQCP 当前唯一标准环境仍为 Docker Compose：
  * admin-web: `http://localhost:15173`
  * api-server health: `http://localhost:18080/actuator/health`
  * PostgreSQL: `localhost:54329`

## 3. 当前活跃任务

* `TASK-021 Result URL 查询接口最小实现` 已完成最小后端只读查询接口实现：
  * 新增 `GET /api/v1/tasks/{taskId}/result`
  * 当前仓库此前不存在现成 Controller / REST API 包结构，本轮补充了最小 Spring MVC 查询入口
  * 当前仓库此前不存在统一错误响应风格，本轮采用 Spring `ProblemDetail` 返回 `404/409`
  * `ReviewResultSnapshot` 仍复用 `reviewengine/ResultComposer.java` 中既有记录类型
  * `TASK-020` 的 `TaskExecutionPersistence.saveSnapshot(...)` 通过本轮新增的最小内存承接层与查询接口衔接
  * 查询接口只读，不触发审核、不重新执行状态机、不修改 execution 状态、不写 stage log
  * 任务存在但结果尚未形成时，本轮采用 `409 Conflict`，理由是任务资源已存在但结果资源当前状态不可读取

## 4. 已完成任务

* `TASK-006` 纯技术脚手架与环境验证
* `TASK-015` Flyway V1 核心 schema 基线
* `TASK-016` MVP 开发前四个最小验证闭环
* `TASK-017` 首批 expected fixtures bootstrap
* `TASK-018` 最小 Review Engine 验证闭环
* `TASK-019` Result Composer + ReviewResultSnapshot 最小合成
* `TASK-020` Task Execution 最小状态机
* `INFRA-001` Docker 唯一标准开发环境收口

## 5. 当前阻塞项

* 无新的主线阻塞项。
* 运行 Gradle 定向测试时，Codex 默认 sandbox 仍可能因插件解析/网络权限受限而需要提权；这属于执行环境限制，不是当前 TASK-020 代码阻塞。
* 前端镜像构建时发现的 5 个依赖 vulnerabilities 仍仅作为后续候选事项记录，本轮未处理。

## 6. 待确认事项

* `TASK-021` 当前实现仍基于最小内存态结果承接层；何时接入真实数据库 query adapter，待后续任务明确，不在本轮扩展。
* `TASK-020` 当前仅完成最小内存态持久化抽象；后续何时接入真实数据库 adapter，待后续任务明确，不在本轮内扩展。

## 7. 下一步顺序

1. 先完成 `TASK-021 Result URL 查询接口最小实现` 的提交收口，不扩展到 TASK-022。
2. `TASK-021` 提交完成后，再推进普通结果页最小展示和管理台任务详情最小诊断。
3. 后续 parser / candidate / evidence 主链路接入，继续保持 Codex 主控，不提前扩成完整调度系统。

## 8. 当前禁止推进

* 不提前进入异步队列、完整调度系统、并发执行扩展或多租户。
* 不修改 `PRD.md`、`docs/ARCHITECTURE.md` 或数据库迁移 SQL，除非后续任务明确触发并先行确认。
* 不处理前端 5 个 vulnerabilities，除非单独建任务。
* 不把非 Docker 启动方式重新写回为标准开发/验收路径。
* `TASK-021` 当前阶段不得扩展到前端、下载、鉴权、任务重跑、异步调度或数据库迁移。

## 9. 长期记忆索引

* `PRD.md`：产品范围与 MVP 冻结基线
* `docs/ARCHITECTURE.md`：当前生效架构约束
* `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
* `decisions/ADR-003-task-execution-snapshot-model.md`
* `decisions/ADR-012-domain-model-freeze.md`
* `decisions/ADR-013-v1-core-schema-bootstrap.md`
* `tasks/active/TASK-020-task-execution-state-machine.md`
* `tasks/active/TASK-021-result-url-query-api.md`
* `changelog/2026-06.md`
