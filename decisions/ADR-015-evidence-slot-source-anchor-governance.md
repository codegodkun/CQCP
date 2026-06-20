# ADR-015：EvidenceSlot / SourceAnchor 正式治理与执行边界

状态：Accepted

日期：2026-06-20

## 背景

`ADR-014` 已冻结最小 `CandidateResolver` 五档置信度和 `HIGH` admission gate，但明确不包含完整 `EvidenceSlot / SourceAnchor` 生命周期。

当前 parser-backed 主链路使用 `PointEvidence` 表达点级候选状态，并在结果合成时生成 `SourceAnchorSummary`。该过渡实现尚不能完整表达：

* 每个审核点需要哪些 required / optional evidence slot。
* slot 接受哪些 role、需要多少候选、要求何种置信度。
* 候选缺失、歧义、低置信或预算截断时如何形成可追溯 coverage 与 `SYS-*`。
* 业务结论使用的可靠证据与诊断阶段展示的候选证据如何区分。
* block / section / table row / cell 级原文定位如何作为正式结果资产保存。

如果不先冻结这些边界，后续实现容易把候选归属、证据需求、模型辅助、结果裁判和页面定位继续耦合在单一 preparer 或结果 DTO 中。

## 适用范围

* 影响模块：review engine、evidence preparation、result composition、result snapshot。
* 影响阶段：MVP。
* 是否影响外部 API：影响外部结果契约的解释边界，但不修改 `PointStatus` 稳定集合，不修改既有 `notConcludedReason / notConcludedDetail` 的外部语义集合；本 ADR 负责补齐内部 `SYS-*` 到既有外部语义的正式映射，并把 `SourceAnchor` 从内部过渡结构正式化为结果契约资产。`TASK-027` 仅允许对外 `sourceAnchors` 采取兼容新增或字段稳定化方式演进，不允许删除、重命名或收窄既有外部字段；若实现需要更新外部 OpenAPI 文档或新增兼容字段说明，必须在实现前补一个 OpenAPI 契约任务。
* 是否影响数据库或版本快照：影响 `ReviewResultSnapshot` 的语义边界，至少需要把 slot coverage / preflight / 正式 `SourceAnchor` 契约视为快照兼容演进对象；历史快照不回填，保持兼容读取。`TASK-027` 当前不允许数据库迁移；如实现前无法从只读核对中确认现有持久化结构是否足以承载兼容新增字段，必须先单独执行 snapshot / persistence 只读核对任务，并在后续拆出数据库/快照兼容任务，而不是把“无影响”写成既成事实。
* 是否影响模型、规则、prompt、正则或证据选择：影响 `EvidenceSlot` policy 与 evidence selection；不在本 ADR 接入或修改模型调用。

## 决策

### 1. 固定职责边界

审核链路保持以下单向顺序：

```text
ReviewExecutionPlan
-> CandidateResolver
-> EvidenceSlot Preflight
-> EvidenceBundle / PointEvidenceOverlay
-> Rule Execution or Gemma Assist
-> Point Verdict
-> Finding / SYS / SourceAnchor
-> ReviewResultSnapshot
```

职责固定为：

* `CandidateResolver`：对候选做保守角色归属，输出 `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN`。
* `EvidenceSlot`：声明审核点需要的证据角色、基数、置信度和降级策略。
* `EvidenceSlot Preflight`：根据 resolver 结果判断点级 slot coverage 和运行资格，不回写增删 `ReviewExecutionPlan` 中的审核点。
* 后端确定性裁判：只消费通过 preflight 的可靠证据。
* `SourceAnchor`：记录结论或诊断所依据的原文定位，不承担候选归属或业务裁判。

### 2. EvidenceSlot 属于不可变 RuleSetVersion

每个审核点的 slot 定义必须进入不可变 `RuleSetVersion` 快照，最小字段采用架构定义：

```text
EvidenceSlot
- name
- required
- acceptedRoles
- minCandidates
- maxCandidates
- confidenceRequired
- resolverPolicy: deterministicOnly / gemmaIfAmbiguous / sysIfMissing
- fallbackPolicy: allowSingleUnknownCandidate / noFallback
```

约束：

* slot 通过内部 `reviewPointCode + name` 唯一识别。
* `acceptedRoles` 使用平台固定 `CandidateRole` 命名空间。
* `1 <= minCandidates <= maxCandidates`。
* required slot 的定义变更必须生成新的 `RuleSetVersion`。
* 管理台在 MVP 不得任意修改完整 slot policy。

### 3. 固定运行时 coverage 语义

Preflight 至少区分以下运行时状态：

