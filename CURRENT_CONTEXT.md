# CURRENT_CONTEXT.md

更新日期：2026-06-08

## 当前状态

项目处于初始化阶段，尚未开始编码。

当前仓库是 Contract Quality Control Platform / 合同质量控制中台 V2 的新仓库。仓库内正在建立 AI Coding 项目记忆系统，用于后续长期开发，避免依赖聊天上下文。

## 已有输入

已有一份架构文档：

- `2026-06-06-contract-quality-control-platform-v2-design.md`
- 文档状态：Draft for implementation planning review - architecture hardening incorporated

已有一份 PRD 草案：

- `PRD.md`
- 文档状态：Draft for planning review

该文档明确：V2 不以现有 `ai-contract-review` 项目的代码、目录、页面、任务板、历史规则或样本作为默认工程基础。旧项目仅作为可选历史参考源，不得默认继承。

## 本轮已完成

- 初始化长期项目记忆目录。
- 创建 Agent 工作规则、项目简报、当前上下文、路线图、模块文档、任务模板、ADR 模板和变更记录。
- 完成一次 Superpowers planning / design review / task decomposition 审查，并将结论写回项目记忆。
- 完成一次项目记忆写入规则文档治理：`AGENTS.md` 只保留短规则和索引，详细规则迁移到 `docs/context-management.md`。
- 完成一次 Next Task Handoff Prompt 文档治理：要求区分“可执行任务”和“征求意见/建议”，只有明确任务包才能输出可复制代码块。
- 完成一次任务命名规范治理：任务文件名继续使用英文 slug，任务标题、目标、验收标准和交付摘要统一使用中文，现有活动任务已补齐中文标题。
- 完成 `PRD.md` v0.1 草案，基于原始架构文档、`PROJECT_BRIEF.md`、`ROADMAP.md`、模块文档和 ADR 汇总产品需求基线。
- 完成 `TASK-001-v1-mvp-scope-gate`，形成 V1 MVP / Pilot / Production Readiness 分层规划基线，最终仍待人工确认。
- 完成 `TASK-002-entry-order-decision`，确认 MVP 入口顺序采用 API-first + 简易管理台，并记录到 `ADR-004-mvp-entry-order.md`。
- 完成 `TASK-003-first-review-points-selection`，形成首批审核点选择基线，并记录到 `ADR-005-first-review-points-selection.md`，状态已确认为 `Accepted`。
- 未创建业务代码。
- 未搭建前端或后端脚手架。
- 未安装依赖。

## 当前审查判断

- 当前不建议开始编码。
- 当前可以开始“技术基线与 V1 MVP 收敛任务”。
- 当前不建议直接进入 project scaffold。
- 原因是关键决策尚未锁定为 ADR，第一批可执行任务包此前尚未形成；如果直接 scaffold，容易把技术栈、目录结构、API 边界、数据库迁移、权限和模型接入等待确认事项提前固化进代码。
- 已确认 MVP 第一批验收口径：使用 2-3 份脱敏或合成中文 Word 合同，系统必须完成解析、首批审核点、证据定位、结果快照和结果页展示；核心审核点至少大部分形成 `PASS / WARNING / ERROR`，所有未结论点必须有业务可读原因。

## 当前一期方向

一期目标是企业内部低频、高质量审核，重点跑通单份中文 Word 合同审核质量。

一期范围包括中文 `.docx/.doc`、Word 表格/附件/控件、结构化字段、合同类型粗分类、规则/正则/Gemma 混合路由、本地 A30/Gemma 生产辅助、异步单 worker、结果 URL 和最终结果快照。

MVP 第一轮输入已确认收窄为单份中文 `.docx`；`.doc` 不进入第一轮 MVP，管理台上传界面必须明显标注“当前仅支持 DOCX，DOC 待后续开发”。

MVP 首批结构化字段采用通用字段必填和按付款方式条件必填。按月度付款时，预付款、进度款、竣工款、结算款、质保款比例必填；节点付款其他节点信息不适用。按节点付款时，预付款比例和节点付款其他节点信息必填；四个月度专属比例不适用。管理台可以直接录入，SAP/API 传入字段与管理台字段定义保持一致。后台支持自定义新增字段，但自定义字段不自动生成审核点、EvidenceSlot 或业务裁判逻辑。

