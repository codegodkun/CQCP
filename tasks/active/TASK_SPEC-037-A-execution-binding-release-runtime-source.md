# TASK_SPEC-037-A：Execution Binding Release Runtime Source

状态：Implemented / 独立规格与实现审计 GO / Codex Review Intake ACCEPT / Ready for PR

TASK_SPEC 类型：`execution`

父 TASK：`TASK-037`

父 TASK Level：`L3 高风险治理`

Integration unit：`TASK-037` 独立高风险 PR

执行方：Claude Code / DeepSeek

## 0. 任务摘要

实现 PostgreSQL V2 binding/profile seed 与 fail-closed Java resolver，为后续任务创建提供一条真实、完整、可重建的 Demo execution binding；不实现任务创建接口，不改变审核语义。

### 0.1 角色与执行门禁

* Codex 负责冻结本规格、审查编码前映射计划、审查 diff 和实现报告。
* Claude Code / DeepSeek 只能在 Codex 明确 `GO_TO_IMPLEMENT` 后修改允许文件，不得 commit、push、切换分支或修改规格边界。
* 独立 agent 只做只读规格/实现审计。
* 测试通过不能替代编码前门禁、独立审计或 Codex Review Intake。

### 0.2 编码前规格映射计划

执行方必须先输出并暂停：

```text
验收断言映射：
- AC1~AC18：逐项说明真实输入、代码路径、数据库约束和可证伪测试。

14 字段来源映射：
- 每个 execution NOT NULL version/model 字段的唯一 seed/release 来源。

明确不修改路径：
- 列出 V1 migration、OpenAPI、现有审核链路、review-assets、TASK-036、fixture/expected。

范围外风险：
- 说明 Task Creation 事务、runtime loader、真实 provider 为什么不在本规格实现。

预计测试：
- migration/integration test、resolver unit test、review-assets validator。
```

Codex 未明确放行前不得修改代码或 migration。

### 0.3 文件访问范围

```text
✅ 允许修改：
  apps/api-server/src/main/resources/db/migration/V2__execution_binding_release.sql
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ExecutionBindingRelease.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ExecutionBindingCatalog.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ExecutionBindingResolutionException.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/JdbcExecutionBindingRepository.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuntimeArtifactVersions.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ExecutionBindingCatalogTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ExecutionBindingMigrationTest.java
  tasks/active/TASK_SPEC-037-A-execution-binding-release-runtime-source.md

👀 允许只读参考：
  AGENTS.md
  CURRENT_CONTEXT.md
  tasks/active/TASK-037-execution-binding-release-and-profile-seed.md
  decisions/ADR-017-execution-binding-release-and-demo-profile-readiness.md
  docs/ARCHITECTURE.md
  docs/backend.md
  docs/database.md
  docs/model-gateway-budget-baseline.md
  decisions/ADR-006-model-profile-switching-and-public-provider-scope.md
  tasks/done/TASK-030-review-assets-versioning-governance.md
  packages/review-assets/
  packages/api-contracts/openapi.yaml
  apps/api-server/src/main/resources/db/migration/V1__cqcp_mvp_core_schema.sql
  apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ResultComposer.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java
  .github/workflows/ci.yml

⛔ 禁止访问：
  .env
  .env.*
  secrets/
  credentials/
  config/production/
  packages/test-fixtures/
  outputs/
```

## 1. 上下文注入

### 1.1 链路位置

```text
Flyway V2 seed
-> JdbcExecutionBindingRepository
-> ExecutionBindingCatalog.resolveDefault(purpose, deploymentScope, contractTypeCode)
-> 后续 TASK_SPEC-MVP-001-A Task Creation 事务
```

本规格止于 resolver，不创建 Task/Execution。

### 1.2 架构红线

* 一期业务可配置包仍只有 `ContractTypeProfile / RuleSetVersion / ReviewBudgetProfile`。
* `execution_binding_release` 是部署发布选择记录，不是业务配置包。
* review-assets 保持 `DRAFT / NOT_BOUND`，runtime loader 不启用。
* 使用 legacy `v20260705.1`，禁止使用 `v20260715.1`。
* MOCK 只用于 Demo，`secretRequired=false`，不得伪造 secret。
* 模型不直接生成最终业务 Finding；本规格不修改模型职责。

### 1.3 冻结版本

