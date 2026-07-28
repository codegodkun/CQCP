# TASK_SPEC-MVP-001-F3：Linux upload-root 测试可移植性修复

## 基本信息

- 父任务：`tasks/done/TASK-MVP-001-contract-review-user-loop-demo.md`
- Task Level：L2 Feature 内 CI 定点修复
- 状态：`COMPLETED / VERIFIED / V9_DUAL_AUDIT_GO / PR_35_MERGED / CI_GREEN`
- 触发来源：PR #35 `Backend Gradle tests`
- 失败 run：`30326662660`
- 失败 job：`90173392371`
- 失败 head：`1dbef9cdbcb0e98996c58b262d1b668f0d06941e`

## Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `tasks/done/TASK-MVP-001-contract-review-user-loop-demo.md`
- `.github/workflows/ci.yml`
- `apps/api-server/src/main/resources/application.yml`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/SingleReviewWorkerIntegrationTest.java`

## Out of Scope

- 不修改业务代码、Worker、上传存储实现、OpenAPI、数据库、migration、Docker 或 workflow。
- 不改变 AC13 fail-closed 语义。
- 不运行或改写 TASK-034，不声明 57/57 coverage 或 Production Ready。
- 不激活 `v20260715.1` 或 TASK-036-C2。

## 失败事实与根因

GitHub Actions/Linux 在 313 项 backend 测试中有 3 项失败：

- `ac13_finalSymlink_failsClosed()`：fixture 构造阶段 `NoSuchFileException`；
- `ac13_parserFailure_failsClosed()`：fixture 构造阶段 `AccessDeniedException`；
- `ac13_parentDirSymlink_failsClosed()`：fixture 构造阶段 `AccessDeniedException`。

测试辅助方法 `uploadRoot()` 在未设置 `CQCP_UPLOAD_ROOT` 时硬编码
`/data/cqcp/uploads`，但应用真源 `cqcp.review.upload-root` 的默认值是
`./data/uploads`。Linux runner 无权创建 `/data/cqcp/uploads`；Windows 本地还会把
fixture 写到与应用不同的根，导致 fail-closed 用例可能因“文件不在应用根”而假阳性。

## 允许修改

- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/SingleReviewWorkerIntegrationTest.java`
- 本规格、父 TASK、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、
  `changelog/2026-07.md`
- 如需保存新的脱敏测试证据，仅允许
  `outputs/task-mvp-001-demo-acceptance/raw/`

## 可证伪验收

1. 测试 fixture 根必须来自与应用相同的 Spring property
   `cqcp.review.upload-root`，不得再自行硬编码环境默认值。
2. 三个 AC13 用例在未设置 `CQCP_UPLOAD_ROOT` 的环境可构造真实 fixture 并通过。
3. `SingleReviewWorkerIntegrationTest` 定向全量通过。
4. backend 全量 313/313 通过。
5. `git diff --check` 通过，业务代码、workflow、migration、OpenAPI 与现有 Demo
   原始证据不变。
6. 由于完成态 commit 后发生代码变化，v5/完成态增量审计基线失效；修复验证后必须
   重新冻结，并重新执行 CC AUDIT 与两个 Codex 独立只读 subagent 审计。双重 GO
   前不得 push 修复 commit 或 merge PR #35。

## 实施

- 向测试类注入 `@Value("${cqcp.review.upload-root}") Path configuredUploadRoot`。
- `uploadRoot()` 返回该属性的绝对规范路径。
- 未修改生产实现或 AC13 断言。

## 验证结果

- host、未设置 `CQCP_UPLOAD_ROOT`：
  `SingleReviewWorkerIntegrationTest` 11/11，exit 0。
- host、未设置 `CQCP_UPLOAD_ROOT`：backend 全量 313/313，exit 0。
- Linux `gradle:8.10.2-jdk21`、未设置 `CQCP_UPLOAD_ROOT`、只读依赖缓存、
  offline：`SingleReviewWorkerIntegrationTest` 11/11，exit 0。
- 一次 Linux 在线依赖下载在 compile 前因 Maven Central
  `Illegal packet size: 42592` 失败，未进入测试且不作为通过证据。
- 原始 console：
  `outputs/task-mvp-001-demo-acceptance/raw/f3-host-targeted.txt`、
  `f3-host-full.txt`、`f3-linux-targeted-stdout.txt`、
  `f3-linux-targeted-stderr.txt`、`f3-linux-online-dependency-failure.txt`。
- 交叉核对摘要：`raw/f3-verification-summary.txt`。
- `git diff --check` 通过；生产代码、workflow、OpenAPI、migration、Compose 与
  既有 Demo 证据内容未改变。

