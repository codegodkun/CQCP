# TASK-EVAL-006：Track B Provider 会话投影有限恢复

状态：ACTIVE / `MODEL_EVALUATION_PASS` / `R10_THREE_PARTY_AUDIT_GO` / `ADMISSION_PENDING_CI`

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

- 无人工门禁待确认。R0-R4 已完成；standing grant 覆盖后续合规审计调用。
- A0/A1/A2 仍等待修复后唯一新 immutable subject 的三方全零 GO 与 CI 内容一致性；当前
  `providerAdmissionEstablished=false`，不因模型评测 9/9 单独解锁。

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

### R4：唯一正式模型评测 9/9（Provider admission 尚未建立）

- 冻结 model input `bb8984ed…`、call set `1121a345…`、dispatch `a427e052…`、derived
  receipt `aa716878…`；精确 9×1 calls，3 controls zero-call。
- 全新 `gpt-5.6-sol/xhigh` Codex blind opinion `cccf0a4d…`；DeepSeek claim
  `600a821a…`、opinion `e863596a…`，9 calls 全部 strict schema accepted、零自动重试，
  未持久化 raw response/reasoning/Secret。
- 本地解盲后两位 evaluator 的 schema、可靠 anchor、role、candidate、anchor、abstention
  全部 9/9，controls 3/3；report `395c0d38…`、seal `2d879b13…` 原样保留。该历史 seal
  提前写入的 `providerAdmissionEstablished=true` 未满足 ADR-026 第 4、5 项，因此不构成
  生效准入事实。
- 该评测 GO 只允许进入 R5；A0/A1/A2 在 verification/freeze/三方全零 GO 与 CI 内容
  一致性前仍禁止。

### R5：两次审计 NO-GO 与失效 subject 保留

- 首次 freeze subject `9dddbe43…` 的 CC AUDIT 为全零 GO；测试/安全 Codex auditor 为
  `NO_GO / P1=1 / P2=1 / blocking=1`，代码/架构 auditor 中止。旧报告不得组合通过，
  旧 freeze manifest 原样保留。
- 项目负责人批准只修复两项原因：Codex evaluator 独立可验证时序和 recovery-v1 Java
  seam。新 evaluator 以 claim `0c0ea838…` 在零文件 readiness 后启动，launch receipt
  `aa7330fb…`、opinion `84215a87…`、completion receipt `2b2edaf4…`；其隔离目录只有
  claim/launch/model-input/prompt 四文件。DeepSeek claim/opinion 未重跑、未改写。
- revalidated report `659e423a…`、seal `927eb673…` 曾记录
  `SEALED_GO_TRACK_B_RECOVERY_ADMISSION_REVALIDATED`；Codex/DeepSeek 六维仍为 9/9，
  controls 3/3，`deepSeekNetworkCallRepeated=false`。
- 新 Java test 逐字比较 source 经 `RuntimeEvidencePacketBuilder` 构建的 12 个 packet 与
  corpus，并核对 identity/admission/anchor；`--rerun-tasks` 为 `5 executed`，JUnit 1/1。
- R5 修复验证：Node `54/54`、verification builder `1/1`、Java `1/1`、新 seal verify、Secret-like/raw Provider/
  CR/diff 均为 0；console manifest 与 verification result 已保存于
  `outputs/task-eval-006/track-b-recovery-v1/verification-v2/`。
  三个纯 harness 失败原始日志按分类保留，不作为通过证据。
- 新 freeze subject `e77a8635…` 的首个代码/架构 auditor 返回
  `NO_GO / P1=1 / P2=3 / blocking=4`；测试/安全 auditor 依硬门禁中止，CC transport
  未形成报告。该 subject、manifest 与三份失败/中断状态证据原样保留，不得组合通过。

### R6：四项审计 finding 限定修复

- Codex v4 opinion 顶层使用 exact-field-set，额外 `finding` 或 `verdict` 必须 fail closed；
  新负向回归覆盖两种字段。
- revalidation 从旧 admission seal 重算 model input、call set、dispatch、receipt 与 DeepSeek
  claim/opinion hash；任一不一致 fail closed，新负向回归覆盖两个 DeepSeek identity。
- 更正后的 report `efba63ff…`、seal `70339354…` 状态为
  `SEALED_GO_TRACK_B_RECOVERY_EVALUATION_REVALIDATED_PENDING_AUDIT`，明确
  `modelEvaluationPassed=true / providerAdmissionEstablished=false /
  admissionDecisionPendingAudit=true`；旧 DeepSeek bytes 未重跑、未改写。
- ARCHITECTURE 已把 ADR-026 记录为 TASK-EVAL-005 后唯一第六套例外并禁止第七套；任务
  地图改用当前 R6 事实。
- 完整 R6 verification 为 Node `56/56`、verification builder `1/1`、Java seam `1/1`
  且 Gradle `5 executed`；更正后的 seal 原字节 verify、Secret-like/raw Provider/CR/diff
  门禁均通过。
- clean HEAD `dfc24de…` 的 immutable subject `a9daf62f…` 已完成一次三审：两个全新
  `gpt-5.6-sol/xhigh` Codex auditor 均全零 GO；隔离 CC AUDIT 为
  `NO_GO / P0=1 / P2=1 / blocking=2`。三审整体 fail closed，两个 GO 不得复用。
