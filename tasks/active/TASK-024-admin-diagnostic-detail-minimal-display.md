# TASK-024：管理台诊断详情最小展示
状态：已完成
类型：B 类前端 / 管理台父任务

优先级：P1
负责人：Codex
创建日期：2026-06-14
最近更新：2026-06-15

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`docs/context-management.md`、`decisions/ADR-002-v1-result-and-diagnostic-contract.md`、`tasks/active/TASK-021-result-url-query-api.md`、`tasks/active/TASK-022-persistent-result-query-adapter.md`

## 背景

`TASK-021` 已冻结结果查询 API 语义，`TASK-022` 已将结果查询切到持久化快照，`TASK-023` 已完成公开结果页最小展示。

根据 `ADR-002`：
- 公开结果页只展示业务化结果
- 管理台可展示更丰富的诊断摘要
- 但管理台也不得展示完整 prompt、完整 raw output、endpoint secret、stack trace、admin logs、secret

因此，`TASK-024` 的目标不是改审核链路，也不是改公开结果 API 契约，而是在不混入 `TASK-023`、不提前进入 `TASK-031` 的前提下，基于方案 A 落地管理台诊断详情最小前端实现。

## 目标

- 基于方案 A 完成管理台诊断详情最小前端实现
- 明确 `TASK-024` 与 `TASK-023`、`TASK-031` 的分工
- 保持 `SYS-*` 管理台可见、公开页不可见
- 只消费现有 `GET /api/v1/tasks/{taskId}/result`
- 输出实现结果、验证结果与遗留边界

## 非目标

- 不写后端代码
- 不修改 `TASK-021` / `TASK-022` 后端核心逻辑
- 不修改数据库迁移 SQL
- 不修改 `PRD.md`
- 不修改 `docs/ARCHITECTURE.md`
- 不修改 Docker 配置
- 不创建 `TASK_SPEC`
- 不进入 `TASK-031`
- 不进入预算诊断扩展
- 不做 correction / deep review 入口

## 输入

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `docs/context-management.md`
- `tasks/MVP_TASK_MAP.md`
- `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- `tasks/active/TASK-021-result-url-query-api.md`
- `tasks/active/TASK-022-persistent-result-query-adapter.md`
- `tasks/active/TASK-023-public-result-page-minimal-display.md`
- `docs/frontend.md`

## Task Context

### Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- 本任务文件
- `docs/context-management.md`
- `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- `docs/frontend.md`

### Optional Context

- `tasks/active/TASK-021-result-url-query-api.md`
- `tasks/active/TASK-022-persistent-result-query-adapter.md`
- `tasks/active/TASK-023-public-result-page-minimal-display.md`
- `docs/backend.md`
- `docs/ARCHITECTURE.md`

### Out of Scope

- `TASK-023` 公开结果页展示扩展
- `TASK-031` Result API / Admin API mapper 补洞
- 预算诊断展示
- correction / deep review 入口
- 鉴权、单点登录、权限体系增强
- 审核链路、EvidenceSlot、CandidateResolver、ReviewPointFamily 机制修改

## 范围

### 包含

- 管理台诊断详情页面最小实现
- 管理台路由接入
- 基于 `taskId` 的查询入口
- `summary` 展示
- `reviewCompleteness` 展示
- 全部点级状态展示
- 点级 diagnostics 展示：
  - `diagnosticCode`
  - `businessReason`
  - `evidenceSummary`
  - `evidenceBlockIds`
- 前端样式与测试最小补齐

### 不包含

- 任意后端 DTO / mapper / controller / service 改动
- 任意数据库 schema / migration 改动
- 任意公开结果页增强
- 任意权限系统实现
- `contractName / currentStage / model summary / stageLogs` 字段补洞

## 约束

- 不进入 `TASK-031`
- 不创建 `TASK_SPEC`
- 不分配 Claude Code / DeepSeek
- 不修改数据库迁移 SQL
- 不修改 `PRD.md`
- 不修改 `docs/ARCHITECTURE.md`
- 不修改 Docker 配置
- 不修改公开结果页边界
- 若发现字段缺口，只记录风险，不顺手扩到新接口

