# docs/ai-review.md

## 审核策略

审核模型通过 `Model Profile` 配置和绑定。当前已发布的普通任务链路只使用：

```text
规则/正则 + MVP_DEMO_MOCK binding + 后端裁判
```

管理台可创建 PUBLIC `EVALUATION` 的 immutable config version、检查
Secret Reference readiness 并执行受控 connectivity test，但 PUBLIC profile 必须保持
disabled/unbound，普通 Task Creation、Demo、内部审核和真实合同均不能选择它。
PUBLIC runtime 只有在独立 Provider gate、execution-scoped activation 和 shadow
审计全部通过后，才允许由内部 activation-aware EVALUATION resolver 消费；这不会
改变普通默认模型。

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
- MVP 不提供提示词模板编辑，不展示完整最终 prompt。ADR-018 允许管理台创建 PUBLIC
  `EVALUATION` 的新 immutable Model Profile config version、查看 Secret Reference
  readiness 并发起受控连通测试；仍不得编辑 prompt、读取/粘贴 raw KEY、输入任意
  endpoint 或启用/绑定 PUBLIC execution。该基础能力不等于 Provider runtime。

## TASK-EVAL-002 双轨盲态语义评测

盲评使用三份已脱敏 DOCX、冻结的结构化输入和 9 个审核点定义。人工 ground truth 与
CQCP actual 在模型执行前封存；模型输出统一称为“模型意见”，不得回写人工标准答案、
fixture、规则或正式结果。

- Track A 使用保留标题、段落和表格结构的全文盲态投影，只暴露 opaque location ID，
  不包含 CQCP `blockId`、actual、expected 或人工结论。
- Track B 必须使用与未来运行时同构的局部 EvidencePacket，只评估
  role/candidate/anchor/abstention，不让模型给最终 Finding。为保持
  runtime-isomorphic，Track B 保留真实 `SourceAnchor.blockId/previewElementRef`；
  Track A 的 opaque location 规则不适用于 Track B。
- Track A 表现不能替代 Track B 的 model-assist admission、TASK-034 candidate/anchor
  门禁或 TASK-036 runtime seam。

2026-07-28 的冻结运行中，三个无历史上下文 Codex blind-eval agent 各处理一份样本；
解盲后 27/27 Codex 模型意见与人工 ground truth 一致，CQCP deterministic 对照为
9/27。该数字只描述本次 Track A 对照，不证明模型运行可行性或根因；现有证据不足以在
parser、候选抽取、CandidateResolver、SourceAnchor、后端裁判与模型消歧之间做唯一
归因，因此根因保持 `UNRESOLVED`。

2026-07-29 的 D1/v29 R7 工件为 27/27 candidate `MATCH`、27/27
`PointStatus=PASS`、57 `MATCHED` + 6 human `EXCLUDED`、0 SYS/Finding。
D2 已实现纯规则 eligibility、FamilyModelCallPlan 与 runtime-isomorphic
EvidencePacket seam；R7 三份包共 27 packet、9 family plan、57 candidate
occurrence。由于 27 点全部 deterministic HIGH，Track B Codex 意见 27/27 均为
`DETERMINISTIC_HIGH_ZERO_CALL` abstention，结论固定为
`NOT_ESTABLISHED_ZERO_ELIGIBLE_SAMPLE`：证明当前语料正确零调用，不证明
Provider 能处理 eligible ambiguity。

2026-07-29，三份样本已取得逐 input SHA-256 绑定的公网外发授权；DeepSeek
Track A 随后只向官方 `api.deepseek.com` 发送对应盲态投影，并以
`deepseek-v4-pro` 完成严格 schema 评测。最终三份 accepted opinion 均为
`finish_reason=stop`，解盲后 27/27 与人工 ground truth 一致；该结果仍只属于
Track A 模型意见，不能替代 Track B admission。授权 artifact 不存在或 hash
不匹配时，runner 仍必须在任何网络访问前以
`EXTERNAL_EGRESS_NOT_AUTHORIZED` fail closed。仓库外 KEY 或 `/models`
连通成功不等于外发授权；DeepSeek EVALUATION 也不得冒充 Gemma profile。
Track A freeze manifest 必须保持 authorization-neutral；实际授权通过独立、逐
sample/input hash、endpoint、model、purpose 与有效期绑定的 overlay 提供。

