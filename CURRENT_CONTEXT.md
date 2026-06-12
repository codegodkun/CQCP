# CURRENT\_CONTEXT.md

更新日期：2026-06-12

## 1\. 当前阶段

项目已完成 `TASK-006` 纯技术脚手架环境验证、`TASK-015` Flyway V1 初始化迁移基线（含 PostgreSQL 15 真实容器回放）、`TASK-017` 首批 expected fixtures bootstrap、`TASK-016` 的 4 个开发前验证最小闭环，以及 `TASK-018` 的最小确定性 `Review Engine` 验证收口。当前阶段已从“前置验证”推进到“可生成真实点级审核结果”，下一优先工作转为补齐 `Result Composer + ReviewResultSnapshot` 合成与最小持久化任务。

## 2\. 当前关键结论

* MVP 第一轮只支持单份中文 .docx 工程采购合同。
* 首批 9 个 core review point 已冻结。
* 技术栈、仓库结构、领域模型和结果/诊断契约已冻结。
* TASK-004、TASK-005、TASK-014 已完成。
* TASK-006 已完成纯技术脚手架，已建立 `apps/admin-web`、`apps/api-server`、`deploy/*`、`packages/review-assets` 和 `packages/test-fixtures` 骨架。
* TASK-015 已完成 Flyway V1 核心迁移，冻结 `task / execution / task_stage_log / review_result_snapshot / tuning_packet / point_diagnostic` 六类数据库基线对象，并已在真实 `PostgreSQL 15.18` 容器中完成 `V1__cqcp_mvp_core_schema.sql` 回放验证。
* TASK-016 已冻结首轮验证顺序为“样例准备与前置门槛 -> Word parser Spike -> 模型客户端契约测试 -> AI 调优包导出验证 -> 管理台点级诊断与执行摘要验证”。
* TASK-017 已完成首批 `expected/*.json` 夹具落地，当前 4 份 `.docx` 样例、`cqcp-mvp-sample-matrix.xlsx` 与 4 份 expected 夹具已建立一一映射。
* 最小 OpenAPI 契约已生成到 packages/api-contracts/。
* 后续仍坚持证据不足不生成业务 finding，Gemma 只做局部辅助，后端最终裁判。
* 当前脚手架补充验证结果为：目录与 Compose 入口已建立，`openapi.json` 可完成本地 JSON 解析；前端依赖已安装，`apps/admin-web` 的 `build/lint` 已通过，`test` 在提权条件下通过、默认沙箱下仍受目录读取权限模型阻塞；后端 `apps/api-server` 的 `gradle test/build` 已在提权条件下通过，本机 Java/Gradle/Flyway 应用层验证通过。
* 数据库基线已新增 `ADR-013-v1-core-schema-bootstrap`，确认 Flyway 目录、V1 核心表范围、JSONB 落地策略和最小索引策略。
* `packages/test-fixtures/` 已落地 4 份 `.docx` 样例、`cqcp-mvp-sample-matrix.xlsx` 和 4 份 `expected/*.json`；当前已形成“样例文件 -> matrix -> expected 夹具”的第一批可执行测试基线。
* `apps/api-server` 已新增最小 `DocxWordParserSpike`：使用 Apache POI 读取 `.docx`，产出 `DocumentBlock / TableBlock / FormControlBlock / ParseQualityReport`，并通过 4 份样例的聚合验证覆盖标题、表格行、附件区域、符号勾选与 provenance 字段。
* `apps/api-server` 已新增最小 `ModelGateway` 契约层与 `MOCK` provider：覆盖 `ModelCallIntent` 最小字段透传、mock success、schema invalid、timeout、unavailable、conflict 的 `SYS-MODEL-*` 失败映射，且验证结果只停留在诊断层，不生成业务 finding。
* `apps/api-server` 已新增最小 `TuningPacket` 导出验证层：覆盖 `SINGLE_POINT` 与 `FOCUSED` 两种导出模式、版本引用写入、诊断摘要导出以及对 `FULL_CONTRACT_TEXT / FULL_PROMPT / FULL_RAW_OUTPUT / ENDPOINT_SECRET / STACK_TRACE` 的排除约束。
* `apps/api-server` 已新增最小管理台诊断摘要契约：覆盖 `taskId / executionId / stage / 审核模型 / 点级摘要计数 / 点级状态 / 阶段日志 / 摘要级 SYS-*` 展示边界，并显式排除 `promptPreview / rawOutput / endpointSecret / stackTrace`。
* `apps/admin-web` 已新增最小静态诊断摘要预览组件，只验证管理台概览、点级状态、阶段日志和诊断码展示，不做真实业务联调、复杂路由或完整交互。
* `TASK-016` 的 4 个最小验证主题现已全部具备可执行验证结果。
* `apps/api-server` 已新增最小 `reviewengine` 包，基于 4 份 expected fixtures 的 `goldenExpected` 作为合同侧最小证据基线，落地：
  * `PointReviewResult[]`
  * `ReviewSummary`
  * `ReviewCompleteness`
  * `ReviewResultSnapshotDraft`
  * `PointDiagnostic[]`
