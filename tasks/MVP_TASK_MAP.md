# MVP 任务地图

更新日期：2026-08-04

## MILESTONE-MVP-002：审核工作台、模型安全与盲态评测

当前基线：`origin/master@115be530480e2ff9b92a7076b2668c066a44ae5c`

`MILESTONE-MVP-002-CORE` 已完成；
`MILESTONE-MVP-002-TRACK-B-HOLDOUT` 正式执行因 schema invalid fail closed，
项目负责人已授权并完成新的 `MILESTONE-MVP-002-TRACK-B-SUCCESSOR` 人工封印；
唯一正式 DeepSeek claim 因第 1 个 call 认证失败 fail closed；TASK-EVAL-004 随后也
在第 8 个 call schema invalid 并终态 BLOCKED。`TASK-EVAL-005` schema 稳定性诊断
已 GO，但第五套 admission 为 `SEALED_NO_GO_MODEL_MISMATCH`。项目负责人已澄清此前
BLOCKED 表述只是询问并批准 ADR-026 / TASK-EVAL-006；第六套模型评测已全维 100%，
但 R5 subject `e77a8635…` 的代码/架构审计为 NO_GO。R6 四项修复和完整 verification
均通过；其 subject `a9daf62f…` 的两个 Codex auditor 全零 GO，但 CC 因隔离 projection
的 CRLF/Git-blob 与 evidence/context 分区问题返回 `P0=1 / P2=1`。R7 仅修该 transport
并已通过完整 verification，
Milestone 当前为
`MODEL_EVALUATION_PASS / ADMISSION_PENDING_AUDIT / R7_VERIFICATION_PASS / FREEZE_PENDING`：

| 任务 | Task Level | 当前状态 | 当前边界 |
|---|---|---|---|
| `TASK-MVP-002` | L3 | Core subject `90c4ae9a…` 三审全零 GO，PR #37 CI 全绿并合并为 `115be530…` | execution 清单、精确结果、受控 preview/下载、左右工作台已进入主线 |
| `TASK-MODEL-001` | L3 | Core subject `90c4ae9a…` 三审全零 GO并进入主线 | immutable Model Profile、Secret Reference、allowlist、readiness/connectivity 已进入主线；PUBLIC 仍 disabled/unbound |
| `TASK-EVAL-002` | L3 | Track A 完成；旧 run-v3 为 `NO_GO_MODEL_MISMATCH`；新 12-packet holdout 已完成人工封印，但正式 DeepSeek 在第 2 个 call schema invalid 并终态 BLOCKED | one-time claim 已消费且不得重试；无 DeepSeek accepted opinion，未解盲，Provider admission `NOT_ESTABLISHED`，Milestone 已标记 `BLOCKED` |
| `TASK-EVAL-003` | L3 | 12 条人工 decisions/seal 已冻结；Codex 9/9 accepted；DeepSeek 第 1 个 call `AUTHENTICATION_FAILED` 并终态 BLOCKED | claim 已消费、未解盲且不得重试；Provider admission `NOT_ESTABLISHED`，A0/A1/A2 保持阻塞 |
| `TASK-EVAL-004` | L3 | human seal 已冻结；唯一 DeepSeek claim 在第 8 个 call schema invalid 终态 BLOCKED；未解盲、不得重试 | call set 9×1/controls=3；claim `62a7451e…`、blocked receipt `8b113c74…`；Provider admission NOT_ESTABLISHED，A0/A1/A2 阻塞 |
| `TASK-EVAL-005` | L3 | 唯一 9×1 DeepSeek 执行 schema 9/9 accepted；解盲后 DeepSeek 4 个 CONFLICTED packet mismatch，历史终态 `SEALED_NO_GO_MODEL_MISMATCH` | 旧 claim 不重试；未来恢复边界由 ADR-026 部分替代 |
| `TASK-EVAL-006` | L3 | R1-R4 模型评测 GO；R5 subject `e77a8635…` 失效；R6 subject `a9daf62f…` 两份 Codex GO、CC `NO_GO / P0=1 / P2=1`，整体失效；R7 verification PASS，freeze pending | human seal `0f23b9eb…` 早于模型访问；Codex/DeepSeek 六维 9/9、controls 3/3；R7 Node 56/56 + verifier 1/1 + Java 1/1（5 executed），verification `628fcd68…` / console `e8ec9322…` / evidence 37；seal `70339354…` 仍只代表 evaluation pass pending audit；one-off Git-blob projection diagnostic `51+3=54` / context 5 / sums `ceab8aa2…`；`providerAdmissionEstablished=false`，A0/A1/A2 继续阻塞 |
| `TASK-034` | L3 | Formal R7 与 Core subject 已通过并进入主线；父任务保持 active 等待 Milestone 最终跨 TASK 审计 | 27/27 point PASS、57 MATCHED + 6 human EXCLUDED 的限定样本门禁不变 |
| `TASK-036` | L3 | B1/B2/C1/C2/D1/D2 随 PR #37 进入主线；D2 required-block identity 已通过最终 Core 三审 | deterministic eligibility、FamilyModelCallPlan、runtime EvidencePacket seam 可供 holdout 使用 |

Core source diff 排除了 Provider A0、standing/CC 审计传输、A1/A2/A3 与
`outputs/**`。最终 Core subject 为 `90c4ae9a…`；更早的 freeze/verification/audit
均保持失效，不得组合复用。

当前 Track B 结论：

- TASK-EVAL-004 已满足人工先封印、四语料 disjoint、9×1 dispatch 和三个 controls
  zero-call；Codex blind opinion 已封存但未解盲。
- DeepSeek 唯一正式 claim 在第 8 个 call schema invalid 并终态 BLOCKED，因缺少完整
  accepted opinion 无法执行双意见先验验证、解盲、freeze 或正式三审。
- `providerAdmission=NOT_ESTABLISHED`；同一 corpus/claim 不得重试。ADR-025 只允许
  独立 schema 诊断和一次第五套 admission，不恢复任何历史 claim；A0/A1/A2 继续阻塞。
- TASK-EVAL-005 的 schema 诊断虽为 24/24，正式 admission 解盲仍为 NO-GO：Codex 全维
  100%，DeepSeek 在 4 个 CONFLICTED packet 上语义不一致，四个语义维度均为 55.56%。
  seal `520d7b38…` 已终态验证；同一 claim 不得重试或改写。
- ADR-026 / TASK-EVAL-006 已获明确批准：去除 model-facing input 中的 runtime routing/
  diagnostic label，先执行 4-call 受控诊断；4/4 后才允许新的独立 12-packet evaluation。
  第六套已完成 Codex/DeepSeek 六维 9/9 与 controls 3/3，但这只证明
  `modelEvaluationPassed=true`；完整 verification、新 freeze、三方全零 GO 与 CI 内容
  一致性完成前 `providerAdmissionEstablished=false`，A0/A1/A2 仍保持阻塞。

失败 holdout challenge `TBH2-fac56106204c4b93a11befc577369360` 的 12 条人工 decisions 已封印；
model input `bd402b40…` 与 9×1 call set `8c63e377…` 已按 standing grant 派生。
正式 DeepSeek 第 2 个 call schema invalid，claim `da3f1eb1…` 已消费，blocked receipt
`f79060fe…` 证明未自动重试。当前不得复用同一 corpus/claim，Track B admission 未建立；
Provider A0/A1/A2 继续阻塞，A3 已拆出本 Milestone。TASK-EVAL-003 与 TASK-EVAL-004
均只读保留，不得重试或解盲；TASK-EVAL-005 的新范围由项目负责人明确批准，standing
grant 仅覆盖其合成诊断和后续 hash-bound 调用，不替代第五套人工 ground truth 确认。
## 当前结论

