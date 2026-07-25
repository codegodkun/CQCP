# TASK_SPEC-MVP-001-B：Single Review Worker 与 PostgreSQL 执行持久化

状态：SPEC_ACCEPTED / INDEPENDENT_SPEC_AUDIT_GO / PRE_CODE_PLAN_REQUIRED

TASK_SPEC 类型：`execution`

父 TASK：`TASK-MVP-001`

父 TASK Level：`L2 Feature`（核心审核链路 / PostgreSQL / 异步执行风险触发）

Integration unit：`FEATURE-MVP-001`

执行方：Claude Code / DeepSeek

所在分支：`codex/feature-mvp-001-contract-review-loop`

基线 commit：`f307d00`（含 A 实现提交 `f8d76e7` 与 PostgreSQL 架构措辞纠正）

## 0. 任务摘要

实现一期单进程 `Single Review Worker`：从 PostgreSQL 原子 claim 一个 `QUEUED`
execution，装载 A 已持久化的 Task、Execution、结构化字段和 DOCX 安全路径，调用既有
`TaskExecutionStateMachine` 完成 parser -> index -> plan -> evidence -> rules ->
compose，并把 execution、stage log 与 `ReviewResultSnapshot` 写回 PostgreSQL。

本规格不新增 endpoint，不修改审核规则、parser、CandidateResolver、EvidenceSlot、
SYS/Finding 边界或 TASK-036-C2，不调用真实模型 endpoint，不新增 migration、依赖、MQ
或多 worker。

### 0.1 角色与门禁

* Codex 冻结规格、审查编码前规格映射计划、实现报告和 diff；不得编写本规格业务代码后自行宣布通过。
* Claude Code / DeepSeek 修改任何文件前，必须先提交 §0.2 计划并暂停。
* Codex 明确 `GO_TO_IMPLEMENT` 前不得编码。
* 执行方不得 commit、push、切分支、创建 PR、修改父 TASK 或扩大文件范围。
* 独立 agent 只做只读规格/实现审计。
* 本 TASK_SPEC 只作为 Feature 内局部执行与 Review Intake 单位，不单独创建 PR。

### 0.2 编码前规格映射计划

必须逐项输出并等待 Codex 审查：

```text
AC1~AC20 映射：
- 每条验收断言的真实 PostgreSQL 输入、代码路径、状态/日志/快照效果和测试。

claim / lease：
- 单 SQL 原子 claim 的 WHERE、ORDER BY、SKIP LOCKED、owner、expiry 与返回值。
- 同一 execution 不重复 claim；lease 丢失时如何 fail closed。

装载映射：
- task + execution + contract_metadata.documentReference -> TaskExecutionRequest。
- structured_fields_snapshot JSON scalar -> Map<String,String>。
- execution 14 字段 -> TaskExecutionRecord / VersionReferences。
- model_config_version -> snapshot.model_profile_version 的 ADR-017 兼容映射。

审核点 snapshot：
- legacy v20260705.1 的 9 个 code/name/family/displayCode/severity/order 的固定来源。
- 为什么不读取 DRAFT / NOT_BOUND review-assets runtime 文件。

原子性：
- stage start 的 execution + STARTED log 事务边界。
- terminal success 的 snapshot + COMPLETED log + terminal execution 事务边界。
- failure 的 FAILED log + FAILED execution 事务边界。
- snapshot insert 或 terminal update 失败时为什么不会留下成功快照。
- `TaskExecutionStateMachine` 当前全部内层/外层异常路径如何统一改用
  `failExecution()`；不得遗漏仍直接调用 failed log + saveExecution 的 catch。

文件安全：
- documentReference 如何先绑定 loaded taskId，再经 LocalReviewDocumentStore 得到
  real, regular, all-components-non-symlink DOCX。
- missing/symlink/escape 如何 fail closed，且 stage log 不保存绝对路径或异常原文。
- 读取时必须独立重做 task ownership、NOFOLLOW 和 real-path 校验，不得以 A 已安全写入
  作为省略 B 读校验的理由。

异步边界：
- @Scheduled 单 worker、默认 poll delay、测试禁用方式。
- 不调用模型 endpoint、不新增 REVIEWING_MODEL 假阶段。

明确不修改：
- V1/V2 migration、OpenAPI、Compose、TASK-036/C2、review-assets、parser、
  review engine、fixture/expected、结果查询 endpoint、前端。
- `LegacyReviewPointSnapshotCatalog` 不得 import、解析或以资源路径依赖
  `packages/review-assets`。

预计测试：
- claim/lease repository、JDBC persistence atomicity、worker success/partial/failure、
  A POST -> B worker -> PostgreSQL snapshot -> existing result query integration、
  existing state machine/parser/review/result regressions。
```

