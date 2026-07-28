# TASK_SPEC-MVP-001-F2 双重审计首轮 finding 修复

## 基本信息

- 父任务：`TASK-MVP-001-contract-review-user-loop-demo`
- Task Level：L2 Feature 内定点修复
- 状态：`DONE / ARCHIVED_WITH_FEATURE / DUAL_AUDIT_GO / FEATURE_PR_PENDING`
- 触发来源：Codex subagent 首轮审计 `NO-GO`
- 失效基线：index tree `c3f2c7a12e66d83351f822869d3c379c1c3478c3`

## Required Context

- `AGENTS.md`
- 父 TASK、E/F/F1 规格
- `apps/admin-web/src/reviewCreation/api.ts`
- `apps/admin-web/src/publicResult/PublicResultPage.tsx`
- 对应 creation/App tests
- `outputs/task-mvp-001-demo-acceptance/`

## Out of Scope

- 后端、OpenAPI、migration、Worker、审核语义、fixture/expected、TASK-034/C2
- 新依赖或新 E2E 框架

## 冻结 findings

1. 创建响应允许 whitespace-only `taskId/executionId` 和空/空白 `resultUrl`。
2. 正式结果路由对 query `executionId` 静默 trim，非规范 URL 可被当作精确 identity。
3. F 证据只保存人工摘要，未冻结测试、Compose、健康检查和目标 HTTP 日志原始输出。

## 允许修改

- creation API validator 与对应测试
- PublicResultPage 正式 identity 校验与对应 App 测试
- F/F2/父任务报告
- `outputs/task-mvp-001-demo-acceptance/raw/**`、manifest 和摘要

## 可证伪验收

1. 202 success 仅接受 trim 后非空且原值已规范的 task/execution identity；resultUrl
   必须 trim 后非空。
2. `?executionId=%20EXEC%20` 直接显示地址无效且不请求 result API。
3. 新增失败测试先在旧实现复现，再随修复通过。
4. 冻结原始文件至少包含 backend 两轮强制回归、admin-web test/lint/build、
   review-assets validator、Compose config/build/ps/health、目标 202/status/result 日志；
   每个文件记录命令、退出码和 stdout/stderr，并进入 manifest SHA-256。
5. admin-web 全量、backend 全量、validator、build、diff check 与受影响真实浏览器路径通过。

## 实施报告

### Red / Green

- 先新增 4 组创建响应畸形用例和 1 组正式结果 query 非规范 identity 用例。
- 修复前定向执行为 `2 failed / 31 passed`：
  whitespace-only `taskId` 被当作成功响应；`%20EXEC%20` 被 trim 后发起结果请求。
- 修复后定向执行为 `33/33`；admin-web 全量为 `63/63`。
- 创建响应仅接受原值已规范且非空的 task/execution identity；`resultUrl` 必须
  trim 后非空。正式结果 query 不再静默 trim，非规范 identity 直接 fail closed。

### 完整回归与真实路径

- 后端使用仓库 Gradle 8.10.2、同一 Compose PostgreSQL test DB 连续两轮强制
  全量回归，均为 `313/313`。
- Compose test profile 的一次依赖下载在测试执行前因外部仓库 `Tag mismatch`
  失败；该原始失败与后续两轮成功均已保留，不用网络失败替代测试结论。
- admin-web `63/63`、lint、production build、review-assets validator `7/7`
  全部通过。
- Compose config 与包含本修复的 `up -d --build` 通过；PostgreSQL healthy，
  API `UP`，admin-web HTTP 200。
- 重建后定点创建返回 HTTP 202：
  `TASK_cc6250cce538441195efe630c220f9c5` /
  `EXEC_e6755d0dd53e44dab7dae9032784fe7b`；状态为 `SUCCESS`，真实浏览器从精确
  状态路由自动进入同源正式结果路由，结果保持 `9 PASS / FULL_REVIEWED`。
- PostgreSQL Task、Execution、stage logs、Snapshot 与上传文件均存在，
  identity 一致，保持 `v20260705.1 / MVP_DEMO_MOCK`。

### 证据补强

- `outputs/task-mvp-001-demo-acceptance/raw/` 保存测试、Compose、HTTP、数据库和
  浏览器 DOM 的命令、退出码与原始输出。
- 原始首次排队 DOM 已固化，明确展示 task/execution identity、`排队中`、
  `MVP_DEMO_MOCK / MOCK` 和 1 秒轮询提示。
- 所有 raw 文件将在新冻结 manifest 中记录 SHA-256。

### 第二次冻结前证据补正