`合同总金额` 的语义已确认为合同含税总金额，管理台标签固定显示“合同总金额（含税）”；税额公式直接使用 `contractTotalAmount`，不再设置额外含税标志或重复的含税金额输入字段。

MVP 币种固定为人民币 `CNY`。管理台不提供币种选择，金额单位显示“元”；SAP/API 可省略币种，显式传入时只允许 `CNY`。外币合同延后。

MVP 金额字段使用十进制定点数并保留两位小数。税额裁判中，`合同总金额（含税） = 合同不含税金额 + 合同税额` 为强校验，允许 `0.01` 元绝对误差；`合同税额 = 合同不含税金额 × 税率` 仅作为 WARNING 校验，不直接判 ERROR。后端负责确定性最终裁判，Gemma/A30 只可辅助抽取。

税率和付款比例已确认统一使用百分比数值：`13` 表示 `13%`，不使用 `0.13`。管理台、外围系统、SAP/API、数据库和结果快照保持相同口径，后端计算时统一除以 `100`。

税率最多保留 4 位小数，付款比例最多保留 2 位小数，末尾零可省略。超出精度的输入直接拒绝，不得静默四舍五入或截断。

月度付款比例采用累计口径：进度款包含预付款，竣工款包含进度款和预付款，结算款包含竣工款、进度款和预付款。业务关系为 `预付款比例 ≤ 进度款比例 ≤ 竣工款比例 ≤ 结算款比例`，且 `结算款比例 + 质保款比例 = 100%`。MVP 不重复校验这两条跨字段关系，也不据此生成业务 finding；外围系统负责输入校验，管理台测试数据由测试人员控制。平台仍校验单字段范围、精度和条件必填。

节点付款仍执行预付款比例一致性审核；进度款、竣工款、结算款和质保款四个审核点因不适用而 `SKIPPED`，不输出 `SYS-* / NOT_CONCLUDED` 或业务 finding。

`节点付款其他节点信息` 在按节点付款场景中为必填多行文本，只用于记录、快照和展示，暂不参与审核点、EvidenceSlot 或业务裁判。

一期明确不做 PDF/OCR 主输入、大规模并行、MQ、分布式 worker、多租户、复杂 IAM、开放式聊天审合同、自动发布规则/prompt/pattern、复杂 BI、全文向量 RAG 主链路。

## 当前首批审核点基线

`TASK-003-first-review-points-selection` 选择以下审核点作为 MVP 首批基线，详见 `ADR-005-first-review-points-selection.md`。该 ADR 状态已确认为 `Accepted`。

- `PARTY_FIELDS`: `PARTY_A_NAME_CONSISTENCY`、`PARTY_B_NAME_CONSISTENCY`，均为 core review point。
- `AMOUNT_TAX`: `CONTRACT_TOTAL_AMOUNT_CONSISTENCY`、`TAX_AMOUNT_FORMULA_CONSISTENCY`，均为 core review point。
- `PAYMENT_TERMS`: 月度付款的预付款、进度款、竣工款、结算款和质保款比例均纳入 MVP 独立一致性审核，五点全部为 core review point。

首批审核点优先使用结构化字段、ValueGrammar、正则/词库候选和后端确定性裁判。Gemma/A30 只在主体或付款比例候选归属歧义时做局部辅助，不直接生成最终业务 finding。

审核点严重级别不 hardcode。管理后台可视化配置审核点默认严重级别，但仅限明确业务不一致时输出 `ERROR` 或 `WARNING`；`PASS`、`NOT_CONCLUDED`、`SKIPPED` 和 `SYS-*` 不可配置为业务级别。MVP 配置保存后即对下一次新建审核任务生效，不设置单独发布流程；已创建任务和历史快照不随配置变化。

点级摘要必须统计 `plannedPointCount`、`passCount`、`errorCount`、`warningCount`、`notConcludedCount` 和 `skippedCount`。`plannedPointCount` 表示本次启用并进入 ReviewExecutionPlan 的审核点总数；配置停用点不进入该分母，进入计划后判定 `SKIPPED` 的点进入分母。`ERROR` 和 `WARNING` 都属于业务 finding；`NOT_CONCLUDED` 表示覆盖不足但不属于风险数量；`SKIPPED` 表示不适用；`PASS`、`NOT_CONCLUDED`、`SKIPPED` 和 `SYS-*` 不计入业务风险数量。

