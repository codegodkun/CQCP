# CURRENT_CONTEXT.md

更新日期：2026-07-05

## 当前阶段

CQCP 当前处于 MVP 主链路接通与 parser-backed evidence 收口阶段。长期架构依据仍以 `docs/ARCHITECTURE.md` 为准；当前任务状态、门禁和下一步以本文与对应 task 文件的最新记录为准。

当前任务推进采用“完成态复核优先”规则：普通任务执行过程中不反复请求外部复核，默认完成后一次性提交 `diff`、测试输出、`git status`、风险说明和 Memory Writeback 状态供复核；父任务归档、`push` / `merge`、进入 `TASK-028` / `TASK-031` / `TASK-032`、涉及 `EvidenceSlot` / `CandidateResolver` / `SourceAnchor`、expected fixture / 评测指标、workflow / CI / required checks / branch protection、Codex 自写代码后自审、工作区或远程状态不清等高风险节点必须追加独立 agent 只读复核。详细规则见 `docs/DEVELOPMENT.md` 与 `docs/VERIFY.md`。

已完成的主线能力摘要：

- 结果快照与结果合同、最小执行状态机、`GET /api/v1/tasks/{taskId}/result`、持久化结果查询适配。
- `TASK-023` 公开结果页最小实现与 `TASK-024` 管理台诊断详情最小实现。
- `TASK-025` parser-backed 主链路最小接入与 fixture 级验收收口，已归档。
- `TASK-026` 最小 `CandidateResolver` / confidence gating / evidence admission 闸门，已归档。
- `TASK-027` ADR-015 边界内的最小 `EvidenceSlot / SourceAnchor` 主实现，已归档；该范围不是完整 `EvidenceBundle` 平台化。
- `TASK-GOV-003` 五类问题整改与角色执行门禁，已归档。
- `TASK-GOV-004` PR 化多 Agent 开发治理与第一阶段 required checks，已归档。
- `TASK-DEBT-001` 已完成父任务归档前审计、文档同步和归档授权流程，归档文件：`tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`。

## 当前活跃任务

- `TASK-EVAL-001` 仍为 active，文件：`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`。2026-07-04 已执行 rebaseline：不再按原父任务 DoD 伪装为 12/12 全通过，不恢复旧条件归档口径；DoD #12 固定为未通过、未补足，A/B 历史 commit / push 授权记录无法完整核实并作为治理债务保留。
- 2026-07-04 用户要求先处理 `TASK-EVAL-001` 归档前审计 / 收口判断。Codex 预审结论为 `NO-GO TO ARCHIVE / INDEPENDENT AUDIT REQUIRED / BASELINE NOT CLEAN`：当前缺少独立只读审计结果，且主工作区仍有未提交治理改动，不能作为干净归档审计基线。
- `TASK-GOV-005` 已拆出并定界为 active 治理债务任务，文件：`tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`。处理决定为 `BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`；2026-07-04 独立只读审计结论为 `GO`。该任务仅记录 `TASK-EVAL-001-A/B` 历史授权链不可完整核实问题，不追溯否定已 merge / push 内容，但阻止其作为后续绕过 commit / push 明确授权门禁的先例；任务仍长期保留 active，不表示已归档。
- `TASK-GOV-006` 本地准备完成且独立只读复核 `GO`，文件：`tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`。本任务准备提交前授权证据模板、PR 模板和 `Authorization evidence check` PR body 文本门禁；本轮本地正向 fixture 已通过、反向 fixture 已按预期失败，云端 GitHub Actions 验证待真实 PR 触发；2026-07-05 已同步“完成态复核优先”规则到 `docs/DEVELOPMENT.md` 与 `docs/VERIFY.md`；不配置 branch protection、repository ruleset 或 required status checks，不发布 `CQCP Code Review` / `CQCP Spec & Docs Review` Check Run 或 Commit Status。

## 最近已合并 PR

- PR #15：`TASK-DEBT-001` pre-archive docs sync，merge commit `80e0dadb1bcf9d8da79214e97189e89ab042570c`，head commit `e9995f2ffff5e76c5a597f1e5ac03779e7bdb662`。仅同步归档前文档状态，不归档父任务，不进入 parser provenance / TABLE_CELL / `TASK-028` / `TASK-031` / `TASK-032`。
- PR #14：`TASK-DEBT-001` 父任务归档前审计写回，merge commit `13794fb2f09e02dad3b1d556cb6a5b9af7731c66`，head commit `d232df3fa320ae1ef85578942a366e125b961a0b`。不归档 `TASK-DEBT-001`，不进入 parser provenance / TABLE_CELL / `TASK-028` / `TASK-031` / `TASK-032`。
- PR #13：`TASK_SPEC-DEBT-001-C` 实现，merge commit `15888df4f0e89882814940c5eca0fc948fd1fef0`。
- PR #8：`TASK-GOV-004` 归档写回，merge commit `5d73ea22c42971df848dbacb49c86d40e2143e1f`。
- PR #5：`TASK-GOV-004 Phase 3` minimal GitHub Actions CI，merge commit `455d2e3b7a4d8397087deb127a649a6f92aa19a0`。
- PR #4：`TASK_SPEC-DEBT-001-B` 实现，merge commit `da724ba49c6a33347641950101a84a84dfa8c000`。

