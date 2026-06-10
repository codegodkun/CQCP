# 首批 ReviewPointDefinition 草案

状态：Draft for contract review

日期：2026-06-09

来源：`ADR-005-first-review-points-selection.md`、`PRD.md`、`CURRENT_CONTEXT.md`、`docs/ARCHITECTURE.md`、`docs/backend.md`、`docs/ai-review.md`

## 1. 目的

本文档把已 Accepted 的首批 9 个 MVP core review point 细化为 `ReviewPointDefinition` 草案，用于后续最小 OpenAPI、数据库 seed、规则/词库/ValueGrammar、样本验证和实现任务拆分。

本文档不是代码实现，不替代 ADR。若后续需要改变核心审核链路、模型职责边界、`SYS-*` / Finding 边界、EvidenceSlot 机制、ReviewPointFamily 或 CandidateResolver，应先记录 ADR。

## 2. 全局约束

- 首批审核点固定为 9 个，均为 core review point。
- MVP 首批合同类型仅为“工程采购合同”，内部建议编码为 `ENGINEERING_PROCUREMENT`；正式合同类型编码仍待 OpenAPI / 数据库契约确认。
- 所有点级最终状态由后端合成；Gemma/A30 不直接输出最终 `PASS / ERROR / WARNING`。
- `ERROR` 和 `WARNING` 属于业务 finding，必须有可靠 EvidenceSlot、SourceAnchor 和可解释依据。
- `PASS` 必须有可靠 EvidenceSlot、SourceAnchor 和可解释依据。
- 证据不足、解析低置信、候选冲突、预算不足或模型异常输出 `NOT_CONCLUDED`，并保留内部 `SYS-*` 诊断；不得生成业务 finding。
- `SKIPPED` 仅表示不适用，不要求 EvidenceSlot 或 SourceAnchor。
- SourceAnchor MVP 最低要求为 block / section / table row / cell 级；缺少字符级范围本身不得导致 `NOT_CONCLUDED`。
- 金额使用 CNY 十进制定点数，两位小数；税率使用百分比数值，最多 4 位小数；付款比例使用百分比数值，最多 2 位小数。
- `defaultSeverity` 不在裁判代码中 hardcode。草案只给出建议初始值，最终值待人工确认，并应由 execution 绑定的有效配置版本决定。

## 3. 草案字段结构

每个 `ReviewPointDefinition` 建议至少包含：

```text
reviewPointCode
displayCode
name
family
category
core
enabledByDefault
applicableContractTypes
requiredStructuredFields
applicabilityPolicy
executionStrategy
evidencePolicy
candidateExtractionPolicy
deterministicRule
modelAssistPolicy
severityPolicy
sourceAnchorPolicy
resultPolicy
businessMessages
versioningPolicy
```

MVP 管理台只允许有限编辑展示编码、名称、分类、默认严重级别、启用状态、排序、依赖字段和合同类型。`executionStrategy`、EvidenceSlot、后端裁判和模型辅助策略不得在 MVP 管理台任意改写。

## 4. 首批审核点总览

