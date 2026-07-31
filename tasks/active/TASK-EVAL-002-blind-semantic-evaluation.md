# TASK-EVAL-002：Codex 与 DeepSeek V4 双轨盲态语义评测

状态：Track A Codex + DeepSeek 封存完成 / Track B run-v3 已解盲为
NO_GO_MODEL_MISMATCH / Provider admission 未建立 /
12 packet 模型未见 holdout 已确认、尚未构建 /
Core 不含 standing grant / 冻结 45568f… 审计 NO_GO 后整改中

类型：Evaluation / Data Governance / Model Governance

Task Level：`L3 高风险治理`

Integration unit：`MILESTONE-MVP-002-CORE`

优先级：P0

负责人：Codex

创建日期：2026-07-28

来源：TASK-034 FAIL、ADR-019

## 背景

`TASK-034` 历史 v1 deterministic E2E 为 9 MATCH / 18 MISMATCH，57 条纳入
人工 anchor 均为 NOT_OBSERVABLE；C2/R6B 曾改善为 18 MATCH /
9 NOT_OBSERVABLE、47 MATCHED / 10 NOT_OBSERVABLE。D1/v29 正式 R7 当前为
27/27 MATCH、57 MATCHED + 6 human EXCLUDED、0 SYS/Finding、
`overallVerdict=PASS`。
需要区分全文语义能力与局部 EvidencePacket 运行可行性，但模型意见不得替代独立
人工 ground truth，也不得用 Track A 表现证明 Track B admission。

## 目标

* 冻结 3 份脱敏 DOCX 的输入 hash、结构化投影和 9 个审核点定义。
* Track A 使用全文盲态投影；Track B 使用未来运行时同构的局部 EvidencePacket。
* 解盲后与人工 ground truth 和 CQCP deterministic actual 分类比较。

## 非目标

* 不让模型生成或修改人工标准答案。
* 不把模型意见直接写入 Finding、规则、expected fixture 或生产链路。
* 未获项目负责人明确且绑定实际输入 hash 的授权时，不向 DeepSeek 或任何公网服务
  发送样本。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `docs/ARCHITECTURE.md` 第 12、20、22 节