MVP 审核规则配置页已确认保持与既有项目页面风格一致，只做预置审核点的列表展示、详情展开和编辑；新增审核点延后。列表支持按审核分类和状态排序。详情/编辑字段包括审核点编码、名称、审核分类、默认级别、启用状态、排序、依赖字段和合同类型；依赖字段来自全部合同字段下拉多选。MVP 不开放任意新增或改写 EvidenceSlot、执行策略、后端裁判或模型辅助策略。

审核点编码是前台展示和人工定位用编码，可编辑但不能为空且在当前配置中唯一；系统内部 `reviewPointCode` 是唯一不可编辑主 key，用于后端执行、EvidenceSlot、快照和 API。快照同时记录当次展示编码和内部 code。

审核点名称、展示编码、审核分类、排序和合同类型等展示字段变更只影响后续新任务；历史结果页必须读取 ReviewResultSnapshot 中的当次展示字段，不读取当前配置覆盖历史。

审核点配置中的合同类型字段 MVP 只是展示和未来扩展预留，仅有“工程采购合同”；不参与复杂适用范围路由。首批 9 个审核点默认适用于工程采购合同。

审核点排序字段只影响管理台列表和普通结果页展示顺序，不影响执行顺序；执行顺序由后端计划器根据依赖、证据复用和执行策略决定。

MVP 第一版审核执行逻辑由项目团队通过预置 ReviewPointDefinition、EvidenceSlot、抽取规则、后端裁判和 Gemma 辅助策略生成；管理台用户不生成执行逻辑。审核分类只用于展示、筛选和排序。审核结果优化通过版本化规则、词库、ValueGrammar、EvidenceSlot、prompt 和后端裁判迭代，并用脱敏/合成样本回归验证。

MVP 管理台审核点详情只展示业务可读执行逻辑摘要：内部 code、执行策略名称、依赖字段、需要的证据类型、是否使用 Gemma 辅助、裁判说明和最近一次版本更新时间；不展示完整正则、prompt、EvidenceSlot 细节或后端代码。

审核点依赖字段可编辑但必须可执行：保存时校验依赖字段来自已启用合同字段，并覆盖该审核点 required structured fields。无法执行的依赖配置拒绝保存，不延迟到审核任务执行时输出 `SYS-MISSING-INPUT`。

合同字段被启用审核点依赖时，不允许直接停用或删除。必须先调整审核点依赖或停用相关审核点；字段操作应提示依赖该字段的审核点清单。

审核点停用后不进入新建任务执行计划，不输出点级状态；普通结果页只展示本次实际启用的审核点。快照记录当次启用和配置停用清单。MVP 首批 9 个审核点默认全部启用；手动停用 core 点不能用于证明核心覆盖率达标。

管理台和评测报告展示 `disabledByConfigCount` 和配置停用审核点清单；普通结果页主摘要不展示该计数，也不把配置停用点混入 planned/status counts。