| reviewPointCode | ReviewPointFamily | 名称 | executionStrategy | 模型辅助 |
| --- | --- | --- | --- | --- |
| `PARTY_A_NAME_CONSISTENCY` | `PARTY_FIELDS` | 甲方名称一致性 | `PATTERN_EXTRACT_THEN_COMPARE` | 仅歧义归属 |
| `PARTY_B_NAME_CONSISTENCY` | `PARTY_FIELDS` | 乙方名称一致性 | `PATTERN_EXTRACT_THEN_COMPARE` | 仅歧义归属 |
| `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` | `AMOUNT_TAX` | 合同总金额一致性 | `PATTERN_EXTRACT_THEN_COMPARE` | 默认不使用 |
| `TAX_AMOUNT_FORMULA_CONSISTENCY` | `AMOUNT_TAX` | 税额公式一致性 | `FORMULA_RULE` | 不使用 |
| `PREPAYMENT_RATIO_CONSISTENCY` | `PAYMENT_TERMS` | 预付款比例一致性 | `PATTERN_EXTRACT_THEN_COMPARE` | 仅歧义归属 |
| `PROGRESS_PAYMENT_RATIO_CONSISTENCY` | `PAYMENT_TERMS` | 进度款比例一致性 | `PATTERN_EXTRACT_THEN_COMPARE` | 仅歧义归属 |
| `COMPLETION_PAYMENT_RATIO_CONSISTENCY` | `PAYMENT_TERMS` | 竣工款比例一致性 | `PATTERN_EXTRACT_THEN_COMPARE` | 仅歧义归属 |
| `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY` | `PAYMENT_TERMS` | 结算款比例一致性 | `PATTERN_EXTRACT_THEN_COMPARE` | 仅歧义归属 |
| `WARRANTY_RETENTION_RATIO_CONSISTENCY` | `PAYMENT_TERMS` | 质保款比例一致性 | `PATTERN_EXTRACT_THEN_COMPARE` | 仅歧义归属 |

## 5. 通用 resultPolicy

### 5.1 点级状态

```text
PASS:
- required structured fields 满足
- required EvidenceSlot 覆盖
- 候选角色可可靠归属
- 后端确定性裁判一致
- 返回 SourceAnchor 和可解释依据

ERROR:
- required structured fields 满足
- required EvidenceSlot 覆盖
- 候选角色可可靠归属
- 后端确定性裁判发现明确业务不一致
- 当前 execution 绑定的 severityPolicy 允许该点以 ERROR 输出

WARNING:
- required structured fields 满足
- required EvidenceSlot 覆盖
- 候选角色可可靠归属
- 后端确定性裁判发现明确业务不一致或弱校验异常
- 当前 execution 绑定的 severityPolicy 或规则定义允许该点以 WARNING 输出

NOT_CONCLUDED:
- required EvidenceSlot 缺失
- 候选归属不清
- 解析低置信影响该点
- 预算截断关键证据
- Gemma 辅助不可用、超时或仍无法消歧
- 后端规则异常导致无法可靠裁判

SKIPPED:
- 审核点按 applicabilityPolicy 不适用
- 不要求 EvidenceSlot
- 不计入业务 finding
```

### 5.2 通用 SYS 映射

| 内部诊断 | 普通结果页业务化原因方向 |
| --- | --- |
| `SYS-MISSING-INPUT` | 缺少执行该审核点所需的结构化输入 |
| `SYS-INDEX-INCOMPLETE` | 未在合同中找到足够可靠的对应证据 |
| `SYS-EVIDENCE-AMBIGUOUS` | 证据存在歧义，无法可靠归属 |
| `SYS-ROLE-CONFLICT` | 多个候选均可能对应该审核点，无法可靠裁判 |
| `SYS-PARSE-LOW-CONFIDENCE` | 文档解析置信度不足，影响该审核点判断 |
| `SYS-EVIDENCE-BUDGET-EXCEEDED` | 证据构建预算不足，未覆盖该审核点所需证据 |
| `SYS-MODEL-UNAVAILABLE` | 模型辅助不可用，且该点需要模型消歧 |
| `SYS-MODEL-TIMEOUT` | 模型辅助超时，且该点需要模型消歧 |
| `SYS-MODEL-OUTPUT-INCOMPLETE` | 模型输出不完整，无法作为候选归属依据 |
| `SYS-MODEL-CONFLICT` | 模型辅助结果与已有候选或补充结果冲突 |
| `SYS-RULE-ERROR` | 后端裁判规则执行异常 |

普通结果页不展示内部 `SYS-*` 技术码，只展示业务化原因。管理台任务详情、评测报告和 AI 调优包可展示摘要级内部诊断。

## 6. PARTY_A_NAME_CONSISTENCY

```text
reviewPointCode: PARTY_A_NAME_CONSISTENCY
displayCode: PARTY_A_NAME_CONSISTENCY
name: 甲方名称一致性
family: PARTY_FIELDS
category: 结构化信息比对
core: true
enabledByDefault: true
applicableContractTypes: [ENGINEERING_PROCUREMENT]
requiredStructuredFields: [partyAName]
executionStrategy: PATTERN_EXTRACT_THEN_COMPARE
```

