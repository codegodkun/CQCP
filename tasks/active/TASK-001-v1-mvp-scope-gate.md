# TASK-001：V1 MVP 范围闸门

状态：已完成

类型：规划 / 范围收敛

优先级：P0

负责人：待确认

创建日期：2026-06-07

来源：Superpowers planning / design review / task decomposition 审查结论、`CURRENT_CONTEXT.md`、`ROADMAP.md`

## 背景

当前 V1 文档描述的是一期愿景，包含 MVP、试点能力和生产治理能力。若不先收敛范围，后续任务窗口容易把 Quality Copilot、完整治理、复杂发布审批、外部系统深度 correction 和生产保护能力提前混入第一条开发链路。

## 目标

- 将 V1 愿景拆成 MVP / Pilot / Production Readiness 三层。
- 明确第一轮 MVP 只交付什么。
- 明确哪些能力必须延后到 Pilot 或 Production Readiness。
- 形成后续任务拆分和 ADR 的范围依据。

## 非目标

- 不写业务代码。
- 不创建前端、后端或数据库脚手架。
- 不安装依赖。
- 不设计完整 ERD。
- 不制定详细 UI 视觉方案。
- 不把任何待确认范围写成已确认事实。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`PROJECT_BRIEF.md`、`ROADMAP.md`
- 相关架构章节：原始架构文档的一期范围、核心架构、结果页、质量治理和部署保护章节。
- 相关 ADR：待确认。
- 上游任务：Superpowers 审查结论写回。

## 范围

### 包含

- 审查当前 V1 范围中哪些属于 MVP、Pilot、Production Readiness。
- 定义第一轮 MVP 的最小审核闭环。
- 标记 Quality Copilot、完整治理、复杂发布审批、SAP/OA 深度 correction 和生产保护能力的进入阶段。
- 更新 `ROADMAP.md`、`CURRENT_CONTEXT.md` 和必要的后续任务列表。

### 不包含

- 不实现任何审核点。
- 不创建业务 API。
- 不创建管理台页面。
- 不创建模型调用代码。
- 不修改旧项目或迁移旧项目资产。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- 只完成当前任务包定义的范围。
- 不确定内容标记为“待确认”。
- 不改变项目“只提供风险提示、证据和审核结果 URL，不审批、不阻断、不修改审批流”的业务边界。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`PROJECT_BRIEF.md`
- 必读：`ROADMAP.md`
- 按需：`docs/frontend.md`
- 按需：`docs/backend.md`
- 按需：`docs/ai-review.md`
- 按需：`docs/sap.md`
- 按需：`docs/deployment.md`

## 交付物

- V1 MVP / Pilot / Production Readiness 范围分层说明。
- 第一轮 MVP 包含与不包含清单。
- 需要新增、更新或延后执行的任务清单。
- 如发现范围决策影响架构边界，提出需要新增的 ADR 草案。

## 范围门禁结果

本任务形成当前规划基线，最终仍待人工确认。

### MVP

MVP 只用于跑通单份中文 Word 合同的第一条最小审核闭环。

MVP 当前规划基线包括：

- 一个明确入口，入口顺序由 `TASK-002-entry-order-decision` 决定。
- 单份中文 Word 合同输入。
- 最小结构化字段输入。
- 最小 Word 解析、候选索引和证据定位。
- 首批少量审核点族，优先从 `PARTY_FIELDS`、`AMOUNT_TAX`、`PAYMENT_TERMS` 中选择。
- 规则/正则优先，Gemma/A30 只做局部抽取、证据选择或复杂语义辅助。
- 后端统一合成 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`，并保持 `SYS-*` 与业务 finding 分流。
- `Task`、`Execution`、`ReviewResultSnapshot` 和结果 URL。
- 普通结果页围绕“审核点 -> 证据 -> 原文定位”解释结果。

### Pilot

Pilot 用于受控内部试点，逐步纳入更完整的管理台诊断展示、失败样本记录、人工反馈、首批样本集评测、版本快照、受控 correction execution 和必要外部 caller contract test。

### Production Readiness

Production Readiness 用于生产前治理、审计、发布和保护，包含 Quality Copilot、完整候选发布审批、样本集生命周期、生产保护、完整审计、脱敏测试、备份恢复、canary 和模型运维指标。

### 第一轮 MVP 不包含

- Quality Copilot。
- 完整质量治理工作台。
- 复杂发布审批流程。
- SAP/OA 深度 correction 联调。
- 生产保护能力、样本集 90 天过期阻断和临时运行审批。
- 完整审计体系和复杂权限治理。
- 自动候选优化、自动发布规则/prompt/pattern。

## 验收标准

- 明确写出“V1 愿景不等于 MVP”。
- 明确 MVP 重点是单份中文 Word 合同的最小审核闭环。
- 明确 Quality Copilot、完整治理、复杂发布审批、SAP/OA 深度 correction、生产保护能力不进入第一轮 MVP。
- 后续任务能依据该分层继续拆解，不依赖聊天上下文。

## 测试与验证

- 本任务为规划任务，不运行代码测试。
- 验证方式是人工审查文档是否能回答“第一轮 MVP 到底交付什么、不交付什么”。

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`。
- 必须更新 `ROADMAP.md`。
- 必须更新 `changelog/2026-06.md`。
- 是否需要新增或更新 ADR：待确认。

## 风险

- 如果 MVP 仍然过大，后续 scaffold 会继续承载过多未确认能力。
- 如果分层过度收缩，可能无法验证核心审核质量。

## 待确认

- MVP 是否包含 `.doc`，还是仅从 `.docx` 开始。
- MVP 是否包含外部 API，还是先从管理台或 API-first 简易入口开始。
- MVP 首批合同类型是否只选一个类型试点。
- MVP 首批审核点数量上限。

## 后续可能派生任务

- `TASK-002-entry-order-decision`
- `TASK-003-first-review-points-selection`
- `TASK-004-word-parser-baseline-plan`
- `ADR-001-technical-stack-and-repo-layout`

## 完成记录

- 完成日期：2026-06-07。
- 变更文件：`ROADMAP.md`、`CURRENT_CONTEXT.md`、`changelog/2026-06.md`、本任务包。
- 测试结果：规划任务，未运行代码测试；已人工检查 `ROADMAP.md` 是否包含 MVP / Pilot / Production Readiness 分层。
- 遗留问题：MVP 入口顺序、`.doc` 是否进入 MVP、首批合同类型、首批审核点数量仍待确认。
- 备注：本任务未创建业务代码，未创建脚手架，未安装依赖。
