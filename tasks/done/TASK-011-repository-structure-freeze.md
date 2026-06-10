# TASK-011：项目目录与仓库结构冻结

状态：已完成

类型：架构决策 / 仓库结构 / scaffold 前置

优先级：P0

负责人：Codex

创建日期：2026-06-09

完成日期：2026-06-09

来源：用户要求在 `TASK-010` 技术栈冻结后，新建并执行 `TASK-011`，冻结项目目录与仓库结构，明确 `apps/`、`packages/`、`docs/` 的组织方式。

## 背景

`TASK-010-tech-stack-freeze` 已确认 V1 MVP 技术栈为 React 18 + Vite + Spring Boot + PostgreSQL + Flyway + MyBatis。进入 scaffold 前仍需先冻结仓库结构，否则后续脚手架会提前固化前后端、共享契约、版本化审核资产、文档和部署文件边界。

本任务只做目录职责和仓库组织冻结，不创建业务代码、不搭建脚手架、不安装依赖、不提前进入 OpenAPI 细节。

## 目标

- 冻结仓库组织方式，明确 `apps/`、`packages/`、`docs/` 的结构。
- 结合已确认技术栈收敛前端、后端、共享契约、文档与部署文件的目录边界。
- 明确哪些目录属于 MVP 必需，哪些延后到后续阶段。
- 新增仓库结构 ADR，并完成 Memory Writeback。

## 非目标

- 不创建业务代码。
- 不创建前端或后端脚手架。
- 不安装依赖。
- 不创建实际 `apps/`、`packages/` 目录。
- 不定义 OpenAPI 详细字段、路径、错误码或认证方式。
- 不实现数据库表结构或 Flyway migration。
- 不拆分独立 worker、质量工作台、SDK、BI、IAM 或 RAG 模块。

## 输入

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `tasks/active/TASK-010-tech-stack-freeze.md`
- `docs/ARCHITECTURE.md`
- `decisions/ADR-001-technical-stack-and-repo-layout.md`
- `decisions/ADR-010-technology-stack-freeze.md`
- `docs/context-management.md`

## 冻结结论

V1 MVP 后续 scaffold 采用以下目标目录结构：

```text
apps/
  admin-web/
  api-server/

packages/
  api-contracts/
  review-assets/
  test-fixtures/

docs/
deploy/
decisions/
tasks/
changelog/
```

### `apps/`

- `apps/admin-web/`：React 18 + TypeScript + Vite + Ant Design 5 管理台与普通结果页。前端按静态资源交付，由 Nginx 承载。
- `apps/api-server/`：Spring Boot 后端 API、异步单 worker、Word parser、Review Engine、ModelGateway、结果快照写入和管理台 API。

MVP 不创建独立 `apps/worker/`、`apps/result-portal/`、`apps/quality-workbench/` 或 `apps/model-service/`。

### `packages/`

- `packages/api-contracts/`：后续 `TASK-013` 的最小 OpenAPI 契约、错误码说明和生成配置归属目录；本任务不写 OpenAPI 细节。
- `packages/review-assets/`：版本化审核资产归属目录，包括 `ReviewPointDefinition`、规则、正则、prompt、字段词库、合同类型画像和 EvidenceSelector 的配置源。
- `packages/test-fixtures/`：脱敏或合成样本 metadata、预期结果和评测夹具归属目录；真实样本或样本文件落地仍需 data owner 确认和后续任务。

`packages/` 不承载绕过后端最终裁判职责的共享业务逻辑。

### `docs/`

`docs/` 继续承载模块级长期知识，保持 `ARCHITECTURE.md`、`backend.md`、`frontend.md`、`database.md`、`ai-review.md`、`sap.md`、`deployment.md`、`context-management.md` 和 `review-point-definitions.md` 的职责边界。

### `deploy/`

- `deploy/compose/`：Docker Compose 编排归属目录。
- `deploy/nginx/`：Nginx 静态资源托管和反向代理配置归属目录。
- `deploy/env/`：环境变量模板归属目录，不提交密钥。

## 交付物

- 新增 `decisions/ADR-011-repository-structure-freeze.md`
- 新增当前任务文件 `tasks/active/TASK-011-repository-structure-freeze.md`
- 更新 `decisions/ADR-001-technical-stack-and-repo-layout.md`
- 更新 `decisions/README.md`
- 更新 `tasks/active/TASK-006-scaffold-only-after-adr.md`
- 更新 `CURRENT_CONTEXT.md`
- 更新 `changelog/2026-06.md`

## 验收标准

- 已明确 `apps/`、`packages/`、`docs/` 的组织方式。
- 已明确前端、后端、共享契约、版本化审核资产、文档和部署文件的目录边界。
- 已区分 MVP 必需目录与延后目录。
- 未创建业务代码、脚手架或依赖。
- 未提前定义 OpenAPI 细节。
- 已完成 ADR / TASK / CURRENT_CONTEXT / changelog 写回。

## 测试与验证

- 本任务为架构/文档任务，不运行代码测试。
- 验证方式为人工核对文档一致性：目录结构必须遵循 `docs/ARCHITECTURE.md`、`ADR-010` 和 MVP 范围冻结口径。

## 风险

- `packages/review-assets/` 后续如果混入后端最终裁判实现，可能破坏后端裁判职责边界。
- `packages/test-fixtures/` 后续若保存真实合同或未经确认样本，会引入数据治理风险。
- scaffold 阶段仍需确认测试框架、CI、Flyway 具体目录和 OpenAPI 生成策略。

## 待确认

- `packages/test-fixtures/` 中样本文件、预期结果文件和 metadata 的具体命名规范。
- Flyway migration 的具体包路径和命名节奏。
- scaffold 阶段测试框架、CI 和 pre-commit 落地方式。

## 完成记录

- 完成日期：2026-06-09
- 变更文件：`decisions/ADR-011-repository-structure-freeze.md`、`tasks/active/TASK-011-repository-structure-freeze.md`、`decisions/ADR-001-technical-stack-and-repo-layout.md`、`decisions/README.md`、`tasks/active/TASK-006-scaffold-only-after-adr.md`、`CURRENT_CONTEXT.md`、`changelog/2026-06.md`
- 测试结果：未运行代码测试；已做文档一致性核对
- 遗留问题：领域模型、最小 OpenAPI、数据库 ERD / Flyway 细节、Word parser baseline 和 Model gateway baseline 仍待后续任务冻结
- 备注：本任务未创建业务代码、未搭建脚手架、未安装依赖、未创建实际 `apps/` 或 `packages/` 目录
