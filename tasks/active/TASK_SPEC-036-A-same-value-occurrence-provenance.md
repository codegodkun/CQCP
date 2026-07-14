# TASK SPEC — TASK_SPEC-036-A 同值 occurrence provenance carrier foundation
# 适用环境：Claude Code（DeepSeek 模型）— 统一执行环境
# 同一本地项目文件夹下与 CODEX 协作

> **版本**：v0.1
> **状态**：Merged via PR #32 / Codex ACCEPT_IMPLEMENTATION / Independent Implementation Audit GO
> **创建日期**：2026-07-14
> **起草**：Codex
> **执行环境**：Claude Code（DeepSeek 模型）
> **TASK_SPEC 类型**：execution
> **父任务**：TASK-036
> **关联 ADR**：ADR-016（Accepted）
> **所在分支**：`codex/task-036-a-consistency-set-provenance`

---

## 0. 任务摘要

**一句话任务**：在不改变普通 resolver 裁判、不引入 `CONSISTENCY_SET_READY`、不让新语义对现有 RuleSetVersion 生效的前提下，建立同一已选 exact `candidateValue` 多 occurrence 的只承载 provenance foundation：resolver 保留原始 occurrence，`PointEvidence` / engine 可显式承载并投影多 anchor；现有任务运行时仍使用 legacy 单 anchor 路径，待后续 B 批次完成版本门禁后才能激活。

### 0.1 角色与执行门禁

- 本规格只关联父任务 `TASK-036`，是 ADR-016 生产实现的第一批**未激活 carrier foundation**。
- Codex 负责冻结本规格、审查编码前规格映射计划、实现报告、测试和 `git diff`。
- Claude Code / DeepSeek 在 Codex 明确 `GO / IMPLEMENTATION AUTHORIZED` 前不得修改任何文件或运行测试。
- Claude Code / DeepSeek 不得 commit、push、merge、切换分支或修改本规格。
- 独立 agent 只做只读事实核查和规格/实现审计，不承担实现。
- 本规格通过不等于 TASK-036 完成，也不授权 TASK-034 正式 MVP E2E。

### 0.2 编码前规格映射计划（硬门禁）

修改代码前必须先输出并停止，至少包含：

```text
验收断言映射：
- §8 每条 Must Pass 对应的真实输入、生产路径、测试方法和可证伪结果

关键字段 / 信号计算：
- retainedOccurrences 的真实来源与不可变复制方式
- selectedValueOccurrences 如何只基于 HIGH selected candidate 的 exact candidateValue 形成
- BLOCK / TABLE_CELL occurrence identity，以及为什么没有 lineage 时不得推断 fallback
- PointEvidenceOccurrence 每个字段如何由 `fromSelectedCandidate` 从 EvidenceCandidate 直接投影
- 任一显式 carrier occurrence 缺可靠 block identity 时如何转为既有 SYS / NOT_CONCLUDED 且不生成空 anchor
- anchorsFor 如何保留顺序、去重并兼容 legacy 单 anchor PointEvidence
- 如何证明现有 preparer 不填充 occurrences、当前规则集不激活新 anchor 基数

明确不修改：
- §0.4 全部路径和语义

范围外风险：
- B/C 批次所需 versioned policy、scope completeness、budget、readiness 和可靠异值裁判

预计测试：
- §6 七个精确测试方法
- §9 两组定向命令与 XML 实际计数
```

未获得 Codex `GO / IMPLEMENTATION AUTHORIZED` 前只允许提交计划，不得编码。

### 0.3 唯一允许修改的文件

生产文件：

1. `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java`
2. `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`

测试文件：

3. `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolverTest.java`
4. `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`
5. `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngineTest.java`
6. `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ResultComposerTest.java`
7. `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/PersistentTaskResultStoreTest.java`

不得新增文件。若实现需要修改其他文件，立即 STOP 并报告，不得自行扩大范围。

### 0.4 明确禁止修改

- `ParserBackedReviewInputPreparer.java`、`ResultComposer.java`、`PersistentTaskResultStore.java`、`TaskExecutionStateMachine.java`
- parser、Word block / table span 结构、OpenAPI、controller、query service
- review-assets、RuleSetVersion manifest、runtime loader、数据库、migration、workflow、前端
- DOCX、XLSX、matrix、fixture、expected JSON、TASK-034 v1 正式 outputs
- 已接受 ADR、`docs/ARCHITECTURE.md`、PRD
- `CandidateResolutionStatus` / `EvidenceConfidenceLevel` / `PointStatus` 新枚举或 `CONSISTENCY_SET_READY`
- Gemma、模型、Prompt、RAG、外部 API、依赖或网络调用

