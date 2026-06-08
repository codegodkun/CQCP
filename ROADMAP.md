# ROADMAP.md

来源：架构文档的一期范围、一期成功标准、后续阶段和当前决策摘要。

## V1: 一期

目标：企业内部低频、高质量审核，重点跑通单份中文 Word 合同的审核质量。

V1 愿景不等于 MVP。V1 可以描述一期希望覆盖的完整能力，但第一轮 MVP 只用于跑通一条最小、可解释、可验证的审核闭环；试点可用能力和生产治理能力需要分层进入，避免把治理平台、发布审批和外部系统深度集成提前压入第一条开发链路。

范围：

- 中文 `.docx` / `.doc` 合同。
- 6 万字左右长合同。
- 合同正文、附件、表格、标题、目录、Word 控件、复选框、单选框。
- 外部系统或管理台提供结构化字段。
- 工程合同、材料供货合同、费用合同、杂项合同粗分类。
- 规则/正则/Gemma 混合路由。
- 本地 A30/Gemma 生产辅助。
- 公网模型用于离线质量优化，不直接决定生产 finding。
- 异步任务，单 worker 顺序处理。
- Task、Execution、Result URL、ReviewResultSnapshot。
- 普通结果页左侧合同原文、右侧审核点，点击右侧审核点定位左侧证据。
- 管理台必要能力：任务、阶段日志、解析质量、审核计划、EvidencePacket 摘要、Gemma 调用状态、规则/词库/正则版本、失败样本、候选优化、管理员批准发布。
- 质量治理、样本集、紧急变更、审计记录。

成功标准摘录：

- Word 合同能稳定解析。
- 表格、附件区域、复选框/单选框能进入证据模型。
- 结构化字段与全文多处候选能比较。
- 金额、税额、比例、主体、日期等基础规则可解释。
- Gemma 只处理局部 EvidencePacket。
- EvidenceBundle / EvidencePacket 有 token 预算、复用策略和 coverageStatus。
- 无证据不生成业务 finding。
- 结果页能从右侧审核点定位左侧原文。
- 失败样本能进入质量工作台。
- 公网模型只生成候选优化，不直接上线。

试点门槛：

- core review point concluded rate >= 80%。
- overall executable point concluded rate >= 70%。
- system-caused NOT_CONCLUDED rate <= 20% for core points。
- 100% NOT_CONCLUDED points 有业务可读原因和 source/coverage explanation。

## V1 分层

### MVP：第一条可跑通审核闭环

目标是跑通单份中文 Word 合同的最小审核闭环。

当前规划基线来自 `TASK-001-v1-mvp-scope-gate`，最终仍待人工确认。

MVP 重点：