## 当前阻塞项

- `DEBT-001-03` parser provenance / `SourceAnchor` 仍是 unresolved and unauthorized debt，未获实现授权。
- `DEBT-001-05` 真实 DOCX `TABLE_CELL` 覆盖仍依赖独立人工 anchor 标注；不得宣称真实 DOCX TABLE_CELL 已验证。
- `TASK-EVAL-001` DoD #12 未通过、未补足；该缺口不追溯否定已 push 内容或独立审计结论，但不得成为后续绕过授权门禁的先例。
- `TASK-EVAL-001` 当前不得归档；旧 `GO TO ARCHIVE WITH CONDITIONS` 口径已回滚，当前有效判断为 `REBASELINED / Active / 不归档 / 不进入 TASK-028`。
- `TASK-EVAL-001` 归档前审计当前不能由 Codex 自审替代；必须等待独立 agent 只读审计返回后，才能由 Codex 单独作下一次 Review Intake Decision。
- 2026-07-04 rebaseline 结论：`TASK-EVAL-001` 不再按原父任务 DoD 归档；DoD #1 至 #11 仅保留为既有独立确认摘要，DoD #12 固定为未通过、未补足；后续如要重新申请归档，必须先经过独立 agent 只读审计和 Codex 单独 Review Intake。
- `TASK-028` Readiness Gate 结论为 `NO-GO`：`TASK-EVAL-001` 收口结果虽已稳定为 blocked，但不是可进入 `TASK-028` 的完成态；本轮只读 Review Intake 不授权实现、不冻结或派发 `TASK_SPEC`。
- `TASK-GOV-005` 为历史授权证据治理债务，不是业务修复任务；不得用该任务替代 `TASK-EVAL-001` 的父任务归档前独立只读审计，也不得用该任务补足 `TASK-EVAL-001` DoD #12。
- `TASK-GOV-004` 已完成第一阶段 CI required checks；`CQCP Code Review` / `CQCP Spec & Docs Review` 尚未机制化发布为 required checks。repository ruleset 不在当前任务范围内。
- `TASK-GOV-006` 的 `Authorization evidence check` 只检查 PR body 文本字段是否存在且非占位，不证明授权、测试、独立审计或 Memory Writeback 已真实发生；如后续要纳入 required status checks，必须另行定界并取得用户授权。

## 禁止进入项

- 不进入 parser provenance / `SourceAnchor` 实现。
- 不进入真实 DOCX `TABLE_CELL` coverage 实现。
- 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 不创建新实现任务，不派发 Claude Code / DeepSeek 实现任务。
- 不修改代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 不配置 branch protection、required checks 或 ruleset。
- 不用 `CURRENT_CONTEXT.md` 证明任务完成；完成依据必须来自真实代码、测试、原始 console 输出、commit、PR、独立审计报告或对应 task 文件。

## 待确认事项

- 待确认：`TASK-EVAL-001` rebaseline 后如需重新申请归档，需先取得独立 agent 只读审计与 Codex Review Intake；2026-07-04 Codex 预审已给出 `NO-GO TO ARCHIVE / INDEPENDENT AUDIT REQUIRED / BASELINE NOT CLEAN`，等待独立审计。
- 已确认：`TASK-GOV-005` 独立 agent 只读审计结论为 `GO`，无阻塞问题；非阻塞措辞问题已回写修正。
- 已确认：建立提交前授权证据模板 / GitHub Actions PR body 文本检查 / PR 模板等机制化治理任务，当前由 `TASK-GOV-006` 承接；模板、PR body 文本检查和项目记忆写回已完成本地准备，云端 PR 触发验证仍待后续 PR。
- 待确认：是否在后续独立任务中把 `Authorization evidence check` 配置为 required status check。
- 待确认：Gemma 4 26B A4B / 31B、Qwen3 30B-A3B / 32B 的具体权重、量化格式、license、A30 24GB 可运行性和 CQCP 样本评测方案。

## 下一步

1. `TASK-EVAL-001` 已完成 rebaseline，继续 active，不归档；2026-07-04 Codex 预审结论为 `NO-GO TO ARCHIVE / INDEPENDENT AUDIT REQUIRED / BASELINE NOT CLEAN`，下一步若继续推进只能先做独立 agent 只读审计。
2. `TASK-028` Readiness Gate 当前为 `NO-GO`；继续禁止 `TASK-028` / `TASK-031` / `TASK-032` 抢跑。
3. 后续如需处理 `DEBT-001-03` parser provenance 或 `DEBT-001-05` TABLE_CELL，应分别另行定界任务；TABLE_CELL 必须先取得独立人工 anchor 标注。
4. `TASK-GOV-006` 承接提交前授权证据模板、PR 模板和 PR body 文本门禁；本任务完成后仍不得因此进入业务实现或配置 required status checks。

## 参考路径

- `tasks/MVP_TASK_MAP.md`
- `tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`
- `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
- `tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`
- `tasks/done/TASK-GOV-004-pr-based-multi-agent-governance.md`
- `tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`
- `tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
- `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
- `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
- `changelog/2026-07.md`
