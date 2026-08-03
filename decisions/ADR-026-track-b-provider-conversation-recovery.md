# ADR-026：Track B Provider 会话投影有限恢复

状态：Accepted

日期：2026-08-03

## 背景

TASK-EVAL-005 的第五套正式 admission 已不可变终态为
`SEALED_NO_GO_MODEL_MISMATCH`。其 9 个 eligible call 均通过 strict schema，
但 `deepseek-v4-pro` 在 4 个 `CONFLICTED` packet 上全部选择 abstain，导致
role/candidate/anchor/abstention 各为 5/9。相同结构的 5 个 `MEDIUM` packet 全部正确。

只读根因诊断发现，旧 Provider request 将 runtime admission/routing 元数据一并发送给
模型，包括 `coverageStatus=AMBIGUOUS`、`diagnosticCode=SYS_ROLE_CONFLICT`、
`reasonCodes=ELIGIBLE_CONFLICT_LOCAL_CONTEXT` 和 `conflicted` identity；prompt 同时允许
abstention，却没有说明这些字段只是调用资格信号。这构成可证伪的模型会话投影偏置，
不能由模型能力不足或合同复杂度直接推出。

项目负责人已澄清此前“Provider BLOCKED”是询问而非终止指令，并明确批准一次新的、
有限恢复范围。TASK-EVAL-005 的历史 claim、opinion、report、seal 与 NO-GO 结论仍不可变。

## 决策

### 1. 状态与替代边界

- Provider 路径改记为 `RECOVERY_ACTIVE / ADMISSION_NOT_ESTABLISHED`。
- 本 ADR 仅替代 ADR-025 的“不得创建第六套、不得再做 Provider recovery”未来边界；
  不替代或改写 TASK-EVAL-005 的历史终态。
- PUBLIC profile 继续 `EVALUATION / disabled / unbound`；A0/A1/A2 在新 admission GO 前
  不得启动，A3 仍不属于本范围。

### 2. Model-facing EvidencePacket projection v3

Provider 只能收到从 runtime `EvidencePacket` 派生的最小语义投影：

- `packetId`、`family`、`reviewPointCode`、`requestedRole`；
- `candidateOccurrences` 中的 `occurrenceId`、`value`、证据原文和可靠 anchor；
- `budget` 与 `requiredOutput`。

下列字段不得进入模型输入：

- `taskId`、`executionId`、`sampleId`、identity class；
- `coverageStatus`、`diagnosticCode`、`reasonCodes`；
- `modelCallAllowed`、admission 状态及 `MEDIUM` / `CONFLICTED` /
  `SYS_ROLE_CONFLICT` 等 routing label；
- 人工 ground truth、CQCP actual/expected、Finding、verdict。

该投影只改变 Provider 会话边界，不改变 runtime `EvidencePacket`、EvidenceSlot、
CandidateResolver、SourceAnchor、SYS/Finding 或后端裁判。

### 3. Prompt/schema/request v3

- prompt 明示 packet 已由后端判定 eligible；不能仅因多个候选而 abstain。
- 选择原则为：直接约定当前 operative target 的条款优先于明确的引用、历史、示例或
  非目标记录。
- 只有真正同等候选、证据不足或 requested role 未覆盖时才 abstain。
- 模型只输出 role/candidate/anchor/abstention，不生成 Finding/verdict。
- projection、prompt、schema、request builder 与 opinion 均使用新的 v3 标识和 hash；
  v2 文件与历史 evidence 不得覆盖。

### 4. 受控恢复诊断

- 只使用第五套中 4 个旧 `CONFLICTED` packet 的语义内容，经 v3 projection 生成新输入。
- 恰好 4 个 `deepseek-v4-pro` 单包 calls；strict JSON、non-streaming、thinking disabled、
  1500 tokens、HTTP-started 零重试。
- 该步骤是 non-admission diagnostic，不恢复旧 claim，不改变旧 opinion/report/seal。
- 4/4 必须通过 schema、可靠 anchor、role、candidate、anchor、abstention，并匹配已封印的
  既有人工 decisions；否则恢复终止，不创建新 corpus。

### 5. 第六套独立 admission

只有恢复诊断 4/4 后才允许：

