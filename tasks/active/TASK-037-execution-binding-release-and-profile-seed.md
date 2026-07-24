# TASK-037：Execution Binding Release 与 Profile Seed

状态：Active / ADR-017 Accepted / 实现与独立审计 GO / Codex Review Intake ACCEPT / Ready for PR

类型：数据库 / 运行时绑定治理

Task Level：`L3 高风险治理`

Integration unit：独立高风险 PR

优先级：P0

负责人：Codex（边界、ADR、规格、Review Intake、Git 收口）

执行方：Claude Code / DeepSeek，仅执行 Codex 放行后的 `TASK_SPEC-037-A`

创建日期：2026-07-24

来源：`TASK-MVP-001` Phase 0；独立 binding source 审计；用户于 2026-07-24 授权按已审计方案创建并实施 `TASK-037 / ADR-017`，采用 `MVP_DEMO_MOCK` 与三类预算 profile seed，并授权必要的 commit、push、PR、merge

## 背景

V1 `execution` 表要求以下 14 个字段全部 `NOT NULL`：

```text
contract_type_profile_version
rule_set_version
review_budget_profile_version
model_profile_code
model_config_version
parser_version
prompt_version
schema_version
pattern_library_version
field_lexicon_version
evidence_selector_version
provider_type
model_name
endpoint_alias
```

当前主线只有字段形状、静态 review-assets 映射和最小 `ModelProfile` record，没有生产任务创建可消费的权威 binding source。`ruleset-v20260705.1.json` 仍为 `DRAFT / NOT_BOUND`，不得被解释为 runtime loader 已发布；parser 与 model-output schema 也没有正式 release 标识。直接进入 `TASK_SPEC-MVP-001-A` 会迫使实现使用假值、test-only 常量或散落硬编码。

## 目标

* 通过 `ADR-017` 冻结 Demo execution binding 的真源、复制语义、readiness 与 legacy 边界。
* 新增 content immutable 的 `ReviewBudgetProfile` 版本表，并 seed `STANDARD / DEEP_REVIEW / EVALUATION` 三类 profile。
* 新增 content immutable 的 `Model Profile Config` 版本表，并 seed `MVP_DEMO_MOCK`。
* 新增部署级 `Execution Binding Release`，一次性绑定 V1 execution 的全部 14 个 `NOT NULL` 字段。
* 新增 fail-closed resolver，供后续任务创建用例消费。
* 为 code-current parser 与 model-output schema 建立独立、可验证的 release 常量。

## 非目标

* 不实现 `POST /api/review/tasks`、multipart 上传、Task/Execution 写入事务或 worker。
* 不修改 OpenAPI、V1 migration、现有 Task 状态机、Result Composer、CandidateResolver、EvidenceSlot 或 SYS/Finding 边界。
* 不启用 `packages/review-assets` runtime loader，不把 `DRAFT / NOT_BOUND` 提升为 `ACTIVE`。
* 不激活 TASK-036 B1/B2/C1/C2，不使用 `v20260715.1`。
* 不接入真实本地模型、公网模型、endpoint secret 或生产模型。
* 不把 `Execution Binding Release` 建模为第四类业务配置包。

## 输入

* `docs/ARCHITECTURE.md` 第 9.3、13、16、22.1、22.3 节
* `docs/backend.md`
* `docs/database.md`
* `docs/model-gateway-budget-baseline.md`
* `decisions/ADR-006-model-profile-switching-and-public-provider-scope.md`
* `tasks/done/TASK-030-review-assets-versioning-governance.md`
* `packages/review-assets/rule-sets/ruleset-v20260705.1.json`
* `apps/api-server/src/main/resources/db/migration/V1__cqcp_mvp_core_schema.sql`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `decisions/ADR-017-execution-binding-release-and-demo-profile-readiness.md`
* `tasks/active/TASK_SPEC-037-A-execution-binding-release-runtime-source.md`
* `docs/ARCHITECTURE.md`
* `docs/backend.md`
* `docs/database.md`
* `docs/model-gateway-budget-baseline.md`
* `decisions/ADR-006-model-profile-switching-and-public-provider-scope.md`
* `tasks/done/TASK-030-review-assets-versioning-governance.md`
* `packages/review-assets/rule-sets/ruleset-v20260705.1.json`
* `apps/api-server/src/main/resources/db/migration/V1__cqcp_mvp_core_schema.sql`

### Optional Context

