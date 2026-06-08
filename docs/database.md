# docs/database.md

## 数据设计方向

架构文档明确要求结果、规则、模型、样本集和治理状态可追溯、可版本化、可审计。

一期 worker 从 MySQL 拉取 `QUEUED` 任务。具体数据库产品除 MySQL 外，未进一步展开。

数据库设计必须支持不可变快照、版本引用和审计追踪。历史结果读取快照，不因后续规则、模型、解析器或 prompt 变化而改变。

## 关键数据对象

### ReviewResultSnapshot

每次审核结果必须绑定版本：

- `taskId`
- `executionId`
- `supersededByExecutionId?`
- `supersededReason?`
- `status`
- `summary`
- `reviewCompleteness`
- `findings`
- `diagnostics`
- `sourceAnchors`
- `contractTypeAmbiguous?`
- `suggestedTypes?`
- `suggestionConfidence?`
- `contractTypeProfileVersion`
- `ruleSetVersion`
- `reviewBudgetProfileVersion`
- `parserVersion`
- `patternLibraryVersion`
- `fieldLexiconVersion`
- `modelVersion`
- `ruleSetDatasetStatus`
- `createdAt`

历史结果读取快照，不因后续规则或模型变化而改变。

快照查询索引至少支持：

- `taskId`
- `taskId + executionId`
- `taskId + latestNonSupersededExecution`

### Superseded Reason

枚举：

- `TYPE_CORRECTION`
- `BUDGET_UPGRADE`
- `MANUAL_RERUN`
- `RULESET_RERUN`
- `MODEL_UPGRADE`
- `PARSER_UPGRADE`
- `ADMIN_RECOVERY`

### Task / Execution

`taskId` 标识业务任务；`executionId` 标识一次具体审核执行。

合同类型修正、预算 profile 变更后重跑、人工或 API correction rerun 都必须创建新的 `executionId`，不得覆盖旧执行记录。

同一 `taskId` 下多个 execution 的默认选择规则：

1. 选择最新 `createdAt` 且未被 superseded 的 terminal execution。
2. 如无 terminal execution，选择最新 `PROCESSING` execution。
3. 管理台和外部 API 可按 `executionId` 查询历史快照。

同一 task 同时只能有一个 non-terminal execution。

超过 `maxExecutionsPerTask` 时拒绝创建新的 execution，返回 `409 EXECUTION_LIMIT_REACHED`。系统不得覆盖或删除最旧 execution。

管理员 recovery execution 可不占普通调用方的 3 次配额，但必须有审计记录。

### 版本包

一期只允许三类业务可配置包：

- `ContractTypeProfile`
- `RuleSetVersion`
- `ReviewBudgetProfile`

`RuleSetVersion` 是整体快照，内部可复用模块版本：

- `reviewPointsVersion`
- `evidenceSlotsVersion`
- `disambiguationVersion`
- `warningPolicyVersion`
- `guardRulesVersion`
- `qualityThresholdsVersion`
- `changedModules[]`
- `changeSummary`
- `previousRuleSetVersion?`

任何模块变化都生成新的整体 `RuleSetVersion` 快照。

审核点配置和严重级别策略也属于 `RuleSetVersion` 的版本化内容：

- 管理台编辑保存后即成为下一次新建审核任务的有效配置。
- MVP 只版本化预置审核点的展示编码、名称、审核分类、启用状态、排序、依赖字段、合同类型和严重级别编辑；不支持通过管理台新增任意审核点。
- 系统内部 `reviewPointCode` 是不可编辑主 key；展示编码是可编辑配置字段，必须非空且在当前配置中唯一。
- ReviewResultSnapshot 必须记录内部 `reviewPointCode`、当次展示编码、名称、审核分类、排序、合同类型和严重级别，保证历史结果可追溯。
- MVP 合同类型配置仅保存 `工程采购合同` 作为展示和后续扩展预留，不作为复杂适用范围路由依据。
- 排序字段只作为展示顺序快照保存，不作为执行顺序依据。
- 执行逻辑版本应能追溯到对应的 ReviewPointDefinition、EvidenceSlot、抽取规则、prompt 和后端裁判版本。
- 审核分类不得作为执行路由依据，只能作为展示、筛选、排序和治理字段。
- 依赖字段应引用已启用的合同字段定义，避免保存不可解释的自由文本或停用字段。
- 审核点配置版本不得保存缺少 required structured fields 的依赖字段集合。
- 保存时生成新的可追溯配置版本或变更记录，并记录审核点定义、启用状态、排序、依赖字段和 `severityPolicy`。
- Execution 与 ReviewResultSnapshot 必须记录当次绑定的 `RuleSetVersion` 和点级严重级别结果。
- ReviewResultSnapshot 摘要应分别保存 `plannedPointCount`、`passCount`、`errorCount`、`warningCount`、`notConcludedCount` 和 `skippedCount`。`plannedPointCount` 表示本次启用并进入 ReviewExecutionPlan 的审核点总数；配置停用点不进入该分母。`PASS` 表示明确通过；`ERROR` 与 `WARNING` 均为业务 finding；`NOT_CONCLUDED` 单独表示审核覆盖不足；`SKIPPED` 单独表示不适用；`PASS`、`NOT_CONCLUDED`、`SKIPPED` 和 `SYS-*` 不计入业务风险数量。
- `SKIPPED` 点级结果应保存适用性原因，但不要求保存 EvidenceSlot 或 SourceAnchor。
- `PASS` 点级结果必须保存证据定位引用和可解释依据摘要，保证历史结果可解释。
- `ERROR` 和 `WARNING` 点级结果必须保存证据定位引用和可解释依据摘要；无可靠证据不得保存为业务 finding。
- SourceAnchor 至少保存 block/section/table row/cell 级引用；exact text range 可选。
- 金额、税率和比例结果应保存合同原文候选值，便于缺少 exact range 时在结果卡片展示。
- 若因同一证据块内多候选无法可靠归属而 `NOT_CONCLUDED`，快照应保存该业务原因和候选歧义摘要。
- 历史快照不得根据当前审核点配置重新计算或重新解释严重级别。
- 历史快照不得根据当前审核点配置重新解释名称、展示编码、审核分类、排序或合同类型。
- `SYS-*` 诊断策略不得混入业务 `severityPolicy`。
- ReviewResultSnapshot 还应记录当次启用审核点清单和配置停用审核点清单，避免后续配置变化导致历史结果无法解释。
- ReviewResultSnapshot 可保存 `disabledByConfigCount` 作为管理台和评测报告摘要字段；该字段不属于普通结果页主摘要分母。
- 配置停用的审核点不生成点级结果记录；如需展示停用原因，应作为 execution/config metadata，而不是业务 finding 或 `SYS-*`。

