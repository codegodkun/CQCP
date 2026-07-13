# CURRENT_CONTEXT.md

更新日期：2026-07-13

## 当前阶段

CQCP 当前处于 MVP 主链路接通与 parser-backed evidence 收口阶段。长期架构依据仍以 `docs/ARCHITECTURE.md` 为准；当前任务状态、门禁和下一步以本文与对应 task 文件的最新记录为准。

当前任务推进采用“完成态复核优先”规则：普通任务执行过程中不反复请求外部复核，默认完成后一次性提交 `diff`、测试输出、`git status`、风险说明和 Memory Writeback 状态供复核；低风险文档同步、状态摘要、changelog 补录、路径修正和 post-merge 状态写回可合并式批处理，不单独建 TASK，不默认派独立 agent；父任务归档、`push` / `merge`、进入 `TASK-028` / `TASK-031` / `TASK-032`、涉及 `EvidenceSlot` / `CandidateResolver` / `SourceAnchor`、expected fixture / 评测指标、workflow / CI / required checks / branch protection、Codex 自写代码后自审、工作区或远程状态不清等高风险节点必须追加独立 agent 只读复核。详细规则见 `docs/DEVELOPMENT.md` 与 `docs/VERIFY.md`。

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

- `TASK-034` 已建档为 Codex 主控正式 MVP E2E 人工 anchor 验收任务，文件：`tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`。冻结样本为 `CQCP-MVP-DOCX-001/002/003`，人工基线为 63 条 occurrence（57 条纳入、6 条排除）；当前状态为 `待开始 / Phase 0 执行入口门禁未完成`。必须先只读证明现有入口可串联真实 DOCX、parser、审核状态机、结果快照与查询；入口不足时停止正式验收并单独冻结 test-only `TASK_SPEC-034-A`。
- `TASK-EVAL-001` 仍为 active，文件：`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`。2026-07-04 已执行 rebaseline：不再按原父任务 DoD 伪装为 12/12 全通过，不恢复旧条件归档口径；DoD #12 固定为未通过、未补足，A/B 历史 commit / push 授权记录无法完整核实并作为治理债务保留。
- 2026-07-09 用户确认 `TASK-EVAL-001` 独立只读审计已完成；Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`。DoD #12 固定未通过、不可补足；`TASK-GOV-005` 不补足 DoD #12。该审计结论针对 `TASK-EVAL-001` 当时的 parser-backed expected / anchor；此后 `TASK-DATA-001` 已形成 63 条独立人工 ground truth，并已完成 fixture / expected JSON 引用 / 定向测试转换，但尚未执行完整 MVP E2E 验证。`TASK-028` / `TASK-031` / `TASK-032` 继续 `NO-GO`。
- `TASK-GOV-005` 已拆出并定界为 active 治理债务任务，文件：`tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`。处理决定为 `BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`；2026-07-04 独立只读审计结论为 `GO`。该任务仅记录 `TASK-EVAL-001-A/B` 历史授权链不可完整核实问题，不追溯否定已 merge / push 内容，但阻止其作为后续绕过 commit / push 明确授权门禁的先例；任务仍长期保留 active，不表示已归档。
- `TASK-GOV-006` 已通过 PR #18 合并完成云端 PR 触发验证，文件：`tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`。PR #18 head commit 为 `432a63a25b0352e5ba9768f68f32c95a266474e4`，merge commit 为 `d3d5d1b507d233b5ff9a20350fad7b0c05a36cf9`；PR 触发的 CI 已通过，包含 `Authorization evidence check`、`Backend Gradle tests`、`Admin web lint, tests, and build`。`Authorization evidence check` 已在 PR #18 中成功运行，但它不是 required status check，也不证明用户授权、测试、独立审计或 Memory Writeback 已真实发生；本任务不配置 branch protection、repository ruleset 或 required status checks，不发布 `CQCP Code Review` / `CQCP Spec & Docs Review` Check Run 或 Commit Status。

## 最近已合并 PR

- PR #30：`TASK-DATA-001` 父任务归档，merge commit `01e59f54284bbab5409f0d7fd392acfd96d7ff83`，head commit `34e913316a5e4e63db7c193b2c6bfaf32df07f61`。三项 checks 均为 `SUCCESS`；该归档不代表完整 MVP E2E 已通过，不归档 `TASK-EVAL-001`，不补足 DoD #12，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- PR #29：`TASK-DATA-001` post-merge 状态写回，merge commit `2b30bf303642d10156eec5844ee09718adb595b3`，head commit `3aaf65de0fb6f3116dcbcce017ccd53c18026f29`。三项 checks 均为 `SUCCESS`；`TASK_SPEC-DATA-001-A` 已迁移到 `tasks/done/`，父任务当时进入归档前审计准备。
- PR #28：`TASK-DATA-001` 人工 anchor fixture / expected JSON 引用 / 定向测试转换，merge commit `23c66aaed34326f242f9fb395d784518421f1575`，head commit `782f0cbcb935b51b7d5d54537730f3abce4a0e22`。三项 PR checks 均为 `SUCCESS`；该合并不代表完整 MVP E2E 已验证，不归档 `TASK-EVAL-001`，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- PR #25：`TASK-033` post-merge 状态写回，merge commit `a60fc9f`，head commit `79c0b3b`。仅清理 `TASK-033` 合并后状态残留并同步项目记忆；不归档 `TASK-033`，不运行完整验收，不修改代码、测试、fixture、expected JSON、workflow、ADR 或 PRD，不归档 `TASK-EVAL-001`，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- PR #24：`TASK-033` MVP 端到端样本验收规格冻结，merge commit `880893639ada9fa5e2d42b3d2bccb1662e37a5c9`，head commits `dcfec071e7b677b23422be174cd95106b42c6517`、`af147edec13d89601307df3470cf3253c7d32cbc`。仅合并规格冻结文档和项目记忆；不运行完整验收，不修改代码、测试、fixture、expected JSON、workflow、ADR 或 PRD，不归档 `TASK-EVAL-001`，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- PR #20：`TASK-030` Review assets 版本化治理 A/B/C，merge commit `eadd017808fc8c2471fb0ac081f1e68e22dce5f5`，head commit `29d4a94eaf07b37831f07dd93f78d3027b770f01`。仅合并静态 Review assets 源文件、校验脚本、README 治理说明和任务记忆；不启用 runtime loader，不声明生产 runtime 绑定，不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- PR #21：`TASK-030` post-merge closeout status，merge commit `ef4643c7d11b1209c935229b4e39b76d659db00f`，head commit `40f28e7159f0cfc7d63546db07c5b8819edbc0c3`。仅修正独立审计 B1-B5 指出的 post-merge 状态残留并同步项目记忆；三项 PR workflow checks 均为 `SUCCESS`；不归档父任务，不启用 runtime loader，不声明生产 runtime 绑定，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- PR #15：`TASK-DEBT-001` pre-archive docs sync，merge commit `80e0dadb1bcf9d8da79214e97189e89ab042570c`，head commit `e9995f2ffff5e76c5a597f1e5ac03779e7bdb662`。仅同步归档前文档状态，不归档父任务，不进入 parser provenance / TABLE_CELL / `TASK-028` / `TASK-031` / `TASK-032`。
- PR #14：`TASK-DEBT-001` 父任务归档前审计写回，merge commit `13794fb2f09e02dad3b1d556cb6a5b9af7731c66`，head commit `d232df3fa320ae1ef85578942a366e125b961a0b`。不归档 `TASK-DEBT-001`，不进入 parser provenance / TABLE_CELL / `TASK-028` / `TASK-031` / `TASK-032`。
- PR #13：`TASK_SPEC-DEBT-001-C` 实现，merge commit `15888df4f0e89882814940c5eca0fc948fd1fef0`。
- PR #8：`TASK-GOV-004` 归档写回，merge commit `5d73ea22c42971df848dbacb49c86d40e2143e1f`。
- PR #5：`TASK-GOV-004 Phase 3` minimal GitHub Actions CI，merge commit `455d2e3b7a4d8397087deb127a649a6f92aa19a0`。
- PR #4：`TASK_SPEC-DEBT-001-B` 实现，merge commit `da724ba49c6a33347641950101a84a84dfa8c000`。

## 当前阻塞项

- `DEBT-001-03` parser provenance / `SourceAnchor` 仍是 unresolved and unauthorized debt，未获实现授权。
- `DEBT-001-05` 真实 DOCX `TABLE_CELL` 已形成 `ACCEPTED_HUMAN_GROUND_TRUTH` 的 `BLOCK` / `TABLE_CELL` 逐出处明细，并已转换为 fixture、expected JSON 引用和定向测试；`TASK-034` 已建档但 Phase 0 尚未执行，不得宣称真实 DOCX 自动化端到端覆盖已验证。
- `TASK-EVAL-001` DoD #12 未通过、未补足；该缺口不追溯否定已 push 内容或独立审计结论，但不得成为后续绕过授权门禁的先例。
- `TASK-EVAL-001` 当前不得归档；旧 `GO TO ARCHIVE WITH CONDITIONS` 口径已回滚，当前有效判断为 `REBASELINED / Active / 不归档 / KEEP ACTIVE / 不进入 TASK-028`。
- `TASK-EVAL-001` 独立只读审计已完成；Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`，后续不再围绕该父任务反复补写归档文档。
- 2026-07-04 rebaseline 结论：`TASK-EVAL-001` 不再按原父任务 DoD 归档；DoD #1 至 #11 仅保留为既有独立确认摘要，DoD #12 固定为未通过、未补足；后续如要重新申请归档，必须先经过独立 agent 只读审计和 Codex 单独 Review Intake。
- `TASK-028` Readiness Gate 结论为 `NO-GO`：`TASK-EVAL-001` 收口结果虽已稳定为 blocked，但不是可进入 `TASK-028` 的完成态；本轮只读 Review Intake 不授权实现、不冻结或派发 `TASK_SPEC`。
- `TASK-GOV-005` 为历史授权证据治理债务，不是业务修复任务；不得用该任务替代 `TASK-EVAL-001` 的父任务归档前独立只读审计，也不得用该任务补足 `TASK-EVAL-001` DoD #12。
- `TASK-GOV-004` 已完成第一阶段 CI required checks；`CQCP Code Review` / `CQCP Spec & Docs Review` 尚未机制化发布为 required checks。repository ruleset 不在当前任务范围内。
- `TASK-GOV-006` 的 `Authorization evidence check` 只检查 PR body 文本字段是否存在且非占位，不证明授权、测试、独立审计或 Memory Writeback 已真实发生；如后续要纳入 required status checks，必须另行定界并取得用户授权。
- `TASK-033` 不解除 `TASK-EVAL-001` / `TASK-028` 门禁，不补足 `TASK-EVAL-001` DoD #12；不得把 AI/parser 输出当作人工 anchor 标准答案。
- v3 强门禁不自动泛化到普通文档状态同步；低风险文档动作以后按 Codex 自查、精确 diff、合并式 Memory Writeback 处理。

