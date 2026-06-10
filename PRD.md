# Contract Quality Control Platform V2 PRD

版本：v0.1

日期：2026-06-07

状态：Draft for planning review

来源：

- `2026-06-06-contract-quality-control-platform-v2-design.md`
- `PROJECT_BRIEF.md`
- `CURRENT_CONTEXT.md`
- `ROADMAP.md`
- `docs/*.md`
- `decisions/ADR-001` 至 `ADR-005`

## 1. Problem Statement

企业内部合同审核需要在长篇中文 Word 合同中稳定识别主体、金额、税额、比例、付款条款等关键信息，并把审核结果以可解释、可定位、可追溯的方式提供给 SAP、OA、采购系统和人工审批人参考。

当前项目要解决的问题不是“让模型一次性读完整份合同并给出审批结论”，而是建立一个合同质量控制中台：

- 能接收外部系统或管理台发起的审核任务。
- 能解析中文 Word 合同，保留证据来源和定位信息。
- 能构建候选索引和审核点计划。
- 能用规则、正则、本地模型局部辅助和后端确定性裁判形成结果。
- 能在证据不足、解析低置信、模型异常、预算不足或候选冲突时明确输出系统诊断，而不是伪造业务风险。
- 能生成普通结果 URL，围绕“审核点 -> 证据 -> 原文定位”解释结果。
- 能通过版本化、快照、样本评测、审计和治理流程支撑后续质量改进。

平台定位为质量控制辅助，不批准、不拒绝、不阻断、不修改审批流。人工审批人仍负责最终业务判断。

## 2. Solution

构建 Contract Quality Control Platform / 合同质量控制中台 V2。平台以 Word-first 合同解析为起点，围绕统一审核链路处理所有入口请求。

核心方案：

1. API-first + 简易管理台作为 MVP 入口顺序。
2. 所有入口进入同一套审核链路，不创建 SAP-only、OA-only 或管理台-only 审核路径。
3. 异步创建 Task、Execution、Result URL 和阶段状态。
4. 解析中文 Word 合同，产出合同证据模型、候选索引和解析质量报告。
5. 根据合同类型画像、结构化字段和规则集生成 ReviewExecutionPlan。
6. 使用 CandidateResolver 和 EvidenceSlot Preflight 判断证据是否足够。
7. 同一 ReviewPointFamily 共享 EvidenceBundle，再按审核点叠加 PointEvidenceOverlay。
8. 规则/正则优先；Gemma/A30 只做局部抽取、证据选择或复杂语义辅助。
9. 后端统一做结构化比对、公式裁判、确定性最终裁判和结果合成。
10. 生成不可变 ReviewResultSnapshot。
11. 普通结果页展示业务化结果、未结论原因和原文定位。
12. 管理台展示阶段日志、解析质量、EvidencePacket 摘要、Gemma 调用状态、诊断和治理信息。
13. 质量治理链路沉淀失败样本、候选优化、离线评测、管理员批准发布和审计记录。

## 3. Product Principles

- 证据不足不生成业务 finding。
- `SYS-*` 系统诊断必须和业务风险 finding 分流。
- Gemma/A30 不吃整份合同，不一次性裁判全部审核点。
- 结构化比对、公式校验和确定性最终裁判由后端完成。
- 规则、正则、prompt、模型、合同类型画像、EvidenceSelector 和预算 profile 必须版本化。
- 普通结果页不展示模型原始输出、prompt、endpoint、stack trace 或 admin logs。
- `NOT_CONCLUDED` 表示系统没有足够依据，不表示合同无风险。
- 平台只提供风险提示、证据和审核结果 URL，不改变审批流。
- 当前仓库不默认继承旧 `ai-contract-review` 项目代码、目录、页面、任务板、历史规则或样本。

## 4. Users And Actors

1. 外部系统调用方：SAP、OA、采购系统或其他业务系统，通过 API 提交合同审核任务并轮询状态或读取结果 URL。
2. 管理台内部用户：通过简易管理台提交 Word 合同、录入结构化字段、选择合同类型并查看任务状态。
3. 质量验证用户：运行评测任务，检查样本集、规则集、预算和模型辅助效果。
4. 管理员：维护规则、词库、正则、prompt、预算 profile、caller policy 和候选发布流程。
5. P0 / 质量平台负责人：参与生产保护、紧急变更、架构复审和高风险治理动作。
6. 人工审批人：在 SAP/OA/线下审批流程中参考审核结果，但不把平台输出视为审批决定。
7. 集成运维联系人：处理 SAP/OA caller policy、错误码、队列过载、规则集不可用和接口兼容问题。

待确认：Pilot / Production Readiness 的正式组织角色名称、账号体系、权限矩阵和人员归属；MVP 管理台暂不做平台内登录和权限矩阵。

## 5. Scope

### 5.1 V1 Vision

V1 目标是企业内部低频、高质量审核，重点跑通单份中文 Word 合同审核质量。

V1 支持方向：

- 中文 `.docx` / `.doc` 合同。
- 6 万字左右长合同。
- 合同正文、附件、表格、标题、目录、Word 控件、复选框、单选框。
- 外部系统或管理台提供结构化字段。
- 工程合同、材料供货合同、费用合同、杂项合同粗分类。
- 规则/正则/Gemma 混合路由。
- 本地模型生产辅助，默认通过本地 OpenAI-compatible 模型服务接入；当前目标硬件为 A30 24GB，但具体模型与硬件组合不写死在 PRD。
- 公网模型仅用于离线质量优化，不直接决定生产 finding。
- 异步任务，单 worker 顺序处理。
- Task、Execution、Result URL、ReviewResultSnapshot。
- 普通结果页左侧合同原文、右侧审核点，点击审核点定位证据。
- 管理台必要诊断能力和质量治理能力。

### 5.2 MVP

MVP 只用于跑通第一条最小、可解释、可验证的审核闭环。

MVP 冻结基线：

- API-first + 简易管理台。
- 单份 `.docx` 中文合同输入。
- MVP 不支持 `.doc` 上传；管理台上传界面必须明显标注“当前仅支持 DOCX，DOC 待后续开发”。
- 最小结构化字段输入。
- 最小 Word 解析、候选索引和证据定位能力。
- DefinitionTermIndex：识别“本合同所称……”等定义性条款，解析阶段与 CandidateIndex 并行构建；按审核点按需注入，不默认注入每个 EvidenceBundle。
- 首批审核点族：`PARTY_FIELDS`、`AMOUNT_TAX`、`PAYMENT_TERMS`。
- 规则/正则优先；Gemma/A30 只做局部辅助。
- 后端统一合成点级状态。
- 生成 Task、Execution、ReviewResultSnapshot 和 Result URL。
- 普通结果页能解释审核点、证据和原文定位。

MVP 首批审核点基线来自已 Accepted 的 `ADR-005`。MVP 固定采用以下 9 个 core review point：

| ReviewPointFamily | 审核点 | core | MVP 状态 |
| --- | --- | --- | --- |
| `PARTY_FIELDS` | `PARTY_A_NAME_CONSISTENCY` | 是 | 已确认纳入 MVP |
| `PARTY_FIELDS` | `PARTY_B_NAME_CONSISTENCY` | 是 | 已确认纳入 MVP |
| `AMOUNT_TAX` | `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` | 是 | 已确认纳入 MVP |
| `AMOUNT_TAX` | `TAX_AMOUNT_FORMULA_CONSISTENCY` | 是 | 已确认纳入 MVP |
| `PAYMENT_TERMS` | `PREPAYMENT_RATIO_CONSISTENCY` | 是 | 已确认纳入 MVP |
| `PAYMENT_TERMS` | `PROGRESS_PAYMENT_RATIO_CONSISTENCY` | 是 | 已确认纳入 MVP |
| `PAYMENT_TERMS` | `COMPLETION_PAYMENT_RATIO_CONSISTENCY` | 是 | 已确认纳入 MVP |
| `PAYMENT_TERMS` | `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY` | 是 | 已确认纳入 MVP |
| `PAYMENT_TERMS` | `WARRANTY_RETENTION_RATIO_CONSISTENCY` | 是 | 已确认纳入 MVP |

MVP 首批合同类型只做“工程采购合同”。管理台合同类型字段 MVP 仅展示“工程采购合同”，作为后续多合同分类预留字段；首批 9 个审核点默认适用于工程采购合同。材料供货合同、费用合同、杂项合同延后到 Pilot 或后续阶段。系统可保留合同类型扩展结构，但 MVP 不做复杂合同类型路由。

MVP 首批结构化字段已确认如下，采用“通用字段必填 + 按付款方式条件必填”。管理台可以直接录入这些字段，SAP/API 传入字段与管理台字段定义保持一致，并使用同一字段配置。后台支持自定义新增字段；新增字段可用于合同录入、SAP/API 传入、展示和后续规则配置，但不得自动成为审核点、EvidenceSlot 或业务裁判逻辑。

