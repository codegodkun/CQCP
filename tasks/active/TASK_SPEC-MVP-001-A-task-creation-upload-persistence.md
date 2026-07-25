# TASK_SPEC-MVP-001-A：任务创建与上传持久化

状态：IMPLEMENTATION_ACCEPTED / CODEX_REVIEW_GO / TASK_SPEC_COMPLETE

TASK_SPEC 类型：`execution`

父 TASK：`TASK-MVP-001`

父 TASK Level：`L2 Feature`（数据库/API/文件安全风险触发）

Integration unit：`FEATURE-MVP-001`

执行方：Claude Code / DeepSeek

所在分支：`codex/feature-mvp-001-contract-review-loop`

## 0. 任务摘要

纠正 `POST /api/review/tasks` 既有 OpenAPI 3.1 schema 的可满足性与错误表达后，实现该接口：接收 DOCX 与 JSON metadata，完成可证伪校验，把文件保存到受控 task-scoped 本地路径，并在单个 PostgreSQL 事务中使用 TASK-037 binding 创建 Task 与首个 `QUEUED` Execution，返回冻结的 `202` 响应。本规格不启动 worker、不调用业务 DOCX parser、不修改审核语义。

### 0.1 角色与门禁

* Codex 冻结规格、审查编码前映射计划、实现报告与 diff；不得编写本规格业务代码后自行宣布通过。
* Claude Code / DeepSeek 修改代码前必须先输出 §0.2 计划并暂停；Codex 明确 `GO_TO_IMPLEMENT` 前不得编码。
* 执行方不得 commit、push、切分支、修改父 TASK 或扩大文件范围。
* 独立 agent 只做只读规格/实现审计。

### 0.2 编码前规格映射计划

必须逐项输出：

```text
AC1~AC18 映射：
- 每条验收断言的真实输入、代码路径、数据库/文件系统效果和测试。

字段映射：
- multipart metadata -> task 列、contract_metadata、structured_fields_snapshot。
- ExecutionBindingRelease 14 字段 -> execution 14 个 NOT NULL 列。
- ValidationError / BusinessError / 202 response 的 OpenAPI 字段。

原子性与补偿：
- binding resolve、Task insert、Execution insert 的事务边界。
- DB 失败时已保存文件的删除补偿；补偿失败如何记录并继续抛出原始失败。

路径安全：
- upload root、随机相对路径、canonical/normalize 校验、原始文件名只作为 metadata。

明确不修改：
- V1/V2 migration、OpenAPI、TASK-036/C2、review-assets、worker、parser、state machine、fixture/expected。

预计测试：
- controller contract/validation、document store、service、PostgreSQL integration、existing regression。
```

### 0.3 文件访问范围

```text
✅ 允许修改：
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationController.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationService.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationRepository.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/LocalReviewDocumentStore.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationModels.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationExceptionHandler.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationConfiguration.java
  apps/api-server/src/main/resources/application.yml
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationControllerTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationServiceTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationIntegrationTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/LocalReviewDocumentStoreTest.java
  deploy/compose/compose.yml
  tasks/active/TASK_SPEC-MVP-001-A-task-creation-upload-persistence.md

👀 允许只读：
  AGENTS.md
  CURRENT_CONTEXT.md
  PRD.md
  tasks/active/TASK-MVP-001-contract-review-user-loop-demo.md
  decisions/ADR-017-execution-binding-release-and-demo-profile-readiness.md
  docs/ARCHITECTURE.md
  docs/backend.md
  docs/database.md
  docs/deployment.md
  packages/api-contracts/openapi.yaml
  packages/api-contracts/openapi.json
  apps/api-server/build.gradle.kts
  apps/api-server/src/main/resources/db/migration/V1__cqcp_mvp_core_schema.sql
  apps/api-server/src/main/resources/db/migration/V2__execution_binding_release.sql
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ExecutionBinding*.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/JdbcExecutionBindingRepository.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskResultQuery*.java

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
multipart DOCX + metadata
  ->【Controller / validation / LocalReviewDocumentStore】
  ->【ExecutionBindingCatalog + PostgreSQL Task/Execution transaction】
  -> 202 taskId/executionId/status/resultUrl
  -> worker（后续 B，不在本规格）
```

红线：

