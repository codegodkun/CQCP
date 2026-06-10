# TASK-015：Flyway V1 数据库初始化

状态：待开始
类型：数据库迁移 / scaffold 后置 / MVP 基础设施

优先级：P0
负责人：待确认
创建日期：2026-06-10

来源：`CURRENT_CONTEXT.md` 当前下一步、`ADR-010-technology-stack-freeze.md`、`ADR-012-domain-model-freeze.md`

## 背景

MVP 已冻结 PostgreSQL + Flyway + MyBatis 技术栈，以及 `Task / Execution / ReviewResultSnapshot / TuningPacket / PointDiagnostic` 领域模型边界，但数据库初始化迁移尚未创建。后续后端骨架、接口联调和验证任务都需要一版可执行的初始迁移基线。

本任务用于在 scaffold 完成后，创建第一版 Flyway 初始化任务包，明确 V1 初始迁移计划和核心表范围。

## 目标

- 定义 Flyway V1 初始化迁移计划。
- 明确 MVP 核心表的初始建表范围。
- 明确 JSONB、快照和诊断对象的初始落地策略。
- 为 scaffold 后的数据库初始化和最小验证提供执行边界。

## 非目标

- 不实现完整 ERD 扩展版。
- 不实现业务逻辑、Repository 或 MyBatis Mapper。
- 不提前扩展候选索引表、规则治理表、完整审计表矩阵。
- 不引入超出 MVP 的多租户、复杂权限或 BI 表。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`
- 相关架构章节：`docs/ARCHITECTURE.md` 中 Task/Execution/Snapshot、版本化、审计与迁移边界章节
- 相关 ADR：`ADR-010`、`ADR-011`、`ADR-012`
- 上游任务：`TASK-006-scaffold-only-after-adr`

## 依赖

- 强依赖：`TASK-006-scaffold-only-after-adr`
- 依赖基线：`TASK-012-domain-model-freeze`

## 范围

### 包含

- Flyway V1 初始迁移计划说明。
- 核心表范围定义：
  - `Task`
  - `Execution`
  - `ReviewResultSnapshot`
  - `TuningPacket`
  - `PointDiagnostic`
- 初始 JSONB 字段、版本快照字段和主查询索引策略说明。
- 迁移目录归属、命名节奏和最小验证方式。

### 不包含

- 不实现完整治理表、样本集表、回归中心表。
- 不实现完整权限审计方案。
- 不实现最终性能优化索引全量设计。
- 不实现正式备份恢复方案。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- 必须遵循 `ADR-010`、`ADR-011`、`ADR-012` 冻结边界。
- Flyway migration 归属 `apps/api-server/` 后端工程边界。
- 不得把 `TuningPacket` 设计成可写回正式结果快照的对象。
- 不确定内容统一标记为“待确认”。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`docs/database.md`
- 按需：`docs/backend.md`
- 按需：`docs/deployment.md`
- 必读：`decisions/ADR-010-technology-stack-freeze.md`
- 必读：`decisions/ADR-011-repository-structure-freeze.md`
- 必读：`decisions/ADR-012-domain-model-freeze.md`

## 交付物

- Flyway V1 初始迁移计划文档
- 核心表清单与字段边界说明
- 迁移命名与目录约定
- 数据库最小验证计划
- 更新后的项目记忆与任务完成记录

## 验收标准

- 明确 V1 初始迁移只覆盖 MVP 核心表。
- 明确 `Task / Execution / ReviewResultSnapshot / TuningPacket / PointDiagnostic` 的落地边界。
- 明确 Flyway 目录、命名规范和最小执行验证方式。
- 不超出 `ADR-012` 已冻结领域模型边界。

## 测试与验证

- 本任务以迁移计划和文档验证为主。
- 如 scaffold 已存在，则验证方式包括：
  - Flyway 目录位置和命名规范检查。
  - 初始 migration 可执行性验证计划。
  - 人工核对核心表与领域模型边界一致。

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`
- 必须更新 `changelog/2026-06.md`
- 必须更新本任务完成记录
- 是否需要更新 `docs/database.md`：待确认
- 是否需要新增或更新 ADR：待确认

## 风险

- 若在 scaffold 前先写迁移，可能与最终工程目录不一致。
- 若初始迁移范围过大，会把未冻结治理能力提前固化进数据库。
- 若核心表字段与快照职责混淆，后续会破坏历史结果不可变约束。

## 待确认

- 核心表是否全部放在单 schema 下，或后续再做 schema 分层。
- `PointDiagnostic` 是独立表还是作为 `TuningPacket` 关联表的最终实现细节。
- 初始索引策略是否仅覆盖 `taskId`、`executionId` 和 latest non-superseded 查询。

## 完成记录

- 完成日期：待填写
- 变更文件：待填写
- 测试结果：待填写
- 遗留问题：待填写
- 备注：待填写
