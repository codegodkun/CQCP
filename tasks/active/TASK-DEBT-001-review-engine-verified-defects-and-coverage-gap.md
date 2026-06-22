# TASK-DEBT-001：Review Engine 已确认缺陷与覆盖盲区记录

状态：Active（仅完成标准记录，未批准修复）

类型：父级技术债记录任务 / Codex 主控

优先级：P0

负责人：Codex

创建日期：2026-06-22

来源：`docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`、`tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`、`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`

## 背景

v3 整改计划要求先把已确认问题写入仓库内可追踪记录，再决定后续分批 TASK_SPEC、人工标注和独立审计。本任务仅承载“4 条代码缺陷 + 1 条覆盖盲区”的标准记录、后续分流线索和持续门禁，不修复代码，不派发实现任务。

本任务不是 `TASK-EVAL-001-B` 提交任务，不是 `TASK-EVAL-001` 归档任务，也不是 `TASK-028`、`TASK-031` 或 `TASK-032` 的启动授权。

## 目标

- 用统一七字段模板记录 4 条已确认代码缺陷和 1 条覆盖盲区。
- 为后续只读 Review Intake 提供可定位的源码、测试和 fixture 证据。
- 明确各问题的修复方向、推迟理由和候选目标任务，但不在本轮批准实现。
- 保持 `TASK-EVAL-001-B`、父任务归档和后续开发门禁。

## 非目标

- 不修改生产代码、测试代码、fixture 或 expected JSON。
- 不创建或派发 Claude Code / DeepSeek 实现 TASK_SPEC。
- 不提交 `TASK-EVAL-001-B`，不归档 `TASK-EVAL-001`。
- 不进入 `TASK-028`、`TASK-031` 或 `TASK-032`。
- 不把本记录中的候选目标任务视为已批准实施。

## Task Context

### Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- 本任务文件
- `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
- `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java`

### Optional Context

- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedEvidenceOverlapBaselineTest.java`
- `packages/test-fixtures/expected/*.json`
- `packages/test-fixtures/README.md`

### Out of Scope

- 生产代码、测试代码、fixture、expected JSON、OpenAPI、数据库迁移、Docker / Compose。
- `PRD.md`、`docs/ARCHITECTURE.md`、ADR / `decisions/`。
- 任意实现、提交、归档或后续开发任务。

## 标准问题记录

### DEBT-001-01：`resolveTextEvidence()` 三个候选信号硬编码

**Finding**

`resolveTextEvidence()` 在构造 PARTY_A / PARTY_B 文本候选时，把 `roleLabelSignal`、`valueFormatSignal`、`blockAttributionSignal` 全部硬编码为 `true`，没有分别基于真实命中条件计算。

**验证**

静态源码证据：`apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java:185` 定义 `resolveTextEvidence()`；第 210–217 行调用 `candidateForBlock(...)` 时连续传入 `true, true, true`。该结论来自真实源码，不来自 `CURRENT_CONTEXT.md`。

**影响**

候选缺少真实 role label、值格式不满足要求或 block 归属不可证明时，仍可能被赋予完整信号，导致 `CandidateResolver` 置信度输入失真，并掩盖非 `HIGH` 情形。

**修复方向**

分别基于真实 role label / alias 命中、非空且合法的文本值、当前 block 与 anchor 可用性计算三个信号；非 `HIGH` 候选继续不得进入确定性裁判。

**推迟理由**

本轮是 v3 Step 1 标准记录，不执行代码修复。该问题应作为首批角色分离试点，在冻结 TASK_SPEC、编码前规格映射和独立抽查门禁下处理。

**目标任务**

待后续只读 Review Intake 定界为第一批局部修复 TASK_SPEC；当前未创建、未批准、未派发实现。

**当前 fixture 覆盖**

现有 PARTY_A / PARTY_B 正负向 fixture 验证最终 evidence 结果，但没有针对三个信号分别为 `false` 的可证伪回归断言；当前覆盖不足以证明信号计算正确。

### DEBT-001-02：`collectPatternCandidates()` 的 `valueFormatSignal` 硬编码

**Finding**

`collectPatternCandidates()` 已计算 `roleLabelSignal`，但传给 `candidateForMatch(...)` 的 `valueFormatSignal` 固定为 `true`，没有根据匹配值的真实格式条件计算。

**验证**

静态源码证据：`ParserBackedReviewInputPreparer.java:377` 定义 `collectPatternCandidates()`；第 386 行计算 `roleLabelSignal`，第 397–406 行构造候选时依次传入 `roleLabelSignal, true, roleLabelSignal`。该结论来自真实源码。