1. 只接受 `.docx`，不接受 `.doc`、PDF、空文件或无文件名输入。
2. 不信任原始文件名；真实路径只能由服务端 `taskId + UUID + ".docx"` 生成。
3. `uploadRoot.resolve(relative).normalize()` 必须仍 `startsWith(uploadRoot.normalize().toAbsolutePath())`；任何越界立即失败。
4. 请求结束后文件仍可读取；不得保存 `MultipartFile` 临时路径。
5. Task 与首个 Execution 必须同一 PostgreSQL 事务；Execution insert 失败时 Task 不得残留。
6. 文件系统不参与数据库事务：先持久化文件，DB/binding 失败时 best-effort 删除该次文件；必须重新抛出原始失败，不得返回成功。
7. 14 字段只来自 `ExecutionBindingCatalog.resolveDefault("MVP_DEMO","DEMO","ENGINEERING")`；不得复制常量、读取环境变量或使用 C2 `v20260715.1`。
8. 不触发 state machine、业务 parser、模型、snapshot、stage log 或 worker；仅允许用已有 Apache POI OPC 能力验证上传是否为可打开且包含 Word 主文档部件的 DOCX 容器。
9. 不新增 migration、依赖、业务 endpoint 或执行状态枚举。OpenAPI 只允许本规格冻结的 schema 可满足性、验证错误码、管理台 caller 描述和 `503` 存储错误纠错。

## 2. 冻结接口

### 2.1 HTTP

```text
POST /api/review/tasks
Content-Type: multipart/form-data

parts:
  file: binary, required
  metadata: application/json, required

success:
  HTTP 202
  {
    "taskId": string,
    "executionId": string,
    "status": "QUEUED",
    "resultUrl": "/review/results/{taskId}?executionId={executionId}"
}
```

当前接口实现只服务受控管理台 Demo 入口。caller 分类由服务端部署边界固定为 `ADMIN`，不得允许请求指定 `callerType`、`callerId`、`modelProfileCode`、版本字段、provider 或路径。外部系统 caller 身份接入不在本规格范围。

### 2.2 Metadata

```text
businessDocumentId?: string
contractType: ENGINEERING
structuredFields:
  contractName: nonblank, length <= 255
  partyAName: nonblank
  partyBName: nonblank
  projectName: nonblank
  contractTotalAmount: decimal scale <= 2
  taxExcludedAmount: decimal scale <= 2
  taxAmount: decimal scale <= 2
  taxRate: 0..100, scale <= 4
  pricingMode: FIXED_TOTAL_PRICE | PROVISIONAL_TOTAL_PRICE
  paymentMethod: MONTHLY | MILESTONE
  invoiceType: VAT_GENERAL | VAT_SPECIAL
  currency?: CNY; absent normalizes to CNY

MONTHLY additionally requires:
  prepaymentRatio
  progressPaymentRatio
  completionPaymentRatio
  settlementPaymentRatio
  warrantyRetentionRatio

MILESTONE additionally requires:
  prepaymentRatio
  milestonePaymentTerms: nonblank

all ratio fields: 0..100, scale <= 2
```

金额不在本规格做跨字段总和/税额推导；不得静默计算或补造业务值。

`metadata`、`structuredFields` 以及 multipart 顶层均拒绝未声明属性。字符串 `nonblank` 表示至少包含一个非空白字符；不得只依赖 Jackson 默认忽略 unknown property。`.docx` 扩展名大小写不敏感。单文件上限冻结为 `25 MiB`（`26,214,400` bytes）。

### 2.3 PostgreSQL mapping

Task：

```text
task_id: "TASK_" + 32-char lowercase UUID hex
caller_id: null
caller_type: ADMIN  # 当前受控管理台 Demo 部署上下文的服务端固定值，不来自请求
source_type: DOCX_UPLOAD  # 仅表示本接口收到并持久化 DOCX upload，不表示 caller 身份
contract_name: structuredFields.contractName
contract_type_code: ENGINEERING
result_url: frozen resultUrl
currency: normalized currency
structured_fields_snapshot: normalized structuredFields JSON
contract_metadata:
  businessDocumentId: optional, omitted when absent
  originalFileName: user-visible metadata only
  documentReference: task-scoped forward-slash relative path
  sizeBytes: persisted file byte count
```

Execution：

```text
execution_id: "EXEC_" + 32-char lowercase UUID hex
task_id: generated taskId
status: QUEUED
current_stage: QUEUED
supersedes_execution_id: null
14 version/model fields: copied from the single validated ExecutionBindingRelease
lease/start/finish timestamps: null
created_at/updated_at: DB defaults
```

`resultUrl` 由后端用已生成 IDs 构造；不得接受客户端路径。

### 2.4 文件存储

配置：

```text
cqcp.review.upload-root = ${CQCP_UPLOAD_ROOT:./data/uploads}
cqcp.review.max-upload-bytes = ${CQCP_MAX_UPLOAD_BYTES:26214400}
```

相对路径：

```text
{taskId}/{random-uuid-hex}.docx
```

