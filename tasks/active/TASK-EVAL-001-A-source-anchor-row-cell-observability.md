# TASK-EVAL-001-A：SourceAnchor 行与单元格可观测性前置

状态：已完成并 push

类型：A 类质量评测前置子任务 / Codex 主控

优先级：P0

负责人：Codex

创建日期：2026-06-20

父任务：`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`

来源：`TASK-EVAL-001` 实现前 Review Intake Decision、`decisions/ADR-015-evidence-slot-source-anchor-governance.md`

## 背景

`TASK-EVAL-001` 的原始 DoD 要求对 block / table-row / cell 级 anchor 建立 overlap 基线。实现前 Review Intake 已确认：

* block-level anchor 已可通过当前 reviewengine 结果链路观察。
* parser 内部已有部分 `tableId + rowIndex` 信息。
* `PointEvidence / SourceAnchorSummary / ResultComposer / TaskResultQuery / PersistentTaskResultStore` 当前稳定可见契约仍主要只有 block-level anchor。
* cell anchor 尚无稳定 `cellIndex` 投影。

因此父任务结论为 `NEEDS-SPLIT`。本任务只补齐后续评测所需的真实 row/cell anchor 可观测性，不实现 overlap evaluator。

## 目标

* 将 parser 已具备或可稳定表达的 table row / cell anchor 信息，以最小兼容方式投影到 reviewengine 可观测层。
* 让 `PointEvidence / SourceAnchorSummary / ResultComposer / TaskResultQuery / PersistentTaskResultStore` 相关测试可以稳定观察 block / table row / table cell anchor。
* 为后续 `TASK-EVAL-001-B` 提供真实、可重复比较的 actual anchor 输入。
* 保持现有 block-level 结果和历史快照兼容读取。

## 非目标

* 不实现 evidence overlap evaluator。
* 不修改 expected JSON 或 DOCX fixture。
* 不执行 4 正向 + 4 负向/冲突 fixture 评测。
* 不改变业务 Finding 语义或点级状态。
* 不改变 `EvidenceSlot` admission。
* 不改变 `CandidateResolver` 五档 confidence gate。
* 不进入 `TASK-028`、`TASK-031` 或 `TASK-032`。
* 不引入模型、BM25、Docling、Ragas、Outlines、OpenTelemetry 或 Label Studio。
* 不进入字符级 span。
* 不修改 `PRD.md` 或 `docs/ARCHITECTURE.md`。
* 不修改 OpenAPI、数据库迁移、Docker 或 Compose。
* 如实现必须改变 ADR-015 的 `SourceAnchor` 语义，立即停止并报告，不继续实现。

## 输入

* 父任务：`tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
* 相关 ADR：`decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* 上游任务：`tasks/done/TASK-027-evidence-slot-source-anchor-governance.md`
* 相关实现：
  * parser 的 `DocumentBlock / TableBlock / TableRowBlock`
  * `ParserBackedReviewInputPreparer`
  * `PointEvidence / SourceAnchorSummary`
  * `ResultComposer`
  * `TaskResultQuery`
  * `PersistentTaskResultStore`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* 父任务 `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
* `tasks/MVP_TASK_MAP.md`
* `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* `docs/ARCHITECTURE.md` 中 `SourceAnchor`、`CandidateResolver`、`EvidenceSlot`、结果合成与快照兼容章节
* 与 `PointEvidence / SourceAnchorSummary / ResultComposer / TaskResultQuery / PersistentTaskResultStore` 直接相关的现有实现和测试

### Optional Context

* `docs/backend.md`
* `docs/ai-review.md`
* `docs/VERIFY.md`
* `docs/DEVELOPMENT.md`
* `changelog/2026-06.md`

### Out of Scope

* overlap 指标、expected anchor 标注和 fixture 评测矩阵。
* 新审核点、新 `CandidateRole`、新 `ReviewPointFamily` 或新合同类型。
* 模型接入、检索方案、parser 替换或完整 `EvidenceBundle` 平台化。
* 外部 API 契约扩展、数据库 schema 变更或历史快照回填。

## 范围

### 包含

* 冻结 row/cell canonical anchor 的最小表达方式。
* 优先复用 ADR-015 已定义的 `previewElementRef` 和现有 `SourceAnchor` 语义。
* 以兼容新增方式将稳定的 row/cell 信息贯穿 parser-backed evidence、结果合成、查询和持久化可见链路。
* 保持旧 block-level anchor 可读。
* 为真实 row/cell anchor 补充定向兼容测试。

### 不包含

* 为满足测试而根据 `candidateValue` 反向搜索 cells 并伪造 `cellIndex`。
* 将同一 table block 命中宽松解释为 row/cell 命中。
* 新增 `locationLevel` 枚举。
* 修改业务裁判、slot coverage 或 resolver confidence。

## Canonical Anchor 冻结边界

后续评测使用以下 canonical key：

```text
BLOCK:<blockId>
TABLE_ROW:<blockId>:<rowIndex>
TABLE_CELL:<blockId>:<rowIndex>:<cellIndex>
```

本任务只负责提供这些 key 所需的真实输入字段，不负责计算 overlap。

约束：