| 序号 | 中文字段 | 建议字段 key | 说明 |
| --- | --- | --- | --- |
| 1 | 合同名称 | `contractName` | 任务展示、结果页摘要和合同识别 |
| 2 | 甲方名称 | `partyAName` | 主体一致性审核 |
| 3 | 乙方名称 | `partyBName` | 主体一致性审核 |
| 4 | 项目名称 | `projectName` | 任务展示、候选排序和上下文辅助 |
| 5 | 合同总金额（含税） | `contractTotalAmount` | 语义固定为合同含税总金额，用于金额一致性和税额公式审核 |
| 6 | 合同不含税金额 | `taxExcludedAmount` | 税额公式审核 |
| 7 | 合同税额 | `taxAmount` | 税额公式审核 |
| 8 | 税率 | `taxRate` | 税额公式诊断；新增固定 `CandidateRole=taxRate` |
| 9 | 计价模式 | `pricingMode` | 枚举：固定总价、暂定总价 |
| 10 | 付款方式 | `paymentMethod` | 枚举：按月度付款、按节点付款 |
| 11 | 预付款比例 | `prepaymentRatio` | 两种付款方式均必填；节点付款下该阶段固定 |
| 12 | 进度款比例 | `progressPaymentRatio` | 仅按月度付款时必填；按节点付款时隐藏且不提交 |
| 13 | 竣工款比例 | `completionPaymentRatio` | 仅按月度付款时必填；按节点付款时隐藏且不提交 |
| 14 | 结算款比例 | `settlementPaymentRatio` | 仅按月度付款时必填；按节点付款时隐藏且不提交 |
| 15 | 质保款比例 | `warrantyRetentionRatio` | 仅按月度付款时必填；按节点付款时隐藏且不提交 |
| 16 | 节点付款其他节点信息 | `milestonePaymentTerms` | 仅按节点付款时为必填多行文本；按月度付款时隐藏且不提交；仅记录和展示 |
| 17 | 发票类型 | `invoiceType` | 枚举：增值税普通发票、增值税专用发票 |

MVP 首批结构化字段采用冻结最小契约：字段 key 以表中建议字段 key 作为 OpenAPI、数据库和结果快照字段名；中文展示文案由前端和结果解释层维护，不进入 code 本身。

MVP 枚举编码固定如下：

- `pricingMode`: `FIXED_TOTAL_PRICE`（固定总价）、`PROVISIONAL_TOTAL_PRICE`（暂定总价）。
- `paymentMethod`: `MONTHLY`（按月度付款）、`MILESTONE`（按节点付款）。
- `invoiceType`: `VAT_GENERAL`（增值税普通发票）、`VAT_SPECIAL`（增值税专用发票）。

MVP 创建任务最小校验错误码固定为：

- `REQUIRED_FIELD_MISSING`
- `CONDITIONAL_FIELD_MISSING`
- `INVALID_ENUM_VALUE`
- `INVALID_DECIMAL_SCALE`
- `INVALID_PERCENT_RANGE`
- `UNSUPPORTED_CURRENCY`
- `UNSUPPORTED_FILE_TYPE`

MVP 币种固定为人民币 `CNY`。管理台不提供币种选择，所有金额控件明确显示单位“元”。SAP/API 可省略 `currency`；若显式传入，只允许 `CNY`。外币合同不进入 MVP。

### 5.3 Pilot

Pilot 用于受控内部试点，逐步纳入：

- 更完整的管理台诊断展示。
- 失败样本记录和人工反馈入口。
- 首批样本集评测和回归样例。
- `requiresHigherBudget` / `recommendedBudgetProfile` 诊断在管理台、评测报告和 AI 调优包中展示；普通结果页的“申请深度审核”和受控 correction execution 延后。
- 规则集、词库、正则和预算 profile 的版本快照。
- SAP/OA/采购系统最小联调或 contract test，是否进入 V1 末尾待确认。
- 延后审核点：签署页主体冲突、统一社会信用代码一致性、多主体关系、中文大写金额一致性、税率合法性、付款比例合计、付款节点顺序和付款条件完整性。

Pilot 门槛：

- core review point concluded rate >= 80%。
- overall executable point concluded rate >= 70%。
- system-caused NOT_CONCLUDED rate <= 20% for core points。
- 100% NOT_CONCLUDED points 有业务可读原因和 source/coverage explanation。

### 5.4 Production Readiness

Production Readiness 用于生产前治理、审计、保护和发布能力：

- Quality Copilot 授权边界和候选优化流程。
- 完整候选发布审批流程。
- 样本集生命周期、过期保护、临时运行审批。
- 质量治理状态、紧急变更、恢复流程和生产保护。
- 完整审计日志、敏感输出审批、脱敏测试和备份恢复演练。
- 生产 canary、fault injection、模型 circuit breaker 指标和运维告警。

## 6. Out Of Scope

V1 / MVP 不包含：

- PDF/OCR 作为用户主输入能力。
- 大规模多合同并行调度。
- RabbitMQ/Kafka 或分布式 worker。
- 多租户。
- 复杂企业 IAM。
- 开放式聊天问答审合同。
- 全文向量 RAG 主链路。
- 复杂 BI 报表。
- 自动发布 prompt、正则、规则。
- SAP/OA 深度 correction 联调进入第一轮 MVP。
- 完整管理台、完整权限治理或完整发布审批进入第一轮 MVP。
- 通过公网模型直接处理真实生产合同主链路或决定生产 finding。
- 从旧 `ai-contract-review` 项目默认迁入代码、规则、样本或页面。

## 7. User Stories

1. 作为外部系统调用方，我想通过 API 提交合同审核任务，以便业务系统能获得审核结果 URL。
2. 作为外部系统调用方，我想任务创建后立即拿到 `taskId`、`executionId`、`status` 和 `resultUrl`，以便异步轮询或展示入口。
3. 作为外部系统调用方，我想外部状态保持有限集合，以便 SAP/OA 不依赖内部阶段细节。
4. 作为外部系统调用方，我想内部 `SYS-*` 被映射为业务化 `NOT_CONCLUDED`，以便不把系统诊断误当作业务风险。
5. 作为外部系统调用方，我想在规则集不可用时收到明确的 `409 RULE_SET_NOT_AVAILABLE`，以便停止自动重试并通知运维。
6. 作为外部系统调用方，我想在已有 execution 处理中收到 `409 TASK_EXECUTION_IN_PROGRESS`，以便继续轮询当前执行而不是重复创建。
7. 作为外部系统调用方，我想 correction execution 有独立接口和 reason code，以便合同类型修正或预算升级不会覆盖历史结果。
8. 作为管理台内部用户，我想通过简易管理台发起审核任务，以便在无正式 SAP/OA 联调时验证 MVP 链路。
9. 作为管理台内部用户，我想简易管理台调用同一套任务创建 API，以便管理台和外部系统不形成两套审核路径。
10. 作为管理台内部用户，我想录入结构化字段，以便系统能执行主体、金额、税额和付款比例一致性审核。
11. 作为管理台内部用户，我想选择或确认合同类型，以便规则集能按合同类型画像路由。
12. 作为管理台内部用户，我想查看任务阶段日志，以便知道任务卡在解析、索引、模型还是合成阶段。
13. 作为管理台内部用户，我想查看 ParseQualityReport，以便识别解析低置信导致的未结论点。
14. 作为管理台内部用户，我想查看 EvidencePacket 摘要，以便理解审核点为什么有或没有足够证据。
15. 作为管理台内部用户，我想查看 Gemma 调用状态，以便知道哪些点使用了局部模型辅助。
16. 作为管理台内部用户，我想查看冲突证据对比，以便辅助人工判断系统无法裁判的点。
17. 作为管理台内部用户，我想查看同一 task 下的 execution history，以便比较修正前后的审核结果。
18. 作为人工审批人，我想通过普通结果 URL 查看合同原文和审核点结果，以便把平台输出作为审批参考。
19. 作为人工审批人，我想点击审核点后定位到左侧原文证据，以便快速核对风险提示。
20. 作为人工审批人，我想看到业务化的未结论原因，以便知道哪些事项需要人工确认。
21. 作为人工审批人，我想看到“关键证据覆盖”这类摘要，以便理解本次审核结果的完整度。
22. 作为人工审批人，我想明确知道 `PASS/WARNING/ERROR` 不是审批决定，以便避免误用平台结果。
23. 作为人工审批人，我想 `NOT_CONCLUDED` 不被解释为无风险，以便继续人工核查未结论事项。
24. 作为质量验证用户，我想运行样本集评测，以便判断 core review point concluded rate 是否达到试点门槛。
25. 作为质量验证用户，我想区分真实样本和合成样本指标，以便不让合成样本掩盖真实合同覆盖不足。
26. 作为质量验证用户，我想验证长合同、复杂表格、合并单元格、控件、缺条款和冲突证据场景，以便评估解析和证据链路稳定性。
27. 作为质量验证用户，我想记录 token 分布、模型调用次数和预算相关 `SYS-*` 比率，以便判断预算 profile 是否可用。
28. 作为质量验证用户，我想验证模型输出 schema validation，以便模型异常不会进入业务裁判。
29. 作为管理员，我想维护规则、词库、正则和 prompt 版本，以便生产结果可追溯。
30. 作为管理员，我想候选规则或 prompt 只能经过离线评测和批准后发布，以便避免自动上线风险。
31. 作为管理员，我想看到失败样本和候选优化建议，以便持续改进审核质量。
32. 作为管理员，我想 Quality Copilot 只生成候选优化，不直接修改生产规则，以便保持发布治理边界。
33. 作为管理员，我想查看 redacted model artifact 摘要，以便排查模型辅助状态，同时避免暴露完整原始输出。
34. 作为 P0 / 质量平台负责人，我想样本集过期后触发生产保护，以便避免长期未评测规则继续处理新任务。
35. 作为 P0 / 质量平台负责人，我想紧急变更有 TTL、审批人、rollback plan 和审计，以便临时措施不会变成永久绕过。
36. 作为集成运维联系人，我想 caller policy 明确允许哪些能力，以便控制类型建议、correction execution 和预算 profile。
37. 作为集成运维联系人，我想 queue overload 有明确错误或降级策略，以便外部系统能正确处理等待或拒绝。
38. 作为平台开发者，我想 Task、Execution、ReviewResultSnapshot 分层，以便重跑不覆盖历史结果。
39. 作为平台开发者，我想每次快照绑定 parser、规则、词库、prompt、schema、model 和预算版本，以便历史结果可解释。
40. 作为平台开发者，我想 CandidateIndex 保留 context/source provenance，以便 EvidenceRankScore 和 CandidateResolver 能解释选择原因。
41. 作为平台开发者，我想 EvidenceSlot Preflight 在规则执行前检查 required slot，以便缺证据时不会硬判业务风险。
42. 作为平台开发者，我想 FamilyEvidencePlan 共享同族证据，以便多个审核点不重复构造大模型上下文。
43. 作为平台开发者，我想 Gemma artifact 可在同 execution 内复用，以便减少重复模型调用。
44. 作为平台开发者，我想模型 circuit breaker 按 endpoint 和 model version 生效，以便模型异常时快速降级为 `SYS-MODEL-UNAVAILABLE`。
45. 作为平台开发者，我想 stage timeout 和 retry 记录可追溯，以便诊断任务失败和性能问题。

