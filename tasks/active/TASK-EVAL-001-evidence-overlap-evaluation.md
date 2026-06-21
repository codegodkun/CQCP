# TASK-EVAL-001：Parser-backed 证据重合度评测基线

状态：实现与验证已完成，待用户确认提交收口

类型：A 类质量评测父任务 / Codex 主控

优先级：P0

负责人：Codex

创建日期：2026-06-20

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`PRD.md`、`docs/ARCHITECTURE.md`、`decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`、`decisions/ADR-015-evidence-slot-source-anchor-governance.md`

## 背景

`TASK-025` 已建立 parser-backed fixture 级主链路验收，`TASK-026` 已完成最小 `CandidateResolver` 五档置信度治理，`TASK-027` 已完成 ADR-015 边界内的最小 `EvidenceSlot / SourceAnchor` 主实现。

当前回归能够验证 `PointStatus`、`candidateValue`、单个 `blockId` 和 `evidenceSummary`，但尚未形成可比较的证据定位质量基线。若缺少 block / table-row / cell level overlap 指标，后续 parser、候选召回、归属规则或模型辅助变化即使让最终状态保持不变，也无法判断证据定位是否真实提升或退化。

本任务建立最小离线评测基线，只评价证据定位与候选归属质量，不改变生产审核语义。

## Review Intake Decision

2026-06-20 实现前 Review Intake Decision：`NEEDS-SPLIT`。

已确认：

* block-level overlap 已具备实现条件。
* parser 内部已有部分 `tableId + rowIndex` 信息，table row 具备潜在实现条件。
* 当前 `PointEvidence / SourceAnchorSummary / ResultComposer / TaskResultQuery / PersistentTaskResultStore` 的稳定可见契约仍主要只有 block-level anchor。
* 当前 cell anchor 缺少稳定 `cellIndex`，不得通过 `candidateValue` 搜索 cells 伪造来源归属。
* 在 row/cell anchor 无法通过真实结果链路稳定观察前，不得直接进入本父任务的完整 overlap evaluator 实现。

拆分决定：

1. 先执行 `TASK-EVAL-001-A`：SourceAnchor row/cell observability 前置任务。
2. `TASK-EVAL-001-A` 完成并经 Codex 验收后，再启动 `TASK-EVAL-001-B`：evidence overlap baseline。
3. `TASK-EVAL-001-B` 暂不创建实现文件；其目标、指标和 fixture DoD 继续由本父任务承载。
4. 本父任务原 DoD 不降级，仍要求 block / table-row / cell、4 正向 + 4 负向/冲突及完整 overlap 指标。

## 目标

* 建立 parser-backed fixtures 的 block / table-row / cell level evidence overlap 最低评测基线。
* 对 expected anchor 与实际 `SourceAnchor` 做可重复的 recall、precision、required hit 和失败归因计算。
* 阻止“`candidateValue` 正确但证据定位错误”被误判为评测通过。
* 为后续 parser、候选召回、`CandidateResolver`、模型辅助或检索方案比较提供统一基线。

## 非目标

* 不改变生产审核语义。
* 不改变 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED` 状态定义。
* 不改变 `SYS-*` 与业务 Finding 分流。
* 不改变 `EvidenceSlot` admission 规则。
* 不改变 `CandidateResolver` 五档定义或 `HIGH` admission gate。
* 不进入字符级 span 强制评分。
* 不进入 `TASK-028`、`TASK-031` 或 `TASK-032`。
* 不引入新模型，不比较 Gemma / Qwen。
* 不引入 BM25、Docling、Ragas、Outlines、OpenTelemetry 或 Label Studio。
* 不替换 parser。
* 不修改 `PRD.md` 或 `docs/ARCHITECTURE.md`。
* 不派发 Claude Code / DeepSeek。

## 输入

* 相关文档：
  * `PRD.md`
  * `docs/ARCHITECTURE.md`
  * `CURRENT_CONTEXT.md`
  * `tasks/MVP_TASK_MAP.md`
* 相关 ADR：
  * `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
  * `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* 上游任务：
  * `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
  * `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
  * `tasks/done/TASK-027-evidence-slot-source-anchor-governance.md`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/MVP_TASK_MAP.md`