```text
SATISFIED
PARTIAL
MISSING
AMBIGUOUS
LOW_CONFIDENCE
BUDGET_TRUNCATED
```

行为规则：

* required slot 为 `SATISFIED` 且达到 `confidenceRequired`，审核点才可进入相应确定性规则。
* optional slot 非 `SATISFIED` 时可继续，但必须保留 coverage 记录。
* required slot 为 `MISSING`：输出 `SYS-INDEX-INCOMPLETE`。
* required slot 为 `AMBIGUOUS`：按根因输出 `SYS-ROLE-CONFLICT` 或 `SYS-EVIDENCE-AMBIGUOUS`。
* required slot 为 `LOW_CONFIDENCE`：按解析或候选根因输出对应 `SYS-*`，不得生成业务 Finding。
* required slot 为 `BUDGET_TRUNCATED`：输出 `SYS-EVIDENCE-BUDGET-EXCEEDED`。
* 上述 required slot 未满足场景统一映射为点级 `NOT_CONCLUDED`。

`EvidenceBundle` 截断后必须重新计算逐点 slot coverage。不能只根据截断优先级推断 required slot 已保留。

内部运行态向点级结果投影规则固定为：

* `pointCoverageStatus=COMPLETE`：全部 required slots 为 `SATISFIED`，且参与本次裁判的 optional slots 未留下覆盖缺口。
* `pointCoverageStatus=PARTIAL`：全部 required slots 为 `SATISFIED`，但至少一个 optional slot 为 `PARTIAL / MISSING / AMBIGUOUS / BUDGET_TRUNCATED`，或 secondary bundle 未完整覆盖；点级结果可继续生成，但必须写入 `missingOptionalSlots[]` 或管理台诊断。
* `pointCoverageStatus=LOW_CONFIDENCE`：任一 required slot 为 `LOW_CONFIDENCE`，或 required slot 仅能依赖低置信区域 / 低置信 anchor；该点必须输出 `NOT_CONCLUDED`。
* required slot 为 `PARTIAL / MISSING / AMBIGUOUS / BUDGET_TRUNCATED / LOW_CONFIDENCE` 时，点级结果一律进入 `NOT_CONCLUDED`；其中 `PARTIAL` 只是内部 coverage 状态，不是可对外稳定依赖的新 `PointStatus`。
* optional slot 未满足时，不得阻断已满足 required slots 的业务裁判，但必须进入 `missingOptionalSlots[]` 的 `cause / businessMessage`，并在管理台保留具体 slot coverage、候选摘要和 root cause。

### 4. 保持 ADR-014 admission gate

* `HIGH` 只是进入 slot preflight 的候选资格，不等于审核点可以直接裁判。
* `MEDIUM / LOW / CONFLICTED / UNKNOWN` 不得通过 slot fallback 被提升为伪 `HIGH`。
* `allowSingleUnknownCandidate` 只允许形成低置信候选或辅助提示，不得直接生成 `ERROR`，也不得单独满足 critical required slot。
* 同 role 竞争仍由 `CandidateResolver` 判定；`EvidenceSlot` 不重复实现候选分类器。

### 5. 固定 SourceAnchor 最小契约

正式 `SourceAnchor` 采用架构定义的最小字段：

```text
SourceAnchor
- primaryBlockId
- relatedBlockIds[]
- evidenceText
- sectionPath
- regionType
- confidence
- previewAssetId?
- previewPage?
- previewElementRef?
- locationLevel: EXACT_TEXT_RANGE / BLOCK_LEVEL / PAGE_LEVEL / SECTION_LEVEL / UNAVAILABLE
- failureReason?
```

MVP 约束：

* 最低保证 block / section 级定位；表格行/单元格定位通过 `regionType` 与 `previewElementRef` 表达，`locationLevel` 仍归入 `BLOCK_LEVEL`，不新增架构外枚举。
* 精确字符范围和页码定位是增强能力，不是业务裁判的硬依赖。
* 缺少精确字符范围本身不得导致 `NOT_CONCLUDED`。
* `PASS / ERROR / WARNING` 的门禁不是“至少一个 required slot anchor”。只有全部 `criticalEvidenceSlots`，以及全部实际参与本次裁判的 required slots，都具备可靠 `SourceAnchor` 时，才允许输出业务结论。
* 任一 `criticalEvidenceSlot` 或任一参与裁判的 required slot 缺少可靠 anchor 时，视为证据契约未满足；该点必须回退为 `NOT_CONCLUDED`，并输出 `SYS-EVIDENCE-BUNDLE-INVALID` 或等价内部诊断，不得继续保留业务 `PASS / ERROR / WARNING`。
* `SKIPPED` 不要求 anchor，但必须保留适用性原因。
* `NOT_CONCLUDED` 可以展示候选或冲突 anchor 作为诊断定位，但这些 anchor 不得被标记为已确认业务证据。

