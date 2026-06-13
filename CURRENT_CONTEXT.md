# CURRENT_CONTEXT.md

更新日期：2026-06-14

## 当前阶段

CQCP 当前处于 MVP 主链路打通后的治理与收口阶段。  
结果快照、执行状态机、结果查询 API、持久化结果查询适配层、普通结果页最小展示均已完成；当前重点是治理历史文档乱码并保持任务边界稳定。

当前可用本地环境：
- admin-web：`http://localhost:15173`
- api-server health：`http://localhost:18080/actuator/health`
- PostgreSQL：`localhost:54329`

## 活跃任务

- 当前无业务实现任务正在进行。
- 当前无未收口的治理文件编码清理任务；`tasks/MVP_TASK_MAP.md`、`tasks/active/TASK-022-persistent-result-query-adapter.md`、`changelog/2026-06.md` 已完成本轮治理。

## 近期已完成任务

- `TASK-021`：Result URL 查询 API 已完成并提交
  - commit：`c5e4ddd`
- `TASK-022`：Persistent Result Query Adapter 已完成并提交
  - 父任务建档 commit：`dde34dd`
  - 实现 commit：`1a206d7`
- `TASK-023`：父任务建档已完成并提交
  - commit：`13d9783`
- `TASK-023`：普通结果页最小实现已完成并提交，并已推送到 `origin/master`
  - commit：`b7ab8db`

## 当前有效结果

- 后端结果链路已经形成：
  - `MinimalReviewEngine -> ResultComposer -> ReviewResultSnapshot`
  - `TaskExecutionStateMachine`
  - `GET /api/v1/tasks/{taskId}/result`
  - `PersistentTaskResultStore`
- `TASK-023` 前端最小实现已完成：
  - 新增 `publicResult/api.ts`
  - 新增 `publicResult/types.ts`
  - 新增 `publicResult/PublicResultPage.tsx`
  - 更新 `App.tsx`、`App.test.tsx`、`styles.css`
- `TASK-023` 的本地与远程 `master` 已对齐到 `b7ab8db`
- `tasks/MVP_TASK_MAP.md` 已重建为可读 UTF-8 版本

## 已接受 ADR

- `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- `decisions/ADR-003-task-execution-snapshot-model.md`
- `decisions/ADR-012-domain-model-freeze.md`
- `decisions/ADR-013-v1-core-schema-bootstrap.md`

## 当前阻塞项

- 无功能阻塞。
- 无明确治理阻塞；本轮已完成 Markdown 长期记忆文件乱码清理复核。

## 待确认事项

- 待确认：公开结果页后续是否需要合同全文预览能力；当前仅支持 block 级定位摘要。
- 待确认：如后续发现公开页展示字段不足，是否转入 `TASK-031` 处理 mapper / 公开视图补洞。
- 待确认：`TASK-024` 管理台诊断详情的启动时点。

## 当前禁止推进事项

- 未建 `TASK-024` 父任务前，不进入 `TASK-024` 实现
- 不进入 `TASK-031`
- 不创建 `TASK_SPEC`
- 未冻结父任务边界前，不分配 Claude Code / DeepSeek
- 不修改 `PRD.md`
- 不修改 `docs/ARCHITECTURE.md`
- 不修改数据库迁移 SQL
- 不修改 Docker 配置

## 下一步

1. 如需推进管理台诊断能力，先创建 `TASK-024` 父任务并冻结边界。
2. 在 `TASK-024` 未建档前，不进入 `TASK-024` 实现，也不提前进入 `TASK-031`。
3. 若后续再发现历史乱码文档，继续按“确认事实后重写，不猜测补全”的原则单独治理。

## 参考路径

- `tasks/active/TASK-023-public-result-page-minimal-display.md`
- `tasks/active/TASK-021-result-url-query-api.md`
- `tasks/active/TASK-022-persistent-result-query-adapter.md`
- `tasks/MVP_TASK_MAP.md`
- `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- `changelog/2026-06.md`