- 一个明确入口。`TASK-002-entry-order-decision` 已确认 MVP 入口顺序采用 API-first + 简易管理台，详见 `ADR-004-mvp-entry-order.md`。
- 单份 `.docx` 中文合同输入；`.doc` 不进入第一轮 MVP，管理台上传界面必须明显标注“当前仅支持 DOCX，DOC 待后续开发”。
- 最小结构化字段输入。
- 管理台可直接录入首批结构化字段；采用通用字段必填和按付款方式条件必填；SAP/API 传入字段与管理台字段定义保持一致；后台支持自定义新增字段，但自定义字段不自动生成审核点或业务裁判逻辑。
- 合同总金额语义固定为合同含税总金额，管理台显示“合同总金额（含税）”，税额公式直接复用该字段。
- MVP 币种固定为人民币 `CNY`，管理台金额单位显示“元”，外币合同延后。
- 节点付款其他节点信息仅在按节点付款时使用必填多行文本，仅记录和展示，暂不参与审核点裁判。
- 最小 Word 解析、候选索引和证据定位能力。
- 首批少量审核点族，优先从 `PARTY_FIELDS`、`AMOUNT_TAX`、`PAYMENT_TERMS` 中选择。`TASK-003-first-review-points-selection` 已形成首批选择基线，并记录到 `ADR-005-first-review-points-selection.md`，状态为 `Proposed`，最终仍待人工确认。
- 规则/正则优先；Gemma/A30 只做局部抽取、证据选择或复杂语义辅助。
- 后端统一合成 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`，并保持 `SYS-*` 与业务 finding 分流。
- 生成 `Task`、`Execution`、`ReviewResultSnapshot` 和结果 URL。
- 普通结果页能围绕“审核点 -> 证据 -> 原文定位”解释结果。

MVP 首批审核点基线：

- `PARTY_FIELDS`: `PARTY_A_NAME_CONSISTENCY`、`PARTY_B_NAME_CONSISTENCY`，均为 core review point。
- `AMOUNT_TAX`: `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` 为 core review point；`TAX_AMOUNT_FORMULA_CONSISTENCY` 为非 core，是否保留在 MVP 仍待人工确认。
- `PAYMENT_TERMS`: 月度付款的预付款、进度款、竣工款、结算款和质保款比例均纳入独立一致性审核，五点全部为 core review point。
- 首批审核点均以结构化字段、候选索引、EvidenceSlot、后端确定性裁判为主；Gemma/A30 只处理主体或付款比例候选归属歧义，不直接生成最终业务 finding。

MVP 不应包含：

- Quality Copilot。
- 完整质量治理工作台。
- 复杂发布审批流程。
- SAP/OA 深度 correction 联调。
- 生产保护能力、样本集 90 天过期阻断和临时运行审批。
- 完整审计体系和复杂权限治理。
- 自动候选优化、自动发布规则/prompt/pattern。

### Pilot：试点可用能力

目标是在受控内部试点中验证质量和操作流程。

Pilot 可逐步纳入：

- 更完整的管理台诊断展示。
- 失败样本记录和人工反馈入口。
- 首批样本集评测和回归样例。
- `requiresHigherBudget` 诊断和受控 correction execution。
- 规则集、词库、正则和预算 profile 的版本快照。
- SAP/OA/采购系统的最小联调或 contract test，是否进入 V1 末尾待确认。
- 首批延后审核点：签署页主体冲突、统一社会信用代码一致性、多主体关系、中文大写金额一致性、税率合法性、付款比例合计、付款节点顺序和付款条件完整性。

### Production Readiness：生产治理、审计、发布、保护能力

目标是支撑生产前的治理、审计和保护。

Production Readiness 可纳入：

- Quality Copilot 授权边界和候选优化流程。
- 完整候选发布审批流程。
- 样本集生命周期、过期保护、临时运行审批。
- 质量治理状态、紧急变更、恢复流程和生产保护。
- 完整审计日志、敏感输出审批、脱敏测试和备份恢复演练。
- 生产 canary、fault injection、模型 circuit breaker 指标和运维告警。

## V1 建议里程碑

以下里程碑只按架构文档中的能力顺序拆分，不代表最终排期。

1. 项目基础与接口边界
   - 确认正式技术栈、目录结构、数据库迁移策略和环境边界。
   - 定义 Task / Execution / Result URL 的最小 API 契约。
   - 定义 `SYS-*` 与业务 finding 分流的结果模型边界。

2. Word-first 解析与证据模型
   - MVP 支持中文 `.docx` 输入；`.doc` 延后到后续开发。
   - 解析正文、附件、表格、标题、目录、Word 控件、复选框和单选框。
   - 产出 `ContractDocument`、`DocumentBlock`、`TableBlock`、`FormControlBlock` 和 `ParseQualityReport`。
   - 保留 `contextType`、`sourceOrigin`、`sourceExtractionMode` 和置信度。

3. 候选索引与审核计划
   - 构建金额、比例、主体、日期、付款条款、标题、目录、附件、敏感信息和控件索引。
   - 确认 `ContractTypeProfile` 作为规则集路由维度。
   - 生成 `ReviewExecutionPlan`。

4. 证据槽、预算和混合路由
   - 实现 CandidateResolver 的保守归属原则。
   - 实现 EvidenceSlot Preflight，证据不足输出 `SYS-*`。
   - 实现 `FamilyEvidencePlan`、`EvidenceBundle`、`PointEvidenceOverlay` 和 token budget。
   - 规则/正则优先，Gemma 只做局部辅助。

5. 异步任务、快照和结果页
   - 实现单 worker 顺序处理。
   - 记录阶段日志和 stage timeout。
   - 生成 `ReviewResultSnapshot`。
   - 普通结果页支持左侧原文、右侧审核点、点击定位证据。

6. 管理台和质量治理
   - 展示任务、阶段日志、解析质量、审核计划、EvidencePacket 摘要和 Gemma 状态。
   - 管理规则集、字段词库、正则库版本。
   - 支持失败样本、候选优化、管理员批准发布。
   - 治理样本集、紧急变更、审计记录和生产保护状态。

## V1 验证方向

架构文档要求一期进入实施前准备自动化或半自动化验证样例，覆盖：

- `FamilyEvidencePlan`：slot matrix、coverage、预算截断、跨 shard 去重。
- `requiresHigherBudget`：STANDARD 预算不足时的 `NOT_CONCLUDED` 和 DEEP_REVIEW 建议。
- `SYS-MODEL-CONFLICT`：primary/supplement artifact 矛盾时不生成业务 finding。
- resolver/preflight：MEDIUM、CONFLICTED、LOW、UNKNOWN role 的模型辅助资格和 SYS 行为。
- parser provenance：`contextType/sourceOrigin/sourceExtractionMode` 贯穿索引、排序和 resolver。
- required clause absence：`PROVEN_REQUIRED_CLAUSE_ABSENT` 与 `coverageProofSummary`。
- execution correction：同一 task 下新 execution、superseded 字段和历史查询。
- redaction：redacted raw output 不暴露个人敏感信息。
- caller policy authorization：能力扩大、紧急停用和恢复审批。
- dataset expiry guardrail：90 天过期保护、临时 approval 和 409 contract test。
- fault injection：endpoint timeout、503、invalid JSON、conflicting supplement。
- EvidenceRankScore sensitivity：固定权重扰动下核心候选稳定性。

## V2: 二期可考虑

- PDFBox/Docling/PaddleOCR 支持 PDF/OCR 输入。
- 多 worker 并行。
- 阶段级 worker pool。
- GPU/Gemma 优先级队列。
- 更完整的质量工作台。
- 规则/prompt A/B 评测。
- 更细粒度权限。
- 正式 SAP 联调增强。

## V3: 三期可考虑

- 独立 worker service。
- MQ。
- 分布式部署。
- 更强观测和审计。
- 复杂人工复核闭环。

## 暂不纳入 V1

- PDF/OCR 作为主输入能力。
- 大规模多合同并行调度。
- RabbitMQ/Kafka 或分布式 worker。
- 多租户。
- 复杂企业 IAM。
- 开放式聊天问答审合同。
- 自动发布 prompt、正则、规则。
- 复杂 BI 报表。
- 全文向量 RAG 主链路。

## 待确认

- V1 内部里程碑拆分。
- 首批审核点是否按 `ADR-005` 确认，以及首批合同类型优先级。
- 试点样本集数量、来源和授权。
- SAP/OA 联调是否进入 V1 末尾或 V2。
- V1 是否先以单一合同类型试点，还是四类粗分类同时进入。
- 普通结果 URL 是内网公开令牌访问，还是必须登录。
- 最小任务创建 API 的请求字段、错误码和访问控制。
- 首批结构化字段的最终英文 key、数值精度、枚举编码、校验错误码和自定义字段权限。
