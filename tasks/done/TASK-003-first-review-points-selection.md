# TASK-003：首批审核点选择

状态：已完成

类型：规划 / 审核点收敛

优先级：P0

负责人：待确认

创建日期：2026-06-07

来源：Superpowers planning / design review / task decomposition 审查结论、`docs/ai-review.md`

## 背景

V1 文档描述了多个审核点族和完整混合路由能力。第一轮 MVP 需要选择少量可验证、可解释、能覆盖最小审核闭环的审核点族，避免一开始就实现过多规则、证据槽和模型辅助路径。

## 目标

- 从 `PARTY_FIELDS`、`AMOUNT_TAX`、`PAYMENT_TERMS` 中选择首批审核点族。
- 明确每个首批审核点需要的结构化字段、证据槽、候选来源和结果状态。
- 明确哪些审核点延后到 Pilot。
- 为规则定义、证据索引、结果页展示和样本验证提供边界。

## 非目标

- 不实现审核规则。
- 不编写 prompt。
- 不创建正则库或词库。
- 不创建模型调用代码。
- 不创建业务 finding 样例库。
- 不扩大到所有合同类型和全部审核点。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`PROJECT_BRIEF.md`、`ROADMAP.md`
- 相关架构章节：审核点定义、EvidencePacket、FamilyEvidencePlan、合成优先级。
- 相关 ADR：`ADR-002-v1-result-and-diagnostic-contract` 草案。
- 上游任务：`TASK-001-v1-mvp-scope-gate`

## 范围

### 包含

- 选择首批审核点族和最小审核点清单。
- 为每个审核点记录目标、输入字段、候选证据、是否需要 Gemma/A30、预期状态。
- 标记 core review point。
- 明确 `NOT_CONCLUDED` 和 `SYS-*` 的触发边界。

### 不包含

- 不写规则代码。
- 不定义完整 RuleSetVersion schema。
- 不设计完整 Quality Copilot 流程。
- 不生成或处理真实合同样本。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- 证据不足不得生成业务 finding。
- Gemma/A30 只做局部抽取、证据选择或复杂语义辅助。
- 后端负责结构化比对和确定性最终裁判。
- 审核点必须是可执行定义，不是单纯 prompt。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`ROADMAP.md`
- 必读：`docs/ai-review.md`
- 按需：`docs/backend.md`
- 按需：`docs/frontend.md`
- 按需：`docs/database.md`

## 交付物

- 首批审核点族和审核点清单。
- 每个审核点的输入、证据槽、路由策略和非结论边界说明。
- 延后审核点清单。
- 后续规则、索引、样本和测试任务建议。

## 验收标准

- 首批审核点足够小，能支撑第一条可跑通审核闭环。
- 每个审核点都能说明为什么需要或不需要 Gemma/A30。
- 每个审核点都能说明证据不足时如何进入 `NOT_CONCLUDED` 或 `SYS-*`。
- 未选择的审核点有明确延后理由。

## 测试与验证