## 禁止进入项

- 不进入 parser provenance / `SourceAnchor` 实现。
- 不进入真实 DOCX `TABLE_CELL` coverage 实现。
- 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 不创建新实现任务，不派发 CC-DS 执行环境实现任务。
- 不修改代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 不配置 branch protection、required checks 或 ruleset。
- 不用 `CURRENT_CONTEXT.md` 证明任务完成；完成依据必须来自真实代码、测试、原始 console 输出、commit、PR、独立审计报告或对应 task 文件。

## 待确认事项

- 已确认：`TASK-EVAL-001` 独立只读审计已完成；Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`。后续实际推进方向合并为 `TASK-DATA-001` / MVP E2E 人工 anchor 准备，不再围绕 `TASK-EVAL-001` 反复写文档。
- 已确认：`TASK-GOV-005` 独立 agent 只读审计结论为 `GO`，无阻塞问题；非阻塞措辞问题已回写修正。
- 已确认：建立提交前授权证据模板 / GitHub Actions PR body 文本检查 / PR 模板等机制化治理任务，当前由 `TASK-GOV-006` 承接；PR #18 已合并并完成云端 PR 触发验证，`Authorization evidence check` 已成功运行，但该 check 不是 required status check，也不证明真实授权事实。
- 已确认：`TASK-033` 已归档为 MVP 端到端样本验收规格冻结任务；提交前独立只读复核已完成，Decision 为 `GO`，无 blocking findings；归档前独立只读审计结论 `NEEDS_POST_MERGE_SYNC` 已通过 PR #25 post-merge 状态写回清理；Codex 归档 Review Intake Decision 为 `GO_TO_ARCHIVE_WITH_POST_MERGE_SYNC_SATISFIED`。2026-07-09 用户确认独立只读审计已对归档迁移与 Memory Writeback 给出 `GO`，并授权精确 stage、commit 与 push。
- 已确认：`TASK-DATA-001` 选定的 `CQCP-MVP-DOCX-001` / `002` / `003` 可用于项目验证，脱敏合规，data owner / 确认人为 `ZK`；三份合同 63 条逐出处明细已提升为 `ACCEPTED_HUMAN_GROUND_TRUTH`；转换实现已通过 PR #28 合并；独立归档前审计最终 `GO`，Codex 归档 Decision 为 `GO_TO_ARCHIVE`。
- 待确认：是否在后续独立任务中把 `Authorization evidence check` 配置为 required status check。
- 待确认：Gemma 4 26B A4B / 31B、Qwen3 30B-A3B / 32B 的具体权重、量化格式、license、A30 24GB 可运行性和 CQCP 样本评测方案。
- 待确认：`TASK-030` 后续是否继续推进 runtime loader 评审、数据库持久化方案或 model / budget profile 源定义；如进入 runtime loading、发布审批、数据库资产表、模型职责或 EvidenceSlot / CandidateResolver 行为变化，必须重新判断 ADR。`TASK-030` 父任务已归档，该归档不等于后续实现授权。

## 下一步

1. `TASK-EVAL-001` 已完成独立审计后状态收口，继续 active，不归档；后续不再围绕该父任务反复补写归档文档。
2. `TASK-028` Readiness Gate 当前为 `NO-GO`；继续禁止 `TASK-028` / `TASK-031` / `TASK-032` 抢跑。
3. 执行 `TASK-034` Phase 0 只读入口门禁；只有现有入口可串联真实 DOCX、parser、审核状态机、结果快照与查询时才进入正式验收。入口不足时停止并冻结 test-only `TASK_SPEC-034-A`。
4. `TASK-GOV-006` 已通过 PR #18 完成云端 PR 触发验证；该结果仍不得用于进入业务实现或配置 required status checks。
5. `TASK-033` 已归档；`TASK-DATA-001` 人工 anchor 标注及 fixture / expected JSON 引用 / 定向测试转换已经完成；正式 MVP 端到端验收由 `TASK-034` 承接。该任务不自动解除 required status checks 或 `TASK-028` / `TASK-031` / `TASK-032` 的各自门禁。
6. `TASK-030` 已归档；runtime loader 评审和 model / budget profile 源定义仍只是候选建议，不代表已授权创建或派发下一实现任务。

## 参考路径

- `tasks/MVP_TASK_MAP.md`
- `tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`
- `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
- `tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`
- `tasks/done/TASK-GOV-004-pr-based-multi-agent-governance.md`
- `tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`
- `tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`
- `tasks/done/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md`
- `tasks/done/TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md`
- `tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/done/TASK-030-review-assets-versioning-governance.md`
- `tasks/done/TASK_SPEC-030-A-review-assets-static-source-manifest.md`
- `tasks/done/TASK_SPEC-030-B-review-assets-schema-manifest-validation.md`
- `tasks/done/TASK_SPEC-030-C-review-assets-version-governance-docs.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
- `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
- `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
- `changelog/2026-07.md`
