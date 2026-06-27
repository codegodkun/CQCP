# MVP 任务地图

更新日期：2026-06-27

## 当前结论

- `TASK-024` 已完成并已 push
- `TASK-025` 已完成 fixture 级验收收口并归档
- `TASK-026`：最小 `CandidateResolver` / 置信度分级 / evidence admission 闸门已完成并归档
- `TASK-026` 已完成真实 parser 主链路非 `HIGH` 可达性治理
- `TASK-027` 最小主实现已完成并归档
- `TASK-GOV-003` 已完成、已独立审计、已 push、远程同步确认
- `TASK-DEBT-001` 已建立为 active 父级债务记录任务，标准登记 4 条代码缺陷和 1 条覆盖盲区；第一批 `TASK_SPEC-DEBT-001-A` 已通过角色分离试点、Codex 审查、提交前独立只读审计、commit / push 和 2026-06-27 post-push 独立只读复审 `GO`；第二批 `TASK_SPEC-DEBT-001-B` 已冻结到编码前规格映射计划阶段，尚未派发实现，其他分流仍未获实现授权
- `TASK-EVAL-001` 父任务归档前独立审计随后给出 `GO WITH CONDITIONS`，Codex Review Intake Decision 为 `GO TO ARCHIVE WITH CONDITIONS`
  【回滚批注】以上为历史记录，该决定已按用户要求回滚，不再作为当前状态依据。
- `TASK-EVAL-001-A` 已完成并 push（`4bac2f4`）
- Git 历史显示 `TASK-EVAL-001-B` 对应 commit 为 `672d97f`；事后独立复核、定向测试 `30/30 PASS` 和父任务归档前独立审计已形成补偿证据，但不能追溯性等同于提交前复核
- `TASK-EVAL-001` 已回滚为暂停归档，仅保留未提交 diff；DoD #1 至 #11 已独立确认，DoD #12 未通过、未补足并作为永久治理债务保留
- `TASK-GOV-004` 已建立 active 治理任务，用于把 PR 化多 Agent 开发治理方案 v2 转为可追踪任务；2026-06-27 Phase 3 minimal GitHub Actions CI 已通过 PR #5 合并落地，Phase 4 手动独立 Code Review + Spec & Docs Review 规格已准备但尚未执行审查；当前 Governance Mode 仍只能标注为 `LEGACY_MANUAL`，`PR_REQUIRED_CHECKS` 尚未生效，`TASK-GOV-004` 未归档，未进入 `TASK-028` / `TASK-031` / `TASK-032`
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
| `TASK-GOV-004` | PR 化多 Agent 开发治理与机制化门禁 | Governance | Active（Phase 4：手动独立审查规格已准备，未归档） | 当前 Governance Mode 为 `LEGACY_MANUAL`；Phase 3 minimal CI 已通过 PR #5 合并落地；Phase 4 规格已定义触发、证据、结论格式和回写方式，但尚未执行审查；branch protection、ruleset 或 required checks 未配置，不得写 `PR_REQUIRED_CHECKS`；未进入 `TASK-028` / `TASK-031` / `TASK-032` |
| `TASK-DEBT-001` | Review Engine 已确认缺陷与覆盖盲区记录 | Governance / Debt | Active（A 批次已提交推送并 post-push GO；B 批次已冻结到映射计划阶段） | 已登记 5 条标准记录；`TASK_SPEC-DEBT-001-A` 已完成实现、Codex Review Intake `A. 可以合并`、提交前独立只读审计 `GO`，并已 commit / push 为 `3223d6760a977fe9deaf722e63b50bcbb6ce3611`；2026-06-27 GitHub 云端 post-push 独立只读复审为 `GO`；`TASK_SPEC-DEBT-001-B` 已冻结到编码前规格映射计划阶段，尚未派发实现；其他分流仍需另行冻结 TASK_SPEC |
| `TASK-EVAL-001` | Parser-backed 证据重合度评测基线 | A | 暂停归档（未提交 diff） | DoD #1 至 #11 已独立确认；DoD #12 未通过、未补足，A/B 历史 commit / push 授权记录无法完整核实并永久保留为治理债务；文件：`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md` |
| `TASK-EVAL-001-A` | SourceAnchor row/cell observability | A | 已完成并 push | 提交 `4bac2f4` |
| `TASK-EVAL-001-B` | Evidence overlap baseline | A | 事后条件接纳 | Git 历史显示 commit `672d97f`；事后复核为 `ACCEPT WITH CONDITIONS`、定向测试 `30/30 PASS`；提交前复核缺失作为治理债务保留 |
| `TASK-028` | Gemma Provider 最小接入 | A | 禁止进入 Review Intake | 仅作为未来 `MEDIUM` 档辅助通道；依赖 `TASK-GOV-003` 和 `TASK-EVAL-001` 收口 |
| `TASK-029` | MVP 端到端验证收口 | A | 未开始 | 依赖 `TASK-025` ~ `TASK-028` |
| `TASK-030` | Review assets 版本化治理 | A | 未开始 | 后续治理任务 |
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
  - 后续候选 `TASK_SPEC-DEBT-001-B collectPatternCandidates valueFormatSignal 修复` 已冻结到编码前规格映射计划阶段；当前只允许派发映射计划，不得直接实现。Codex 审查映射计划 `GO` 前，不得授权 CC/DeepSeek 修改代码或测试。
