# CURRENT_CONTEXT.md

更新时间：2026-08-04

## 当前阶段

> 2026-08-04 最新事实（替代本节后续较早的 R5 叙事）：TASK-EVAL-006 R5 repair 的
> freeze subject `e77a8635…` 已被全新代码/架构 auditor 判定
> `NO_GO / P1=1 / P2=3 / blocking=4`；测试/安全 auditor 依门禁中止，CC transport 未形成
> 报告，该轮不能组合通过。四项 finding 为：Codex v4 opinion 顶层未 exact-field-set、
> DeepSeek reuse 未从旧 seal 验证 claim/opinion hash、ARCHITECTURE/ADR/admission 状态冲突、
> MVP task map 使用旧测试/seal 事实。失败 freeze、NO_GO 与中断记录已保留。
> R6 限定修复现已落地：额外 `finding/verdict` 顶层字段 fail closed；旧 seal 与实际
> DeepSeek claim/opinion hash 不一致 fail closed；ARCHITECTURE 明确 ADR-026 是第五套失败
> 后唯一第六套例外且禁止第七套；更正后的 report/seal 为 `efba63ff… / 70339354…`，状态
> `SEALED_GO_TRACK_B_RECOVERY_EVALUATION_REVALIDATED_PENDING_AUDIT`，明确
> `modelEvaluationPassed=true / providerAdmissionEstablished=false /
> admissionDecisionPendingAudit=true`。DeepSeek claim/opinion `600a821a… / e863596a…` 未重跑、
> 未改写。R6 完整 verification 已通过：Node `56/56`、verification builder `1/1`、
> Java seam `1/1` 且 Gradle `5 executed`，更正后 seal verify 与 Secret/raw Provider/CR/diff
> 门禁均通过。clean HEAD `dfc24de…` 的 subject `a9daf62f…` 随后三审：两个全新 Codex
> auditor 均全零 GO，CC AUDIT 为 `NO_GO / P0=1 / P2=1 / blocking=2`。CC findings 仅指向
> 隔离 projection 的 9 个 CRLF/Git-blob hash mismatch，以及 subject evidence、敏感
> hash-only exclusion 与额外 source context 未显式分区；不是产品裁判或评测正确性
> finding。该失败 freeze、原始 CC 报告和两份 Codex GO 已只读保留，不能组合通过。
> R7 只修一次性 CC isolation projection 构建流程：从 frozen HEAD 的 Git blob 导出原字节，
> 将每条 evidence 完整划分为 included 或 hash-only excluded，额外源码单列 context；
> 定向 Node `2/2` 已通过；一次性 builder 已在旧失败 subject 上完成非审计 diagnostic：
> `51 included + 3 hash-only excluded = 54 evidence`、`5 source context`，package sums
> `ceab8aa2…`，全部可发送 bytes 与 Git blob 一致。R7 随后把唯一 CR hit 精确定位到
> R6 `cc-audit-status.json`，保留原 CRLF SHA `bf1c4b5c…` 后仅按既有 eol 规则规范为 LF；
> raw CC report `244ae3f8…` 未改写。完整 elevated verification 已通过：Node `56/56`、
> Java seam `1/1` 且 `5 executed`、seal/Secret/CR=0/diff/builder 全绿。首次 R7 freeze 前置校验
> 发现 verification 使用的脚本工作区字节为 CRLF、Git blob 为 LF，因此未创建 subject；现已用精确
> `.gitattributes` 规则固定该脚本为 LF，并在相同语义下重跑全套门禁。最终 verification result
> `1805feb1…`、console manifest `8aba6687…`、evidence `37`，并保留 R5/R6 两轮失败审计和本次
> freeze-precondition 失败记录。当前等待 clean commit、新 freeze 和三方重审，A0/A1/A2 仍禁止。

`MILESTONE-MVP-002` 已保留 `MILESTONE-MVP-002-TRACK-B-SUCCESSOR` 的
`AUTHENTICATION_FAILED` 终态证据；其一次性 claim 已消费且不得重试。L0 诊断随后以
显式仓库外 Secret 证明官方 endpoint、`deepseek-v4-flash` 与
`deepseek-v4-pro` strict JSON 通路可用。项目负责人已批准 ADR-024 与
`TASK-EVAL-004 / MILESTONE-MVP-002-TRACK-B-FINAL`。正式 connectivity gate 已 GO：evidence SHA
`22d38b1183dc9fd53e5212fbb2b9dd2077234967cea503c8b4e624c50b80d1ac`，
模型清单包含 flash/pro，exact pro strict JSON probe 为 200/stop/sentinel matched，
实际 KEY 泄漏文件数 0。第四套 corpus 已完成人工先封印并启动唯一正式 admission；
DeepSeek 在第 8 个单包 call 因 `OPINION_SCHEMA_INVALID` fail closed，claim 已消费且
不得重试。未形成完整 DeepSeek opinion、未解盲，Provider admission 固定为
`NOT_ESTABLISHED`，A0/A1/A2 继续阻塞。项目负责人现已批准 ADR-025 与
`TASK-EVAL-005 / MILESTONE-MVP-002-TRACK-B-SCHEMA-STABILITY`：只允许先以全新
合成输入执行有限 schema 诊断，按证据版本化并冻结 prompt/schema；稳定性 GO 后才
创建第五套独立 12-packet corpus 并等待新的人工 ground truth。TASK-EVAL-004 不重试、
不补跑；TASK-EVAL-005 再失败即终止且不创建第六套。D1/D2 现已完成：旧 prompt
baseline 12/12；单包专用 prompt/schema v2 的 qualification + confirmation 为 24/24，
stability freeze SHA `44468d84327f001f50bcbf1f38d2e63d30b750227d7c00b3cde0f1bca1e16bd5`。
第五套 preseal 已冻结：corpus SHA `17343dd34820c847676ed681a8a4e6ee9f669c60cf3c0257aa2363ba4bba8f5e`，
相对前四套及诊断集 identity/value/text overlap 全为 0；challenge
`TB51-5deddd744f6f432f8f7760bd51899e16` 已由项目负责人绑定 challenge/corpus/review
SHA 确认全部 12 条人工 decisions。confirmation SHA `bdb56738768b7b5ca01a413a8a58d45d0f1252818ad2138399c76f3bc7ebdf37`，
human seal SHA `85391e4573911f8e0147eb2ff2dda4908e79afe8029190d1c97fa2b73a96e4e3`。
第五套 dispatch 已冻结为 9×1 calls/3 controls zero-call：model input `e1516fb0…`、
call set `365e1722…`、dispatch `31e9d718…`、derived receipt `6e9f6955…`。全新
Codex blind opinion 已按严格 v2 schema 封存为 `95ecd582…`。唯一正式 DeepSeek
9×1 执行全部 strict schema accepted，无自动重试；claim SHA `f9086db1…`、opinion SHA
`0e4f96fc…`。双意见先验验证后解盲，Codex 为全部维度 100%，DeepSeek schema 与可靠
anchor 为 100%，但 role/candidate/anchor/abstention 均为 55.56%（4 个 CONFLICTED
packet mismatch）。终态 seal `520d7b38…` 为 `SEALED_NO_GO_MODEL_MISMATCH`，Provider
admission 固定 `NOT_ESTABLISHED`，同一 claim 不得重试。项目负责人已澄清此前
“Provider BLOCKED”是询问而非终止指令，并批准 ADR-026 / TASK-EVAL-006 的有限恢复：
建立不含 runtime routing/diagnostic label 的 model-facing EvidencePacket projection v3，
先执行旧 4 个 CONFLICTED packet 的 4-call non-admission diagnostic；4/4 后才允许第六套
独立 12-packet corpus、人工先封印与唯一 9×1 admission。该恢复 admission 现已全维
100% 的模型评测结果；当前真实状态为
`MODEL_EVALUATION_PASS / ADMISSION_PENDING_AUDIT / R6_REPAIR_ACTIVE`，A0/A1/A2 在本
recovery integration unit 完整验证、冻结、三方全零 GO 与 CI 内容一致性前继续阻塞。当前基线为
`origin/master@115be530480e2ff9b92a7076b2668c066a44ae5c`。