启动/首次使用时创建 upload root 并解析其 real path。写入前创建 task 目录，拒绝任一既有路径组件或 task 目录为 symbolic link，并验证 task 目录 real path 位于 root real path 内。temp 与 final 均使用服务端随机名及 `CREATE_NEW` 语义；先写同目录 `.tmp`，再使用不带 `REPLACE_EXISTING` 的 atomic move。文件系统不支持 `ATOMIC_MOVE` 时只允许在同目录 fallback 为不覆盖的普通 move；任何目标碰撞 fail closed。失败不得保留 `.tmp`。

扩展名与非空校验后，必须对实际 bytes 做最小 DOCX 容器验证：不信任客户端 MIME；使用已有 Apache POI OPC 打开并确认存在 Word 主文档部件（等价于合法 OOXML package 中的 `word/document.xml`）。随机 bytes、普通 ZIP 或缺主文档部件的 OOXML 包均为无效；该检查不调用业务 parser、不抽取合同文本。

Compose 必须：

* 为 api-server 设置 `CQCP_UPLOAD_ROOT=/data/cqcp/uploads`；
* 为 api-server 设置 `CQCP_MAX_UPLOAD_BYTES=26214400`；
* 使用 host bind `${CQCP_UPLOAD_HOST_PATH:-../../data/uploads}:/data/cqcp/uploads`；
* 为 postgres 增加 `pg_isready` healthcheck，不改变 image、port、environment 或 volume；
* 增加以下语义精确的 opt-in `api-server-test` 服务，用于容器内定向/全量测试：

```yaml
profiles: [test]
image: gradle:8.10.2-jdk21
working_dir: /workspace/apps/api-server
volumes:
  - ../..:/workspace
  - ${CQCP_UPLOAD_HOST_PATH:-../../data/uploads}:/data/cqcp/uploads
environment:
  CQCP_DB_URL: jdbc:postgresql://postgres:5432/${CQCP_TEST_DB_NAME:-cqcp_mvp001_a_test}
  CQCP_DB_USERNAME: ${CQCP_DB_USERNAME:-cqcp}
  CQCP_DB_PASSWORD: ${CQCP_DB_PASSWORD:-cqcp}
  CQCP_UPLOAD_ROOT: /data/cqcp/uploads
  CQCP_MAX_UPLOAD_BYTES: "26214400"
depends_on:
  postgres:
    condition: service_healthy
```

* 不修改 PostgreSQL volume 或端口。

`application.yml` 同时冻结 Spring multipart `max-file-size=25MB`、`max-request-size=26MB`，并由 exception handler 把 framework 提前拒绝的超限请求映射到 `FILE_TOO_LARGE`。

### 2.5 错误契约

ValidationErrorResponse：

```text
code=VALIDATION_ERROR
message=请求校验失败
fieldErrors[] = {field, code, message}
```

只使用 OpenAPI 已有 field error code：

```text
REQUIRED_FIELD_MISSING
CONDITIONAL_FIELD_MISSING
INVALID_ENUM_VALUE
INVALID_DECIMAL_SCALE
 INVALID_PERCENT_RANGE
 UNSUPPORTED_CURRENCY
 UNSUPPORTED_FILE_TYPE
 INVALID_JSON
 EMPTY_FILE
 INVALID_DOCUMENT_CONTENT
 FILE_TOO_LARGE
 UNKNOWN_FIELD
 INVALID_STRING_LENGTH
 INVALID_FIELD_TYPE
```

HTTP 400 的冻结映射：

```text
缺 file part                  -> file / REQUIRED_FIELD_MISSING / "file 为必填项"
缺 metadata part              -> metadata / REQUIRED_FIELD_MISSING / "metadata 为必填项"
metadata JSON 无法解析         -> metadata / INVALID_JSON / "metadata JSON 无法解析"
空文件或空文件名               -> file / EMPTY_FILE / "file 不能为空"
文件超过 25 MiB               -> file / FILE_TOO_LARGE / "file 超过 25 MiB 上限"
扩展名不是 case-insensitive .docx -> file / UNSUPPORTED_FILE_TYPE / "仅支持 DOCX 文件"
.docx 名称但容器/主文档无效     -> file / INVALID_DOCUMENT_CONTENT / "file 不是有效 DOCX 文档"
unknown multipart property    -> <part name> / UNKNOWN_FIELD / "<part name> 为未声明字段"
unknown metadata/structured field -> <JSON path> / UNKNOWN_FIELD / "<JSON path> 为未声明字段"
contractName 超过 255 字符    -> structuredFields.contractName / INVALID_STRING_LENGTH / "contractName 不能超过 255 字符"
字段 JSON 类型不符合 OpenAPI  -> <JSON path> / INVALID_FIELD_TYPE / "<JSON path> 类型无效"
```

