# TASK_SPEC-MVP-001-C：Execution 状态查询 API

状态：DONE / ARCHIVED_WITH_FEATURE / DUAL_AUDIT_GO / FEATURE_PR_PENDING

TASK_SPEC 类型：`execution`

父 TASK：`TASK-MVP-001`

父 TASK Level：`L2 Feature`（公开 API / PostgreSQL 查询边界）

Integration unit：`FEATURE-MVP-001`

执行方：Claude Code / DeepSeek

所在分支：`codex/feature-mvp-001-contract-review-loop`

规格冻结基线 commit：`21203998da7482e25d86136c14d1f42cff2d2ec7`

## 0. 任务摘要

实现冻结 OpenAPI 中的：

```text
GET /api/review/tasks/{taskId}/executions/{executionId}
```

接口从 PostgreSQL 精确读取同一 `taskId + executionId` 的 execution，返回有限公开
`status`、内部 `currentStage`、`terminal`、真实 `snapshotAvailable`、Task 已持久化的
`resultUrl`、执行绑定模型摘要和时间字段。

本规格是只读查询能力，不修改 execution、stage log 或 snapshot，不新增 migration、
OpenAPI、依赖、缓存、认证或前端，不读取或返回 stage log、diagnostics、`SYS-*`、
prompt、raw output、真实 endpoint URL、secret 或 stack trace。

### 0.1 角色与门禁

* Codex 冻结规格、审查编码前规格映射计划、实现报告和真实 diff。
* Claude Code / DeepSeek 修改任何文件前，必须先提交 §0.2 计划并暂停。
* Codex 明确 `GO_TO_IMPLEMENT` 前不得编码。
* 执行方不得 commit、push、切分支、创建 PR、修改父 TASK 或扩大文件范围。
* 本 TASK_SPEC 只作为 Feature 内局部执行与 Review Intake 单位，不单独创建 PR。

### 0.2 编码前规格映射计划

必须逐项输出并等待 Codex 审查：

```text
AC1~AC18 映射：
- 每条验收断言的真实 PostgreSQL 输入、查询/映射路径、HTTP 结果和测试。

SQL 与 identity：
- JOIN/WHERE 如何同时约束 task.task_id、execution.task_id、execution.execution_id。
- 为什么 task 存在但 execution 属于另一 task 时仍返回同一个 404，且不泄露存在性。
- snapshotAvailable、superseded、supersededReason 的 LEFT JOIN 真源。

状态映射：
- CREATED/QUEUED -> QUEUED。
- PARSING/INDEXING/PLANNING/BUILDING_EVIDENCE/REVIEWING_RULES/
  REVIEWING_MODEL/COMPOSING -> PROCESSING。
- SUCCESS -> SUCCESS；PARTIAL_SUCCESS -> PARTIAL_SUCCESS。
- FAILED/CANCELLED -> FAILED。
- terminal 的独立计算以及 currentStage 保留内部原值。

响应字段：
- resultUrl 直接来自 task.result_url，禁止重新拼接。
- reviewModel 五字段精确来自 execution；endpointAlias 是冻结公开 alias，
  不等于 endpoint URL。
- createdAt/updatedAt 的 timestamptz -> Instant -> ISO-8601 映射。
- optional superseded 字段的序列化策略。

安全边界：
- 成功和 404 响应如何保证只出现 OpenAPI 字段。
- 不读取/返回 stage logs、business_reason、diagnostic_code、detail_payload、
  SYS-*、prompt、raw output、endpoint URL、secret、stack trace。
- 不记录完整响应、模型敏感配置或数据库异常原文。

明确不修改：
- migration、OpenAPI YAML/JSON、A/B 实现、TaskExecutionStateMachine、
  worker、review engine、result composer、result query、review-assets、
  fixture/expected、TASK-036/C2、前端、Compose。

预计测试：
- service 全 13 个内部状态映射；
- repository exact identity / snapshot / superseded；
- controller 200/404 契约与字段白名单；
- PostgreSQL + MockMvc 的 A POST -> C QUEUED -> B runOnce -> C terminal 集成；
- A/B/C 定向和全量 backend regression。
```