`ContractTypeProfile.reviewPointOverrides` 只允许覆盖少数白名单字段：

- `severity`
- `criticalEvidenceSlots`
- `allowedWarningTypes`
- `budgetPriority`

`ContractTypeProfile` 不得覆盖执行逻辑、模型 prompt、后端裁判规则或任意脚本。

### ContractTypeProfile

字段方向：

- `type: ENGINEERING / MATERIAL_SUPPLY / SERVICE_FEE / MISC`
- `enabledReviewPoints`
- `requiredStructuredFields`
- `fieldLexiconOverrides`
- `patternLibraryOverrides`
- `evidenceSelectorProfile`
- `modelPromptProfile`
- `severityPolicy`
- `evaluationDataset`
- `resultGroupingPolicy`

合同类型来源优先级：外部系统传入 > 管理台人工选择 > 系统自动建议。

### StructuredFieldDefinition

MVP 需要支持合同结构化字段配置。管理台直接录入字段和 SAP/API 传入字段必须使用同一字段定义。

MVP 首批结构化字段采用通用字段必填和按付款方式条件必填。字段定义除 `required` 外，需要支持按 `paymentMethod` 表达适用性和条件必填规则。自定义新增字段可按配置设为必填或可选。

- `contractTotalAmount` 固定保存合同含税总金额，不需要额外 `amountTaxIncludedFlag` 改变其语义。
- MVP 任务和 ReviewResultSnapshot 固定保存 `currency=CNY`。币种不是可编辑的首批 StructuredFieldDefinition。
- 税率和付款比例按百分比数值保存，例如 `13` 表示 `13%`，不得按小数比例保存为 `0.13`。管理台、SAP/API、数据库和结果快照必须保持同一语义。
- 税率字段支持最多 4 位小数，付款比例字段支持最多 2 位小数；数据库精度必须与 API 校验保持一致，超精度值不得在持久化阶段被静默舍入。
- 月度付款的预付款、进度款、竣工款和结算款保存累计比例；质保款保存独立比例。不得将五项比例解释为互斥分项并简单求和。
- 按月度付款时，五类付款比例适用且必填，`milestonePaymentTerms` 不适用。
- 按节点付款时，`prepaymentRatio` 和 `milestonePaymentTerms` 适用且必填，四个月度专属比例不适用。
- 不适用字段不应保存虚假零值或占位字符串。
- `milestonePaymentTerms` 按多行文本保存，属于记录和展示字段，不绑定审核点、EvidenceSlot 或业务裁判逻辑。

字段定义方向：

- `fieldName`
- `fieldCode`
- `fieldType`
- `sortOrder`
- `placeholder`
- `selectOptions`
- `required`
- `enabled`
- `createdAt`
- `updatedAt`

字段类型第一版至少需要覆盖：

- 文本。
- 数字。
- 百分比。
- 下拉选择。
- 日期。

字段配置变化必须可审计。字段停用或删除不得影响已创建任务、历史 execution 和 ReviewResultSnapshot；历史快照读取当次结构化字段输入，不依赖当前字段配置重新解释。
字段停用或删除必须检查审核点依赖关系。被启用审核点依赖的字段不得直接停用或删除；应记录被阻止操作和依赖审核点清单。