### 0.3 文件访问范围

```text
✅ 允许修改：
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/SingleReviewWorker.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/SingleReviewWorkerRepository.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/JdbcTaskExecutionPersistence.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/LegacyReviewPointSnapshotCatalog.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/SingleReviewWorkerConfiguration.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/LocalReviewDocumentStore.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java
  apps/api-server/src/main/resources/application.yml
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/SingleReviewWorkerRepositoryTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/JdbcTaskExecutionPersistenceTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/SingleReviewWorkerIntegrationTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachineTest.java
  tasks/active/TASK_SPEC-MVP-001-B-single-review-worker-persistence.md

👀 允许只读：
  AGENTS.md
  CURRENT_CONTEXT.md
  PRD.md
  tasks/active/TASK-MVP-001-contract-review-user-loop-demo.md
  tasks/active/TASK_SPEC-MVP-001-A-task-creation-upload-persistence.md
  decisions/ADR-005-first-review-points-selection.md
  decisions/ADR-017-execution-binding-release-and-demo-profile-readiness.md
  docs/ARCHITECTURE.md
  docs/backend.md
  docs/database.md
  docs/deployment.md
  docs/review-point-definitions.md
  packages/api-contracts/openapi.yaml
  apps/api-server/build.gradle.kts
  apps/api-server/src/main/resources/db/migration/V1__cqcp_mvp_core_schema.sql
  apps/api-server/src/main/resources/db/migration/V2__execution_binding_release.sql
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreation*.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ExecutionBinding*.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/JdbcExecutionBindingRepository.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ResultComposer.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/PersistentTaskResultStore.java
  apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/Task034MvpE2EAcceptanceHarnessTest.java

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
POST /api/review/tasks（A，已完成）
  -> PostgreSQL Task + QUEUED Execution + persisted DOCX
  ->【Single Review Worker / JDBC TaskExecutionPersistence】← 本规格
  -> TaskExecutionStateMachine
  -> parser -> index -> plan -> evidence -> rules -> compose
  -> PostgreSQL stage logs + terminal Execution + ReviewResultSnapshot
  -> status API（C，后续）
```

红线：

1. 一期只能是单 worker、顺序处理；不得引入 RabbitMQ、Kafka、分布式 worker 或线程池并发执行。
2. 必须从 PostgreSQL claim `QUEUED` execution；不得使用内存队列、启动时全表加载或 HTTP 自调用。
3. 必须复用既有 `TaskExecutionStateMachine`、`ParserBackedReviewInputPreparer`、
   `MinimalReviewEngine` 与 `ResultComposer`；不得复制或重写审核逻辑。
4. 不激活 TASK-036-C2，不传入 `v20260715.1`，不读取 DRAFT / NOT_BOUND review-assets 作为 runtime loader。
5. 只消费 execution 已冻结的 legacy `v20260705.1` binding；worker 不重新 resolve default，
   不回查当前 binding，也不改写 execution 的 14 个版本/model 字段。
