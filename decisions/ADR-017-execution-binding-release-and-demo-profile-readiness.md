# ADR-017：Execution Binding Release 与 Demo Profile Readiness

状态：Accepted / 独立规格审计 GO

日期：2026-07-24

## 背景

V1 `execution` 表已经要求 14 个不可空的版本与模型字段，但主线没有可供生产任务创建消费的权威绑定源。静态 `RuleSetVersion v20260705.1` 是 `code-current-mapping`，仍为 `DRAFT / NOT_BOUND`；Model Profile、ReviewBudgetProfile、parser release 与 model-output schema release 也没有完整持久化来源。

`TASK-MVP-001` 不允许以假值、test-only 常量或散落硬编码填充首个 Execution。与此同时，Demo 需要一个不依赖真实 secret 的稳定模型 profile，架构又要求首次启动 seed `STANDARD / DEEP_REVIEW / EVALUATION`。

## 适用范围

* 影响模块：PostgreSQL schema、Task Creation 前置 binding、Model Profile readiness、ReviewBudgetProfile、Execution/ReviewResultSnapshot 版本追溯。
* 是否影响外部 API：否。
* 是否影响数据库：是，新增 V2 migration 与三类不可变版本表/seed。
* 是否改变审核语义：否，仍使用 code-current legacy 行为。
* 是否激活 review-assets loader：否。

## 决策

### 1. 使用部署级不可变 Execution Binding Release

新增 `execution_binding_release`，由部署/迁移发布，而不是由业务管理员自由编辑。binding identity 与 14 字段 content immutable；只有 `enabled` 属于 lifecycle state。它不是第四类业务配置包；一期三类业务可配置包仍只有：

```text
ContractTypeProfile
RuleSetVersion
ReviewBudgetProfile
```

每条 binding release 一次性记录 V1 `execution` 的全部 14 个 `NOT NULL` 字段，并包含：

```text
bindingVersion
purpose
deploymentScope
contractTypeCode
enabled
effectiveFrom
contentDigest
```

新 execution 创建时选择恰好一条有效 binding，将 14 字段复制进 execution。执行中和历史查询不回查当前默认配置。

一期不在 `execution` 新增 `binding_version`：V1 已有的 14 字段组成实际审计快照，且本任务不伪造对历史 execution 的 binding 归属。binding release 通过 content-immutable version、content digest 与 14 字段精确 tuple 重建来源。

binding 另存 `contractTypeProfileCode` 作为 traceability metadata，不复制到 V1 execution。`contentDigest` 使用以下唯一算法：

1. 按固定顺序构造字符串数组：
   `bindingVersion, purpose, deploymentScope, contractTypeCode, contractTypeProfileCode, contractTypeProfileVersion, ruleSetVersion, reviewBudgetProfileVersion, modelProfileCode, modelConfigVersion, parserVersion, promptVersion, schemaVersion, patternLibraryVersion, fieldLexiconVersion, evidenceSelectorVersion, providerType, modelName, endpointAlias`。
2. 使用代码内专用、默认配置的 Jackson `JsonMapper` 及其默认 JSON string escaping，将该数组序列化为无缩进、无额外空白的 compact JSON array；不得使用可能受应用配置影响的共享 ObjectMapper。
3. 对序列化结果的 UTF-8 bytes 计算 SHA-256，保存小写 64 位 hex。
4. `enabled / effectiveFrom / createdAt` 是 lifecycle/audit metadata，不进入 digest；`modelBudget JSONB` 通过 immutable version reference 间接绑定，不参与本 digest，不存在 JSONB canonical 化歧义。

Java resolver 必须重算并验证 digest，数据库只用 CHECK 验证 64 位小写 hex 形状。任一参与字段变化都必须产生新 `bindingVersion` 与新 digest。

### 2. legacy RuleSetVersion 只表示 code-current 行为

Demo binding 使用：