* block anchor 必须继续可用。
* table row 仅在 `blockId + rowIndex` 均来自 parser 结构化产物时表达。
* table cell 仅在 `blockId + rowIndex + cellIndex` 均具备稳定来源时表达。
* 不得通过候选文本或 `candidateValue` 在 cells 中搜索并推断 `cellIndex`。
* 若 cellIndex 无法稳定获得，本任务必须保留为明确阻塞并回交 Codex，不得伪造或静默降级父任务 DoD。

## 授权文件范围建议

实现前必须由 Codex 再次执行 Review Intake 并冻结精确文件。候选范围仅限：

* `apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/` 下与 table row/cell identity 直接相关的最小文件。
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/` 下与 parser-backed evidence、`PointEvidence`、`SourceAnchorSummary`、结果合成、查询和快照兼容直接相关的最小文件。
* 上述范围对应的定向测试文件。
* 本任务文件、父任务文件、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-06.md`。

明确禁止：

* `packages/test-fixtures/docx/**`
* `packages/test-fixtures/expected/**`
* `PRD.md`
* `docs/ARCHITECTURE.md`
* `decisions/**`
* `packages/api-contracts/**`
* 数据库 migration
* Docker / Compose
* 前端代码

## DoD

1. 明确并实现 row/cell canonical anchor 所需的稳定字段表达。
2. 优先复用 ADR-015 的 `previewElementRef` 或现有 `SourceAnchor` 兼容语义。
3. 不新增 `locationLevel`；若确实必须新增，停止任务并触发 ADR 评审。
4. `PointEvidence / SourceAnchorSummary` 可以稳定表达 block + table row。
5. table cell 仅在具备稳定 `cellIndex` 时表达；不得伪造。
6. `ResultComposer / TaskResultQuery / PersistentTaskResultStore` 的测试可观察 row/cell anchor。
7. 旧 block-level 快照和结果继续兼容读取。
8. row/cell anchor 的兼容新增不改变现有业务 Finding 状态、`SYS-*` 分流或 `NOT_CONCLUDED` 语义。
9. `EvidenceSlot` admission 与 `CandidateResolver` confidence gate 保持不变。
10. `git diff` 严格限定在本任务最终授权文件。
11. `TASK-EVAL-001-B` 在本任务完成并经 Codex 验收前不得启动。

## 测试与验证

实现阶段至少执行：

* parser table row/cell identity 定向测试。
* `ParserBackedReviewInputPreparerEvidenceTest`。
* `ResultComposerTest`。
* `TaskResultQueryControllerTest` 或等价 Query 可见性测试。
* `PersistentTaskResultStoreTest`，覆盖旧 block-only 快照兼容读取和新增 row/cell 字段读取。
* `TaskExecutionStateMachineTest`，确认业务状态语义未变化。
* `git diff --check`。
* `git status --short`。

标准验证环境以 `docs/VERIFY.md` 和 `docs/DEVELOPMENT.md` 定义的 Docker Compose 环境为准；若后端定向测试继续使用项目既有 Gradle 方式，必须同时记录 Compose 服务状态和目标测试结果。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：是。
* 是否需要更新父任务：是。
* 是否需要更新 `changelog/2026-06.md`：是。
* 是否需要更新 `PRD.md`：否。
* 是否需要更新 `docs/ARCHITECTURE.md`：否。
* 是否需要新增或更新 ADR：默认否；若必须改变 ADR-015 `SourceAnchor` 语义则停止并另行提出 ADR。

## Next Task Handoff

* 本任务完成并经 Codex 验收后，下一任务为 `TASK-EVAL-001-B` evidence overlap baseline。
* `TASK-EVAL-001-B` 目标继续由父任务承载：
  * expected JSON anchor 标注
  * test-only evaluator
  * block / row / cell canonical key
  * 4 正向 + 4 负向/冲突
  * `expectedRecall / actualPrecision / requiredHitRate`
  * `missingExpectedBlocks / unexpectedMatchedBlocks / attributionFailureReason`
* 本任务已提交并 push；`TASK-EVAL-001-B` 已按父任务边界完成最小实现与验证。

## 风险

* parser 现有 row 信息可能在 candidate/evidence 投影时丢失。
* cell 数据可能只有文本列表而没有稳定 cell identity。
* 扩展过渡结构可能进一步耦合 parser、evidence 和结果 DTO，必须保持最小兼容新增。
* 若错误地从文本值推断 cell，会让后续 overlap 基线产生虚假精度。

## 待确认

* 已确认：稳定 cellIndex 来自 Apache POI `row.getTableCells()` 的零基序位；parser 需同步保留拼接文本字符范围。
* 已确认：row/cell identity 通过 `PointEvidence / SourceAnchorSummary` 的兼容新增字段和 ADR-015 `previewElementRef` 承载。

## 完成记录

* 完成日期：2026-06-21。
* 变更文件：`WordParserSpikeDocument.java`、`DocxWordParserSpike.java`、`MinimalCandidateResolver.java`、`ParserBackedReviewInputPreparer.java`、`MinimalReviewEngine.java`、`ResultComposer.java` 及授权测试、项目记忆文件。
* 测试结果：目标 Gradle 测试矩阵通过；Compose PostgreSQL `pg_isready` 通过；`docker compose ... build api-server` 通过。
* 遗留问题：无 A 阶段实现阻塞；后续由 B 完成 overlap baseline。
* 备注：提交 `4bac2f4 feat(reviewengine): expose table row and cell source anchors` 已 push；未改变 Finding、EvidenceSlot admission、CandidateResolver gate。
