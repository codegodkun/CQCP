# TASK-EVAL-003：Track B 后续独立准入评测

状态：人工 decisions 与 seal 已冻结 / successor 唯一正式 DeepSeek claim 在第 1 个
call 认证失败并终态 BLOCKED / 未解盲、不得重试 / Provider admission 未建立

类型：Evaluation / Model Governance / Data Governance

Task Level：`L3 高风险治理`

Integration unit：`MILESTONE-MVP-002-TRACK-B-SUCCESSOR`

优先级：P0

负责人：Codex

创建日期：2026-08-02

来源：项目负责人对 Track B 后续独立评测的明确授权、ADR-019、ADR-022、ADR-023、
`TASK-EVAL-002`

## 背景

`TASK-EVAL-002` 的模型未见 12-packet holdout 已先完成人工封印并启动一次正式
admission。Codex blind evaluator 返回严格结构化意见；DeepSeek
`deepseek-v4-pro` 完成第 1 个 call 后，第 2 个 call 以
`SCHEMA_INVALID_OR_EMPTY / OPINION_SCHEMA_INVALID` fail closed。one-time claim
`da3f1eb1770a1eeb25d62527db58a663834549ec6cdd7be41e2e244143f329c9`
已消费，terminal blocked receipt
`f79060feb0577caec735cd12ffbfdb7a8edd8d36b42c2dd8ec2cf3aa4c49c6d2`
证明没有自动重试。该 holdout 未解盲、不可重试，Provider admission 保持
`NOT_ESTABLISHED`。

项目负责人现明确授权另立后续 Track B 评测治理任务。用户只负责人工答案、重大
范围变化和最终 merge；standing grant 范围内的模型调用、CC、Codex auditor、
验证、freeze、commit、push、PR、CI 和可证明无副作用的安全重试均由主 Codex
连续执行，不再逐轮请求确认。

## 目标

* 建立一套与所有已解盲或失败 corpus 在 identity、候选文本和值上独立的 12-packet
  Track B successor corpus。
* 在任何 evaluator 访问前取得项目负责人的逐项人工 decisions 并封印。
* 使用同一冻结 prompt/schema，由一个全新 Codex blind evaluator 与 DeepSeek
  `deepseek-v4-pro` 独立执行一次 runtime-isomorphic admission。
* 只有 schema、可靠 anchor、role、candidate、anchor、abstention 和三个 zero-call
  controls 全部 100% 时，才建立 Provider admission。
* 对同一 immutable subject 完成 CC AUDIT 与两个全新 Codex auditor 的三方全零审计。

## 非目标

* 不重试、补跑、解盲或修改 `TASK-EVAL-002` 的失败 holdout。
* 不基于未保存的 raw response 推测失败字段或针对该响应调 prompt。
* 不修改 CandidateResolver、EvidenceSlot、SourceAnchor、Finding/SYS、状态机或业务
  verdict。
* 不实现 Provider A0/A1/A2，不进入 `REVIEWING_MODEL`，不实现 A3 guarded assist。
* 不建设新的通用 claim、lease、receipt 或审计传输平台。

## 输入

* 相关文档：`CURRENT_CONTEXT.md`、`docs/ai-review.md`、
  `docs/ARCHITECTURE.md` 第 12、22.7、22.8、22.12 节。
* 相关 ADR：ADR-019、ADR-022、ADR-023。
* 上游任务：TASK-EVAL-002、TASK-034、TASK-036。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `docs/ARCHITECTURE.md` 第 12、22.7、22.8、22.12 节
* `docs/ai-review.md`
* `decisions/ADR-019-blind-evaluation-and-model-activation-boundary.md`
* `decisions/ADR-022-mvp002-standing-egress-grant-and-derived-receipt.md`
* `decisions/ADR-023-track-b-successor-independent-admission.md`
* `tasks/active/TASK-EVAL-002-blind-semantic-evaluation.md`

### Optional Context

