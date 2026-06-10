# ADR-012 领域模型冻结

Status: Accepted

日期：2026-06-09

## 背景

`TASK-011-repository-structure-freeze` 已冻结仓库结构，后续 scaffold 需要明确后端领域模型边界，避免把 `Task`、`Execution`、`ReviewResultSnapshot`、`PointDiagnostic` 和 `TuningPacket` 混成单层任务表或可变结果对象。

`docs/ARCHITECTURE.md` 已明确生产审核链路与调优治理链路隔离：

```text
生产审核链路:
Contract -> Parser -> CandidateResolver -> EvidenceSlot -> Review Engine -> Finding -> ReviewResultSnapshot

调优治理链路:
ReviewResultSnapshot -> TuningPacket -> CrossModelDiagnostic -> AITuningAdvice -> Candidate Change -> Regression Validation -> Human Approval -> Release
```

本 ADR 冻结领域对象职责、关系和边界，不定义完整 ERD、不写 OpenAPI 细节、不创建业务代码或脚手架。

## 决策

V1 MVP 领域模型采用以下分层：

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

核心原则：

- `Task` 是业务请求聚合根。
- `Execution` 是一次具体审核运行。
- `ReviewResultSnapshot` 是不可变正式结果快照。
- `PointDiagnostic` 是点级诊断，不是业务 finding。
- `TuningPacket` 是调优治理链路的派生产物，不影响正式快照。
- `ExecutionSummary` 是调优与管理台诊断摘要，不替代普通结果页摘要。

## 领域对象职责

### Task

`Task` 表示一次业务审核请求的稳定聚合。

职责：

- 保存 `taskId`。
- 保存创建来源、caller、合同基础元数据、任务创建时间和 result URL 基础入口。
- 保存任务创建时的结构化字段输入快照，包括 MVP 首批字段、币种 `CNY` 和条件必填场景。
- 聚合多个 `Execution`。
- 提供 latest non-superseded execution 的选择入口。

非职责：

- 不承载最终审核状态的权威解释。
- 不保存点级结果。
- 不覆盖历史 execution。
- 不直接承载模型输出、EvidenceBundle 或后端裁判细节。

### Execution

`Execution` 表示同一 `Task` 下的一次具体审核执行。

职责：

- 保存 `executionId` 和所属 `taskId`。
- 保存 execution 状态机和 stage 进度。
- 绑定本次执行使用的 `RuleSetVersion`、`ContractTypeProfile`、`ReviewBudgetProfile`、`ModelProfile`、parser / prompt / schema / pattern / field lexicon / EvidenceSelector 等版本引用。
- 承载异步单 worker 的 stage lease、attempt、timeout 和阶段日志。
- 作为 `ReviewExecutionPlan`、EvidenceSlot coverage、ModelCallIntent、GemmaExtractionArtifact 和结果合成的运行上下文。

约束：

- 同一 `taskId` 下同时只能有一个 non-terminal execution。
- 合同类型修正、预算升级、人工重跑、规则集重跑、模型升级或 parser 升级必须创建新的 `executionId`。
- `maxExecutionsPerTask = 3` 作为 MVP 普通执行上限。
- 管理员 recovery execution 可不占普通配额，但必须有审计记录。

### ReviewResultSnapshot

`ReviewResultSnapshot` 是正式审核结果的不可变快照。

职责：

- 按 `taskId + executionId` 唯一绑定。
- 保存结果状态、点级结果、业务 finding、业务化 `NOT_CONCLUDED` 原因、`SKIPPED` 适用性原因、SourceAnchor、结果摘要和审核完整度。
- 保存本次 execution 的版本引用和配置快照。
- 保存当次审核点展示编码、名称、分类、排序、合同类型、严重级别和内部 `reviewPointCode`。
- 保存 `supersededByExecutionId?` 与 `supersededReason?`，用于历史结果解释。

约束：

- 快照一旦生成不得被后续规则、模型、parser、prompt、配置或字段定义变更重算覆盖。
- 普通结果页和历史结果查询读取快照，不读取当前配置覆盖历史。
- 业务 finding 只来自 `ERROR / WARNING`；`PASS / NOT_CONCLUDED / SKIPPED / SYS-*` 不计入业务风险数量。

MVP 最小摘要字段包括：

- `plannedPointCount`
- `passCount`
- `errorCount`
- `warningCount`
- `notConcludedCount`
- `skippedCount`
- `disabledByConfigCount`
- `reviewCoverageStatus`
- `criticalSlotCoverageRate`
- `evidenceCoverageRate`
- `confidenceLevel`

### PointResult

`PointResult` 是 `ReviewResultSnapshot` 内的点级正式结果。

状态枚举：

- `PASS`
- `ERROR`
- `WARNING`
- `NOT_CONCLUDED`
- `SKIPPED`

边界：

- `ERROR / WARNING` 是业务 finding，必须有可靠证据定位和解释。
- `PASS` 必须有可靠证据定位和解释。
- `NOT_CONCLUDED` 表示系统没有足够可靠依据形成业务结论，不是业务风险，也不是无风险结论。
- `SKIPPED` 表示审核点不适用，不要求 EvidenceSlot 或 SourceAnchor。

### PointDiagnostic

`PointDiagnostic` 是点级诊断对象，服务管理台、评测报告和 TuningPacket。

