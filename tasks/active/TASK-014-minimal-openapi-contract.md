# TASK-014：最小 OpenAPI 契约

状态：待开始
类型：接口契约 / 文档冻结 / scaffold 前置

优先级：P0
负责人：待确认
创建日期：2026-06-10

来源：`CURRENT_CONTEXT.md` 当前下一步、`ADR-012-domain-model-freeze.md`、`ADR-008-definition-term-index.md`、`TASK-004-word-parser-baseline-plan`、`TASK-005-model-gateway-and-budget-baseline`

## 背景

`ADR-010`、`ADR-011` 和 `ADR-012` 已冻结 MVP 技术栈、仓库结构与领域模型边界，但最小 OpenAPI 契约尚未落地。后续 scaffold、前后端接口对齐、Flyway 初始迁移和 MVP 验证都需要基于统一的 API 输入输出口径推进。

本任务用于在不扩展 MVP 范围的前提下，冻结第一版最小 OpenAPI 契约，明确任务创建、执行状态查询、普通结果读取以及核心对象结构边界，并生成 YAML/JSON 契约文件。

## 目标

- 定义 MVP 最小 OpenAPI 契约边界。
- 明确 `Evidence`、`Candidate`、`ReviewResultSnapshot`、`DefinitionTermIndex` 的最小接口表示。
- 明确任务创建、状态查询、结果 URL 和结果读取的最小接口。
- 生成 `packages/api-contracts/` 下的 OpenAPI YAML/JSON 契约文件。
- 为后续前后端一致性验证提供固定输入。

## 非目标

- 不实现后端 Controller、Service 或前端页面。
- 不创建数据库 migration。
- 不扩展 MVP 之外的新业务字段、新审核点或新调用方能力。
- 不开放外部调用方自定义 `modelProfileCode`。
- 不在本任务中实现认证、权限矩阵或完整 SAP/OA 集成策略。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`PRD.md`
- 相关架构章节：`docs/ARCHITECTURE.md` 中任务链路、结果契约、DefinitionTermIndex、Evidence 相关章节
- 相关 ADR：`ADR-002`、`ADR-006`、`ADR-008`、`ADR-010`、`ADR-011`、`ADR-012`
- 上游任务：`TASK-004-word-parser-baseline-plan`、`TASK-005-model-gateway-and-budget-baseline`、`TASK-012-domain-model-freeze`

## 依赖

- 强依赖：`TASK-004-word-parser-baseline-plan`
- 强依赖：`TASK-005-model-gateway-and-budget-baseline`
- 依赖基线：`TASK-012-domain-model-freeze`

## 范围

### 包含

- 任务创建 API 最小请求/响应契约。
- 执行状态查询 API 最小响应契约。
- 普通结果读取 API 最小响应契约。
- `ReviewResultSnapshot`、`PointResult`、`SourceAnchor`、`Evidence`、`Candidate`、`DefinitionTermIndex` 的最小 schema。
- 错误码、`NOT_CONCLUDED` 业务化原因和 `SYS-*` 外部可见映射边界。
- `packages/api-contracts/` 中的 OpenAPI YAML/JSON 文件与说明文档。
- 前后端契约一致性验证要求。

### 不包含

- 不实现真实 API。
- 不实现 SDK 生成。
- 不定义完整 Admin API。
- 不开放 MVP 之外的 correction execution 完整契约。
- 不定义完整权限、登录和访问控制。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- 必须遵循 `docs/ARCHITECTURE.md`、`ADR-008`、`ADR-012` 已冻结边界。
- 外部 API 不得暴露完整 `SYS-*` 技术诊断细节、完整 prompt、raw output、endpoint secret 或 stack trace。
- `DefinitionTermIndex` 属于索引层能力，不得重新写回 Parser 职责。
- 模型输出不得直接决定最终业务 finding。
- 不确定内容统一标记为“待确认”。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`docs/backend.md`
- 必读：`docs/ai-review.md`
- 按需：`docs/database.md`
- 按需：`docs/sap.md`
- 必读：`decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- 必读：`decisions/ADR-006-model-profile-switching-and-public-provider-scope.md`
- 必读：`decisions/ADR-008-definition-term-index.md`
- 必读：`decisions/ADR-012-domain-model-freeze.md`

## 交付物

- `packages/api-contracts/openapi.yaml`
- `packages/api-contracts/openapi.json`
- 最小 OpenAPI 契约说明文档
- 前后端契约一致性验证说明
- 更新后的项目记忆与任务完成记录

## 验收标准

- 能明确说明任务创建、状态查询、普通结果读取的最小接口边界。
- 能明确 `Evidence`、`Candidate`、`ReviewResultSnapshot`、`DefinitionTermIndex` 的最小 schema。
- OpenAPI YAML/JSON 两种格式均已生成。
- 契约内容不超出 `TASK-004`、`TASK-005` 和 `ADR-012` 已冻结边界。
- 明确前后端一致性验证方法。

## 测试与验证

- 本任务以契约与文档验证为主，不实现业务代码。
- 验证方式包括：
  - 人工核对 OpenAPI 契约与 `ADR-012` 领域模型一致。
  - 人工核对 `DefinitionTermIndex`、`Evidence` 边界与 `TASK-004` 结论一致。
  - 人工核对模型调用与预算字段边界与 `TASK-005` 结论一致。
  - 如 scaffold 已存在，则增加最小前后端契约一致性检查记录。

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`
- 必须更新 `changelog/2026-06.md`
- 必须更新本任务完成记录
- 是否需要更新 `docs/backend.md`、`docs/frontend.md`、`docs/ai-review.md`：待确认
- 是否需要新增或更新 ADR：待确认

## 风险

- 若在 `TASK-004` 和 `TASK-005` 未冻结前提前定稿，可能固化错误的 Evidence 或模型预算边界。
- 若外部 API 暴露过多内部诊断字段，后续演进会被外部系统绑定。
- 若契约过度扩展到 Admin API 或 correction execution，会拖慢 scaffold 和 MVP 首轮落地。

## 待确认

- `ADR-002` 是否需在本任务前先推进为 `Accepted`。
- 外部 API 是否需要返回 `DefinitionTermIndex` 相关最小命中摘要，还是仅用于内部诊断与 TuningPacket。
- `PointDiagnostic` 在普通结果 API 中的最小可见性边界。
- OpenAPI 文件生成方式：手写主文件还是配合生成脚本。

## 完成记录

- 完成日期：待填写
- 变更文件：待填写
- 测试结果：待填写
- 遗留问题：待填写
- 备注：待填写