### 6.1 evidencePolicy

| slotKey | required | acceptedRoles | min/max | 置信要求 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `partyANameCandidate` | 是 | `PARTY_A` | 1 / 5 | `HIGH` | 合同正文、首页、表格或签署页中的甲方候选 |
| `partyAContextAnchor` | 是 | `PARTY_A_CONTEXT` | 1 / 5 | `MEDIUM` 或以上 | “甲方、发包人、采购人、委托方”等 label 附近上下文 |

`sourceAnchorPolicy.minLevel = BLOCK_LEVEL`。若有精确文本范围，应返回 exact range；没有 exact range 但 block / row / cell 可靠时仍可裁判。

### 6.2 candidateExtractionPolicy

- 使用 `PartyIndex`、标题/签署页区域 hint、主体角色词库和组织名 ValueGrammar。
- 角色 hint 包括：`甲方`、`发包人`、`采购人`、`委托方`、`买方`。
- 不按同值直接合并候选；必须 role、value、compatible context 一致才可合并为 `ResolvedCandidateGroup`。
- 签署页、合同首页、正文定义段和表格中的主体候选都可进入候选池，但候选必须保留 `contextType`、`sourceOrigin`、`sourceExtractionMode` 和置信度。

### 6.3 deterministicRule

- 对 `partyAName` 和 `partyANameCandidate.normalizedValue` 做后端名称归一化比较。
- 归一化可处理空白、全半角、括号样式、常见公司后缀噪声；不得删除实质性主体名称差异。
- 一致输出 `PASS`。
- 明确不一致时按当前 severityPolicy 输出 `ERROR` 或 `WARNING`。
- 候选不足、候选冲突或归属不清输出 `NOT_CONCLUDED`。

### 6.4 modelAssistPolicy

```text
mode: AMBIGUITY_RESOLUTION
modelAssistPriority: 7
inputScope: only party-related candidate blocks and nearby context
outputUse: CandidateResolver 辅助信号
```

只有候选为 `MEDIUM` / `CONFLICTED` 且存在可比较 anchor/context 时才请求 Gemma/A30。模型不得直接输出最终状态。

### 6.5 businessMessages

- `PASS`：结构化甲方名称与合同中识别到的甲方名称一致。
- `ERROR/WARNING`：结构化甲方名称与合同中识别到的甲方名称不一致，请核对主体信息。
- `NOT_CONCLUDED`：未能从合同中可靠确认甲方名称，需人工核对。

## 7. PARTY_B_NAME_CONSISTENCY

```text
reviewPointCode: PARTY_B_NAME_CONSISTENCY
displayCode: PARTY_B_NAME_CONSISTENCY
name: 乙方名称一致性
family: PARTY_FIELDS
category: 结构化信息比对
core: true
enabledByDefault: true
applicableContractTypes: [ENGINEERING_PROCUREMENT]
requiredStructuredFields: [partyBName]
executionStrategy: PATTERN_EXTRACT_THEN_COMPARE
```

### 7.1 evidencePolicy

| slotKey | required | acceptedRoles | min/max | 置信要求 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `partyBNameCandidate` | 是 | `PARTY_B` | 1 / 5 | `HIGH` | 合同正文、首页、表格或签署页中的乙方候选 |
| `partyBContextAnchor` | 是 | `PARTY_B_CONTEXT` | 1 / 5 | `MEDIUM` 或以上 | “乙方、承包人、供应商、服务方、卖方”等 label 附近上下文 |

`sourceAnchorPolicy.minLevel = BLOCK_LEVEL`。

### 7.2 candidateExtractionPolicy

- 使用 `PartyIndex`、签署页区域 hint、主体角色词库和组织名 ValueGrammar。
- 角色 hint 包括：`乙方`、`承包人`、`供应商`、`服务方`、`卖方`。
- 同值但 label、section、role hint 或来源置信冲突时不得直接合并。

