# TASK_SPEC-MVP-001-F1 后端创建任务集成测试隔离修复

## 基本信息

- 父任务：`TASK-MVP-001-contract-review-user-loop-demo`
- Task Level：L2 Feature 内定点修复
- 状态：`DONE / ARCHIVED_WITH_FEATURE / DUAL_AUDIT_GO / FEATURE_PR_PENDING`
- 触发来源：`TASK_SPEC-MVP-001-F` 全量回归
- 冻结基线：`539c9e6d026fcac112831118022b451e7632c40c`

## Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `tasks/done/TASK-MVP-001-contract-review-user-loop-demo.md`
- `tasks/done/TASK_SPEC-MVP-001-F-docker-compose-real-demo-acceptance.md`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationIntegrationTest.java`

## Optional Context

- 其他后端集成测试中的 `cqcp.review.worker.enabled=false` 与 FK 清理顺序

## Out of Scope

- 生产代码、API、migration、Worker 实现、审核语义和测试数据库结构
- 清空真实 Demo 数据库 `cqcp`

## 失败事实

强制执行 `gradle.bat test --no-daemon --rerun-tasks` 时，313 个测试中 16 个失败。失败全部发生在
`ReviewTaskCreationIntegrationTest.cleanDatabaseAndUploads()` 的 `DELETE FROM execution`：

- test context 未关闭 Single Worker，上一用例创建的 `QUEUED` execution 可被异步推进；
- 清理逻辑遗漏 `task_stage_log`、`review_result_snapshot`、`tuning_packet`、`point_diagnostic`；
- Worker 写入 `task_stage_log` 后，下一用例删除 `execution` 触发 FK 约束失败。

## 允许修改

- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationIntegrationTest.java`
- 本规格与父任务的状态/报告

## 验收断言

1. 该集成测试显式设置 `cqcp.review.worker.enabled=false`，创建任务测试不再与 Worker 并发。
2. `@BeforeEach` 按 FK 依赖顺序清理四张 execution 子表，再清理 `execution` 与 `task`。
3. 不改生产代码、migration、API 或审核语义。
4. 专用测试库已有残留子表记录时，强制全量后端测试通过。
5. 不重建测试库的情况下，再连续强制执行一次全量后端测试仍通过，证明清理可重复。

## 实施报告

### 修改

- `ReviewTaskCreationIntegrationTest` 显式设置
  `cqcp.review.worker.enabled=false`，避免创建任务断言与 Single Worker 并发。
- `@BeforeEach` 按 FK 依赖顺序清理 `point_diagnostic`、`tuning_packet`、
  `review_result_snapshot`、`task_stage_log`，再清理 `execution`、`task`。
- 未修改生产代码、migration、API、Worker 实现或审核语义。

### 原始验证结论

- 定向 `ReviewTaskCreationIntegrationTest`：16/16 通过。
- 第一次全量强制回归：313/313 通过。
- 未重建 test DB 连续第二次全量强制回归：313/313 通过。
- review-assets validator：7/7 通过。
- admin-web：62/62、lint、production build 通过。
- `git diff --check`：通过。

### Review Intake

`ACCEPTED`。失败根因是测试上下文与 Single Worker 并发、且清理遗漏 FK 子表；
修复局限于测试隔离，不改变业务行为。
