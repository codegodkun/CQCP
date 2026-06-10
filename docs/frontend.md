# docs/frontend.md

## 范围

前端包括普通结果页和一期必要管理台。

前端不负责审批决策。页面文案必须明确平台结果是审核提示，不是审批“通过/不通过”。

## 普通结果页

MVP 普通结果 URL 不做平台侧登录、公开令牌或独立访问控制。外围系统负责对 URL 进行编码、加密、分发和访问控制，平台暂不在该方向投入额外能力。

普通结果页仍必须遵守展示安全边界：不暴露 prompt、raw output、endpoint、stack trace、admin logs、secret 或管理台诊断详情。

布局：

- 左侧：合同原文全文/预览。
- 右侧：审核点结果卡片。
- 点击右侧审核点或证据，左侧滚动并高亮对应原文位置。

处理中：

- 只显示简单状态页。
- 不展示半成品 finding。
- 不展示未经过统一合成和 post-check 的中间结果。

完成后：

- 展示左右分栏正式结果。
- 业务 finding 和 `SYS-*` 诊断分开。
- 不同审核点按类型展示不同字段，不再统一显示“结构化值 / 合同原文值”。
- 普通用户不直接看到 `SYS-INDEX-INCOMPLETE`、`SYS-ROLE-CONFLICT` 等技术码。
- 受影响审核点显示业务化提示，例如“证据不足，未形成可靠结论”或“证据存在冲突，请人工结合原文判断”。
- 第一轮 MVP 普通结果页不展示 `requiresHigherBudget` 或 `recommendedBudgetProfile`，也不提供“申请深度审核”入口；只展示业务化 `NOT_CONCLUDED` 原因和人工核对提示。
- 存在合同类型歧义时，普通结果页仅显示合同类型可能不匹配，部分审核点未完成可靠判断。
- Gemma 不可用、超时或输出异常时，普通结果页只展示业务化 `NOT_CONCLUDED` 原因，不展示内部 `SYS-MODEL-*` 技术码。
- 管理台任务详情展示内部 `SYS-MODEL-*`、模型调用状态、超时/不可用原因和诊断摘要。
- MVP 管理台不展示完整模型原始输出；只展示结构化诊断摘要、模型调用元数据和必要的 redacted artifact 摘要。
- `PROVEN_REQUIRED_CLAUSE_ABSENT` 作为独立业务 finding 展示，不映射为 `EVIDENCE_NOT_FOUND`。

顶部摘要至少展示：

- `reviewCoverageStatus`
- `confidenceLevel`
- `concludedPointCount / executablePointCount`
- `notConcludedPointCount`
- `corePointNotConcludedCount`
- `criticalSlotCoverageRate`
- `lowConfidenceRegionImpact summary`

普通用户默认看到业务化等级：

- 关键证据覆盖：高 / 中 / 低。

原始 `criticalSlotCoverageRate` 百分比放在展开详情或管理台中。

“关键证据覆盖”必须提供 tooltip，说明关键证据是形成可靠判断所必需的核心信息；覆盖越高表示系统找到的可用依据越完整，不承诺结论正确率。

### NOT_CONCLUDED 体验

未结论点应单独汇总为“需人工确认事项”，提供：

- 原因。
- 相关候选证据。
- 原文定位。
- source/coverage explanation。

试点培训和结果页文案必须明确：

- `PASS/WARNING/ERROR` 是审核提示，不是审批决定。
- `NOT_CONCLUDED` 表示系统没有足够依据，不表示合同无风险。
- 人工应优先查看核心未结论点和系统能力原因。

对于冲突证据：

- 右侧审核点卡片显示“证据冲突”。
- 展开后显示最多 2-3 个冲突候选。
- 每个候选显示标签、值、位置、置信原因。
- 左侧支持多 anchor 高亮。
- 当前选中 anchor 用主色，其他冲突 anchor 用次级高亮。
- 系统不替用户判断哪个冲突候选更可信，只解释为何无法裁判。

## 申请深度审核

