# ADR-009: AI Tuning Governance

状态：Accepted

日期：2026-06-08

## 背景

项目长期成功不仅取决于首批审核能力，还取决于调优能力。模型会变化，可能从 Gemma4 切到 Gemma5、DeepSeek、Qwen、Claude 或 GPT；但 EvidenceSlot、CandidateResolver、ReviewPointFamily、Tuning Governance 是长期资产。

如果允许 AI 建议直接修改生产 prompt、规则、CandidateResolver、EvidenceSlot 或 ModelProfile，平台会失去可解释、可审计和可回归验证的边界。

## 适用范围

- 影响模块：Quality Governance、Admin Console、Model Gateway、RuleSetVersion、EvidenceSlot、CandidateResolver、ModelProfile。
- 影响阶段：MVP 起确立红线；Pilot 引入自动诊断建议；Production Readiness 完整治理。
- 是否影响外部 API：否。
- 是否影响数据库或版本快照：是，候选建议、验证结果和发布记录需要审计。
- 是否影响模型、规则、prompt、正则或证据选择：是，定义 AI 建议到生产变更的门禁。

## 决策

平台架构明确分为两条隔离链路。

生产审核链路：

```text
Contract
-> Parser
-> CandidateResolver
-> EvidenceSlot
-> Review Engine
-> Finding
-> ReviewResultSnapshot
```

原则：

- 稳定。
- 可解释。
- 可审计。
- 证据不足不生成业务 finding。
- 后端负责最终点级裁判。

调优治理链路：

```text
ReviewResultSnapshot
-> TuningPacket
-> CrossModelDiagnostic
-> AITuningAdvice
-> Candidate Change
-> Regression Validation
-> Human Approval
-> Release
```

原则：

- 实验。
- 诊断。
- 优化。
- 治理。

核心红线：

```text
AI Advice != Production Change
```

任何 AI 建议不得直接：

- 改 Prompt。
- 改 Rule。
- 改 CandidateResolver。
- 改 EvidenceSlot。
- 改 ModelProfile。
- 改后端确定性裁判。
- 影响当前正式 ReviewResultSnapshot。

AI 建议必须经过：

```text
Regression Validation
-> Human Approval
-> Version Release
```

才可能成为生产配置或规则变更。

## MVP / Pilot / Production 分层

MVP：

- 目标：让 AI 能帮助分析问题。
- 做 `TuningPacket`、`PointDiagnostic`、`ExecutionSummary`、`ExportConfig`。
- 支持复制诊断包给 DeepSeek、ChatGPT、Claude 或其他 AI。
- 不自动调用公网 AI。
- 不自动调优。
- 不自动改规则。

Pilot：

- 目标：让系统辅助调优。
- 新增 `CrossModelDiagnostic`、`CrossModelComparison`、`AITuningAdvice`。
- 只生成候选建议，不自动生效。
- 必须记录 Input Equivalence，避免不同上下文下误判模型能力差异。

Production Readiness：

- 目标：形成完整治理闭环。
- 新增 Regression Center、Release Governance、Rollback、Audit、OptimizationEffect。
- AI 建议上线后定期评估效果。
- 回归变差时自动告警或冻结；自动回滚需单独治理规则。

## Cross-Model Diagnostic 原则

跨模型诊断保留为 Pilot 能力。比较模型前必须记录输入等价性：

```text
STRICT
EXPANDED_WITH_POLICY
DIFFERENT_INPUT
```

只有 `STRICT` 才能直接比较模型能力差异。若输入不等价，必须先标记 input equivalence warning，不得直接归因为模型能力差距。

## 备选方案

- 方案 A：让 AI 自动改规则。风险不可控，不采用。
- 方案 B：AI 只生成候选建议，经过验证和人工批准后发布。当前采用。
- 方案 C：完全不使用 AI 调优。会导致调优效率过低，不采用。

## 选择理由

- 调优能力将长期比单次审核能力更重要。
- 团队不一定有专职模型调优能力，必须降低诊断和建议生成成本。
- 生产审核链路必须保持稳定、可解释、可审计。
- AI 适合提出候选建议，不适合无监督修改生产系统。

## 影响

### 正向影响

- 锁定 AI 调优不能绕过治理链路。
- 支持未来多模型参与调优。
- 防止项目变成“AI 自动改审核系统”。

### 代价与风险

- 短期无法获得全自动调优速度。
- 需要维护候选建议、回归验证、审批和发布记录。
- Pilot / Production Readiness 会增加治理实现成本。

## 不做什么

- 不把项目定位改成 AI 合同审核平台。
- 不让 AI 直接修改生产配置。
- 不让 AI 直接决定业务 finding。
- 不让 Cross-Model Diagnostic 覆盖正式审核快照。

## 回滚与迁移

- 回滚方式：关闭调优治理入口，不影响生产审核链路。
- 数据迁移影响：候选建议和验证记录可独立保留。
- 历史快照兼容性：正式 ReviewResultSnapshot 不受调优链路变更影响。

## 验证方式

- AI 建议不能直接修改任何生产版本。
- 所有候选变更必须产生验证记录和人工批准记录。
- 未通过回归验证的建议不得发布。
- Cross-Model Diagnostic 不得修改原 execution 的 ReviewResultSnapshot。

## 后续动作

- PRD 新增 `AI 调优治理` 能力域。
- 架构文档新增 `Tuning Governance Chain`。
- 后续任务拆分时把 Tuning Packet、Cross-Model Diagnostic、Regression Validation 分阶段处理。

## 关联

- 相关 ADR：ADR-006、ADR-007、ADR-008。
- 相关文档：`PRD.md`、`docs/ARCHITECTURE.md`、`docs/ai-review.md`。

## 待确认

- Pilot 阶段 Cross-Model Diagnostic 的自动触发条件。
- Production Readiness 阶段自动冻结和回滚策略。
