# docs/ai-review.md

## 审核策略

审核模型通过 `Model Profile` 配置和绑定。默认可使用：

```text
规则/正则 + Model Profile（本地 Gemma4 / 公网兼容 OpenAI API / mock fallback）+ 后端裁判
```

管理员可启用多个模型 profile，并通过默认模型、启用状态、密钥配置状态和 `usageScope` 控制新任务使用哪个审核模型。公网模型在业务允许的场景下可用于审核链路验证、演示、评测或内部审核任务；能否用于真实合同由业务方、管理员和部署环境承担配置责任。

公网模型和本地模型都不得直接决定生产 finding。模型输出只作为局部抽取、候选归属、证据选择或语义辅助，最终点级状态由后端裁判与结果合成模块决定。

## 模型边界

- Model Profile 指向的模型默认负责局部抽取、证据选择和复杂语义辅助。
- 结构化比对和确定性最终裁判由后端完成。
- 模型不吃整份合同。
- 模型不一次性裁判全部审核点。
- 规则/正则能处理的审核点不进入模型 token 预算。
- 模型输出只提供候选、归属、语义辅助或候选 warning。

## EvidencePacket 与预算

长合同可能达到 90k-120k 中文字符。V2 不应把合同按固定长度粗切后直接交给模型，也不应让每个审核点独立重复构造大包。

核心原则：

- 不以“全文切成多个 15k 包”作为默认审核方式。
- 先构建全局索引和候选证据，再按审核点或审核点族选择局部证据。
- 相关审核点可以共享证据包，避免重复发送相同上下文。
- 每次模型调用必须有明确 token budget、截断策略和 coverageStatus。

证据上下文三层：

- Global Contract Index：全文级轻量索引，不直接进入模型 prompt。
- EvidenceBundle：可被多个相关审核点共享的证据集合。
- PointEvidenceOverlay：某个审核点额外需要的少量补充证据、结构化字段和输出要求。

一期建议的审核点族包括：

- `PARTY_FIELDS`
- `AMOUNT_TAX`
- `PAYMENT_TERMS`

模型 prompt 默认由以下内容组成：

- task metadata 摘要。
- contractTypeProfile 摘要。
- relevant structured fields。
- shared EvidenceBundle。
- point-specific PointEvidenceOverlay。
- strict JSON schema。

## FamilyEvidencePlan

构造族级 evidence 前必须先生成：

- `family`
- `plannedReviewPoints`
- `requiredSlotMatrix`
- `candidatePool`
- `selectedBundleBlocks`
- `uncoveredSlots`
- `coverageByPoint`

默认规模边界：

- `maxReviewPointsPerFamilyPlan = 12`
- `maxSlotsPerReviewPoint = 5`
- `maxCandidatesPerSlot = 5`

不得仅因 shard 边界丢弃 core point。

截断优先级固定：

1. core review point critical slots。
2. high severity point required slots。
3. same-family shared context。
4. non-core point slots。

预算不足时低优先级点 `coverageByPoint=PARTIAL`，后续进入 `NOT_CONCLUDED` 或在预算 profile 允许时进入 second pass。

family primary 模型调用不直接“授予某一个 shard”。系统先从全部 shards 汇总获得模型辅助资格的 requested roles 和 required evidence，再按 family budget 构造一次 `FamilyModelCallPlan`。

`requestedRoles[]` 的模型辅助资格：

- HIGH role：默认不请求模型；仅当 `SEMANTIC_INTERPRETATION + LLM_SEMANTIC_WITH_GUARD` 时请求。
- MEDIUM role：required/critical slot 且 `resolverPolicy=gemmaIfAmbiguous` 时请求。
- CONFLICTED role：required/critical slot 且冲突候选具有可比较 anchor/context 时请求。
- LOW role：默认不请求；仅当存在最低门槛候选且 `resolverPolicy=gemmaIfAmbiguous` 时请求。
- UNKNOWN/no candidate：不请求模型，输出 `SYS-INDEX-INCOMPLETE`。

模型无法消歧时必须保留 `SYS-ROLE-CONFLICT`，不得强行选边。

## Model Assist

`modelAssistPolicy` 至少声明：

- `mode: NONE / AMBIGUITY_RESOLUTION / SEMANTIC_INTERPRETATION`
- `modelAssistPriority: 0..10`

配置校验：