### 0.3 文件访问范围

```text
✅ 允许新增：
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusController.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusService.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusRepository.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusModels.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusExceptionHandler.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusControllerTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusServiceTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusRepositoryTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusIntegrationTest.java

✅ 允许修改：
  tasks/active/TASK_SPEC-MVP-001-C-execution-status-query-api.md

👀 允许只读：
  AGENTS.md
  CURRENT_CONTEXT.md
  PRD.md
  tasks/active/TASK-MVP-001-contract-review-user-loop-demo.md
  tasks/active/TASK_SPEC-MVP-001-A-task-creation-upload-persistence.md
  tasks/active/TASK_SPEC-MVP-001-B-single-review-worker-persistence.md
  docs/ARCHITECTURE.md
  docs/backend.md
  docs/frontend.md
  docs/database.md
  docs/VERIFY.md
  packages/api-contracts/openapi.yaml
  apps/api-server/build.gradle.kts
  apps/api-server/src/main/resources/db/migration/V1__cqcp_mvp_core_schema.sql
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreation*.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/SingleReviewWorker*.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskResultQuery*.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/PersistentTaskResultStore.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/SingleReviewWorkerIntegrationTest.java

⛔ 禁止访问：
  .env
  .env.*
  secrets/
  credentials/
  config/production/
  packages/test-fixtures/
  outputs/
```

## 1. 上下文与架构红线

链路位置：

```text
POST /api/review/tasks（A）
  -> QUEUED Execution
  ->【GET execution status】← 本规格，可在任意阶段轮询
  -> Single Review Worker（B）
  -> terminal Execution + optional persisted Snapshot
  ->【GET execution status】← 本规格，返回 terminal 与 snapshotAvailable
  -> 状态页（E，后续）
```

红线：

1. 公开 `status` 与内部 `currentStage` 必须分离；不得返回 `status=PARSING` 等内部值。
2. 查询必须精确绑定 `taskId + executionId`；不得按 executionId 单独查询后忽略 taskId。
3. `resultUrl` 必须读取 `task.result_url`；不得由 Controller 或 Service 重新拼接。
4. `snapshotAvailable` 只由同 task/execution 的 `review_result_snapshot` 是否存在决定；
   不得由 terminal 状态推测。
5. `reviewModel` 只返回 OpenAPI 冻结的五字段摘要；`endpointAlias` 可返回，
   真实 endpoint URL、secret 与完整配置不得返回。
6. 不读取 stage log、diagnostics 或失败异常原文；本接口不是管理台诊断 API。
7. 不修改数据库、OpenAPI、状态机、worker 或现有结果查询语义。

## 2. 冻结实现契约

### 2.1 精确只读查询

单次 repository 查询语义必须等价于：

```sql
SELECT
  t.task_id,
  t.result_url,
  e.execution_id,
  e.status,
  e.current_stage,
  e.model_profile_code,
  e.provider_type,
  e.model_name,
  e.endpoint_alias,
  e.model_config_version,
  e.created_at,
  e.updated_at,
  s.execution_id AS snapshot_execution_id,
  s.superseded_by_execution_id,
  s.superseded_reason
FROM task t
JOIN execution e
  ON e.task_id = t.task_id
LEFT JOIN review_result_snapshot s
  ON s.task_id = e.task_id
 AND s.execution_id = e.execution_id
WHERE t.task_id = ?
  AND e.execution_id = ?
```

要求：

* 查询只返回零或一行；多行视为数据完整性错误并 fail closed。
* task 不存在、execution 不存在、execution 属于另一 task，统一映射同一 404。
* 不为判断 404 额外查询 execution 是否存在，避免泄露跨 task identity。
* 不查询 `task_stage_log`、snapshot JSON payload 或任何诊断字段。
* repository/service/controller 全部保持只读，不执行 UPDATE/INSERT/DELETE。

### 2.2 公开状态与终态

冻结映射：