**影响**

只要正则产生非空捕获值，格式信号就被视为满足；格式不合法或语义不足的候选可能获得高于真实证据质量的置信度输入。

**修复方向**

依据对应候选类型的真实格式规则计算 `valueFormatSignal`，并与第一批 `resolveTextEvidence()` 的信号语义保持一致；如存在路径差异，必须给出可验证理由。

**推迟理由**

该问题与 DEBT-001-01 属同一抽象根因，但 v3 要求分批处理并核查同根因一致性。本轮只登记，不与第一批修复混合。

**目标任务**

待第一批修复完成并经 Codex 与独立 agent 审查后，再通过第二批冻结 TASK_SPEC 定界；当前未批准实现。

**当前 fixture 覆盖**

现有 ratio / amount fixture 覆盖候选输出和最终状态，但没有构造“正则可匹配、值格式不合法”并断言 `valueFormatSignal=false` 的专项覆盖。

### DEBT-001-03：parser provenance 被常量覆盖

**Finding**

`PointEvidence` 的 `sourceOrigin`、`sourceExtractionMode`、`contextType` 由类级常量 `SOURCE_ORIGIN`、`SOURCE_EXTRACTION_MODE`、`CONTEXT_TYPE` 写入，没有从选中候选或 parser block 的真实 provenance 透传。

**验证**

静态源码证据：`ParserBackedReviewInputPreparer.java:20-22` 固定定义 `NATIVE_WORD`、`STRUCTURED`、`NORMAL`；第 619–626 行和第 659–666 行在 confirmed / unresolved evidence 构造中无条件写入这些常量。parser block 本身具备 `SourceOrigin`、`SourceExtractionMode`、`ContextType` 字段，但当前 evidence 构造未使用它们。

**影响**

真实来源、抽取模式或上下文类型可能在进入 `PointEvidence / SourceAnchor` 时丢失，影响 provenance 审计、诊断解释和后续不同来源证据的治理。

**修复方向**

先冻结 provenance 数据契约和透传边界，再从真实 parser block / candidate 传递来源字段；必须评估对 SourceAnchor、快照兼容和诊断证据的影响。

**推迟理由**

该问题涉及 parser provenance 和结果契约，风险高于局部 signal 修复；不适合作为首个角色分离试点，也不得在 Step 1 顺手修改。

**目标任务**

候选为独立 parser provenance task，或在后续 `TASK-032` Review Intake 中判定是否拆出；当前两者均未获实现授权。

**当前 fixture 覆盖**

现有测试多以 `NATIVE_WORD / STRUCTURED / NORMAL` 构造或断言，未覆盖其他 provenance 值从真实 parser 输出贯穿到最终 evidence 的场景。

### DEBT-001-04：`resolveRatioEvidence()` early return 跳过 fallback 候选

**Finding**

除预付款审核点外，只要 `semanticCandidates` 非空，`resolveRatioEvidence()` 会立即返回，后续 direct pattern、whole-text / fallback 候选收集不会执行。

**验证**

静态源码证据：`ParserBackedReviewInputPreparer.java:261-268` 收集 semantic candidates 后，在非预付款且集合非空时直接 `return resolveFromCandidates(...)`；第 270 行之后才开始合并 pattern 和后续候选。控制流明确证明该条件成立时 fallback 路径被跳过。当前记录不把既有项目记忆当作验证证据。

**影响**

semantic 候选与 fallback 候选存在冲突时，resolver 无法看到完整候选集，可能把本应 `CONFLICTED` 的结果误判为较高置信度。

**修复方向**

重构为“全部候选收集 → 合并去重 → 统一送入 resolver”，消除基于 semantic 非空的提前返回，并补充语义候选与 fallback 候选冲突回归测试。

**推迟理由**

该修改会牵动 ratio 候选收集 pipeline，并与 `ParserBackedReviewInputPreparer` 后续拆分重构存在边界重叠；必须先通过 Review Intake 决定独立任务还是纳入 `TASK-032`。

**目标任务**

候选为独立 candidate collection pipeline task，或后续 `TASK-032` 的冻结子任务；当前不得进入 `TASK-032`，也未批准独立实现。

**当前 fixture 覆盖**

现有 4 正 4 负主 fixture 验证最终结果，但未覆盖“semantic 与 fallback 同时产生不同值并要求 resolver 判为冲突”的专项场景。

### DEBT-001-05：TABLE_CELL 真实 DOCX 覆盖盲区

**Finding**

