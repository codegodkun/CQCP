# TASK-DOC-002：多 Agent 派发与只读复查治理规则固化
状态：Done
类型：文档治理 / 协作治理 / 父 TASK

优先级：中高
负责人：Codex 主控
创建日期：2026-06-19
完成日期：2026-06-19

来源：TASK-025 / TASK-026 收口复盘、CURRENT_CONTEXT.md、AGENTS.md、tasks/TEMPLATE_ROUTER.md

## 术语约定

* Codex：项目总控、父 TASK 边界冻结者、最终审查者。
* Claude Code（DeepSeek 模型）：当前项目中由 Claude Code 承载、接入 DeepSeek 模型的统一执行环境，不表示两个独立 Agent。
* CC-DS 执行环境：`Claude Code（DeepSeek 模型）` 的可选简称。
* 后续文档不得再把 Claude Code 和 DeepSeek 写成两个可独立接收任务的执行者。

## 背景

TASK-025 / TASK-026 收口过程中，Codex 曾连续承担实现、测试、Memory Writeback 和初步验收角色。随后通过一次只读复查型 TASK_SPEC，成功发现并隔离了 `apps/api-server/tmp/` 临时目录，以及 `AGENTS.md`、`docs/context-management.md`、`tasks/TEMPLATE_ROUTER.md` 等文档治理改动混入 TASK-025 工作区的问题。
这说明项目已有 Codex / Claude Code / DeepSeek 的基本职责边界，但仍缺少可执行的长期规则，用于约束：

* 何时必须由 Codex 主控；
* 何时必须拆分 TASK_SPEC；
* 何时必须安排独立只读复查；
* dirty 工作区如何先归属、清理或分离；
* 复查报告如何作为 Codex 的后续决策输入，而不是自动验收结论。

## 目标

* 将本轮已验证有效的协作机制固化为长期项目规则。
* 明确父 TASK 的 Delegation Decision 规则。
* 明确只读复查模板、路由模板和记忆写回规则的治理边界。
* 保证最终治理改动仅落在允许的文档范围内。
* 将 `Claude Code（DeepSeek 模型）` / `CC-DS 执行环境` 术语澄清固化为正式项目规则。

## 主控原则

* Codex 是 `TASK-DOC-002` 的唯一主控、唯一 `TASK_SPEC` 派发者、唯一边界确认者。
* 外部顾问只提供审计建议，不得直接派发 `TASK_SPEC`，不得替代 Codex 做 Review Intake Decision。
* `TASK_SPEC` 的实际派发、边界冻结、Review Intake Decision、最终接纳和正式文档整合，均必须由 Codex 完成。
* 本任务下的任何 `TASK_SPEC` 均不得并行派发；必须按 Codex 明确批准的顺序串行推进。

## 非目标

* 不修改业务代码。
* 不修改 parser、review engine、状态机、result API。
* 不修改 `docs/ARCHITECTURE.md`。
* 不修改 `PRD.md`。
* 不修改数据库迁移。
* 不新增或修改 ADR，除非后续明确发现本任务已越过协作治理边界。
* 不进入 `TASK-027` / `TASK-028` / `TASK-032`。
* 不安装依赖。
* 不 `push`。

## 输入

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/TEMPLATE_ROUTER.md`
* `tasks/TASK-000-template.md`
* `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
* `docs/context-management.md`
* `changelog/2026-06.md`
* `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
* `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务文件
* `tasks/TEMPLATE_ROUTER.md`
* `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
* `docs/context-management.md`

### Optional Context

* `tasks/MVP_TASK_MAP.md`
* `changelog/2026-06.md`
* `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
* `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`

### Out of Scope

* 任意业务实现代码
* 架构文档调整
* 产品需求调整
* 数据库迁移
* ADR 变更

## 范围

### 包含

* 在 `AGENTS.md` 增加协作治理总原则。
* 在 `tasks/TEMPLATE_ROUTER.md` 增加 Delegation Decision 和 `readonly-review` 路由规则。
* 在 `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md` 增加 `TASK_SPEC` 类型字段和 `readonly-review` 约束。
* 新增 `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md` 只读复查模板。
* 在 `docs/context-management.md` 增加只读复查报告与 Review Intake Decision 的写回规则。
* 对 `CURRENT_CONTEXT.md` 和 `changelog/2026-06.md` 做最小写回。
* 在上述治理文档中统一 `Claude Code（DeepSeek 模型）` / `CC-DS 执行环境` 术语，不再并列描述为两个独立执行者。

### 不包含

* 任意业务功能扩展
* 任意测试框架增强
* Git hook、CI、自动化脚本治理
* 任务外文档统一重构

## 默认 ADR 判断

默认判断：`不需要 ADR`。

理由：
* 本任务属于协作流程治理，不改变业务架构。
* 不改变核心审核链路、接口契约、数据库结构、模型职责边界或 PRD。
* 只要最终改动保持在任务定义的文档治理范围内，即不应升级为 ADR。

