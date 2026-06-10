# ADR-011 仓库结构冻结

Status: Accepted

日期：2026-06-09

## 背景

`ADR-010-technology-stack-freeze.md` 已冻结 V1 MVP 技术栈：React 18 + TypeScript + Vite、Spring Boot、PostgreSQL、Flyway、MyBatis、Docker Compose、Nginx 和 LibreOffice headless 容器化转换服务。

在进入 scaffold 前，必须先冻结仓库组织方式，避免后续脚手架把未确认的模块边界、OpenAPI 细节、数据库实体或部署拓扑提前固化。

本 ADR 只冻结目录职责和边界，不创建业务代码、不创建脚手架、不安装依赖、不定义 OpenAPI 细节。

## 决策

V1 MVP 采用单仓库、多应用、共享契约与资产分层的组织方式：

```text
apps/
  admin-web/
  api-server/

packages/
  api-contracts/
  review-assets/
  test-fixtures/

docs/
  ARCHITECTURE.md
  backend.md
  frontend.md
  database.md
  ai-review.md
  sap.md
  deployment.md
  context-management.md
  review-point-definitions.md

deploy/
  compose/
  nginx/
  env/

decisions/
tasks/
changelog/
```

以上为后续 scaffold 的目标目录结构。本 ADR 不要求当前立即创建这些目录。

## 目录职责

### `apps/`

`apps/` 只放可运行应用。

- `apps/admin-web/`：MVP 管理台与普通结果页前端。技术栈为 React 18 + TypeScript + Vite + Ant Design 5。前端以静态资源方式交付，由 Nginx 承载。
- `apps/api-server/`：MVP 后端 API、异步任务执行、Word parser、Review Engine、ModelGateway、结果快照写入和管理台 API。技术栈为 Java 21 + Spring Boot 3.2+ + MyBatis + Flyway。

MVP 不拆独立 worker 应用。异步单 worker 由 `apps/api-server/` 内部承载，后续如需独立 worker，必须通过新任务和必要 ADR 评估。

MVP 不创建独立 public result app。普通结果 URL 由 `admin-web` 中的结果页路由承载，读取后端结果快照 API。

### `packages/`

`packages/` 只放跨应用共享的契约、版本化审核资产和测试夹具，不放可独立运行的业务服务。

- `packages/api-contracts/`：后续 `TASK-013` 产出的最小 OpenAPI 契约、错误码说明和生成配置。当前任务不写 OpenAPI 细节。
- `packages/review-assets/`：版本化审核资产的源定义，例如 `ReviewPointDefinition`、规则/正则/prompt/字段词库/合同类型画像/EvidenceSelector 的配置源。这里保存的是可版本化资产，不保存后端最终裁判实现代码。
- `packages/test-fixtures/`：脱敏或合成样本的元数据、预期结果和评测夹具。不得提交真实生产合同；样本文件进入仓库前必须由业务方或 data owner 确认可用于项目验证。

`packages/` 不作为绕过后端裁判职责的共享业务逻辑层。结构化比对、EvidenceSlot coverage、CandidateResolver 最终边界和点级确定性裁判仍由后端负责。

### `docs/`

`docs/` 保持模块级长期知识，不放运行时代码。

- `docs/ARCHITECTURE.md` 是生效架构文档。
- `docs/backend.md`、`docs/frontend.md`、`docs/database.md`、`docs/ai-review.md`、`docs/sap.md`、`docs/deployment.md` 分别承载模块级长期知识。
- `docs/context-management.md` 承载项目记忆写回规则。
- `docs/review-point-definitions.md` 继续作为首批审核点定义草案来源，后续可被 `packages/review-assets/` 的物理配置格式承接。

文档优先级仍按 `AGENTS.md` 执行。若目录结构文档与 `docs/ARCHITECTURE.md` 冲突，以 `docs/ARCHITECTURE.md` 为准，并通过 ADR 修正。

### `deploy/`

`deploy/` 放部署和本地编排文件，不放业务实现。

- `deploy/compose/`：Docker Compose 编排，包括 PostgreSQL、api-server、admin-web/Nginx、LibreOffice headless 转换服务和 mock model server 的后续落地位置。
- `deploy/nginx/`：前端静态资源托管、反向代理和普通结果 URL 路由相关 Nginx 配置。
- `deploy/env/`：环境变量模板和本地开发示例，不提交密钥。

MVP 不引入 Kubernetes、RabbitMQ、Kafka、分布式 worker、多租户 IAM 或复杂 BI 部署目录。

## MVP 必需目录

后续 scaffold 阶段的 MVP 必需目录为：

- `apps/admin-web/`
- `apps/api-server/`
- `packages/api-contracts/`
- `packages/review-assets/`
- `docs/`
- `decisions/`
- `tasks/`
- `changelog/`
- `deploy/compose/`
- `deploy/nginx/`
- `deploy/env/`

`packages/test-fixtures/` 是样本验证前置目录，但真实样本和脱敏/合成样本的落地需要单独任务确认命名、保存范围和 data owner 责任。

## 延后目录

以下目录或应用不进入第一轮 MVP：

- 独立 `apps/worker/`
- 独立 `apps/result-portal/`
- 独立 `apps/quality-workbench/`
- 独立 `apps/model-service/`
- `packages/sdk-*`
- `packages/iam-*`
- `packages/bi-*`
- `packages/rag-*`
- Kubernetes / Helm / Terraform 等生产级基础设施目录

如后续需要引入，应通过独立任务和必要 ADR 评估。

## 边界约束

- 不默认继承旧 `ai-contract-review` 项目的代码、目录、页面、样本或规则。
- 前端不得形成管理台专属审核链路；管理台创建任务必须进入与外部 API 相同的后端审核链路。
- `packages/review-assets/` 中的配置资产必须版本化，但不得替代后端最终裁判职责。
- Flyway migration 归属 `apps/api-server/` 的后端工程边界，后续数据库任务再冻结具体迁移目录和命名。
- OpenAPI 细节由 `TASK-013` 冻结；本 ADR 只确定其归属目录为 `packages/api-contracts/`。
- 本 ADR 不创建脚手架，不安装依赖，不创建业务代码。

## 影响

- `TASK-006-scaffold-only-after-adr` 后续应按本 ADR 创建工程骨架。
- `TASK-012` 领域模型冻结时，应以 `apps/api-server/` 为后端领域模型落地边界，以 `packages/review-assets/` 为版本化审核资产边界。
- `TASK-013` 最小 OpenAPI 契约应落入 `packages/api-contracts/`，但不得在本任务提前定义细节。

## 回滚与迁移

- scaffold 前如需调整目录，可通过新 ADR 或本 ADR 修订完成。
- scaffold 后若更改 `apps/`、`packages/` 或部署目录边界，应评估构建链路、CI、部署脚本、数据库迁移和历史快照兼容性。

## 验证方式

- 人工核对本 ADR 是否遵循 `docs/ARCHITECTURE.md`、`ADR-010` 和 MVP 范围冻结口径。
- 后续 scaffold 阶段验证目录结构、最小 build/test/lint 命令和 Docker Compose 编排入口。

## 关联

- 来源任务：`TASK-011-repository-structure-freeze`
- 上游 ADR：`ADR-010-technology-stack-freeze`
- 历史 ADR：`ADR-001-technical-stack-and-repo-layout`
- 后续任务：`TASK-012`、`TASK-013`、`TASK-006`

## 待确认

- `packages/test-fixtures/` 中样本文件、预期结果文件和 metadata 的具体命名规范。
- Flyway migration 的具体包路径和命名节奏。
- scaffold 阶段的测试框架、CI 和 pre-commit 落地方式。