- `TASK-024` 已完成并已 push
- `TASK-025` 已完成 fixture 级验收收口并归档
- `TASK-026`：最小 `CandidateResolver` / 置信度分级 / evidence admission 闸门已完成并归档
- `TASK-026` 已完成真实 parser 主链路非 `HIGH` 可达性治理
- `TASK-027` 最小主实现已完成并归档
- `TASK-GOV-003` 已完成、已独立审计、已 push、远程同步确认
- `TASK-DEBT-001` 已归档，文件为 `tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`。该父任务已完成 4 条代码缺陷和 1 条覆盖盲区的标准记录、A/B/C 批次落地记录、父任务归档前独立只读审计 `GO`、Codex Review Intake Decision `GO TO ARCHIVE WITH CONDITIONS`、PR #14 / PR #15 文档同步和用户授权归档流程；归档不解锁 parser provenance、real DOCX `TABLE_CELL`、`TASK-028`、`TASK-031` 或 `TASK-032`
- `TASK-EVAL-001-A` 已完成并 push（`4bac2f4`）
- Git 历史显示 `TASK-EVAL-001-B` 对应 commit 为 `672d97f`；事后独立复核、定向测试 `30/30 PASS` 和父任务归档前独立审计已形成补偿证据，但不能追溯性等同于提交前复核
- `TASK-EVAL-001` 已于 2026-07-04 执行 rebaseline：旧 `GO TO ARCHIVE WITH CONDITIONS` 口径已回滚，不再作为当前状态依据；DoD #1 至 #11 仅保留为既有独立确认摘要，DoD #12 固定为未通过、未补足并作为永久治理债务保留
- 2026-07-09 用户确认 `TASK-EVAL-001` 独立只读审计已完成；Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`。DoD #12 固定未通过、不可补足；`TASK-GOV-005` 不补足 DoD #12。该结论针对 `TASK-EVAL-001` 当时的 parser-backed expected / anchor；此后 `TASK-DATA-001` 已接受 63 条独立人工 ground truth并完成转换，`TASK-034` 已执行正式 MVP E2E 但判定 `FAIL`。`TASK-028` / `TASK-031` / `TASK-032` 继续 `NO-GO`
- 2026-07-04 归档路径复判结论为 `ARCHIVE BLOCKED`；不采用 `ARCHIVE WITH EXPLICIT DEBT SPLIT`；rebaseline 后本父任务仍 active，不归档，不解除 `TASK-028`
- `TASK-028` Readiness Gate 只读结论为 `NO-GO`；仍不是实现授权，也不是 `TASK_SPEC` 派发授权
- 2026-07-04 用户确认：不单独创建 MVP 上线 / readiness 任务；后续按正常开发顺序推进，不代表进入 `TASK-029`
- `TASK-GOV-005` 已拆出并定界为历史 commit / push 授权证据治理债务；处理决定为 `BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`；2026-07-04 独立只读审计结论为 `GO`；该债务不追溯否定已 merge / push 内容，但阻止其作为后续绕过授权门禁的先例
- `TASK-GOV-006` 已通过 PR #18 合并完成云端 PR 触发验证，用于建立提交前授权证据模板、PR 模板和 `Authorization evidence check` PR body 文本门禁；PR #18 head commit 为 `432a63a25b0352e5ba9768f68f32c95a266474e4`，merge commit 为 `d3d5d1b507d233b5ff9a20350fad7b0c05a36cf9`，PR 触发的 CI 已通过。`Authorization evidence check` 已在 PR #18 中成功运行，但它不是 required status check，也不证明真实授权事实；该任务不配置 branch protection、repository ruleset 或 required status checks，不补足 `TASK-EVAL-001` DoD #12，不支撑父任务归档
- `TASK-GOV-007` 已通过 PR #33 合并，merge commit `1f62320f20ec29c52f49c0ed33c4244bb1be669e`；独立只读审计、状态增量复核与三项 CI 均通过
- `TASK-037 / ADR-017 / TASK_SPEC-037-A` 已通过 PR #34 合并，merge commit `401fd05b7a6c23014adb4f5511533467016c37ba`；`MVP_DEMO_MOCK` 与三类 budget profile seed 已进入主线，不激活 TASK-036-C2
- `FEATURE-MVP-001` 基于 `401fd05`；F3 与归档提交 `332d365` 已 push，PR #35 最终三项 CI 全绿并已合并，merge commit 为 `ca2798cd4db400f1fe512e2a13c0d40624929b7d`。backend CI 暴露的 3 个 Linux upload-root fixture 构造失败已由 F3 修复，host 定向 11/11、backend 313/313、Linux 11/11。v6/v7/v8 findings 已关闭；v9 的 CC AUDIT 与两个 Codex subagent 均为 GO，P0/P1/P2/blocking 全为 0。父 TASK 与 F3 已迁移到 `tasks/done/`，Feature 无剩余开发或集成门禁
- `TASK-033` 已完成 MVP 端到端样本验收规格冻结并归档到 `tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`。PR #24 已合并规格冻结建档，merge commit 为 `880893639ada9fa5e2d42b3d2bccb1662e37a5c9`；PR #25 已合并 post-merge 状态写回，merge commit 为 `a60fc9f`；Codex 归档 Review Intake Decision 为 `GO_TO_ARCHIVE_WITH_POST_MERGE_SYNC_SATISFIED`；2026-07-09 用户确认独立只读审计已对归档迁移与 Memory Writeback 给出 `GO`，并授权精确 stage、commit 与 push。归档不运行完整验收，不修改代码、测试、fixture、expected JSON，不解除 `TASK-EVAL-001` / `TASK-028` 门禁
- `TASK-DATA-001` 已完成真实 DOCX 人工 anchor 准备、转换实现和父任务归档前独立审计；独立审计最终 `GO`，Codex Decision 为 `GO_TO_ARCHIVE / INDEPENDENT PRE_ARCHIVE AUDIT SATISFIED / FULL MVP E2E NOT VERIFIED`，父任务已通过 PR #30 归档，merge commit 为 `01e59f54284bbab5409f0d7fd392acfd96d7ff83`。归档不解除 `TASK-EVAL-001` / `TASK-028` 门禁
- `TASK-034` v1 Phase 1 与 C2/R6B 的 `FAIL` 为历史运行；当前 worktree 的
  v29/R7 限定三样本工件为 27/27 candidate `MATCH`、27/27
  `PointStatus=PASS`、57 `MATCHED` + 6 human `EXCLUDED`、70 production
  semantic exclusions、0 SYS/Finding、`overallVerdict=PASS`。父任务保持
  active，等待当前 Milestone 最终三审；不自动进入 `TASK-028` /
  `TASK-031` / `TASK-032`
- `TASK-035` test-only `mvp-e2e-candidate-comparison-v2` 与
  `TASK_SPEC-035-A` 已实现并随 PR #32 合并；v1 历史失败不变，当前 worktree
  已以 v29/R7 正式复跑形成独立新工件
- `TASK-036` 的 A 已随 PR #32 合并；B1/B2/C1/C2/D1/D2 的最终 Core subject
  `90c4ae9a…` 已完成 verification、三方全零 GO 和 GitHub run `30748527866`
  全绿，并随 PR #37 合并为 `115be530…`
- 2026-07-24 治理规则收敛口径：L0 默认不进入主线；L1 合并式批处理；L2 一个父 TASK 可包含多个 TASK_SPEC，默认一个 Feature PR；L3 按可回滚风险边界独立审计。普通 push 不因传输动作重复完整审计，post-merge 只有改变下一门禁时即时写回，active→done 默认在 Feature/Milestone 收口时批量完成
- `TASK-030` A/B/C 当前批次已通过 PR #20 合并，PR #21 已合并 post-merge 状态写回，独立审计结论 `GO_WITH_CONDITIONS` 的 B1-B5 条件已满足，Codex 归档 Review Intake Decision 为 `GO_TO_ARCHIVE_WITH_CONDITIONS_SATISFIED`，父任务已归档到 `tasks/done/TASK-030-review-assets-versioning-governance.md`；后续只可按单独 `TASK_SPEC` 或任务授权推进，不自动进入 `TASK-028` / `TASK-031` / `TASK-032`
- `TASK-GOV-004` 已完成并进入归档写回准备：2026-06-27 Phase 3 minimal GitHub Actions CI 已通过 PR #5 合并落地，2026-06-28 Phase 5 第一阶段 GitHub branch protection / required checks 已配置并验证，Phase 5 post-implementation 独立只读审计结论为 `GO`；PR #8 已合并，merge commit 为 `5d73ea22c42971df848dbacb49c86d40e2143e1f`，PR head 为 `e9812bc118aa5a2f33294dcc9507566703da7517`。当前 Governance Mode 可标注为 `PR_REQUIRED_CHECKS`（第一阶段 CI required checks）；`CQCP Code Review` / `CQCP Spec & Docs Review` 尚未机制化发布为 required checks，未进入 `TASK-028` / `TASK-031` / `TASK-032`
- Step 2 原始逐条认领报告未入库，作为治理债务保留；父任务归档判断依据为归档前独立审计对本父任务相关关键断言的重新覆盖，不得表述为原始 Step 2 报告已入库。
  【回滚批注】经独立核实，上述“重新覆盖审计”未找到可追溯的原始报告记录，该依据不能支撑归档判断。
- `TASK-031` 仍未进入，继续禁止抢跑
- `TASK-032` 已登记为后续重构任务，当前禁止进入实现

## 已完成主链路任务

| 任务 | 名称 | 类别 | 当前状态 |
|---|---|---|---|
| `TASK-019` | Result Composer + ReviewResultSnapshot 最小合成 | A | 已完成 |
| `TASK-020` | Task Execution 最小状态机 | A | 已完成 |
| `TASK-021` | Result URL 查询 API | A | 已完成 |
| `TASK-022` | Persistent Result Query Adapter | A | 已完成 |
| `TASK-023` | 公开结果页最小展示 | B | 已完成 |
| `TASK-024` | 管理台诊断详情最小展示 | B | 已完成 |
| `TASK-025` | Parser / Candidate / Evidence 主链路接入 | A | 已完成并归档 |

## 当前后续任务

| 任务 | 名称 | 类别 | 当前状态 | 说明 |
|---|---|---|---|---|
| `TASK-026` | 最小 CandidateResolver 置信度治理 | A | 已完成并归档 | 文件：`tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`；已通过真实 parser 主链路 fixture 覆盖 `MEDIUM / LOW / CONFLICTED`，`HIGH` 才可进入确定性裁判 |
| `TASK-027` | EvidenceSlot / SourceAnchor 正式治理 | A | 已完成并归档 | `ADR-015` 已接受；`TASK-027-C`、`TASK-027-D` 与主实现提交 `b85f4dd` 均已完成；完成的是最小主实现落地，不是完整 `EvidenceBundle` 平台化 |
| `TASK-GOV-003` | 五类问题整改与角色执行门禁 | Governance | 已完成并归档 | 已独立审计、push、远程同步确认；完成前置治理，不等于 `TASK-EVAL-001-B` 可提交或 `TASK-028` 可进入 |
| `TASK-GOV-004` | PR 化多 Agent 开发治理与机制化门禁 | Governance | 已完成并归档写回准备 | 当前 Governance Mode 可标注为 `PR_REQUIRED_CHECKS`（第一阶段 CI required checks）；Phase 3 minimal CI 已通过 PR #5 合并落地；Phase 4 规格已定义且外部 GLM 5.2 Code Review 与 Spec & Docs Review 均为 `GO`；Phase 5 第一阶段已配置 branch protection / required checks / PR-only direct push 拒绝；Phase 5 post-implementation 独立只读审计 `GO`；PR #8 已合并，merge commit `5d73ea22c42971df848dbacb49c86d40e2143e1f`，head `e9812bc118aa5a2f33294dcc9507566703da7517`；任务文件已移动到 `tasks/done/TASK-GOV-004-pr-based-multi-agent-governance.md`；`CQCP Code Review` / `CQCP Spec & Docs Review` 尚未机制化发布为 required checks；未进入 `TASK-028` / `TASK-031` / `TASK-032` |
| `TASK-DEBT-001` | Review Engine 已确认缺陷与覆盖盲区记录 | Governance / Debt | 已完成并归档 | 已登记 5 条标准记录；`TASK_SPEC-DEBT-001-A/B/C` 均已落地并完成对应审计记录；2026-07-02 父任务归档前独立只读审计为 `GO`，Codex Review Intake Decision 为 `GO TO ARCHIVE WITH CONDITIONS`；PR #14 记录归档前审计写回，PR #15 同步归档前文档状态；2026-07-03 经用户授权执行归档流程。归档不授权 parser provenance、real DOCX `TABLE_CELL`、`TASK-028`、`TASK-031` 或 `TASK-032` |
| `TASK-EVAL-001` | Parser-backed 证据重合度评测基线 | A | REBASELINED / Active / 不归档 / 独立审计已完成 / KEEP ACTIVE | 2026-07-09 Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`；DoD #12 固定未通过、不可补足；`TASK-GOV-005` 不补足 DoD #12。后续 `TASK-DATA-001` 已接受独立人工 ground truth并完成转换，`TASK-034` 已执行正式 MVP E2E 但判定 `FAIL`。文件：`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md` |
| `TASK-GOV-005` | 历史 commit / push 授权证据治理债务 | Governance / Debt | Active / 已定界 / 独立只读审计 GO / 长期治理边界保留 | 文件：`tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`；从 `TASK-EVAL-001` 拆出 A/B 历史授权链不可完整核实问题；处理决定为 `BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`；不追溯否定已 merge / push 内容，但不得成为后续绕过授权门禁的先例；不写业务代码；任务仍长期保留 active，不表示已归档 |
| `TASK-GOV-006` | 提交前授权证据模板与 PR 文本门禁 | Governance | Active / PR #18 已合并 / 云端 PR 触发验证已通过 / 不配置 required status checks | 文件：`tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`；PR #18 head commit 为 `432a63a25b0352e5ba9768f68f32c95a266474e4`，merge commit 为 `d3d5d1b507d233b5ff9a20350fad7b0c05a36cf9`，PR 触发的 CI 已通过；`Authorization evidence check` 已在 PR #18 中成功运行，但它不是 required status check，也不证明真实授权事实；不配置 branch protection、ruleset 或 required status checks |
| `TASK-GOV-007` | Task Level 与 Git 收口治理优化 | L3 高风险治理 | PR #33 已合并 / Independent Audit GO / CI PASS | 文件：`tasks/active/TASK-GOV-007-task-level-and-git-closure-governance.md`；merge commit `1f62320f20ec29c52f49c0ed33c4244bb1be669e` |
| `TASK-037` | Execution Binding Release 与 Profile Seed | L3 高风险治理 | PR #34 已合并 / ADR-017 Accepted / Implementation Audit GO / CI PASS | 文件：`tasks/active/TASK-037-execution-binding-release-and-profile-seed.md`；merge commit `401fd05b7a6c23014adb4f5511533467016c37ba`；不激活 TASK-036-C2 |
| `TASK-MVP-001` | 合同审核用户闭环 Demo | L2 Feature（风险触发型） | Done / F3 VERIFIED / V9 DUAL AUDIT GO / PR #35 MERGED / CI GREEN | 文件：`tasks/done/TASK-MVP-001-contract-review-user-loop-demo.md`；F3 与归档提交 `332d365` 已 push；PR #35 三项 CI 全绿并合并为 `ca2798cd4db400f1fe512e2a13c0d40624929b7d`；F3 host 11/11、backend 313/313、Linux 11/11；v9 的 CC AUDIT 与两个 Codex subagent 均 GO，P0/P1/P2/blocking 全为 0；保持 legacy `v20260705.1` 与 `MVP_DEMO_MOCK`，未运行 TASK-034，不声明 Production Ready |
| `TASK-033` | MVP 端到端样本验收规格冻结 | A / Governance | 已完成并归档 | 文件：`tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`；PR #24 merge commit `880893639ada9fa5e2d42b3d2bccb1662e37a5c9`；PR #25 post-merge 状态写回 merge commit `a60fc9f`；Codex 归档 Review Intake Decision 为 `GO_TO_ARCHIVE_WITH_POST_MERGE_SYNC_SATISFIED`；2026-07-09 用户确认独立只读审计已对归档迁移与 Memory Writeback 给出 `GO`。仅冻结 2-3 份 DOCX 样本选择原则、输入字段、验收命令、证据口径和 expected 来源说明；不修改代码、测试、fixture、expected JSON，不把 AI/parser 输出当作人工 anchor 标准答案；不解除 `TASK-EVAL-001` / `TASK-028` 门禁 |
| `TASK-DATA-001` | MVP E2E 人工 anchor 准备 | Data / Evaluation | 已完成并归档 / 独立审计 GO / Codex GO_TO_ARCHIVE | 文件：`tasks/done/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md`；63 条逐出处明细均已接受；转换规格已归档到 `tasks/done/TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md`；PR #28 merge commit `23c66aaed34326f242f9fb395d784518421f1575`，PR #29 merge commit `2b30bf303642d10156eec5844ee09718adb595b3`，PR #30 归档 merge commit `01e59f54284bbab5409f0d7fd392acfd96d7ff83`；后续 `TASK-034` 正式 MVP E2E 已执行并判定 `FAIL`，不进入 `TASK-028` / `TASK-031` / `TASK-032` |
| `TASK-034` | MVP E2E 人工 anchor 正式验收执行 | A / Evaluation | Active / Final Core R7 PASS / PR #37 Merged / Milestone Provider recovery active | 文件：`tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`；最终 Core subject `90c4ae9a…` 已完成 Formal R7、verification、三方全零 GO 与 CI，并随 PR #37 合并；Provider admission 尚未建立，成功路径 Final Audit 仍等待 TASK-EVAL-006；正式限定样本目标为 3 份样本 27/27 candidate `MATCH`、27/27 `PointStatus=PASS`、57 `MATCHED` + 6 human `EXCLUDED`、70 production semantic exclusions、0 SYS/Finding；不声明 Production Ready，不进入 `TASK-028` / `TASK-031` / `TASK-032` |
| `TASK_SPEC-034-A` | test-only MVP E2E harness | A / Test-only | Implemented / Codex ACCEPT / Independent Audit ACCEPT | 文件：`tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md`；实现提交 `99bea3a6a3ce0cbecf337e76692aac3a6c428228`，序列化修复提交 `46a625a5eb5aee8ff5a31f86bb7300fb2d8e703a`；harness 13/13、既有回归 27/27；仅 test-only observer、同 task 查询和 63 occurrence 比较，未修改受保护生产或人工数据路径 |
| `TASK-035` | MVP E2E candidate comparison 契约重基线 | A / Evaluation / Governance | Active / TASK_SPEC-035-A Merged via PR #32 / v29 Formal R7 Executed | 文件：`tasks/active/TASK-035-mvp-e2e-candidate-comparison-contract-rebaseline.md`；harness 15/15、四类回归 27/27；实现提交 `52d73b3`；PR #32 merge commit `97ef08f`；v1 历史失败不变，当前 worktree 的 v29/R7 为独立新运行 |
| `TASK-036` | 多出处一致性证据架构冻结与分批实现治理 | A / Architecture | Active / A via PR #32 / B1-B2-C1-C2-D1-D2 via PR #37 / Final Core Audit GO | 文件：`tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`；A 随 PR #32 合并；B1/B2/C1/C2/D1/D2 最终 subject `90c4ae9a…` 完成 verification、三方全零 GO 与 CI，并随 PR #37 合并为 `115be530…`；父任务保持 active 等待 Milestone 最终跨 TASK 审计 |
| `TASK_SPEC-036-A` | 同值 occurrence provenance carrier foundation | A / Production Foundation | Implemented / Codex ACCEPT / Independent Implementation Audit GO / Merged via PR #32 | 文件：`tasks/active/TASK_SPEC-036-A-same-value-occurrence-provenance.md`；精确 2 个生产文件和 5 个测试文件；47/47 与 25/25；随 PR #32 合并，不修改现有 preparer，不激活 RuleSetVersion，不运行正式 E2E |
| `TASK_SPEC-035-A` | test-only candidate comparison v2 | A / Test-only | Implemented / Codex ACCEPT / Independent Implementation Audit GO / Merged via PR #32 | 唯一实现文件为 `Task034MvpE2EAcceptanceHarnessTest.java`；harness 15/15、四类回归 27/27；提交 `52d73b3` 已随 PR #32 合并；未运行 formal E2E，未修改 fixture/expected/production |
| `TASK-EVAL-001-A` | SourceAnchor row/cell observability | A | 已完成并 push | 提交 `4bac2f4` |
| `TASK-EVAL-001-B` | Evidence overlap baseline | A | 事后条件接纳 | Git 历史显示 commit `672d97f`；事后复核为 `ACCEPT WITH CONDITIONS`、定向测试 `30/30 PASS`；提交前复核缺失作为治理债务保留 |
| `TASK-028` | Gemma Provider 最小接入 | A | Readiness Gate NO-GO / 禁止进入 Review Intake | 仅作为未来 `MEDIUM` 档辅助通道；依赖 `TASK-GOV-003` 和 `TASK-EVAL-001` 收口；2026-07-04 只读复判未授权实现或 `TASK_SPEC` 派发 |
| `TASK-029` | MVP 端到端验证收口 | A | 未开始 | 依赖 `TASK-025` ~ `TASK-028` |
| `TASK-030` | Review assets 版本化治理 | Governance / A | 已完成并归档 | 文件：`tasks/done/TASK-030-review-assets-versioning-governance.md`；PR #20 已合并，merge commit `eadd017808fc8c2471fb0ac081f1e68e22dce5f5`；PR #21 已合并 post-merge 状态写回，merge commit `ef4643c7d11b1209c935229b4e39b76d659db00f`；独立审计 `GO_WITH_CONDITIONS` 的 B1-B5 条件已满足，Codex Review Intake Decision 为 `GO_TO_ARCHIVE_WITH_CONDITIONS_SATISFIED`。归档不启用 runtime loader，不声明生产 runtime 绑定，不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD，不进入 `TASK-028` / `TASK-031` / `TASK-032`，不创建 `TASK-033` |
| `TASK-031` | Result API / Admin API mapper 补洞 | B | 未开始 | 当前明确不进入 |
| `TASK-032` | ParserBackedReviewInputPreparer 按 ReviewPointFamily 拆分 | A | 禁止进入实现 | 等待治理门禁解除后再定界；不得以债务存在为由提前启动 |

