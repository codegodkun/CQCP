# TASK-MVP-001：合同审核用户闭环 Demo

状态：Done / A~F+F2 ACCEPTED / DUAL_AUDIT_GO / ARCHIVED / FEATURE_PR_PENDING

类型：产品父任务 / MVP 用户闭环

Task Level：`L2 Feature`（风险触发型）

Integration unit：`FEATURE-MVP-001`

优先级：P0

负责人：Codex（规划、TASK_SPEC 冻结、Review Intake、Feature 最终接纳）

执行方：A~D 为 Claude Code（DeepSeek 模型）按冻结规格执行；E 起按用户本 Feature
专属授权由主 Codex 实施，独立审计仍保持只读角色分离

创建日期：2026-07-23

来源：用户确认 2026-07-23 “合同审核用户闭环 Demo”方案；`ROADMAP.md` 的“Feature：MVP 用户闭环”；`PRD.md`；`packages/api-contracts/openapi.yaml`

## 背景

CQCP 已具备 DOCX parser 基础能力、Candidate / Evidence / Review Engine、`ReviewResultSnapshot`、持久化结果查询、普通结果页和管理台诊断页面，但这些能力仍缺少非 test harness 的真实用户入口与应用调用闭环。

当前主要断点：

```text
打开系统
-> 上传 DOCX
-> 录入结构化字段
-> 创建 Task / Execution
-> Single Review Worker 执行
-> 轮询状态
-> 自动进入结果页
```

冻结 OpenAPI 已定义任务创建和 execution 状态查询，但真实 Controller、写入适配、worker 和前端入口尚未接通。现有结果页仍依赖用户手工输入预置 `taskId`。

本任务只把既有架构和契约接成可现场演示的最小闭环，不把 Demo 表述为 Production Readiness，也不补做完整 PRD MVP。

## 目标

用户能够：

1. 打开 CQCP 新建审核页面；
2. 上传一份 `.docx` 合同；
3. 录入冻结 OpenAPI 要求的结构化字段；
4. 发起审核并获得 `taskId`、`executionId`、`status` 和 `resultUrl`；
5. 查看公开状态和当前执行阶段；
6. 在 execution 进入终态后自动打开普通结果页；
7. 查看 9 个审核点、状态统计、业务说明、结构化输入值、证据摘要和最小 SourceAnchor 定位。

## 非目标

### 架构与审核语义

* 不修改 EvidenceSlot 架构、CandidateResolver 职责、ReviewPointFamily 或 SYS/Finding 边界。
* 不新增审核链路，不改变 parser -> review engine -> composer 的现有职责分工。
* 不修改 `TaskExecutionStateMachine` 的 TASK-036-C2 激活语义。
* 不激活未获准的 RuleSetVersion，不把本 Feature 当作 TASK-036-C2 或正式 TASK-034 重跑。

### 质量治理

* 不归档 `TASK-EVAL-001`。
* 不修改 fixture、expected JSON、人工 ground truth、评测指标或 TASK-034 历史结果。
* 不声明 57/57 occurrence coverage，不补足 TASK-EVAL-001 DoD #12。

### 产品与基础设施扩展

* 不做 SAP/OA 集成、登录、权限矩阵、多租户、文件管理平台、PDF/OCR、RabbitMQ/Kafka 或分布式 Worker。
* 不做完整 Word 在线预览、字符级高亮、table cell 高亮、多 anchor 可视化或复杂冲突证据展示。
* 不配置新的 required checks、branch protection 或 repository ruleset。
* 不建设外部系统 caller 身份接入；当前 Feature 的创建接口只服务受控管理台 Demo 入口，并由服务端部署上下文固定记录 `callerType=ADMIN`。

## 输入

