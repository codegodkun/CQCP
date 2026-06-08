# docs/sap.md

## 外部系统定位

平台接收外部系统发起的合同审核任务。外部系统包括 SAP、OA、采购系统或其他业务系统。

平台只提供风险提示、证据和审核结果 URL，供 SAP/OA/人工审批参考。平台不批准、不拒绝、不阻断、不修改审批流。

所有入口必须进入同一套审核链路，不允许 SAP-only 或管理台-only 审核路径。

## 外部 API 行为

任务创建后立即返回：

```json
{
  "taskId": "TASK_2026_xxx",
  "executionId": "EXEC_2026_xxx_001",
  "status": "QUEUED",
  "resultUrl": "https://.../review/results/TASK_2026_xxx"
}
```

结果 URL 可以提前生成，但 `ReviewResultSnapshot` 必须等审核完成、部分完成或失败后生成。

外部 API 顶层状态：

- `QUEUED`
- `PROCESSING`
- `SUCCESS`
- `PARTIAL_SUCCESS`
- `FAILED`

点级状态：

- `PASS`
- `WARNING`
- `ERROR`
- `NOT_CONCLUDED`
- `SKIPPED`

内部 `SYS-*` 映射为 `NOT_CONCLUDED`。外部系统不得依赖内部 `SYS-*` 技术码。

外部系统默认只需要知道点级状态和业务化原因。`notConcludedDetail` 是 diagnostic-only，不是外部系统流程控制契约。

`ERROR` 和 `WARNING` 都属于业务 finding，但外部系统必须分级处理。API 摘要应提供 `plannedPointCount`、`passCount`、`errorCount` 和 `warningCount`。`plannedPointCount` 表示本次启用并进入 ReviewExecutionPlan 的审核点总数；配置停用点不进入该分母。`NOT_CONCLUDED` 通过 `notConcludedCount` 单独统计，用于表示审核覆盖不足；`SKIPPED` 通过 `skippedCount` 单独统计，用于表示审核点不适用。`PASS`、`NOT_CONCLUDED`、`SKIPPED` 和内部 `SYS-*` 不计入业务风险数量。

外部系统可以请求重跑或请求更高 budget profile，但后端仍按 caller policy、system load 和审批策略决定是否批准。

MVP 币种固定为人民币 `CNY`。外部系统创建任务时可以省略 `currency`；若显式传入，只允许 `CNY`。其他币种不进入审核链路，应返回请求字段校验错误，不得静默按人民币解释。

税率和付款比例使用百分比数值传输，例如 `taxRate: 13` 表示 `13%`、`prepaymentRatio: 20` 表示 `20%`，不得传入 `0.13` 表示 `13%`。该口径与外围系统现有存储方式一致。

税率最多允许 4 位小数，付款比例最多允许 2 位小数；末尾零可省略。超出精度的请求应返回字段校验错误，不得由平台静默四舍五入或截断。

月度付款比例采用累计口径，外围系统应满足 `prepaymentRatio ≤ progressPaymentRatio ≤ completionPaymentRatio ≤ settlementPaymentRatio`，并满足 `settlementPaymentRatio + warrantyRetentionRatio = 100`。不按五项比例简单求和。

上述跨字段关系由外围系统负责校验，MVP 合同质量控制平台不重复拦截，也不据此生成业务 finding。平台仍执行单字段范围、精度和条件必填校验。

## Correction Execution

外部系统可用修正后的合同类型在同一 `taskId` 下创建新的 `executionId`，或重新提交新任务；平台必须保留旧 execution 快照和 superseded 原因。

同 task 合同类型修正使用专门 correction API，不复用普通创建任务接口：

```text
POST /api/review/tasks/{taskId}/executions
```

请求字段：

- `correctedContractType`
- `requestedBudgetProfile?`
- `correctionReason`

外部 reasonCode：

- `TYPE_CORRECTION`
- `BUDGET_UPGRADE`
- `MANUAL_RERUN`

internal/admin reasonCode 不允许外部 caller 提交。

同一 task 同时只能有一个 non-terminal execution；处理中的 execution 不允许并发创建 correction execution。

错误边界：

- `404 TASK_NOT_FOUND`
- `409 TASK_EXECUTION_IN_PROGRESS`
- `400 INVALID_CORRECTED_CONTRACT_TYPE`
- `403 CORRECTION_NOT_ALLOWED_BY_CALLER_POLICY`
- `429 REVIEW_QUEUE_OVERLOADED`
- `409 EXECUTION_LIMIT_REACHED`

外部 caller 提交 internal/admin reasonCode 时返回 `400 INVALID_CORRECTION_REASON`。

## 历史查询

历史 execution 查询：

```text
GET /api/review/tasks/{taskId}/executions?page=0&size=20
GET /api/review/tasks/{taskId}/executions/{executionId}/snapshot
```

普通结果 URL 默认指向最新未 superseded execution；API 调用方可显式查询历史 execution 进行对比或审计。

## 409 契约

规则集过期保护：

```text
new normal task:
- HTTP 409
- code=RULE_SET_NOT_AVAILABLE
- reason=DATASET_EXPIRED_GUARDRAIL
- retryable=false
- operatorActionRequired=true
```

已有 execution：

- 继续使用创建 execution 时绑定的 RuleSetVersion。
- 不因第 90 天到达而中途取消。

外部集成契约：

`RULE_SET_NOT_AVAILABLE`：

- 不自动重试。
- 不切换为其他规则集或预算 profile。
- 停止该业务请求的轮询/重提。
- 记录 operatorActionRequired 并通知集成运维联系人。

`TASK_EXECUTION_IN_PROGRESS`：

- 可按当前 execution 状态继续轮询。
- 不创建重复 execution。

上线前 SAP/OA/外部 caller contract test 必须覆盖两个 `409` 业务码。

## Caller Policy

一期 caller policy 只区分受控入口来源，例如外部系统 API key、管理台用户、评测任务或本地 DEBUG。它不是 SaaS 租户隔离模型。

`CallerPolicyRegistry`：

- `callerId`
- `callerType: EXTERNAL_SYSTEM / ADMIN / EVALUATION / DEBUG`
- `allowedBudgetProfiles`
- `allowTypeSuggestions`
- `allowCorrectionExecution`
- `enabled`

`CallerPolicyRegistry.allowTypeSuggestions` 默认 `false`。关闭时 API 只返回 `contractTypeAmbiguous=true`，不返回 `suggestedTypes[]`。

只有受控集成方明确获准时才返回类型建议。对于不允许暴露类型推断的 caller，可进一步只返回通用 `REVIEW_CONTEXT_REQUIRES_CONFIRMATION`。

## SAP/OA 上线前契约测试

上线前外部 caller contract test 必须覆盖：

- `409 RULE_SET_NOT_AVAILABLE`：不自动重试，不切换规则集或预算 profile。
- `409 TASK_EXECUTION_IN_PROGRESS`：继续轮询当前 execution，不创建重复 execution。
- correction execution：同一 task 创建新 execution，旧 snapshot superseded。
- caller policy：无权限 caller 不返回 `suggestedTypes[]`，不允许 correction execution。
- queue overload：`429 REVIEW_QUEUE_OVERLOADED` 或按平台策略降级。

## 待确认

- SAP/OA/采购系统实际 callerId。
- 认证方式。
- 网络边界。
- 轮询频率。
- 错误码适配。
- 运维联系人。
- 是否需要 API gateway 对旧 caller 做非重试映射。
- SAP/OA 是否支持按业务错误码而不是 HTTP status 决定重试。
