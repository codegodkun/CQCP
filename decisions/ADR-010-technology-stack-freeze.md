# ADR-010 技术栈冻结

## Status
Accepted

## Context
CQCP MVP 已完成范围冻结，需要冻结正式技术栈，作为 OpenAPI、领域模型、目录结构和脚手架的前置条件。  
本 ADR 记录长期有效技术栈及相关约束。

## Decision

### Frontend
- React 18
- TypeScript 5.x
- Vite
- Ant Design 5
- TanStack Query
- React Router v6
- Zustand（全局 UI 状态）
- 前端静态文件由 Nginx 托管，MVP 阶段前端与后端分离

### Backend
- Java 21 LTS
- Spring Boot 3.2+
- Gradle Kotlin DSL
- 多模块分层：word-parser、review-engine、model-gateway、admin-console、governance

### Database
- PostgreSQL 15+
- Flyway
- MyBatis only（禁止 JPA 使用）
- JSONB 用于 Snapshot / Diagnostic / TuningPacket 数据存储

### Document Processing
- Apache POI + docx4j
- LibreOffice headless 容器化，用于 .doc 转 .docx
- PDFBox 暂不进入 MVP

### Model Integration
- ModelGateway
- OpenAI-compatible API
- vLLM / Gemma adapter
- Mock model server for dev/test
- MVP 仅启用 STANDARD profile

### Runtime
- Docker Compose (local dev)
- 后端 API 与前端静态资源分离部署

### Observability
- Spring Boot Actuator /metrics 和 /health 最小指标
- MVP 阶段采集任务队列长度、模型调用延迟、错误率、数据库连接池状态

## Long-term Constraints

### 配置管理
- Spring Boot 使用 `application.yml` + `application-{profile}.yml`
- 敏感配置通过环境变量注入
- 前端使用 `.env.*` 管理环境配置
- 禁止在代码中硬编码数据库连接、模型 endpoint、密码、token 或其他敏感配置

### 模块依赖治理
- 模块边界与依赖方向属于长期治理内容
- review-engine 不得依赖 admin-api 实现层
- word-parser 不得依赖 review-engine
- model-gateway 不得依赖 review-engine 实现层
- 管理台通过 API 调用后端能力，不参与审核核心链路
- 具体模块划分和依赖规则通过后续 ADR 与仓库结构冻结任务管理

### 明确不采用（Rejected）
本 ADR 明确不采用以下方案：
- MySQL
- JPA（MVP）
- Next.js
- PDF-first 架构
- OCR-first 架构
- 模型直接生成业务 Finding
- 绕过 ModelGateway 的模型调用方式

如需重新引入上述方案，必须通过新的 ADR 进行评审和决策。

## Consequences
- 可以调整：
  - modelProfile
  - model endpoint
  - token budget
- 不得改变：
  - EvidenceSlot
  - CandidateResolver
  - ReviewResultSnapshot
  - 后端最终裁判职责
  - AI Advice ≠ Production Change
- 所有实现必须遵循本 ADR 冻结技术栈及长期约束