* `docs/ai-review.md`
* `decisions/ADR-019-blind-evaluation-and-model-activation-boundary.md`
* `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
* `packages/test-fixtures/human-anchors/`
* `outputs/task-034-mvp-e2e-acceptance/`

### Optional Context

* `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`

### Out of Scope

* 修改人工 anchor、修复 CandidateResolver/SourceAnchor、生产模型激活。

## 范围

### 包含

* 可重复的盲态投影生成器、manifest/hash、JSON Schema 和解盲比较器。
* 三个无历史上下文 Codex blind-eval agent，各评一份样本。
* 经明确授权后，DeepSeek V4 Pro 以 non-streaming JSON mode 运行同一输入。

### 不包含

* 未授权公网外发；完整 prompt/raw response/reasoning 持久化。

## 约束

* 模型运行前不可见 CQCP actual、expected、人工 anchor 或结论。
* Track A 使用 opaque location ID，不暴露 CQCP `blockId`；Track B 为验证未来
  runtime-isomorphic `EvidencePacket`，必须保留 packet 内真实
  `SourceAnchor.blockId/previewElementRef`，但不得包含 expected、人工 anchor、
  CQCP verdict/Finding 或全文 fallback。两轨盲态泄漏口径不得混用。
* 接纳结果必须 `finish_reason=stop` 且严格 schema valid；空内容、额外字段、非法 enum 均 fail closed。
* 不保存或回显 `reasoning_content`。
* 模型输出统一称“模型意见”。

## 交付物

* `scripts/blind-evaluation/` 工具。
* `outputs/task-eval-002/` 冻结 manifest、盲态输入、模型意见、解盲报告和原始命令 hash。
* 外发授权缺失时的明确 BLOCKED 证据。

## 可证伪验收断言

1. 投影生成器输入不变时 manifest/input hash 完全一致。
2. Track A blind input 中搜索任一人工 expected、CQCP actual 状态、`blockId`
   或人工 anchor 标识均无命中；Track B 只允许 runtime SourceAnchor identity，
   搜索 expected、人工 ground truth、CQCP verdict/Finding 均无命中。
3. 三个 Codex 评测 agent 在 `fork_turns="none"` 下各只收到一份 blind package。
4. 解盲前修改/读取 expected 的运行被流程门禁拒绝或使 run invalid。
5. Track A 与 Track B 分开计分，报告不能用 A 的结果填充 B admission。
6. 未存在外发授权 artifact 时 DeepSeek runner 不发起网络请求并返回 `EXTERNAL_EGRESS_NOT_AUTHORIZED`。
7. schema invalid、空内容、非 stop finish_reason 或 reasoning-only 响应均不进入 accepted opinions。

## 测试与验证

* Node/Java 定向测试覆盖投影确定性、盲态泄漏扫描、schema、授权门禁和解盲分类。
* 独立审计核验输入、hash、agent prompt 与输出时间顺序。

## 回滚边界

* 删除评测工具和输出即可回滚；不修改运行时、数据库或人工 ground truth。

## 文档更新要求

* 更新 `docs/ai-review.md`、ADR-019、父 TASK、CURRENT_CONTEXT、MVP_TASK_MAP 与 changelog。

## 风险

* 盲态泄漏、循环验证、模型意见冒充 ground truth、未经授权的数据外发。

## 待确认

* 三份脱敏样本的 DeepSeek runtime evaluation 公网外发授权：`已确认`；授权逐
  input SHA-256 绑定，只覆盖官方 `api.deepseek.com` 的 EVALUATION。
* Track B admission 语料的 18 项人工 decisions：`已确认`。旧语义 seal 通过
  challenge-bound identity-only migration 迁移到三组真实 runtime identity；
  该确认不授权公网外发。
* Track B run-v3 前两次精确一次性公网外发：`已确认但均未发生 HTTP 外发`。
  两次正式 runner 均在生成 execution claim 和 HTTP request 前进入
  `ENDPOINT_DNS_REJECTED`，证据明确 `networkAttempted=false`、0 provider request。
  两份授权均已归档且不得重用。第三次精确授权越过 DNS 后完成 3 个 call，第 4 个
  call 因 `finish_reason != stop` 被严格拒绝；claim 已消费、没有 DeepSeek opinion。
  旧 builder generation 已整体归档。显式关闭 thinking 的 builder v3 generation
  已由项目负责人精确授权并完成 6 calls / 15 inputs；当前无逐次聊天确认阻塞。
* 新 12 packet holdout 的人工 ground truth 和正式执行均尚未开始；Core
  integration unit 不执行新的公网模型调用。

## 完成记录

* Track A：3 个 blind package、source document hash、9 个审核点定义与人工
  ground truth/CQCP actual package hash 已在模型执行前冻结。三个
  `fork_turns="none"` Codex blind agent 与 DeepSeek v4-pro 的严格模型意见均在解盲
  前封存；两者分别为 27/27 与人工 ground truth 一致，CQCP deterministic 对照为
  9/27。该结论只描述模型意见，不建立 runtime admission。
* Track A 输出只含 opaque location ID；DeepSeek 固定 non-streaming JSON、
  `finish_reason=stop`、strict schema，raw response、reasoning 与 KEY 均未持久化。
* Track B：TASK-036 D2 runtime-isomorphic EvidencePacket seam 与 R7 zero-call 证据已
  形成。R7 的 27 packet 均为 deterministic HIGH，27/27 正确 abstention，只证明
  zero-call，不证明 Provider 能处理 eligible ambiguity。
* 旧 18 packet eligible-ambiguity corpus 的人工 decisions 已先确认并封印；run-v3
  形成 6 calls / 15 eligible inputs / 3 controls。解盲为 Codex 15/15、DeepSeek
  6/15、controls 3/3，结论严格为
  `NO_GO_MODEL_MISMATCH / providerAdmission=NOT_ESTABLISHED`。该 corpus 已解盲，
  只能作回归证据。
* 下一 admission 必须使用模型未见的 12 packet holdout（9 eligible + 3 controls），
  人工答案先封印、正式运行一次、任一断言失败即停止。Core 合并前不创建或执行该
  holdout。
* Core source diff 排除 Provider A0、standing/CC 传输和 `outputs/**`；冻结包仅以
  SHA-256 引用 R7、Track A/B、browser 与 Compose 派生证据。
* Integration unit：`MILESTONE-MVP-002-CORE`。
* 独立审计触发依据：评测正确性、模型职责与公网数据治理。
