# ADR-016：多出处一致性证据保留与裁判边界

状态：Accepted

日期：2026-07-14

接受日期：2026-07-14

接受依据：用户明确接受 `ADR-016`，并要求先同步 `docs/ARCHITECTURE.md`；该接受不构成生产实现授权。

## 背景

`TASK-034` 对 3 份真实 DOCX 执行正式 MVP E2E 后，27 个审核点均产生 `PointStatus=PASS`，但 57 条纳入人工 occurrence 全部为 `NOT_OBSERVABLE`。

直接证据显示：

* 人工 ground truth 有 57 条纳入 occurrence，其中 39 条为 `BLOCK`、18 条为 `TABLE_CELL`。
* 正式结果只有 27 个 SourceAnchor，即每个审核点一个；只有 4 个 row-level `previewElementRef`，23 个无 `previewElementRef`。
* `MinimalCandidateResolver` 先按 `reviewPointCode + role + blockId + candidateValue` 去重；该 key 不含 row/cell/ref occurrence identity，可能先折叠同 block 内不同 cell 的 occurrence。
* 去重后，resolver 在 fully-attributed 候选只有一个 distinct value 时返回 `HIGH`，但只保留 `fullyAttributed.getFirst()`。
* `PointEvidence` 只表达单个 candidateValue、blockId 和 previewElementRef；`MinimalReviewEngine.anchorsFor()` 因而只输出一个 anchor。

`ADR-014` 冻结了最小 resolver confidence gate，`ADR-015` 冻结了 EvidenceSlot / SourceAnchor 职责，但均未明确“多个同值候选的 occurrence provenance 必须保留”以及“一致性审核点中的可靠异值”与“候选归属歧义”的分流。当前出处丢失是 occurrence-insensitive dedup 与 selected-candidate 投影的双重折叠，不是单一 `getFirst()` 问题。

架构一期成功标准要求“结构化字段与全文多处候选能比较”，现状尚未满足这一点。

## 适用范围

- 影响模块：Candidate Index、`CandidateResolver`、EvidenceSlot Preflight、Review Engine、Result Composer、Result Snapshot / Query。
- 影响阶段：MVP。
- 是否影响外部 API：只改变既有 `pointResults[].sourceAnchors[]` 的基数与语义，不新增 endpoint；只读核查已确认 OpenAPI 使用数组结构。
- 是否影响数据库或版本快照：影响新快照的 evidence / anchor 内容；历史快照不回填。只读核查已确认 JSONB query 与 Java snapshot record 使用 list 结构。
- 是否影响模型、规则、prompt、正则或证据选择：影响 CandidateResolver 与 EvidenceSlot / occurrence scope policy；不接入或修改模型调用。

## 决策

### 1. 分离 semantic candidate value 与 occurrence provenance

候选解析必须区分：

```text
semantic value group
- reviewPointCode / role
- canonicalValue
- confidence / attribution status
- occurrences[]

occurrence
- source block identity
- section / region
- table row / cell identity（如真实可用）
- evidence text / summary
- source anchor fields
```

上述为语义草图，不预先冻结 Java class 名称或公共 API 字段。

同 role、同 canonical value 的多个 occurrence 属于一个 semantic value group，但每个 occurrence provenance 必须保留；不得只保存第一个。occurrence provenance 的捕获必须发生在任何 dedup、canonical distinct grouping 和 selected-candidate 投影之前；后续可按 semantic value 分组，但不得再用忽略 row/cell/ref identity 的 key 删除 occurrence。

### 2. EvidenceSlot 基数与 occurrence budget 分离

普通 EvidenceSlot 继续完全遵守 ADR-015，`minCandidates / maxCandidates` 约束可 admission 的 candidate 基数，普通同 role 竞争仍走 ADR-014 的 `CONFLICTED` gate。

只有显式声明 `cardinalityMode=CONSISTENCY_SET` 的全文多出处一致性 required slot，才使用以下窄化语义：

