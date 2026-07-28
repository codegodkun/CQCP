# TASK_SPEC-MVP-001-F 运行与验证摘要

执行目录：`C:\tmp\cqcp-mvp001`

分支：`codex/feature-mvp-001-contract-review-loop`

F1 后 HEAD：`439a02012c54f11577c6e916591d69db2a7d5d58`

## Compose 与健康检查

- `docker compose ... config --quiet`：通过。
- `docker compose ... up -d --build`：通过。
- API 镜像：`sha256:7d935ab9f6d590a7bf72e9038a71a6d5f0b98d0594ff561a999aefd10208e5a1`
- admin-web 镜像：`sha256:c5a87f2c95a5103904b9a7a11e83b2531d058d283d00d5ca93fbc3337d536326`
- PostgreSQL：`healthy`，端口 `54329`。
- API：`http://localhost:18080/actuator/health` 返回 `{"status":"UP"}`。
- admin-web：`http://localhost:15173/review/new` 返回 HTTP 200。

## 浏览器闭环

1. 真实浏览器打开 `/review/new`。
2. 选择既有 `CQCP-MVP-DOCX-001` DOCX，并使用规格冻结的 Demo 结构化字段。
3. Nginx access log 记录 `POST /api/review/tasks` 返回 HTTP 202、响应 251 bytes。
4. 浏览器进入：
   `/review/tasks/TASK_2f2539c99d574d29b993c5733c20b4bd/executions/EXEC_321f36a3ad37415993b5dcdd5c11c0cb`。
5. 首次页面事实为 `公开状态=排队中`、`当前阶段=排队中`，随后按 1 秒规则继续查询。
6. Worker 完成后自动跳转一次到同源正式结果 URL：
   `/review/results/TASK_2f2539c99d574d29b993c5733c20b4bd?executionId=EXEC_321f36a3ad37415993b5dcdd5c11c0cb`。
7. 结果页显示 `SUCCESS`、PLANNED 9、PASS 9，其余统计均为 0，审核完整度为 9/9、HIGH。
8. 页面展示 17 个结构化字段、9 个审核点、业务说明、证据摘要和 block 级 SourceAnchor。
9. 点击 `定位到 block-4` 后页面滚动至对应原文定位卡片，URL identity 不变。
10. 页面未展示完整 prompt、raw model output、secret、stack trace 或内部诊断明细。

截图：

- `screenshots/01-new-review.png`
- `screenshots/02-execution-status.png`
- `screenshots/03-result-page.png`
- `screenshots/04-review-result-summary.png`（补充的结果摘要首屏）

## 后端回归与 F1

Compose test profile 第一次启动未进入测试执行，原因是容器无法解析
`repo.maven.apache.org`。该事实归类为测试容器网络基础设施失败，不归类为代码失败。

随后使用仓库现有 Gradle 8.10.2、同一 PostgreSQL 专用 test DB 强制执行全量测试。
首次强制执行暴露 `ReviewTaskCreationIntegrationTest` 测试隔离缺陷：
Single Worker 未关闭且清理遗漏 FK 子表，313 个测试中 16 个在同一清理行失败。

按 `TASK_SPEC-MVP-001-F1` 定点修复后：

- 定向创建任务集成测试：16/16 通过；
- 第一次全量强制回归：313/313 通过；
- 不重建 test DB 的连续第二次全量强制回归：313/313 通过。

F1 commit：`439a020 test(review): isolate task creation integration tests`。

## 前端与静态验证

- admin-web 全量测试：3 files、63/63 通过；
- admin-web lint：通过；
- admin-web production build：通过，1489 modules transformed；
- review-assets validator：7/7 通过；
- `git diff --check`：通过。

Vite 仅输出既有 500 kB chunk size warning；React Router 仅输出 v7 future flag warning。
两者均不构成本 Feature 的阻塞 finding。

## 非声明项

- 未运行或改写 TASK-034。
- 不声明正式质量 E2E、57/57 coverage 或 Production Ready。
- 未激活 `v20260715.1`；保持 legacy `v20260705.1` 与 `MVP_DEMO_MOCK`。

## F2 首轮审计 finding 修复与证据补强

首轮 Codex 双代理审计中，一条审计为 GO，另一条因两个前端 fail-closed 缺口和
F 原始证据不足为 NO-GO。旧冻结基线失效，CC AUDIT 未发送。

- 修复前新增定向测试真实结果：2 failed / 31 passed。
- 修复后定向测试：33/33；admin-web 全量：63/63。
- 创建响应拒绝 whitespace-only task/execution identity 和空白 resultUrl。
- 正式结果 query 不再静默 trim；非规范 executionId 直接地址无效且不请求 API。
- 后端本机缓存路径连接同一 Compose PostgreSQL test DB 连续两轮强制全量：
  313/313、313/313。
- Compose test profile 的一次依赖下载因 Maven 传输 `Tag mismatch` 在 compile
  阶段失败；该原始失败已保留，测试通过结论来自上述两轮真实执行。
- Compose config 与包含 F2 修复的重建启动通过；PostgreSQL healthy，API UP，
  admin-web HTTP 200。
