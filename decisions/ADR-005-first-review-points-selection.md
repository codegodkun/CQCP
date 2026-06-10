# ADR-005: First Review Points Selection

Status: Accepted

日期：2026-06-07

## 背景

V1 MVP 需要从 `PARTY_FIELDS`、`AMOUNT_TAX`、`PAYMENT_TERMS` 中收敛首批审核点，跑通单份中文 Word 合同的最小、可解释、可验证审核闭环。当前任务不实现规则、prompt、正则库、模型调用或数据库迁移，只确定第一批可执行审核点边界。

## 适用范围

- 影响模块：AI 审核、后端裁判、证据索引、结果页展示、后续 ReviewPointDefinition 草案。
- 影响阶段：V1 MVP / Pilot。
- 是否影响外部 API：间接影响，后续最小任务创建 API 需要提供首批结构化字段。
- 是否影响数据库或版本快照：间接影响，后续快照需要记录审核点、EvidenceSlot、规则/正则/prompt/model 版本。
- 是否影响模型、规则、prompt、正则或证据选择：是，影响首批证据槽和混合路由边界。

## 决策

第一轮 MVP 首批审核点采用以下 9 个审核点，均为 core review point：

| ReviewPointFamily | MVP 审核点 | core | 主要策略 |
| --- | --- | --- | --- |
| `PARTY_FIELDS` | `PARTY_A_NAME_CONSISTENCY` | 是 | 结构化字段 + 正则/词库候选 + 后端确定性比对；Gemma 仅做歧义归属辅助 |
| `PARTY_FIELDS` | `PARTY_B_NAME_CONSISTENCY` | 是 | 结构化字段 + 正则/词库候选 + 后端确定性比对；Gemma 仅做歧义归属辅助 |
| `AMOUNT_TAX` | `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` | 是 | 金额 ValueGrammar + 正则候选 + 后端金额归一化比对 |
| `AMOUNT_TAX` | `TAX_AMOUNT_FORMULA_CONSISTENCY` | 是 | 后端公式裁判，结构化税额字段或合同内税额 slot 不足时不生成业务 finding |
| `PAYMENT_TERMS` | `PREPAYMENT_RATIO_CONSISTENCY` | 是 | 比例 ValueGrammar + 付款条款局部证据 + 后端比对；Gemma 仅处理预付款语义归属不清 |
| `PAYMENT_TERMS` | `PROGRESS_PAYMENT_RATIO_CONSISTENCY` | 是 | 累计进度款比例候选 + 后端比对；Gemma 仅处理比例角色歧义 |
| `PAYMENT_TERMS` | `COMPLETION_PAYMENT_RATIO_CONSISTENCY` | 是 | 累计竣工款比例候选 + 后端比对；Gemma 仅处理比例角色歧义 |
| `PAYMENT_TERMS` | `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY` | 是 | 累计结算款比例候选 + 后端比对；Gemma 仅处理比例角色歧义 |
| `PAYMENT_TERMS` | `WARRANTY_RETENTION_RATIO_CONSISTENCY` | 是 | 独立质保款比例候选 + 后端比对；Gemma 仅处理比例角色歧义 |

MVP 不新增审核点族，不引入 `DOCUMENT_STRUCTURE`、`SENSITIVE_INFO` 或 `CONTRACT_TYPE_SPECIFIC`。`PAYMENT_TERMS` 纳入月度付款五项比例的一致性审核，但仍不审核比例之间的跨字段关系、付款节点顺序、付款条件完整性或其他复杂付款语义。

## 首批审核点边界

### PARTY_A_NAME_CONSISTENCY

- `ReviewPointFamily`: `PARTY_FIELDS`
- 目标：结构化甲方名称与合同正文/签署页中甲方候选一致。
- required structured fields：`partyAName`。
- EvidenceSlot：
  - `partyANameCandidate`：required，acceptedRoles=`PARTY_A`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`gemmaIfAmbiguous`。
  - `partyContextAnchor`：required，来源为甲方、发包人、采购人等 label 附近正文、表格或签署页 block。
