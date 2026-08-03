# TASK-EVAL-004：Track B 最终独立准入评测

状态：人工 decisions/seal 已冻结 / Codex blind opinion 已封存 / 唯一正式
DeepSeek claim 在第 8 个 call schema invalid 并终态 BLOCKED / 未解盲、不得重试 /
Provider admission NOT_ESTABLISHED

类型：Evaluation / Model Governance / Provider Connectivity

Task Level：`L3 高风险治理`

Integration unit：`MILESTONE-MVP-002-TRACK-B-FINAL`

优先级：P0

负责人：Codex

创建日期：2026-08-03

来源：项目负责人对 connectivity 诊断结果和新独立 admission 的明确批准、ADR-019、
ADR-022、ADR-023、ADR-024、TASK-EVAL-003 terminal blocked evidence

## 背景

TASK-EVAL-003 的一次性 claim 因错误 Secret 候选导致第一个 DeepSeek call
`AUTHENTICATION_FAILED`，该执行永久不可重试或解盲。随后 L0 诊断以显式仓库外
Secret 验证 `api.deepseek.com`、`deepseek-v4-flash` 和 `deepseek-v4-pro` 可用；
`deepseek-v4-pro` 的 thinking-disabled strict JSON 合成请求返回 200、
`finish_reason=stop` 和精确 sentinel。

项目负责人批准一项新的重大范围：先形成可审计 connectivity gate，再以第四套完全
独立的 12-packet corpus 执行一次最终 Track B admission。该任务不是历史 claim 的
重试，也不降低任何 admission、Secret、Provider 或三方审计门禁。

## 目标

* 用显式 Secret Reference 生成一份最小、hash-bound、无敏感内容的 connectivity
  evidence，并在任何正式 claim 前验证 exact endpoint/model/JSON 通路。
* 建立与前三套 Track B corpus 在 identity、candidate value 和 evidence text 上全部
  disjoint 的 12-packet synthetic corpus。
* 在 evaluator 访问前由项目负责人确认并封印 12 条人工 decisions。
* 由全新 Codex blind evaluator 与 `deepseek-v4-pro` 独立完成 9×1 正式 admission，
  3 个 controls 保持 zero-call。
* 只有全维 100% 且同一 freeze 三方全零 GO 时，建立 Provider admission。

## 非目标

* 不修改、重跑、解盲或迁移 TASK-EVAL-002/TASK-EVAL-003 的任何执行链。
* 不根据历史失败响应改变 prompt/schema、阈值、预算或输出字段。
* 不实现 Provider A0/A1/A2/A3，不进入 `REVIEWING_MODEL`。
* 不修改 CandidateResolver、EvidenceSlot、SourceAnchor、Finding/SYS、状态机或 verdict。
* 不建设通用 Secret 扫描、claim/lease/receipt 或审计传输平台。

## 输入

* 相关文档：`CURRENT_CONTEXT.md`、`docs/ARCHITECTURE.md` 第 12、22.7、22.8、
  22.12 节、`docs/ai-review.md`。
* 相关 ADR：ADR-018、ADR-019、ADR-022、ADR-023、ADR-024。
* 上游任务：TASK-EVAL-002、TASK-EVAL-003、TASK-034、TASK-036。

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
* `decisions/ADR-024-track-b-connectivity-gate-and-final-independent-admission.md`
* `tasks/active/TASK-EVAL-003-track-b-successor-admission.md`

### Optional Context