---

## 1. 需求背景

### 1.1 已确认代码事实

- `EvidenceCandidate` 已包含 `blockId / sectionPath / regionType / tableId / rowIndex / cellIndex / previewElementRef`。
- 当前 resolver 先按不含 row/cell/ref identity 的 key 去重，再在唯一 fully-attributed exact value 时只返回 `fullyAttributed.getFirst()`。
- preparer 只把 selected candidate 投影为单值 `PointEvidence`。
- `PointEvidence` 只有一个 `blockId / previewElementRef`，`MinimalReviewEngine.anchorsFor()` 固定只生成一个 anchor。
- `ResultComposer` 已能保留点级 anchors，并只对顶层导航列表按 `blockId + previewElementRef` 去重；持久化与查询已经按列表读取，无需修改生产 composer/store/API。
- 当前没有 RuleSetVersion runtime policy、scope completeness、双预算或 readiness 数据，静态 review-assets 明确不绑定 runtime。

### 1.2 本批次边界

本批次只完成未激活的 carrier foundation：

```text
同一 HIGH selected exact candidateValue 的多个可靠 occurrences
→ CandidateResolutionResult
→ 可由显式构造路径写入的 PointEvidence anchor-ready occurrences
→ MinimalReviewEngine 对显式 carrier 的多 anchor 投影能力
→ 既有 ResultComposer / persistent JSON read / query 列表兼容回归
```

`ParserBackedReviewInputPreparer` 的现有生产路径不得填充 `PointEvidence.occurrences`，因此现有 RuleSetVersion、现有任务和新建普通任务仍输出 legacy 单 anchor。本批次不声称这些 exact strings 已是 ADR-016 的最终 canonical semantic value group。`exact candidateValue` 只表示当前 resolver 输入字符串逐字相等；versioned canonicalization / unit policy 及显式激活由后续 B 批次冻结。

### 1.3 强制 A/B/C 拆分

- **A（本规格）**：同值 occurrence provenance 的未激活 carrier foundation；普通生产路径及普通多异值语义不变。
- **B（后续，未冻结）**：新 RuleSetVersion 标识、显式 carrier activation、`cardinalityMode=CONSISTENCY_SET`、`maxCandidates`、`occurrenceBudget`、scope/exclusion、canonicalization/unit、配置校验与 `CONSISTENCY_SET_READY` preflight。
- **C（后续，未冻结）**：各审核点真实 scope/collector、可靠异值 point-local deterministic verdict、预算/coverage 降级与完整 snapshot/query 集成。
- TASK-034 正式重跑只能在 TASK-035 接纳、TASK-036 全部自身门禁完成且 Codex 获得单独正式授权后执行。

---

## 2. 冻结数据契约

### 2.1 CandidateResolutionResult 兼容扩展

保留现有字段与语义，并按以下顺序增加两个不可变列表：

```java
record CandidateResolutionResult(
        EvidenceConfidenceLevel confidenceLevel,
        Optional<EvidenceCandidate> selectedCandidate,
        List<EvidenceCandidate> retainedOccurrences,
        List<EvidenceCandidate> selectedValueOccurrences,
        String diagnosticCode,
        NotConcludedReasonCode notConcludedReason) {}
```

必须提供兼容构造路径，避免测试或包内调用方因新增字段被迫伪造 provenance。

字段语义：

- `retainedOccurrences`：`rawCandidates` 的不可变、有序快照，必须在现有 dedup 之前捕获；不因 candidate value、block、row/cell/ref 或 attribution status 删除元素。本字段是诊断与后续 B 批次输入，不直接生成业务 verdict。
- `selectedValueOccurrences`：仅当现有 resolver 按原规则得到 `HIGH + selectedCandidate` 时非空；来源只允许是 `rawCandidates` 中与 selected candidate 同 `reviewPointCode`、同 role、exact `candidateValue` 相等且 fully attributed 的候选。
- `UNKNOWN / MEDIUM / LOW / CONFLICTED` 的 `selectedValueOccurrences` 固定为空。
- 两个列表均须 `List.copyOf` 等价不可变；不得暴露可修改输入列表。
- 现有 `confidenceLevel / selectedCandidate / diagnosticCode / notConcludedReason` 计算顺序与结果不得改变。

