# MVP 任务地图

更新日期：2026-07-07

## 当前结论

- `TASK-024` 已完成并已 push
- `TASK-025` 已完成 fixture 级验收收口并归档
- `TASK-026`：最小 `CandidateResolver` / 置信度分级 / evidence admission 闸门已完成并归档
- `TASK-026` 已完成真实 parser 主链路非 `HIGH` 可达性治理
- `TASK-027` 最小主实现已完成并归档
- `TASK-GOV-003` 已完成、已独立审计、已 push、远程同步确认
- `TASK-DEBT-001` 已归档，文件为 `tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`。该父任务已完成 4 条代码缺陷和 1 条覆盖盲区的标准记录、A/B/C 批次落地记录、父任务归档前独立只读审计 `GO`、Codex Review Intake Decision `GO TO ARCHIVE WITH CONDITIONS`、PR #14 / PR #15 文档同步和用户授权归档流程；归档不解锁 parser provenance、real DOCX `TABLE_CELL`、`TASK-028`、`TASK-031` 或 `TASK-032`
- `TASK-EVAL-001` 父任务归档前独立审计随后给出 `GO WITH CONDITIONS`，Codex Review Intake Decision 为 `GO TO ARCHIVE WITH CONDITIONS`
  【回滚批注】以上为历史记录，该决定已按用户要求回滚，不再作为当前状态依据。
- `TASK-EVAL-001-A` 已完成并 push（`4bac2f4`）
- Git 历史显示 `TASK-EVAL-001-B` 对应 commit 为 `672d97f`；事后独立复核、定向测试 `30/30 PASS` 和父任务归档前独立审计已形成补偿证据，但不能追溯性等同于提交前复核
- `TASK-EVAL-001` 已于 2026-07-04 执行 rebaseline：旧 `GO TO ARCHIVE WITH CONDITIONS` 口径已回滚，不再作为当前状态依据；DoD #1 至 #11 仅保留为既有独立确认摘要，DoD #12 固定为未通过、未补足并作为永久治理债务保留
- 2026-07-04 用户要求先处理 `TASK-EVAL-001` 归档前审计 / 收口判断；Codex 预审结论为 `NO-GO TO ARCHIVE / INDEPENDENT AUDIT REQUIRED / BASELINE NOT CLEAN`，当前必须等待独立 agent 只读审计，不能由 Codex 自审替代
- 2026-07-04 归档路径复判结论为 `ARCHIVE BLOCKED`；不采用 `ARCHIVE WITH EXPLICIT DEBT SPLIT`；rebaseline 后本父任务仍 active，不归档，不解除 `TASK-028`
- `TASK-028` Readiness Gate 只读结论为 `NO-GO`；仍不是实现授权，也不是 `TASK_SPEC` 派发授权
- 2026-07-04 用户确认：不单独创建 MVP 上线 / readiness 任务；后续按正常开发顺序推进，不代表进入 `TASK-029`
- `TASK-GOV-005` 已拆出并定界为历史 commit / push 授权证据治理债务；处理决定为 `BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`；2026-07-04 独立只读审计结论为 `GO`；该债务不追溯否定已 merge / push 内容，但阻止其作为后续绕过授权门禁的先例
- `TASK-GOV-006` 已通过 PR #18 合并完成云端 PR 触发验证，用于建立提交前授权证据模板、PR 模板和 `Authorization evidence check` PR body 文本门禁；PR #18 head commit 为 `432a63a25b0352e5ba9768f68f32c95a266474e4`，merge commit 为 `d3d5d1b507d233b5ff9a20350fad7b0c05a36cf9`，PR 触发的 CI 已通过。`Authorization evidence check` 已在 PR #18 中成功运行，但它不是 required status check，也不证明真实授权事实；该任务不配置 branch protection、repository ruleset 或 required status checks，不补足 `TASK-EVAL-001` DoD #12，不支撑父任务归档
- `TASK-033` 已建档为 MVP 端到端样本验收规格冻结任务；仅冻结样本选择原则、输入字段、验收命令、证据口径和 expected 来源说明，不运行完整验收，不修改代码、测试、fixture、expected JSON，不解除 `TASK-EVAL-001` / `TASK-028` 门禁；正式提交前需要独立 agent 只读复核
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
| `TASK-EVAL-001` | Parser-backed 证据重合度评测基线 | A | REBASELINED / Active / 不归档 / 独立审计待执行 | 2026-07-04 已重新定义父任务 DoD 与归档门禁；DoD #1 至 #11 仅保留为既有独立确认摘要，DoD #12 固定为未通过、未补足；A/B 历史 commit / push 授权记录无法完整核实并永久保留为治理债务；旧条件归档口径已回滚；2026-07-04 Codex 预审为 `NO-GO TO ARCHIVE / INDEPENDENT AUDIT REQUIRED / BASELINE NOT CLEAN`；文件：`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md` |
| `TASK-GOV-005` | 历史 commit / push 授权证据治理债务 | Governance / Debt | Active / 已定界 / 独立只读审计 GO / 长期治理边界保留 | 文件：`tasks/active/TASK-GOV-005-historical-commit-authorization-evidence-debt.md`；从 `TASK-EVAL-001` 拆出 A/B 历史授权链不可完整核实问题；处理决定为 `BOUNDARY RECORDED / NO RECOVERY PATH / NO IMPLEMENTATION AUTHORIZATION`；不追溯否定已 merge / push 内容，但不得成为后续绕过授权门禁的先例；不写业务代码；任务仍长期保留 active，不表示已归档 |
| `TASK-GOV-006` | 提交前授权证据模板与 PR 文本门禁 | Governance | Active / PR #18 已合并 / 云端 PR 触发验证已通过 / 不配置 required status checks | 文件：`tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`；PR #18 head commit 为 `432a63a25b0352e5ba9768f68f32c95a266474e4`，merge commit 为 `d3d5d1b507d233b5ff9a20350fad7b0c05a36cf9`，PR 触发的 CI 已通过；`Authorization evidence check` 已在 PR #18 中成功运行，但它不是 required status check，也不证明真实授权事实；不配置 branch protection、ruleset 或 required status checks |
| `TASK-033` | MVP 端到端样本验收规格冻结 | A / Governance | Active / 规格冻结中 / 待独立只读复核 / 不实现 | 文件：`tasks/active/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`；仅冻结 2-3 份 DOCX 样本选择原则、输入字段、验收命令、证据口径和 expected 来源说明；不修改代码、测试、fixture、expected JSON，不把 AI/parser 输出当作人工 anchor 标准答案；不解除 `TASK-EVAL-001` / `TASK-028` 门禁 |
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
- 2026-07-04 归档前审计启动前收口判断：`NO-GO TO ARCHIVE / INDEPENDENT AUDIT REQUIRED / BASELINE NOT CLEAN`。用户已触发归档判断，但当前缺少独立 agent 只读审计，且主工作区不是干净审计基线；不得由 Codex 自审替代独立审计。
- 2026-07-04 归档路径复判：`ARCHIVE BLOCKED`；治理债务拆出不足以支撑 `ARCHIVE WITH EXPLICIT DEBT SPLIT`；随后已执行 rebaseline，重新定义父任务 DoD 与归档门禁。
- 重新定界 Review Intake Decision：`NO-GO TO ARCHIVE / SPLIT GOVERNANCE DEBT`。历史授权链证据进入不可恢复 / 不可完整核实分支；DoD #12 不可支撑，父任务不得归档。
- Rebaseline 后 DoD：DoD #1 至 #11 仅保留为既有独立确认摘要；DoD #12 固定为未通过、未补足；A/B 实现结果可作为历史技术质量信号，不作为父任务归档通过证据；若后续重新申请归档，必须先经过独立 agent 只读审计和 Codex 单独 Review Intake。
- Rebaseline 后持续边界：
  - 仅表示父任务已完成重新定界并保持 active，不代表 12/12 DoD 全部通过
  - DoD #12 未通过、未补足；A/B 历史 commit / push 授权记录无法完整核实
  - 该缺口永久保留为历史流程治理债务，不追溯否定已 push 内容、独立审计结论或 `30/30 PASS`
  - 该例外不得成为后续绕过 commit / push 明确授权门禁的先例
  - expected anchor 依赖 parser 内部 `blockId / rowIndex / cellIndex`，只证明一致性和回归稳定性，不证明独立人工 ground truth 正确性
  - 真实 DOCX positive baseline 的 `TABLE_CELL` 覆盖仍为 0，由 `TASK-DEBT-001` 或后续人工 anchor 标注任务追踪
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

