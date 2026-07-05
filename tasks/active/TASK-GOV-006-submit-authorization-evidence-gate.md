# TASK-GOV-006：提交前授权证据模板与 PR 文本门禁

状态：Active / 本地准备中 / 待提交 / 待 PR 验证 / 不配置 required status checks

类型：Governance

优先级：P0

负责人：Codex

创建日期：2026-07-04

来源：`TASK-GOV-005` 遗留问题、`CURRENT_CONTEXT.md` 待确认事项、用户确认“建立机制化提交前授权证据模板 / GitHub Actions PR body 文本检查 / PR 模板”

## 背景

`TASK-GOV-005` 已确认 `TASK-EVAL-001-A/B` 历史 commit / push 授权链无法恢复到可完整核实状态。该债务不追溯否定已 merge / push 内容，但不得成为后续绕过 commit / push 明确授权门禁的先例。

当前仓库已有第一阶段 required status checks：`Backend Gradle tests` 与 `Admin web lint, tests, and build`。但提交前授权证据、PR 描述中的治理字段、独立审计状态和用户授权记录仍主要依赖人工自觉与文档规则，尚未形成 PR body 层面的自动检查。本任务不配置新的 branch protection、repository ruleset 或 required status checks；未提供对应 GitHub 配置验证证据时，不得宣称本任务达到或变更 `PR_REQUIRED_CHECKS`。

本任务建立轻量机制化治理：通过授权证据模板、PR 模板和 GitHub Actions 文本检查，阻止 PR 在缺少基本授权证据字段时通过 CI。

## 目标

* 建立提交前授权证据模板，统一 commit / push / merge 授权记录格式。
* 建立 PR 模板，要求每个 PR 填写任务编号、允许文件、禁止文件、测试证据、独立审计状态和授权记录。
* 建立 GitHub Actions PR body 文本检查，确保授权证据字段存在且非空。
* 将该检查明确标注为文本证据门禁，不替代真实用户授权、独立只读审计、GitHub branch protection、required status checks 或 `CQCP Code Review` / `CQCP Spec & Docs Review`。

## 非目标

* 不修改业务代码。
* 不修改测试、fixture、expected JSON、OpenAPI、数据库、Docker、PRD、架构文档或 ADR。
* 不配置 GitHub branch protection、repository ruleset 或 required status checks。
* 不发布 `CQCP Code Review` / `CQCP Spec & Docs Review` Check Run 或 Commit Status。
* 不归档 `TASK-EVAL-001` 或 `TASK-GOV-005`。
* 不解除 `TASK-028` / `TASK-031` / `TASK-032` 门禁。
* 不把 PR 文本字段填写等同于真实授权已经发生。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`
* `tasks/done/TASK-GOV-004-pr-based-multi-agent-governance.md`
* `docs/DEVELOPMENT.md`
* `docs/VERIFY.md`
* `docs/context-management.md`

### Optional Context

* `tasks/MVP_TASK_MAP.md`
* `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
* `.github/workflows/ci.yml`
* `changelog/2026-07.md`

### Out of Scope

* 业务实现、测试实现、fixture、expected JSON、OpenAPI、数据库、Docker、PRD、架构文档、ADR。
* GitHub branch protection、repository ruleset、required status checks 外部配置。
* 独立 agent 自动化实现。

## 允许修改范围

* `.github/pull_request_template.md`
* `.github/workflows/ci.yml`
* `scripts/check-pr-authorization-evidence.mjs`
* `scripts/fixtures/valid-pr-authorization-evidence.md`
* `scripts/fixtures/invalid-pr-authorization-evidence.md`
* `docs/governance/pre-submit-authorization-evidence-template.md`
* `docs/DEVELOPMENT.md`
* `docs/VERIFY.md`
* `CURRENT_CONTEXT.md`
* `tasks/MVP_TASK_MAP.md`
* `changelog/2026-07.md`
* 本任务文件

## 授权证据字段

PR body 必须包含 `CQCP Authorization Evidence` 区块，并填写以下字段：

```text
* Task:
* Governance mode:
* Allowed files:
* Forbidden files:
* Commit authorization:
* Push authorization:
* Merge authorization:
* Test evidence:
* Independent review:
* Memory writeback:
* Out-of-scope confirmation:
```

说明：上方字段名为脚本检查契约。字段值必须写在冒号后同一行，不得为空，不得只写 `待确认`、`TBD`、`TODO`、`N/A` 或模板占位说明。

## 检查机制

新增 `Authorization evidence check` GitHub Actions job：

* 仅在 `pull_request` 事件运行。
* 读取 PR body。
* 调用 `node scripts/check-pr-authorization-evidence.mjs`。
* 若字段缺失或占位，job 失败。
* `push` 到 `master` 时不运行该 job，因为 push 事件没有 PR body。

