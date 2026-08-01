# TASK_SPEC-036-D1：版本化 ratio role/scope v20260729.1

状态：IMPLEMENTED / R7 PASS / MILESTONE FINAL AUDIT PENDING

父任务：`TASK-036`

关联门禁：`TASK-034` R6B、`TASK-EVAL-002` Track B、`TASK-MODEL-002`

Task Level：`L3 高风险治理`

执行者：主 Codex

创建日期：2026-07-29

## 目标

在不改写 `v20260715.1` 冻结语义的前提下，发布新的
`ruleSetVersion=v20260729.1` 与
`scopePolicy.version=consistency-scope-v20260729.1`，解决全量扫描中：

1. 其他支付形式、履约担保等上下文仅因出现“进度款/竣工款/结算款/质保金 +
   百分比”而被错误归属为 ratio candidate；
2. product template 的未选节点付款 branch 与已选月度付款 branch 同时产生
   prepayment / settlement occurrence；
3. engineering template 的 `B模式：按节点付款` 明细被计入 monthly-only
   progress occurrence。

新版本仍逐 block 扫描完整 document、保留 ledger、fail closed，不读取
structured `paymentMethod`、sampleId、fixture、人工 expected、human
`includedInConsistencyEvaluation` 或 candidateValue 反搜结果。

## Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md`
- `docs/ARCHITECTURE.md` 第 10.1、22.1.1、22.4 节
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`
- `tasks/active/TASK_SPEC-036-C1-consistency-set-runtime-core.md`
- `tasks/active/TASK_SPEC-036-C2-consistency-set-execution-activation.md`
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/active/TASK_SPEC-034-B-monotonic-occurrence-bridge-v2.md`
- `packages/review-assets/review-point-definitions/review-points-v20260715.1.json`
- `packages/review-assets/rule-sets/ruleset-v20260715.1.json`
- `outputs/task-034-mvp-e2e-acceptance-v2/`

## Optional Context

- `docs/ai-review.md`

## Out of Scope

- 修改或覆盖 `v20260715.1` 的 asset、scope/probe、loader contract 或历史输出。
- 修改人工 XLSX、DOCX、fixture、expected、人工 anchor 或 exclusion。
- 使用 structured `paymentMethod`、human candidate/evidence、sampleId 或 fixture
  路径决定 candidate inclusion。
- 修改普通 `MVP_DEMO_MOCK / v20260705.1` binding。
- 模型 Provider、`REVIEWING_MODEL`、Finding 覆盖、公共 API 或数据库 migration。

## R6B 冻结输入事实

- 点级：18 `MATCH` / 9 `NOT_OBSERVABLE`；18 `PASS` /
  9 `NOT_CONCLUDED`；9 点均为 `SYS_ROLE_CONFLICT`。
- point-local SourceAnchor：001/002/003 为 16/17/16，共 49；人工纳入为
  20/17/20，共 57。
- occurrence bridge 修正为逐 group fail-closed 后：47 `MATCHED` +
  10 `NOT_OBSERVABLE` + 6 `EXCLUDED`。
- 10 个 group mismatch = 9 个失败 ratio 点各缺 1 anchor + 002
  PREPAYMENT 多 1 个未选 node branch anchor。
- 真实污染上下文包括：
  - 001/003 实物支付、支付担保、水电费等段落；
  - 001/003 `B模式：按节点付款` 的多个 node progress 段；
  - 002 `□按节点付款` 到 `√月度付款` 之间的未选 branch；
  - 002 warranty paragraph 内“延期支付结算款”造成 settlement 假归属。

## 冻结契约

### 1. 版本与不可变性

1. 新 RuleSetVersion 固定为 `v20260729.1`。
2. 新 scope version 固定为 `consistency-scope-v20260729.1`。
3. canonicalization 与 anchor identity 继续引用既有 immutable version；未变化的
   module 可继续引用旧版本。