| 内部 execution status | 公开 status | terminal |
|---|---|---|
| `CREATED` | `QUEUED` | `false` |
| `QUEUED` | `QUEUED` | `false` |
| `PARSING` | `PROCESSING` | `false` |
| `INDEXING` | `PROCESSING` | `false` |
| `PLANNING` | `PROCESSING` | `false` |
| `BUILDING_EVIDENCE` | `PROCESSING` | `false` |
| `REVIEWING_RULES` | `PROCESSING` | `false` |
| `REVIEWING_MODEL` | `PROCESSING` | `false` |
| `COMPOSING` | `PROCESSING` | `false` |
| `SUCCESS` | `SUCCESS` | `true` |
| `PARTIAL_SUCCESS` | `PARTIAL_SUCCESS` | `true` |
| `FAILED` | `FAILED` | `true` |
| `CANCELLED` | `FAILED` | `true` |

`currentStage` 始终返回数据库 `execution.current_stage` 的原内部枚举值。数据库出现
OpenAPI 未声明的 status/currentStage 时必须 fail closed，不得 fallback 到
`PROCESSING` 或回显未知字符串。

### 2.3 snapshot 与 superseded

* `snapshotAvailable = (snapshot_execution_id != null)`。
* terminal 不等于 snapshotAvailable；B 的 parser/file/rule-set 失败可为
  `terminal=true, status=FAILED, snapshotAvailable=false`。
* `superseded = (superseded_by_execution_id != null)`；无 snapshot 时为 `false`。
* `supersededReason` 只在数据库值非 null 时返回，并必须属于 OpenAPI
  `SupersededReason`；否则 fail closed。
* 不返回 `supersededByExecutionId`，因为公开状态响应没有该字段。

### 2.4 响应字段

成功响应字段精确为：

```text
taskId
executionId
status
currentStage
terminal
snapshotAvailable
resultUrl
reviewModel:
  modelProfileCode
  providerType
  modelName
  endpointAlias
  modelConfigVersion
createdAt
updatedAt
superseded
supersededReason?  # null 时省略
```

映射：

* `resultUrl <- task.result_url`。
* `reviewModel.* <- execution.model_profile_code/provider_type/model_name/
  endpoint_alias/model_config_version`。
* `createdAt/updatedAt <- execution.created_at/updated_at`，通过 `Instant`
  输出 ISO-8601 date-time。
* `providerType` 只接受 `LOCAL / PUBLIC_OPENAI_COMPATIBLE / MOCK`。
* 所有 OpenAPI required 字段必须非 null；违反时 fail closed。
* 成功 JSON 不得出现额外字段。

`endpointAlias` 是已冻结的公开部署别名，例如 `mock-local`；禁止把它解析、替换或扩展为
真实 endpoint URL。

### 2.5 404 与输出安全

404 响应使用 OpenAPI `BusinessErrorResponse` 形状：

```json
{
  "code": "REVIEW_EXECUTION_NOT_FOUND",
  "message": "未找到指定审核执行"
}
```

`reason/retryable/operatorActionRequired` 不需要时省略。响应不得说明是 task 不存在、
execution 不存在还是跨 task identity。

成功和 404 响应均不得出现：

```text
SYS-*
diagnosticCode
businessReason
detailPayload
prompt / promptVersion
rawOutput
endpoint / endpointUrl
secret / apiKey
stackTrace / exception
stageLeaseOwner
```

服务端日志不得记录完整响应、model endpoint 配置、数据库异常原文或 stack trace 到业务
响应；测试日志不得包含人为注入的敏感 sentinel。

## 3. 行为步骤

1. Controller 接收 path 中的 `taskId`、`executionId`。
2. Service 调用 repository 执行 §2.1 的精确只读查询。
3. 无行时抛出稳定 not-found 异常，由专用 advice 转为 §2.5 的 404。
4. Service 校验内部枚举和 required 字段。
5. 按 §2.2 生成公开 status 与 terminal。
6. 按 §2.3 生成 snapshot/superseded 信息。
7. 返回 §2.4 的精确 DTO。

## 4. 验收标准

### Must Pass

1. `GET /api/review/tasks/{taskId}/executions/{executionId}` 返回 HTTP 200 和冻结
   `ReviewExecutionStatusResponse`。
