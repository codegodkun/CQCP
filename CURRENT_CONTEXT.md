# CURRENT_CONTEXT.md

更新日期：2026-07-03

## 当前阶段

CQCP 当前处于 MVP 主链路接通与 parser-backed evidence 收口阶段。长期架构依据仍以 `docs/ARCHITECTURE.md` 为准；当前任务状态、门禁和下一步以本文与对应 task 文件的最新记录为准。

已完成的主线能力摘要：

- 结果快照与结果合同、最小执行状态机、`GET /api/v1/tasks/{taskId}/result`、持久化结果查询适配。
- `TASK-023` 公开结果页最小实现与 `TASK-024` 管理台诊断详情最小实现。
- `TASK-025` parser-backed 主链路最小接入与 fixture 级验收收口，已归档。
- `TASK-026` 最小 `CandidateResolver` / confidence gating / evidence admission 闸门，已归档。
- `TASK-027` ADR-015 边界内的最小 `EvidenceSlot / SourceAnchor` 主实现，已归档；该范围不是完整 `EvidenceBundle` 平台化。
- `TASK-GOV-003` 五类问题整改与角色执行门禁，已归档。
- `TASK-GOV-004` PR 化多 Agent 开发治理与第一阶段 required checks，已归档。

## 当前活跃任务

- `TASK-DEBT-001` 仍为 active，文件：`tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`。
  - 已登记 4 条代码缺陷和 1 条覆盖盲区。
  - A 批次 `TASK_SPEC-DEBT-001-A` 已完成并进入远端 `master`：实现提交 `3223d6760a977fe9deaf722e63b50bcbb6ce3611`，文档修正提交 `3b35e2728143ce8f6c89bcc74cb1cb7fb469d973`，2026-06-27 post-push 独立只读复审为 `GO`。
  - B 批次 `TASK_SPEC-DEBT-001-B` 已通过 PR #4 合并，merge commit `da724ba49c6a33347641950101a84a84dfa8c000`，PR head commit `6513e669ac251f881c4d06ea2574ce6e9c7c9d69`；CI 状态只能记录为当时临时豁免，不得追溯写成 CI PASS。
  - C 批次 `TASK_SPEC-DEBT-001-C` 已通过 PR #13 合并，merge commit `15888df4f0e89882814940c5eca0fc948fd1fef0`。
  - 2026-07-02 父任务归档前独立只读审计为 `GO`；Codex Review Intake Decision 为 `GO TO ARCHIVE WITH CONDITIONS`。
  - PR #14 已合并到 `master`，merge commit `13794fb2f09e02dad3b1d556cb6a5b9af7731c66`，head commit `d232df3fa320ae1ef85578942a366e125b961a0b`；该 PR 仅记录 `TASK-DEBT-001` 父任务归档前审计写回，不归档父任务。
- `TASK-EVAL-001` 仍为 active / 暂停归档，文件：`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`。DoD #12 未通过、未补足；A/B 历史 commit / push 授权记录无法完整核实并作为治理债务保留。

## 最近已合并 PR

- PR #14：`TASK-DEBT-001` 父任务归档前审计写回，merge commit `13794fb2f09e02dad3b1d556cb6a5b9af7731c66`，head commit `d232df3fa320ae1ef85578942a366e125b961a0b`。不归档 `TASK-DEBT-001`，不进入 parser provenance / TABLE_CELL / `TASK-028` / `TASK-031` / `TASK-032`。
- PR #13：`TASK_SPEC-DEBT-001-C` 实现，merge commit `15888df4f0e89882814940c5eca0fc948fd1fef0`。
- PR #8：`TASK-GOV-004` 归档写回，merge commit `5d73ea22c42971df848dbacb49c86d40e2143e1f`。
- PR #5：`TASK-GOV-004 Phase 3` minimal GitHub Actions CI，merge commit `455d2e3b7a4d8397087deb127a649a6f92aa19a0`。
- PR #4：`TASK_SPEC-DEBT-001-B` 实现，merge commit `da724ba49c6a33347641950101a84a84dfa8c000`。

## 当前阻塞项

- `TASK-DEBT-001` 父任务仍未归档；`GO TO ARCHIVE WITH CONDITIONS` 不得简写为无条件归档结论。
- `DEBT-001-03` parser provenance / `SourceAnchor` 仍是 unresolved and unauthorized debt，未获实现授权。
- `DEBT-001-05` 真实 DOCX `TABLE_CELL` 覆盖仍依赖独立人工 anchor 标注；不得宣称真实 DOCX TABLE_CELL 已验证。
- `TASK-EVAL-001` DoD #12 未通过、未补足；该缺口不追溯否定已 push 内容或独立审计结论，但不得成为后续绕过授权门禁的先例。
- `TASK-GOV-004` 已完成第一阶段 CI required checks；`CQCP Code Review` / `CQCP Spec & Docs Review` 尚未机制化发布为 required checks。repository ruleset 不在当前任务范围内。

## 禁止进入项

- 不进入 parser provenance / `SourceAnchor` 实现。
- 不进入真实 DOCX `TABLE_CELL` coverage 实现。
- 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 不创建新实现任务，不派发 Claude Code / DeepSeek 实现任务。
- 不修改代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 不配置 branch protection、required checks 或 ruleset。
- 不用 `CURRENT_CONTEXT.md` 证明任务完成；完成依据必须来自真实代码、测试、原始 console 输出、commit、PR、独立审计报告或对应 task 文件。

## 待确认事项

- 待确认：`TASK-EVAL-001` 完成边界冻结后，`TASK-028` 与 `TASK-029` / `TASK-030` 的后续排序。
- 待确认：Gemma 4 26B A4B / 31B、Qwen3 30B-A3B / 32B 的具体权重、量化格式、license、A30 24GB 可运行性和 CQCP 样本评测方案。

## 下一步

1. 仅围绕 `TASK-DEBT-001` 执行父任务归档前文档状态同步、归档流程准备和显式授权后的 Git 流程。
2. 保持 `TASK-DEBT-001` active / 未归档，直到用户明确授权归档并完成对应归档流程。
3. 后续如需处理 `DEBT-001-03` parser provenance 或 `DEBT-001-05` TABLE_CELL，应另行定界任务；TABLE_CELL 必须先取得独立人工 anchor 标注。
4. 继续禁止 `TASK-028` / `TASK-031` / `TASK-032` 抢跑。

## 参考路径

- `tasks/MVP_TASK_MAP.md`
- `tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`
- `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
- `tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`
- `tasks/done/TASK-GOV-004-pr-based-multi-agent-governance.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
- `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
- `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
- `changelog/2026-07.md`
