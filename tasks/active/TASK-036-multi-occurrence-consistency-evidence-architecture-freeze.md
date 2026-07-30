# TASK-036：多出处一致性证据架构冻结

状态：Active / A-B-C2-D1-D2 Implemented in MVP-002 Worktree /
TASK-034 R7 PASS / MILESTONE FINAL AUDIT PENDING

类型：A 类主链路架构治理父任务

优先级：P0

负责人：Codex

创建日期：2026-07-14

来源：`TASK-034` Phase 1 的 57/57 `NOT_OBSERVABLE`

## 背景

`TASK-034` 正式运行输出 27 个 `PointStatus=PASS` 和 27 个 actual anchors，但人工 ground truth 有 57 条纳入 occurrence。actual anchors 中只有 4 个带 row-level `previewElementRef`，23 个没有 `previewElementRef`，没有一个能表达完整的 57 条逐出处集合。

只读代码核查确认：

* `MinimalCandidateResolver` 先用不包含 row/cell/ref occurrence identity 的 `reviewPointCode + role + blockId + candidateValue` 做去重，再对多个 fully-attributed 同值候选判为 `HIGH` 并只返回 `fullyAttributed.getFirst()`。
* `PointEvidence` 只有单个 `blockId / previewElementRef`。
* `MinimalReviewEngine.anchorsFor()` 因而每个审核点只生成一个 `SourceAnchorSummary`。
* `TASK-EVAL-001-A` 已解决单个已选 candidate 的 row/cell 可观测性，但没有保存同值候选的全部 occurrence provenance。

因此根因不是单纯缺 `cellIndex`，而是 resolver 内 occurrence-insensitive dedup 与 selected-candidate 投影造成双重折叠。provenance 必须在任何去重和 distinct value grouping 之前保留。解决该问题会改变 `CandidateResolver` 输出、EvidenceSlot 基数解释、确定性一致性裁判和 SourceAnchor 基数，必须先记录 ADR。

## 目标

* 通过 `ADR-016` 冻结“语义候选值”与“同值 occurrence provenance”分离的架构。
* 冻结一致性审核点对多出处同值、可靠异值、归属歧义和截断的不同处理。
* 冻结多 SourceAnchor 输出、精确 row/cell 定位与快照兼容边界。
* 冻结 occurrence scope / exclusion policy 的版本归属，禁止硬编码样本编号或人工 fixture。
* ADR 未接受前不创建实现 `TASK_SPEC`；现已按用户单独授权冻结第一批未激活 carrier foundation 规格。

## 非目标

* 架构冻结阶段不直接修改生产代码或测试；后续实现只能经独立冻结、审计和授权的局部 `TASK_SPEC` 执行。fixture、expected JSON、DOCX、XLSX 与 matrix 始终不在 A 范围。
* 不直接实现 57 条 occurrence coverage。
* 不修改 `TASK-034` v1 验收结果。
* 不接入 Gemma、不引入全文 RAG、不回灌全文。
* 不进入 `TASK-028` / `TASK-031` / `TASK-032`。

## 输入

* `TASK-034` 正式证据与 63 行 occurrence comparison。
* `docs/ARCHITECTURE.md` 的 CandidateResolver、EvidenceSlot、Result Composition、SourceAnchor 和一期成功标准。
* `PRD.md` 的结构化字段一致性、EvidenceSlot 和结果页证据规则。
* `ADR-014`、`ADR-015`。
* 当前 `MinimalCandidateResolver`、`ParserBackedReviewInputPreparer`、`MinimalReviewEngine`、`ResultComposer`。
* 上游任务：`TASK-EVAL-001-A`、`TASK-DATA-001`、`TASK-034`。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md`
* `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
* `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* `docs/ARCHITECTURE.md`
* `PRD.md` 第 8.2、8.6、8.9 节
* `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
* `tasks/active/TASK-EVAL-001-A-source-anchor-row-cell-observability.md`

### Optional Context

