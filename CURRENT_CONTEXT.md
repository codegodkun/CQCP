# CURRENT_CONTEXT.md

更新时间：2026-08-01

## 当前阶段

当前处于 `MILESTONE-MVP-002-CORE` 收口阶段，唯一基线为
`origin/master@1035739b751386176e47c6871738a62bff86de02`。

先行 Core integration unit 包含：

- M1：execution 任务清单、精确结果查询、parser-backed preview、原始 DOCX 下载和
  左右审核工作台；
- M2：immutable Model Profile config、server-side Secret Reference、endpoint
  allowlist、readiness/connectivity 与管理页；
- M3 已完成部分：Track A Codex/DeepSeek 盲评、Track B runtime-isomorphic seam、
  R7 zero-call 与旧 18 packet run-v3 `NO_GO_MODEL_MISMATCH` 证据；
- TASK-034 v29/R7 与 TASK-036 B1/B2/C1/C2/D1/D2 seam。

Provider A0、standing/CC 审计传输、A1 adapter、A2 shadow 和 guarded assist A3 均不
进入 Core source diff。PUBLIC profile 保持 `EVALUATION / disabled / unbound`，
普通 Demo 继续使用 `MVP_DEMO_MOCK`；模型不得直接改变 Finding/verdict。

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

## 当前活跃任务

- `tasks/active/TASK-MVP-002-review-workbench.md`
- `tasks/active/TASK-MODEL-001-model-profile-secret-readiness.md`
- `tasks/active/TASK-EVAL-002-blind-semantic-evaluation.md`
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`

## 当前阻塞项

1. `ea19a52a…`、`33890cb…`、`81e47f…`、`5bcfe410…` 及 `c156b4a7…` 对应的审计轮
   均已因 `NO_GO` 失效，永远不能用于后续收口；其中任一旧 `GO` 也不得单独复用。
2. 收口只接受从当前 clean candidate HEAD 从零重建的 R7、Compose/browser、
   四轮不可覆盖 JUnit、完整验证和 immutable freeze，并且必须使用全新
   CC AUDIT 与两个全新 `fork_turns="none"` Codex auditor。动态 freeze/audit
   结果以 hash-bound outputs 和 Git 事实为真源，本文不复制运行中状态。
3. CI、PR 与 merge 尚未完成；没有主线 merge 就不能声明 TASK-036 seam 已集成。
4. 新 Track B admission holdout 尚未创建：固定 12 packet（9 eligible + 3
   controls），人工 ground truth 必须在模型访问前封印，正式运行只允许一次。

## 下一步

1. 将 `c156b4a7…` 审计 finding 的原子修复与本阶段记忆写回形成新的 clean Core
   candidate HEAD。
2. 在该新 HEAD 上从零执行 R7、两轮 backend、D1/D2、admin-web、Core Node、
   OpenAPI、Track A/B、bootJar，以及每轮随机凭据的 Compose + Chrome/CDP 浏览器
   验收；所有原始 console/JUnit/事件/access-log/截图均绑定实际字节。
3. 验证全部通过后生成并验证新 freeze，再从零执行 CC AUDIT 与两个全新 Codex
   独立审计；旧审计会话和结论不复用。
4. 三审全零 GO 后 push、创建 PR、等待 CI；满足既有授权条件后 merge。
5. Core merge 后建立并执行一次新的 Track B holdout；失败即停止并重新收敛。

不得声明 Production Ready，不得宣称 TASK-028/031/032 已解锁。
