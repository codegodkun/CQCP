# ADR-023：Track B 后续独立准入评测边界

状态：Accepted / Successor Claim Terminal BLOCKED / Superseded by ADR-024/025 /
Provider Admission NOT_ESTABLISHED

日期：2026-08-02

## 背景

`TASK-EVAL-002` 的新 12-packet holdout 已在人工 ground truth 封印后执行一次。
DeepSeek 在第 2 个 eligible call 返回 schema invalid，one-time claim 已消费；依
ADR-019 和有限轮次计划，该 holdout 永久 `BLOCKED`、不得重试，Provider admission
保持 `NOT_ESTABLISHED`。

项目负责人随后明确授权另立后续 Track B 评测治理任务，并将人工答案、重大范围变化
和最终 merge 之外的 standing-grant 范围内执行权交给主 Codex。需要冻结 successor
与失败 holdout 的隔离边界，避免把新任务变成对已失败语料的调参重跑。

## 决策

### 1. successor 是新的独立评测，不是失败 run 的重试

新 integration unit 为 `MILESTONE-MVP-002-TRACK-B-SUCCESSOR`，父任务为
`TASK-EVAL-003`。它必须使用新的 taskId、executionId、packetId、sampleId、候选
文本和值；与旧 18-packet run-v3 和失败 12-packet holdout 的这些集合全部 disjoint。

失败 holdout 的 corpus、human seal、model input、dispatch、claim、Codex opinion 和
terminal blocked receipt 只读保留。不得删除、覆盖、迁移 identity、补跑剩余 calls、
读取不存在的 raw response，或把它重新描述为未执行。

### 2. 不根据失败响应调 prompt/schema

successor 继续使用既有 Track B role/candidate/anchor/abstention prompt 和严格 schema。
冻结 prompt SHA 为
`8a06b5177e7df8ca6097aaa752e2b089a0aa28e4f313ef158438627427aafe10`。
不得为了取得 GO 扩大输出预算、允许额外字段、降低 100% 阈值或让模型产生 Finding。

### 3. 人工答案仍是独立 HITL 门禁

主 Codex 可以独立构建 synthetic source signals 和非权威 proposed decisions，但这些
内容在项目负责人逐项确认前不是 ground truth。任何 Codex/DeepSeek evaluator 访问
前必须先形成 challenge-bound human confirmation 与 immutable seal。

项目负责人只负责该人工 decisions、重大范围变化和最终 merge；corpus 工具建设、
验证、standing-grant 调用、freeze、CC AUDIT、Codex auditor、commit、push、PR 和 CI
由主 Codex连续执行，不逐轮请求授权。

### 4. 正式执行与安全重试

successor 仍为 12 packets：9 个 MEDIUM/CONFLICTED eligible 单包调用和 3 个
deterministic HIGH/invalid bundle/无可靠 anchor zero-call control。DeepSeek 固定
`api.deepseek.com:443`、`deepseek-v4-pro`、strict JSON、non-streaming、thinking
disabled、`finish_reason=stop` 和现有预算。

只有机器证据明确证明 HTTP payload 尚未发送、Provider 不可能产生副作用的 DNS 或
pre-send 失败，才允许在同一冻结执行和预先声明上限内安全重试。以下情况始终零重试：

```text
HTTP send 已开始
timeout / UNKNOWN_SIDE_EFFECT
schema or content invalid
finish_reason != stop
accepted Provider response
```

安全重试必须进入 hash-bound execution receipt，不能新建通用 retry/lease 平台。

### 5. admission 与三方审计

Codex blind evaluator 必须全新、`fork_turns="none"`、只收到 exact blind input。
解盲前必须同时验证 Codex 与 DeepSeek opinion/receipt。schema、可靠 anchor、role、
candidate、anchor、abstention 和 zero-call controls 全部 100% 才建立 admission。

正式 freeze 后的两个 Codex auditor 必须全新、`fork_turns="none"`，显式使用
`gpt-5.6-sol` 与 `reasoning_effort="xhigh"`，不得使用默认或降级模型，也不得复用
blind evaluator。CC AUDIT 继续使用 CC 当前连接的 `deepseek-v4-flash`。三份报告
必须绑定同一 subject 且全部 `GO / P0=P1=P2=blocking=0`。

### 6. 后续 Provider 门禁不变

本 ADR 只授权新的独立 admission 机会，不放行 Provider。只有 successor admission
与三方审计 GO 后，才可进入 TASK-MODEL-002 的 A0+A1；A2 shadow 仍须自身验证、
freeze 和最终跨 TASK 三审。PUBLIC profile 继续 `EVALUATION / disabled / unbound`，
普通 `MVP_DEMO_MOCK` 和最终后端裁判不变。

## 备选方案

### 方案 A：重试失败 holdout 的第 2 call

不采用。one-time claim 已消费，会破坏有限轮次与第一次行为证据。

### 方案 B：根据 schema failure 放宽 output contract

不采用。会把评测变成追求通过，且偏离未来 runtime fail-closed contract。

### 方案 C：跳过 Track B 直接实现 Provider

不采用。Track A、Core seam 或 connectivity 都不能替代 eligible ambiguity admission。

## 影响

### 正向影响

* 保留失败证据的真实性，同时给独立 successor 一次明确、有限的准入机会。
* 用户交互收敛到人工 ground truth、重大范围和最终 merge。
* 审计员模型与推理强度成为可验证的冻结门禁。

### 代价与风险

* 新 synthetic corpus 仍需人工逐项确认。
* DeepSeek 再次 schema/content failure 时 Milestone 继续 BLOCKED，不得自动创建第三套
  corpus。
* 完全 disjoint 约束增加 corpus 设计和机器验证成本。

## 回滚

删除尚未执行的 successor corpus/tooling 即可回滚，不影响失败 holdout、Core 主线或
生产数据。若 successor 已产生 claim，证据必须只读保留且不得回滚为未执行状态。

## 验证

* 三语料 identity/text/value disjointness tests。
* human seal 早于 model input、dispatch、两模型执行和任何网络 attempt。
* strict 9×1 call set、3 controls zero-call、safe-retry boundary 和不可重放 tests。
* Secret/raw response/reasoning/ground truth payload absence。
* immutable freeze 与指定模型的三方全零审计。

## 关联

* ADR-019、ADR-022
* TASK-EVAL-002、TASK-EVAL-003
* TASK-034、TASK-036

## 接受记录

* 2026-08-02：项目负责人明确授权另立后续 Track B 评测治理任务；用户只负责人工
  答案、重大范围变化和最终 merge，其余 standing-grant 范围内动作由主 Codex
  连续执行，无需逐轮确认。
* 2026-08-02：项目负责人明确要求正式审计 subagents 使用
  `gpt-5.6-sol / xhigh`；本 ADR 将其固化为 freeze/audit 门禁。
* 2026-08-03：successor 人工 decisions 与 seal 已冻结，唯一 DeepSeek claim 在第 1
  个 call `AUTHENTICATION_FAILED` 并终态 BLOCKED，不得重试或解盲。ADR-024/025 的
  后续独立机会不恢复或改写本 claim；最终 Provider admission 仍为 `NOT_ESTABLISHED`。