### 2.2 PointEvidenceOccurrence

在 `MinimalReviewEngine.java` 包内新增 anchor-ready record，并提供包内 `fromSelectedCandidate(EvidenceCandidate)` 工厂：

```java
record PointEvidenceOccurrence(
        String candidateValue,
        String blockId,
        String evidenceSummary,
        List<String> sectionPath,
        String regionType,
        String confidence,
        String locationLevel,
        String previewElementRef) {}
```

工厂映射固定为：

```text
candidateValue    := candidate.candidateValue（逐字）
blockId           := candidate.blockId
evidenceSummary   := candidate.blockText
sectionPath       := candidate.sectionPath
regionType        := candidate.regionType
confidence        := HIGH
locationLevel     := blockId 非空时 BLOCK_LEVEL，否则 null
previewElementRef := candidate.previewElementRef（仅逐字保留 parser-issued ref）
```

约束：

- `sectionPath` 必须不可变且 null 转空列表。
- 字段只能按上述映射从具体 `EvidenceCandidate` 直接投影，不得从 structured field、fixture、expected 或 candidateValue 反向搜索来源。
- `candidateValue` 保留当前 exact resolver 值，只作 provenance；本批次不新增 canonicalization。
- `locationLevel` 延续当前 `BLOCK_LEVEL`，TABLE_CELL 精度由 parser-issued `previewElementRef` 表达；不得伪造新的 SourceAnchor 粒度枚举。

### 2.3 PointEvidence 兼容扩展

在现有 `PointEvidence` 最后增加：

```java
List<PointEvidenceOccurrence> occurrences
```

- canonical constructor 必须执行 null → empty 与不可变复制。
- 保留现有 legacy primary scalar 字段；对正常 HIGH 结果，这些字段继续来自 `selectedCandidate`，不得改变既有 candidateValue、status、slot coverage 或 evidence summary。
- 必须保留现有构造调用兼容性；legacy test/override 未提供 occurrences 时仍能通过旧 primary block/ref 生成原有单 anchor。
- `occurrences` 不进入外部 API 或持久化；A 只允许测试或后续已版本化调用方通过显式构造路径提供它，并由 engine 生成点级 `SourceAnchorSummary` 列表。现有 preparer 生产路径固定为空。

---

## 3. occurrence 捕获与投影规则

### 3.1 provenance 捕获顺序

1. `retainedOccurrences` 在 resolver 任何 dedup、distinct value grouping 或 selected projection 之前捕获。
2. resolver 按现有规则计算 confidence 与 selected candidate，不得因本规格把普通 distinct values 提升为业务裁判。
3. 只有现有结果为 HIGH 时，按 selected candidate 的 exact value 从 raw retained list 形成 `selectedValueOccurrences`。
4. A 不把 selected list 接入现有 preparer；该激活步骤留给 B 的新 RuleSetVersion 门禁。A 仅冻结 `EvidenceCandidate → PointEvidenceOccurrence` 的逐字段投影契约，供显式构造测试验证。

### 3.2 MVP occurrence identity

投影 anchor-ready occurrences 时固定：

```text
exact TABLE_CELL ref := ^table:[^/]+/row:[0-9]+/cell:[0-9]+$

TABLE_CELL identity := reviewPointCode + role + blockId + previewElementRef
BLOCK identity      := reviewPointCode + role + blockId
```

- 同一 identity 的重复抽取只计一次；保持第一次出现顺序。
- 当前 `EvidenceCandidate` 没有 extraction-lineage / fallback 标记，因此不得推断 row-only / null-ref candidate 是某个 cell candidate 的 fallback，也不得仅因同 block/value 存在 cell ref 就删除它。
- 不同 cell refs 即使 blockId、row 和 candidateValue 相同也必须分别保留。
- row-only / null-ref candidate 按 BLOCK identity 计数；同一 BLOCK identity 只保留第一次。它与同 block 的 TABLE_CELL 是不同 identity，二者均须保留。
- 不同 block 的同值 occurrences 必须分别保留。
- 不得用 candidateValue 搜索 parsed document、cell text 或 fixture 来补 `previewElementRef`。

### 3.3 不可靠 occurrence 门禁

