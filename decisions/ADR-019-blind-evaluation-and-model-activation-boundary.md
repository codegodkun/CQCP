# ADR-019：双轨盲态评测与模型激活边界

状态：Accepted / Track A Codex + DeepSeek 与 Track B seam 已实施 /
旧 eligible corpus 已解盲为 NO_GO / Core 已合并 /
三个独立 holdout 均终态失败 / TASK-EVAL-004 final claim schema invalid BLOCKED /
Provider admission 未建立

日期：2026-07-28

## 背景

`TASK-034` 暴露 candidate、SourceAnchor 与人工 ground truth 的明显差距。全文语义模型可能对合同作出合理意见，但生产链路只允许模型消费受控局部 EvidencePacket、辅助候选归属或语义解释，最终裁判仍在后端。若不区分这两种输入，全文评测会错误地替代运行 admission 证据；若模型在解盲前看到 expected/actual，又会形成循环验证。

## 适用范围

* 影响模块：离线评测协议、样本投影、Model Profile usage scope、shadow/assist 激活门禁。
* 是否影响外部 API：否。
* 是否影响数据库：评测本身否；本 Core integration unit 不新增 Provider runtime
  migration。
* 是否改变审核语义：本 ADR 不改变；只定义未来激活门禁。
* 是否允许公网外发：仅在 `ADR-022` standing grant 有效且本次实际输入、dispatch、
  provider call set 与 outbound request hashes 已自动派生绑定后。

## 决策

### 1. 人工 ground truth 与模型意见严格分离

人工 anchor/expected 是唯一独立 ground truth。Codex、DeepSeek 或 CQCP actual 均为待比较对象，统一称为“模型意见”或“系统输出”，不得生成、修改或补齐 ground truth。

模型运行前封存：

```text
sample identity
source DOCX SHA-256
review point definition hash
parser/structured projection version
blind input hash
ground-truth package hash（只记录 hash，不向评测 agent 暴露内容）
CQCP actual package hash（只记录 hash，不向评测 agent 暴露内容）
```

### 2. Track A：全文语义意见

输入为保留标题、段落、表格行列结构的完整脱敏文本投影。每个位置使用本次 run 生成的 opaque location ID，不暴露 CQCP `blockId`、actual、expected、人工 anchor 或结论。

输出 schema：

```text
reviewPointCode
opinion: CONSISTENT / INCONSISTENT / NOT_ENOUGH_EVIDENCE / NOT_APPLICABLE
confidence: 0..1
candidateValues[]
evidenceQuotes[]
opaqueLocations[]
insufficiencyReason
```

三份样本由三个 `fork_turns="none"`、无历史上下文且互不复用的 Codex blind-eval agent 分别评测。获得明确公网外发授权后，DeepSeek V4 Pro 使用相同投影和 schema。

Track A 只回答全文语义能力，不证明运行时 EvidencePacket、CandidateResolver 或 SourceAnchor admission。

### 3. Track B：运行可行性

输入必须与计划中的运行时 EvidencePacket 同构，至少包含：

```text
reviewPoint/family identity
requested role
candidate occurrences
reliable source anchors
coverage/abstention signals
budget/truncation metadata
```

模型只输出 role/candidate/anchor/abstention 建议，不输出最终 Finding。缺 candidate、无可靠 anchor、invalid bundle 或 admission 不满足时应零调用/拒答，而不是回灌全文。

Track A、Track B 分开报告、分开计分；不得互相填补缺失证据。

### 4. 解盲与根因分类

全部意见落盘并绑定 hash 后才能解盲。解盲器只读比较人工 ground truth、CQCP deterministic output 与模型意见，根因枚举固定为：

```text
PARSER
CANDIDATE_EXTRACTION
CANDIDATE_RESOLVER
SOURCE_ANCHOR
BACKEND_ADJUDICATION
MODEL_DISAMBIGUATION
INSUFFICIENT_EVIDENCE
NOT_APPLICABLE
UNRESOLVED
```

无法由证据唯一归因时必须使用 `UNRESOLVED`，不得推断补全。

### 5. DeepSeek 执行契约