## 任务边界与依赖

### `TASK-GOV-003`

- 定位：五类问题整改、角色分离、证据和父任务归档审计规则的治理任务。
- 依赖关系：
  - 是 `TASK-EVAL-001` 父任务归档前置治理任务。
  - 是 `TASK-028` Review Intake 前置治理任务。
- 边界：
  - 不等于 `TASK-028`。
  - 不进入业务实现，不修生产代码、测试或 fixture。
  - 任务收口不自动解除 `TASK-EVAL-001-B`、`TASK-EVAL-001`、`TASK-028` / `TASK-031` / `TASK-032` 的专属门禁。
- 文件：`tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`
- 治理依据：`docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`

### `TASK-DEBT-001`

- 定位：Codex 主控的父级债务记录任务，只承载标准记录和后续分流线索。
- 已登记：
  - `resolveTextEvidence()` 三个 signal 硬编码。
  - `collectPatternCandidates()` 的 `valueFormatSignal` 硬编码。
  - parser provenance 被 `SOURCE_ORIGIN / SOURCE_EXTRACTION_MODE / CONTEXT_TYPE` 常量覆盖。
  - `resolveRatioEvidence()` early return 跳过 fallback 候选。
  - TABLE_CELL 真实 DOCX 覆盖盲区；该项不是已确认代码 bug。