- CC 两项 finding 均属于 audit projection transport：9 个治理文件从 Windows worktree
  复制后变成 CRLF/mixed bytes，与 manifest 的 Git/LF hash 不符；同时 54 evidence 中
  4 项敏感内容被排除、4 个源码文件作为额外 context，却未显式分区。失败 manifest、
  CC raw/status/NO_GO 与两份 Codex GO 均已保留。

### R7：CC audit projection transport 限定修复

- 使用本轮一次性、仓库外 isolation builder：所有可发送 evidence/source context 从
  frozen HEAD 的 Git blob 导出并逐字验 hash；不再复制 Windows worktree bytes，不把
  builder 演化为产品能力、通用审计平台或新的 repository evidence schema。
- frozen subject 的每条 evidence 必须恰好归入 `included` 或
  `EVIDENCE_EXCLUDED_HASH_ONLY`。人工 ground truth 与包含 ground-truth comparison 的两个
  admission report 只保留 path/size/SHA/reason，不发送内容；额外审计源码单列为
  `SOURCE_CONTEXT_NOT_SUBJECT_EVIDENCE`，不得伪计入 evidence。
- builder 生成 LF inventory、boundary、prompt、full diff、package sums 和 projection
  verification；独立 verify 从头重算 Git blob、完整 evidence partition、敏感内容缺席、
  full diff 与整包 sums。该脚本及 receipt 只属于本轮隔离投递材料，不进入产品仓库。
- 旧失败 subject `a9daf62f…` 上的非审计 diagnostic 已验证
  `51 included + 3 hash-only excluded = 54 evidence`、`5 source context`，整包 66 files，
  package sums `ceab8aa2…`；没有重跑 CC 或改变旧 NO_GO。
- 原 aggregate-only CR 门禁增强为失败时输出稳定排序的 relative path、CR count 与 SHA；
  唯一命中是 R6 `cc-audit-status.json`。该状态元数据保留原 CRLF SHA `bf1c4b5c…` 于
  NO_GO 摘要后，仅按现有 `outputs/task-eval-006/**/*.json text eol=lf` 规范为 LF SHA
  `33ef2fae…`；JSON 值与 raw CC report `244ae3f8…` 均未改变。
- R7 完整 elevated verification 已通过：Node `56/56`、verification builder `1/1`、
  Java runtime seam `1/1` 且 Gradle `5 executed`，seal 原字节、Secret/raw Provider、CR=0、
  diff 全绿。首次 freeze 前置校验发现 verification 脚本工作区 CRLF 与 Git blob LF 不一致，
  因而未创建 subject；现以精确 `.gitattributes` LF 规则消除该字节漂移，并强制重跑全套门禁。
  最终 console manifest `8aba6687…`、verification result `1805feb1…`、evidence `37`、runs `7`，
  并绑定 R5/R6 两轮失败审计和本次 freeze-precondition 失败记录。
- 本轮不修改 corpus、human decisions、model input、Codex/DeepSeek opinion、report/seal
  结论、Java runtime seam 或 Provider 边界；不重跑 DeepSeek、不启动 A0/A1/A2。

### R8：CC package content isolation 限定修复

- R7 subject `47d84299…` / manifest `e83c7be5…` 的测试/安全 Codex auditor 返回
  `NO_GO / P0=1 / blocking=1`：仓库外 builder 又把完整 full diff 写入
  `allowed-subject.diff`，使 hash-only excluded ground truth/comparison 及历史人工答案以
  text patch 重新进入 CC 包。CC GO 因此失效，代码/架构 auditor 中断无 verdict；失败轮次
  已写入 `audit-round-status.json` 并只读保留。
- R8 只修仓库外一次性 builder：删除完整 content diff，改为 320 项
  status/path/base+HEAD Git blob OID/size inventory；full diff 只保留 SHA identity。新增仓库外
  fail-closed verifier，扫描包内每个文件，禁止 12 个 ground-truth/review/comparison path 的
  HEAD blob 与逐 path full diff 表示出现。
- 旧错误包定向 RED；修复后两次离线 diagnostic GREEN。最终诊断包 72 files、57 included、
  3 hash-only excluded、5 source context，package sums `798c551c…`；12 个禁止路径 × 2 种
  表示、全包 72 files 扫描为 `leakCount=0`。builder/verifier SHA 分别为
  `76dcd3fa… / a28d2e0e…`。
- 正式 verification 增加对 R7 manifest、NO_GO、失效 CC GO、中断状态与 audit status 的
  原字节绑定；定向测试按 RED→GREEN 通过。完整 R8 verification 为 Node `56/56`、Java
  `1/1` 且 `5 executed`、seal/Secret/raw Provider/CR/diff/builder 全绿；console manifest
  `50288e22…`、result `3297f5c3…`、evidence `42`、runs `7`。新 clean candidate/freeze
  尚未形成，未启动新审计。
