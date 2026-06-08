# ADR-007: Tuning Packet Architecture

状态：Accepted

日期：2026-06-08

## 背景

合同审核质量会随着合同样本、规则、模型、词库、EvidenceSlot 和 CandidateResolver 迭代持续变化。项目团队不能长期依赖运维人员手工理解日志、拼接上下文并询问公网 AI。平台需要生成最小必要、结构化、可复制、可追溯的调优诊断包，让 DeepSeek、ChatGPT、Claude、Gemma 或未来模型能够理解系统为什么做出某个审核结果。

Tuning Packet 是调优治理链路的数据底座，不是普通任务日志，也不是生产审核结果。

## 适用范围

- 影响模块：Task Execution、ReviewResultSnapshot、Evidence Planning、Model Gateway、Admin Console、Quality Governance。
- 影响阶段：MVP 起支持基础 Tuning Packet；Cross-Model Diagnostic 和结构化 AI 建议延后到 Pilot。
- 是否影响外部 API：MVP 不默认暴露给外部业务调用方；管理台可导出。
- 是否影响数据库或版本快照：是，需要保存或可重建 packet 与版本引用。
- 是否影响模型、规则、prompt、正则或证据选择：是，但只作为调优诊断输入，不直接改变生产配置。

## 决策

平台新增 `TuningPacket` 作为每次 execution 可生成的结构化调优诊断包。它基于正式审核执行产物生成，用于解释问题、复制给 AI 辅助分析、记录候选调优建议和支撑后续回归验证。

Tuning Packet 原则：

- AI 需要看到为什么选这个候选、为什么拒绝其他候选、证据是否完整、模型是否调用、后端如何裁判。
- AI 不需要看到整份合同、所有日志、全部上下文、完整 prompt、完整 raw output、endpoint secret 或 stack trace。
- Tuning Packet 不改变 `ReviewResultSnapshot`。
- Tuning Packet 不代表生产规则变更。
- Tuning Packet 的导出必须有裁剪策略和用途标记。

MVP 最小能力：

```text
TuningPacket
- packetId
- taskId
- executionId
- packetVersion
- contractType
- modelProfileUsed
- ruleSetVersion
- parserVersion
- promptVersion
- evidenceSelectorVersion
- candidateResolverVersion
- executionSummary
- pointDiagnostics[]
- parseQualitySummary
- exportConfig
- dataMode?
- redactionStatus
```

`PointDiagnostic` 最小字段：

```text
reviewPointCode
pointStatus
expectedStructuredValue
selectedCandidate
rejectedCandidates[]
evidenceSlotCoverage
sourceAnchorSnippets[]
modelAssistSummary
deterministicRuleResult
sysDiagnostics[]
preflightFailedReason?
modelWasCalled
evidenceBundleTruncated
truncatedSlots[]
suspectedFailureClasses[]
```

`ExecutionSummary` 最小字段：

```text
totalPoints
byStatus
preflightFailCount
modelCalledCount
truncationOccurredCount
candidateConflictCount
topFailureClasses[]
tuningReadinessScore
```

`tuningReadinessScore` 是启发式建议，不是硬门禁：

```text
EVIDENCE_LAYER_FIRST
DEFINITION_INDEX_FIRST
READY_FOR_AI_TUNING
MIXED_ISSUES
```

导出模式：

```text
SINGLE_POINT
FOCUSED
FULL
```

MVP 默认只开放 `SINGLE_POINT` 和 `FOCUSED`。`FULL` 仅限受控内部场景，不默认给普通管理员。

## 备选方案

- 方案 A：继续让运维人员复制任务日志给 AI。成本低，但上下文不可控、不可复现、容易泄露或误导。
- 方案 B：新增 Tuning Packet 作为标准调优输入。当前采用。
- 方案 C：直接让公网 AI 读取任务详情页。不可控，不采用。

## 选择理由

- 运维人员不一定具备判断 EvidenceSlot、CandidateResolver、prompt 或模型问题的能力。
- 结构化调优包能降低“复制日志问 AI”的成本。
- 标准输入能让不同模型对同一问题给出可比较建议。
- 导出裁剪能减少泄露和上下文污染。

## 影响

### 正向影响

- AI 能更稳定理解系统失败原因。
- 后续 Cross-Model Diagnostic 和 AI Tuning Advice 有统一输入。
- 调优建议可追溯到具体 execution、版本和证据片段。

### 代价与风险

- Tuning Packet 本身会成为重要数据资产，需要权限和导出控制。
- 若字段过多，MVP 可能膨胀；因此只落地最小诊断字段。
- `tuningReadinessScore` 早期阈值可能不准，只能作为建议。

## 不做什么

- 不把 Tuning Packet 当作生产审核结果。
- 不让 Tuning Packet 自动修改规则、prompt、EvidenceSlot、CandidateResolver 或 ModelProfile。
- 不默认导出完整合同或完整模型原始输出。
- MVP 不自动调用公网 AI。

## 回滚与迁移

- 回滚方式：关闭 Tuning Packet 导出入口，不影响正式审核链路。
- 数据迁移影响：已有 ReviewResultSnapshot 不需要重写；Tuning Packet 可按需重建或延后生成。
- 历史快照兼容性：Tuning Packet 只引用快照，不覆盖快照。

## 验证方式

- 对 `NOT_CONCLUDED`、候选冲突、模型异常、证据截断样例生成 `FOCUSED` packet。
- 检查 packet 不包含完整合同、完整 prompt、完整 raw output 或 secret。
- 检查 packet 能定位到具体审核点、证据、候选和失败分类。

## 后续动作

- PRD 新增 AI 调优治理能力域。
- 任务详情页后续新增 `AI 调优包` tab。
- Pilot 再引入 Cross-Model Diagnostic Run 和结构化 AITuningAdvice。

## 关联

- 相关 ADR：ADR-006、ADR-009。
- 相关文档：`PRD.md`、`docs/ARCHITECTURE.md`、`docs/ai-review.md`。

## 待确认

- Tuning Packet 是否持久化为独立表，还是按 execution 产物可重建。
- `dataMode`、`redactionStatus` 和导出权限的生产上线门禁。
