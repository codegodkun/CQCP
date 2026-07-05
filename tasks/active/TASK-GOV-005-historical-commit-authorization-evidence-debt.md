# TASK-GOV-005：历史 commit / push 授权证据治理债务

状态：Active / 已定界 / 独立只读审计 GO / 长期治理边界保留

类型：治理债务任务 / Codex 主控

优先级：P0

负责人：Codex

创建日期：2026-07-04

来源：`TASK-EVAL-001` 重新定界 Review Intake Decision、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`

## 背景

`TASK-EVAL-001` 父任务当前为 `REBASELINED / Active / 不归档 / 不进入 TASK-028`。其中原 DoD #12 要求 commit / push 必须单独取得用户确认；rebaseline 后该项固定为未通过、未补足，不再作为可由事后补写文档修复的完成项。

经当前仓库记录和 GOAL-2 证据恢复结果判断，`TASK-EVAL-001-A` 与 `TASK-EVAL-001-B` 的历史 commit / push 授权链无法恢复到可完整核实状态。事后独立复核、定向测试 `30/30 PASS`、已 merge / push 内容和既有补偿性审计不被追溯否定，但它们不能替代提交前授权门禁，也不能支撑 `TASK-EVAL-001` 父任务归档。

本任务将该历史授权链不可完整核实问题从 `TASK-EVAL-001` 主任务中拆出，作为治理债务单独追踪。

## 目标

* 固化 A/B 历史 commit / push 授权链不可完整核实的治理债务边界。
* 明确该债务不追溯否定已 merge / push 内容、事后独立审计结论或定向测试结果。
* 明确该债务阻止其作为后续绕过 commit / push 明确授权门禁的先例。
* 为后续父任务归档、Review Intake、PR 审查和提交前判断提供可引用的治理记录。

## 非目标

* 不写业务代码。
* 不修改测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、PRD、架构文档或 ADR。
* 不追溯重写 Git 历史。
* 不重新判断已 merge 内容的业务正确性。
* 不替代 `TASK-EVAL-001` 的重新定界、独立只读审计或后续归档门禁。
* 不解除 `TASK-028`、`TASK-031` 或 `TASK-032` 门禁。
* 不覆盖 `DEBT-001-03` parser provenance / `SourceAnchor` 债务。
* 不覆盖 `DEBT-001-05` real DOCX `TABLE_CELL` 覆盖债务。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
* `tasks/MVP_TASK_MAP.md`
* `docs/context-management.md`
* `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`

### Optional Context

* `tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`
* `tasks/done/TASK-GOV-004-pr-based-multi-agent-governance.md`
* `changelog/2026-06.md`
* `changelog/2026-07.md`

### Out of Scope

* 生产代码、测试代码、fixture、expected JSON、OpenAPI、数据库、Docker、workflow。
* `PRD.md`、`docs/ARCHITECTURE.md`、ADR。
* `TASK-EVAL-001` 归档。
* `TASK-028` / `TASK-031` / `TASK-032` Review Intake 或实现。

## 债务记录

Finding：
`TASK-EVAL-001-A` 与 `TASK-EVAL-001-B` 的历史 commit / push 授权链无法完整核实，无法支撑 `TASK-EVAL-001` DoD #12。

验证：
当前仓库记忆和 `TASK-EVAL-001` 记录均显示 DoD #12 未通过、未补足；旧 `GO TO ARCHIVE WITH CONDITIONS` 口径已回滚；未找到可恢复到完整链路的历史授权证据。

影响：
该缺口阻止 `TASK-EVAL-001` 父任务归档。它不追溯否定已 merge / push 内容、事后独立审计结论或定向测试 `30/30 PASS`，但不得被用作后续绕过 commit / push 明确授权门禁的先例。

处理方向：
将该问题作为历史流程治理债务长期保留；后续任何父任务归档、commit、push、PR merge 或 required checks 例外，必须重新取得当次明确授权和对应审计证据。

## 处理决定

2026-07-04 Codex 对 `TASK-GOV-005` 执行治理债务定界，结论为：`BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`。

本任务不再尝试恢复 `TASK-EVAL-001-A/B` 的历史 commit / push 授权链。原因是当前仓库记忆、`TASK-EVAL-001` rebaseline 记录和 GOAL-2 证据恢复结论均指向同一事实：历史授权证据无法恢复到可完整核实状态。

该结论的含义：

* 历史授权链缺口作为长期治理边界保留。
* 不追溯否定 `TASK-EVAL-001-A/B` 已 merge / push 内容。
* 不否定事后独立复核、定向测试 `30/30 PASS` 或既有质量证据。
* 不补足 `TASK-EVAL-001` DoD #12。
* 不支撑 `TASK-EVAL-001` 父任务归档。
* 不授权 `TASK-028`、`TASK-031`、`TASK-032`。
* 不覆盖 `DEBT-001-03` parser provenance / `SourceAnchor`。
* 不覆盖 `DEBT-001-05` real DOCX `TABLE_CELL`。

后续治理使用方式：

* 后续父任务归档、commit、push、PR merge、required checks 例外或 direct push 例外，必须取得当次明确授权，不能引用 `TASK-EVAL-001-A/B` 的历史处理作为先例。
* 后续 Review Intake 若发现类似“历史授权链不可完整核实”问题，应直接标记为治理债务或阻塞项，不得通过事后测试或项目记忆补写恢复为提交前授权。
* 若需要机制化治理，应另行定界提交前授权证据模板、GitHub Check / PR 模板或审计报告格式；本任务不直接修改 workflow、branch protection、ruleset 或模板。

## 只读状态确认

2026-07-04 处理本任务前执行只读确认：

```text
git status --short:
 M CURRENT_CONTEXT.md
 M changelog/2026-07.md
 M tasks/MVP_TASK_MAP.md
 M tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md
?? tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md

git status -sb:
## master...origin/master
 M CURRENT_CONTEXT.md
 M changelog/2026-07.md
 M tasks/MVP_TASK_MAP.md
 M tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md
?? tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md

git rev-list --left-right --count origin/master...HEAD:
0 0

git log --oneline -8:
965af67 Merge pull request #16 from codegodkun/codex/task-debt-001-archive
fc56e0c docs(task): archive TASK-DEBT-001
80e0dad Merge pull request #15 from codegodkun/codex/task-debt-001-docs-sync-pr
e9995f2 docs(task): translate PR 15 added notes to Chinese
5f22ad9 docs(context): slim current context and sync TASK-DEBT-001 status
13794fb Merge pull request #14 from codegodkun/codex/task-debt-001-prearchive-writeback
d232df3 docs(task): record TASK-DEBT-001 prearchive review
15888df Merge pull request #13 from codegodkun/codex/task-debt-001-c-implementation
```

说明：`git status -sb` 同时输出了本机全局 ignore 权限 warning，不影响本任务的治理文档判断。

## 验收标准

1. `TASK-EVAL-001` 已记录新的 Codex Review Intake Decision：`NO-GO TO ARCHIVE / SPLIT GOVERNANCE DEBT`。
2. `TASK-EVAL-001` 已完成 rebaseline，并明确本债务不补足 DoD #12。
3. 本任务文件已创建，并明确债务不追溯否定已 merge 内容。
4. 本任务文件已明确债务不得成为后续绕过授权门禁的先例。
5. 本任务已记录处理决定：`BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`。
6. `CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md` 和 `changelog/2026-07.md` 已同步记录本任务。
7. 本轮不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、PRD、架构文档或 ADR。
8. `git diff --check` 通过。
9. `git status --short` 只包含本轮治理文档变更和已有同范围文档变更。

## 测试与验证

本任务为文档治理债务拆分，不运行业务测试。最低验证：

```bash
git diff --check
git status --short
```

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是，记录 `TASK-GOV-005` 已拆出及其边界。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：是，记录任务地图层面的治理债务。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要新增或更新 ADR：否，本任务不改变架构、审核链路、模型职责、EvidenceSlot、ReviewPointFamily 或 CandidateResolver。

## Next Task Handoff

本任务当前已完成治理债务定界。下一步不是业务实现，也不是 `TASK-EVAL-001` 归档；如需继续处理，应由独立 agent 对 `TASK-GOV-005` 做只读审计，或由用户另行确认是否需要建立提交前授权证据模板 / GitHub Check / PR 模板等机制化治理任务。

## 风险

* 若后续把本债务误读为“历史授权缺口已补足”，会错误恢复 `TASK-EVAL-001` 归档路径。
* 若后续把事后测试和审计等同于提交前授权门禁，会削弱 `TASK-GOV-003` / `TASK-GOV-004` 已建立的角色分离和 required checks 治理边界。

## 待确认

* 待确认：后续是否需要为历史授权证据保全建立更细的审计模板或 GitHub Check 机制。
* 待确认：是否安排独立 agent 对 `TASK-GOV-005` 执行只读审计。

## 完成记录

* 完成日期：2026-07-04。
  * 说明：此处仅指 `TASK-GOV-005` 的治理债务定界完成，不表示任务已归档；本任务仍长期保留在 `tasks/active/`。
* 变更文件：`tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-07.md`。
* 测试结果：文档级验证，`git diff --check` 通过；未运行业务测试。
* 独立只读审计：2026-07-04 用户提供独立审计结论为 `GO`；阻塞问题为无；非阻塞问题为完成记录措辞可能混淆“债务定界完成”和“任务归档完成”，已在本完成记录中补充说明。
* 遗留问题：是否需要机制化提交前授权证据模板 / GitHub Check / PR 模板仍待确认。
* 备注：本任务不写业务代码，不修改测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、PRD、架构文档或 ADR；不补足 `TASK-EVAL-001` DoD #12；不解除 `TASK-028` / `TASK-031` / `TASK-032` 门禁。
