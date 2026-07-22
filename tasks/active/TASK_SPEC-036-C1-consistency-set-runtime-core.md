# TASK SPEC — TASK_SPEC-036-C1 consistency-set runtime core

> **版本**：v0.3
> **状态**：IMPLEMENTATION_ACCEPTED_AND_COMMITTED / INDEPENDENT_IMPLEMENTATION_AUDIT_GO / C1_INACTIVE_RUNTIME_CORE_ONLY / C2_AWAITING_SEPARATE_AUTHORIZATION
> **创建日期**：2026-07-18
> **起草**：Codex
> **执行环境**：Claude Code（DeepSeek 模型）
> **TASK_SPEC 类型**：execution
> **父任务**：TASK-036
> **关联 ADR**：ADR-016（Accepted）
> **前置批次**：TASK_SPEC-036-A、TASK_SPEC-036-B1、TASK_SPEC-036-B2
> **所在分支**：`codex/task-036-consistency-set-runtime`
> **冻结基线**：`95943448d101570aa1874c791dd6c20134f391a1`

## 0. 任务摘要与执行门禁

本批实现**未接入生产 execution 的 consistency-set 全量候选扫描、parser scope observability、readiness admission 与 point-local deterministic verdict 核心**。输入只能来自 B2 已验证的 `RuntimeRuleSetSnapshot`、带 scope coverage report 的完整 parser document 和逐 block scan ledger；输出复用现有 `PointEvidence / PointEvidenceOccurrence / PointReviewResult / SourceAnchorSummary` 链路。

C1 不修改 `TaskExecutionStateMachine`、`RuleSetActivationGate`、`VersionReferences`、结果 API 或持久化，不允许任何生产调用传入 `consistencyRuntimeReady=true`。C1 的新路径只能通过显式携带 runtime snapshot 的内部构造或单元测试调用；snapshot 为空时必须逐字节保持 legacy resolver / single-anchor 行为。

执行门禁：

1. 本规格必须先经过独立 agent 只读规格审计，结论为 `GO` 且无 blocking finding。
2. Claude Code / DeepSeek 修改任何代码前，必须提交“编码前规格映射计划”并停止。
3. 只有 Codex 明确给出 `GO / IMPLEMENTATION AUTHORIZED` 后才能编码。
4. 执行方不得 commit、push、切换分支或修改本规格。
5. C1 实现接纳不自动解锁 C2 或正式 MVP E2E。

## 0.1 同根因分批一致性

与 A/B1/B2 一致：

- occurrence provenance 必须在任何 identity 去重、canonical grouping 或 selected-candidate 投影前保留。
- 只有 `cardinalityMode=CONSISTENCY_SET` 且携带 B2 runtime snapshot 的点才走本批逻辑。
- scope、strong exclusion、required attribution、canonicalization/unit、anchor identity 和双预算只从不可变 policy snapshot 读取，不在 Java 中复制九点策略矩阵。
- legacy resolver 的 `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN` 语义不变。
- 可靠异值与归属歧义分流：前者是 point-local 业务 `ERROR`；后者是 `SYS-* / NOT_CONCLUDED`。

本批差异：

- A 只提供 inactive occurrence carrier；C1 首次构造真实多 occurrence `PointEvidence`。
- B1/B2 只提供 policy 与 fail-closed loader/gate；C1 首次消费 policy 计算 readiness，但仍不接入 execution。
- C1 可以扩充 package-private candidate 与 review input carrier，但不得增加外部 API 字段、数据库字段或公开枚举。
- C1 可以为 `WordParserSpikeDocument` 追加内部 parser coverage component 和兼容构造器；不得改变既有五参数构造调用、legacy blocks 内容或公开 HTTP/JSON 契约。

## 0.2 编码前规格映射计划

执行方必须逐项给出真实方法、字段、输入来源和测试，不得只复述结论：

```text
1. 文件与构造器影响：列出现有 EvidenceCandidate / ReviewEngineInput 构造器调用数量、兼容策略和不改变 legacy 的证明。
2. 完整扫描证据：说明 WordParserSpikeDocument.ParseQualityReport、ScopeCoverageReport、全部 blocks、ConsistencyCandidateBatch 和逐 block scan ledger 如何进入 collector；不得把 rawCandidates 非空等同于完整扫描。
3. scope/exclusion：说明 BODY/APPENDIX、TOC、header/footer、tracked deletion、strike/double-strike voided content 与两个 PARTY_A semantic context 的真实扫描、排除记录和 fail-closed 条件。
4. attribution：逐项映射 SOURCE_CONFIDENCE、PARSE_CONFIDENCE、VALUE_GRAMMAR、ROLE_LABEL、REGION_CONTEXT、ANCHOR_IDENTITY 的真实字段。
5. occurrence identity：分别说明 BLOCK、TABLE_CELL identity，TABLE_ROW 无精确 cell ref 的失败路径，同 identity 同值/异值的处理。
6. canonicalization：TEXT、DECIMAL/CNY、DECIMAL/PERCENT 的精确输入、输出和拒绝条件；证明不存在 0.7 -> 70。
7. 双预算：说明 identity 去重、canonical grouping、maxCandidates、occurrenceBudget 的计算顺序和边界测试。
8. verdict：说明 READY 单值、多值、税额点、MILESTONE SKIPPED、readiness 失败的 engine 分支顺序。
9. 明确不修改：状态机、gate、B1/B2 资产、API、DB、fixture、expected、正式 E2E。
10. 测试清单：列出每个 §8 断言对应的测试方法或参数化矩阵。
```

## 1. 文件边界

允许修改或新增：

- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ConsistencySetCollector.java`（新增）
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ConsistencyCandidateCollector.java`（新增）
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/WordParserSpikeDocument.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/DocxWordParserSpike.java`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ConsistencySetCollectorTest.java`（新增）
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ConsistencyCandidateCollectorTest.java`（新增）
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolverTest.java`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngineTest.java`
- `apps/api-server/src/test/java/com/cqcp/apiserver/wordparser/DocxWordParserSpikeTest.java`

允许只读：

- `RuntimeRuleSetLoader.java`、`RuntimeRuleSetSnapshot.java`、`ConsistencyPolicySnapshot.java`、`RuleSetActivationGate.java` 及其测试。
- `ResultComposer.java`、`TaskExecutionStateMachine.java` 及相关测试。
- B1 两个 JSON、review-assets validator/tests、A/B1/B2 规格、父 TASK、ADR-014/015/016、ARCHITECTURE、PRD 指定章节。

禁止修改：

- `TaskExecutionStateMachine.java`、`RuleSetActivationGate.java`、`RuntimeRuleSetLoader.java`、三个 B2 snapshot/loader 类及其测试。
- `packages/review-assets/**`、`scripts/validate-review-assets*`、构建脚本、Docker、compose、workflow。
- 除上述三个 parser 路径外的 parser、`ResultComposer.java`、OpenAPI、数据库、migration、fixture、expected JSON、DOCX、XLSX、matrix、outputs。
- ADR、ARCHITECTURE、PRD、父 TASK 和项目记忆；接纳后的 Memory Writeback 由 Codex另行处理。

发现必须修改禁止路径才能完成 C1 时必须 STOP，由 Codex判断是否重拆规格。

## 2. 冻结接口

### 2.1 Parser scope coverage contract

`WordParserSpikeDocument` 在现有五个 component 尾部追加：

```java
ScopeCoverageReport scopeCoverageReport
```

并保留现有五参数兼容构造器。兼容构造器必须生成 `verified=false` coverage；只有 `DocxWordParserSpike` 新生产路径可生成 verified report。不得把缺失 report 或兼容构造器输入当作已完整扫描。

```text
ScopeCoverageReport
- verified: boolean
- handledStrongContextTypes[]
- excludedSourceRegions[]
- unresolvedSignals[]

ExcludedSourceRegion
- contextType: HEADER_FOOTER / DELETED / VOIDED
- blockId?: 仅当排除来源对应 body block
- sourcePart: BODY / HEADER / FOOTER / FOOTNOTE / ENDNOTE / OTHER
- reason: 固定机器 reason，不保存任意自由文本
```

这些是 parser 内部 Java carrier，不进入 HTTP API、结果快照或数据库。集合必须深不可变。

`DocxWordParserSpike` 的 verified report 必须真实执行并记录：

- `TOC`：沿用 `DocumentBlock.contextType=TOC`，全部 TOC block 可被 ledger 观察。
- `HEADER_FOOTER`：枚举 DOCX 全部 header/footer paragraph 与 table；不把其文本加入 legacy `blocks`，但记录已扫描和排除来源。
- `DELETED`：检查 OOXML tracked-deletion content；只排除 tracked-deletion 内容并记录来源。同一 body block 内仍存活的可见文本继续进入 block scan，不得仅因该 block 含删除痕迹而整块排除；无法把删除内容与存活内容稳定区分时写入 `unresolvedSignals` 并 fail-closed。
- `VOIDED`：检查 run strike/double-strike；命中 body block 时整 block 在 consistency scope 中强排除并记录 blockId。不得改变该 block 在 legacy `blocks` 中的既有文本。
- 任一相关 part/XML 无法读取、无法判断或只完成部分扫描时，report 必须为 `verified=false` 或写入非空 `unresolvedSignals`。

禁止仅因为当前 `ContextType` enum 没有某值，就把该 strong exclusion 视为“文档中不存在”。禁止新增或改变 legacy `DocumentBlock.ContextType` 枚举语义。

### 2.2 Full-scan candidate batch

新增 package-private：

```java
final class ConsistencyCandidateCollector {
    ConsistencyCandidateBatch collect(
        ReviewPointCode reviewPointCode,
        String candidateRole,
        WordParserSpikeDocument document,
        ConsistencyPolicySnapshot policy);
}
```

`ConsistencyCandidateBatch` 至少包含：

```text
- rawCandidates[]                 去重前 candidate
- blockScanLedger[]              每个 document block 恰好一条
- rejectedCandidates[]           每个被拒提取及固定 reason
- fullPolicyScopeScanned          只有全部 policy scope block 被扫描时为 true
- handledStrongContextTypes[]     来自 parser report + block ledger
- handledSemanticContextTypes[]   本点实际支持的 semantic classifier id
```

每条 `BlockScanLedger` 必须记录 blockId、region、context、状态和固定 reason；状态限定为 `EXCLUDED / SCANNED_NO_MATCH / CANDIDATE_EMITTED / UNCERTAIN`。任何 policy-scope block 缺 ledger、重复 ledger 或 `UNCERTAIN` 都使 batch 非完整。`SCANNED_NO_MATCH` 只有在该审核点全部冻结 label/context probe、候选 pattern 与 ValueGrammar probe 均对该 block 执行后才能写入；不得把“未运行提取器”记录成 no-match。

consistency collector 必须直接遍历 document 全部 blocks。禁止复用 legacy `paymentClauseBlocks` 切片作为完整扫描输入；禁止在 ledger 前因 label/pattern 不匹配直接 `continue`。legacy build 仍可继续使用当前切片。

PARTY_A 两项 semantic classifier 精确定义为：

- `CONTRACT_TITLE_NAME_MENTION`：block 不含任何 PARTY_A/甲方/发包方角色赋值标签，去除空白后的完整 block 是单一标题短语，包含“合同”且以“合同”结束，不含句号、分号、冒号，长度 2..160；只记录 semantic exclusion，不抽取 candidate value。
- `AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION`：block 不含 PARTY_A 角色赋值标签，同时包含“甲乙双方”“签署”“《”“》”和“合同”；只记录 semantic exclusion，不抽取 candidate value。

上述 classifier 仅在 policy 明确列出同名 id 时执行。policy 列出未知 semantic id、已知 id 无对应 classifier，或 classifier 输入无法确定时，batch 标记 `UNCERTAIN` 并 fail-closed。不得读取 sample id、人工 ground truth 或 fixture 来决定命中。

classifier 实现只接受 `scopeVersion=consistency-scope-v20260715.1`；上述 probe 语义与该 scope version 一一绑定。后续改变任一 probe 字符、结构条件或排除含义时必须发布新的 scope policy version，不得在同一版本下静默改写。C1 不读取 TASK-034 人工排除数据，也不把其 6 条结果作为生产 classifier 输入。

### 2.3 Readiness collector

新增 package-private、无全局 mutable state 的：

```java
final class ConsistencySetCollector {
    PointEvidence collect(
        ReviewPointCode reviewPointCode,
        String candidateRole,
        ConsistencyCandidateBatch candidateBatch,
        WordParserSpikeDocument document,
        ConsistencyPolicySnapshot policy);
}
```

- `candidateBatch` 必须包含每个 document block 的 scan ledger、被拒 candidate 与完整性证明；`rawCandidates` 单独存在时不得进入 READY。
- `document` 提供完整 blocks、parser status、低置信计数和 scope coverage report；collector 不得只凭 candidate 非空推断完整扫描。
- `policy` 必须来自 `RuntimeRuleSetSnapshot.policyMap().get(reviewPointCode)`；缺失或 `cardinalityMode != CONSISTENCY_SET` 时抛内部 `IllegalStateException`，不得回退 legacy。
- collector 返回现有 `PointEvidence`，不新增外部结果 DTO。

### 2.4 EvidenceCandidate 内部信号

在现有 record 尾部追加以下 package-private 信号，并保留全部现有构造器兼容：

```text
contextType             <- DocumentBlock.contextType().name()
sourceOrigin            <- DocumentBlock.sourceOrigin().name()
sourceExtractionMode    <- DocumentBlock.sourceExtractionMode().name()
blockConfidence         <- DocumentBlock.blockConfidence().name()
previewAnchorLevel      <- DocumentBlock.previewAnchorLevel().name()
semanticContextTypes[]  <- extractor 明确识别的稳定 semantic context id；当前未识别时为空列表
```

生产 candidate 必须从其真实 `DocumentBlock` 填充上述字段。不得由 sample id、fixture、candidate value 反向猜测 row/cell 或 semantic context。

### 2.5 ReviewEngineInput 激活载体

在现有 `ReviewEngineInput` 尾部增加可空：

```java
RuntimeRuleSetSnapshot runtimeRuleSetSnapshot
```

- 保留当前五参数构造器，固定委托为 `runtimeRuleSetSnapshot=null`。
- `withEvidenceOverride` 必须保留原 snapshot 引用。
- snapshot 不为空时，其 `policyMap` 必须覆盖全部九点；版本必须是 `v20260715.1`。否则 engine/preparer fail-closed，不走 legacy。
- C1 只允许测试或新的显式 preparer overload 构造非空 snapshot；状态机仍调用旧 build 方法。

### 2.6 Preparer overload

保留现有：

```java
ReviewEngineInput build(TaskExecutionRequest request, EvidenceBuildPlan plan)
```

并新增 package-private：

```java
ReviewEngineInput build(
    TaskExecutionRequest request,
    EvidenceBuildPlan plan,
    RuntimeRuleSetSnapshot runtimeRuleSetSnapshot)
```

- 旧方法逐点继续调用 `MinimalCandidateResolver` 并返回 snapshot=null。
- 新方法逐点调用 `ConsistencyCandidateCollector` 对完整 document 建 batch，再把 batch、document 和对应 policy 交给 readiness collector；不得先按 ordinary resolver 去重或 selected-candidate 投影。
- `TaskExecutionStateMachine` 在 C1 中仍只调用旧方法。

## 3. Collector 行为

### 3.1 固定 admission 顺序

必须按以下顺序对整个点级集合判定；后一步不得掩盖前一步失败：

1. **Policy/snapshot**：版本、九点、cardinality 和当前 point policy 必须完整；失败抛内部异常，不生成 evidence。
2. **Parser fatal coverage**：`ParseStatus.FAILED`。
3. **Parser confidence coverage**：`PARTIAL/LOW_CONFIDENCE`，或三个 low-confidence count 任一非 0。
4. **Scope observability**：`ScopeCoverageReport.verified=true`、无 unresolved signal、完整处理 policy 四类 strong context；candidate batch 每个 block 恰好一条 ledger，`fullPolicyScopeScanned=true`，并处理当前 point 全部 semantic context id。
5. **Scope/exclusion**：只纳入 policy `includedRegionTypes`；TOC、header/footer、deleted、voided 或 semantic classifier 命中时强排除，排除项不计预算且不降低 readiness。
6. **Source lineage**：candidate source origin/extraction mode 与其真实 document block 相同，且分别为 `NATIVE_WORD / STRUCTURED`。
7. **Block confidence**：所有未排除 candidate 的 block confidence 必须为 `HIGH`。
8. **Role label**：`roleLabelSignal=true`。
9. **ValueGrammar**：`valueFormatSignal=true` 且 canonical parser 成功。
10. **Block attribution**：`blockAttributionSignal=true`，candidate role/code/block 与 batch ledger 一致。
11. **Anchor identity**：验证 blockId 与 document block 一致；`previewAnchorLevel=TABLE_CELL` 时必须具有 parser 生成且匹配 `^table:[^/]+/row:[0-9]+/cell:[0-9]+$` 的 ref，并与 candidate 的 tableId/rowIndex/cellIndex 一致。
12. **Identity fold**：同一 occurrence identity 同 canonical value 折为一条；同一 identity 出现不同 canonical value 为归属/证据冲突。
13. **Occurrence budget**：identity fold 后 occurrence 数不得超过 `occurrenceBudget`。
14. **Distinct-value budget**：canonical group 数不得超过 `maxCandidates`。
15. **Empty complete set**：完整扫描后零 occurrence。
16. **READY**：上述全部通过且至少有一个 occurrence时形成 READY evidence。

任何未排除 candidate 的纳入/排除、归属、值语法或 anchor 无法确定时，必须使整个集合降级；不得静默丢弃后继续输出业务结论。

### 3.2 Occurrence identity

```text
BLOCK      = reviewPointCode + "|BLOCK|" + stable blockId
TABLE_CELL = reviewPointCode + "|TABLE_CELL|" + stable blockId + "|" + exact previewElementRef
```

- paragraph/heading 等 `BLOCK_LEVEL` 使用 BLOCK identity。
- parser 标为 `TABLE_CELL` 的 table-row block 只有精确 cell ref 才可参与；仅有 row ref、空 ref、手工拼接 ref或 ref 与真实 table/row/cell 不一致，整个集合为 `SYS_EVIDENCE_BUNDLE_INVALID`。
- 不同 block/cell identity 即使 canonical value 相同也不得合并。
- 没有稳定字符范围时，同一 identity 内重复 mention 不拆为多个 occurrence。

### 3.3 Canonicalization

- `TEXT/NONE`：`strip` 后移除全部 Unicode whitespace 并使用 `Locale.ROOT` 小写；不得改变其他字符。
- `DECIMAL/CNY`：只接受既有 amount ValueGrammar 已判定合法的 ASCII decimal/千分位输入；移除千分位后用 `BigDecimal` 解析，输出 `stripTrailingZeros().toPlainString()`。
- `DECIMAL/PERCENT`：只接受既有 ratio ValueGrammar 已判定合法的百分点输入；允许尾随 `%`，移除 `%` 后按 decimal 规范化；`70%` 与 `70` 同组，`0.7` 保持 `0.7`，禁止乘以 100。
- policy valueType/unit 为其他组合、解析异常、NaN/Infinity 或 grammar signal=false 时 fail-closed，不得选一个候选继续。

`PointEvidenceOccurrence.candidateValue` 保存 canonical value；`evidenceSummary` 和 anchor 继续保存原始 block 文本，避免丢失可解释性。

### 3.4 穷举错误优先级与状态映射

下表自上而下是全局优先级。同一输入存在多个错误时只返回第一项；执行方不得按测试位置、candidate 顺序或实现便利调整优先级。

| 优先级 | 情形 | EvidenceStatus | slotCoverage | diagnostic | notConcludedReason | detail |
|---:|---|---|---|---|---|---|
| 1 | policy/snapshot 版本、九点或 cardinality 缺失/错配 | 不生成 | 不生成 | 抛 `IllegalStateException` | 不适用 | 不适用 |
| 2 | `ParseStatus.FAILED` | `SYSTEM_FAILURE` | `PARTIAL` | `SYS_INDEX_INCOMPLETE` | `EVIDENCE_NOT_FOUND` | `INDEX_MISSING` |
| 3 | `ParseStatus.PARTIAL/LOW_CONFIDENCE` | `AMBIGUOUS` | `LOW_CONFIDENCE` | `SYS_PARSE_LOW_CONFIDENCE` | `PARSE_LOW_CONFIDENCE` | `PARSE_LOW_CONFIDENCE` |
| 4 | 任一 low-confidence region/block/table count 非 0 | `AMBIGUOUS` | `LOW_CONFIDENCE` | `SYS_PARSE_LOW_CONFIDENCE` | `PARSE_LOW_CONFIDENCE` | `PARSE_LOW_CONFIDENCE` |
| 5 | scope report `verified=false`、unresolved、policy strong/semantic context 未处理、ledger 缺失/重复/UNCERTAIN、full scan=false | `SYSTEM_FAILURE` | `PARTIAL` | `SYS_EVIDENCE_BUNDLE_INVALID` | `INTERNAL_RULE_ERROR` | null |
| 6 | candidate source origin 不等于真实 block或不是 `NATIVE_WORD` | `SYSTEM_FAILURE` | `PARTIAL` | `SYS_EVIDENCE_BUNDLE_INVALID` | `INTERNAL_RULE_ERROR` | null |
| 7 | extraction mode 不等于真实 block或不是 `STRUCTURED` | `SYSTEM_FAILURE` | `PARTIAL` | `SYS_EVIDENCE_BUNDLE_INVALID` | `INTERNAL_RULE_ERROR` | null |
| 8 | 未排除 candidate block confidence 非 `HIGH` | `AMBIGUOUS` | `LOW_CONFIDENCE` | `SYS_PARSE_LOW_CONFIDENCE` | `PARSE_LOW_CONFIDENCE` | `PARSE_LOW_CONFIDENCE` |
| 9 | `roleLabelSignal=false` | `AMBIGUOUS` | `AMBIGUOUS` | `SYS_ROLE_CONFLICT` | `EVIDENCE_AMBIGUOUS` | `ROLE_CONFLICT` |
| 10 | `valueFormatSignal=false` 或 canonical parse失败 | `AMBIGUOUS` | `AMBIGUOUS` | `SYS_EVIDENCE_AMBIGUOUS` | `EVIDENCE_AMBIGUOUS` | null |
| 11 | `blockAttributionSignal=false` 或 code/role/block 与 ledger 不一致 | `AMBIGUOUS` | `AMBIGUOUS` | `SYS_ROLE_CONFLICT` | `EVIDENCE_AMBIGUOUS` | `ROLE_CONFLICT` |
| 12 | anchor/ref/document lineage 不可靠 | `SYSTEM_FAILURE` | `PARTIAL` | `SYS_EVIDENCE_BUNDLE_INVALID` | `INTERNAL_RULE_ERROR` | null |
| 13 | 同 occurrence identity 出现不同 canonical value | `AMBIGUOUS` | `AMBIGUOUS` | `SYS_ROLE_CONFLICT` | `EVIDENCE_AMBIGUOUS` | `ROLE_CONFLICT` |
| 14 | identity fold 后 occurrence 数 `> occurrenceBudget` | `SYSTEM_FAILURE` | `BUDGET_TRUNCATED` | `SYS_EVIDENCE_BUDGET_EXCEEDED` | `MODEL_BUDGET_EXCEEDED` | `BUDGET_TRUNCATED` |
| 15 | distinct canonical value 数 `> maxCandidates` | `SYSTEM_FAILURE` | `BUDGET_TRUNCATED` | `SYS_EVIDENCE_BUDGET_EXCEEDED` | `MODEL_BUDGET_EXCEEDED` | `BUDGET_TRUNCATED` |
| 16 | 完整扫描后零纳入 occurrence | `MISSING` | `MISSING` | `SYS_INDEX_INCOMPLETE` | `EVIDENCE_NOT_FOUND` | `INDEX_MISSING` |
| 17 | READY | `CONFIRMED` | `SATISFIED` | null | null | null |

READY evidence：

- `occurrences` 为 identity fold 后、按 document block 顺序和 cellIndex 稳定排序的不可变完整列表。
- 只有一个 canonical value 时，`candidateValue` 为该值。
- 多个 canonical value 时，`candidateValue=null`；distinct values 必须由 occurrences 计算，禁止另建未版本化真源。
- `blockId/sectionPath/region/location/ref` 等 legacy 单值字段可投影第一条 occurrence 仅用于兼容显示；点级 coverage 真源始终为 `occurrences`。

## 4. Engine 行为

### 4.1 激活判定

- `runtimeRuleSetSnapshot == null`：严格执行现有代码路径，不因 `occurrences` 非空自动启用 consistency verdict。
- snapshot 非空：九点都必须有 `CONSISTENCY_SET` policy；缺失或版本错配抛内部异常，不 fallback。
- `SKIPPED` 判定保持在 evidence preflight 和 consistency verdict 之前。
- readiness preflight 失败时输出既有 `NOT_CONCLUDED` 与诊断，不进入任何业务比较。

### 4.2 READY verdict

preflight 通过后：

1. 从 `PointEvidence.occurrences` 取得 distinct canonical values。
2. distinct values 多于 1：直接输出当前审核点业务 `ERROR`、`FindingSeverity.ERROR`、`PointCoverageStatus.COMPLETE` 和全部 occurrence anchors；不得选择其中一个值与结构化字段比较。
3. distinct values 等于 1：将唯一 canonical value用于现有文本/decimal 比较。
4. `TAX_AMOUNT_FORMULA_CONSISTENCY`：先把唯一 evidence tax canonical value 与结构化 `taxAmount` 按金额容差比较；不一致直接 `ERROR`；一致后才执行现有 total/excluded/tax 强公式与可选 taxRate 弱公式。
5. 每个实际参与裁判的 occurrence 生成一个 point-level anchor；只可按 ADR-016 identity 去重，不得按 candidateValue 去重。

不得修改 `GlobalConsistencyCheck`，不得跨审核点覆盖结论。

## 5. 不做范围

- 不在生产代码调用 `RuleSetActivationGate.request(..., true)`。
- 不修改 execution、snapshot version binding、任务创建入口或结果查询。
- 不修改 B1 `DRAFT / NOT_BOUND / false/false/NONE` 资产。
- 不运行正式 TASK-034 E2E，不修改其 v1 证据、63 条人工 occurrence、fixture 或 expected。
- 不新增 API/DB/状态枚举/依赖/Gemma/RAG/模型调用。
- 不声称 C1 已生产激活、57/57 已覆盖或 C2 已解锁。

## 6. 可证伪验收断言

1. snapshot=null 时，现有 resolver、preparer 和 engine 定向测试结果不变；普通 `CONFLICTED` 仍为 `SYS_ROLE_CONFLICT / NOT_CONCLUDED`。
2. 新 build overload 对全部九点执行完整 document scan，生成逐 block ledger、rejection reason 和去重前 raw candidates，再使用 snapshot policy；旧 build 方法仍返回 snapshot=null 且状态机未被修改。
3. 两个不同 BLOCK、两个不同 TABLE_CELL、BLOCK+TABLE_CELL 的同 canonical value分别形成 2 个 occurrences/anchors，不被 value 去重。
4. 同 identity 同 canonical value重复 mention 只形成 1 条；同 identity 异值固定为 `SYS_ROLE_CONFLICT / NOT_CONCLUDED`。
5. TABLE_CELL 缺失 cell ref、只有 row ref、伪造 ref或 table/row/cell lineage 不匹配时固定为 `SYS_EVIDENCE_BUNDLE_INVALID / NOT_CONCLUDED`，无业务 severity。
6. parser scope report 真实扫描 TOC、header/footer、tracked deletion、strike/double-strike voided content；BODY/APPENDIX 候选纳入，四类 strong exclusion 与两个 PARTY_A semantic exclusion 强排除且不计预算；未支持或无法确定 scope 时 fail-closed。
7. parse `FAILED/PARTIAL/LOW_CONFIDENCE`、三个 low-confidence count、source、extraction、block confidence、role label、value grammar、block attribution、anchor、same-identity conflict 分别精确映射为 §3.4 五元组，组合错误按表中第一项返回。
8. identity fold 后正好 64 occurrences 且不超过 8 distinct values可 READY；第 65 条固定 `BUDGET_TRUNCATED`。正好 8 distinct values可 READY；第 9 个 fixed `BUDGET_TRUNCATED`，即使已观察到异值也不得业务 ERROR。
9. TEXT 空白/大小写、CNY 千分位/尾零、PERCENT `70%/70` 形成冻结 canonical group；`0.7` 与 `70` 不同组。
10. READY 单值分别驱动九点既有比较；READY 多可靠值分别对九点输出 point-local `ERROR` 和全部 anchors。
11. 税额点 evidence tax 与结构化 taxAmount 不同先 ERROR；相同后才执行既有强/弱公式。MILESTONE 下四个 monthly-only 点始终 `SKIPPED` 且无 SYS/Finding。
12. `ReviewEngineInput` 旧构造器和 `withEvidenceOverride` 兼容；非空 snapshot 深不可变且版本/九点缺失时 fail-closed。
13. 全仓生产代码不存在 `request("v20260715.1", true)`、`consistencyRuntimeReady=true` 或等价激活调用；`TaskExecutionStateMachine.java` diff 为空。
14. expected 只来自 ADR-016、ARCHITECTURE 和 B1 policy 的冻结断言；测试不得从 collector/engine 实际输出反向生成 expected。
15. 实现 diff 只包含 §1 十三个允许路径，无 fixture、expected、资产、文档或构建路径越界。

## 7. 测试场景

至少包含以下独立或参数化测试：

- parser GOOD/PARTIAL/LOW_CONFIDENCE/FAILED、三类 low-confidence count、verified/unverified scope report。
- BODY、APPENDIX、TOC、header/footer、tracked deletion、strike/double-strike、两个 PARTY_A semantic classifier、未知/无法表示的 policy context。
- 每个 document block 恰好一条 ledger；缺失、重复、UNCERTAIN、full scan=false；被拒 candidate 带固定 reason。
- 六个 attribution signals 分别失败。
- BLOCK/BLOCK、CELL/CELL、BLOCK/CELL；same identity same/different value；真实与伪造 lineage。
- occurrence 0、1、64、65；distinct value 1、2、8、9；预算与可靠异值同时存在时预算优先。
- TEXT、CNY、PERCENT 正反 canonicalization。
- 九点单值 PASS/结构化 mismatch ERROR、多值 ERROR；税额顺序与四点 SKIPPED。
- snapshot null、错版本、缺 policy、不可变集合、旧构造器与 ordinary resolver 回归。

测试使用代码内 synthetic document/candidate/policy builder；不得读取或修改 TASK-034 fixture/expected，不得把被测输出保存后再作为 expected。

## 8. 测试与验证命令

执行方必须提供原始命令、退出码、Gradle XML tests/failures/errors/skipped 计数和失败摘要：

```powershell
Set-Location C:\Users\1\Documents\CQCP\apps\api-server
gradle test --tests "com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollectorTest" --tests "com.cqcp.apiserver.reviewengine.ConsistencySetCollectorTest" --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest" --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest" --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest" --tests "com.cqcp.apiserver.wordparser.DocxWordParserSpikeTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.RuntimeRuleSetLoaderTest" --tests "com.cqcp.apiserver.reviewengine.RuleSetActivationGateTest" --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest" --tests "com.cqcp.apiserver.reviewengine.ResultComposerTest"
gradle test
gradle bootJar
Set-Location C:\Users\1\Documents\CQCP
node --test scripts/validate-review-assets.test.mjs
node scripts/validate-review-assets.mjs
git diff --check
git status --short
git diff --stat
git diff -- apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuleSetActivationGate.java packages/review-assets scripts/validate-review-assets.mjs scripts/validate-review-assets.test.mjs
rg -n 'request\s*\(\s*"v20260715\.1"\s*,\s*true|consistencyRuntimeReady\s*=\s*true' apps/api-server/src/main/java
```

`gradle test` 若仅因既有 `contextLoads` PostgreSQL/Flyway 环境依赖失败，必须附完整失败证据，并以其余定向测试证明代码层结果；不得写成 full suite 通过。C1 不要求 Docker build。

禁止执行：commit、push、branch switch、正式 E2E、Docker/compose up/down、数据库 migration、外部 API/模型调用、依赖安装。

## 9. STOP 条件

遇到以下任一情况立即停止：

- 工作区不是冻结分支/基线，或出现无法归属的既有变更。
- 实现需要修改 §1 允许范围外文件。
- 无法用现有 parser 信号证明 scope/parse/anchor readiness。
- 需要新增外部 API/DB字段/公开 enum/依赖或改变历史 snapshot。
- 需要通过 sample id、fixture path、occurrenceNo、人工 included 标记或 candidate value反搜伪造 provenance。
- legacy 回归只能通过改变既有 expected 才能通过。
- 发现 B2 snapshot 与 B1 policy 不足以表达冻结 admission。

输出格式：

```text
[STOP: <类型> — <事实与路径> — <需要 Codex 决策的事项>]
```

## 10. 实现报告要求

实现报告必须包含：

- 当前分支、基线 HEAD、`git status --short`、完整修改路径、`git diff --stat`。
- §6 十五条断言逐项证据。
- §8 每条命令、退出码和测试计数；环境失败单列。
- 新增/扩充字段的真实来源和 legacy 构造器兼容说明。
- 假设、歧义、未实现、STOP 记录。
- 明确声明：未 commit、未 push、未修改 C2/资产/fixture/expected、未运行正式 E2E、未生产激活。

## 11. Codex Review 与独立实现审计

### 11.1 独立规格审计记录

- 首轮独立规格审计结论：`NO_GO`。
- Codex接受全部三项 blocking finding：原 `rawCandidates` 无法证明完整扫描；parser 无法表达四类 strong exclusion 与两个 PARTY_A semantic exclusion 的 observability；admission 错误映射与组合优先级不完整。
- v0.2 修正：新增 parser scope coverage report、完整 block scan ledger/rejection batch、两个 semantic classifier、十三路径边界及十七级穷举优先级。
- v0.2 增量复审结论：`GO`；三项 blocking finding 全部关闭，无剩余 blocking finding。
- 唯一 non-blocking 澄清已接受并在 v0.3 写回：`DELETED` 只排除 tracked-deletion 内容，不连带排除同 block 的存活文本；无法区分时按 unresolved/fail-closed。
- Codex Review Intake：`SPEC_FROZEN / INDEPENDENT_SPEC_AUDIT_GO / PRE_CODING_PLAN_PENDING / NO_IMPLEMENTATION_AUTHORIZATION`。

### 11.1.1 首轮编码前规格映射计划审查

- 结论：`NO_GO / REVISION_REQUIRED / NO_IMPLEMENTATION_AUTHORIZATION`。
- Blocking 1：未列出现有 `EvidenceCandidate`、`ReviewEngineInput` 构造器及生产/测试调用数量，也未逐类说明追加 record component 后的兼容委托与 `withEvidenceOverride` 引用保持证据，不满足 §0.2 第 1 项。
- Blocking 2：未说明新 collector 如何复用或受控暴露 `ParserBackedReviewInputPreparer` 中现有 private 九点 label、pattern、ValueGrammar 与 fallback probe；因此无法证明每个 block 的 `SCANNED_NO_MATCH` 是在全部冻结 probe 实际执行后形成，也无法证明没有复制第二套九点提取矩阵。
- Blocking 3：TOC 计划新增 style regex/`CT_SectPr` columns 推断，偏离 §2.1 冻结的“沿用现有 `DocumentBlock.contextType=TOC`”边界；同一 `scopeVersion` 下不得扩写 probe 语义。
- Blocking 4：`CONTRACT_TITLE_NAME_MENTION` 计划额外排除中文逗号 `，`，而冻结 classifier 只禁止句号、分号、冒号；这是同一 `scopeVersion` 下的语义漂移。
- Blocking 5：tracked-deletion 计划引用 Apache POI 5.3.0 `XWPFRun.isDelete()`，当前依赖 API 不存在；同时未说明如何遍历 OOXML `w:del` 包裹的 run、如何证明 `paragraph.getRuns()` 未漏掉删除内容，以及混合删除/存活文本的真实 fail-closed 判据。
- Blocking 6：TABLE_CELL 计划未把 `previewElementRef` 的生产来源映射到当前 `DocumentBlock.tableId/rowIndex/tableCells` 与 match offset，也未说明 collector 如何用真实 cell span 复算并校验 candidate ref，因而不能证明 ref 是 parser lineage 而非候选侧手工拼接。
- Blocking 7：十七级 mapping 只写了 `SystemFailure(...)` / `Ambiguous(...)` 等缩写，未逐行固定 `EvidenceStatus / slotCoverage / diagnostic / notConcludedReason / detail` 五元组，也未说明它们如何进入现有 `PointEvidence`/engine preflight，不能证明 §3.4 精确 reason 与优先级。
- Blocking 8：九审核点测试仅列名称集合，没有逐点给出 collector probe、policy、单值/多值 verdict 与 anchor 保留的参数化矩阵；scope parser 测试也未说明如何在不新增 fixture 路径的前提下构造 header/footer、`w:del`、strike/double-strike DOCX。
- 修订只允许更新编码前计划文本；CC 不得修改仓库文件。Codex 明确 `GO / IMPLEMENTATION AUTHORIZED` 前不得实现。

### 11.1.2 第二轮编码前规格映射计划审查

- 结论：`NO_GO / REVISION_REQUIRED / NO_IMPLEMENTATION_AUTHORIZATION`。
- 已关闭：首轮 Blocking 3（TOC 只沿用现有 `DocumentBlock.contextType=TOC`）；首轮 Blocking 7（十七级完整状态五元组）。
- Round-2 Blocking 1：`EvidenceCandidate` 调用盘点不准确。真实 `rg` 结果为生产 1 处、测试 8 处，共 9 处，不是计划声明的 12 处；且旧构造器把新增 lineage/confidence 默认成 `NORMAL/NATIVE_WORD/STRUCTURED/HIGH/BLOCK_LEVEL` 会伪造可通过 C1 readiness 的可信信号。兼容构造必须使用不能通过 C1 admission 的缺失/unknown sentinel，只有真实 `DocumentBlock` 投影的生产构造可填可信值。
- Round-2 Blocking 2：仅暴露 `patternsFor/labelHintsFor/fallbackPatternsFor` 仍未覆盖现有 semantic ratio、whole-text、structured amount tuple、role-block percent 与 weak payment fallback 的真实 probe；同时新 collector 冻结签名没有 `TaskExecutionRequest/paymentMethod`，计划却声称复用需要 `paymentMethod` 的 `collectSemanticRatioCandidates`。必须逐点说明 C1 全文 occurrence 提取实际运行哪些既有 probe、哪些 legacy gating 不适用于 C1，以及如何在不改变冻结签名、不复制第二套提取矩阵的前提下形成唯一 probe descriptor。
- Round-2 Blocking 3：PARTY_A classifier 仍偏离冻结语义。计划把任何“甲方/发包方”出现都当成角色赋值标签，并额外排除“承包方/乙方”；冻结条件仅排除 PARTY_A 角色**赋值**标签。`block.text().trim() == block.text()` 既是 Java 引用比较错误，也新增了“原文不得有首尾空白”的未冻结条件。必须完全按 §2.2 条件实现，不新增关键词或格式约束。
- Round-2 Blocking 4：tracked-deletion OOXML 映射仍不可编译。POI 5.3.0 schema 的真实层级是 `CTP.getDelList()` 返回 `CTRunTrackChange`，再由 `CTRunTrackChange.getRList()/getRArray()` 取得删除 run，删除 run 的 `CTR.getDelTextList()` 取得 `w:delText`；`CTR.getDel()/isSetDel()/addNewDel()` 均不是该结构。`CTP.getRArray()` 只给直接 `w:r`，不能证明包含嵌套于 `w:del` 的 run。计划必须以本地 `javap` 可验证 API 重写，并覆盖 paragraph/table cell/header/footer 中相关 paragraph 的遍历与解析异常 fail-closed。
- Round-2 Blocking 5：TABLE_CELL 计划声称用 `matchStart/matchEnd` 复算，但冻结 `EvidenceCandidate` 及计划新增字段均不保留 offset，readiness collector 无法执行该校验；“cellIndex 位于 startOffset/endOffset 范围”也混淆了 index 与字符 offset。必须明确校验发生在候选创建前还是追加可信 offset carrier，并分别冻结 candidate collector 与 readiness collector 能验证的 lineage，不得声称不存在的数据可用。
- Round-2 Blocking 6：parser 测试仍可用 synthetic `ScopeCoverageReport` 代替真实 parser 扫描，并给出了不可编译的 `ctr.addNewDel()` builder。`DocxWordParserSpikeTest` 必须用 POI 在 `@TempDir` 动态写出并重新 `parse(Path)` 的 DOCX，分别证明 TOC、header/footer paragraph/table、同段 `w:del`+存活 run、strike 和 double-strike 的真实 report/legacy-block 行为；collector 的 synthetic carrier 测试只能作为补充。
- Round-2 Blocking 7：九点矩阵仍只列预期类别，没有逐点列出真实输入 block、实际 probe 方法、产生的去重前 candidate、policy 与单值/多值全部 anchors 断言；`milestoneMonthlyOnlySkipped` 名称也未明确四个不适用点和仍适用的 PREPAYMENT。必须给出可执行参数源与逐点 expected 来源。
- 下一轮仍只允许修订计划文本；不得修改任何仓库文件。

### 11.1.3 第三轮编码前规格映射计划审查

- 结论：`NO_GO / STOP_SCOPE_VIOLATION / REVISION_REQUIRED / NO_IMPLEMENTATION_AUTHORIZATION`。
- 已关闭：旧 candidate 新 lineage/confidence 字段使用 null/empty sentinel；TOC、十七级五元组；TABLE_CELL offset 只在候选创建阶段使用、readiness 校验 ref/components 的分层方向可接受。
- Blocking 1（STOP）：计划要求从 `ConsistencyPolicySnapshot.structuredFields()` 读取 `paymentMethod` 并给 snapshot 增加访问路径，但该 record 没有此字段，且 `ConsistencyPolicySnapshot.java` 是 C1 禁止修改路径。B1 consistency policy 也没有 `applicablePaymentMethods`。C1 collector 必须保持 payment-method-neutral；现有 `MinimalReviewEngine.isSkippedForPaymentMethod()` 已从 `ReviewEngineInput.structuredFields` 在 evidence preflight 前处理四个 monthly-only 点，不得把 applicability 塞入 B2 policy。
- Blocking 2：构造器盘点仍不准确。真实调用是 `EvidenceCandidate` 生产 1 + 测试 8 = 9；`ReviewEngineInput` 生产 2 + 测试 5 = 7。计划不得用“兼容两种计数”替代事实。
- Blocking 3：所谓唯一 `ProbeDescriptor` 仍允许“逻辑等价体 inline 到 collector”，会复制第二套提取实现；且列出的 probe 依赖不存在的 policy paymentMethod。必须以一个 package-private immutable descriptor/dispatcher 为唯一真源，由 legacy 与 C1 调用；C1 的全文 occurrence probe 不使用 `paymentClauseBlocks` 或 paymentMethod gating，engine 负责 applicability。
- Blocking 4：PARTY_A classifier 仍使用“包括但不限于”“或类似”并新增 `甲方名称/发包人`，不符合版本化 classifier 的封闭定义。必须直接复用现有 `PARTY_A_BLOCK_PATTERNS` 判断角色赋值，不新增同义词或开放式规则。
- Blocking 5：OOXML 主读取层级已修正，但测试类型仍错误：POI 5.3.0 `CTR.addNewDelText()` 返回 `CTText`，不是 `CTDelText`。相同删除文本与存活文本在 XML 层仍可由 `w:del` wrapper 区分，不应仅因值相同标 unresolved。测试 TOC 必须设置现有 parser 可识别的 TOC style；只构造 field/SDT 不会自动令 `resolveParagraphType` 返回 `TOC_ITEM`。
- Blocking 6：九点矩阵大量 expected 不来自真实冻结资产/代码：B1 九点均为 `maxCandidates=8 / occurrenceBudget=64`，不是 PARTY 点的 4/8；总价/税额示例 label 与现有 hints/patterns 不一致；结算 100 示例缺少现有 `isExpectedRatioValue` 要求的“结算金额相等”；PROGRESS 的 MILESTONE 行自相矛盾。
- Blocking 7：SKIPPED 映射错误。现有代码没有 `EvidenceStatus.SKIPPED`；`SKIPPED` 是 `PointStatus`，由 `MinimalReviewEngine.reviewPoint()` 在 evidence preflight 前返回，`notConcludedReason=null`、`skippedReason=NOT_APPLICABLE_FOR_PAYMENT_METHOD`、anchors 为空。不得声明 `slotCoverage=MISSING / EVIDENCE_NOT_FOUND`，也不得由 policy applicability 驱动。
- 下一轮只需提交上述七项的事实纠错增量，不再重写整份计划。仍不得修改仓库文件。

### 11.1.4 第四轮编码前规格映射计划 Review Intake

- 结论：`GO / IMPLEMENTATION AUTHORIZED / C1_INACTIVE_RUNTIME_CORE_ONLY`。
- 七项第三轮 finding 已实质关闭：不修改 B2 snapshot；C1 collector 与 paymentMethod 解耦；旧 candidate 使用 fail-closed sentinel；TOC/OOXML/SKIPPED/统一预算与 PARTY_A classifier 回到冻结事实。
- 绑定澄清 1：真实构造调用分布固定为 `EvidenceCandidate` 生产 1 处、`MinimalCandidateResolverTest` 7 处、`MinimalReviewEngineTest` 1 处，共 9；`ReviewEngineInput` 生产 2、测试 5，共 7。原 8 参数和 14 参数 candidate 构造器都必须保留，并为六个新增信号使用 null/empty sentinel。
- 绑定澄清 2：不得用所谓“静态 paymentMethod 配置”。在 `ParserBackedReviewInputPreparer.java` 内建立唯一 package-private probe dispatcher，可使用 package-private `ProbeExecutionMode { LEGACY, CONSISTENCY_FULL_SCAN }`：legacy 模式保持现有 runtime paymentMethod gating；C1 full-scan 模式不接收/读取 paymentMethod、不使用 `paymentClauseBlocks`，对完整 document 执行 probe。两种模式复用同一现有 probe 实现，禁止 inline 复制。必须新增回归测试证明 legacy MONTHLY 行为不变，并证明 C1 full scan 不受 paymentMethod gating。
- 绑定澄清 3：`ConsistencyPolicySnapshot.java`、B1/B2 资产和 `MinimalReviewEngine` 既有 `isSkippedForPaymentMethod()` 不得修改其 applicability 语义；四个 monthly-only 点仍在 evidence preflight 前返回现有 `PointStatus.SKIPPED`。
- 实现严格限制在 §1 十三个允许路径；不得修改本规格、`AGENTS.md`、状态机、gate、snapshot/loader、资产、fixture、expected 或构建文件。
- CC 完成实现与 §8 验证后提交实现报告并停止；不得 commit、push、进入 C2 或正式 E2E。

### 11.2 实现审查要求

Codex 必须审查真实 tracked/untracked diff、测试原始输出和 §6 断言。Codex 初审无 blocking finding 后，独立 agent 对实现做只读审计，至少核查：

- 文件边界和 legacy 无回归。
- parser scope coverage、完整 document、逐 block ledger、rejection reason 与 parse quality 确实参与 readiness。
- provenance 在去重/grouping 前保留。
- scope/exclusion/attribution/anchor/canonicalization/双预算顺序与映射。
- 可靠异值与 role conflict 的 SYS/Finding 分流。
- 九点、税额、SKIPPED 和全部 anchors。
- 无 production `ready=true`、状态机绑定、fixture/expected 倒填或正式 E2E 越界。

独立审计输出 `GO` 或 `NO_GO`，区分 blocking/non-blocking finding。最终接纳只能由 Codex作出：

```text
ACCEPT_IMPLEMENTATION / C1_INACTIVE_RUNTIME_CORE_ONLY
```

或拒绝并列出精确修正范围。

### 11.2.1 首轮实现 Review Intake

- 结论：`NO_GO / BLOCKING_FINDINGS_ACCEPTED / CORRECTION_PLAN_REQUIRED / NO_CORRECTION_IMPLEMENTATION_AUTHORIZATION`。
- Blocking 1（legacy 回归）：`MinimalReviewEngine` 的 multi-value 与 evidence-tax 新分支未以 `runtimeRuleSetSnapshot != null` 为前提，snapshot=null 的 legacy execution 已改变。Codex在 PowerShell 7 复现 `TaskExecutionStateMachineTest`：10 tests、1 failed，`CQCP-MVP-DOCX-001#1.4` 出现非预期 `TAX_AMOUNT_FORMULA_CONSISTENCY ERROR`；该失败由新 tax-evidence 比较引入，不接受“既有失败”声明。
- Blocking 2（parser coverage）：`buildScopeCoverageReport` 把“已扫描能力”错误等同为“文档实际出现该 context”，正常无 TOC/DELETED/VOIDED 文档会 `verified=false`；DELETED/VOIDED 未扫描 table cell/header/footer paragraph，table 分支仍是空 TODO；VOIDED 不记录 body blockId，无法让 ledger 精确排除对应 block；`hasDstrike` 异常被静默吞掉而非 unresolved/fail-closed。
- Blocking 3（固定机器证据）：`ExcludedSourceRegion.reason` 和 ledger reason 使用任意中文说明文本，不是冻结的固定机器 reason；DELETED 的 `checkedDeletion` 跨 paragraph 复用，不能形成逐来源记录。
- Blocking 4（完整扫描证明）：`ConsistencyCandidateCollector.collect` 额外接收外部 `rawCandidates`，只按 blockId 回填 ledger，并在没有 candidate 时直接声称“全部冻结 probe 已执行”；collector 本身没有执行 probe，无法证明 `SCANNED_NO_MATCH`。`rejectedCandidates` 永远为空。该接口与 §2.2 冻结四参数 collector 不符。
- Blocking 5（probe 唯一真源）：`probeAllForPoint` 的 `LEGACY` 模式直接抛异常，legacy 没有使用共享 dispatcher；新增 `collectTextCandidates/collectAmountCandidates/collectRatioCandidates` 复制现有提取链。C1 ratio 路径还对 PREPAYMENT 运行了 legacy 明确禁止的 weak payment fallback。
- Blocking 6（semantic observability）：两个 semantic context 只有 classifier **命中**时才加入 handled 集合，而不是 classifier 已执行即 handled；第一项命中后短路第二项。`PARTY_A_BLOCK_PATTERNS` 被复制，且 `hasPartyARoleAssignment` 额外用 broad `contains` 扩大冻结语义。
- Blocking 7（admission fail-closed）：roleLabel/valueFormat/blockAttribution 使用 `anyMatch`，一个可靠 candidate 可掩盖同集合中的不可靠 candidate；未逐 candidate 验证 region/context、真实 blockConfidence、reviewPointCode、candidateRole 与 ledger attribution，违反“任一不确定则整集合降级”。
- Blocking 8（anchor lineage）：TABLE_CELL 只校验 tableId，未校验 ref/candidate/真实 block 的 rowIndex 与 cellIndex，也未验证真实 `tableCells`；生成 occurrence 时无论 anchor 类型都写 `locationLevel=BLOCK_LEVEL`。CELL/CELL、BLOCK/CELL、伪造 row/cell identity 均未覆盖。
- Blocking 9（测试与报告证据）：授权的 `DocxWordParserSpikeTest`、`MinimalReviewEngineTest`、`ParserBackedReviewInputPreparerEvidenceTest`、`MinimalCandidateResolverTest` 均无实现 diff；不存在真实 `@TempDir -> write DOCX -> parse(Path)` 四类 scope 测试、九点单/多值 verdict 矩阵、snapshot-null/new snapshot engine 分流测试、精确 64/65 与 8/9 边界。实现报告声称的 `MinimalReviewEngine.consistencyMultiValueError` 测试在仓库中不存在。
- 允许的下一动作仅为：CC提交修正轮编码前规格映射计划并停止。未获得 Codex新的 `GO / CORRECTION IMPLEMENTATION AUTHORIZED` 前不得继续修改文件。

### 11.2.2 首轮修正计划审查

- 结论：`NO_GO / PLAN_REVISION_REQUIRED / NO_CORRECTION_IMPLEMENTATION_AUTHORIZATION`。
- Blocking 1（collector 依赖方向自相矛盾）：计划一面声明恢复 §2.2 四参数 `ConsistencyCandidateCollector.collect(...)`，一面仍要求 `ParserBackedReviewInputPreparer` 先收集 candidates 并传给 collector。修订必须明确由四参数 collector 直接调用 `ParserBackedReviewInputPreparer` 中唯一 package-private `probeAllForPoint(..., CONSISTENCY_FULL_SCAN)`/descriptor，对每个完整 document block 形成 probe 执行事实；preparer 不得向 collector 传第五参数或隐藏外部候选状态。
- Blocking 2（ledger/rejected 证据未闭合）：计划未说明每个 probe 对每个 block 的执行结果如何聚合为恰好一条 ledger，也未说明 `rejectedCandidates` 在 scope、semantic、probe/ValueGrammar 拒绝时如何真实填充；不得继续保留永远为空的 carrier，也不得仅凭“候选列表无命中”写 `SCANNED_NO_MATCH`。
- Blocking 3（parser 测试矩阵不足）：生产描述虽声称扫描 body/table/header/footer，但测试只列简单文档、table deletion、body strike 和 header paragraph，不能证伪 table/header/footer 的 tracked deletion 与 strike/double-strike、body mixed deleted/live text、准确 body blockId、header/footer table 枚举以及 XML/part 判断异常进入 `unresolvedSignals` 的 fail-closed 行为。修订计划须逐项列出动态 `@TempDir -> write DOCX -> parse(Path)` 测试或明确可构造性边界；不能构造的异常路径必须通过最小可注入 seam 在允许路径内测试。
- Blocking 4（九点 verdict 与边界证据不够明确）：计划列出通用 single/multi-value engine 测试，但未给出九个 `ReviewPointCode` 的参数化单值/多值 verdict、全部 occurrence anchors、税额先 evidence-tax 后公式、MILESTONE `SKIPPED` 的精确方法与 expected；还须明确 `8/9` distinct-value 与 `64/65` occurrence 四个真实边界 case，而不是小预算替代。所有 expected 必须直接来自冻结 B1/B2 policy、现有 deterministic engine 规则或人工写定断言，不得从被测 collector/engine 输出反向生成。
- 下一动作仍仅为 CC 提交上述四项计划纠错增量并停止；不得修改任何代码、测试或治理文件。

### 11.2.3 第二轮修正计划审查

- 结论：`NO_GO / TWO_PLAN_GAPS_REMAIN / NO_CORRECTION_IMPLEMENTATION_AUTHORIZATION`。
- 已关闭：四参数 collector 通过唯一 immutable probe descriptor 在 `collect(...)` 内逐 block 执行 `CONSISTENCY_FULL_SCAN`，不再外传 candidate；ledger 聚合、真实 `rejectedCandidates`、8/9 与 64/65 边界及 expected 来源已给出可实施映射。
- Blocking 1（parser 矩阵仍不完整）：新增矩阵覆盖 body strike/double-strike、body/table deletion、header/footer paragraph/table 与 header deletion，但仍没有 table、header、footer 中 strike 和 double-strike 的可证伪 case，也没有明确 footer tracked-deletion case。修订增量须列出这些 source-part × voiding/deletion 组合，验证 `handledStrongContextTypes`、`ExcludedSourceRegion.sourcePart`、body 才允许的准确 blockId 以及无法判断时的 unresolved/fail-closed。
- Blocking 2（九点 engine verdict 未覆盖）：所谓九点单值/多值矩阵调用的是 `ConsistencySetCollector.collect()`，只能证明 readiness `PointEvidence`，不能证明 §4 要求的九点 `MinimalReviewEngine` deterministic verdict。修订须在 `MinimalReviewEngineTest` 列出九点参数化 snapshot-present case：单 canonical value 与匹配/不匹配结构化字段进入各点既有比较；两个可靠 canonical values 对每一点均直接产生业务 `ERROR`、`FindingSeverity.ERROR` 并保留全部 anchors；税额仍由单独顺序测试覆盖，四个 monthly-only 点另验证 MILESTONE 在 evidence preflight 前 `SKIPPED`。
- 下一动作仅为 CC 提交上述两项计划纠错增量并停止；其他已接受计划内容不重写，不得修改文件。

### 11.2.4 修正实现授权

- 结论：`GO / CORRECTION IMPLEMENTATION AUTHORIZED / C1_INACTIVE_RUNTIME_CORE_ONLY`。
- §11.2.1 九项实现 finding 已由首轮修正计划及两轮纠错增量形成可实施闭环；实施范围仍严格限制在 §1 十三个 Java 路径子集，Codex治理文件不得由 CC 修改。
- 绑定澄清 1：位于 DOCX body table 的 cell/row 对应现有 `DocumentBlock.TABLE_ROW`，其 VOIDED/DELETED 排除必须复算并记录准确 body blockId；不得按“table 不是 body block”写 null 或 unresolved。只有 header/footer/footnote/endnote/other 等不进入 `document.blocks` 的 part 才使用 null blockId。
- 绑定澄清 2：`MinimalReviewEngineTest` 的 multi-value business-error 证据必须对全部九个 `ReviewPointCode` 参数化（或九个等价显式 case），每点断言 `PointStatus.ERROR / FindingSeverity.ERROR / PointCoverageStatus.COMPLETE` 与两个不同 identity 的全部 anchors；不得只用一个通用审核点代表九点。单值 match/mismatch 也必须逐点进入现有 deterministic 比较，税额点由 evidence-tax → 强公式 → 弱公式顺序测试补充，四个 monthly-only 点另证 MILESTONE preflight `SKIPPED`。
- 实施完成后重新运行 §8 全部 PowerShell 7 验证，必须单独报告 `TaskExecutionStateMachineTest` XML 计数并证明 `CQCP-MVP-DOCX-001#1.4` legacy 回归消失。提交实现报告后停止，等待 Codex重新审查；不得 commit、push、进入 C2、运行 Docker 或正式 MVP E2E。

### 11.2.5 修正实现 Review Intake

- 结论：`NO_GO / BLOCKING_FINDINGS_ACCEPTED / CORRECTION_PLAN_REQUIRED / NO_CORRECTION_IMPLEMENTATION_AUTHORIZATION`。
- 已确认通过：Codex在 PowerShell 7 以 `--rerun-tasks` 强制重跑 `TaskExecutionStateMachineTest`，退出码 0，首轮 `CQCP-MVP-DOCX-001#1.4` legacy 税额回归已消失；六个 C1 定向类也退出码 0，XML 合计 87 tests、0 failures、0 errors、0 skipped。测试为绿不改变以下代码与覆盖 finding。
- Blocking 1（parser capability 仍按命中记录）：`DocxWordParserSpike.buildScopeCoverageReport()` 只有 body 实际发现 deletion/voided 时才加入 `DELETED/VOIDED` handled；正常无命中文档以及仅 header/footer 命中文档仍未证明扫描能力已执行。header/footer VOIDED 只扫 paragraph、不扫 table；`scanParagraphForVoided` 和 `hasDstrike` 仍吞异常并当作未命中，违反 unresolved/fail-closed。
- Blocking 2（parser body lineage 与消费链断裂）：body table deletion/voided 辅助方法完全没有使用 `tableRowToBlockId`，paragraph 文本 map 也不能稳定映射 table row；重复正文文本还会令 `textToBlockId` 后写覆盖。更关键的是 `ConsistencyCandidateCollector` 从未消费 `ScopeCoverageReport.excludedSourceRegions`，因此 parser 即使记录 body `VOIDED` block，ledger 仍按 `DocumentBlock.contextType=NORMAL` 纳入候选，强排除没有实际生效。
- Blocking 3（probe 唯一真源与逐 block 事实仍不成立）：legacy `build(request, plan)` 继续直接调用 `resolvePartyA/resolveNumericEvidence/resolveRatioEvidence`，从未调用 `probeAllForPoint(..., LEGACY)`；`ProbeExecutionMode` 在 dispatcher 内没有行为差异，新 `probeTextCandidates/probeAmountCandidates/probe*RatioCandidates` 仍复制第二套提取编排。collector 在循环外对 `allBlocks` 只调用一次 descriptor，无法形成逐 block probe 执行事实；descriptor 异常会直接逸出，永远不会写 `UNCERTAIN` ledger。
- Blocking 4（ledger 完整性不足）：`fullPolicyScopeScanned` 只比较 ledger/block 数量，不验证 blockId 唯一性、每个 block 恰好一条、无缺失/重复；`CANDIDATE_EMITTED` reason 仍可能为 null，违反每条 ledger 固定机器 reason。semantic 两项虽然预先标 handled，但 `isSemanticallyExcluded` 第一项命中后仍短路第二 classifier，实际执行事实与 handled 声明不一致。
- Blocking 5（ValueGrammar 被静默丢弃）：candidate collector 将 `valueFormatSignal=false` 候选只放入 `rejectedCandidates`，却从 `rawCandidates` 删除；readiness collector 又不消费 `rejectedCandidates`。同一集合中一个合法候选加一个 grammar 失败候选会只保留合法候选并输出业务结论，违反“任一未排除 candidate 不可靠则整集合降级”。
- Blocking 6（真实 confidence 未核对）：readiness 只检查 candidate carrier 的 `blockConfidence == HIGH`，没有把它与真实 `DocumentBlock.blockConfidence` 比较；伪造 HIGH 可掩盖真实 LOW/MEDIUM block。
- Blocking 7（engine multi-value 真源错误）：`hasConsistencyMultiValueConflict` 仍以 `evidence.candidateValue()==null` 推断多值，没有从 `occurrences.candidateValue` 计算 distinct canonical values。测试 helper 也先手工写 null 再断言 ERROR，属于按实现形状构造的循环验证；同值多 occurrence + null 会误报，异值 occurrences + 非 null 会漏报。
- Blocking 8（授权测试矩阵仍未落地）：实际无 `DocxWordParserSpikeTest.java` 和 `ParserBackedReviewInputPreparerEvidenceTest.java` diff；现有 parser XML 仅 3 tests，未动态验证 body/table/header/footer deletion/strike/double-strike、blockId、unresolved；preparer 未验证 LEGACY/C1 共享 dispatcher。`ConsistencySetCollectorTest` 的预算仍使用 2/3 与 2/4 小预算，不是冻结 64/65 和 8/9；anchor 仍只有“缺 cell ref”一项，未覆盖 row/cell mismatch、CELL/CELL、BLOCK/CELL 和 forged lineage；probe exception/UNCERTAIN、重复 ledger、good+bad grammar、真实 block confidence、occurrence-derived multi-value 反例均缺失。
- 允许的下一动作仅为 CC 提交针对上述八项的下一轮编码前修正规格映射计划并停止。未获得 Codex新的 `GO / CORRECTION IMPLEMENTATION AUTHORIZED` 前不得修改任何文件；不得 commit、push、进入 C2、Docker 或正式 MVP E2E。

### 11.2.6 下一轮修正计划 Review Intake

- 结论：`NO_GO / PLAN_REVISION_REQUIRED / NO_CORRECTION_IMPLEMENTATION_AUTHORIZATION`。
- 已接受方向：`ValueGrammar` 失败候选保留在 raw candidates 并由 readiness fail-closed、candidate/真实 block confidence 对照、engine 从 occurrences 计算 distinct value，以及四参数 collector 内逐 block probe 的总体方向可继续沿用；但当前计划仍有以下六项 blocking gap。
- Blocking 1（handled 与异常语义自相矛盾）：计划一面定义 handled 表示扫描能力成功执行，一面又要求 `scanParagraphForVoided` 异常时“handled context 仍标记”。发生异常的 context 不得声明已成功 handled；必须写入 `unresolvedSignals`、令 `verified=false`，并明确该 context 不计入成功 handled 集合。测试必须断言这三项，而不是只断言 unresolved。
- Blocking 2（混合删除/存活内容被整 block 误排除）：计划要求 collector 只要发现同 blockId 的 `DELETED/VOIDED` region 就把整个 block 标为 `EXCLUDED`，这违反 §11.1 已接受澄清“tracked deletion 只排除删除内容，不连带排除同 block 的存活文本；无法区分时 unresolved/fail-closed”。必须说明 parser 如何保证删除内容不进入 live `DocumentBlock`/candidate，何时才可整 block 排除，并增加 mixed deleted+live 的 collector 测试证明 live candidate 仍可纳入；无法区分 deleted/live 时必须降级，不能静默整块排除。body paragraph/table lineage 也必须基于解析时对象身份与真实 `tableRowToBlockId`/row identity 建立，不能再用文本、body-element 索引或“最接近 TABLE_ROW”推测；重复正文和重复表格文本都要有可证伪测试。
- Blocking 3（legacy shared dispatcher 契约仍未闭合）：计划声称 `probeAllForPoint(code, role, blocks, LEGACY)` 保持 runtime `paymentMethod` gating，却没有任何参数或 immutable context 能携带 `paymentMethod`，并且又说 legacy 传完整 blocks 后在 dispatcher 内使用 `paymentClauseBlocks`。必须给出可编译的唯一 dispatcher/descriptor 签名与调用图，逐点说明 legacy 如何保留现有 runtime paymentMethod、semantic ratio、fallback 顺序和 payment-clause slice，同时 C1 full scan 明确不接收/读取 paymentMethod、不使用 slice；不得以新的 `probeSingleBlock` 再复制一套九点提取矩阵。必须保留 `legacy MONTHLY` 行为回归和 C1 payment-method-neutral 的独立测试。
- Blocking 4（ledger/semantic 测试不能证明声明）：`testLedgerDuplicateBlockIdMakesFullScanFalse` 不能靠“伪造 ledger 条目”证明 collector 自己的唯一性校验；测试必须给 collector 一个具有重复/缺失 blockId 的真实 document carrier，或使用明确的允许 seam，并断言 collector 计算出的 boolean。`handledSemanticContextTypes` 预先写满后再断言两个 id 存在不能证明两个 classifier 都执行；须用独立可观察的 package-private classifier seam/counter，或两个非短路输出，证明第一项命中后第二项仍运行。`CANDIDATE_EMITTED` 的固定 reason 不得统一写成语义错误的 `CANDIDATE_ACCEPTED`；grammar-rejected-only block 仍是 probe emitted，reason 必须与真实事实一致。
- Blocking 5（engine 反循环 case 未覆盖）：计划的“同值多 occurrence”仍给非 null `candidateValue`，另一个 null case却使用空 occurrences，均不能证伪原 bug。必须精确新增：两个不同 identity、同 canonical value、`evidence.candidateValue=null` 时不得产生 multi-value ERROR；两个不同 identity、不同 canonical value、`evidence.candidateValue` 非 null 时仍必须产生 ERROR，并断言全部 anchors。
- Blocking 6（矩阵仍不完整且部分名称/断言错误）：最新 parser 表缺 header tracked deletion，以及 table/header/footer 的 double-strike（也未完整覆盖 header/footer table 的 voiding/deletion）；必须按 source-part × deletion/strike/double-strike 明确全部可构造 case。`testTableCellAndBlockCellSameValueTwoIdentities` 把两个 cell 错写成 BLOCK+TABLE_CELL，须分别覆盖真正 CELL/CELL 与 BLOCK/CELL，并断言 occurrence identity、anchor 数量、ref 和 `locationLevel`。8/9 边界须分别使用恰好 8 与 9 个 distinct canonical values，不能写“9 identities 中 8 values → 8 ready”这种含混矩阵。preparer 的“输出一致”不能证明共享 dispatcher，须给出可观察调用 seam/counter并同时证明 legacy MONTHLY 不变、C1 full scan 不受 paymentMethod gating。
- 下一动作仅允许 CC 针对上述六项提交计划纠错增量并停止；其他已接受内容不重写。未获得新的 `GO / CORRECTION IMPLEMENTATION AUTHORIZED` 前不得修改代码、测试或治理文件，不得 commit、push、进入 C2、Docker 或正式 MVP E2E。

### 11.2.7 最终计划 Review Intake 与绑定授权

- 结论：`GO / CORRECTION IMPLEMENTATION AUTHORIZED WITH BINDING OVERRIDES / C1_INACTIVE_RUNTIME_CORE_ONLY`。
- 已接受：成功扫描才计入 handled、真实 18 项 source-part 矩阵、实际 `8/9` distinct 边界、CELL/CELL 与 BLOCK/CELL identity/anchor/locationLevel、occurrence-derived multi-value、真实 block confidence 和 raw/rejected ValueGrammar fail-closed 方向。
- 绑定 1（scope/exclusion 与 lineage，以此覆盖 CC 原文）：必须使用真正的 `IdentityHashMap<XWPFParagraph,String>` 与 `IdentityHashMap<XWPFTableRow,String>`，在主解析创建 `DocumentBlock` 时按对象引用写入；禁止 `System.identityHashCode`、文本、body-element index、“最接近 row”或其他推测映射。`verified` 必须同时满足四类 required strong context 均成功 handled 且 `unresolvedSignals` 为空。只有整个 body block 的 reviewable content 都属于 tracked-deleted 或 strike/double-strike voided content 时，才产生可被 collector 消费的 block-level exclusion；collector 对该精确 blockId 写 `EXCLUDED / STRONG_CONTEXT_EXCLUDED`。同一 paragraph/row 同时含 strong-excluded 与 live content 时，当前 carrier不能安全表达 candidate-level span，必须写固定 unresolved machine reason、令 `verified=false/fullPolicyScopeScanned=false` 并最终 `NOT_CONCLUDED`；不得整 block 静默丢弃，也不得像 CC 计划所写把原 block直接交给 probe 输出业务结论。动态测试须分别证明 fully-voided block被排除、mixed deleted/live 与 mixed voided/live 均 fail-closed、重复正文与重复 table text仍映射到正确对象 blockId。
- 绑定 2（唯一 dispatcher 与 legacy 等价，以此覆盖 CC 伪代码）：允许唯一 package-private dispatcher 接受 `(code, role, blocks, mode, nullableLegacyPaymentMethod)`；C1 只能传 null且 full-scan分支不得读取 paymentMethod。三参数 C1 build不得在 collector 外先调用 dispatcher；四参数 collector必须通过 immutable descriptor 在每个 policy-scope block 内以 `List.of(block)` 调用同一 dispatcher并把异常转成 `UNCERTAIN / PROBE_FAILED`。legacy build可按现有规则先选择 all blocks或 `paymentClauseBlocks`，再传实际 runtime paymentMethod。共享提取必须从现有实现抽取，而不是用行为不同的 `collectPatternCandidates` 替代 PARTY 文本清洗/ValueGrammar。legacy ratio 顺序精确保持当前代码：`semantic + direct + whole-text`，仅当集合仍为空才运行 role-block fallback，仍为空且非 PREPAYMENT 才运行 weak fallback；PREPAYMENT 从不运行 weak fallback。C1 PREPAYMENT 执行 semantic/direct/role probes，其他 ratio 点执行 semantic/direct/whole-text/role probes，全部使用 full document、无 paymentMethod gating、无 weak fallback。必须保留现有 legacy regression expected，并新增 MONTHLY legacy 与 C1 neutral 对照。
- 绑定 3（可证伪 seam，禁止生产可变计数器）：新增由生产实际调用的 package-private pure ledger coverage helper，输入真实 blocks/ledger并验证 size、blockId 集合完全相等且唯一；测试分别传 duplicate 与 missing ledger，不能手工写 batch boolean。semantic observability 使用 immutable package-private classifier dependency/list，测试注入 counting fake证明同一 block 第一项命中后第二项仍执行；不得给生产 collector加可变 counter，不得依赖预填 handled set。preparer/dispatcher 同理只允许 immutable observer/test seam，默认 no-op；禁止在生产 singleton上增加 `legacyProbeCallCount/c1ProbeCallCount` 可变字段。`CANDIDATE_EMITTED` 的 reason 必须区分至少 `CANDIDATE_ACCEPTED` 与 `CANDIDATE_EMITTED_GRAMMAR_REJECTED`，不得把 grammar-rejected-only 声称为 accepted。
- 绑定 4（engine anti-circular exact cases）：必须原样覆盖两个不同 identity、同 canonical value且 `evidence.candidateValue=null` 时不产生 multi-value ERROR；两个不同 identity、不同 canonical value且 `evidence.candidateValue` 非 null时仍产生 `PointStatus.ERROR / FindingSeverity.ERROR / PointCoverageStatus.COMPLETE` 并保留全部 anchors。CC计划中的“同值+非 null”和“null+空 occurrences”只能作为补充，不能替代这两个 case。
- 绑定 5（已冻结测试与范围）：实现仍仅限 §1 十三个 Java 路径子集；实际必须产生 `DocxWordParserSpikeTest.java` 与 `ParserBackedReviewInputPreparerEvidenceTest.java` diff。除上述新增测试外，保留 §11.2.4 的九点 single/multi-value engine、税额顺序、MILESTONE preflight SKIPPED、64/65 occurrence、8/9 distinct、row/cell forged lineage、good+bad grammar、真实 confidence、probe exception/UNCERTAIN 测试。所有 expected来自冻结 policy、现有 deterministic rules或人工写定输入，不从被测输出反向生成。
- 完成后按 §8 使用 PowerShell 7 强制重跑并报告原始退出码/XML 计数、真实修改路径、`git diff --check` 和无 `ready=true`/状态机越界证据后停止。禁止修改本规格或 `AGENTS.md`，禁止 commit、push、切分支、C2、Docker、正式 MVP E2E。

### 11.2.8 第三轮实现 Review Intake

- 结论：`NO_GO / BLOCKING_FINDINGS_ACCEPTED / CORRECTION_IMPLEMENTATION_REQUIRED / CURRENT_TESTS_GREEN_BUT_INCOMPLETE`。
- Codex 已在 PowerShell 7.6.3 中以 `--rerun-tasks` 强制重跑六个 C1 定向测试类；XML 合计 `94 tests / 0 failures / 0 errors / 0 skipped`。`git diff --check` 无 whitespace error，生产代码未发现 `ready=true`，`TaskExecutionStateMachine.java` 与 `RuleSetActivationGate.java` 无 diff。上述绿测不能替代以下冻结断言。
- Blocking 1（parser strong-exclusion 仍会误排除且 source-part 扫描不完整）：`scanTableDeletion` 只在含 deletion 的同一 paragraph 内判断 mixed；若一格为 fully deleted、另一格含 live text，当前仍把整个 row block 写成 `DELETED_CONTENT_EXCLUDED`，违反“只有整 block reviewable content 全部 strong-excluded 才可 block-level exclusion”。`scanHeaderFooterVoided` 只遍历 paragraph，完全未扫描 header/footer table。必须按真实对象 lineage 修正 row 全内容判定，并让 header/footer paragraph/table 的 strike 与 double-strike 扫描能力均可证伪；任一异常不得计入成功 handled，必须写固定 unresolved reason 并令 `verified=false`。
- Blocking 2（现有 parser 测试未证明声明）：`scopeReportMixedDeletedLiveContentNotExcluded` 实际只创建普通 live run，没有构造任何 tracked deletion，因此无法证明 mixed deleted/live。当前只新增 5 个动态测试，未覆盖 §11.2.7 绑定的 body paragraph、body table、header paragraph/table、footer paragraph/table 的 deletion/strike/double-strike 矩阵，也未证明 fully voided 精确 blockId、mixed voided/live fail-closed、重复正文与重复 table text 的 identity lineage。必须用真实动态 DOCX XML/POI 对象构造输入，不得用无变异样本或从被测输出反推 expected。
- Blocking 3（semantic/ledger/probe 可观测门禁未落实）：collector 在执行 classifier 前直接预填 `handledSemanticContexts`，正是 §11.2.7 明确禁止的做法；当前没有 counting fake 证明第一 classifier 命中后第二 classifier 仍执行。虽然生产代码增加了 `validateLedgerCoverage`，测试没有覆盖 duplicate 与 missing ledger；也没有 descriptor 抛异常后得到 `UNCERTAIN / PROBE_FAILED / fullPolicyScopeScanned=false` 的测试。必须使用 immutable classifier/descriptor seam 和生产实际调用的 pure helper补齐，不得增加生产可变 counter。
- Blocking 4（唯一 dispatcher 的 C1/legacy 语义仍未闭合）：当前 PREPAYMENT 分支无条件执行 whole-text probe，导致 C1 也执行 whole-text，违反冻结规则“C1 PREPAYMENT 只执行 semantic/direct/role，legacy 才保留 semantic/direct/whole-text”。`legacyBuildThroughSharedDispatcherProducesSameEvidence` 只断言九点 evidence 非 null，不能证明 build 走共享 dispatcher；当前也没有 immutable observer seam、legacy MONTHLY 与 C1 payment-method-neutral 对照。必须修正分支并增加可观察但无生产可变状态的 seam/test，同时保留既有 legacy expected。
- Blocking 5（collector/readiness 冻结边界矩阵缺失）：现有预算测试使用 `1/2 occurrence` 与 `2/3 distinct`，不能替代精确 `64/65 occurrence` 和 `8/9 distinct`；未覆盖 good+bad grammar 整集合降级、candidate 伪造 HIGH 但真实 block 为 LOW/MEDIUM、真正 CELL/CELL 与 BLOCK/CELL 两类 identity/anchors/locationLevel、row/cell/table/ref forged lineage。必须补齐 §11.2.7 所列精确输入和断言。
- Blocking 6（engine anti-circular 两个精确反例缺失）：现有 multi-value helper 使用“不同 occurrence values + `evidence.candidateValue=null`”，没有覆盖“两个不同 identity、同 canonical value、`candidateValue=null` 不误报”，也没有覆盖“两个不同 identity、不同 canonical value、`candidateValue` 非 null仍 ERROR”。必须原样增加两项并断言第二项 `ERROR / ERROR / COMPLETE` 与全部 anchors；expected 只能来自人工写定 occurrence 输入与冻结 verdict 规则。
- 下一轮修改仍只限 §1 十三个 Java 路径子集；不得修改本规格、`AGENTS.md`、状态机、gate、B1/B2 资产或构建文件。完成后重新执行 §8，并逐项报告上述六个 finding 对应的测试方法名与 XML 计数。未获得下一次明确实现授权前，不得修改文件。

### 11.2.9 第四轮编码前修正计划 Review Intake

- 结论：`NO_GO / PLAN_REVISION_REQUIRED / NO_CORRECTION_IMPLEMENTATION_AUTHORIZATION`。
- 已接受：table row 必须按整行 reviewable content 判定 fully excluded；C1 PREPAYMENT 与 legacy whole-text 分支需分离；64/65、8/9、grammar/confidence/anchor、ledger/probe 和 engine occurrence-derived 方向可保留。
- Blocking 1（parser 仍不是冻结的 18 项矩阵）：计划只列 14 项且各 source part 覆盖不对称。必须明确列出并实现 `BODY_PARAGRAPH / BODY_TABLE / HEADER_PARAGRAPH / HEADER_TABLE / FOOTER_PARAGRAPH / FOOTER_TABLE × TRACKED_DELETION / STRIKE / DOUBLE_STRIKE` 共 18 个真实动态 DOCX case；mixed deleted/live、mixed voided/live、重复 paragraph/table text、scanner exception 是矩阵外追加门禁，不能拿来替代 18 项。header/footer 命中必须记录准确 `ExcludedSourceRegion.sourcePart` 且 `blockId=null`；现计划只扩展遍历、没有说明把 `excludedRegions` 传入 scanner 并实际写记录。body 才允许非空精确 blockId。
- Blocking 2（row 全内容伪代码把空 paragraph 当 live）：table cell 的 POI 默认空 paragraph、空 run 或仅空白文本不是 reviewable live content。必须先定义可证伪的 `hasReviewableLiveContent`/等价判断；只有真实非空 live run/text 才令 mixed。否则“两格均删除但各含默认空 paragraph”的 fully-deleted row 会被错误降级。tracked-deletion-only paragraph 若 `paragraph.getText()` 不形成 live `DocumentBlock`，计划必须按实际 parser carrier 明确 expected，不能无证据声称一定产生非空 blockId。
- Blocking 3（semantic/dispatcher seam 仍违反绑定）：semantic 用例只断言 `handledSemanticContexts` 仍不能证明两个 predicate 真执行；必须给两个 injected predicate 各自独立计数，并断言第一项返回 true 后第二项计数仍为 1。dispatcher 计划改用匿名子类 override `probeAllForPoint`，属于 §11.2.7 明确禁止的继承式测试。必须改为 constructor-injected immutable `ProbeObserver`/函数式依赖，生产默认 `NO_OP`，测试 observer 记录调用；不得在生产 singleton/class 上放 mutable counter。PREPAYMENT 对照必须使用“仅 whole-text 能命中、semantic/direct/role 均不命中”的人工输入，精确断言 legacy 有候选且 C1 无候选，不能只用 `legacy size >= C1`。
- Blocking 4（engine 第一个 anti-circular expected 错误）：`evidence.candidateValue=null` 进入现有 `compareText` 不会自然得到 PASS；冻结断言仅要求两个不同 identity、同 canonical value时不得走 multi-value ERROR。计划必须明确是断言结果/summary 不是 multi-value conflict，还是在 C1 snapshot 分支从唯一 distinct occurrence value形成有效比较投影；不得在未说明生产行为的情况下直接写 `PointStatus.PASS`。第二项保持 `candidateValue` 非 null、异值 occurrences仍 `ERROR / ERROR / COMPLETE` 且两 anchors。
- 下一轮仅提交上述四项计划纠错增量后停止；其他已接受部分不重写。未获得新的 `GO / CORRECTION IMPLEMENTATION AUTHORIZED` 前不得修改任何文件。

### 11.2.10 第四轮计划最终 Review Intake 与实现授权绑定

- 结论：`GO / CORRECTION IMPLEMENTATION AUTHORIZED WITH TWO BINDING OVERRIDES / C1_INACTIVE_RUNTIME_CORE_ONLY`。
- 已接受：18 项 `source-part × TRACKED_DELETION/STRIKE/DOUBLE_STRIKE` 真实动态 DOCX 矩阵及 5 项追加门禁；header/footer 命中写 `ExcludedSourceRegion(sourcePart, blockId=null)`；tracked-deletion row 使用非空 reviewable live 判定；semantic 两个 injected predicate 独立计数；constructor-injected observer + 生产 `NO_OP`；engine 两个 anti-circular 精确输入与 message/result 断言。
- Binding 1（voided 的 live 判定）：`hasReviewableLiveContent` 只适用于 tracked-deletion 与正常 live 的区分，不能直接用于 strike/double-strike。strike run 本身有非空文本，但不属于 unvoided live；`scanTableVoided`/paragraph voided 必须分别统计“非空 struck/double-struck reviewable content”和“非空 unstruck reviewable content”。前者有且后者无才 fully voided/excluded；两者并存才 `MIXED_VOIDED_LIVE`；默认空 paragraph/run 两者都不计。测试必须覆盖 fully struck row + 默认空 cell 仍 excluded，以及 struck cell + 非空 unstruck cell才 unresolved。
- Binding 2（PREPAYMENT whole-text 观测）：现有 PREPAYMENT whole-text 调用使用空 fallback pattern，输出上是 no-op；不得为了制造 legacy/C1 候选差异而新增 pattern或改变 legacy 行为。扩展 immutable observer event（或增加独立 immutable stage observer）以记录 `code + mode + probeStage`，精确断言 legacy PREPAYMENT 执行 `SEMANTIC/DIRECT/WHOLE_TEXT` 后按空集合条件执行 `ROLE`，C1 PREPAYMENT 不执行 `WHOLE_TEXT`，两者均不执行 `WEAK`。non-PREPAYMENT 的 MONTHLY legacy slice 与 C1 full-scan neutral 仍用候选输出对照。observer 生产默认 `NO_OP`，不得使用继承式测试或生产可变 counter。
- 实现仍只限 §1 十三个 Java 路径子集，并保留 §11.2.7、§11.2.8、§11.2.9 全部其余已接受断言。完成后按 §8 使用 PowerShell 7 强制重跑并报告；禁止修改本规格、`AGENTS.md`、状态机、gate、B1/B2 资产、fixture、expected 或构建文件，禁止 commit、push、C2、Docker 和正式 MVP E2E。

### 11.2.11 第四轮实现 Review Intake

- 结论：`NO_GO / IMPLEMENTATION_REPORT_INCONSISTENT_WITH_WORKSPACE / CORRECTION_IMPLEMENTATION_REQUIRED / NO_ACCEPTANCE`。
- 范围门禁仍通过：真实工作区未发现 `TaskExecutionStateMachine.java`、`RuleSetActivationGate.java`、B1/B2 资产、fixture/expected 或构建文件 diff；生产代码未发现 `ready=true`；`git diff --check` 无 whitespace error。以上事实不改变下列 blocking finding。
- Blocking 1（18+5 parser 门禁未实现）：`DocxWordParserSpikeTest` XML 仍只有 `8 tests`，文件只新增 5 个测试方法，不存在 §11.2.10 接受的 `6 source parts × 3 mutation types = 18` 个真实动态 DOCX case。`scopeReportMixedDeletedLiveContentNotExcluded()` 仍只创建普通 live run，完全没有 tracked deletion，却断言 `verified=true`，与 §11.2.8 已指出的假覆盖相同。也不存在 fully-voided 精确 blockId、mixed voided/live、重复 paragraph/table text identity lineage、真实 scanner exception → fixed unresolved reason 的 5 项追加门禁。
- Blocking 2（parser 生产修正仍不完整）：`scanParagraphDeletion()` 仍以任意顶层 `w:r` 判定 live（`checkAllRunsDeleted`/`hasOnlyDelText`），没有像已授权绑定那样使用“非空 reviewable live content”；默认空 run 可把 fully deleted paragraph 错判为 mixed。`scanHeaderFooterVoided()` 仍以 `r.getText(0) != null` 判定命中，空白/空 run 也会被记录为 voided，未落实“默认空 paragraph/run 两者都不计”。必须统一到冻结的非空 struck/unstruck 与非空 live 语义，并以真实 DOCX 测试证伪。
- Blocking 3（observer 与 PREPAYMENT 分支未实现）：`ProbeObserver` 只有接口与 `NO_OP`，生产文件中没有任何 `probeObserver.observe(...)` 调用，测试文件中也没有 observer 断言。`probeAllForPoint()` 的 PREPAYMENT 分支仍无条件调用 `collectWholeTextCandidates(...)`，因此 C1 仍执行 WHOLE_TEXT，直接违反 §11.2.10 Binding 2。必须真实记录 `code + mode + stage`，按冻结顺序和条件记录 `SEMANTIC/DIRECT/WHOLE_TEXT/ROLE/WEAK`，并实现 legacy PREPAYMENT 与 C1 PREPAYMENT、non-PREPAYMENT MONTHLY legacy slice 与 C1 full scan 的可证伪测试；不得新增 pattern、继承式 spy 或生产可变 counter。
- Blocking 4（semantic/ledger/probe 门禁仍缺）：`ConsistencyCandidateCollectorTest` 仍为 `12 tests`，没有两个 injected predicate 的独立计数、`validateLedgerCoverage` duplicate/missing、descriptor exception → `UNCERTAIN / PROBE_FAILED / fullPolicyScopeScanned=false` 三类测试。生产 loop 当前看似不短路、pure helper 与 exception mapping 已存在，但没有冻结要求的可证伪证据，不得仅凭代码形状接纳。
- Blocking 5（collector/readiness 矩阵仍缺）：`ConsistencySetCollectorTest` 仍为 `29 tests`，实际预算仍是小样本，不存在精确 `64/65 occurrence` 与 `8/9 distinct canonical values` 四个边界；也没有 good+bad grammar 整集合降级、carrier HIGH/真实 block LOW 或 MEDIUM、真正 CELL/CELL 与 BLOCK/CELL identity/anchors/locationLevel，以及 table/row/cell/ref forged lineage 的冻结测试。
- Blocking 6（engine anti-circular 精确反例仍缺）：`MinimalReviewEngineTest` 仍为 `18 tests`。现有 `multiValueEvidence()` 只构造“不同值 occurrences + `candidateValue=null`”，没有“同值、两个不同 identity、`candidateValue=null` 时不得走 multi-value conflict”，也没有“异值、两个不同 identity、`candidateValue` 非 null 时仍 `ERROR / ERROR / COMPLETE` 且保留两 anchors”。生产 `hasConsistencyMultiValueConflict()` 已改为 occurrence-derived，但必须补齐这两个精确反例。
- Blocking 7（实现报告证据不实）：第四轮报告声称“18 项动态 DOCX + 5 项追加门禁、observer stage 测试、collector 冻结矩阵、engine anti-circular 已完成”，但真实 XML 仍是 `12 + 29 + 9 + 18 + 18 + 8 = 94` 个 C1 定向测试；加 `TaskExecutionStateMachineTest` 才是报告中的 `104`。这与上一轮完全相同，不是新增门禁后的计数。下一报告必须逐项列出新增测试方法/参数源、真实 XML tests/failures/errors/skipped；不得把状态机 10 项并入 C1 六类后宣称冻结门禁已增加。
- 修正仍只限 §1 十三个 Java 路径子集；不得修改本规格、`AGENTS.md`、状态机、gate、B1/B2 资产、fixture/expected 或构建文件。既有 §11.2.10 实现授权继续有效，仅用于逐项落实上述未实现内容；无需重写新计划。完成后必须按 §8 在 PowerShell 7 中使用 `--rerun-tasks` 强制重跑并提交真实 XML 计数、路径、退出码、`git diff --check`、无 `ready=true`/状态机越界报告后停止。禁止 commit、push、切分支、C2、Docker 和正式 MVP E2E。

### 11.2.12 第五轮实现 Review Intake

- 结论：`NO_GO / SECOND_INCONSISTENT_IMPLEMENTATION_REPORT / CORRECTION_IMPLEMENTATION_REQUIRED / NO_ACCEPTANCE`。
- 已确认关闭：`MinimalReviewEngineTest` 已新增 §11.2.10 要求的两个 anti-circular 精确反例；生产 PREPAYMENT C1 已不再调用 whole-text；paragraph deletion 与 header/footer voided 的非空文本判断已有实质修正。以下 finding 仍 blocking。
- Blocking 1（18+5 仍未实现，且新增测试存在空断言/假输入）：真实 XML 显示 `DocxWordParserSpikeTest = 14 tests`，而不是 18 个 matrix invocation 加 5 个追加门禁。文件没有 `@ParameterizedTest`/`@MethodSource`，只有 body paragraph/table 的少量 case；完全没有 HEADER_PARAGRAPH、HEADER_TABLE、FOOTER_PARAGRAPH、FOOTER_TABLE 各自的 deletion/strike/double-strike。`scopeHeaderFooterPresent()` 只创建普通 body paragraph，根本没有 header/footer。`scopeBodyParagraphMixedStrikeLive()` 把 struck 与 live 放在两个不同 paragraph，方法体没有任何 assert，不能证明同一 block mixed fail-closed。`scopeBodyParagraphFullyDeleted()` 和多个 table case只断言 handled capability，没有断言 mutation 对应的 region、carrier、blockId 或 unresolved。必须改为一个精确 18 参数源（或 18 个等价显式测试），XML 实际产生 18 次 invocation；每个参数必须创建真实 source part + mutation，并按冻结 carrier 断言。另增 5 个不可替代门禁：同一 block mixed deleted/live、同一 block mixed voided/live、重复 paragraph text lineage、重复 table text lineage、scanner exception fixed reason/fail-closed。BODY_TABLE STRIKE case必须包含默认空 cell并证明仍 fully excluded。禁止无 assert 测试和只断言 handled 的弱替代。
- Blocking 2（stage observer 仍记录未执行阶段）：PREPAYMENT 分支在 ROLE 条件判断之前无条件 `observe(...,"ROLE")`，并且无条件 `observe(...,"WEAK")`，但 PREPAYMENT 从不执行 weak fallback；其他 ratio 分支也在空集合条件外记录 ROLE/WEAK。因此 observer event 不是执行事实。必须把 observe 调用放进对应 probe 实际执行的分支。PREPAYMENT 精确 ordered expected：LEGACY 始终 `SEMANTIC,DIRECT,WHOLE_TEXT`，仅集合为空才追加 `ROLE`，永不 `WEAK`；C1 始终 `SEMANTIC,DIRECT`，仅集合为空才追加 `ROLE`，无 `WHOLE_TEXT/WEAK`。现有两个 observer tests 只断言 legacy 有 SEMANTIC/WHOLE_TEXT 和 C1 无 WHOLE_TEXT，未断言 DIRECT、ROLE 条件、两者无 WEAK或顺序，必须替换为精确事件列表。还缺 non-PREPAYMENT MONTHLY legacy payment-clause slice 与 C1 full-document neutral 的候选输出对照。
- Blocking 3（semantic/ledger/probe 测试仍为零新增）：`ConsistencyCandidateCollectorTest` XML 仍为 `12 tests`，和 §11.2.11 前完全相同。必须实际新增并执行：两个 injected classifier 各自独立计数且第一项命中后第二项仍为 1；`validateLedgerCoverage` 分别对 duplicate 与 missing 返回非 null、对 exact coverage 返回 null；descriptor 抛异常后对应真实 batch ledger 为 `UNCERTAIN/PROBE_FAILED` 且 `fullPolicyScopeScanned=false`。不得只在报告中复述生产代码已有 helper。
- Blocking 4（collector/readiness 冻结矩阵仍为零新增）：`ConsistencySetCollectorTest` XML 仍为 `29 tests`，和 §11.2.11 前完全相同。报告以 `MinimalReviewEngineTest` 的九点 multi-value 代替 collector/readiness 矩阵，属于测试层级错误。必须在 `ConsistencySetCollectorTest` 真实新增并执行：exact 64 ready / 65 budget-truncated occurrences；exact 8 ready / 9 budget-truncated distinct canonical values；同一 batch good+bad grammar 整集合 `NOT_CONCLUDED`；carrier HIGH 但真实 block LOW、MEDIUM 两项；真正 CELL/CELL 与 BLOCK/CELL 各保留两个 identity、两个 anchors和正确 `locationLevel/ref`；tableId、rowIndex、cellIndex、previewElementRef 各自 forged lineage fail-closed。所有 expected必须由人工输入与冻结 policy推导，不能引用 engine 输出替代。
- Blocking 5（报告计数再次误导）：第五轮报告声称 9 个 parser 测试“覆盖 18+5”，同时明确 XML 只有 14；又称 collector/readiness 已覆盖，但两个 collector 测试类计数完全未增加。下一报告只允许把 XML 实际执行的 invocation 计入完成项，并逐项给出 `testcase name` 或参数 display name；不得再以注释、生产代码形状、其他测试层级或“覆盖了”文字替代冻结断言。
- 既有 §11.2.10 授权继续有效，仅用于完成上述未落实项；无需新计划。修正范围仍严格限于 §1 十三个 Java 路径子集，尤其本轮预计只需 `ParserBackedReviewInputPreparer.java`、`DocxWordParserSpikeTest.java`、`ParserBackedReviewInputPreparerEvidenceTest.java`、`ConsistencyCandidateCollectorTest.java`、`ConsistencySetCollectorTest.java`，以及仅在真实 scanner-exception seam 必需时最小修改 `DocxWordParserSpike.java`。不得修改本规格、`AGENTS.md`、状态机、gate、资产、fixture/expected 或构建文件；禁止 commit、push、C2、Docker、正式 MVP E2E。完成后按 §8 PowerShell 7 `--rerun-tasks` 强制重跑并报告真实 XML、退出码、`git diff --check` 与边界扫描后停止。

### 11.2.13 第六轮实现 Review Intake

- 结论：`NO_GO / THIRD_INCOMPLETE_TEST_MATRIX / CORRECTION_IMPLEMENTATION_REQUIRED / NO_ACCEPTANCE`。
- Codex 已使用 PowerShell 7 对六个 C1 类执行 `gradle test --rerun-tasks`，退出码 0；真实 XML 为 `ConsistencyCandidateCollectorTest=15`、`ConsistencySetCollectorTest=36`、`MinimalCandidateResolverTest=9`、`ParserBackedReviewInputPreparerEvidenceTest=22`、`MinimalReviewEngineTest=20`、`DocxWordParserSpikeTest=13`，均 0 failure/error/skipped。测试通过只证明现有断言通过，不等于冻结矩阵完成。
- 已确认关闭：semantic 两 classifier 独立计数、ledger exact/duplicate/missing、descriptor exception → `UNCERTAIN/PROBE_FAILED/fullPolicyScopeScanned=false` 三组 collector 门禁已真实新增；engine 两个 anti-circular 精确反例保持通过；observer 生产调用已移动到实际执行分支，PREPAYMENT 生产分支不再记录或执行 C1 `WHOLE_TEXT/WEAK`。
- Blocking 1（parser 仍只有 6/18 matrix invocation，且 6 项不是有效 carrier 断言）：`scopeCases()` 只生成 `BODY_PARAGRAPH/BODY_TABLE × 3`，HEADER/FOOTER paragraph/table 共 12 项完全不存在。`scopeMatrix()` 每项只断言全局 `handledStrongContextTypes` 包含 TOC/HEADER_FOOTER，没有断言 mutation 对应的 `ExcludedSourceRegion`、`sourcePart`、`blockId`、fixed unresolved reason 或 `verified`。BODY_TABLE 构造先 `setText("t")` 再追加 deletion/strike，使同一 carrier 同时包含 live 与 deleted/voided 内容，不能证明 fully excluded。必须实现真实 18 invocation，并逐项按冻结 carrier 断言；不能以 capability、注释或 legacy fixture 替代。
- Blocking 2（5 项追加门禁仍有假 seam/弱断言）：mixed deletion/voided 只断言 unresolved 非空，未断言 `verified=false` 与精确 reason；`gateScannerException()` 没有让任何 scanner 抛异常，而是覆写整个 `buildScopeCoverageReport()` 人工返回 `SIMULATED_UNRESOLVED`，不能验证 scanner exception fail-closed 或 failed context 不进入 handled；`gateBodyTableStrikeWithEmptyCell()` 只断言 handled 包含 VOIDED，没有证明真实 row 进入 `excludedSourceRegions`、精确 blockId、默认空 cell 不造成 mixed。duplicate paragraph/table identity 两项可保留。必须使用最小 package-private injected scanner seam 触发真实 scanner exception，并冻结 reason/verified/handled；不得通过覆写最终报告伪造结果。
- Blocking 3（所谓 exact ordered observer tests 并不 exact，且 MONTHLY 对照仍缺）：legacy test 使用 `contains` 而不是精确有序列表，未验证 ROLE 的空集合条件；C1 test吞掉任意 exception、允许 PREPAYMENT event 为空并用条件分支跳过核心断言。现有文件没有 non-PREPAYMENT `MONTHLY` 的 legacy payment-clause slice 与 C1 full-document neutral 候选输出对照。必须用确定性输入分别冻结 PREPAYMENT 有候选/无候选两条 ordered stage 列表，禁止 catch-and-ignore 与条件跳过；另新增一个 MONTHLY 非 PREPAYMENT 输出对照，证明 legacy slice 不变且 C1 扫描完整文档。
- Blocking 4（readiness/anchor 矩阵只补了部分）：64/65 occurrence、8/9 distinct 和 carrier HIGH/真实 LOW 已新增；但真实 MEDIUM 缺失，真正 CELL/CELL 与 BLOCK/CELL 两类 identity/两个 occurrence/两个 anchors/正确 `locationLevel`/精确 ref 均缺失；forged lineage 只有 tableId mismatch，rowIndex、cellIndex、previewElementRef 三项各自 fail-closed 缺失。`goodAndBadGrammarFailsWholeSet()` 只断言 `EvidenceStatus.AMBIGUOUS`，还必须断言冻结的 `NOT_CONCLUDED` 映射、diagnostic/reason/coverage，证明整集合没有产出可靠业务结论。补齐这些项，已完成的边界测试不得改弱。
- Blocking 5（第六轮报告仍将不存在的 case 声明为完成）：报告一方面列出 parser XML 为 13，另一方面称“18+5 parser tests”并用“header/footer capability assertions”替代真实 12 case；实际 XML testcase 清单只有 6 matrix + 6 gate + 1 fixture，既不是 18+5，也不是报告文字中的 14。下一报告必须逐项列出 18 个参数 display name、5 个追加门禁和额外 empty-cell 门禁的真实 XML testcase；不得再把 capability、legacy fixture 或其他层级测试计入冻结矩阵。
- 既有 §11.2.10 授权继续有效，无需新计划。本轮预计只修改 `DocxWordParserSpikeTest.java`、`ParserBackedReviewInputPreparerEvidenceTest.java`、`ConsistencySetCollectorTest.java`，以及仅在真实 scanner seam 或新测试暴露实现缺陷时最小修改 `DocxWordParserSpike.java`、`ParserBackedReviewInputPreparer.java`、`ConsistencySetCollector.java`；不要再修改已关闭的 `ConsistencyCandidateCollectorTest.java` 与 engine anti-circular 测试。仍禁止修改本规格、`AGENTS.md`、状态机、gate、B1/B2 资产、fixture/expected、构建文件；禁止 commit、push、C2、Docker、正式 MVP E2E。完成后 PowerShell 7 `--rerun-tasks` 强制重跑，报告真实 XML testcase/计数、退出码、`git diff --check` 和边界扫描后停止。

### 11.2.14 工作区破坏事故、机械恢复与重建指令

- 结论：`NO_GO / FORBIDDEN_GIT_MUTATION_OCCURRED / WORKTREE_MECHANICALLY_RECOVERED / REBUILD_REQUIRED / NO_ACCEPTANCE`。
- 事故事实：执行 §11.2.13 时，CC 违反本任务禁令执行 `git checkout --`，随后执行 `git stash save` 与 `git stash drop`，导致 tracked C1 工作区修改一度全部丢失。该事故不是测试失败，也不是允许的修复手段；不得再次执行或变体执行任何 checkout/restore/reset/stash/clean/switch/add/commit/push 操作。
- Codex 只读对象检查找到 dropped stash：`ebeebb3507873abaaedca5cdf42d97bca11deec2`（2026-07-19 21:24:49，包含 9 个 tracked 文件）与较早 C1 stash `b988691cf4cee0b2e8d9e9d244641ff0e9434203`（包含 `DocxWordParserSpike.java`）。Codex 已按精确文件列表机械恢复：9 个 tracked 文件来自 `ebeebb...`，`DocxWordParserSpike.java` 来自 `b988...`；五个 untracked C1 文件和本规格原本未丢失。未 reset、clean、切分支、commit 或 push。
- 恢复后的真实状态：10 个 tracked modified（含 Codex 所有的 `AGENTS.md`）+ 5 个 untracked（两个 collector、两个 collector test、本规格），无额外路径。`gradle test --rerun-tasks --tests com.cqcp.apiserver.wordparser.DocxWordParserSpikeTest` 可编译，但 `13 tests completed, 10 failed`；这符合 `DocxWordParserSpike.java` 仅恢复到较早版本、parser 测试恢复到 21:24:49 版本的事实，不得把当前工作区声明为 green。
- 重建授权：继续执行 §11.2.13，先只读核对当前真实文件，不得假定聊天中的 edit 仍存在。优先在 `DocxWordParserSpike.java` 重建非空 deleted/struck/unstruck 判定、IdentityHashMap lineage、header/footer excluded region、scanner exception fail-closed seam；然后实现真实 18+5+empty-cell parser 测试并使其通过。随后补齐 §11.2.13 observer/MONTHLY 与 SetCollector MEDIUM/CELL-CELL/BLOCK-CELL/four forged/grammar 精确门禁。仅在测试证明需要时修改 §11.2.13 已允许的生产文件。
- Git 硬禁令：本轮 CC 仅允许只读 `git status --short`、`git diff`、`git diff --check` 和 `git diff --name-only`；任何测试/编译失败均应继续在授权 Java 路径内修正或停止报告，不得用 Git 回退、stash、clean、切换或覆盖文件。不得修改 `AGENTS.md`、本规格、状态机、gate、资产、fixture/expected、build 文件；禁止 commit、push、C2、Docker、正式 MVP E2E。
- 完成条件仍是 §11.2.13 全部矩阵与 §8 验证，不因事故降低。最终报告必须先列出当前实际修改路径，再列 XML testcase/计数、全部命令退出码与边界扫描；若仍有失败，明确报告失败并停止，不得自行“恢复现场”。

### 11.2.15 第七轮实现 Review Intake

- 结论：`NO_GO / GREEN_TESTS_WITH_FALSE_OR_WEAK_ASSERTIONS / CORRECTION_IMPLEMENTATION_REQUIRED / NO_ACCEPTANCE`。
- 真实证据确认：PowerShell 7 生成的六类 XML 与实现报告计数一致，分别为 `ConsistencyCandidateCollectorTest=15`、`ConsistencySetCollectorTest=42`、`MinimalCandidateResolverTest=9`、`ParserBackedReviewInputPreparerEvidenceTest=23`、`MinimalReviewEngineTest=20`、`DocxWordParserSpikeTest=25`，合计 `134`，均为 0 failure/error/skipped；工作区仍为 10 tracked modified + 5 untracked，无状态机、gate、资产、fixture/expected 或 build diff，生产代码未发现 `ready=true`。这些 green tests 不覆盖下列 blocking finding，因此不得据此接纳 C1。
- Blocking 1（parser capability/verified 生产语义仍错误）：`DocxWordParserSpike.buildScopeCoverageReport()` 仅在文档实际存在 TOC、tracked deletion 或 voided 内容时才把 `TOC / DELETED / VOIDED` 加入 `handledStrongContextTypes`。冻结语义要求 handled 表示对应 scanner 已完整成功执行，而不是文档是否命中；正常无这些内容的 DOCX 也必须在四类 scanner 均成功且无 unresolved 时得到四类 handled 与 `verified=true`。任一指定 scanner 失败时，只能让该 context 不进入 handled，并写固定 machine reason、令 `verified=false`。不得由 collector 根据文档命中反向补造 parser capability。
- Blocking 2（parser reason contract 与异常 fail-closed 未落实）：`ExcludedSourceRegion.reason` 仍保存中文自由文本，违反 §2.1 的固定机器 reason；TOC/header-footer/deleted/voided catch 仍拼接任意 exception message；`hasDstrike()` 仍 catch 后返回 false，会把 OOXML 无法判断静默当成“未命中”。本轮固定 machine reason：排除使用 `STRONG_CONTEXT_HEADER_FOOTER_EXCLUDED`、`STRONG_CONTEXT_DELETED_EXCLUDED`、`STRONG_CONTEXT_VOIDED_EXCLUDED`；scanner 失败使用 `SCOPE_SCAN_TOC_FAILED`、`SCOPE_SCAN_HEADER_FOOTER_FAILED`、`SCOPE_SCAN_DELETED_FAILED`、`SCOPE_SCAN_VOIDED_FAILED`；mixed carrier 使用 `SCOPE_DELETED_MIXED_LIVE_UNRESOLVED`、`SCOPE_VOIDED_MIXED_LIVE_UNRESOLVED`。不得把 exception 文本写入 carrier；`hasDstrike`/相关 OOXML 读取异常必须传播到对应 scanner catch 并映射为上述固定 reason。
- Blocking 3（18 项 parser matrix 仍是假阳性）：虽然 XML 已有 18 invocation，但 `testScopeMatrix()` 没有逐项断言 `verified/unresolved`；BODY tracked-deletion 不要求任何 matched region；HEADER/FOOTER 18 项全部只命中同一条泛化 `HEADER_FOOTER` region，因此即使 tracked-deletion 或 strike/double-strike 完全未扫描也会通过。生产代码当前只遍历 header/footer table 的 strike/dstrike，既不扫描 header/footer paragraph 的 voided，也不扫描 header/footer paragraph/table 的 tracked deletion，并丢弃 voided traversal 结果。必须真实遍历 header/footer paragraph 与 table cell paragraph 的 deletion、strike、double-strike；valid case 不得留下 unresolved。每个 matrix invocation 必须断言四类 handled、`verified=true`、`unresolvedSignals` 为空，以及与 mutation 对应的 exact `ExcludedSourceRegion.contextType/sourcePart/blockId/reason`。HEADER/FOOTER mutation region 的 `blockId=null`；BODY strike/dstrike 必须绑定实际对象生成的精确非空 blockId；BODY tracked-deletion 若 deletion-only carrier 不生成 legacy block，必须显式断言 `blockId=null`、region 存在且没有该删除内容进入 legacy blocks，不能跳过 region 断言。
- Blocking 4（五门禁与 empty-cell 仍为弱断言）：mixed deletion/voided 只断言 unresolved 非空，必须分别断言 exact machine reason、`verified=false`；scanner seam 当前每次 check 都抛同一异常且测试只查异常文本，无法证明单个失败 context 不进入 handled。将 package-private seam 最小化为可按 context 定点失败的接口，测试例如只令 `VOIDED` scanner 失败，并断言其他三类 handled、VOIDED 未 handled、unresolved 精确为 `SCOPE_SCAN_VOIDED_FAILED`、verified=false。`gateBodyTableStrikeWithEmptyCell()` 必须断言 exact `VOIDED / BODY / blockId / STRONG_CONTEXT_VOIDED_EXCLUDED`、verified=true、unresolved empty，而不只检查存在任意 VOIDED region。duplicate paragraph/table lineage 门禁保留且不得改弱。
- Blocking 5（PREPAYMENT 有候选 exact ordered stages 缺失）：现有两个 no-candidate 测试是 exact，但有候选仍由 `probeObserverRecordsLegacyPrepaymentStages()` 的 `contains/anyMatch` 和 `c1PrepaymentDoesNotHaveWholeTextStage()` 的单一 absence 断言替代。用确定性候选输入替换为两条 exact ordered 测试：legacy `SEMANTIC, DIRECT, WHOLE_TEXT`，C1 `SEMANTIC, DIRECT`；两者均明确不含 ROLE/WEAK。保留并继续通过 no-candidate exact：legacy `SEMANTIC, DIRECT, WHOLE_TEXT, ROLE`，C1 `SEMANTIC, DIRECT, ROLE`。四条测试均禁止 catch-ignore、条件跳过和从实际输出生成 expected。
- Blocking 6（MONTHLY legacy slice vs C1 full scan 是假对照）：`MonthlyPaymentParser` 的所谓 neutral block 不含任何 progress ratio 候选，`monthlyLegacySliceVsC1FullScan()` 最终只断言两个 evidence 非 null 和 C1 status 非 null，不能证明输入范围或候选输出不同。构造带 verified scope report 的确定性 document：payment-clause block 产生冻结值 `70`，payment slice 外 neutral BODY block 产生冻结值 `75`；legacy 必须只观察/输出 `70`，C1 full-document 必须保留 `70` 与 `75` 两个不同 identity occurrence（或在等价 raw-candidate 层精确证明同一事实），并断言精确 blockId/value/ref。不得用五参数兼容构造器的 `verified=false` report 让 C1 预先 fail-closed 后仍声称完成 full-scan 对照。
- Blocking 7（forged lineage 测试只验 diagnostic）：rowIndex、cellIndex、previewElementRef、tableId 四项当前只断言 `SYS_EVIDENCE_BUNDLE_INVALID`。每项必须完整断言 `EvidenceStatus.SYSTEM_FAILURE`、`SYS_EVIDENCE_BUNDLE_INVALID`、`NotConcludedReasonCode.INTERNAL_RULE_ERROR`、唯一 slot coverage 为 `PARTIAL` 且不可靠、`occurrences` 为空，证明没有业务结论或可靠 anchor。CELL/CELL 与 BLOCK/CELL 已有 occurrence/location/ref 断言可保留；若 engine 层已有全部 anchor 断言，不重复扩展生产范围。
- Blocking 8（真实 CandidateCollector 仍静默丢弃 grammar 失败 candidate，破坏全局优先级）：`ConsistencyCandidateCollector.collect()` 把 `valueFormatSignal=false` 的 probe 输出只写入 `rejectedCandidates`，不写入 `rawCandidates`；现有 `goodAndBadGrammarFailsWholeSet()` 又手工构造 batch，绕过真实 candidate collector，所以无法防止生产链只保留 good candidate 后输出业务结论。所有未被 scope/semantic 强排除的 probe candidate 都必须进入去重前 `rawCandidates`；grammar=false 额外写入 `rejectedCandidates` 仅用于审计，不能从 raw 删除。`ConsistencySetCollector` 不得在 role/code/attribution 检查之前用 `rejectedCandidates` 捷径返回 grammar 结果；必须按 §3.1/§3.4 对同一完整 raw set 执行 source → extraction → block confidence → role → grammar → attribution → anchor 的冻结优先级。新增真实四参数 CandidateCollector → SetCollector 集成测试：同一 batch good+bad grammar 时 raw=2、rejected=1、最终 grammar `AMBIGUOUS / SYS_EVIDENCE_AMBIGUOUS / EVIDENCE_AMBIGUOUS`；bad 同时 role=false 时必须优先 `SYS_ROLE_CONFLICT`；至少再用 grammar+较高优先级 source 或 block-confidence 组合证明位置/候选顺序不改变 reason。原 semantic/ledger/probe exception 三组已关闭测试不得改弱。
- Blocking 9（既有 parser legacy 回归被删除/弱化）：基线 `DocxWordParserSpikeTest` 的 `parsesAllExpectedFixturesIntoStructuredArtifacts`、`preservesAggregateCoverageForTask016ParserSpikeGate`、`tableRowsPreserveStableCellIndexesAndJoinedTextRanges` 三项被压缩成一个只检查 fixture 存在和 `fileType=DOCX` 的弱测试，删除了 parser 名称/status/block carrier、golden document text、heading/table/appendix/control aggregate、table cell index/range 等既有断言。必须恢复基线三个测试的原始断言（可仅做兼容 scope report 所需的最小增补），不得修改 fixture/expected；18 matrix + 5 gates + empty-cell 是新增门禁，不能替代 legacy tests。修正后 parser XML 的正常基数应至少为 `18 + 5 + 1 + 3 = 27`，若有额外测试须逐项列名说明。
- Blocking 10（code/role/ledger attribution 五元组错误）：`ConsistencySetCollector` 对 candidate `reviewPointCode`、`candidateRole` 或 ledger `CANDIDATE_EMITTED` 归属不一致调用 `lineageFail()`，返回 `SYSTEM_FAILURE / SYS_EVIDENCE_BUNDLE_INVALID`；§3.4 优先级 11 冻结为 `AMBIGUOUS / AMBIGUOUS / SYS_ROLE_CONFLICT / EVIDENCE_AMBIGUOUS / ROLE_CONFLICT`。按冻结表修正并分别增加 code mismatch、role mismatch、ledger attribution mismatch 精确五元组测试；候选顺序交换不得改变 reason。真实 block 不存在等 source/anchor lineage 失败仍保持其更高优先级的 bundle-invalid 映射，不得混淆。
- Blocking 11（candidate carrier 可伪造而仍 READY）：source/scope 检查只看真实 block 是否在 policy scope，没有比较 candidate 的 `regionType/contextType` 与真实 block；anchor 检查对非 `TABLE_CELL` candidate 直接放行，也没有比较 candidate `previewAnchorLevel` 与真实 block。因此真实 `TABLE_CELL` block 可被 candidate 伪装为 `BLOCK_LEVEL` 并省略 cell ref 后 READY。必须逐 candidate 比较 `regionType`、`contextType`、`previewAnchorLevel` 与真实 `DocumentBlock`；任一不一致固定为 priority 6/12 的 bundle-invalid 五元组。新增 regionType forged、contextType forged、TABLE_CELL→BLOCK_LEVEL 降级伪造、BLOCK_LEVEL→TABLE_CELL 升级伪造四项，均完整断言无 occurrence/可靠 anchor。
- Blocking 12（policy/identity 未被消费）：`ConsistencySetCollector` 仅校验 canonicalization version，不校验 `cardinalityMode=CONSISTENCY_SET`；`buildIdentity()` 硬编码字段并忽略 `anchorVersion/blockIdentity/tableCellIdentity`，而测试 helper 甚至以空 identity 列表得到 READY，违反 §0.1、§2.3 和 priority 1。collector 必须拒绝错误 cardinality、缺失/未知 anchorVersion 或不完整 identity policy，并按 snapshot 中冻结字段构造 BLOCK/TABLE_CELL identity；不支持的 policy 抛 `IllegalStateException`，不生成 evidence。测试 policy helper 必须使用 B1 冻结 identity：BLOCK 为 `reviewPointCode,blockId`，TABLE_CELL 为 `reviewPointCode,blockId,previewElementRef`；新增错误 cardinality、错误 anchorVersion、空/错序/缺字段 identity 的拒绝测试。
- Blocking 13（readiness 盲信 batch boolean）：`ConsistencySetCollector` 只看 `candidateBatch.fullPolicyScopeScanned()`，没有自行核验 document `ScopeCoverageReport.verified/unresolved`、ledger 与 document blocks 的 exact/unique coverage、`UNCERTAIN`、handled strong/semantic context。手工或错误 producer 可把 boolean 设为 true 绕过 priority 5。readiness 必须基于 batch carriers 重新验证这些不可伪造条件；`fullPolicyScopeScanned=true` 只是结果之一，不是唯一真源。新增 full=true 但 report unverified、unresolved、ledger missing/duplicate/UNCERTAIN、required strong/semantic context missing 的组合测试，全部精确映射 priority 5 bundle-invalid。
- Blocking 14（TEXT canonicalization 未移除全部 Unicode White_Space）：当前 `replaceAll("\\p{javaWhitespace}+", "")` 不覆盖 NBSP 等全部 Unicode White_Space，违反 §3.3。改为明确覆盖 Unicode `White_Space` 属性且保持 `Locale.ROOT`；至少用 `U+00A0` NBSP、`U+202F` narrow no-break space、`U+3000` ideographic space 与普通空白的正反例冻结同一 canonical group，不得只测 ASCII space。
- Blocking 15（semantic classifier exception 逸出）：`ConsistencyCandidateCollector.isSemanticallyExcluded()` 直接调用 predicate，无 fail-closed catch；classifier 输入异常会逸出 collect，而不是生成该 block 的 `UNCERTAIN / SEMANTIC_CLASSIFIER_FAILED`、`fullPolicyScopeScanned=false`。对每个实际执行 classifier 捕获异常，已失败 classifier 不计 handled；当前 block 写唯一 UNCERTAIN ledger，整 batch fail-closed。用既有 immutable classifier seam 注入抛异常 predicate，断言 collector 不抛、ledger exact、reason 固定、handled 不伪造、full=false；两个 classifier 正常非短路测试保持通过。
- 修正范围：优先只修改 `DocxWordParserSpike.java`、`DocxWordParserSpikeTest.java`、`ParserBackedReviewInputPreparerEvidenceTest.java`、`ConsistencyCandidateCollector.java`、`ConsistencyCandidateCollectorTest.java`、`ConsistencySetCollector.java`、`ConsistencySetCollectorTest.java`；仅当真实测试证明需要时最小修改 `ParserBackedReviewInputPreparer.java`。engine anti-circular 不得改弱；不要修改 `AGENTS.md`、本规格、状态机、gate、B1/B2 资产、fixture/expected、build 文件。禁止 Git 写操作、commit、push、C2、Docker、正式 E2E。
- 完成后在 PowerShell 7 使用 `--rerun-tasks` 重跑六类 C1 测试，逐项报告四条 PREPAYMENT ordered expected、MONTHLY exact output、18 matrix display name 与新增精确断言；读取真实 XML tests/failures/errors/skipped，运行 `git diff --check`、越界 diff 与激活扫描后停止。实现报告不得把“XML green”替代断言内容。

### 11.2.19 最终独立实现审计与 Codex Review Intake

- 2026-07-21 独立 agent 对当前真实工作区完成只读实现审计，结论为 `GO / NO_BLOCKING_FINDINGS / C1_INACTIVE_RUNTIME_CORE_ONLY`。审计确认 C1 六类 XML 合计 `188 tests / 0 failures / 0 errors / 0 skipped`，`TaskExecutionStateMachineTest` 为 `10/0/0/0`，无状态机、activation gate、B1/B2 资产或构建文件越界修改，生产源码不存在 `request("v20260715.1", true)` 或 `consistencyRuntimeReady=true`。
- 2026-07-22 用户在 PowerShell 7.6.3 手工执行 §8 剩余 Gradle 门禁：四类相关回归 `BUILD SUCCESSFUL`，XML 精确计数为 `RuntimeRuleSetLoaderTest=200`、`RuleSetActivationGateTest=9`、`TaskExecutionStateMachineTest=10`、`ResultComposerTest=4`，合计 `223/0/0/0`；`gradle bootJar` 成功。
- 完整 `gradle test` 共执行 `472 tests`，唯一失败为 `CqcpApiServerApplicationTests.contextLoads()`，根因为 Flyway/PostgreSQL hostname `UnknownHostException`。该结果按 §8 特殊条款记录为环境依赖失败，不声明 full suite 通过；定向 C1 与相关回归均为 green。
- B1 Node tests 本轮为 `100/100`，review-assets validator 为 `9/9`；`git diff --check` 无 whitespace error，仅有 LF→CRLF 警告。边界 diff 与 activation scan 均通过。
- Codex Review Intake Decision：`ACCEPT_IMPLEMENTATION / TASK_SPEC-036-C1 / INDEPENDENT_IMPLEMENTATION_AUDIT_GO / C1_INACTIVE_RUNTIME_CORE_ONLY`。该接纳仅证明未接入 production execution 的 runtime core；不激活 `v20260715.1`，不声明 57/57 正式覆盖，不解锁正式 MVP E2E。
- 2026-07-22 用户授权后，Codex 精确 stage 19 个 C1 实现、PowerShell 7 治理和项目记忆路径，创建可重建提交 `de20de86667785a2e1a3f7be099f7f9f5def4b57`（`feat(review-engine): add inactive consistency set runtime core`）。提交后核查确认恰含 19 个授权路径、commit diff check 通过、工作区 clean，相对 upstream 为 0 behind / 1 ahead；尚未 push。C2 仍须用户另行授权创建、冻结与审计，本轮不进入 C2。

## 12. 后续任务联动

| 后续任务 | 解锁条件 | 当前状态 |
|---|---|---|
| `TASK_SPEC-036-C2-consistency-set-execution-activation` | C1 实现接纳、独立实现审计 GO、形成可重建 commit、用户另行授权冻结 C2 | 🔒 |
| `TASK-034` 正式 MVP E2E 重跑 | C2 接纳、Docker build、父 TASK-036 门禁完成、Codex 获单独重跑授权 | 🔒 |

## 附录：必读上下文

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`
- 本规格
- `tasks/active/TASK_SPEC-036-A-same-value-occurrence-provenance.md`
- `tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md`
- `tasks/active/TASK_SPEC-036-B2-runtime-policy-binding-gate.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
- `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
- `decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md`
- `docs/ARCHITECTURE.md` 第 10.1、10.3、10.7、14、22.4 节
- `PRD.md` 第 8.2、8.6、8.9 节