* `PRD.md` 中 `Candidate Index And Resolver`、`EvidenceSlot And EvidencePacket`、`Result Composition`、`Result Page`、MVP 验收相关章节
* `docs/ARCHITECTURE.md` 中 `Evidence ranking`、`CandidateIndex 与 CandidateResolver 边界`、`EvidenceSlot Preflight`、`SourceAnchor`、质量评测与治理相关章节
* `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
* `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
* `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
* `tasks/done/TASK-027-evidence-slot-source-anchor-governance.md`

### Optional Context

* `docs/backend.md`
* `docs/ai-review.md`
* `packages/test-fixtures/README.md`
* `changelog/2026-06.md`

### Out of Scope

* 生产代码、API、数据库迁移、OpenAPI、前端、Docker、Compose 或依赖修改。
* 新审核点、新合同类型、新 `CandidateRole`、新 `ReviewPointFamily`。
* `EvidenceBundle` 平台化、模型接入、检索方案引入或 parser 替换。
* 业务 Finding 正确率、最终状态准确率或模型能力排名。

## 范围

### 包含

* 为现有 parser-backed fixture 定义 expected evidence anchors。
* 实现 test-only overlap 计算与断言。
* 覆盖 block 与 table row/cell 两类 anchor。
* 覆盖至少 4 个正向 fixture 和 4 个负向 / 冲突 fixture。
* 输出可定位的失败原因，不只输出布尔结果。

### 不包含

* 修改主代码以迎合评测。
* 修改 fixture 合同正文来提高分数。
* 用最终 `PointStatus` 代替 anchor overlap。
* 用 `candidateValue` 相同代替来源归属正确。
* 把评测结果直接作为生产规则发布或自动优化依据。

## 授权文件范围

实现阶段只允许修改以下范围：

* `packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json`
* `packages/test-fixtures/expected/CQCP-MVP-DOCX-002.json`
* `packages/test-fixtures/expected/CQCP-MVP-DOCX-003.json`
* `packages/test-fixtures/expected/CQCP-MVP-DOCX-004.json`
* `packages/test-fixtures/README.md`
* `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`
* `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/` 下新增的 test-only evaluation helper 或测试文件
* 本任务文件、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-06.md`

明确禁止：

* `apps/api-server/src/main/**`
* `packages/test-fixtures/docx/**`
* `PRD.md`
* `docs/ARCHITECTURE.md`
* `decisions/**`

如果现有测试可见输出无法表达 table row/cell anchor，必须停止并回交 Codex 重新定界，不得直接修改生产模型或 parser。

## 最低评测模型

### Anchor 标识

评测必须使用稳定、可比较的 canonical anchor key：

```text
BLOCK:<blockId>
TABLE_ROW:<blockId>:<rowIndex>
TABLE_CELL:<blockId>:<rowIndex>:<cellIndex>
```

block 级最低字段：

```text
expectedBlockIds
actualSourceAnchor.blockIds
```

table row/cell 级 expected 数据可使用与当前 fixture schema 兼容的附加字段，但最终必须转换为 canonical anchor key 后参与相同 overlap 计算。

### 最低公式

对单个 fixture + review point：

```text
expected = expected canonical anchor keys
actual = actual SourceAnchor canonical anchor keys
matched = expected ∩ actual

expectedRecall = |matched| / |expected|
actualPrecision = |matched| / |actual|
missingExpectedBlocks = expected - actual
unexpectedMatchedBlocks = actual - expected
requiredHit = 1 when missingExpectedBlocks is empty, otherwise 0
```

集合为空时：

