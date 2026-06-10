# TASK-010：技术栈冻结

状态：已完成
类型：架构决策 / 技术基线 / 范围收敛
优先级：P0
负责人：Codex
创建日期：2026-06-09
完成日期：2026-06-09

来源：用户确认“先做技术栈冻结，再做领域模型、目录结构和最小 OpenAPI 契约”；并明确模型与硬件不作为核心技术栈冻结项。

## 背景

`TASK-009-mvp-scope-freeze` 已完成 MVP 范围收敛，但正式技术栈未锁定。前端、后端、数据库、持久层、迁移工具、文档处理方案和运行时编排会直接影响：

- Word parser baseline 的实现方式
- Model gateway 的运行时与依赖管理
- `Task / Execution / ReviewResultSnapshot` 的落地方式
- 后续仓库结构冻结
- scaffold 的实际边界

如果在技术栈未冻结前先写 OpenAPI、目录结构或脚手架，后续返工概率很高。

## 目标

- 冻结 V1 MVP 的正式技术栈组合
- 明确前端、后端、数据库、Persistence、迁移工具、文档处理和任务执行模型
- 明确运行时、部署、观测和本地开发编排口径
- 产出正式 ADR：`ADR-010-technology-stack-freeze.md`
- 明确模型与硬件不属于核心技术栈冻结项

## 非目标

- 不创建前后端脚手架
- 不安装依赖
- 不实现 OpenAPI 契约
- 不实现数据库表结构
- 不实现 Word parser 或 ModelGateway
- 不冻结仓库目录结构细节

## 输入

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `ROADMAP.md`
- `docs/ARCHITECTURE.md`
- `decisions/ADR-010-technology-stack-freeze.md`
- `docs/backend.md`
- `docs/frontend.md`
- `docs/database.md`
- `docs/deployment.md`

## 冻结结论

### Frontend

- React 18
- TypeScript 5.x
- Vite
- Ant Design 5
- TanStack Query
- React Router v6
- Zustand（仅全局 UI state）

### Backend

- Java 21 LTS
- Spring Boot 3.2+
- Gradle Kotlin DSL

### Database / Persistence

- PostgreSQL 15+
- Flyway
- MyBatis only
- MVP 不引入 JPA

### Document Processing

- Apache POI + docx4j
- LibreOffice headless 作为容器化转换服务
- PDFBox 不进入 MVP

### Model Runtime

- `ModelGateway`
- OpenAI-compatible API
- 本地适配器可承载 `vLLM / Gemma adapter`
- dev/test 提供 mock model server
- MVP 只冻结 `STANDARD` profile

### Runtime / Deployment

- Docker Compose
- Nginx 承载前端静态资源
- Spring Boot 仅提供后端 API

### Observability

- Spring Boot Actuator health / metrics
- MVP 仅保留最小 metrics

## 模型与硬件口径

- 模型与硬件不作为核心技术栈的一部分冻结
- 平台通过 `ModelGateway + ModelProfile + ReviewBudgetProfile` 隔离具体模型和显卡差异
- PRD 不写死 `Gemma4 26B Q4`、`A30 24GB` 这类具体组合
- MVP 默认支持本地 OpenAI-compatible 模型服务
- 当前目标硬件为 `A30 24GB`
- 具体模型可通过评测切换

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

## 选择理由

- Java / Spring Boot 更适合承载稳定工作流、事务边界和后端最终裁判职责
- PostgreSQL JSONB 非常适合 `ReviewResultSnapshot / PointDiagnostic / TuningPacket` 这类结构化字段与半结构化 payload 混合的数据模型
- MyBatis 更利于在 MVP 阶段显式控制 SQL、JSONB、快照和版本化查询边界
- Flyway 与 Spring Boot / PostgreSQL 组合成熟，适合迁移治理
- React 18 + Vite + Ant Design 5 能以较低复杂度支撑管理台与结果页
- Docker Compose + Nginx + Spring Boot 足够覆盖 MVP 部署与本地开发

## 交付物

- 更新后的 `ADR-010`
- 更新后的 `CURRENT_CONTEXT.md`
- 更新后的 `docs/backend.md`
- 更新后的 `docs/database.md`
- 更新后的 `docs/frontend.md`
- 更新后的 `docs/deployment.md`
- 更新后的 `changelog/2026-06.md`

## 验收标准

- 已明确前端、后端、数据库、Persistence、迁移工具、文档处理、运行时与观测方案
- 已产出 `ADR-010`，作为技术栈冻结的长期决策来源
- 已明确模型与硬件不属于核心技术栈冻结项
- 文档中不再保留 `MySQL`、`Gemma 4 26B via vLLM` 等旧冻结口径
- 结论足以作为 `TASK-011`、`TASK-012`、`TASK-013` 和 `TASK-006` 的输入

## 测试与验证

- 本任务为架构/规划任务，不运行代码测试
- 验证方式为人工审查文档一致性与冲突项清理结果

## 风险

- LibreOffice headless 容器化服务需要部署环境长期支持
- PostgreSQL / JSONB 需要从一开始就保持迁移纪律、索引纪律和 SQL 边界
- 模型与显卡切换若绕过 `ModelProfile` 治理，会重新引入架构漂移

## 待确认

- 部署环境对 LibreOffice headless 的长期支持方式
- 当前目标硬件之外是否还需要兼容其他本地 GPU 环境
- scaffold 阶段测试框架与 CI 细节

## 完成记录

- 完成日期：2026-06-09
- 变更文件：`decisions/ADR-010-technology-stack-freeze.md`、`decisions/README.md`、`CURRENT_CONTEXT.md`、`docs/backend.md`、`docs/database.md`、`docs/frontend.md`、`docs/deployment.md`、`changelog/2026-06.md`
- 测试结果：未运行代码测试；已做文档一致性核对
- 遗留问题：仓库结构、领域模型、最小 OpenAPI 契约待后续任务继续冻结
- 备注：模型与硬件口径已写入 `ADR-010`，不作为核心技术栈冻结项
