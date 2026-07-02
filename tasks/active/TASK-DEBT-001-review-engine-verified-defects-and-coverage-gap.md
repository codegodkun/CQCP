# TASK-DEBT-001：Review Engine 已确认缺陷与覆盖盲区记录

状态：Active（Step 1 标准记录与非 Git Review Intake 已完成；Step 3 第一批 TASK_SPEC-DEBT-001-A 已实现并经 Codex 审查、提交前独立只读审计通过，已 commit / push 为 `3223d6760a977fe9deaf722e63b50bcbb6ce3611`；2026-06-27 post-push 独立只读复审为 GO；第二批 TASK_SPEC-DEBT-001-B 实现 PR #4 已合并到 `master`；第三批 TASK_SPEC-DEBT-001-C 实现 PR #13 已合并到 `master`，merge commit 为 `15888df4f0e89882814940c5eca0fc948fd1fef0`；父任务归档前独立只读审计为 GO，Codex Review Intake Decision 为 `GO TO ARCHIVE WITH CONDITIONS`；父任务未归档，未进入下一任务）

类型：父级技术债记录任务 / Codex 主控

优先级：P0

负责人：Codex

创建日期：2026-06-22

来源：`docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`、`tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`、`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`

## 背景

v3 整改计划要求先把已确认问题写入仓库内可追踪记录，再决定后续分批 TASK_SPEC、人工标注和独立审计。本任务承载“4 条代码缺陷 + 1 条覆盖盲区”的标准记录、后续分流线索和持续门禁。2026-06-26 起，第一批 `TASK_SPEC-DEBT-001-A` 已作为角色分离试点完成实现、审查、commit 与 push；2026-06-27 GitHub 云端 post-push 独立只读复审确认实现提交 `3223d6760a977fe9deaf722e63b50bcbb6ce3611` 和文档修正提交 `3b35e2728143ce8f6c89bcc74cb1cb7fb469d973` 均在远端 `master`，上一轮 `NEEDS-FIX` 的文档过期问题已消除，复审结论为 `GO`。第二批 `TASK_SPEC-DEBT-001-B` 实现 PR #4 已合并到 `master`，merge commit 为 `da724ba49c6a33347641950101a84a84dfa8c000`，PR head commit 为 `6513e669ac251f881c4d06ea2574ce6e9c7c9d69`，审计依据为 `CQCP_AUDIT GO`。第三批 `TASK_SPEC-DEBT-001-C` 实现 PR #13 已合并到 `master`，merge commit 为 `15888df4f0e89882814940c5eca0fc948fd1fef0`。2026-07-02 父任务归档前独立只读审计结论为 `GO`，Codex Review Intake Decision 为 `GO TO ARCHIVE WITH CONDITIONS`。父任务仍保持 active，未归档，未进入 parser provenance、real DOCX `TABLE_CELL`、`TASK-028`、`TASK-031` 或 `TASK-032`。

本任务不是 `TASK-EVAL-001-B` 提交任务，不是 `TASK-EVAL-001` 归档任务，也不是 `TASK-028`、`TASK-031` 或 `TASK-032` 的启动授权。

## 目标

- 用统一七字段模板记录 4 条已确认代码缺陷和 1 条覆盖盲区。
- 为后续只读 Review Intake 提供可定位的源码、测试和 fixture 证据。
- 明确各问题的修复方向、推迟理由和候选目标任务，但不在本轮批准实现。
- 保持 `TASK-EVAL-001-B`、父任务归档和后续开发门禁。

## 非目标

- 不修改生产代码、测试代码、fixture 或 expected JSON。
- 不创建或派发 Claude Code / DeepSeek 实现 TASK_SPEC。
- 不提交新的 `TASK-EVAL-001-B` 代码、测试、fixture 或 expected JSON 变更，不归档 `TASK-EVAL-001`。
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

第一批局部修复 TASK_SPEC 为 `tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md`。2026-06-27 状态：Claude Code / DeepSeek 已完成授权范围内实现；Codex Review Intake Decision 为 `A. 可以合并`；提交前独立只读审计结论为 `GO`；已 commit / push 为 `3223d6760a977fe9deaf722e63b50bcbb6ce3611 fix(reviewengine): compute text evidence confidence signals`；后续文档修正已 commit / push 为 `3b35e2728143ce8f6c89bcc74cb1cb7fb469d973 docs(task): record A post-push audit and draft B spec`。GitHub 云端 post-push 独立只读复审结论为 `GO`，可作为 B 批次冻结前置审计结论使用。

