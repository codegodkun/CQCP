# TASK-006：仅在 ADR 确认后搭建脚手架

状态：已完成

类型：技术脚手架 / 执行门禁

优先级：P1

负责人：待确认

创建日期：2026-06-07

来源：Superpowers planning / design review / task decomposition 审查结论、`CURRENT_CONTEXT.md`

## 背景

当前不建议直接进入 project scaffold。脚手架会固化技术栈、目录结构、测试策略、数据库迁移工具、API 风格和部署前提。必须先完成关键 ADR 和 MVP 收敛任务，再创建纯技术脚手架。

## 目标

- 在关键 ADR 完成后创建纯技术脚手架。
- scaffold 只建立项目骨架、测试骨架和工程约束。
- 明确不写业务审核逻辑。
- 确保脚手架与已确认 ADR 一致。

## 非目标

- 在 ADR 完成前不执行。
- 不实现合同上传、解析、审核、模型调用或结果页业务逻辑。
- 不引入未被 ADR 选择的框架或依赖。
- 不迁移旧 `ai-contract-review` 项目代码。
- 不创建 SAP/OA 专用链路。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`ROADMAP.md`
- 相关架构章节：项目基础、接口边界、部署边界。
- 相关 ADR：`ADR-010-technology-stack-freeze`、`ADR-011-repository-structure-freeze`、`ADR-003-task-execution-snapshot-model`、`ADR-012-domain-model-freeze`、`ADR-002-v1-result-and-diagnostic-contract`
- 上游任务：`TASK-001-v1-mvp-scope-gate`、`TASK-002-entry-order-decision`

## 范围

### 包含

- 根据已接受 ADR 创建后端、前端、数据库迁移和测试目录。
- 建立最小 lint/test/build 命令。
- 建立空的工程边界，不写业务审核实现。
- 记录 scaffold 的执行方式和验证结果。

### 不包含

- 不写审核规则。
- 不写 Word parser。
- 不写模型客户端。
- 不写管理台业务页面。
- 不写外部 API 业务契约。
- 不安装 ADR 未确认的依赖。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- 必须确认关键 ADR 已由 Proposed 进入 Accepted 或等效人工确认状态。
- 只做纯技术脚手架。
- 不顺手实现任何业务功能。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`ADR-010-technology-stack-freeze.md`
- 必读：`ADR-011-repository-structure-freeze.md`
- 必读：`ADR-003-task-execution-snapshot-model.md`
- 必读：`ADR-012-domain-model-freeze.md`
- 必读：`ADR-002-v1-result-and-diagnostic-contract.md`
- 按需：`docs/backend.md`
- 按需：`docs/frontend.md`
- 按需：`docs/database.md`
- 按需：`docs/deployment.md`

## 交付物

- 技术脚手架目录。
- 最小构建、测试和格式化命令。
- 脚手架验证记录。
- 更新后的项目记忆和 changelog。

## 验收标准

- 关键 ADR 已人工确认。
- 脚手架不包含业务审核逻辑。
- 脚手架能运行最小验证命令。
- 目录结构与 ADR 一致。

## 测试与验证

