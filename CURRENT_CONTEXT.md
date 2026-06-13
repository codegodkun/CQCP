# CURRENT\_CONTEXT.md

更新日期：2026-06-13

## 1\. 当前阶段

项目已完成 `TASK-006` 纯技术脚手架环境验证、`TASK-015` Flyway V1 初始化迁移基线（含 PostgreSQL 15 真实容器回放）、`TASK-017` 首批 expected fixtures bootstrap、`TASK-016` 的 4 个开发前验证最小闭环、`TASK-018` 的最小确定性 `Review Engine` 验证收口，以及 `TASK-019` 的正式最小 `Result Composer + ReviewResultSnapshot` 合成。当前阶段已从“可生成真实点级审核结果”推进到“可合成正式最小结果快照”。`INFRA-001` 正在把本地开发、验证和测试路径收口为 Docker Compose 唯一标准；在 Docker 标准环境阻塞解除前，不应继续推进 `TASK-020` 业务实现。

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
* `apps/api-server` 已新增最小 `ResultComposer`，可将 `PointReviewResult[] / ReviewSummary / ReviewCompleteness / PointDiagnostic[]` 合成为正式最小 `ReviewResultSnapshot` 内存对象，并显式产出：
  * `findings`
  * `diagnostics`
  * `sourceAnchors`
  * `enabledReviewPointsSnapshot / disabledReviewPointsSnapshot`
  * 最小版本引用字段
* `TASK-019` 已冻结并实现点级正式结果到 `ReviewResultSnapshot` 的最小映射边界：`ERROR / WARNING` 进入业务 `findings`，`PASS / NOT_CONCLUDED / SKIPPED` 不进入业务风险统计，`SYS-*` 仍只保留在 `diagnostics`。
* 当前 `Review Engine` 仍未接入真实 parser/candidate/evidence 主链路；source anchor 仍是最小摘要对象，不等同正式 block/row/cell 原文定位。
* 当前 `ResultComposer` 只完成正式最小快照内存对象合成，尚未进入数据库持久化 adapter、结果读取 API 或状态机衔接。
* `INFRA-001` 已将 CQCP 标准 Docker Compose 口径收口为：`COMPOSE_PROJECT_NAME=cqcp`，前端宿主机端口 `15173`，后端宿主机端口 `18080`，PostgreSQL 宿主机端口 `54329`，Compose 网络名 `cqcp_default`，PostgreSQL named volume `cqcp_postgres_data`。
* `INFRA-001` 已明确 Docker Compose 是 CQCP 唯一标准开发、验证、测试方式；非 Docker 启动仅允许临时故障定位，不作为验收依据。
* 当前 `Review Engine` 后续实现主线仍是 `TASK-020 Task Execution 最小状态机` 与 `TASK-021 Result URL 查询接口最小实现`，但在 `INFRA-001` Docker 阻塞解除前暂不推进。

## 3\. 当前活跃任务

* `INFRA-001-docker-standard-dev-environment` 进行中，当前状态为阻塞收口：旧 `cqcp-postgres-test` 已确认为空历史测试容器并删除，Compose 配置静态验证通过，但标准启动阻塞于 Docker Hub 基础镜像拉取。

## 4\. 当前阻塞点

* 旧测试容器 `cqcp-postgres-test` 已只读检查：旧库 `cqcp_test` 仅有 V1 基线 6 张表，且表行数均为 0；已停止并删除旧容器，未删除 Docker volume。
* Docker Compose 标准启动当前阻塞于 Docker Hub 拉取 `gradle:8.10.2-jdk21` 和 `node:20-alpine`，失败原因为获取 Docker Hub 匿名 token 超时。应优先处理镜像源、代理、镜像加速器或内部镜像仓库，不得继续推进业务 TASK。
* `apps/admin-web` 的 `vitest` 在 Codex 默认沙箱下仍会被目录读取权限拦截；前端 `test` 虽已在提权条件下通过，但默认执行环境仍不是稳定基线。
* Codex 默认会话仍不会自动继承本机 `JAVA_HOME/PATH`，且默认沙箱网络受限；后续凡涉及 Gradle 远程依赖解析或前端目录权限问题，仍需显式提权或等效执行环境。
* 本机 `docker` CLI 访问 `C:\Users\1\.docker\config.json` 仍存在权限警告；当前虽不影响已完成的 PostgreSQL 回放验证，但后续若进入镜像构建、容器联调或 compose 启停，仍需复核本地 Docker 配置可持续使用性。
* 当前主要阻塞已从业务实现前置验证转为 Docker 标准环境收口；`TASK-020` 暂缓。
* 本轮 `gradle build` 的唯一失败点是 `CqcpApiServerApplicationTests` 启动阶段无法连接 PostgreSQL；执行时 `docker ps` 为空，说明本地测试库容器未运行。该问题属于当前执行环境/本地依赖状态，不是 `TASK-019` 新增 composer 代码的编译失败。

## 5\. 下一步顺序

1. 处理 Docker Hub 基础镜像拉取阻塞：`gradle:8.10.2-jdk21` 与 `node:20-alpine`。
2. 镜像拉取阻塞解除后，继续验证 `docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml up -d --build`。
3. Docker Compose 标准环境启动、`api-server` 健康检查和 `admin-web` 访问均通过后，再恢复 `TASK-020`。

## 6\. 当前禁止推进

* 不开始业务功能编码。
* Docker Compose 标准环境未通过前，不进入 `TASK-020`。
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
* TASK-019：Result Composer + ReviewResultSnapshot 最小合成，已完成正式最小快照对象、findings/diagnostics/sourceAnchors 合成与版本引用占位。
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