## 8. Functional Requirements

### 8.1 Unified Entry And Task Creation

- 系统必须提供统一审核入口模型，支持外部 API、简易管理台和后续评测入口。
- MVP 入口顺序采用 API-first + 简易管理台。
- 简易管理台不得拥有独立审核链路。
- 任务创建必须异步返回 `taskId`、`executionId`、`status=QUEUED` 和 `resultUrl`。
- Result URL 可以提前生成，但正式结果快照必须在审核完成、部分完成或失败后生成。
- MVP 普通结果 URL 不做平台侧登录、公开令牌或独立访问控制。外围系统负责对 URL 进行编码、加密、分发和访问控制，平台暂不在该方向投入额外能力。
- 平台仍必须保证普通结果 URL 不暴露 prompt、raw output、endpoint、stack trace、admin logs、secret 或管理台诊断详情。
- 同一 `taskId` 下同时只能有一个 non-terminal execution。
- MVP 任务创建输入必须能承载首批结构化字段清单。
- 通用字段始终必填；SAP/API 传入的结构化字段必须与管理台字段配置使用同一字段 key、类型、条件必填规则、枚举和启用状态。
- `contractTotalAmount` 的业务语义固定为合同含税总金额；管理台标签必须显示“合同总金额（含税）”，SAP/API 不再通过额外标志解释其含税属性。
- MVP 平台常量 `currency=CNY`，不作为管理台录入字段。SAP/API 传入非 `CNY` 时拒绝创建任务。
- CNY 金额字段使用十进制定点数并保留两位小数，不得使用二进制浮点数参与持久化或确定性裁判。
- 税率和付款比例统一使用百分比数值存储与传输，例如 `13` 表示 `13%`、`20` 表示 `20%`，不得使用 `0.13` 表示 `13%`。管理台、SAP/API、数据库和结果快照采用同一口径；后端参与金额计算时统一除以 `100`。
- 比例字段合法范围为 `0` 至 `100`。税率最多保留 4 位小数，付款比例最多保留 2 位小数；末尾零可省略。超出精度的输入必须拒绝，不得静默四舍五入或截断。
- 税额公式采用两层裁判：`contractTotalAmount = taxExcludedAmount + taxAmount` 为强校验，按金额保留两位小数后允许绝对误差不超过 `0.01` 元；`taxAmount = taxExcludedAmount × taxRate` 在 MVP 仅作为 WARNING 校验，不直接判定 ERROR，以兼容分项计税、多税率和逐行舍入差异。
- 税额公式的最终裁判由后端完成；Gemma/A30 只可辅助抽取金额和税率，不得直接给出公式一致性结论。
- `paymentMethod=MONTHLY` 时，`prepaymentRatio`、`progressPaymentRatio`、`completionPaymentRatio`、`settlementPaymentRatio`、`warrantyRetentionRatio` 必填，`milestonePaymentTerms` 不适用且不提交。
- 月度付款的预付款、进度款、竣工款和结算款比例采用累计付款口径：进度款包含预付款，竣工款包含进度款和预付款，结算款包含竣工款、进度款和预付款。
- 月度付款比例的业务口径为 `prepaymentRatio ≤ progressPaymentRatio ≤ completionPaymentRatio ≤ settlementPaymentRatio`，且 `settlementPaymentRatio + warrantyRetentionRatio = 100`。MVP 不对这两条跨字段关系增加输入拦截或业务 finding；外围系统负责其输入校验，管理台由测试人员控制数据质量。单字段范围、精度和条件必填校验仍保留。
- 月度付款的预付款、进度款、竣工款、结算款和质保款比例均作为独立一致性审核点，分别将结构化比例与合同付款条款候选比较，并分别展示状态、证据与原文定位。
- 五项比例审核共享付款条款局部 EvidencePacket，但各自拥有独立 required EvidenceSlot。证据缺失、角色冲突或语义歧义时输出 `SYS-* / NOT_CONCLUDED`，不得生成无可靠证据的业务 finding。
- `paymentMethod=MILESTONE` 时，`prepaymentRatio` 和 `milestonePaymentTerms` 必填；四个月度专属比例不适用、隐藏且不提交。
- 节点付款仍执行 `PREPAYMENT_RATIO_CONSISTENCY`；`PROGRESS_PAYMENT_RATIO_CONSISTENCY`、`COMPLETION_PAYMENT_RATIO_CONSISTENCY`、`SETTLEMENT_PAYMENT_RATIO_CONSISTENCY` 和 `WARRANTY_RETENTION_RATIO_CONSISTENCY` 因不适用而输出 `SKIPPED`，不得输出 `SYS-* / NOT_CONCLUDED` 或业务 finding。
- `milestonePaymentTerms` 只进入记录、快照和展示，不进入 ReviewExecutionPlan 的审核点依赖，不参与 EvidenceSlot 或业务裁判。
- 具体字段 key、枚举编码和错误码需在最小 OpenAPI 契约任务中确认。

### 8.2 Word-first Parsing

- 系统必须支持 Word-first 文档解析。
- `.docx` 为主解析方向。
- MVP 仅支持 `.docx` 用户输入。
- `.doc` 延后到后续开发，不进入第一轮 MVP；若后续支持，应通过 LibreOffice 转换链路和定位降级验收。
- PDF 可作为 Word 转换后的内部预览形态，不作为 V1 用户主输入能力。
- 解析产物必须支持正文、表格、附件、标题、目录、Word 控件、复选框、单选框。
- 解析产物必须保留 `contextType`、`sourceOrigin`、`sourceExtractionMode`、block/table/control/region confidence。
- `PREVIEW_ONLY` 不得进入业务裁判。
- `TEXT_FALLBACK` 不得生成精确文本范围，不得单独支持 HIGH role、确定性 ERROR 或 `PROVEN_REQUIRED_CLAUSE_ABSENT`。

### 8.3 Contract Type Profile

- 合同类型画像是规则集路由维度，不是独立系统。
- 一期合同类型包括 `ENGINEERING`、`MATERIAL_SUPPLY`、`SERVICE_FEE`、`MISC`。
- 合同类型来源优先级为外部系统传入 > 管理台人工选择 > 系统自动建议。
- 系统自动建议只作为 hint，不得无声覆盖外部或人工选择。
- 低置信分类应启用通用规则 + 保守补充规则。

### 8.4 Candidate Index And Resolver

- 系统必须构建金额、比例、主体、日期、付款条款、标题、目录、附件、敏感信息和控件索引。
- CandidateIndex 只承诺高召回候选，不承诺正确角色。
- RawCandidate 必须独立保存来源、原文、归一化值、valueType、附近标签、附近文本、section、region、table context、blockId 和 confidence signals。
- CandidateResolver 只在高置信场景做确定性归属。
- 只有 HIGH role 可直接进入确定性裁判。
- MEDIUM 只可触发模型辅助或候选展示。
- LOW、CONFLICTED、UNKNOWN 不得生成业务 finding。

### 8.5 Review Point Planning