该 job 只检查文本字段，不证明字段内容客观真实。字段真实性仍由 Codex Review Intake、独立只读审计、用户授权和 GitHub PR 记录共同判断。

## 验收标准

1. 应创建 `TASK-GOV-006` 并明确目标、非目标、允许修改范围和禁止范围。
2. 应创建提交前授权证据模板。
3. 应创建 PR 模板，并包含 `CQCP Authorization Evidence` 区块。
4. 应创建无依赖检查脚本，能识别有效 PR body 与无效 PR body。
5. `.github/workflows/ci.yml` 应新增 `Authorization evidence check` job，且仅在 `pull_request` 事件运行。
6. `docs/DEVELOPMENT.md` 和 `docs/VERIFY.md` 应记录提交前授权证据规则。
7. `CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md` 和 `changelog/2026-07.md` 应完成 Memory Writeback。
8. 本轮不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、PRD、架构文档或 ADR。
9. 脚本正向 fixture 验证通过。
10. 脚本反向 fixture 验证失败且输出缺失或占位字段。
11. `git diff --check` 通过。
12. `git status --short` 输出可解释，且不包含任务外代码变更。

## 测试与验证

```powershell
node scripts/check-pr-authorization-evidence.mjs scripts/fixtures/valid-pr-authorization-evidence.md
node scripts/check-pr-authorization-evidence.mjs scripts/fixtures/invalid-pr-authorization-evidence.md
git diff --check
git status --short
```

反向 fixture 命令预期返回非零退出码；验证时必须明确记录该失败是预期行为。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是，记录 `TASK-GOV-006` 当前状态与边界。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：是，记录任务地图层面的治理机制。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要更新 `docs/DEVELOPMENT.md` / `docs/VERIFY.md`：是，记录开发与提交前检查规则。
* 是否需要新增或更新 ADR：否。本任务不改变核心审核链路、模型职责、SYS/Finding 边界、EvidenceSlot、ReviewPointFamily 或 CandidateResolver。

## 风险

* PR body 文本检查只能防止字段缺失，不能证明用户授权、独立审计或测试证据真实发生。
* 如果后续把该 job 纳入 required status checks，需要另行取得用户授权并采集 branch protection / required status checks 证据。
* 如果 PR 作者填写虚假内容，仍需 Codex Review Intake 和独立只读审计识别。

## 待确认

* 待确认：是否在后续独立任务中把 `Authorization evidence check` 配置为 required status check。
* 待确认：是否后续机制化发布 `CQCP Code Review` / `CQCP Spec & Docs Review` Check Run 或 Commit Status。

## Next Task Handoff

本任务当前准备提交前授权证据模板、PR 模板和 PR body 文本检查。后续如需把该 check 纳入 GitHub required status checks，必须另行定界任务并取得用户明确授权；本任务不直接配置 branch protection 或 ruleset。

## 本地准备记录

* 准备日期：2026-07-04。
* 变更文件：
  * `.github/pull_request_template.md`
  * `.github/workflows/ci.yml`
  * `scripts/check-pr-authorization-evidence.mjs`
  * `scripts/fixtures/valid-pr-authorization-evidence.md`
  * `scripts/fixtures/invalid-pr-authorization-evidence.md`
  * `docs/governance/pre-submit-authorization-evidence-template.md`
  * `docs/DEVELOPMENT.md`
  * `docs/VERIFY.md`
  * `CURRENT_CONTEXT.md`
  * `tasks/MVP_TASK_MAP.md`
  * `changelog/2026-07.md`
  * 本任务文件
* 本轮本地验证：
  * `node scripts/check-pr-authorization-evidence.mjs scripts/fixtures/valid-pr-authorization-evidence.md`：通过，输出 `CQCP authorization evidence check passed.`。
  * `node scripts/check-pr-authorization-evidence.mjs scripts/fixtures/invalid-pr-authorization-evidence.md`：按预期失败，退出码为 1，并列出 11 个空白或占位字段。
  * `git diff --check`：通过，仅有 CRLF warning，无 whitespace error。
  * `git status --short`：已核查；当前仍包含本轮 C 批候选变更以及明确排除的 D 批剩余变更。
* GitHub Actions 云端验证：待真实 PR 触发；当前仅完成本地脚本与 workflow 配置验证。
* 遗留问题：
  * 是否将 `Authorization evidence check` 纳入 required status checks，仍需后续单独任务和用户授权。
  * `CQCP Code Review` / `CQCP Spec & Docs Review` 机制化发布仍未实现。
* 备注：本轮不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、PRD、架构文档或 ADR；不配置 branch protection、repository ruleset 或 required status checks；不补足 `TASK-EVAL-001` DoD #12；不支撑 `TASK-EVAL-001` 归档；不进入 `TASK-028` / `TASK-031` / `TASK-032`。