* Model ID 只允许 `deepseek-v4-pro`。
* non-streaming Chat Completions。
* `response_format={"type":"json_object"}`，prompt 明确要求只输出 JSON。
* Track B 局部 role/candidate/anchor/abstention 评测显式
  `thinking={"type":"disabled"}`；保持 `ReviewBudgetProfile` 的
  `maxOutputTokens=1500`，不得用 evaluation-only 更大预算绕过 runtime-isomorphic
  admission。
* 只有 `finish_reason=stop`、content 非空且严格 schema valid 才接纳。
* 不保存或回显 `reasoning_content`、完整 prompt 或 raw response。
* 空内容、schema invalid、额外字段、非 stop、timeout 和 upstream error 全部 fail closed。

未存在有效 standing grant 与本次 hash-bound 派生 receipt 时，runner 必须在网络调用
前返回：

```text
EXTERNAL_EGRESS_NOT_AUTHORIZED
```

### 6. `REVIEWING_MODEL` 激活边界

PUBLIC provider 进入 runtime 前必须全部满足：

1. ADR-018/019 已接受并通过实现审计。
2. `ADR-022` standing grant 有效，且派生 receipt 明确覆盖实际输入 hash、dispatch、
   provider call set、每个 outbound request、模型、调用数和时间。
3. TASK-034 candidate/anchor gate 有可接受复跑证据。
4. TASK-036 的 `ModelAssistEligibilityEvaluator`、`FamilyModelCallPlan`、EvidencePacket 与可靠 anchor seam 已进入主线并通过自身门禁。
5. Profile 仅 `purpose/deploymentScope=EVALUATION`，disabled/unbound 初始发布。
6. Provider contract 与 shadow 独立审计 GO。

激活顺序固定为：

```text
disabled adapter
-> EVALUATION shadow（进入 REVIEWING_MODEL，artifact 不改 verdict）
-> shadow 双审计
-> guarded assist
```

guarded assist 仍遵守：

* deterministic HIGH 默认零调用且不可覆盖。
* 只允许具有 EvidenceSlot、可靠 anchor、预算完整的 MEDIUM/CONFLICTED。
* UNKNOWN/no candidate、invalid bundle、无可靠 anchor 零调用。
* 模型失败或无覆盖只令受影响点 `NOT_CONCLUDED / SYS-MODEL-*`。
* 最终点级状态与 Finding 由后端裁判。

## 备选方案

### 方案 A：只做全文盲评并直接选择模型

不采用。输入拓扑与运行时不一致。

### 方案 B：让模型查看 CQCP actual 后解释错误

不采用。不是盲评，会形成确认偏差。

### 方案 C：连通测试成功即进入 REVIEWING_MODEL

不采用。连通性不证明证据 admission、模型职责或数据授权。

## 选择理由

* 双轨协议分别回答“模型理解全文吗”和“当前运行输入足以安全调用吗”。
* hash 与时间顺序使解盲证据可审计。
* fail-closed JSON contract 避免空内容、截断或 reasoning-only 响应进入结果。
* shadow 先行确保模型 artifact 不先于后端裁判获得业务效力。

## 影响

### 正向影响

* 评测可重复、可解盲、可分类。
* 公网外发和运行激活有明确门禁。
* Track A 不能掩盖 TASK-034/TASK-036 缺口。

### 代价与风险

* 三份样本没有外发授权时 DeepSeek 轨道保持阻塞。
* R7 当前语料全部 deterministic HIGH、Track B 只能验证正确零调用时，
  Provider 仍不能实现；seam 存在不等于 eligible ambiguity admission。
* 盲评规模小，只能作为早期证据，不能声明模型整体质量。

## 不做什么

* 不替代人工 ground truth。
* 不自动修改规则、Prompt 或 expected fixture。
* 不声明 Production Ready。

## 回滚与迁移

* 评测工具和输出不影响生产 schema，可独立删除。
* PUBLIC profile/adapter 未发布时无需运行时迁移。
* shadow 出现问题时停用 EVALUATION binding/profile，不改变 deterministic path。

## 验证方式

* 泄漏扫描、hash 可重复性、schema/finish_reason、授权门禁和解盲时间顺序测试。
* 三个盲评 agent prompt 不含历史上下文或 ground truth。
* 独立审计核验 Track A/B 分离和所有激活门禁。

## 关联

* ADR-015、ADR-016、ADR-018
* TASK-EVAL-002
* TASK-034、TASK-036