自定义新增字段只进入结构化字段池和后续规则配置候选，不自动生成审核点、EvidenceSlot 或业务裁判逻辑。

### ReviewBudgetProfile

至少包含：

- `STANDARD`
- `DEEP_REVIEW`
- `EVALUATION`

记录：

- `reviewBudgetProfileVersion`
- `modelBudget`
- `standardToDeepRatio`
- `budgetApprovalPolicyVersion`
- `createdAt`

`standardToDeepRatio` 一期默认值为 `5:1`。

### CallerPolicyRegistry

一期使用简单 caller policy，不做多租户：

- `callerId`
- `callerType: EXTERNAL_SYSTEM / ADMIN / EVALUATION / DEBUG`
- `allowedBudgetProfiles`
- `allowTypeSuggestions`
- `allowCorrectionExecution`
- `enabled`

所有 caller policy 变更必须审计。

扩大 caller 能力需要第二名 `CALLER_POLICY_ADMIN` 批准；收紧或紧急停用可由单名管理员立即执行并审计。

不得以生产 SQL 作为常规变更路径。

### DatasetVersion

样本集生命周期：

- `coverageScope`
- `effectiveFrom`
- `reviewBy`
- `productionDriftSignals`
- `retiredAt`

规则集标记：

- `ruleSetDatasetStatus: CURRENT / EXPIRED / DRIFTED`

### Audit Log

MVP 管理台暂不做平台内登录、账号体系和权限矩阵；访问控制由部署环境、内网、VPN、反向代理或外围系统承担。MVP 配置变更仍应记录配置版本、变更时间和操作者占位信息；操作者可暂记为 `SYSTEM`、`ADMIN_PLACEHOLDER` 或环境账号。

完整登录、角色权限、审批、敏感诊断导出和审计矩阵延后到 Pilot / Production Readiness 设计。

审计日志一期默认保留 365 天，仅安全审计员和受控管理员可查询。

审计日志必须持久化为 append-only 记录，普通管理员无删除/修改权限，并纳入定期备份与恢复演练。

一期最低要求：

- 数据库权限隔离。
- 应用层禁止 update/delete。
- 事件唯一 ID。
- 写入时间。
- 操作者。
- 变更前后摘要。

事件哈希链属于可选增强。

`changeSummary` 使用有限结构，不在审计事件复制完整规则集或合同：

- `entityType`
- `entityId`
- `action`
- `beforeVersion?`
- `afterVersion?`
- `changedFields[]`
- `beforeDigest?`
- `afterDigest?`
- `humanReason`
- `relatedApprovalId?`

敏感值、完整 prompt、完整合同和 raw model output 不进入 `changeSummary`。

### ModelCallIntent

模型调用先创建 intent：

- `taskId`
- `executionId`
- `family`
- `pointCode?`
- `inputBlockIds`
- `inputBlockIdsHash`
- `promptVersion`
- `schemaVersion`
- `modelVersion`
- `attempt`

### GemmaExtractionArtifact

一期最小字段：

- `artifactId`
- `taskId`
- `executionId`
- `bundleId`
- `reviewPointFamily`
- `requestedRoles`
- `coveredRoles`
- `uncoveredRoles`
- `inputBlockIds`
- `inputBlockIdsHash`
- `promptVersion`
- `schemaVersion`
- `modelVersion`
- `outputSchemaValidationStatus`
- `parsedRoles`
- `confidence`
- `evidenceAnchors`
- `redactedRawOutputRef?`
- `encryptedRawOutputRef?`

### ReviewCompleteness

任务级结果需要生成审核完整度摘要：

- `reviewCoverageStatus: FULL_REVIEWED / PARTIAL_REVIEWED / LOW_CONFIDENCE_REVIEW`
- `executablePointCount`
- `concludedPointCount`
- `notConcludedPointCount`
- `corePointNotConcludedCount`
- `criticalSlotCoverageRate`
- `evidenceCoverageRate`
- `confidenceLevel: HIGH / MEDIUM / LOW`
- `lowConfidenceRegionImpact`

`criticalSlotCoverageRate` 计算方式：

```text
covered critical slots / total critical slots required by executable core review points
```

slot 只有在存在候选、达到该 slot 的 `confidenceRequired`、且未因预算截断或低置信区域失效时才算 covered。

### TemporaryRuleSetRunApproval

样本集过期 90 天后，临时运行必须先创建审批：

- `ruleSetVersion`
- `callerId` or exact task scope
- `purpose`
- `approvedByP0`
- `approvedByAdmin`
- `expiresAt: max 24h`
- `maxTaskCount`

任务创建时显式引用 approval ID；不得静默降级到其他规则集，也不得以环境变量绕过。

## 待确认

- 完整 ERD。
- 表命名和字段类型。
- 迁移工具。
- 文件、预览资产、脱敏样本和 raw output 的存储位置。
- 加密密钥和 HMAC audit key 管理。
- 审计日志 append-only 的具体数据库权限实现。
- 不可变版本快照的存储策略。
