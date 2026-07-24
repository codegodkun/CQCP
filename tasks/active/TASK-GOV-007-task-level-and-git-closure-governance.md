# TASK-GOV-007：Task Level 与 Git 收口治理优化

状态：Active / 规则重建完成 / 独立审计 GO / Codex Review Intake ACCEPT / PR 待创建

类型：治理流程

Task Level：`L3 高风险治理`

Integration unit：独立高风险 PR

优先级：P0

负责人：Codex

创建日期：2026-07-23

来源：用户要求完成文档治理；2026-07-23 当前任务管理和 Git 收口流程只读优化评估；2026-07-24 用户授权必要的 commit、push、PR、merge

## 背景

当前仓库已经建立父 `TASK`、局部 `TASK_SPEC`、完成态复核、独立只读审计、PR 和授权证据门禁，但实际执行中仍把任务、执行规格、commit、PR、merge 和项目记忆写回视为近似一一对应关系。

2026-07-01 至 2026-07-14 的 `master` 历史包含大量计划接纳、审查记录、push 状态、post-merge 同步和归档状态文档提交。本任务在不削弱核心审核链路、评测正确性和生产激活门禁的前提下，将执行粒度、审计粒度和 Git 集成粒度分离。

## 目标

* 建立 `L0 探索 / L1 小文档 / L2 Feature / L3 高风险治理` 四级 Task Level。
* 明确 `TASK_SPEC` 是执行与审查边界，不自动等于 commit、push、PR 或 merge。
* 默认以 Feature 作为 PR 集成单位，以 Milestone 作为任务归档和项目记忆收口单位。
* 将独立审计绑定到变更内容和风险，而不是一切 push 操作。
* 合并低价值的中间状态提交、post-merge 状态 PR 和单文件归档 PR。
* 保留人工 ground truth、expected/fixture、核心审核链路、生产激活、数据库/API/CI/安全等高风险门禁。

## 非目标

* 不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker 或 Review assets。
* 不修改 `.github/workflows/*`、授权证据检查脚本、branch protection、required status checks 或 repository ruleset。
* 不改变 Codex / Claude Code / DeepSeek 的业务代码角色分离。
* 不解除 TASK-028 / TASK-031 / TASK-032 或 TASK-036-C2 的现有任务级门禁。
* 不追溯改写已有 commit、PR、merge 或审计结论。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `docs/context-management.md`
* `docs/DEVELOPMENT.md`
* `docs/VERIFY.md`
* `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
* `tasks/TEMPLATE_ROUTER.md`
* `tasks/TASK-000-template.md`
* `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
* `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`

### Optional Context

* `ROADMAP.md`
* `tasks/MVP_TASK_MAP.md`
* `.github/pull_request_template.md`
* `docs/governance/pre-submit-authorization-evidence-template.md`

### Out of Scope

* 业务实现与产品架构
* GitHub 外部配置
* 既有任务重新拆分或历史提交重写
* TASK-036-C2 的规格、实现或状态修改

## 允许修改范围

* `AGENTS.md`
* `docs/context-management.md`
* `docs/DEVELOPMENT.md`
* `docs/VERIFY.md`
* `ROADMAP.md`
* `tasks/TEMPLATE_ROUTER.md`
* `tasks/TASK-000-template.md`
* `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
* `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`
* `.github/pull_request_template.md`
* `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
* `docs/governance/pre-submit-authorization-evidence-template.md`
* `CURRENT_CONTEXT.md`
* `tasks/MVP_TASK_MAP.md`
* `changelog/2026-07.md`
* 本任务文件

## 冻结规则

### Task Level

| Level | 定位 | 默认 Git 收口 | 独立审计 |
|---|---|---|---|
| `L0 探索` | 只读盘点、诊断、可行性调查、throwaway spike | 默认不进入主线 | 默认不需要 |
| `L1 小文档` | 无行为变化的状态摘要、路径、链接、changelog、记忆压缩 | 文档批处理或并入下一 Feature/Milestone | 默认 Codex 自查 |
| `L2 Feature` | 一个可演示用户能力或完整技术能力 | 一个 Feature 分支、一个 PR、一次 merge | 风险触发 |
| `L3 高风险治理` | 架构、核心审核语义、评测正确性、生产激活、安全/CI/数据库等 | 按可回滚风险边界独立 PR | 必须 |

### 集成与写回

