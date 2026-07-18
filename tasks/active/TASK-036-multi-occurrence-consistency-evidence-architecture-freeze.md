# TASK-036：多出处一致性证据架构冻结

状态：Active / A Merged / B1 Committed at 137c202 / B2 Implementation Accepted and Awaiting Commit / C Pending

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

`TASK_SPEC-036-B2` loader / fail-closed activation gate foundation 已实现、通过 Codex Review、最终独立实现审计 `GO` 与真实 Linux Docker image build，当前等待用户对 B2 十路径及治理写回路径的精确 commit 授权。C 仍须另行冻结、审计和授权；正式 E2E 继续锁定。

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
* `TASK_SPEC-036-B1` 编码前规格映射计划经 Codex 附条件放行；真实 Claude Code 仅修改两个静态 policy JSON、Review Assets README、validator 与 Node test 五个允许路径，未修改生产 Java、API、数据库、fixture、expected、DOCX、XLSX、matrix、workflow、ADR 或 ARCHITECTURE。
* B1 首轮实现独立审计为 `NO_GO_TO_ACCEPT`：冻结预算/版本、九点精确数量与六模块固定映射存在可变异绕过。Codex 接受 findings，仅授权 CC 修正 validator 与测试。
* B1 修正后 Codex 复验 Node tests `100/100`、review-assets validator `9/9`、`git diff --check` 退出 0；第二轮独立审计对预算/版本、追加空对象、模块整体交换与 table-cell identity 乱序执行真实变异探针，结论 `GO_TO_ACCEPT`，无剩余 blocking finding。
* Codex Review Intake Decision：`ACCEPT_IMPLEMENTATION / INDEPENDENT_IMPLEMENTATION_AUDIT_GO / AWAITING_PRECISE_COMMIT_AUTHORIZATION`。B1 仍保持 `DRAFT / NOT_BOUND / loaderEnabled=false / databasePersistence=false / productionEffect=NONE`，不解锁 B2/C 或正式 E2E。
* 2026-07-16 Codex 创建并冻结 `TASK_SPEC-036-B2`。首轮独立规格审计 `NO_GO`，阻断为 Docker context 无法看见仓库级 review-assets、跨包不可访问 package-private `ReviewPointCode`、错误使用不存在的 Gradle Wrapper，以及 loader reason code / expected 未冻结。
* Codex 接受全部 findings：纳入最小 Docker context/Dockerfile/.dockerignore 边界；新类移入 `reviewengine`；改用系统 `gradle`；冻结 runtime source/release state、reason codes、优先级与镜像 boot JAR fail-fast 证据。增量独立复审最终为 `GO`，无剩余 findings。
* B2 Codex Review Intake Decision：`SPEC_FROZEN / INDEPENDENT_SPEC_AUDIT_GO / CODE_BLOCKED_BY_B1_COMMIT / PRE_CODING_PLAN_NOT_AUTHORIZED / NO_IMPLEMENTATION_AUTHORIZATION`。B2 只冻结 loader/gate foundation；C 前不得把 `v20260715.1` 写为已生效 execution 版本。
* B1 已形成精确基线提交 `137c202` 并推送到同名远端分支；B2 编码前规格映射计划随后经 Codex 放行，Claude Code / DeepSeek 仅修改冻结的十个允许路径。
* B2 实现首轮审查与独立实现审计暴露全局 reason 优先级、schema 类型门禁和 `reviewPointDefinitions` 引用整体异常映射缺口；各轮 finding 均经 Codex 接受后定点修正，最终补齐 209 个 loader/gate 定向测试。
* B2 最终验证：`RuntimeRuleSetLoaderTest` 200/200、`RuleSetActivationGateTest` 9/9，B1 Node tests 100/100、validator 9/9，`bootJar` 与两条精确 JAR entry 断言通过，Compose config 与真实 Linux `api-server` image build 通过；最终增量独立审计 `GO`，无 findings。
* B2 Codex Review Intake Decision：`ACCEPT_IMPLEMENTATION / B2_FOUNDATION_ONLY / INDEPENDENT_IMPLEMENTATION_AUDIT_GO / DOCKER_BUILD_GO / AWAITING_PRECISE_COMMIT_AUTHORIZATION`。不接线 execution/snapshot，不生产激活，不解锁 C 或正式 E2E。

## 风险

* 把可靠异值一律当 resolver conflict，会把真实合同不一致降级成 SYS；反向把归属歧义当业务 ERROR，会生成无可靠证据 Finding。
* 保存全部 occurrences 可能增加快照体积，必须以版本化 scope 与截断降级控制。
* 只扩 SourceAnchor 列表而不改变上游 candidate/evidence 保留，仍无法找回已丢失出处。
* 现有 6 条人工排除不能直接硬编码成生产规则。

## 待确认

* 已确认：用户接受 `ADR-016`，`docs/ARCHITECTURE.md` v0.10 已同步并审计 `GO`。
* 已确认：B 拆为 B1 静态不可变策略与 B2 runtime binding/activation；A 不改变当前规则集输出。
* 已确认：B1 冻结并实现新 RuleSetVersion `v20260715.1`、九点 policy、`maxCandidates=8`、`occurrenceBudget=64` 与未激活校验；实现已通过提交 `137c202` 形成可重建基线。
* 已确认：B2 runtime loader / fail-closed activation gate foundation 已实现并经 Codex、独立审计和真实 Linux Docker build 接纳；B2 不直接接线 execution/snapshot，也不解锁 C。

## 完成记录

* 完成日期：未完成。
* 变更文件：本任务包、已接受的 ADR-016、`docs/ARCHITECTURE.md` v0.10 与项目记忆文档。
* 测试结果：架构冻结两轮独立只读审计及最终 delta 核对 `GO`；TASK_SPEC-036-A 第一组 47/47、第二组 25/25，独立实现审计最终 `GO`；TASK_SPEC-036-B1 Node tests 100/100、review-assets validator 9/9，第二轮独立实现审计 `GO_TO_ACCEPT`；TASK_SPEC-036-B2 loader/gate 209/209、Node 100/100、validator 9/9、boot JAR 与真实 Linux Docker build 通过，最终独立实现审计 `GO`。
* 遗留问题：B2 已接纳但尚未 commit；C 的真实 collector/readiness/verdict 集成尚未冻结和实现，正式 MVP E2E 继续锁定。
* 备注：A 实现提交 `c2fd17e` 已随 PR #32 合并，merge commit `97ef08f1cae88e8a702069eb0e07c2035b3b063f`。不得据此宣称生产 57/57、多 anchor 已激活或正式 E2E 通过。