### 7.3 deterministicRule

- 对 `partyBName` 和 `partyBNameCandidate.normalizedValue` 做后端名称归一化比较。
- 一致输出 `PASS`。
- 明确不一致时按当前 severityPolicy 输出 `ERROR` 或 `WARNING`。
- 候选不足、候选冲突或归属不清输出 `NOT_CONCLUDED`。

### 7.4 modelAssistPolicy

```text
mode: AMBIGUITY_RESOLUTION
modelAssistPriority: 7
inputScope: only party-related candidate blocks and nearby context
outputUse: CandidateResolver 辅助信号
```

### 7.5 businessMessages

- `PASS`：结构化乙方名称与合同中识别到的乙方名称一致。
- `ERROR/WARNING`：结构化乙方名称与合同中识别到的乙方名称不一致，请核对主体信息。
- `NOT_CONCLUDED`：未能从合同中可靠确认乙方名称，需人工核对。

## 8. CONTRACT_TOTAL_AMOUNT_CONSISTENCY

```text
reviewPointCode: CONTRACT_TOTAL_AMOUNT_CONSISTENCY
displayCode: CONTRACT_TOTAL_AMOUNT_CONSISTENCY
name: 合同总金额一致性
family: AMOUNT_TAX
category: 结构化信息比对
core: true
enabledByDefault: true
applicableContractTypes: [ENGINEERING_PROCUREMENT]
requiredStructuredFields: [contractTotalAmount]
executionStrategy: PATTERN_EXTRACT_THEN_COMPARE
```

### 8.1 evidencePolicy

| slotKey | required | acceptedRoles | min/max | 置信要求 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `contractTotalAmountCandidate` | 是 | `CONTRACT_TOTAL_AMOUNT` | 1 / 5 | `HIGH` | 合同中的含税总金额候选 |
| `amountContextAnchor` | 是 | `AMOUNT_CONTEXT` | 1 / 5 | `MEDIUM` 或以上 | “合同价款、合同金额、总价、含税总价”等上下文 |
| `amountInWordsCandidate` | 否 | `AMOUNT_IN_WORDS` | 0 / 3 | `MEDIUM` 或以上 | 中文大写金额候选；MVP 仅作为解释或诊断，不参与独立一致性裁判 |

`sourceAnchorPolicy.minLevel = BLOCK_LEVEL`。金额候选若有 exact range，应返回字符级高亮；否则必须返回候选原文值和 block / row / cell anchor。

### 8.2 candidateExtractionPolicy

- 使用 `AmountIndex`、金额 ValueGrammar、含税/总价 role label 和工程采购合同金额词库。
- 候选 role 必须可靠归属为 `CONTRACT_TOTAL_AMOUNT`。
- 不得把税额、不含税金额、分项金额、付款金额或预算金额按同值直接复用为合同总金额。

### 8.3 deterministicRule

- `contractTotalAmount` 固定语义为合同总金额（含税）。
- 结构化字段和合同候选均归一化为 CNY 两位小数。
- 数值一致输出 `PASS`。
- 明确不一致时按当前 severityPolicy 输出 `ERROR` 或 `WARNING`。
- 金额解析失败、多个金额候选无法归属或证据不足输出 `NOT_CONCLUDED`。

### 8.4 modelAssistPolicy

```text
mode: NONE
modelAssistPriority: 0
```

MVP 默认不调用 Gemma。后续如需对金额角色歧义使用模型辅助，必须通过独立任务更新 EvidenceSlot / CandidateResolver policy。

### 8.5 businessMessages

- `PASS`：结构化合同总金额（含税）与合同中识别到的合同总金额一致。
- `ERROR/WARNING`：结构化合同总金额（含税）与合同中识别到的合同总金额不一致，请核对金额信息。
- `NOT_CONCLUDED`：未能从合同中可靠确认合同总金额，需人工核对。

## 9. TAX_AMOUNT_FORMULA_CONSISTENCY