* `minCandidates` 固定为 `1`；`maxCandidates` 约束完整收集后的 distinct semantic value group 数，且发布配置必须显式满足 `maxCandidates >= 2`，不得沿用单候选 slot 的 `maxCandidates=1`。
* occurrence 数量由单独、必填、正整数的 `occurrenceBudget` 约束，不占用 `maxCandidates`。`occurrenceBudget` 必须不小于 `maxCandidates`；不得以同一字段同时表达 semantic cardinality 和 provenance 预算。
* 未配置/非法配置必须在 RuleSetVersion 发布时拒绝。
* 发现 distinct semantic value groups 超过 `maxCandidates`，或 occurrence 超过 `occurrenceBudget`，均停止业务裁判并映射为 slot=`BUDGET_TRUNCATED`、pointCoverage=`PARTIAL`、`PointStatus=NOT_CONCLUDED`；即使已观察到异值也不得输出业务 ERROR。
* 只有 scope 完整、集合完整收集、未超过任一上限且通过下节全部 admission gate 的集合，才可进入确定性一致性裁判。

### 3. 一致性审核点区分可靠异值与归属歧义

对于在不可变 RuleSetVersion 中同时声明“全文多出处一致性”和 `cardinalityMode=CONSISTENCY_SET` 的审核点，resolver/preflight 必须先形成新的输入状态 `CONSISTENCY_SET_READY`。该状态不是把 `CONFLICTED` fallback 提升为伪 `HIGH`，而是“完整 occurrence 集合已按统一策略可靠归属”的点级输入。

`CONSISTENCY_SET_READY` 必须同时满足以下可证伪门槛：

1. scope policy 已枚举并扫描全部声明的 section / region / table 范围，无 parser 区域缺失、失败 shard、预算截断或其他 coverage 缺口；strong exclusion 规则已执行并记录版本化 reason。
2. 每个潜在 in-scope occurrence 均独立通过 source confidence、parse confidence、ValueGrammar、role label、section/region/table context 和 attribution 校验；未命中 deleted/voided/TOC/header-footer 等已版本化 strong exclusion。
3. 所有参与 occurrence 使用同一版本的 canonicalization 与 unit policy；不得按实际值临时选择 normalization。
4. 所有参与 occurrence 都有符合粒度要求的可靠 anchor；TABLE_CELL 必须有 parser 真实 row/cell identity。
5. 任一潜在 in-scope occurrence 为低置信、role 冲突、anchor 不可靠或无法确定纳入/排除时，整个集合不得静默忽略该 occurrence，必须保持 `SYS-* / NOT_CONCLUDED`。
6. distinct semantic value group 数和 occurrence 数均未超过上一节两个独立上限。

只有满足上述门槛后：

* 规范化后只有一个 semantic value：进入后端确定性结构化字段比较，并保留全部 in-scope occurrence anchors。
* 存在多个 distinct semantic values：这是可证实的合同内部不一致，进入后端确定性点级一致性裁判，可输出业务 `ERROR` 与对应 Finding，并附全部冲突 anchors。
* 候选 role / block attribution 不可靠、存在无法消解的语义归属冲突：保持 `SYS-* / NOT_CONCLUDED`，不得伪装成业务不一致。
* occurrence scope 不完整、parser 区域缺失或 provenance 截断：保持 `SYS-* / NOT_CONCLUDED`，不得输出业务 `PASS`。

### 3.1 对既有决策的窄化取代边界

若本 ADR 被接受，仅在 `CONSISTENCY_SET_READY` 的上述精确范围内取代：

* ADR-014“同 role 竞争一律 `CONFLICTED`”和“只有唯一 `HIGH` 才可进入业务裁判”的条款；
* ADR-015“保持 ADR-014 admission gate”及普通 slot `minCandidates / maxCandidates` 对该新 cardinality mode 的解释；
* `docs/ARCHITECTURE.md` 第 10.1、10.4、22.4 节中“多个 HIGH 指向同一 role 且无法声明式消歧时输出 `CONFLICTED`”的 generic 规则，仅增加 `CONSISTENCY_SET_READY` 窄例外。

本 ADR 不改变普通 role selection、普通 EvidenceSlot、模型消歧、SYS/Finding 分流，也不改变第 10.7 节 optional `GlobalConsistencyCheck` 只做诊断且不覆盖单点结论的性质。这里的新裁判是明确一致性审核点自身的 point-local deterministic rule，不是 `GlobalConsistencyCheck` 自动裁决。ADR 接受后必须先把上述窄例外同步进入 `docs/ARCHITECTURE.md`，再允许生产实现拆分。

### 4. occurrence scope policy 必须版本化

哪些 section / region / label context 属于某一致性审核点的纳入或排除范围，必须进入不可变 RuleSetVersion 或与其绑定的版本化 policy。

禁止：