```text
ruleSetVersion = v20260705.1
contractTypeProfileVersion = v20260705.1
promptVersion = v20260705.1
patternLibraryVersion = v20260705.1
fieldLexiconVersion = v20260705.1
evidenceSelectorVersion = v20260705.1
```

这些 module version 来自 `ruleset-v20260705.1.json` 的静态引用，仅用于 traceability。runtime 不读取该 manifest 驱动裁判；`DRAFT / NOT_BOUND / loaderEnabled=false / productionEffect=NONE` 保持不变。execution 实际绑定的是当前代码中的 legacy 审核行为，不是一次资产发布激活。

TASK-036-C2 未进入主线，本 binding 不允许使用或激活 `v20260715.1`。

外部 OpenAPI、架构和数据库使用 `contractTypeCode=ENGINEERING`；legacy 静态画像文件内唯一 identity 是 `profileCode=ENGINEERING_PROCUREMENT`。本 ADR 冻结窄 alias：

```text
contractTypeCode ENGINEERING
-> contractTypeProfileCode ENGINEERING_PROCUREMENT
-> contractTypeProfileVersion v20260705.1
```

该 alias 只用于本次 code-current legacy traceability，不改变 OpenAPI 枚举，不启用 profile routing。binding 必须保存 `contractTypeProfileCode`，resolver/test 必须同时验证 code、profile identity 与 version；后续正式统一编码必须另发新 binding，不得原地改写。

### 3. ReviewBudgetProfile 使用三个 immutable seed

新增 `review_budget_profile_version`，首次 migration seed：

```text
STANDARD
DEEP_REVIEW
EVALUATION
```

每行至少记录：

```text
profileCode
reviewBudgetProfileVersion
modelBudget
standardToDeepRatio = 5:1
budgetApprovalPolicyVersion
enabled
effectiveFrom
createdAt
```

`STANDARD` 是 Demo binding 使用的 active profile。`DEEP_REVIEW / EVALUATION` 完成结构性 seed，但在形成单独评测和审批前不可作为普通 Demo task 默认值。

三类 seed 允许共享架构文档给出的保守启动 `ModelBudget`。共享数值只说明具备可追溯初始结构，不代表三类质量、SLA 或 token 权益相同；后续差异化必须发布新版本并提供评测/批准证据。

三类 seed 统一冻结：

```text
budgetApprovalPolicyVersion = budget-approval-policy-mvp-v20260724.1
effectiveFrom = 2026-07-24T00:00:00Z
createdAt = 2026-07-24T00:00:00Z
displayName:
  STANDARD = 标准审核
  DEEP_REVIEW = 深度审核（预留）
  EVALUATION = 评测（预留）
```

只有 `STANDARD.enabled=true`；另外两类 `enabled=false`。

### 4. MVP_DEMO_MOCK 使用 provider-specific readiness

新增 `model_profile_config_version`，seed：

```text
profileCode = MVP_DEMO_MOCK
configVersion = model-config-mvp-demo-mock-v20260724.1
displayName = MVP Demo Mock
providerType = MOCK
endpointAlias = mock-local
modelName = cqcp-demo-mock
usageScope = DEMO
enabled = true
isDefaultForNewTask = true
secretRequired = false
readinessStatus = READY
timeoutSeconds = 30
retryCount = 0
effectiveFrom = 2026-07-24T00:00:00Z
createdAt = 2026-07-24T00:00:00Z
```

ADR-006 的 `secretConfigured=true` 门禁适用于需要 secret 的 provider。对 `MOCK`，readiness 由 provider-specific 条件判断；不得为通过门禁伪造 secret 已配置。只有 `DEMO` deployment 可选择该默认 profile，UI/API 必须如实展示 MOCK，不得表述为 Production Ready。

每个 scope 只能有一个 enabled default。binding 中的 `providerType / modelName / endpointAlias` 必须与同一 immutable model config row 完全一致。

`readinessStatus` 最小枚举固定为 `READY / NOT_READY`。本任务 resolver 的 provider matrix：