其余字段错误使用最接近的冻结 code；缺通用必填为 `REQUIRED_FIELD_MISSING`，缺付款方式条件字段为 `CONDITIONAL_FIELD_MISSING`。同一已成功解析请求的字段错误一次返回；不得返回 stack trace。
* `ExecutionBindingResolutionException`：HTTP 409，`code=EXECUTION_BINDING_UNAVAILABLE`，`message=当前执行绑定不可用`，`reason` 使用稳定 enum name，`retryable=false`，`operatorActionRequired=true`。
* 文件持久化失败：HTTP 503，`code=DOCUMENT_STORAGE_FAILED`，`message=文档存储失败`，省略 optional `reason`，`retryable=true`，`operatorActionRequired=false`，不得返回绝对路径。
* DB 写入失败：抛出原始服务端异常并回滚；不得映射为 202。

## 3. 行为步骤

1. Controller 解析 multipart，不写文件。
2. Service 完整校验 file/metadata；所有 field error 一次返回。
3. 生成 taskId、executionId、resultUrl 与随机相对 documentReference。
4. LocalReviewDocumentStore 校验 DOCX 容器并安全、完整地持久化文件，返回实际 size。
5. 在 `@Transactional` service 方法内，文件保存成功后立即登记 `TransactionSynchronization`；`afterCompletion(status)` 仅在 `STATUS_COMMITTED` 保留文件，`STATUS_ROLLED_BACK`、`STATUS_UNKNOWN` 及任何其他非 committed 状态都幂等删除 final/temp。若当前没有 active transaction/synchronization，立即删除并 fail closed。
6. `ReviewTaskCreationConfiguration` 以 `Clock.systemUTC()` 和既有 `JdbcExecutionBindingRepository` 显式构造 `ExecutionBindingCatalog`；调用 Catalog 获取唯一 binding，不修改 TASK-037 类的可见性或行为。
7. 依次 insert Task、insert Execution，复制全部 14 字段。
8. 事务成功后返回 202 DTO；不启动任何后台执行。
9. 方法内 binding/DB 失败允许立即 best-effort 删除；事务提交阶段失败必须由 synchronization 在 `afterCompletion(status != STATUS_COMMITTED)` 时补偿，明确包含 `STATUS_ROLLED_BACK`、`STATUS_UNKNOWN` 及其他非提交完成状态。删除失败只记录 taskId/documentReference 摘要，不得记录绝对 root、原始文件名或覆盖原始异常。

## 4. 验收标准

### Must Pass

1. 合法 MONTHLY multipart 返回 202 和四个冻结字段，DB 恰好新增一个 Task 与一个 `QUEUED` Execution。
2. 合法 MILESTONE 输入通过；两类 structured snapshot 保留用户值，仅 currency 可按 OpenAPI default 变为 CNY。
3. Task/Execution IDs 满足冻结格式，resultUrl 精确引用同一 IDs。
4. Task 列与 contract_metadata/structured_fields_snapshot 精确匹配 §2.3。
5. Execution 14 字段与主线 seed binding 完全一致；rule set 为 legacy `v20260705.1`，没有 `v20260715.1`。
6. binding unavailable 时返回 409 stable reason，Task/Execution 均为 0 新增且文件被补偿删除。
7. Execution insert 失败时 Task insert 回滚；提交阶段失败也触发 synchronization 文件补偿；`STATUS_ROLLED_BACK` 与 `STATUS_UNKNOWN` 均有独立回调测试，不得出现仅 Task 无 Execution。insert-time、真实 commit-time 与直接回调测试证据必须明确区分，不能互相冒充。
8. 空文件、空文件名、超限文件、`.doc`、`.pdf`、随机 bytes、普通 ZIP、缺 Word 主文档部件的 OOXML、缺 part、坏 JSON 全部按 §2.5 返回完整 400 body；不得写文件或 DB。
9. YAML/JSON 中 MONTHLY/MILESTONE 的 `allOf`、`unevaluatedProperties:false`、required、string/number 约束和错误码保持同步；Controller 对合法两类 payload 返回 202，并覆盖 common required/nonblank/maxLength、unknown property、enum、decimal scale、percent range、currency 与条件字段负例。本规格不声明执行仓库尚未具备的独立 OpenAPI 3.1 instance validator。
10. 原始文件名含 `../`、反斜线、绝对路径或重复文件名时，实际 reference 仍为服务端随机安全相对路径。
11. 保存文件与输入 bytes 相同，请求完成后可重新读取；重复原始文件名不会覆盖。
12. store 的 normalized/real target 无法保持在 root real path 内、task directory 是 symlink 或目标随机名碰撞时 fail closed 且不覆盖；临时文件在成功/失败后均不残留。symlink 用 Docker/Linux 测试证明。
13. application.yml 的 25 MiB 与 upload root 配置、Compose env/bind 精确匹配 §2.4；标准 Compose 中容器内 `/data/cqcp/uploads` 可写且 host bind 出现同一随机相对文件。
14. 不调用 parser、state machine、model、worker 或 snapshot writer。
15. 除本规格冻结的 YAML/JSON 同步契约纠错外，不修改 V1/V2 migration、其他 OpenAPI endpoint/schema、review-assets、TASK-036/C2、fixture/expected 或现有审核链路。
16. 不新增依赖；Docker 化定向/全量 backend tests 通过，真实 PostgreSQL 从 V1→V2 migration 成功。
17. `node scripts/validate-review-assets.mjs`、`git diff --check` 通过。
18. 实现报告与实际 diff/status 一致。

