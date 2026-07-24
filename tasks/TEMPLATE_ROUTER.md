# 任务模板路由说明

更新日期：2026-06-12

## 1. 目的

本文档只回答一个问题：当前任务应该使用哪个模板。

它用于区分父 `TASK`、`TASK_SPEC` 和 to-issues 草案，避免把任务地图、执行子任务和 issue 草案混用。

## 2. 模板选择表

| 场景 | 使用物 | 说明 |
|---|---|---|
| 只读盘点、诊断、可行性调查或不进入主线的 spike | `L0 探索`，默认不创建 TASK | 形成已确认决策、风险或执行边界时再升级 |
| 无行为变化的状态摘要、链接/路径修正、changelog、记忆压缩 | `L1 小文档`，不单独创建 TASK | 合并为文档批处理或下一 Feature/Milestone 写回 |
| 一个可演示用户能力或完整技术能力 | `L2 Feature` + 父 `TASK` | 一个父 TASK 可含多个 TASK_SPEC；默认一个 Feature PR |
| 涉及架构边界、核心链路、接口契约、数据库结构、状态机、审核结果结构、模型职责边界、ADR、长期项目记忆 | `tasks/TASK-000-template.md` | 这是 Codex 主控的父 `TASK` |
| 父 `TASK` 已冻结边界，现需局部实现、补测试、补 mapper、补页面组件、补错误分支、修局部 bug | `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md` | 这是派发给 Claude Code（DeepSeek 模型）的执行子任务 |
| 父 `TASK` 过大，包含多个模块或后续可能拆成多个 `TASK_SPEC` | 先输出 to-issues 草案 | 先拆执行单元，再决定哪些 issue 转成 `TASK_SPEC` |
| 涉及 MVP 边界变化、新审核点、新输入格式、新模型提供方、公网模型、数据库基线调整、权限边界、SAP/OA 正式联调范围 | 先人工确认 | 确认后再决定创建 `TASK`、`ADR`，或暂不创建 |
| 目标执行型 `TASK_SPEC` 交付物已就绪，且 Codex 判断需要独立只读复查 | `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md` | 这是只读复查类型 `TASK_SPEC`，由 Codex 派发；输出只作为 Codex Review Intake Decision 的输入，不自动代表验收通过 |

上表中的高风险父 TASK 归类为 `L3 高风险治理`。Task Level 由影响决定：文档变更如果改变审计、权限、Git 门禁或生产激活规则，也属于 L3。

## 3. 四者区别

父 `TASK`：
由 Codex 创建和主控，用来定义目标、范围、约束、验收标准、依赖关系和记忆写回要求。

`TASK_SPEC`：
由 Codex 基于父 `TASK` 拆出，只用于边界已冻结的局部执行，不替代父 `TASK`，也不负责重定义架构或契约。它是执行和 Review Intake 单位，不自动等于独立 commit、push、PR 或 merge。

to-issues 草案：
只是拆分草案，用来整理一个父 `TASK` 内部可能的执行单元，不等同于正式 `TASK`，也不等同于 `TASK_SPEC`。

只读复查 `TASK_SPEC`（readonly-review）：
由 Codex 基于已冻结边界派发的只读复查任务类型。只负责对目标执行型 `TASK_SPEC` 的交付物、实现报告和工作区状态做 claim-based 复查，不负责实现、测试、bugfix 或 refactor。其输出只作为 Codex 的 Review Intake Decision 输入，不自动代表验收通过。

## 4. 什么时候使用父 TASK

* 任务会改变或触碰主链路、快照、状态机、接口、数据库、模型职责边界。
* 任务需要 Codex 先定义边界，再决定是否可拆子任务。
* 任务完成后需要写回长期项目记忆，或可能触发 ADR。

## 5. 什么时候生成 TASK_SPEC

* 已存在父 `TASK`。
* 输入输出、错误码、边界和禁改范围已经由 Codex 冻结。
* 执行内容是局部实现，不需要重新定义架构、接口、数据库、状态机或审核链路。

## 6. 什么时候先输出 to-issues 草案

* 一个父 `TASK` 内包含多个相对独立的交付物。
* 存在前后端联动、多个模块并行、或后续可能分别委派给 Claude Code（DeepSeek 模型）的子块。
* Codex 需要先看到拆分结果，才能判断是否值得生成一个或多个 `TASK_SPEC`。

## 7. 什么时候不创建 TASK_SPEC

* 还没有父 `TASK`。
* 边界未冻结。
* 任务本身仍处于 A 类 Codex 主控阶段，边界尚未冻结。
* 任务属于 C 类，必须先人工确认。

## 8. 什么时候必须人工确认

* 任务会扩大 MVP 范围或改变任务地图顺序、协作边界。
* 任务会引入新审核点、新输入格式、新模型提供方、公网模型。
* 任务会调整数据库基线、权限边界、SAP/OA 正式联调范围，或其他可能触发 ADR 的治理决策。

## 9. 职责边界

Codex：
负责主任务定义、边界冻结、模板选择、是否拆 issue 草案、是否生成 `TASK_SPEC`、最终审查和项目记忆写回。

Codex 同时也是 `TASK_SPEC` 的唯一派发者，以及 readonly-review 的唯一 Review Intake Decision 执行者。
外部顾问只提供审计建议，不得直接派发 `TASK_SPEC`，不得替代 Codex 做 Review Intake Decision。

Claude Code（DeepSeek 模型）：
只执行已定界的局部任务，不直接接手父 `TASK`，不改架构、接口、数据库、状态机、审核链路，不写长期项目记忆。

## 10. Feature / Milestone 收口

* L2 默认由一个父 TASK 承载一个 Feature，可包含多个顺序或并行 TASK_SPEC。
* 边界兼容、回滚关系一致的 TASK_SPEC 默认共享一个 Feature 分支和 PR。
* L3 按可回滚风险边界拆 PR，不按每个执行步骤拆 PR。
* active/done 迁移和长期记忆默认在 Feature/Milestone 收口时批量完成。
* 只有改变下一门禁的 post-merge 事实才需要即时写回。

## 11. 什么时候使用 readonly-review TASK_SPEC

* 目标执行型 `TASK_SPEC` 的交付物已就绪。
* Codex 判断需要独立复查其 claim、范围或工作区状态。
* readonly-review 默认不修改文件、不安装依赖、不运行测试或构建，除非该 `TASK_SPEC` 明确授权。
* readonly-review 必须使用 `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`。
