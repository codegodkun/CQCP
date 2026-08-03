# TASK-EVAL-002：Codex 与 DeepSeek V4 双轨盲态语义评测

状态：Track A Codex + DeepSeek 封存完成 / Track B run-v3 已解盲为
NO_GO_MODEL_MISMATCH / Core 已随 PR #37 合并到 `master@115be530…` /
新 12 packet holdout 已完成人工封印与一次正式执行 /
DeepSeek 第 2 个 call schema invalid，claim 已消费且不得重试 /
Provider admission `NOT_ESTABLISHED` / `MILESTONE-MVP-002 RECOVERY_ACTIVE` /
失败 holdout 永久不可重试 / TASK-EVAL-003、004 已分别终态 BLOCKED /
TASK-EVAL-005 第五套已历史终态 `SEALED_NO_GO_MODEL_MISMATCH` /
ADR-026、TASK-EVAL-006 有限会话投影恢复已获批准，A0/A1/A2 仍阻塞

类型：Evaluation / Data Governance / Model Governance

Task Level：`L3 高风险治理`

Integration unit：`MILESTONE-MVP-002-TRACK-B-HOLDOUT`

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
6. 未存在有效 `ADR-022` standing grant 与绑定实际 input/dispatch/call-set/request
   hashes 的派生 receipt 时，DeepSeek runner 不发起网络请求并返回
   `EXTERNAL_EGRESS_NOT_AUTHORIZED`。
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
* 新 12 packet holdout 已构建 9 个 eligible 与 3 个 zero-call control；项目负责人
  已对 challenge `TBH2-fac56106204c4b93a11befc577369360` 原样确认全部 12 条
  proposedExpected。human seal SHA 为
  `11fbcc06458e5c24c9c3080f1b126c9b9c1358ad90b2532096b0ab5b90f3601b`。
* `MILESTONE-MVP-002` standing egress grant：`已确认并落盘`。它只免除范围内的
  逐次聊天确认；人工 ground truth 仍须独立确认，且每次真实执行仍须自动派生绑定
  actual input、dispatch、provider call set、9 个 outbound request hash、模型、调用
  数和时间的 receipt。

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
* 下一 admission 使用模型未见的 12 packet holdout（9 eligible + 3 controls）。
  Core 已合并；当前 source signals、非权威 proposed decisions、pre-seal manifest、
  challenge/seal 门禁、standing grant、9×1 单包 request contract、派生 receipt 和
  runtime seam test 已建立。人工答案必须先封印，正式运行一次，任一断言失败即停止。
* Core source diff 排除 Provider A0、standing/CC 传输和 `outputs/**`；冻结包仅以
  SHA-256 引用 R7、Track A/B、browser 与 Compose 派生证据。
* 旧 Track B v2 dispatch 所绑定的 packet manifest 与 R7 manifest 已按原始
  SHA-256 固化为历史 fixture；verify-only override 必须成对提供且严格匹配旧
  dispatch，current-HEAD R7 继续独立重建。定向 contract `7/7` 已通过。
* PR #37 的 Linux CI 证明 Track A checked-in manifest/input hash 与 TASK-036 v2
  historical output hash 曾依赖 Windows CRLF 工作区字节。LF 候选 `76f5685…`
  又在完整验证的 `blind-unblind` 阶段证明：直接迁移为 LF 会破坏 immutable Track A
  freeze/DeepSeek seal 对三份 ground truth 的历史 SHA 绑定。当前修复因此不迁移
  Track A seal：Track A generator 与其历史 JSON 继续显式 CRLF；TASK-036 v2 的四个
  JSON 同样保留历史 CRLF，而冻结 SHA 本来就是 LF 的 Markdown/CSV 明确使用 LF。
  盲评内容、人工 decisions、CQCP actual 语义与 Provider 边界均不变。HEAD
  `ad14800c…` 的 GitHub run `30729941151` 暴露 Markdown/CSV 属性错配后，最小属性
  修复再次取得 Windows 与断网 Linux fresh-clone `31/31 PASS`，直接 unblind 既有
  27 项结论不变。
  该证据只关闭跨平台字节与历史 seal 兼容根因，不替代新 HEAD 的完整 verification、
  freeze 或三审。
* 已完成 integration unit：`MILESTONE-MVP-002-CORE`；
  `MILESTONE-MVP-002-TRACK-B-HOLDOUT` 已在正式执行门禁终态 `BLOCKED`。
* 独立审计触发依据：评测正确性、模型职责与公网数据治理。
* 2026-08-02：Core 最终 subject
  `90c4ae9aca0bc06dcdb2ec5af98e93590970653be56fa3dbdcc86eab00451cba`
  三审全零 GO，GitHub run `30748527866` 全绿，PR #37 合并为
  `115be530480e2ff9b92a7076b2668c066a44ae5c`。
* 2026-08-02：新 holdout 的扩展 pre-seal 精确定向为 Node `51/51`、JUnit 新旧
  Track B + D2 `24/24`。challenge
  `TBH2-fac56106204c4b93a11befc577369360` 绑定 corpus SHA
  `15d1f845d65c5018363bba324d0527f36ff07ddf0703f188f1d557711a9e2b24`
  与 challenge SHA
  `37a05c4b0d2ed4b21282f0fa09dd00938da60857a2d53e187490a28f6cf5d23a`；
  项目负责人随后确认 12 条 proposedExpected 无修改，decisions SHA 为
  `f8557a880a201376f00598eb6b7cc30e54e9a8a42df3d3c7070ade52376fdb5b`，
  human seal verify 通过。
* 2026-08-02：正式 dispatch 绑定 model input `bd402b40…`、dispatch `8fe2a996…`、
  call set `8c63e377…` 与派生 receipt `76d1b171…`。Codex blind evaluator 的 9 条
  opinion 已严格封存但未解盲。DeepSeek `deepseek-v4-pro` 完成第 1 个 call 后，
  第 2 个 call 因 `SCHEMA_INVALID_OR_EMPTY / OPINION_SCHEMA_INVALID` fail closed；
  claim `da3f1eb1…` 已消费，终态 blocked receipt `f79060fe…` 证明
  `automaticRetryPerformed=false`。没有 DeepSeek accepted opinion，未执行 unblind、
  freeze 或三方审计；同一 holdout 不得重试，Provider admission 保持
  `NOT_ESTABLISHED`。
* `node --test scripts/blind-evaluation/*.test.mjs` 不是 fresh worktree 的独立入口：
  旧 Track A/B 历史测试仍有若干项要求 Core verification 预先重建未检出的
  `outputs/**`，否则以 `ENOENT` fail closed。该诊断未修改旧 harness；本阶段使用
  明确列出的 13 个可独立 Node test 文件（51 assertions）与 3 个 Java suite
  （24 tests）作为 pre-seal 定向门禁。