普通结果页默认展示进入 ReviewExecutionPlan 的全部审核点，包括 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED`；可提供状态筛选，但默认不得隐藏 `PASS` 或 `SKIPPED`。

`SKIPPED` 点不要求证据定位或 EvidenceSlot，只展示适用性原因，例如付款方式导致某些付款比例审核点不适用。

`PASS` 点必须有证据定位和可解释依据；证据不足不得判 `PASS`，应输出 `NOT_CONCLUDED` 和业务可读原因。

`ERROR` 和 `WARNING` 点必须有可靠证据定位和可解释依据；没有可靠证据不得生成业务 finding，应输出 `NOT_CONCLUDED`。

MVP 证据定位最低要求为 block/section/table row/cell 级。金额、税率和比例候选若能拿到精确文本范围则展示字符级高亮；若没有，仍可形成结论，但结果卡片必须展示合同原文候选值。缺少字符级范围本身不得导致 `NOT_CONCLUDED`。

一个证据块内存在多个金额、税率或比例候选时，必须能可靠归属到当前审核点所需候选值才可形成结论；只有 block 定位但候选归属不清时，输出 `NOT_CONCLUDED`，业务原因说明“证据块内存在多个候选，无法可靠归属”。

候选归属不清时允许 Gemma 做局部辅助，但输入仅限相关证据块和必要上下文，不得提交整份合同。Gemma 只辅助 CandidateResolver，不直接输出最终 `PASS / ERROR / WARNING`。

Gemma 辅助后仍无法可靠归属时，输出 `NOT_CONCLUDED`，业务原因说明“候选归属仍不可靠”；不得猜测候选继续裁判。

Gemma 不可用或超时时，不阻断所有审核点。规则/候选本身已可靠的点继续由后端确定性裁判；只有依赖 Gemma 消歧且无法完成消歧的点输出 `NOT_CONCLUDED`。

普通结果页不展示内部 `SYS-MODEL-*` 技术码，只展示业务化 `NOT_CONCLUDED` 原因。管理台任务详情和评测报告展示模型调用状态、超时/不可用原因和内部诊断摘要。

MVP 不保存完整模型原始输出，也不在管理台展示完整模型原始输出。只保存模型调用状态、`SYS-MODEL-*` 诊断码、schema 校验结果、redacted artifact 摘要、token 用量、耗时、模型版本、调用时间和必要的候选归属结论摘要。

配置审核规则页面 MVP 只把 `审核点` 作为可编辑主能力；`模型配置` 只读展示当前模型版本和 endpoint 状态；`提示词模板` 不编辑，仅摘要或延后；`最终提示词预览` 不展示完整 prompt。`任务日志` 不属于配置审核规则页面，应放在任务列表/任务详情层级。

任务详情页顶部概览区必须展示本次 execution 实际使用的审核模型。模型来源通过 `Model Profile` 管理，可包括本地 Gemma4、公网 DeepSeek 兼容 OpenAI API 和 mock fallback。管理员可启用多个模型 profile，并通过默认模型和使用范围控制新任务使用哪个模型。公网模型在业务允许的场景下可用于链路验证、演示、评测或内部审核任务；能否用于真实合同由业务方、管理员和部署环境承担配置责任。

每个 execution 必须绑定当次 `modelProfileCode`、provider 类型、模型名称和配置版本；历史快照不随后续模型切换变化。模型不论来自本地还是公网，都不得直接决定最终业务 finding；最终点级状态仍由后端裁判合成。

项目主定位继续保持为 Contract Quality Control Platform / 合同质量控制中台，不改为 AI 合同审核平台。平台已确认拆分为生产审核链路和调优治理链路：生产审核链路追求稳定、可解释、可审计；调优治理链路追求实验、诊断、优化和治理。两条链路必须隔离。

生产审核链路为 `Contract -> Parser -> CandidateResolver -> EvidenceSlot -> Review Engine -> Finding -> ReviewResultSnapshot`。调优治理链路为 `ReviewResultSnapshot -> TuningPacket -> CrossModelDiagnostic -> AITuningAdvice -> Candidate Change -> Regression Validation -> Human Approval -> Release`。

核心红线已确认：`AI Advice != Production Change`。任何 AI 建议不得直接修改 prompt、rule、CandidateResolver、EvidenceSlot、ModelProfile 或后端确定性裁判，不得影响当前正式 `ReviewResultSnapshot`；必须经过回归验证、人工批准和版本发布后，才可能影响后续新任务。

MVP 只建设 TuningPacket、PointDiagnostic、ExecutionSummary、ExportConfig 和任务详情页 `AI 调优包` tab，支持生成、导出、复制诊断包给公网 AI 或本地 AI 辅助分析，并保存外部 AI 建议文本；不自动调用公网 AI，不自动生成 AITuningAdvice，不自动执行 CrossModelDiagnostic，不自动调优，不自动改规则或生产审核配置。CrossModelDiagnostic、CrossModelComparison 和结构化 AITuningAdvice 延后到 Pilot。Regression Center、Release Governance、Rollback、Audit 和 OptimizationEffect 延后到 Production Readiness。

DefinitionTermIndex 已确认为索引层能力，不属于 Parser。Parser 产出结构化 blocks 后，DefinitionTermIndex 与 CandidateIndex 并行构建，并按审核点或 ReviewPointFamily 需要注入 compact definition context，不默认注入每个 EvidenceBundle。

MVP 管理台暂不做平台内登录、账号体系和权限矩阵；访问控制由部署环境、内网、VPN、反向代理或外围系统承担。管理台常规页面和常规 API 默认只展示摘要级诊断信息，不展示或返回完整 prompt、完整模型 raw output、endpoint secret、stack trace、完整合同敏感调试包或密钥。完整敏感诊断导出如后续需要，应作为 Pilot / Production Readiness 的受控能力单独设计。

延后到 Pilot 的审核点包括：签署页主体冲突、统一社会信用代码一致性、多主体关系、中文大写金额一致性、税率合法性、付款比例合计、付款节点顺序和付款条件完整性。

## 当前核心约束

- 证据不足不生成业务 finding。
- `SYS-*` 系统诊断和业务风险 finding 分流。
- Gemma/A30 不做整份合同一次性裁判。
- 后端负责结构化比对和确定性最终裁判。
- 普通结果页必须解释审核点、证据和原文定位。
- 所有规则、正则、prompt、模型、合同类型画像和 EvidenceSelector 必须版本化。
- 一期少配置、强版本、硬预算、可降级、可追溯、可治理。

## 当前窗口项目记忆总结

### 已确认事项

- MVP 验收使用 2-3 份脱敏或合成中文 Word 合同，必须完成解析、首批审核点、证据定位、结果快照和结果页展示；核心审核点至少大部分形成 `PASS / WARNING / ERROR`，所有 `NOT_CONCLUDED` 必须有业务可读原因。
- MVP 第一轮仅支持 `.docx`；`.doc` 延后。管理台上传界面必须明显标注“当前仅支持 DOCX，DOC 待后续开发”。
- 首批结构化字段包括合同名称、甲方名称、乙方名称、项目名称、合同总金额（含税）、合同不含税金额、合同税额、税率、计价模式、付款方式、预付款比例、进度款比例、竣工款比例、结算款比例、质保款比例、节点付款其他节点信息、发票类型。
- MVP 首批合同类型只做“工程采购合同”。管理台合同类型字段 MVP 仅展示“工程采购合同”，作为后续多合同分类预留字段；首批 9 个审核点默认适用于工程采购合同。材料供货合同、费用合同、杂项合同延后到 Pilot 或后续阶段。系统可保留合同类型扩展结构，但 MVP 不做复杂合同类型路由。
- 管理台可直接录入首批结构化字段；SAP/API 传入字段与管理台保持一致；后台支持自定义新增字段，但自定义字段不自动生成审核点、EvidenceSlot 或业务裁判逻辑。
- 付款方式采用条件必填：按月度付款时五个付款比例必填；按节点付款时预付款比例和节点付款其他节点信息必填，其他月度专属比例不适用。
- 节点付款其他节点信息在 MVP 中为多行文本必填，只用于记录、快照和展示，暂不参与审核点裁判。
- 合同总金额语义固定为合同含税总金额，管理台标签显示“合同总金额（含税）”。
- MVP 固定只支持人民币 `CNY`；税率和付款比例统一使用百分比数值，例如 `13` 表示 `13%`。
- 月度付款比例采用累计口径：`预付款比例 <= 进度款比例 <= 竣工款比例 <= 结算款比例`，且 `结算款比例 + 质保款比例 = 100%`。MVP 不重复校验该跨字段关系，也不据此生成业务 finding。
- 首批审核点为 9 个，且均标记为 core：甲方名称一致、乙方名称一致、合同总金额一致、税额公式一致、预付款比例一致、进度款比例一致、竣工款比例一致、结算款比例一致、质保款比例一致。
- 管理台“配置审核规则”页面 MVP 只支持预置审核点列表、详情展开和有限编辑；新增审核点延后。
- 审核点默认级别可配置为 `ERROR` 或 `WARNING`，不 hardcode；保存后对下一次新建任务生效，不设置独立发布流程。
- 审核点展示编码可编辑，用于前台定位；系统内部 `reviewPointCode` 是唯一、不可编辑主键。
- 审核点排序只影响管理台列表和普通结果页展示顺序，不影响执行顺序；审核分类只用于展示、筛选和排序。
- 第一版审核执行逻辑由项目团队通过预置 ReviewPointDefinition、EvidenceSlot、抽取规则、后端裁判和 Gemma 辅助策略生成；管理台用户不生成执行逻辑。
- `NOT_CONCLUDED` 表示系统没有足够可靠依据形成业务结论，不是业务风险，也不是无风险结论。
- 普通结果页默认展示进入 `ReviewExecutionPlan` 的全部审核点，包括 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED`。
- `PASS / ERROR / WARNING` 必须有可靠证据定位和解释；证据不足不得生成业务 finding，应输出 `NOT_CONCLUDED`。
- MVP SourceAnchor 最低要求为 block/section/table row/cell 级；缺少字符级高亮本身不得导致 `NOT_CONCLUDED`。
- Gemma 只做局部候选归属和复杂语义辅助，不直接输出最终业务结论；Gemma 不可用或超时时，不阻断不依赖 Gemma 的确定性审核点。
- MVP 不保存完整模型原始输出，也不在管理台展示完整模型原始输出。
- “任务日志”不属于“配置审核规则”页面，应放在任务列表/任务详情层级。
- 任务详情页顶部概览区已确认展示“审核模型”，包括当次 execution 绑定的模型 profile、provider 类型、模型名称和配置版本摘要。公网 DeepSeek 等公网模型 profile 可由管理员启用并在业务允许场景下使用；真实合同使用责任由业务方、管理员和部署环境承担。
- 每个 execution 固定绑定当次 `Model Profile`，历史快照不随后续模型切换变化；模型无论来自本地还是公网，都只做辅助，不直接决定最终业务 finding。
- MVP 普通结果 URL 不做平台侧登录、公开令牌或独立访问控制。外围系统负责对 URL 进行编码、加密、分发和访问控制，平台暂不在该方向投入额外能力。平台仍必须保证普通结果 URL 不暴露 prompt、raw output、endpoint、stack trace、admin logs、secret 或管理台诊断详情。
- MVP 管理台暂不做平台内登录、账号体系和权限矩阵；访问由部署环境、内网、VPN、反向代理或外围系统承担。管理台常规页面和常规 API 默认只展示摘要级诊断信息，不展示或返回完整 prompt、完整模型 raw output、endpoint secret、stack trace、完整合同敏感调试包或密钥。MVP 配置变更仍记录配置版本、变更时间和操作者占位信息，操作者可暂记为 `SYSTEM`、`ADMIN_PLACEHOLDER` 或环境账号。
- 管理台创建任务时可以选择已启用且密钥已配置的模型 profile；不选择时使用当前默认模型。外部 API MVP 不允许调用方直接指定 `modelProfileCode`，统一使用平台当前默认启用的 `Model Profile`。外部系统可查看本次实际使用的审核模型摘要，但不能控制模型选择；后续如需开放，必须通过 caller policy 白名单和独立 API 契约扩展。
- 项目定位保持为合同质量控制中台，不改为 AI 合同审核平台；模型会变化，EvidenceSlot、CandidateResolver、ReviewPointFamily 和 Tuning Governance 是长期资产。
- 生产审核链路和调优治理链路必须隔离；调优链路不得直接影响当前正式 `ReviewResultSnapshot`。
- `AI Advice != Production Change` 已确认为架构红线。AI 建议只能进入候选变更，必须经过 Regression Validation、Human Approval 和 Version Release 后才可能影响后续新任务。
- MVP 只做 TuningPacket、PointDiagnostic、ExecutionSummary、ExportConfig 和任务详情页 `AI 调优包` tab；支持生成/导出/复制 TuningPacket，并保存外部 AI 建议文本。Pilot 再做 CrossModelDiagnostic、CrossModelComparison 和结构化 AITuningAdvice；Production Readiness 再做 Regression Center、Release Governance、Rollback、Audit 和 OptimizationEffect。
- MVP AI 调优治理明确不允许自动调用公网 AI、自动生成 AITuningAdvice、自动执行 CrossModelDiagnostic、自动修改 prompt/rule/pattern/field lexicon/CandidateResolver/EvidenceSlot/ModelProfile/ReviewPointDefinition，也不得自动影响当前 ReviewResultSnapshot。
- DefinitionTermIndex 已确认为索引层能力，不属于 Parser，并按需注入 compact definition context。
- MVP 任务日志记录粒度已确认：只记录阶段级事件，不记录每个内部函数调用。阶段级事件用于判断任务卡在解析、索引、计划、证据构建、规则审核、模型辅助或结果合成等阶段。
- 任务日志可记录 `taskId`、`executionId`、`stageName`、`attempt`、事件类型、发生时间、耗时、摘要状态、业务化原因和摘要级 `SYS-*` 诊断；模型阶段只展示调用状态、token 用量、耗时、模型版本、schema 校验和诊断摘要，不展示完整 prompt、完整模型 raw output、endpoint secret、stack trace 或逐函数调试日志。