- 重建后定点创建获得 HTTP 202：
  `TASK_cc6250cce538441195efe630c220f9c5` /
  `EXEC_e6755d0dd53e44dab7dae9032784fe7b`。真实浏览器打开精确状态路由后自动进入
  同源正式结果路由，结果仍为 SUCCESS、9/9 PASS、FULL_REVIEWED。
- 新任务的 Task、Execution、stage logs、Snapshot 和上传文件已在 PostgreSQL /
  容器中只读复核，identity 一致并保持 v20260705.1 / MVP_DEMO_MOCK。

原始命令、退出码、stdout/stderr 和浏览器 DOM 位于 `raw/`。首次 F 的 QUEUED DOM
也已固化，明确包含双 identity、排队状态、模型摘要和 1 秒非重叠轮询提示。所有原始
文件在重新冻结时进入 SHA-256 manifest。

第二次冻结复核进一步发现：数据库文件存在性检查的原始 stdout 曾包含容器绝对上传根，
且原始 red 执行输出未冻结。旧 v2 基线因此失效。当前证据已完成以下补正：

- 数据库文件检查仅输出 size `90276`，命令路径全部使用 placeholder；
- 独立临时副本以 `HEAD@439a020` 旧实现 + staged F2 tests 重现
  `EXIT_CODE=1 / 2 failed / 31 passed`；
- 当前 staged 实现相同定向测试为 `EXIT_CODE=0 / 33 passed`；
- 两份完整 stdout/stderr 分别保存在 `raw/f2-red-reproduction.txt` 和
  `raw/f2-green-targeted.txt`。

## F2 浏览器创建链路重新验收

v3 审计指出：此前 F2 重新验收用 curl 创建任务，再由浏览器直接打开已经完成的状态
路由，不能证明受影响的前端 202 parser 已在真实链路中工作。旧 v3 基线已失效。

本轮使用工作区自带浏览器自动化能力控制本机已安装 Google Chrome，未新增仓库依赖：

1. 打开 `/review/new`；
2. 选择既有 `CQCP-MVP-DOCX-001` 并填入 Demo 结构化字段；
3. 点击“提交审核”，浏览器真实收到 HTTP 202/QUEUED；
4. 前端从响应解析 taskId、executionId 和 resultUrl，进入精确状态路由；
5. 轮询依次观察到 `QUEUED/QUEUED`、`PROCESSING/PARSING`、
   `SUCCESS/SUCCESS`；
6. 自动进入同源正式结果路由，结果为 9/9 PASS、17 个结构化字段、8 个
   SourceAnchor。

本次 identity 为 `TASK_0b5335afd3b4423ba5485c94a029ce3c` /
`EXEC_be1954f494dd4d19b73d33b9801cd710`。Nginx 访问日志证明 POST、三次 status
GET 和 result GET 均来自同一 Chrome user-agent；PostgreSQL 只读核对确认
Task、Execution、12 条阶段日志和 Snapshot identity 一致。

新增证据：

- `raw/f2-browser-creation-chain.txt`
- `raw/f2-browser-service-log.txt`
- `raw/f2-browser-database.txt`
- `screenshots/05-f2-browser-new.png`
- `screenshots/06-f2-browser-filled.png`
- `screenshots/07-f2-browser-status.png`
- `screenshots/08-f2-browser-result.png`

v4 代码审计随后发现 `run-manifest.json` 顶层 `capturedAt` 仍早于新增 Chrome run，
构成证据元数据 P2。旧 v4 基线立即失效；顶层捕获时间已更新为所有已纳入证据完成之后，
并将重算 manifest、完整 diff 和双重审计。

## F3 PR #35 Linux upload-root 测试可移植性修复

完成态 commit `1dbef9c` 的 PR #35 backend CI 在 313 项中有 3 项失败。三项均未进入
业务断言，而是在 Linux runner 构造 symlink/parser-failure fixture 时因测试辅助方法
硬编码 `/data/cqcp/uploads` 抛出 `NoSuchFileException` / `AccessDeniedException`。
应用真源 `cqcp.review.upload-root` 在未设置环境变量时使用 `./data/uploads`，因此
测试 fixture 与应用实际根不一致；Windows 本地还可能因文件缺失而形成 fail-closed
假阳性。

F3 仅修改 `SingleReviewWorkerIntegrationTest`：注入同一个 Spring
`cqcp.review.upload-root` property，并将其规范为绝对路径。验证结果：

- host 定向 `SingleReviewWorkerIntegrationTest`：11/11；
- host backend 全量：313/313；
- Linux `gradle:8.10.2-jdk21`、只读依赖缓存、offline 定向：11/11；
- 一次在线 Linux 依赖下载在进入测试前因 `Illegal packet size` 失败，原样记录且
  不作为通过证据。

原始 console 见 `raw/f3-host-targeted.txt`、`raw/f3-host-full.txt`、
`raw/f3-linux-targeted-stdout.txt`、`raw/f3-linux-targeted-stderr.txt` 与
`raw/f3-linux-online-dependency-failure.txt`；交叉核对摘要见
`raw/f3-verification-summary.txt`。F3 未修改生产代码、workflow、OpenAPI、
migration、Docker Compose 或既有真实 Demo 结果；代码变化使此前 v5 与完成态
增量审计基线失效，必须重新冻结并完整重跑双重独立审计。