旧 18 packet Track B corpus 已按 runtime identity 形成 15 个 eligible input 与 3 个
zero-call control。项目负责人先确认人工答案，模型执行后再解盲；结果为 Codex 15/15、
DeepSeek 6/15、zero-call 3/3，因此结论固定为
`NO_GO_MODEL_MISMATCH / providerAdmission=NOT_ESTABLISHED`。该 corpus 已解盲，后续
只能作为回归证据，不能在同一答案集上调参后重新声明独立 admission。

DeepSeek 评测固定 non-streaming JSON mode，prompt 明确要求 JSON；只接纳
`finish_reason=stop` 且 strict schema valid 的 content。空内容、reasoning-only、
额外字段、非法 enum 或其他 finish reason 均拒绝；不得保存或回显
`reasoning_content`。Track B 局部职责不需要 thinking，请求显式
`thinking={"type":"disabled"}`，模型不得生成或改变最终 Finding/verdict。

首个模型未见 12-packet holdout 已先完成人工 ground truth 封印并正式执行。Codex
opinion 已封存但未解盲；DeepSeek 第 2 个 eligible call 因 schema invalid fail closed，
one-time claim 已消费且无 accepted opinion。该 holdout 永久不可重试，Provider
admission 仍为 `NOT_ESTABLISHED`。

项目负责人已通过 TASK-EVAL-003 / ADR-023 授权一个新的 successor admission：仍为
9 个 MEDIUM/CONFLICTED eligible 与 3 个 zero-call control，但 identity、候选文本和值
必须与旧 18-packet corpus 和失败 holdout 完全 disjoint。用户只负责人工答案、重大
范围变化和最终 merge；其余 standing-grant 范围内执行由主 Codex 连续推进。正式
Codex auditors 必须为全新 `fork_turns="none"`，显式使用
`gpt-5.6-sol / reasoning_effort=xhigh`。

successor pre-seal 已冻结：12 packets / 9 eligible / 3 controls，且与两套 prior
corpus 的 identity、candidate values、evidence texts overlap 全部为 0。当前 challenge
为 `TBS1-5bfbe8b667944e7182df3ec537d45725`；在项目负责人确认 12 条人工 decisions
前，human seal、model input、dispatch、claim 与网络调用均不存在。

该 holdout 不沿用旧 run-v3 的 family 分组：9 个 eligible packet 各形成一个与 runtime
同构的单 packet request，固定为 `9 calls / 9 inputs / 3 excluded controls`。项目负责人
已为 `MILESTONE-MVP-002` 授予 standing egress grant；机器门禁见 `ADR-022`。standing
grant 不替代人工 ground truth 封印，每次真实执行仍须在网络前自动派生绑定 actual
input、dispatch、provider call set、每个 outbound request、模型、调用数和时间的
receipt。用户负责输入脱敏把关，本轮不建设程序化脱敏平台；人工 ground truth、CQCP
actual/expected、最终 Finding/verdict、Secret/raw KEY 仍不得进入 payload。

Provider A0、adapter、PUBLIC binding、`REVIEWING_MODEL` 与 shadow runtime 均不在
当前 successor integration unit；在 successor admission 与三方审计 GO 前继续保持
disabled/unbound 与 `NOT_ESTABLISHED`。

TASK-EVAL-003 的唯一正式 claim 随后因错误 Secret 候选在第一个 call 认证失败并终态
BLOCKED。ADR-024 不允许重跑该 claim；它只批准 TASK-EVAL-004 的最后一个独立机会。
新任务必须先以显式 `DEEPSEEK_OFFICIAL_EVAL` Secret Reference 完成 hash-bound
connectivity gate：官方 `/models` 包含 `deepseek-v4-pro`，并由该 exact model 对全合成
输入完成 thinking-disabled、non-streaming、strict JSON、`finish_reason=stop` 探针。
connectivity evidence 不保存 KEY、Secret hash、raw request/response 或 reasoning，且
明确 `formalAdmissionAffected=false`。gate 成功不能替代人工先封印、第四套 corpus
独立性、9×1 Track B admission 或三方全零审计。