- 每个审核点必须是可执行定义，不是单纯 prompt。
- ReviewPointDefinition 至少包含 code、name、category、applicableContractTypes、requiredStructuredFields、executionStrategy、evidencePolicy、candidateExtractionPolicy、deterministicRule、modelAssistPolicy、severityPolicy、sourceAnchorPolicy、evaluationPolicy。
- 管理后台应提供审核点配置列表，支持可视化维护审核点名称、编码、分类、启用状态、排序、依赖字段和默认严重级别。
- MVP 审核规则配置页只支持审核点列表展示、详情展开和有限编辑，不开放新增审核点能力；新增审核点延后到后续阶段。
- 审核点列表支持按审核分类、状态进行排序，并支持按审核点名称或编码搜索。
- 点击详情以下拉展开方式展示审核点编码、名称、审核分类、默认级别、启用状态、排序、依赖字段和合同类型。
- MVP 编辑区允许编辑审核点编码、名称、审核分类、默认级别、启用状态、排序、依赖字段和合同类型等配置字段；不允许通过管理台任意新增或改写执行策略、EvidenceSlot、后端裁判或模型辅助策略。
- 审核点编码是前台展示和人工定位用编码，例如 `R1-01`，可编辑但不能为空，且在当前配置中唯一。
- 系统内部 `reviewPointCode` 是唯一、不可编辑的主 key，例如 `PARTY_A_NAME_CONSISTENCY`。后端执行、EvidenceSlot、结果快照、API 和历史解释必须使用内部 `reviewPointCode`，不得使用前台展示编码作为执行逻辑主键。
- ReviewResultSnapshot 必须同时保存当次展示编码和内部 `reviewPointCode`。
- MVP 第一版审核执行逻辑由项目团队预置生成，通过 `ReviewPointDefinition`、EvidenceSlot、候选抽取规则、后端裁判规则和 Gemma 辅助策略固化，不由管理台用户生成。
- 审核点详情可只读展示业务可读的执行逻辑摘要，包括内部 code、执行策略名称、依赖字段、需要的证据类型、是否使用 Gemma 辅助、裁判说明和最近一次版本更新时间。
- MVP 普通配置页不展示完整正则、prompt、EvidenceSlot 细节或后端代码；这些内容延后到后续治理/技术视图。
- 审核分类只作为管理台展示、筛选和排序字段，不参与执行路由；实际执行逻辑由内部 `reviewPointCode` 和预置 execution strategy 决定。
- 若审核结果不理想，应按问题类型迭代规则、词库、ValueGrammar、EvidenceSlot policy、Gemma 局部 prompt、后端裁判或结果页解释；除严重级别外，不通过 MVP 管理台直接改写执行逻辑。
- 执行逻辑迭代必须版本化并通过脱敏/合成样本回归验证，新任务使用新版本，历史快照保持旧版本解释。
- 审核分类 MVP 至少支持 `结构化信息比对` 和 `合同内部一致性`，并保留后续扩展空间。
- 合同类型字段 MVP 仅有 `工程采购合同`，只是展示和后续更多合同分类预留字段，不参与复杂适用范围路由。首批 9 个审核点默认适用于工程采购合同。
- 排序字段只影响管理台列表和普通结果页展示顺序，不影响审核执行顺序。执行顺序由后端计划器根据依赖、证据复用和执行策略决定。
- 依赖字段以下拉多选方式展示全部已配置合同字段。
- 保存审核点配置时必须校验依赖字段：依赖字段必须来自已启用合同字段，且每个审核点必须保留其运行所需的 required structured fields。
- 若依赖字段配置会导致审核点不可执行，管理台必须拒绝保存并提示缺失的必需依赖字段；不得等到任务执行时才输出 `SYS-MISSING-INPUT`。
- 审核点严重级别只允许配置“明确业务不一致”时输出 `ERROR` 或 `WARNING`；`PASS`、`NOT_CONCLUDED`、`SKIPPED` 由适用性、证据和后端裁判决定，不允许手工配置。
- `ERROR` 和 `WARNING` 都属于业务 finding，但必须分级统计为 `errorCount` 和 `warningCount`。外部系统和结果页不得只用“是否存在 finding”替代严重级别判断。
- 结果摘要必须统计 `plannedPointCount`，作为本次启用并进入 ReviewExecutionPlan 的审核点总数。被配置停用的审核点不进入分母；进入计划后因适用性判定为 `SKIPPED` 的审核点进入分母。
- 结果摘要必须统计 `passCount`，用于展示明确通过的审核点数量；单独统计 `notConcludedCount`，用于提示审核覆盖不足；同时统计 `skippedCount`，用于说明审核点不适用。`PASS`、`NOT_CONCLUDED`、`SKIPPED` 和 `SYS-*` 不计入业务风险数量。默认级别只影响业务 finding 的严重程度，不影响是否生成 finding。
- 严重级别配置不得 hardcode 在业务裁判中。后端裁判先确定业务事实是否一致，再由当次 execution 绑定的有效 `severityPolicy` 决定不一致结果输出 `ERROR` 还是 `WARNING`。
- MVP 审核点配置采用保存后生效机制：管理员保存配置后，下一次新建审核任务使用最新配置；已经创建或正在执行的任务不受影响。
- 每次保存审核点配置都必须形成可追溯的配置版本或变更记录，并由新建 execution 绑定当次配置版本。
- 历史 ReviewResultSnapshot 必须保存当次执行使用的审核点配置和严重级别，不因后续管理台修改而变化。
- 审核点名称、展示编码、审核分类、排序和合同类型等展示字段变更不影响已有任务结果页；历史结果页必须读取快照中的当次展示字段。
- `SYS-*` 系统诊断不得配置成业务严重级别，也不得通过管理台改写为业务 finding。
- 停用的审核点不进入新建任务的 ReviewExecutionPlan，不输出 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`。普通结果页只展示本次实际启用并进入计划的审核点。
- ReviewResultSnapshot 必须记录本次有效审核点配置版本、启用审核点清单和被配置停用的审核点清单。管理台或评测报告可以展示“因配置停用未执行”。
- 管理台和评测报告应展示 `disabledByConfigCount` 和配置停用审核点清单；普通结果页主摘要不展示该计数，避免干扰审批人。
- MVP 验收基线下首批 9 个审核点默认全部启用；测试时手动停用 core 审核点，不能用于证明核心审核点覆盖率达标。
- 执行策略包括 `STRUCTURED_COMPARE`、`FORMULA_RULE`、`PATTERN_EXTRACT_THEN_COMPARE`、`LLM_EXTRACT_THEN_RULE`、`LLM_SEMANTIC_WITH_GUARD`。
- 明确公式和数值关系必须优先走后端规则。
- 正则/词库能召回候选时，优先候选抽取 + 后端比较。
- 候选存在但语义归属不清时，Gemma 只做局部辅助。

### 8.6 EvidenceSlot And EvidencePacket

- 每个审核点必须声明最小 EvidenceSlot。
- EvidenceSlot 至少包含 name、required、acceptedRoles、minCandidates、maxCandidates、confidenceRequired、resolverPolicy、fallbackPolicy。
- required slot 完整且达到置信度时才执行规则。
- required slot 缺失或歧义时，按 resolverPolicy 输出 `SYS-*` 或进入模型辅助资格评估。
- 证据上下文分为 Global Contract Index、EvidenceBundle、PointEvidenceOverlay。
- 同一 ReviewPointFamily 内优先共享 EvidenceBundle。
- EvidencePacket 必须有 token budget、截断策略和 coverageStatus。
- 不得因证据不足静默回灌全文。

### 8.7 Model Assist

- 生产审核默认使用规则/正则 + 本地 A30 Gemma + 后端裁判。
- Gemma/A30 默认负责局部抽取、证据选择和复杂语义辅助。
- Gemma 不得吃整份合同。
- Gemma 不得一次性裁判全部审核点。
- 规则/正则可处理的点不进入模型 token 预算。
- 模型输出只提供候选、归属、语义辅助或候选 warning。
- 模型输出必须经过 schema validation。
- 缺字段、JSON 错误、anchor 缺失或 schema 不匹配必须转为 `SYS-MODEL-OUTPUT-*`。
- primary/supplement artifact 对同一 role 或 slot 输出矛盾时输出 `SYS-MODEL-CONFLICT`。

### 8.8 Result Composition

- 点级状态包括 `PASS`、`WARNING`、`ERROR`、`NOT_CONCLUDED`、`SKIPPED`。
- 内部 `SYS-*` 映射为业务化 `NOT_CONCLUDED`。
- 外部系统不得依赖内部 `SYS-*` 码。
- 业务 finding 和系统诊断必须分流。
- 后端必须根据固定模板生成 `businessMessage`，不由模型生成。
- `PROVEN_REQUIRED_CLAUSE_ABSENT` 必须和普通 `EVIDENCE_NOT_FOUND` 区分。
- `NOT_CONCLUDED` 不计入业务风险 finding。
- `NOT_CONCLUDED` 必须通过 `notConcludedCount` 单独统计，并展示业务可读原因，避免被误解为无风险。
- `SKIPPED` 必须通过 `skippedCount` 单独统计，并展示适用性原因；它不属于风险，也不表示覆盖不足。
- `PASS` 必须通过 `passCount` 单独统计，用于展示明确通过的审核点数量。

### 8.9 Result Page

- 普通结果页布局为左侧合同原文/预览，右侧审核点结果卡片。
- 普通结果页默认展示进入 ReviewExecutionPlan 的全部审核点，包括 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED`。
- 普通结果页可以提供状态筛选，但不得默认隐藏 `PASS` 或 `SKIPPED`，以免用户无法解释 `plannedPointCount`。
- `SKIPPED` 点不要求证据定位或 EvidenceSlot，只展示适用性原因，例如“当前付款方式为按节点付款，进度款比例审核点不适用”。
- `PASS` 点必须有证据定位和可解释依据；若证据不足，不得判定 `PASS`，应输出 `NOT_CONCLUDED` 并提供业务可读原因。
- `ERROR` 和 `WARNING` 点必须有可靠证据定位和可解释依据；没有可靠证据不得生成业务 finding，应输出 `NOT_CONCLUDED`。
- 点击审核点或证据应滚动并高亮对应原文位置。
- 处理中只显示简单状态页，不展示半成品 finding。
- 完成后展示统一合成后的结果。
- 普通用户不直接看到内部技术码。
- 普通结果页对 Gemma 不可用、超时或输出异常只展示业务化 `NOT_CONCLUDED` 原因，不展示内部 `SYS-MODEL-*` 技术码。
- 管理台任务详情应展示内部 `SYS-MODEL-*`、模型调用状态、超时/不可用原因和相关诊断摘要，便于排查。
- MVP 不保存完整模型原始输出，也不在管理台展示完整模型原始输出；只保存模型调用状态、`SYS-MODEL-*` 诊断码、schema 校验结果、redacted artifact 摘要、token 用量、耗时、模型版本、调用时间和必要的候选归属结论摘要。
- 结果页必须展示审核完整度摘要。
- 未结论点应单独汇总为“需人工确认事项”。
- 冲突证据展开后最多展示 2-3 个候选，并支持多 anchor 高亮。
- SourceAnchor 至少应支持 block/section/table row/cell 级定位；精确字符范围不是 MVP 硬要求。
- 对金额、税率和比例候选，如果解析器能提供精确文本范围，应展示字符级高亮。
- 若只能提供 block/section/table row/cell 级定位，只要证据块和候选值可靠，仍可形成 `PASS / ERROR / WARNING`。结果卡片必须展示匹配到的候选值，例如“合同原文值：13%”，帮助用户在证据块内定位。
- 若一个证据块内存在多个金额、税率或比例候选，必须能可靠归属到当前审核点所需候选值才可形成结论。
- 只有 block 级定位但候选值归属不清时，不得形成 `PASS / ERROR / WARNING`，应输出 `NOT_CONCLUDED`，原因示例：“证据块内存在多个候选，无法可靠归属”。
- 候选归属不清时可触发 Gemma 局部辅助，但输入仅限相关证据块和必要上下文，不得提交整份合同。Gemma 只辅助 CandidateResolver 选择候选，不得直接输出最终 `PASS / ERROR / WARNING`。
- Gemma 辅助后仍无法可靠归属时，必须输出 `NOT_CONCLUDED`，业务原因说明“候选归属仍不可靠”。不得猜测候选继续裁判。
- Gemma 不可用或超时时，不得阻断所有审核点。若规则/候选本身已达到可靠标准，后端可继续确定性裁判；若当前点依赖 Gemma 消歧且 Gemma 不可用，则该点输出 `NOT_CONCLUDED`。
- 缺少字符级范围本身不得导致 `NOT_CONCLUDED`；金额、税率和比例的字符级高亮延后作为 Pilot 质量提升目标。

