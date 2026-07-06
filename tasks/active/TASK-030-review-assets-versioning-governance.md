# TASK-030：Review assets 版本化治理最小落地

状态：A/B/C 已完成并经 PR #20 合并 / 归档 Review Intake GO / 待归档执行授权 / 后续实现待用户确认

类型：Governance / A

优先级：高

负责人：Codex 总控

创建日期：2026-07-05

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`docs/ARCHITECTURE.md`、`docs/DEVELOPMENT.md`、`docs/VERIFY.md`

## 背景

CQCP 已完成 MVP 主链路最小接入、`CandidateResolver` 置信度治理、`EvidenceSlot / SourceAnchor` 最小主实现、PR required checks 第一阶段治理，以及提交前授权证据 PR body 文本门禁。

当前仍要求所有规则、正则、Prompt、模型、合同类型画像和 EvidenceSelector 必须版本化。`tasks/MVP_TASK_MAP.md` 已登记 `TASK-030` 为 Review assets 版本化治理后续任务，但尚未启动。本任务承接该治理方向的最小落地准备，先冻结 Review assets 范围、版本字段、目录口径、验收口径和禁止边界。

本任务不是 `TASK-028` Gemma 接入，不是 `TASK-031` mapper 补洞，不是 `TASK-032` parser 拆分，也不解除 `TASK-EVAL-001` blocked 状态。

## 目标

* 定义 CQCP 一期 Review assets 的最小版本化范围。
* 明确规则、正则、Prompt、合同类型画像、EvidenceSelector 配置的版本字段和存放口径。
* 梳理当前代码与文档中已有 Review assets 的实际位置和调用入口。
* 输出可执行的后续实现切分建议，但不直接派发 Claude Code / DeepSeek 实现任务。
* 保持业务审核链路、模型职责边界、Finding / SYS 分流和 EvidenceSlot 机制不变。

## 非目标

* 不接入 Gemma / A30，不进入 `TASK-028`。
* 不修改 Result API / Admin API mapper，不进入 `TASK-031`。
* 不拆分 `ParserBackedReviewInputPreparer`，不进入 `TASK-032`。
* 不修改业务 Finding 语义。
* 不修改 fixture、expected JSON、OpenAPI、数据库、Docker、workflow、branch protection 或 required checks。
* 不新增 ADR，除非审查中发现版本化方案会改变架构边界。
* 不归档 `TASK-EVAL-001`，不补足 `TASK-EVAL-001` DoD #12。

## 输入

* 相关文档：
  * `AGENTS.md`
  * `CURRENT_CONTEXT.md`
  * `tasks/MVP_TASK_MAP.md`
  * `docs/ARCHITECTURE.md`
  * `docs/DEVELOPMENT.md`
  * `docs/VERIFY.md`
* 上游任务：
  * `TASK-025`
  * `TASK-026`
  * `TASK-027`
  * `TASK-GOV-004`
  * `TASK-GOV-006`
* 相关 ADR：
  * `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
  * `decisions/ADR-015-evidence-slot-source-anchor-governance.md`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/MVP_TASK_MAP.md`
* 本任务包
* `docs/ARCHITECTURE.md`
* `docs/DEVELOPMENT.md`
* `docs/VERIFY.md`

### Optional Context

* `PROJECT_BRIEF.md`
* `PRD.md`
* `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
* `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
* `tasks/done/TASK-027-evidence-slot-source-anchor-governance.md`
* `tasks/done/TASK-GOV-004-pr-based-multi-agent-governance.md`
* `tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`

### Out of Scope

* `TASK-028`
* `TASK-031`
* `TASK-032`
* `TASK-EVAL-001` 归档
* parser provenance 实现
* 真实 DOCX `TABLE_CELL` coverage 实现
* GitHub required checks / branch protection / repository ruleset 配置

## 范围

### 包含

* 只读盘点当前 Review assets 相关文件、类、配置、Prompt、规则和正则。
* 定义最小版本化元数据字段，例如：
  * `assetId`
  * `assetType`
  * `version`
  * `effectiveFrom`
  * `status`
  * `source`
  * `changeReason`
* 定义一期目录或文件命名建议。
* 定义后续实现任务的验收断言。
* 更新必要项目记忆文件。

### 不包含

* 不改生产审核逻辑。
* 不改测试 fixture 或 expected JSON。
* 不引入数据库表，除非后续任务另行 ADR 或任务授权。
* 不引入外部配置中心。
* 不做多租户、复杂 IAM、BI 或开放式聊天审合同。