* 硬编码 `CQCP-MVP-DOCX-001/002/003`、occurrenceNo 或人工 fixture 路径。
* 把人工 `includedInConsistencyEvaluation` 直接作为生产运行时输入。
* 用模糊文本包含、同 block、同 row 或同 table 替代真实 scope / anchor 归属。

### 5. 每个参与裁判的 occurrence 输出独立 SourceAnchor

结果应通过既有 `sourceAnchors[]` 表达多个 anchors；每个实际参与裁判的 occurrence 对应一个可追溯 anchor。

* `BLOCK` 至少有稳定 blockId。
* table row / cell 使用真实 `previewElementRef`；TABLE_CELL 必须有 parser 来源的稳定 cell identity。
* 不得由 candidateValue 反向搜索 cell 伪造 `cellIndex`。
* Result Composer 去重键必须保留不同 block / row / cell occurrence，不得把同值出处合并成一个 anchor。

`primaryBlockId / relatedBlockIds[]` 可继续用于单个复合证据 anchor，但不能替代多个独立 occurrence anchors 的列表语义。

MVP occurrence identity 固定为：`BLOCK` 粒度按 `reviewPointCode + stable blockId` 计一个 occurrence；`TABLE_CELL` 粒度按 `reviewPointCode + stable blockId + parser-issued row/cell previewElementRef` 计一个 occurrence。没有稳定字符 range 时，同一 block 或同一 cell 内的重复 mention 不拆成多个 MVP occurrence；未来若引入稳定 range，必须另行版本化。不同 identity 即使 canonical value 相同也不得在点级列表中去重。

点级 `pointResults[].sourceAnchors[]` 是 occurrence 输出与 coverage 的唯一真源。顶层聚合 anchor 列表可为跨审核点导航按完全相同 source identity 去重，但不得用于计算某审核点的 occurrence coverage，也不得反向折叠点级列表。

### 6. SYS 与 Finding 分流保持不变

* 可靠异值不一致是业务 Finding。
* 归属歧义、解析缺失、scope 不完整、预算截断或 anchor 不可靠是 `SYS-*`，点级 `NOT_CONCLUDED`。
* Evidence 不足不得回灌全文，也不得保留业务 `PASS / ERROR / WARNING`。

### 7. 版本与兼容

* 新语义必须绑定新的 RuleSetVersion / comparison-relevant policy version，并包含 `cardinalityMode`、`maxCandidates`、`occurrenceBudget`、scope/exclusion、canonicalization/unit 与 anchor identity policy。
* 历史快照不回填，继续按旧版本解释。
* 新任务只使用新版本；不得用新规则重新解释历史 `PASS`。
* 现有 OpenAPI point result 与 Java snapshot 已使用 `sourceAnchors[]` / `List<SourceAnchorSummary>`，JSONB query 也按列表读取，因此一点评多 anchors 不要求新 endpoint 或数据库 migration。若实现发现与该只读事实不同，必须停止并拆出兼容任务；当前 ADR 不授权直接扩公共字段或 migration。

## 备选方案

- 方案 A：只给当前 selected candidate 补 cellIndex。拒绝；仍只有 27 个 anchor，无法覆盖 57 条 occurrence。
- 方案 B：由 test harness 在全文中按 expected / candidateValue 搜索并生成 anchors。拒绝；会使用被测值反向构造 provenance，形成循环验证和伪定位。
- 方案 C：保留 distinct semantic value groups 与全部 occurrence provenance，并由确定性一致性裁判区分可靠异值和归属歧义。已选择。
- 方案 D：把所有多值候选继续统一视为 `CONFLICTED / NOT_CONCLUDED`。拒绝作为一致性点默认语义；会隐藏已可靠证明的合同内部不一致。

## 选择理由

- 与架构“结构化字段与全文多处候选能比较”目标一致。
- 保持 CandidateResolver 负责归属、后端负责确定性裁判、SourceAnchor 只负责定位的职责分离。
- 不把同值重复 occurrence 错当冲突，也不把可靠异值降级为系统歧义。
- 允许复用现有 `sourceAnchors[]` 和 row/cell identity，不需要先发明新 endpoint。

## 影响

### 正向影响

- 一致性审核点能真实覆盖全文多出处，而不是只比较第一个候选。
- 业务不一致与系统不确定性得到清晰分流。
- 结果页可展示全部参与裁判的证据位置。
- `TASK-034` 的 57 条人工 occurrence 可作为独立验收标准，而不是被 parser 输出倒填。

### 代价与风险

