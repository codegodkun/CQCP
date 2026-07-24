# docs/backend.md

## 核心链路

后端负责统一审核链路：

1. 接收外部系统、管理台或评测入口。
2. 创建异步任务、execution、result URL 和状态机。
3. 调用 Word 解析链路。
4. 构建合同证据模型和 CandidateIndex。
5. 生成 ReviewExecutionPlan。
6. 执行 CandidateResolver 和 EvidenceSlot Preflight。
7. 构建 FamilyEvidencePlan、FamilyModelCallPlan、EvidenceBundle 和 PointEvidenceOverlay。
8. 执行规则/正则/Gemma 混合路由。
9. 校验 GemmaExtractionArtifact。
10. 执行后端确定性裁判。
11. 合成 Finding、SYS、SourceAnchor 和 ReviewResultSnapshot。

## Word-first 解析

一期输入范围是 Word。推荐开源库组合：

- `.docx` 段落/表格/样式解析：Apache POI XWPF，主解析器。
- `.doc` 支持：LibreOffice headless，转 `.docx` 后进入主解析链路。
- WordprocessingML / content controls / checkbox 辅助：docx4j。
- 通用文本/metadata 兜底：Apache Tika，交叉校验，不作为主解析。
- Word 预览转换：LibreOffice headless，转 PDF/HTML 供左侧预览。

解析产物：

- `ContractDocument`
- `DocumentBlock`
- `TableBlock`
- `FormControlBlock`
- `SectionTree`
- `RegionIndex`
- `ContractIndex`
- `ParseQualityReport`

解析必须保留：

- `contextType`
- `sourceOrigin`
- `sourceExtractionMode`
- `blockConfidence`
- `tableConfidence`
- `controlConfidence`
- `regionConfidence`

`sourceOrigin=PREVIEW_ONLY` 的内容不得进入业务裁判。

### 解析质量门槛

`ParseQualityReport.parseStatus`：

- `GOOD`：可进入完整审核。
- `PARTIAL`：可审核，但低置信审核点应标注。
- `LOW_CONFIDENCE`：只跑基础规则，复杂点输出 `SYS-PARSE-LOW-CONFIDENCE`。
- `FAILED`：不输出业务风险，只提示解析失败。

若解析产物只有 `PREVIEW_ONLY` / `sourceExtractionMode=PREVIEW` blocks，且不存在任何可用于合同证据的 `NATIVE_WORD` / `LIBREOFFICE_CONVERSION` block，则合同内容解析状态必须为 `FAILED`。

`TEXT_FALLBACK` 不得生成 `EXACT_TEXT_RANGE`，不得单独支持 HIGH role、确定性 ERROR 或 `PROVEN_REQUIRED_CLAUSE_ABSENT`。

## 中文证据索引

一期索引：

- `AmountIndex`
- `RatioIndex`
- `PartyIndex`
- `DateIndex`
- `PaymentTermIndex`
- `HeadingIndex`
- `TocIndex`
- `AttachmentIndex`
- `SensitiveInfoIndex`
- `ControlIndex`

索引构建应使用稳定规则、字段词库、正则和值语法，不应依赖 Gemma 作为原始解析主力。

## 审核点与路由

每个审核点必须是可执行定义，不是单纯 prompt。

`ReviewPointDefinition` 包含：

- `code`
- `name`
- `category`
- `applicableContractTypes`
- `requiredStructuredFields`
- `executionStrategy`
- `evidencePolicy`
- `candidateExtractionPolicy`
- `deterministicRule`
- `modelAssistPolicy`
- `severityPolicy`
- `sourceAnchorPolicy`
- `evaluationPolicy`

审核点配置由管理后台维护，但运行时只读取 execution 创建时的有效配置版本：