TASK-EVAL-004 随后由项目负责人绑定 challenge/corpus/review SHA 确认 12 条人工
decisions，并在任何 evaluator 前形成 human seal。9×1 dispatch 与全新 Codex blind
opinion 均已封存；唯一 DeepSeek `deepseek-v4-pro` claim 在第 8 个单包 call 因
`OPINION_SCHEMA_INVALID` fail closed，前 7 个 calls 完成且无自动重试。由于没有完整
accepted DeepSeek opinion，执行不得解盲、不得重试或调 prompt，Provider admission
保持 `NOT_ESTABLISHED`，A0/A1/A2 不启动。

项目负责人随后批准 ADR-025 / TASK-EVAL-005，作为最后一次、有限的 schema 稳定性
诊断与独立 admission。TASK-EVAL-004 仍不可重试、补跑或解盲。新任务先在与前四套
corpus 完全 disjoint 的合成诊断集上运行旧版本 baseline，并最多测试两个版本化候选；
单一候选只有在两个独立 12-call pass 上累计 24/24 strict accepted、全部失败分类为 0
时才能冻结。诊断总上限 60 calls，只保存 request/config hash、accepted 与聚合拒绝
类别，不保存 raw request/response/content/reasoning 或 Secret。

TASK-EVAL-005 的单包 prompt/schema v2 在合成诊断上达到 24/24 strict accepted；第五套
正式 9×1 DeepSeek 调用也达到 schema 9/9 accepted，证明 schema 稳定性门禁通过。但
解盲后 DeepSeek 对 4 个 CONFLICTED packet 的 role/candidate/anchor/abstention 与人工
答案不一致，四项均为 55.56%；Codex 同批意见全维 100%。因此 admission seal 为
`SEALED_NO_GO_MODEL_MISMATCH`，Provider admission `NOT_ESTABLISHED`。该 claim 不得
重试或改写，A0/A1/A2 继续禁止。

稳定性 GO 后才生成第五套独立 12-packet corpus，并在任何 evaluator/model 访问前由
项目负责人绑定实际 hash 确认 12 条人工答案。正式 admission 仍固定
`deepseek-v4-pro`、9×1 calls、3 个 zero-call controls、全维 100%；失败即终止，不建
第六套。该历史规则已完成并终态封存。

项目负责人随后澄清此前 BLOCKED 表述只是询问，并接受 ADR-026 / TASK-EVAL-006 的
一次有限 Provider 会话投影恢复。v3 model-facing projection 只保留 review point、
requested role、候选 occurrence、证据、可靠 anchor、预算和输出契约；runtime routing、
diagnostic、identity/admission label、人工 ground truth、actual/expected、Finding/verdict
均不得进入模型输入。先以旧第五套 4 个 CONFLICTED packet 做 4-call non-admission
diagnostic；只有 4/4 才创建第六套独立 12-packet corpus并等待新的人工先封印。正式
admission 仍为 9×1、3 controls zero-call、全维 100%；失败不建第七套。A0/A1/A2 继续
等待 admission、verification/freeze 和三方全零 GO。

TASK-EVAL-006 实际恢复结果为：旧 4 个 CONFLICTED packet 的 non-admission diagnostic
六维 4/4；第六套 disjoint corpus 在人工先封印后，由全新 Codex evaluator 与
`deepseek-v4-pro` 分别完成 9 条 blind opinion。两者的 schema、可靠 anchor、role、
candidate、anchor、abstention 均为 9/9，3 个 controls 全部 zero-call；admission seal
`2d879b13…` 为历史模型评测派生结果。全维 9/9 只建立
`modelEvaluationPassed=true`，不建立 EVALUATION shadow 运行资格；模型仍不得生成或改变
Finding/verdict，PUBLIC profile 仍 disabled/unbound。完整 verification、immutable
freeze、三方全零 GO 与 CI 内容一致性前，`providerAdmissionEstablished=false` 且不得启动
A0/A1/A2。