- 执行策略：`PATTERN_EXTRACT_THEN_COMPARE` + 后端 `STRUCTURED_COMPARE`；正则/词库召回主体候选，后端做名称归一化和确定性比对。
- Gemma/A30：只在候选为 `MEDIUM` / `CONFLICTED` 且存在可比较上下文时做 `AMBIGUITY_RESOLUTION`。
- 降级规则：`partyAName` 缺失输出 `SYS-MISSING-INPUT`；required slot 缺失输出 `SYS-INDEX-INCOMPLETE`；候选冲突输出 `SYS-ROLE-CONFLICT` 或 `SYS-EVIDENCE-AMBIGUOUS`；解析低置信输出 `SYS-PARSE-LOW-CONFIDENCE`；Gemma 不可用且不能弱回退输出 `SYS-MODEL-UNAVAILABLE`。

### PARTY_B_NAME_CONSISTENCY

- `ReviewPointFamily`: `PARTY_FIELDS`
- 目标：结构化乙方名称与合同正文/签署页中乙方候选一致。
- required structured fields：`partyBName`。
- EvidenceSlot：
  - `partyBNameCandidate`：required，acceptedRoles=`PARTY_B`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`gemmaIfAmbiguous`。
  - `partyContextAnchor`：required，来源为乙方、承包人、供应商、服务方等 label 附近正文、表格或签署页 block。
- 执行策略：`PATTERN_EXTRACT_THEN_COMPARE` + 后端 `STRUCTURED_COMPARE`；正则/词库召回主体候选，后端做名称归一化和确定性比对。
- Gemma/A30：只在候选为 `MEDIUM` / `CONFLICTED` 且存在可比较上下文时做 `AMBIGUITY_RESOLUTION`。
- 降级规则：同 `PARTY_A_NAME_CONSISTENCY`。

### CONTRACT_TOTAL_AMOUNT_CONSISTENCY

- `ReviewPointFamily`: `AMOUNT_TAX`
- 目标：结构化合同总金额（固定语义为含税总金额）与合同正文/表格中的含税合同总价候选一致。
- required structured fields：`contractTotalAmount`；币种由平台固定为 `CNY`。
- optional structured fields：`contractTotalAmountText`。
- EvidenceSlot：
  - `contractTotalAmountCandidate`：required，acceptedRoles=`CONTRACT_TOTAL_AMOUNT`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`deterministicOnly`。
  - `amountContextAnchor`：required，来源为合同价款、合同金额、总价、含税总价等 label 附近正文或表格 block。
  - `amountInWordsCandidate`：optional，来源为中文大写金额候选。
- 执行策略：`PATTERN_EXTRACT_THEN_COMPARE` + 后端金额归一化比对；ValueGrammar 和正则优先，不默认调用 Gemma。
- Gemma/A30：默认不需要；只有候选角色归属不清且 slot policy 明确为 `gemmaIfAmbiguous` 的后续版本才允许进入局部辅助。
- 降级规则：`contractTotalAmount` 缺失输出 `SYS-MISSING-INPUT`；金额候选缺失输出 `SYS-INDEX-INCOMPLETE`；多个高置信金额角色冲突输出 `SYS-ROLE-CONFLICT`；金额表达无法解析且无可裁判值输出 `SYS-INDEX-INCOMPLETE` 或 `SYS-PARSE-LOW-CONFIDENCE`；预算截断关键候选输出 `SYS-EVIDENCE-BUDGET-EXCEEDED`。非 `CNY` 输入在任务创建阶段拒绝，不进入审核点执行。

### TAX_AMOUNT_FORMULA_CONSISTENCY