2. service 对 13 个内部状态逐一满足 §2.2 映射；任何内部阶段都不直接成为公开 status。
3. `currentStage` 对 13 个允许值逐一原样返回，且与公开 status 相互独立。
4. `terminal` 仅在 `SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELLED` 为 true。
5. task 不存在、execution 不存在和跨 task execution 均返回完全相同的
   `404 REVIEW_EXECUTION_NOT_FOUND` 形状。
6. repository SQL 同时约束 taskId 和 executionId，跨 task 时不返回 row，且不执行
   第二次存在性探测查询。
7. `resultUrl` 精确返回 task 表保存值；含特殊字符或不同前缀时仍不被重写。
8. `snapshotAvailable` 对同 task/execution snapshot 存在为 true，不存在为 false；
   terminal FAILED 且无 snapshot 时仍为 false。
9. snapshot 有 `superseded_by_execution_id + superseded_reason` 时返回
   `superseded=true` 与合法 reason；无 snapshot/无 superseded 时返回 false 且省略 reason。
10. `reviewModel` 五字段逐项匹配 execution；`endpointAlias` 返回 alias，
    不返回真实 endpoint URL 或 secret。
11. `createdAt/updatedAt` 精确映射 PostgreSQL timestamptz，HTTP JSON 为 ISO-8601。
12. 成功响应仅含 §2.4 字段；404 仅含 §2.5 非 null 字段，不出现任何禁止字段。
13. A 创建成功后立即查询得到
    `QUEUED/currentStage=QUEUED/terminal=false/snapshotAvailable=false`。
14. 同一 execution 经 B `runOnce()` 后，C 返回与数据库一致的
    `SUCCESS` 或 `PARTIAL_SUCCESS`、terminal=true、snapshotAvailable=true 和原 resultUrl。
15. B 失败 execution 返回
    `FAILED/terminal=true/snapshotAvailable=false`，不返回失败日志或 SYS 信息。
16. repository 只读测试证明不执行任何写 SQL；实现不持有 worker/persistence 写接口。
17. A/B/C 定向测试、既有 task result query、A/B 回归与全量 backend regression 通过。
18. `node scripts/validate-review-assets.mjs`、`git diff --check` 通过；实现报告与真实
    status/diff 一致。

### Must Not

* 不修改 migration、OpenAPI、依赖、Compose、状态机、worker、A/B 或现有结果查询。
* 不新增状态值、endpoint、缓存、认证、管理台诊断字段或 API 版本别名。
* 不用 terminal 推测 snapshotAvailable，不重新拼接 resultUrl。
* 不按 executionId 单独查询，不泄露跨 task execution 存在性。
* 不返回 prompt/raw output/endpoint URL/secret/stack trace/stage log/SYS-*。
* 不 commit、不 push、不创建 PR。

### Expected 来源

本规格不修改 fixture/expected，也不把系统输出当作独立正确性 ground truth。Expected
来自冻结 OpenAPI、V1 schema、父 TASK 的公开/内部状态分离表、显式 SQL seed 以及 A/B
真实链路状态。集成测试只证明查询映射与链路一致性，不声明正式 MVP E2E 质量达标。

## 5. 测试与验证命令

```powershell
node scripts/validate-review-assets.mjs

Push-Location deploy/compose
docker compose --env-file ../env/.env.example -p cqcp --profile test config
docker compose --env-file ../env/.env.example -p cqcp up -d --wait postgres
docker compose --env-file ../env/.env.example -p cqcp exec -T postgres dropdb --if-exists -U cqcp cqcp_mvp001_c_test
docker compose --env-file ../env/.env.example -p cqcp exec -T postgres createdb -U cqcp cqcp_mvp001_c_test
$env:CQCP_TEST_DB_NAME = "cqcp_mvp001_c_test"
docker compose --env-file ../env/.env.example -p cqcp --profile test run --rm -e CQCP_REVIEW_WORKER_ENABLED=false -e CQCP_DB_URL=jdbc:postgresql://postgres:5432/cqcp_mvp001_c_test api-server-test gradle test --rerun-tasks --tests "*ReviewExecutionStatusControllerTest" --tests "*ReviewExecutionStatusServiceTest" --tests "*ReviewExecutionStatusRepositoryTest" --tests "*ReviewExecutionStatusIntegrationTest"
docker compose --env-file ../env/.env.example -p cqcp --profile test run --rm -e CQCP_REVIEW_WORKER_ENABLED=false -e CQCP_DB_URL=jdbc:postgresql://postgres:5432/cqcp_mvp001_c_test api-server-test gradle test --rerun-tasks
Remove-Item Env:CQCP_TEST_DB_NAME
Pop-Location

git diff --check
git status --short
git diff --stat
```

