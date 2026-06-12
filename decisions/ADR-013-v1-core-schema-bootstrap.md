# ADR-013：V1 Core Schema Bootstrap

Status: Accepted

日期：2026-06-11

## 背景

`ADR-010` 已冻结 PostgreSQL 15+、Flyway 和 MyBatis 技术栈，`ADR-011` 已冻结 Flyway migration 归属 `apps/api-server/`，`ADR-012` 已冻结 `Task / Execution / ReviewResultSnapshot / TuningPacket / PointDiagnostic` 领域边界。

在 `TASK-006` 完成后，仓库已经具备后端骨架，但还没有一版可执行的数据库初始化迁移。后续 API、异步执行、快照写入和 MVP 验证都需要一组最小核心表作为数据库基线。

本 ADR 只冻结 V1 初始 schema 的核心范围、表拆分方式和最小索引策略，不扩展到治理表矩阵、样本集或权限体系。

## 决策

### 1. Flyway 目录与命名

- Flyway migration 固定放在 `apps/api-server/src/main/resources/db/migration/`
- 使用 `V{version}__{description}.sql`
- description 采用小写 snake_case
- 已执行迁移只允许追加，不允许回写改名

### 2. V1 核心表范围

V1 初始迁移只创建以下表：

- `task`
- `execution`
- `task_stage_log`
- `review_result_snapshot`
- `tuning_packet`
- `point_diagnostic`

本轮不创建：

- 样本集表
- 规则治理表
- 发布审批表
- 权限矩阵表
- BI / 回归中心表
- 候选索引明细表

### 3. 表拆分边界

- `task` 保存稳定业务请求元数据、result URL 和结构化字段输入快照
- `execution` 保存状态机、阶段 lease、版本引用和本次执行的模型/解析配置摘要
- `task_stage_log` 保存阶段级事件，不保存函数级调试日志
- `review_result_snapshot` 保存正式不可变结果快照
- `tuning_packet` 保存调优治理链路导出包
- `point_diagnostic` 作为 `tuning_packet` 的从属明细表，不进入普通结果页默认读取对象

### 4. JSONB 落地策略

以下内容采用 JSONB：

- `task.structured_fields_snapshot`
- `task.contract_metadata`
- `task_stage_log.detail_payload`
- `review_result_snapshot.summary`
- `review_result_snapshot.review_completeness`
- `review_result_snapshot.point_results`
- `review_result_snapshot.findings`
- `review_result_snapshot.diagnostics`
- `review_result_snapshot.source_anchors`
- `review_result_snapshot.structured_fields_snapshot`
- `review_result_snapshot.enabled_review_points_snapshot`
- `review_result_snapshot.disabled_review_points_snapshot`
- `tuning_packet.execution_summary`
- `tuning_packet.evidence_summary`
- `tuning_packet.version_references`
- `tuning_packet.redacted_context`
- `point_diagnostic.suspected_failure_classes`
- `point_diagnostic.missing_slots`
- `point_diagnostic.candidate_ambiguity_summary`
- `point_diagnostic.model_call_summary`
- `point_diagnostic.diagnostic_payload`

V1 不把 `PointResult`、`Finding`、`SourceAnchor` 单独拆成关系表。

### 5. 最小索引策略

V1 只保证以下查询：

- `taskId`
- `taskId + executionId`
- latest non-superseded snapshot
- execution 阶段日志按时间倒序
- tuning packet 和 point diagnostic 的从属读取

同一 `taskId` 下只允许一个 non-terminal execution，通过 partial unique index 保证。

`maxExecutionsPerTask = 3` 仍由应用层控制，不在数据库层做计数约束。

## 选择理由

- 与 `ADR-012` 的聚合边界保持一致，没有把调优治理对象回写到正式结果快照。
- 以最小表集支撑 MVP，而不把未冻结的治理能力提前固化进 schema。
- 使用 JSONB 保存快照与诊断 payload，可降低 V1 迁移复杂度，保留后续按热点字段再拆表的空间。
- 通过最小索引覆盖当前已确认查询场景，避免过早引入性能优化型索引矩阵。

## 影响

- `TASK-015` 可以直接落 `V1__cqcp_mvp_core_schema.sql`
- 后续后端实现应以本 ADR 作为核心表边界
- 若后续需要拆 schema、拆 point result 关系表、引入审计矩阵或样本治理表，应通过新任务和必要 ADR 推进

## 不做什么

- 不创建完整 ERD 扩展版
- 不创建业务 Mapper 或 Repository
- 不引入治理/样本/权限/BI 表
- 不把 `TuningPacket` 设计成可回写正式快照的对象
- 不在本 ADR 内定义备份恢复、加密密钥或分库分 schema 策略

## 验证方式

- 核对 migration 目录是否归属 `apps/api-server/`
- 核对表范围是否仅覆盖 MVP 核心对象
- 核对 `taskId / executionId / latest non-superseded` 查询索引是否存在
- 使用 PostgreSQL 15 容器执行 `V1__cqcp_mvp_core_schema.sql` 做语法与建表验证

## 关联

- 来源任务：`tasks/active/TASK-015-flyway-v1-bootstrap.md`
- 上游 ADR：`ADR-010-technology-stack-freeze`、`ADR-011-repository-structure-freeze`、`ADR-012-domain-model-freeze`

## 待确认

- 是否在后续阶段拆分独立 schema
- `review_result_snapshot` 中哪些热点字段未来需要关系化拆表
- 审计 append-only 的数据库权限实现细节