```text
reviewPointCode: TAX_AMOUNT_FORMULA_CONSISTENCY
displayCode: TAX_AMOUNT_FORMULA_CONSISTENCY
name: 税额公式一致性
family: AMOUNT_TAX
category: 合同内部一致性
core: true
enabledByDefault: true
applicableContractTypes: [ENGINEERING_PROCUREMENT]
requiredStructuredFields: [contractTotalAmount, taxExcludedAmount, taxAmount]
optionalStructuredFields: [taxRate]
executionStrategy: FORMULA_RULE
```

### 9.1 evidencePolicy

| slotKey | required | acceptedRoles | min/max | 置信要求 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `contractTotalAmountCandidate` | 是 | `CONTRACT_TOTAL_AMOUNT` | 1 / 5 | `HIGH` | 合同含税总金额候选 |
| `taxExcludedAmountCandidate` | 是 | `TAX_EXCLUDED_AMOUNT` | 1 / 5 | `HIGH` | 合同不含税金额候选 |
| `taxAmountCandidate` | 是 | `TAX_AMOUNT` | 1 / 5 | `HIGH` | 合同税额候选 |
| `taxRateCandidate` | 否 | `TAX_RATE` | 0 / 5 | `HIGH` | 税率候选；固定 `CandidateRole=taxRate` |

`sourceAnchorPolicy.minLevel = BLOCK_LEVEL`。强校验所需三个金额 slot 缺一不可；税率 slot 只影响弱校验。

### 9.2 candidateExtractionPolicy

- 使用 `AmountIndex` 提取含税总金额、不含税金额、税额。
- 使用 `RatioIndex` 提取税率，但必须经 CandidateResolver 可靠归属为 `TAX_RATE`。
- 不得把付款比例、质保比例、违约金比例等同值比例复用为税率。

### 9.3 deterministicRule

强校验：

```text
contractTotalAmount = taxExcludedAmount + taxAmount
absoluteTolerance = 0.01 CNY
```

- 三个结构化字段与三个 required EvidenceSlot 均可靠时执行。
- 在容差内输出 `PASS`。
- 超出容差时可输出 `ERROR`，但最终业务级别仍读取当次 severityPolicy。

弱校验：

```text
taxAmount = taxExcludedAmount * taxRate / 100
```

- 仅在 `taxRate` 结构化字段或 `taxRateCandidate` 可靠时执行。
- MVP 中弱校验偏差只生成 `WARNING`，不得单独生成 `ERROR`。
- 税率缺失或不可靠时不执行弱校验，不因此生成业务 finding。

### 9.4 applicabilityPolicy

- `contractTotalAmount`、`taxExcludedAmount`、`taxAmount` 在 MVP 创建任务契约中属于通用必填字段。若执行阶段缺失，输出 `SYS-MISSING-INPUT`。
- 不因 `taxRate` 缺失跳过整个审核点；只跳过弱校验。

### 9.5 modelAssistPolicy

```text
mode: NONE
modelAssistPriority: 0
```

公式关系和数值裁判不得交给模型。

### 9.6 businessMessages

- `PASS`：合同总金额（含税）、不含税金额和税额之间的金额关系一致。
- `ERROR`：合同总金额（含税）、不含税金额和税额之间的金额关系不一致，请核对税额和金额。
- `WARNING`：按税率计算的税额与合同税额存在差异，请结合分项计税或舍入规则人工核对。
- `NOT_CONCLUDED`：未能获得足够可靠的金额或税率证据，无法完成税额公式核对。

## 10. 付款比例一致性通用定义

以下 5 个审核点共享 `PAYMENT_TERMS` 族策略：

- `PREPAYMENT_RATIO_CONSISTENCY`
- `PROGRESS_PAYMENT_RATIO_CONSISTENCY`
- `COMPLETION_PAYMENT_RATIO_CONSISTENCY`
- `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY`
- `WARRANTY_RETENTION_RATIO_CONSISTENCY`

### 10.1 通用 evidencePolicy