```text
MOCK -> secretRequired=false 且 readinessStatus=READY 才可用
LOCAL / PUBLIC_OPENAI_COMPATIBLE -> UNSUPPORTED_PROVIDER
```

非 MOCK provider 在本任务中没有权威 secret/endpoint readiness source，不得仅凭一条 `READY` 字符串放行。

### 4.1 Content immutable 与 lifecycle 原子切换

三类版本表均区分：

```text
content immutable:
- identity/version
- budget/model/binding content
- secretRequired
- usageScope
- effectiveFrom

lifecycle mutable:
- enabled
- isDefaultForNewTask（仅 model profile）
- readinessStatus（仅 model profile）
```

新版本发布或回滚必须在单个数据库事务内更新 lifecycle：

1. 锁定目标选择域；
2. 将旧 enabled/default 置为 false；
3. 插入或启用新版本；
4. 提交后才对新 Task Creation 可见。

不得原地修改旧版本 content。`execution_binding_release` 的唯一 partial index 只使用 immutable selector columns 加 `WHERE enabled = true`；`effectiveFrom <= now()` 由 resolver 校验，禁止在 PostgreSQL partial unique index 谓词中使用 `now()`。

本任务不建设发布管理 API；上述事务语义作为后续受控 migration/管理动作的硬约束。seed 是初始状态，不发生切换。

### 5. parser 与 schema 使用独立 code-owned release

新增代码拥有的 release 常量：

```text
parserVersion = parser-docx-word-v20260724.1
schemaVersion = model-output-artifact-v20260724.1
```

`parserVersion` 对应当前 `DocxWordParserSpike` 驱动的 parser-backed 路径；`schemaVersion` 对应当前 `ModelGatewayArtifact` 的受控输出 schema。它们不得复用 OpenAPI `0.1.0`、人工 fixture `v1` 或 `RuleSetVersion v20260705.1`。

resolver 必须验证数据库 binding 与代码 release 常量一致；代码升级未发布新 binding 时 fail closed。

### 6. modelConfigVersion 与 snapshot 字段的映射

V1 字段命名保持兼容：

```text
execution.model_config_version
    == ReviewResultSnapshot.model_profile_version
```

二者表示同一个 immutable Model Profile config version。`model_profile_code` 是稳定 profile identity，不代替 config version。

### 7. Fail-closed 选择规则

resolver 输入：

```text
purpose
deploymentScope
contractTypeCode
```

必须依次验证：

1. 恰好一个 enabled 且已生效的 binding；
2. budget/model 外键存在；
3. 14 字段全部非空；
4. binding 与 model config 的 provider/model/endpoint 完全一致；
5. budget active 且本 Demo 使用 `STANDARD`；
6. model profile enabled、scope 匹配、唯一默认、readiness 为 `READY`；
7. MOCK 不要求 secret，其他 provider 不在本任务内自动判 ready；
8. legacy module version、parser release、schema release 全部匹配。

任一失败都抛出 `ExecutionBindingResolutionException`，携带稳定 reason：

```text
NOT_FOUND
AMBIGUOUS
INACTIVE_OR_NOT_EFFECTIVE
REFERENCE_MISMATCH
PROFILE_NOT_READY
RUNTIME_VERSION_MISMATCH
CONTENT_DIGEST_MISMATCH
UNSUPPORTED_PROVIDER
```

后续 Task Creation 只依赖 reason 做事务/API 错误映射，不解析 message；必须整体回滚，不得 fallback 到内存默认或另一 binding。

## 备选方案

### 方案 A：在任务创建代码中硬编码 14 个值

不采用。来源分散、不可审计，升级时容易形成半新半旧 execution。

### 方案 B：直接把静态 review-assets manifest 当 runtime loader

不采用。当前资产明确 `DRAFT / NOT_BOUND`，且没有发布审批、rollback 或生产 loader 证据。

### 方案 C：只 seed STANDARD

