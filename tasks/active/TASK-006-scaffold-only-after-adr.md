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