TASK-EVAL-006 R1/R2 已完成：model-facing projection/prompt/schema/request builder v3
已冻结，runtime routing/diagnostic/identity/admission label 不进入 Provider payload；定向
契约与 runner 回归通过。4 个旧 CONFLICTED packet 的 non-admission diagnostic 使用
精确 4 calls、零自动重试，全部 strict schema accepted；与既有人工 decisions 本地比较后，
schema、可靠 anchor、role、candidate、anchor、abstention 均为 4/4。input `809c244e…`、
call-set `892e6b4f…`、dispatch `338d1168…`、receipt `6893767f…`、claim `14715db0…`、
opinion `eef82a45…`、report `a1472689…`、seal `fedc4e21…`。seal 状态为
`SEALED_GO_RECOVERY_DIAGNOSTIC`，只放行第六套 corpus 创建；Provider admission 仍未建立。

R3 第六套 preseal 已冻结：source `47cf4a04…`、proposal `2cb18d75…`、corpus
`3a988a9a…`、review draft `8edd0ea4…`、review document `7ff431cc…`、manifest
`937d6500…`。相对前五套及两份 diagnostic input 的 identity/value/evidence overlap 全为
0；5 MEDIUM + 4 CONFLICTED eligible、3 zero-call controls。challenge
`TB61-8e5e524790c9457a80d8e2fe2433b63d` / SHA `328f4349…` 已生成，2026-08-05
09:37:54.991Z 到期。项目负责人已绑定 challenge/corpus/review SHA 接受 12 条 decisions；
confirmation `814e8146…`、human seal `0f23b9eb…`，人工封印早于 model input 和 evaluator access。

R4 唯一正式 admission 已完成：v3 model input `bb8984ed…`、call set `1121a345…`、
dispatch `a427e052…`、derived receipt `aa716878…`；全新 `gpt-5.6-sol/xhigh` Codex blind
opinion `cccf0a4d…`，DeepSeek 9×1 claim `600a821a…`、opinion `e863596a…`，3 controls
zero-call、无自动重试、无 raw response/reasoning/Secret 持久化。解盲后 Codex 与 DeepSeek
的 schema、可靠 anchor、role、candidate、anchor、abstention 均为 9/9，controls 3/3；
report `395c0d38…`、seal `2d879b13…`，状态 `SEALED_GO_TRACK_B_RECOVERY_ADMISSION`。
模型未生成或改变 Finding/verdict。R5 bounded verification 已通过：phase-appropriate Node
`70/70`、verification builder unit `1/1`、Java runtime seam `1/1`、admission seal 重建一致、
61 个 hash-bound 文本文件 CR=0、实际 KEY/禁止 Provider payload=0、`git diff --check=0`。
最终 verification result 与 console manifest 已重建并复验 PASS，将随 clean commit 冻结；
freeze/三审尚未完成，A0/A1/A2 尚未解锁。

先行 Core integration unit 已通过 PR #37 合并，包含：

- M1：execution 任务清单、精确结果查询、parser-backed preview、原始 DOCX 下载和
  左右审核工作台；
- M2：immutable Model Profile config、server-side Secret Reference、endpoint
  allowlist、readiness/connectivity 与管理页；
- M3 已完成部分：Track A Codex/DeepSeek 盲评、Track B runtime-isomorphic seam、
  R7 zero-call 与旧 18 packet run-v3 `NO_GO_MODEL_MISMATCH` 证据；
- TASK-034 v29/R7 与 TASK-036 B1/B2/C1/C2/D1/D2 seam。

新 holdout 的人工 ground truth 已先封印；正式 DeepSeek 执行在第二个 eligible
packet 因 opinion schema invalid fail closed，同一一次性 claim 已消费且不得重试。
因此 Provider admission 仍为 `NOT_ESTABLISHED`，Provider A0/A1/A2 尚未开始，
guarded assist A3 不属于本 Milestone。PUBLIC profile 保持
`EVALUATION / disabled / unbound`，普通 Demo 继续使用 `MVP_DEMO_MOCK`；模型不得
直接改变 Finding/verdict。

## 已确认事实

- Windows document read/download directory junction、Secret root junction 和单一
  Secret sentinel 定向证据已补齐；最新定向组合为 34/34 通过。
- 单一 sentinel 测试扫描 HTTP body/header、异常、日志和数据库，并证明
  execution/Snapshot/stage/TuningPacket writer 在 connectivity 边界不可达。
- backend/CI 测试不再隐式依赖 `outputs/**`：盲评 source、Track B corpus、人工
  anchor workbook 与 D1 oracle 已迁入 `apps/api-server/src/test/resources/`。
  Node evidence validator 明确消费外部派生证据，并由 formal verification/freeze
  逐文件绑定 hash；`outputs/**` 不进入 Core source diff。
- Core-only 脚本 allowlist、Provider-free import closure、验证入口与冻结入口已建立；
  它们不执行模型公网调用，也不导入 Provider contract、standing grant 或 CC send
  lease。
- 旧 Track B run-v3 解盲结果为 Codex 15/15、DeepSeek 6/15、controls 3/3，
  `providerAdmission=NOT_ESTABLISHED`。同一已解盲 corpus 不得再次作为独立 admission。
