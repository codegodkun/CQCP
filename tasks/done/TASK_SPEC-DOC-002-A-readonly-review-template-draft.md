# TASK SPEC：TASK-DOC-002-A 只读复查模板起草

> 版本：v0.1
> 状态：Done
> 创建日期：2026-06-19
> 完成日期：2026-06-19
> 起草：Codex
> 父任务关联：TASK-DOC-002
> 任务类型：template-draft / readonly-review-template
> 执行环境：Claude Code（DeepSeek 模型）

## 任务摘要

在不修改任何非目标文件的前提下，由 CC-DS 执行环境起草 `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md` 草案，用于固化只读复查任务的结构、身份、输出和禁止项。
本任务只可由 Codex 派发。外部顾问只可提供审计建议，不得替代 Codex 发起、冻结边界或接纳结果。

## 目标文件

* `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`

## Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/active/TASK-DOC-002-multi-agent-delegation-and-readonly-review-governance.md`
* 本 TASK_SPEC 文件
* `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
* `tasks/TEMPLATE_ROUTER.md`
* 父任务中的“术语约定”小节

## Scope

* 定义独立第三方复查身份
* 定义默认怀疑，不默认信任
* 定义 claim-based review
* 定义“测试通过不等于细节为真”
* 定义证据标注规则
* 定义禁止修改、`commit`、`push`、`reset`、`checkout`、`clean`
* 定义输出格式
* 定义 Review Intake Decision 边界
* 只输出模板草案和必要说明，由 Codex 后续执行 Review Intake Decision

## Out of Scope

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `changelog/2026-06.md`
* 任意业务代码
* 任意架构文档
* 任意数据库迁移
* 任意 ADR

## 禁止项

* 不得修改目标文件之外的任何文件
* 不得修改 `AGENTS.md`
* 不得修改 `CURRENT_CONTEXT.md`
* 不得修改 `changelog/2026-06.md`
* 不得修改业务代码
* 不得新增依赖
* 不得 `commit`
* 不得 `push`
* 不得 `git reset --hard`
* 不得 `git checkout -- .`
* 不得 `git clean -fd`
* 不得把 `Claude Code` 和 `DeepSeek` 写成两个可独立接收任务的执行者

## 输出要求

* 输出一个可被后续 Codex 审定的模板草案
* 模板必须使用中文正文
* 模板中的规则必须可执行，不能只写抽象原则
* 模板必须明确“复查报告不代表最终验收通过”
* 模板中的执行环境表述必须统一为 `Claude Code（DeepSeek 模型）` 或 `CC-DS 执行环境`
* 完成后报告：
  * `git status --short`
  * 修改文件清单
  * 是否存在术语冲突或待确认项

## 派发与接纳边界

* 本 TASK_SPEC 仅可由 Codex 派发给 `Claude Code（DeepSeek 模型）`。
* 本 TASK_SPEC 的输出只构成草案输入，不自动代表模板被接受。
* Codex 接收本任务输出后，必须先做 Review Intake Decision。
* 在 Codex 明确接受或修正 readonly-review 定义之前，不得派发 `TASK_SPEC-DOC-002-B`。

## 执行限制

* 不得修改非目标文件
* 不得 `commit / push`
* 不得把本任务扩展为 `AGENTS.md`、`CURRENT_CONTEXT.md` 或 `changelog` 写回
* 如发现边界冲突，必须停在报告中标记“待 Codex 决策”
* 不得并行触发或建议触发 `TASK_SPEC-DOC-002-B`

## 执行结果

* 已实际用于承接 `TASK_SPEC-DOC-002-A` 的派发与起草过程。
* 产出已由 Codex 做 Review Intake Decision，并冻结为 readonly-review 的 accepted definition。
* 冻结结果已落地为正式文件 `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`。

## Codex Intake 结论

* 结论：Accepted after Codex rewrite
* 说明：
  * 接受了 readonly-review 的独立类型、dirty 工作区识别、证据标注、输出格式与 Review Intake Decision 边界。
  * 拒绝了“git status 必须为空，否则 STOP”“只要 dirty 就不能复查”“不可验证的人际断言”“过细且非必要 Git 禁令”等内容。
  * 最终 accepted definition 已由 Codex 修正并冻结，不再以本草案文本作为正式治理依据。

## 不再活跃说明

* 本文件已完成其治理过程记录职责，不再属于 active 任务。
* 正式治理依据以 `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md` 为准。
