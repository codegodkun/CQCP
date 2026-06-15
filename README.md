# CQCP

Contract Quality Control Platform（合同质量控制中台 V2）项目仓库。

当前状态：MVP 开发阶段。

`README.md` 只作为项目入口与导航，不重复维护任务细节、实施状态或历史决策。当前任务状态、阻塞项和下一步，请以 [CURRENT_CONTEXT.md](CURRENT_CONTEXT.md) 为准。

## CQCP 是什么

CQCP 面向合同质量控制辅助场景，统一承接合同审核任务，输出风险提示、证据与结果 URL，供 SAP、OA、采购系统和人工审核参考。

项目定位是“质量控制辅助平台”，不是审批系统，不批准、不拒绝、不阻断、不修改审批流。

## 如何阅读项目文档

推荐阅读顺序：

1. [AGENTS.md](AGENTS.md)
2. [CURRENT_CONTEXT.md](CURRENT_CONTEXT.md)
3. [PRD.md](PRD.md)
4. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
5. [tasks/MVP_TASK_MAP.md](tasks/MVP_TASK_MAP.md)
6. [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
7. [docs/VERIFY.md](docs/VERIFY.md)
8. [changelog/2026-06.md](changelog/2026-06.md)

## 核心文档入口

- [AGENTS.md](AGENTS.md)：仓库总规则、任务流程、文档优先级
- [PRD.md](PRD.md)：产品需求定义
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)：当前生效架构约束
- [CURRENT_CONTEXT.md](CURRENT_CONTEXT.md)：当前阶段、活跃任务、阻塞项、下一步
- [tasks/MVP_TASK_MAP.md](tasks/MVP_TASK_MAP.md)：MVP 任务顺序与协作边界
- [changelog/2026-06.md](changelog/2026-06.md)：本月项目记忆与关键变更
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)：开发流程、角色分工、Git 规则
- [docs/VERIFY.md](docs/VERIFY.md)：任务验收、验证清单、提交前检查

## 开发原则

- Docker Compose 是唯一标准开发 / 验证 / 测试环境
- 当前仓库实际 Compose 入口为 `deploy/compose/compose.yml`
- 环境变量样例位于 `deploy/env/.env.example`
- Codex 是总控与最终审查者
- Claude Code / DeepSeek 只能执行已冻结 `TASK_SPEC`
- 未经用户明确授权不得 `push`
- 禁止使用 `git add .`

## 开发与验证入口

- 开发流程：见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
- 验收流程：见 [docs/VERIFY.md](docs/VERIFY.md)