- Core 冻结 `45568f205597d7532a1e1efa1d9cc496d160bb1a8d27f3a7dcd78f310e2ad1bc`
  已失效并封存为
  `outputs/task-mvp-002/core-audit/freeze-rejected-45568f20/`。两名全新 Codex
  审计员均返回 `NO_GO`：代码/架构侧为 `P1=2 / P2=1`，测试/安全侧为
  `P1=4`。已确认根因为 DOCX 安全快照后按路径重开、R7 未绑定当前 HEAD、
  Browser 关键断言缺少原始事件/DOM 证据、零模型外呼为脚本常量、Core
  ARCHITECTURE 混入 Provider attempt contract，以及 TASK 状态叙事冲突。
- 上述 NO_GO 使 `fa869af3b79f0e417a9a3de5f5630dcbe4fd5885` 上的 Compose/browser、
  formal verification 与 freeze 全部只能作为失效历史证据，不得用于最终收口。
- `45568f…` 的六类 blocking causes 已完成原子整改：Worker 只消费已校验的
  DOCX byte snapshot；R7 绑定当前 HEAD/tree/source closure；browser 证据改为
  原始 filechooser/network/DOM/console/dialog 事实；模型外呼计数来自 Compose
  transport；Core 架构与 Provider contract 分离；父 TASK 状态叙事已收敛。
- 正式验证随后暴露旧 Track B v2 dispatch 与 current-HEAD R7 共用 live 路径。
  历史 packet manifest 与 R7 manifest 现以原始 SHA-256 fixture 封存，verify-only
  override 必须成对提供且仍须匹配 dispatch hash；当前 R7 不回退。定向 contract
  `7/7` 与历史链 direct verify 已通过。
- Core 冻结 `ea19a52ad1bfdb0a9ae25d4082b8af03a3fcbbbe7a6f5bcbbdced9a9e85f9db5`
  （HEAD `006bfa8c9901506137695d314ce9afa61ee39507`）已完成三方从零审计并判定
  `NO_GO`，现封存为
  `outputs/task-mvp-002/core-audit/freeze-invalidated-ea19a52a/`。两个 Codex
  auditor 分别发现 occurrence `contextType` 丢失、Core/Provider 范围证明可
  false-negative、Compose provenance 错绑旧 outputs override、四轮 Gradle
  原始 JUnit 未独立冻结；CC AUDIT 发现 D1/D2 计数文档滞后和本文件叙事滞后。
- 上述六项 finding 均绑定同一 `ea19a52a…`，旧三份审计结论及其 verification
  不得用于后续 GO。单批最小源码整改已经完成，定向 D1/D2 为
  `444/444`、`20/20`，Core 范围负向 Node tests 为 `11/11`；整改没有启动
  Provider A0/A1/A2，没有执行模型公网调用，也没有改变 Finding/verdict。
- 送审就绪性 CC 复核随后确认：当前 Formal R7 已绑定候选 HEAD 并通过，但
  `core-node-test` 因 Track B fixture 混用 live packet 与历史 manifest 而
  `203/208`、5 项失败，故未生成 verification summary 或 canonical freeze。
  主 Codex 已从原封存工作区恢复与历史 manifest SHA 精确匹配的三份 packet，
  将 packet + manifest 作为同一 test-resource directory 供 `makeFixture` 消费，
  将三份 packet 纳入 Core subject 闭包，并令 dispatch 与 opinion seal 的
  verify-only historical override 都从 manifest 同目录读取 packet bytes、同时
  保持 canonical artifact path identity；Track B contract 与 Core scope 定向回归
  合计 `15/15 PASS`。该结论只证明最小 fixture/dispatch/seal 同源修复，新的完整
  verification、freeze 与三方从零审计仍待执行。
- 同源修复后的候选 HEAD `33890cb80178134371f8589a8aa117332f7c6a12` 已完成
  一轮完整 verification 和 freeze；CC AUDIT 与代码/架构 Codex auditor 曾给出
  `GO`，但测试/安全 Codex auditor 给出 `NO_GO（P1=2 / P2=1 / blocking=3）`，
  因此该轮三份结论已全部失效。三个根因为：浏览器汇总 JSON 未由不可变原始事件
  与服务端 access log 独立重建；resolved Compose config 保存验收 token；Admin 与
  readonly token 配成相同值时先命中 Admin 分支。
- 上述三项已完成最小整改和定向验证：相同 token 在 Filter 构造期 fail closed，并
  覆盖相同、缺失、单角色和轮换场景；resolved Compose config 使用字段级确定性
  脱敏并保留 Secret Reference；旧自报式 browser capture 入口已移除，Compose
  每轮生成独立短期随机 token，由隔离 Chrome 直接通过 CDP 捕获 native
  filechooser、network、console/dialog、native download 与 DOM 事件，验证器同时
  解析绑定 Chrome Nginx access log。Node 负向回归 `13/13`、鉴权定向 Gradle
  `4/4` 通过；首次真实重建在修正旧固定 token 请求后通过，绑定 primary
  `TASK_7e642342e98b4af4b3b4574a15047bcf / EXEC_ef28692a49214cc09b2645916448044a`、
  browser upload `TASK_b4e3ddce26c94d9aa5c1d27481993799 /
  EXEC_69a4fd5a55b9401a8761c035accaf0f4`、malicious
  `TASK_9acaccfbdce54b849c7a2273bead0b27 / EXEC_f1aad66e69bc4e2d99f6d90d82431e52`；
  CDP event stream SHA 为
  `08918f1b77dcaa65812e08004dc119eb5a3d8bedcc4fc88fb0a474ac362d4f39`，
  Chrome access log SHA 为
  `32b50aba526a48c4c00430d180cd64e4de4e1f9c8018558f1d9c9b6608af6bcd`。
  这些只是整改定向证据；完整 verification、新 freeze 与三方从零审计仍未开始。
- 候选 HEAD `0cdf7ccc79c43ae5da5c53df15d1fdafc894c549` 随后完成从零完整
  verification：两轮 backend 各 `54 suites / 895 tests`，D1=`444`、D2=`20`，
  admin-web=`70`、Core Node=`215`，bootJar/OpenAPI/Track A/Track B/Compose +
  direct Chrome CDP 全部通过；canonical freeze subjectIdentity 为
  `81e47f117a5cc4e452321acf3e33349612d7e00c8010651fd69ae325daaf518a`。