- 边界：
  - Step 1 已完成登记。
  - `TASK_SPEC-DEBT-001-A` 已在角色分离试点下完成实现、Codex 审查、提交前独立只读审计、commit 与 push；提交为 `3223d6760a977fe9deaf722e63b50bcbb6ce3611`。2026-06-27 GitHub 云端 post-push 独立只读复审为 `GO`，可作为 B 冻结前置审计结论使用。
  - 建立该任务以及 A 批次通过不代表其他修复任务、`TASK_SPEC` 或 `TASK-032` 已获启动授权。
  - TABLE_CELL 补强依赖独立人工 anchor 标注；当前父任务 DoD 不要求真实 DOCX cell，因此不阻塞 `TASK-EVAL-001` 归档判断。
  - Step 2 `CURRENT_CONTEXT.md` 逐条认领审计已有独立 agent 报告摘要；本任务地图不以自身证明完成，父任务归档前仍需复核原始报告和对应证据。
  - `TASK_SPEC-DEBT-001-B collectPatternCandidates valueFormatSignal 修复` 已通过 PR #4 合并并收口；该收口不代表父任务归档，也不授权 provenance、ratio early return、TABLE_CELL 或 `TASK-028` / `TASK-031` / `TASK-032`。
  - `TASK_SPEC-DEBT-001-C resolveRatioEvidence 候选收集拓扑修复` 已通过 PR #13 实现并合并，merge commit 为 `15888df4f0e89882814940c5eca0fc948fd1fef0`；该状态只代表 C 批次落地，不归档父任务，不授权 parser provenance、TABLE_CELL 或 `TASK-028` / `TASK-031` / `TASK-032`。
  - PR #14 已合并，merge commit 为 `13794fb2f09e02dad3b1d556cb6a5b9af7731c66`，head commit 为 `d232df3fa320ae1ef85578942a366e125b961a0b`；该 PR 仅记录父任务归档前审计写回，不归档父任务，不授权 parser provenance、TABLE_CELL 或 `TASK-028` / `TASK-031` / `TASK-032`。
  - PR #15 已合并，merge commit 为 `80e0dadb1bcf9d8da79214e97189e89ab042570c`，head commit 为 `e9995f2ffff5e76c5a597f1e5ac03779e7bdb662`；该 PR 仅同步归档前文档状态。
  - 2026-07-03 用户已授权归档流程，本任务归档到 `tasks/done/`；归档不授权 parser provenance、TABLE_CELL 或 `TASK-028` / `TASK-031` / `TASK-032`。