### 6. SYS 与 Finding 继续分流

* required slot 不满足时只生成 `SYS-*` 和 `NOT_CONCLUDED`，不得生成业务 Finding。
* 只有 required slot 满足并完成后端确定性裁判，才可生成 `PASS / ERROR / WARNING`。
* 普通结果页围绕“审核点 -> 证据 -> 原文定位”展示业务化解释。
* 管理台可展示 slot coverage、冲突候选和内部诊断，但不得把内部诊断伪装为业务风险。

### 6.1 post-build validation 与 INVALID bundle

* `EvidenceBundle` 只要发生截断、slot 去重、secondary bundle 合并或 anchor 重写，就必须执行 post-build validation。
* post-build validation 至少复核：required slots 是否仍达到 `minCandidates + confidenceRequired`、preserved slot 与实际 selected blocks 是否一致、secondary required slot 的 source bundle snapshot/anchor 是否存在、以及业务结论要求的 required slots 是否全部具备可靠 anchor。
* validation 失败时，bundle 状态固定为 `INVALID`，点级结果输出内部 `SYS-EVIDENCE-BUNDLE-INVALID` 或等价内部诊断，并统一映射为 `PointStatus=NOT_CONCLUDED`。
* `INVALID` bundle 不得继续调用模型，不得继续执行确定性业务裁判，也不得生成业务 Finding。

### 6.2 SYS-* 到外部 `NOT_CONCLUDED` 的正式映射

本 ADR 不扩展外部 `PointStatus`、`notConcludedReason` 或 `notConcludedDetail` 的稳定语义集合；仅把 `TASK-027` 范围内的内部 root cause 正式映射到 `docs/ARCHITECTURE.md` 第 17 节已定义的外部语义。

#### 运行时 root cause 到内部 `SYS-*`

| 运行时 root cause | coverage / execution 场景 | 内部诊断 |
| --- | --- | --- |
| 缺少 required slot 候选 | `MISSING`，且无可用相关候选 | `SYS-INDEX-INCOMPLETE` |
| required slot 只保留到部分候选 | `PARTIAL`，不足以达到 `minCandidates` / `confidenceRequired` | `SYS-INDEX-INCOMPLETE` |
| 同 role 竞争无法消歧 | `AMBIGUOUS`，根因为 role conflict | `SYS-ROLE-CONFLICT` |
| 非 role 级证据歧义 | `AMBIGUOUS`，根因为候选值或上下文歧义 | `SYS-EVIDENCE-AMBIGUOUS` |
| required slot 依赖低置信解析区域 | `LOW_CONFIDENCE`，根因为 parse / region confidence | `SYS-PARSE-LOW-CONFIDENCE` |
| required slot 候选存在但仅达到低置信候选门槛 | `LOW_CONFIDENCE`，根因为 candidate attribution / source confidence | `SYS-PARSE-LOW-CONFIDENCE` 或现有低置信内部诊断；对外语义统一按解析/证据低置信处理 |
| required slot 因 token 截断未被保留 | `BUDGET_TRUNCATED` | `SYS-EVIDENCE-BUDGET-EXCEEDED` |
| post-build validation 失败 | `INVALID bundle` | `SYS-EVIDENCE-BUNDLE-INVALID` |
| Gemma 不可用且当前点必须依赖模型辅助 | 非 preflight 成功完成 | `SYS-MODEL-UNAVAILABLE` |
| 规则执行异常 | 后端确定性裁判失败 | `SYS-RULE-ERROR` |

#### 内部 `SYS-*` 到外部语义映射