| slotKey | required | min/max | 置信要求 | 说明 |
| --- | --- | --- | --- | --- |
| 当前点比例候选 slot | 是 | 1 / 5 | `HIGH` | 见各点定义 |
| `paymentClauseAnchor` | 是 | 1 / 8 | `MEDIUM` 或以上 | 付款、预付款、进度、竣工、结算、质保等相关条款 block |

`sourceAnchorPolicy.minLevel = BLOCK_LEVEL`。若同一付款条款 block 内有多个比例候选，必须可靠归属到当前审核点所需比例。

### 10.2 通用 candidateExtractionPolicy

- 使用 `RatioIndex`、`PaymentTermIndex`、付款条款标题和付款比例 ValueGrammar。
- 使用 `paymentMethod` 判断适用性，但不裁判月度付款比例之间的跨字段关系。
- 比例统一使用百分比数值，`10` 表示 `10%`。
- 不得把税率、违约金比例、质保期年限、付款期限天数误归为付款比例。

### 10.3 通用 deterministicRule

- 结构化比例与合同候选比例按百分比数值比较。
- 比例字段最多 2 位小数；超出精度应在任务创建或管理台提交阶段拒绝。
- 一致输出 `PASS`。
- 明确不一致时按当前 severityPolicy 输出 `ERROR` 或 `WARNING`。
- 候选缺失、候选冲突或归属不清输出 `NOT_CONCLUDED`。

### 10.4 通用 modelAssistPolicy

```text
mode: AMBIGUITY_RESOLUTION
modelAssistPriority: 6
inputScope: only payment clause blocks, nearby headings, and candidate ratios
outputUse: CandidateResolver 辅助信号
```

Gemma/A30 只在多个比例候选语义归属不清且局部上下文充分时辅助消歧。不得提交整份合同，不得直接输出最终状态。

## 11. PREPAYMENT_RATIO_CONSISTENCY

```text
reviewPointCode: PREPAYMENT_RATIO_CONSISTENCY
displayCode: PREPAYMENT_RATIO_CONSISTENCY
name: 预付款比例一致性
family: PAYMENT_TERMS
category: 结构化信息比对
core: true
enabledByDefault: true
applicableContractTypes: [ENGINEERING_PROCUREMENT]
requiredStructuredFields: [paymentMethod, prepaymentRatio]
executionStrategy: PATTERN_EXTRACT_THEN_COMPARE
```

### 11.1 applicabilityPolicy

- `paymentMethod=MONTHLY`：适用。
- `paymentMethod=MILESTONE`：仍适用。
- `prepaymentRatio` 缺失：创建任务阶段应拒绝；若执行阶段缺失，输出 `SYS-MISSING-INPUT`。

### 11.2 evidencePolicy

| slotKey | required | acceptedRoles | min/max | 置信要求 |
| --- | --- | --- | --- | --- |
| `prepaymentRatioCandidate` | 是 | `PREPAYMENT_RATIO` | 1 / 5 | `HIGH` |
| `paymentClauseAnchor` | 是 | `PAYMENT_CLAUSE_CONTEXT` | 1 / 8 | `MEDIUM` 或以上 |

### 11.3 businessMessages

- `PASS`：结构化预付款比例与合同付款条款中的预付款比例一致。
- `ERROR/WARNING`：结构化预付款比例与合同付款条款中的预付款比例不一致，请核对付款条款。
- `NOT_CONCLUDED`：未能从合同付款条款中可靠确认预付款比例，需人工核对。

## 12. PROGRESS_PAYMENT_RATIO_CONSISTENCY

```text
reviewPointCode: PROGRESS_PAYMENT_RATIO_CONSISTENCY
displayCode: PROGRESS_PAYMENT_RATIO_CONSISTENCY
name: 进度款比例一致性
family: PAYMENT_TERMS
category: 结构化信息比对
core: true
enabledByDefault: true
applicableContractTypes: [ENGINEERING_PROCUREMENT]
requiredStructuredFields: [paymentMethod, progressPaymentRatio]
executionStrategy: PATTERN_EXTRACT_THEN_COMPARE
```

### 12.1 applicabilityPolicy