```text
bindingVersion: mvp-demo-engineering-v20260724.1
purpose: MVP_DEMO
deploymentScope: DEMO
contractTypeCode: ENGINEERING
contractTypeProfileCode: ENGINEERING_PROCUREMENT

ruleSetVersion: v20260705.1
contractTypeProfileVersion: v20260705.1
promptVersion: v20260705.1
patternLibraryVersion: v20260705.1
fieldLexiconVersion: v20260705.1
evidenceSelectorVersion: v20260705.1

STANDARD budget version: budget-standard-v20260724.1
DEEP_REVIEW budget version: budget-deep-review-v20260724.1
EVALUATION budget version: budget-evaluation-v20260724.1
budgetApprovalPolicyVersion: budget-approval-policy-mvp-v20260724.1
seedEffectiveFrom: 2026-07-24T00:00:00Z
seedCreatedAt: 2026-07-24T00:00:00Z

budgetDisplayName:
  STANDARD: 标准审核
  DEEP_REVIEW: 深度审核（预留）
  EVALUATION: 评测（预留）

modelProfileCode: MVP_DEMO_MOCK
modelConfigVersion: model-config-mvp-demo-mock-v20260724.1
modelDisplayName: MVP Demo Mock
providerType: MOCK
modelName: cqcp-demo-mock
endpointAlias: mock-local
usageScope: DEMO
secretRequired: false
readinessStatus: READY
timeoutSeconds: 30
retryCount: 0

parserVersion: parser-docx-word-v20260724.1
schemaVersion: model-output-artifact-v20260724.1
```

## 2. 数据库契约

### 2.1 `review_budget_profile_version`

至少包含：

```text
review_budget_profile_version PK
profile_code
display_name VARCHAR NOT NULL
model_budget JSONB NOT NULL
standard_ratio INTEGER NOT NULL
deep_review_ratio INTEGER NOT NULL
budget_approval_policy_version
enabled
effective_from
created_at
```

约束：

* `profile_code` 只允许三类值。
* ratio 必须为正，seed 为 `5 / 1`。
* identity 与 budget content immutable；`enabled` 是 lifecycle state，本规格不提供发布 API。
* 三类 seed 均存在；仅 `STANDARD.enabled=true`。
* `model_budget` 使用架构启动基线的八个数值字段；三类 seed 共享时在 SQL comment 与测试名中说明无差异化质量承诺。
* `budget_approval_policy_version`、display name、effective/created timestamp 精确使用 §1.3 冻结值。

### 2.2 `model_profile_config_version`

至少包含：

```text
profile_code
config_version
display_name
provider_type
endpoint_alias
model_name
enabled
usage_scope
is_default_for_new_task
secret_required
readiness_status
timeout_seconds
retry_count
effective_from
created_at
PK(profile_code, config_version)
```

约束：

* provider/usage/readiness 使用 CHECK；`readiness_status` 最小枚举固定为 `READY / NOT_READY`。
* enabled default 按 usage scope 唯一。
* config content immutable；`enabled / is_default_for_new_task / readiness_status` 是 lifecycle state。
* seed 精确使用 §1.3 的全部 model 值。

### 2.3 `execution_binding_release`

包含 metadata 与 14 个 execution 字段。约束：

* `binding_version` 主键。
* budget FK；model `(profile_code, config_version)` 复合 FK。
* 14 字段全部 `NOT NULL`。
* 额外保存 `contract_type_profile_code=ENGINEERING_PROCUREMENT`，与 `contract_type_code=ENGINEERING` 共同闭合 legacy alias traceability。
* binding identity、alias metadata 与 14 字段 content immutable；`enabled` 是 lifecycle state。
* `enabled=true` binding 在 `purpose + deployment_scope + contract_type_code` 上唯一；partial index 只使用 `WHERE enabled = true`，不得包含 `now()`。
* `effective_from <= current time` 只由 resolver 校验。
* `content_digest` 为小写 64 位 SHA-256，算法精确按 ADR-017：19 个冻结顺序字符串组成 compact JSON array，使用 Jackson string escaping，UTF-8 bytes，SHA-256 lowercase hex；lifecycle/audit 字段与 JSONB 不参与。
* seed 只创建一个 `MVP_DEMO / DEMO / ENGINEERING` binding。

### 2.4 Lifecycle 发布与回滚

* 新默认/新 binding 发布必须在单个事务内锁定选择域、撤销旧 enabled/default、再插入或启用新版本。
* 回滚使用相同事务切回旧版本，只改 lifecycle，不改旧 content。
* 本规格不实现发布 API；migration test 必须证明唯一索引允许“先撤销旧、再启用新”，拒绝同时两个 enabled/default。