6. `MVP_DEMO_MOCK` 只表示 Demo binding；本规格不调用任何模型 endpoint，也不伪造模型结果。
7. parser 失败必须进入 `FAILED`，不生成 snapshot 或业务 Finding。
8. `SYS-*` 只存在于 snapshot diagnostics，不进入 findings；本规格不得更改该边界。
9. 文件路径只能来自 A 保存的 `contract_metadata.documentReference`，必须精确绑定当前
   loaded `taskId` 并再次通过受控 root 验证；不得信任 originalFileName 或把绝对路径
   写入 DB/log/response。
10. 不新增 migration、依赖、API、状态枚举、审核点、规则或 version source。

## 2. 冻结实现契约

### 2.1 Worker 配置

`application.yml` 只允许新增：

```yaml
cqcp:
  review:
    worker:
      enabled: ${CQCP_REVIEW_WORKER_ENABLED:true}
      poll-delay-ms: ${CQCP_REVIEW_WORKER_POLL_DELAY_MS:1000}
      lease-duration-seconds: ${CQCP_REVIEW_WORKER_LEASE_DURATION_SECONDS:3600}
```

* `enabled=false` 用于测试隔离。
* worker bean 与 `runOnce()` 在 `enabled=false` 时仍存在；定时入口检查该开关并 no-op，
  集成测试只允许显式调用 `runOnce()`，不得用条件装配移除真实 worker 后另造测试入口。
* 一次 poll 最多 claim 并执行一个 execution。
* 同一 scheduler invocation 未结束前不得重入。
* worker owner 为进程内随机稳定标识；不得从请求或数据库业务字段推导。
* 不新增 Compose env；默认值即可用于 Demo。

### 2.2 原子 claim

必须使用单条 PostgreSQL statement，语义等价于：

```sql
WITH candidate AS (
  SELECT execution_id
  FROM execution
  WHERE status = 'QUEUED'
    AND (
      stage_lease_owner IS NULL
      OR stage_lease_expires_at IS NULL
      OR stage_lease_expires_at < NOW()
    )
  ORDER BY created_at ASC, execution_id ASC
  FOR UPDATE SKIP LOCKED
  LIMIT 1
)
UPDATE execution e
SET stage_lease_owner = :owner,
    stage_lease_acquired_at = NOW(),
    stage_lease_expires_at = NOW() + :leaseDuration,
    heartbeat_at = NOW(),
    updated_at = NOW()
FROM candidate
WHERE e.execution_id = candidate.execution_id
  AND e.status = 'QUEUED'
RETURNING e.execution_id
```

要求：

* 无候选返回 empty，不报错、不写 stage log。
* 已有未过期 lease 的 row 不可被 claim。
* 过期 lease 的 `QUEUED` row 可被重新 claim。
* claim 不提前把 status 改为 `PARSING`；状态迁移由 state machine 负责。
* 后续所有 execution 更新必须带 `execution_id + stage_lease_owner` 条件；更新 0 row
  视为 lease lost，立即 fail closed。
* stage log 与 snapshot INSERT 也必须以同一 `execution_id + stage_lease_owner`
  及允许的 non-terminal status 在 SQL 内校验 owner/status，并校验 exactly 1 inserted row；
  不得先无锁 SELECT owner/status 再写入。
* B 只重领仍为 `QUEUED` 的过期 lease。进程在已经进入 `PARSING` 及后续 non-terminal
  stage 后崩溃的自动恢复、续跑或 reset 不在本 Demo 规格内；不得把这类 row 静默改回
  `QUEUED`。该限制作为已知残余风险进入实现报告。

### 2.3 TaskExecutionRequest 装载

按 claim 返回的 `executionId` 一次装载：

```text
task:
  task_id
  contract_name
  structured_fields_snapshot::text
  contract_metadata ->> documentReference

execution:
  execution_id, task_id, status, current_stage
  14 version/model fields
  started_at, finished_at
```

映射：

* `structured_fields_snapshot` 必须是 JSON object。
* object 中 string/number/boolean scalar 以不改变数值文本语义的字符串写入
  `ReviewTaskRecord.structuredFieldsSnapshot`；null/object/array 触发 fail closed。
