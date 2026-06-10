# ADR-001: Technical Stack and Repository Layout

Status: Accepted

日期：2026-06-09

说明：技术栈冻结的长期有效决策已沉淀到 `ADR-010-technology-stack-freeze.md`；仓库结构冻结的长期有效决策已沉淀到 `ADR-011-repository-structure-freeze.md`。本 ADR 现仅保留历史上下文，不再作为技术栈或仓库结构主引用来源。

## 背景

当前项目尚未开始业务编码，也未创建前后端脚手架。`TASK-009-mvp-scope-freeze` 已完成 MVP 范围收敛，但前端、后端、数据库、迁移工具、持久层方案、运行时编排和文档处理技术栈仍需正式冻结。

如果在这些决策未锁定前先做 OpenAPI、仓库结构或 scaffold，后续返工概率很高。

## 适用范围

- 影响模块：前端、后端、数据库、部署、测试、文档处理、模型接入层
- 影响阶段：V1 MVP
- 是否影响外部 API：是
- 是否影响数据库或版本快照：是
- 是否影响模型、规则、prompt、正则或证据选择：间接影响

## 决策

V1 MVP 核心技术栈冻结如下：

- Frontend：React 18 + TypeScript 5.x + Vite
- UI：Ant Design 5
- Frontend state / data：TanStack Query、React Router v6、Zustand（仅全局 UI state）
- Backend：Java 21 LTS + Spring Boot 3.2+
- Build：Gradle Kotlin DSL
- Database：PostgreSQL 15+
- Migration：Flyway
- Persistence：MyBatis only；MVP 不引入 JPA
- Document Processing：Apache POI + docx4j；LibreOffice headless 以容器化转换服务方式提供转换能力；PDFBox 不进入 MVP
- Model integration runtime：ModelGateway + OpenAI-compatible API + 本地适配器（可承载 vLLM / Gemma adapter）+ dev/test mock model server
- Review budget：MVP 只冻结 `STANDARD` profile
- Runtime / deployment：Docker Compose、Nginx 承载前端静态资源、Spring Boot 提供后端 API
- Observability：Spring Boot Actuator health / metrics；MVP 仅保留最小指标

仓库目录结构已由 `ADR-011-repository-structure-freeze.md` 冻结；后续 scaffold 应以 `ADR-011` 为主引用来源。

本 ADR 明确以下口径：

- 模型与硬件不作为“核心技术栈冻结”的组成部分
- 不把 `Gemma4 26B Q4`、`A30 24GB` 这类具体模型/硬件组合作为平台架构常量

模型与硬件隔离口径：

- 平台通过 `ModelGateway + ModelProfile + ReviewBudgetProfile` 隔离具体模型和硬件差异
- MVP 默认支持本地 OpenAI-compatible 模型服务
- 当前目标硬件可记录为 `A30 24GB`，但该信息属于运行环境目标，不属于核心技术栈冻结项
- 具体模型通过评测、回归验证和 `ModelProfile` 切换管理，不进入本 ADR 的核心技术栈冻结结论

更换模型或显卡时，允许调整：

- `model endpoint`
- `modelProfile`
- `promptVersion`
- `schemaVersion`
- `reviewBudgetProfile`
- `timeout`
- `retry`
- `circuit breaker`
- `token budget`

更换模型或显卡时，不得改变：

- 生产审核链路
- `EvidenceSlot` 机制
- `CandidateResolver` 边界
- `SYS-*` 与业务 finding 分流
- 后端最终裁判职责
- `ReviewResultSnapshot` 不可变性
- `AI Advice != Production Change`

## 备选方案

- 方案 A：Java / Spring Boot 后端 + React 前端 + PostgreSQL + Flyway + MyBatis
- 方案 B：Node.js / TypeScript 后端 + React 前端 + PostgreSQL + Prisma / Knex migration
- 方案 C：Java / Spring Boot 后端 + React 前端 + PostgreSQL + JPA / Hibernate

## 选择理由

