# TASK-007：首批 ReviewPointDefinition 草案

状态：已完成

类型：AI 审核 / 后端契约 / 文档任务

优先级：高

负责人：Codex

创建日期：2026-06-09

来源：`CURRENT_CONTEXT.md`、`PRD.md`、`docs/ARCHITECTURE.md`、`decisions/ADR-005-first-review-points-selection.md`

## 背景

`ADR-005-first-review-points-selection.md` 已 Accepted，并确认 MVP 首批 9 个 core review point。当前 PRD 仍停留在产品口径与 ADR 决策层，尚未形成可审阅的 `ReviewPointDefinition` 草案。

该任务用于把首批审核点从“已选定”推进到“可实现契约草案”，但不创建业务代码、不创建数据库 schema、不写 OpenAPI 文件、不安装依赖。

## 目标

- 基于 `ADR-005` 为 9 个 MVP core review point 生成首批 `ReviewPointDefinition` 草案。
- 草案必须覆盖 required structured fields、EvidenceSlot、candidate role、executionStrategy、deterministicRule、modelAssistPolicy、sourceAnchorPolicy、状态降级规则和业务可读文案方向。
- 草案必须遵守 `SYS-*` 与业务 finding 分流、证据不足不生成业务 finding、Gemma/A30 只做局部辅助、后端确定性裁判的架构约束。

## 非目标

- 不实现审核点代码。
- 不创建数据库表、迁移脚本或配置 seed。
- 不创建 OpenAPI 契约。
- 不新增审核点。
- 不修改 `ADR-005` 的已确认审核点清单。
- 不引入旧 `ai-contract-review` 项目的规则、样本或页面。

## 输入

- 相关文档：`CURRENT_CONTEXT.md`、`PRD.md`
- 相关架构章节：`docs/ARCHITECTURE.md` 第 4、5、6、8、22 节相关约束
- 相关模块文档：`docs/ai-review.md`、`docs/backend.md`
- 相关 ADR：`decisions/ADR-005-first-review-points-selection.md`
- 上游任务：`tasks/active/TASK-003-first-review-points-selection.md`

## 范围

### 包含

- 生成 `docs/review-point-definitions.md`。
- 覆盖以下 9 个审核点：
  - `PARTY_A_NAME_CONSISTENCY`
  - `PARTY_B_NAME_CONSISTENCY`
  - `CONTRACT_TOTAL_AMOUNT_CONSISTENCY`
  - `TAX_AMOUNT_FORMULA_CONSISTENCY`
  - `PREPAYMENT_RATIO_CONSISTENCY`
  - `PROGRESS_PAYMENT_RATIO_CONSISTENCY`
  - `COMPLETION_PAYMENT_RATIO_CONSISTENCY`
  - `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY`
  - `WARRANTY_RETENTION_RATIO_CONSISTENCY`
- 写回 `CURRENT_CONTEXT.md` 和 `changelog/2026-06.md`。

### 不包含

- 不确认正式技术栈。
- 不确认数据库 ERD。
- 不确认最小 OpenAPI 细节。
- 不确认本地 A30/Gemma endpoint、模型版本或预算数值。
- 不确认评测样本命名和保存目录。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- 根据任务类型按需阅读 `docs/ai-review.md`、`docs/backend.md`、`decisions/ADR-005-first-review-points-selection.md`。
- 只完成当前任务包定义的范围。
- 不顺手实现任务外功能。
- 不确定内容标记为“待确认”。
- 不得引入架构文档未明确允许的业务能力。
- 必须区分业务 finding 与 `SYS-*` 系统诊断。
- Gemma/A30 只做局部抽取、证据选择或复杂语义辅助；后端做确定性裁判。
- 涉及规则、正则、prompt、模型、合同类型画像或 EvidenceSelector，必须考虑版本化。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`docs/ARCHITECTURE.md`
- 必读：`PRD.md`
- 必读：`decisions/ADR-005-first-review-points-selection.md`
- 按需：`docs/backend.md`
- 按需：`docs/ai-review.md`
- 按需：`docs/context-management.md`

## 交付物

- `docs/review-point-definitions.md`：首批 9 个 `ReviewPointDefinition` 草案。
- `CURRENT_CONTEXT.md`：记录草案已生成、后续应进入最小 OpenAPI 契约或样本验证任务。
- `changelog/2026-06.md`：记录本次项目记忆变更。
- 本任务文件完成记录。

## 验收标准

- 每个审核点都有唯一 `reviewPointCode`、中文名称、所属 `ReviewPointFamily`、适用合同类型和 core 标记。
- 每个审核点明确 required structured fields。
- 每个非 `SKIPPED` 场景的审核点明确 required EvidenceSlot 和 source anchor 最低要求。
- 每个审核点明确是否允许 Gemma/A30 辅助，以及辅助输入和输出边界。
- 每个审核点明确 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED` 的生成条件或降级边界。
- 草案不把证据不足、解析低置信、模型异常或候选冲突写成业务 finding。
- 草案不改变 `ADR-005` 的 9 个审核点基线。

## 测试与验证

- 文档自查：确认 9 个审核点均已覆盖。
- 约束自查：确认未创建业务代码、未创建脚手架、未安装依赖。
- 一致性自查：字段 key、枚举、CandidateRole、SYS/Finding 边界与 `CURRENT_CONTEXT.md`、`PRD.md`、`ADR-005` 一致。

## 文档更新要求

- 是否需要更新 `CURRENT_CONTEXT.md`：是，记录草案完成和下一步建议。
- 是否需要更新 `docs/*.md`：是，新增 `docs/review-point-definitions.md`。
- 是否需要更新 `changelog/当前月份.md`：是。
- 是否需要新增或更新 ADR：否。本任务不改变架构决策，只把已 Accepted 的 `ADR-005` 细化为草案。

## Next Task Handoff

- 任务完成后必须判断是否存在明确下一任务。
- 如果存在明确下一任务，输出可直接复制到新窗口执行的 `Next Task Handoff Prompt`。
- 如果不存在明确下一任务，不输出代码块。
- 如果只是建议或征求意见，不得放入代码块。
- 没有任务编号时不得生成可执行 Handoff Prompt。

## 风险

- 草案仍未绑定具体数据库模型、OpenAPI schema 或测试样本，后续实现前仍需契约化。
- `defaultSeverity` 初始值尚未人工确认，草案只定义可配置边界与建议项。
- 候选词库、ValueGrammar 和正则边界尚未生成，草案中的 candidate policy 仍需后续规则任务细化。

## 待确认

- 9 个审核点的初始 `defaultSeverity` 是否全部采用同一默认值，或按审核点分别配置。
- `ReviewPointDefinition` 后续落地格式采用数据库 seed、YAML/JSON 配置还是代码内预置。
- 首批样本验证的 expected result 文件格式。
- 最小 OpenAPI 是否直接引用本草案中的点级输出字段。

## 完成记录

- 完成日期：2026-06-09。
- 变更文件：
  - `tasks/active/TASK-007-first-review-point-definitions-draft.md`
  - `docs/review-point-definitions.md`
  - `CURRENT_CONTEXT.md`
  - `changelog/2026-06.md`
- 测试结果：完成文档自查；本任务为文档契约任务，未运行代码测试。
- 遗留问题：最小 OpenAPI 契约、字段词库/ValueGrammar/正则边界、样本验证任务仍待创建或执行。
- 备注：未创建业务代码，未搭建脚手架，未安装依赖，未修改数据库。
