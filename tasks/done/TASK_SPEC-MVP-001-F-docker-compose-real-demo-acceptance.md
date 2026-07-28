# TASK_SPEC-MVP-001-F：Docker Compose 真实浏览器 Demo 验收

状态：DONE / ARCHIVED_WITH_FEATURE / DUAL_AUDIT_GO / FEATURE_PR_PENDING

TASK_SPEC 类型：`acceptance`

父 TASK：`TASK-MVP-001`

父 TASK Level：`L2 Feature`（真实浏览器 / PostgreSQL / Docker Compose 风险触发）

Integration unit：`FEATURE-MVP-001`

执行方：Codex

所在分支：`codex/feature-mvp-001-contract-review-loop`

冻结基线 commit：`539c9e6`（A~E 已接纳）

## 0. 任务摘要

本规格只验证已接纳 A~E 能否在标准 Docker Compose 环境形成真实用户闭环：

```text
/review/new
-> POST /api/review/tasks
-> Task + QUEUED Execution
-> Single Review Worker
-> status polling
-> /review/results/{taskId}?executionId={executionId}
-> ReviewResultSnapshot
```

F 不新增产品能力，不修改业务代码，不运行 TASK-034 正式 E2E，不把 Demo 输出声明为
独立质量 ground truth 或 Production Ready。

## 1. Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/active/TASK-MVP-001-contract-review-user-loop-demo.md`
* 本规格
* `docs/deployment.md`
* `docs/frontend.md`
* `docs/backend.md`
* `docs/database.md`
* `deploy/compose/compose.yml`
* `deploy/env/.env.example`
* `packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json`

## 2. 冻结输入与环境

### 2.1 标准环境

```text
Compose project: cqcp
Web:      http://localhost:15173
API:      http://localhost:18080
Postgres: localhost:54329 / cqcp
```

启动命令：

```powershell
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml up -d --build
```

允许重建当前 `cqcp` 服务；不得执行 `down -v`、删除 volume、删除其他容器或修改 Docker
全局设置。现有 `cqcp_postgres_data` 保留，F 以新建 task/execution identity 隔离证据。

### 2.2 Demo 输入

DOCX：

```text
packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx
```

结构化字段使用 `CQCP-MVP-DOCX-001.json` 的
`goldenExpected.structuredFields`，即：

```text
contractName=奔腾公司企鹅岛项目三标段土建总承包工程合同
partyAName=奔腾公司
partyBName=前水公司
projectName=企鹅岛
contractTotalAmount=8848
taxExcludedAmount=7830.09
taxAmount=1017.91
taxRate=13
pricingMode=FIXED_TOTAL_PRICE
paymentMethod=MONTHLY
prepaymentRatio=0
progressPaymentRatio=70
completionPaymentRatio=80
settlementPaymentRatio=97
warrantyRetentionRatio=3
invoiceType=VAT_SPECIAL
currency=CNY
```

Demo 页面允许使用“填入 Demo 示例”，但必须由执行者选择真实 DOCX 文件并确认页面可见字段。

## 3. 文件与副作用边界

允许写入：

```text
outputs/task-mvp-001-demo-acceptance/**
tasks/active/TASK_SPEC-MVP-001-F-docker-compose-real-demo-acceptance.md
tasks/active/TASK-MVP-001-contract-review-user-loop-demo.md
data/uploads/**                      # runtime，受 .gitignore 约束
Docker cqcp containers/volume state # runtime
```

禁止修改：

* `apps/**`、`packages/api-contracts/**`
* migration、ADR、ARCHITECTURE、PRD
* fixture、expected JSON、DOCX、XLSX、人工 ground truth
* TASK-034、TASK-036/C2、Review assets
* 依赖、Dockerfile、Compose、环境模板

如真实验收发现产品缺陷，立即 STOP；先由 Codex建立同一父 TASK 下的定点修复边界，修复并
完成回归后，从新的冻结 commit 重跑 F。

## 4. 验收步骤

### 4.1 Compose 与健康

1. `docker version` 成功。
2. `docker compose ... config --quiet` 成功。
3. 使用当前 Feature 源码执行 `up -d --build`。
4. `postgres` 为 healthy，`api-server`、`admin-web` 为 running。
5. `GET http://localhost:18080/actuator/health` 返回 HTTP 200 与 `status=UP`。
6. `GET http://localhost:15173/review/new` 返回 HTTP 200。

### 4.2 浏览器闭环

使用浏览器真实页面：

1. 打开 `/review/new`，确认“当前仅支持 DOCX，DOC 待后续开发”。
2. 使用 Demo 示例预填并选择 §2.2 DOCX。
3. 发起审核，捕获创建响应的 taskId、executionId、`QUEUED` 和 resultUrl。
4. 确认进入精确状态路由；状态页展示 taskId/executionId、公开状态、currentStage、
   时间和 `MVP_DEMO_MOCK / MOCK` 模型摘要。