“申请深度审核”不进入第一轮 MVP 普通结果页。`requiresHigherBudget / recommendedBudgetProfile` 属于预算诊断信息，MVP 仅在管理台任务详情、评测报告和 AI 调优包中展示。

后续若进入 Pilot，应与 correction execution、caller policy、预算审批和幂等保护一起设计，不在普通结果页提前放置不可执行入口。

## 管理台

一期管理台必要能力：

- 创建审核任务。
- 任务列表。
- 任务详情。
- 阶段日志。
- `ParseQualityReport`。
- 审核执行计划。
- EvidencePacket 摘要。
- Gemma 调用状态。
- 模型配置列表和审核模型展示。
- 审核点/规则集配置。
- 字段词库/正则库版本管理。
- 失败样本与候选优化。
- 管理员批准发布。
- `requiresHigherBudget` 和 `recommendedBudgetProfile` 诊断。
- 同一 `taskId` 下的 `executionId` history、superseded execution 和 correction rerun 记录。
- 合同类型歧义、建议类型和建议置信度。
- 冲突证据对比、redacted model artifact 摘要、schema validation errors 和 artifact anchors。
- `ModelCallIntent`、模型重试 attempt、backoff、最终 artifact 复用或失败原因。
- 人工反馈入口；若一期提供该入口，至少支持对核心审核点标记误报并记录反馈来源。

管理台可以展示中间诊断和部分 point result；普通结果 URL 不展示半成品审核结果。

MVP 管理台暂不做平台内登录、账号体系和权限矩阵；访问控制由部署环境、内网、VPN、反向代理或外围系统承担。前端不得因为 MVP 不做登录而扩大敏感信息展示范围。

管理台常规页面默认只展示摘要级诊断信息。完整 prompt、完整模型 raw output、endpoint secret、stack trace、完整合同敏感调试包和密钥不在常规页面展示；如后续需要查看或导出，应作为 Pilot / Production Readiness 的受控诊断能力单独设计，纳入权限、脱敏、审批和审计。

### 任务详情页顶部概览

任务详情页顶部概览区采用分层双视图的第一层，面向快速判断任务状态。MVP 至少展示：

- 任务基础信息：`taskId`、`executionId`、合同名称、创建来源、创建时间。
- 当前执行状态：任务状态、当前 stage、耗时。
- 审核模型：本次 execution 绑定的 `Model Profile`，包括模型展示名、provider 类型、模型名称和配置版本摘要。
- 审核点摘要：`plannedPointCount`、`passCount`、`errorCount`、`warningCount`、`notConcludedCount`、`skippedCount`。
- 关键异常摘要：解析低置信、模型不可用、证据不足、结果合成失败等业务化摘要和必要诊断。
- 结果入口：普通结果 URL 和 `ReviewResultSnapshot` 入口。

审核模型示例：

```text
DeepSeek V4 Flash（公网兼容 OpenAI 接口）
本地 OpenAI-compatible 模型服务（通过 ModelProfile 管理）
Local mock fallback
```

公网模型是否可用于真实合同由业务方、管理员和部署环境承担配置责任。前端必须展示当次实际使用模型，避免用户误以为所有任务都使用同一模型。

管理台创建任务时可以选择已启用且密钥已配置的模型 profile；不选择时使用当前默认模型。外部 API MVP 不允许调用方指定 `modelProfileCode`，因此管理台模型选择能力不得被理解为 SAP/OA/采购系统的第一版接口能力。

### AI 调优包

任务详情页 MVP 新增 `AI 调优包` tab。该 tab 面向内部人员生成和复制 TuningPacket，用于辅助人和外部 AI 分析问题，不是自动调优系统。

允许能力：

- 生成 TuningPacket。
- 按 `SINGLE_POINT` 或 `FOCUSED` 导出 TuningPacket。
- 复制结构化文本或 JSON 到外部 AI。
- 保存外部 AI 返回的建议文本，作为调优线索记录。

不允许能力：