## 3. Java 契约

### 3.1 `RuntimeArtifactVersions`

* 只暴露 parser/schema 两个 code-owned immutable 常量。
* 不包含 RuleSetVersion 或六个 module version，避免伪装 runtime loader。

### 3.2 `ExecutionBindingRelease`

* record 保存 metadata 与 14 字段。
* 构造时拒绝空值。
* `versionReferences()` 映射到现有 `VersionReferences`：
  `modelProfileVersion = modelConfigVersion`。
* 不新增或修改现有 execution/snapshot 字段。
* 暴露按 ADR-017 固定顺序生成的 19 个 digest input strings；Catalog 使用专用、默认配置的 Jackson `JsonMapper` 生成 compact JSON UTF-8 bytes 并重算 SHA-256，不受应用全局 ObjectMapper 配置影响。

### 3.3 Repository 与 Catalog

Repository 返回同 selector 下的 binding 与所引用 budget/model row 验证视图，不能在 SQL 中静默过滤掉 disabled/not-effective row。Catalog 必须按固定顺序：

1. raw rows 为 0：`NOT_FOUND`。
2. 过滤 `binding.enabled=true && effectiveFrom<=Clock.instant()`。
3. 有 raw rows 但有效 candidate 为 0：`INACTIVE_OR_NOT_EFFECTIVE`。
4. 有效 candidates 超过 1：`AMBIGUOUS`。
5. 有效 candidate 恰好 1：继续执行引用、readiness、runtime version 与 digest 校验。

selector 下存在多个历史 raw rows是正常发布结果；“旧 disabled + 新 enabled/effective”必须成功选择新 row，不得按 raw row 数量报 ambiguity。

选中有效 candidate 后，Catalog 还必须：

* 校验 binding、budget、model enabled/readiness/default/scope。
* 校验 provider/model/endpoint 与 model row 一致。
* 校验 budget 为 `STANDARD` 且 version 一致。
* 校验 legacy 六版本精确为 `v20260705.1`。
* 校验 `ENGINEERING -> ENGINEERING_PROCUREMENT -> v20260705.1` alias。
* 校验 parser/schema 精确匹配 `RuntimeArtifactVersions`。
* 重算并校验 `contentDigest`。
* 不 fallback、不取 `first()`、不静默改用其他 profile。

readiness matrix：

```text
MOCK:
- secretRequired 必须为 false
- readinessStatus 必须为 READY

LOCAL / PUBLIC_OPENAI_COMPATIBLE:
- 本规格没有权威 secret/endpoint readiness source
- 一律 UNSUPPORTED_PROVIDER，不能仅凭 READY 字符串放行
```

新增 `ExecutionBindingResolutionException`，携带稳定 `reason` enum：

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

分类规则：

* selector 下 raw rows 为 0：`NOT_FOUND`。
* enabled 且已生效 candidate 超过 1 条：`AMBIGUOUS`。
* 有 raw rows 但没有 enabled 且已生效 candidate：`INACTIVE_OR_NOT_EFFECTIVE`。
* budget/model/legacy alias/复制字段不一致：`REFERENCE_MISMATCH`。
* MOCK secret/readiness/default/scope 不满足：`PROFILE_NOT_READY`。
* parser/schema 与 code-owned release 不一致：`RUNTIME_VERSION_MISMATCH`。
* digest 重算不一致：`CONTENT_DIGEST_MISMATCH`。
* provider 不是 MOCK：`UNSUPPORTED_PROVIDER`。

稳定异常消息不得包含 secret、连接串或 stack trace。后续 MVP-A 只依赖 `reason` 做事务/API 错误映射，不解析 message。

## 4. 禁止操作

* 不修改 V1 migration。
* 不修改 `ModelProfile`、Model Gateway、Result Composer、Task Execution State Machine。
* 不修改 OpenAPI、review-assets、TASK-036、fixture、expected、Docker、workflow。
* 不新增依赖。
* 不实现任务创建、文件上传、worker 或执行写入。
* 不把 `DEEP_REVIEW / EVALUATION` 设置为 Demo default。

## 5. Git 工作区规则

* 分支必须为 `codex/task-037-execution-binding-release`。
* 执行前记录 `git status --short`。
* 已声明的父 TASK、ADR、规格和架构同步文档 dirty 属于同一 L3 integration unit；不得修改它们，除本规格实现报告小节外。
* 出现未知、范围外或不可解释 dirty 立即 STOP。
* 不得 commit、push、切换分支、reset、clean 或覆盖其他文件。