4. `v20260715.1` 两个 asset 必须逐字节保持；loader/snapshot/probe 的 v15
   分支必须由既有 frozen tests 与本规格新增的三样本差分门禁证明行为不变。
   v15 asset SHA-256 固定为：
   - `review-points-v20260715.1.json`：
     `65bc666993d75afe8aaa9817633916a3614546970d1ca74453e43f2c529f7a98`
   - `ruleset-v20260715.1.json`：
     `22a86d933a6dfcee20e37979be1268fcc29d063687f0f287ce8a07cfda80bd14`
5. code-owned release 可同时声明 v15 与 v29 READY；普通 task binding 不自动选择
   v29。只有显式冻结 v29 的 execution/test harness 才能激活。
6. v29 asset 是 v15 的**完整不可变快照**，不得只保存九点
   `consistencyPolicy`。允许的逐字段 delta 只有：
   - 两个 asset 的 `assetId/version/createdAt/changeReason/source.paths[2]/
     source.bindingNote`；
   - RuleSet 的 `moduleVersions.reviewPointDefinitions` 三字段；
   - 九点 `scopePolicy.version`；
   - 五个 ratio point 的 `strongExcludedSemanticContexts` 从 `[]` 变为
     `["INACTIVE_PAYMENT_BRANCH"]`。
   其余九点 `name/family/candidateRole/requiredStructuredFields/
   optionalStructuredFields/applicabilityPolicy/executionStrategy/
   deterministicRule/currentRuntimeSource`、cardinality/budget、region/context、
   required attribution、canonicalization、anchor identity 与未变 module ref
   必须逐字段等于 v15。
   两个 v29 asset 的 `createdAt` 固定为 `2026-07-29`，
   `source.paths[2]` 固定为
   `tasks/active/TASK_SPEC-036-D1-versioned-ratio-scope-v20260729.1.md`。
7. v29 identity 固定：
   - RuleSet `cqcp.ruleset.mvp.consistency-set.v20260729.1`，path
     `rule-sets/ruleset-v20260729.1.json`；
   - ReviewPointDefinition
     `cqcp.review-points.mvp.consistency-set.v20260729.1`，path
     `review-point-definitions/review-points-v20260729.1.json`；
   - 两者 `status=DRAFT`、`source.type=architecture-approved-policy`、
     `runtimeBinding=NOT_BOUND`；RuleSet `loaderEnabled=false`、
     `databasePersistence=false`、`productionEffect=NONE`；
   - 六个 module ref 中只有 review-point ref 变为 v29，其余五个精确复用 v15。

### 2. 全量扫描与 branch scope

1. collector 仍遍历 document 全部 blocks，每个 block 恰好一条 ledger。
2. classifier 只使用 `DocumentBlock.blockId/text/sectionPath/regionType` 和 blocks
   原始顺序。marker normalization 仅允许：删除全部 Unicode `White_Space`、
   全角冒号转 ASCII 冒号、trim；不得删除 checkbox、汉字、数字或其他标点。
3. 每个 branch envelope 的所有 marker 必须都是 `BODY`、位于完全相同且非空的
   `sectionPath`，并按 document block order 唯一配对；同 block 双 marker、跨
   section/appendix 配对、重复 marker、逆序、缺边界均为 classifier uncertainty，
   不得猜测 template/profile。
4. product envelope 仅由以下三项同时证明：
   - 同一 section 内唯一 normalized exact node marker `□按节点付款`；
   - 其后唯一 normalized exact active marker `√月度付款`；
   - 同 section 不存在 `√按节点付款`、`√节点付款`、`□月度付款` 或第二个 active
     payment marker。
   node marker block **包含**在 inactive range，active marker block **不包含**；
   即 `[nodeMarkerIndex, activeMonthlyMarkerIndex)` 对五个 ratio point 标为
   `INACTIVE_PAYMENT_BRANCH`。
5. engineering envelope 不通过“template 类型”或 profile routing 判断，只由同一
   section 内以下唯一、顺序有效的文本证据证明 B branch 未选：
   - selector block normalized text 必须为 `进度款:A模式□B模式`，且不含
     `□A模式`、`√B模式` 或第二个 active marker；
   - selector 后唯一 node marker normalized exact
     `B模式:按节点付款:`（尾冒号可省略一次）；
   - node marker 后第一个 normalized text 以 `竣工款:` 或 `安装完工款:` 开始的
     direct completion block 作为 common end。
   inactive range 为 `[nodeMarkerIndex, commonEndIndex)`；selector 与 A-mode
   monthly block不排除，common end 不排除。只要 selector/node/end 任一不唯一、
   不同 section、逆序或存在互斥 checkbox 信号，即 classifier uncertainty。