- 本任务为规划任务，不运行代码测试。
- 验证方式是用 2-3 个脱敏或合成合同场景人工检查审核点是否可执行。
- MVP 验收样本由业务方提供脱敏或合成中文 `.docx` 工程采购合同。平台不负责自动识别样本是否已脱敏，也不在 MVP 中提供脱敏检测或自动脱敏能力；样本进入仓库或评测环境的前提，是业务方/data owner 已确认其可用于项目验证。

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`。
- 必须更新 `ROADMAP.md` 或相关任务列表。
- 必须更新 `changelog/2026-06.md`。
- 是否需要更新 `docs/ai-review.md` 或 `docs/backend.md`：待确认。
- 是否需要新增或更新 ADR：待确认。

## 风险

- 审核点过少可能无法验证结果页和 EvidencePacket 设计。
- 审核点过多会导致第一轮 MVP 范围失控。
- 如果缺少样本来源，审核点可执行性只能停留在纸面。

## 待确认

- 首批合同类型是否只选一个。
- 首批结构化字段由谁提供。
- 首批样本集数量和数据 owner。
- 哪些审核点算 core review point。

## 后续可能派生任务

- 首批 ReviewPointDefinition 草案任务。
- 首批字段词库/正则边界任务。
- 首批样本集与脱敏任务。
- 审核点结果页展示任务。

## 任务产出

本任务形成首批审核点选择基线，并记录到 `decisions/ADR-005-first-review-points-selection.md`。ADR 状态已确认为 `Accepted`。

### MVP 第一批必须实现的审核点

| ReviewPointFamily | 审核点 | core | 说明 |
| --- | --- | --- | --- |
| `PARTY_FIELDS` | `PARTY_A_NAME_CONSISTENCY` | 是 | 结构化甲方名称与合同候选一致性 |
| `PARTY_FIELDS` | `PARTY_B_NAME_CONSISTENCY` | 是 | 结构化乙方名称与合同候选一致性 |
| `AMOUNT_TAX` | `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` | 是 | 结构化合同总金额与合同金额候选一致性 |
| `AMOUNT_TAX` | `TAX_AMOUNT_FORMULA_CONSISTENCY` | 是 | 含税、不含税、税额公式关系；已确认纳入 MVP |
| `PAYMENT_TERMS` | `PREPAYMENT_RATIO_CONSISTENCY` | 是 | 结构化预付款比例与付款条款候选一致性 |
| `PAYMENT_TERMS` | `PROGRESS_PAYMENT_RATIO_CONSISTENCY` | 是 | 结构化累计进度款比例与付款条款候选一致性 |
| `PAYMENT_TERMS` | `COMPLETION_PAYMENT_RATIO_CONSISTENCY` | 是 | 结构化累计竣工款比例与付款条款候选一致性 |
| `PAYMENT_TERMS` | `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY` | 是 | 结构化累计结算款比例与付款条款候选一致性 |
| `PAYMENT_TERMS` | `WARRANTY_RETENTION_RATIO_CONSISTENCY` | 是 | 结构化质保款比例与付款条款候选一致性 |

### PARTY_A_NAME_CONSISTENCY

- `ReviewPointFamily`: `PARTY_FIELDS`
- 输入字段：`partyAName`。
- EvidenceSlot：
  - `partyANameCandidate`：required，acceptedRoles=`PARTY_A`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`gemmaIfAmbiguous`。
  - `partyContextAnchor`：required，甲方、发包人、采购人等 label 附近正文、表格或签署页 block。
- 策略：规则/正则/词库候选优先，后端做名称归一化和确定性比对。
- Gemma/A30：仅在候选为 `MEDIUM` / `CONFLICTED` 且存在局部上下文时做归属歧义辅助。
- 降级：结构化字段缺失输出 `SYS-MISSING-INPUT`；required slot 缺失输出 `SYS-INDEX-INCOMPLETE`；角色冲突输出 `SYS-ROLE-CONFLICT` 或 `SYS-EVIDENCE-AMBIGUOUS`；解析低置信输出 `SYS-PARSE-LOW-CONFIDENCE`。

### PARTY_B_NAME_CONSISTENCY

- `ReviewPointFamily`: `PARTY_FIELDS`
- 输入字段：`partyBName`。
- EvidenceSlot：
  - `partyBNameCandidate`：required，acceptedRoles=`PARTY_B`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`gemmaIfAmbiguous`。
  - `partyContextAnchor`：required，乙方、承包人、供应商、服务方等 label 附近正文、表格或签署页 block。
- 策略：规则/正则/词库候选优先，后端做名称归一化和确定性比对。
- Gemma/A30：仅在候选为 `MEDIUM` / `CONFLICTED` 且存在局部上下文时做归属歧义辅助。
- 降级：同 `PARTY_A_NAME_CONSISTENCY`。

### CONTRACT_TOTAL_AMOUNT_CONSISTENCY

- `ReviewPointFamily`: `AMOUNT_TAX`
- 输入字段：`contractTotalAmount`、`currency`。
- 可选字段：`amountTaxIncludedFlag`、`contractTotalAmountText`。
- EvidenceSlot：
  - `contractTotalAmountCandidate`：required，acceptedRoles=`CONTRACT_TOTAL_AMOUNT`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`deterministicOnly`。
  - `amountContextAnchor`：required，合同价款、合同金额、总价、含税总价等 label 附近正文或表格 block。
  - `amountInWordsCandidate`：optional，中文大写金额候选。
- 策略：ValueGrammar、规则和正则候选优先，后端金额归一化比对。
- Gemma/A30：默认不需要；后续只有在 slot policy 明确允许时才可做局部归属辅助。
- 降级：结构化金额或币种缺失输出 `SYS-MISSING-INPUT`；金额候选缺失输出 `SYS-INDEX-INCOMPLETE`；金额角色冲突输出 `SYS-ROLE-CONFLICT`；关键候选被预算截断输出 `SYS-EVIDENCE-BUDGET-EXCEEDED`。

### TAX_AMOUNT_FORMULA_CONSISTENCY

- `ReviewPointFamily`: `AMOUNT_TAX`
- 输入字段：`taxIncludedAmount`、`taxExcludedAmount`、`taxAmount`。
- 可选字段：`taxRate`、`currency`。
- EvidenceSlot：
  - `taxIncludedAmountCandidate`：required，acceptedRoles=`TAX_INCLUDED_AMOUNT`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`deterministicOnly`。
  - `taxExcludedAmountCandidate`：required，acceptedRoles=`TAX_EXCLUDED_AMOUNT`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`deterministicOnly`。
  - `taxAmountCandidate`：required，acceptedRoles=`TAX_AMOUNT`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`deterministicOnly`。
  - `taxRateCandidate`：optional，acceptedRoles=`TAX_RATE`，来源为经 CandidateResolver 可靠归属的 `CandidateRole=taxRate`；不得把其他比例候选按同值直接复用为税率。
- 策略：后端 `FORMULA_RULE`，校验含税金额、不含税金额、税额之间的公式关系。
- Gemma/A30：不需要，公式和数值裁判不得交给模型。
- 降级：结构化税额字段未提供时 `SKIPPED`；合同税额 slot 缺失输出 `SYS-INDEX-INCOMPLETE`；角色冲突输出 `SYS-ROLE-CONFLICT`；规则异常输出 `SYS-RULE-ERROR`。

### PREPAYMENT_RATIO_CONSISTENCY

- `ReviewPointFamily`: `PAYMENT_TERMS`
- 输入字段：`prepaymentRatio`。
- 可选字段：`paymentScheduleText`、`contractTotalAmount`。
- EvidenceSlot：
  - `prepaymentRatioCandidate`：required，acceptedRoles=`PREPAYMENT_RATIO`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`gemmaIfAmbiguous`。
  - `paymentClauseAnchor`：required，付款、预付款、进度款、结算款等相关条款 block。
- 策略：比例 ValueGrammar、规则/正则候选优先，后端比例归一化比对。
- Gemma/A30：仅在预付款候选与进度款/结算款候选语义归属不清时做局部归属辅助。
- 降级：`prepaymentRatio` 未提供时 `SKIPPED`；付款条款候选缺失输出 `SYS-INDEX-INCOMPLETE`；角色冲突输出 `SYS-ROLE-CONFLICT` 或 `SYS-EVIDENCE-AMBIGUOUS`；Gemma 输出不完整或矛盾输出对应 `SYS-MODEL-*`。

### 其余月度付款比例一致性审核点

- `PROGRESS_PAYMENT_RATIO_CONSISTENCY`：输入 `progressPaymentRatio`，required EvidenceSlot=`progressPaymentRatioCandidate`，acceptedRole=`PROGRESS_PAYMENT_RATIO`。
- `COMPLETION_PAYMENT_RATIO_CONSISTENCY`：输入 `completionPaymentRatio`，required EvidenceSlot=`completionPaymentRatioCandidate`，acceptedRole=`COMPLETION_PAYMENT_RATIO`。
- `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY`：输入 `settlementPaymentRatio`，required EvidenceSlot=`settlementPaymentRatioCandidate`，acceptedRole=`SETTLEMENT_PAYMENT_RATIO`。
- `WARRANTY_RETENTION_RATIO_CONSISTENCY`：输入 `warrantyRetentionRatio`，required EvidenceSlot=`warrantyRetentionRatioCandidate`，acceptedRole=`WARRANTY_RETENTION_RATIO`。
- 四点共享 required `paymentClauseAnchor`，策略为比例 ValueGrammar / 正则候选优先、Gemma 局部角色歧义辅助、后端确定性比对。
- 节点付款时仍执行 `PREPAYMENT_RATIO_CONSISTENCY`，其余四点因不适用而 `SKIPPED`；不适用不得输出 `SYS-* / NOT_CONCLUDED` 或业务 finding。月度付款下字段缺失、证据不足、角色冲突、模型异常分别输出对应 `SYS-* / NOT_CONCLUDED`，不得生成无可靠证据的业务 finding。

### 延后审核点

延后到 Pilot：

- `PARTY_FIELDS`：签署页主体与正文主体冲突、主体统一社会信用代码一致性、多乙方/联合体/代理人主体关系。
- `AMOUNT_TAX`：中文大写金额一致性、税率合法性、含税/不含税上下文冲突、币种与单位复杂归一。
- `PAYMENT_TERMS`：比例之间的跨字段关系、付款节点顺序、付款条件完整性、发票前置条件，以及节点付款其他阶段审核。

延后到 Production Readiness：

- 审核点自动优化、候选规则自动发布、完整质量治理样本生命周期、跨合同类型专属付款规则、生产保护与审计门禁。

### SYS-* 与 NOT_CONCLUDED 边界

- EvidencePacket 或 required EvidenceSlot 不足时，不生成业务 finding。
- 结构化核心输入字段缺失时，输出 `SYS-MISSING-INPUT`；可选审核点字段缺失时按审核点定义 `SKIPPED`。
- 候选索引缺失必要证据时输出 `SYS-INDEX-INCOMPLETE`。
- 角色冲突或证据歧义时输出 `SYS-ROLE-CONFLICT` 或 `SYS-EVIDENCE-AMBIGUOUS`。
- 关键证据被 token 预算截断时输出 `SYS-EVIDENCE-BUDGET-EXCEEDED`。
- 解析低置信时输出 `SYS-PARSE-LOW-CONFIDENCE`。
- Gemma 不可用、超时、输出不完整或 primary/supplement 冲突时输出对应 `SYS-MODEL-*`。
- `SYS-*` 均映射为业务化 `NOT_CONCLUDED`，不得计入业务风险 finding。

### 待确认

- 评测环境、样本文件命名规范、保存目录和预期结果文件格式。

### 后续任务线索

- 创建首批 `ReviewPointDefinition` 草案任务。
- 创建首批字段词库、ValueGrammar 和正则边界任务。
- 创建首批样本场景任务，明确样本命名、保存目录、预期结果文件格式和业务方/data owner 确认记录。
- 在 `ADR-002` 中继续固化普通结果页、管理台和外部 API 的状态/诊断可见性。

## 完成记录

- 完成日期：2026-06-07。
- 变更文件：`CURRENT_CONTEXT.md`、`ROADMAP.md`、`docs/ai-review.md`、`changelog/2026-06.md`、`decisions/ADR-005-first-review-points-selection.md`、本任务包。
- 测试结果：规划任务，未运行代码测试；已按 2-3 个合成场景口径人工检查首批点可覆盖一致、冲突、缺证据/歧义降级三类场景。
- 遗留问题：评测环境、样本文件命名规范、保存目录和预期结果文件格式仍待确认。
- 备注：本任务未创建业务代码，未创建脚手架，未安装依赖，未修改数据库。