## 6. 验收标准

### Must Pass

1. V2 migration 在 PostgreSQL 16 上从 V1 顺序应用。
2. 三类 budget seed 数量、version、displayName、policy version、enabled、`5:1` 与固定 UTC timestamps 精确匹配。
3. `MVP_DEMO_MOCK` 的全部 §1.3 字段精确匹配，且为 Demo 唯一 enabled default。
4. binding 的 14 字段全部非空，FK 与复制值一致。
5. binding legacy/module versions 精确匹配冻结值，review-assets validator 仍通过。
6. parser/schema 独立于 OpenAPI、fixture 和 RuleSetVersion。
7. `modelConfigVersion -> modelProfileVersion` 映射测试通过。
8. 0 个 raw row 为 `NOT_FOUND`；0/2 个有效 candidates 分别为 `INACTIVE_OR_NOT_EFFECTIVE / AMBIGUOUS`。
9. disabled/not-effective/wrong-scope/non-default/not-ready 按冻结 reason fail closed。
10. model provider/name/endpoint mismatch 与 budget mismatch fail closed。
11. parser/schema/legacy version mismatch fail closed。
12. digest 可按冻结算法重算；任意参与字段变化都会 mismatch 并 fail closed。
13. lifecycle 唯一索引拒绝同时两个 enabled/default；先撤销旧 lifecycle 后可启用新版本，且索引不使用 `now()`。
14. selector 下“旧 disabled raw row + 新 enabled/effective raw row”成功选择新 row，不因 raw rows=2 误报 ambiguity。
15. `ENGINEERING -> ENGINEERING_PROCUREMENT -> v20260705.1` alias 持久化并由 resolver/test 验证。
16. `READY / NOT_READY` CHECK、MOCK readiness matrix、非 MOCK `UNSUPPORTED_PROVIDER` 测试通过。
17. 八类稳定 failure reason 均有可证伪测试；后续消费者无需解析 exception message。
18. 全量 backend tests 通过，`git diff --check` 通过。

### Must Not

* 任何范围外文件变化。
* 任何 review-assets 状态或 runtime policy 变化。
* 任何 Task Creation、worker、C2 或审核语义实现。
* 任何假 secret、真实 endpoint 或 Production Ready 声明。
* 任何 `first candidate wins` 或内存默认 fallback。

## 7. 测试与验证命令

```powershell
node scripts/validate-review-assets.mjs

Push-Location apps/api-server
gradle test --tests "*ExecutionBindingCatalogTest" --tests "*ExecutionBindingMigrationTest"
gradle test
Pop-Location

git diff --check
git status --short
git diff --stat
```

PostgreSQL 环境使用现有 `CQCP_DB_URL / CQCP_DB_USERNAME / CQCP_DB_PASSWORD`；不得写入或输出 secret。

## 8. 实现报告

### 编码前规格映射计划

提交于编码前，包含 AC1~AC18 逐项映射、14 字段真源表、19 字段 digest 算法、lifecycle raw/effective 顺序、8 类 failure reason matrix、精确预计修改与不修改路径、范围外风险与 STOP 条件、完整测试计划。

初版因七项问题被 Codex `NO_GO_TO_IMPLEMENT`（Testcontainers/embedded DB、跨表 CHECK 暗示、failure reason 分类、无效 `SELECT COUNT(*) ... FOR UPDATE` 聚合、`RuntimeArtifactVersions` 接口/类二选一、DB 唯一索引与 AMBIGUOUS 双层防御说明不清）。修订版逐项关闭后 Codex Decision 为 `ACCEPT_PRE_CODE_PLAN / GO_TO_IMPLEMENT`。

### 实际修改文件（共 8 个，不含本 TASK_SPEC）

| 文件 | 类型 | 行数约 |
|---|---|---|
| `db/migration/V2__execution_binding_release.sql` | 迁移 | 214 |
| `reviewengine/ExecutionBindingRelease.java` | 生产 record | 141 |
| `reviewengine/ExecutionBindingCatalog.java` | 生产 resolver | 250 |
| `reviewengine/ExecutionBindingResolutionException.java` | 生产 Exception + reason enum | 50 |
| `reviewengine/JdbcExecutionBindingRepository.java` | 生产 repository + BindingCandidate DTO | 158 |
| `reviewengine/RuntimeArtifactVersions.java` | 生产 final utility class | 25 |
| `reviewengine/ExecutionBindingCatalogTest.java` | 纯单元测试（mock + fake Clock） | 572 |
| `reviewengine/ExecutionBindingMigrationTest.java` | SpringBootTest + Flyway + @Transactional 集成测试 | 468 |