- HIGH selected value 的每个 `selectedValueOccurrence` 必须至少具有非空 `blockId` 与现有 fully-attributed 三信号。
- 任一 selected occurrence 缺少最低可靠 block identity 时，不得静默删除后继续 PASS/WARNING/ERROR。
- 对显式提供的 carrier，engine 必须在业务裁判前沿既有 preflight 语义映射为 `EvidenceStatus.SYSTEM_FAILURE` 等价结果、diagnostic=`SYS_EVIDENCE_BUNDLE_INVALID`、required slot=`PARTIAL`，输出 `PointStatus.NOT_CONCLUDED`；不得新增诊断码或状态。
- 无可靠 block identity 的 occurrence 不得进入 `anchorsFor`，不得生成空 `blockId` anchor；对应点仅保留可可靠构造的 anchors，且整体仍为上述 SYS / NOT_CONCLUDED。
- row-only / null-ref 不等于缺 block identity；它只能降为 BLOCK occurrence，不能宣称 TABLE_CELL coverage。

### 3.4 anchorsFor

- 仅当调用方通过显式构造路径提供非空 `PointEvidence.occurrences` 时，按列表顺序为每个可靠 occurrence 生成一个 `SourceAnchorSummary`。
- `sourceOrigin / sourceExtractionMode / contextType` 继续使用 PointEvidence 已有字段；其他 anchor 字段来自 occurrence。
- 生成时按 `blockId + previewElementRef-or-empty` 防御性去重，保持第一次出现顺序。
- 若 occurrences 为空，必须回退现有 legacy primary block/ref 单 anchor 行为。
- PASS、WARNING、ERROR、NOT_CONCLUDED 均使用同一个 `anchorsFor`；不得只在 PASS 路径保留多 anchors。
- 现有 preparer 不提供 occurrences，因此所有当前生产任务仍走 legacy primary block/ref 单 anchor 分支；A 不得改变其 point-result anchor 基数。

---

## 4. 明确不改变的行为

- 普通 resolver 五档保持 `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN`；不得新增 readiness 状态。
- 普通 distinct candidate values 继续 `CONFLICTED + SYS_ROLE_CONFLICT + EVIDENCE_AMBIGUOUS`，不得生成业务 ERROR。
- 结构化字段与 selected exact value 的现有文本/数值/公式比较不变。
- 现有 slot coverage、SYS/Finding 分流、summary、snapshot status 计算不变；仅 §3.3 的无可靠 block identity 走既有 bundle-invalid hardening。
- `ResultComposer` 点级列表复制和顶层导航去重规则不变；A 的显式 carrier 测试只证明其列表兼容性，不代表现有规则集已激活多 anchor。
- 历史 snapshot JSON 不回填；旧单 anchor 结果继续可读。
- 现有 `ParserBackedReviewInputPreparer`、现有 RuleSetVersion 与普通任务的输出 anchor 基数不变。

---

## 5. STOP 条件

出现以下任一情况立即停止并报告：

- 需要新增/修改 RuleSetVersion runtime loader 或 review-assets 绑定。
- 需要在 A 内把 resolver 的 occurrence 列表自动接入现有 preparer，或以任何方式让当前规则集无条件输出多 anchor。
- 需要决定 `cardinalityMode / occurrenceBudget / maxCandidates / scope policy / canonicalization / unit policy` 的具体生产值。
- 需要引入 `CONSISTENCY_SET_READY`、可靠异值业务 ERROR 或新诊断码。
- 需要修改 ResultComposer、PersistentTaskResultStore、parser、OpenAPI、数据库或允许列表之外文件。
- 需要把 row ref 当 TABLE_CELL，或用 candidateValue / fixture 反查 cell。
- 需要硬编码 `CQCP-MVP-DOCX-001/002/003`、occurrenceNo 或人工 included 标记。
- 任一范围不完整、低置信或截断集合需要继续输出业务 PASS/ERROR。

---

## 6. 必须新增的精确测试

以下方法名必须存在并被 Gradle 发现：

```text
MinimalCandidateResolverTest
- sameExactValueRetainsDistinctBlockAndCellOccurrences
- distinctValuesRemainConflictedWithoutSelectedValueOccurrences

ParserBackedReviewInputPreparerEvidenceTest
- currentRuleSetPathDoesNotActivateOccurrenceAnchors

MinimalReviewEngineTest
- explicitCarrierPreservesAllEvidenceOccurrenceAnchorsForPassAndError
- unreliableExplicitOccurrenceDowngradesWithoutEmptyAnchor

ResultComposerTest
- findingAndSnapshotRetainAllPointOccurrenceAnchors

PersistentTaskResultStoreTest
- multiAnchorPersistenceJsonRemainsQueryable
```