* PostgreSQL 必须使用 Compose service 并从全新 C 专用数据库运行 V1 -> V2。
* 所有 Docker test 命令必须显式传 `CQCP_REVIEW_WORKER_ENABLED=false`；
  集成测试只显式调用一次 `worker.runOnce()`。
* 不得执行 `down -v`、删除 named volume、启动标准 api-server、浏览器 E2E、
  外部 API、模型 endpoint 或依赖安装。
* host Gradle 只可用于诊断，不构成验收证据。

## 6. Git 工作区

执行编码前计划时必须：

```text
branch = codex/feature-mvp-001-contract-review-loop
HEAD = Codex 派发 prompt 中给出的 C 规格冻结 commit
git status --short = clean
```

编码前规格映射阶段不得修改任何文件。Codex 接受计划并明确
`GO_TO_IMPLEMENT` 后，执行方才可修改 §0.3 allowlist，并只允许向本 TASK_SPEC 的
“实现报告”章节追加真实报告。

禁止：

* `git commit`
* `git push`
* `git switch/checkout`
* `git reset`
* `git clean`
* `git restore`
* `git stash`

## 7. STOP 条件

出现任一项立即停止：

* 需要 migration、OpenAPI、依赖、Compose、认证、缓存或 ADR 变化；
* 需要修改 A/B、状态机、worker、review engine、result composer 或结果查询；
* 无法用现有 task/execution/snapshot 表满足精确查询；
* 需要读取 stage log、diagnostics、prompt/raw output 或 endpoint URL；
* 需要通过 terminal 推测 snapshotAvailable 或重新拼接 resultUrl；
* 专用 PostgreSQL 测试库不可用、基线不 clean 或出现范围外 dirty。

## 8. 编码前执行指令

把以下内容作为 Claude Code 首条任务：

```text
你只执行 CQCP 父任务 TASK-MVP-001 下已冻结的局部规格
tasks/active/TASK_SPEC-MVP-001-C-execution-status-query-api.md。

第一阶段只做编码前规格映射，不修改任何文件：
1. 完整读取 AGENTS.md、CURRENT_CONTEXT.md、父 TASK、当前 TASK_SPEC 及 §0.3 只读上下文。
2. 确认 PowerShell 7、当前分支、Codex prompt 给出的规格冻结 HEAD 与 clean status
   精确满足 §6。
3. 按 TASK_SPEC §0.2 输出 AC1~AC18、SQL identity、13 状态映射、snapshot/superseded
   真源、resultUrl/reviewModel/timestamp 映射、404 同形、安全字段白名单、不修改路径和
   预计测试的逐项计划。
4. 发现歧义或 STOP 条件立即停止。
5. 输出计划后暂停，等待 Codex 明确 GO_TO_IMPLEMENT；不得提前编码。

不得 commit、push、切分支或创建 PR。
```

## 9. 实现报告

### 实际修改文件（9 个新增，1 个修改）