* 正向 case 的 `expected` 不允许为空。
* 正向 case 的 `actual` 为空时，`expectedRecall=0`、`actualPrecision=0`。
* 负向 case 不以空 expected 集合计算正向 recall/precision，而是验证实际结果未错误命中禁止 anchor、未错误通过 admission，并记录失败归因。

跨基线集合：

```text
requiredHitRate = Σ requiredHit / positiveEvaluationCaseCount
```

最低通过门槛：

* 正向 case：`expectedRecall = 1.0`、`actualPrecision = 1.0`、`requiredHit = 1`。
* 基线集合：`requiredHitRate = 1.0`。
* 若确有允许的补充 anchor，必须在 expected 数据中显式声明，不能在计算时静默忽略。
* `candidateValue` 正确但 canonical anchor 不匹配时，评测必须失败。

### 失败归因

每个失败 case 必须提供非空 `attributionFailureReason`，最低枚举：

```text
EXPECTED_ANCHOR_MISSING
UNEXPECTED_ANCHOR_SELECTED
WRONG_BLOCK_ATTRIBUTION
WRONG_TABLE_ROW_ATTRIBUTION
WRONG_TABLE_CELL_ATTRIBUTION
CONFLICTING_CANDIDATE_ADMITTED
ATTRIBUTION_AMBIGUOUS
SOURCE_ANCHOR_UNAVAILABLE
```

## Fixture 矩阵

### 正向

至少 4 个正向 fixture，优先复用：

* `CQCP-MVP-DOCX-001`
* `CQCP-MVP-DOCX-002`
* `CQCP-MVP-DOCX-003`
* `CQCP-MVP-DOCX-004`

正向 case 必须验证：

* expected required evidence anchor 被命中。
* 实际 anchor 未吸附到无关 block / row / cell。
* `candidateValue` 与来源归属同时正确。
* 至少一个 case 覆盖 block anchor。
* 至少一个 case 覆盖 table row/cell anchor。

### 负向 / 冲突

至少 4 个负向 / 冲突 case，必须覆盖：

* 错误 block。
* 无关 block。
* 冲突候选。
* 候选值可匹配但归属不清。

负向 case 必须验证：

* 禁止 anchor 不会被判为通过。
* `CONFLICTED / MEDIUM / LOW / UNKNOWN` 不会因值相同被当作正确归属。
* 错误 row/cell 不会因同表或同 block 被宽松判为通过。
* 失败时生成明确 `attributionFailureReason`。

## 约束

* 评测只验证证据定位质量，不改变业务 Finding 语义。
* 生产状态、admission、resolver 和 result composition 行为必须保持不变。
* 不得通过修改 DOCX fixture 正文来让评测通过。
* 不得把字符级 span 设为 MVP 强制门槛。
* 不得静默忽略 unexpected anchor。
* 不得把 table row/cell 退化为“同一 table block 即通过”。
* 后续任何模型、检索或 parser 优化必须使用同一基线比较前后结果，且不得只报告最终状态变化。

## 交付物

* expected fixture 中的 anchor expectation 定义。
* test-only evidence overlap evaluator。
* block 与 table row/cell overlap 自动化测试。
* 至少 4 正 + 4 负/冲突 case 的评测结果。
* 本任务文件完成记录与项目记忆写回。

## 验收标准

1. 父任务文档明确目标、非目标、授权文件范围和验收标准。
2. 已实现并验证 `expectedBlockIds`、`actualSourceAnchor.blockIds`、`expectedRecall`、`actualPrecision`、`requiredHitRate`、`missingExpectedBlocks`、`unexpectedMatchedBlocks`、`attributionFailureReason`。
3. 至少覆盖 4 个正向 fixture 和 4 个负向 / 冲突 fixture。
4. 正向 fixture 的 expected required evidence anchor 全部被命中。
5. `candidateValue` 正确但 `SourceAnchor` 错误时评测失败。
6. 错误 block、无关 block、冲突候选和归属不清不会被判为通过。
7. 自动化测试覆盖 block 与 table row/cell 两类 anchor。
8. 文档明确评测只验证证据定位质量，不改变业务 Finding 语义。
9. 生产代码、测试外依赖、DOCX fixture、PRD 和架构文档保持不变。
10. 在 Docker Compose 标准环境或项目认可的既有测试方式下完成验证。
11. 阶段结束时 `git status --short` 必须干净。
12. commit / push 必须单独取得用户确认。