### 待确认事项

- 正式技术栈、框架、工程目录和测试策略。
- 数据库最终 ERD、迁移策略和字段版本化落地方式。
- API OpenAPI 契约、错误码、认证方式和外部系统调用策略。
- Pilot / Production Readiness 的管理台登录、角色权限、敏感诊断导出审批和审计矩阵。
- 本地 A30/Gemma endpoint、模型版本、部署形态、容量边界和超时预算。
- `usageScope=PRODUCTION_REVIEW` 的公网模型 profile 审批角色、默认关闭策略和审计字段。
- 首批结构化字段的最终英文 key、枚举编码、校验错误码和自定义字段权限细节。
- 税率是否需要新增固定 CandidateRole，或仅作为 optional raw candidate 进入诊断。
- 样本集来源、脱敏流程、评测环境和数据 owner。
- `.doc` 后续开发目标阶段和验收标准。
- 外币合同目标阶段、币种范围和汇率语义。

### 已更新文档

- `PRD.md`
- `CURRENT_CONTEXT.md`
- `docs/frontend.md`
- `docs/backend.md`
- `docs/database.md`
- `docs/sap.md`
- `docs/ai-review.md`
- `decisions/ADR-006-model-profile-switching-and-public-provider-scope.md`
- `decisions/ADR-007-tuning-packet-architecture.md`
- `decisions/ADR-008-definition-term-index.md`
- `decisions/ADR-009-ai-tuning-governance.md`
- `decisions/ADR-005-first-review-points-selection.md`
- `tasks/active/TASK-003-first-review-points-selection.md`
- `changelog/2026-06.md`