| 内部诊断 | PointStatus | notConcludedReason | notConcludedDetail | 说明 |
| --- | --- | --- | --- | --- |
| `SYS-INDEX-INCOMPLETE` | `NOT_CONCLUDED` | `EVIDENCE_NOT_FOUND` | `INDEX_MISSING` | 对齐架构第 17 节 |
| `SYS-ROLE-CONFLICT` | `NOT_CONCLUDED` | `EVIDENCE_AMBIGUOUS` | `ROLE_CONFLICT` | 对齐架构第 17 节 |
| `SYS-EVIDENCE-AMBIGUOUS` | `NOT_CONCLUDED` | `EVIDENCE_AMBIGUOUS` | 省略 | 当前不新增新的外部 detail 枚举 |
| `SYS-PARSE-LOW-CONFIDENCE` | `NOT_CONCLUDED` | `PARSE_LOW_CONFIDENCE` | `PARSE_LOW_CONFIDENCE` | 对齐架构第 17 节 |
| `SYS-EVIDENCE-BUDGET-EXCEEDED` | `NOT_CONCLUDED` | `MODEL_BUDGET_EXCEEDED` | `BUDGET_TRUNCATED` | 外部沿用既有预算类 reason，内部继续区分 evidence/model budget |
| `SYS-EVIDENCE-BUNDLE-INVALID` | `NOT_CONCLUDED` | `INTERNAL_RULE_ERROR` | 省略 | 视为证据契约/后端校验失败，不暴露新的外部 detail |
| `SYS-MODEL-UNAVAILABLE` | `NOT_CONCLUDED` | `MODEL_UNAVAILABLE` | 省略 | 对齐架构第 17 节 |
| `SYS-RULE-ERROR` | `NOT_CONCLUDED` | `INTERNAL_RULE_ERROR` | 省略 | 对齐架构第 17 节 |

附加约束：

* `SYS-* -> NOT_CONCLUDED` 是外部稳定契约；外部系统不得依赖内部技术码。
* `notConcludedDetail` 只使用 `docs/ARCHITECTURE.md` 第 17 节已有枚举；本 ADR 不新增新的外部 detail。
* `missingOptionalSlots[]` 的 `cause / contributingCauses / businessMessage` 继续沿用架构第 17 节；其中 optional slot 的 `BUDGET_TRUNCATED / LOW_CONFIDENCE / SECONDARY_BUNDLE_PARTIAL / NOT_FOUND` 不改写点级 `PointStatus`，但必须保留到结果快照或管理台诊断。

### 6.3 外部 Result API 影响结论