5. 确认轮询无页面错误；execution 到达 `SUCCESS` 或 `PARTIAL_SUCCESS` 后自动进入后端
   resultUrl。`FAILED` 不通过 F。
6. 结果页 task/execution identity 必须匹配；展示 9 个审核点、状态统计、业务说明、
   结构化输入、证据摘要和至少一个最小 SourceAnchor 定位入口。
7. 页面不得出现 secret、stack trace、raw output、endpoint URL、`SYS-*` 或 diagnostics。

### 4.3 PostgreSQL 只读核查

以浏览器产生的 taskId/executionId 精确查询：

```sql
SELECT task_id, caller_type, source_type, contract_name, contract_type_code,
       result_url, currency, contract_metadata, structured_fields_snapshot
FROM task
WHERE task_id = '<taskId>';

SELECT execution_id, task_id, status, current_stage, rule_set_version,
       model_profile_code, model_config_version, parser_version,
       provider_type, model_name, endpoint_alias, started_at, finished_at
FROM execution
WHERE task_id = '<taskId>' AND execution_id = '<executionId>';

SELECT stage_name, attempt, event_type, summary_status, diagnostic_code,
       duration_ms, created_at
FROM task_stage_log
WHERE task_id = '<taskId>' AND execution_id = '<executionId>'
ORDER BY task_stage_log_id;

SELECT task_id, execution_id, status,
       jsonb_array_length(point_results) AS point_count,
       jsonb_array_length(source_anchors) AS anchor_count,
       structured_fields_snapshot,
       rule_set_version, model_profile_version
FROM review_result_snapshot
WHERE task_id = '<taskId>' AND execution_id = '<executionId>';
```

必须证明：

* Task、Execution、stage logs、Snapshot 均存在且 identity 一致；
* execution 与 snapshot 使用 legacy `ruleSetVersion=v20260705.1`；
* model profile 来自 ADR-017 的 `MVP_DEMO_MOCK` binding；
* snapshot `point_count=9`；
* `contract_metadata.documentReference` 是受控相对路径；
* 容器内 `/data/cqcp/uploads/<documentReference>` 存在且位于配置根目录。

### 4.4 API 与日志

* 保存创建、最终 status 和 result API 的脱敏 JSON。
* 保存 `docker compose ps`、健康检查和目标 task/execution 的过滤日志。
* 日志证据不得包含合同全文、绝对上传根、secret 或非目标任务敏感内容。

## 5. 证据目录

固定目录：

```text
outputs/task-mvp-001-demo-acceptance/
  run-manifest.json
  console-summary.md
  create-response.json
  final-status.json
  result-snapshot.json
  database-summary.md
  screenshots/
    01-new-review.png
    02-execution-status.png
    03-result-page.png
```

`run-manifest.json` 至少记录：

```text
schemaVersion
runId
branch
headCommit
startedAt
finishedAt
taskId
executionId
publicStatus
currentStage
snapshotStatus
pointCount
anchorCount
ruleSetVersion
modelProfileCode
commands
evidenceFiles
finalDecision
```

任何凭据、secret、完整合同内容、绝对用户路径不得写入证据。

## 6. Must Pass

1. Compose 使用当前 Feature 源码成功 build/start，三个服务健康。
2. 真实浏览器从 `/review/new` 创建新任务，无预置 taskId。
3. 创建响应为 202 且四个关键字段合法。
4. 页面进入精确状态路由并轮询至成功或部分成功终态。
5. 自动进入后端返回的正式结果路由。
6. 结果 snapshot identity 与页面 identity 完全一致。
7. 结果页展示 9 个审核点、结构化输入、证据摘要和 SourceAnchor。
8. PostgreSQL 四类记录和上传文件持久化均存在。
9. 使用 legacy `v20260705.1` 与 `MVP_DEMO_MOCK`，未激活 C2。
10. 页面、证据和目标日志无敏感信息泄露。
11. 验收证据文件齐全、相互一致、可复核。
12. 不修改 §3 禁止路径，不运行 TASK-034 正式 E2E。

## 7. 最终回归

F 通过后执行：

```powershell
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml \
  --profile test run --rm api-server-test gradle test --no-daemon

npm.cmd run test:admin-web
npm.cmd run lint:admin-web
npm.cmd run build:admin-web
node scripts/validate-review-assets.mjs
git diff --check
```

必须报告真实退出码和测试计数，不得用定向 green 覆盖全量失败。

## 8. STOP 条件

* Compose 无法使用标准端口或标准 project 启动；
* 需要删除现有 volume/容器数据才能继续；
* 需要修改业务代码、API、migration、Compose、fixture/expected 或核心审核语义；
* execution 进入 `FAILED`；
* 结果不是 9 个审核点，identity 不一致或 snapshot 不存在；
* 需要激活 `v20260715.1` 或调用外部真实模型；
* 无法形成脱敏、可复核证据。