### 8.10 Admin Console

- 管理台一期必要能力包括创建审核任务、任务列表、任务详情、阶段日志、ParseQualityReport、审核执行计划、EvidencePacket 摘要、Gemma 调用状态。
- 任务详情页顶部概览区必须展示本次 execution 实际使用的“审核模型”，包括 `modelProfileCode`、模型展示名、provider 类型、模型名称和配置版本摘要，例如“公网兼容 OpenAI 接口模型”或“本地 OpenAI-compatible 模型服务中的某个 Model Profile”。
- 管理台支持维护多个 `Model Profile`，包括本地模型、公网兼容 OpenAI API 模型和 mock fallback。管理员可通过启用状态、密钥配置状态、默认模型和 `usageScope` 控制新任务可选模型。
- `Model Profile` 最小字段包括 `profileCode`、`displayName`、`providerType`、`endpointAlias`、`modelName`、`enabled`、`secretConfigured`、`timeoutSeconds`、`retryCount`、`usageScope`、`isDefaultForNewTask` 和 `configVersion`。
- 管理台创建任务时可以选择模型 profile；如果未指定模型，系统使用当前启用的默认 `Model Profile`。管理台只能选择已启用且密钥已配置的 profile。
- 外部 API MVP 不允许调用方直接指定 `modelProfileCode`，避免 SAP/OA/采购系统把模型选择变成业务接口依赖。外部 API 创建任务时使用平台当前默认启用的 `Model Profile`。
- 如果后续需要允许外部 caller 指定模型，必须通过 caller policy 白名单和独立 API 契约扩展，不进入第一版 MVP。
- 每个 execution 固定绑定当次模型 profile 和配置版本，历史快照不随后续模型配置切换变化。外部系统可查看本次实际使用的审核模型摘要，但不能控制它。
- 平台允许管理员启用公网模型 profile，并在业务允许的场景下用于审核链路验证、演示、评测或内部审核任务。公网模型能否用于真实合同，由业务方、管理员和部署环境共同承担配置责任。
- 无论使用本地模型还是公网模型，模型仍只做局部抽取、候选归属、证据选择或语义辅助；最终 `PASS / ERROR / WARNING / NOT_CONCLUDED` 仍由后端裁判与结果合成模块决定。
- `任务日志` 属于任务列表/任务详情层级能力，不属于“配置审核规则”页面标签页。
- MVP 任务日志只记录阶段级事件，不记录每个内部函数调用。阶段级事件至少覆盖 `QUEUED`、`PARSING`、`INDEXING`、`PLANNING`、`BUILDING_EVIDENCE`、`REVIEWING_RULES`、`REVIEWING_MODEL`、`COMPOSING` 等 stage 的开始、完成、失败、重试、超时和跳过。
- 每条任务日志事件应记录 `taskId`、`executionId`、`stageName`、`attempt`、事件类型、发生时间、耗时、摘要状态和业务化原因；如存在内部诊断，可关联摘要级 `SYS-*` code，但不得要求前端展示内部函数名或调用栈。
- 模型相关阶段日志可展示模型调用状态、耗时、token 用量、模型版本、schema 校验状态和诊断码摘要；不得记录或展示完整 prompt、完整模型 raw output、endpoint secret、stack trace 或逐函数调试日志。
- 管理台必须支持合同结构化字段配置，包括字段名称、字段编码、字段类型、排序号、占位提示、下拉选项、是否必填、是否启用。
- 管理台创建合同任务时，应按已启用字段渲染录入表单，并允许直接录入首批结构化字段。
- 后台支持自定义新增合同字段；删除字段不得影响已创建任务数据和历史快照。
- 合同字段被启用审核点依赖时，不允许直接停用或删除。必须先调整依赖它的审核点配置，或停用相关审核点。
- 字段停用/删除操作应提示依赖该字段的审核点清单，避免产生不可执行配置。
- 自定义字段只进入结构化字段池和后续规则配置候选，不自动生成审核点，不自动绑定 EvidenceSlot，不自动参与业务裁判。
- 管理台可展示完整内部诊断码、EvidenceSlot、CandidateResolver、Gemma artifact 摘要、token 预算、schema validation errors 和 artifact anchors；MVP 常规页面只展示摘要级诊断信息，不展示完整模型原始输出、完整 prompt、endpoint secret、stack trace 或完整合同敏感调试包。
- “配置审核规则”页面标签页 MVP 范围：
  - `审核点`：支持列表、详情展开和有限编辑。
  - `模型配置`：只读展示当前模型版本和 endpoint 状态，不支持编辑。
  - `提示词模板`：不支持编辑，仅可展示业务摘要或延后。
  - `最终提示词预览`：不展示完整 prompt，延后到后续治理/技术视图。
- `任务日志` 不放在“配置审核规则”页面；应放在任务列表/任务详情相关位置。
- MVP 管理台暂不做平台内登录、账号体系和权限矩阵；访问控制由部署环境、内网、VPN、反向代理或外围系统承担。
- MVP Admin API 暂不设计细粒度角色权限，但仍必须限制常规接口输出范围，不返回完整 prompt、完整模型 raw output、endpoint secret、stack trace、完整合同敏感调试包或密钥。
- MVP 配置变更仍应记录配置版本、变更时间和操作者占位信息；操作者可暂记为 `SYSTEM`、`ADMIN_PLACEHOLDER` 或环境账号。Pilot / Production Readiness 再补登录、角色、审批、审计和敏感诊断导出。
- 规则、prompt、pattern 发布治理延后到 Pilot / Production Readiness；MVP 不支持自动发布，也不支持 AI 建议直接改生产配置。

### 8.11 Versioning And Snapshot

- 历史结果必须读取不可变 ReviewResultSnapshot。
- 快照必须绑定 `taskId`、`executionId`、状态、summary、reviewCompleteness、findings、diagnostics、sourceAnchors 和各类版本。
- 每次审核结果必须绑定 contract type profile、rule set、budget profile、parser、pattern library、field lexicon、model 等版本。
- 任何规则、词库、正则、prompt、模型、合同类型画像或 EvidenceSelector 变化必须可追溯。

### 8.12 Quality Governance

- Quality Copilot 只负责失败样本归因、候选 prompt/正则/词库/规则、合成回归样本、评测报告解释。
- Quality Copilot 不直接修改生产规则，不直接决定业务 finding，不绕过管理员批准。
- 候选发布流程为 DRAFT -> EVALUATING -> READY_FOR_REVIEW -> APPROVED / REJECTED -> PUBLISHED。
- 失败样本进入质量工作台前必须满足脱敏和最小化裁剪。
- 样本集过期和生产保护必须有明确状态、审批和恢复流程。

### 8.13 AI 调优治理

项目主定位保持为 Contract Quality Control Platform / 合同质量控制中台，不改为 AI 合同审核平台。具体模型可通过评测和 `ModelProfile` 切换，但 `EvidenceSlot`、`CandidateResolver`、`ReviewPointFamily` 和 `Tuning Governance` 是长期资产。