**当前 fixture 覆盖**

已新增专项测试覆盖文本候选 `valueFormatSignal=false`、`blockAttributionSignal=false`、纯数字降级和字母数字有效值确认；测试只证明代码路径和回归行为，不声明真实合同 anchor 独立正确性。现有 PARTY_A / PARTY_B 正负向 fixture 回归仍通过。

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

第一批 `TASK_SPEC-DEBT-001-A` 已完成 Codex 审查、提交前独立只读审计、commit 与 push，且 2026-06-27 post-push 独立只读复审为 `GO`。第二批规格为 `tasks/active/TASK_SPEC-DEBT-001-B-collect-pattern-candidates-value-format-signal.md`，用于定界 `collectPatternCandidates()` 的 `valueFormatSignal` 修复；实现 PR #4 已合并到 `master`，merge commit 为 `da724ba49c6a33347641950101a84a84dfa8c000`，PR head commit 为 `6513e669ac251f881c4d06ea2574ce6e9c7c9d69`。第三批 `tasks/active/TASK_SPEC-DEBT-001-C-resolve-ratio-evidence-candidate-collection-topology.md` 已完成 `resolveRatioEvidence()` 候选收集拓扑修复，PR #13 已合并到 `master`，merge commit 为 `15888df4f0e89882814940c5eca0fc948fd1fef0`。父任务仍未归档，后续 provenance 和 TABLE_CELL 分流仍未获实现授权。

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

- Step 1 已完成登记；第一批 `TASK_SPEC-DEBT-001-A` 已在用户授权、编码前映射计划、Codex 放行、实现后 Codex 审查和独立只读审计门禁下完成。
- `TASK-DEBT-001` 建立以及 A 批次通过不代表其他修复任务获准启动。
- 不提交新的 `TASK-EVAL-001-B` 代码、测试、fixture 或 expected JSON 变更。
- 不归档 `TASK-EVAL-001`。
- 不进入 `TASK-028`、`TASK-031`、`TASK-032`。
- 不派发新的 Claude Code / DeepSeek 实现任务，除非先冻结对应 TASK_SPEC 并完成编码前规格映射计划与 Codex 放行。
- 不以 `CURRENT_CONTEXT.md` 作为缺陷或完成状态的验证凭证。
- 后续任何实现必须先经过冻结 TASK_SPEC、编码前规格映射计划、Codex 放行和实现后审查门禁；当前仅 A 批次已通过，B 批次及其他分流仍未获实现授权。

## 交付物

- 本任务中的五条标准问题记录。
- `TASK-EVAL-001` 的评测指标解释边界。
- `CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md` 和当月 changelog 的摘要与引用。

## 验收标准

1. 五条问题全部存在，且每条包含 Finding、验证、影响、修复方向、推迟理由、目标任务、当前 fixture 覆盖。
2. 前四条明确为代码缺陷，第五条明确为覆盖盲区而非代码 bug。
3. TABLE_CELL 记录完整说明真实 DOCX 覆盖现状、父任务 DoD 边界和人工标注前置。
4. 未把任何候选目标任务写成已批准实现。
5. `TASK-EVAL-001-B` 已事后条件接纳；不得提交新的 B 代码、测试、fixture 或 expected JSON 变更，父任务归档和 `TASK-028 / TASK-031 / TASK-032` 门禁保持不变。
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

## TASK-EVAL-001-B 事后接纳状态

- Git 历史显示 `TASK-EVAL-001-B` 对应 commit 为 `672d97f695756249a871da53ad2821eb5146997f`；据用户提供的外部报告摘要，该提交已 push，且提交前独立复核流程曾缺失。
- 据用户提供的独立 agent 事后复核报告摘要，复核建议为 `ACCEPT WITH CONDITIONS`；定向测试复跑报告摘要的基线为 `CQCP_AUDIT` clean clone、HEAD `829796f2a18a87f1155eea96ed991a5fd0748b99`，四组测试合计 `30/30 PASS`，测试前后工作区干净。凭证应以独立 agent 原始报告和 console 输出为准，本任务文件仅记录摘要，不作为完成凭证。
- 据 Codex Review Intake 摘要，`TASK-EVAL-001-B` 被接纳为 `ACCEPT WITH CONDITIONS — TEST EVIDENCE SATISFIED`。该接纳仅覆盖 B 子任务，不代表 `TASK-EVAL-001` 父任务可归档；父任务归档前仍须由独立 agent 对原始报告、测试输出、commit 和 diff 再次核验。
- `1.0 / 1.0 / 1` 只证明 parser-backed 输出与 expected JSON 的一致性和回归稳定性。expected blockId、rowIndex、cellIndex 仍依赖 parser 内部稳定标识；candidateValue 来源于独立登记的 matrix；不得据此宣称 parser anchor 位置客观正确。
- evaluator 支持 TABLE_CELL canonical key，test-only / mock 覆盖已存在；真实 DOCX positive baseline TABLE_CELL 覆盖仍未完成，继续由本任务和后续人工 anchor 标注任务追踪。

