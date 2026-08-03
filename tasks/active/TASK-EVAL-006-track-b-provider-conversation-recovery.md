# TASK-EVAL-006：Track B Provider 会话投影有限恢复

状态：ACTIVE / `ADMISSION_ESTABLISHED` / `R5_FREEZE_PENDING`

类型：Evaluation / Model Governance / Provider Conversation Recovery

Task Level：`L3 高风险治理`

Integration unit：`MILESTONE-MVP-002-TRACK-B-PROVIDER-RECOVERY`

优先级：P0

负责人：Codex

创建日期：2026-08-03

来源：项目负责人对有限恢复范围的明确批准、ADR-026、TASK-EVAL-005 的
`SEALED_NO_GO_MODEL_MISMATCH` 证据和只读根因诊断

## 目标

- 版本化建立不泄露 runtime routing/诊断标签的 model-facing EvidencePacket projection v3。
- 通过旧第五套 4 个 CONFLICTED packet 的受控 non-admission diagnostic 证伪或支持
  “会话投影偏置”根因。
- 只有诊断 4/4 后，建立第六套完全独立 12-packet corpus，并在人工先封印后执行唯一
  9×1 `deepseek-v4-pro` admission。
- 全维 100% 与三方全零 GO 后才恢复 A0/A1/A2；失败则终止且不建第七套。

## Task Context

### Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- 本任务包
- `docs/ARCHITECTURE.md` 第 12、22.7、22.8、22.12、22.13 节
- `docs/ai-review.md`
- `decisions/ADR-019-blind-evaluation-and-model-activation-boundary.md`
- `decisions/ADR-022-mvp002-standing-egress-grant-and-derived-receipt.md`
- `decisions/ADR-025-track-b-schema-stability-diagnosis-and-fifth-admission.md`
- `decisions/ADR-026-track-b-provider-conversation-recovery.md`
- `tasks/active/TASK-EVAL-005-track-b-schema-stability-and-fifth-admission.md`

### Optional Context