## 约束

* 必须遵守 `docs/ARCHITECTURE.md`。
* 不确定事项标记为“待确认”。
* 如发现需要改变核心审核链路、模型职责边界、SYS/Finding 边界、EvidenceSlot、ReviewPointFamily 或 CandidateResolver，必须先停止并提出 ADR 需求。
* Review assets 版本化不得改变后端确定性裁判职责。
* Gemma/A30 仍只能作为未来局部抽取、证据选择或复杂语义辅助，不得在本任务中接入。
* 完成态复核按 `docs/DEVELOPMENT.md` 与 `docs/VERIFY.md` 执行。

## 交付物

* `tasks/active/TASK-030-review-assets-versioning-governance.md`
* 必要的 `CURRENT_CONTEXT.md` 写回
* 必要的 `tasks/MVP_TASK_MAP.md` 写回
* `changelog/2026-07.md` 追加记录
* 如仅完成只读盘点，则输出后续实现切分建议，不直接派发实现

## 验收标准

* 明确列出当前 Review assets 的实际盘点结果。
* 明确哪些资产纳入一期版本化，哪些标记为待确认或后续任务。
* 明确版本字段、文件职责、禁止边界和后续实现入口。
* 明确本任务未进入 `TASK-028 / TASK-031 / TASK-032`。
* 明确未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD，除非用户在本任务中另行授权并通过 Review Intake。
* 完成后工作区状态、diff、测试或只读核查输出可复核。

## 测试与验证

* `git status --short`
* `git status -sb`
* `git diff --name-status`
* `git diff --stat`
* `git diff --check`
* 如本任务只做文档和盘点，不运行业务测试；需在完成摘要中说明原因。
* 如后续进入代码实现，必须另行冻结 `TASK_SPEC`，并按完成态复核规则提交测试输出。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：是。
* 是否需要更新 `changelog/2026-07.md`：是。
* 是否需要更新 `docs/*.md`：待确认，仅当发现长期规则入口缺失时更新。
* 是否需要新增或更新 ADR：默认否；如触及架构边界则必须先停下并提出 ADR。

## Next Task Handoff

* 任务完成后必须判断是否存在明确下一任务。
* 如果只是建议、征求意见、需要人工确认或没有明确任务编号，不输出可执行代码块。
* 不得把 `TASK-030` 收口自动写成 `TASK-028 / TASK-031 / TASK-032` 启动授权。

## 风险

* 版本化范围可能与未来数据库配置化或规则平台化重叠；本任务必须保持一期最小治理，不提前平台化。
* 当前 `TASK-EVAL-001` 仍 blocked，不得用 Review assets 版本化绕过其归档门禁。
* 如果盘点发现 Prompt / 规则散落在代码中，先记录事实和后续切分，不顺手重构。

## 待确认

* 一期 Review assets 是否仅采用仓库内静态文件版本化，还是需要后续数据库持久化任务。
* 是否需要把 Review assets 版本号展示到结果页或管理台诊断页；当前默认不进入 UI 改动。
* `TASK-033` 当前保持空置，后续是否用于 MVP 端到端样本验收规格冻结，待用户另行确认。

## 执行记录：2026-07-05

### 执行边界确认

本轮按用户要求直接进入 `TASK-030` 执行阶段，不再做额外规格冻结审查。

本轮实际操作：

* 执行 `git status --short` 与 `git status -sb`。
* 读取 Required Context：`AGENTS.md`、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、本任务包、`docs/ARCHITECTURE.md`、`docs/DEVELOPMENT.md`、`docs/VERIFY.md`。
* 只读盘点 `packages/review-assets/`、后端 `reviewengine` / `modelgateway` / `tuning` 相关代码、数据库迁移、审核点定义草案和相关 ADR。
* 仅写回任务文件、项目记忆和任务地图。

本轮未进入：

* `TASK-028`
* `TASK-031`
* `TASK-032`
* `TASK-EVAL-001` 归档或解除 blocked
* `TASK-033` 创建
* Gemma / A30 接入
* Claude Code / DeepSeek 实现派发

### 初始工作区状态

`git status --short`：

```text
 M tasks/MVP_TASK_MAP.md
?? tasks/active/TASK-030-review-assets-versioning-governance.md
```

`git status -sb`：