- 建立一套与前五套和恢复诊断均独立的 12-packet corpus：5 MEDIUM、4 CONFLICTED、
  3 zero-call controls；
- 模型访问前，由项目负责人绑定实际 challenge/corpus/review hash 确认 12 条人工答案；
- 使用全新 Codex blind evaluator 与 `deepseek-v4-pro` 执行唯一 9×1 admission；
- schema、可靠 anchor、role、candidate、anchor、abstention、controls 必须全部 100%；
- 任一失败即终止，不建第七套。

新公网预算上限为 13 calls（4 diagnostic + 9 formal）。正常安全重试仍受冻结契约约束；
HTTP-started、schema/content/finish/timeout 或 `UNKNOWN_SIDE_EFFECT` 均不得自动重发。

### 6. 授权与证据

ADR-022 standing grant 继续有效，本任务在该授权内具备明确调用资格，无需逐 call 聊天
确认。每次外发仍必须生成绑定实际 input、dispatch、provider-call-set、模型、调用数与
时间的 hash-bound receipt。Secret 仅在进程内使用；不得保存 raw response、
`reasoning_content`、raw KEY、人工 ground truth 或最终 Finding/verdict。

## 验收与恢复条件

只有以下全部满足，Provider admission 才可改为 `ESTABLISHED` 并恢复 A0/A1/A2：

1. v3 projection 的禁止字段/允许字段机器测试通过；
2. 4-packet 受控诊断 4/4；
3. 新 12-packet corpus 的人工先封印与唯一 admission 全维 100%；
4. 完整 verification、immutable freeze、CC AUDIT 与两个全新
   `fork_turns="none"`、`gpt-5.6-sol/xhigh` Codex auditors 全部
   `GO / P0=0 / P1=0 / P2=0 / blocking=0`；
5. 受审 subject 与 CI 内容未漂移。

## 回滚边界

- 网络前可删除未执行的 v3 tooling；已执行 receipt/evidence 只读保留。
- 诊断或正式 admission 失败均保持 `ADMISSION_NOT_ESTABLISHED`，不启动 A0/A1/A2。
- 不修改数据库、公共 API、PUBLIC binding、普通 Demo 或最终裁判。

## 影响

- 正向：消除 runtime routing/诊断元数据对模型语义选择的提示偏置，同时保留后端资格
  判断和 fail-closed 边界。
- 风险：projection 过窄可能丢失必要语义；通过旧 4-packet 诊断和全新独立 admission
  分别验证恢复假设与泛化能力。
- 不声明 Production Ready，不解锁 TASK-028/031/032。

## 执行证据（2026-08-03）

- 4-call non-admission diagnostic 六维 4/4，seal `fedc4e21…`。
- 第六套人工 seal `0f23b9eb…` 早于模型访问；Codex/DeepSeek 六维均 9/9，controls 3/3。
  首次派生 seal `2d879b13…` 的 `providerAdmissionEstablished=true` 早于本 ADR 第 4、5
  项完成，属于未生效的历史派生标志，不构成架构层准入或 A0/A1/A2 解锁证据；该 seal、
  DeepSeek claim/opinion 与失败审计均只读保留。
- 首次 R5 subject `9dddbe43…` 因 Codex evaluator 时序证据不足和 Java seam 未直接覆盖
  recovery-v1 被独立审计拒绝。项目负责人批准的有限修复不改变本 ADR 的 Provider、
  payload、模型职责或网络预算：新 `fork_turns=none / gpt-5.6-sol/xhigh` evaluator 由
  human seal 之后创建的 execution claim、零文件 readiness、launch/completion receipt
  绑定，只读隔离目录；DeepSeek claim/opinion 原字节复用且不重跑。
- revalidated R5 seal `927eb673…` 与 subject `e77a8635…` 未通过三审：全新代码/架构
  auditor 返回 `NO_GO / P1=1 / P2=3 / blocking=4`，因此该 freeze 与全部同轮审计结果
  失效并保留。findings 为 opinion 顶层字段未完全 fail closed、DeepSeek reuse 未从旧
  seal 验证、架构/准入状态冲突及任务地图事实滞后。