TASK-EVAL-006 首次 R5 freeze 后，独立审计指出原 Codex opinion 只有“未读人工答案”的
自述，缺少 evaluator 启动/访问时序证据；同时旧 Java 测试只覆盖 holdout-v1。有限修复
使用 human seal 之后预创建的 execution claim、全新 `fork_turns=none` evaluator 的
零文件 readiness、隔离四文件 allowlist、launch/completion receipt 形成可重建时间链；
DeepSeek 正式 claim/opinion 不重跑。新增 recovery-v1 Java test 通过真实
`RuntimeEvidencePacketBuilder` 逐字核对 12 个 packet、identity、admission 与 anchor。
该修复的 subject `e77a8635…` 又因 opinion 顶层字段、DeepSeek reuse、架构状态与任务地图
四项 blocking finding 被拒绝。R6 使用 exact-field-set、旧 seal hash 复用校验，并将更正
后的 seal 状态固定为
`SEALED_GO_TRACK_B_RECOVERY_EVALUATION_REVALIDATED_PENDING_AUDIT`；新三方全零 GO 与
CI 内容一致性前仍不启动 A0/A1/A2。R6 完整 verification 已达到 Node `56/56`、Java
runtime seam `1/1` 且 `5 executed`、verification builder `1/1`；该结果只放行新 freeze
与三审。R6 subject `a9daf62f…` 的两个 Codex auditor 全零 GO，但 CC 因隔离包从 Windows
worktree 复制出 9 个 CRLF/mixed bytes、且 evidence/hash-only exclusion/source context
未显式分区而返回 `NO_GO / P0=1 / P2=1`，所以正式 admission 仍未建立。R7 只用仓库外
一次性 builder 修本轮 audit projection：从 frozen Git blob 导出精确字节，三类集合
完整、互斥、可复算；人工 ground truth 和 comparison report 内容继续禁止进入 CC 包。该 transport
修复不重跑 DeepSeek、不改变 model evaluation 或最终 Finding/verdict 边界。
R7 完整 verification 已通过：Node `56/56`、Java runtime seam `1/1` 且 `5 executed`、
verification builder `1/1`、Secret/raw Provider、CR=0 与 diff 门禁全绿。唯一历史 CR hit
为 R6 CC status 元数据，原 SHA 已在 NO_GO 摘要保留；raw CC report 未改写。首次 freeze
前置校验因 verification 脚本工作区 CRLF/Git blob LF 不一致而在创建 subject 前停止；精确
LF 属性修复并强制全量重跑后的 verification result `1805feb1…`、console manifest
`8aba6687…`，仍只放行新 freeze 与三方重审。
R7 subject `47d84299…` 的三审随后发现新的 package isolation P0：完整 full diff 绕过
hash-only exclusion，将 ground truth/comparison text patch 放入 CC 包，因此 CC GO 失效。
R8 限定修复移除 full content diff，仅发送 status/path/base+HEAD Git blob OID/size inventory；
仓库外 fail-closed verifier 对 72 个包文件扫描 12 个禁止路径的 HEAD blob 与逐 path diff 表示，
共 24 项均无命中。该 diagnostic 不构成审计 GO；正式 admission 仍等待新 verification、freeze、
CC 与两个全新 Codex auditor 全零 GO及 CI 内容一致性。R8 正式 verification 已达到 Node
`56/56`、Java `1/1` 且 `5 executed`、Secret/raw Provider/CR/diff/builder 全绿；result
`3297f5c3…`、console `50288e22…`、evidence `42`，当前只放行 clean commit 和新 freeze。
正式 R8 subject `a85e96f5…` / manifest `08a0bd23…` 为 325 changed paths、65 evidence；
CC 包实际为 77 total files、sums `16097ea7…`，独立扫描 24 forbidden representations 为 0。
CC 对隔离和产品证据无 finding，但因上述正式事实未在冻结前同步到任务/项目记忆，返回
`NO_GO / P2=1 / blocking=1`；两个 Codex auditor 中断。本轮不能复用，admission 仍 pending。
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