必须覆盖：

1. 同 point/role/value、不同 block 的两个 fully attributed candidates：仍为 HIGH，selected occurrences 精确为 2。
2. 同 block、不同 parser cell refs、同值：两个 cell occurrences 均保留。
3. 完全相同 occurrence identity 重复输入：显式 carrier 点级只计一个。
4. 同 row 两个 cell 加一个 row/null-ref candidate：由于没有 fallback lineage，显式 carrier 点级恰好保留两个 cell anchors 与一个 BLOCK anchor。
5. 普通两个 distinct values：仍 CONFLICTED、selected empty、selectedValueOccurrences empty、无业务 ERROR。
6. 当前 preparer 即使 resolver 已保留多 occurrence，也不填充 `PointEvidence.occurrences`，仍生成 legacy 单 anchor，证明 A 未激活。
7. 显式 carrier 的 structured value 与唯一 exact value 相等：PASS 且点级 anchors 精确为全部可靠 occurrences。
8. 显式 carrier 的 structured value 与唯一 exact value 不等：ERROR，Finding 与点级结果均保留全部可靠 occurrences。
9. 显式 occurrence 缺可靠 block identity：`SYS_EVIDENCE_BUNDLE_INVALID / NOT_CONCLUDED`，且不生成空 block anchor。
10. compose 后同一点两个 cell anchors 仍为两个；包含同一点两个 anchors 的数据库行 JSON 经 `PersistentTaskResultStore` 反序列化并由 `TaskResultQueryService` 查询后仍为两个。这里只证明 read/query 兼容，不声称生产写入 round-trip；legacy 单 anchor 读取仍通过。

测试数据必须为测试内 synthetic candidates / parsed blocks / snapshots，不得修改 fixture 或 expected JSON。测试 expected 来自本规格冻结的 identity 与状态映射，不得从被测输出倒填。

---

## 7. 版本化与兼容要求

- 本批次不发布新 RuleSetVersion，不修改静态 review-assets，因此 carrier 不接入现有 preparer、不会对任何当前规则集或普通任务激活；版本化 policy 与激活门禁由后续 B 批次承接。
- 本批次新增 record 字段属于包内执行数据，不新增公共 API 字段，不修改数据库 schema。
- 历史 `PointEvidence` 构造与单 anchor 测试必须保持兼容。
- `SourceAnchorSummary`、`PointReviewResult`、`ReviewResultSnapshot` 字段形状不变，只允许列表基数从 1 增加到 N。

---

## 8. 验收标准

### Must Pass

- [x] diff 只包含 §0.3 七个允许文件中的必要文件。
- [x] provenance 在 resolver dedup / grouping / selected projection 之前捕获并不可变保存。
- [x] HIGH 同值 candidates 的 selected occurrences 不因同 block 同值而丢失真实 BLOCK / TABLE_CELL identity。
- [x] 未有显式 lineage 时不推断 fallback；同 block 的 BLOCK 与 TABLE_CELL 均保留，不合并不同 cells。
- [x] PointEvidence 保留 legacy primary projection并新增不可变 anchor-ready occurrences。
- [x] 任一显式 carrier occurrence 无可靠 block identity 时为既有 SYS / NOT_CONCLUDED，不继续业务裁判，也不生成空 block anchor。
- [x] 显式 carrier 的 PASS 与 ERROR 均在点级结果及 Finding 中保留所有可靠 occurrences。
- [x] 普通 distinct values 仍 CONFLICTED / NOT_CONCLUDED，无 readiness 或业务 ERROR。
- [x] compose、persistent JSON deserialization、query 的既有列表兼容回归通过；不声称写入 round-trip；历史单 anchor 行为不变。
- [x] 当前 preparer 不填充 occurrences，当前规则集与普通任务仍输出 legacy 单 anchor。
- [x] §6 七个精确测试实际发现执行；§9 第一组 XML tests >=47，第二组 tests=25，均 0 failures/errors/skipped。

### 同根因分批修复一致性

- [x] 与 ADR-016 一致：provenance 先于 dedup，点级 anchors 为真源，TABLE_CELL 只用 parser-issued identity。
- [x] A 与未来 B/C 的差异明确：A 只建立未激活 exact-value occurrence carrier，不声明最终 semantic canonical group、readiness 或生产多 anchor 已生效。
- [x] 不用 A 的多 anchor 结果提前宣称 57/57 coverage 或正式 E2E PASS。