- 文件：`tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`

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
- 暂停归档边界：
  - 仅表示父任务当前暂停归档，不代表 12/12 DoD 全部通过
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
- 当前 Governance Mode：`LEGACY_MANUAL`。
- Phase 3 当前状态：已新增 `.github/workflows/ci.yml` 最小 CI workflow；PR #5 已 merged，merge commit 为 `455d2e3b7a4d8397087deb127a649a6f92aa19a0`，PR head commit 为 `50f0befadbd17e7ea80cc2a9d90d38365753f4de`；final head GitHub Actions run `28288707273` completed / success，`Backend Gradle tests` 与 `Admin web lint, tests, and build` 均为 success。`TASK-GOV-004` 未归档。
- Phase 4 当前状态：手动独立 Code Review + Spec & Docs Review 规格已准备；触发条件、审查证据、结论格式和回写方式已写入任务文件，但尚未执行审查，尚不发布 Check Run 或 Commit Status。
- 当前只读证据：
  - 主仓库工作区干净。
  - `master` 与 `origin/master` 对齐。
  - 2026-06-27 前本地无 `.github` 目录；Phase 3 后新增 `.github/workflows/ci.yml`。
  - `gh` CLI 不可用。
  - 公开 GitHub REST API 对 repo / branch protection / rulesets / workflows 返回 `403`。
  - 当前仍不能证明 GitHub 设置真实状态，也不能证明 required checks 已生效。
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

1. `TASK-GOV-004` Phase 4 手动独立 Code Review + Spec & Docs Review 规格已准备；下一步如获授权，仅可按该规格执行一次手动独立审查试运行。Governance Mode 仍为 `LEGACY_MANUAL`，branch protection / required checks / ruleset 未配置，`PR_REQUIRED_CHECKS` 尚未生效；`TASK-GOV-004` 未归档。
2. `TASK-GOV-003` 已完成并归档。
3. v3 Step 1 已通过 `TASK-DEBT-001` 完成五条问题标准记录；后续对该任务执行只读 Review Intake 时，不进入开发。
4. `TASK-DEBT-001` 建立不代表修复获准启动，不得直接派发实现 TASK_SPEC。
5. 当前仅允许派发 `TASK_SPEC-DEBT-001-B` 编码前规格映射计划；不提交新的 B 代码、测试、fixture 或 expected JSON 变更，不进入 `TASK-028` / `TASK-031` / `TASK-032`，不派发实现任务。