## 后续治理缺口

- 当前 v3 门禁仍依赖文档规则、Codex 遵守、用户判断和独立 agent 审计，尚未通过 GitHub branch protection / required status checks 形成机制化硬门禁，当前门禁不具备 GitHub 机制强制能力。
- 后续建议单独建立 `TASK-GOV-004`，评估 CI、Code Review Agent、Spec & Docs Review Agent required checks；review agent 判决以 GitHub Check Run 或 Commit Status 发布；required checks 指定可信 GitHub App / source；default branch 未满足 required checks 时禁止 merge；管理员 bypass 关闭或单独审计。
- `TASK-GOV-004` 尚未创建、未 active、未批准、未实施，required checks 未配置、branch protection 未生效。本轮只记录治理线索，不修改 CI、GitHub Actions、branch protection 或仓库设置。

## 非 Git Review Intake Decision（2026-06-22）

**Decision：先执行 Step 2，不提前准备 Step 3。**

判断依据：

1. `TASK-GOV-003` 已完成独立审计、Review Intake、push 和远程同步，并已归档至 `tasks/done/`；本轮无需重复归档，也不再检查 Git。
2. v3 执行者矩阵和执行清单均将 Step 2 排在 Step 3 之前：先由独立 agent 完成 `CURRENT_CONTEXT.md` 逐条认领审计，再由 Codex 编写第一批 `resolveTextEvidence` TASK_SPEC。
3. v3 第 8.4 节说明 Step 2 不构成单点 TASK_SPEC 试点的硬阻塞，但这只代表“允许并行”，不代表存在应当打破正式顺序的理由。
4. 当前没有紧急修复授权、实现窗口或其他必须抢跑 Step 3 的证据。保持 Step 2 → Step 3 可先建立可信项目记忆基线，减少 TASK_SPEC 引用未核验完成声明的风险。

执行边界：

- 下一事项为 Step 2，由独立 agent 只读执行；Codex 不代做认领审计。
- 【2026-06-25 更新】经用户明确授权，已进入 Step 3 的规格起草阶段，并创建 `tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md`。原先“本轮不创建、不起草、不冻结”的限制不再适用于规格起草，但仍适用于代码实现和任务派发。
- 【2026-06-26 更新】`TASK_SPEC-DEBT-001-A` 已完成编码前映射计划、Codex 放行、Claude Code / DeepSeek 实现、Codex 实现审查、提交前独立只读审计、commit 与 push；提交为 `3223d6760a977fe9deaf722e63b50bcbb6ce3611 fix(reviewengine): compute text evidence confidence signals`。后续文档修正提交为 `3b35e2728143ce8f6c89bcc74cb1cb7fb469d973 docs(task): record A post-push audit and draft B spec`；2026-06-27 GitHub 云端 post-push 独立只读复审结论为 `GO`。
- 【2026-06-26 更新】`TASK_SPEC-DEBT-001-B` 已创建 Draft：`tasks/active/TASK_SPEC-DEBT-001-B-collect-pattern-candidates-value-format-signal.md`。该 Draft 仅用于后续审查和冻结，不代表 B 批次已获实现授权。
- `TASK_SPEC-DEBT-001-B` 实现 PR #4 已合并到 `master`；不得把 B 批次合并扩展为父任务归档、后续债务修复授权或 `TASK-028` / `TASK-031` / `TASK-032` 启动授权。
- `TASK-EVAL-001-B` 已事后条件接纳；不提交新的 B 代码、测试、fixture 或 expected JSON 变更，`TASK-EVAL-001` 归档以及 `TASK-028 / TASK-031 / TASK-032` 门禁保持不变。

## TASK_SPEC-DEBT-001-B post-merge 记录（2026-06-27）