## 9. 实现报告

执行完成后记录真实：

* PowerShell、branch、HEAD、初始状态；
* Compose build/start/health；
* 浏览器步骤与截图；
* taskId/executionId；
* PostgreSQL 与上传文件证据；
* API、结果页和敏感信息检查；
* 最终回归命令、退出码与测试计数；
* dirty allowlist；
* `GO / NO-GO / STOP`。

### 9.1 实际运行

- PowerShell：7；分支：
  `codex/feature-mvp-001-contract-review-loop`；A~E 基线：`539c9e6`；
  F1 后运行 HEAD：`439a02012c54f11577c6e916591d69db2a7d5d58`。
- Compose config、build、start 均通过；PostgreSQL healthy，API `status=UP`，
  admin-web HTTP 200。
- 真实浏览器从 `/review/new` 选择既有 `CQCP-MVP-DOCX-001`，使用冻结的
  17 个结构化字段提交；Nginx access log 记录 POST 返回 HTTP 202。
- Task：`TASK_2f2539c99d574d29b993c5733c20b4bd`。
- Execution：`EXEC_321f36a3ad37415993b5dcdd5c11c0cb`。
- 首次状态页为 `QUEUED / 排队中`；Worker 完成后自动进入同源正式结果路由。
- 最终 `SUCCESS`；9 个审核点全部 `PASS`，其余统计为 0；
  `FULL_REVIEWED / 9 of 9 / HIGH`。
- 页面展示 17 个结构化字段、9 个业务审核点、证据摘要和 block 级
  SourceAnchor；`定位到 block-4` 交互已验证。
- PostgreSQL 的 Task、Execution、12 条阶段事件和 Snapshot identity 一致；
  snapshot 有 9 个 point result、8 个去重 SourceAnchor。
- execution/snapshot 保持 legacy `v20260705.1`；
  model profile 为 `MVP_DEMO_MOCK / MOCK`，未激活 C2。
- `documentReference` 是 task-scoped 随机相对路径；源文件与持久化文件均为
  90,276 bytes，SHA-256 一致。
- 脱敏证据位于 `outputs/task-mvp-001-demo-acceptance/`，包含 manifest、
  API JSON、数据库摘要、命令摘要和四张页面截图。

### 9.2 回归与定点修复

- Compose test profile 首次因容器 DNS 无法解析 Maven 域名而未进入测试执行；
  该事实已保留在 `console-summary.md`。
- 随后强制全量回归暴露创建任务集成测试的 Worker/FK 清理竞态；按同一父任务
  `TASK_SPEC-MVP-001-F1` 定点修复测试隔离，commit `439a020`。
- F1 定向 16/16；连续两轮后端全量强制回归均为 313/313。
- admin-web 63/63、lint、production build、review-assets validator 7/7、
  `git diff --check` 全部通过。
- 未修改生产业务代码、API、migration、Compose、fixture/expected、
  核心审核语义或 TASK-034。

### 9.4 双审计补充验收

- 首轮 Codex 双代理审计中，代码/API/数据库代理为 `GO`；测试/安全/真实证据代理
  因两个前端 fail-closed 缺口和原始证据不足给出 `NO-GO`，因此旧冻结基线失效，
  CC AUDIT 未发送。
- `TASK_SPEC-MVP-001-F2` 已按 red-green 定点修复并接纳；修复后 admin-web
  `63/63`，后端连续两轮 `313/313`，Compose 重建与健康检查通过。
- 重建后再次创建真实任务并由浏览器验证精确状态路由到正式结果路由，结果仍为
  `SUCCESS / 9 PASS / FULL_REVIEWED`。
- 测试、Compose、HTTP、数据库和浏览器 DOM 原始输出已补充到
  `outputs/task-mvp-001-demo-acceptance/raw/`，等待新冻结基线的完整双重复审。

### 9.3 Dirty allowlist

- `outputs/task-mvp-001-demo-acceptance/**`
- `tasks/active/TASK_SPEC-MVP-001-F-docker-compose-real-demo-acceptance.md`
- 父 TASK 与 Feature 统一 Memory Writeback 文件
- `data/uploads/**` 仅为 Compose runtime side effect，不进入 Git diff/commit

## 10. Codex Review Intake

F 证据完成后由 Codex逐条核验 Must Pass；F 通过只证明 Demo 用户闭环，不证明正式
TASK-034 质量 E2E、完整 PRD MVP 或 Production Ready。

Decision：`ACCEPT_F / GO_TO_FEATURE_MEMORY_WRITEBACK_AND_DUAL_AUDIT`。

Must Pass 1~12 全部满足，无 F 阶段遗留 blocking finding。F1 只修复测试隔离，
不改变 Demo 业务结果。F 的接纳口径严格限定为“真实用户闭环 Demo 可演示”，不扩张为
正式质量 E2E、57/57 coverage、完整 PRD MVP 或 Production Ready。