```text
## master...origin/master
 M tasks/MVP_TASK_MAP.md
?? tasks/active/TASK-030-review-assets-versioning-governance.md
warning: unable to access 'C:\Users\1/.config/git/ignore': Permission denied
warning: unable to access 'C:\Users\1/.config/git/ignore': Permission denied
```

当前未提交文件清单：

* `tasks/MVP_TASK_MAP.md`
* `tasks/active/TASK-030-review-assets-versioning-governance.md`

判断：上述未提交变更均落在 TASK-030 建档 / 执行范围内。未发现业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、branch protection 或 required checks 变更。

### Review assets 实际位置和调用入口

#### 仓库资产目录

`packages/review-assets/` 已存在以下目录：

* `review-point-definitions/`
* `rule-sets/`
* `pattern-libraries/`
* `field-lexicons/`
* `prompts/`
* `contract-type-profiles/`
* `evidence-selectors/`

当前状态：上述目录只有 `.gitkeep`，`packages/review-assets/README.md` 明确说明该目录只是版本化审核资产源定义占位，尚未写入业务规则、prompt、正则或词库内容。

#### 后端主链路入口

当前 Review assets 的真实运行入口仍主要散落在后端代码中：

* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java`
  * 持有 party / amount / tax / payment 相关 label hints。
  * 持有 `Pattern` 正则列表、`ValueGrammar` 近似逻辑、候选 role 映射和 display label 映射。
  * 调用 `MinimalCandidateResolver` 形成 `PointEvidence`。
  * 直接写入 `SOURCE_ORIGIN=NATIVE_WORD`、`SOURCE_EXTRACTION_MODE=STRUCTURED`、`CONTEXT_TYPE=NORMAL`。
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java`
  * 持有 `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN` 最小置信度判定逻辑。
  * 按 role label signal、value format signal、block attribution signal 进行 gate。
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`
  * 持有 EvidenceSlot runtime preflight、`SATISFIED / PARTIAL / MISSING / AMBIGUOUS / LOW_CONFIDENCE / BUDGET_TRUNCATED` 映射。
  * 持有 deterministic compare、金额容差、税额公式、付款方式 `SKIPPED`、业务 message 和 `SYS-*` 映射。
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java`
  * 从 `TaskExecutionRecord.versionReferences()` 读取版本引用并交给 `ResultComposer`。
  * 未读取 `packages/review-assets/`。
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ResultComposer.java`
  * 将 `contractTypeProfileVersion`、`ruleSetVersion`、`reviewBudgetProfileVersion`、`modelProfileVersion`、`parserVersion`、`promptVersion`、`schemaVersion`、`patternLibraryVersion`、`fieldLexiconVersion`、`evidenceSelectorVersion` 写入 `ReviewResultSnapshot`。
* `apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/`
  * `ModelProfile` 只有 `profileCode / providerType / modelName / enabled`。
  * `ModelCallIntent` 记录 `promptVersion / schemaVersion / modelVersion`。
  * `ModelGatewayService.invoke(...)` 接收 prompt 字符串，但仓库未见正式 prompt 模板来源。
  * `MockModelProvider` 通过 `MOCK_TIMEOUT / MOCK_UNAVAILABLE / MOCK_CONFLICT / MOCK_SCHEMA_INVALID` 等字符串模拟模型行为。
* `apps/api-server/src/main/java/com/cqcp/apiserver/tuning/VersionRefs.java`
  * TuningPacket 记录 `ruleSetVersion / modelProfileVersion / parserVersion / promptVersion / schemaVersion / patternLibraryVersion / fieldLexiconVersion / evidenceSelectorVersion`。

#### 数据库与快照入口

`apps/api-server/src/main/resources/db/migration/V1__cqcp_mvp_core_schema.sql` 已在 `execution` 和 `review_result_snapshot` 中预留版本字段：

* `contract_type_profile_version`
* `rule_set_version`
* `review_budget_profile_version`
* `model_profile_code` / `model_config_version`
* `model_profile_version`
* `parser_version`
* `prompt_version`
* `schema_version`
* `pattern_library_version`
* `field_lexicon_version`
* `evidence_selector_version`

当前状态：数据库已有版本引用字段，但没有 Review assets 版本表、发布表、审批表或 asset manifest 表。

#### 文档源入口

* `docs/review-point-definitions.md` 是首批 9 个 `ReviewPointDefinition` 草案来源，覆盖 family、executionStrategy、evidencePolicy、candidateExtractionPolicy、deterministicRule、modelAssistPolicy、businessMessages 和版本化要求。
* `docs/model-gateway-budget-baseline.md` 冻结 ModelGateway、ModelCallIntent、GemmaExtractionArtifact 和预算 profile 的 MVP 边界。
* `docs/ARCHITECTURE.md` 第 22.1 节明确一期只允许三类业务可配置包：`ContractTypeProfile`、`RuleSetVersion`、`ReviewBudgetProfile`。
* `docs/database.md` 同步了三类版本包和 `RuleSetVersion` 内部模块版本边界。
* `decisions/ADR-011-repository-structure-freeze.md` 明确 `packages/review-assets/` 是版本化审核资产源定义目录，不保存后端最终裁判实现代码。
* `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md` 和 `decisions/ADR-015-evidence-slot-source-anchor-governance.md` 分别冻结 CandidateResolver 与 EvidenceSlot / SourceAnchor 的治理边界。

### 分类盘点结果

#### 规则 / 后端确定性裁判相关资产

已存在：

* `MinimalReviewEngine` 中的 deterministic compare、税额公式、金额容差、付款方式适用性、业务 message、`SYS-*` preflight 映射。
* `docs/review-point-definitions.md` 中的 deterministicRule 草案。
* `RuleSetVersion` 版本引用字段已存在于 execution / snapshot。

尚未落地：

* `packages/review-assets/rule-sets/` 下没有实际 `RuleSetVersion` 源文件。
* 没有 rule set manifest、schema 校验、发布校验或 runtime loader。
* 后端确定性裁判实现未绑定具体 asset 文件版本；当前只通过外部传入的版本引用留痕。

#### 正则 / Pattern 相关资产

已存在：

* `ParserBackedReviewInputPreparer` 中硬编码 party、金额、税额、预付款、进度款、竣工款、结算款、质保款等正则。
* `Pattern` 使用集中在候选抽取路径。
* `patternLibraryVersion` 已进入 snapshot / execution / tuning packet 版本引用。

尚未落地：

* `packages/review-assets/pattern-libraries/` 没有实际 pattern library 文件。
* 没有 pattern id、适用 review point、适用 contract type、value grammar 绑定、误召回排除规则或评测引用。
* Pattern 变更目前无法通过资产版本独立追溯到具体正则内容。

#### Prompt / 模型辅助相关资产

已存在：

* `ModelCallIntent` 记录 `promptVersion / schemaVersion / modelVersion`。
* `ModelGatewayService` 接收 prompt 字符串并进行 schema 解析。
* `docs/model-gateway-budget-baseline.md` 冻结 prompt/schema/model version 记录要求。
* 管理台 / 公开结果页已有“不展示完整 prompt”的边界。

尚未落地：

* `packages/review-assets/prompts/` 没有 prompt 模板。
* 当前没有正式 prompt builder、prompt manifest、schema 文件或 family / point 绑定规则。
* 当前未接入 Gemma / A30；模型路径仍是 mock / gateway 基础能力。

#### ContractTypeProfile / 合同类型画像相关资产

已存在：

* 架构和数据库文档定义了 `ContractTypeProfile` 字段方向、来源优先级和禁止覆盖边界。
* `ReviewPointSnapshot` 测试中使用 `ENGINEERING_PROCUREMENT`，但属于测试构造。
* `contractTypeProfileVersion` 已进入 execution / snapshot 版本引用。

尚未落地：

* `packages/review-assets/contract-type-profiles/` 没有 profile 文件。
* 后端没有 `ContractTypeProfile` loader、selector 或 profile override 白名单校验。
* 合同类型画像尚未实际参与 CandidateIndex boost / profile routing。

#### EvidenceSelector / EvidenceSlot / CandidateResolver 相关资产

已存在：

* `MinimalCandidateResolver` 已实现最小 confidence gating。
* `MinimalReviewEngine` 已实现 EvidenceSlot runtime coverage preflight。
* `PointEvidence` / `EvidenceSlotCoverage` 已记录 slot coverage 状态、diagnostic code、reliable anchor。
* `evidenceSelectorVersion` 已进入 execution / snapshot / tuning packet 版本引用。
* ADR-014 / ADR-015 已接受并提供治理边界。

尚未落地：

* `packages/review-assets/evidence-selectors/` 没有 selector / slot policy 文件。
* `EvidenceSlot` 定义尚未作为不可变 `RuleSetVersion` 源资产落地。
* CandidateResolver policy 尚未形成可版本化资产；当前仍为 Java 代码中的固定逻辑。
* 完整 `EvidenceBundle` / `FamilyEvidencePlan` / `PointEvidenceOverlay` 平台化未落地，且本任务不进入该实现。

#### Model profile / budget profile 相关资产

已存在：

* `ModelProfile` 最小 record 已存在。
* `ModelCallIntent` 记录 model / prompt / schema version。
* `docs/model-gateway-budget-baseline.md` 定义 `STANDARD / DEEP_REVIEW / EVALUATION` 预算 profile 边界。
* `reviewBudgetProfileVersion`、`modelProfileVersion`、`model_config_version` 等版本引用字段已存在。

尚未落地：

* 仓库内没有 `model-profiles/` 或 `review-budget-profiles/` 实际资产目录。
* 没有 `ReviewBudgetProfile` runtime class / loader / seed 文件。
* 没有模型 profile 发布、禁用、回滚、license / hardware compatibility 记录。
* Gemma / A30 具体模型、权重、量化格式、license 和 A30 可运行性仍待确认。

### 一期最小版本化字段

建议所有 Review assets 源文件统一包含最小元数据：

```text
assetId
assetType
version
status
effectiveFrom
source
owner
changeReason
schemaVersion
contentHash
createdAt
```

字段说明：

* `assetId`：仓库内稳定 ID，不随文件名大小写或路径微调变化。
* `assetType`：`RULE_SET / REVIEW_POINT_DEFINITION / FIELD_LEXICON / PATTERN_LIBRARY / PROMPT_TEMPLATE / CONTRACT_TYPE_PROFILE / EVIDENCE_SELECTOR / MODEL_PROFILE / REVIEW_BUDGET_PROFILE`。
* `version`：推荐 `vYYYYMMDD.N` 或 `v1` 起步；一期只要求稳定、唯一、可引用。
* `status`：`DRAFT / ACTIVE / RETIRED / NEEDS_REVALIDATION`。
* `effectiveFrom`：对后续新 execution 生效的时间；历史 snapshot 不回算。
* `source`：`manual / migrated / generated-candidate / test-only` 等来源。
* `owner`：规则负责人或治理 owner；当前可先写 `待确认`。
* `changeReason`：变更原因摘要，不写完整合同或敏感内容。
* `schemaVersion`：资产文件结构版本，不等同 prompt/schema/model 业务版本。
* `contentHash`：资产内容 canonical JSON / YAML 的摘要；一期可作为后续校验建议，不要求本轮实现。
* `createdAt`：资产源文件创建时间。

`RuleSetVersion` 建议作为聚合 manifest，至少引用：

```text
ruleSetVersion
reviewPointsVersion
evidenceSlotsVersion
disambiguationVersion
warningPolicyVersion
guardRulesVersion
qualityThresholdsVersion
patternLibraryVersion
fieldLexiconVersion
promptVersion
evidenceSelectorVersion
previousRuleSetVersion?
changedModules[]
changeSummary
```

### 建议目录和命名口径

沿用当前 `packages/review-assets/`，不另建业务代码目录。

建议一期文件命名：

```text
packages/review-assets/review-point-definitions/review-points-v1.yaml
packages/review-assets/rule-sets/ruleset-v1.yaml
packages/review-assets/pattern-libraries/pattern-library-v1.yaml
packages/review-assets/field-lexicons/field-lexicon-v1.yaml
packages/review-assets/prompts/prompt-v1.yaml
packages/review-assets/contract-type-profiles/contract-type-profile-v1.yaml
packages/review-assets/evidence-selectors/evidence-selector-v1.yaml
```

模型和预算 profile 当前没有目录。后续如纳入静态资产，建议新增：

```text
packages/review-assets/model-profiles/model-profile-v1.yaml
packages/review-assets/review-budget-profiles/review-budget-profile-v1.yaml
```

新增上述目录属于普通资产目录补齐；如果仅保存源定义且不改变模型职责、不接入 Gemma / A30、不改变审核链路，默认不需要 ADR。但若同时引入 runtime loading、发布审批或数据库持久化，应按下文 ADR 边界判断。

### 后续实现切分建议

以下只是后续 `TASK_SPEC` 候选建议，本任务不派发实现。

1. `TASK_SPEC-030-A`：Review assets manifest 与静态资产源文件最小建档。
   * 范围：在 `packages/review-assets/` 下新增首批 YAML / JSON 源文件和 README 说明。
   * 不改业务代码，不改测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow。
   * 验收：每类资产至少有一个 `assetId / assetType / version / status / source / changeReason`；内容与当前代码行为只声明映射，不声明 runtime 已绑定。
2. `TASK_SPEC-030-B`：Review assets schema 校验脚本。
   * 范围：新增只读校验脚本和示例校验，不改变主链路。
   * 验收：能检测缺少必需元数据、重复 assetId、RuleSetVersion 引用不存在。
   * 是否需要 ADR：默认不需要。
3. `TASK_SPEC-030-C`：版本引用默认值与静态资产 manifest 对齐。
   * 范围：只让当前测试/执行构造中的版本引用与资产 manifest 名称一致。
   * 风险：可能触及代码测试构造；必须单独冻结，不得混入本任务。
   * 是否需要 ADR：若不改变审核语义，默认不需要；若改变 version binding 来源或运行时加载，则需重新判断。
4. `TASK_SPEC-030-D`：Pattern / field lexicon 从代码硬编码到资产源定义的非运行时映射。
   * 范围：只建立“代码现状 -> 资产源定义”的可追溯映射，不启用 loader。
   * 验收：每个当前硬编码 Pattern / label hint 有 asset 条目、reviewPointCode、candidateRole、适用范围和来源说明。
   * 是否需要 ADR：默认不需要。
5. `TASK_SPEC-030-E`：Runtime loader 方案评审。
   * 范围：提出是否从 `packages/review-assets/` 读取 RuleSetVersion / PatternLibrary / EvidenceSelector 的方案。
   * 是否需要 ADR：大概率需要先判断；若改变核心审核链路、CandidateResolver、EvidenceSlot 或发布治理，必须先 ADR。
6. `TASK_SPEC-030-F`：ModelProfile / ReviewBudgetProfile 静态源定义。
   * 范围：仅记录 profile 元数据、启用状态、provider 类型、预算 profile 名称和待确认字段。
   * 不接入 Gemma / A30，不配置真实 endpoint。
   * 是否需要 ADR：仅源定义默认不需要；模型职责、生产 provider、外部模型范围或发布治理变化必须 ADR。

### ADR 判断

普通 `TASK_SPEC` 即可处理：

* 补齐 `packages/review-assets/` 静态源文件。
* 增加资产 README、manifest、schema 校验脚本。
* 将当前代码硬编码规则映射到只读资产清单，但不启用 runtime loader。
* 规范版本字段命名和文件路径。

需要 ADR 或至少先做架构评审：

* 改变核心审核链路、ReviewExecutionPlan、CandidateResolver、EvidenceSlot 或 EvidenceBundle 行为。
* 让 runtime 从资产文件或数据库动态加载规则并影响生产裁判。
* 引入数据库版本表、发布审批流、回滚机制或权限治理。
* 改变模型职责边界、接入 Gemma / A30 或公网模型生产辅助。
* 改变 `SYS-*` / Finding 分流、business finding 生成条件或 EvidenceSlot required gate。
* 将 Review assets 版本展示到公开结果页或管理台并改变 API / mapper 契约。

### 本轮结论

* 当前已经存在版本化目录骨架、版本引用字段、审核点草案、ModelGateway 最小结构和 ADR 边界。
* 当前尚未存在可执行的 Review assets 源文件、asset manifest、schema 校验、runtime loader、发布审批或资产内容 hash。
* 一期最小落地建议先做仓库内静态资产源定义与校验，保持后端确定性裁判职责不变。
* 本轮不需要新增 ADR；原因是只做盘点和文档级治理方案，没有改变架构边界。
* 后续若进入 runtime loading、数据库持久化或规则发布治理，必须重新判断 ADR。

## 完成记录

* 完成日期：2026-07-05。
* 变更文件：
  * `tasks/active/TASK-030-review-assets-versioning-governance.md`
  * `CURRENT_CONTEXT.md`
  * `tasks/MVP_TASK_MAP.md`
  * `changelog/2026-07.md`
* 测试结果：
  * 本任务为文档盘点与治理方案，不运行后端 / 前端业务测试。
  * 已执行只读检索和文件读取，完成后执行 `git status --short`、`git status -sb`、`git diff --name-status`、`git diff --stat`、`git diff --check`。
* 遗留问题：
  * `TASK_SPEC-030-A` 已补齐首批 Review assets 静态源文件和 RuleSetVersion manifest；schema 校验、runtime loader、数据库持久化、发布审批、UI 展示和模型 profile 真实接入仍待后续单独定界。
  * Gemma / A30 具体模型、权重、量化格式、license 和 A30 可运行性仍待确认。
* 备注：
  * 本轮未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、branch protection、required checks、ADR 或 PRD。
  * 本轮未创建 `TASK-033`，未派发 Claude Code / DeepSeek，实现建议仅作为后续切分候选。

## TASK_SPEC-030-A 执行记录：2026-07-05

### 执行范围

按用户授权推进 `TASK_SPEC-030-A`：Review assets 静态源文件与 manifest 最小建档。

本轮创建任务文件：

* `tasks/active/TASK_SPEC-030-A-review-assets-static-source-manifest.md`

本轮新增或更新资产文件：

* `packages/review-assets/README.md`
* `packages/review-assets/rule-sets/ruleset-v20260705.1.json`
* `packages/review-assets/review-point-definitions/review-points-v20260705.1.json`
* `packages/review-assets/pattern-libraries/pattern-library-v20260705.1.json`
* `packages/review-assets/field-lexicons/field-lexicon-v20260705.1.json`
* `packages/review-assets/prompts/prompts-v20260705.1.json`
* `packages/review-assets/contract-type-profiles/contract-type-profile-v20260705.1.json`
* `packages/review-assets/evidence-selectors/evidence-selector-v20260705.1.json`

### 验收结果

* 七类资产均包含 `assetId`、`assetType`、`version`、`status`、`source`、`changeReason`。
* `ruleset-v20260705.1.json` 作为 RuleSetVersion manifest，能引用 review point、pattern library、field lexicon、prompt、contract type profile 和 evidence selector 的模块版本。
* 所有资产均明确当前为 `code-current-mapping` 或等价“代码现状映射”，并声明 `runtimeBinding` 为 `NOT_BOUND` 或未绑定生产 runtime。
* 本轮只做源定义和映射，不启用 runtime loader。

### 范围外确认

本轮未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、branch protection、required checks、ADR 或 PRD。

本轮未进入 `TASK-028` / `TASK-031` / `TASK-032`，未接入 Gemma / A30，未创建 `TASK-033`。

### ADR 判断

不需要新增 ADR。原因：本轮只补静态资产源文件和 manifest，不改变核心审核链路、模型职责边界、`SYS-*` / Finding 边界、`EvidenceSlot` 机制、`ReviewPointFamily` 或 `CandidateResolver` runtime 行为。

### 角色分工复核补充

本次 TASK_SPEC-030-A 因范围仅为未绑定 runtime 的静态 Review assets 源文件建档，由 Codex 直接完成并作为一次性例外接受；后续更偏实现、代码修改或 runtime 行为变更的 TASK_SPEC 应按角色分工交由 Claude Code / DeepSeek 执行，Codex 负责冻结规格和审查。

## TASK_SPEC-030-B 执行记录：2026-07-05

### 执行范围

按用户授权（Codex 审查放行编码前规格映射计划）由 Claude Code 执行 `TASK_SPEC-030-B`：Review assets schema 与 manifest 引用校验。

### 交付物

- `scripts/validate-review-assets.mjs`：新增，无第三方依赖，校验 7 个资产 JSON 的必需字段、`assetId` 唯一性、`source.type`/`source.runtimeBinding`、`loaderEnabled` 和 RuleSet manifest `moduleVersions` 引用一致性。
- `packages/review-assets/README.md`：新增静态校验说明。
- `tasks/active/TASK_SPEC-030-B-review-assets-schema-manifest-validation.md`：执行记录。
- `CURRENT_CONTEXT.md`：状态更新。
- `changelog/2026-07.md`：追加记录。

### 验收结果

- `node scripts/validate-review-assets.mjs` 退出码 0，7 文件全通过。
- `git diff --check` 无格式问题。
- 未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 未进入 `TASK-028` / `TASK-031` / `TASK-032`，未创建 `TASK-033`，未接入 Gemma / A30。
- 未 stage、未 commit、未 push。

## TASK_SPEC-030-C 执行记录：2026-07-05

### 执行范围

按 Codex 审查放行的编码前规格映射计划，由 Claude Code 执行 `TASK_SPEC-030-C`：Review assets 版本治理与变更流程最小建档。

本轮创建任务文件：

- `tasks/active/TASK_SPEC-030-C-review-assets-version-governance-docs.md`

本轮更新文件：

- `packages/review-assets/README.md`：新增版本命名规则（`vYYYYMMDD.N`）、新增/升级资产流程、RuleSetVersion manifest `moduleVersions` 引用规则、校验命令说明和 runtime loader 未启用声明。
- `tasks/active/TASK-030-review-assets-versioning-governance.md`：本执行记录。
- `CURRENT_CONTEXT.md`：状态更新。
- `changelog/2026-07.md`：追加记录。

### 验收结果

- `packages/review-assets/README.md` 包含完整的版本命名规则、资产新增/升级流程、manifest 引用规则、校验命令和 runtime loader 状态说明。
- `node scripts/validate-review-assets.mjs` 退出码 0，7 文件全通过（未修改资产文件，校验仍通过）。
- `git diff --check` 无格式问题。
- 未修改 7 个 JSON 资产文件、`scripts/validate-review-assets.mjs`、业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 未进入 `TASK-028` / `TASK-031` / `TASK-032`，未创建 `TASK-033`，未接入 Gemma / A30。
- 未 stage、未 commit、未 push。

## PR #20 合并与父任务收口判断：2026-07-06

### 合并事实

- `TASK-030` A/B/C 当前批次已通过 PR #20 合并到 `master`。
- PR head commit：`29d4a94eaf07b37831f07dd93f78d3027b770f01`（`docs: add review assets versioning governance`）。
- PR merge commit：`eadd017808fc8c2471fb0ac081f1e68e22dce5f5`。
- 本地 `master` 已与 `origin/master` 对齐，工作区在 post-merge 只读核查时为 clean。
- post-merge 只读核查结论：`POST_MERGE_GO`。

### post-merge 核查摘要

- 变更文件共 15 个，均属于 `TASK-030` A/B/C 授权范围。
- 7 个 Review assets JSON 均通过 `node scripts/validate-review-assets.mjs` 校验。
- `git diff --check` 通过。
- README 与资产文件未声明 runtime loader 已启用，未声明 `RuleSetVersion` 已在生产 runtime 绑定。
- 未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 未进入 `TASK-028` / `TASK-031` / `TASK-032`，未创建 `TASK-033`，未解除 `TASK-EVAL-001` blocked。

### Codex 父任务收口判断

Codex Review Intake Decision：`READY_FOR_INDEPENDENT_PRE_ARCHIVE_AUDIT / NOT_ARCHIVED`。

判断依据：`AGENTS.md` 要求每个父任务归档前必须经过独立 agent 只读审计；当前已有 Codex post-merge 只读核查，但不能替代独立审计。因此 `TASK-030` 当前不得归档，下一步只可进入父任务归档前独立只读审计；独立审计返回后，再由 Codex 单独给出归档 Review Intake Decision。

## TASK-030 归档 Review Intake Decision：2026-07-06

### 输入证据

- 独立只读审计结论：`GO_WITH_CONDITIONS`。
- 独立审计条件 B1-B5：`TASK_SPEC-030-A/B/C`、`TASK-030` 父任务和 `tasks/MVP_TASK_MAP.md` 的 post-merge 状态残留需修正。
- 条件修正提交：`40f28e7159f0cfc7d63546db07c5b8819edbc0c3`（`docs: record TASK-030 post-merge closeout status`）。
- 条件修正合并：PR #21，merge commit `ef4643c7d11b1209c935229b4e39b76d659db00f`，merged at `2026-07-06T15:27:11Z`。
- PR #21 required checks：`Authorization evidence check`、`Backend Gradle tests`、`Admin web lint, tests, and build` 均为 `SUCCESS`。
- 本地 `master` 已同步到 `origin/master`，当前工作区在 Decision 前为 clean。
- `node scripts/validate-review-assets.mjs` 通过：7 个 JSON asset 全部通过静态校验。

### Codex Review Intake Decision

结论：`GO_TO_ARCHIVE_WITH_CONDITIONS_SATISFIED`。

判断：

- `TASK-030` A/B/C 已通过 PR #20 合并，PR #21 已完成 post-merge 状态写回并满足独立审计条件。
- 独立只读审计已发生，Codex 已单独进行 Review Intake Decision，满足 AGENTS.md 父任务归档门禁。
- 当前结论只授权 `TASK-030` 父任务归档流程；不授权 runtime loader、数据库资产表、发布审批、model / budget profile 源定义、`TASK-028`、`TASK-031`、`TASK-032` 或 `TASK-033`。
- 归档执行仍需单独执行文件移动与记忆写回，不得把本 Decision 直接等同于已归档。
