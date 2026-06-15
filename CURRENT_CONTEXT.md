# CURRENT_CONTEXT.md

更新日期：2026-06-15

## 当前阶段

CQCP 当前处于 MVP 主链路打通后的治理与收口阶段。

已完成：
- 结果快照与结果合成
- 最小执行状态机
- `GET /api/v1/tasks/{taskId}/result`
- 持久化结果查询适配
- `TASK-023` 普通结果页最小展示
- 治理文档历史乱码清理

当前重点：
- 保持 `TASK-023`、`TASK-024`、`TASK-031` 三者边界分离
- 在不扩大边界的前提下评估后续主链路任务与潜在字段补洞需求
- 补齐项目入口、开发流程与验收规则文档

当前可用本地环境：
- admin-web：前端本地开发端口已固定
- api-server：本地健康检查端口已固定
- PostgreSQL：本地数据库端口已固定

## 活跃任务

- 当前无新的活跃实现任务。

## 近期已完成任务

- `TASK-021`：Result URL 查询 API 已完成并提交
  - commit：`c5e4ddd`
- `TASK-022`：Persistent Result Query Adapter 已完成并提交
  - 父任务建档 commit：`dde34dd`
  - 实现 commit：`1a206d7`
- `TASK-023`：父任务建档已完成并提交
  - commit：`13d9783`
- `TASK-023`：普通结果页最小实现已完成并提交，且已推送到 `origin/master`
  - commit：`b7ab8db`
- `TASK-024`：管理台诊断详情最小实现已完成，已提交并已 push 到 `origin/master`
  - commit：`2339db5`
  - 本地 `master` 与远程 `origin/master` 已对齐到 `2339db5c5441ebb5841a2676c8276c51f2de0960`
  - 任务文件：`tasks/active/TASK-024-admin-diagnostic-detail-minimal-display.md`
- `2026-06-14`：治理文档乱码清理已完成并提交
  - commit：`d0349da`
- `2026-06-14`：垃圾文件 `-Pattern` 删除已完成并提交
  - commit：`e4dc93c`

## 当前有效结果

- 后端结果链路已经形成：
  - `MinimalReviewEngine -> ResultComposer -> ReviewResultSnapshot`
  - `TaskExecutionStateMachine`
  - `GET /api/v1/tasks/{taskId}/result`
  - `PersistentTaskResultStore`
- `TASK-023` 前端最小实现已完成：
  - `apps/admin-web/src/publicResult/api.ts`
  - `apps/admin-web/src/publicResult/types.ts`
  - `apps/admin-web/src/publicResult/PublicResultPage.tsx`
  - `apps/admin-web/src/App.tsx`
  - `apps/admin-web/src/App.test.tsx`
  - `apps/admin-web/src/styles.css`
- `TASK-023` 的本地与远程 `master` 已对齐到 `b7ab8db`
- `TASK-024` 的本地与远程 `master` 已对齐到 `2339db5c5441ebb5841a2676c8276c51f2de0960`
- `tasks/MVP_TASK_MAP.md` 已重建为可读 UTF-8 版本
- `TASK-024` 最小前端实现已完成：
  - `apps/admin-web/src/adminDiagnostics/api.ts`
  - `apps/admin-web/src/adminDiagnostics/types.ts`
  - `apps/admin-web/src/adminDiagnostics/AdminDiagnosticPage.tsx`
  - `apps/admin-web/src/App.tsx`
  - `apps/admin-web/src/App.test.tsx`
  - `apps/admin-web/src/styles.css`
- `TASK-024` 已确认并保持以下边界：
  - 只消费既有 `GET /api/v1/tasks/{taskId}/result`
  - 只展示 `summary`、`reviewCompleteness`、全部点级状态与点级 diagnostics
  - 管理台可见 `SYS-*`
  - 不展示 prompt、raw output、endpoint、stack trace、admin logs、secret
- `TASK-024` 当前页面路径为 `/admin/diagnostics`
- `TASK-024` 已通过前端最小验证：
  - `npm.cmd run test -- src/App.test.tsx`
  - `npm.cmd run build`
- 文档治理入口已补齐：
  - `README.md`
  - `docs/DEVELOPMENT.md`
  - `docs/VERIFY.md`

## 已接受 ADR

- `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- `decisions/ADR-003-task-execution-snapshot-model.md`
- `decisions/ADR-012-domain-model-freeze.md`
- `decisions/ADR-013-v1-core-schema-bootstrap.md`

## 当前阻塞项

- `TASK-024` 若坚持展示任务级诊断头部、当前 stage、模型摘要和阶段日志，现有已暴露接口字段不足。
- 当前未发现可直接消费的管理台诊断详情接口；若继续实现这些字段，存在进入 `TASK-031` 或新增管理台接口任务的风险。

## 待确认事项

- 待确认：公开结果页后续是否需要合同全文预览能力；当前仅支持 block 级定位摘要。
- 待确认：如后续发现公开页展示字段不足，是否转入 `TASK-031` 处理 mapper / 公开视图补洞。
- 待确认：公开结果页后续是否需要合同全文预览能力；当前仅支持 block 级定位摘要。

## 当前禁止推进事项

- 不进入 `TASK-031`
- 不创建 `TASK_SPEC`
- 不分配 Claude Code / DeepSeek
- 不修改 `PRD.md`
- 不修改 `docs/ARCHITECTURE.md`
- 不修改数据库迁移 SQL
- 不修改 Docker 配置
- 在字段缺口未确认前，不顺手扩大到管理台新接口或结果契约补洞

## 下一步

1. 如后续只需维持现有管理台最小诊断能力，可进入人工审阅、归档与提交阶段。
2. 若后续明确需要 `contractName / currentStage / model summary / stageLogs`，应单独判断是否进入 `TASK-031`。
3. `TASK-025 ~ TASK-030` 仍为后续主链路深化候选，不受本轮前端最小实现影响。
4. 后续如继续推进仓库治理，可在 `README.md`、`docs/DEVELOPMENT.md`、`docs/VERIFY.md` 基础上补充更细的模块级协作文档。

## 参考路径

- `tasks/active/TASK-023-public-result-page-minimal-display.md`
- `tasks/active/TASK-024-admin-diagnostic-detail-minimal-display.md`
- `tasks/active/TASK-021-result-url-query-api.md`
- `tasks/active/TASK-022-persistent-result-query-adapter.md`
- `tasks/MVP_TASK_MAP.md`
- `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- `changelog/2026-06.md`