- PR #4 已合并，`TASK_SPEC-DEBT-001-B` 实现 PR 已收口。
- merge commit：`da724ba49c6a33347641950101a84a84dfa8c000`。
- PR head commit：`6513e669ac251f881c4d06ea2574ce6e9c7c9d69`。
- 修复范围：`ParserBackedReviewInputPreparer.collectPatternCandidates()` 的 pattern 候选 `valueFormatSignal` 计算，不扩展到 parser provenance、ratio early return、TABLE_CELL、OpenAPI、数据库、Docker 或下一任务。
- 审计依据：`CQCP_AUDIT GO`。
- CI 状态：临时豁免，原因是当前无 GitHub Actions workflow / checks 可运行；不得写成 CI PASS。
- 父任务状态：`TASK-DEBT-001` 未归档。
- 后续任务状态：未进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 后续治理候选：`TASK-GOV-004 Phase 3：GitHub Actions CI minimal setup`，仅作为候选 / 待 Review Intake；不得写成已启动、已批准或已实施。

## TASK_SPEC-DEBT-001-C implementation record（2026-07-01）

- `TASK_SPEC-DEBT-001-C resolveRatioEvidence candidate collection topology` has completed controlled implementation on branch `codex/task-debt-001-c-implementation`.
- Scope was limited to:
  - `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java`
  - `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`
- Implementation removed the non-prepayment semantic-candidate early return in `resolveRatioEvidence()` and sends the complete ordered candidate set through `resolveFromCandidates(...)` / `MinimalCandidateResolver`.
- Candidate topology is now semantic candidates, direct pattern candidates, whole-text fallback candidates, role-block fallback only when the previous set is empty, and weak fallback only when still empty and non-prepayment.
- Added parser-double regression coverage for `MONTHLY` progress payment conflict: semantic `70%` plus whole-text fallback `85%` returns `AMBIGUOUS / CONFLICTED / SYS_ROLE_CONFLICT`.
- Required tests run from `apps/api-server`:
  - `gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"`: `BUILD SUCCESSFUL`.
  - `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"`: `BUILD SUCCESSFUL`.
  - `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest"`: `BUILD SUCCESSFUL`.
- Codex Review Intake Decision: `GO`.
- Independent read-only audit decision: `GO`, no blocking findings.
- Parent task remains `Active`; this C batch does not archive `TASK-DEBT-001` and does not authorize parser provenance, real DOCX `TABLE_CELL`, `TASK-028`, `TASK-031` or `TASK-032`.

## Parent pre-archive independent audit and Codex Review Intake（2026-07-02）

- Independent read-only audit conclusion: `GO`.
- Audit baseline: `master` at `15888df`; local `HEAD` and `origin/master` aligned with `origin/master...HEAD = 0 0`; working tree clean.
- Evidence accepted:
  - A batch landed via commit `3223d6760a977fe9deaf722e63b50bcbb6ce3611` and post-push independent read-only review `GO`.
  - B batch landed via PR #4, merge commit `da724ba49c6a33347641950101a84a84dfa8c000`, audit basis `CQCP_AUDIT GO`.
  - C batch landed via PR #13, merge commit `15888df4f0e89882814940c5eca0fc948fd1fef0`, Codex Review Intake `GO` and independent read-only audit `GO`.
- Codex Review Intake Decision: `GO TO ARCHIVE WITH CONDITIONS`.
- Conditions before archival Git flow:
  - Align stale status text in `tasks/MVP_TASK_MAP.md`, `CURRENT_CONTEXT.md` and `TASK_SPEC-DEBT-001-C`.
  - Keep `DEBT-001-03` parser provenance / `SourceAnchor` as unresolved and unauthorized debt.
  - Keep `DEBT-001-05` real DOCX `TABLE_CELL` coverage as dependent on independent manual anchor annotation.
  - Continue to prohibit parser provenance / `TABLE_CELL` implementation and `TASK-028` / `TASK-031` / `TASK-032`.
- This section records the pre-archive gate result only; it does not move this file to `tasks/done/` and does not itself perform archival.

## Next Task Handoff

`TASK_SPEC-DEBT-001-B collectPatternCandidates valueFormatSignal 修复` 的实现 PR #4 已合并到 `master`，merge commit 为 `da724ba49c6a33347641950101a84a84dfa8c000`，PR head commit 为 `6513e669ac251f881c4d06ea2574ce6e9c7c9d69`。CI 状态为临时豁免，原因是当前无 GitHub Actions workflow / checks 可运行；不得写成 CI PASS。`TASK-DEBT-001` 父任务仍为 active 且未归档，未进入 `TASK-028` / `TASK-031` / `TASK-032`。后续治理候选 `TASK-GOV-004 Phase 3：GitHub Actions CI minimal setup` 仅待 Review Intake，不得写成 active / approved / implemented。