| # | 路径 | 操作 |
|---|------|------|
| 1 | `.../reviewengine/ReviewExecutionStatusModels.java` | 新增 — DTOs、carrier record、ExecutionNotFoundException |
| 2 | `.../reviewengine/ReviewExecutionStatusRepository.java` | 新增 — 只读 SQL JOIN |
| 3 | `.../reviewengine/ReviewExecutionStatusService.java` | 新增 — 13 状态映射、currentStage/providerType/SupersededReason 验证、required 字段校验 |
| 4 | `.../reviewengine/ReviewExecutionStatusController.java` | 新增 — GET endpoint |
| 5 | `.../reviewengine/ReviewExecutionStatusExceptionHandler.java` | 新增 — 404/500 advice |
| 6 | `.../reviewengine/ReviewExecutionStatusControllerTest.java` | 新增 — MockMvc 200/404/timestamp/白名单 |
| 7 | `.../reviewengine/ReviewExecutionStatusServiceTest.java` | 新增 — 13 状态映射、校验 fail-closed、快照/超脱 |
| 8 | `.../reviewengine/ReviewExecutionStatusRepositoryTest.java` | 新增 — 真实 PostgreSQL identity、snapshot、superseded、只读 spy |
| 9 | `.../reviewengine/ReviewExecutionStatusIntegrationTest.java` | 新增 — A→C QUEUED、A→B→C terminal、A→B→C FAILED、404 三路径同形、spy 计数 |
| 10 | `tasks/active/TASK_SPEC-MVP-001-C-...md` | 修改 — 本报告 |

### AC1~AC18 映射

| AC | 测试方法 | 层级 | DB 专用库 |
|----|---------|------|-----------|
| 1 200+DTA | `ac1_returns200WithAllFields` | ControllerTest | 否（mock） |
| 2 13 状态映射 | `ac2_all13StatusesMapped`, `ac2_unknownStatusThrows`, `ac2_nullStatusThrows` | ServiceTest | 否 |
| 3 currentStage | `ac3_currentStageReturnsAsIs`, `ac3_currentStagePassthrough_all13Stages`, `unknownCurrentStageFailsClosed`, `nullCurrentStageFailsClosed` | ServiceTest | 否 |
| 4 terminal 判定 | `ac4_terminalOnlyForTerminalStatuses` | ServiceTest | 否 |
| 5 同形 404 | `ac5_notFound_returns404` (Controller), `ac5_taskNotFound_returns404`, `ac5_executionNotFound_returns404`, `ac5_crossTaskExecution_returns404` (Integration) | ControllerTest + IntegrationTest | IntegrationTest 使用 PostgreSQL |
| — 一次查询 | `ac5_onlyOneQuery_noSecondProbe` | IntegrationTest | PostgreSQL spy |
| 6 SQL identity | `ac6_findStatus_returnsRow`, `ac6_crossTaskExecution_returnsEmpty`, etc | RepositoryTest | PostgreSQL |
| 7 resultUrl | `ac7_resultUrlDirect` (Integration), `resultUrlFromTaskTable` (`resultUrlDirect` Service) | 两者 | PostgreSQL |
| 8 snapshot | `ac8_snapshotWithoutSnapshot_returnsAvailableFalse`, `ac8_snapshotWithSnapshot_returnsAvailableTrue`; Service: `snapshotAvailableTrueWhenSnapshotExists`, `snapshotAvailableFalseWhenNoSnapshot` | RepositoryTest + ServiceTest | PostgreSQL |
| 9 superseded | `ac9_supersededFields` (Repository), `ac9_supersededReasonValidEnum`, `ac9_supersededReasonInvalidThrows`, `ac9_noSuperseded_returnsFalse` (Service) | RepositoryTest + ServiceTest | RepositoryTest PostgreSQL |
| 10 reviewModel | `ac10_reviewModelFieldsMatchExecution`, `unknownProviderTypeFailsClosed`, `nullRequiredFieldFailsClosed` | ServiceTest | 否 |
| 11 ISO-8601 | `ac11_timestampsAreIso8601InJson` (Controller: explicit UTF-8 + exact assertion), `ac11_timestampsPreserved` (Service) | ControllerTest + ServiceTest | 否 |
| 12 字段白名单 | `ac12_success_exactTopLevelAndReviewModelKeys`, `ac12_404_exactCodeAndMessageKeys` (both explicit UTF-8) | ControllerTest | 否 |
| 13 A→C QUEUED | `ac13_afterACreate_returnsQueuedStatus` | IntegrationTest | PostgreSQL + MockMvc + worker disabled |
| 14 A→B→C terminal | `ac14_afterBRunOnce_returnsTerminal` | IntegrationTest | PostgreSQL + real runOnce() |
| 15 A→B→C FAILED | `ac15_failedExecutionViaUnsupportedRuleSet` | IntegrationTest | real runOnce() + unsupported-v999 |
| 16 只读 | `ac16_repositoryOnlySelects` (Repository: `ac16_repositoryOnlySelects`), `ac16_happyPath_repositoryOnlySelects` (Integration) | RepositoryTest + IntegrationTest | PostgreSQL + @SpyBean |
| 17 回归 | 全量 backend 313 tests / 0 failures | Docker Compose cqcp_mvp001_c_test |
| 18 validator | `node scripts/validate-review-assets.mjs` | host | — |