职责：

- 记录点级覆盖不足、解析低置信、候选冲突、预算不足、模型异常、定义条款缺失或冲突等诊断。
- 记录 `suspectedFailureClasses[]`、缺失 slot、候选歧义摘要、模型调用摘要和业务化原因。
- 支撑 `TuningPacket` 生成。

非职责：

- 不直接进入业务 finding。
- 不作为普通结果页默认展示的技术对象。
- 不改变 `ReviewResultSnapshot` 的正式点级状态。

### ExecutionSummary

`ExecutionSummary` 是 execution 级诊断摘要。

职责：

- 汇总执行阶段、模型调用状态、预算使用、解析质量、点级覆盖、失败类别和调优线索。
- 作为 `TuningPacket` 的固定组成部分。
- 服务管理台任务详情、评测报告和 AI 调优包。

非职责：

- 不替代 `ReviewResultSnapshot.summary`。
- 不作为外部 API 流程控制契约。

### TuningPacket

`TuningPacket` 是从正式 execution / snapshot 派生的调优治理包。

职责：

- 绑定 `taskId`、`executionId`、`snapshotVersion` 和导出配置版本。
- 组合 `ExecutionSummary`、`PointDiagnostic[]`、必要证据摘要、版本引用和脱敏上下文。
- 支持 `SINGLE_POINT`、`FOCUSED` 导出模式；`FULL` 延后到受控内部场景。
- 支持复制给外部 AI 或本地 AI 辅助分析，并保存外部 AI 建议文本。

约束：

- 不改变当前正式 `ReviewResultSnapshot`。
- 不自动调用公网 AI。
- 不自动生成正式 `AITuningAdvice`。
- 不自动修改 prompt、rule、pattern、field lexicon、CandidateResolver、EvidenceSlot、ModelProfile 或 ReviewPointDefinition。
- 不自动影响后续新任务，除非后续通过 Regression Validation、Human Approval 和 Version Release。

## Result URL 选择规则

普通结果 URL 形式：

```text
/review/results/{taskId}?executionId={executionId}
```

规则：

- 带 `executionId` 时展示指定历史 execution 的快照。
- 不带 `executionId` 时选择最新未被 superseded 的 terminal execution。
- 如无 terminal execution，可展示最新 non-terminal execution 的处理中状态。
- 普通结果 URL 不做平台侧登录、公开令牌或独立访问控制；外围系统负责编码、加密、分发和访问控制。

## Superseded 边界

`supersededReason` MVP 枚举：

- `TYPE_CORRECTION`
- `BUDGET_UPGRADE`
- `MANUAL_RERUN`
- `RULESET_RERUN`
- `MODEL_UPGRADE`
- `PARSER_UPGRADE`
- `ADMIN_RECOVERY`

被 superseded 的快照仍可通过 `executionId` 查询，不得删除或覆盖。

## 存储边界

- PostgreSQL 是领域模型落地数据库。
- `ReviewResultSnapshot`、`PointDiagnostic`、`TuningPacket` 可使用 JSONB 保存结构化字段与半结构化诊断 payload。
- 本 ADR 不冻结完整 ERD、表命名、字段类型、索引和 Flyway migration 文件路径；这些进入后续数据库任务。
- Flyway migration 归属 `apps/api-server/` 后端工程边界，具体路径后续确认。

## 不做什么

- 不创建业务代码。
- 不创建数据库 schema 或 Flyway migration。
- 不定义完整 OpenAPI。
- 不实现 correction execution。
- 不创建 TuningPacket 导出文件格式。
- 不把 `PointDiagnostic` 暴露为普通结果页默认技术详情。
- 不让 TuningPacket 或外部 AI 建议影响正式结果快照。

## 影响

- `ADR-003-task-execution-snapshot-model.md` 更新为 `Accepted`，作为 `Task / Execution / ReviewResultSnapshot` 子模型决策。
- `TASK-006-scaffold-only-after-adr` 的关键 ADR 门禁中，`ADR-003` 不再阻塞于 Proposed 状态。
- `TASK-013` 最小 OpenAPI 契约应基于本 ADR 输出任务创建、execution 查询、结果 URL 和快照读取边界。
- 后续数据库 ERD 任务应基于本 ADR 设计表、JSONB、索引和迁移。

## 验证方式

- 人工核对是否符合 `docs/ARCHITECTURE.md`、`ADR-007`、`ADR-009`、`ADR-010` 和 `ADR-011`。
- 用普通任务、合同类型修正、预算升级、历史结果查询、TuningPacket 导出五类场景验证关系是否闭合。

## 关联

- 来源任务：`TASK-012-domain-model-freeze`
- 子决策：`ADR-003-task-execution-snapshot-model`
- 相关 ADR：`ADR-007-tuning-packet-architecture`、`ADR-009-ai-tuning-governance`、`ADR-011-repository-structure-freeze`
- 后续任务：`TASK-013`、数据库 ERD / Flyway 任务、Word parser baseline、Model gateway baseline

## 待确认

- 完整 ERD、表命名、字段类型、JSONB 拆分和索引策略。
- `TuningPacket` 物理导出格式和保存 TTL。
- `PointDiagnostic` 哪些字段进入普通 Result API、管理台 API 或仅进入 TuningPacket。
- correction execution 的完整 API 与权限策略。