## 待确认

* 三份脱敏样本的公网外发授权：已确认并逐 input SHA-256 封存。
* 首个独立 12-packet holdout 已先完成人工封印，但 DeepSeek 在第 2 个 eligible call
  schema invalid 并终态 BLOCKED；claim 已消费、未解盲、不得重试。项目负责人已通过
  ADR-023 授权一个 identity/text/value 全新且同样为 9 eligible + 3 controls 的
  successor；严格 schema、可靠 anchor、HIGH 零覆盖、无 Finding 以及
  role/candidate/abstention 与人工答案仍须 100% 匹配。
* 项目负责人已授予 `MILESTONE-MVP-002` standing egress grant；该授权已由
  `ADR-022` 与机器门禁记录，不替代上述人工答案确认。新 holdout 固定为 9 个
  runtime-isomorphic 单 packet 调用，不沿用旧 run-v3 的 6 个 family 分组调用。

## 接受记录

* 2026-07-28：用户明确授权实施《CQCP MVP-002 审核工作台、模型安全与盲态评测计划》。
* 2026-07-29：D1/R7 形成限定三样本的正式 `PASS` 工件；D2
  eligibility/family-plan/runtime-isomorphic EvidencePacket seam 已实现，
  Track B Codex 27/27 正确 abstention。结论仍为
  `NOT_ESTABLISHED_ZERO_ELIGIBLE_SAMPLE`，不放行 Provider。
* 2026-07-29：用户明确授权三份脱敏 runtime evaluation 样本公网外发。官方
  `deepseek-v4-pro` Track A 的三份严格输出已封存，解盲为 27/27 与人工
  ground truth 一致；该结果不替代 Track B admission。用户同时接受建立独立
  脱敏 MEDIUM/CONFLICTED admission 语料及上述全量准入阈值。
* 2026-07-30：旧 18 packet Track B run-v3 解盲结果为 Codex 15/15、
  DeepSeek 6/15、zero-call control 3/3，结论为
  `NO_GO_MODEL_MISMATCH / providerAdmission=NOT_ESTABLISHED`。该已解盲语料只作
  回归证据，不得再次用于独立 admission。
* 2026-08-02：Core subject `90c4ae9a…` 三方全零 GO 并随 PR #37 合并为
  `115be530…`。新 12-packet holdout 已绑定 challenge
  `TBH2-fac56106204c4b93a11befc577369360`、corpus SHA `15d1f845…` 与 challenge
  SHA `37a05c4b…`；当前 `modelInputCreated=false / networkCallAllowed=false`，等待
  项目负责人逐项人工确认。
* 2026-08-02：standing grant 已按项目负责人既有确认写入 `ADR-022` 和精确机器门禁；
  封印前离线契约固定为 `9 calls / 9 eligible inputs / 3 excluded controls`。真实
  ground-truth seal、model input、dispatch、派生 receipt 与网络调用均尚未产生。
* 2026-08-02：上述 holdout 随后完成人工封印并正式执行；DeepSeek 第 2 个 call 因
  `OPINION_SCHEMA_INVALID` fail closed，one-time claim 已消费且无 accepted opinion。
  项目负责人明确授权 TASK-EVAL-003 successor，并要求正式 Codex auditors 固定使用
  `gpt-5.6-sol / xhigh`；失败 holdout 只读保留且不得重试。
* 最终 L3 冻结包仍须通过 CC AUDIT 与两个全新 Codex 独立审计；审计不通过则本实现不得收口。
* 2026-08-03：ADR-024 最终独立 admission 已完成人工先封印和 9×1 dispatch；
  DeepSeek 在第 8 个 call 返回 schema invalid，claim 已消费且无自动重试。该执行不
  解盲、不重试，`providerAdmission=NOT_ESTABLISHED`，A0/A1/A2 继续阻塞。
* 2026-08-03：项目负责人批准 ADR-025 / TASK-EVAL-005，允许在与前四套完全独立的
  合成诊断集上有限诊断、版本化并冻结 prompt/schema，再创建一次第五套独立
  admission。该范围不恢复 TASK-EVAL-004；正式门禁仍为 9×1、3 controls、全维
  100%，失败后不建第六套且不启动 A0/A1/A2。