- 文件：`tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`

### `TASK-025`

- 定位：fixture 级验收任务
- 退出线：
  - 4 个正向 fixture + 4 个负向 fixture
  - `PointStatus + candidateValue + blockId + evidenceSummary`
- 不再继续开放式补规则
- 若后续出现新表达变体，必须新开轻量任务，不回流到 `TASK-025`

### `TASK-026`

- 定位：最小 `CandidateResolver` 治理任务
- 覆盖范围：
  - `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN`
  - 同 role 候选竞争检测
  - 真实 parser 主链路非 `HIGH` 可达性治理
  - 只有 `HIGH` 可进入确定性裁判
- 依赖：
  - `TASK-025` 主链路接入已完成
- 不包含：
  - 完整 `EvidenceSlot / SourceAnchor`
  - Gemma 接入

### `TASK-027`

- 定位：完整 `EvidenceSlot / SourceAnchor` 正式治理
- 依赖：
  - `TASK-026`
- 当前状态：
  - `decisions/ADR-015-evidence-slot-source-anchor-governance.md` 已接受
  - `TASK-027-A` / `TASK-027-B` 已完成并被 Codex 接受为有效前置输入
  - `TASK-027-C` 已完成并本地提交 `8e09dc6`
  - `TASK-027-D` 已完成并本地提交 `ed63184`
  - `TASK-027` 最小主实现已完成并本地提交 `b85f4dd`
  - 当前进入归档收口；未进入完整 `EvidenceBundle` 平台化
- 说明：
  - 负责把当前最小 resolver 提升为正式 evidence 生命周期与定位资产

### `TASK-028`

- 定位：Gemma `MEDIUM` 档辅助通道
- 依赖：
  - `TASK-026`
  - `TASK-027`
  - `TASK-EVAL-001` 最低评测基线
- 说明：
  - 仅作为复杂语义辅助，不承担最终确定性裁判

### `TASK-EVAL-001`

