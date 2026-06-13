# CURRENT_CONTEXT.md

更新日期：2026-06-13

## 当前阶段

CQCP 当前处于 MVP 主链路打通阶段。结果快照、执行状态机、结果查询 API 与持久化查询适配层已经完成，当前重点进入普通结果页最小展示收口。

当前可用本地环境：

* admin-web：`http://localhost:15173`
* api-server health：`http://localhost:18080/actuator/health`
* PostgreSQL：`localhost:54329`

## 活跃任务

* `TASK-023`：普通结果页最小展示
  * 状态：已完成最小实现，待提交
  * 任务文件：`tasks/active/TASK-023-public-result-page-minimal-display.md`
  * 当前实现只落在 `apps/admin-web/src`
  * 公开页只调用既有接口 `GET /api/v1/tasks/{taskId}/result`
  * 默认展示 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`
  * 公开页不展示 `SYS-*`、prompt、raw output、endpoint、stack trace、admin logs、secret

## 近期已完成任务

* `TASK-021`：Result URL 查询 API 已完成并提交
  * commit：`c5e4ddd`
* `TASK-022`：Persistent Result Query Adapter 已完成并提交
  * 父任务建档 commit：`dde34dd`
  * 实现 commit：`1a206d7`
* `TASK-023`：父任务建档已完成并提交
  * commit：`13d9783`

## 当前有效结果

* 后端结果链路已经形成：
  * `MinimalReviewEngine -> ResultComposer -> ReviewResultSnapshot`
  * `TaskExecutionStateMachine`
  * `GET /api/v1/tasks/{taskId}/result`
  * `PersistentTaskResultStore`
* `TASK-023` 当前前端最小实现已完成：
  * 新增 `publicResult/api.ts`
  * 新增 `publicResult/types.ts`
  * 新增 `publicResult/PublicResultPage.tsx`
  * 更新 `App.tsx`、`App.test.tsx`、`styles.css`
* `TASK-023` 已完成验证：
  * `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example ps`
  * `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example exec postgres pg_isready -U cqcp -d cqcp`
  * `npm.cmd run test`
  * `npm.cmd run build`
  * `npm.cmd run lint`

## 已接受 ADR

* `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
* `decisions/ADR-003-task-execution-snapshot-model.md`
* `decisions/ADR-012-domain-model-freeze.md`
* `decisions/ADR-013-v1-core-schema-bootstrap.md`

## 当前阻塞项

* 无明确功能阻塞。
* 当前仅有收口动作未完成：`TASK-023` 实现改动尚未提交。

## 待确认事项

* 待确认：普通结果页后续是否需要合同全文预览能力；当前仅支持 block 级定位摘要。
* 待确认：如果后续发现公开页展示字段不足，是否转入 `TASK-031` 处理 mapper / 公开视图补洞。
* 待确认：`TASK-024` 管理台诊断详情的具体推进时点。

## 当前禁止推进事项

* 不进入 `TASK-024`
* 不进入 `TASK-031`
* 不创建 `TASK_SPEC`
* 不分配 Claude Code / DeepSeek
* 不 push
* 不修改 `PRD.md`
* 不修改 `docs/ARCHITECTURE.md`
* 不修改数据库迁移 SQL
* 不修改 Docker 配置
* 不处理前端 5 个 vulnerabilities

## 下一步

1. 优先审阅并提交 `TASK-023` 当前改动。
2. 提交时只按显式文件列表 `git add`，禁止 `git add .`。
3. 提交完成前仍不得顺手进入 `TASK-024`、`TASK-031` 或预算诊断扩展。

## 参考路径

* `tasks/active/TASK-023-public-result-page-minimal-display.md`
* `tasks/active/TASK-021-result-url-query-api.md`
* `tasks/active/TASK-022-persistent-result-query-adapter.md`
* `tasks/MVP_TASK_MAP.md`
* `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
* `changelog/2026-06.md`