* TASK-EVAL-002 与前三套 Track B 只读 corpus/evidence。
* `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
* `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`

### Out of Scope

* Provider runtime、数据库 migration、管理 API、普通 Demo binding。
* 真实合同、公网生产合同、人工 ground truth 外发。
* 复用历史人工 decisions 或创建第五套 corpus。

## 范围

### 包含

1. **Connectivity gate**
   * 只接受进程注入 KEY 和固定 `DEEPSEEK_OFFICIAL_EVAL` Secret alias。
   * 固定 `api.deepseek.com:443`，禁用 redirect、代理、任意 endpoint。
   * `/models` 必须包含 `deepseek-v4-pro`。
   * exact model synthetic strict JSON probe 必须 200、`finish_reason=stop`、schema valid。
   * 生成任务专用 immutable evidence；不包含 KEY、Secret hash、raw request/response 或
     reasoning。

2. **独立 corpus 与 preseal**
   * 新建 5 MEDIUM、4 CONFLICTED eligible 与 3 zero-call controls。
   * 与旧 18、失败 holdout、失败 successor 的 task/execution/packet/sample identity、
     candidate values 和 evidence texts 全部 0 overlap。
   * proposed decisions 与 source/corpus 物理分离；人工确认前不生成 model input、
     dispatch 或 claim。

3. **人工封印**
   * challenge 绑定 source/proposal/corpus/review/preseal manifest SHA。
   * 项目负责人逐项接受、修改或拒答 12 条 proposedExpected。
   * confirmation/seal 必须早于两模型输入与任何网络 attempt。

4. **正式双模型 admission**
   * 一个全新 `fork_turns="none"` Codex blind evaluator，只接收 exact prompt/input。
   * DeepSeek 固定 `deepseek-v4-pro`、strict JSON、thinking disabled、non-streaming、
     `finish_reason=stop`、9 个单包 calls 与 3 个 excluded controls。
   * HTTP-started、timeout、UNKNOWN_SIDE_EFFECT、schema/content/finish failure 均零重试；
     只有可证明未发送 payload 的 DNS/pre-send failure 可在冻结上限内安全重试。
   * 双 opinion/receipt 先验证，之后才能解盲。

5. **验证、冻结与三审**
   * Node/Java、Secret/泄漏、时间序、不可重放、zero-call、unblind、freeze verify。
   * 同一 clean subject 上生成一次 immutable freeze。
   * CC AUDIT 与两个全新 Codex auditors 全部
     `GO / P0=0 / P1=0 / P2=0 / blocking=0`。

### 不包含

* Track B GO 后的 Provider Foundation 与 A2 shadow；继续由 TASK-MODEL-002 承接。

## 约束

* 主 Codex 完成全部实现；blind evaluator 与正式 auditors 只读，不修改业务代码。
* 用户只负责人工答案、重大范围变化和最终 merge；standing-grant 内其他动作连续执行。
* 正式 Codex auditors 必须全新、`fork_turns="none"`、`gpt-5.6-sol`、
  `reasoning_effort="xhigh"`，不得复用 blind evaluator。
* Secret 只在进程内使用；不得写入仓库、数据库、日志、Snapshot、artifact 或审计包。
* 模型只给局部意见；最终 point status、Finding 与 verdict 始终由后端裁判。
* 现有 PUBLIC profile 保持 `EVALUATION / disabled / unbound`。

## 交付物

* ADR-024 与本 TASK。
* connectivity gate 工具、tests 和 immutable evidence。
* final source/proposal/corpus/review/challenge/human seal。
* blind input/dispatch/call set/standing-grant receipt、两模型意见和执行 receipts。
* unblind/admission report、verification、freeze、三份审计报告。
* CURRENT_CONTEXT、MVP_TASK_MAP、docs/ai-review 与 changelog 写回。

## 验收标准

1. connectivity evidence 绑定 exact endpoint/model/config，strict JSON synthetic probe
   通过，且不包含 Secret、raw request/response 或 reasoning。
2. final 12 packets 与前三套语料的 identity/value/text overlap 全部为 0。
3. human seal 在任何 evaluator/model input/network attempt 之前完成。
4. call set 精确 9 calls / 9 eligible / 3 zero-call controls，每 call 单 packet。
5. Codex/DeepSeek schema、可靠 anchor、role、candidate、abstention 全部 100%，模型不
   输出 Finding/verdict。
6. 内容/schema/finish/HTTP-started/UNKNOWN_SIDE_EFFECT 不重试，claim 不可重放。
7. Secret sentinel 和实际 KEY 均未出现在 repo/output/log/database/freeze。
8. verification/freeze verify 通过，三份正式审计全部全零 GO。
9. 只有 1–8 全部成立，`providerAdmission=ESTABLISHED`；否则保持
   `NOT_ESTABLISHED`，A0/A1/A2 不启动。

## 测试与验证

* Connectivity：Secret 缺失/alias 错误、401/403/429/5xx/timeout/redirect/proxy、
  `/models` 缺 model、malformed、strict JSON/finish/content failure、create/verify。
* Corpus：四语料 disjointness、schema、counts、preseal/challenge、人工 seal。
* Provider：9×1 partition、zero-call、safe retry、claim、strict opinion、unblind。
* Java：runtime EvidencePacket/eligibility/anchor seam 与 D2/R7 回归。
* 安全：actual KEY、Secret sentinel、raw response/reasoning/ground truth payload absence。
* Git：PowerShell 7、`git diff --check`、clean subject、changed paths/full diff hash。

## 文档更新要求

* L3 connectivity、human seal、正式执行和最终收口门禁更新 `CURRENT_CONTEXT.md`、
  本 TASK、`tasks/MVP_TASK_MAP.md`、`docs/ai-review.md`、ADR 和 changelog。
* 不把 connectivity success 写成 admission 或 Provider activation。

## Next Task Handoff

* admission 与三审 GO 后，下一明确任务为 TASK-MODEL-002 Provider Foundation A0+A1。
* 未 GO 时不生成执行型 handoff。

## 风险

* connectivity 通过但正式 9×1 opinion 任一点 schema/content 或语义不满足 100%。
* 第四套语料与历史文本/值存在隐性重叠。
* Secret alias 配置错误再次导致正式 claim 前认证失败。
* 为追求 GO 而调 prompt、扩预算或降低阈值。

## 待确认

* 12 条人工 decisions：项目负责人已绑定 challenge/corpus/review SHA 全部确认，无修改。
* 本任务的唯一正式 claim 已终态 BLOCKED，不得重试或补跑。项目负责人随后通过
  ADR-025/TASK-EVAL-005 批准独立 schema 诊断和第五套 corpus；该新范围不属于本任务，
  不恢复或改写本任务 evidence。

## 完成记录

* 完成日期：待填写。
* 2026-08-03 connectivity gate：离线 transport/gate 定向 `20/20 PASS`；正式 evidence
  SHA `22d38b1183dc9fd53e5212fbb2b9dd2077234967cea503c8b4e624c50b80d1ac`，
  model IDs 为 `deepseek-v4-flash/deepseek-v4-pro`，exact pro strict JSON probe 为
  200/stop/sentinel matched；`formalAdmissionAffected=false`，实际 KEY 泄漏文件数 0。
* 变更文件：ADR/TASK/项目记忆、secure transport 固定 `/models` GET、connectivity gate
  与 tests、immutable connectivity evidence。
* 测试结果：connectivity transport/gate Node `20/20 PASS`；`git diff --check` 通过。
* 2026-08-03 final preseal：source SHA `1a91aebb…`、proposals SHA `c40e467f…`、
  corpus SHA `ed216d43…`、review SHA `65c775b2…`、manifest SHA `b03fdca4…`。
  相对前三套共 42 prior packets，identity、23 candidate values、19 evidence texts
  overlap 全为 0；challenge `TBF1-0fc18b77754248c28d472b0245a57b81`、SHA
  `74f051c1…`，2026-08-05T02:46:07.009Z 到期。
* 当前精确 Node 回归 `22/22 PASS`；human confirmation/seal、model input、dispatch、
  claim 在 preseal 时均不存在，`admissionNetworkCallAllowed=false`。
* 2026-08-03 human seal：confirmation SHA `b225f36a…`、decisions SHA
  `4fb8dd89…`、human seal SHA
  `8716d66d484af326d994aaaf7f5b2ed7dea1f2c4832b9c0987018bf6c410fc68`；
  `confirmedAt=2026-08-03T06:08:33.574Z`，早于 dispatch 和两模型执行。
* 正式离线身份：model input SHA
  `bd648dd1cd9073217b9d493da2c9e4d12d9c6481eb5f34ccb6c9e1725da3fbba`、
  call set SHA `9881d520121766f3331a0aff6a006511c6fea65f08736b4ba58e1fe59200a57e`、
  dispatch SHA `141d5344a373a90c53c002a8d7e830cbd3fa7a2b853e0fdfb44057faf686d0f2`、
  derived receipt SHA
  `f17018cf126ec4bd1696030e84b267dcdffd654e157cfb6113ef33bd9c500573`；
  9 calls / 9 eligible / 3 excluded controls，preflight 无网络。
* 全新 `fork_turns="none"` Codex blind evaluator 的 9 条 strict opinion 已封存：
  opinion SHA `8abc40637a19b3fa030f117d1b992314ee4650f1b7d1e6d6d8539b626252554b`、
  receipt SHA `70acc344e9578a7be06e64eb5ced274f9251bf48cc868eb7f857e0a679ce4897`；
  未解盲，不得描述为与人工答案一致。
* DeepSeek `deepseek-v4-pro` 唯一正式 claim SHA
  `62a7451eeacc2a5ed7601986277753e7fa790c8d8187f7df26c4852409254610`
  已消费。第 8 个 call（`failedCallIndex=7`）以
  `SCHEMA_INVALID_OR_EMPTY / OPINION_SCHEMA_INVALID` fail closed；前 7 个 calls
  完成但没有形成可接纳的完整 DeepSeek opinion。terminal blocked receipt SHA
  `8b113c74bcef19f06020e1a2b6f2265123d134ce93f8562b139d0a13f0daa47f`，
  `networkAttempted=true / completedCallCount=7 / automaticRetryPerformed=false`。
* 终态验证：post-seal phase-appropriate Node `44/44 PASS`；actual KEY/forbidden Provider
  field 泄漏文件数为 0；final claim、model input 与 Codex opinion contract 重建通过；
  DeepSeek opinion/unblind/audit 均不存在。加入只适用于 model-dark 阶段的 preseal test
  时为 `44/45`，唯一失败是其“postseal artifacts 必须不存在”断言，属于证据测试阶段
  错配；本轮依硬停止规则不修改。
* 遗留问题：Provider admission 固定为 `NOT_ESTABLISHED`；同一 corpus/claim 不重试、
  不换模型/KEY、不调 prompt、不解盲、不生成 freeze 或三方正式审计；A0/A1/A2 保持
  阻塞，等待项目负责人对 Milestone 终止或另立重大范围作出决策。
* Integration unit / PR：`MILESTONE-MVP-002-TRACK-B-FINAL`，终态 BLOCKED，无 PR。
* 独立审计触发依据：评测正确性、Provider connectivity、Secret、公网外发与模型职责。
