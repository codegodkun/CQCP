# 最小 OpenAPI 契约说明

## 范围

本目录承载 `TASK-014-minimal-openapi-contract` 的第一版最小契约产物：

- `openapi.yaml`
- `openapi.json`

本版只冻结以下公开接口：

- `POST /api/review/tasks`
- `GET /api/review/tasks/{taskId}/executions/{executionId}`
- `GET /api/review/results/{taskId}`

## 公开边界

- 任务创建使用 `multipart/form-data`，其中 `file` 为 `.docx` 二进制，`metadata` 为 JSON。
- 外部 API MVP 不允许调用方传入 `modelProfileCode`。
- 外部 API 顶层状态只暴露 `QUEUED / PROCESSING / SUCCESS / PARTIAL_SUCCESS / FAILED`。
- 点级状态只暴露 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`。
- 内部 `SYS-*` 不进入公开契约，只映射为业务化 `notConcludedReason`。
- `requiresHigherBudget`、`recommendedBudgetProfile`、`PointDiagnostic`、完整 `TuningPacket`、完整模型原始输出、prompt、endpoint secret、stack trace 不进入本版公开接口。

## 最小对象

本版在 `components.schemas` 中冻结了以下最小对象：

- `Evidence`
- `Candidate`
- `ReviewResultSnapshot`
- `DefinitionTermIndexEntry`

其中：

- `Evidence`、`Candidate` 作为结果解释对象进入公开结果接口。
- `DefinitionTermIndexEntry` 本版仅作为已冻结的索引层最小接口表示保留在 schema 中，不挂到公开 path，避免与 `ADR-008` 的“外部 API 无直接影响”边界冲突。

## 与上游任务的一致性

- `TASK-004`：公开结果中的 `SourceAnchor` 保留 `contextType`、`sourceOrigin`、`sourceExtractionMode` 和最低 block/section/table row/cell 级定位边界；上传文件类型限制为 `.docx`。
- `TASK-005`：任务创建不暴露 `modelProfileCode`；状态查询和结果读取只返回执行绑定后的 `reviewModel` 摘要；预算与模型诊断不进入普通结果接口。
- `TASK-012`：结果读取以 `taskId + executionId` 绑定的 `ReviewResultSnapshot` 为中心；`supersededReason`、不可变快照、最新未 superseded execution 读取规则都保持一致。

## 验证方法

- `openapi.json` 使用 `Test-Json` 做结构校验。
- 若运行环境支持 `ConvertFrom-Yaml`，则对 `openapi.yaml` 做解析校验。
- 人工核对 `TASK-004`、`TASK-005`、`ADR-012`、`ADR-008`、`docs/backend.md` 与 `docs/sap.md` 的公开边界。