### Migration 与 seed 结果

- PostgreSQL 16.14 全新空库 V1→V2 顺序应用成功。
- 三类 `review_budget_profile_version` seed（STANDARD/DEEP_REVIEW/EVALUATION）精确写入：version、displayName、`5:1` ratio、budget_approval_policy_version、固定 UTC timestamps、model_budget 八字段 JSONB。仅 STANDARD.enabled=true。
- `model_profile_config_version` seed MVP_DEMO_MOCK：MOCK/DEMO/secretRequired=false/READY/timeoutSeconds=30/retryCount=0。唯一 enabled default（partial unique index 验证通过）。
- `execution_binding_release` seed：14 V1 字段全部 NOT NULL；FK `fk_binding_budget` / `fk_binding_model` 约束；chk_content_digest_hex CHECK；`uq_execution_binding_release_active` 唯一索引。
- seed content_digest：`d79d1d7056d15eb9ed189cabc9980ea000c4188fbd725f9b5c847727488f658b`（独立 Node.js JSON.stringify + SHA-256 预计算，与 Java `ExecutionBindingCatalog.computeDigest()` 交叉核对）。

### 定向/全量测试原始结果

```
# Review assets 验证（未修改）
node scripts/validate-review-assets.mjs
→ passed: 7 file(s) checked, 7 JSON file(s) scanned

# 定向测试（全新 PostgreSQL 16 数据库）
gradle test --rerun-tasks --tests "*ExecutionBindingCatalogTest" --tests "*ExecutionBindingMigrationTest"
→ BUILD SUCCESSFUL — 45 tests (Catalog 30 + Migration 15), 0 failure, 0 skipped

# 全量测试
gradle test --rerun-tasks
→ BUILD SUCCESSFUL — 19 suites / 157 tests / 0 failure / 0 error / 0 skipped

# Git whitespace
git diff --check → exit 0
```

### 实现迭代纪实

实现过程中依次发现并关闭以下缺陷（均在冻结允许文件内修复，无新增依赖，无 V1/OpenAPI/review-assets/Task Creation/C2 修改）：

1. **单元测试编译失败**：`ExecutionBindingCatalogTest` 缺少 `ExecutionBindingFailureReason.*` 静态导入。
2. **Success fixture digest 占位值**：`validBinding()` 硬编码 digest `d3e7a8f2...` 导致 3 个成功路径测试抛出 `CONTENT_DIGEST_MISMATCH`。改为动态 `computeDigest()`。
3. **PostgreSQL COMMENT `||` 拼接语法**：V2 migration 三处 `COMMENT ON` 使用 string concatenation，PostgreSQL 16 报 SQLSTATE `42601`。合并为单行字符串字面量。
4. **budget seed 行序假设**：`ORDER BY profile_code` 字母序为 DEEP_REVIEW/EVALUATION/STANDARD，测试 `rows.get(0)` 假定 STANDARD。改为 `Collectors.toMap(profileCode → row)` key 定位。
5. **SQL seed 占位 digest**：V2 seed content_digest 为 64 个 0，注释写 "placeholder"，真实 repository→Catalog 无法消费。替换为独立预计算真实值。
6. **一期 selector/domain guard 缺失**：Catalog 未约束 `purpose/deploymentScope/contractTypeCode` → Demo MOCK 可能被误用于非 ENGINEERING binding。新增 domain guard + 3 个可证伪单元测试。ENGINEERING alias 校验改为无条件。
7. **FK/seed/lifecycle 可证伪测试缺口**：FK 约束仅 assertThrows(Exception.class)，可能被唯一索引先拦截。修正为 `DataIntegrityViolationException` + 精确约束名断言（`fk_binding_budget` / `fk_binding_model` / `chk_content_digest_hex`），使用 isolate selector + enabled=false 避免碰撞。新增 lifecycle binding 切换和 model default 切换正例。

### STOP / 假设 / 遗留

- **实现 STOP**：无。
- **已验证假设**：
  - PostgreSQL 16 `TIMESTAMPTZ` 在 `to_char(AT TIME ZONE 'UTC')` 输出稳定的 `YYYY-MM-DDTHH:MI:SSZ` 字符串。
  - `DataIntegrityViolationException.getMostSpecificCause().getMessage()` 包含 PostgreSQL 约束名。
  - `DuplicateKeyException` 在 partial unique index 冲突时抛出。