* `TaskExecutionRecord.versionReferences` 精确使用：
  `contract_type_profile_version`、`rule_set_version`、
  `review_budget_profile_version`、`model_config_version`（映射到
  `modelProfileVersion`）、`parser_version`、`prompt_version`、
  `schema_version`、`pattern_library_version`、`field_lexicon_version`、
  `evidence_selector_version`。
* model 元数据精确使用 execution 的 `model_profile_code/provider_type/model_name/endpoint_alias`。
* `documentReference` 缺失、不是 string 或空白时触发 FAILED。
* `sampleId` 使用 `taskId`，仅作为既有 parser-backed input 的稳定内部标识；
  不把 task 当人工 ground truth 或正式样本 ID。

### 2.4 DOCX 安全读取

`LocalReviewDocumentStore` 只允许新增 package-private 读解析能力，输入为 loaded
`taskId + documentReference`：

* `documentReference` 必须精确匹配
  `^{loadedTaskId}/[0-9a-f]{32}\.docx$`，并使用 forward slash；
  任一其他 task 前缀、额外层级、反斜线、absolute 或 `..` 均拒绝。
* 从 upload root 到 task directory、final file 的所有既有路径组件都必须使用
  `NOFOLLOW_LINKS` 拒绝 symbolic link；不得只检查 final file。
* 解析后的 final path 必须是 regular file。
* final real path 必须仍位于 upload root real path 内。
* 返回给 `TaskExecutionDocumentReference` 的只能是验证后的 real path。
* missing/unreadable/symlink/escape 均 fail closed。
* 不删除 A 保存的 final DOCX；B 只读。

### 2.5 legacy 审核点 snapshot

只支持 `execution.rule_set_version == "v20260705.1"`。其他值触发 FAILED，不 fallback。

启用点固定为 9 个，disabled 列表固定为空：

| order | displayCode / reviewPointCode | name | family | severity |
|---:|---|---|---|---|
| 1 | `PARTY_A_NAME_CONSISTENCY` | 甲方名称一致性 | `PARTY_FIELDS` | `ERROR` |
| 2 | `PARTY_B_NAME_CONSISTENCY` | 乙方名称一致性 | `PARTY_FIELDS` | `ERROR` |
| 3 | `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` | 合同总金额一致性 | `AMOUNT_TAX` | `ERROR` |
| 4 | `TAX_AMOUNT_FORMULA_CONSISTENCY` | 税额公式一致性 | `AMOUNT_TAX` | `WARNING` |
| 5 | `PREPAYMENT_RATIO_CONSISTENCY` | 预付款比例一致性 | `PAYMENT_TERMS` | `ERROR` |
| 6 | `PROGRESS_PAYMENT_RATIO_CONSISTENCY` | 进度款比例一致性 | `PAYMENT_TERMS` | `ERROR` |
| 7 | `COMPLETION_PAYMENT_RATIO_CONSISTENCY` | 竣工款比例一致性 | `PAYMENT_TERMS` | `ERROR` |
| 8 | `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY` | 结算款比例一致性 | `PAYMENT_TERMS` | `ERROR` |
| 9 | `WARRANTY_RETENTION_RATIO_CONSISTENCY` | 质保款比例一致性 | `PAYMENT_TERMS` | `ERROR` |

`contractType` 固定为 `ENGINEERING_PROCUREMENT`。这些值来自 ADR-005、
`docs/review-point-definitions.md` 与当前 code-current severity；不得在运行时解析
`packages/review-assets`，因为其状态仍为 `DRAFT / NOT_BOUND`。

`severity` 只冻结 legacy code-current snapshot metadata：当前
`MinimalReviewEngine` 对税额弱校验输出 `WARNING`，其他明确不一致输出 `ERROR`。
这不是启用可编辑 severityPolicy、不是从当前配置回查历史 execution，也不声明
Production Ready；如实现需要可编辑 severity 真源，立即 STOP 并拆后续任务。

