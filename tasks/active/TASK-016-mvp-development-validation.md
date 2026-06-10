# TASK-016：MVP 开发与验证

状态：待开始
类型：开发验证 / 集成测试计划 / MVP 验收

优先级：P0
负责人：待确认
创建日期：2026-06-10

来源：`CURRENT_CONTEXT.md` 当前下一步、`TASK-004-word-parser-baseline-plan`、`TASK-005-model-gateway-and-budget-baseline`、`TASK-006-scaffold-only-after-adr`

## 背景

在 Word parser、模型网关与预算、脚手架、最小 OpenAPI 和 Flyway 初始迁移冻结并落地后，需要有一组明确的 MVP 开发验证任务包，保证首轮开发不是只做代码搭建，而是围绕样例、契约、调优包和管理台摘要进行可验证推进。

本任务用于定义 MVP 开发验证阶段的目标、最小测试计划和验证顺序。

## 目标

- 明确 MVP 开发验证范围和顺序。
- 输出最小测试计划和验收目标。
- 覆盖 Word parser、模型客户端、AI 调优包、管理台点级诊断与执行摘要四类验证主题。
- 为后续实现任务提供可执行验收口径。

## 非目标

- 不在本任务中直接实现所有开发项。
- 不扩展 Pilot / Production Readiness 能力。
- 不引入新审核点、新合同类型或新治理模块。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`PRD.md`
- 相关架构章节：`docs/ARCHITECTURE.md` 中审核链路、结果契约、TuningPacket、任务状态机章节
- 相关 ADR：`ADR-010`、`ADR-011`、`ADR-012`
- 上游任务：`TASK-004`、`TASK-005`、`TASK-006`、`TASK-014`、`TASK-015`

## 依赖

- 强依赖：`TASK-004-word-parser-baseline-plan`
- 强依赖：`TASK-005-model-gateway-and-budget-baseline`
- 强依赖：`TASK-006-scaffold-only-after-adr`
- 强依赖：`TASK-014-minimal-openapi-contract`
- 强依赖：`TASK-015-flyway-v1-bootstrap`

## 范围

### 包含

- MVP 测试计划。
- 验证目标与通过标准。
- 以下开发验证主题：
  - Word parser Spike / 测试样例生成
  - 模型客户端契约测试
  - AI 调优包导出验证
  - 管理台点级诊断和执行摘要验证
- 最小样例准备要求和验证顺序。

### 不包含

- 不定义 Pilot/Production 完整压测计划。
- 不定义复杂安全测试、BI 测试或多租户测试。
- 不实现完整 CI 流程扩展。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- 所有验证都必须遵循 MVP 范围冻结边界。
- 不得让模型直接决定最终业务 finding。
- 不得在 Evidence 不可靠时生成业务 finding。
- 不确定内容统一标记为“待确认”。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 按需：`docs/backend.md`
- 按需：`docs/database.md`
- 按需：`docs/ai-review.md`
- 按需：`docs/deployment.md`
- 必读：`decisions/ADR-010-technology-stack-freeze.md`
- 必读：`decisions/ADR-012-domain-model-freeze.md`

## 交付物

- MVP 测试计划
- 验证目标清单
- 样例准备要求
- 开发验证主题拆分说明
- 更新后的项目记忆与任务完成记录

## 验收标准

- 能明确说明 MVP 首轮需要验证的开发主题和顺序。
- 能明确每类验证的最小通过标准。
- 依赖关系与 `TASK-004 / TASK-005 / TASK-006 / TASK-014 / TASK-015` 一致。
- 不把 Pilot / Production 能力混入 MVP 首轮开发验证。

## 测试与验证

- 本任务本身为测试计划任务，不实现业务代码。
- 验证方式为人工审查测试计划完整性、依赖完整性和 MVP 边界一致性。

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`
- 必须更新 `changelog/2026-06.md`
- 必须更新本任务完成记录
- 是否需要更新 `docs/backend.md`、`docs/ai-review.md`：待确认
- 是否需要新增或更新 ADR：否

## 风险

- 若没有统一验证计划，scaffold 之后容易进入只搭工程不验业务闭环的状态。
- 若样例准备、契约测试和调优包验证顺序混乱，会导致问题定位成本升高。
- 若把管理台验证做成纯 UI 验证，会遗漏点级诊断和执行摘要的业务解释责任。

## 待确认

- 样例文件命名规范和存放目录的最终格式。
- MVP 阶段是否先采用合成样例，再接入脱敏真实样例。
- 管理台点级诊断与执行摘要验证是否拆成独立后续实现任务。

## 完成记录

- 完成日期：待填写
- 变更文件：待填写
- 测试结果：待填写
- 遗留问题：待填写
- 备注：待填写