* `TASK-018` 已验证首批 9 个审核点的最小确定性裁判，并覆盖 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED` 与 `SYS-* -> NOT_CONCLUDED`。
* 当前 `Review Engine` 仍未接入真实 parser/candidate/evidence 主链路；source anchor 仍是最小摘要对象，不等同正式 block/row/cell 原文定位。
* 下一优先实现主线已更新为：新建并执行 `TASK-019 Result Composer + Snapshot`，随后再推进 `TASK-020 Task Execution 最小状态机`；当前不优先扩展 parser、真实模型调用或完整管理台页面。

## 3\. 当前活跃任务

* `TASK-018-review-engine-minimal-validation` 已完成，当前待新建下一优先任务 `TASK-019 Result Composer + Snapshot`。

## 4\. 当前阻塞点

* `apps/admin-web` 的 `vitest` 在 Codex 默认沙箱下仍会被目录读取权限拦截；前端 `test` 虽已在提权条件下通过，但默认执行环境仍不是稳定基线。
* Codex 默认会话仍不会自动继承本机 `JAVA_HOME/PATH`，且默认沙箱网络受限；后续凡涉及 Gradle 远程依赖解析或前端目录权限问题，仍需显式提权或等效执行环境。
* 本机 `docker` CLI 访问 `C:\Users\1\.docker\config.json` 仍存在权限警告；当前虽不影响已完成的 PostgreSQL 回放验证，但后续若进入镜像构建、容器联调或 compose 启停，仍需复核本地 Docker 配置可持续使用性。
* 当前没有新的开发前验证主题阻塞；主要风险已转为如何在不扩大 `Review Engine` 输入边界的前提下，把当前内存态 `ReviewResultSnapshotDraft` 平滑衔接到后续 `TASK-019` 的结果合成与最小持久化结构。

## 5\. 下一步顺序

1. 新建并执行 `TASK-019`，把当前 `Review Engine` 输出合成为不可变 `ReviewResultSnapshot` 草案/最小持久化结构。
2. `TASK-019` 完成后，再推进 `TASK-020`，补齐最小 `Task Execution` 阶段状态机。
3. 在 `TASK-019` / `TASK-020` 期间继续保留当前边界：不反向扩展为真实 parser 正式抽取、复杂 CandidateResolver 或真实模型调用。
4. 继续保留执行环境注意事项：前端 `npm test` 在 Codex 默认沙箱下仍需提权或等效执行环境；Gradle 相关验证仍需显式设置 `JAVA_HOME/PATH`。

## 6\. 当前禁止推进

* 不开始业务功能编码。
* 不提前引入 Pilot / Production Readiness 能力。
* 不做 SAP/OA 深度 correction 联调。
* 不做复杂权限、完整质量治理、自动调优、自动发布。
* 不把公网 AI 建议直接接入生产审核链路。

## 7\. 长期记忆索引

* PRD.md：产品范围与 MVP 冻结基线。
* docs/ARCHITECTURE.md：完整架构设计。
* context\_history.md：长期项目记忆 / 历史记录。
* ADR-010：技术栈冻结。
* ADR-011：仓库结构冻结。
* ADR-012：领域模型冻结。
* ADR-013：V1 核心数据库 schema 基线。
* TASK-004：Word parser MVP 边界。
* TASK-005：ModelGateway 与预算基线。
* TASK-006：纯技术脚手架基线。
* TASK-015：Flyway V1 初始化迁移基线。
* TASK-016：MVP 开发前验证基线。
* TASK-017：首批 expected fixtures bootstrap。
* TASK-018：Review Engine 最小验证闭环，已完成最小确定性点级裁判与 Snapshot draft 对象基线。
* TASK-014：最小 OpenAPI 契约。

更新日期：2026-06-12

补充事实（Codex execution environment escalated verification）：
- 已重建干净测试库 `cqcp_app_test`。
- 针对 `apps/api-server` 的 `gradle test` 已按要求构造提权命令，并显式设置 `JAVA_HOME`、`PATH` 与 `CQCP_DB_*`。
- `require_escalated` 审批链路本轮两次均超时，返回 `The automatic permission approval review did not finish before its deadline`。
- 因提权命令未实际执行，本轮没有新增 `gradle test/build` 成功或失败事实；该状态应记录为 Codex 执行环境提权验证阻塞，而不是 CQCP 后端工程失败。
- 目前已确认的根因仍是：Codex 默认沙箱 `network restricted`，默认环境下无法完成 Gradle 插件远程解析；提权后的恢复性验证本轮未拿到执行结果。
更新补充（2026-06-12）：
- Codex 提权审批链路在客户端重启后恢复可用。
- 在 `apps/api-server` 下，显式设置 `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`、前置 `C:\Gradle\gradle-8.10.2\bin` 到 `PATH`，并设置 `CQCP_DB_URL=jdbc:postgresql://localhost:54329/cqcp_app_test`、`CQCP_DB_USERNAME=postgres`、`CQCP_DB_PASSWORD=postgres` 后，以 `require_escalated` 执行：
  - `gradle test` -> `BUILD SUCCESSFUL in 6s`
  - `gradle build` -> `BUILD SUCCESSFUL in 1s`