### 2.6 PostgreSQL persistence

`JdbcTaskExecutionPersistence` 实现既有 `TaskExecutionPersistence`：

* `saveExecution` 更新 status/current_stage/started_at/finished_at/updated_at。
* non-terminal 更新刷新 heartbeat 与 lease expiry。
* terminal 更新清空四个 lease 字段。
* 每次 execution update 必须校验 exactly 1 row；否则抛出稳定内部异常。
* `appendStageLog` 写入 V1 全部字段，`detail_payload` 由 ObjectMapper 写 JSON。
* `appendStageLog` 必须使用 owner/status-guarded `INSERT ... SELECT` 或等价单 SQL；
  owner/status 不匹配时写入 0 row 并抛出 lease-lost，不能留下旧 worker 日志。
* FAILED stage log 的 `business_reason` 只能保存固定业务化摘要
  `执行阶段失败`，不得保存 `exception.getMessage()`、绝对路径、原始文件名或 stack trace。
* `saveSnapshot` 写入 V1 `review_result_snapshot` 全部字段；
  JSON 只能由现有 DTO/record 通过 ObjectMapper 序列化，不得手拼 JSON。
* `saveSnapshot` 必须在同一 INSERT 内校验 lease owner 与 composing non-terminal status；
  owner/status 不匹配时不得写快照。
* snapshot insert 必须使用 plain INSERT；同 execution 重复 snapshot fail closed，不覆盖历史。

### 2.7 状态/日志/快照原子性

允许对 `TaskExecutionPersistence` 增加 default lifecycle 方法，并让 JDBC 实现覆写为
`@Transactional`：

```text
startStage(execution, startedLog)
failExecution(failedExecution, failedLog)
completeExecution(completedExecution, snapshot, completedLog)
```

冻结语义：

* `startStage`：execution 状态迁移与对应 STARTED log 同事务。
* `failExecution`：FAILED log 与 FAILED execution 同事务。
* `completeExecution`：snapshot INSERT、COMPOSING COMPLETED log、terminal execution
  同一事务；任一失败全部回滚。
* InMemory/test 实现可消费 default 方法，保持原有行为。
* `TaskExecutionStateMachine` 只允许为调用上述 lifecycle 方法做最小重排；
  阶段顺序、状态判定、parser/review/composer 调用和 SYS/Finding 语义不得改变。

阶段顺序：

```text
PARSING
INDEXING
PLANNING
BUILDING_EVIDENCE
REVIEWING_RULES
COMPOSING
SUCCESS | PARTIAL_SUCCESS
```

不依赖模型的 code-current Demo 不创建虚假的 `REVIEWING_MODEL` stage。

### 2.8 Worker 外层失败

若 request 装载、路径解析、legacy catalog 或 state machine 外层发生异常：

* 使用 lease owner 条件读取当前 status/current_stage。
* 仅当仍非 terminal 时，以单事务写一条固定摘要 FAILED log 并更新 execution 为 FAILED。
* 已由 state machine 写成 FAILED 时不得重复写失败 log。
* 不生成 snapshot。
* server log 可记录 `taskId/executionId` 与受控异常类型；不得把 document root、
  originalFileName、metadata JSON 或合同内容写入日志。
* 单个 execution 失败不得停止后续 poll。

## 3. 行为步骤

1. scheduler 在 `enabled=true` 时按 fixed delay 调用 `runOnce()`。
2. 使用 owner + lease duration 原子 claim 最早的可领取 `QUEUED` execution。
3. 无候选立即返回。
4. 装载 Task/Execution，校验 task/execution identity 与 legacy rule set。
5. 通过 LocalReviewDocumentStore 得到验证后的 DOCX real path。
6. 构造 9 个 enabled `ReviewPointSnapshot` 和空 disabled snapshot。
7. 构造 `TaskExecutionRequest.forDocument(...)`。
8. 使用 JDBC persistence 调用既有 state machine。
9. state machine 依次写 stage 状态/日志，最终原子写 snapshot + terminal execution。
10. 发生异常时执行 §2.8 fail closed；本次 `runOnce()` 返回，下一 poll 可继续处理下一 row。

