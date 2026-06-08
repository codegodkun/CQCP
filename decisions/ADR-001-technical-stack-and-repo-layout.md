# ADR-001: Technical Stack and Repository Layout

Status: Proposed

日期：2026-06-07

## 背景

当前项目尚未开始编码，也未创建前端或后端脚手架。正式技术栈、语言、框架、项目目录、测试策略和数据库迁移工具仍为待确认事项。Superpowers planning / design review 结论认为，在这些决策锁定前不建议直接进入 project scaffold。

## 适用范围

- 影响模块：前端、后端、数据库、部署、测试。
- 影响阶段：V1 MVP。
- 是否影响外部 API：是。
- 是否影响数据库或版本快照：是。
- 是否影响模型、规则、prompt、正则或证据选择：间接影响。

## 决策

待确认。

本 ADR 需要确认：

- 后端语言与框架。
- 前端框架、路由和组件策略。
- 仓库目录结构。
- 数据库产品确认和迁移工具。
- 测试分层策略。
- 本地开发、构建和运行命令。
- 是否需要 Docker Compose 或其他本地环境编排。

## 备选方案

- 方案 A：Java / Spring Boot 后端 + React 前端 + MySQL + Flyway 或 Liquibase。
- 方案 B：Node.js / TypeScript 后端 + React 前端 + MySQL + Prisma 或 Knex migration。
- 方案 C：其他待确认技术组合。

## 选择理由

- 待确认。

## 影响

### 正向影响

- 锁定 scaffold 边界。
- 降低后续任务窗口重复选择技术栈的成本。
- 让测试、迁移和目录结构在编码前形成统一约束。

### 代价与风险

- 技术栈选择会影响 Word 解析库、部署方式、模型网关接入和团队维护成本。
- 如果过早 Accepted，可能固化未验证的部署前提。

## 不做什么

- 不在本 ADR 中实现业务代码。
- 不创建脚手架。
- 不安装依赖。
- 不默认继承旧 `ai-contract-review` 项目目录或代码。

## 回滚与迁移

- 回滚方式：在 scaffold 前可直接修改 Proposed 内容；Accepted 后若需变更，创建新 ADR 替代。
- 数据迁移影响：待确认。
- 历史快照兼容性：若尚未编码则无历史快照影响；若 scaffold 后变更需单独评估。

## 验证方式

- 人工审查所选技术栈是否覆盖 V1 MVP 的 Word 解析、异步任务、MySQL、结果页和模型网关需求。
- 在后续 scaffold 任务中验证最小 build/test/lint 命令。

## 后续动作

- 完成 `TASK-001-v1-mvp-scope-gate`。
- 完成 `TASK-002-entry-order-decision`。
- 人工确认本 ADR 后，才允许执行 `TASK-006-scaffold-only-after-adr`。

## 关联

- 相关任务：`TASK-001-v1-mvp-scope-gate`、`TASK-002-entry-order-decision`、`TASK-004-word-parser-baseline-plan`、`TASK-006-scaffold-only-after-adr`
- 相关文档：`CURRENT_CONTEXT.md`、`ROADMAP.md`、`docs/backend.md`、`docs/frontend.md`、`docs/database.md`、`docs/deployment.md`
- 相关版本：待确认。

## 待确认

- 团队首选语言和框架。
- 部署环境是否支持 LibreOffice headless。
- 数据库迁移工具。
- 测试框架和 CI 策略。
- 本地开发环境是否要求容器化。