### 评测 / fixture / expected 来源

- [x] 不修改或读取人工 included 标记作为生产输入。
- [x] 测试 expected identity 来自 ADR-016 与本规格，不依赖被测系统输出。
- [x] TASK-034 v1 outputs 只作为历史失败证据，不作为测试 expected 生成源。

### Must Not

- [x] 未发生：允许列表外存在 diff。
- [x] 未发生：新增 readiness、scope policy、budget、canonicalization、RuleSetVersion runtime 行为或 reliable distinct verdict。
- [x] 未发生：修改现有 preparer，或让当前 RuleSetVersion / 普通任务自动填充并输出多 occurrence anchors。
- [x] 未发生：修改 fixture/expected/DOCX/XLSX/matrix/formal outputs/API/数据库/workflow/ADR/ARCHITECTURE。
- [x] 未发生：candidateValue 反查 anchor、row ref 冒充 TABLE_CELL、静默删除不可靠 occurrence。
- [x] 未发生：运行正式 MVP E2E、外部模型、网络或安装依赖。
- [x] 未发生：Claude Code / DeepSeek commit、push、merge 或切分支。

---

## 9. 测试与验证命令

获得 Codex 实现放行后运行，不得设置 TASK-034 formal property/input：

```powershell
Set-Location apps/api-server
gradle test --rerun-tasks --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest" --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest" --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest" --tests "com.cqcp.apiserver.reviewengine.ResultComposerTest" --tests "com.cqcp.apiserver.reviewengine.PersistentTaskResultStoreTest" --tests "com.cqcp.apiserver.reviewengine.TaskResultQueryControllerTest"
# 读取 build/test-results/test/TEST-*.xml；实现前基线为 40，完成后必须 >=47，failures/errors/skipped 全为 0。

gradle test --rerun-tasks --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest" --tests "com.cqcp.apiserver.reviewengine.Task034MvpE2eAcceptanceHarnessTest"
# 读取 XML；tests 必须 =25，failures/errors/skipped 全为 0。不得设置 cqcp.task034.formal / formalInput。

Set-Location ../..
git diff --check
git status --short
git diff --name-status
git diff --stat
```

禁止运行无过滤模块全量测试来掩盖 test discovery，禁止正式 001/002/003、Docker、migration、网络或模型命令。

---

## 10. 实现报告

实现范围精确为 §0.3 七个文件，代码 diff 为 `621 insertions / 18 deletions`：

- resolver 在 dedup 前保存有序不可变 `retainedOccurrences`，全部退出分支携带该快照；仅 HIGH selected exact fully-attributed value 形成 `selectedValueOccurrences`。
- `PointEvidence` 增加兼容的不可变显式 carrier，`PointEvidenceOccurrence.fromSelectedCandidate` 按冻结字段直接投影。
- engine 对显式 carrier 执行可靠性 preflight、有序 BLOCK/TABLE_CELL identity 去重和 legacy fallback；不可靠 occurrence 映射既有 SYS / NOT_CONCLUDED 且不生成空 anchor。
- 生产 `ParserBackedReviewInputPreparer` 未修改；当前 RuleSetVersion 与普通任务不激活 carrier，仍为 legacy 单 anchor。
- composer 与 persistent adapter 生产文件未修改；测试只证明 point list compose、persistent JSON deserialize 与 `TaskResultQueryService` query 兼容。
- 七个精确测试均被发现。独立审计指出“两条不同 block 同值 → selected occurrences 精确 2”向量未直接断言后，执行方在原测试方法内补充该向量，未新增第八个测试。

最终验证：

- 第一组 XML：`47 tests / 0 failures / 0 errors / 0 skipped`。
- 第二组 XML：`25 tests / 0 failures / 0 errors / 0 skipped`。
- Codex 补充向量后复验：第一组 47/47；第二组首次因沙箱中新 Gradle daemon 无法解析已声明插件而在测试发现前失败，随后经批准仅使用本机离线缓存重跑，25/25 成功。该环境失败不计作测试失败，原始失败事实保留于本记录。
- `git diff --check` 通过；只有 LF/CRLF 提示。
- 未运行正式 MVP E2E、外部模型、网络、Docker、migration 或无过滤全量测试；实现与审计阶段未 commit、push、merge 或切换分支。