6. document-local dispatch 固定为：
   - 两类 family 均无任何 exact/relevant marker：`NO_INACTIVE_BRANCH`，classifier
     视为 handled，所有 block NOT_EXCLUDED；
   - 恰好一类形成完整且唯一 envelope，另一类零 relevant marker：使用该 envelope；
   - 任一类出现部分 marker、重复/逆序/跨 section/互斥 marker，或两类同时形成完整
     envelope：`UNCERTAIN`，不得选择一种 template。
   relevant marker 只指 §2.4/2.5 列出的 exact token，不对普通“节点/模式/付款”
   自由文本做 profile 推断。
7. 对 policy 声明 `INACTIVE_PAYMENT_BRANCH` 的 ratio point，classifier 必须在该
   document 执行一次。正常确定且 block 不在 inactive range 时为 NOT_EXCLUDED；
   range 内为 `EXCLUDED / SEMANTIC_EXCLUDED`。不确定时所有本点 BODY scope block
   均产生且仅产生一条 `UNCERTAIN / SEMANTIC_CLASSIFIER_FAILED` ledger，
   `fullPolicyScopeScanned=false`，readiness 固定进入
   `SYSTEM_FAILURE / SYS_EVIDENCE_BUNDLE_INVALID / NOT_CONCLUDED`，无 Finding。
8. exclusion 只来自 document block identity/order/text 与版本化 classifier；
   classifier/probe/candidate inclusion 不读取 structured `paymentMethod`、
   sampleId、文件名/路径、fixture 或人工数据。现有 Review Engine 使用
   `paymentMethod` 判断 monthly-only point applicability 的后置逻辑不在本批修改。
9. 被排除 block 仍记录 `EXCLUDED / SEMANTIC_EXCLUDED`，不得从 ledger 消失。

### 3. v29 point-local ratio probe

1. v29 ratio probe 只执行下表冻结 grammar。匹配前只允许删除全部 Unicode
   `White_Space` 与全角冒号等价；百分比 token 固定为 ASCII decimal
   `0..100` 加 `%`（空白已在 normalization 删除）。除 product 的
   adjacent-block rule 外，
   role、payment base、连接词与 capture 必须在同一 block。

| point | role/start | payment base 与连接词 | 唯一 capture |
|---|---|---|---|
| PREPAYMENT | `本工程无预付款`，或 block 以 `预付款:` 开始 | exact `无预付款` 生成 `0`；否则仅接受 `预付款比例(?:为|[:])PCT` | `0` 或 `PCT` |
| PROGRESS engineering | block 以 `A模式:按月形象进度付款` 开始 | `支付` 后、`上月完成合格形象进度产值的PCT` | payment-base 后的 `PCT` |
| PROGRESS product | 当前 block 含 `支付至乙方到货总价的PCT` | 当前 block 自身以 `到货验收款:` 开始，或其紧邻前一 BODY block（同 section）以 `到货验收款:` 开始且不含 `%` | `到货总价` 后的 `PCT` |
| COMPLETION | block 以 `竣工款:` 或 `安装完工款:` 开始 | `支付至已完工程量的PCT` 或 `支付至该批安装完工款...的PCT` | payment-base 后的 `PCT` |
| SETTLEMENT | block 以 `结算款:` 开始 | `支付至结算金额的PCT` 或 `支付至结算总价的PCT` | payment-base 后的 `PCT` |
| WARRANTY | block 以 `质保金:` 开始，或包含 `提交保证金额` + `《质量保函》` | `质保金为工程结算总价的PCT`；或 `提交保证金额为...的PCT的《质量保函》` | 对应 base 后的 `PCT` |