- `81e47f…` 三方审计结果为：CC AUDIT `GO`、测试/安全 Codex auditor `GO`、
  代码/架构 Codex auditor `NO_GO（P1=1 / blocking=1）`，因此三份结论已整体
  失效。阻断根因是 `candidateForPartyValue` 用归一化 `candidateValue` 反向搜索
  `tableCells[].text` 并构造 `cellIndex/previewElementRef`，违反 ADR-016 与
  ARCHITECTURE 的 parser-issued SourceAnchor provenance 门禁。该轮 freeze、
  verification、browser runtime 和审计观察已分别封存到
  `outputs/task-mvp-002/core-audit/*-invalidated-81e47f/`。
- 主 Codex 已完成该 P1 的限定原子修复：party candidate 现在从同一次 line matcher
  捕获中按 parser join 分隔符与既有字段边界裁出实际值 span，并仅由
  `TableCellSpan.startOffset/endOffset` 映射 cell identity；不再读取
  `tableCells[].text` 或反向搜索 `candidateValue`。label/value 分处相邻 cell 时由
  parser-issued structural span 定位 value cell；span 不可映射时在一致性裁判前
  `SYS_EVIDENCE_BUNDLE_INVALID / INTERNAL_RULE_ERROR` fail closed。新增结构化 cell
  split、重复值/错误 cell 和不可映射 fail-closed 三项回归；定向 `3/3`、完整
  `ParserBackedReviewInputPreparerEvidenceTest 29/29` 与原始 Formal R7 均通过，
  Formal R7 新 seal SHA-256 为
  `9cfb8ea3eeb895b752779567f996c38ae90d2baf5b1461db229e8e123d4acb86`。新的完整
  verification、freeze 与三方从零审计仍待执行。
- 上述 party 修复进入候选 HEAD
  `e044768fe7ac3aba6a0d4a0d2633261f4f141272` 后，完整 verification 全部通过：
  D1=`447`、D2=`20`、两轮 backend 各 `898`、admin-web=`70`、Core Node=`215`，
  Compose/PostgreSQL、direct Chrome、R7、OpenAPI、Track A/B 与 bootJar 均通过；
  freeze subjectIdentity 为
  `5bcfe410c6366313ff220552ba4f5845dceefb92ac20a9335aa4aa83523b293b`。
  CC AUDIT 与测试/安全 Codex auditor 给出 `GO`，代码/架构 Codex auditor 给出
  `NO_GO（P1=1 / blocking=1）`，故该轮三份结论整体失效。阻断根因是 legacy 与
  `v20260715.1` 可达的 whole-text fallback 在拼接全文提取数值后丢失原 matcher
  block/span，再由 `findBlock` 按 label + candidate value containment 选择首块并
  提升为 fully-attributed anchor。该轮 freeze、verification、R7、browser 与三份
  审计报告已封存到 `outputs/task-mvp-002/core-audit/*-invalidated-5bcfe410/` 及
  `outputs/task-mvp-002/browser-evidence-invalidated-5bcfe410/`。
- 主 Codex 已完成该 finding 的最小红绿整改：先用 4 条 regression 复现错误首块、
  重复数字、split-table value span、重叠 TABLE_CELL fail-closed 和错误 anchor
  进入业务 PASS；红测为 `33 tests / 3 failures`，修复后
  `ParserBackedReviewInputPreparerEvidenceTest=33/33`。fallback 现在逐 block 扫描，
  通过 offset-preserving projection 将同一次 matcher 的 value group 映回原文 span；
  TABLE_CELL 只有唯一 parser-issued cell span 命中才保留可靠 attribution，否则在
  v15 preflight 前 `SYS_EVIDENCE_BUNDLE_INVALID / NOT_CONCLUDED`。D1 新硬门禁已同步
  为 `451/451`；尚未执行新的完整 verification、R7、freeze 或三方审计。
- 整改提交 `067db30e62227ad331c1d6372c82b589a95737e6` 首次启动完整 verification
  时，Core boundary 在任何 R7/Compose/browser 之前因新当月
  `changelog/2026-08.md` 未进入精确 allowlist 而 fail closed；隔离数据库已在
  `finally` 删除，partial console 封存为
  `verification-failed-067db30-core-boundary/`。整改只把该单一当月文件加入
  `CORE_EXACT_PATHS`，并增加“2026-08 允许、2026-09 拒绝”的负向边界断言；不放宽
  `changelog/` 目录，不改变业务或 Provider 边界。
- 候选 HEAD `fc32b55d36870203a3d5d21543cb453b01b78d74` 随后完成一次完整
  Core verification 并冻结为 subjectIdentity
  `c156b4a741df1e24a4bf3bc9c52d31237cf9d6597f19d3344f7d393961d7cf80`。
  第一份全新代码/架构 Codex auditor 返回 `NO_GO（P1=1 / blocking=1）`，因此
  该轮依硬停止条件终止，未向 CC 发送新审计、未启动第二名 Codex auditor，也未
  push/PR/CI。finding 证明两个 TABLE_CELL fail-closed 用例的测试 parser 使用
  `ScopeCoverageReport.unverified()`，其中重叠 cell 用例被 scope 前置门禁代偿；
  verified-scope 下真实结果为 `SYS_ROLE_CONFLICT / EVIDENCE_AMBIGUOUS`。
- 主 Codex 已以提交 `d00dcca0e8dab8760fe5851cd894758e6f99eb46` 完成该 finding 的
  限定原子修复：测试 fixture 明确提供 verified scope 并断言候选确实携带
  `TABLE_CELL + blockAttribution=false + 无 cell identity`；collector 仅把这种
  不可靠 TABLE_CELL identity 映射为
  `SYS_EVIDENCE_BUNDLE_INVALID / INTERNAL_RULE_ERROR`，普通 BLOCK_LEVEL
  attribution mismatch 继续保持既有 role-conflict 语义。红测为 `2 tests / 1
  failure`，修复后定向 `2/2`、完整 preparer `33/33`、collector `91/91`。新的完整
  verification、freeze 与三方从零审计尚未开始。
- 后续 HEAD `847bb2c7a76aef2236ff7c4ca8b7ea2d0b1ed7ec` 已完成 Core verification，
  freeze subject 为 `3bc24a842a331ca9b86f491c26904ac1d84f108ecc69bbba8a33042ab510ec5b`，
  CC AUDIT 与两个全新 Codex auditor 均为全零 `GO`。PR #37 随后创建，但
  `Authorization evidence check` 因 PR body 缺少必填区块失败，backend Linux CI
  因 Track A 与 TASK-036 v2 的 Windows CRLF hash 固化出现 2 个失败；admin-web CI
  通过。该 PR 当前未 merge。