平台必须隔离两条链路：

生产审核链路：

```text
Contract -> Parser -> CandidateResolver -> EvidenceSlot -> Review Engine -> Finding -> ReviewResultSnapshot
```

调优治理链路：

```text
ReviewResultSnapshot -> TuningPacket -> CrossModelDiagnostic -> AITuningAdvice -> Candidate Change -> Regression Validation -> Human Approval -> Release
```

生产审核链路追求稳定、可解释和可审计。调优治理链路追求实验、诊断、优化和治理。两条链路不得混用。

核心红线：

```text
AI Advice != Production Change
```

任何 AI 建议不得直接修改 prompt、rule、CandidateResolver、EvidenceSlot、ModelProfile 或后端确定性裁判，不得影响当前正式 `ReviewResultSnapshot`。AI 建议只能作为候选建议，必须经过 Regression Validation、Human Approval 和 Version Release 后，才可能影响后续新建任务。

MVP 基础能力：

- 生成 `TuningPacket`、`PointDiagnostic`、`ExecutionSummary` 和 `ExportConfig`。
- 任务详情页新增 `AI 调优包` tab，用于导出和复制 `TuningPacket`。
- 支持 `SINGLE_POINT` 和 `FOCUSED` 两级导出，供内部人员复制给 DeepSeek、ChatGPT、Claude 或其他 AI 辅助分析。
- 支持保存外部 AI 返回的建议文本，作为调优线索记录，不作为结构化 `AITuningAdvice`。
- 不自动调用公网 AI。
- 不自动调优。
- 不自动生成正式 `AITuningAdvice`。
- 不自动执行 `CrossModelDiagnostic`。
- 不自动改 prompt、rule、pattern、field lexicon、CandidateResolver、EvidenceSlot、ModelProfile 或 ReviewPointDefinition。
- 不自动影响当前 `ReviewResultSnapshot`。

Pilot 核心能力：

- 新增 `CrossModelDiagnostic`、`CrossModelComparison` 和结构化 `AITuningAdvice`。
- 支持用同一份局部 EvidencePacket / ModelCallIntent 调用另一个 Model Profile 做诊断对比。
- 必须记录输入等价性：`STRICT / EXPANDED_WITH_POLICY / DIFFERENT_INPUT`。
- 只生成候选建议，不自动生效。

Production Readiness 能力：

- 新增 Regression Center、Release Governance、Rollback、Audit 和 OptimizationEffect。
- AI 建议上线后必须定期评估效果。
- 回归变差时可自动告警或冻结；自动回滚策略需独立治理确认。

Tuning Packet 是调优治理链路的数据底座，不是普通日志，也不是生产审核结果。它只包含最小必要、结构化、可复制、可追溯的诊断信息，不默认导出整份合同、完整 prompt、完整 raw output、endpoint secret、stack trace 或全部上下文。

`AI 调优包` tab 的目的，是帮助人和外部 AI 分析问题，不是自动调优系统。MVP 只允许生成 TuningPacket、导出 TuningPacket、复制到外部 AI、保存外部 AI 建议文本；不允许自动调用公网 AI、自动生成 AITuningAdvice、自动执行 CrossModelDiagnostic 或自动修改任何生产审核配置。

DefinitionTermIndex 是索引层能力，不属于 Parser。Parser 产出结构化 blocks 后，DefinitionTermIndex 与 CandidateIndex 并行构建，用于识别“本合同所称……”“以下简称……”等定义条款，并按审核点或 ReviewPointFamily 需要注入 compact definition context；不得默认注入每个 EvidenceBundle。

## 9. Non-functional Requirements

### 9.1 Performance And Queue

- 一期使用单 worker 顺序处理，不承诺高并发 SLA。
- 默认运营边界包括 `maxActiveQueuedTasks=20`、`maxExecutionsPerTask=3`、`warnQueueDepth>=10`、`rejectOrDelayDeepReviewWhenQueueDepth>=10`。
- 管理台至少监控 queueDepth、oldestQueuedAge、estimatedWaitTime、averageExecutionDuration by budget profile、modelCircuitBreakerState。
- `estimatedWaitTime` 是估算值，不是 SLA 承诺。

### 9.2 Reliability

- 每个 stage 必须幂等。
- 已完成 stage 不重复执行。
- 重试从最后一个未完成或失败 stage 继续。
- 所有 stage 输出必须按 `taskId + executionId + stageName + attempt` 记录。
- 模型重试固定为最多 3 次，指数退避。
- circuit breaker 以 `endpointId + modelVersion` 为作用域。

### 9.3 Security And Privacy

- 普通结果 URL 不暴露 prompt、raw output、endpoint、stack trace、admin logs。
- MVP 管理台暂不做平台内登录、账号体系和权限矩阵；访问控制由部署环境、内网、VPN、反向代理或外围系统承担。
- 管理台常规页面和常规 API 不展示或返回完整 prompt、完整模型 raw output、endpoint secret、stack trace、完整合同敏感调试包或密钥。
- MVP 不保存完整模型原始输出；受控保存完整 redacted raw output、TTL、审批、脱敏和审计展开延后到 Pilot / Production Readiness。
- encryptedRawOutput 默认仅 admin/evaluation tasks 启用，TTL 7 days；该能力不进入 MVP 常规管理台。
- 脱敏规则必须版本化并测试。
- 评测报告不得导出原始合同内容。

### 9.4 Auditability

- 审计日志默认保留 365 天。
- 审计日志必须 append-only。
- 普通管理员无删除或修改权限。
- 所有 caller policy 变更必须审计。
- 扩大 caller 能力需要第二名 `CALLER_POLICY_ADMIN` 批准。
- 紧急停用可单名管理员执行，但必须审计。

## 10. Implementation Decisions

### 10.1 Confirmed Decisions

- V2 使用新仓库和新工程上下文启动。
- 旧 `ai-contract-review` 项目只作为可选历史参考，不默认继承。
- MVP 入口顺序采用 API-first + 简易管理台，见 `ADR-004`。
- 平台不改变审批流，只提供风险提示、证据和结果 URL。
- 业务 finding 和 `SYS-*` 诊断分流。
- 后端负责最终确定性裁判。
- Gemma/A30 只做局部辅助。
- 规则、正则、prompt、模型、合同类型画像和 EvidenceSelector 版本化。

### 10.2 Proposed Decisions

- 仓库目录、测试策略和数据库实体/迁移细节仍需后续任务继续冻结；核心技术栈已由 `ADR-001` 确认。
- V1 结果和诊断契约仍由 `ADR-002` 待确认。
- Task / Execution / ReviewResultSnapshot 模型仍由 `ADR-003` 待确认。
- 首批审核点基线由 `ADR-005` 确认，状态为 `Accepted`。

### 10.3 Deep Modules To Build

以下是 PRD 层面的模块划分，具体代码目录和技术实现仍待 `ADR-001` 确认：

- Entry and Task API：封装任务创建、状态查询、result URL 和 caller policy 边界。
- Task Execution Engine：封装 stage 状态机、单 worker、幂等、租约、timeout 和 retry。
- Word Parser Pipeline：封装 Word 解析、预览转换、证据模型和 ParseQualityReport。
- Contract Evidence Model：封装 DocumentBlock、TableBlock、FormControlBlock、SectionTree、RegionIndex 和 provenance。
- Candidate Index and Resolver：封装高召回索引、候选归一、角色置信度和冲突解释。
- Review Planning：封装合同类型画像、启用审核点、结构化字段依赖和 ReviewExecutionPlan。
- Evidence Planning：封装 EvidenceSlot Preflight、FamilyEvidencePlan、EvidenceBundle 和 PointEvidenceOverlay。
- Model Gateway and Artifact Store：封装 ModelCallIntent、Gemma 调用、schema validation、artifact 复用和模型诊断。
- Deterministic Review Engine：封装结构化比较、公式规则、数值归一、后端最终裁判。
- Result Composer and Snapshot：封装点级状态、businessMessage、SourceAnchor、ReviewCompleteness 和不可变快照。
- Result Page UI：封装普通用户可见结果、证据定位、未结论解释和冲突候选展示。
- Admin Console：封装管理台任务、阶段日志、诊断、版本、样本和发布治理。
- Governance and Audit：封装规则集版本、样本集生命周期、紧急变更、caller policy 和审计日志。

## 11. Testing Decisions

测试应验证外部行为和契约，不测试内部实现细节。每个深模块都应优先用稳定输入输出、状态转移和错误边界进行测试。

### 11.1 MVP Test Focus

- Task creation：创建任务返回 `taskId`、`executionId`、`QUEUED` 和 result URL。
- Stage state machine：验证阶段顺序、timeout、幂等和失败恢复。
- Word parser：验证 `.docx` 正文、表格、控件、附件和低置信场景。
- Candidate resolver：验证 HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN 的行为边界。
- EvidenceSlot Preflight：验证 required slot 缺失不会生成业务 finding。
- First review points：验证主体、金额、税额和五个月度付款比例的 PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED。
- Result composition：验证 `SYS-*` 映射为业务化 `NOT_CONCLUDED`。
- Result page：验证审核点到原文定位和未结论解释。
- Snapshot：验证历史结果不因版本变化而改变。
- Model gateway：验证 timeout、invalid JSON、schema mismatch、primary/supplement conflict。

### 11.2 Pilot Test Focus