- 定位：parser-backed fixture 的证据定位质量评测基线
- 当前收口判断：`REBASELINED / Active / 不归档 / 不进入 TASK-028`。旧 `GO TO ARCHIVE WITH CONDITIONS` 不再作为当前状态依据；后续不得恢复旧归档口径。
- 2026-07-09 独立审计后状态收口：独立只读审计已完成；Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`。DoD #12 固定未通过、不可补足；`TASK-GOV-005` 不补足 DoD #12。该结论针对当时的 parser-backed expected / anchor；后续 `TASK-DATA-001` 已接受 63 条真实 DOCX 独立人工 ground truth并完成转换，`TASK-034` 已执行正式 MVP E2E 但判定 `FAIL`。
- 2026-07-04 归档路径复判：`ARCHIVE BLOCKED`；治理债务拆出不足以支撑 `ARCHIVE WITH EXPLICIT DEBT SPLIT`；随后已执行 rebaseline，重新定义父任务 DoD 与归档门禁。
- 重新定界 Review Intake Decision：`NO-GO TO ARCHIVE / SPLIT GOVERNANCE DEBT`。历史授权链证据进入不可恢复 / 不可完整核实分支；DoD #12 不可支撑，父任务不得归档。
- Rebaseline 后 DoD：DoD #1 至 #11 仅保留为既有独立确认摘要；DoD #12 固定为未通过、未补足；A/B 实现结果可作为历史技术质量信号，不作为父任务归档通过证据；若后续重新申请归档，必须先经过独立 agent 只读审计和 Codex 单独 Review Intake。
- Rebaseline 后持续边界：
  - 仅表示父任务已完成重新定界并保持 active，不代表 12/12 DoD 全部通过
  - DoD #12 未通过、未补足；A/B 历史 commit / push 授权记录无法完整核实
  - 该缺口永久保留为历史流程治理债务，不追溯否定已 push 内容、独立审计结论或 `30/30 PASS`
  - 该例外不得成为后续绕过 commit / push 明确授权门禁的先例
  - expected anchor 依赖 parser 内部 `blockId / rowIndex / cellIndex`，只证明一致性和回归稳定性，不证明独立人工 ground truth 正确性
  - 真实 DOCX positive baseline 的独立人工 `TABLE_CELL` ground truth 已由 `TASK_SPEC-DATA-001-A` 转换为 fixture / expected JSON 引用 / 定向测试；完整 MVP E2E 覆盖仍未验证
  - 不自动解除 `TASK-028` / `TASK-031` / `TASK-032` 门禁，不进入 Step 3，不起草或派发 `TASK_SPEC`
- 依赖：
  - `TASK-025`
  - `TASK-026`
  - `TASK-027`
- 最低范围：
  - block / table-row / cell level overlap
  - 至少 4 个正向 fixture + 4 个负向 / 冲突 fixture
  - `candidateValue` 正确但 `SourceAnchor` 错误时必须失败
- 不包含：
  - 生产审核语义变更
  - 模型比较或新模型接入
  - parser 替换、检索方案引入或字符级 span 强制评分
- Review Intake：
  - 结论为 `NEEDS-SPLIT`
  - 原 DoD 不降级
  - A 先解决 SourceAnchor row/cell observability
  - B 依赖 A 完成并经 Codex 验收

### `TASK-GOV-005`

- 定位：从 `TASK-EVAL-001` 拆出的历史 commit / push 授权证据治理债务。
- 当前状态：Active / 已定界 / 独立只读审计 GO / 长期治理边界保留。
- 处理决定：`BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`。
- 独立只读审计：2026-07-04 结论为 `GO`，无阻塞问题；非阻塞措辞问题已回写修正。
- 边界：
  - 不追溯否定 `TASK-EVAL-001-A/B` 已 merge / push 内容、事后独立审计结论或定向测试结果。
  - 不补足 `TASK-EVAL-001` DoD #12，不支撑父任务归档。
  - 不得成为后续绕过 commit / push 明确授权门禁的先例。
  - 不写业务代码，不修改测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、PRD、架构文档或 ADR。
- 后续如需继续处理，只能做只读审计、机制化治理定界或提交前授权证据规则补强；不得直接派发实现任务。
- 本任务仅覆盖历史 commit / push 授权证据治理债务，不覆盖 `DEBT-001-03` parser provenance / `SourceAnchor`，也不覆盖 `DEBT-001-05` real DOCX `TABLE_CELL`。

### `TASK-GOV-006`

- 定位：提交前授权证据模板、PR 模板和 PR body 文本门禁。
- 当前状态：Active / PR #18 已合并 / 云端 PR 触发验证已通过 / 不配置 required status checks。
- 范围：
  - 新增 `docs/governance/pre-submit-authorization-evidence-template.md`。
  - 新增 `.github/pull_request_template.md`。
  - 新增 `scripts/check-pr-authorization-evidence.mjs` 与正反 fixture。
  - 在 `.github/workflows/ci.yml` 中新增 `Authorization evidence check` job，仅在 `pull_request` 事件运行。
  - PR #18 已合并完成真实 PR 触发验证，head commit 为 `432a63a25b0352e5ba9768f68f32c95a266474e4`，merge commit 为 `d3d5d1b507d233b5ff9a20350fad7b0c05a36cf9`；PR 触发的 CI 已通过，且 `Authorization evidence check` 成功运行。
- 边界：
  - 该 check 只检查 PR body 字段存在且非占位，不证明用户授权、测试、独立审计或 Memory Writeback 已真实发生。
  - `Authorization evidence check` 不是 required status check；如后续要纳入 required status checks，必须另行定界并取得用户授权。
  - 不配置 branch protection、repository ruleset 或 required status checks。
  - 不发布 `CQCP Code Review` / `CQCP Spec & Docs Review` Check Run 或 Commit Status。
  - 不补足 `TASK-EVAL-001` DoD #12，不支撑 `TASK-EVAL-001` 归档，不归档 `TASK-EVAL-001` 或 `TASK-GOV-005`。
  - 不进入 `TASK-028` / `TASK-031` / `TASK-032`。

### `TASK-033`

- 定位：MVP 端到端样本验收规格冻结任务，Codex 主控，不是 CC-DS 执行环境实现 `TASK_SPEC`。
- 当前状态：Done / 已归档 / 规格冻结完成 / PR #24 与 PR #25 已合并 / 不实现。
- 范围：
  - 冻结 2-3 份 DOCX 样本选择原则。
  - 冻结每份样本的输入字段定义。
  - 冻结后续正式验收任务的命令候选和证据口径。
  - 明确 expected 来源说明和独立性要求。
  - 明确 `TASK-DATA-001` 承接真实 DOCX `TABLE_CELL` 人工 anchor 标准答案准备。
- 边界：
  - 不写业务代码，不改测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
  - 不修改 parser、`CandidateResolver`、`EvidenceSlot` 或 `SourceAnchor`。
  - 不运行完整 MVP 端到端验收。
  - 不把 AI/parser 输出当作人工 anchor 标准答案。
  - 不归档 `TASK-EVAL-001`，不补足 DoD #12。
  - 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
  - 不迁移 historical active。
- 提交前门禁：
  - 独立 agent 只读复核已完成，Decision 为 `GO`，无 blocking findings；复核重点为 expected 来源、证据口径和验收指标是否过度声明。
- PR 与归档状态：
  - PR #24 已合并，merge commit 为 `880893639ada9fa5e2d42b3d2bccb1662e37a5c9`。
  - PR #25 已合并 post-merge 状态写回，merge commit 为 `a60fc9f`，head commit 为 `79c0b3b`。
  - 归档前独立只读审计结论为 `NEEDS_POST_MERGE_SYNC`；该阻塞项已通过 PR #25 和本次归档迁移清理。
  - Codex 归档 Review Intake Decision 为 `GO_TO_ARCHIVE_WITH_POST_MERGE_SYNC_SATISFIED`。
  - 2026-07-09 用户确认独立只读审计已对归档迁移与 Memory Writeback 给出 `GO`，并授权精确 stage、commit 与 push。

### `TASK-034`

- 定位：Codex 主控正式 MVP E2E 人工 anchor 验收任务，不是 CC-DS 实现 `TASK_SPEC`。
- 依赖：`TASK-033` 验收规格和 `TASK-DATA-001` 的 63 条独立人工 ground truth 均已完成并归档。
- 固定输入：
  - `CQCP-MVP-DOCX-001/002/003` 三份真实 DOCX。
  - 22/19/22 共 63 条 occurrence，57 条纳入、6 条排除。
- Phase 0 门禁：
  - 只读证明现有入口可串联真实 DOCX、parser、审核状态机、结果快照和结果查询。
  - 必须能记录 `PointStatus`、`candidateValue`、证据摘要、anchor 定位、结果 URL 或 `SYS-*` 诊断，并支持 63 条 occurrence 的显式比较。
  - 入口不足时停止正式验收，单独冻结 `TASK_SPEC-034-A`；只允许 test-only E2E harness。
- Phase 0 结果（2026-07-14）：
  - `NO_GO_TEST_ONLY_HARNESS_REQUIRED`；Phase 0 当时父任务为 `STOPPED_FOR_TASK_SPEC_034_A` 并保持 active，后续状态见 Phase 1 结果。
  - 真实 DOCX parser-backed 状态机、结果快照和 GET 查询组件分别存在，但没有单一入口证明同一次 DOCX run 的同 task 查询。
  - actual `candidateValue` 只存在于内部 `PointEvidence`，未进入 `PointReviewResult` / `ReviewResultSnapshot` 查询输出。
  - human fixture 的 63/57/6 契约已验证，但人工位置到 actual anchor 的 bridge/逐条比较不存在。
  - 后端四类定向测试合计 27/27；前端命令因本地缺 `vitest` / `tsc` 环境失败。Phase 0 当时未运行正式 MVP E2E。
  - `TASK_SPEC-034-A` 已冻结为 test-only harness，并在编码前规格映射计划获 Codex 放行后完成实现。
- Phase 1 结果（2026-07-14）：
  - harness 实现提交 `99bea3a6a3ce0cbecf337e76692aac3a6c428228`，manifest 序列化修复提交 `46a625a5eb5aee8ff5a31f86bb7300fb2d8e703a`；Codex Review Intake 与独立只读复核均接纳。
  - 001/002/003 均完成同 run parser、审核状态机、snapshot 和同 task 查询；27 个 `PointStatus` 均为 `PASS`，无 Finding、无 `SYS-*`。
  - candidate comparison 为 9 `MATCH` / 18 `MISMATCH`；税额 expected 为复合值而 actual 为标量，5 类比例 expected 含 `%` 而 actual 不含 `%`。
  - 63 条 occurrence 保持 57 纳入 / 6 排除；57 条纳入全部 `NOT_OBSERVABLE`。生产结果只有 27 个一点评一 anchor，其中 23 个无 `previewElementRef`；基数折叠与精确定位同时不足。6 条排除全部 `EXCLUDED`。
  - v1 正式最终判定 `FAIL`，作为历史工件保留。后续由 `TASK-035` 承接
    test-only candidate comparison 重基线，由 `TASK-036` / `ADR-016`
    承接多出处 evidence 架构。
- R6B 历史结果与 v29/R7 当前结果（2026-07-29）：
  - C2/R6B 仍为历史 `FAIL`，不覆写、不删除。
  - D1 引入显式版本 `v20260729.1` scope / classifier / runtime loader 后，
    正式 R7 对相同 3 份真实 DOCX 完成同 run parser → state machine →
    snapshot → query。
  - R7 为 27/27 candidate `MATCH`、27/27 `PointStatus=PASS`、
    57 `MATCHED` + 6 human `EXCLUDED`、70 条 production semantic
    exclusion ledger、0 SYS/Finding、`overallVerdict=PASS`；原始 console、
    JUnit XML、run manifest、三份 result、occurrence CSV 与 seal 位于
    `outputs/task-034-mvp-e2e-acceptance-v3/`。
  - 该结论只证明冻结样本、版本和规则集的限定门禁，不声明 Production Ready、
    普遍正确性或后续任务解锁；冻结 45568f… 的最终统一验证已因审计 NO_GO 失效，
    整改后的统一验证与三审必须从零重建。
- 边界：
  - 不修改 DOCX、人工 XLSX、matrix、fixture、expected JSON或人工 ground truth。
  - 不修改生产 parser、`CandidateResolver`、`EvidenceSlot`、`SourceAnchor`、Review Engine、公共 API、数据库、workflow、ADR 或生产数据结构。
  - 不从 parser、AI 或 actual 输出倒填人工标准答案。
  - 不归档 `TASK-EVAL-001`，不补足 DoD #12，不进入 `TASK-028` / `TASK-031` / `TASK-032`。

### `TASK-035`

- 定位：Codex 主控的 MVP E2E candidate comparison 契约重基线，不修改生产 candidate 语义。
- 冻结版本：`mvp-e2e-candidate-comparison-v2`。
- 核心规则：
  - raw human expected 与 raw production actual 必须保留。
  - 名称使用 strip 后精确文本比较；金额/比例使用已冻结的 ASCII、精度、范围、符号、千分位和完整匹配 grammar。
  - 比例按 PRD 百分点数值比较，`70%` 与 `70` 可投影为同一 comparable value，但禁止 `0.7 -> 70` 隐式缩放。
  - 税额公式只从人工复合 expected 唯一提取 `taxAmount` 与 actual taxAmount 标量比较；PointStatus 继续承载公式裁判。
  - comparable 固定为 canonical decimal JSON string 或 null；projection 失败为 `NOT_OBSERVABLE`；禁止 actual 倒填 expected。
- 审计：父契约最终独立审计 `GO`；`TASK_SPEC-035-A` 规格阻断已整改，编码前计划经 Codex 放行；实现独立只读审计 `GO`、无 findings，Codex Review Intake 为 `ACCEPT_IMPLEMENTATION`。
- 实现：唯一 harness 文件提交为 `52d73b3`；定向 XML 计数为 harness `15/15`、四类回归 `27/27`，均无失败、错误或跳过。
- 当前门禁：TASK-035 candidate comparison 路线已接纳；v29/R7 已随最终 Core
  subject `90c4ae9a…` 完成三方全零 GO 和主线合并。v1 历史失败工件保持不变；
  Milestone 最终仍须执行跨 TASK 冻结与三审。
- ADR：不需要；若触及生产 CandidateResolver / Review Engine 则停止并另行 ADR。

### `TASK-036`

- 定位：多出处一致性 evidence 的 CandidateResolver / EvidenceSlot / deterministic verdict / SourceAnchor 架构冻结。
- 事实基线：57 条纳入人工 occurrence 对 27 个 actual anchors；27 个 anchors 中仅 4 个有 row-level ref，23 个无 `previewElementRef`。
- 根因：resolver 先以忽略 row/cell/ref identity 的 key 去重，再只投影 `getFirst()` selected candidate，形成双重折叠；`PointEvidence` 与结果链路每点只保留单个 block/ref，`TASK-EVAL-001-A` 的单 candidate row/cell 可观测性不能恢复已丢失 occurrence provenance。
- ADR：`decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md`，状态 `Accepted`。
- Draft 方向：distinct semantic value group 与 occurrence provenance 分离；仅 `CONSISTENCY_SET_READY` 完整可靠集合进入后端确定性一致性裁判；普通冲突/归属歧义保持 `SYS-* / NOT_CONCLUDED`；`maxCandidates` 与 `occurrenceBudget` 分离；点级 `pointResults[].sourceAnchors[]` 为 occurrence coverage 真源；scope / exclusion policy 版本化。
- 兼容结论：现有 point result、snapshot、OpenAPI 与 JSONB query 使用列表结构，可承载一点评多 anchors；若实现发现事实不同必须停止并拆兼容任务。
- 审计：首轮四项 `NO_GO` 阻断已整改，最终 delta 核对 `GO`；接受后 ARCHITECTURE 同步独立审计同样为 `GO`。
- 实现拆分：A 为未激活 carrier foundation；B 承接新 RuleSetVersion、显式 activation、scope/budget/readiness；C 承接真实 point collectors、可靠异值裁判与集成。
- A 规格审计：第一轮 `NO_GO` 的三项阻断（无 lineage 删除不同 identity、未绑定版本却改变普通任务 anchor 基数、误称 persistence 写入 round-trip）已整改；第二轮 `GO`。
- A 接纳：Codex 已审查实际 diff 并复验第一组 47/47、第二组 25/25；独立实现审计先指出双 block 精确测试向量缺口，补充后最终 `GO`，Review Intake 为 `ACCEPT_IMPLEMENTATION`。
- 当前实现与门禁：A 已随 PR #32 合并；B1/B2/C1/C2/D1/D2 的最终 Core subject
  `90c4ae9a…` 已完成完整 verification、三方全零 GO、CI 和 PR #37 合并。
  当前仅证明 runtime seam 与 R7 zero-call，不证明 Provider admission；仍不得声明
  Production Ready 或解锁 `TASK-028` / `TASK-031` / `TASK-032`。

### `TASK-EVAL-001-A`

- 定位：SourceAnchor row/cell observability 前置任务
- 最低范围：
  - 稳定表达 block + table row
  - cell 仅在具备真实稳定 `cellIndex` 时表达
  - 优先复用 ADR-015 `previewElementRef`
  - 保持旧 block-level 快照兼容读取
- 禁止：
  - 不实现 overlap evaluator
  - 不修改 expected JSON / DOCX fixture
  - 不通过 candidateValue 搜索 cells 伪造 cell anchor
  - 不改变业务 Finding、EvidenceSlot admission 或 CandidateResolver gate
- 完成结果：
  - parser 保留真实 `tableId + rowIndex` 与每个 cell 的稳定 `cellIndex + joined-text range`
  - 单 cell 命中输出 cell `previewElementRef`
  - 跨 cell 或无法唯一映射的命中降级为 row `previewElementRef`
  - 旧 block-only snapshot 兼容读取并规范化为 `BLOCK_LEVEL`
  - ResultComposer 以 `blockId + previewElementRef` 去重，不吞并同一 row 的不同 cell anchor

### `TASK-EVAL-001-B`

- 定位：父任务的 evidence overlap baseline 实现阶段
- 依赖：`TASK-EVAL-001-A` 完成并经 Codex 验收
- 最低范围：
  - expected JSON anchor 标注
  - test-only evaluator
  - block / row / cell canonical key
  - 4 正向 + 4 负向/冲突
  - `expectedRecall / actualPrecision / requiredHitRate`
  - `missingExpectedBlocks / unexpectedMatchedBlocks / attributionFailureReason`
- 当前状态：Git 历史显示 commit 为 `672d97f695756249a871da53ad2821eb5146997f`；据用户提供的独立 agent 报告摘要，提交前独立复核流程曾缺失，事后复核建议为 `ACCEPT WITH CONDITIONS`，clean clone 定向测试报告为 `30/30 PASS`；据 Codex Review Intake 摘要，接纳建议为 `ACCEPT WITH CONDITIONS — TEST EVIDENCE SATISFIED`。上述摘要不替代原始报告、console、commit 和 diff，待父任务归档前独立审计复核
- 完成结果：
  - 四份 expected JSON 已各冻结一个正向 evidence evaluation case
  - canonical key 覆盖 BLOCK / TABLE_ROW / TABLE_CELL
  - 4 个真实正向 fixture 达到 `expectedRecall=1.0`、`actualPrecision=1.0`、`requiredHitRate=1.0`
  - 负向覆盖 conflict / medium / low、wrong block、wrong row、wrong cell、unexpected 与 unavailable anchor
  - 据用户提供的独立 agent 报告摘要，定向复跑四组测试合计 `30/30 PASS`，测试前后工作区干净；凭证以原始报告和 console 输出为准
  - `1.0 / 1.0 / 1` 只证明 parser-backed 输出与 expected JSON 的一致性和回归稳定性；expected blockId / rowIndex / cellIndex 依赖 parser 内部稳定标识，candidateValue 来源于独立登记的 matrix，不证明 anchor 客观正确
  - evaluator 支持 TABLE_CELL canonical key，test-only / mock 覆盖已存在；`TASK-DATA-001` 的真实 DOCX 人工 ground truth 已完成转换，后续 `TASK-034` 正式 MVP E2E 已执行并判定 `FAIL`
  - 未修改生产代码或 DOCX fixture

### `TASK-GOV-004`

- 定位：PR 化多 Agent 开发治理与 GitHub 机制化门禁父任务。
- 当前 Governance Mode：`PR_REQUIRED_CHECKS`（第一阶段 CI required checks）。
- Phase 3 当前状态：已新增 `.github/workflows/ci.yml` 最小 CI workflow；PR #5 已 merged，merge commit 为 `455d2e3b7a4d8397087deb127a649a6f92aa19a0`，PR head commit 为 `50f0befadbd17e7ea80cc2a9d90d38365753f4de`；final head GitHub Actions run `28288707273` completed / success，`Backend Gradle tests` 与 `Admin web lint, tests, and build` 均为 success。`TASK-GOV-004` 已完成 post-merge 归档写回准备。
- Phase 4 当前状态：手动独立 Code Review + Spec & Docs Review 规格已准备；触发条件、审查证据、结论格式和回写方式已写入任务文件。2026-06-28 外部 GLM 5.2 Code Review 与 Spec & Docs Review 均为 `GO`；该试运行不发布 Check Run 或 Commit Status，不代表 required checks 生效。
- Phase 5 当前状态：2026-06-28 第一阶段 GitHub branch protection / required checks 已配置并验证；required checks 为 `Backend Gradle tests` 与 `Admin web lint, tests, and build`，source 为 GitHub Actions `app_id: 15368`，`strict: true`；已启用 PR-only direct push 拒绝。Phase 5 post-implementation 独立只读审计结论为 `GO`。repository ruleset 仍未配置；`CQCP Code Review` / `CQCP Spec & Docs Review` 尚未机制化发布为 required checks。
- 归档写回：PR #8 已合并，URL 为 `https://github.com/codegodkun/CQCP/pull/8`，merge commit 为 `5d73ea22c42971df848dbacb49c86d40e2143e1f`，PR head 为 `e9812bc118aa5a2f33294dcc9507566703da7517`；本地 `master` 与 `origin/master` 对齐，HEAD 为同一 merge commit；任务文件已移动到 `tasks/done/TASK-GOV-004-pr-based-multi-agent-governance.md`。
- 当前只读证据：
  - 主仓库工作区干净。
  - `master` 与 `origin/master` 对齐。
  - 2026-06-27 前本地无 `.github` 目录；Phase 3 后新增 `.github/workflows/ci.yml`。
  - `gh` CLI 不可用。
  - 公开 GitHub REST API 对 repo / branch protection / rulesets / workflows 返回 `403`。
  - Phase 5 第一阶段已通过 GitHub API、测试 PR 和 direct push probe 证明 required checks / PR-only 门禁生效。