- `executionStrategy=LLM_SEMANTIC_WITH_GUARD` 时，`modelAssistPolicy.mode` 必须为 `SEMANTIC_INTERPRETATION`。
- `executionStrategy=LLM_EXTRACT_THEN_RULE` 时，`modelAssistPolicy.mode` 必须为 `AMBIGUITY_RESOLUTION`。
- 其他 deterministic strategy 默认 `NONE`；若声明其他 mode，发布校验失败。

## Gemma Artifact

模型输出必须经过 schema validation。缺字段、JSON 错误、anchor 缺失或 schema 不匹配，一律转为 `SYS-MODEL-OUTPUT-*`，不得进入业务裁判。

Artifact 复用规则：

- exact `inputBlockIdsHash` match -> reusable。
- `requestedBlockIds` subset of `artifact.inputBlockIds` -> reusable。
- same blocks but different prompt/schema/model -> not reusable。
- new blocks required -> second pass or `NOT_CONCLUDED`。

一期只做同 task、同 execution 语义下的 artifact 复用，不做跨任务复用。

second pass 只用于补充缺失 slot，不重新生成整个族 artifact：

- `FamilyPrimaryArtifact`：第一次族级模型辅助结果。
- `PointSupplementArtifact`：绑定具体审核点和缺失 slot 的补充结果。

若 primary 与 supplement 对同一 role / slot 输出矛盾，后端不得裁判，输出 `SYS-MODEL-CONFLICT`。

supplement 的 `requestedRoles[]` 只能包含 primary artifact 的 `uncoveredRoles` 或当前点仍缺失的 roles，不得重新请求 primary 已覆盖的全部 roles。

## 合成优先级

后端合成优先级固定：

1. 硬公式和确定性规则优先于模型语义判断。
2. 模型只提供候选、归属、语义辅助或候选 warning。
3. 规则 PASS + 模型 WARNING 时，不直接升级业务风险。
4. 证据不足或模型输出不完整时，不生成 ERROR。

`WARNING_CANDIDATE` 默认不进入普通结果页。默认 `allowedWarningTypes = NONE`，模型主动发现的新疑点先进入管理台 `MODEL_SUGGESTION_DIAGNOSTIC`。

## Quality Copilot

AI 调优治理必须与生产审核链路隔离。Quality Copilot / 公网 AI / 本地模型输出的任何建议都只是候选建议，不是生产变更。

核心红线：

```text
AI Advice != Production Change
```

职责：

- 失败样本归因。
- 生成候选 prompt。
- 生成候选正则/词库。
- 生成候选规则。
- 生成合成回归样本。
- 对比本地 Gemma 与候选方案输出。
- 解释评测报告。

上线门禁：

```text
候选方案 -> 离线评测 -> 回归样本验证 -> 管理员批准 -> 发布版本 -> 生产生效
```

管理台候选发布流程：

```text
DRAFT -> EVALUATING -> READY_FOR_REVIEW -> APPROVED / REJECTED -> PUBLISHED
```

Quality Copilot 不负责：

- 直接修改生产规则。
- 直接决定业务 finding。
- 绕过管理员批准。
- 处理未经授权的真实敏感合同全文。
- 直接修改 CandidateResolver、EvidenceSlot、ModelProfile 或后端确定性裁判。
- 影响当前正式 `ReviewResultSnapshot`。

Quality Copilot 读取失败样本前必须经过脱敏策略和最小化裁剪。脱敏未通过或无法确认时，公网模型不得处理该样本。

### Tuning Packet

MVP 新增 Tuning Packet 作为 AI 调优治理链路的数据底座。它基于正式 execution 产物生成，用于复制给 DeepSeek、ChatGPT、Claude 或其他 AI 辅助诊断，但不改变正式审核结果。

MVP 最小能力：

- `TuningPacket`
- `PointDiagnostic`
- `ExecutionSummary`
- `ExportConfig`
- 任务详情页 `AI 调优包` tab。

默认导出模式：

- `SINGLE_POINT`
- `FOCUSED`

`FULL` 仅限受控内部场景，不默认给普通管理员。

MVP 允许：

- 生成 TuningPacket。
- 导出 TuningPacket。
- 复制到外部 AI。
- 保存外部 AI 建议文本。

MVP 不允许：

- 自动调用公网 AI。
- 自动生成正式 `AITuningAdvice`。
- 自动执行 `CrossModelDiagnostic`。
- 自动修改 prompt、rule、pattern、field lexicon、CandidateResolver、EvidenceSlot、ModelProfile 或 ReviewPointDefinition。
- 自动影响当前 `ReviewResultSnapshot`。

