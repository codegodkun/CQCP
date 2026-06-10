# TASK-012：领域模型冻结

状态：已完成

类型：架构决策 / 领域模型 / scaffold 前置

优先级：P0

负责人：Codex

创建日期：2026-06-09

完成日期：2026-06-09

来源：`CURRENT_CONTEXT.md` 当前下一步要求创建并执行 `TASK-012`，冻结领域模型，明确 `Task / Execution / ReviewResultSnapshot / PointDiagnostic / TuningPacket` 的关系和边界。

## 背景

`TASK-010` 已冻结技术栈，`TASK-011` 已冻结仓库结构。进入 scaffold、数据库 ERD 或最小 OpenAPI 前，需要冻结核心领域模型边界，避免把业务任务、具体执行、正式结果快照、点级诊断和调优治理包混成可变单层对象。

本任务涉及数据库核心模型、不可变快照和生产审核链路 / 调优治理链路边界，因此必须记录 ADR。

## 目标

- 冻结 `Task / Execution / ReviewResultSnapshot / PointDiagnostic / TuningPacket` 的职责边界。
- 明确 `Task -> Execution -> ReviewResultSnapshot` 的关系、重跑和 superseded 规则。
- 明确 `PointDiagnostic / ExecutionSummary / TuningPacket` 属于调优治理与管理台诊断链路，不影响正式结果快照。
- 更新 `ADR-003`，使 `Task / Execution / ReviewResultSnapshot` 子模型进入 Accepted。
- 新增领域模型总览 ADR。
- 完成 Memory Writeback。

## 非目标

- 不创建业务代码。
- 不创建数据库 schema 或 Flyway migration。
- 不定义完整 ERD、表名、字段类型或索引。
- 不定义完整 OpenAPI。
- 不实现 correction execution。
- 不创建 TuningPacket 导出文件格式。
- 不改变 EvidenceSlot、CandidateResolver、ReviewPointFamily 或模型职责红线。

## 输入

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `docs/ARCHITECTURE.md`
- `docs/backend.md`
- `docs/database.md`
- `docs/ai-review.md`
- `docs/context-management.md`
- `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- `decisions/ADR-003-task-execution-snapshot-model.md`
- `decisions/ADR-007-tuning-packet-architecture.md`
- `decisions/ADR-009-ai-tuning-governance.md`
- `decisions/ADR-011-repository-structure-freeze.md`

## 冻结结论

V1 MVP 领域模型采用以下关系：

```text
Task
  1 -> n Execution
Execution
  1 -> 0..1 ReviewResultSnapshot
ReviewResultSnapshot
  1 -> n PointResult
  1 -> n SourceAnchor
  1 -> n DiagnosticSummary
ReviewResultSnapshot
  1 -> 0..n TuningPacket
TuningPacket
  1 -> n PointDiagnostic
  1 -> 1 ExecutionSummary