- R6 仅修复上述四项：Codex v4 envelope 使用顶层 exact-field-set；DeepSeek claim/opinion
  必须与旧 seal hash 一致；更正后的 revalidation report/seal 分别为
  `efba63ff… / 70339354…`，状态
  `SEALED_GO_TRACK_B_RECOVERY_EVALUATION_REVALIDATED_PENDING_AUDIT`，明确
  `modelEvaluationPassed=true / providerAdmissionEstablished=false /
  admissionDecisionPendingAudit=true`。DeepSeek claim/opinion 未重跑、未改写。
- R6 完整 verification 已通过：Node `56/56`、verification builder `1/1`、Java seam
  `1/1` 且 Gradle `5 executed`，更正后 seal、Secret/raw Provider/CR/diff 门禁均通过。
  新 immutable freeze、三份全新全零 GO 与 CI 内容一致性仍是 `ESTABLISHED` 的必要
  条件；完成前 A0/A1/A2 继续禁止。
- R6 subject `a9daf62f…` 的两个全新 Codex auditor 均全零 GO，但 CC AUDIT 因隔离包
  使用 Windows worktree CRLF bytes、且 evidence/hash-only exclusion/source context 未
  显式分区而返回 `NO_GO / P0=1 / P2=1 / blocking=2`。该轮整体失效并保留；findings
  不改变 model evaluation、Provider payload、模型职责或准入门槛。
- R7 仅允许以仓库外一次性 builder 修复本轮 audit projection：内容从 frozen HEAD 的 Git blob 精确导出，
  subject evidence 完整划分为 content-included 或敏感 hash-only excluded，额外源码明确
  标记为非 evidence context，并由 package sums/verify mode 从头复算。不得借此发送人工
  ground truth 或 comparison report、重跑 DeepSeek、改变 seal 结论或启动 A0/A1/A2；不得
  将该 builder 演变为产品能力、通用审计平台或新的 repository evidence schema。
- R7 一次性 projection diagnostic 在旧失败 subject 上验证 `51 included + 3 hash-only
  excluded = 54 evidence`、`5 source context` 与 package sums `ceab8aa2…`。唯一 CR hit
  `cc-audit-status.json` 保留原 CRLF SHA `bf1c4b5c…` 后按既有 JSON eol 规则规范为 LF；
  raw CC report `244ae3f8…` 未改写。完整 verification 为 Node `56/56`、Java `1/1`
  且 `5 executed`、builder `1/1`、Secret/raw Provider/CR/diff 全绿。首次 freeze 前置校验
  因 verification 脚本工作区 CRLF/Git blob LF 不一致而未创建 subject；精确 LF 属性修复并
  强制全量重跑后的结果为 `1805feb1…`、console `8aba6687…`。新 freeze/三审/CI 前
  admission 仍 pending，A0/A1/A2 仍禁止。
- R7 subject `47d84299…` 的正式三审发现 `P0=1 / blocking=1`：隔离包完整 full diff
  绕过 hash-only exclusion，重新包含人工 ground truth/comparison text patch；CC GO 失效，
  另一 Codex audit 中断。R8 仍在本 ADR 的一次性仓库外 builder 边界内修复：删除 content
  diff，仅保留 changed-path Git identity inventory，并 fail closed 扫描全部包文件中 12 个
  禁止路径的 blob/逐 path diff 表示。两次离线 diagnostic 为 leak 0；这不替代新正式
  verification、freeze、三方全零 GO 或 CI。R8 正式 verification 随后为 Node `56/56`、
  Java `1/1` 且 `5 executed`、Secret/raw Provider/CR/diff/builder 全绿；result `3297f5c3…`、
  console `50288e22…`、evidence `42`，admission 仍 pending。

## 关联

- `decisions/ADR-019-blind-evaluation-and-model-activation-boundary.md`
- `decisions/ADR-022-mvp002-standing-egress-grant-and-derived-receipt.md`
- `decisions/ADR-025-track-b-schema-stability-diagnosis-and-fifth-admission.md`
- `tasks/active/TASK-EVAL-005-track-b-schema-stability-and-fifth-admission.md`
- `tasks/active/TASK-EVAL-006-track-b-provider-conversation-recovery.md`
