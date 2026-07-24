# CURRENT_CONTEXT.md

更新日期：2026-07-24

## 当前阶段

CQCP 当前处于 MVP 主链路接通与 parser-backed evidence 收口阶段。长期架构依据仍以 `docs/ARCHITECTURE.md` 为准；当前任务状态、门禁和下一步以本文与对应 task 文件的最新记录为准。

当前任务推进采用 Task Level + Feature/Milestone 收口规则：`L0 探索`默认不进入主线，`L1 小文档`批量处理，`L2 Feature`默认一个父 TASK、一个 Feature PR，`L3 高风险治理`按可回滚风险边界独立审计。TASK_SPEC 保持细粒度执行和 Review Intake，但不自动等于 commit/push/PR/merge；普通 push 不因传输动作重复完整审计；人工 ground truth、expected/fixture、核心审核链路、生产激活、数据库/API/CI/安全等强门禁继续有效。详细规则见 `AGENTS.md`、`docs/DEVELOPMENT.md`、`docs/VERIFY.md` 与 `docs/context-management.md`。

已完成的主线能力摘要：

- 结果快照与结果合同、最小执行状态机、`GET /api/v1/tasks/{taskId}/result`、持久化结果查询适配。
- `TASK-023` 公开结果页最小实现与 `TASK-024` 管理台诊断详情最小实现。
- `TASK-025` parser-backed 主链路最小接入与 fixture 级验收收口，已归档。
- `TASK-026` 最小 `CandidateResolver` / confidence gating / evidence admission 闸门，已归档。
- `TASK-027` ADR-015 边界内的最小 `EvidenceSlot / SourceAnchor` 主实现，已归档；该范围不是完整 `EvidenceBundle` 平台化。
- `TASK-GOV-003` 五类问题整改与角色执行门禁，已归档。
- `TASK-GOV-004` PR 化多 Agent 开发治理与第一阶段 required checks，已归档。
- `TASK-DEBT-001` 已完成父任务归档前审计、文档同步和归档授权流程，归档文件：`tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`。
- `TASK-030` Review assets 版本化治理 A/B/C 已完成、独立审计条件已满足、Codex 归档 Review Intake Decision 为 `GO_TO_ARCHIVE_WITH_CONDITIONS_SATISFIED`，已归档；归档文件：`tasks/done/TASK-030-review-assets-versioning-governance.md`。归档不启用 runtime loader，不声明生产 runtime 绑定，不授权 `TASK-028` / `TASK-031` / `TASK-032` 或 `TASK-033`。
- `TASK-033` MVP 端到端样本验收规格冻结已完成并归档，归档文件：`tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`。PR #24 已合并规格冻结建档，merge commit 为 `880893639ada9fa5e2d42b3d2bccb1662e37a5c9`；PR #25 已合并 post-merge 状态写回，merge commit 为 `a60fc9f`。Codex 归档 Review Intake Decision 为 `GO_TO_ARCHIVE_WITH_POST_MERGE_SYNC_SATISFIED`。2026-07-09 用户确认独立只读审计已对 `TASK-033` 归档迁移与 Memory Writeback 给出 `GO`，并授权提交前收口、精确 stage、commit 与 push。归档不运行完整验收，不修改代码、测试、fixture、expected JSON、workflow、ADR 或 PRD，不归档 `TASK-EVAL-001`，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- `TASK-DATA-001` 已完成并归档到 `tasks/done/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md`。PR #28 merge commit 为 `23c66aaed34326f242f9fb395d784518421f1575`，PR #29 post-merge merge commit 为 `2b30bf303642d10156eec5844ee09718adb595b3`，PR #30 归档 merge commit 为 `01e59f54284bbab5409f0d7fd392acfd96d7ff83`；独立归档前审计最终结论为 `GO`，Codex Decision 为 `GO_TO_ARCHIVE / INDEPENDENT PRE_ARCHIVE AUDIT SATISFIED / FULL MVP E2E NOT VERIFIED`。归档不代表完整 MVP E2E 已通过，不归档 `TASK-EVAL-001`，不补足 DoD #12，不进入 `TASK-028` / `TASK-031` / `TASK-032`。