- `paymentMethod=MONTHLY`：适用。
- `paymentMethod=MILESTONE`：`SKIPPED`，适用性原因为“付款方式为按节点付款，进度款比例审核点不适用”。

### 12.2 evidencePolicy

| slotKey | required | acceptedRoles | min/max | 置信要求 |
| --- | --- | --- | --- | --- |
| `progressPaymentRatioCandidate` | 是 | `PROGRESS_PAYMENT_RATIO` | 1 / 5 | `HIGH` |
| `paymentClauseAnchor` | 是 | `PAYMENT_CLAUSE_CONTEXT` | 1 / 8 | `MEDIUM` 或以上 |

### 12.3 businessMessages

- `PASS`：结构化进度款比例与合同付款条款中的进度款比例一致。
- `ERROR/WARNING`：结构化进度款比例与合同付款条款中的进度款比例不一致，请核对付款条款。
- `NOT_CONCLUDED`：未能从合同付款条款中可靠确认进度款比例，需人工核对。
- `SKIPPED`：付款方式为按节点付款，进度款比例审核点不适用。

## 13. COMPLETION_PAYMENT_RATIO_CONSISTENCY

```text
reviewPointCode: COMPLETION_PAYMENT_RATIO_CONSISTENCY
displayCode: COMPLETION_PAYMENT_RATIO_CONSISTENCY
name: 竣工款比例一致性
family: PAYMENT_TERMS
category: 结构化信息比对
core: true
enabledByDefault: true
applicableContractTypes: [ENGINEERING_PROCUREMENT]
requiredStructuredFields: [paymentMethod, completionPaymentRatio]
executionStrategy: PATTERN_EXTRACT_THEN_COMPARE
```

### 13.1 applicabilityPolicy

- `paymentMethod=MONTHLY`：适用。
- `paymentMethod=MILESTONE`：`SKIPPED`，适用性原因为“付款方式为按节点付款，竣工款比例审核点不适用”。

### 13.2 evidencePolicy

| slotKey | required | acceptedRoles | min/max | 置信要求 |
| --- | --- | --- | --- | --- |
| `completionPaymentRatioCandidate` | 是 | `COMPLETION_PAYMENT_RATIO` | 1 / 5 | `HIGH` |
| `paymentClauseAnchor` | 是 | `PAYMENT_CLAUSE_CONTEXT` | 1 / 8 | `MEDIUM` 或以上 |

### 13.3 businessMessages

- `PASS`：结构化竣工款比例与合同付款条款中的竣工款比例一致。
- `ERROR/WARNING`：结构化竣工款比例与合同付款条款中的竣工款比例不一致，请核对付款条款。
- `NOT_CONCLUDED`：未能从合同付款条款中可靠确认竣工款比例，需人工核对。
- `SKIPPED`：付款方式为按节点付款，竣工款比例审核点不适用。

## 14. SETTLEMENT_PAYMENT_RATIO_CONSISTENCY

```text
reviewPointCode: SETTLEMENT_PAYMENT_RATIO_CONSISTENCY
displayCode: SETTLEMENT_PAYMENT_RATIO_CONSISTENCY
name: 结算款比例一致性
family: PAYMENT_TERMS
category: 结构化信息比对
core: true
enabledByDefault: true
applicableContractTypes: [ENGINEERING_PROCUREMENT]
requiredStructuredFields: [paymentMethod, settlementPaymentRatio]
executionStrategy: PATTERN_EXTRACT_THEN_COMPARE
```

### 14.1 applicabilityPolicy

- `paymentMethod=MONTHLY`：适用。
- `paymentMethod=MILESTONE`：`SKIPPED`，适用性原因为“付款方式为按节点付款，结算款比例审核点不适用”。

### 14.2 evidencePolicy

| slotKey | required | acceptedRoles | min/max | 置信要求 |
| --- | --- | --- | --- | --- |
| `settlementPaymentRatioCandidate` | 是 | `SETTLEMENT_PAYMENT_RATIO` | 1 / 5 | `HIGH` |
| `paymentClauseAnchor` | 是 | `PAYMENT_CLAUSE_CONTEXT` | 1 / 8 | `MEDIUM` 或以上 |