### Must Not

* 不使用客户端文件名或 MIME 构造/信任真实路径或 DOCX 类型。
* 不把绝对 upload root 写入数据库或响应。
* 不在 Controller 内拼装 14 字段或直接执行 SQL。
* 不使用 test-only binding、空占位、latest 或 C2 版本。
* 不以 host Gradle 结果或 `docker compose config` 单独声明验收通过。
* 不提交、不 push、不创建 PR。

### Expected 来源

本规格不修改 fixture/expected。测试 expected 来自 OpenAPI、V1/V2 schema、ADR-017 seed 和显式测试输入；不得从被测响应/数据库输出倒填。

## 5. 测试命令

```powershell
node scripts/validate-review-assets.mjs

Push-Location deploy/compose
docker compose --env-file ../env/.env.example -p cqcp --profile test config
docker compose --env-file ../env/.env.example -p cqcp up -d --wait postgres
docker compose --env-file ../env/.env.example -p cqcp exec -T postgres dropdb --if-exists -U cqcp cqcp_mvp001_a_test
docker compose --env-file ../env/.env.example -p cqcp exec -T postgres createdb -U cqcp cqcp_mvp001_a_test
docker compose --env-file ../env/.env.example -p cqcp --profile test run --rm api-server-test gradle test --rerun-tasks --tests "*ReviewTaskCreationControllerTest" --tests "*ReviewTaskCreationServiceTest" --tests "*ReviewTaskCreationIntegrationTest" --tests "*LocalReviewDocumentStoreTest"
docker compose --env-file ../env/.env.example -p cqcp --profile test run --rm api-server-test gradle test --rerun-tasks
docker compose --env-file ../env/.env.example -p cqcp up -d --build api-server
docker compose --env-file ../env/.env.example -p cqcp exec api-server sh -c "test -w /data/cqcp/uploads"
$probeName = ".cqcp-mvp001-a-" + [guid]::NewGuid().ToString("N")
$probeToken = [guid]::NewGuid().ToString("N")
docker compose --env-file ../env/.env.example -p cqcp --profile test run --rm api-server-test sh -c "printf '$probeToken' > /data/cqcp/uploads/$probeName"
$probePath = Join-Path (Resolve-Path ../../data/uploads).Path $probeName
if ((Get-Content -Raw -LiteralPath $probePath) -ne $probeToken) { throw "upload bind probe mismatch" }
Remove-Item -LiteralPath $probePath -Force
Pop-Location

git diff --check
git status --short
git diff --stat
```

PostgreSQL 必须使用 Compose service，测试需观察 V1→V2 Flyway 与真实 transaction。允许上述标准 Compose 启动/构建；不得执行 `down -v`、删除 named volume、正式浏览器 E2E、外部 API、模型或仓库依赖安装。若标准 `cqcp` 端口/容器已被范围外实例占用，STOP 并报告，不得擅自删除或重建未知容器。host Gradle 只可诊断，不构成验收证据。

## 6. Git 工作区

* 开始时分支必须为 `codex/feature-mvp-001-contract-review-loop`。
* 编码前已知 dirty 精确为以下 4 个 Codex 归属文件：
  * `M packages/api-contracts/openapi.yaml`；
  * `M packages/api-contracts/openapi.json`；
  * `?? tasks/active/TASK-MVP-001-contract-review-user-loop-demo.md`；
  * `?? tasks/active/TASK_SPEC-MVP-001-A-task-creation-upload-persistence.md`。
* 两份 OpenAPI 与父 TASK 已由 Codex 冻结，执行方只读，不得继续修改；TASK_SPEC 只允许填写实现报告。其余允许修改文件在编码前应不存在。
* 任何其他未知 dirty 立即 STOP。
* 不得 commit、push、switch、reset、clean、restore 或 stash。

## 7. STOP 条件

出现以下任一项立即停止：