- Phase 0-6 顺序：
  - Phase 0：治理状态基线与任务边界冻结。
  - Phase 1：目录结构与审计环境核实。
  - Phase 2：Draft PR 流程。
  - Phase 3：基础 GitHub Actions CI。
  - Phase 4：手动独立 Code Review + Spec & Docs Review。
  - Phase 5：Protected Branch + Required Checks。
  - Phase 6：归档流程 PR 化。
- 目录口径：
  - `C:\Users\1\Documents\CQCP` 是主仓库。
  - `C:\Users\1\Documents\CQCP-work` 是未来执行 agent 工作区，当前尚未建立。
  - `C:\Users\1\Documents\CQCP_AUDIT` 是审计环境根目录。
  - `C:\Users\1\Documents\CQCP_AUDIT\CQCP` 是被审计 git clone，后续审计 git 命令必须在此目录执行。
  - 不得使用 `git worktree` 创建 `CQCP_AUDIT\CQCP`。
  - `audit-scratch` 建议放在 `C:\Users\1\Documents\CQCP_AUDIT\audit-scratch`，不放进被审计 clone。
- 边界：
  - 不替代五类问题整改 v3。
  - 不修复已知代码缺陷。
  - Phase 3 仅允许新增最小 `.github/workflows/ci.yml`；不修改 fixture、expected JSON、ADR、PRD、OpenAPI、数据库、Docker 或 GitHub 设置。
  - 不进入 `TASK-EVAL-001-B`、`TASK-028`、`TASK-031`、`TASK-032`。
  - Codex 不得充当 Code Review Agent 或 Spec & Docs Review Agent。

