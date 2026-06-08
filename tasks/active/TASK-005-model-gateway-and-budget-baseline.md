# TASK-005：模型网关与预算基线

状态：待开始

类型：规划 / 技术基线

优先级：P0

负责人：待确认

创建日期：2026-06-07

来源：Superpowers planning / design review / task decomposition 审查结论、`docs/ai-review.md`、`docs/deployment.md`

## 背景

V1 生产辅助默认使用本地 A30/Gemma，但模型只负责局部抽取、证据选择和复杂语义辅助。第一轮 MVP 需要确认 endpoint、timeout、schema、prompt/version 记录和 token budget 的最小边界，否则审核链路、EvidencePacket 和失败降级无法稳定设计。

## 目标

- 明确本地 A30/Gemma endpoint 的最小接入边界。
- 明确模型调用 timeout、schema validation、prompt/schema/model version 记录要求。
- 明确 MVP token budget 的启动边界。
- 明确模型不可用、预算不足、输出不完整时的 `SYS-*` 行为。

## 非目标

- 不实现模型客户端。
- 不写 prompt。
- 不安装模型依赖。
- 不接入公网模型。
- 不实现 Quality Copilot。
- 不做动态预算调优。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`ROADMAP.md`
- 相关架构章节：EvidencePacket token 预算、Model Assist、Gemma Artifact、模型重试与 Circuit Breaker。
- 相关 ADR：`ADR-002-v1-result-and-diagnostic-contract` 草案。
- 上游任务：`TASK-001-v1-mvp-scope-gate`、`TASK-003-first-review-points-selection`

## 范围

### 包含

- 明确 MVP 模型 endpoint、认证、网络边界和容量待确认项。
- 明确 `ModelCallIntent` 和 `GemmaExtractionArtifact` 的 MVP 最小记录字段。
- 明确 `modelCallTimeout`、stage timeout 和重试策略是否采用架构文档默认值。
- 明确 `STANDARD` budget 的启动值或待确认方式。
- 明确模型失败和预算不足的降级行为。

### 不包含

- 不调用真实模型。
- 不保存真实合同或 raw output。
- 不建设完整 circuit breaker 指标平台。
- 不创建候选优化或发布流程。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- Gemma/A30 不吃整份合同。
- Gemma/A30 不一次性裁判全部审核点。
- 规则/正则能处理的审核点不进入模型 token 预算。
- 模型输出必须经过 schema validation，不能直接进入业务裁判。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`docs/ai-review.md`
- 必读：`docs/deployment.md`
- 按需：`docs/backend.md`
- 按需：`docs/database.md`

## 交付物

- 模型网关 MVP 接入边界说明。
- token budget 启动边界或待确认清单。
- prompt/schema/model version 记录要求。
- 模型失败、预算不足和 schema invalid 的降级映射。
- 后续模型客户端 spike 或 contract test 任务建议。

## 验收标准

- 能明确模型在 MVP 中只做哪些辅助职责。
- 能明确模型不可用时审核链路如何继续或如何输出 `SYS-*`。
- 能明确哪些字段必须进入快照或诊断记录。
- 不把公网模型写入真实生产合同主链路。

## 测试与验证

- 本任务为规划任务，不运行代码测试。
- 验证方式是用首批审核点检查哪些点需要模型、哪些点必须走规则/正则。

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`。
- 必须更新 `changelog/2026-06.md`。
- 是否需要更新 `docs/ai-review.md`、`docs/deployment.md` 或 `docs/database.md`：待确认。
- 是否需要新增或更新 ADR：待确认。

## 风险

- endpoint 和容量未确认会阻塞模型辅助审核点。
- budget 过小会导致核心审核点频繁 `NOT_CONCLUDED`。
- budget 过大可能违背“局部 EvidencePacket”原则。

## 待确认

- 本地 A30/Gemma endpoint 地址和认证方式。
- 模型版本命名。
- schema version 管理方式。
- prompt version 管理方式。
- `STANDARD` / `DEEP_REVIEW` token budget 数值。

## 后续可能派生任务

- 模型网关 contract test 任务。
- Gemma artifact schema 草案任务。
- EvidenceBundle budget 测试样例任务。
- 模型失败降级测试任务。

## 完成记录

- 完成日期：待填写。
- 变更文件：待填写。
- 测试结果：待填写。
- 遗留问题：待填写。
- 备注：待填写。