### 14.3 businessMessages

- `PASS`：结构化结算款比例与合同付款条款中的结算款比例一致。
- `ERROR/WARNING`：结构化结算款比例与合同付款条款中的结算款比例不一致，请核对付款条款。
- `NOT_CONCLUDED`：未能从合同付款条款中可靠确认结算款比例，需人工核对。
- `SKIPPED`：付款方式为按节点付款，结算款比例审核点不适用。

## 15. WARRANTY_RETENTION_RATIO_CONSISTENCY

```text
reviewPointCode: WARRANTY_RETENTION_RATIO_CONSISTENCY
displayCode: WARRANTY_RETENTION_RATIO_CONSISTENCY
name: 质保款比例一致性
family: PAYMENT_TERMS
category: 结构化信息比对
core: true
enabledByDefault: true
applicableContractTypes: [ENGINEERING_PROCUREMENT]
requiredStructuredFields: [paymentMethod, warrantyRetentionRatio]
executionStrategy: PATTERN_EXTRACT_THEN_COMPARE
```

### 15.1 applicabilityPolicy

- `paymentMethod=MONTHLY`：适用。
- `paymentMethod=MILESTONE`：`SKIPPED`，适用性原因为“付款方式为按节点付款，质保款比例审核点不适用”。

### 15.2 evidencePolicy

| slotKey | required | acceptedRoles | min/max | 置信要求 |
| --- | --- | --- | --- | --- |
| `warrantyRetentionRatioCandidate` | 是 | `WARRANTY_RETENTION_RATIO` | 1 / 5 | `HIGH` |
| `paymentClauseAnchor` | 是 | `PAYMENT_CLAUSE_CONTEXT` | 1 / 8 | `MEDIUM` 或以上 |

### 15.3 businessMessages

- `PASS`：结构化质保款比例与合同付款条款中的质保款比例一致。
- `ERROR/WARNING`：结构化质保款比例与合同付款条款中的质保款比例不一致，请核对付款条款。
- `NOT_CONCLUDED`：未能从合同付款条款中可靠确认质保款比例，需人工核对。
- `SKIPPED`：付款方式为按节点付款，质保款比例审核点不适用。

## 16. 版本化与后续落地

后续落地时，以下内容必须版本化：

- `ReviewPointDefinition`
- EvidenceSlot policy
- Candidate extraction policy
- ValueGrammar
- 字段词库和正则
- deterministicRule
- modelAssistPolicy prompt/schema/model version
- severityPolicy
- sourceAnchorPolicy

建议后续任务按以下顺序推进：

1. 最小 OpenAPI 契约：引用本草案中的结构化字段、点级状态、结果摘要和错误边界。
2. 字段词库 / ValueGrammar / 正则边界任务：为 `PartyIndex`、`AmountIndex`、`RatioIndex`、`PaymentTermIndex` 定义首批候选规则。
3. 样本验证任务：为 9 个审核点建立正例、明确冲突、证据缺失、角色歧义和节点付款 `SKIPPED` 场景。
4. 数据库 / 配置落地任务：决定 `ReviewPointDefinition` 采用 seed、YAML/JSON 配置还是数据库版本表。

## 17. 待确认

- `defaultSeverity` 初始值：草案只确认可配置为 `ERROR` 或 `WARNING`，具体初始值待人工确认。
- `ENGINEERING_PROCUREMENT` 是否作为正式合同类型编码，或继续使用架构文档中的 `ENGINEERING`。
- `PARTY_A_CONTEXT`、`PARTY_B_CONTEXT`、`PAYMENT_CLAUSE_CONTEXT` 是否作为正式 CandidateRole，或仅作为 EvidenceSlot 上下文类型。
- `ReviewPointDefinition` 最终物理形态：数据库 seed、YAML/JSON 配置、代码内预置或组合方式。
- `businessMessages` 是否进入后端固定模板，或进入可版本化文案配置。