* `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
* `tasks/done/TASK-027-evidence-slot-source-anchor-governance.md`
* `tasks/done/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md`
* `docs/backend.md`
* `docs/ai-review.md`

### Out of Scope

* 新审核点、新模型、新 API endpoint、数据库 migration 或 UI 实现。
* 人工 ground truth 重标注。
* 生产 parser 替换、OCR、PDF 或字符级范围强制化。

## 范围

### 包含

* 多出处同值候选的 provenance 保存语义。
* 可靠多值不一致与归属歧义的 SYS/Finding 分流。
* EvidenceSlot `maxCandidates` 对“distinct semantic values”与独立 `occurrenceBudget` 的基数解释，以及 `CONSISTENCY_SET` 的合法配置。
* 一致性审核点使用的版本化 occurrence scope policy。
* 一点多 anchor 的结果、查询与历史快照兼容要求。
* parser row/cell identity 在每个 occurrence 上的真实透传要求。

### 不包含

* 具体 Java record / class 命名和一次性大规模重构。
* 修改人工排除项以迎合生产范围。
* 把同 block / row / table 当作 TABLE_CELL 命中。
* 使用 candidateValue 反向搜索 cell 伪造 provenance。

## 约束

* `ADR-016` 已由用户明确接受；接受本身只授权架构同步。
* 用户随后已单独授权进入 TASK-036 生产实现治理流程；Codex 已冻结 `TASK_SPEC-036-A`，执行方编码前规格映射计划已经 Codex 审查为 `GO / IMPLEMENTATION AUTHORIZED`，当前只允许按冻结范围实现 A。
* 独立 agent 必须只读审计架构事实、ADR 与任务边界。
* 任何生产实现必须保持 `SYS-*` 与 Finding 分流、Evidence 不足不裁判、历史快照不回填。
* 对完整一致性检查，occurrence provenance 被截断时不得继续输出业务 `PASS`。

## 交付物

* `decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md` Draft。
* 本任务父任务包。
* 独立只读审计报告与 Codex Review Intake Decision。
* 已冻结的局部 `TASK_SPEC-036-A` 及其独立规格审计记录。

## 验收标准

1. ADR 明确区分 semantic value group 与 occurrence provenance。
2. 同值多出处不再被折叠丢失，也不被误判为冲突。
3. 只有通过完整 admission gate 的 `CONSISTENCY_SET_READY` 可靠异值集合才进入确定性一致性 ERROR；普通同 role 竞争和归属歧义仍为 `CONFLICTED` 或 `SYS-* / NOT_CONCLUDED`。
4. occurrence 截断、解析缺失或 scope 不完整时不得输出业务 `PASS`。
5. `CONSISTENCY_SET` 明确要求 `minCandidates=1`、`maxCandidates>=2` 并单列 `occurrenceBudget`；任一上限超限均为 `BUDGET_TRUNCATED / PARTIAL / NOT_CONCLUDED`，不输出业务 ERROR。
6. 每个参与裁判的 occurrence 能输出独立 SourceAnchor；TABLE_CELL 只接受真实 cell identity。
7. occurrence scope / exclusion policy 版本化，不硬编码 001/002/003 或人工 occurrenceNo。
8. 点级 `pointResults[].sourceAnchors[]` 是 occurrence 输出真源；现有 Result API / snapshot 能承载多 anchor，顶层聚合不得作为点级 coverage 真源。
9. 架构冻结交付不夹带实现；后续生产改动只允许进入独立 `TASK_SPEC`。fixture、expected、DOCX、XLSX、matrix、workflow 与已接受 ADR 不得修改。

## 测试与验证

架构冻结阶段仅执行：

* 正式 sample JSON anchor 基数与 `previewElementRef` 统计。
* 63 行 occurrence 的 57/6、39 BLOCK / 18 TABLE_CELL 纳入统计。
* 当前 resolver → evidence → anchor 代码路径只读核查。
* ADR-014 / ADR-015 / PRD / ARCHITECTURE 一致性核对。
* `git diff --check`、`git status --short`。

不运行生产测试，不开启正式 E2E，不安装依赖。

## 文档更新要求

* `CURRENT_CONTEXT.md`：是。
* `tasks/MVP_TASK_MAP.md`：是。
* `changelog/2026-07.md`：是。
* `docs/ARCHITECTURE.md`：当前否；先由 ADR 冻结差异。
* ADR：是，新增 `ADR-016` Draft。

## Next Task Handoff

当前 A/B/C1/C2/D1/D2 已按冻结局部规格在 `codex/task-mvp-002` worktree
实现，R7 已补足 execution-scoped exact query 证明并重建 Track B v5，统一验证已通过。
下一步只做 `MILESTONE-MVP-002` 新冻结和三审；不扩大到 Provider、
`REVIEWING_MODEL`、commit、push、PR 或 merge。

## 独立审计与 Codex Review Intake

* 2026-07-14 首轮独立审计：`NO_GO`。阻断项为根因漏记 occurrence-insensitive dedup、未明确对 ADR-014/015/ARCHITECTURE 的窄化取代、可靠集合 admission gate 不完整、`maxCandidates` 与 occurrence budget 语义冲突。
* Codex Review Intake：`ACCEPT_FINDINGS`。ADR-016 Draft 已按四项阻断意见修订，并补充点级 `sourceAnchors[]` 真源、MVP occurrence identity 与精确状态映射。
* 2026-07-14 第二轮独立复审：`GO_WITH_FINDINGS`，无 blocking finding；四项首轮阻断均已解除。两项非阻断文档一致性（兼容核查状态、真实 DTO 路径）已修正。
* 2026-07-14 最终 delta 只读核对：`FINAL_GO`，上述增量未引入 blocking finding。
* Codex Review Intake：`ACCEPT_REVIEW / READY_FOR_USER_ADR_DECISION / NO_IMPLEMENTATION_AUTHORIZATION`。
* 2026-07-14 用户明确接受 `ADR-016`，并要求先同步 `docs/ARCHITECTURE.md`、不得直接进入生产实现。
* 接受后同步独立审计：`GO`，无 blocking / non-blocking finding；确认 v0.10 与 ADR 等价，未引入公共 API、migration 或实现授权。
* Codex Review Intake：`ADR_ACCEPTED / ARCHITECTURE_SYNCHRONIZED / NO_IMPLEMENTATION_AUTHORIZATION`。
* 2026-07-14 用户单独授权进入 TASK-036 生产实现治理流程。Codex 建立分支 `codex/task-036-a-consistency-set-provenance` 并冻结 `TASK_SPEC-036-A`。
* TASK_SPEC-036-A 第一轮独立规格审计：`NO_GO`；三项阻断为无 lineage 删除不同 identity、未绑定版本却改变普通任务 anchor 基数、把只读 persistent adapter 误称写入 round-trip。
* Codex 接受 findings 并将 A 收窄为未激活 carrier foundation；第二轮独立规格审计：`GO`。该阶段 Decision：`SPEC_FROZEN / INDEPENDENT_SPEC_AUDIT_GO / PRE-CODING_PLAN_PENDING / NO CODE AUTHORIZATION`。
* Claude Code / DeepSeek 编码前规格映射计划已提交；Codex 审查接受并附加 raw snapshot、preflight 先行、nonempty carrier 不回退 legacy anchor、现有 preparer 不改四项强制条件。Decision：`GO / IMPLEMENTATION AUTHORIZED`。
* TASK_SPEC-036-A 实现完成；Codex 审查实际 diff 并重跑两组冻结测试。独立实现审计发现一项精确双 block 测试向量缺口，补充同一测试方法后最终审计 `GO`、无 blocking finding。
* Codex Review Intake Decision：`ACCEPT_IMPLEMENTATION`。该接纳仅覆盖未激活 carrier foundation；现有 preparer / RuleSetVersion / 普通任务仍输出 legacy 单 anchor。

## 风险

* 把可靠异值一律当 resolver conflict，会把真实合同不一致降级成 SYS；反向把归属歧义当业务 ERROR，会生成无可靠证据 Finding。
* 保存全部 occurrences 可能增加快照体积，必须以版本化 scope 与截断降级控制。
* 只扩 SourceAnchor 列表而不改变上游 candidate/evidence 保留，仍无法找回已丢失出处。
* 现有 6 条人工排除不能直接硬编码成生产规则。

## 待确认

* 已确认：用户接受 `ADR-016`，`docs/ARCHITECTURE.md` v0.10 已同步并审计 `GO`。
* 已确认：B1/B2/C1/C2 已在当前 `codex/task-mvp-002` worktree 集成；
  `v20260715.1` execution 可进入 consistency-set runtime，普通
  `MVP_DEMO_MOCK / v20260705.1` binding 不变。
* 已确认：2026-07-29 C1/C2 相关 10 个测试类联合复跑 413/413 通过。
* 历史事实：TASK-034 R6 为 `FAIL`；C1 full scan 的
  payment-method-neutral 冻结语义使 inactive payment template 候选与 active
  branch 候选发生 role conflict。
* 已确认：D1 用新 `v20260729.1` 与
  `consistency-scope-v20260729.1` 解决 active/inactive branch attribution；
  没有原地修改 v15，R7 正式工件为 `PASS`。
* 已确认：D2 eligibility/family-plan/runtime-isomorphic EvidencePacket seam
  已实现；当前 R7 27 点全部 deterministic HIGH，Track B 为 27/27 zero-call
  abstention，Provider admission 仍未建立。

## 完成记录

* 完成日期：未完成。
* 变更文件：本任务包、已接受的 ADR-016、`docs/ARCHITECTURE.md` v0.10 与项目记忆文档。
* 测试结果：架构冻结两轮独立只读审计及最终 delta 核对 `GO`；TASK_SPEC-036-A 第一组 47/47、第二组 25/25，独立实现审计最终 `GO`。
* MVP-002 worktree 增量：B1 versioned policy、B2 loader/gate、C1 inactive runtime
  core 和 C2 execution activation 已集成；定向联合回归 413/413。
* D1/R7 增量：D1 30/30、与既有回归合并 443/443；R7 为 27/27
  candidate `MATCH`、27/27 `PASS`、57 `MATCHED` + 6 human `EXCLUDED`、
  70 production semantic ledger、0 SYS/Finding。
* D2/Track B 增量：seam 20/20；3 份包、27 packet、9 plan、57 occurrence，
  v5 三个全新盲评 agent 27/27 `ZERO_CALL_REQUIRED`，seal 为
  `edcfa024dfe2cbf6c5b1fe6ca1427a91d61101dd8903f19f61c70294f1fe2970`。
  这不构成 Provider admission。
* 遗留问题：当前 worktree 增量统一验证已通过，尚待新冻结和同一 hash 三审；
  尚未进入主线。
* 2026-07-31 中途收敛：B1/B2/C1/C2/D1/D2 runtime seam 纳入先行 Core
  integration unit；Core 完整验证、正式三方全零审计、CI 和 merge 后才可声明
  `seamIntegratedIntoMainline=true`。该顺序不授权 Provider A0、A1/A2 或 A3。
* 备注：不得据此宣称 Production Ready、Provider admitted 或
  `TASK-028 / TASK-031 / TASK-032` 已解锁；未授权 commit、push、PR 或 merge。