### 关键行为

1. **查询 identity**: `FROM task t JOIN execution e ON e.task_id=t.task_id LEFT JOIN review_result_snapshot s ON ... WHERE t.task_id=? AND e.execution_id=?`。0 row → 同形 404。
2. **状态映射**: `mapPublicStatus(internalStatus)` switch 13 值 → 4 公开；未知值 fail closed。
3. **currentStage 独立校验**: Service 先 check `VALID_CURRENT_STAGES`（13 枚举），合法原样返回。
4. **providerType 校验**: `VALID_PROVIDER_TYPES` = `{LOCAL, PUBLIC_OPENAI_COMPATIBLE, MOCK}`。
5. **required 字段**: 先在 carrier 层逐一 `validateRequired()`，再构造 DTO。
6. **snapshotAvailable**: `s.execution_id != null`，不从 terminal 推测。
7. **resultUrl**: `t.result_url` 不拼接。
8. **404 三路径**: task 不存在、execution 不存在、跨 task execution 均返回 `{"code":"REVIEW_EXECUTION_NOT_FOUND","message":"未找到指定审核执行"}`。
9. **只读证据**: `@SpyBean JdbcTemplate` + `clearInvocations` → verify 一次 query, never update/batchUpdate/execute。
10. **安全**: SQL 仅查 task/execution/snapshot，不读 stage_log/diagnostics/SYS-*/prompt/endpoint。错误 handler 不泄露 stack trace。

### 验证结果（cqcp_mvp001_c_test, CQCP_REVIEW_WORKER_ENABLED=false, CQCP_DB_URL=jdbc:postgresql://postgres:5432/cqcp_mvp001_c_test）