- clean HEAD `d08484cc…` 的正式 freeze subject `a85e96f5…` / manifest `08a0bd23…`
  自校验通过，实际为 325 changed paths、65 evidence。正式 CC 包为 62 included + 3 hash-only
  excluded、5 context、77 total files（sums 覆盖 76 content files），package sums
  `16097ea7…`；独立 verifier 扫描全部 77 files、24 forbidden representations 为 leak 0。
- CC AUDIT 确认隔离、产品证据和前序 findings 均关闭，但因本 TASK、CURRENT_CONTEXT、任务地图
  与 changelog 仍写 pre-freeze diagnostic 的 320 paths / 72 files / `798c551c…` /
  `FREEZE_PENDING`，返回 `NO_GO / P2=1 / blocking=1`。两个 Codex auditor 依门禁中断无 verdict；
  本轮不能复用。该问题只属于 governance-document freshness，不是产品或评测正确性失败。

### R9：R8 正式事实同步与失败轮绑定

- 只同步并机器绑定已完成的 R8 权威事实：subject `a85e96f5…`、manifest `08a0bd23…`、
  325 changed paths、65 evidence、正式 CC 包 77 total files / sums `16097ea7…`、CC
  `NO_GO / P2=1 / blocking=1` 与两个 interrupted status。
- verification-builder 定向测试先以 failed round count `3 != 4` RED，再在 R8 manifest/CC
  status/report/两份中断 evidence 的原字节、identity、count 与分类断言加入后 GREEN。
- 本轮不改变 corpus、human seal、Codex/DeepSeek opinion、Java seam、Provider payload、PUBLIC
  binding、A0/A1/A2 或任何产品 runtime。当前 clean subject 尚未生成；其身份只能由提交后的
  immutable freeze manifest 给出，不在被冻 HEAD 内自引用。
- 完整 verification 为 Node `56/56`、Java seam `1/1` 且 `5 executed`、seal、Secret/raw
  Provider、CR/diff/builder 全绿；console manifest `cd563436…`、result `c2cc2213…`、
  evidence `47`、runs `7`。当前仅放行 clean commit 与其后的新 freeze。
- clean HEAD `140b9a3…` 已形成 subject `e566c8d2…` / manifest `d791a053…`：tree
  `224feded…`、full diff `81d48f4e…`、330 changed paths、70 evidence。安全 CC 包为
  67 included + 3 hash-only excluded、5 context、82 total files，sums `9a769043…`，
  forbidden-content leak 0。
- CC AUDIT 返回全零 GO，但同一 subject 的全新代码/架构 auditor 返回
  `NO_GO / P2=1 / blocking=1`：本 TASK 第 137 行仍把 A0/A1/A2 实时门禁写成等待已失败、
  不可复用的 R6 freeze。测试/安全 auditor 随即中断无 verdict；CC GO 作废且不得跨轮复用。
  本轮按硬门禁停止，未修该 finding、未 push/PR/CI、未启动 A0/A1/A2。

### R10：R9 单一治理状态 finding 限定修复

- 唯一直接修复是把“等待 R6 immutable freeze”纠正为“等待修复后唯一新 immutable subject”；
  不改变三方全零 GO、CI 内容一致性、admission pending 或 A0/A1/A2 禁止门禁。
- verification builder 新增 R9 manifest、round status、CC invalidated GO、代码/架构 NO_GO、
  测试/安全中断五项原字节与身份断言，并把失败轮次从 4 增至 5；同时断言旧 R6 实时门禁文本
  不得再次出现。
- 定向测试按 `4 != 5` RED 后 GREEN。完整 verification 为 Node `56/56`、Java seam `1/1`
  且 Gradle `5 executed`、seal/Secret/raw Provider/CR/diff/builder 全绿；evidence `53`、runs
  `7`，最终 result/console identity 由随后生成的 freeze manifest 外部绑定。未修改 corpus、人工 seal、
  Codex/DeepSeek opinion、Java seam、Provider、PUBLIC binding 或产品 runtime；新 freeze 待执行。
- clean HEAD `fa64271…` 的 subject `d73e15e5…` / manifest `845bf692…` 已验证：tree
  `3a180069…`、full diff `ef21f51e…`、335 changed paths、75 evidence。安全 CC 包为
  72 included + 3 hash-only excluded、5 context、87 files、sums `e507bd57…`，24 forbidden
  representations leak 0。
- CC、代码/架构 Codex、测试/安全 Codex 三份全新报告均为
  `GO / P0=0 / P1=0 / P2=0 / blocking=0`，绑定同一 subject。正式 admission 仍等待
  CI 与 subject 内容一致性；在 CI 通过前 `providerAdmissionEstablished=false`，A0/A1/A2 不启动。

## Next Task Handoff

- R9 subject `e566c8d2…` 已因任务状态矛盾 `P2=1 / blocking=1` 失效并完整保留。
- R10 subject `d73e15e5…` 已完成三方全零 GO；下一步仅提交审计 evidence/status，验证
  post-audit diff 不改变受审产品内容，再 push/PR/CI。
- CI 全绿且受审内容一致后才建立 Provider admission 并进入 A0/A1；旧 verdict 不得复用，
  A0/A1/A2 在 CI 前继续禁止。
