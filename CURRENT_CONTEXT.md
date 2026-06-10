# CURRENT\_CONTEXT.md

更新日期：2026-06-10

## 1\. 当前阶段

项目已完成 `TASK-006` 纯技术脚手架落地，仍未开始业务功能编码。当前进入 Flyway 初始化和 MVP 开发前验证阶段。

## 2\. 当前关键结论

* MVP 第一轮只支持单份中文 .docx 工程采购合同。
* 首批 9 个 core review point 已冻结。
* 技术栈、仓库结构、领域模型和结果/诊断契约已冻结。
* TASK-004、TASK-005、TASK-014 已完成。
* TASK-006 已完成纯技术脚手架，已建立 `apps/admin-web`、`apps/api-server`、`deploy/*`、`packages/review-assets` 和 `packages/test-fixtures` 骨架。
* 最小 OpenAPI 契约已生成到 packages/api-contracts/。
* 后续仍坚持证据不足不生成业务 finding，Gemma 只做局部辅助，后端最终裁判。
* 当前脚手架验证结果为：目录与 Compose 入口已建立；前端 `build/lint/test` 因未安装依赖失败；本机缺少 `Java/Gradle`，后端未完成编译级验证。

## 3\. 当前活跃任务

* TASK-015-flyway-v1-bootstrap
* TASK-016-mvp-development-validation

## 4\. 当前阻塞点

* 本机尚未安装前端依赖，`apps/admin-web` 无法完成 `build/lint/test`。
* 本机 `PATH` 中缺少 `Java/Gradle`，`apps/api-server` 无法完成编译与测试验证。

## 5\. 下一步顺序

1. 执行 TASK-015，落 Flyway V1 初始化迁移。
2. 执行 TASK-016，做 MVP 开发前验证。
3. 在具备依赖与 Java/Gradle 环境后，补跑 TASK-006 的前后端 build/test/lint 验证。
4. 验证通过后再进入 MVP 业务功能开发。

## 6\. 当前禁止推进

* 不开始业务功能编码。
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
* TASK-004：Word parser MVP 边界。
* TASK-005：ModelGateway 与预算基线。
* TASK-006：纯技术脚手架基线。
* TASK-014：最小 OpenAPI 契约。