- `ReviewPointFamily`: `AMOUNT_TAX`
- 目标：在证据完整时，校验合同总金额（含税）、不含税金额、税额之间的公式关系。
- required structured fields：`contractTotalAmount`、`taxExcludedAmount`、`taxAmount`。
- optional structured fields：`taxRate`；币种由平台固定为 `CNY`。
- EvidenceSlot：
  - `contractTotalAmountCandidate`：required，acceptedRoles=`CONTRACT_TOTAL_AMOUNT`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`deterministicOnly`。
  - `taxExcludedAmountCandidate`：required，acceptedRoles=`TAX_EXCLUDED_AMOUNT`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`deterministicOnly`。
  - `taxAmountCandidate`：required，acceptedRoles=`TAX_AMOUNT`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`deterministicOnly`。
  - `taxRateCandidate`：optional，acceptedRoles=`TAX_RATE`，来源为经 CandidateResolver 可靠归属的 `CandidateRole=taxRate`；不得把其他比例候选按同值直接复用为税率。
- 执行策略：`FORMULA_RULE`；后端执行公式和金额精度容差裁判。CNY 金额使用十进制定点数并保留两位小数，不得使用二进制浮点数。
- 税率和付款比例使用百分比数值，例如 `13` 表示 `13%`。管理台、SAP/API、数据库和快照禁止使用 `0.13` 表示 `13%`；公式计算时后端统一将百分比数值除以 `100`。
- 税率最多保留 4 位小数，付款比例最多保留 2 位小数；末尾零可省略。超出精度时在任务创建或管理台提交阶段拒绝，不得静默四舍五入或截断。
- 月度付款的预付款、进度款、竣工款和结算款比例为累计比例，业务口径为 `prepaymentRatio ≤ progressPaymentRatio ≤ completionPaymentRatio ≤ settlementPaymentRatio`。
- 质保款比例单独表达，业务口径为 `settlementPaymentRatio + warrantyRetentionRatio = 100`。
- MVP 不重复校验上述跨字段关系，也不据此生成业务 finding。外围系统负责输入质量校验，管理台测试数据由测试人员控制；单字段范围、精度和条件必填仍由平台校验。
- 强校验：`contractTotalAmount = taxExcludedAmount + taxAmount`。各金额按两位小数参与裁判，绝对误差不超过 `0.01` 元视为一致；超出容差时可生成确定性 `ERROR`。
- 弱校验：`taxAmount = taxExcludedAmount × taxRate`。MVP 只用于生成 `WARNING`，不得单独生成 `ERROR`，以兼容分项计税、多税率和逐行舍入差异。
- 税率新增固定 `CandidateRole=taxRate`，作为本审核点 optional EvidenceSlot 候选角色。税率缺失或不可靠时不执行弱校验，不得因此生成无可靠证据的业务 finding。
- Gemma/A30：不需要；公式关系和数值裁判不得交给模型。
- 降级规则：结构化税额字段未提供时该点 `SKIPPED`，不视为系统失败；合同证据 slot 缺失输出 `SYS-INDEX-INCOMPLETE`；金额角色冲突输出 `SYS-ROLE-CONFLICT`；解析或公式执行异常输出 `SYS-RULE-ERROR`；税率缺失或不可靠时不执行弱校验，不得因缺税额或税率证据直接生成业务 finding。

### PREPAYMENT_RATIO_CONSISTENCY

- `ReviewPointFamily`: `PAYMENT_TERMS`
- 目标：当结构化预付款比例存在时，校验合同付款条款中的预付款比例候选是否一致。
- required structured fields：`prepaymentRatio`。
- optional structured fields：`paymentScheduleText`、`contractTotalAmount`。
- EvidenceSlot：
  - `prepaymentRatioCandidate`：required，acceptedRoles=`PREPAYMENT_RATIO`，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`gemmaIfAmbiguous`。
  - `paymentClauseAnchor`：required，来源为付款、预付款、进度款、结算款等相关条款 block，用于原文定位和归属解释。