- 候选提交 `76f56856c07e8c35a08144131ead9a68f4bc5356` 曾把相关工件统一为 LF。
  从零完整 verification 在产品、D1=`451/451`、D2=`20/20`、两轮 backend
  各 `902/902`、admin-web=`70/70`、Core Node=`215/215`、bootJar、R7、
  blind freeze 和 DeepSeek seal 通过后，于 `blind-unblind` fail closed：当前三份
  LF human ground truth 与 immutable Track A freeze/DeepSeek seal 绑定的历史 CRLF
  SHA 不一致；失败日志为
  `outputs/task-mvp-002/core-audit/verification/blind-unblind.log`，SHA-256
  `04ee667b7c39444fe4218d43ce613191c9604dc73e129530d548ca8271e11376`。
  未生成 verification summary，Compose/browser、freeze 和审计均未启动。
- 本批次限定修复保留历史 seal，不做 identity migration：hash-sensitive
  fixture/expected 使用 `text eol=crlf` 保持 Git blob 不变并跨平台检出历史 CRLF bytes，Track A writer 在所有平台
  显式写 CRLF，manifest 与 TASK-036 v2 恢复历史 SHA。Windows 定向 `31/31`、
  仓库只读且 `--network none --offline` 的 Linux Java 21 / Gradle 8.10.2 定向
  `31/31` 均通过；直接 `unblind` 恢复为
  `TRACK_A_CODEX_AND_DEEPSEEK_COMPLETE_TRACK_B_ZERO_ELIGIBLE_SAMPLE`、`27` 项。
  该结果只关闭当前字节身份冲突；新的完整 verification 仍须从零执行。
  `3bc24a84…` 及其三份旧 GO 已整体失效，不得组合用于后续收口。
- HEAD `22e44fc45decb9cb2cec41848dfcf393f2c49378` 随后完成从零 Core verification，
  并冻结为 subject `8d01dd9ad49a1c440bf0aa4ec2eee4a09600653c721dd570f2f66be86cf2b092`；
  manifest SHA 为 `f69d114a821c781f9d479f77f1093885938ada9e6b62baecdd264044849cc670`，
  full diff SHA 为 `8d1d598d2fdff4ccc264131dde74821a7a787d43f2154f44be6be1b02902c6a9`，
  verification SHA 为 `f5748d2f53e7d6c7a26fd5dca77d6aa155600e922f2c7fce3407baee31584b47`。
  第一份全新代码/架构 Codex auditor 返回
  `NO_GO（P1=1 / P2=1 / blocking=2）`，故依硬停止条件未启动第二名 Codex auditor、
  未发送 CC AUDIT，也未进入 push/PR/CI/merge。P1 证明普通 pattern 与 v29 比例在
  TABLE_ROW 中丢失捕获值原始 span，导致常见表格证据降为不可靠 row anchor；P2
  证明内部 `TABLE_CELL` 曾泄漏到公共 `SourceAnchor.locationLevel`。审计建议新增
  公共 `TABLE_CELL` enum 与 ADR-015/ARCHITECTURE 冲突，未被采纳；正确边界为内部
  occurrence 保留 `TABLE_CELL`，公共 anchor 归一为 `BLOCK_LEVEL`。
- 主 Codex 已完成上述两项 finding 的限定红绿修复，并以提交
  `97b55a0b13facbb30397ee79c3f0b1d7b0d31d67` 形成候选：
  ordinary pattern 使用 capture group span；v29 使用可逆 NFKC/空白 projection 将
  capture span 映回 parser 原始 cell；“无预付款”使用精确 token span；无法可靠映射
  时继续 fail closed。红测为 `32 tests / 4 failures`，修复后同组 `32/32`；扩大定向
  回归为 `6 suites / 200 tests / 0 failures`；OpenAPI verifier 为
  `PASS（8 paths / 48 schemas）`，admin-web workbench Vitest 为 `2/2`，build 通过，
  最后清理后的 v29 class 复跑通过。未修改 CandidateResolver、EvidenceSlot、最终
  Finding/verdict、Provider 或模型网络边界，也未执行任何模型调用。
- 已否决 subject 的 canonical `freeze/`、`verification/` 与 browser evidence 已原样
  封存到 `freeze-invalidated-8d01dd9a/`、`verification-invalidated-8d01dd9a/` 与
  `browser-evidence-invalidated-8d01dd9a/`。当前尚未创建新 verification 或 freeze。
- `97b55a0…` 的首次 Core verification 启动因 sandbox 无法创建 canonical evidence
  目录而在任何验证命令前停止；同一未变 HEAD 经获准在 sandbox 外重启后，Core
  scope `7/7` 与 Provider-free boundary 通过，但 Formal R7 fail closed：57 条纳入
  occurrence 中 `39 MATCHED / 18 NOT_OBSERVABLE`，18 条恰为全部人工
  `TABLE_CELL`。根因不是生产 anchor identity 丢失，而是 test-only harness
  `resolveElement()` 在解析 `previewElementRef` 前按公共 `BLOCK_LEVEL` 短路，未遵循
  “preview ref 优先、否则 blockId”的已接受契约。失败 verification 与 R7 已分别
  封存为 `verification-failed-97b55a0-formal-r7/`、
  `r7-failed-97b55a0-public-anchor-harness/`；未生成 summary、freeze 或审计。
- 主 Codex 已在 `Task034MvpE2EAcceptanceHarnessTest` 建立最小反馈环：新增真实公共
  `BLOCK_LEVEL + table/.../cell/...` 红测为 `1/1 FAIL`，调整 test-only resolver 为
  preview ref 优先后 `1/1 PASS`，完整 harness 为 `19/19 PASS`；synthetic cell
  anchor 也改用公共 `BLOCK_LEVEL`。当前只有该 test-only 文件和本阶段项目记忆为
  working-tree 增量；为避免把 dirty worktree 伪绑定到 `97b55a0…`，尚未执行新的
  Formal R7 或完整 verification。
- 上述 test-only 修复以提交 `91d5e161aa0eba03317a883e1ef93238931a3b44`
  形成 clean candidate。新 Formal R7 已恢复 `57 MATCHED + 6 EXCLUDED` 并完成
  seal/verify，seal SHA 为
  `bf199763f5f6ef2dbbc97b4869ce2b1f099fe5b69d35e8c6661b1ad2358e6100`；
  完整 verification 随后在 D1 精确计数门禁停止。11 个 D1 suites 实际为
  `452 tests / 0 failures / 0 errors / 0 skipped`，而 verification/freeze 脚本仍固定
  451；新增项为本轮 P1 ParserBacked evidence 回归，属于门禁计数滞后，不是产品
  测试失败。partial verification 与本轮 R7 已分别封存为
  `verification-failed-91d5e16-d1-count/`、`r7-superseded-91d5e16-d1-count/`，未运行
  D2、backend 全量、Compose/browser，也未创建 freeze 或审计。