## 方案 A 边界

### 仅消费现有字段

来自既有 `ReviewResultSnapshot`：
- `summary`
- `reviewCompleteness`
- `pointResults`
- `diagnostics`
- `enabledReviewPointsSnapshot`

### 页面最小展示

- 任务查询入口
- `summary`
- `reviewCompleteness`
- 全部点级状态
- 点级 diagnostics

### 不展示

- 完整 prompt
- raw output
- endpoint
- stack trace
- admin logs
- secret

### 已知仍缺失字段

- `contractName`
- `currentStage`
- `model summary`
- `stageLogs`

结论：
- 本轮不补后端字段。
- 若后续必须展示上述字段，应单独判断是否进入 `TASK-031`。

## 实现结果

### 页面与路由

- 新增页面路径：`/admin/diagnostics`
- 支持通过输入 `taskId` 查询
- 保留公开结果页根路由，不混入管理台展示

### 新增/修改文件

- `apps/admin-web/src/adminDiagnostics/api.ts`
- `apps/admin-web/src/adminDiagnostics/types.ts`
- `apps/admin-web/src/adminDiagnostics/AdminDiagnosticPage.tsx`
- `apps/admin-web/src/App.tsx`
- `apps/admin-web/src/App.test.tsx`
- `apps/admin-web/src/styles.css`

### 展示结果

- 已展示 `summary`
- 已展示 `reviewCompleteness`
- 已展示全部点级状态
- 已展示点级 diagnostics：
  - `diagnosticCode`
  - `businessReason`
  - `evidenceSummary`
  - `evidenceBlockIds`
- 已允许管理台可见 `SYS-*`

## 测试与验证

- `npm.cmd run test -- src/App.test.tsx`
- `npm.cmd run build`

## 文档更新要求

- 是否需要更新 `CURRENT_CONTEXT.md`：是
- 是否需要更新 `docs/*.md`：否
- 是否需要更新 `changelog/当前月份.md`：是
- 是否需要新增或更新 ADR：否
- 是否需要更新 `tasks/MVP_TASK_MAP.md`：是

## 风险

- 当前仍未提供 `contractName`、`currentStage`、`model summary`、`stageLogs`；若坚持展示这些字段，将触发 `TASK-031` 或新增管理台接口任务判断。
- 若 `TASK-024` 与 `TASK-023` 边界失守，容易把管理台诊断信息泄露到公开页。
- 若后续顺手扩大到后端字段补洞，容易把局部展示诉求升级成结果契约返工。

## 待确认

- 待确认：如后续产品要求管理台展示任务级头部诊断、模型摘要或阶段日志，是否正式进入 `TASK-031`。

## 完成记录

- 完成日期：2026-06-15
- 变更文件：
  - `apps/admin-web/src/adminDiagnostics/api.ts`
  - `apps/admin-web/src/adminDiagnostics/types.ts`
  - `apps/admin-web/src/adminDiagnostics/AdminDiagnosticPage.tsx`
  - `apps/admin-web/src/App.tsx`
  - `apps/admin-web/src/App.test.tsx`
  - `apps/admin-web/src/styles.css`
  - `tasks/active/TASK-024-admin-diagnostic-detail-minimal-display.md`
  - `CURRENT_CONTEXT.md`
  - `changelog/2026-06.md`
  - `tasks/MVP_TASK_MAP.md`
- 测试结果：
  - `npm.cmd run test -- src/App.test.tsx`
  - `npm.cmd run build`
- 遗留问题：
  - `contractName`、`currentStage`、`model summary`、`stageLogs` 仍不在现有接口返回中
  - 若后续必须展示上述字段，应单独判断是否进入 `TASK-031`
- 备注：
  - 本轮未修改后端代码、数据库迁移、`PRD.md`、`docs/ARCHITECTURE.md` 或 Docker 配置
  - 本轮未创建 `TASK_SPEC`，未分配 Claude Code / DeepSeek