### Cross-Model Diagnostic

Cross-Model Diagnostic 延后到 Pilot。它使用同一份局部 EvidencePacket / ModelCallIntent 调用另一个 Model Profile 做诊断对比，并生成 `CrossModelComparison`。

比较前必须记录输入等价性：

```text
STRICT
EXPANDED_WITH_POLICY
DIFFERENT_INPUT
```

只有 `STRICT` 才能直接比较模型能力差异。输入不等价时，不得直接归因为模型能力差距。

### DefinitionTermIndex

DefinitionTermIndex 是索引层能力，不属于 Parser。Parser 负责产出结构化 block、table、section 和 control；DefinitionTermIndex 在解析后与 CandidateIndex 并行构建。

定义条款命中后，只按相关审核点或 ReviewPointFamily 需要注入 compact definition context，不默认注入每个 EvidenceBundle。定义缺失、预算截断或定义冲突应进入 PointDiagnostic 和 Tuning Packet，影响结论时输出 `NOT_CONCLUDED`，不得猜测定义。

## 脱敏与输出保存

一期默认保存策略：

- MVP 不保存完整模型原始输出；受控保存完整 redacted raw output、TTL 和审计展开延后到 Pilot / Production Readiness。
- encryptedRawOutput TTL 7 days: enabled for admin/evaluation tasks。
- encryptedRawOutput TTL 7 days: disabled for normal external tasks unless caller policy enables it。

脱敏规则属于平台安全配置，不属于业务规则集，但必须版本化并测试。

`RedactionPolicyVersion` 除回归测试外，至少每季度执行一次对抗性敏感信息测试，覆盖身份证、手机号、银行卡、IBAN/SWIFT、邮箱、地址、人名、账号和自定义敏感词。

一期最低实现是静态规则测试集、合成攻击样本和人工抽查；不要求自研完整 DLP 或自动渗透平台。

## 模型运行保护

模型重试和 circuit breaker 详见 `docs/deployment.md`。AI 审核侧需要记录：

- `ModelCallIntent`。
- `operationalCallAttempts`。
- `successfulModelCallsUsed`。
- prompt/schema/model 版本。
- artifact 复用或失败原因。

## 首批审核点选择基线

`TASK-003-first-review-points-selection` 形成首批审核点选择基线，已记录到 `decisions/ADR-005-first-review-points-selection.md`，状态为 `Accepted`。

MVP 首批审核点固定为以下 9 个 core review point：

| ReviewPointFamily | 审核点 | core | 策略摘要 |
| --- | --- | --- | --- |
| `PARTY_FIELDS` | `PARTY_A_NAME_CONSISTENCY` | 是 | 结构化甲方名称 + 主体候选 + 后端确定性比对；Gemma 只做歧义归属辅助 |
| `PARTY_FIELDS` | `PARTY_B_NAME_CONSISTENCY` | 是 | 结构化乙方名称 + 主体候选 + 后端确定性比对；Gemma 只做歧义归属辅助 |
| `AMOUNT_TAX` | `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` | 是 | 金额 ValueGrammar / 正则候选 + 后端金额归一化比对 |
| `AMOUNT_TAX` | `TAX_AMOUNT_FORMULA_CONSISTENCY` | 是 | 后端公式裁判；字段或证据不足不生成业务 finding |
| `PAYMENT_TERMS` | `PREPAYMENT_RATIO_CONSISTENCY` | 是 | 比例 ValueGrammar / 付款条款候选 + 后端比对；Gemma 只做预付款归属歧义辅助 |
| `PAYMENT_TERMS` | `PROGRESS_PAYMENT_RATIO_CONSISTENCY` | 是 | 累计进度款比例候选 + 后端比对；Gemma 只做角色歧义辅助 |
| `PAYMENT_TERMS` | `COMPLETION_PAYMENT_RATIO_CONSISTENCY` | 是 | 累计竣工款比例候选 + 后端比对；Gemma 只做角色歧义辅助 |
| `PAYMENT_TERMS` | `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY` | 是 | 累计结算款比例候选 + 后端比对；Gemma 只做角色歧义辅助 |
| `PAYMENT_TERMS` | `WARRANTY_RETENTION_RATIO_CONSISTENCY` | 是 | 独立质保款比例候选 + 后端比对；Gemma 只做角色歧义辅助 |