- v2 Codex 复审发现 `revalidation-database.txt` 的 `wc -c` stdout 固化了容器
  绝对上传根，违反 F 的证据脱敏门禁；该 v2 tree 立即失效，CC AUDIT 未发送。
- 文件存在性与大小检查已改为只输出 `90276`，命令中的根路径和 documentReference
  均使用受控 placeholder；证据目录全文检索不再包含绝对上传根或用户目录。
- 为避免只依赖实现报告自述，使用 `HEAD@439a020` 的旧实现与 staged F2 tests
  在独立临时副本重现 `EXIT_CODE=1 / 2 failed / 31 passed`；当前实现相同定向用例
  原始输出为 `EXIT_CODE=0 / 33 passed`。两个 stdout/stderr 均已脱敏并冻结到
  `raw/f2-red-reproduction.txt` 与 `raw/f2-green-targeted.txt`。
- 同轮修正 `CURRENT_CONTEXT.md` 中遗漏 F2 的归档清单和 TASK-037 已合并事实。

### 第三次冻结前审计补正

v3 Codex 代码审计继续判定 `NO-GO`，旧 v3 基线立即失效，CC AUDIT 仍未发送。
本轮完成三项定点补正：

1. B/D/E 顶部状态真源已改为实现接纳并提交；B 的编码前规格结论明确标记为历史记录，
   E 补充实际 commit `539c9e6d026fcac112831118022b451e7632c40c`。
2. 新冻结不再只比较 `HEAD -> index`，而是以 Feature base
   `401fd05b7a6c23014adb4f5511533467016c37ba -> index` 生成完整 changed-path
   allowlist、SHA-256 manifest 和 binary full diff。
3. F2 受影响创建链路已在安装的真实 Google Chrome 中重新执行：
   `/review/new` 上传 fixture、点击提交、浏览器收到 HTTP 202/QUEUED，前端解析
   task/execution/resultUrl 后进入精确状态路由，依次轮询
   `QUEUED -> PROCESSING/PARSING -> SUCCESS`，并自动进入同源结果路由。

本次浏览器 run identity：

```text
taskId: TASK_0b5335afd3b4423ba5485c94a029ce3c
executionId: EXEC_be1954f494dd4d19b73d33b9801cd710
POST: 202
result: SUCCESS / 9 PASS / 17 structured fields / 8 anchors
```

浏览器原始链路、Nginx Chrome user-agent 访问日志、只读 PostgreSQL 摘要和四张页面截图
已保存到 `outputs/task-mvp-001-demo-acceptance/`，并将进入新冻结 manifest。

v4 代码审计发现一项证据元数据 P2：`run-manifest.json` 顶层 `capturedAt` 早于同文件
新纳入的 Chrome run。旧 v4 基线立即失效；顶层时间已更新为所有证据完成之后，
run-manifest hash、完整 Feature diff 和冻结 manifest 将全部重算，随后两条独立审计
均从新基线重跑。

Codex Review Intake：`ACCEPT_F2 / GO_TO_REFREEZE_AND_FULL_DUAL_REAUDIT`。
旧 index tree `c3f2c7a12e66d83351f822869d3c379c1c3478c3` 永久失效。

### 最终 v5 冻结与双重审计

- Feature base：`401fd05b7a6c23014adb4f5511533467016c37ba`。
- HEAD：`439a02012c54f11577c6e916591d69db2a7d5d58`。
- index tree：`dc81a8c3d2636844db7e7e85748ca507720323fd`。
- base → index changed paths：102；tracked unstaged：0；`git diff --cached --check`
  通过。
- full diff SHA-256：
  `D60D11D81D6FBEEBE29FF32553B2A6159853820C988D8A8278F605AD559BC37F`。
- freeze manifest SHA-256：
  `E7F6A67D36F253FFFC3704DE5E7BFF128DE8E64798FCCCA45995B61022D92C4E`。
- 两个 Codex 独立只读 subagent 均为 `GO`，P0/P1/P2 blocker 为 0；
  `CODEX_SUBAGENT_AUDIT=GO`。
- CC AUDIT 第三方只读审计为 `CC_AUDIT=GO`，`P0=0 / P1=0 / P2=0`，
  blocking findings 为 0。
- 最终门禁已满足：
  `CC_AUDIT=GO AND CODEX_SUBAGENT_AUDIT=GO AND blocking findings=0`。

Codex 最终 Review Intake：
`ACCEPT_F2 / DUAL_AUDIT_GO / GO_TO_FEATURE_COMMIT_PR_CI_MERGE`。
