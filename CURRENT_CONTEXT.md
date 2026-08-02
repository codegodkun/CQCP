# CURRENT_CONTEXT.md

更新时间：2026-08-02

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

## 当前活跃任务

- `tasks/active/TASK-MVP-002-review-workbench.md`
- `tasks/active/TASK-MODEL-001-model-profile-secret-readiness.md`
- `tasks/active/TASK-EVAL-002-blind-semantic-evaluation.md`
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`

## 当前阻塞项

1. `ea19a52a…`、`33890cb…`、`81e47f…`、`5bcfe410…`、`c156b4a7…`、
   `3bc24a84…`、`8d01dd9a…`、`75d86031…`、`72b934c2…` 及 `3e4e8d00…`
   对应的审计轮均已失效，永远不能用于后续收口；其中任一旧 `GO`
   也不得单独复用。
2. 收口只接受从当前 clean candidate HEAD 从零重建的 R7、Compose/browser、
   四轮不可覆盖 JUnit、完整验证和 immutable freeze，并且必须使用全新
   CC AUDIT 与两个全新 `fork_turns="none"` Codex auditor。动态 freeze/audit
   结果以 hash-bound outputs 和 Git 事实为真源，本文不复制运行中状态。
3. PR #37 已存在但 run `30729941151` backend CI 失败、merge 未完成；PR body
   授权区块已补齐且该 check 已通过。没有新 subject 三审和全绿 CI 就不能 merge，
   也不能声明 TASK-036 seam 已集成。
4. 新 Track B admission holdout 尚未创建：固定 12 packet（9 eligible + 3
   controls），人工 ground truth 必须在模型访问前封印，正式运行只允许一次。

## 下一步

1. 完成当前 D2 required-block identity 修复的 diff/格式自检，并在既有授权下形成新的 clean
   candidate；JSON 历史 CRLF seal、历史 451/20 运行记录不得改写。
2. 在该新 clean HEAD 上从零执行 R7、两轮 backend、D1=`452`、D2=`22`、admin-web、Core Node、
   OpenAPI、Track A/B、bootJar，以及每轮随机凭据的 Compose + Chrome/CDP 浏览器
   验收；所有原始 console/JUnit/事件/access-log/截图均绑定实际字节。
3. 验证全部通过后生成并验证新 freeze，再从零执行 CC AUDIT 与两个全新 Codex
   独立审计；旧审计会话和结论不复用。
4. 三审全零 GO 后补齐 PR #37 授权区块、push 精确受审 HEAD 并等待 CI；满足既有
   授权条件后 merge。
5. Core merge 后建立并执行一次新的 Track B holdout；失败即停止并重新收敛。

不得声明 Production Ready，不得宣称 TASK-028/031/032 已解锁。
