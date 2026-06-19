# TASK SPEC：TASK-DOC-002-B 模板路由与 TASK_SPEC 字段补充起草

> 版本：v0.1
> 状态：Done
> 创建日期：2026-06-19
> 完成日期：2026-06-19
> 起草：Codex
> 父任务关联：TASK-DOC-002
> 任务类型：template-draft / router-and-spec-fields
> 执行环境：Claude Code（DeepSeek 模型）

## 任务摘要

在不修改任何非目标文件的前提下，由 CC-DS 执行环境检查并起草 `tasks/TEMPLATE_ROUTER.md` 与 `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md` 的补充草案，固化 Delegation Decision、`readonly-review` 类型以及 `TASK_SPEC` 类型字段。
本任务只可由 Codex 在 A 完成 Review Intake Decision 后派发。外部顾问只可提供审计建议，不得替代 Codex 发起、冻结边界或接纳结果。

## 目标文件

* `tasks/TEMPLATE_ROUTER.md`
* `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`

## Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/active/TASK-DOC-002-multi-agent-delegation-and-readonly-review-governance.md`
* Codex 对 `TASK_SPEC-DOC-002-A` 的 Review Intake Decision 结果
* 已被 Codex 接受或修正的 readonly-review 定义
* 本 TASK_SPEC 文件
* `tasks/TEMPLATE_ROUTER.md`
* `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
* 父任务中的“术语约定”小节

## Scope

* 基于 A 已接受的 readonly-review 定义起草补充草案
* 增加 Delegation Decision
* 增加 `readonly-review` 类型
* 增加 `TASK_SPEC` 类型字段
* 增加“不派 TASK_SPEC 时必须说明原因”
* 保持与现有父 TASK / `TASK_SPEC` / `to-issues` 术语一致

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

* 输出可供 Codex 审定的文档补充草案
* 明确列出将补充到两个目标文件的段落结构
* 明确说明术语复用关系：
  * 父 `TASK`
  * `TASK_SPEC`
  * `readonly-review`
  * `to-issues`
* 文档补充草案中的执行环境表述必须统一为 `Claude Code（DeepSeek 模型）` 或 `CC-DS 执行环境`
* 完成后报告：
  * `git status --short`
  * 修改文件清单
  * 是否存在术语冲突、范围越界风险或待确认项

## 派发前置条件与接纳边界

* 本 TASK_SPEC 仅可由 Codex 派发给 `Claude Code（DeepSeek 模型）`。
* 本任务不得早于 `TASK_SPEC-DOC-002-A` 的 Codex Review Intake Decision。
* 只有当 A 的 readonly-review 定义已被 Codex 接受或由 Codex 修正后，本任务才可执行。
* 本任务输出只构成草案输入，不自动代表路由补充与模板字段补充被接受。
* Codex 接收本任务输出后，必须再次执行 Review Intake Decision，并由 Codex 统一整合正式文档。

## 执行限制

* 不得修改非目标文件
* 不得 `commit / push`
* 不得把本任务扩展到 `AGENTS.md`、`docs/context-management.md`、`CURRENT_CONTEXT.md` 或 `changelog`
* 如发现现有路由术语冲突，必须停在报告中标记“待 Codex 决策”
* 不得并行于 `TASK_SPEC-DOC-002-A` 执行

## 执行结果

* 已实际用于承接 `TASK_SPEC-DOC-002-B` 的起草与 Intake 过程。
* 产出已由 Codex 做 Review Intake Decision，并冻结为 B 的 Accepted Edits List。
* 冻结结果已落地到：
  * `tasks/TEMPLATE_ROUTER.md`
  * `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`

## Codex Intake 结论

* 结论：Accepted after Codex integration
* 说明：
  * 接受了 readonly-review 路由、四者区别、使用时机、Codex 唯一派发边界。
  * 接受了 `TASK_SPEC 类型` 字段及 `execution / readonly-review` 最小二分。
  * 未把 readonly-review 的工作区规则、证据规则、STOP 规则和输出格式重复塞入 execution 模板。

## 不再活跃说明

* 本文件已完成其治理过程记录职责，不再属于 active 任务。
* 正式治理依据以 `tasks/TEMPLATE_ROUTER.md` 与 `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md` 的已整合文本为准。