* 需要 migration/OpenAPI/依赖/审核链路变化；
* 真实 schema 与 §2.3 不兼容；
* 无法在现有 Catalog 内获得完整 14 字段；
* 需要 worker、parser 或 snapshot 才能返回 202；
* 文件安全/补偿无法在允许文件内实现；
* 测试数据库不可用或出现范围外 dirty。

## 8. 实现报告

### 编码前计划

* Claude Code / DeepSeek session：`d5bba5af-51e3-4f01-8f15-b0249fa1877c`。
* 首轮计划因 commit-time `@Disabled`、snapshot currency、未冻结错误码等问题被 Codex 拒绝。
* 经三轮 Review Intake 与最终 delta，计划已冻结：真实 Spring proxy/PostgreSQL insert-time 与 commit-stage failure、production `FileCleanupSynchronization`、精确 DTO、BigDecimal scale、仅缺省 currency、OPC/IO 分流、test-specific DB/upload isolation、Compose bind probe 和精确 allowlist。
* Codex decision：`ACCEPT_PRE_CODE_PLAN / GO_TO_IMPLEMENT`。

### 实际文件

**新建 production 文件**（7 个，完全在 §0.3 allowlist）：
- `ReviewTaskCreationModels.java` — DTOs（ValidationErrorResponse、BusinessErrorResponse、FieldError、CreateReviewTaskResponse、ExecutionInsert）、异常（ValidationException、DocumentStorageException、InvalidDocumentContentException）
- `ReviewTaskCreationRepository.java` — JdbcTemplate insertTask / insertExecution
- `LocalReviewDocumentStore.java` — DOCX 安全持久化、CREATE_NEW/atomic move、OPC READ、路径安全、symlink 检查 TOCTOU、400/503 异常分流
- `ReviewTaskCreationService.java` — @Transactional createTask、完整字段校验（type/enum/scale/range/currency/conditional/unknown）、JsonNode + BigDecimal 精度、currency 归一化（仅 property 缺失）、FileCleanupSynchronization package-private static
- `ReviewTaskCreationController.java` — POST /api/review/tasks、HttpServletRequest.getParts() unknown part 检测
- `ReviewTaskCreationExceptionHandler.java` — @RestControllerAdvice：exact DTO、禁止 ProblemDetail、400/409/503 精确 message
- `ReviewTaskCreationConfiguration.java` — @Bean wiring Catalog + Store + Service

**新建 test 文件**（4 个，完全在 §0.3 allowlist）：
- `LocalReviewDocumentStoreTest.java` — 文件安全、OPC、碰撞、temp 清理、deleteIfExists
- `ReviewTaskCreationServiceTest.java` — 全部校验规则、FileCleanupSynchronization direct callback（COMMITTED/ROLLED_BACK/UNKNOWN）
- `ReviewTaskCreationControllerTest.java` — @WebMvcTest、HTTP binding、missing/unknown part、handler mapping
- `ReviewTaskCreationIntegrationTest.java` — @SpringBootTest + @AutoConfigureMockMvc、@SpyBean insert-time、@Primary TxMgr commit-stage、@TempDir + @DynamicPropertySource、binding-unavailable SQL

**修改文件**（2 个）：
- `application.yml` — 添加 `cqcp.review.*`、`spring.servlet.multipart.*`
- `deploy/compose/compose.yml` — postgres healthcheck、api-server env+volumes、api-server-test 服务

**只读文件**（保持冻结）：
- `packages/api-contracts/openapi.yaml`、`openapi.json` — Codex 已冻结
- 父 TASK、V1/V2 migration、ExecutionBinding*、TaskResultQuery*、fixtures

### API/DB/Storage 行为

- `POST /api/review/tasks` 接收 multipart，校验 file（DOCX）/metadata（JSON），拒绝 non-DOCX、空、超限
- 服务端生成 taskId/executionId/resultUrl，随机 UUID documentReference
- LocalReviewDocumentStore 保存文件到 `uploadRoot/{taskId}/{uuid}.docx`，CREATE_NEW + ATOMIC_MOVE fallback，OPC READ 验证
- 非 COMMITTED 状态由 FileCleanupSynchronization 补偿删除
- @Transactional 内 catalog.resolveDefault("MVP_DEMO","DEMO","ENGINEERING") → insertTask → insertExecution（14 字段）
- 202 返回 taskId/executionId/status/resultUrl；400/409/503 返回 exact DTO

### 测试结果（Codex 独立复验）

**compileTestJava**：BUILD SUCCESSFUL（Fix-7b 最终版本）。

**host 四组定向诊断（Controller + Service + Integration + LocalReviewDocumentStore）**：75 tests，0 failures/errors/skipped — BUILD SUCCESSFUL。

**Docker Compose 权威全量**：232 tests，23 suites，0 failures/errors/skipped — BUILD SUCCESSFUL。