MVP 不扩展到完整付款条件语义审核，不新增审核点族。五个月度付款比例均作为 core，执行结构化字段与合同候选的一致性审核，但不裁判比例之间的跨字段关系。

MVP 首批合同类型只做“工程采购合同”。首批 9 个审核点默认适用于工程采购合同。材料供货合同、费用合同、杂项合同延后到 Pilot 或后续阶段；MVP 不做复杂合同类型路由。

当 `paymentMethod=MILESTONE` 时，仍执行 `PREPAYMENT_RATIO_CONSISTENCY`；其余四个付款比例点按适用性输出 `SKIPPED`，不视为证据不足或系统失败。

`TAX_AMOUNT_FORMULA_CONSISTENCY` 使用固定 `CandidateRole=taxRate` 作为 optional EvidenceSlot 候选角色。税率候选可来自 `RatioIndex`，但必须经 CandidateResolver 可靠归属为税率；税率缺失或不可靠时不执行 `taxAmount = taxExcludedAmount × taxRate` 弱校验，不得生成无可靠证据的业务 finding。

MVP 第一版审核执行逻辑由项目团队预置生成，不由管理台用户生成。若审核结果不理想，应通过版本化迭代规则、词库、正则、ValueGrammar、EvidenceSlot policy、Gemma 局部 prompt 和后端裁判来优化，并用脱敏/合成样本回归验证。

MVP 管理台只展示业务可读的执行逻辑摘要，不展示完整 prompt、正则或 EvidenceSlot 细节。完整技术治理视图延后。

候选归属不清时可调用 Gemma 做局部辅助，但输入仅限相关证据块和必要上下文，不得提交整份合同。Gemma 输出只可作为 CandidateResolver 的辅助信号，不得直接生成最终 `PASS / ERROR / WARNING`。
Gemma 辅助后仍无法可靠归属时，必须输出 `NOT_CONCLUDED`，业务原因说明“候选归属仍不可靠”。不得猜测候选继续裁判。
Gemma 不可用或超时时，不得阻断所有审核点。若规则/候选本身已达到可靠标准，后端可继续确定性裁判；若当前点依赖 Gemma 消歧且 Gemma 不可用，则该点输出 `NOT_CONCLUDED`。

首批通用降级规则：

- required structured field 缺失：核心输入字段输出 `SYS-MISSING-INPUT`；可选类审核点按定义 `SKIPPED`。
- required EvidenceSlot 缺失：输出 `SYS-INDEX-INCOMPLETE`，不得回灌全文。
- 候选角色冲突或证据歧义：输出 `SYS-ROLE-CONFLICT` 或 `SYS-EVIDENCE-AMBIGUOUS`。
- token 预算截断关键证据：输出 `SYS-EVIDENCE-BUDGET-EXCEEDED`。
- 解析低置信：受影响点输出 `SYS-PARSE-LOW-CONFIDENCE`。
- Gemma 不可用、超时、输出不完整或 primary/supplement 冲突：输出对应 `SYS-MODEL-*`，不得生成业务 finding。
- 普通结果页不展示内部 `SYS-MODEL-*` 技术码，只展示业务化 `NOT_CONCLUDED` 原因。管理台任务详情和评测报告展示模型调用状态、超时/不可用原因和诊断摘要。
- MVP 管理台不展示完整模型原始输出。模型运行记录只保存模型调用状态、`SYS-MODEL-*` 诊断码、schema 校验结果、redacted artifact 摘要、token 用量、耗时、模型版本、调用时间和必要的候选归属结论摘要。
- MVP 不提供提示词模板编辑，不展示完整最终 prompt。模型配置只读展示当前模型版本和 endpoint 状态；可编辑模型配置、提示词模板和最终 prompt 预览延后到后续治理/技术视图。

## 基线冻结文档

- 模型网关、模型调用记录、预算与降级策略的 MVP 冻结结论见 `docs/model-gateway-budget-baseline.md`
- Word parser provenance、preview mapping 与 DefinitionTermIndex 依赖边界见 `docs/word-parser-mvp-boundary.md`

## 待确认

- 本地 A30/Gemma endpoint。
- 模型版本命名和发布流程。
- prompt/schema 版本管理方式。
- Quality Copilot 使用的公网模型和授权边界。
- 脱敏测试集、敏感词策略和安全审批流程。
- 首批 `ReviewPointFamily` 是否只落地 `PARTY_FIELDS`、`AMOUNT_TAX`、`PAYMENT_TERMS`。
- `ReviewBudgetProfile` 的 token 预算数值。