* `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
* `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`
* 旧 18-packet run-v3 与失败 12-packet holdout 的只读冻结证据。

### Out of Scope

* Provider runtime、数据库 migration、管理 API、普通 Demo binding。
* 真实未脱敏合同、公网生产合同、人工 ground truth 外发。
* 第二次 successor corpus 或同一 successor 的内容/schema 重试。

## 范围

### 包含

1. **AFK：独立 corpus 与 pre-seal**
   * 新建 5 个 MEDIUM、4 个 CONFLICTED eligible packet 和 3 个分别覆盖
     deterministic HIGH、invalid bundle、无可靠 anchor 的 zero-call control。
   * taskId、executionId、packetId、sampleId、候选文本和值不得与旧 18-packet corpus
     或失败 12-packet holdout 重用。
   * source signals 与 Codex 非权威 proposed decisions 物理分离；自动证明语料集合
     identity/text/value disjoint。
   * 在人工确认前只生成 corpus、review draft/Markdown、preseal manifest 和 challenge；
     不生成 model input、dispatch、receipt 或 claim。

2. **HITL：人工答案**
   * 项目负责人逐项接受、修改或拒答 12 条 proposedExpected。
   * confirmation 必须绑定 challenge、corpus、review、preseal manifest 和 decisions
     SHA-256；Codex/DeepSeek 在 seal create/verify 前均不可访问 model input。

3. **AFK：一次正式双模型执行与解盲**
   * successor 固定为 9 个 eligible 单 packet call；3 个 controls 绝不进入调用集合。
   * 继续使用冻结 prompt SHA
     `8a06b5177e7df8ca6097aaa752e2b089a0aa28e4f313ef158438627427aafe10`
     和既有严格 output schema，不根据失败 holdout 调参。
   * Codex evaluator 必须是全新 `fork_turns="none"` agent，只接收 exact prompt 与
     blind model input，不得读取仓库或历史上下文。
   * DeepSeek 固定 `api.deepseek.com:443`、`deepseek-v4-pro`、non-streaming、
     strict JSON、thinking disabled、`finish_reason=stop` 和现有预算。
   * 只有明确证明 HTTP payload 尚未发送且 Provider 无副作用的 DNS/pre-send 失败，
     才允许在同一冻结执行内执行有上限的安全重试并写 receipt；HTTP 已开始、
     timeout/UNKNOWN_SIDE_EFFECT、schema/content invalid、非 stop 或 accepted response
     均不得自动重试。
   * 两份 blind opinion 和执行 receipt 均验证后才读取 human seal。任何维度不为
     100% 时固定 `Provider admission NOT_ESTABLISHED`，不调 prompt、不重跑同一
     successor。

4. **AFK：验证、冻结与三审**
   * 完成 Node/Java 契约、泄漏扫描、Secret sentinel、hash/时间顺序、claim/receipt、
     zero-call、不可重放和 unblind tests。
   * 生成一次 immutable freeze，绑定 base/HEAD/tree/status、changed paths/full diff、
     所有测试原始 console、human seal、model input/dispatch/call set、两份意见、claim、
     receipts、unblind report、TASK/ADR/Memory Writeback。
   * CC AUDIT 使用 CC 当前连接的 `deepseek-v4-flash`，只读、不得修改。
   * 两个 Codex auditor 均必须全新、`fork_turns="none"`，显式使用
     `model="gpt-5.6-sol"`、`reasoning_effort="xhigh"`；一个覆盖代码/API/架构，
     一个覆盖测试/安全/证据。不得使用默认或降级模型，不得复用 blind evaluator。
   * 三份报告必须绑定同一 subject，全部
     `GO / P0=0 / P1=0 / P2=0 / blocking=0`。

### 不包含

* Track B GO 后的 Provider Foundation 与 shadow；它们继续由 TASK-MODEL-002 和
 既定后续 integration unit 承接。

## 约束

* 主 Codex 完成全部实现；CC 与 Codex subagents 只做盲评或独立只读审计，不修改
  业务代码。
* 用户只负责人工答案、重大范围变化和最终 merge；其余本任务内正常动作不重复请求
  授权。
* standing grant 继续遵守 ADR-022；KEY 仅进程内使用，禁止写入仓库、数据库、日志、
  Snapshot、artifact 或审计包。
* raw Provider response、reasoning、人工 ground truth、CQCP actual/expected 和最终
  Finding/verdict 不得进入模型 payload。
* 模型只输出 role/candidate/anchor/abstention 意见，后端裁判边界不变。
* 当前失败 holdout 的 claim/blocked receipt 不得删除、覆盖、迁移或伪装成未执行。
* TASK 是计划与责任边界，不自动等于独立 merge；最终 merge 仍由用户明确执行或授权。

## 交付物

* TASK-EVAL-003 与 ADR-023。
* successor source/proposal/corpus/review/challenge/human seal。
* blind prompt/input/dispatch/call set/standing-grant derived receipt。
* Codex 与 DeepSeek opinion/receipts；不含 raw Provider response 或 reasoning。
* unblind/admission report、完整 verification、immutable freeze、三份全零审计报告。
* CURRENT_CONTEXT、MVP_TASK_MAP、父 TASK、docs/ai-review 和 changelog 写回。

## 验收标准

1. successor 12 个 packet 与两套历史/失败 corpus 的 identity、候选文本和值集合完全
   disjoint；无生产合同文本。
2. human seal 的 confirmedAt 早于 model input/dispatch、两模型 startedAt 和任何网络
   attempt；输入中不存在 ground truth、actual/expected、Finding/verdict。
3. provider call set 精确为 9 calls / 9 eligible inputs / 3 excluded controls；每个
   call 只含一个 runtime-isomorphic packet。
4. Codex 与 DeepSeek 均 strict schema 100%；role/candidate/anchor/abstention 与人工
   decisions 全部 100%；可靠 anchor 100%；三个 controls 100% zero-call；无 Finding。
5. 内容/schema/finish_reason/HTTP-started/UNKNOWN_SIDE_EFFECT 失败均不重试；只有
   receipt 证明无 Provider 副作用的 pre-send 失败可执行冻结上限内安全重试。
6. Secret sentinel 和实际 KEY 均未出现在仓库、输出、日志、数据库或冻结包。
7. verification 与 freeze verify 全部通过；CC AUDIT 和两个指定模型的全新 Codex
   auditor 全部全零 GO。
8. 只有上述全部成立，`providerAdmission=ESTABLISHED`；否则保持
   `NOT_ESTABLISHED`，A0/A1/A2 不启动。

## 测试与验证

* Node：corpus contract、disjointness、human seal、dispatch、standing grant、strict
  opinion schema、safe-retry boundary、runner、unblind、freeze verifier。
* Java：runtime EvidencePacket/schema/zero-call seam 与现有 D2/R7 回归。
* 安全：输入泄漏扫描、Secret sentinel、TLS/endpoint/proxy/DNS、不可重放、
  UNKNOWN_SIDE_EFFECT、raw response/reasoning absence。
* Git：PowerShell 7、`git diff --check`、changed-path manifest、blob/hash 重建。

## 文档更新要求

* L3 关键门禁和最终收口更新 `CURRENT_CONTEXT.md`、本 TASK、
  `tasks/MVP_TASK_MAP.md`、`docs/ai-review.md`、ADR-019/022/023 和
  `changelog/2026-08.md`。
* 不把未执行、未解盲或未审计状态写成完成事实。

## Next Task Handoff

* admission 三审 GO 后，下一明确任务为 TASK-MODEL-002 Provider Foundation A0+A1；
  未 GO 时不生成执行型 handoff。

## 风险

* DeepSeek 再次 schema/content failure，导致 Provider admission 仍不能建立。
* successor corpus 与旧语料语义重叠形成隐性记忆污染。
* 为追求 GO 而放宽 schema、增加输出预算或重试内容错误，破坏 runtime-isomorphic
  证据。
* 审计员模型或上下文配置错误导致独立性失效。

## 待确认

* 12 条人工 decisions：项目负责人已绑定 challenge/corpus/review SHA 全部确认，无修改。
* 重大范围变化：无；如需改变 packet 数、prompt/schema/model、准入阈值或重试内容
  错误，必须重新提交项目负责人。

## 完成记录

* 完成日期：待填写。
* 2026-08-02 pre-seal：source SHA `84f04dbf…`、proposed decisions SHA
  `4e813049…`、corpus SHA `a6090054…`、review SHA `f8c4c6a0…`、manifest SHA
  `1a22f599…`。三语料 disjointness 为 identity/value/evidence text 全部 0 overlap。
* challenge：`TBS1-5bfbe8b667944e7182df3ec537d45725`，challenge SHA
  `0bd06f76212b902d090ec16db77a404af733f8a83da5eb42e3fba78570bfbb4f`，
  2026-08-04T15:54:39.897Z 到期。
* 变更文件：successor source/proposals、profile contract、preseal/challenge 工具、
  TASK/ADR/项目记忆；model input/dispatch/claim 不存在。
* 测试结果：旧 holdout + successor contract/disjointness/preseal Node `6/6 PASS`；
  `git diff --check` 通过。
* 2026-08-03 post-seal tooling：在临时 fixture 中完成 successor human confirmation
  记录、human seal、9×1
  dispatch、Codex opinion seal、DeepSeek strict runner、安全 DNS/pre-send 单次重试、
  HTTP-started 零重试、双意见先验验证和 unblind GO/NO_GO evaluator；正式目录仍无
  confirmation/seal/model input/dispatch/claim。机器时间序门禁固定为
  `human confirmation <= dispatch <= evaluator`；unblind 可 verify-only 重建，freeze
  工具在临时 clean Git subject 上证明可绑定 HEAD/tree/full diff/console/evidence 和
  `CC + 两个 gpt-5.6-sol/xhigh` 审计配置。相关 Node 精确回归 `45/45 PASS`，Java
  runtime seam `1/1 PASS`，正式 freeze 尚未生成。
* 扩大历史 `*track-b*` sweep 为 `63/79 PASS`；16 项失败绑定未纳入当前 worktree 的
  旧 archive/opinion fixture 和另一套旧 admission prompt hash，不作为本任务完成
  证据，正式 freeze 前须保留精确测试清单并明确历史排除边界。
* 2026-08-03 正式 postseal：human confirmation SHA `38b7d534…`、human seal SHA
  `6ad4a1e4…`、model input SHA `654168c6…`、call set SHA `79a3ab0b…`、dispatch SHA
  `f9d6cd05…`、derived receipt SHA `1788387d…`。Codex 盲评 9/9 strict schema
  accepted，opinion SHA `eb0face7…`、receipt SHA `594f503f…`，未解盲。
* DeepSeek `deepseek-v4-pro` 的唯一正式 claim SHA `45f1d5ea…` 已消费；第 1 个 call
  终态 `AUTHENTICATION_FAILED`，blocked receipt SHA `03f46cb8…`，
  `networkAttempted=true / completedCallCount=0 / automaticRetryPerformed=false`。
  未持久化 raw response、reasoning 或 Secret；同一 successor 不重试、不换模型/KEY
  补跑、不解盲、不生成 freeze 或正式三审。
* 遗留问题：Provider admission 固定为 `NOT_ESTABLISHED`；A0/A1/A2 保持阻塞。继续
  admission 需要项目负责人批准新的重大评测治理范围，且不得复用本次 corpus/claim。
* Integration unit / PR：`MILESTONE-MVP-002-TRACK-B-SUCCESSOR`，待填写。
* 独立审计触发依据：评测正确性、模型职责、公网外发和 Secret 安全。