- 定位：MVP 端到端样本验收规格冻结任务，Codex 主控，不是 Claude Code / DeepSeek 实现 `TASK_SPEC`。
- 当前状态：Active / 规格冻结中 / 待独立只读复核 / 不实现。
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
  - 必须先经过独立 agent 只读复核，重点核查 expected 来源、证据口径和验收指标是否过度声明。

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
  - evaluator 支持 TABLE_CELL canonical key，test-only / mock 覆盖已存在；真实 DOCX positive baseline TABLE_CELL 覆盖仍未完成，由 `TASK-DEBT-001` 和后续人工 anchor 标注任务追踪
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
4. `TASK-EVAL-001` 已执行 rebaseline；2026-07-04 Codex 预审为 `NO-GO TO ARCHIVE / INDEPENDENT AUDIT REQUIRED / BASELINE NOT CLEAN`；后续如需重新申请归档，必须先做独立只读审计和 Codex Review Intake。
5. `TASK-GOV-006` 已通过 PR #18 完成云端 PR 触发验证；该结果仍不自动纳入 required status checks。
6. `TASK-033` 已建档；下一步必须先做独立 agent 只读复核，复核通过前不得提交或进入实现。
7. `TASK-030` 已归档；runtime loader 评审、model / budget profile 源定义等仍只是后续候选建议，不等于实现授权，需用户另行确认。
8. 用户已确认不单独创建 MVP 上线 / readiness 任务；后续按正常开发顺序推进，当前不进入 `TASK-029`。
9. 后续如需处理 parser provenance 或 real DOCX `TABLE_CELL`，必须另行定界任务；TABLE_CELL 必须先取得独立人工 anchor 标注。
10. 继续禁止 `TASK-028` / `TASK-031` / `TASK-032` 抢跑。