- 当前 working-tree 增量仅把 D1 当前门禁同步为 452：verification log 名称、精确
  计数、freeze validator 与其 fixture 同步更新；freeze/core-scope Node 定向合计
  `11/11 PASS`，脚本中无残留当前 451 门禁。历史运行的 451 数字保持不变。
- 上述增量随后形成 HEAD `ad14800c161c096018f91fc8021feec9338a951b`，完成从零
  Core verification 并冻结为 subject
  `75d86031f7b65c0047270d2933c0397331ae53c9cf1957dc856f93d859a8fb66`；
  freeze manifest SHA 为
  `905f48adb02a810618f57b53d52b56a828a7c15ffd8166e4711aa795c544b560`，
  verification summary SHA 为
  `8b7813ad959868642362c5cd54b536762c7a94475350911157c2cca547c1c479`。
  CC AUDIT 与两个全新 Codex auditor 均为
  `GO / P0=P1=P2=blocking=0`，PR #37 已更新并 push 精确受审 HEAD。
- GitHub Actions run `30729941151` 的 Authorization evidence 与 admin-web 均通过，
  backend 为 `904 tests / 1 failure / 3 skipped`。只读重建证明失败不是四个历史
  CRLF JSON：它们在 Linux fresh clone 的 SHA-256 正确；实际根因是
  `console-summary.md` 与 `occurrence-comparison.csv` 的冻结 SHA 绑定 LF，
  `.gitattributes` 却将两者声明为 CRLF，fresh checkout 因而改变字节。
- 当前已获用户明确授权完成最小修复：只把 TASK-036 v2 Markdown/CSV 改为
  `text eol=lf`，四个 JSON 继续 `text eol=crlf`，不迁移历史 seal。Windows 与断网
  Linux ephemeral-commit + fresh-clone 定向均为 `31/31 PASS`；Linux checkout 六个
  SHA 全部等于现有冻结常量。当前 tracked 增量为该属性修复与本阶段项目记忆；
  `75d86031…` 三份 GO 已因 tracked 内容变化整体失效；当前阶段一修复已形成 clean
  candidate，尚未对该新 HEAD 执行 verification、freeze 或审计。
- 上述属性修复与阶段记忆形成 HEAD
  `c21a68d844a90dddc01b511e1b76403a5ed1c39b`；完整 Core verification 全部通过，
  freeze subject 为
  `72b934c271e15bbb4e5e76f24a7413015eb949f0bcb77b432177ea85ef63f84b`。
  第一名全新代码/架构 Codex auditor 返回
  `NO_GO（P2=1 / blocking=1）`：`FamilyModelCallPlanner` 对同一 role 的跨-shard
  request 逐条预算，可使该 role 同时 requested/uncovered 并保留部分 evidence。
  CC `deepseek-v4-flash` 审计因 `$8` budget exhausted 未产出报告，第二名 Codex
  auditor 未启动；`72b934c2…` 及其 verification/freeze 不得用于最终收口。
- 用户已批准该 D2 finding 的最小修复。主 Codex 先以同 role、双 shard、第二 block
  超预算用例复现 `1/1 FAIL`，再把 planner 改为先按 role 聚合/去重 required blocks、
  使用 role 最高 priority 排序后整体装入硬预算，并增加 requested/uncovered 不相交
  不变量。绿测为 D2 `21/21`，D2 + 历史 Track B runtime contract 联合 `22/22`，
  freeze/Core scope Node `11/11`；当前只是 tracked working-tree 定向证据，尚未形成
  新 clean HEAD，也未运行完整 verification、freeze 或三审。
- 上述原子预算修复和 D1 计数叙事修正随后形成 HEAD
  `979ddbd3f9c903ee6cce40cf578676b084096f91`，完整 Core verification 通过并冻结为
  subject `3e4e8d00cb477ae79fdefac7449fb623ad123a16f8be096f9c07f559ff99587f`，
  verification SHA 为
  `68154e589b91e6c32a9e6fe4979f7b82ebc2cf5e1618ad550a9e645ffe2a500f`。
  重试后的 CC AUDIT 与测试/安全 Codex auditor 为全零 `GO`，代码/架构 Codex
  auditor 为 `NO_GO（P1=1 / blocking=1）`：evaluator 选出的精确
  `requiredBlockIds` 未写入 `RuntimeEvidencePacket.ModelAssistAdmission`，packet
  转回 role request 时被扩大为全部 candidates，可把 `localRelation=NONE` 的 block
  送入 family budget。依硬停止条件，`3e4e8d00…` 已拒绝，canonical verification、
  freeze、R7 与 browser 证据均迁入对应 `*-rejected-3e4e8d00/` 封存目录。
- 用户批准最小 D2 seam 修复后，真实 packet JSON round-trip → role request → family
  plan 回归精确复现 `block-c` 被错误加入。当前 admission 持久化 evaluator 的精确
  block 集合并校验非空/去重/allowed 一致性，role request 只消费该集合；旧 Track B
  corpus 字节和人工 hash 不变，只在测试内以内存迁移方式用独立 eligibility source
  signals 补齐新字段。定向绿测为 D2 `22/22`、D2 + 历史 Track B runtime contract
  `23/23`、freeze validator Node `4/4`。当前仍是 tracked working-tree 修复，尚未形成
  新 clean HEAD，也未执行新的完整 verification、freeze 或三审。
- 上述 D2 seam 最小修复最终形成 HEAD
  `fe1d61bcf37060f84c8247493dd801774d6bc8b7`。从零 Core verification 通过并冻结为
  subject `90c4ae9aca0bc06dcdb2ec5af98e93590970653be56fa3dbdcc86eab00451cba`；
  CC AUDIT 与两个全新 Codex auditor 均为
  `GO / P0=P1=P2=blocking=0`。GitHub run `30748527866` 全绿，PR #37 于
  2026-08-02 合并为 `115be530480e2ff9b92a7076b2668c066a44ae5c`。
- 新 Track B holdout 已在独立分支
  `codex/task-eval-002-track-b-holdout` 建立 12 个合成最小 packet source signals：
  5 MEDIUM + 4 CONFLICTED eligible，以及 deterministic HIGH、invalid bundle、
  no reliable anchor 三个 zero-call control。扩展 pre-seal 定向为 Node `51/51`、
  JUnit 新旧 Track B + D2 联合 `24/24` 通过。