- 自动调用公网 AI。
- 自动生成正式 `AITuningAdvice`。
- 自动执行 `CrossModelDiagnostic`。
- 自动修改 prompt、rule、pattern、field lexicon、CandidateResolver、EvidenceSlot、ModelProfile 或 ReviewPointDefinition。
- 自动影响当前 `ReviewResultSnapshot`。

`AI 调优包` tab 不展示完整合同、完整 prompt、完整模型 raw output、endpoint secret、stack trace 或全部内部日志。

### 合同字段配置

管理台需要提供合同字段配置能力，用于维护合同录入和结构化比对时使用的字段。

字段列表至少展示：

- 字段名称。
- 字段编码。
- 字段类型。
- 必填 / 可选。
- 启用 / 停用。
- 展示摘要。
- 排序。
- 操作入口。

字段编辑至少支持：

- 字段名称。
- 字段编码。
- 字段类型。
- 排序号。
- 占位提示。
- 下拉选项。
- 是否必填。
- 是否启用。

管理台创建合同任务时，应按已启用字段渲染录入表单。SAP/API 传入字段与管理台字段配置使用同一字段定义。

MVP 首批结构化字段采用通用字段必填和按付款方式条件必填，管理台创建合同任务时必须在提交前完成相同的条件校验。自定义新增字段可配置必填 / 可选。

MVP 首批字段 key 采用 PRD 冻结最小契约；前端中文文案映射到稳定英文 code：

- `pricingMode`: `FIXED_TOTAL_PRICE` 显示“固定总价”，`PROVISIONAL_TOTAL_PRICE` 显示“暂定总价”。
- `paymentMethod`: `MONTHLY` 显示“按月度付款”，`MILESTONE` 显示“按节点付款”。
- `invoiceType`: `VAT_GENERAL` 显示“增值税普通发票”，`VAT_SPECIAL` 显示“增值税专用发票”。

任务创建字段校验错误码由后端返回稳定 code，前端负责映射中文提示；MVP 最小集合为 `REQUIRED_FIELD_MISSING`、`CONDITIONAL_FIELD_MISSING`、`INVALID_ENUM_VALUE`、`INVALID_DECIMAL_SCALE`、`INVALID_PERCENT_RANGE`、`UNSUPPORTED_CURRENCY` 和 `UNSUPPORTED_FILE_TYPE`。

- `contractTotalAmount` 在管理台上的标签固定显示为“合同总金额（含税）”，不得只显示“合同总金额”。
- MVP 不展示币种选择控件；合同总金额、不含税金额和合同税额输入框统一显示单位“元”，币种固定为人民币。
- 选择按月度付款时：显示并要求填写预付款、进度款、竣工款、结算款、质保款比例；隐藏节点付款其他节点信息。
- 选择按节点付款时：显示并要求填写预付款比例和节点付款其他节点信息；隐藏四个月度专属比例。
- 被隐藏的不适用字段不提交空值或占位值。
- 节点付款其他节点信息使用多行文本控件，仅用于录入、任务详情和结果摘要展示，暂不展示任何审核状态或风险结论。

后台支持自定义新增合同字段。自定义字段只进入结构化字段池和后续规则配置候选，不自动生成审核点、不自动绑定 EvidenceSlot、不自动参与业务裁判。删除字段不得影响已创建任务数据和历史快照。
合同字段被启用审核点依赖时，不允许直接停用或删除。字段配置页应提示依赖该字段的审核点清单，并引导用户先调整审核点依赖或停用相关审核点。

### 审核规则配置

管理后台应提供“配置审核规则”页面，用于可视化维护审核点列表、分类、启用状态、排序、依赖字段和默认严重级别。