## 4. 验收标准

### Must Pass

1. 两个 `QUEUED` execution 按 `created_at, execution_id` FIFO，每次 `runOnce` 只执行一个。
2. claim 使用真实 PostgreSQL `FOR UPDATE SKIP LOCKED`；并发 claim 不返回同一 execution。
3. 未过期 lease 不可重复 claim；过期 `QUEUED` lease 可被重新 claim；已进入
   `PARSING` 或后续 stage 的过期 lease 不得被静默重置或重复执行。
4. claim 后 row 仍为 `QUEUED`；state machine 开始时才进入 `PARSING`。
5. A 的合法 MONTHLY `POST /api/review/tasks` 创建后，调用 `runOnce` 产生同 task/execution
   的 terminal execution、完整 stage logs 与恰好一个 snapshot。
6. 合法 MILESTONE 路径完成，四个月度专属点为 `SKIPPED`，不产生对应 Finding。
7. 成功/部分成功 stage 顺序精确为 §2.7，STARTED/COMPLETED 成对；
   terminal status 与 snapshot status 一致。
8. snapshot 包含 9 个 enabled review point snapshots、空 disabled 列表与真实 9 个点级结果。
9. snapshot structured fields 精确来自 Task 创建时 snapshot；number/string 百分比语义不漂移。
10. snapshot 10 个版本字段逐项匹配 execution；`model_profile_version ==
    execution.model_config_version`，rule set 为 legacy `v20260705.1`。
11. worker 不调用 `ExecutionBindingCatalog.resolveDefault`，不重新选择版本/model。
12. `PersistentTaskResultStore.findLatestSnapshot(taskId)` 可读回本次真实 PostgreSQL snapshot，
    identity/status/9 point results/version references 一致。
13. parser 失败、DOCX missing、cross-task in-root reference、task parent directory
    symlink、final symlink、escape documentReference 或不支持 rule set 时，
    execution 进入 FAILED、无 snapshot、无业务 Finding。
14. 失败 stage log 只含固定 `执行阶段失败`，DB 与应用日志均不出现 upload root、
    originalFileName、metadata JSON 或合同内容。
15. snapshot INSERT、COMPOSING COMPLETED log 或 terminal execution 任一注入失败时，
    `completeExecution` 事务整体回滚，不留下成功 snapshot；随后 execution 最终为 FAILED。
16. FAILED log insert 注入失败时，FAILED execution update 同事务回滚，不形成半个失败记录；
    测试明确区分注入失败与正常失败。
17. execution update、stage log INSERT 或 snapshot INSERT 的 lease owner 不匹配时均为
    0 row 并 fail closed；旧 worker 不得继续写 execution、日志或 snapshot。
18. `CQCP_REVIEW_WORKER_ENABLED=false` 时 scheduled entry 不 claim；真实 worker bean 保留，
    测试可手动调用 `runOnce`。全部定向/全量 Docker test 命令必须显式传该环境变量，
    不得依赖 application default。
19. 现有 TaskExecutionStateMachine、parser-backed evidence、MinimalReviewEngine、
    ResultComposer、PersistentTaskResultStore、A 四组测试与全量 backend regression 通过。
20. `node scripts/validate-review-assets.mjs`、`git diff --check` 通过；实现报告与真实
    status/diff 一致。

### Must Not

* 不新增 migration、dependency、endpoint、状态枚举、审核点或模型调用。
* 不修改 OpenAPI、Compose、review-assets、fixture/expected、TASK-036/C2、ADR 或架构。
* 不把 `MVP_DEMO_MOCK` 当真实模型结果，不伪造 REVIEWING_MODEL。
* 不用内存队列、HTTP 自调用、异步线程池、MQ 或多 worker。
* 不把 exception message、absolute path、original filename 或合同内容保存到 stage log。
* 不 commit、不 push、不创建 PR。