```text
本轮验证日期: 2026-07-27
Docker PostgreSQL 证据:
  - postgres: localhost:54329 (Docker Compose service, healthy)
  - database: cqcp_mvp001_c_test (全新创建, V1→V2 migration 自动运行)
  - env: CQCP_REVIEW_WORKER_ENABLED=false, CQCP_DB_URL=jdbc:postgresql://postgres:5432/cqcp_mvp001_c_test

C 定向测试（Docker Compose, cqcp_mvp001_c_test）:
  ReviewExecutionStatusControllerTest   → 6 tests / 0 failed / 0 skipped
  ReviewExecutionStatusServiceTest      → 19 tests / 0 failed / 0 skipped
  ReviewExecutionStatusRepositoryTest   → 9 tests / 0 failed / 0 skipped
  ReviewExecutionStatusIntegrationTest  → 9 tests / 0 failed / 0 skipped
  C suite total                         → 43 tests / 0 failed / 0 skipped / 0 errors

全量 backend（Docker Compose, cqcp_mvp001_c_test, CQCP_REVIEW_WORKER_ENABLED=false）:
  gradle test --rerun-tasks              → BUILD SUCCESSFUL
  Total: 313 tests / 0 failures / 0 errors / 0 skipped (30 suites)

其他验证:
  node scripts/validate-review-assets.mjs   → 7/7 passed
  git diff --check                           → 0 issues
  git diff --stat HEAD                       → 1 file changed (101 insertions, 2 deletions)
  git status --short                         → 1 modified + 9 untracked, 全部 §0.3 allowlist
  untracked: 9 files (5 production + 4 test), 全部 §0.3 allowlist 内新增文件
  无 data/ 残留（使用仓库外临时 upload path）

### 残余风险

1. **ControllerTest 中文消息 UTF-8 解码**：使用 `getContentAsString(StandardCharsets.UTF_8)` 显式 UTF-8 解码，404 message 精确断言 `"未找到指定审核执行"`, 500 message 精确断言 `"审核执行状态查询失败"`。此方式不依赖 JDBC driver 或 Gradle image 默认编码，生产环境不受影响。未修改生产 Controller/ExceptionHandler 响应文案。
2. **RepositoryTest 使用直接 SQL 插入 snapshot**：`saveSnapshot` 生命周期不携带 superseded 字段，因此 superseded 测试用直接 SQL 验证 LEFT JOIN 行为。
3. **未修改 migration/OpenAPI/A/B/状态机/worker/前端/Compose/application.yml**：C 仅新增 9 个文件，不修改任何现有代码。

### Git 工作区

```
?? apps/api-server/src/main/java/.../ReviewExecutionStatusController.java
?? apps/api-server/src/main/java/.../ReviewExecutionStatusExceptionHandler.java
?? apps/api-server/src/main/java/.../ReviewExecutionStatusModels.java
?? apps/api-server/src/main/java/.../ReviewExecutionStatusRepository.java
?? apps/api-server/src/main/java/.../ReviewExecutionStatusService.java
?? apps/api-server/src/test/java/.../ReviewExecutionStatusControllerTest.java
?? apps/api-server/src/test/java/.../ReviewExecutionStatusIntegrationTest.java
?? apps/api-server/src/test/java/.../ReviewExecutionStatusRepositoryTest.java
?? apps/api-server/src/test/java/.../ReviewExecutionStatusServiceTest.java
```

9 个文件，全部在 §0.3 allowlist 内。未修改 A/B/状态机/worker/migration/OpenAPI/Compose/前端/ADR。

## 10. Codex Review Intake

规格依据：

* OpenAPI 已冻结 endpoint、response、公开状态、内部阶段、模型摘要与 404 schema；
* V1 schema 已具备 task result URL、execution 状态/模型/时间字段和 snapshot identity；
* A 已提供 Task + QUEUED Execution，B 已提供 worker 与 terminal persistence；
* 本规格不改变架构、核心审核语义、SYS/Finding、EvidenceSlot、
  ReviewPointFamily 或 CandidateResolver，因此不需要新 ADR。

Codex 规格决策：

```text
ACCEPT_SPEC / PRE_CODE_PLAN_REQUIRED / NO_IMPLEMENTATION_YET
```

执行方计划至少必须证明：

* task/execution identity 在同一 SQL 内精确约束，跨 task 404 不泄露；
* 13 个内部状态全部有显式公开映射；
* snapshotAvailable 与 superseded 来自真实 LEFT JOIN，不从 status 推测；
* resultUrl 和 reviewModel 字段均来自持久化真源；
* success/404 响应字段白名单和敏感字段禁入可由测试证伪；
* 集成测试真实覆盖 A -> C QUEUED -> B -> C terminal。

Codex 实现审查决定（2026-07-27）：

```text
ACCEPT_IMPLEMENTATION / READY_TO_COMMIT / NO_PUSH
```

审查证据：

* C 定向测试：43 tests / 0 failures / 0 errors / 0 skipped；
* backend 全量：313 tests / 0 failures / 0 errors / 0 skipped；
* 测试 XML `hostname` 为 Docker container ID，确认不是 host Gradle 结果；
* `node scripts/validate-review-assets.mjs`：7/7 passed；
* `git diff --check`：通过；
* 工作区仅包含本规格 allowlist 内 9 个新增 Java 文件和本 TASK_SPEC 报告修改，
  无 `data/` 残留；
* 未执行 commit、push、merge。

Git 收口（2026-07-27）：

```text
commit: 0eb9d9e312c6698fb4532e623362bea091165f87
message: feat(review): add execution status query API
push: 未执行
```

## 11. 后续联动

C 经 Codex Review Intake 接纳后解锁 `TASK_SPEC-MVP-001-D`（新建审核页面）。
C 不修改前端，不实现状态页轮询或结果跳转；这些由 D/E 分别承接。
