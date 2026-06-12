# Flyway Migration Conventions

当前目录归属 `apps/api-server/` 后端工程边界。

## 已落地迁移

- `V1__cqcp_mvp_core_schema.sql`
  - 建立 `task`
  - 建立 `execution`
  - 建立 `task_stage_log`
  - 建立 `review_result_snapshot`
  - 建立 `tuning_packet`
  - 建立 `point_diagnostic`

## 命名规则

- 使用 `V{version}__{description}.sql`
- `description` 采用小写 snake_case
- 只允许顺序递增，不修改已执行版本

## 边界

- V1 只覆盖 MVP 核心表和最小索引
- 不在当前迁移引入样本集、规则治理、权限矩阵或 BI 表
- JSONB 仅用于快照、结构化输入、诊断和调优 payload

## 后续任务

- 具体数据库回放、容器联调和应用启动验证由后续任务继续补齐
- 业务 Mapper、Repository 和查询实现不在本目录任务范围内