2. 同一 block 内的发票/产值 `100%`、期限数字或其他百分比不进入 candidate；
   只发出上表 capture。相同 grammar 出现多个相同 capture 时保留一个 block
   identity；出现多个不同 capture 时全部发出并由既有 same-identity conflict
   fail closed，不按位置猜一个。
3. 下列文本必须 `SCANNED_NO_MATCH`：node 明细；水电费在进度款中 `103%`
   扣除；实物支付 `10%/70%/80%` 提升；支付形式/非现金支付/调差；履约担保、
   履约保函及工程款支付保证担保 `10%`；只提及质保金、竣工款或结算款；warranty
   段内“延期支付结算款”；仅有发票/产值 `100%`。
4. v29 probe 对非 ratio 四点完全复用 v15 probe；五个 ratio point 不执行 v15
   `WHOLE_TEXT/ROLE/WEAK` fallback。probe exception、adjacent context 不可验证或
   policy/classifier 不匹配均 fail closed，不 fallback v15/legacy。

### 4. execution 与验收

1. state machine 只按 execution 冻结的 ruleSetVersion 加载同版本 snapshot。
2. v15 execution 继续得到 R6B 行为；v29 execution 使用 v29
   scope/probe，不改普通 binding。
3. TASK-034 新正式运行写入
   `outputs/task-034-mvp-e2e-acceptance-v3/`，不得覆盖 v1/v2。

## 可证伪验收断言

1. v15 synthetic full-scan test 仍同时看到 70 与 75；不得被 v29 probe 改写。
2. v29 对 001/003 实物支付、水电费、履约担保段均
   `SCANNED_NO_MATCH`，只保留点级直接比例。
3. v29 对 002 未选 node branch 记录 semantic exclusion；prepayment 只保留
   checked monthly branch 的一个 occurrence，settlement 只保留 95。
4. marker 缺失、重复、逆序或双 active 时 fail closed，不猜测 branch。
5. v29 loader 拒绝 path traversal、资产 identity/version/scope mismatch、
   forbidden fixture/human 内容和未知版本。
6. 数据流差分必须证明：仅改变 structured `paymentMethod`、sampleId、document
   filename/path、同目录伪 fixture/human 文件，v29 candidate/ledger 完全相同；
   只有 Review Engine 的既有 applicability 允许因 paymentMethod 改变。
7. 每点 ledger 数量和顺序必须与 document blocks 完全一致；inactive block 精确
   为 `EXCLUDED/SEMANTIC_EXCLUDED`；classifier uncertainty 固定映射 §2.6 的
   SYS/NOT_CONCLUDED，无可靠 anchor、无 Finding。
8. 普通 task creation 与 `MVP_DEMO_MOCK/v20260705.1` binding 不变；只有显式冻结
   v29 的 execution 可通过 gate。移除 v29 code-owned release 后，同一 execution
   必须 unknown/fail closed，而 v15、legacy、普通 binding 不变。
9. occurrence bridge 升级为
   `mvp-e2e-occurrence-bridge-v3-monotonic-plus-human-oracle`：仍先仅按
   point/granularity/identity/document order 建唯一单调配对，之后才用独立人工
   fixture 做 post-pair validation，绝不以文本或 candidate 反向定位 actual：
   - ratio：移除人工编号和 selector-only 行后，人工/actual 规范化文本必须双向
     containment 至少一向成立，且人工 expected 百分比与 actual 文本中的冻结
     payment-base capture 一致；
   - PARTY：actual 必须包含人工 expected party value；BLOCK 还须含对应角色标签，
     TABLE_CELL 必须与人工 value 规范化相等；
   - CONTRACT_TOTAL：TABLE_CELL 与人工 expected 数值相等；BLOCK 同时含总价角色
     标签与该数值；
   - TAX：actual BLOCK 同时含人工 expected 中的 total/net/tax 三个 decimal
     （忽略千分位）及税款角色标签；taxRate 可位于另一人工位置，不要求在该 anchor。
   任一 post-pair oracle 失败，该组为 `NOT_MATCHED`，不得仍标 `MATCHED`。
