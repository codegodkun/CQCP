# TASK-008：架构与 PRD 定义术语索引补充

状态：已完成

类型：架构文档 / PRD 文档 / 项目记忆

优先级：高

负责人：Codex

创建日期：2026-06-09

来源：用户给出的最终文档修改意见、`ADR-008-definition-term-index.md`、`ADR-009-ai-tuning-governance.md`、`CURRENT_CONTEXT.md`

## 背景

用户给出最终可直接执行的文档修改意见，要求补充 `.doc` MVP 开放边界、DefinitionTermIndex 细节、CandidateRole 枚举、Gemma artifact 复用边界、PointDiagnostic / ExecutionSummary 诊断字段、`suspectedFailureClass` 和 `AITuningAdvice.targetType` 补充枚举，以及 PRD 中 DefinitionTermIndex 和月度付款累计口径说明。

## 目标

- 按用户指定章节最小化修改 `docs/ARCHITECTURE.md`。
- 按用户指定章节最小化修改 `PRD.md`。
- 不把用户明确要求“降级处理（不写入主文档）”的内容写入主干文档。
- 完成项目记忆写回。

## 非目标

- 不创建业务代码。
- 不创建脚手架。
- 不安装依赖。
- 不修改数据库。
- 不创建 OpenAPI 契约。
- 不展开 OptimizationEffect、CrossModelDiagnosticMode、TuningPacketExportConfig 或 AITuningAdvice 完整字段定义。

## 输入

- 相关文档：`docs/ARCHITECTURE.md`、`PRD.md`
- 相关 ADR：`decisions/ADR-008-definition-term-index.md`、`decisions/ADR-009-ai-tuning-governance.md`
- 上游任务：`TASK-007-first-review-point-definitions-draft`

## 范围

### 包含

- `docs/ARCHITECTURE.md` Section 3、6、9.3、10.1、10.6、16 的指定修改。
- `PRD.md` Section 5.2 和 15.1 的指定修改。
- `CURRENT_CONTEXT.md` 和 `changelog/2026-06.md` 写回。

### 不包含

- 不执行任何业务实现。
- 不变更已 Accepted ADR 的状态。
- 不把用户列为“降级处理”的内容写入 ARCHITECTURE 或 PRD 主干。

## 约束

- 必须遵守 `AGENTS.md` 和 `docs/context-management.md`。
- 不改变平台定位：只提供风险提示、证据和审核结果 URL，不批准、不拒绝、不阻断、不修改审批流。
- 不改变模型职责边界：Gemma/A30 只做局部辅助，后端做确定性裁判。
- 不改变 `SYS-*` 与业务 finding 分流规则。
- 不确定内容标记为“待确认”。

## 交付物

- `docs/ARCHITECTURE.md`
- `PRD.md`
- `CURRENT_CONTEXT.md`
- `changelog/2026-06.md`
- 本任务文件

## 验收标准

- 用户列出的 ARCHITECTURE.md 修改点均已落入对应章节或就近章节。
- 用户列出的 PRD.md 修改点均已落入对应章节。
- 用户标注“不写入主文档”的内容未写入 ARCHITECTURE.md 或 PRD.md 主干。
- 未创建业务代码、脚手架、依赖或数据库变更。

## 测试与验证

- 使用 `rg` 检查关键新增术语是否存在。
- 使用 `git status --short` 检查变更范围。

## 文档更新要求

- 更新 `CURRENT_CONTEXT.md`：是。
- 更新 `changelog/2026-06.md`：是。
- 新增或更新 ADR：否。本次是按已有 ADR 和用户确认意见补充主文档，不新增架构决策。

## Next Task Handoff

- 任务完成后判断是否存在明确下一任务。
- 若没有明确任务编号，不输出可复制代码块。

## 风险

- `PointDiagnostic`、`ExecutionSummary` 和 `AITuningAdvice.targetType` 仍是文档契约，不代表数据库或 API 已落地。
- `DefinitionTermIndex` 的实际 Parser 触发规则、冲突检测和注入策略仍需后续实现任务验证。

## 待确认

- `DefinitionTermIndex` 字段最终数据库类型和索引策略。
- `suspectedFailureClasses[]` 是否进入普通 Result API、管理台诊断 API 或仅进入 TuningPacket。
- `AITuningAdvice.targetType` 的完整字段结构仍按用户意见延后到 ADR-007 / Pilot 文档。

## 完成记录

- 完成日期：2026-06-09。
- 变更文件：`docs/ARCHITECTURE.md`、`PRD.md`、`CURRENT_CONTEXT.md`、`changelog/2026-06.md`、`tasks/active/TASK-008-architecture-prd-definition-index-alignment.md`。
- 测试结果：完成关键术语 `rg` 检查；本任务为文档任务，未运行代码测试。
- 遗留问题：后续仍需最小 OpenAPI、数据库/配置落地、DefinitionTermIndex 实现和样本验证任务。
- 备注：未创建业务代码，未搭建脚手架，未安装依赖，未修改数据库。