* 一个父 `TASK` 可以包含多个 `TASK_SPEC`；这些规格默认在同一 Feature 分支中实现和审查。
* commit 只在形成有意义、可理解、可回滚的变化时创建，不为每个计划、审查、push 或 checks 状态单独创建。
* `push` 是传输动作；若冻结 diff 未变化，不因 push 本身重复执行完整独立审计。
* post-merge 状态只有在会改变下一门禁时才即时写回；否则并入下一相关 Feature 或 Milestone 文档批次。
* `tasks/active → tasks/done` 默认在 Feature 或 Milestone 收口时批量执行。
* GitHub 是 PR、checks 和 merge 状态的事实源；项目记忆保留结果摘要和必要引用，不复制完整流水账。

## 验收标准

1. 四级 Task Level 在规则入口、开发流程、验证规则和模板中含义一致。
2. 文档明确区分任务、执行规格、commit、PR、merge 和 Milestone。
3. L2 默认采用一个 Feature PR；L3 按可回滚风险边界收口。
4. L0/L1 不再强制创建 TASK 或逐次 Memory Writeback。
5. 普通 push 不再自动等同于必须重新执行完整独立审计。
6. 人工 ground truth、expected/fixture、核心审核链路、生产激活、数据库/API/CI/安全等高风险门禁继续要求独立审计。
7. 父任务归档审计从“一律触发”调整为 L3、正式 Milestone 和风险触发；现有明确任务门禁不追溯解除。
8. `CURRENT_CONTEXT.md` 不再承担详细 PR 流水账；changelog 默认一条 Feature/Milestone 摘要。
9. PR 模板能够记录 Task Level、Integration unit 和授权包络，同时保留现有检查脚本需要的字段。
10. 不修改业务代码、workflow、检查脚本、fixture、expected JSON、OpenAPI、数据库、Docker、架构文档或 ADR。
11. `git diff --check` 通过。
12. 完成独立 agent 只读审计，并由 Codex 单独形成 Review Intake Decision。

## 测试与验证

```powershell
git diff --check
git diff --name-status
git status --short
rg -n "L0 探索|L1 小文档|L2 Feature|L3 高风险治理" AGENTS.md docs tasks ROADMAP.md .github/pull_request_template.md
rg -n "push.*独立|父任务归档.*独立|每次任务结束.*CURRENT_CONTEXT" AGENTS.md docs tasks
```

## 文档更新要求

* `CURRENT_CONTEXT.md`：记录本治理规则、真实 Git 状态和边界。
* `tasks/MVP_TASK_MAP.md`：记录治理层协作与收口规则变化。
* `changelog/2026-07.md`：记录一次治理变更，不记录逐步骤流水账。
* ADR：不需要。本任务调整开发治理流程，不改变产品架构、审核链路、模型职责、数据模型或对外契约。

## 风险

* 过度合并可能导致 Feature diff 过大；L2 仍须按可审查和可回滚边界拆分。
* 审计从“按操作”改为“按内容”后，必须以冻结 diff/head SHA 证明审计后内容未变化。
* 既有活跃任务的显式强门禁继续有效，不能用新分级追溯绕过。

## Git 基线重建记录

* 初始本地规则曾位于 `codex/task-036-consistency-set-runtime@3adcab4` 的未提交工作区，不能作为主线确认基线。
* 2026-07-24 从 `origin/master@97ef08f1cae88e8a702069eb0e07c2035b3b063f` 创建隔离工作区与分支 `codex/task-gov-007-task-level-git-closure`。
* 本分支只重建治理规则及对应项目记忆，不带入 TASK-036 B1/B2/C1、TASK-036-C2 或 TASK-MVP-001 规划文件。
* 原独立审计基线因重建而失效；必须对本分支冻结 diff 重新执行独立只读审计。

## 完成记录

* 完成日期：未完成。
* 变更文件：以允许修改范围内的实际 diff 为准。
* 验证结果：`git diff --check` 通过；冻结快照仅包含允许范围内 16 个文件。
* 独立审计：`GO_TO_CODEX_REVIEW_INTAKE`；12 项验收全部通过，无 blocking findings；审计前冻结 manifest SHA-256 为 `45b528a24dc750dbd8fadb5ce3ed99af5788357c1e68a9b24c35d798e109faac`。
* Codex Review Intake：`ACCEPT_GOVERNANCE_DIFF / GO_TO_COMMIT_AND_PR`；审计后仅允许写回本审计与接纳状态，须经聚焦增量复核。
* Integration unit / PR：独立高风险 PR / 待创建。
* 独立审计触发依据：治理门禁、授权包络和 Git 收口规则变化。

## Next Task Handoff

当前明确下一步为：完成审计状态增量复核、提交、PR、CI 和 merge。合并后再从新的 `origin/master` 冻结 `TASK-037 / ADR-017`；不得把两个 L3 风险边界合并成一个 PR。