- 执行策略：`PATTERN_EXTRACT_THEN_COMPARE` + 后端比例归一化比对。
- Gemma/A30：仅在预付款候选与进度款/结算款候选语义归属不清、且存在局部付款条款上下文时做 `AMBIGUITY_RESOLUTION`。
- 降级规则：`prepaymentRatio` 未提供时该点 `SKIPPED`；付款条款候选缺失输出 `SYS-INDEX-INCOMPLETE`；预付款/进度款/结算款角色冲突输出 `SYS-ROLE-CONFLICT` 或 `SYS-EVIDENCE-AMBIGUOUS`；Gemma 输出不完整或矛盾输出 `SYS-MODEL-OUTPUT-*` 或 `SYS-MODEL-CONFLICT`；不得把付款条款缺失直接作为业务 finding，除非后续任务定义并验证 `PROVEN_REQUIRED_CLAUSE_ABSENT`。

### 其余月度付款比例一致性审核点

以下四个审核点仅在 `paymentMethod=MONTHLY` 时适用，并分别输出点级状态、证据和原文定位：

| 审核点 | required structured field | required EvidenceSlot | acceptedRole |
| --- | --- | --- | --- |
| `PROGRESS_PAYMENT_RATIO_CONSISTENCY` | `progressPaymentRatio` | `progressPaymentRatioCandidate` | `PROGRESS_PAYMENT_RATIO` |
| `COMPLETION_PAYMENT_RATIO_CONSISTENCY` | `completionPaymentRatio` | `completionPaymentRatioCandidate` | `COMPLETION_PAYMENT_RATIO` |
| `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY` | `settlementPaymentRatio` | `settlementPaymentRatioCandidate` | `SETTLEMENT_PAYMENT_RATIO` |
| `WARRANTY_RETENTION_RATIO_CONSISTENCY` | `warrantyRetentionRatio` | `warrantyRetentionRatioCandidate` | `WARRANTY_RETENTION_RATIO` |

- 每个比例候选 slot：required，minCandidates=1，maxCandidates=5，confidenceRequired=`HIGH`，resolverPolicy=`gemmaIfAmbiguous`。
- 共享 `paymentClauseAnchor`：required，来源为付款、进度、竣工、结算、质保等相关条款 block。
- 执行策略：`PATTERN_EXTRACT_THEN_COMPARE` + 后端比例归一化与确定性比对。
- Gemma/A30：仅在多个比例候选语义归属不清且局部上下文充分时做 `AMBIGUITY_RESOLUTION`，不得直接输出最终状态。
- 降级规则：节点付款时仍执行 `PREPAYMENT_RATIO_CONSISTENCY`，其余四点因不适用而 `SKIPPED`，不得输出 `SYS-* / NOT_CONCLUDED` 或业务 finding；月度付款下对应结构化字段缺失输出 `SYS-MISSING-INPUT`；required slot 缺失输出 `SYS-INDEX-INCOMPLETE`；比例角色冲突或证据歧义输出 `SYS-ROLE-CONFLICT` / `SYS-EVIDENCE-AMBIGUOUS`；模型异常输出对应 `SYS-MODEL-*`。证据不足不得生成业务 finding。

## 延后范围

延后到 Pilot：

- `PARTY_FIELDS`：签署页主体与正文主体冲突、主体统一社会信用代码一致性、多乙方/联合体/代理人主体关系。
- `AMOUNT_TAX`：中文大写金额一致性、税率合法性、含税/不含税上下文冲突、币种与单位复杂归一。
- `PAYMENT_TERMS`：月度付款比例之间的跨字段关系、付款节点顺序、付款条件完整性、发票前置条件，以及节点付款其他阶段的结构化审核。

延后到 Production Readiness：

- 审核点自动优化、候选规则自动发布、完整质量治理样本生命周期、跨合同类型专属付款规则、生产保护与审计门禁。

## 选择理由