**review-assets**：`node scripts/validate-review-assets.mjs` — 通过（7 files / 7 JSON）。

**OpenAPI 一致性**：本规格修改的 YAML/JSON 节点 semantic equality — 通过。

**git diff --check**：通过。

**Compose 标准验证**：
- `docker compose config` 通过；healthcheck 通过；PostgreSQL service healthy，V1→V2 migration 由 Spring context 实际执行；
- Linux symlink test 实际运行，0 skipped；
- `docker compose ... up -d --build api-server` 对 Fix-7b 最终源码 BUILD SUCCESSFUL；api-server 容器启动，`/data/cqcp/uploads` 可写；
- host-bind token probe 写入宿主同路径精确一致，探针已删除，无残留。
- （首次 default build 曾因 host-network DNS 不可达失败；使用 host-network 恢复依赖访问后，冻结的 `--build` 命令重新执行并成功。）

### Fix-5 / Fix-6 实际变更

**Fix-5**（OPC/IO 分流 + symlink + AC 证据）：
- `validateDocx`：`InvalidFormatException` / `ZipException` / POI runtime → `InvalidDocumentContentException`（400）；其他 `IOException` → `DocumentStorageException`（503）
- `LocalReviewDocumentStoreTest`：新增真实 symlink fail-closed 测试（Linux `assumeTrue`）；随机 bytes / 缺主文档部件 / collision 后显式断言 `.tmp` 不存在
- `ReviewTaskCreationIntegrationTest`：新增 7 项 AC3/AC4/AC10/AC11 测试：ID 精确 regex、resultUrl 精确构造、DB 关联、全部 task 列、contract_metadata 精确 JSONB（businessDocumentId + sizeBytes）、MONTHLY/MILESTONE structured_fields_snapshot、危险原始文件名仍使用安全随机 reference、重复原始文件名不覆盖

**Fix-6**（编译修复 + 断言补强）：
- `row.get("result_url")` 编译错误：强转为 `(String)` 后匹配
- `ac4_taskColumns_matchSpec`：task_id 与 response 精确关联，result_url 使用 response 对比而非独立 regex
- `ac4_contractMetadata_jsonb`：使用局部 `docxBytes` 变量精确断言 `sizeBytes == docxBytes.length`；metadata 显式传 `businessDocumentId="BD-001"`；`monthlyJsonWithBusinessDocumentId` helper 构造请求

**Fix-7 / Fix-7b**（POI runtime 精确捕获 + 清理日志安全 + AC10 循环）：
- `validateDocx` 只捕获 `NotOfficeXmlFileException` 和 `OpenXML4JRuntimeException` 等已知 POI runtime content exceptions，不再把所有 `RuntimeException` 映射 400
- delete/final/temp 清理失败只以 `documentReference` 摘要 warn，不记录 Path/absolute root/original filename/exception cause，且不覆盖原始异常
- `cleanupTemp` 签名接收 `(Path, String documentReference)`，日志仅 `docRef={}`
- AC10 同一 integration test 循环覆盖 `../../...`、反斜线相对路径、POSIX absolute、Windows absolute 四种原始文件名；每次按 response `taskId` 精确查 DB，`documentReference` 匹配 `TASK_[hex32]/[hex32].docx`，real path 位于 upload root

### STOP、假设、遗留

* 无 STOP 条件触发。
* 假设：`DataSourceTransactionManager` 类路径可用（spring-jdbc 从 mybatis-spring-boot-starter 传递）。
* 假设：`@SpyBean` 在 Spring Boot 3.3.x / Mockito 中正确包装 BeanFactory 单例，AOP proxy 收到 spied repository。
* 遗留：commit-stage `TransactionSystemException` 被 Spring AOP 拦截后以 `ServletException` 包装抛出，由 `assertThrows` + `findCause` 验证原因链。
* 不新增依赖、migration、endpoint 或 allowlist 外 Java 文件。
* 执行方本次变更：7 production Java + 4 test Java + application.yml + compose.yml + TASKSPEC §8 实现报告。当前 worktree 另有 Codex 在编码前冻结及后续状态同步拥有的 OpenAPI YAML/JSON、父 TASK、TASKSPEC §9 Codex Review Intake 等变更。执行方未修改 OpenAPI、父 TASK、架构、ADR。

## 9. Codex Review Intake

第一轮独立规格审计结论：`NO-GO`。阻塞项为 OpenAPI 3.1 `allOf + additionalProperties:false` 不可满足、验证错误码不足、caller provenance 未冻结、事务 commit failure 补偿缺口、DOCX/symlink/覆盖安全不可证伪及本机 Gradle 与 Docker 权威验收冲突。

本轮修订：