## 测试与验证

实现阶段必须执行：

* overlap evaluator 定向测试。
* `ParserBackedReviewInputPreparerEvidenceTest` 回归。
* `TaskExecutionStateMachineTest` 回归，确认最终业务状态语义未变化。
* `git diff --check`。
* `git status --short`。

测试命令以仓库届时认可的 Gradle / Docker Compose 标准方式为准；若标准命令仍受既有 PostgreSQL host 问题阻塞，必须区分目标测试结果与既有环境阻塞，不得伪报全量通过。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：是，记录当前优先级与 `TASK-028` 依赖。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要更新 `PRD.md`：否。
* 是否需要更新 `docs/ARCHITECTURE.md`：否。
* 是否需要新增或更新 ADR：否；若实现要求改变 `EvidenceSlot`、`CandidateResolver`、`SourceAnchor` 或生产审核链路，必须停止并另行提出 ADR。

## Next Task Handoff

* `TASK-EVAL-001-A` 已完成并 push，提交为 `4bac2f4`。
* `TASK-EVAL-001-B` 已完成最小实现与验证，当前等待用户确认提交 / push。
* 父任务提交收口前不得进入 `TASK-028`、`TASK-031` 或 `TASK-032`。
* 本任务未派发 Claude Code / DeepSeek。

## 风险

* expected canonical key 绑定当前 parser 的稳定 block 顺序；后续 parser 结构变化若改变 blockId，必须通过基线评审更新，不得运行时自动重写 expected。
* 四份主 DOCX 当前覆盖 BLOCK 与 TABLE_ROW；TABLE_CELL 由 test-only parser-backed case 覆盖。后续如要求真实主 fixture 覆盖 cell，必须单独授权新增样本，不得修改现有 DOCX 迎合评测。
* expected anchor 标注若不完整，会把合法补充证据误报为 precision 下降；因此所有允许 anchor 必须显式登记，不得静默忽略 unexpected anchor。
* 基线规模较小，只用于回归门禁，不得宣称代表真实合同总体质量。

## 待确认

* 已确认：现有 parser 可在不改变 ADR-015 语义的前提下，通过真实 cell ordinal 与 joined-text range 稳定表达 cell anchor。
* 已确认：跨 cell 或无法唯一映射的 matcher 命中降级为 row anchor，不降低父任务 cell DoD。

## 完成记录

* 完成日期：2026-06-21。
* 变更文件：四份 `packages/test-fixtures/expected/*.json`、`packages/test-fixtures/README.md`、三个 test-only evaluator / baseline 文件及项目记忆文件。
* 测试结果：
  * 4 个真实正向 fixture：`expectedRecall=1.0`、`actualPrecision=1.0`、`requiredHit=1`
  * 集合 `requiredHitRate=1.0`
  * BLOCK / TABLE_ROW / TABLE_CELL canonical key 均有自动化覆盖
  * 真实 `CONFLICTED / MEDIUM / LOW` 与注入 wrong block / row / cell / unexpected / unavailable anchor 均不会误判通过
  * `ParserBackedReviewInputPreparerEvidenceTest` 与 `TaskExecutionStateMachineTest` 回归通过
* 遗留问题：当前变更尚未 commit / push；父任务提交收口后需重新确认后续任务排序。
* 备注：未修改生产代码、DOCX fixture、OpenAPI、数据库、Docker/Compose、前端、PRD、架构文档或 ADR；未改变 Finding、EvidenceSlot admission、CandidateResolver gate 或业务状态语义。