- 覆盖三个候选族，但每族只保留能跑通证据槽、候选索引、后端裁判和结果定位的最小点。
- `PARTY_FIELDS` 和 `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` 是最稳定的结构化字段比对闭环，适合作为 core review point。
- `TAX_AMOUNT_FORMULA_CONSISTENCY` 用于验证后端公式裁判，并作为 core review point 纳入 MVP。若样本验证发现误报高，应通过规则版本、容差策略和回归验证调整，不在 MVP 范围中移除该点。
- 五个月度付款比例一致性点共同验证 `PAYMENT_TERMS` 族和 Gemma 局部歧义辅助，但不扩大到跨字段比例裁判或完整付款条件语义审核。
- 所有业务 finding 都必须由后端确定性裁判合成；证据不足、模型失败或角色冲突只进入 `SYS-*` / `NOT_CONCLUDED`。
- 审核点“明确不一致”时输出 `ERROR` 或 `WARNING` 的严重级别由当次 execution 绑定的有效 `severityPolicy` 决定，不在裁判代码中 hardcode。
- 管理后台可维护审核点配置和默认严重级别；MVP 保存后即对下一次新建审核任务生效，不设置单独发布流程。配置变更只影响后续 execution。
- `PASS`、`NOT_CONCLUDED`、`SKIPPED` 和 `SYS-*` 诊断不允许通过严重级别配置改写。
- 停用审核点属于配置层面不进入执行计划，不输出 `SKIPPED`、`NOT_CONCLUDED` 或业务 finding。
- MVP 验收基线要求首批 9 个审核点默认全部启用；手动停用 core 审核点的测试结果不能证明 core 覆盖率达标。
- 管理台可编辑的审核点编码仅作为展示编码；系统内部 `reviewPointCode` 是唯一、不可编辑主 key。ReviewExecutionPlan、EvidenceSlot、后端裁判、API 和快照必须使用内部 `reviewPointCode`。

## 影响

- 后续 `ReviewPointDefinition` 草案任务可以围绕 9 个点展开。
- 后续最小 API 契约需要至少支持 `partyAName`、`partyBName`、`contractTotalAmount`，并固定 `currency=CNY`。
- 后续样本验证至少需要覆盖主体、金额、税额、税率和五个月度付款比例的正例、冲突例、缺证据例。
- `TAX_RATE` 已确认为固定 CandidateRole，用于 `TAX_AMOUNT_FORMULA_CONSISTENCY` 的 optional EvidenceSlot。
- MVP 验收样本由业务方提供脱敏或合成中文 `.docx` 工程采购合同。平台不负责自动识别样本是否已脱敏，也不在 MVP 中提供脱敏检测或自动脱敏能力；样本进入仓库或评测环境的前提，是业务方/data owner 已确认其可用于项目验证。

## 不做什么

- 不新增审核点族。
- 不把未证明缺失的条款当作业务风险。
- 不让 Gemma 直接输出最终 `PASS / WARNING / ERROR`。
- 不做完整付款条款语义审核。
- 不做生产规则、正则、prompt 或模型版本发布。

## 回滚与迁移

- 当前尚未编码，无数据迁移影响。
- 若后续删减或调整首批审核点，必须更新本 ADR 或创建替代 ADR，并同步任务包、`ROADMAP.md`、`docs/ai-review.md`。

## 验证方式

- 用 2-3 个脱敏或合成中文 Word 合同场景人工检查：
  - 主体、金额、税额、预付款比例均一致。
  - 主体或金额存在明确冲突。
  - 付款条款候选歧义、税额 slot 缺失或解析低置信导致 `SYS-*` / `NOT_CONCLUDED`。
- 检查每个点是否能解释“审核点 -> 证据 -> 原文定位”。
- 检查所有 `SYS-*` 是否不进入业务 finding。

## 后续动作

- 创建首批 `ReviewPointDefinition` 草案任务。
- 创建首批字段词库、ValueGrammar 和正则边界任务。
- 创建首批合成/脱敏样本场景任务。
- 在 `ADR-002` 中继续固化普通结果页、管理台和外部 API 的状态/诊断可见性。

## 关联

- 相关任务：`TASK-003-first-review-points-selection`
- 相关文档：`CURRENT_CONTEXT.md`、`ROADMAP.md`、`docs/ai-review.md`、`docs/backend.md`
- 相关版本：V1 MVP。

## 待确认

- 首批合同类型是否只选一个类型试点。
- 首批结构化字段由外部系统、简易管理台人工录入还是两者都支持。
- 评测环境、样本文件命名规范、保存目录和预期结果文件格式。