- 已按项目负责人既有确认建立 `ADR-022` 和精确 standing-grant 机器门禁；新
  holdout request contract 固定为 9 个 runtime-isomorphic 单 packet 调用，禁止沿用
  旧 run-v3 的 6 个 family 分组。离线 dispatch test 证明未封印时不创建任何输出，
  封印后才可派生绑定 actual input、dispatch、call set 与 9 个 outbound request hash
  的 receipt。
- holdout DeepSeek runner 已复用现有 one-time execution claim 与 secure transport：
  preflight 不读取 KEY/不触网，正式路径固定 9 calls、strict stop/schema、零自动重试，
  UNKNOWN_SIDE_EFFECT 写终态 BLOCKED receipt；注入式假 Provider 成功/失败回归 `2/2`。
  Codex seal 只接受 `fork_turns="none"` 的严格结构化 agent output，回归 `1/1`。
- holdout unblind/evaluator 已建立有限轮次门禁：先验证两份 blind opinion、Codex
  receipt、DeepSeek claim/9 个 request receipts，再读取 human seal；schema、可靠
  anchor、role、candidate、anchor、abstention 任一非 100% 即
  `NO_GO_MODEL_MISMATCH / NOT_ESTABLISHED`，且 `sameHoldoutRetryAllowed=false`。
  GO、结构合法但答案错误的 NO_GO 与完整先验时间顺序回归合计 `3/3`。
- 项目负责人已确认 challenge
  `TBH2-fac56106204c4b93a11befc577369360`，corpus SHA
  `15d1f845d65c5018363bba324d0527f36ff07ddf0703f188f1d557711a9e2b24`，
  challenge SHA
  `37a05c4b0d2ed4b21282f0fa09dd00938da60857a2d53e187490a28f6cf5d23a`，
  review SHA `f07b4b1ceb193531089af6b37b964577b9655f4173f92abb40ea6523a4ed4b1d`；
  12 条 decisions 原样封印，decisions SHA
  `f8557a880a201376f00598eb6b7cc30e54e9a8a42df3d3c7070ade52376fdb5b`，
  human seal SHA
  `11fbcc06458e5c24c9c3080f1b126c9b9c1358ad90b2532096b0ab5b90f3601b`。
- 离线 dispatch/preflight 绑定 model input
  `bd402b4019f5a8cddcd6c433a4d9981d8e39300df8ad488e09a6ccd92fdb3dcc`、
  dispatch `8fe2a996c011f190bfbfada4446645a6d5e693b2c98d845dfd4e3995d211647b`、
  provider call set
  `8c63e3777fbc36a7f05821e9f3ccf2d81f64c1ccfdb9c4d2acd29d7467c8e5e6`
  与派生 receipt
  `76d1b171cda3ca55d4d9b92e3a4d3d446e08034ed4ebb9d607e64347b887ffda`；
  计划调用为 9×1，3 个 controls 未进入调用集合。
- 全新 `fork_turns="none"` Codex blind evaluator 已返回 9 条严格 schema opinion，
  封存 opinion SHA
  `6f091ae123268b0675e0debd1b09568d5ba7ec01df72220f124b228424dc78e8`、
  receipt SHA
  `8a9ea6c8fa339e4d4dc0067c4060270fc1b897eb61e06b2c2b1f6e40b9b54201`；
  尚未解盲，不得把该意见描述为与人工答案一致。
- DeepSeek `deepseek-v4-pro` 正式执行已消费 claim
  `da3f1eb1770a1eeb25d62527db58a663834549ec6cdd7be41e2e244143f329c9`。
  第 1 个 call 完成，第 2 个 call 以
  `SCHEMA_INVALID_OR_EMPTY / OPINION_SCHEMA_INVALID` fail closed；终态 blocked
  receipt SHA
  `f79060feb0577caec735cd12ffbfdb7a8edd8d36b42c2dd8ec2cf3aa4c49c6d2`，
  `networkAttempted=true / automaticRetryPerformed=false / completedCallCount=1`。
  runner 未持久化 raw response、reasoning 或 Secret；无 DeepSeek accepted opinion，
  未执行 unblind、freeze 或三方审计。
- TASK-EVAL-003 successor 已建立 12 个全新 synthetic packet：5 MEDIUM + 4
  CONFLICTED eligible、3 zero-call controls。相对旧 18-packet 与失败 holdout，
  identity、23 个 candidate values、19 个 evidence texts 的 overlap 均为 0；pre-seal
  Node 回归连同旧 holdout contract 为 `6/6 PASS`。
- successor source SHA 为
  `84f04dbfe35b11a0945124a5546412abd3f98f9a95081691e2963c2b7c0a486d`，
  corpus SHA 为
  `a609005418a970c45f4494cd95c94f64ee7704bec5dcdcf42cb6a11dfd066de3`，
  review SHA 为
  `f8c4c6a00d65bf3d4ae1c9628cb7bf99cee07575682755427520f18b9ddd099f`，
  preseal manifest SHA 为
  `1a22f5992bdc9e2bcdc9c06a3d01ee683a85a57b58288904385cec43e9820c8c`。
- 人工 challenge `TBS1-5bfbe8b667944e7182df3ec537d45725`，challenge SHA
  `0bd06f76212b902d090ec16db77a404af733f8a83da5eb42e3fba78570bfbb4f`，
  已由项目负责人在到期前确认 12 条 decisions；confirmation SHA
  `38b7d53451e6b1ea074298e515ec47d3ad447fc0391afe526809b0fe5e50642e`，
  human seal SHA
  `6ad4a1e4098ace3d572b496772c40f9458105e5bb7be4bffcfccfec37527dcad`。
- successor 的 post-seal 工具链已在临时 fixture 中完成：独立 schema/track、人工 seal
  create/verify、9×1 dispatch、Codex opinion seal、DeepSeek strict runner、双意见先验
  验证和解盲均可达；正式目录仍保持 model-dark。successor 只允许 DNS/pre-send 的
  一次安全重试，HTTP 已开始后的 schema/content/timeout/UNKNOWN_SIDE_EFFECT 均保持
  零重试；成功证据不保存 Secret、raw response 或 reasoning。Java runtime seam
  定向为 `1/1 PASS`，`git diff --check` 通过。随后补强时间序门禁，机器强制
  `human confirmedAt <= dispatch.createdAt <= evaluator startedAt`，并为
  unblind seal 增加 verify-only 重建；successor verification/freeze 工具可在临时
  Git subject 上绑定 clean HEAD、tree、full diff、console、全部 evidence hash 和
  三个指定审计角色。扩大后的当前精确回归为 `45/45 PASS`；正式 freeze 尚未生成。