- MVP 保持与既有项目的配置页面风格一致，只做列表展示、详情展开和编辑，不提供新增审核点入口。
- 列表列包括审核点、审核分类、状态、默认级别、排序和操作；审核分类和状态列支持排序，顶部支持按审核点名称或编码搜索。
- 点击详情以下拉展开方式展示审核点编码、名称、审核分类、默认级别、启用状态、排序、依赖字段和合同类型。
- 编辑区域应支持审核点编码、名称、审核分类、默认级别、启用状态、排序、依赖字段和合同类型；EvidenceSlot、执行策略、后端裁判和模型辅助策略不在 MVP 页面中开放任意修改。
- 审核点编码是面向前台定位的展示编码，可编辑但不能为空且必须唯一；系统内部 `reviewPointCode` 不在页面中开放编辑。
- 搜索可匹配展示编码和名称；如后续展示内部 code，应明确标识为系统字段，避免与展示编码混淆。
- 审核点详情应只读展示业务可读的执行逻辑摘要，包括内部 code、执行策略名称、依赖字段、需要的证据类型、是否使用 Gemma 辅助、裁判说明和最近一次版本更新时间。
- MVP 普通配置页不展示完整正则、prompt、EvidenceSlot 细节或后端代码；这些内容延后到后续治理/技术视图。
- 审核分类仅用于页面展示、筛选和排序，不得暗示会改变执行逻辑。
- MVP 管理台不提供执行逻辑编辑入口；执行逻辑变更通过后续版本化规则/证据槽/prompt/后端裁判迭代完成。
- 审核分类选项 MVP 至少包含“结构化信息比对”和“合同内部一致性”。
- 合同类型 MVP 仅提供“工程采购合同”，作为后续多合同分类预留字段；页面不应暗示该字段会在 MVP 中改变审核点执行范围。
- 排序字段只影响管理台列表和普通结果页展示顺序，不影响审核执行顺序。
- 依赖字段使用下拉多选，选项来自“配置合同字段”的全部字段。
- 依赖字段保存前必须校验是否来自已启用合同字段，并是否覆盖该审核点运行所需的 required structured fields。
- 若缺少必需依赖字段，编辑表单拒绝保存，并显示业务可读提示，例如“该审核点至少需要依赖字段：甲方名称”。
- 默认严重级别只允许在 `ERROR` 与 `WARNING` 之间选择，用于明确业务不一致时的输出级别。
- `PASS`、`NOT_CONCLUDED`、`SKIPPED` 不作为可配置严重级别，由后端裁判、证据状态和适用性决定。
- 普通结果页应分别展示 `ERROR` 和 `WARNING` 数量。两者都属于业务 finding，但级别不同。
- 普通结果页应展示 `PASS` 数量，单独展示 `NOT_CONCLUDED` 数量和业务可读原因，并展示 `SKIPPED` 数量和适用性原因。`PASS`、`NOT_CONCLUDED`、`SKIPPED` 和 `SYS-*` 不计入业务风险数量。
- 普通结果页应展示 `plannedPointCount`，文案可为“本次计划审核点 N 个”。被配置停用的审核点不进入该分母；进入计划后因适用性跳过的审核点进入该分母。
- 普通结果页默认展示进入 ReviewExecutionPlan 的全部审核点，包括 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED`。
- 页面可提供状态筛选，但默认不得隐藏 `PASS` 或 `SKIPPED`。
- `SKIPPED` 点展示适用性原因，不展示证据定位入口。

配置审核规则页面标签页 MVP 范围：

- `审核点`：支持列表、详情展开和有限编辑。
- `模型配置`：只读展示当前模型版本和 endpoint 状态，不支持编辑。
- `提示词模板`：不支持编辑，仅可展示业务摘要或延后。
- `最终提示词预览`：不展示完整 prompt，延后到后续治理/技术视图。
- `任务日志` 不属于配置审核规则页面，应放在任务列表/任务详情相关位置。

### 任务日志

任务日志属于任务列表/任务详情层级能力，MVP 提供基础阶段日志，用于查看解析、索引、审核执行、模型辅助和结果合成等阶段状态。

MVP 任务日志只记录阶段级事件，不记录每个内部函数调用。前端展示重点是让管理台用户判断任务当前卡在哪个阶段、是否发生重试或超时、是否有业务化失败原因或摘要级 `SYS-*` 诊断。

阶段日志至少展示：

- stage 名称和阶段展示名。
- 事件类型：开始、完成、失败、重试、超时、跳过。
- attempt、发生时间、耗时和摘要状态。
- 业务化原因和摘要级诊断码。
- 模型阶段的调用状态、token 用量、耗时、模型版本、schema 校验状态和诊断摘要。

任务日志不得展示完整 prompt、完整模型 raw output、endpoint secret、stack trace 或逐函数调试日志。
- `PASS` 点必须展示证据定位入口和可解释依据。
- `ERROR` 和 `WARNING` 点必须展示证据定位入口和可解释依据。
- MVP 证据定位最低支持 block/section/table row/cell 级跳转；精确字符级高亮不是硬要求。
- 金额、税率和比例候选若有精确文本范围，应展示字符级高亮；若没有，结果卡片必须展示合同原文候选值，辅助用户在证据块内定位。
- 若证据块内存在多个候选且系统无法可靠归属，结果页应展示 `NOT_CONCLUDED` 和业务可读原因，不展示为通过或风险 finding。
- 编辑审核点配置后点击保存即对下一次新建审核任务生效。
- 配置页应提示“保存后仅影响后续新任务，不影响已创建任务和历史结果”。
- 历史任务结果页读取 ReviewResultSnapshot，不随当前配置页变更而改变。
- 历史任务结果页展示审核点名称、展示编码、审核分类、排序和合同类型时，必须使用快照中的当次值，不读取当前配置页最新值。
- `SYS-*` 系统诊断不在该页面配置为业务级别。
- 普通结果页只展示本次 execution 实际启用并进入执行计划的审核点。
- 被配置停用的审核点不在普通结果页输出 `SKIPPED` 或 `NOT_CONCLUDED`；管理台任务详情或评测报告可展示“因配置停用未执行”。
- 管理台和评测报告展示 `disabledByConfigCount` 和配置停用审核点清单；普通结果页主摘要不展示该计数。
- MVP 验收视图应能识别 core 审核点是否被人为停用；手动停用 core 点不能计入覆盖率达标。

### 管理台上传入口

MVP 上传入口仅支持 `.docx`。

上传区域必须明显标注：

```text
当前仅支持 DOCX，DOC 待后续开发。
```

若用户选择 `.doc` 或其他非 `.docx` 文件，前端应阻止提交并显示业务化提示；后端仍需在任务创建 API 层校验文件类型，避免绕过前端限制。

### 管理台发布页

候选发布流程：

```text
DRAFT
-> EVALUATING
-> READY_FOR_REVIEW
-> APPROVED / REJECTED
-> PUBLISHED
```

`READY_FOR_REVIEW` 页面必须并列展示：

- 当前版本与候选版本。
- 样本集。
- 核心点指标。
- 预算相关 SYS。
- 误报/漏报摘要。
- 变更模块。

批准动作生成新的版本快照，不允许直接修改当前生产版本。

### 管理台诊断展示

管理台可展示：

- 完整内部诊断码。
- EvidenceSlot。
- CandidateResolver。
- Gemma artifact。
- token 预算信息。
- redacted model artifact 摘要。
- schema validation errors。
- artifact anchors。

MVP 不保存也不提供 `redactedRawOutput` 展开；受控保存完整 redacted raw output、TTL 和审计展开延后。

## 权限边界

- 普通结果 URL 不暴露 prompt、raw output、endpoint、stack trace、admin logs。
- MVP 管理台暂不做平台内登录、账号体系和权限矩阵；访问控制由部署环境、内网、VPN、反向代理或外围系统承担。
- 管理台常规页面不展示完整 prompt、完整模型 raw output、endpoint secret、stack trace、完整合同敏感调试包或密钥。
- 规则/prompt/pattern 发布治理延后到 Pilot / Production Readiness；MVP 不支持自动发布，也不支持 AI 建议直接改生产配置。
- 受控敏感诊断导出、`encryptedRawOutput` 展开、角色权限和审批审计延后到 Pilot / Production Readiness。

## 待确认

- 前端框架、路由、组件库和设计系统。
- Pilot / Production Readiness 的登录方案、角色权限、敏感诊断导出审批和审计矩阵。
- 人工反馈入口是否进入 V1 MVP。