- MVP 管理台只支持展示和有限编辑预置审核点，不支持新增任意审核点。
- MVP 可编辑配置字段包括审核点编码、名称、审核分类、默认严重级别、启用状态、排序、依赖字段和合同类型。
- 管理台可编辑的审核点编码仅为展示编码，不能为空且在当前配置中唯一。
- 系统内部 `reviewPointCode` 是唯一不可编辑主 key。ReviewExecutionPlan、EvidenceSlot 绑定、后端裁判、API 输出和快照解释必须使用内部 `reviewPointCode`。
- ReviewResultSnapshot 同时记录当次展示编码和内部 `reviewPointCode`。
- MVP 第一版审核执行逻辑由项目团队预置生成，通过 `ReviewPointDefinition`、EvidenceSlot、候选抽取规则、后端裁判规则和 Gemma 辅助策略固化。
- 后端应向管理台提供业务可读的执行逻辑摘要：内部 code、执行策略名称、依赖字段、需要的证据类型、是否使用 Gemma 辅助、裁判说明和最近一次版本更新时间。
- MVP 管理台摘要接口不得暴露完整正则、prompt、EvidenceSlot 细节或后端代码。
- 审核分类只作为展示和治理字段，不参与执行路由。执行路由必须由内部 `reviewPointCode`、execution strategy 和版本化规则定义决定。
- 审核结果不理想时，应按问题类型迭代词库、正则、ValueGrammar、EvidenceSlot policy、Gemma 局部 prompt、后端裁判或结果合成；除严重级别外，MVP 管理台不得直接改写执行逻辑。
- 执行逻辑变更必须生成可追溯版本，并通过脱敏/合成样本回归验证后用于后续 execution。
- 审核分类 MVP 至少支持 `结构化信息比对` 和 `合同内部一致性`；合同类型 MVP 仅支持 `工程采购合同`，但字段保留扩展能力。
- MVP 中审核点配置里的合同类型只是展示和未来扩展预留字段，不参与复杂适用范围路由。首批 9 个审核点默认适用于工程采购合同。
- 排序字段只用于展示顺序；ReviewExecutionPlan 的执行顺序由后端计划器根据依赖、证据复用和 execution strategy 决定，不得由管理台排序字段直接驱动。
- 依赖字段来自已配置合同字段清单，可多选。
- 保存审核点配置时，后端必须校验依赖字段来自已启用合同字段，并覆盖该审核点定义所需的 required structured fields。
- 依赖字段配置会导致审核点不可执行时，后端拒绝保存配置并返回字段级错误。配置错误不得延迟到任务执行阶段表现为 `SYS-MISSING-INPUT`。
- 执行策略、EvidenceSlot、后端裁判和模型辅助策略不允许在 MVP 管理台任意改写。
- 新增审核点需要同步定义 EvidenceSlot、抽取策略、后端裁判、结果展示和样本验证，应延后到后续阶段通过独立任务实现。
- 管理员可配置审核点默认严重级别，用于明确业务不一致时输出 `ERROR` 或 `WARNING`。
- `PASS`、`NOT_CONCLUDED`、`SKIPPED` 由后端裁判、证据状态和适用性生成，不得作为管理员可选严重级别。
- `ERROR` 和 `WARNING` 都是业务 finding，结果摘要必须分别计算 `errorCount` 和 `warningCount`。
- 结果摘要必须计算 `plannedPointCount`，表示本次启用并进入 ReviewExecutionPlan 的审核点总数。配置停用的审核点不进入分母；进入计划后判定 `SKIPPED` 的审核点进入分母。
- 结果摘要必须计算 `passCount`、`notConcludedCount` 和 `skippedCount`。`passCount` 表示明确通过的审核点数量；`notConcludedCount` 表示覆盖不足或未形成可靠结论；`skippedCount` 表示审核点不适用。`PASS`、`NOT_CONCLUDED`、`SKIPPED` 和 `SYS-*` 不计入业务风险数量。默认严重级别只影响 finding 严重程度，不影响 finding 是否生成。
- 后端裁判不得 hardcode 业务不一致的最终严重级别；应先形成事实判断，再读取当前 execution 绑定的 `severityPolicy`。
- MVP 审核点配置保存后即对下一次新建审核任务生效，不设置单独发布流程。
- 每次保存配置应形成新的可追溯配置版本或变更记录；Execution 创建时绑定当时有效的配置版本，后续配置变更不得影响该 execution 的结果合成或快照解释。
- Execution 结果合成时必须把审核点名称、展示编码、审核分类、排序、合同类型和严重级别写入 ReviewResultSnapshot；历史结果查询不得用当前配置覆盖快照值。
- `SYS-*` 系统诊断仍独立于业务 finding，不受审核点严重级别配置影响。
- 被配置停用的审核点不得进入新建 execution 的 ReviewExecutionPlan，不产生点级状态，也不得映射为 `SKIPPED` 或 `NOT_CONCLUDED`。
- Result API 应返回进入 ReviewExecutionPlan 的全部点级结果，包括 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED`，供普通结果页默认完整展示。
- `SKIPPED` 点不要求 EvidenceSlot 或 SourceAnchor；结果中必须提供适用性原因。
- `PASS` 点必须具备 required EvidenceSlot 和 SourceAnchor。证据不足、证据歧义或覆盖不足时不得判定 `PASS`，应按规则输出 `NOT_CONCLUDED` 和业务可读原因。
- `ERROR` 和 `WARNING` 点必须具备 required EvidenceSlot 和 SourceAnchor。没有可靠证据不得生成业务 finding，应输出 `NOT_CONCLUDED` 和业务可读原因。
- MVP SourceAnchor 最低支持 block/section/table row/cell 级定位，精确字符范围不是硬要求。
- 金额、税率和比例候选若有 exact text range，应提供给前端做字符级高亮；若没有 exact range，但证据块和候选值可靠，仍可执行裁判并返回候选值。
- 同一证据块存在多个金额、税率或比例候选时，CandidateResolver 必须能可靠归属到当前审核点所需候选值才可执行裁判。
- 只有 block 级定位但候选值归属不清时，应输出 `NOT_CONCLUDED`，业务原因说明“证据块内存在多个候选，无法可靠归属”。
- 候选归属不清时可调用 Gemma 局部辅助，但输入仅限相关证据块和必要上下文。Gemma 输出仅作为 CandidateResolver 辅助信号，不得直接决定点级最终状态。
- Gemma 辅助后仍无法可靠归属时，必须输出 `NOT_CONCLUDED`，业务原因说明“候选归属仍不可靠”。不得猜测候选继续裁判。
- Gemma 不可用或超时时，不得阻断所有审核点。若规则/候选本身已达到可靠标准，后端可继续确定性裁判；若当前点依赖 Gemma 消歧且 Gemma 不可用，则该点输出 `NOT_CONCLUDED`。
- 缺少字符级范围本身不得导致 `NOT_CONCLUDED`。
- ReviewResultSnapshot 应记录本次配置版本、启用审核点清单和配置停用审核点清单。
- 管理台和评测报告可读取 `disabledByConfigCount` 和配置停用审核点清单；普通结果页主摘要不得把配置停用点混入 planned/status counts。
- 首批 9 个审核点默认全部启用。若测试期间停用 core 审核点，结果不得用于证明 MVP core 覆盖率达标。

执行策略：

- `STRUCTURED_COMPARE`
- `FORMULA_RULE`
- `PATTERN_EXTRACT_THEN_COMPARE`
- `LLM_EXTRACT_THEN_RULE`
- `LLM_SEMANTIC_WITH_GUARD`

路由规则：

- 结构化字段缺失时，按审核点定义 `SKIPPED` 或 `SYS-MISSING-INPUT`。
- 明确公式和数值关系优先走后端规则。
- 正则/词库能召回候选时，优先用候选抽取 + 后端比较。
- 候选证据存在但语义归属不清时，Gemma 只做局部抽取或归属判断。
- 证据不足时输出 `SYS-*`，不得回灌全文，不得伪造业务 finding。

### 结构化字段配置

MVP 首批结构化字段由管理台直接录入，同时 SAP/API 传入字段与管理台字段定义保持一致。

MVP 首批结构化字段采用通用字段必填和按付款方式条件必填。任务创建 API 和管理台提交必须执行相同的条件校验；缺失适用场景的必填字段时拒绝创建任务，不进入审核链路。

- `contractTotalAmount` 的业务语义固定为合同含税总金额。后端不接受通过 `amountTaxIncludedFlag` 改变该字段语义。
- MVP 币种固定为 `CNY`，作为平台常量写入任务和结果快照，不要求管理台用户录入。SAP/API 可省略 `currency`；显式传入时只接受 `CNY`，其他币种拒绝创建任务。
- MVP 首批结构化字段 key 沿用 PRD 冻结最小契约，作为 OpenAPI、数据库和 ReviewResultSnapshot 字段名。
- MVP 枚举编码固定为：`pricingMode=FIXED_TOTAL_PRICE / PROVISIONAL_TOTAL_PRICE`，`paymentMethod=MONTHLY / MILESTONE`，`invoiceType=VAT_GENERAL / VAT_SPECIAL`。
- MVP 创建任务最小校验错误码固定为：`REQUIRED_FIELD_MISSING`、`CONDITIONAL_FIELD_MISSING`、`INVALID_ENUM_VALUE`、`INVALID_DECIMAL_SCALE`、`INVALID_PERCENT_RANGE`、`UNSUPPORTED_CURRENCY`、`UNSUPPORTED_FILE_TYPE`。
- 按月度付款：五类付款比例必填，`milestonePaymentTerms` 不适用且不得要求传入。
- 按节点付款：`prepaymentRatio` 和 `milestonePaymentTerms` 必填；四个月度专属比例不适用且不得要求传入。
- 后端不得要求调用方为不适用字段传空字符串、零值或虚假占位值。
- `milestonePaymentTerms` 保存原始值并写入任务/结果快照，但不将其加入首批 ReviewPointDefinition 的 requiredStructuredFields，不构建对应 EvidenceSlot，也不据此生成业务 finding。

后端需要支持合同字段配置，至少包含：

- 字段名称。
- 字段编码。
- 字段类型。
- 排序号。
- 占位提示。
- 下拉选项。
- 是否必填。
- 是否启用。

结构化字段配置用于：

- 管理台创建合同任务时渲染录入表单。
- API 校验 SAP/外部系统传入字段。
- 审核点依赖字段配置。

合同字段停用或删除前，后端必须检查是否被任何启用审核点依赖。若存在依赖，拒绝停用/删除并返回依赖审核点清单。用户必须先移除依赖或停用相关审核点。
- ReviewExecutionPlan 判断 requiredStructuredFields 是否满足。
- 结果快照记录结构化字段输入。

后台支持自定义新增字段，但自定义字段不得自动生成审核点、EvidenceSlot、规则或业务裁判逻辑。新增字段若要参与审核，必须通过后续 ReviewPointDefinition、RuleSetVersion 或明确任务绑定。

字段被停用或删除后，不得影响已创建任务、历史 execution 和 ReviewResultSnapshot 的解释；历史快照必须继续保留当次输入字段。

### 合同类型画像

合同类型是规则集路由维度，不是独立系统。

一期类型：

- `ENGINEERING`
- `MATERIAL_SUPPLY`
- `SERVICE_FEE`
- `MISC`

合同类型来源优先级：

```text
外部系统传入 > 管理台人工选择 > 系统自动建议
```

系统自动建议只作为 hint，不得无声覆盖外部系统或人工选择。低置信分类应启用“通用规则 + 保守补充规则”。

### 候选归属边界

禁止按“同值”直接合并语义候选。

- `RawCandidate`：每个来源独立保存。
- `ResolvedCandidateGroup`：只有 role + value + compatible context 一致才合并。

同值但标签不同、section 不同、role hint 不同或来源置信度冲突时，不合并。

CandidateResolver 的 HIGH 必须满足值语法、解析置信、标签、上下文和无竞争高置信候选；多个 HIGH 指向同一 role 且无法声明式消歧时，输出 `CONFLICTED`。

### 缺证据归因

系统不得轻易断言“合同本身缺少证据”。缺证据归因必须保守区分：

- `PROVEN_REQUIRED_CLAUSE_ABSENT`：缺少必备条款本身是业务风险，且 applicability、解析、索引、覆盖证明完整，可生成 WARNING / ERROR。
- `CONTRACT_CONTENT_INSUFFICIENT`：覆盖良好但不足以形成该点结论，输出 `NOT_CONCLUDED`。
- `SYSTEM_COVERAGE_LIMITED`：解析、索引、预算、模型或内部错误影响覆盖，输出 `NOT_CONCLUDED`。

生成 `PROVEN_REQUIRED_CLAUSE_ABSENT` 时必须附 `coverageProofSummary`，不得伪造“未找到证据列表”。

## 异步任务

任务创建后立即返回 `taskId`、`executionId`、`status=QUEUED` 和 `resultUrl`。

`taskId` 标识业务任务；`executionId` 标识一次具体审核执行。合同类型修正、预算 profile 变更后重跑、人工或 API correction rerun 都必须创建新的 `executionId`，不得覆盖旧执行记录。

一期状态机：

- `CREATED`
- `QUEUED`
- `PARSING`
- `INDEXING`
- `PLANNING`
- `BUILDING_EVIDENCE`
- `REVIEWING_RULES`
- `REVIEWING_MODEL`
- `COMPOSING`
- `SUCCESS`
- `PARTIAL_SUCCESS`
- `FAILED`
- `CANCELLED`

一期 worker：

- Single Review Worker。
- 从 PostgreSQL 拉取 `QUEUED` 任务。
- 顺序处理单份合同。
- 每阶段写 `TaskStageLog`。
- 模型调用通过 `Model Profile` 绑定到本地 OpenAI-compatible 模型服务、公网兼容 OpenAI API 或 mock fallback；MVP 可按 profile 设置 `maxConcurrent`、timeout 和 retry。
- 结果完成后生成 `ReviewResultSnapshot`。

一期不用 RabbitMQ/Kafka，不做多 worker。后续可在不改变入口 API 的前提下升级为多 worker 或 MQ。

### 任务幂等

task 只作为聚合标识，不维护一套与 execution 竞争的审核状态。execution 拥有独立状态。

result URL 形式：

```text
/review/results/{taskId}?executionId={executionId}
```

不带 `executionId` 时解析到最新未 superseded execution；带 `executionId` 时展示该历史执行。API 轮询使用 execution 资源返回的 `status`。

MVP 普通结果 URL 不做平台侧登录、公开令牌或独立访问控制。外围系统负责对 URL 进行编码、加密、分发和访问控制。平台侧仍必须限制普通结果接口输出范围，不返回 prompt、raw output、endpoint、stack trace、admin logs、secret 或管理台诊断详情。

MVP 管理台暂不做平台内登录、账号体系和权限矩阵；访问控制由部署环境、内网、VPN、反向代理或外围系统承担。后端 Admin API 暂不设计细粒度角色权限，但必须限制常规接口输出范围，不返回完整 prompt、完整模型 raw output、endpoint secret、stack trace、完整合同敏感调试包或密钥。

MVP 配置变更仍应记录配置版本、变更时间和操作者占位信息；操作者可暂记为 `SYSTEM`、`ADMIN_PLACEHOLDER` 或环境账号。Pilot / Production Readiness 再补登录、角色、审批、审计和受控敏感诊断导出。

同一 `taskId` 下同时只能有一个 non-terminal execution。

## API 状态与诊断映射

外部 API 顶层状态保持有限集合：

- `QUEUED`
- `PROCESSING`
- `SUCCESS`
- `PARTIAL_SUCCESS`
- `FAILED`

点级状态：

- `PASS`
- `WARNING`
- `ERROR`
- `NOT_CONCLUDED`
- `SKIPPED`

内部 `SYS-*` 映射为 `NOT_CONCLUDED`，并提供业务化 `notConcludedReason`。

外部系统不得依赖内部 `SYS-*` 码。管理台和评测报告保留完整内部诊断。

## 金额与税额确定性裁判

- MVP 币种固定为 `CNY`，金额使用十进制定点数并保留两位小数，不得使用二进制浮点数进行公式裁判。
- 税率和付款比例使用百分比数值存储和传输，例如 `13` 表示 `13%`。管理台、外围系统、SAP/API、数据库和快照必须采用同一语义，禁止混用 `13` 与 `0.13`。
- 后端执行比例计算时统一除以 `100`，例如 `taxExcludedAmount × taxRate ÷ 100`。
- 税率最多保留 4 位小数，付款比例最多保留 2 位小数；末尾零不影响语义。超出精度时必须返回字段校验错误，不得静默四舍五入或截断。
- 月度付款的 `prepaymentRatio`、`progressPaymentRatio`、`completionPaymentRatio`、`settlementPaymentRatio` 为累计付款比例，业务关系为 `prepaymentRatio ≤ progressPaymentRatio ≤ completionPaymentRatio ≤ settlementPaymentRatio`。
- `warrantyRetentionRatio` 为单独比例，业务关系为 `settlementPaymentRatio + warrantyRetentionRatio = 100`。
- MVP 不在任务创建阶段重复校验上述月度比例跨字段关系，也不将其作为 `PAYMENT_TERMS` 业务 finding。外围系统负责输入校验，管理台测试数据由测试人员控制。
- 平台仍校验每个比例字段的 `0` 至 `100` 范围、数值精度和按付款方式条件必填规则。
- `paymentMethod=MILESTONE` 时继续执行预付款比例一致性审核，其余四个月度专属比例审核点按适用性输出 `SKIPPED`。不适用不得映射为 `SYS-* / NOT_CONCLUDED`，也不得生成业务 finding。
- `contractTotalAmount = taxExcludedAmount + taxAmount` 为强校验，绝对误差不超过 `0.01` 元视为一致；证据完整且超出容差时可判定 `ERROR`。
- `taxAmount = taxExcludedAmount × taxRate` 在 MVP 为弱校验，偏差只生成 `WARNING`，不得单独生成 `ERROR`。
- 弱校验必须考虑分项计税、多税率和逐行舍入的可能性；税率证据缺失或不可靠时不执行该校验。
- 税率新增固定 `CandidateRole=taxRate`，作为 `TAX_AMOUNT_FORMULA_CONSISTENCY` 的 optional EvidenceSlot 候选角色。税率候选来自 `RatioIndex`，但必须由 CandidateResolver 可靠归属为税率，不得把其他比例候选按同值直接复用。
- 公式结论由后端确定性裁判生成。模型只可提供局部抽取或证据候选，不得直接决定 `PASS / WARNING / ERROR`。
- 金额或税率证据不足、角色冲突、解析异常时输出相应 `SYS-*` 并映射为 `NOT_CONCLUDED`，不得生成无可靠证据的业务 finding。

点级结果摘要还应包含：

- `plannedPointCount`
- `passCount`
- `errorCount`
- `warningCount`
- `notConcludedCount`
- `skippedCount`
- `pointCoverageStatus: COMPLETE / PARTIAL / LOW_CONFIDENCE`
- `missingOptionalSlots[]`
- `requiresHigherBudget`
- `recommendedBudgetProfile?`
- `notConcludedReason?`
- `notConcludedDetail?`

`notConcludedReason`：

- `PARSE_LOW_CONFIDENCE`
- `EVIDENCE_NOT_FOUND`
- `EVIDENCE_AMBIGUOUS`
- `MODEL_UNAVAILABLE`
- `MODEL_BUDGET_EXCEEDED`
- `INTERNAL_RULE_ERROR`

`businessMessage` 由平台后端根据固定国际化模板生成，不由模型生成，也不允许规则集写任意自由文本。

## 失败与降级

- 文档解析失败：`FAILED`，不输出业务 finding。
- 解析低置信：`PARTIAL_SUCCESS`，受影响审核点输出 `SYS-PARSE-LOW-CONFIDENCE`。
- EvidencePacket 为空：`SYS-EVIDENCE-INSUFFICIENT` 或审核点专用 SYS，不回灌全文。
- Evidence token 预算不足：`SYS-EVIDENCE-BUDGET-EXCEEDED`，不强行业务裁判。
- 候选索引缺失必要证据：`SYS-INDEX-INCOMPLETE`。
- 候选角色冲突：`SYS-ROLE-CONFLICT` 或 `SYS-EVIDENCE-AMBIGUOUS`。
- 规则执行异常：对应点 `SYS-RULE-ERROR`。
- Gemma 超时：对应点 `SYS-MODEL-TIMEOUT`。
- Gemma 输出不完整：`SYS-MODEL-OUTPUT-INCOMPLETE`。
- Gemma primary/supplement 结果矛盾：`SYS-MODEL-CONFLICT`。
- 合同类型画像歧义且影响审核计划：`SYS-CONTRACT-TYPE-AMBIGUOUS`。
- 结果合成失败：`FAILED`，不生成业务结果快照。

任何 `SYS-*` 都不是业务 finding，不计入业务风险统计。

普通结果页只暴露业务化 `NOT_CONCLUDED` 原因。管理台和评测报告保留内部 `SYS-MODEL-*`、模型调用状态、超时/不可用原因和诊断摘要。
`requiresHigherBudget / recommendedBudgetProfile` 不进入第一轮 MVP 普通结果页响应；普通结果页 API 只返回业务化未结论原因和人工核对提示。管理台任务详情、评测报告和 AI 调优包 API 可返回预算诊断字段。
MVP 不保存完整模型原始输出，管理台 API 也不返回完整模型原始输出、完整 prompt、endpoint secret、stack trace 或完整合同敏感调试包。模型运行记录只保存模型调用状态、`SYS-MODEL-*` 诊断码、schema 校验结果、redacted artifact 摘要、token 用量、耗时、模型版本、调用时间和必要的候选归属结论摘要。

MVP 管理台模型配置相关 API 只读：

- 模型配置只读返回当前模型版本和 endpoint 状态，不支持编辑。
- 提示词模板不支持编辑；可返回业务摘要或延后。
- 最终提示词预览不返回完整 prompt，延后到后续治理/技术视图。
- 任务日志 API 属于任务详情能力，不属于配置审核规则模块。
- 后端必须在每个 execution 绑定当次 `modelProfileCode`、provider 类型、模型名称、endpoint alias 和 `modelConfigVersion`。任务详情 API 顶部概览返回“审核模型”摘要；历史快照不随后续模型配置切换变化。
- `Model Profile` 可配置本地模型、公网兼容 OpenAI API 模型和 mock fallback。公网模型 profile 是否可用于真实合同由业务方、管理员和部署环境承担配置责任；后端必须校验 profile 已启用、使用范围、配置版本和 provider-specific readiness。需要 secret 的 provider 必须校验密钥 readiness；MOCK 使用 `secretRequired=false`，不得伪造 `secretConfigured=true`。
- 管理台创建任务时可以传入 `modelProfileCode`；外部 API MVP 不允许调用方直接指定 `modelProfileCode`。外部 API 创建任务统一使用当前默认启用的 `Model Profile`。如后续需要开放，必须通过 caller policy 白名单和独立 API 契约扩展。
- 模型不论来自本地还是公网，都不得直接决定最终业务 finding；后端仍负责结构化比对、确定性裁判和点级结果合成。
- MVP 任务日志 API 只暴露阶段级事件，不暴露每个内部函数调用。阶段级事件按 `taskId + executionId + stageName + attempt` 记录，至少包含事件类型、发生时间、耗时、摘要状态、业务化原因和摘要级 `SYS-*` 诊断。
- 模型阶段日志可返回模型调用状态、token 用量、耗时、模型版本、schema 校验状态和诊断码摘要；不得返回完整 prompt、完整模型 raw output、endpoint secret、stack trace 或逐函数调试日志。

## Execution Binding Release

新 Task Creation 不得自行拼装 execution 的版本与模型字段。后端通过 ADR-017 定义的 resolver，以 `purpose + deploymentScope + contractTypeCode` 查询全部 raw binding rows，再按 `enabled && effectiveFrom<=Clock.instant()` 得到有效 candidates：

* raw rows 为 0：`NOT_FOUND`；
* 有 raw rows 但有效 candidate 为 0：`INACTIVE_OR_NOT_EFFECTIVE`；
* 有效 candidates 超过 1：`AMBIGUOUS`；
* 恰好 1 条时继续校验 budget/model 引用、legacy alias、MOCK readiness、parser/schema release 和 content digest。

正常 lifecycle 切换后允许“旧 disabled row + 新 enabled row”共存，不得按 raw row 数量误判 ambiguity。resolver 不 fallback、不取第一条、不解析异常 message；消费者使用稳定 reason enum。

一期 Demo 只允许 `MVP_DEMO / DEMO / ENGINEERING` binding，显式验证 `ENGINEERING -> ENGINEERING_PROCUREMENT -> v20260705.1` legacy alias。静态 review-assets 不参与 runtime loading；`MVP_DEMO_MOCK` 只用于 Demo，不代表 Production Ready。

resolver 返回完整 14 字段 tuple。后续 Task Creation 必须在一个 PostgreSQL 事务中完成 binding 校验、Task 插入和首个 `QUEUED` Execution 插入；任一失败整体回滚。`execution.model_config_version` 写入 snapshot 时映射为同值 `model_profile_version`。

## 基线冻结文档

- Word parser MVP 边界与最小字段清单见 `docs/word-parser-mvp-boundary.md`
- 模型网关与预算基线见 `docs/model-gateway-budget-baseline.md`

## 待确认

- 后端语言和框架。
- API 详细 OpenAPI 契约。
- PostgreSQL schema、JSONB 字段设计和 Flyway 迁移工具。
- 文件存储、预览资产存储和加密策略。
- 具体审核点实现顺序。