- 现可确认：后端本机 Java/Gradle/Flyway 应用层验证通过；Codex 默认失败根因是沙箱网络限制与默认会话未继承 `JAVA_HOME/PATH`，不是 CQCP 后端工程失败。
- 当前剩余环境阻塞项主要为前端 `apps/admin-web` 的 `npm test`，其 `Vitest/esbuild` 启动阶段仍受当前执行环境目录读取权限模型影响。
更新补充（2026-06-12，frontend test scaffold verification）：
- `apps/admin-web` 新增 Vitest 全局 setup：`apps/admin-web/src/test/setup.ts`，用于统一注入 `@testing-library/jest-dom/vitest` 与 `window.matchMedia` mock。
- `apps/admin-web/vite.config.js` 已补充 `test.setupFiles` 指向该 setup 文件；`apps/admin-web/src/App.test.tsx` 已移除重复初始化导入。
- 默认沙箱下执行 `apps/admin-web` 的 `npm.cmd run test` 仍失败于目录读取权限阻塞，核心错误仍为：
  - `Cannot read directory "../../../.."`: Access is denied
  - `Could not resolve "...\\apps\\admin-web\\vite.config.js"`
- 以 `require_escalated` 执行前端 `npm test` 后，目录权限阻塞消失，测试进入真实运行阶段；补齐 `window.matchMedia` 后，前端测试已通过。
- 当前环境级结论应更新为：
  - 后端 `gradle test/build` 在提权条件下通过
  - 前端 `build/lint` 通过
  - 前端 `test` 在提权条件下通过，但默认沙箱下仍为环境权限阻塞
更新补充（2026-06-12，TASK-006 closeout review）：
- `TASK-006` 现可视为 scaffold 环境级验证收口完成。
- 最终确认结果为：
  - 后端 `apps/api-server`：`gradle test/build` 在提权条件下通过，可确认本机 Java/Gradle/Flyway 应用层验证通过。
  - 前端 `apps/admin-web`：`build/lint` 通过，`test` 在提权条件下通过。
  - 前端 `test` 在 Codex 默认沙箱下仍受目录读取权限模型阻塞，但该问题属于执行环境限制，不再视为 CQCP scaffold 工程阻塞。
- 后续从 `TASK-006` 切换到下一任务时，应把“默认沙箱下前端 test 仍需提权或等效执行环境”作为操作注意事项保留。