* YAML/JSON 同步改用 concrete schema `unevaluatedProperties:false`，冻结 nonblank 与 `contractName <= 255`；
* 增加 `INVALID_JSON / EMPTY_FILE / INVALID_DOCUMENT_CONTENT / FILE_TOO_LARGE / UNKNOWN_FIELD` 和存储失败 `503`；
* 明确当前仅受控管理台 Demo，服务端固定 `ADMIN`，外部 caller 身份接入不在范围；
* 使用 transaction synchronization 覆盖提交阶段回滚补偿；
* 冻结 OPC 主文档校验、root real path、symlink 拒绝、`CREATE_NEW` 与不覆盖 move；
* 增加 Compose test profile、专用 PostgreSQL 数据库与容器内验收。

第二轮独立规格审计结论：`NO-GO`。本次 delta 已把补偿条件改为所有非 `STATUS_COMMITTED` 状态，冻结真实 4 文件 dirty 清单及 OpenAPI 只读边界，补齐 `api-server-test` 完整服务定义、PostgreSQL health/readiness 与 host bind 内容探针，并取消无验证器支撑的 OpenAPI instance-validation 声明。

第三轮独立 delta 规格审计结论：`GO`，确认最后一处补偿文本已统一为 `afterCompletion(status != STATUS_COMMITTED)`。

Codex decision：`ACCEPT_SPEC / PRE_CODE_PLAN_REQUIRED`。允许执行方只读提交 §0.2 编码前规格映射计划；计划仍须单独 `GO_TO_IMPLEMENT`。

首轮编码前计划 Review Intake：`REVISE_PRE_CODE_PLAN / NO_IMPLEMENTATION`。计划不得把真实 commit-time 测试标为 `@Disabled`，不得把缺省 `currency=CNY` 排除在 normalized `structured_fields_snapshot` 外，不得使用契约不存在的 `INVALID_VALUE`。Codex 已把字符串长度错误冻结为 `INVALID_STRING_LENGTH`，并把 JSON 类型错误冻结为 `INVALID_FIELD_TYPE`。

最终编码前计划 Review Intake：`ACCEPT_PRE_CODE_PLAN / GO_TO_IMPLEMENT`。实现必须包含最终 delta：真实 proxied service 的 insert/commit failure 注入、成功文件保留与失败文件清理分离、精确无额外字段 DTO、仅 property 缺失时 default currency、BigDecimal exact scale、OPC invalid-content 400 / filesystem I/O 503 分流。

实现后必须由 Codex 审查真实 diff、事务/补偿证据、测试与实现报告；本 TASK_SPEC 不自动产生 commit 或 PR。

### 实现 Review Intake（2026-07-25）

最终独立只读实现复审：`GO`。上轮 400/503 分流、Linux symlink 证据和实现报告三项阻塞，以及 Fix-7 的 POI runtime 精确捕获、清理日志安全和 AC10 四类危险文件名覆盖均已关闭；审计未修改文件或执行 Git 写操作。

Codex 最终决定：`ACCEPT_IMPLEMENTATION / TASK_SPEC_COMPLETE / TASK_SPEC-MVP-001-B_UNLOCKED / NO_GIT_ACTION`。

接纳证据：

* host 四组定向诊断 75/75 通过；
* Docker Compose 权威全量 232/232、23 suites、0 failures/errors/skipped；
* Linux symbolic-link fail-closed 测试在 Docker 内真实执行；
* 标准 `docker compose ... up -d --build api-server` 对最终源码成功，runtime health=`UP`，upload root 可写；
* host-bind token 内容一致且无探针残留；
* `node scripts/validate-review-assets.mjs`、OpenAPI 本轮节点 YAML/JSON 语义一致性与 `git diff --check` 通过；
* 14 个 Execution 字段只来自 `resolveDefault("MVP_DEMO","DEMO","ENGINEERING")`，逐项匹配 ADR-017/V2 seed，rule set 为 legacy `v20260705.1`；
* 当前差异均为编码前 Codex-owned dirty、父任务状态同步或 §0.3 allowlist，未修改 migration、依赖、worker、parser、TASK-036/C2、架构或 ADR。

非阻塞残余风险：

* Demo 级 symbolic-link 检查存在已接受的 TOCTOU 窗口；
* 极低概率的预存同名 `.tmp` 碰撞可能在 `finally` 中被清理；
* 清理日志按冻结要求不记录 cause，运维诊断信息有限；
* commit-stage 异常仍表现为通用 5xx。

本 TASK_SPEC 不单独 commit、push、PR 或 merge；继续由 `FEATURE-MVP-001` 统一集成。

## 10. 后续联动

结论 A 后解锁 `TASK_SPEC-MVP-001-B`；A 不启动或模拟 worker。