* `PRD.md`
* `packages/api-contracts/openapi.yaml`
* `apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/`

### Out of Scope

* TASK-MVP-001 业务实现
* TASK-036 B/C 与正式 E2E
* Review assets 发布审批与动态 loader
* Model Gateway 真实 provider 配置
* 前端、Docker、CI、权限和部署改造

## 范围

### 包含

* `ADR-017`。
* Flyway V2 migration：三类 budget seed、`MVP_DEMO_MOCK`、唯一 Demo binding。
* Java binding release record、repository、resolver 与 code-owned parser/schema release 常量。
* 单元测试与 PostgreSQL migration/integration test。
* `docs/ARCHITECTURE.md`、`docs/backend.md`、`docs/database.md` 的 ADR 接受后同步。
* 父 TASK、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md` 和 `changelog/2026-07.md` 的 L3 收口写回。

### 不包含

* 任务创建 Controller / service / persistence。
* 对现有 `execution` 或 snapshot 字段改名。
* 新增 `execution.binding_version` 字段。
* 修改静态资产 JSON 的状态、内容或 runtime policy。

## 约束

### 绑定真源

* 新 execution 的 14 字段必须来自一条有效 `execution_binding_release`，不得由调用方拼装。
* binding resolver 必须要求选择结果恰好一条；缺失、重复、失效、引用不一致或 readiness 失败均 fail closed。
* execution 创建后复制 14 字段，不在执行中回查“当前默认值”。
* `review_result_snapshot.model_profile_version` 与 `execution.model_config_version` 表示同一个 immutable config version；本任务只冻结映射，不改历史字段名。
* 版本 identity 与配置 content 不得原地修改；`enabled / isDefaultForNewTask / readinessStatus` 是独立 lifecycle state，可在受控事务中更新。
* 发布新默认必须在单个事务内先撤销旧 default/enabled，再插入或启用新版本；回滚同样通过 lifecycle 切换，不修改旧 content。
* `execution_binding_release.enabled` 是 lifecycle state；`effectiveFrom` 只由 resolver 判断，数据库唯一索引只约束同一选择域最多一个 `enabled=true`，不得在 partial index 中使用 `now()`。

### legacy 行为

* Demo binding 使用 `rule_set_version=v20260705.1`，语义是 code-current legacy 行为。
* manifest 的六个 module reference 只提供来源追溯；runtime 仍不从资产文件加载规则。
* `contract_type_profile_version / prompt_version / pattern_library_version / field_lexicon_version / evidence_selector_version` 使用该 manifest 已记录的 `v20260705.1` 引用。
* 外部契约 `contractTypeCode=ENGINEERING` 与 legacy 静态画像 `profileCode=ENGINEERING_PROCUREMENT` 的 alias 必须作为 binding metadata 显式保存并由 resolver/test 精确验证；不得把两个 identity 当作同一个字符串。

### Model readiness

* `MVP_DEMO_MOCK` 只允许 `usageScope=DEMO`，不得标记 Production Ready。
* `providerType=MOCK` 使用 provider-specific readiness：`secretRequired=false`、`readinessStatus=READY`。
* 不得伪造 `secretConfigured=true`。
* Demo 默认 profile 必须唯一。

### Budget seed

* 必须 seed `STANDARD / DEEP_REVIEW / EVALUATION`。
* `STANDARD` 为本次 binding 唯一使用的 active profile。
* `DEEP_REVIEW / EVALUATION` 只完成不可变 seed，可保持不可选择；不得凭空声明更高质量或生产 SLA。
* `standardToDeepRatio=5:1`。
* `modelBudget` 使用架构文档已给出的保守启动基线；三类 profile 如共享该启动值，必须显式说明“尚未形成差异化质量承诺”。
* 三类 seed 的 `budgetApprovalPolicyVersion` 统一为 `budget-approval-policy-mvp-v20260724.1`。
* 所有 seed 的 `effectiveFrom / createdAt` 固定为 UTC `2026-07-24T00:00:00Z`；它是本次 release metadata，不读取部署机器当前时间。

## 交付物

* `decisions/ADR-017-execution-binding-release-and-demo-profile-readiness.md`
* `tasks/active/TASK_SPEC-037-A-execution-binding-release-runtime-source.md`
* Flyway V2 migration
* runtime binding resolver 与测试
* ADR 接受后的架构/数据库/后端说明
* L3 审计、Review Intake、PR/CI/merge 证据

## 验收标准

1. PostgreSQL migration 可在 V1 后成功应用。
2. 三类 budget profile 各有一个 immutable seed，`STANDARD` 可用，比例为 `5:1`。
3. `MVP_DEMO_MOCK` 为 Demo 唯一默认，`MOCK / secretRequired=false / READY`。
4. Demo binding 的 14 个字段全部非空，且 budget/model 外键与复制字段一致。
5. parser/schema 使用独立 code-owned release，不复用 OpenAPI `0.1.0`、fixture `v1` 或 RuleSetVersion。
6. resolver 对 0 条 raw row、0/2 条 effective candidate、disabled/not-effective、not-ready、model/budget mismatch、parser/schema mismatch 按稳定 reason fail closed；“旧 disabled + 新 enabled/effective”两条 raw rows必须成功选择新 row。
7. resolver 产出的 snapshot `modelProfileVersion` 精确等于 execution `modelConfigVersion`。
8. review-assets 保持 `DRAFT / NOT_BOUND / loaderEnabled=false`。
9. 不修改 OpenAPI、V1 migration、现有审核链路、TASK-036 或评测资产。
10. 独立规格审计、实现审计、Codex Review Intake、CI 全部通过后才可 merge。
11. content digest 按 ADR-017 的确定字段顺序与 UTF-8 紧凑 JSON 数组算法重算并匹配；任意参与字段变化必须导致 digest 变化。
12. lifecycle/default 切换语义可由 PostgreSQL 唯一索引与 resolver 测试证伪，不依赖 `now()` partial index。
13. `ENGINEERING -> ENGINEERING_PROCUREMENT` legacy alias 被持久化并验证；不得只比较 `contract_type_profile_version`。

## 测试与验证

```powershell
node scripts/validate-review-assets.mjs
gradle test
git diff --check
git diff --name-status origin/master...HEAD
```

定向测试必须覆盖 migration seed、唯一默认约束、14 字段完整性、legacy/module mapping、MOCK readiness、parser/schema release 与 fail-closed resolver。

## 文档更新要求

* ADR 接受后更新 `docs/ARCHITECTURE.md`、`docs/backend.md`、`docs/database.md`。
* L3 merge 前更新父 TASK、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-07.md`。
* 不更新静态 review-assets 内容或其 `DRAFT / NOT_BOUND` 声明。

