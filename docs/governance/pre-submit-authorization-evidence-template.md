# 提交前授权证据模板

## 目的

本模板用于记录 CQCP 每次 commit、push、merge 或例外操作前的授权证据。它不替代用户真实授权、独立只读审计、Codex Review Intake Decision 或 GitHub required status checks，只用于让证据格式稳定、可审查、可回溯。

## 使用时机

以下动作前必须填写或引用本模板：

* stage / commit 当前任务范围文件。
* push 工作分支。
* 将 PR 标记为 ready。
* merge PR。
* 对 required status checks、branch protection、ruleset 或 direct push 例外作出处理。

## 标准字段

用于 PR body 时，字段值必须写在冒号后同一行，供 `Authorization evidence check` 自动检查：

```text
Task:
Governance mode:
Allowed files:
Forbidden files:
Commit authorization:
Push authorization:
Merge authorization:
Test evidence:
Independent review:
Memory writeback:
Out-of-scope confirmation:
```

## 字段说明

* `Task`：任务编号和任务文件路径，例如 `TASK-GOV-006 / tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`。
* `Governance mode`：当前真实治理模式，例如 `LEGACY_MANUAL`、`PR_MANUAL_REVIEW` 或 `PR_REQUIRED_CHECKS`。未提供 branch protection 与 required status checks 验证证据时，不得填写 `PR_REQUIRED_CHECKS`。
* `Allowed files`：本次允许修改、stage、commit 或 push 的文件路径。
* `Forbidden files`：本任务明确禁止修改的范围。
* `Commit authorization`：用户是否已明确授权 commit；如未授权，写“未授权，不执行 commit”。
* `Push authorization`：用户是否已明确授权 push；如未授权，写“未授权，不执行 push”。
* `Merge authorization`：用户是否已明确授权 merge；如未授权，写“未授权，不执行 merge”。
* `Test evidence`：实际运行的命令、结果和失败归因；文档任务至少记录 `git diff --check` 与 `git status --short`。
* `Independent review`：独立只读审计状态；如未要求或未执行，必须说明原因，不能写成已通过。
* `Memory writeback`：说明 `CURRENT_CONTEXT.md`、`changelog/当前月份.md`、当前 TASK 和任务地图的写回状态。
* `Out-of-scope confirmation`：确认未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、PRD、架构文档或 ADR，除非当前任务明确授权。

## 禁止写法

以下内容不能作为有效字段值：

* 空白值。
* `待确认`、`TBD`、`TODO`、`N/A`。
* “见上文”“同前”“已处理”这类无法独立审查的描述。
* 用 `CURRENT_CONTEXT.md` 自述替代真实命令、PR、commit、审计报告或用户授权记录。

## 示例

```text
Task: TASK-GOV-006 / tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md
Governance mode: LEGACY_MANUAL / PR_MANUAL_REVIEW / PR_REQUIRED_CHECKS（按当前真实证据填写；未提供 branch protection 与 required status checks 验证证据时，不得填写 PR_REQUIRED_CHECKS）。
Allowed files: .github/pull_request_template.md, .github/workflows/ci.yml, scripts/check-pr-authorization-evidence.mjs, docs/governance/pre-submit-authorization-evidence-template.md, docs/DEVELOPMENT.md, docs/VERIFY.md, CURRENT_CONTEXT.md, tasks/MVP_TASK_MAP.md, changelog/2026-07.md, tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md
Forbidden files: 业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、PRD、docs/ARCHITECTURE.md、decisions/*。
Commit authorization: 未授权，不执行 commit。
Push authorization: 未授权，不执行 push。
Merge authorization: 未授权，不执行 merge。
Test evidence: valid fixture 通过；invalid fixture 预期失败；git diff --check 通过；git status --short 已核查。
Independent review: 本轮未执行独立只读审计；后续如归档或纳入 required check，必须另行安排。
Memory writeback: 已更新 CURRENT_CONTEXT.md、tasks/MVP_TASK_MAP.md、changelog/2026-07.md 和 TASK-GOV-006。
Out-of-scope confirmation: 未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、PRD、架构文档或 ADR。
```