## 当前活跃任务

- `TASK-034` 已完成 Phase 0 与 Phase 1，父任务保持 active，正式 MVP E2E 最终判定为 `FAIL`。3 份真实 DOCX 均完成同 run parser → review → snapshot → 同 task 查询；27 个 `PointStatus` 全为 `PASS`，但 candidate comparison 仅 9 `MATCH`、18 `MISMATCH`；63 条 occurrence 保持 57 纳入 / 6 排除，其中 57 条纳入全部 `NOT_OBSERVABLE`，6 条排除全部 `EXCLUDED`。三份结果均无 Finding、无 `SYS-*`。证据目录：`outputs/task-034-mvp-e2e-acceptance/`。
- `TASK_SPEC-034-A` test-only E2E harness 已实现并经 Codex Review Intake 与独立只读复核接纳，文件：`tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md`；实现提交 `99bea3a6a3ce0cbecf337e76692aac3a6c428228`，manifest 序列化修复提交 `46a625a5eb5aee8ff5a31f86bb7300fb2d8e703a`。harness 13/13、既有四类定向回归 27/27；未修改生产链路或人工 ground truth。
- `TASK-035` 的 `mvp-e2e-candidate-comparison-v2` 与 `TASK_SPEC-035-A` 已接纳并随 PR #32 合并；实现提交为 `52d73b3`，定向证据为 harness `15/15`、四类回归 `27/27`。正式 MVP E2E 未重跑，父任务仍 active。
- `TASK-036` 的 A 未激活 carrier foundation 已随 PR #32 合并；B1/B2/C1 已在远端分支 `codex/task-036-consistency-set-runtime@3adcab4` 形成可重建提交但未进入主线，C2 未实现。主线普通任务仍走 legacy single anchor。
- `TASK-EVAL-001` 仍为 active，文件：`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`。2026-07-04 已执行 rebaseline：不再按原父任务 DoD 伪装为 12/12 全通过，不恢复旧条件归档口径；DoD #12 固定为未通过、未补足，A/B 历史 commit / push 授权记录无法完整核实并作为治理债务保留。
- 2026-07-09 用户确认 `TASK-EVAL-001` 独立只读审计已完成；Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`。DoD #12 固定未通过、不可补足；`TASK-GOV-005` 不补足 DoD #12。该审计结论针对 `TASK-EVAL-001` 当时的 parser-backed expected / anchor；此后 `TASK-DATA-001` 已形成 63 条独立人工 ground truth并完成转换，`TASK-034` 已执行正式 MVP E2E 但判定 `FAIL`。`TASK-028` / `TASK-031` / `TASK-032` 继续 `NO-GO`。
- `TASK-GOV-005` 已拆出并定界为 active 治理债务任务，文件：`tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`。处理决定为 `BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`；2026-07-04 独立只读审计结论为 `GO`。该任务仅记录 `TASK-EVAL-001-A/B` 历史授权链不可完整核实问题，不追溯否定已 merge / push 内容，但阻止其作为后续绕过 commit / push 明确授权门禁的先例；任务仍长期保留 active，不表示已归档。
- `TASK-GOV-006` 已通过 PR #18 合并完成云端 PR 触发验证，文件：`tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`。PR #18 head commit 为 `432a63a25b0352e5ba9768f68f32c95a266474e4`，merge commit 为 `d3d5d1b507d233b5ff9a20350fad7b0c05a36cf9`；PR 触发的 CI 已通过，包含 `Authorization evidence check`、`Backend Gradle tests`、`Admin web lint, tests, and build`。`Authorization evidence check` 已在 PR #18 中成功运行，但它不是 required status check，也不证明用户授权、测试、独立审计或 Memory Writeback 已真实发生；本任务不配置 branch protection、repository ruleset 或 required status checks，不发布 `CQCP Code Review` / `CQCP Spec & Docs Review` Check Run 或 Commit Status。
- `TASK-GOV-007` 已通过 PR #33 合并，merge commit 为 `1f62320f20ec29c52f49c0ed33c4244bb1be669e`；独立只读审计 12 项全部通过、状态增量复核 `GO`，三项 CI 全部通过。该任务不修改业务代码、workflow、检查脚本或 TASK-036 门禁。
- `TASK-037 / ADR-017` 已从 `origin/master@1f62320` 创建独立 L3 分支 `codex/task-037-execution-binding-release`；ADR-017 已接受，规格、架构同步与第二轮实现审计均为 `GO`。Claude Code 编码前计划经修订后获准实现；V2 seed、binding resolver 与定向测试已完成，Codex Review Intake 为 `ACCEPT_IMPLEMENTATION / GO_TO_COMMIT_PR`。全新 PostgreSQL 16.14 空库定向 45 tests、全量 backend 157 tests、review-assets validator 与 `git diff --check` 全部通过；当前待 PR/CI/merge。

## Git 集成基线

GitHub 是 PR、checks 与 merge 状态的事实源。当前主线最近的确认集成基线为 PR #33 / merge commit `1f62320f20ec29c52f49c0ed33c4244bb1be669e`；它只合并 TASK-GOV-007 治理规则。PR #32 / merge commit `97ef08f1cae88e8a702069eb0e07c2035b3b063f` 合并 TASK-035 candidate comparison v2 与 TASK-036-A 未激活 carrier，不激活 RuleSetVersion runtime loader，不证明 57/57 occurrence coverage，也不解除 TASK-028 / TASK-031 / TASK-032。

## 当前阻塞项

- `DEBT-001-03` parser provenance / `SourceAnchor` 仍是 unresolved and unauthorized debt，未获实现授权。
- `TASK_SPEC-035-A` 与 TASK-036-A 已随 PR #32 合并；TASK-036 B1/B2/C1 仅存在于远端工作分支 `codex/task-036-consistency-set-runtime@3adcab4`，未进入主线，C2 未实现。正式重跑继续被阻塞；不得提前改写 v1 的 9 `MATCH` / 18 `MISMATCH` 与 57/57 `NOT_OBSERVABLE` 证据。
- `TASK-EVAL-001` DoD #12 未通过、未补足；该缺口不追溯否定已 push 内容或独立审计结论，但不得成为后续绕过授权门禁的先例。
- `TASK-EVAL-001` 当前不得归档；旧 `GO TO ARCHIVE WITH CONDITIONS` 口径已回滚，当前有效判断为 `REBASELINED / Active / 不归档 / KEEP ACTIVE / 不进入 TASK-028`。
- `TASK-EVAL-001` 独立只读审计已完成；Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`，后续不再围绕该父任务反复补写归档文档。
- 2026-07-04 rebaseline 结论：`TASK-EVAL-001` 不再按原父任务 DoD 归档；DoD #1 至 #11 仅保留为既有独立确认摘要，DoD #12 固定为未通过、未补足；后续如要重新申请归档，必须先经过独立 agent 只读审计和 Codex 单独 Review Intake。
- `TASK-028` Readiness Gate 结论为 `NO-GO`：`TASK-EVAL-001` 收口结果虽已稳定为 blocked，但不是可进入 `TASK-028` 的完成态；本轮只读 Review Intake 不授权实现、不冻结或派发 `TASK_SPEC`。
- `TASK-GOV-005` 为历史授权证据治理债务，不是业务修复任务；不得用该任务替代 `TASK-EVAL-001` 的父任务归档前独立只读审计，也不得用该任务补足 `TASK-EVAL-001` DoD #12。
- `TASK-GOV-004` 已完成第一阶段 CI required checks；`CQCP Code Review` / `CQCP Spec & Docs Review` 尚未机制化发布为 required checks。repository ruleset 不在当前任务范围内。
- `TASK-GOV-006` 的 `Authorization evidence check` 只检查 PR body 文本字段是否存在且非占位，不证明授权、测试、独立审计或 Memory Writeback 已真实发生；如后续要纳入 required status checks，必须另行定界并取得用户授权。
- `TASK-033` 不解除 `TASK-EVAL-001` / `TASK-028` 门禁，不补足 `TASK-EVAL-001` DoD #12；不得把 AI/parser 输出当作人工 anchor 标准答案。
- v3 强门禁不自动泛化到 L1 小文档；L0/L1/L2/L3 的审计与写回口径以本次治理规则为准，现有任务显式强门禁不追溯解除。