## 协作边界

### A 类任务

适用范围：
- 主审核链路
- 状态机
- 证据机制
- 模型职责边界
- 结果快照与契约

规则：
- 由 Codex 主控
- 涉及核心审核链路、`EvidenceSlot`、`CandidateResolver`、`ReviewPointFamily` 等边界时，先记录 ADR

### B 类任务

适用范围：
- 公开页
- 管理台
- 视图层 mapper / view-model

规则：
- 不得反向扩写主链路
- `TASK-031` 不得提前启动

## 当前建议顺序

1. `TASK-GOV-004` 已完成 post-merge 归档写回准备；Governance Mode 可标注为 `PR_REQUIRED_CHECKS`（第一阶段 CI required checks）。`CQCP Code Review` / `CQCP Spec & Docs Review` 尚未机制化发布为 required checks；不得因此进入 `TASK-028` / `TASK-031` / `TASK-032`。
2. `TASK-GOV-003` 已完成并归档。
3. v3 Step 1 已通过 `TASK-DEBT-001` 完成五条问题标准记录，`TASK-DEBT-001` 已经用户授权归档；该归档不代表后续修复任务获准启动，不得直接派发实现 TASK_SPEC。
4. `TASK-EVAL-001` 已完成独立审计后状态收口，Codex Review Intake Decision 为 `NO-GO TO ARCHIVE / KEEP ACTIVE`；后续不再围绕该父任务反复补写归档文档。
5. `TASK-GOV-006` 已通过 PR #18 完成云端 PR 触发验证；该结果仍不自动纳入 required status checks。
6. `TASK-DATA-001` 已完成规则冻结、63 条 `ACCEPTED_HUMAN_GROUND_TRUTH`、转换实现和父任务归档审计，已通过 PR #30 归档。
7. `TASK-034` v1 Phase 1 与 C2/R6B 历史运行均为 `FAIL`；v29/R7 当前限定三样本工件为 `PASS`，父任务保持 active 并等待 Milestone 最终审计。
8. `TASK_SPEC-035-A` 已实现并接纳，提交 `52d73b3`；v1 正式失败证据保持不变，v29/R7 是独立新运行。
9. `ADR-016` 已接受，`docs/ARCHITECTURE.md` v0.10 已同步并审计 `GO`；TASK_SPEC-036-A 已随 PR #32 合并，B1/B2/C1/C2/D1/D2 已随 PR #37 合并。Track B 第五套 admission 已历史终态 `SEALED_NO_GO_MODEL_MISMATCH / NOT_ESTABLISHED` 且不得重试；ADR-026 / TASK-EVAL-006 的 model evaluation 已通过，但 R6 subject `a9daf62f…` 因 CC projection transport finding 失效。R7 只用仓库外一次性 builder 修 Git-blob 字节和 evidence/source/excluded 分区，不建设新的审计协议；完成新 verification/freeze/三方全零 GO/CI 前不进入 Provider A0/A1/A2，也不得组合任何旧 subject 报告通过。
10. `TASK-GOV-007` 已通过 PR #33 合并，merge commit `1f62320f20ec29c52f49c0ed33c4244bb1be669e`。
11. `TASK-037 / ADR-017 / TASK_SPEC-037-A` 已通过 PR #34 合并，merge commit `401fd05b7a6c23014adb4f5511533467016c37ba`。该任务采用 `MVP_DEMO_MOCK` 与三类 budget profile seed，不激活 TASK-036-C2。
12. `FEATURE-MVP-001` 基于 `401fd05`；A~F/F1/F2、真实 Demo 与 F3 已完成，F3 与归档提交 `332d365` 已 push。backend CI 暴露的 3 个 Linux fixture 构造失败已由 F3 修复；host 定向 11/11、backend 313/313、Linux 11/11。v6/v7/v8 的原始证据与状态真源 findings 已补正；v9 的 CC AUDIT 与两个 Codex subagent 均为 GO。PR #35 最终三项 CI 全绿并合并为 `ca2798cd4db400f1fe512e2a13c0d40624929b7d`，父 TASK 与 F3 已归档，Feature 无剩余开发或集成门禁。
13. 低风险文档动作采用合并式批处理：Codex 自查、精确 diff、一次 Memory Writeback；不因普通状态同步单独建 TASK或派独立 agent。
14. 用户已确认不单独创建 MVP 上线 / readiness 任务；后续按正常开发顺序推进，当前不进入 `TASK-029`。
15. 后续如需处理 parser provenance，必须另行定界任务；real DOCX `TABLE_CELL` 独立人工 anchor 已完成 fixture / expected JSON 引用 / 定向测试转换。TASK-034 v29/R7 的限定样本结果为 `PASS`，但不代表 production readiness 或普遍正确性。
16. 继续禁止 `TASK-028` / `TASK-031` / `TASK-032` 抢跑。