* 公开契约：`packages/api-contracts/openapi.yaml`
* 架构：`docs/ARCHITECTURE.md`
* 产品边界：`PRD.md`、`PROJECT_BRIEF.md`、`ROADMAP.md`
* 模块说明：`docs/backend.md`、`docs/frontend.md`、`docs/database.md`
* 现有实现：`TaskExecutionStateMachine`、`ResultComposer`、`PersistentTaskResultStore`、普通结果页
* Demo 样本：`packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx`
* Demo 结构化字段：`packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `docs/ARCHITECTURE.md`
* `PRD.md`
* `packages/api-contracts/openapi.yaml`
* `docs/backend.md`
* `docs/frontend.md`
* `docs/database.md`
* `tasks/MVP_TASK_MAP.md`

### Optional Context

* `PROJECT_BRIEF.md`
* `ROADMAP.md`
* `tasks/active/TASK-019-result-composer-review-result-snapshot.md`
* `tasks/active/TASK-020-task-execution-state-machine.md`
* `tasks/active/TASK-021-result-url-query-api.md`
* `tasks/active/TASK-022-persistent-result-query-adapter.md`
* `tasks/active/TASK-023-public-result-page-minimal-display.md`
* `tasks/active/TASK-024-admin-diagnostic-detail-minimal-display.md`
* `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`
* `tasks/active/TASK_SPEC-036-C2-consistency-set-execution-activation.md`，仅在该文件已进入确认 Git 基线时读取

### Out of Scope

* TASK-028 / TASK-031 / TASK-032
* TASK-034 正式重跑
* TASK-036-C2 实现、修改、接纳或 Git 收口
* Review assets、fixture、expected JSON、DOCX、XLSX、matrix 修改
* 新 ADR、数据库 migration
* 除 TASK_SPEC-MVP-001-A 独立规格审计确认的 OpenAPI 3.1 schema 可满足性、验证错误码、管理台 caller 边界和存储故障响应纠错之外的 API 契约变更

## 启动前置条件

以下条件全部满足前，不得创建或派发实现型 `TASK_SPEC`：

1. `TASK-GOV-007` 的治理规则已进入可引用的确认 Git 基线，或 Feature 分支已明确包含等价规则。
2. 从干净、可重建的主线基线创建 `codex/feature-mvp-001-contract-review-loop`；不得带入当前 TASK-036 分支的未提交文件。
3. TASK-036-C2 状态已明确：
   * 若 C2 已独立完成并进入 Feature 基线，本任务只消费已合并行为，不修改 C2；
   * 若 C2 未进入基线，本任务必须绑定已确认 legacy RuleSetVersion，不传入或激活 `v20260715.1`。
4. `git status --short` 中所有既有 dirty 文件均已说明来源和归属；未知或范围外文件触发 STOP。
5. 每个 `TASK_SPEC` 由 Codex 单独冻结，执行方先提交编码前规格映射计划，经 Codex 放行后才能修改代码。
6. Phase 0 必须确认首个 Execution 的全部 NOT NULL version references、model profile、provider、model name 和 endpoint alias 的真实绑定来源；不得为满足 V1 schema 使用假值、空占位或散落硬编码。

## Phase 0 基线核查（2026-07-24）

结论：`GO`。

### Git 事实

* 可重建主线基线：`origin/master@401fd05b7a6c23014adb4f5511533467016c37ba`，即 TASK-037 PR #34 merge commit。
* TASK-GOV-007：PR #33 已合并，merge commit `1f62320f20ec29c52f49c0ed33c4244bb1be669e`。
* TASK-037 / ADR-017：PR #34 已合并，三项 CI 全部通过；V2 binding/profile seed 已进入主线。
* TASK-036 远端分支：`codex/task-036-consistency-set-runtime@3adcab4a34ef4a291b4d69fdc23ab78be485e0d4`，与新主线 merge-base 为 `97ef08f`，相对新主线 7 ahead / 7 behind，无关联 PR。
* TASK_SPEC-036-C2：只存在于原始 dirty 工作区的未跟踪文件，状态为 `NO_IMPLEMENTATION_AUTHORIZATION`；远端分支与主线均不存在 C2 文件或实现。
* 原始 dirty 工作区保持 `codex/task-036-consistency-set-runtime@3adcab4`，未被本 Feature 读取之外的动作修改。

### Feature 冻结选择

* Feature 分支：`codex/feature-mvp-001-contract-review-loop`。
* Feature 不消费、不传入、不激活 `v20260715.1`；继续使用已确认 legacy code-current `v20260705.1`。
* review-assets 继续 `DRAFT / NOT_BOUND / loaderEnabled=false`，TASK-036-C2 不在本 Feature 范围。
* 首个 Execution 通过 `ExecutionBindingCatalog.resolveDefault("MVP_DEMO", "DEMO", "ENGINEERING")` 获取唯一有效 binding；Task Creation 不自行拼装版本值。

### 首个 Execution 14 个 NOT NULL 字段真源

| Execution 字段 | 权威来源 / 冻结值 |
|---|---|
| `contract_type_profile_version` | binding seed：`v20260705.1` |
| `rule_set_version` | binding seed：`v20260705.1`（legacy code-current） |
| `review_budget_profile_version` | FK 到 enabled STANDARD：`budget-standard-v20260724.1` |
| `model_profile_code` | FK 到 Demo default：`MVP_DEMO_MOCK` |
| `model_config_version` | `model-config-mvp-demo-mock-v20260724.1` |
| `parser_version` | `RuntimeArtifactVersions.PARSER_VERSION = parser-docx-word-v20260724.1` |
| `prompt_version` | binding seed：`v20260705.1` |
| `schema_version` | `RuntimeArtifactVersions.SCHEMA_VERSION = model-output-artifact-v20260724.1` |
| `pattern_library_version` | binding seed：`v20260705.1` |
| `field_lexicon_version` | binding seed：`v20260705.1` |
| `evidence_selector_version` | binding seed：`v20260705.1` |
| `provider_type` | model config + binding：`MOCK` |
| `model_name` | model config + binding：`cqcp-demo-mock` |
| `endpoint_alias` | model config + binding：`mock-local` |

`execution.model_config_version` 后续写入 snapshot 时仍映射为同值 `model_profile_version`；TASK_SPEC-MVP-001-A 只创建 Task 与首个 `QUEUED` Execution，不创建 snapshot。

## 冻结产品流程

### 页面与路由

```text
/review/new
-> /review/tasks/{taskId}/executions/{executionId}
-> /review/results/{taskId}?executionId={executionId}
```

后端返回的 `resultUrl` 是跳转真源，前端不得自行拼接与契约不一致的 URL。

### 创建任务

前端使用冻结契约：

```text
POST /api/review/tasks
Content-Type: multipart/form-data
```

请求包含：

* `file`：仅 `.docx`；
* `metadata.contractType`：界面显示“工程采购合同”，提交 `ENGINEERING`；
* `metadata.structuredFields`：必须满足 OpenAPI 全部通用必填字段和付款方式条件必填字段。

不得通过后端静默补造合同名称、项目名称、税额、税率、付款比例或发票类型。允许 Demo 页面从已接受样本显式预填，但用户必须能看到并确认这些值。

当前 Feature 只激活受控管理台 Demo 入口。后端根据部署边界固定写入 `callerType=ADMIN`、`callerId=null`，不得接受客户端自报 caller。外部系统 caller 的可信身份、API key 与 caller policy 接入留给后续独立高风险任务。

### 状态展示

公开 `status` 与内部 `currentStage` 必须分离：

| 字段 | 允许值 |
|---|---|
| `status` | `QUEUED / PROCESSING / SUCCESS / PARTIAL_SUCCESS / FAILED` |
| `currentStage` | `CREATED / QUEUED / PARSING / INDEXING / PLANNING / BUILDING_EVIDENCE / REVIEWING_RULES / REVIEWING_MODEL / COMPOSING / SUCCESS / PARTIAL_SUCCESS / FAILED / CANCELLED` |

页面以 `status` 判断轮询和终态，以 `currentStage` 解释当前阶段；不得返回或展示 `status=PARSING`。

### Demo 级 DOCX 持久化

本 Feature 使用既有架构内的最小本地文件策略：

* 上传根目录必须通过配置或环境变量指定，并通过 Docker bind mount 持久化；
* 服务端生成 task-scoped 随机相对路径，不使用用户原始文件名作为真实存储路径；
* 原始文件名只作为 metadata 保存；
* `task.contract_metadata` 保存受控相对 document reference，不新增数据库 migration；
* worker 只允许在配置根目录内解析规范化后的目标路径，必须拒绝路径穿越；
* 请求结束后不得依赖 multipart 临时文件；
* Demo 清理由明确的运维命令或环境重建完成；不在本 Feature 建设通用文件生命周期平台。

该策略只用于 MVP Demo，不代表 Pilot / Production 的对象存储、加密、保留期和敏感数据策略已经完成。

### 执行编排

复用既有：

```text
Task
-> Execution
-> Single Review Worker
-> Parser
-> Review Engine
-> ResultComposer
-> ReviewResultSnapshot
```

worker 从 PostgreSQL 获取 `QUEUED` execution，顺序执行单份合同并写入 `TaskStageLog`、execution 状态和最终 snapshot。不得引入 MQ、多 worker 或新的模型调用方式。

### 结果展示

普通结果页继续围绕“审核点 -> 证据 -> 原文定位”展示：

* 9 个审核点；
* 状态统计；
* `businessMessage`；
* 对应的结构化输入值；
* 证据摘要；
* 最小 block 级 SourceAnchor 定位。

现有公开 `PointReviewResult` 没有独立 `actualCandidateValue` 字段。本 Feature 不从 `evidenceSummary` 反向解析候选值，也不修改 `ReviewResultSnapshot` / OpenAPI 增加候选值字段。“合同发现候选值”留给后续独立风险边界。

## 计划 TASK_SPEC

以下仅是父任务内的冻结顺序草案；本任务建档不创建这些文件，也不授权实现。

### `TASK_SPEC-MVP-001-A`：任务创建与上传持久化

* 实现 multipart 校验和 `.docx` 拒绝边界；
* 保存受控 document reference；
* 原子创建 Task 与首个 `QUEUED` Execution；
* 返回冻结的 `202` 响应；
* 从 Phase 0 确认的真实版本/模型绑定来源填充 Execution，不得使用 test-only 常量或假值；
* 不触发审核执行。

### `TASK_SPEC-MVP-001-B`：Execution 持久化与 Single Review Worker

* 实现 PostgreSQL execution / stage log / snapshot 写入适配；
* 实现单实例、顺序处理的最小 worker；
* 调用既有 `TaskExecutionStateMachine`；
* 不改变状态机审核语义，不修改 TASK-036-C2。

### `TASK_SPEC-MVP-001-C`：Execution 状态查询 API

* 实现 `GET /api/review/tasks/{taskId}/executions/{executionId}`；
* 公开 `status` 与 `currentStage` 分离；
* 返回 `terminal`、`snapshotAvailable` 和后端生成的 `resultUrl`；
* 不泄露内部 `SYS-*`、prompt、raw output、endpoint 或 stack trace。

### `TASK_SPEC-MVP-001-D`：新建审核页面

* 实现 `/review/new`；
* 支持 DOCX 选择、完整冻结字段、Demo 样本预填和业务化错误提示；
* 按 OpenAPI 生成 multipart 请求；
* 防止重复点击产生并发提交。

### `TASK_SPEC-MVP-001-E`：状态页、轮询与结果跳转

* 实现 execution 状态页；
* 终态停止轮询；
* `SUCCESS / PARTIAL_SUCCESS` 自动打开后端 `resultUrl`；
* `FAILED` 停留并展示业务化失败信息；
* 结果页支持正式路由，并展示结构化输入值和现有证据摘要。

### `TASK_SPEC-MVP-001-F`：Docker Compose 真实 Demo 验收

* 使用既有真实 DOCX 和已接受结构化输入；
* 从浏览器入口完成一次 Task 创建、worker 执行、状态轮询和结果展示；
* 核对 PostgreSQL 中 Task、Execution、stage logs 和 Snapshot；
* 形成 Feature 完成态验收证据；
* 不运行或改写 TASK-034 正式 E2E。

## 交付物

* 真实任务创建与状态查询 API；
* Demo 级 DOCX 持久化；
* PostgreSQL execution 写入与 Single Review Worker；
* 新建审核页面；
* execution 状态页；
* 自动结果跳转；
* 普通结果页最小增强；
* Docker Compose 浏览器 E2E 证据。

## Definition of Done

### 用户闭环

* 浏览器打开 `/review/new`，无需预置 `taskId`。
* 用户上传既有已接受 `.docx`，并确认完整结构化字段。
* 创建接口返回 `202`、`taskId`、`executionId`、`status=QUEUED` 和 `resultUrl`。
* 状态页展示公开状态与当前阶段，并在终态停止轮询。
* 成功或部分成功后自动进入后端返回的结果 URL。

### 非 test harness 应用链路

* 不依赖 test-only harness 或 `InMemoryTaskResultStore`。
* PostgreSQL 真实保存 Task、Execution、stage logs 和 `ReviewResultSnapshot`。
* worker 从 `QUEUED` 推进到终态。
* 上传文件在请求结束后仍能从受控持久化路径读取。
* 失败时 execution 进入 `FAILED`，不得生成无可靠证据的业务 Finding。

### 结果页

* 展示 9 个启用审核点及状态统计。
* 展示业务说明、对应结构化输入值、证据摘要和最小 SourceAnchor 定位。
* 选定 Demo 样本必须出现 9 个启用审核点；状态可与 `outputs/task-034-mvp-e2e-acceptance/sample-results/CQCP-MVP-DOCX-001.json` 中 snapshot identity `task-034-CQCP-MVP-DOCX-001:execution-034-CQCP-MVP-DOCX-001:2026-07-14T00:00:00Z` 做非回归一致性比较，但该历史系统输出不构成独立正确性 ground truth。
* 不要求出现人为构造的 `NOT_CONCLUDED`；如需混合状态演示，必须引用已有合法负例并另行冻结。

### 边界

* 未修改 EvidenceSlot、CandidateResolver、ReviewPointFamily、SYS/Finding 或模型职责。
* 未修改 fixture、expected JSON、人工 ground truth、TASK-034 历史结果或 TASK-036-C2。
* 未新增 migration、MQ、多 worker、认证、PDF/OCR 或文件管理平台。
* 本 Feature 只证明“可演示用户闭环”，不声明“完整 PRD MVP”“Production Ready”或“正式 E2E 质量达标”。

## 测试与验证

每个 TASK_SPEC 必须冻结可证伪测试；Feature 最终至少包含：

* 后端任务创建 API contract / validation tests；
* document reference 路径安全、临时文件独立性和重复文件名测试；
* PostgreSQL Task / Execution / stage log / snapshot persistence tests；
* worker success / partial success / failure 状态转移测试；
* status API public status / currentStage mapping tests；
* 前端新建审核、校验、提交、轮询、失败和跳转测试；
* 现有结果页、管理台、parser、review engine 和 result query 回归；
* Docker Compose 真实浏览器 Demo 验收。

独立审计必须覆盖：

* `TASK_SPEC-MVP-001-A` 的上传与 PostgreSQL 写入边界；
* `TASK_SPEC-MVP-001-B` 的主应用执行编排；
* Feature 完成态 diff 与真实 Demo E2E；
* 是否绕过 TASK-036-C2、fixture/expected 和 SYS/Finding 门禁。

## Git / 收口规则

* 默认一个 Feature 分支：`codex/feature-mvp-001-contract-review-loop`。
* 允许父任务内多个有语义的 commit，不为每个 TASK_SPEC 创建独立 PR。
* 最终以一个 Feature Review、一个 PR、CI、独立审计、Codex Review Intake 和一次 merge 收口。
* `CQCP Code Review` / `CQCP Spec & Docs Review` 当前不是机制化 required checks，不得在证据中写成已自动强制。
* 未经用户明确授权，不执行 commit、push 或 merge。

## 文档更新要求

Feature 完成后统一更新：

* 本父 TASK；
* `CURRENT_CONTEXT.md`；
* `tasks/MVP_TASK_MAP.md`；
* `changelog/当前月份.md`；
* 实际发生行为变化的模块文档。

单个 TASK_SPEC 的编码前计划、实现报告、commit、push 或 checks 状态不单独创建归档流程。

ADR：当前不需要。本任务实现已冻结 API、数据库和审核链路；如果实现中需要修改 OpenAPI、migration、核心审核语义、TASK-036-C2 或文件存储长期架构，立即 STOP，重新判断 ADR。

## 风险

* 当前工作区属于 TASK-036 分支且包含未收口文档和未跟踪 C2 文件，直接实施会混入错误基线。
* DOCX 请求临时文件不能支撑异步 worker，必须先完成受控持久化。
* OpenAPI 结构化字段多于最初产品草案中的四个字段，不能静默补默认值。
* 公开 status 与内部 currentStage 混用会破坏外部契约。
* 现有结果快照不承载独立候选值，前端不得从证据文案反向解析。
* arbitrary DOCX 不能保证固定点级结果；Demo 必须绑定已接受样本和输入。
* V1 execution 的版本与模型字段均为 NOT NULL；如果真实绑定来源未先冻结，容易用占位值制造不可追溯快照。
* 执行编排容易越界修改 C2 或核心审核语义，必须保持独立审计。

## 待确认

* 已确认：TASK-036-C2 未进入 Feature 基线，本 Feature 使用 legacy `v20260705.1`。
* 已确认：`TASK_SPEC-MVP-001-B` 实现已通过 Codex Review Intake 与独立只读实现审计，
  12 个接受文件提交为 `21203998da7482e25d86136c14d1f42cff2d2ec7`；B 不 push，
  Feature 工作区提交后 clean。
* 已确认：`TASK_SPEC-MVP-001-C` 已通过 Codex Review Intake；C 定向 43/43、
  backend 全量 313/313、review-assets validator 7/7 与 `git diff --check` 通过，
  实现提交为 `0eb9d9e312c6698fb4532e623362bea091165f87`，未 push。
* 已确认：`TASK_SPEC-MVP-001-D` 已接纳并提交为 `9fcf857`。
* 已确认：`TASK_SPEC-MVP-001-E` 状态页、1000ms 非重叠轮询、安全结果跳转、正式结果
  路由与结构化字段白名单实现已经 Codex Review Intake 接纳；Codex 重跑 admin-web
  `62/62`、lint、production build、review-assets validator `7/7` 和
  `git diff --check` 全部通过；E 已提交为 `539c9e6`。
* 已确认：`TASK_SPEC-MVP-001-F` 已完成标准 Compose、真实浏览器、PostgreSQL 与
  上传持久化验收；Task/Execution 最终 `SUCCESS`，9/9 审核点 `PASS`，
  证据位于 `outputs/task-mvp-001-demo-acceptance/`。
* 已确认：F 回归暴露的创建任务集成测试隔离缺陷已由
  `TASK_SPEC-MVP-001-F1` 定点修复并提交为 `439a020`；定向 16/16、连续两轮
  全量后端强制回归均为 313/313。
* 已确认：首轮 Codex 双代理审计为一条 `GO`、一条 `NO-GO`，旧冻结基线失效且
  CC AUDIT 未发送。`TASK_SPEC-MVP-001-F2` 已修复创建响应 whitespace identity /
  空白 resultUrl 和正式结果 query 静默 trim 两项 fail-closed 缺口，并补齐原始
  验收输出；修复前 `2 failed / 31 passed`，修复后定向 33/33、admin-web 全量
  63/63、后端连续两轮 313/313，重建后的真实任务仍为 `SUCCESS / 9 PASS`。
* 已确认：第二次冻结复核发现数据库证据输出包含容器绝对上传根，且缺少可独立复核的
  red stdout；该基线再次失效，CC AUDIT 仍未发送。证据现仅输出文件 size，并已在
  独立临时副本用旧实现 + staged tests 固化 `EXIT_CODE=1 / 2 failed / 31 passed`，
  当前实现对应 green 为 `EXIT_CODE=0 / 33 passed`。

## Next Task Handoff

本父任务与 A~F/F1/F2 已完成实现、真实 Demo、Memory Writeback 和双重独立审计；
不存在新的 TASK Handoff。后续仅继续同一 `FEATURE-MVP-001` 的完成态 commit、push、
一个 Feature PR、CI 与 merge，不创建新的执行任务。

## 规划建档审查

* 独立只读审计：`GO`。两项首轮文档阻塞已关闭：ARCHITECTURE 的 MySQL 残留已对齐 PostgreSQL；九点状态验收已改为明确 snapshot 的非回归一致性，不再声明独立正确性。
* Codex Review Intake：`ACCEPT_FEATURE_PLAN / PHASE_0_NEXT / NO_TASK_SPEC_CREATED / NO_IMPLEMENTATION_AUTHORIZATION`。
* ADR：不需要。当前只对齐既有 PostgreSQL、OpenAPI、V1 schema 和审核链路；没有新增或改变产品架构。

## 完成记录

* 完成日期：2026-07-28。Feature 实现、Demo 验收、Memory Writeback 与双重独立
  审计完成；当前只剩同一 Feature 的 PR/CI/merge 集成。
* 变更文件：A 见 `TASK_SPEC-MVP-001-A` 实现报告；B 见
  `TASK_SPEC-MVP-001-B` 第 9 节实现报告；C 见
  `TASK_SPEC-MVP-001-C` 第 9 节实现报告；D/E/F/F1 见各自实现报告。
* 测试结果：A 的 Docker Compose 权威全量 backend 232/232 通过；B 定向 48/48，
  全量 backend 270/270；C 定向 43/43、全量 backend 313/313；E admin-web
  `63/63`、lint、production build、review-assets validator `7/7` 和
  `git diff --check` 通过；F 真实浏览器闭环为 `SUCCESS / 9 PASS`，F1 定向 16/16、
  连续两轮 backend 313/313；F2 定向 33/33、重建真实路径 `SUCCESS / 9 PASS`；
  A~F+F2 均已接纳。
* 遗留问题：C2 未进入基线，本 Feature 已冻结使用 legacy `v20260705.1`。
* 备注：E 已接纳并提交为 `539c9e6`；F 已接纳；F1 已提交为 `439a020`；
  F2 已接纳并进入 Feature 完成态提交。首轮与 v2~v4 旧冻结均已失效；最终 v5
  冻结以 base `401fd05b7a6c23014adb4f5511533467016c37ba`、HEAD
  `439a02012c54f11577c6e916591d69db2a7d5d58`、index tree
  `dc81a8c3d2636844db7e7e85748ca507720323fd` 为基线，覆盖 102 个 changed paths。
* 冻结证据：full diff SHA-256
  `D60D11D81D6FBEEBE29FF32553B2A6159853820C988D8A8278F605AD559BC37F`；
  freeze manifest SHA-256
  `E7F6A67D36F253FFFC3704DE5E7BFF128DE8E64798FCCCA45995B61022D92C4E`；
  102/102 文件与 37/37 原始证据 SHA-256 均经独立复核匹配。
* 双重独立审计：CC AUDIT 为 `GO`，`P0=0 / P1=0 / P2=0`、无 blocking
  findings；Codex 代码/API/数据库/状态机 subagent 为 `GO`，Codex
  测试/前端安全/Compose/真实 Demo subagent 为 `GO`。最终门禁
  `CC_AUDIT=GO AND CODEX_SUBAGENT_AUDIT=GO AND blocking findings=0` 已满足。
* 非阻塞观察：`REVIEWING_MODEL` 枚举在当前 Demo 路径未使用；两个 runtime
  data 目录保持 untracked 且不得提交；TASK-034 正式 FAIL 与本 Feature Demo
  SUCCESS 的语义边界继续保留；后续任务应保持 `openapi.json` / `openapi.yaml`
  等价。以上均不构成本 Feature blocker。
* Codex 最终 Review Intake：
  `ACCEPT_FEATURE_IMPLEMENTATION / DUAL_AUDIT_GO / GO_TO_COMMIT_PR_CI_MERGE`。
* Integration unit / PR：`FEATURE-MVP-001` / A commit `f8d76e7` /
  B commit `21203998da7482e25d86136c14d1f42cff2d2ec7` /
  C commit `0eb9d9e312c6698fb4532e623362bea091165f87` / D commit `9fcf857`；
  E commit `539c9e6`；F1 commit `439a020`；Feature 完成态 PR 尚未创建。
* 独立审计触发依据：公开 API、PostgreSQL 写入、上传文件安全、主应用执行编排和真实 Demo E2E。
