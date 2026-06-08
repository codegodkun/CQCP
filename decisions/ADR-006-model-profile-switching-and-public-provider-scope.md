# ADR-006: Model Profile 切换与公网模型使用边界

状态：Accepted

日期：2026-06-08

## 背景

MVP 需要在管理台配置多个审核模型，例如本地 `Gemma4`、公网 `DeepSeek` 兼容 OpenAI API、local mock fallback。项目早期可能需要用公网模型验证链路、演示效果、评测质量，也可能在业务明确允许的场景下切换为公网模型执行审核辅助。

既有架构约束要求模型不得直接决定业务 finding，结构化比对和最终点级裁判必须由后端完成。该约束继续生效。

## 适用范围

- 影响模块：Admin Console、Model Gateway、Task Execution、ReviewResultSnapshot、AI Review。
- 影响阶段：MVP 起支持基础 Model Profile 配置与选择；更复杂审批和密钥治理延后。
- 是否影响外部 API：待确认，取决于任务创建 API 是否开放模型选择字段。
- 是否影响数据库或版本快照：是，需要 execution 绑定当次模型配置版本。
- 是否影响模型、规则、prompt、正则或证据选择：是，影响模型 provider 选择和模型调用审计。

## 决策

平台采用 `Model Profile` 管理审核模型配置。管理员可配置多个模型 profile，并通过启用状态、默认模型和使用范围决定新任务使用哪个模型。

`Model Profile` 最小字段：

```text
profileCode
displayName
providerType: LOCAL / PUBLIC_OPENAI_COMPATIBLE / MOCK
endpointAlias
modelName
enabled
secretConfigured
timeoutSeconds
retryCount
usageScope: DEMO / EVALUATION / INTERNAL_REVIEW / PRODUCTION_REVIEW
isDefaultForNewTask
configVersion
```

任务创建时：

- 如果调用方或管理台不指定模型，使用当前启用的默认 `Model Profile`。
- 如果指定模型，只能选择 `enabled=true` 且 `secretConfigured=true` 的 profile。
- 每个 execution 固定绑定当次 `modelProfileCode`、`providerType`、`modelName`、`endpointAlias` 和 `modelConfigVersion`。
- 任务详情页顶部展示“审核模型”，例如 `DeepSeek V4 Flash（公网兼容 OpenAI 接口）` 或 `Gemma 4 26B via vLLM`。
- 历史 `ReviewResultSnapshot` 读取当次绑定模型，不随后续模型配置切换变化。

公网模型使用边界：

- 平台允许管理员启用公网模型 profile，并在业务允许的场景下用于审核链路验证、演示、评测或内部审核任务。
- 公网模型能否用于真实合同，由业务方、管理员和部署环境共同承担配置责任。
- 系统必须记录每次 execution 实际使用的模型 profile、provider 类型和配置版本，以便审计和追溯。
- 模型无论来自本地还是公网，都只做局部抽取、候选归属、证据选择或语义辅助。
- 最终 `PASS / ERROR / WARNING / NOT_CONCLUDED` 仍由后端裁判与结果合成模块决定。
- 公网模型不得绕过 EvidencePacket、CandidateResolver、EvidenceSlot Preflight、schema validation、预算和诊断边界。

## 备选方案

- 方案 A：只允许本地 Gemma4。风险最低，但不利于早期演示、链路验证和模型效果对比。
- 方案 B：允许多个 Model Profile，由管理员启用和选择，业务承担公网模型使用责任。当前采用。
- 方案 C：允许公网模型直接独立审核整份合同并给出最终结论。不采用，违反证据链路和后端最终裁判边界。

## 选择理由

- MVP 需要快速验证端到端链路和演示稳定性，公网 DeepSeek 在某些场景下可能比本地模型更稳定。
- 通过 `Model Profile` 绑定 execution，可以兼顾灵活切换和历史可追溯。
- 继续保持模型只做辅助、后端最终裁判，避免把模型输出直接变成业务 finding。
- 公网模型的业务合规责任通过启用状态、使用范围、配置版本和审计记录显式化。

## 影响

### 正向影响

- 支持本地、mock、公网兼容 OpenAI API 等不同 provider。
- 任务详情页可以明确展示当次审核采用哪个模型。
- 评测、演示和链路验证可更灵活。
- 历史 execution 的模型来源可追溯。

### 代价与风险

- 公网模型可能涉及合同内容外发、授权、合规和数据安全责任。
- `usageScope=PRODUCTION_REVIEW` 的公网 profile 需要业务方明确批准和管理员配置。
- 如果密钥、endpoint 或使用范围配置错误，可能造成不符合预期的模型调用。

## 不做什么

- 不允许模型直接决定最终业务 finding。
- 不允许模型绕过后端确定性裁判。
- 不在 MVP 做完整密钥审批、双人授权、数据出境审查或 DLP 平台。
- 不在普通结果页展示完整 prompt、完整模型 raw output、endpoint secret 或 stack trace。

## 回滚与迁移

- 回滚方式：停用公网 `Model Profile`，将默认模型切回本地 Gemma4 或 mock fallback。
- 数据迁移影响：已有 execution 和快照继续保留当次模型 profile 引用。
- 历史快照兼容性：历史结果不随模型配置变更而改变。

## 验证方式

- 创建任务时未指定模型，应绑定默认 enabled profile。
- 指定未启用或未配置密钥的 profile，应拒绝创建或拒绝执行模型阶段。
- 任务详情页应展示当次 execution 实际绑定模型。
- ReviewResultSnapshot 应保存模型 profile 和配置版本引用。
- 模型输出异常、schema 校验失败或超时时，仍输出 `SYS-MODEL-* / NOT_CONCLUDED`，不得生成无可靠证据的业务 finding。

## 后续动作

- 更新 `PRD.md`、`CURRENT_CONTEXT.md`、`docs/frontend.md`、`docs/backend.md` 和 `docs/ai-review.md`。
- 后续 OpenAPI 契约任务中确认任务创建 API 是否暴露 `modelProfileCode`。
- 后续数据库任务中确认 `ModelProfile`、`modelConfigVersion` 和快照字段。
- 后续权限任务中确认谁可以启用 `PUBLIC_OPENAI_COMPATIBLE` profile 和 `PRODUCTION_REVIEW` usage scope。

## 关联

- 相关任务：后续最小 OpenAPI 契约任务、Model gateway and budget baseline。
- 相关文档：`PRD.md`、`CURRENT_CONTEXT.md`、`docs/ai-review.md`、`docs/backend.md`、`docs/frontend.md`。
- 相关版本：MVP PRD v0.1。

## 待确认

- 任务创建 API 是否允许外部调用方指定 `modelProfileCode`。
- `usageScope=PRODUCTION_REVIEW` 的审批角色、审计字段和默认关闭策略。
- 公网模型调用是否需要脱敏开关、调用前提示或环境级总开关。