ADR：必须，见 `ADR-017`。

## Next Task Handoff

本任务 merge 后，Codex 必须从新的 `origin/master` 重跑 `TASK-MVP-001` Phase 0。只有 binding source、TASK-036 legacy 边界和全部前置门禁都为 GO，才可创建 `FEATURE-MVP-001` 分支并冻结 `TASK_SPEC-MVP-001-A`。

## 风险

* 把 deployment binding 误做成第四类业务配置包。
* 将 legacy manifest 的可追溯引用误写成 runtime loader 激活。
* 为 MOCK 伪造 secret 状态。
* 为 DEEP_REVIEW / EVALUATION 编造未经评测的预算差异。
* resolver 只取第一条而未拒绝重复默认。
* migration seed 与 Java code-owned parser/schema release 漂移。

## 待确认

无。用户已确认采用 `MVP_DEMO_MOCK` 与三类 budget seed；其余字段与边界由本任务和 ADR-017 冻结。

## 完成记录

* 实现完成日期：2026-07-24；父任务待 PR/CI/merge 后完成。
* 变更文件：V2 migration、5 个 binding/runtime Java 文件、2 个定向测试文件、TASK/ADR/架构与项目记忆文档。
* 测试结果：全新 PostgreSQL 16.14 空库定向 45 tests、全量 backend 157 tests 全部通过；review-assets validator 与 `git diff --check` 通过。
* 独立审计：规格审计最终 `GO`；第二轮实现审计 `GO`，无 P1/P2 blocker。
* Codex 实现 Review Intake：`ACCEPT_IMPLEMENTATION / GO_TO_COMMIT_PR`。
* Integration unit / PR：独立 L3 PR / 待创建。
* Memory Writeback：实现接纳状态已写入父 TASK、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md` 与当月 changelog；merge 事实待后续相关分支写回。
* 规格审计：最终 `GO`；允许 ADR 接受、架构同步和编码前映射计划，不直接授权实现。
* Codex 规格 Review Intake：`ACCEPT_ADR_AND_SPEC / GO_TO_ARCHITECTURE_SYNC / PRE_CODE_PLAN_REQUIRED`。
