# TASK-000：任务标题

状态：待开始

类型：待确认

Task Level：`L2 Feature` / `L3 高风险治理`，待确认

Integration unit：Feature / Milestone / 独立高风险 PR，待确认

优先级：待确认

负责人：待确认

创建日期：待填写

来源：架构文档 / PROJECT_BRIEF.md / CURRENT_CONTEXT.md / ROADMAP.md / ADR，待填写

## 任务命名规范

* 任务文件名使用英文 slug，格式为 `TASK-xxx-english-slug.md`。
* 任务文件标题必须使用中文，格式为 `# TASK-xxx：中文任务名称`。
* 任务正文必须以中文为主。
* 英文 slug 仅作为稳定文件路径和工程标识。

## 背景

说明该任务来自哪个项目目标、架构文档章节、ADR 或上游任务。

## 目标

* 待填写。

## 非目标

* 待填写。

## 输入

* 相关文档：待填写。
* 相关架构章节：待填写。
* 相关 ADR：待填写。
* 上游任务：待填写。

## Task Context

### Required Context

* AGENTS.md
* CURRENT_CONTEXT.md
* 本任务包
* 待填写

### Optional Context

* 待填写

### Out of Scope

* 待填写

## 范围

### 包含

* 待填写。

### 不包含

* 待填写。

## 约束

* 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
* 根据任务需要按需阅读相关文档。
* 不得默认全量读取项目文档。
* 只完成当前任务包定义的范围。
* 不顺手实现任务外功能。
* 不确定内容标记为“待确认”。
* 不得引入架构文档未明确允许的业务能力。
* 如涉及审核结果，必须区分业务 Finding 与 `SYS-*` 系统诊断。
* 如涉及模型，Gemma/A30 只做局部抽取、证据选择或复杂语义辅助；后端做确定性裁判。
* 如涉及外部系统，不得创建 SAP-only、OA-only 或管理台-only 审核路径。
* 如涉及规则、正则、Prompt、模型、合同类型画像或 EvidenceSelector，必须考虑版本化。
* TASK 是计划与责任边界，不自动等于独立 commit、push、PR 或 merge。
* 同一 Feature 下可拆多个 TASK_SPEC；默认在一个 Feature 分支和 PR 中集成。
* L3 必须按可回滚风险边界收口，并在冻结 diff/head SHA 上执行独立只读审计。

## 交付物

* 待填写。

## 验收标准

* 待填写。

## 测试与验证

* 待填写。

## 文档更新要求

* 写回时机：Feature / Milestone / L3 关键门禁，待确认。
* 是否需要更新 `CURRENT_CONTEXT.md`：待确认；不要求每个 TASK_SPEC 中间状态同步。
* 是否需要更新 `docs/*.md`：待确认。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要新增或更新 ADR：待确认。

## Next Task Handoff

* 任务完成后必须判断是否存在明确下一任务。
* 如果存在明确下一任务，输出可直接复制到新窗口执行的 `Next Task Handoff Prompt`。
* 如果不存在明确下一任务，不输出代码块。
* 如果只是建议、征求意见、需要人工确认或没有明确任务编号，不得放入代码块。
* 没有任务编号时不得生成可执行 Handoff Prompt。

## 风险

* 待填写。

## 待确认

* 待填写。

## 完成记录

* 完成日期：待填写。
* 变更文件：待填写。
* 测试结果：待填写。
* 遗留问题：待填写。
* 备注：待填写。
* Integration unit / PR：待填写。
* 独立审计触发依据：待填写。