## 禁止进入项

- 不进入 `TASK_SPEC-036-A` 允许文件和未激活 carrier 边界之外的 parser provenance / `SourceAnchor` 实现。
- 不进入真实 DOCX `TABLE_CELL` coverage 激活或 TASK-036 B/C 批次实现；已授权的 `TASK-037 / ADR-017` 仅负责独立冻结并实现 execution binding 与 version/model source，不等于激活 TASK-036-C2。
- 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 不重新运行 TASK-034 正式 MVP E2E，直到 `TASK-035` 接纳、`TASK-036 / ADR-016` 全部自身门禁完成且 Codex 获得单独正式重跑授权；当前仅第一项已满足。
- `TASK_SPEC-036-A` 已随 PR #32 合并；不得以当前远端 TASK-036 B1/B2/C1 分支状态替代主线事实。A 未修改 EvidenceSlot、结果快照合同、现有 preparer、OpenAPI、数据库、parser、fixture、expected JSON、Docker、workflow、已接受 ADR 或 PRD。
- 不配置 branch protection、required checks 或 ruleset。
- 不用 `CURRENT_CONTEXT.md` 证明任务完成；完成依据必须来自真实代码、测试、原始 console 输出、commit、PR、独立审计报告或对应 task 文件。

## 待确认事项