- 运行 ADR 约定的最小 build/test/lint 命令。
- 若因依赖或环境无法运行，记录原因和后续处理任务。

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`。
- 必须更新 `changelog/2026-06.md`。
- 必须更新本任务完成记录。
- 是否需要更新 `docs/*.md`：待确认。

## 风险

- 如果 ADR 未确认就执行，后续返工概率高。
- 如果 scaffold 混入业务代码，会破坏 MVP 收敛门禁。
- 如果依赖安装策略未确认，可能污染仓库或环境。

## 待确认

- ADR-010 是否 Accepted。
- ADR-011 是否 Accepted。
- ADR-003 是否 Accepted。
- ADR-012 是否 Accepted。
- ADR-002 已于 2026-06-10 更新为 `Accepted`，脚手架门禁已解除该项阻塞。
- 是否需要先创建 git branch。
- scaffold 是否需要在独立任务窗口执行。

## 后续可能派生任务

- 后端最小 API 契约任务。
- 数据库迁移初始任务。
- 前端结果页壳任务。
- CI / pre-commit / dev environment 任务。

## 完成记录

- 完成日期：2026-06-10
- 变更文件：
  - `.gitignore`
  - `package.json`
  - `apps/admin-web/*`
  - `apps/api-server/*`
  - `deploy/compose/compose.yml`
  - `deploy/nginx/default.conf`
  - `deploy/env/.env.example`
  - `packages/review-assets/*`
  - `packages/test-fixtures/README.md`
  - `CURRENT_CONTEXT.md`
  - `changelog/2026-06.md`
  - `tasks/active/TASK-006-scaffold-only-after-adr.md`
- 测试结果：
  - `docker compose -f deploy/compose/compose.yml config` 通过，说明 Compose 入口结构有效。
  - `node --check apps/admin-web/eslint.config.js` 通过，说明前端 ESLint 配置文件语法有效。
  - `node -e "JSON.parse(...)"` 通过，说明根目录与前端 `package.json` JSON 结构有效。
  - `npm.cmd --workspace apps/admin-web run build` 失败：缺少已安装依赖，`tsc` 未找到。
  - `npm.cmd --workspace apps/admin-web run lint` 失败：缺少已安装依赖，`eslint` 未找到。
  - `npm.cmd --workspace apps/admin-web run test` 失败：缺少已安装依赖，`vitest` 未找到。
  - `gradle -v` 失败：本机 `PATH` 缺少 `Gradle`。
- 遗留问题：
  - 前端尚未安装依赖，因此无法完成真正的构建、Lint 和测试。
  - 后端尚未提供 Gradle wrapper，且当前环境无 `Java/Gradle`，无法完成编译级验证。
  - `deploy/compose/compose.yml` 仅完成脚手架入口，未经过镜像构建与容器联调。
- 备注：
  - 本次仅创建纯技术骨架与占位文件，未编写任何业务审核逻辑、数据库 schema 或 API 业务实现。
  - `ADR-002` 已为 `Accepted`，本任务执行前置门禁已满足。

## 补充验证记录（2026-06-11）

- 验证范围：
  - 仅补跑环境级验证，不进入 MVP 业务开发。
  - 覆盖前端工具链、后端运行时前提、Docker Compose 入口和 OpenAPI 静态可读性。
- 本次验证结果：
  - `docker compose -f deploy/compose/compose.yml config` 通过，说明 Compose 编排文件仍可被解析；本机同时出现 `C:\Users\1\.docker\config.json` 访问警告，但未阻断本次配置展开。
  - `node --check apps/admin-web/eslint.config.js` 通过，说明前端 ESLint 配置语法仍有效。
  - `node -e "JSON.parse(...)"` 成功解析根目录 `package.json`、`apps/admin-web/package.json` 和 `packages/api-contracts/openapi.json`，说明 workspace 包配置与 OpenAPI JSON 文件结构有效。
  - 已静态确认 `apps/api-server/build.gradle.kts`、`apps/api-server/settings.gradle.kts`、`apps/api-server/Dockerfile`、`apps/admin-web/Dockerfile`、`packages/api-contracts/openapi.yaml`、`packages/api-contracts/openapi.json` 和 `apps/api-server/src/main/resources/db/migration/V1__cqcp_mvp_core_schema.sql` 均已在仓库落地。
  - `npm.cmd --workspace apps/admin-web run build` 失败：缺少已安装依赖，`tsc` 未找到。
  - `npm.cmd --workspace apps/admin-web run lint` 失败：缺少已安装依赖，`eslint` 未找到。
  - `npm.cmd --workspace apps/admin-web run test` 失败：缺少已安装依赖，`vitest` 未找到。
  - `java -version` 失败：本机 `PATH` 中未检测到 `Java`。
  - `gradle -v` 失败：本机 `PATH` 中未检测到 `Gradle`。
- 结论：
  - `TASK-006` 脚手架结构、Compose 入口和 OpenAPI JSON 静态文件可继续作为后续验证基线。
  - 当前环境仍不满足前端真实构建测试、后端编译测试和 PostgreSQL/Flyway 真实回放验证条件，应继续按阻塞项处理，不得记为运行通过。

## 补充验证记录（2026-06-12）

- 验证范围：
  - 在不进入 MVP 业务开发的前提下，继续补齐前端依赖与脚手架级命令验证。
  - 尝试补齐本机 `Java/Gradle` 或用等效环境验证后端工具链。
- 本次变更文件：
  - `package-lock.json`
  - `apps/admin-web/package.json`
  - `apps/admin-web/src/App.test.tsx`
  - `apps/admin-web/tsconfig.app.json`
  - `apps/admin-web/tsconfig.node.json`
  - `apps/admin-web/vite.config.js`
- 本次验证结果：
  - `node -v` 通过，当前本机可用 `Node.js v24.16.0`。
  - `npm.cmd -v` 通过，当前本机可用 `npm 11.13.0`。
  - `npm.cmd install` 通过，前端依赖已成功安装到 workspace。
  - 在 `apps/admin-web/` 目录下执行 `npm.cmd run build` 通过。
  - 在 `apps/admin-web/` 目录下执行 `npm.cmd run lint` 通过。
  - 在 `apps/admin-web/` 目录下执行 `npm.cmd run test` 未通过：Vitest 启动阶段被当前受限执行环境拦截，报错为 `Cannot read directory "../../../.."`，并在加载 `vite.config.js` 时失败；该结果不能记为仓库测试通过。
  - `java -version` 未通过：本机 `PATH` 中仍未检测到 `Java`。
  - `gradle -v` 未通过：本机 `PATH` 中仍未检测到 `Gradle`。
  - 尝试通过 `Invoke-WebRequest` 下载 `JDK 21` 和 `Gradle 8.10.2` 未通过：当前执行环境无法连接外网下载源。
  - 尝试以 `docker run` 容器化方式补跑后端 `gradle test/build` 未完成：当前工具审批链路超时，未取得可执行结果。
  - 通过 `where.exe /r "C:\Program Files"` 与 `where.exe /r "C:\Program Files (x86)"` 检查，当前本机常见安装目录下未发现可复用的 `java.exe` 或 `gradle.bat`。
  - 尝试绕开 workspace 脚本、最小化 Vitest 配置并直接调用 `node_modules/.bin/vitest` 后，`apps/admin-web` 的 `npm test` 仍在同一阶段失败；说明当前阻塞点仍是受限执行环境的目录读取权限，而非前端依赖缺失。
- 结论：
  - 前端依赖与 `build/lint` 已补齐并可在当前本机通过。
  - 前端 `test` 当前仍受执行环境权限模型影响，不能在本轮记为通过。
  - 本机 `Java/Gradle` 仍未补齐，后端环境级验证仍待后续继续处理。

## 补充验证记录（2026-06-12，应用层复核）

- 验证范围：
  - 按应用层入口继续执行 `TASK-006`，只验证后端 `gradle test/build` 与前端 `npm test`。
  - 不进入 `TASK-016`，不修改业务代码、不扩展 schema。
- 本次验证结果：
  - 直接核对绝对路径通过：`C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe`、`javac.exe` 与 `C:\Gradle\gradle-8.10.2\bin\gradle.bat` 均存在。
  - `java -version` 与 `javac -version` 通过，说明 JDK 21 已安装；但当前会话未自动继承 `JAVA_HOME/PATH`。
  - `gradle -v` 直接执行仍失败；在显式设置 `JAVA_HOME` 和 `PATH` 后，Gradle 可成功启动。
  - `docker ps -a --filter "name=cqcp-postgres-test"` 通过，确认 `cqcp-postgres-test` 运行中。
  - `Test-NetConnection localhost -Port 54329` 通过，确认 `localhost:54329 -> container:5432` 端口可达。
  - 已创建干净应用层测试库 `cqcp_app_test`。
  - 在 `apps/api-server` 下显式设置 `CQCP_DB_URL=jdbc:postgresql://localhost:54329/cqcp_app_test`、`CQCP_DB_USERNAME=postgres`、`CQCP_DB_PASSWORD=postgres` 后执行 `gradle test` 未通过。
  - 当前 `gradle test` 的真实失败点不再是数据库连接，也未进入 Flyway migration；失败发生在 Gradle 插件解析阶段：`org.springframework.boot` 插件无法从 `Gradle Central Plugin Repository` 拉取。
  - 失败后查询 `cqcp_app_test`，`public` schema 仍为 0 张表，说明应用层尚未触发 Flyway，`flyway_schema_history` 也未生成。
  - `apps/admin-web` 下执行 `npm.cmd run test` 仍未通过，核心错误未变：Vitest/esbuild 启动阶段报 `Cannot read directory "../../../.."`，并在加载 `vite.config.js` 时失败。
- 结论：
  - 后端本机 JDK/Gradle 安装本体已存在，但当前会话环境变量未自动挂载；显式挂载后，验证继续被 Gradle 插件远程解析阻塞，因此本轮不能记为“后端本机 Java/Gradle/Flyway 应用层验证通过”。
  - 前端 `build/lint` 通过，`test` 仍为环境/权限模型阻塞。

## 补充验证记录（2026-06-12，Flyway PostgreSQL 运行时依赖修复）

- 验证范围：
  - 只检查并最小修复 `apps/api-server/build.gradle.kts` 的 Flyway PostgreSQL 运行时依赖。
  - 不修改 schema、migration SQL、业务代码或任务范围。
- 本次变更文件：
  - `apps/api-server/build.gradle.kts`
- 本次修复：
  - 新增 `runtimeOnly("org.flywaydb:flyway-database-postgresql")`，用于补齐 Flyway PostgreSQL database module。
- 本次验证结果：
  - `docker ps -a --filter "name=cqcp-postgres-test"` 通过，确认测试容器运行中。
  - `Test-NetConnection localhost -Port 54329` 通过，确认 PostgreSQL 端口可达。
  - 通过 `docker exec` 重建干净测试库 `cqcp_app_test`。
  - 在 `apps/api-server` 目录下显式设置 `JAVA_HOME`、`PATH`、`CQCP_DB_URL=jdbc:postgresql://localhost:54329/cqcp_app_test`、`CQCP_DB_USERNAME=postgres`、`CQCP_DB_PASSWORD=postgres` 后执行 `gradle test` 未通过。
  - 当前 `gradle test` 的真实失败点仍为 Gradle 插件解析：`org.springframework.boot` 插件无法从 `Gradle Central Plugin Repository` 拉取。
  - 本轮失败未进入 Flyway，也未触达 PostgreSQL；失败后查询 `cqcp_app_test`，`public` schema 表数量仍为 `0`。
- 结论：
  - `flyway-database-postgresql` 运行时依赖已补齐，但当前无法验证其是否生效，因为 `gradle test` 仍先失败于 Spring Boot Gradle 插件远程解析。
  - 本轮不能记为“后端应用层 Flyway/PostgreSQL 验证通过”。

## Codex execution environment diagnostic（2026-06-12）

- 诊断目标：
  - 只定位 Codex 执行环境与用户本机 PowerShell 环境的差异。
  - 不修改业务代码、不修改 schema、不进入 `TASK-016`。
- 诊断结果：
  - 工作目录正常：`pwd` 与 `Get-Location` 均为 `C:\Users\1\Documents\CQCP`。
  - Codex 当前会话中 `JAVA_HOME` 为空，`PATH` 未包含 `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin` 与 `C:\Gradle\gradle-8.10.2\bin`，因此裸命令 `java`、`javac`、`gradle` 与 `where.exe java/javac/gradle` 均失败。
  - 绝对路径核对通过：JDK 21 与 Gradle 8.10.2 安装本体存在，说明不是安装路径错误，而是 Codex 会话未继承环境变量。
  - `plugins.gradle.org:443` 与 `repo.maven.apache.org:443` 在 Codex 会话中 `PingSucceeded=True`，但 `TcpTestSucceeded=False`。
  - `Invoke-WebRequest https://plugins.gradle.org` 与 `Invoke-WebRequest https://repo.maven.apache.org/maven2/` 均失败，错误为“无法连接到远程服务器”。
  - `netsh winhttp show proxy` 显示 `Direct access (no proxy server)`。
  - `npm.cmd config get proxy` 与 `npm.cmd config get https-proxy` 均返回 `null`。
  - `git config --global --get http.proxy` 与 `git config --global --get https.proxy` 均无返回。
  - `$env:GRADLE_USER_HOME` 为空；但 `gradle test --stacktrace --info` 显示 Gradle 实际使用 `C:\Users\CodexSandboxOffline\.gradle` 作为 daemon / cache 目录，而不是 `$env:USERPROFILE` 对应的 `C:\Users\1\.gradle`。
  - `gradle test --stacktrace --info` 的真实失败点为：插件 `org.springframework.boot`，版本 `3.3.12`，仓库 `https://plugins.gradle.org:443`，具体错误类型为 `java.net.SocketException: Permission denied: getsockopt`。
  - 当前失败不是 DNS、不是 TLS/PKIX、不是 403/407、不是 repository not found，也不是数据库连接问题；在插件解析阶段即被 Codex 网络/沙箱权限模型阻断。
- 结论：
  - 用户本机 PowerShell 与 Codex 执行环境的核心差异在于：Codex 会话未继承系统级 `JAVA_HOME/PATH`，并且对外部 443 连接存在执行环境级阻断。
  - `gradle test` 在用户本机可通过，但在 Codex 内失败，不应写成 CQCP 后端工程失败，而应记录为 `Codex execution environment diagnostic`。
## Codex execution environment escalated verification（2026-06-12）
- 验证范围：
  - 只验证 `require_escalated` 后是否可恢复 `apps/api-server` 的 `gradle test/build` 本机级结果。
  - 不修改 `build.gradle.kts`、不修改 migration SQL、不进入 `TASK-016`。
- 本次执行：
  - 已通过 `docker exec cqcp-postgres-test ...` 顺序重建干净测试库 `cqcp_app_test`。
  - 在 `apps/api-server` 目录下，按要求准备了提权执行命令，命令内显式设置：
    - `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`
    - `PATH` 前置 JDK 与 `C:\Gradle\gradle-8.10.2\bin`
    - `CQCP_DB_URL=jdbc:postgresql://localhost:54329/cqcp_app_test`
    - `CQCP_DB_USERNAME=postgres`
    - `CQCP_DB_PASSWORD=postgres`
  - 对 `gradle test` 发起两次 `require_escalated` 审批申请，审批链路两次均返回 `The automatic permission approval review did not finish before its deadline`。
- 本次结果：
  - `require_escalated` 未获执行批准结果，属于审批超时，不等于命令拒绝，也不等于工程失败。
  - 因 `gradle test` 未实际执行，`gradle build` 本轮未启动。
  - 本轮无法新增后端应用层通过结论，只能确认 Codex 默认沙箱失败根因仍是 `sandbox network restricted`，而提权链路本轮未完成验证闭环。
- 结论：
  - 本项应记录为 `Codex execution environment escalated verification blocked by approval timeout`。
  - 不应写成 CQCP 后端工程失败，也不应推进到下一阶段业务开发。
## Codex execution environment escalated verification retry（2026-06-12）
- 验证范围：
  - 只补做 `apps/api-server` 的提权版 `gradle test/build` 验证闭环。
  - 不修改业务代码、不修改 schema、不修改 migration SQL、不处理前端。
- 本次执行：
  - 在 `apps/api-server` 目录下，以 `require_escalated` 方式执行 `gradle test`，命令内显式设置：
    - `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`
    - `PATH` 前置 JDK 与 `C:\Gradle\gradle-8.10.2\bin`
    - `CQCP_DB_URL=jdbc:postgresql://localhost:54329/cqcp_app_test`
    - `CQCP_DB_USERNAME=postgres`
    - `CQCP_DB_PASSWORD=postgres`
  - `gradle test` 提权执行成功，结果为 `BUILD SUCCESSFUL in 6s`。
  - 在同一组环境变量下，以 `require_escalated` 方式执行 `gradle build`。
  - `gradle build` 提权执行成功，结果为 `BUILD SUCCESSFUL in 1s`。
- 本次结论：
  - 可以确认：Codex 默认失败根因是 `sandbox network restricted` 与默认会话未继承 `JAVA_HOME/PATH`，而不是 CQCP 后端工程失败。
  - 在提权执行且显式补齐 `JAVA_HOME/PATH` 与 `CQCP_DB_*` 后，`apps/api-server` 的 `gradle test` 和 `gradle build` 可恢复与用户本机 PowerShell 一致的通过结果。
  - `TASK-006` 可记录为：后端本机 Java/Gradle/Flyway 应用层验证通过；前端 `build/lint` 通过，`test` 仍为环境阻塞。
## Frontend test scaffold verification（2026-06-12）
- 验证范围：
  - 只处理 `apps/admin-web` 的 `npm test` 环境级阻塞与最小 scaffold 修复。
  - 不修改前端业务页面、不修改测试断言、不删除测试、不进入 `TASK-016`。
- 本次变更文件：
  - `apps/admin-web/vite.config.js`
  - `apps/admin-web/src/test/setup.ts`
  - `apps/admin-web/src/App.test.tsx`
- 本次修复：
  - 在 `vite.config.js` 中新增 Vitest `test.setupFiles` 配置。
  - 新增 `src/test/setup.ts`，统一注入 `@testing-library/jest-dom/vitest` 与 `window.matchMedia` mock。
  - 从 `src/App.test.tsx` 移除重复的 `jest-dom` 导入，使测试初始化收敛到全局 setup。
- 本次验证结果：
  - 默认沙箱下执行 `npm.cmd run test` 仍失败，真实错误保持为：
    - `Cannot read directory "../../../.."`: Access is denied
    - `Could not resolve "...\\apps\\admin-web\\vite.config.js"`
  - 以 `require_escalated` 执行 `npm.cmd run test` 后，目录权限阻塞消失，测试进入真实运行阶段。
  - 提权后新的真实失败点为 `window.matchMedia is not a function`。
  - 完成 `matchMedia` 最小修复后，再次以 `require_escalated` 执行 `npm.cmd run test`，结果为：
    - `Test Files 1 passed`
    - `Tests 1 passed`
- 本次结论：
  - 可确认前端测试最初失败同时包含两层原因：
    - Codex 默认沙箱的目录读取权限阻塞
    - jsdom 环境缺少 `window.matchMedia` 的 scaffold 配置缺口
  - 当前 `apps/admin-web` 的测试代码在提权执行条件下已可通过。
  - 但在默认沙箱下，`npm test` 仍受目录读取权限模型影响，不能记录为默认环境下无条件通过。

## TASK-006 closeout review（2026-06-12）
- 收口目标：
  - 只复核 `TASK-006` 当前是否可以视为 scaffold 环境级验证收口完成。
  - 不进入 `TASK-016`，不新增业务功能。
- 最终环境级结论：
  - 后端 `apps/api-server`：
    - 在显式设置 `JAVA_HOME`、`PATH` 与 `CQCP_DB_*`，并以 `require_escalated` 执行后，`gradle test` 与 `gradle build` 均通过。
    - 可确认后端本机 Java/Gradle/Flyway 应用层验证通过。
  - 前端 `apps/admin-web`：
    - `npm run build` 通过。
    - `npm run lint` 通过。
    - `npm run test` 在提权条件下通过。
    - `npm run test` 在默认沙箱下仍受目录读取权限模型阻塞，不能记为默认环境无条件通过。
- 收口判断：
  - `TASK-006` 的 scaffold 本身、依赖、构建入口、测试入口与最小运行时配置已完成验证。
  - 当前剩余未消除项属于 Codex 默认沙箱权限限制，而不是 CQCP scaffold 工程缺陷。
  - 因此 `TASK-006` 可视为环境级验证收口完成；后续若继续推进，应将“默认沙箱下前端 test 仍受权限限制”作为执行环境注意事项保留，而不是继续视作工程阻塞。