- **已知遗留（本规格范围外）**：
  - LOCAL / PUBLIC_OPENAI_COMPATIBLE provider 继续 `UNSUPPORTED_PROVIDER`；真实 secret/endpoint readiness source 留给后续任务。
  - `modelConfigVersion → modelProfileVersion` 映射在 `ExecutionBindingRelease.versionReferences()` 实现；Task Creation 事务尚未实现，该映射未在生产 snapshot 中验证。
  - review-assets runtime loader 不启用（`DRAFT/NOT_BOUND` 保持不变）。
  - content immutability 依赖版本表治理与无写 API 设计；本任务未添加 DB `UPDATE` trigger 或发布审批流。
- **独立只读实现审计**：第二轮结论为 `GO`，无 P1/P2 blocker。
- **Codex Review Intake**：`ACCEPT_IMPLEMENTATION / GO_TO_COMMIT_PR`。

### `git status --short` / 范围摘要

```
# 工作区状态分类（当前没有已暂存文件）
未暂存修改（Codex 已同步的 TASK-037/ADR/架构/项目记忆声明文件）：
  M CURRENT_CONTEXT.md
  M changelog/2026-07.md
  M docs/ARCHITECTURE.md
  M docs/backend.md
  M docs/database.md
  M tasks/MVP_TASK_MAP.md
  M tasks/active/TASK-GOV-007-task-level-and-git-closure-governance.md

# 未跟踪（本规格 8 个实现文件 + 本 TASK_SPEC）
?? apps/api-server/src/main/java/.../ExecutionBindingCatalog.java
?? apps/api-server/src/main/java/.../ExecutionBindingRelease.java
?? apps/api-server/src/main/java/.../ExecutionBindingResolutionException.java
?? apps/api-server/src/main/java/.../JdbcExecutionBindingRepository.java
?? apps/api-server/src/main/java/.../RuntimeArtifactVersions.java
?? apps/api-server/src/main/resources/db/migration/V2__execution_binding_release.sql
?? apps/api-server/src/test/java/.../ExecutionBindingCatalogTest.java
?? apps/api-server/src/test/java/.../ExecutionBindingMigrationTest.java
?? tasks/active/TASK_SPEC-037-A-execution-binding-release-runtime-source.md

# 未跟踪（Codex 已建档的 TASK/ADR）
?? decisions/ADR-017-...
?? tasks/active/TASK-037-...

# 无范围外 dirty。执行方未修改 V1、OpenAPI、review-assets、ModelGateway、
# Docker、workflow、fixture/expected/outputs；上列架构/项目记忆文件是
# Codex 在 TASK-037 integration unit 中预先声明的文档修改。
```

`git diff --stat` 对 untracked 文件默认不显示；以上 status 分类为完整变更范围。

## 9. Codex Review Intake

编码前计划 Review Intake：

* Decision：`ACCEPT_PRE_CODE_PLAN / GO_TO_IMPLEMENT`。
* 允许实现：仅 §0.3 的 V2 migration、5 个 Java 文件、2 个测试文件和本规格实现报告。
* 明确禁止：新增依赖、Testcontainers/embedded DB、跨表 CHECK、发布 API、Task Creation、review-assets loader、现有审核链路或范围外文件。

实现完成后仍须单独 Review Intake。只有以下输入齐全才能接纳：

* 编码前计划已在代码修改前放行；
* 实现报告与真实 diff 一致；
* 定向/全量测试通过；
* 独立只读实现审计 GO；
* 未触碰禁止范围。

实现后 Review Intake：

* 独立只读实现审计：第二轮结论 `GO`，无 P1/P2 blocker；确认 allowlist、V2 seed/digest、domain guard、FK/lifecycle、14 字段来源与实现报告一致。
* 最终验证：全新 PostgreSQL 16.14 空库定向 45 tests、全量 157 tests、review-assets validator 与 `git diff --check` 全部通过。
* Decision：`ACCEPT_IMPLEMENTATION / GO_TO_COMMIT_PR`。
* 接受的非阻塞残余：content immutability 依赖治理与无写 API而非 DB trigger；readiness CHECK 集成测试后续可收紧为精确 constraint reason。

## 10. 后续联动

TASK-037 merge 后重跑 `TASK-MVP-001` Phase 0。本规格不创建 `TASK_SPEC-MVP-001-A`，也不授权其实现。
