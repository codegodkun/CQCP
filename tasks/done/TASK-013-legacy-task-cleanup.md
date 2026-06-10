# TASK-013：Legacy Task Cleanup（历史任务清理）

状态：已完成
类型：项目记忆 / 任务治理 / 仓库整理

优先级：P0

负责人：Codex

创建日期：2026-06-09
完成日期：2026-06-09

来源：用户要求对 `tasks/active/` 中遗留任务进行清理，扫描 `TASK-001` ~ `TASK-012`，按完成状态、完成记录完整度和 ADR/冻结覆盖情况归档或保留。

## 背景

截至 `TASK-012` 完成后，`tasks/active/` 同时保留了已完成任务与未完成任务，导致活动任务列表不再反映真实在制项。后续继续推进 `TASK-004 / TASK-005 / TASK-006` 或创建新任务前，需要先完成一次历史任务清理，统一 active / done 目录边界，并把迁移结果写回项目记忆。

本任务只处理任务文档治理，不创建业务代码、不搭建脚手架、不安装依赖、不提前进入 OpenAPI 细节。

## 目标

- 扫描 `tasks/active/` 中的 `TASK-001` ~ `TASK-012`
- 读取每个任务的状态字段与完成记录完整度
- 按“归档到 done / 保留在 active / 标记 Superseded 或 Cancelled”分类
- 执行任务文档迁移，保证 `tasks/active/` 仅保留未完成任务
- 更新 `CURRENT_CONTEXT.md` 与 `changelog/2026-06.md`，完成 Memory Writeback

## 非目标

- 不修改业务架构边界
- 不创建或修改业务代码
- 不创建脚手架
- 不安装依赖
- 不新建 OpenAPI 契约内容
- 不变更已 Accepted ADR 的技术结论

## 输入

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `tasks/active/TASK-001-v1-mvp-scope-gate.md`
- `tasks/active/TASK-002-entry-order-decision.md`
- `tasks/active/TASK-003-first-review-points-selection.md`
- `tasks/active/TASK-004-word-parser-baseline-plan.md`
- `tasks/active/TASK-005-model-gateway-and-budget-baseline.md`
- `tasks/active/TASK-006-scaffold-only-after-adr.md`
- `tasks/active/TASK-007-first-review-point-definitions-draft.md`
- `tasks/active/TASK-008-architecture-prd-definition-index-alignment.md`
- `tasks/active/TASK-009-mvp-scope-freeze.md`
- `tasks/active/TASK-010-tech-stack-freeze.md`
- `tasks/active/TASK-011-repository-structure-freeze.md`
- `tasks/active/TASK-012-domain-model-freeze.md`
- `docs/context-management.md`

## 清理策略

### 归档到 `tasks/done/`

满足以下条件的任务归档：

- 状态为“已完成”
- 完成记录完整，至少包含完成日期、变更文件、测试结果
- 任务结论已写回长期记忆文件
- 不再作为当前 active workflow 的待执行项

### 保留在 `tasks/active/`

满足以下条件的任务保留：

- 状态不是“已完成”
- 或仍属于明确待执行前置任务
- 或尚未进入收尾状态

### Superseded / Cancelled 判定

仅当任务被后续 ADR 或冻结任务直接替代，且已不再需要继续执行时，才标记为 `Superseded` 或 `Cancelled`。

本次清理结论：`TASK-001` ~ `TASK-012` 中没有任务需要在本次直接标记为 `Superseded` 或 `Cancelled`。

原因：

- 已完成任务都形成了有效历史决策或项目记忆，不是“作废任务”
- 未完成任务 `TASK-004 / TASK-005 / TASK-006` 仍是当前真实 backlog

## 分类结果

### 归档到 `tasks/done/`

- `TASK-001-v1-mvp-scope-gate.md`
- `TASK-002-entry-order-decision.md`
- `TASK-003-first-review-points-selection.md`
- `TASK-007-first-review-point-definitions-draft.md`
- `TASK-008-architecture-prd-definition-index-alignment.md`
- `TASK-009-mvp-scope-freeze.md`
- `TASK-010-tech-stack-freeze.md`
- `TASK-011-repository-structure-freeze.md`
- `TASK-012-domain-model-freeze.md`

### 保留在 `tasks/active/`

- `TASK-004-word-parser-baseline-plan.md`
- `TASK-005-model-gateway-and-budget-baseline.md`
- `TASK-006-scaffold-only-after-adr.md`

### 本次未使用 `Superseded / Cancelled`

- 无

## 验收标准

- `tasks/active/` 只保留未完成任务
- `tasks/done/` 收纳所有已完成且完成记录完整的任务
- `TASK-013` 记录清理策略和每个任务的最终状态
- `CURRENT_CONTEXT.md` 明确写回 active backlog 已完成清理
- `changelog/2026-06.md` 记录本次迁移操作

## 测试与验证

- 本任务为文档治理与文件整理任务，不运行代码测试
- 验证方式为人工核对任务状态、完成记录字段和迁移后的目录结果

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`
- 必须更新 `changelog/2026-06.md`
- 必须更新本任务完成记录
- 不要求新增 ADR；本任务不涉及架构边界调整

## 风险

- 如果直接删除历史任务而不保留完成记录，会破坏项目长期记忆
- 如果把未完成任务误归档，会破坏后续 workflow
- 如果沿用旧“下一步”编号而不修正，会造成任务编号冲突

## 待确认

- 原 `CURRENT_CONTEXT.md` 中“创建并执行 TASK-013 最小 OpenAPI 契约”的编号已被本任务占用；后续最小 OpenAPI 契约任务需重新编号

## 完成记录

- 完成日期：2026-06-09
- 变更文件：
  - `tasks/done/TASK-013-legacy-task-cleanup.md`
  - `CURRENT_CONTEXT.md`
  - `changelog/2026-06.md`
  - `tasks/done/TASK-001-v1-mvp-scope-gate.md`
  - `tasks/done/TASK-002-entry-order-decision.md`
  - `tasks/done/TASK-003-first-review-points-selection.md`
  - `tasks/done/TASK-007-first-review-point-definitions-draft.md`
  - `tasks/done/TASK-008-architecture-prd-definition-index-alignment.md`
  - `tasks/done/TASK-009-mvp-scope-freeze.md`
  - `tasks/done/TASK-010-tech-stack-freeze.md`
  - `tasks/done/TASK-011-repository-structure-freeze.md`
  - `tasks/done/TASK-012-domain-model-freeze.md`
- 测试结果：未运行代码测试；已人工核对任务状态字段、完成记录字段与迁移结果
- 遗留问题：后续最小 OpenAPI 契约任务需重新编号，避免与本次清理任务冲突
- 备注：本任务未创建业务代码、未搭建脚手架、未安装依赖、未修改 ADR 结论