### Expected 来源

本规格不修改 fixture/expected，不使用 TASK-034 系统输出作为独立正确答案。测试 expected
来自 V1 schema、ADR-005、ADR-017、冻结阶段顺序、显式 HTTP 输入与独立 SQL 查询；snapshot
内容只声明链路一致性与持久化正确性，不声明正式 MVP E2E 质量达标。

## 5. 测试与验证命令

```powershell
node scripts/validate-review-assets.mjs

Push-Location deploy/compose
docker compose --env-file ../env/.env.example -p cqcp --profile test config
docker compose --env-file ../env/.env.example -p cqcp up -d --wait postgres
docker compose --env-file ../env/.env.example -p cqcp exec -T postgres dropdb --if-exists -U cqcp cqcp_mvp001_b_test
docker compose --env-file ../env/.env.example -p cqcp exec -T postgres createdb -U cqcp cqcp_mvp001_b_test
$env:CQCP_TEST_DB_NAME = "cqcp_mvp001_b_test"
docker compose --env-file ../env/.env.example -p cqcp --profile test run --rm -e CQCP_REVIEW_WORKER_ENABLED=false api-server-test gradle test --rerun-tasks --tests "*SingleReviewWorkerRepositoryTest" --tests "*JdbcTaskExecutionPersistenceTest" --tests "*SingleReviewWorkerIntegrationTest" --tests "*TaskExecutionStateMachineTest"
docker compose --env-file ../env/.env.example -p cqcp --profile test run --rm -e CQCP_REVIEW_WORKER_ENABLED=false api-server-test gradle test --rerun-tasks
Remove-Item Env:CQCP_TEST_DB_NAME
Pop-Location

git diff --check
git status --short
git diff --stat
```

* PostgreSQL 必须使用 Compose service 并从全新 B 专用数据库运行 V1 -> V2。
* 允许启动既有 `cqcp` postgres 与 `api-server-test` profile。
* 不得执行 `down -v`、删除 named volume、构建/启动标准 api-server、浏览器 E2E、
  外部 API、模型 endpoint 或依赖安装。
* host Gradle 只可用于诊断，不构成验收证据。

## 6. Git 工作区

执行前必须：

```text
branch = codex/feature-mvp-001-contract-review-loop
HEAD = Codex 派发 prompt 中给出的 B 规格冻结 commit
git status --short = clean
```

编码前规格映射阶段不得修改任何文件。Codex 接受计划并明确 `GO_TO_IMPLEMENT` 后，
执行方才可修改 §0.3 allowlist，并只允许向 TASKSPEC §9 追加实现报告。

任何 dirty、HEAD 漂移、未知文件或规格冻结 commit 不匹配立即 STOP。

执行中禁止：

* `git commit`
* `git push`
* `git switch/checkout`
* `git reset`
* `git clean`
* `git restore`
* `git stash`

## 7. STOP 条件

出现任一项立即停止：

* 需要 migration、OpenAPI、依赖、Compose、架构或 ADR 变化；
* 需要修改 parser/review engine/result composer/CandidateResolver/EvidenceSlot；
* 无法用单 SQL claim 或无法用现有 lease 字段保证 owner 条件；
* 无法在现有 V1 表内原子完成 success/failure 持久化；
* 需要 runtime 加载 review-assets 或激活 TASK-036-C2；
* 需要调用模型 endpoint；
* 需要把原始异常/路径/合同内容写入 stage log；
* 专用 PostgreSQL 测试库不可用或出现范围外 dirty。

## 8. 编码前执行指令

把以下内容作为 Claude Code 首条任务：