```

### `Task`

- 表示业务审核请求聚合。
- 保存 `taskId`、创建来源、caller、合同基础元数据、结构化字段输入快照和 result URL 基础入口。
- 聚合多个 `Execution`。
- 不保存点级结果，不覆盖历史 execution，不承载最终审核状态的权威解释。

### `Execution`

- 表示一次具体审核运行。
- 保存 `executionId`、所属 `taskId`、状态机、stage、lease、attempt、timeout 和阶段日志。
- 绑定当次 `RuleSetVersion`、`ContractTypeProfile`、`ReviewBudgetProfile`、`ModelProfile`、parser、prompt、schema、pattern、field lexicon 和 EvidenceSelector 等版本引用。
- 同一 `taskId` 下同时只能有一个 non-terminal execution。
- 合同类型修正、预算升级、人工重跑、规则集重跑、模型升级或 parser 升级必须创建新的 `executionId`。
- MVP 普通执行上限采用 `maxExecutionsPerTask=3`。

### `ReviewResultSnapshot`

- 是按 `taskId + executionId` 唯一绑定的不可变正式结果快照。
- 保存点级结果、业务 finding、业务化 `NOT_CONCLUDED` 原因、`SKIPPED` 原因、SourceAnchor、结果摘要、审核完整度、版本引用和配置快照。
- 保存当次审核点展示编码、名称、分类、排序、合同类型、严重级别和内部 `reviewPointCode`。
- 后续规则、模型、parser、prompt、字段配置或审核点配置变化不得覆盖历史快照。

### `PointDiagnostic`

- 是点级诊断对象，服务管理台、评测报告和 TuningPacket。
- 记录覆盖不足、解析低置信、候选冲突、预算不足、模型异常、定义条款缺失或冲突等诊断。
- 不直接进入业务 finding，不改变 `ReviewResultSnapshot` 的正式点级状态。

### `ExecutionSummary`

- 是 execution 级诊断摘要，服务管理台任务详情、评测报告和 AI 调优包。
- 汇总执行阶段、模型调用状态、预算使用、解析质量、点级覆盖、失败类别和调优线索。
- 不替代 `ReviewResultSnapshot.summary`。

### `TuningPacket`

- 是从正式 execution / snapshot 派生的调优治理包。
- 绑定 `taskId`、`executionId`、`snapshotVersion` 和导出配置版本。
- 组合 `ExecutionSummary`、`PointDiagnostic[]`、必要证据摘要、版本引用和脱敏上下文。
- 不改变当前正式 `ReviewResultSnapshot`。
- 不自动调用公网 AI，不自动生成正式 `AITuningAdvice`，不自动修改生产审核配置。

## 交付物

- 新增 `decisions/ADR-012-domain-model-freeze.md`
- 更新 `decisions/ADR-003-task-execution-snapshot-model.md`
- 新增 `tasks/active/TASK-012-domain-model-freeze.md`
- 更新 `decisions/README.md`
- 更新 `tasks/active/TASK-006-scaffold-only-after-adr.md`
- 更新 `CURRENT_CONTEXT.md`
- 更新 `changelog/2026-06.md`

## 验收标准

- 已明确 `Task / Execution / ReviewResultSnapshot / PointDiagnostic / TuningPacket` 的关系和边界。
- 已明确 result URL 默认选择 latest non-superseded execution，带 `executionId` 时查询指定历史 execution。
- 已明确 `ReviewResultSnapshot` 不可变，`TuningPacket` 不影响正式快照。
- 已明确 `PointDiagnostic` 不是业务 finding。
- 已把 `ADR-003` 从 Proposed 推进到 Accepted。
- 未创建业务代码、脚手架、数据库 migration 或 OpenAPI 文件。
- 已完成 Memory Writeback。

## 测试与验证

- 本任务为架构/文档任务，不运行代码测试。
- 验证方式为人工一致性核对：冻结结论必须遵循 `docs/ARCHITECTURE.md`、`ADR-007`、`ADR-009`、`ADR-010` 和 `ADR-011`。

## 风险

- 后续 ERD 如果把 `Task` 与 `Execution` 状态混为一体，会破坏重跑和历史快照解释。
- 后续 API 如果暴露过多 `PointDiagnostic` 技术字段，可能导致外部系统绑定内部诊断。
- 后续 TuningPacket 如果被实现为可写回正式结果的对象，会破坏 `AI Advice != Production Change` 红线。

## 待确认

- 完整 ERD、表命名、字段类型、JSONB 拆分和索引策略。
- `TuningPacket` 物理导出格式和保存 TTL。
- `PointDiagnostic` 哪些字段进入普通 Result API、管理台 API 或仅进入 TuningPacket。
- correction execution 的完整 API 与权限策略。

## 完成记录

- 完成日期：2026-06-09
- 变更文件：`decisions/ADR-012-domain-model-freeze.md`、`decisions/ADR-003-task-execution-snapshot-model.md`、`tasks/active/TASK-012-domain-model-freeze.md`、`decisions/README.md`、`tasks/active/TASK-006-scaffold-only-after-adr.md`、`CURRENT_CONTEXT.md`、`changelog/2026-06.md`
- 测试结果：未运行代码测试；已做文档一致性核对
- 遗留问题：完整 ERD、最小 OpenAPI、TuningPacket 物理导出格式、PointDiagnostic API 可见性和 correction execution 权限仍待后续任务确认
- 备注：本任务未创建业务代码、未搭建脚手架、未安装依赖、未创建数据库 migration 或 OpenAPI 文件
