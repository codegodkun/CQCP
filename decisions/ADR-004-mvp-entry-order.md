# ADR-004: MVP Entry Order

Status: Accepted

日期：2026-06-07

## 背景

当前 V1 文档同时描述外部系统入口、管理台入口和评测入口。所有入口必须进入同一套审核链路，但第一轮 MVP 不宜同时完整实现所有入口。`TASK-002-entry-order-decision` 比较了管理台上传优先、外部 API 优先、API-first + 简易管理台三种入口顺序。

## 适用范围

- 影响模块：后端、前端、SAP/OA API、结果 URL、任务模型。
- 影响阶段：V1 MVP。
- 是否影响外部 API：是。
- 是否影响数据库或版本快照：间接影响。
- 是否影响模型、规则、prompt、正则或证据选择：否。

## 决策

MVP 入口顺序采用：

```text
API-first + 简易管理台
```

具体含义：

- 先固定统一审核链路和最小任务创建 API 边界。
- 简易管理台只作为内部触发和查看入口，调用同一套最小任务创建 API。
- 简易管理台不拥有独立审核链路。
- 第一轮 MVP 不做正式 SAP/OA/采购系统深度联调。
- 第一轮 MVP 不做完整管理台、完整权限治理或完整发布审批。

## 备选方案

- 方案 A：管理台上传优先。
- 方案 B：外部 API 优先。
- 方案 C：API-first + 简易管理台。

## 选择理由

- 符合“所有入口必须进入同一套审核链路”的长期约束。
- 先固定 Task / Execution / Result URL 的最小外部边界。
- 保留内部样本调试和结果页验证入口。
- 避免第一轮 MVP 变成完整管理台项目。
- 降低后续 SAP/OA 接入时 API 返工风险。

## 影响

### 正向影响

- 后续 scaffold 可以围绕最小 API、简易管理台和结果 URL 建立工程骨架。
- 后端、前端和结果页边界更清晰。
- 管理台不会成为独立审核路径。

### 代价与风险

- 需要严格限制简易管理台范围，避免滑向完整管理台。
- 需要尽早定义普通结果 URL 访问控制。
- SAP/OA 正式联调延后，外部系统适配风险需要在 Pilot 前处理。

## 不做什么

- 不做管理台专属审核链路。
- 不做 SAP-only 或 OA-only 审核链路。
- 不在第一轮 MVP 做 SAP/OA 深度 correction。
- 不在第一轮 MVP 做完整历史查询、caller policy 管理或 contract test。
- 不在本 ADR 中定义完整 OpenAPI。

## 回滚与迁移

- 回滚方式：若人工决定改为管理台上传优先或外部 API 优先，创建新 ADR 替代本 ADR。
- 数据迁移影响：当前尚未编码，无数据迁移影响。
- 历史快照兼容性：当前尚未生成历史快照；后续实现必须保证入口顺序变化不改变已生成快照语义。

## 验证方式

- 后续最小 OpenAPI 契约任务必须证明简易管理台调用同一任务创建 API。
- 后续 scaffold 任务不得创建管理台专属审核链路。
- 后续结果页任务必须从 `taskId` / `executionId` / `resultUrl` 模型读取结果。

## 后续动作

- 创建最小 OpenAPI 契约任务。
- 在 `ADR-010` 中冻结支持 API-first + 简易管理台的技术栈基线。
- 在 `ADR-002` 中固化普通结果页、管理台和外部 API 的诊断可见性。
- 在 `ADR-003` 中固化 Task / Execution / ReviewResultSnapshot 最小模型。

## 关联

- 相关任务：`TASK-002-entry-order-decision`、`TASK-006-scaffold-only-after-adr`
- 相关文档：`CURRENT_CONTEXT.md`、`ROADMAP.md`、`docs/frontend.md`、`docs/backend.md`、`docs/sap.md`
- 相关版本：V1 MVP。

## 待确认

- 普通结果 URL 是内网公开令牌访问，还是必须登录。
- 第一批试点用户是内部管理台用户、外部系统 caller，还是质量验证用户。
- 最小任务创建 API 的请求字段和错误码。
- SAP/OA/采购系统正式联调进入 Pilot 还是 V1 末尾。