- CandidateResolver / evidence adapter 需要保留更多 provenance，快照体积增加。
- occurrence scope policy 若定义不严会造成漏召回或错误纳入。
- 现有 `PointEvidence` 单值结构可能需要兼容演进，必须避免一次性完整 EvidenceBundle 平台化。
- reliable distinct values 到业务 ERROR 的判定必须只在 `CONSISTENCY_SET_READY` 全部门槛满足时发生，否则会生成无可靠证据 Finding。

## 不做什么

- 不接入 Gemma 或公网模型。
- 不引入全文向量 RAG、OCR 或 PDF 主链路。
- 不把字符级 range 设为所有 occurrence 的硬依赖。
- 不修改人工 ground truth、fixture、expected JSON、DOCX 或 XLSX。
- ADR 接受只授权架构文档同步；在后续局部 `TASK_SPEC` 冻结、编码前规格映射计划获 Codex 放行和单独实现门禁满足前，不实现生产代码。

## 回滚与迁移

- 回滚方式：新语义只随新 RuleSetVersion 生效；回滚时停止发布新版本，不改写历史快照。
- 数据迁移影响：只读核查已确认现有 JSONB 与 Java record 按 list 承载多 anchor，当前无需数据库 migration；若实现发现事实不同则停止并拆兼容任务。
- 历史快照兼容性：旧 snapshot 保持单 anchor / 旧 resolver 语义；不回填、不重算。

## 验证方式

- 同值多出处：PointStatus 按结构化字段比较，所有 in-scope occurrences 均有独立 anchor。
- 可靠异值：仅当完整集合通过 `CONSISTENCY_SET_READY` 全部门槛且未超过两类上限时，确定性业务 ERROR，冲突值与 anchors 可解释。
- 归属歧义：`SYS-* / NOT_CONCLUDED`，无业务 Finding。
- distinct value 超 `maxCandidates`、occurrence 超 `occurrenceBudget`：slot=`BUDGET_TRUNCATED`、pointCoverage=`PARTIAL`、`PointStatus=NOT_CONCLUDED`，无业务 Finding。
- occurrence 截断或 parser 区域缺失：不得输出业务 PASS/ERROR/WARNING。
- row/cell：错误 cell 不因同 row / table / block 被宽松判为命中。
- 以 001/002/003 的 63 条人工 ground truth 做正式 E2E，但不得修改其 expected 或排除语义。

## 后续动作

1. 已完成首轮独立审计、整改、复审与最终 delta 核对，最终结论为 `GO`。
2. 用户已于 2026-07-14 明确接受 ADR-016；接受不构成生产实现授权。
3. 先同步 `docs/ARCHITECTURE.md` 的窄例外与版本字段；不得直接进入生产实现。
4. 后续由 Codex 另行拆分局部生产 `TASK_SPEC`；Claude Code / DeepSeek 必须先提交编码前规格映射计划。
5. 实现接纳与独立复核后，回到 `TASK-034` 重新运行正式 MVP E2E。

## 关联

- 相关任务：`TASK-034`、`TASK-036`、`TASK-EVAL-001-A`、`TASK-DATA-001`。
- 相关文档：`docs/ARCHITECTURE.md`、`PRD.md`、`CURRENT_CONTEXT.md`。
- 相关 ADR：`ADR-014`、`ADR-015`。
- 相关版本：后续生产实现规格必须显式冻结新的 RuleSetVersion / occurrence scope policy version；本 ADR 不替代该实现门禁。

## 待确认

- 已确认：用户接受本 ADR；`docs/ARCHITECTURE.md` 须先同步且不得直接实现。
- 新 RuleSetVersion 标识，以及各首批一致性点显式 `maxCandidates` / `occurrenceBudget` 数值；不得使用隐式默认值。

## 接受后同步记录

- `docs/ARCHITECTURE.md` 已更新为 v0.10，精确同步 occurrence 捕获时机、`CONSISTENCY_SET_READY` 窄例外、EvidenceSlot 双基数、point-local / GlobalConsistencyCheck 边界、点级 anchor 真源与普通 conflict UI 区分。
- 独立只读审计结论：`GO`，无 blocking / non-blocking finding；未引入 ADR 外字段、公共 API、migration 或生产实现授权。
- Codex Review Intake：`ADR_ACCEPTED / ARCHITECTURE_SYNCHRONIZED / NO_IMPLEMENTATION_AUTHORIZATION`。