类型：覆盖盲区，不是已确认代码 bug。BLOCK、TABLE_ROW 已有真实 DOCX 覆盖；TABLE_CELL 当前仅有 test-only / parser-backed case，没有真实主 `.docx` fixture 端到端触发。

**验证**

真实 fixture 的 expected evidence case 使用 `BLOCK:*` 或 `TABLE_ROW:*`；例如 `packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json:59` 和 `CQCP-MVP-DOCX-003.json:59` 为 TABLE_ROW。TABLE_CELL 自动化覆盖来自 `ParserBackedEvidenceOverlapBaselineTest.java:244-265` 中程序化构造的 `WordParserSpikeDocument`，以及 evaluator / result composition 等 test-only case。父任务 `TASK-EVAL-001` 验收标准第 7 条只要求自动化覆盖 table row/cell，没有要求真实 DOCX cell fixture。

**影响**

当前可以证明 TABLE_CELL canonical key、匹配和错误归因逻辑可运行，但不能证明真实 DOCX 的 cell 位置在端到端 parser 链路中客观正确，也不得宣称真实 DOCX TABLE_CELL 已验证。

**修复方向**

先由人工标注者在不参考 parser 输出的前提下完成至少一个真实 `.docx` table-cell anchor 标准答案，再由冻结 TASK_SPEC 将人工答案转换为 fixture / expected JSON / 测试，并由独立 agent 核查不存在 parser 输出倒填 expected。

**推迟理由**

该项依赖人工 anchor 标注，不属于代码缺陷修复。父任务 DoD 不要求真实 DOCX cell，因此该覆盖缺口不阻塞 `TASK-EVAL-001` 归档判断，但必须显式保留为后续补强项。

**目标任务**

待人工标注完成后新建独立 TABLE_CELL coverage task；当前未创建实现任务，且不得用 `TASK-EVAL-001-B` 提交替代该补强。

**当前 fixture 覆盖**

BLOCK、TABLE_ROW：已有真实 DOCX fixture 覆盖。TABLE_CELL：仅 test-only / parser-backed case 覆盖；没有独立人工标注驱动的真实 DOCX 端到端 fixture。

## 约束与门禁

- Step 1 只登记，不修复。
- `TASK-DEBT-001` 建立不代表任何修复任务获准启动。
- 不提交 `TASK-EVAL-001-B`。
- 不归档 `TASK-EVAL-001`。
- 不进入 `TASK-028`、`TASK-031`、`TASK-032`。
- 不派发 Claude Code / DeepSeek 实现任务。
- 不以 `CURRENT_CONTEXT.md` 作为缺陷或完成状态的验证凭证。
- 后续任何实现必须先经过独立的只读 Review Intake、冻结 TASK_SPEC 和编码前规格映射门禁。

## 交付物

- 本任务中的五条标准问题记录。
- `TASK-EVAL-001` 的评测指标解释边界。
- `CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md` 和当月 changelog 的摘要与引用。

## 验收标准

1. 五条问题全部存在，且每条包含 Finding、验证、影响、修复方向、推迟理由、目标任务、当前 fixture 覆盖。
2. 前四条明确为代码缺陷，第五条明确为覆盖盲区而非代码 bug。
3. TABLE_CELL 记录完整说明真实 DOCX 覆盖现状、父任务 DoD 边界和人工标注前置。
4. 未把任何候选目标任务写成已批准实现。
5. `TASK-EVAL-001-B`、父任务归档和 `TASK-028 / TASK-031 / TASK-032` 门禁保持不变。
6. 本轮只修改授权的五个文档路径，暂存区保持为空。

## 测试与验证

本任务不运行代码测试。文档写入后仅执行：

- `git diff --check`
- `git diff --name-status`
- `git diff --stat`
- `git status --short`
- `git status -sb`
- `git diff --cached --name-status`
- 使用 `rg` 检查五条记录、七字段、TABLE_CELL 定性、指标解释和门禁。

## 文档更新要求

- `CURRENT_CONTEXT.md`：写入 active 债务记录任务、摘要和门禁。
- `tasks/MVP_TASK_MAP.md`：登记任务地图位置与候选分流，不登记实现授权。
- `changelog/2026-06.md`：记录 Step 1 文档落地。
- `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`：只补充指标解释和 TABLE_CELL 覆盖边界。
- 不更新 PRD、ARCHITECTURE 或 ADR。

## Next Task Handoff

本任务当前只完成记录阶段。下一步必须先执行只读 Review Intake，决定五条问题的分批顺序、人工标注前置和 TASK_SPEC 边界；当前不生成实现 Handoff Prompt。