待确认升级条件：

* 若后续规则被设计为系统主链路强制门禁，而非协作治理规则；
* 若后续规则实质改变 Agent 在项目中的架构级职责边界；
* 若 Memory Writeback 规则被提升为新的架构治理层。

仅在上述条件真实发生时，才重新评估 ADR。

## Delegation Decision

* 是否可拆 TASK_SPEC：是
* 是否必须安排只读复查 TASK_SPEC：是
* 是否允许 Codex 独占实现：否
* 是否允许在 dirty 工作区继续扩任务：否，必须先归属和分离
* 是否允许外部顾问直接派发 TASK_SPEC：否，只允许提出审计建议
* 是否允许 A / B 并行派发：否，必须串行

适合派发给 Claude Code / DeepSeek 的子块：

* 起草只读复查模板
* 起草模板路由补充
* 起草 `TASK_SPEC` 字段扩展
* 对最终文档 diff 执行独立 `readonly-review`

必须由 Codex 自己处理的子块：

* `AGENTS.md` 总原则定稿
* `docs/context-management.md` 的 Memory Writeback 规则定稿
* `CURRENT_CONTEXT.md` 最小写回
* `changelog/2026-06.md` 最小写回
* 最终 diff 审查
* 是否提交的判断

不允许进入的范围：

* `TASK-027`
* `TASK-028`
* `TASK-032`
* 业务代码
* `docs/ARCHITECTURE.md`
* `PRD.md`
* 数据库迁移
* ADR

## TASK_SPEC 候选

### TASK_SPEC-DOC-002-A：起草只读复查模板

* 目标文件：`tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`
* 用途：固化独立第三方只读复查模板
* 执行方式：仅可由 Codex 派发给 `Claude Code（DeepSeek 模型）`
* 产出形式：只输出草案，不修改文件，不 `commit`，不 `push`
* 后置动作：Codex 接收 A 输出后，先执行 Review Intake Decision；只有当 readonly-review 定义被 Codex 接受或修正后，才允许进入 B

### TASK_SPEC-DOC-002-B：检查并起草模板路由补充