不采用。违反架构首次启动必须 seed 三类 ReviewBudgetProfile 的约束。

### 方案 D：为 MOCK 设置 secretConfigured=true

不采用。会伪造不存在的密钥状态。

## 选择理由

* 一条 binding release 能让 Task Creation 原子复制完整版本 tuple。
* 数据库外键、唯一默认约束和 Java fail-closed 校验共同防止隐式 fallback。
* 保留 legacy code-current 行为，不抢跑 TASK-036-C2。
* MOCK 可稳定支撑 Demo，同时明确排除生产 readiness。
* parser/schema 版本不再借用无关版本号。

## 影响

### 正向影响

* `TASK_SPEC-MVP-001-A` 可以消费真实、可重建的版本与模型来源。
* 历史 execution/snapshot 不随后续 profile 或 binding 变化漂移。
* 三类 budget profile 与 Demo model profile 有明确 seed 和 readiness。

### 代价与风险

* 新增 V2 migration、repository 与 resolver。
* code-owned parser/schema release 与数据库 seed 必须同步发布。
* `execution.model_config_version` 与 snapshot 字段名仍不一致，需要长期保留映射说明。

## 不做什么

* 不实现任务创建事务。
* 不启用 review-assets runtime loader。
* 不修改审核点、规则、CandidateResolver、EvidenceSlot、模型职责或最终裁判。
* 不接入真实 secret、LOCAL/Public provider 或生产 profile。
* 不声明 DEEP_REVIEW / EVALUATION 已具备差异化质量能力。

## 回滚与迁移

* 回滚应用版本时停用新的 Demo binding；已创建 execution 继续使用复制后的 14 字段。
* V2 migration 不改写 V1 execution 或 snapshot 历史行。
* 不删除已发布版本或修改其 content；通过受控事务切换 lifecycle 到新版本/新 binding。
* 如果 parser/schema release 与 binding 不匹配，resolver fail closed，禁止创建新 execution。

## 验证方式

* PostgreSQL 从 V1 顺序应用 V2。
* 查询三类 budget seed、Demo mock profile 与唯一 binding。
* 验证唯一默认、FK、状态和非空约束。
* 按冻结算法重算 content digest，校验 seed 值且任意参与字段变化会改变 digest。
* 验证 lifecycle 切换先撤销旧 enabled/default，再启用新版本；唯一索引不使用 `now()`。
* 验证 `ENGINEERING -> ENGINEERING_PROCUREMENT -> v20260705.1` alias。
* 单元测试覆盖 0/2 candidate、readiness、model/budget mismatch、parser/schema mismatch。
* 验证 `modelConfigVersion -> modelProfileVersion` 精确映射。
* 运行 review-assets validator，证明静态资产仍为 `NOT_BOUND`。

## 后续动作

* `TASK_SPEC-037-A` 实现 migration、resolver 和测试。
* TASK-037 L3 收口并 merge 后重跑 `TASK-MVP-001` Phase 0。
* Phase 0 GO 后，`TASK_SPEC-MVP-001-A` 在单事务内选择 binding、创建 Task 与首个 `QUEUED` Execution。

## 关联

* 父任务：`tasks/active/TASK-037-execution-binding-release-and-profile-seed.md`
* 执行规格：`tasks/active/TASK_SPEC-037-A-execution-binding-release-runtime-source.md`
* 既有决策：ADR-006
* 静态资产治理：TASK-030
* 消费方：TASK-MVP-001

## 待确认

无。

## 接受记录

* 2026-07-24 独立只读规格审计最终结论：`GO`；六项首轮 blocking findings 与两项 delta finding 均已闭合。
* Codex Review Intake：`ACCEPT_ADR_AND_SPEC / GO_TO_ARCHITECTURE_SYNC / PRE_CODE_PLAN_REQUIRED`。
* 本接受记录不等于 `GO_TO_IMPLEMENT`；执行方仍须先提交 `AC1~AC18` 编码前规格映射计划并等待 Codex 放行。