- 已确认：`TASK-EVAL-001` 独立只读审计已完成；Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`。后续实际推进方向合并为 `TASK-DATA-001` / MVP E2E 人工 anchor 准备，不再围绕 `TASK-EVAL-001` 反复写文档。
- 已确认：`TASK-GOV-005` 独立 agent 只读审计结论为 `GO`，无阻塞问题；非阻塞措辞问题已回写修正。
- 已确认：建立提交前授权证据模板 / GitHub Actions PR body 文本检查 / PR 模板等机制化治理任务，当前由 `TASK-GOV-006` 承接；PR #18 已合并并完成云端 PR 触发验证，`Authorization evidence check` 已成功运行，但该 check 不是 required status check，也不证明真实授权事实。
- 已确认：`TASK-033` 已归档为 MVP 端到端样本验收规格冻结任务；提交前独立只读复核已完成，Decision 为 `GO`，无 blocking findings；归档前独立只读审计结论 `NEEDS_POST_MERGE_SYNC` 已通过 PR #25 post-merge 状态写回清理；Codex 归档 Review Intake Decision 为 `GO_TO_ARCHIVE_WITH_POST_MERGE_SYNC_SATISFIED`。2026-07-09 用户确认独立只读审计已对归档迁移与 Memory Writeback 给出 `GO`，并授权精确 stage、commit 与 push。
- 已确认：`TASK-DATA-001` 选定的 `CQCP-MVP-DOCX-001` / `002` / `003` 可用于项目验证，脱敏合规，data owner / 确认人为 `ZK`；三份合同 63 条逐出处明细已提升为 `ACCEPTED_HUMAN_GROUND_TRUTH`；转换实现已通过 PR #28 合并；独立归档前审计最终 `GO`，Codex 归档 Decision 为 `GO_TO_ARCHIVE`。
- 已确认：`TASK-034` 的 18 个 candidate mismatch 是验收 raw 表示形态错位；PRD 生产规范明确百分比以 `70` 表示 `70%`，税额公式 actual candidate 为 `taxAmount` 标量。`TASK-035` 只冻结 test-only typed projection，不改变生产语义。
- 已确认：现有 `pointResults[].sourceAnchors[]`、Java snapshot、持久化 JSON 和 query DTO 使用列表结构，可在不新增 endpoint 或数据库 migration 的前提下承载一点评多 anchors；若实现发现事实不同必须停止并拆兼容任务。
- 已确认：`TASK_SPEC-036-A` 已随 PR #32 合并；B1/B2/C1 的远端分支状态不等于主线集成，C2 和正式 E2E 仍需各自门禁。
- 已确认：`TASK_SPEC-035-A` 编码前计划、实现、Codex Review Intake、独立只读实现审计和精确提交均已完成；提交为 `52d73b3`。
- 已确认：`TASK-GOV-007` 已通过 PR #33 合并，merge commit `1f62320f20ec29c52f49c0ed33c4244bb1be669e`；独立审计、状态增量复核与 CI 均通过。
- 待确认：是否在后续独立任务中把 `Authorization evidence check` 配置为 required status check。
- 待确认：Gemma 4 26B A4B / 31B、Qwen3 30B-A3B / 32B 的具体权重、量化格式、license、A30 24GB 可运行性和 CQCP 样本评测方案。
- 已确认：用户授权在 `TASK-GOV-007` 合并后独立创建并实施 `TASK-037 / ADR-017`，收口 execution binding、model / budget profile 与 parser/schema 版本源；该授权不改变 TASK-030 已归档边界，也不激活 TASK-036-C2。

## 下一步

1. 对 TASK-037 接纳状态写回执行轻量 delta 复核，然后精确提交、创建独立 L3 PR并等待 CI。
2. TASK-037 merge 后从新 `origin/master` 重跑 `TASK-MVP-001` Phase 0；通过前不得创建 `TASK_SPEC-MVP-001-A`。
3. Phase 0 为 GO 后，按用户授权创建 FEATURE-MVP-001 分支并冻结 `TASK_SPEC-MVP-001-A`。
4. `TASK-028` / `TASK-031` / `TASK-032` 继续禁止抢跑；TASK-034 v1 正式失败证据保持不变。

## 参考路径

- `tasks/MVP_TASK_MAP.md`
- `tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`
- `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
- `tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`
- `tasks/done/TASK-GOV-004-pr-based-multi-agent-governance.md`
- `tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`
- `tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`
- `tasks/active/TASK-GOV-007-task-level-and-git-closure-governance.md`
- `tasks/active/TASK-037-execution-binding-release-and-profile-seed.md`
- `tasks/active/TASK_SPEC-037-A-execution-binding-release-runtime-source.md`
- `decisions/ADR-017-execution-binding-release-and-demo-profile-readiness.md`
- `tasks/done/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md`
- `tasks/done/TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md`
- `tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md`
- `tasks/active/TASK-035-mvp-e2e-candidate-comparison-contract-rebaseline.md`
- `tasks/active/TASK_SPEC-035-A-test-only-candidate-comparison-v2.md`
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`
- `tasks/active/TASK_SPEC-036-A-same-value-occurrence-provenance.md`
- `decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md`
- `outputs/task-034-mvp-e2e-acceptance/entrypoint-audit.md`
- `tasks/done/TASK-030-review-assets-versioning-governance.md`
- `tasks/done/TASK_SPEC-030-A-review-assets-static-source-manifest.md`
- `tasks/done/TASK_SPEC-030-B-review-assets-schema-manifest-validation.md`
- `tasks/done/TASK_SPEC-030-C-review-assets-version-governance-docs.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
- `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
- `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
- `changelog/2026-07.md`