- 前五套 Track B 只读 corpus/evidence。
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`

### Out of Scope

- 重试、补跑、修改或恢复 TASK-EVAL-005 claim/opinion/report/seal。
- Provider A0/A1/A2/A3、`REVIEWING_MODEL`、PUBLIC binding、数据库 migration。
- 修改 RuntimeEvidencePacket、EvidenceSlot、CandidateResolver、SourceAnchor、
  Finding/SYS、状态机或 verdict。
- raw response/reasoning/Secret/人工 ground truth 外发或持久化。
- 第七套 corpus、通用评测平台或审计传输平台。

## 分阶段范围与停止点

### R0：治理冻结

- 接受 ADR-026，登记 TASK-EVAL-006，纠正项目记忆中的误写 BLOCKED 叙事。
- 明确 TASK-EVAL-005 历史终态不可变，当前状态为
  `RECOVERY_ACTIVE / ADMISSION_NOT_ESTABLISHED`。
- 验收：文档引用一致；无网络；旧 evidence hash 未变化。

### R1：projection/prompt/schema v3（TDD）

- 以逐条 RED→GREEN 建立 model-facing projection 公共接口。
- 允许字段仅限 ADR-026；禁止 runtime routing/diagnostic/identity 与 ground truth/
  Finding/verdict 字段。
- 新建 prompt/schema/request builder v3；不得覆盖 v2。
- 验收：允许字段、禁止字段、可靠 anchor、单包 request 和 strict opinion validator 测试
  全部通过；旧测试不回退。

### R2：4-packet 受控恢复诊断

- 从第五套 4 个旧 CONFLICTED packet 派生 v3 input，冻结 input/call-set/dispatch/receipt。
- 恰好 4 个 `deepseek-v4-pro` 单包 calls，零自动重试。
- 验收：schema、可靠 anchor、role、candidate、anchor、abstention 4/4，且与既有人工
  decisions 一致。
- 停止：任一失败或 `UNKNOWN_SIDE_EFFECT`，立即终止恢复，不创建第六套。

### R3：第六套 corpus 与人工 challenge

- 生成 5 MEDIUM + 4 CONFLICTED eligible、3 zero-call controls。
- 相对前五套和恢复诊断的 identity/value/evidence overlap 全部为 0。
- proposal/source 与 model input 物理分离，生成实际 challenge/corpus/review hash。
- 停止：只向项目负责人提交 12 条 proposedExpected 和实际 hash；人工确认前不得访问
  evaluator 或 Provider。

### R4：唯一正式 admission

- 人工 seal 必须早于任何 evaluator/model access。
- 全新 Codex blind evaluator；DeepSeek 为 `deepseek-v4-pro`、9×1、3 controls zero-call、
  strict JSON、non-streaming、thinking disabled、1500 tokens、HTTP-started 零重试。
- 验收：schema、reliable anchor、role、candidate、anchor、abstention、controls 全部 100%。
- 停止：任一失败即终止，不建第七套，不启动 A0/A1/A2。

### R5：验证、冻结与三审

- admission 100% 后运行完整 verification，冻结同一 clean immutable subject。
- 派发全新 CC AUDIT 与两个全新 `fork_turns="none"`、
  `gpt-5.6-sol/xhigh` Codex auditors；盲评 agent 不得复用为 auditor。
- 验收：三份均 `GO / P0=0 / P1=0 / P2=0 / blocking=0`，绑定同一 hash；CI 通过且
  subject 未漂移后才进入已授权 PR/merge 流程。

## 可证伪验收断言

1. TASK-EVAL-005 既有 claim/opinion/report/seal bytes 与 hash 全部不变。
2. v3 model input 不含 ADR-026 禁止字段，且只含允许字段。
3. 旧 4-packet diagnostic 精确 4 calls、零重试、4/4 全维正确；否则无第六套。
4. 第六套与前五套及诊断集 identity/value/evidence overlap 均为 0。
5. 人工 seal 早于任何 Codex evaluator 与 Provider access。
6. 正式 call set 精确 9×1，3 controls zero-call；所有维度 100%。
7. 新网络调用总量 `<=13`；每次有实际 hash-bound receipt；无 Secret/raw Provider 内容。
8. 模型不生成或改变 Finding/verdict，PUBLIC profile 仍 disabled/unbound。
9. 任一门禁失败不启动 A0/A1/A2，不创建第七套。

## 回滚边界

- R2 网络前可删除未执行 tooling；已执行 evidence 只读保留。
- R2/R4 claim 一经创建不可恢复为未执行；失败后任务终态 BLOCKED。
- 无数据库、公共 API、runtime binding 或最终裁判回滚。

## Memory Writeback

- R0、R2、人工封印、R4 和最终审计为关键写回点。
- 更新本 TASK、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、
  `docs/ai-review.md`、ADR-019/022/025/026 与 `changelog/2026-08.md`。
- 不得声明 Production Ready 或解锁 TASK-028/031/032。

## 当前待确认

- 无人工门禁待确认。R0-R4 已完成；standing grant 覆盖 R5 的合规审计调用。
- A0/A1/A2 仍等待 R5 完整验证、immutable freeze 与三方全零 GO，不因 admission 单独解锁。

## 阶段完成记录

### R0/R1：治理与 v3 会话契约

- ADR-026 与本任务已接受，状态纠正为
  `RECOVERY_ACTIVE / ADMISSION_NOT_ESTABLISHED`；TASK-EVAL-005 历史 evidence 不变。
- 新增 model-facing projection/prompt/schema/request builder v3。Provider payload 排除
  task/execution/sample identity、coverage/diagnostic/reason/admission label、人工答案、
  actual/expected、Finding/verdict；只保留语义选择所需字段。
- projection/request/opinion/diagnostic/runner/seal 定向回归通过；合法 JSON 字段顺序不再
  被误当 schema failure，字段集合仍严格，duplicate key/非 stop/伪 anchor fail closed。

### R2：4-packet non-admission diagnostic

- 冻结 input `809c244e…`、call-set `892e6b4f…`、dispatch `338d1168…`、derived receipt
  `6893767f…`，精确 4 calls；payload 不含 runtime routing/diagnostic label 或人工答案。
- `deepseek-v4-pro` 四个单包 call 全部 strict schema accepted，无自动重试；claim
  `14715db0…`、opinion `eef82a45…`，未保存 raw response/reasoning/Secret。
- 本地解盲 report `a1472689…`、seal `fedc4e21…`；schema、可靠 anchor、role、candidate、
  anchor、abstention 全部 4/4，状态 `SEALED_GO_RECOVERY_DIAGNOSTIC`。
- 该 GO 只允许进入 R3；`providerAdmissionEstablished=false`、A0/A1/A2 仍禁止。

### R3：第六套 corpus 与人工 challenge

- source `47cf4a04…`、proposal `2cb18d75…`、corpus `3a988a9a…`、review draft
  `8edd0ea4…`、review document `7ff431cc…`、preseal manifest `937d6500…`。
- 相对前五套 corpus 及两份 diagnostic input 的 task/execution/packet/sample identity、
  candidate value、evidence text overlap 全部为 0；5 MEDIUM + 4 CONFLICTED eligible、
  3 zero-call controls。
- challenge `TB61-8e5e524790c9457a80d8e2fe2433b63d` / SHA `328f4349…`，到期
  `2026-08-05T09:37:54.991Z`；项目负责人绑定 challenge/corpus/review SHA 接受全部
  12 条 proposedExpected。confirmation `814e8146…`、human seal `0f23b9eb…`，封印早于
  model input/evaluator/network access。

### R4：唯一正式 admission GO

- 冻结 model input `bb8984ed…`、call set `1121a345…`、dispatch `a427e052…`、derived
  receipt `aa716878…`；精确 9×1 calls，3 controls zero-call。
- 全新 `gpt-5.6-sol/xhigh` Codex blind opinion `cccf0a4d…`；DeepSeek claim
  `600a821a…`、opinion `e863596a…`，9 calls 全部 strict schema accepted、零自动重试，
  未持久化 raw response/reasoning/Secret。
- 本地解盲后两位 evaluator 的 schema、可靠 anchor、role、candidate、anchor、abstention
  全部 9/9，controls 3/3；report `395c0d38…`、seal `2d879b13…`，状态
  `SEALED_GO_TRACK_B_RECOVERY_ADMISSION`，`providerAdmissionEstablished=true`。
- 该 GO 只允许进入 R5；A0/A1/A2 在 verification/freeze/三方全零 GO 前仍禁止。

### R5：bounded verification PASS / freeze pending

- phase-appropriate Node `70/70`、verification builder unit `1/1`、Java Track B runtime
  seam `1/1`；admission seal 原字节重建一致。
- 61 个 hash-bound 文本文件 CR=0，实际 KEY 泄漏文件=0、禁止 Provider payload 文件=0，
  `git diff --check=0`。全历史 Node glob 不是 fresh worktree 独立入口，失败日志只作为
  non-gating harness 诊断保留，不替代明确列出的 phase-appropriate suite。
- 最终 verification result/console manifest 已重建并复验 PASS；下一步创建 clean commit
  并冻结 immutable subject。尚未派发三审，A0/A1/A2 仍禁止。

## Next Task Handoff

- 当前在 R5 freeze 前：创建 clean commit 并冻结 immutable subject；再派发一个全新
  CC AUDIT 与两个全新 `fork_turns="none"`、
  `gpt-5.6-sol/xhigh` Codex auditors。任一失败立即停止，不组合旧 GO。