- FamilyEvidencePlan：多审核点、多 slot、多 secondaryFamilies、跨 shard 去重、预算截断。
- requiresHigherBudget：STANDARD 预算不足时输出 `NOT_CONCLUDED` 和 DEEP_REVIEW 建议。
- parser provenance：验证 context/source metadata 贯穿索引、排序和 resolver。
- required clause absence：验证 `PROVEN_REQUIRED_CLAUSE_ABSENT` 和 coverageProofSummary。
- correction execution：验证同一 task 新 execution、superseded 字段和历史查询。
- redaction：验证 redacted raw output 不暴露敏感信息。
- caller policy：验证权限扩大、紧急停用和恢复审批。
- dataset expiry guardrail：验证 90 天过期保护和 409 contract test。
- fault injection：验证模型 endpoint timeout、503、invalid JSON、conflicting supplement。

### 11.3 Sample Set Requirements

上线前样本集最低建议：

- Pilot 前至少 50 份合同。
- 每个优先合同类型至少 10 份样本，若可获得。
- 包含长合同、复杂表格、合并单元格、控件、缺条款和冲突证据场景。
- 包含 expected-correct 和 expected-risk variants。
- 至少 30 份为脱敏真实合同、批准历史案例或隔离评测环境中的授权原始合同。
- 合成样本可覆盖罕见边界，但不能替代真实样本最低要求。

## 12. Acceptance Criteria

### 12.1 MVP Acceptance

- MVP 验收使用 2-3 份脱敏或合成中文 Word 合同作为第一批验证样本。
- 对这些验证样本，系统必须完成解析、首批审核点执行、证据定位、结果快照和结果页展示。
- 核心审核点至少大部分形成 `PASS / WARNING / ERROR`，不能以大量 `NOT_CONCLUDED` 作为“链路跑通”的替代。
- 所有 `NOT_CONCLUDED` 点必须有业务可读原因和 source/coverage explanation。
- 可以通过 API-first + 简易管理台创建单份中文 Word 合同审核任务。
- MVP 任务创建和管理台上传只接受 `.docx`；上传界面必须明显提示 `.doc` 暂不支持、待后续开发。
- 任务创建后返回 `taskId`、`executionId`、状态和 result URL。
- Word 解析产物能进入合同证据模型。
- 首批审核点能基于结构化字段和合同候选执行。
- Gemma/A30 只在局部歧义场景辅助，不直接给最终业务结论。
- 后端能统一合成点级状态和业务化文案。
- 证据不足、解析低置信、模型异常或预算不足时不生成业务 finding。
- ReviewResultSnapshot 保存版本引用和审核完整度。
- 普通结果页能展示审核点、证据和原文定位。
- 管理台能查看任务阶段、解析质量、EvidencePacket 摘要和模型调用状态。

### 12.2 Pilot Acceptance

- core review point concluded rate >= 80%。
- overall executable point concluded rate >= 70%。
- system-caused NOT_CONCLUDED rate <= 20% for core points。
- 100% NOT_CONCLUDED points 有业务可读原因和 source/coverage explanation。
- 预算相关 `SYS-*` 在核心审核点上不高频出现。
- 样本集、规则集、模型、prompt、正则、词库和预算 profile 均可追溯。

## 13. Risks

- Word 解析质量不足会导致核心审核点大量 `NOT_CONCLUDED`。
- EvidenceSlot 定义过宽会导致预算频繁截断。
- 首批审核点过多会拖垮 MVP；过少会不足以验证结果页和证据模型。
- 首批结构化字段和必填性虽然已确认，但字段 key、数值精度、枚举编码、禁用/删除后的历史兼容规则仍会影响 API 契约和管理台输入设计。
- 本地模型服务 endpoint、模型版本和容量边界未定，会影响模型网关和预算基线；当前目标硬件为 A30 24GB，但该信息不作为核心技术栈冻结项。
- MVP 普通结果 URL 访问控制已确认由外围系统编码加密和访问控制承担，平台侧暂不做登录、公开令牌或独立访问控制。
- MVP 管理台访问控制同样由部署环境、内网、VPN、反向代理或外围系统承担，平台内暂不做登录、账号体系和权限矩阵。
- MVP 管理台常规页面和常规 API 默认只展示摘要级诊断信息，不展示或返回完整 prompt、完整模型 raw output、endpoint secret、stack trace、完整合同敏感调试包或密钥。完整敏感诊断导出如后续需要，应作为 Pilot / Production Readiness 的受控能力单独设计。
- 数据库 ERD 和迁移策略未定，会影响 scaffold。
- `ADR-001`、`ADR-002`、`ADR-003` 尚未全部 Accepted，当前仍不建议开始业务功能开发或 scaffold。

## 14. Open Questions

- 项目目录、领域模型与最小 OpenAPI 契约的最终冻结结果是什么？
- `.doc` 后续开发的目标阶段和验收标准是什么？
- 外币合同进入哪个后续阶段，以及需要支持哪些币种和汇率语义？
- 数据库实体最终 ERD 和迁移策略是什么？
- API OpenAPI 契约完整细节和认证方式是什么？
- Pilot / Production Readiness 的管理台登录、角色权限、敏感诊断导出审批和审计矩阵是什么？
- 自定义新增字段的权限、字段类型集合、编码唯一性、禁用/删除规则和审计要求是什么？
- 评测环境、样本文件命名规范、保存目录和预期结果文件格式是什么？
- 本地模型服务 endpoint、模型版本、部署形态和容量边界是什么？
- SAP/OA/采购系统正式联调进入 Pilot、V1 末尾还是 V2？
- 生产、预生产、测试环境部署拓扑是什么？

## 15. 当前窗口项目记忆总结

### 15.1 已确认事项

- MVP 验收口径：使用 2-3 份脱敏或合成中文 Word 合同，系统完成解析、首批审核点、证据定位、结果快照和结果页展示；核心审核点至少大部分可形成 `PASS / WARNING / ERROR`，所有 `NOT_CONCLUDED` 必须有业务可读原因。
- MVP 验收样本由业务方提供脱敏或合成中文 `.docx` 工程采购合同；平台不负责自动识别样本是否已脱敏，也不在 MVP 中提供脱敏检测或自动脱敏能力。样本进入仓库或评测环境的前提，是业务方/data owner 已确认其可用于项目验证。
- MVP 输入格式：第一轮仅支持 `.docx`；`.doc` 延后。管理台上传界面必须明显标注“当前仅支持 DOCX，DOC 待后续开发”。
- 首批结构化字段：合同名称、甲方名称、乙方名称、项目名称、合同总金额（含税）、合同不含税金额、合同税额、税率、计价模式、付款方式、预付款比例、进度款比例、竣工款比例、结算款比例、质保款比例、节点付款其他节点信息、发票类型。
- 首批结构化字段采用 MVP 冻结最小契约：字段 key 沿用 PRD 表中建议 key；`pricingMode` 使用 `FIXED_TOTAL_PRICE / PROVISIONAL_TOTAL_PRICE`，`paymentMethod` 使用 `MONTHLY / MILESTONE`，`invoiceType` 使用 `VAT_GENERAL / VAT_SPECIAL`；创建任务最小错误码为 `REQUIRED_FIELD_MISSING`、`CONDITIONAL_FIELD_MISSING`、`INVALID_ENUM_VALUE`、`INVALID_DECIMAL_SCALE`、`INVALID_PERCENT_RANGE`、`UNSUPPORTED_CURRENCY`、`UNSUPPORTED_FILE_TYPE`。
- 管理台可直接录入首批结构化字段；SAP/API 传入字段与管理台字段定义保持一致；后台支持自定义新增字段。
- 自定义新增字段只用于记录、录入、展示和后续规则配置候选；不会自动生成审核点、EvidenceSlot 或业务裁判逻辑。
- 付款方式采用条件必填：按月度付款时，预付款、进度款、竣工款、结算款、质保款比例必填；按节点付款时，预付款比例和节点付款其他节点信息必填，其他月度专属比例不适用。
- 节点付款其他节点信息在 MVP 中为多行文本必填，只用于记录、快照和展示，暂不参与审核点裁判。
- 合同总金额语义固定为合同含税总金额；管理台标签显示“合同总金额（含税）”。
- MVP 固定只支持人民币 `CNY`；管理台不提供币种选择，金额单位显示“元”。SAP/API 可省略币种；显式传入时只允许 `CNY`。
- 税率和付款比例统一使用百分比数值存储和传输，例如 `13` 表示 `13%`，不是 `0.13`；后端计算时统一除以 `100`。
- 月度付款比例采用累计口径：`预付款比例 <= 进度款比例 <= 竣工款比例 <= 结算款比例`，且 `结算款比例 + 质保款比例 = 100%`。MVP 不重复校验这两条跨字段关系，也不据此生成业务 finding。
  - “累计口径”含义：进度款比例 = 预付款阶段 + 进度款阶段累计支付占合同总额的比例，依此类推。
  - 单调递增关系是累计值的必然结果，不是各阶段独立比例的加总关系。
  - 后端裁判逻辑：校验累计递增关系是否成立，以及结算款比例 + 质保款比例 = 100%。
  - MVP 阶段不重复校验这两条跨字段关系，也不据此生成业务 finding；此处口径说明用于避免实现时理解偏差，不改变 MVP 范围约束。