```text
你只执行 CQCP 父任务 TASK-MVP-001 下已冻结的局部规格
tasks/active/TASK_SPEC-MVP-001-B-single-review-worker-persistence.md。

第一阶段只做编码前规格映射，不修改任何文件：
1. 完整读取 AGENTS.md、CURRENT_CONTEXT.md、父 TASK、当前 TASK_SPEC 及 §0.3 只读上下文。
2. 确认 PowerShell 7、当前分支、Codex prompt 给出的规格冻结 HEAD 与 clean status
   精确满足 §6。
3. 按 TASK_SPEC §0.2 输出 AC1~AC20、claim/lease、装载字段、legacy 审核点 snapshot、
   success/failure 原子性、文件安全、异步边界、不修改路径和预计测试的逐项映射。
4. 发现歧义或 STOP 条件立即停止。
5. 输出计划后暂停，等待 Codex 明确 GO_TO_IMPLEMENT；不得提前编码。

不得 commit、push、切分支或创建 PR。
```

## 9. 实现报告

尚未进入编码。执行方获准实现后，只能在本节追加：

* 编码前计划与 Codex decision；
* 实际修改文件；
* claim/lease、装载、persistence、worker 行为；
* 逐项测试原始摘要；
* STOP、假设和遗留；
* `git status --short` 与 `git diff --stat`。

## 10. Codex Review Intake

独立只读规格审计结论：`GO`。审计基线为
`codex/feature-mvp-001-contract-review-loop@f307d00` 与三项已声明 Codex 文档 dirty；
审计确认无未知 dirty、无 HEAD 漂移、无 B 实现文件，并明确未修改任何文件、未执行
任何 Git 写操作。

审计确认：

* PostgreSQL 单 SQL claim、owner/status-guarded execution/log/snapshot 写入在 V1 schema
  内可实现且可证伪；
* success/failure lifecycle 原子性、14 字段及
  `model_config_version -> model_profile_version` 映射完整；
* legacy `v20260705.1` 九点 snapshot 不加载 DRAFT review-assets、不激活 TASK-036-C2；
* documentReference 已绑定 loaded taskId，并覆盖 cross-task、全路径 symlink 与 root
  escape；
* Docker 定向/全量测试显式禁用 scheduler，真实 worker bean 与 `runOnce()` 仍保留；
* AC1~AC20、allowlist、测试命令与 Git dirty 清单一致。

Codex Review Intake：`ACCEPT_SPEC / PRE_CODE_PLAN_REQUIRED / NO_IMPLEMENTATION_YET`。

编码前计划必须额外明确：

1. `TaskExecutionStateMachine` 全部内层/外层异常路径都改用 `failExecution()`；
2. stage log / snapshot 使用 owner + 允许 status 双条件的单 SQL guard；
3. DOCX 读取独立重做 ownership/symlink/real-path 校验；
4. legacy catalog 对 `packages/review-assets` 无编译期或运行期依赖。

非阻塞残余风险：

* V1 未在 stage-log/snapshot 表上存 lease owner，owner guard 依赖 JDBC SQL 与 AC17；
* worker 在 `PARSING` 及后续 non-terminal stage 崩溃后不会自动续跑或 reset；
* lifecycle 原子性依赖 JDBC override 的 Spring transaction proxy 正确生效，禁止
  `noRollbackFor`。

规格审计至少核对：

* 是否绕过 TASK-036-C2 或 DRAFT review-assets runtime 边界；
* claim/lease 是否可证伪且不重复执行；
* success snapshot/log/execution 与 failure log/execution 是否具备真实事务原子性；
* 结构化字段、14 字段与 model_config -> snapshot mapping 是否完整；
* 文件路径与失败日志是否可能泄露；
* 测试是否真实覆盖 PostgreSQL、scheduler isolation、existing result query 和 A->B 链路；
* allowlist 是否足以实现且没有扩大到 parser/review engine/migration/OpenAPI。

## 11. 后续联动

B 经 Codex Review Intake 接纳后解锁 `TASK_SPEC-MVP-001-C`（execution 状态查询 API）。
B 不创建 C endpoint，也不修改前端。