- 与架构文档要求的“后端最终裁判、异步任务、快照不可变、规则与治理版本化”约束一致，Java / Spring Boot 更适合承载稳定工作流和明确的事务边界
- PostgreSQL 对 `ReviewResultSnapshot`、`PointDiagnostic`、`TuningPacket` 这类“结构化字段 + JSONB”组合更友好；`json/jsonb`、`jsonpath`、索引与操作符能力适合后续查询和诊断演进
- MyBatis 比 JPA 更贴合当前阶段的边界控制需求。MVP 需要显式掌控 SQL、JSONB、快照写入、版本化查询和迁移节奏，不适合先引入 JPA 的隐式映射复杂度
- Flyway 与 Spring Boot / PostgreSQL 组合成熟，便于在 scaffold 前先冻结迁移节奏和版本治理方式
- React 18 + TypeScript + Vite + Ant Design 5 足以支撑管理台与结果页两类界面，复杂度低于引入 Next.js SSR
- TanStack Query、React Router v6 与 Zustand 的职责边界清晰，分别承载服务端状态、路由和轻量全局 UI 状态
- Docker Compose + Nginx + Spring Boot 可以覆盖 MVP 本地开发与部署，不需要在一期引入更重的基础设施编排

## 影响

### 正向影响

- 锁定 `TASK-011` 仓库结构冻结和 `TASK-012` 领域模型冻结的输入边界
- 降低后续 OpenAPI、ERD、scaffold 与部署方案反复修改的成本
- 让数据库迁移、快照设计、JSONB 结构和异步任务执行模型能在编码前形成统一约束

### 代价与风险

- 技术栈冻结会同步约束后续目录结构、测试框架、部署方式和团队技能要求
- LibreOffice headless 容器化转换服务需要部署环境明确支持
- 如果过早把模型或显卡视为核心技术栈冻结项，会错误放大后续评测切换成本
- PostgreSQL / JSONB 的能力虽然更适合当前快照模型，但也要求从一开始就保持 SQL、索引和迁移纪律

## 不做什么

- 不在本 ADR 中实现业务代码
- 不创建脚手架
- 不安装依赖
- 不默认继承旧 `ai-contract-review` 项目目录或代码
- 不在本 ADR 中继续维护仓库目录结构细节；仓库结构以 `ADR-011` 为准

## 回滚与迁移

- 在 scaffold 前可直接通过新 ADR 替换当前决策
- scaffold 后若需更改核心技术栈，应创建新 ADR，并单独评估数据库迁移、仓库结构、构建链路和历史快照兼容性
- 模型切换或硬件切换不视为本 ADR 层级的核心技术栈回滚；应通过 `ModelProfile`、预算配置和运行参数治理

## 验证方式

- 人工审查所选技术栈是否覆盖 V1 MVP 的 Word 解析、异步任务、PostgreSQL/JSONB、结果页和模型网关需求
- 在后续 scaffold 任务中验证最小 `build / test / lint` 命令

## 后续动作

- 完成 `TASK-010-tech-stack-freeze`
- 已完成 `TASK-011` 仓库目录与项目结构冻结
- 执行 `TASK-012` 领域模型冻结
- 人工确认本 ADR 后，继续推进 `TASK-013` 最小 OpenAPI 契约
- 在 `TASK-006-scaffold-only-after-adr` 中按本 ADR 执行脚手架落地

## 关联

- 相关任务：`TASK-001-v1-mvp-scope-gate`、`TASK-002-entry-order-decision`、`TASK-004-word-parser-baseline-plan`、`TASK-006-scaffold-only-after-adr`、`TASK-010-tech-stack-freeze`、`TASK-011-repository-structure-freeze`
- 相关文档：`CURRENT_CONTEXT.md`、`ROADMAP.md`、`docs/backend.md`、`docs/frontend.md`、`docs/database.md`、`docs/deployment.md`

## 待确认

- 部署环境是否长期支持 LibreOffice headless 容器化服务
- 当前目标硬件之外是否还需要兼容其他本地 GPU 环境
- 测试框架与 CI 细节在 scaffold 阶段的最终落地方式