- 一次非正式的全 `*track-b*` 历史 sweep 为 `63/79 PASS`；16 项失败来自当前 worktree
  未包含的旧 v1/v2 archive/opinion 输出，以及另一套旧 admission prompt 的冻结 hash
  与当前字节不一致。该 sweep 不作为 successor 当前门禁，也不得被描述为全量绿；
  正式 freeze 前须按 TASK-EVAL-003 精确验证清单保留原始 console 并明确排除的历史
  fixture 边界。
- successor postseal 正式身份为 model input
  `654168c6b0a671f3f8d0e44222be51d6c97edd8cc5f67e2c41b1a1248032fe6e`、
  provider call set
  `79a3ab0b100dfddce64b506840abb2346e2b77aaf83abf07ea44ef9d53a9550a`、
  dispatch `f9d6cd05340cbc82fbaada00462ac4570fb008a9c19dc0203b7155090ec94807`、
  derived receipt
  `1788387d85e8d21c365d4f16e4a5123dad78730b9d66d1b67e75c43643159138`。
  Codex 盲评 9/9 严格 schema accepted，opinion SHA `eb0face7…`、receipt SHA
  `594f503f…`；未解盲。
- DeepSeek claim SHA
  `45f1d5ead1d34e41f015efb08d951039f5ee16f9be54998595e30069704bdc83`
  已消费。第 1 个 call 返回稳定类别 `AUTHENTICATION_FAILED`，terminal blocked
  receipt SHA
  `03f46cb8b4af330dd12afb4640e7942e5a38c81e4d7189a78988f3f7daa99674`，
  `networkAttempted=true / completedCallCount=0 / automaticRetryPerformed=false`。
  未保存或回显 raw response、reasoning 或 Secret；同一 successor 不得重试、调 prompt、
  换 KEY/模型补跑或解盲。
- TASK-EVAL-004 final source SHA `1a91aebb4d03e83a94ef5784d9e762dd293eb6b9632d90c056aabdd32cdf8117`，
  corpus SHA `ed216d435b82af250c39e0ba927f75ceaa233f70ae9aa516fa4adad42aa3ca95`，
  review SHA `65c775b2a5018997ac9bcc7f5c66979608970e520037897698ed93c7430a1971`，
  preseal manifest SHA
  `b03fdca495145d5c824bb3e35334b474c099182310d7a479869d26df2e645e1c`。
  相对前三套共 42 prior packets 的 identity、23 candidate values、19 evidence texts
  overlap 均为 0；精确定向 Node 为 `22/22 PASS`。
- final challenge `TBF1-0fc18b77754248c28d472b0245a57b81`，challenge SHA
  `74f051c179865afb97efb8709fe903e76689c76b19c86acc12b09fae68813692`，
  2026-08-05T02:46:07.009Z 到期。项目负责人已在到期前绑定 challenge/corpus/review
  SHA 确认全部 12 条 decisions；confirmation SHA `b225f36a…`、decisions SHA
  `4fb8dd89…`、human seal SHA `8716d66d…`。
- final 正式离线身份为 model input `bd648dd1…`、call set `9881d520…`、dispatch
  `141d5344…`、derived receipt `f17018cf…`；9 calls / 9 eligible / 3 excluded
  controls，preflight `networkAttempted=false`。全新 Codex blind opinion 已封存为
  `8abc4063…`、receipt `70acc344…`，未解盲。
- DeepSeek final claim SHA `62a7451e…` 已消费。第 8 个 call
  (`failedCallIndex=7`) 以 `SCHEMA_INVALID_OR_EMPTY / OPINION_SCHEMA_INVALID`
  fail closed；terminal receipt SHA `8b113c74…`，
  `networkAttempted=true / completedCallCount=7 / automaticRetryPerformed=false`。
  未保存 raw response、reasoning 或 Secret；同一 corpus/claim 不得重试、换模型/KEY、
  调 prompt 或解盲。
- 终态只读核验：phase-appropriate Node suite `44/44 PASS`，actual KEY 与 forbidden
  Provider field 泄漏文件数均为 0，claim/model-input/Codex opinion contract 重建通过，
  DeepSeek accepted opinion、unblind 与 audit 目录均不存在。若把仅适用于 model-dark
  preseal 阶段的 `track-b-final-preseal.test` 继续纳入 post-seal suite，则为
  `44/45`：唯一失败是该测试仍断言 confirmation/seal/run-v1 必须不存在，属于纯证据
  测试未区分阶段；依正式执行后的硬停止规则当前不自动修复。

## 当前活跃任务

- `tasks/active/TASK-MVP-002-review-workbench.md`
- `tasks/active/TASK-MODEL-001-model-profile-secret-readiness.md`
- `tasks/active/TASK-EVAL-002-blind-semantic-evaluation.md`
- `tasks/active/TASK-EVAL-003-track-b-successor-admission.md`
- `tasks/active/TASK-EVAL-004-track-b-final-independent-admission.md`
- `tasks/active/TASK-EVAL-005-track-b-schema-stability-and-fifth-admission.md`
- `tasks/active/TASK-EVAL-006-track-b-provider-conversation-recovery.md`
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`

## 当前阻塞项

1. TASK-EVAL-006 的模型评测已重验证为 GO，但 R6 subject `a9daf62f…` 因 CC audit
   projection transport 的 `P0=1 / P2=1` 失败；因此正式 admission 仍未建立，A0/A1/A2
   仍无资格。
2. subjects `9dddbe43…`、`e77a8635…`、`a9daf62f…` 的失败/中断/NO_GO freeze 与报告
   必须只读保留，不得与后续报告组合通过。
3. TASK-EVAL-002~005 的历史 BLOCKED/NO-GO claim、opinion、report、seal 均不可重试或
   改写；本次修复没有重跑 DeepSeek、创建第七套 corpus 或改变这些历史终态。
4. PUBLIC profile 继续 `EVALUATION / disabled / unbound`；模型不得直接生成或改变
   Finding/verdict。未完成新三审、CI、PR/merge 门禁前不得进入 Provider runtime。

## 下一步

1. 将 R7 transport/CR diagnostic、`a9daf62f…` 失败 freeze 与三份审计终态、完整 PASS
   verification evidence 形成 clean commit；不修改模型输入、意见、人工 seal 或 Provider 边界。
2. 对 clean HEAD 生成并验证唯一新 immutable freeze；发生身份漂移立即停止。
3. 对同一新 subject 从头派发 CC AUDIT 与两个全新
   `fork_turns=none / gpt-5.6-sol / xhigh` Codex auditors；任一 finding 立即停止，旧 GO
   不得组合。
4. 只有三份均 `GO / P0=P1=P2=blocking=0` 后才按既有授权 push、PR、CI/merge；
   A0/A1/A2 仍须等待该门禁完成。

不得声明 Production Ready，不得宣称 TASK-028/031/032 已解锁。