### 未完成讨论

- 任务详情页信息架构仍需继续确认，包括任务日志、解析质量、EvidencePacket 摘要、模型辅助状态和结果快照入口；顶部概览区已确认需要展示审核模型。
- 首批 `ReviewPointDefinition` 草稿尚未生成。
- 最小 OpenAPI 契约尚未生成。
- Word parser baseline plan 尚未完成。
- Model gateway and budget baseline 尚未完成。
- 当前仍不建议开始 scaffold。

## 待确认

- 正式技术栈、语言、框架和项目目录。
- 数据库实体最终 ERD 和迁移策略。
- API 具体 OpenAPI 契约。
- Pilot / Production Readiness 的管理台登录、角色权限、敏感诊断导出审批和审计矩阵。
- 具体一期规则集、字段词库和正则边界。
- 样本集来源、脱敏流程、评测环境和数据 owner。
- 最小任务创建 API 的请求字段、错误码和访问控制。
- 首批结构化字段的最终英文 key、枚举编码、校验错误码和自定义字段权限。
- 税率是否需要新增固定 CandidateRole，或仅作为 optional raw candidate 进入诊断。
- SAP/OA/采购系统的实际集成方式、认证方式和回调/轮询策略。
- 本地 A30/Gemma endpoint、模型版本、部署形态和容量边界。
- 生产、预生产、测试环境的部署拓扑。
- `.doc` 后续开发的目标阶段和验收标准。
- 外币合同的目标阶段、币种范围和汇率语义。

## 当前下一步

1. 人工确认 `TASK-001-v1-mvp-scope-gate` 的 MVP / Pilot / Production Readiness 分层。
2. 基于已 Accepted 的 `ADR-005-first-review-points-selection` 创建首批 `ReviewPointDefinition` 草案任务。
3. 继续执行 `TASK-004-word-parser-baseline-plan`。
4. 继续执行 `TASK-005-model-gateway-and-budget-baseline`。
5. 创建首批 `ReviewPointDefinition` 草案任务，基于 `ADR-005` 定义 9 个审核点的字段、EvidenceSlot、策略和降级规则。
6. 创建最小 OpenAPI 契约任务，基于 `ADR-004` 定义任务创建、状态查询和结果 URL 的第一版边界。
7. 人工确认 `ADR-001`、`ADR-002` 和 `ADR-003` 后，再执行 `TASK-006-scaffold-only-after-adr`。

## 当前禁止推进

在以下内容确认前，不开始业务功能开发：

- 正式技术栈。
- 项目目录结构。
- V1 MVP 范围。
- 第一批任务包和首批审核点基线。
- 关键 ADR。
- 数据库迁移策略。
- 本地 Gemma/A30 接入方式。