* 目标文件：
  * `tasks/TEMPLATE_ROUTER.md`
  * `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
* 用途：补充 Delegation Decision、`readonly-review` 类型和 `TASK_SPEC` 字段
* 执行方式：仅可由 Codex 在 A 完成 Review Intake Decision 之后，派发给 `Claude Code（DeepSeek 模型）`
* 前置条件：A 的 readonly-review 定义已被 Codex 接受或由 Codex 修正
* 产出形式：只输出草案，不修改文件，不 `commit`，不 `push`
* 后置动作：Codex 接收 B 输出后，再执行 Review Intake Decision，然后由 Codex 统一整合正式文档

### TASK_SPEC-DOC-002-R：文档治理只读复查

* 目标对象：TASK-DOC-002 最终文档 diff
* 用途：独立 claim-based review，不自动视为验收结论
* 执行方式：已以最小 R 型 readonly-review 记录完成，结果仅作为 Codex 最终完成态判断输入

## 约束

* 本任务为父 TASK，必须由 Codex 主控边界冻结与最终整合。
* `TASK_SPEC-DOC-002-A` 与 `TASK_SPEC-DOC-002-B` 必须顺序执行，禁止并行。
* 在规则未冻结前，不得直接实施治理正文。
* 在本轮创建草案文件阶段，只允许新增任务文件，不允许修改既有文档。
* 后续任何执行型子任务均不得 `commit`、`push`、`reset`、`checkout`、`clean`。
* 复查报告只作为 Codex 的 Review Intake Decision 输入，不等于自动验收通过。

## 交付物

* 父任务文件
* `tasks/done/TASK_SPEC-DOC-002-A-readonly-review-template-draft.md`
* `tasks/done/TASK_SPEC-DOC-002-B-template-router-and-task-spec-fields-draft.md`
* `tasks/done/TASK_SPEC-DOC-002-R-final-doc-governance-readonly-review.md`
* `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`
* `tasks/TEMPLATE_ROUTER.md` 中的 readonly-review 路由补充
* `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md` 中的 `TASK_SPEC 类型` 字段与最小路由说明

## 执行顺序

1. Codex 派发 `TASK_SPEC-DOC-002-A` 给 `Claude Code（DeepSeek 模型）`，仅起草 `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md` 内容草案。
2. Codex 接收 A 输出后，先做 Review Intake Decision；未接受或未修正前，不得派发 B。
3. 仅当 A 的 readonly-review 定义被 Codex 接受或修正后，Codex 才可派发 `TASK_SPEC-DOC-002-B` 给 `Claude Code（DeepSeek 模型）`。
4. Codex 接收 B 输出后，再做 Review Intake Decision。
5. 两轮 Intake 完成后，由 Codex 统一整合正式文档。
6. 最终 diff 已补做最小 R 型 readonly-review，作为最终完成态判断输入。

## 验收标准

* 父任务文件明确 Delegation Decision。
* 父任务文件明确 `TASK_SPEC-DOC-002-A / B / R` 候选。
* 父任务文件明确默认不需要 ADR。
* 父任务文件明确不允许进入 `TASK-027 / TASK-028 / TASK-032`。
* 父任务文件明确禁止修改业务代码、`docs/ARCHITECTURE.md`、`PRD.md`、数据库迁移、ADR。

## 测试与验证

* 本轮仅验证任务文件与治理文档是否按约束落地。
* 本轮不运行测试或构建。
* 本轮不修改业务代码。

## 风险

* 若子任务边界写得不够硬，后续仍可能出现范围越界。
* 若只读复查边界不清晰，复查报告可能被误当作自动验收结论。
* 若 dirty 工作区规则不先固化，后续父 TASK 仍可能混入无关改动。

## 本轮执行记录

已完成：

* 基于 `TASK_SPEC-DOC-002-A` 已冻结的 accepted definition，新建 `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`
* 在 `tasks/TEMPLATE_ROUTER.md` 增加 readonly-review 路由、扩展“四者区别”、补充使用时机与职责边界
* 在 `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md` 增加 `TASK_SPEC 类型` 字段，并明确：
  * `execution` 使用本模板
  * `readonly-review` 指向 `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`
* 本轮未把 readonly-review 的工作区规则、证据规则、STOP 规则和输出格式回灌到 execution 模板

本轮未做：

* 未修改业务代码
* 未修改 `PRD.md`
* 未修改 `docs/ARCHITECTURE.md`
* 未修改数据库迁移、Docker、后端或前端代码
* 未新增 ADR
* 未 `commit`
* 未 `push`

## 本轮收口决策

* A/B 草案文件处理结论：按治理过程记录保留，不再留在 `tasks/active/`，已转入 `tasks/done/`。
* 判断依据：
  * A/B 并非临时备忘，而是承接了真实派发、真实反馈和真实 Codex Intake 过程的 TASK_SPEC 记录。
  * 父任务、`CURRENT_CONTEXT.md` 与 `changelog/2026-06.md` 已保留必要过程摘要，但仍需要单独任务文件承载过程细节。
* R 处理结论：执行一次最小 `TASK_SPEC-DOC-002-R` 只读复查，并将其结果仅作为 Codex 最终 Review Intake Decision 的输入。
* ADR 判断：仍不需要 ADR；本轮仍未触碰核心审核链路、模型职责边界、SYS/Finding 边界、EvidenceSlot、ReviewPointFamily 或 CandidateResolver。

## 完成摘要

* readonly-review 已形成正式模板：`tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`
* 模板路由已补齐 readonly-review 入口、四者区别、使用时机与 Codex 主控边界
* `TASK_SPEC` 执行模板已补齐 `TASK_SPEC 类型` 字段与 `execution / readonly-review` 最小路由
* A/B/R 三个治理过程 TASK_SPEC 已全部从 active 收口到 done
* 外部顾问不得直接派发 `TASK_SPEC`、Codex 是唯一派发者和唯一 Review Intake Decision 执行者的规则已落到正式治理文档

## Codex Review Intake Decision

* 结论：Accept
* 理由：
  * DoD 已满足。
  * 治理范围已限定在任务模板、路由模板、只读复查模板和最小 Memory Writeback。
  * R 型 readonly-review 已形成最终 diff 复查记录。

## 未触碰范围

* 未修改业务代码
* 未修改 `PRD.md`
* 未修改 `docs/ARCHITECTURE.md`
* 未修改数据库、Docker、后端、前端代码
* 未进入 `TASK-027 / TASK-028 / TASK-032`

## ADR 判断

* 不需要 ADR。

## Next Task Handoff

当前父任务已完成并归档。下一步不是继续扩展治理范围，而是由人工决定是否对当前文档改动执行提交。

Next Task Handoff Prompt

```text
26/06/19 任务交接

请在 CQCP 仓库中接手文档治理收口后的提交决策工作。

已完成前置：
- TASK-DOC-002 已归档到 tasks/done/
- readonly-review 正式模板、模板路由、TASK_SPEC 类型字段已落地
- A/B/R 过程记录已收口到 tasks/done/
- CURRENT_CONTEXT.md 与 changelog/2026-06.md 已完成最小写回

本轮目标：
1. 复核当前 git 工作区中与 TASK-DOC-002 相关的文档改动范围；
2. 判断是否进入提交；
3. 如允许提交，仅准备文档治理相关文件，不得扩大到业务代码；
4. 不进入 TASK-027 / TASK-028 / TASK-032。
```