* 不修改 `PointStatus` 稳定集合，仍固定为 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`。
* 不修改 `notConcludedReason / notConcludedDetail` 的外部语义集合；本 ADR 只补齐内部 `SYS-*` 到这些既有外部语义的映射。
* `SourceAnchor` 在本 ADR 中先完成内部正式化；对外现有 `sourceAnchors` 字段按“稳定化 + 兼容新增”原则演进，不删除、不重命名既有外部字段。
* 若实现需要更新对外 OpenAPI 文档、补充兼容字段说明或标注 `notConcludedDetail` diagnostic-only 约束，必须拆出后续 OpenAPI 契约任务；该任务属于 `TASK-027` 的实现前置兼容任务，不属于本轮实现。

### 7. Gemma 边界

本 ADR 只定义 `resolverPolicy=gemmaIfAmbiguous` 的资格语义：

* preflight 可将相关候选标记为未来模型辅助的合格输入。
* 不允许回灌全文。
* Gemma 不能覆盖 deterministic `HIGH`，不能直接决定最终状态。
* Gemma Provider、调用预算、artifact 与重试实现留给 `TASK-028`。

### 8. 兼容与渐进迁移

* 当前 `PointEvidence` 和 `SourceAnchorSummary` 视为 adapter 兼容期结构；`TASK-027` 不立即删除旧结构，除非实现阶段另行冻结替换方案。
* `TASK-027` 优先采用兼容新增或双读写适配方式引入正式 slot / anchor 语义；是否进入双写期由实现阶段单独冻结，不在本 ADR 中预设为必须动作。
* `ReviewResultSnapshot` 在语义上受影响：正式 `SourceAnchor`、point-level coverage、missing optional slot 和 preflight/validation 诊断都属于允许新增的快照信息；但这些新增字段应保持可选、兼容读取，不要求历史快照回填。
* 当前 `TASK-027` 不允许数据库迁移；若实现前无法确认现有持久化 JSON/表结构是否足以承载兼容新增字段，必须先执行 snapshot / persistence 只读核对任务，并据此拆出数据库/快照兼容任务。
* 若实现最终触发外部 Result API、持久化 JSON 或数据库 schema 的边界变更，需在实施前补充对应兼容任务，并按触达范围更新相关 ADR。

### 8.1 businessMessage 与模板版本

* `businessMessage` 继续遵循 `docs/ARCHITECTURE.md` 第 17 节：由后端固定模板生成，不由模型生成，也不允许规则集写任意自由文本。
* 本 ADR 不新增模板版本字段，也不在当前 Draft 中把模板版本直接写死进快照 schema。
* 若后续需要把模板版本显式写入 `ReviewResultSnapshot` 或外部 API 文档，应作为 OpenAPI / snapshot compatibility 后续任务处理，而不是在当前 ADR 前置阶段直接扩展实现范围。

## 备选方案

### 方案 A：继续扩展 PointEvidence

把 slot、coverage、诊断和 anchor 字段继续加入 `PointEvidence`。

放弃原因：

* 会把候选归属、运行资格、裁判输入和结果定位耦合为一个点级对象。
* 无法清晰表达一个审核点存在多个 required / optional slot。

### 方案 B：直接实现完整 EvidenceBundle 平台

一次完成 family bundle、跨族 overlay、token budget、模型调用计划和持久化快照。

放弃原因：

* 超出 `TASK-027` 最小治理范围。
* 会提前吞并 `TASK-028` 和后续平台化能力。

### 方案 C：分层引入正式 slot preflight 与 anchor 契约

先固定 slot 定义、运行时 coverage、裁判门禁和 anchor 契约，再按 MVP 需要逐步接入 bundle 与模型辅助。

选择方案 C。

## 选择理由

* 与 `docs/ARCHITECTURE.md` 的单向组件依赖一致。
* 保持 `ADR-014` 的 resolver 语义稳定，不让 `EvidenceSlot` 变成第二个分类器。
* 能先解决“证据不足仍可能裁判”和“结果定位只是摘要”的核心风险。
* 允许兼容当前最小实现，避免一次性引入完整 evidence 平台。

## 影响

### 正向影响

* 每个审核点的证据需求和降级原因可版本化、可测试、可追溯。
* 业务 Finding 与 `SYS-*` 的证据门禁更明确。
* 结果页可以稳定解释审核点使用了什么证据以及证据位于何处。
* 为 `TASK-028` 提供明确的模型辅助入口，而不让模型越权裁判。

### 代价与风险

* 需要把当前点级 evidence 过渡结构逐步拆成 slot 定义、coverage 和 anchor。
* 结果快照与持久化兼容性需要在实现前专项核对；当前不得把“无迁移”误写成已确认事实。
* 首批审核点 slot 定义过宽会带来过度平台化和持续 `NOT_CONCLUDED` 风险。
* 若 coverage 状态与 `SYS-*` 映射重复维护，可能产生不一致，必须由单一 preflight 结果驱动。

## 不做什么

* 不接入 Gemma Provider。
* 不拆分 `ParserBackedReviewInputPreparer`。
* 不新增 `ReviewPointFamily`、`CandidateRole`、审核点或合同类型。
* 不把 PDF/OCR、页码定位或精确字符范围设为 MVP 强制能力。
* 不实现跨任务 artifact reuse、学习型排序或复杂冲突自动消解。

## 回滚与迁移

* 回滚方式：在正式 slot preflight 尚未成为裁判门禁前，可回退新增适配层并继续使用当前最小 evidence 路径；不得回退 `ADR-014` 的 `HIGH` admission gate。
* 数据迁移影响：Draft 阶段不创建迁移；`TASK-027` 当前不允许数据库迁移。如实现需要数据库变更，先做 snapshot / persistence 只读核对，再拆数据库/快照兼容任务并补充对应 ADR。
* 历史快照兼容性：历史结果继续按旧结构读取；新字段采用可选兼容读取，不要求历史数据回填。

## 验证方式

ADR 接受前：

* 与 `docs/ARCHITECTURE.md` 第 9.6、10.1、10.4、10.5、10.9 节及 `SourceAnchor` 定义逐项核对。
* 与 `ADR-014` 核对，确保没有改变五档置信度或 `HIGH` admission gate。
* 确认未进入 `TASK-028 / TASK-032`。

ADR 接受后：

* 首批 9 个审核点的 slot 定义审查。
* required slot 缺失、歧义、低置信、预算截断的单元测试。
* parser-backed 正负 fixture 回归。
* `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED` 的 anchor 门禁测试。
* 历史结果读取兼容测试。

## 后续动作

1. 人工评审并决定是否接受本 ADR。
2. 接受后冻结 `TASK-027` 实际修改文件和测试矩阵。
3. 再判断是否拆分局部 execution / readonly-review `TASK_SPEC`。
4. 完成 `TASK-027` 后，才评估是否进入 `TASK-028`。

## 关联

* 相关任务：`tasks/active/TASK-027-evidence-slot-source-anchor-governance.md`
* 上游任务：`tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
* 相关 ADR：`decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
* 相关文档：`docs/ARCHITECTURE.md`、`docs/backend.md`、`docs/ai-review.md`
* 相关版本：后续 `RuleSetVersion`，具体版本号待实现阶段确定

## 待确认

* ADR 接受后，是否按“单独 OpenAPI 契约任务 + 单独 snapshot / persistence 只读核对任务”拆分实现前兼容工作。
* 若实现只读核对确认现有持久化结构不足，是否需要更新 `ADR-003 / ADR-013` 并单独拆数据库/快照兼容任务。