10. bridge adversarial test 必须把正确 ratio anchor 替换为同 point、同 granularity、
    同基数、同 candidate value 的 node/实物支付污染 anchor，并证明 formal verdict
    FAIL；缺点、重复点、错 cell、额外同粒度 anchor、重排/重复 identity 同样失败。
11. R7 必须同时满足：
   - 27/27 candidate `MATCH`；
   - 27/27 PointStatus `PASS`；
   - anchors 20/17/20；
   - 57 `MATCHED` + 6 `EXCLUDED`；
   - 0 `SYS-*`、0 Finding；
   - `overallVerdict=PASS`。
12. `57 MATCHED + 6 EXCLUDED` 中的 6 只称为
   `humanGroundTruthExcluded=6`，不得冒充 production scope exclusion。R7 另行输出
   `production-branch-scope-ledger.json`，对五个 ratio point 精确证明 001/002/003
   inactive range 的 block identity/text SHA-256/status/reason。当前三份输入的
   inactive block 数固定为 4/6/4，每点 14，五点共
   `productionInactiveBranchLedgerEntries=70`，全部
   `EXCLUDED/SEMANTIC_EXCLUDED`；其他 block 仍各有唯一 ledger。
13. R7 每份 result 必须恰有 9 个唯一且完整的 `ReviewPointCode`；task execution
   snapshot、query result、runtime snapshot 与 run manifest 均精确为
   `v20260729.1`，输出路径只能是 v3。

### 5. 可执行测试门禁

宿主命令均在 PowerShell 7 执行，统一带 `--no-daemon --rerun-tasks`：

1. 既有 C1/C2/R6B 回归类固定为以下 10 类，D1 不增加或删除其 test class；
   R10/R11 安全整改新增的 1 个防御性 invocation 属于独立审计整改例外，
   不改变 D1 业务范围；整改后的基线与 D1 后均须精确
   `414 tests / 0 failures / 0 errors / 0 skipped`：
   - `ConsistencyCandidateCollectorTest`
   - `ConsistencySetCollectorTest`
   - `MinimalCandidateResolverTest`
   - `ParserBackedReviewInputPreparerEvidenceTest`
   - `MinimalReviewEngineTest`
   - `DocxWordParserSpikeTest`
   - `ConsistencyRuntimeExecutionActivationTest`
   - `RuntimeRuleSetLoaderTest`
   - `RuleSetActivationGateTest`
   - `TaskExecutionStateMachineTest`
2. 新增 `VersionedRatioScopeV20260729Test`，冻结为精确 30 个 invocation，覆盖：
   v15 synthetic 70/75；三份 DOCX v15 精确 R6B
   `18 MATCH/9 NOT_OBSERVABLE、49 anchors、47/10/6 bridge`；product 与
   engineering marker 正/反矩阵；五点 grammar 正负向；多百分比选择；adjacent
   block；ledger/SYS；dataflow invariance；v29 loader/gate/release rollback；
   ordinary binding 不变、bridge 同值污染替换 adversarial、production branch
   ledger 与 human exclusion 分账。必须为 `30/0/0/0`。
3. 两组合并门禁必须精确 `444/0/0/0`；每轮 XML testcase 名、计数、逐文件
   SHA-256 和 console
   SHA-256 进入 R7 审计包。
4. Node review-assets validator/tests 必须全部通过；v15 两个 hash 必须与 §1
   一致；v2 六个 output 文件 SHA-256 必须保持：
   - `console-summary.md` `f6d7f7307c0599e1cc7207a5bb04b7b19ff164bf5e5a0eebfabc1e2aa0e8c099`
   - `occurrence-comparison.csv` `4bf012bc8b6d0071584bab0d8d3cc916cfa7f93681cb4314d8280373648cdb8d`
   - `run-manifest.json` `7635cf2158a8fcee371f5a5d082700ca85817b00f87ecb481b6adc78b8dd7cff`
   - sample 001 `db17e982e89553f17da807175b854746224c1186ceedcea2f139ce46abe1c734`
   - sample 002 `a8f52a16b26830d03287353b465260d432a4058c8641e67832546d29ac636be9`
   - sample 003 `646efeef88495d3cfccd556d90079126e6a1630946ce2ab3467ee516cd283903`