Codex Review Intake：
`ACCEPT_F3_IMPLEMENTATION / GO_TO_REFREEZE_AND_FULL_DUAL_REAUDIT`。

## v6 审计 NO-GO 与证据补正

v6 代码审计为 `NO-GO / P1=1 / P2=1 / blocking=2`：

1. F3 仅冻结人工交叉核对摘要，未冻结 host targeted/full、Linux offline green
   与 Linux online dependency failure 的原始 stdout/stderr；
2. 父 TASK 的 v5 完成记录未明确标为历史失效，仍残留“PR 尚未创建”等冲突叙事。

补正：

- 重新执行 host targeted 11/11 与 host full 313/313，并固化完整 console；
- 重新执行 Linux offline targeted 11/11，分别固化 stdout/stderr 与 XML summary；
- 固化 Linux online 依赖下载失败的完整 console，明确未进入测试、不作为通过证据；
- 将父 TASK 的 v5 完成段标为已被 PR #35 CI 失效，并把当前 Handoff 改为 F3
  证据补正、重新冻结与完整双审计。

v6 冻结基线永久失效；补正后的新基线必须让 CC AUDIT 与两个 Codex subagent
全部从零重跑。

## v7 审计 NO-GO 与状态元数据补正

v7 两个 subagent 均为 NO-GO，合并后有 3 个 blocking findings：

1. `CURRENT_CONTEXT.md` 首段仍写 v5 的“双审计完成、只待 PR/CI/merge”；
2. 父 TASK Handoff 仍写“补齐原始证据、重新冻结”，落后于已完成的 v7 冻结；
3. `f3-verification-summary.txt` 的捕获时间早于其汇总的 12:38~12:41 原始
   console。

补正：

- 当前阶段首段明确 PR #35/F3/v7 NO-GO 与双重审计待重跑；
- 父 TASK Handoff 明确代码和原始证据已完成，下一步为新基线完整双审计；
- F3 summary 捕获时间对齐 run-manifest 的 `2026-07-28T12:44:21+08:00`。

v7 冻结基线永久失效；下一基线必须让两名 Codex subagent 与 CC AUDIT 全部从零
重跑。

## v8 审计 NO-GO 与下一步真源补正

v8 两个 subagent 均为 `NO-GO / P2=1`：`CURRENT_CONTEXT.md` 的“下一步”仍把
已经完成的 F3 实现、定向测试与 backend 全量测试列为待办。该过期步骤已删除，
当前下一步直接从新基线冻结与完整双审计开始。

v8 其余核验全部通过：109/109 paths、43/43 evidence hashes、五份 F3 原始
console、test-only diff、AC13、安全边界、Compose/Chrome/PostgreSQL 与 A~F
全范围均无新增 finding。v8 基线永久失效。

## v9 双重独立审计

- 冻结基线：base `401fd05b7a6c23014adb4f5511533467016c37ba`、HEAD
  `1dbef9cdbcb0e98996c58b262d1b668f0d06941e`、index tree
  `c37c328841be41a09b84c4a96056b0282c881f05`，109/109 changed-path hashes 与
  43/43 evidence hashes 匹配，tracked unstaged 为 0。
- Codex 代码/API/数据库/状态机审计：
  `CODE_AUDIT=GO / P0=0 / P1=0 / P2=0 / blocking=0`。
- Codex 测试/前端安全/Compose/真实 Demo 审计：
  `TEST_SECURITY_AUDIT=GO / P0=0 / P1=0 / P2=0 / blocking=0`。
- CC AUDIT：
  `CC_AUDIT=GO / P0=0 / P1=0 / P2=0 / BLOCKING_FINDINGS=0`。
- 最终门禁已满足。Codex Review Intake：
  `ACCEPT_F3_IMPLEMENTATION / V9_DUAL_AUDIT_GO / GO_TO_COMMIT_PUSH_PR_CI_MERGE`。

## Git 集成完成事实

- F3 与归档写回提交为 `332d365e75b00102e8ce6a54df716024c7b9288c`，已 push。
- PR #35 最终 CI run `30333059261` 的三项检查全部成功；其中 backend job
  `90192109599` 已在 Linux 上验证修复。
- PR #35 于 `2026-07-28T05:56:19Z` 合并，merge commit 为
  `ca2798cd4db400f1fe512e2a13c0d40624929b7d`。
- F3 完成态为 `VERIFIED / V9_DUAL_AUDIT_GO / PR_35_MERGED / CI_GREEN`。