## 11. CODEX 审查记录

当前结论：`ACCEPT_IMPLEMENTATION / INDEPENDENT_IMPLEMENTATION_AUDIT_GO / EXACT_COMMIT_AUTHORIZED / NO_PUSH`。

独立规格审计记录（2026-07-14）：

- 第一轮：`NO_GO`。阻断为无 lineage 却删除 BLOCK fallback、未绑定版本却改变普通任务 anchor 基数、把只读 persistent adapter 误称写入 round-trip。
- Codex 修订：无 lineage 时保留不同 BLOCK/TABLE_CELL identity；A 改为未激活 carrier；persistence 验收改为 JSON read + query。
- 第二轮：`GO`。两个生产文件、五个测试文件足够；40 + 7 → `>=47`、第二组 `=25` 可证伪。
- Non-blocking 措辞已收口：测试名改为 `sameExactValueRetainsDistinctBlockAndCellOccurrences`。

编码前规格映射计划与 Codex Decision（2026-07-14）：

- 执行代理已只读提交完整映射计划，覆盖 §8、7 个精确测试、40→`>=47`、第二组 `=25`、未激活 carrier、persistent JSON read/query 和全部 STOP 条件。
- Codex 审查结论：`GO / IMPLEMENTATION AUTHORIZED`。
- 强制实现条件：所有 resolver 退出分支均携带同一个 dedup 前不可变 raw snapshot；只有 HIGH exact selected value 形成 selected list；显式 carrier 可靠性失败必须先于业务比较并生成既有 PARTIAL / SYS / NOT_CONCLUDED；非空 carrier 即使全无效也不得回退 legacy primary anchor；现有 preparer 与普通规则集路径不修改。
- 放行只限 §0.3 七个文件；不得新增文件、扩大到 B/C、运行正式 E2E、commit、push、merge 或切换分支。

实现审查与独立实现审计（2026-07-14）：

- Codex 已审查实际生产与测试 diff，并独立重跑冻结的两组测试；最终 XML 为 47/47 与 25/25。
- 独立实现审计首轮指出冻结向量“两个不同 block 同值 → selected occurrences 精确 2”没有直接测试；Codex 接受 finding，执行方在原精确测试内补充断言并重跑通过。
- 独立实现审计最终结论：`GO`，无 blocking finding。
- Non-blocking：resolver 捕获快照后仍对原 `rawCandidates` dedup，在调用方并发修改输入时存在理论时序风险；当前接口无并发共享证据，且冻结要求保持现有裁判逻辑，本批不修改。
- Codex Review Intake Decision：`ACCEPT_IMPLEMENTATION`。该接纳只覆盖未激活 carrier foundation；不授权 B/C、RuleSetVersion activation、57/57 coverage 声明、正式 E2E、commit、push 或 merge。
- 用户随后明确授权精确提交本轮 7 个实现文件和 5 个治理文档；实现提交 `c2fd17e` 已随 PR #32 合并，merge commit `97ef08f1cae88e8a702069eb0e07c2035b3b063f`。该合并不授权正式 E2E，也不表示 B1/B2/C 已完成。

## 12. 后续任务联动

| 后续动作 | 门禁 |
|---|---|
| TASK_SPEC-036-A 编码 | 本规格独立审计 GO + 编码前规格映射计划经 Codex GO |
| TASK_SPEC-036-A 接纳 | 代码、测试、实现报告、git diff 经 Codex Review Intake 与独立只读实现复核 |
| TASK-036-B | A 接纳；另行冻结 versioned policy / readiness / budget 规格 |
| TASK-036-C | B 接纳；另行冻结 point scope / reliable distinct verdict / integration 规格 |
| TASK-034 正式重跑 | TASK-035 接纳 + TASK-036 A/B/C 各自门禁完成 + Codex 单独正式重跑授权 |

## 附录：引用文档

| 文档 | 关键内容 |
|---|---|
| AGENTS.md | 角色分离、ADR 与证据门禁 |
| ADR-016 | provenance 先于 dedup、窄例外、点级 anchor 真源 |
| docs/ARCHITECTURE.md v0.10 | `CONSISTENCY_SET_READY` admission gate 与 SourceAnchor identity |
| TASK-036 | 父任务范围与 A/B/C 生产实现边界 |
| TASK-034 | 57/57 NOT_OBSERVABLE 历史失败证据与正式重跑门禁 |