5. 正式 R7 单独运行
   `Task034MvpE2eAcceptanceHarnessTest.formalAcceptanceEntryPointIsPropertyAndInputGated`，
   写入 v3；test 本身、R7 run manifest 与所有 §4.11–4.13 断言同时 PASS。

### 6. 文件边界与 STOP 条件

允许修改/新增：

- `packages/review-assets/review-point-definitions/review-points-v20260729.1.json`
- `packages/review-assets/rule-sets/ruleset-v20260729.1.json`
- `scripts/validate-review-assets.mjs` 与对应 test
- `RuntimeRuleSetLoader.java`、新增 v29 loader/helper
- `ConsistencyRuntimeRelease.java`、`RuleSetActivationGate.java`
- `ConsistencyCandidateCollector.java`、`ParserBackedReviewInputPreparer.java`
- `MinimalReviewEngine.java`
- 上述生产类对应 test、新增 `VersionedRatioScopeV20260729Test`
- test-only `Task034MvpE2EAcceptanceHarnessTest.java`
- `outputs/task-034-mvp-e2e-acceptance-v3/`
- 本规格、父 TASK/项目记忆的真实状态写回。

禁止修改：v15 assets、人工 XLSX/JSON、DOCX、structured input、expected JSON、
parser identity/version、CandidateResolver、EvidenceSlot、Finding/SYS enum、公共
API、数据库/migration、普通 binding、v1/v2 outputs。若需要新增 template/profile
routing、改 parser identity、改变 payment applicability、SYS/Finding、
EvidenceSlot/CandidateResolver 或上述禁止路径，立即 STOP 并先决定 ADR/新 TASK。

任一项不满足即 `NO_GO`，不得启动 Track B admission 或 Provider。

## 回滚边界

删除 v29 assets、release entry、loader branch、v29 classifier/probe 与对应测试；
恢复 formal harness 使用 v15 后，v29 execution 必须由 gate 返回 unknown/fail
closed；v15 三样本必须重现 R6B，v15 asset/v2 output hash、legacy 与普通 Demo
binding 必须保持。任何一项变化均表示回滚失败。

## Memory Writeback

随 `TASK-034` / `TASK-036` / `MILESTONE-MVP-002` 关键门禁统一写回。若 R7 失败，
必须记录真实失败，不得宣称 TASK-034 或 TASK-036 收口。

## 审计与 Git

实现由主 Codex 完成。里程碑冻结后由 CC AUDIT 与两个
`fork_turns="none"` 的全新 Codex subagents 对同一冻结 hash 从零只读审计。
未经单独授权不得 commit、push、PR 或 merge。

## 实现记录

* `v20260729.1` assets、loader/release/gate、inactive branch classifier 与五类
  point-local ratio probe 已在 `codex/task-mvp-002` worktree 实现；v15 asset 与
  v2 六个历史输出 hash 保持冻结。
* 既有 10 类回归在独立审计安全整改后为 `414/0/0/0`，D1 动态门禁为
  `30/0/0/0`，当时合并为 `444/0/0/0`。MVP-002 Core `81e47f…` 审计发现
  SourceAnchor provenance P1 后，新增 3 条仅针对 parser-issued structural span、
  重复值错误 cell 与不可映射 fail-closed 的防御性回归；当前统一门禁因此为
  `447/0/0/0`，不改变 D1 的 30 个业务 invocation，也不扩大 D1 业务实现范围。
  最终统一验证须保存每轮不可覆盖的原始
  JUnit XML、逐文件 SHA-256 与 console。
* 正式 R7 已通过真实双 property 门禁运行；`formal-test-result.xml` 为
  `1/0/0/0`，R7 为 27/27 candidate `MATCH`、27/27 `PointStatus=PASS`、
  57 `MATCHED` + 6 human `EXCLUDED`、70 production semantic-exclusion
  ledger entries、0 SYS、0 Finding、`overallVerdict=PASS`。
* R7 证明限定三样本和冻结 v29 契约通过，不等于 Production Ready，也不解锁
  `TASK-028 / TASK-031 / TASK-032`。当前增量尚待同一冻结 hash 的最终三审。