- 首批审核点为 9 个：`PARTY_A_NAME_CONSISTENCY`、`PARTY_B_NAME_CONSISTENCY`、`CONTRACT_TOTAL_AMOUNT_CONSISTENCY`、`TAX_AMOUNT_FORMULA_CONSISTENCY`、`PREPAYMENT_RATIO_CONSISTENCY`、`PROGRESS_PAYMENT_RATIO_CONSISTENCY`、`COMPLETION_PAYMENT_RATIO_CONSISTENCY`、`SETTLEMENT_PAYMENT_RATIO_CONSISTENCY`、`WARRANTY_RETENTION_RATIO_CONSISTENCY`。
- 税率新增固定 `CandidateRole=taxRate`，作为 `TAX_AMOUNT_FORMULA_CONSISTENCY` 的 optional EvidenceSlot 候选角色；税率缺失或不可靠时不执行税额弱校验，不得因此生成无可靠证据的业务 finding。
- 首批 9 个审核点均标记为 core；管理台可配置审核点默认级别为 `ERROR` 或 `WARNING`，不 hardcode。
- 按节点付款仍执行预付款比例一致性审核；进度款、竣工款、结算款、质保款四个比例审核点因不适用输出 `SKIPPED`，不输出 `SYS-*`、`NOT_CONCLUDED` 或业务 finding。
- 管理台“配置审核规则”页面 MVP 只做预置审核点的列表、详情展开和有限编辑；新增审核点延后。
- 审核点配置保存后即对下一次新建任务生效，不设置独立发布流程；已创建任务和历史快照不受配置变化影响。
- 审核点展示编码可编辑，用于前台定位；系统内部 `reviewPointCode` 是唯一、不可编辑主键，用于后端执行、EvidenceSlot、API 和快照。
- 审核点排序只影响管理台列表和普通结果页展示顺序，不影响执行顺序。
- 审核分类只用于展示、筛选和排序，不作为执行路由。
- 审核点执行逻辑第一版由项目团队通过预置 `ReviewPointDefinition`、EvidenceSlot、抽取规则、后端裁判和 Gemma 辅助策略生成；管理台用户不生成执行逻辑。
- 执行逻辑若效果不理想，通过版本化规则、词库、ValueGrammar、EvidenceSlot、prompt 和后端裁判迭代，并使用脱敏/合成样本回归验证。
- `NOT_CONCLUDED` 表示系统没有足够可靠依据形成业务结论，不是业务风险，也不是无风险结论。
- 普通结果页默认展示进入 `ReviewExecutionPlan` 的全部审核点，包括 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED`。
- `requiresHigherBudget / recommendedBudgetProfile` 不进入第一轮 MVP 普通结果页；普通结果页只展示业务化 `NOT_CONCLUDED` 原因和人工核对提示。管理台任务详情、评测报告和 AI 调优包可展示预算诊断。
- `PASS`、`ERROR`、`WARNING` 必须有可靠证据定位和解释；证据不足不得生成业务 finding，应输出 `NOT_CONCLUDED` 和业务可读原因。
- MVP SourceAnchor 最低要求为 block/section/table row/cell 级；金额、税率和比例如果无法字符级高亮，仍可基于可靠 block/cell 形成结论，但结果卡片必须展示合同原文候选值。
- Gemma 只允许做局部候选归属和复杂语义辅助，不直接输出最终 `PASS / ERROR / WARNING`。
- Gemma 不可用或超时时，不阻断所有审核点；规则和候选已可靠的点继续由后端确定性裁判执行，依赖 Gemma 消歧且无法完成的点输出 `NOT_CONCLUDED`。
- MVP 不保存完整模型原始输出，也不在管理台展示完整模型原始输出。
- “任务日志”不属于“配置审核规则”页面，应放在任务列表/任务详情层级。
- 任务详情页顶部需要展示当次 execution 实际绑定的“审核模型”；模型来源通过 `Model Profile` 管理，可包括本地 OpenAI-compatible 模型服务、公网兼容 OpenAI API 的模型和 mock fallback。
- 管理台可启用多个模型 profile，并通过默认模型和使用范围控制新任务使用哪个模型。公网模型在业务允许的场景下可用于链路验证、演示、评测或内部审核任务；能否用于真实合同由业务方、管理员和部署环境承担配置责任。
- 每个 execution 必须绑定当次 `modelProfileCode`、provider 类型、模型名称和配置版本；历史快照不随后续模型切换变化。
- 模型不论来自本地还是公网，都不得直接决定最终业务 finding；最终点级状态仍由后端裁判合成。
- MVP 任务日志记录粒度已确认：只记录阶段级事件，不记录每个内部函数调用；用于判断任务卡在解析、索引、计划、证据构建、规则审核、模型辅助或结果合成等阶段。
- 阶段日志可包含摘要级 `SYS-*` 诊断、attempt、耗时、重试和超时信息；模型阶段只展示调用状态、token、耗时、模型版本、schema 校验和诊断摘要，不展示完整 prompt、完整 raw output、endpoint secret、stack trace 或逐函数调试日志。

### 15.2 待确认事项

- 工程目录、测试策略和脚手架边界。
- 数据库最终 ERD、迁移策略和字段版本化落地方式。
- API OpenAPI 契约完整细节、认证方式和外部系统调用策略。
- Pilot / Production Readiness 的管理台登录、角色权限、敏感诊断导出审批和审计矩阵。
- 本地模型服务 endpoint、模型版本、部署形态、容量边界和超时预算。
- `usageScope=PRODUCTION_REVIEW` 的公网模型 profile 审批角色、默认关闭策略和审计字段。
- 自定义字段权限细节。
- 评测环境、样本文件命名规范、保存目录和预期结果文件格式。
- `.doc` 后续开发的目标阶段和验收标准。
- 外币合同的目标阶段、币种范围和汇率语义。

### 15.3 已更新文档

- `PRD.md`：持续沉淀本窗口已确认的产品口径、MVP 范围、首批结构化字段、首批审核点、配置审核规则页面、证据定位、Gemma 降级和未完成讨论。
- `CURRENT_CONTEXT.md`：同步当前阶段、已确认事项、待确认事项和下一步约束。
- `docs/frontend.md`：同步管理台录入、配置审核规则、结果页、任务日志归属和上传格式限制。
- `docs/backend.md`：同步后端确定性裁判、审核点配置、配置生效方式、任务日志归属和模型配置只读边界。
- `docs/database.md`：同步结构化字段、配置、快照、比例/税率存储口径和版本化方向。
- `docs/sap.md`：同步 SAP/API 与管理台字段一致、币种和结构化字段输入口径。
- `docs/ai-review.md`：同步 Gemma 辅助边界、EvidenceSlot、NOT_CONCLUDED、模型原始输出保存限制和技术治理延后项。
- `ADR-006-model-profile-switching-and-public-provider-scope.md`：记录 Model Profile 切换和公网模型使用边界。
- `ADR-007-tuning-packet-architecture.md`：记录 Tuning Packet 架构和导出边界。
- `ADR-008-definition-term-index.md`：记录 DefinitionTermIndex 作为索引层能力。
- `ADR-009-ai-tuning-governance.md`：记录 AI 调优治理链路和 `AI Advice != Production Change` 红线。
- `ADR-005-first-review-points-selection.md`：记录首批 9 个 core 审核点选择基线，状态为 `Accepted`。
- `tasks/active/TASK-003-first-review-points-selection.md`：记录首批审核点选择任务结果。
- `tasks/active/TASK-009-mvp-scope-freeze.md`：记录第一轮 MVP 范围冻结任务和收口结果。
- `changelog/2026-06.md`：记录 2026-06 的项目记忆变更。

### 15.4 未完成讨论

- “配置审核规则”之外的任务详情页信息架构仍需继续确认，包括任务日志、解析质量、EvidencePacket 摘要、模型辅助状态和结果快照入口如何组织；顶部概览区已确认需要展示审核模型。
- 首批 `ReviewPointDefinition` 草稿已生成到 `docs/review-point-definitions.md`，仍需后续映射到最小 OpenAPI、数据库/配置落地格式、字段词库/ValueGrammar/正则边界和样本验证。
- 最小 OpenAPI 契约尚未生成，包括任务创建、状态查询、结果 URL、字段录入、错误码和认证边界。
- Word parser baseline plan 尚未完成，包括 DOCX 解析库选择、证据模型、表格/控件/附件处理和 SourceAnchor 产物。
- Model gateway and budget baseline 尚未完成，包括 Gemma endpoint、超时、重试、schema 校验、预算和诊断码。
- 当前仍不建议开始 scaffold；应先确认 `ADR-001`、`ADR-002` 和 `ADR-003`，并完成最小 OpenAPI 契约、Word parser baseline plan、model gateway baseline 以及必要的数据库/配置落地边界。

## 16. Further Notes

本 PRD 是产品需求基线，不替代 ADR、任务包或模块文档。涉及技术栈、数据库模型、审核链路、模型职责、SAP/OA 集成、权限或治理流程的重大变化，仍必须通过 ADR 或明确任务包推进。

本 PRD 中标记为“待确认”的内容不得在后续实现中当作已确认事实。若用户或后续任务确认这些内容，应同步更新 `PRD.md`、`CURRENT_CONTEXT.md`、`ROADMAP.md`、相关 `docs/*.md`、任务包和必要 ADR。

当前仍不建议开始 scaffold；应优先完成 `TASK-004-word-parser-baseline-plan`、`TASK-005-model-gateway-and-budget-baseline`、最小 OpenAPI 契约任务，并人工确认 `ADR-001`、`ADR-002` 和 `ADR-003`。
