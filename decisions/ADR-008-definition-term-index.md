# ADR-008: DefinitionTermIndex

状态：Accepted

日期：2026-06-08

## 背景

长合同中常出现“本合同所称……是指……”“以下简称……”“前款所称……”等定义条款。定义条款会影响合同角色、付款节点、金额口径、税费口径和术语解释。如果定义条款没有被索引或按需注入相关证据上下文，审核点可能因证据缺失、候选归属不清或术语误解而 `NOT_CONCLUDED` 或误判。

定义条款不是 Parser 的基础解析职责，而是基于解析产物构建的索引能力。

## 适用范围

- 影响模块：CandidateIndex、Evidence Planning、CandidateResolver、Tuning Packet、ReviewPointDefinition。
- 影响阶段：MVP 可落地轻量能力；复杂定义冲突治理延后。
- 是否影响外部 API：否。
- 是否影响数据库或版本快照：是，需要保存或可重建定义条目及版本引用。
- 是否影响模型、规则、prompt、正则或证据选择：是，影响 PointEvidenceOverlay 的定义上下文注入。

## 决策

平台新增 `DefinitionTermIndex`。它在 Parser 产出 `DocumentBlock / TableBlock / SectionTree` 后，与 CandidateIndex 并行构建，不放入 Parser 内部。

最小结构：

```text
DefinitionTermIndex
- definitionId
- term
- normalizedTerm
- definitionText
- sourceAnchor
- applicableScope: GLOBAL / SECTION / APPENDIX / TABLE
- scopeReference?
- confidence
- conflictsWith[]
- patternVersion
```

构建触发模式包括但不限于：

```text
本合同所称
本协议所称
以下简称
本条所称
前款所称
统称为
合称为
```

触发查询条件由 `contractTypeProfile`、`ReviewPointDefinition` 或 EvidenceSlot policy 版本化声明，不靠人工临时判断。MVP 优先覆盖：

- 合同角色类。
- 付款节点类。
- 金额口径类。
- 税费口径类。
- 术语解释类。

注入规则：

- 命中的定义作为 compact definition context 注入相关 `PointEvidenceOverlay`。
- `GLOBAL` scope 的定义可作为 shared context 注入相关 ReviewPointFamily。
- 不默认注入每个 EvidenceBundle。
- 预算不足导致定义未注入时，必须记录 truncation reason。

未命中和冲突处理：

- 索引无定义：记录 `DEFINITION_TERM_MISSING_NO_INDEX`。
- 定义存在但因预算截断未注入：记录 `DEFINITION_TERM_MISSING_TRUNCATED`。
- 同一术语存在冲突定义：记录 `DEFINITION_TERM_CONFLICT`。
- 定义缺失或冲突影响当前审核点结论时，输出 `NOT_CONCLUDED` 和业务可读原因；不得猜测定义。
- 普通结果页不把定义冲突直接展示为业务 `WARNING` finding；管理台诊断可展示 warning 摘要。

## 备选方案

- 方案 A：把定义条款识别写入 Parser。职责边界不清，不采用。
- 方案 B：新增 DefinitionTermIndex，作为索引层能力。当前采用。
- 方案 C：让模型在每次审核点 prompt 中自行理解定义。不可追溯，不采用。

## 选择理由

- 定义条款本质是索引和证据选择问题，不是基础解析问题。
- 按需注入比每个分片都注入更节省 token，并更可解释。
- 定义命中、缺失、截断和冲突可进入 Tuning Packet，帮助定位问题。

## 影响

### 正向影响

- 减少因定义条款缺失导致的候选归属错误。
- 支持 AI 调优包解释定义相关失败。
- 保持 Parser 简洁，不把语义索引塞入解析层。

### 代价与风险

- 定义识别规则需要版本化和回归测试。
- 短语触发模式可能漏召回，需要后续样本迭代。
- 定义冲突处理会增加 `NOT_CONCLUDED`，但这是符合证据原则的保守行为。

## 不做什么

- 不在 MVP 做复杂法律术语推理。
- 不默认把所有定义注入所有 EvidenceBundle。
- 不让模型自行决定使用哪个冲突定义。

## 回滚与迁移

- 回滚方式：关闭 DefinitionTermIndex 注入，仅保留诊断或完全停用。
- 数据迁移影响：已有快照不回写定义索引。
- 历史快照兼容性：新任务使用新索引版本，历史结果不变。

## 验证方式

- 构造包含“本合同所称”“以下简称”的合同样本。
- 验证相关审核点能在 `PointEvidenceOverlay` 中获得 compact definition context。
- 验证定义缺失、定义截断和定义冲突分别进入不同诊断分类。

## 后续动作

- 更新 PRD 和架构文档。
- 在 Tuning Packet 的 `PointDiagnostic` 中记录定义查询、命中、缺失、截断和冲突。
- 后续 Word parser baseline / CandidateIndex 任务中细化实现。

## 关联

- 相关 ADR：ADR-007、ADR-009。
- 相关文档：`docs/ARCHITECTURE.md`、`docs/ai-review.md`。

## 待确认

- DefinitionTermIndex 是否进入第一轮 MVP 开发任务，还是作为 MVP+。
- 首批定义触发 pattern 和适用审核点清单。
