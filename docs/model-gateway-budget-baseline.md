# 模型网关与预算基线

日期：2026-06-10

状态：Frozen for MVP baseline

来源：`TASK-005-model-gateway-and-budget-baseline`、`docs/ARCHITECTURE.md`、`docs/ai-review.md`、`docs/deployment.md`、`ADR-006-model-profile-switching-and-public-provider-scope.md`

## 1. 目标

本文档冻结 V1 MVP 阶段 ModelGateway、模型调用记录、预算入口和降级策略边界，供后续 OpenAPI、scaffold、模型客户端契约测试和管理台诊断使用。

## 2. 模型职责边界

- 模型只负责局部抽取、候选归属、证据选择和复杂语义辅助
- 模型不得吃整份合同
- 模型不得一次性裁判全部审核点
- 最终 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED` 必须由后端合成
- 规则/正则可处理的审核点不进入模型 token 预算

## 3. ModelGateway 最小接入边界

### 3.1 Provider 边界

MVP 支持以下 provider 类型：

- `LOCAL`
- `PUBLIC_OPENAI_COMPATIBLE`
- `MOCK`

### 3.2 Profile 边界

- 通过 `ModelProfile` 管理模型接入
- 每个 execution 固定绑定当次 `ModelProfile`
- 外部 API MVP 不允许调用方直接指定 `modelProfileCode`
- 管理台可在受控范围内选择已启用且密钥已配置的 profile

### 3.3 默认口径

- MVP 默认支持本地 OpenAI-compatible 模型服务
- 当前目标硬件仍为 A30 24GB
- 硬件和具体模型不作为核心技术栈冻结项

## 4. ModelCallIntent 最小记录字段

```text
ModelCallIntent
- taskId
- executionId
- family
- pointCode?
- inputBlockIds
- inputBlockIdsHash
- promptVersion
- schemaVersion
- modelVersion
- attempt
```

约束：

- family primary call 可为空 `pointCode`
- supplement call 必须绑定 `pointCode`
- 只允许在同 task、同 execution 语义下做 artifact 复用

## 5. GemmaExtractionArtifact 最小记录字段

```text
GemmaExtractionArtifact
- artifactId
- taskId
- executionId
- bundleId
- reviewPointFamily
- requestedRoles
- coveredRoles
- uncoveredRoles
- inputBlockIds
- inputBlockIdsHash
- promptVersion
- schemaVersion
- modelVersion
- outputSchemaValidationStatus
- parsedRoles
- confidence
- evidenceAnchors
- redactedRawOutputRef?
- encryptedRawOutputRef?
```

约束：

- 模型输出必须先过 schema validation
- schema invalid、字段缺失、JSON 不合法都转为 `SYS-MODEL-OUTPUT-*`
- artifact 不得直接进入业务裁判

## 6. Timeout 与重试基线

### 6.1 固定运行时边界

- `modelCallTimeout = 4m`
- `REVIEWING_MODEL stage timeout = 20m`
- `maxAttempts = 3`
- `backoff = exponential, initial 30s, max 5m`
- `modelValidationReserve = 30s`

### 6.2 Circuit Breaker 基线

- 2 分钟内连续 3 次 endpoint failure -> `OPEN`
- `OPEN` 持续 2 分钟
- `HALF_OPEN` 允许一次 probe
- probe success -> `CLOSED`
- probe failure -> 再次 `OPEN`

### 6.3 失败映射

- 重试后仍无成功 artifact -> `SYS-MODEL-UNAVAILABLE`
- 超时 -> `SYS-MODEL-TIMEOUT`
- 输出不完整 -> `SYS-MODEL-OUTPUT-INCOMPLETE`
- primary / supplement 冲突 -> `SYS-MODEL-CONFLICT`

## 7. 预算基线

### 7.1 冻结内容

本次冻结以下预算结构，不冻结具体 token 数值：

- MVP 仅正式启用 `STANDARD` profile
- `DEEP_REVIEW`、`EVALUATION` 保留为后续配置和治理边界
- 预算按 `ReviewPointFamily` 和 `EvidenceBundle + PointEvidenceOverlay` 分配
- 预算失败必须走诊断与降级，不得静默回灌全文
- 预算相关信息进入管理台诊断与 TuningPacket，不进入 MVP 普通结果页主展示

### 7.2 不在本次冻结的内容

以下内容保留“待确认”，不得凭空写死：

- `STANDARD` 的具体 token 数值
- `DEEP_REVIEW` 的具体 token 数值
- 不同合同长度下的预算分层阈值

原因：

- 当前仓库尚未完成样例集与实测分布验证
- `AGENTS.md` 要求不补全架构文档未明确的信息

### 7.3 已冻结的预算策略

- 预算单位是局部 EvidencePacket，不是整份合同
- 预算不足时优先保 core point critical slots
- 低优先级点可 `PARTIAL` 覆盖并最终输出 `NOT_CONCLUDED`
- 不得为追求结论强行扩大到全文 prompt

## 8. 降级策略

### 8.1 模型不可用

若当前审核点依赖模型消歧，且模型不可用：

- 输出对应 `SYS-MODEL-*`
- 业务层映射为 `NOT_CONCLUDED`

若规则/候选已足以完成裁判：

- 不因模型不可用阻断该点

### 8.2 预算不足

- 输出 `SYS-EVIDENCE-BUDGET-EXCEEDED`
- 不强行生成业务 finding
- 管理台和评测报告可显示 `requiresHigherBudget`、`recommendedBudgetProfile`
- 普通结果页只展示业务化 `NOT_CONCLUDED` 原因

### 8.3 Schema 无效

- 输出 `SYS-MODEL-OUTPUT-INCOMPLETE`
- 当前点进入 `NOT_CONCLUDED`
- 不得将模型原始文本直接用于业务裁判

## 9. 可见性边界

### 9.1 普通结果页

不展示：

- 完整 prompt
- 完整 raw output
- endpoint secret
- stack trace
- `requiresHigherBudget`
- `recommendedBudgetProfile`

仅展示：

- 业务化 `NOT_CONCLUDED` 原因
- 证据与原文定位

### 9.2 管理台 / 评测 / TuningPacket

允许展示摘要级：

- `ModelCallIntent`
- operational call attempts
- successful model calls used
- timeout / breaker / schema validation 结果
- 预算截断与推荐预算诊断

## 10. 与 DefinitionTermIndex 的关系

- `DefinitionTermIndex` 查询与注入受预算影响
- 定义存在但因预算截断未注入，记录为 `DEFINITION_TERM_MISSING_TRUNCATED`
- 不得因定义截断而猜测术语含义继续裁判

## 11. 后续任务接口

本冻结结论直接服务于：

- `TASK-014-minimal-openapi-contract`
- `TASK-006-scaffold-only-after-adr`
- 模型客户端 contract test
- AI 调优包导出验证

## 12. 待确认

- `STANDARD` / `DEEP_REVIEW` / `EVALUATION` 的具体 token 数值
- 本地 endpoint alias、模型命名规范和发布节奏
- raw output 受控保存是否在 MVP 首轮实现
