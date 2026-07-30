# TASK_SPEC-034-B：人工 occurrence 到 parser SourceAnchor 的单调双射桥接 v2

状态：v2 Implemented / Formal R6B FAIL / v3 ORACLE SPEC FROZEN

父任务：`TASK-034`

Task Level：L3 高风险评测正确性（test-only）

负责人：Codex

## Required Context

- `AGENTS.md`
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md`
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`
- `decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md`
- `packages/test-fixtures/README.md`

## Optional Context

- `tasks/done/TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md`
- `outputs/task-034-mvp-e2e-acceptance-v2/`

## Out of Scope

- 修改人工 XLSX、human-anchor fixture、expected JSON 或 DOCX。
- 修改生产 parser、CandidateResolver、EvidenceSlot、SourceAnchor、Review Engine、API 或数据库。
- 使用 human candidateValue、evidence text 模糊包含、同 row/table/block 宽松匹配
  **反向定位或构造** SourceAnchor。唯一单调配对完成后的独立人工 oracle 校验不属于
  反向定位。
- 为普通 UI 或生产接口增加 canonical bridge 字段。

## 背景与输入事实

桥接实现后的临时 R5 曾得到 27/27 candidate `MATCH`、27/27 PointStatus `PASS`
和 57 个 SourceAnchor；随后复核发现 R5 在 C1 full-scan probe 中读取
`paymentMethod` 过滤 payment branch，违反已冻结的 C1
payment-method-neutral 契约，因此 R5 证据和结论已经失效，不得作为门禁证据。

恢复 C1 冻结语义后的正式 R6 为：

- 18/27 candidate comparison 为 `MATCH`，9/27 为 `NOT_OBSERVABLE`；
- 18/27 PointStatus 为 `PASS`，9/27 为 `NOT_CONCLUDED`；
- 三份样本共输出 49 个可靠 SourceAnchor，无法与 57 条纳入人工 occurrence
  形成同基数桥接；
- 9 个未结论点均为 payment ratio role conflict，固定输出
  `SYS_ROLE_CONFLICT`，未生成业务 Finding；
- 首轮 R6 暴露 comparator 的全局 shape 耦合：一个 group 失败会污染所有 group。
  修复为逐 group fail-closed 后的 R6B 为 47 `MATCHED` +
  10 `NOT_OBSERVABLE` + 6 `EXCLUDED`，总判定仍为 `FAIL`。

旧 test-only comparator 仍要求人工自然语言 `humanLocationDescription/tableContext/rowContext/cellContext`
与 parser canonical reference 或 parser 原文完全相等。人工 fixture 按独立性规则明确禁止保存 parser
canonical key，因此该条件结构性地无法成立，57 条均被判为 `NOT_OBSERVABLE`。

## 冻结桥接契约

R6B 版本：`mvp-e2e-occurrence-bridge-v2-monotonic-bijection`。

1. 只对 `includedInConsistencyEvaluation=true` 的 occurrence 建桥；排除项保持 `EXCLUDED`。
2. 分组键固定为 `reviewPointCode + human/actual exact granularity`。
3. actual anchor 必须先由同一次 run 的 parsed document 精确解析：
   - BLOCK：稳定且唯一的 blockId；`previewElementRef` 可为空。
   - TABLE_CELL：稳定且唯一的 blockId + parser-issued `table/row/cell` ref。
4. 每组 human 与 actual 数量必须完全相等；actual identity/reference 必须唯一。
5. 每组 actual 按 parser document block 顺序、同 block cell 顺序排序。
6. 每组 human 保持已冻结 XLSX → fixture 数组顺序；组内超过 1 条时，人工页码必须可解析且单调不降。
7. 只有两侧形成唯一、同粒度、同基数、单调的一一对应时，第 n 条 human occurrence 才映射到第 n 条 actual anchor。
8. 桥接不得读取 human `expectedCandidateValue` 或 production `candidateValue`，不得搜索 evidence text，也不得生成、修改或倒填任何 SourceAnchor。
9. 任一数量、粒度、顺序、页码、block identity 或 cell ref 不满足时，该组全部 `NOT_OBSERVABLE`。
10. candidate 正确性仍由独立的 `mvp-e2e-candidate-comparison-v2` 判断；bridge 只判断 57 个既有 anchors 是否完整覆盖 57 个独立人工 occurrence。

### R7 v3 oracle 增量

R7 版本升级为
`mvp-e2e-occurrence-bridge-v3-monotonic-plus-human-oracle`。v2 的分组、解析、
identity、基数和 document-order 配对全部保留；新增校验只能发生在唯一配对之后，
不得使用人工文本或 expected value 搜索、筛选、重排 actual。

post-pair oracle 固定为：

1. ratio：去除人工 `1、/2、` 编号与 selector-only 行后，人工/actual 规范化文本
   必须至少一向 containment；人工 expected percentage 必须等于 actual
   point-local payment-base capture。
2. PARTY：actual 必须包含人工 expected party value；BLOCK 还须含对应角色标签，
   TABLE_CELL 必须与人工 value 规范化相等。
3. CONTRACT_TOTAL：TABLE_CELL 与人工 expected decimal 相等；BLOCK 同时含总价
   role label 与该 decimal。
4. TAX：actual BLOCK 必须同时包含人工 expected 中的 total/net/tax 三个 decimal
   （忽略千分位）及税款 role label；taxRate 可由另一人工位置证明，不要求位于
   actual anchor。
5. oracle 失败为 `NOT_MATCHED`；不得降格成 `MATCHED`，也不得换配另一 actual。

必须有 adversarial 自测：把正确 ratio anchor 替换为同 point、同 granularity、
同基数、同 candidate value 的 node 或实物支付污染 anchor，v2 形状条件仍成立但
v3 必须 `NOT_MATCHED` 并令 formal verdict `FAIL`。同时保留少/多 anchor、错 cell、
重复 identity、人工页码逆序和单组失败不污染其他组的 fail-closed 自测。

## 可证伪验收断言

- 目标门禁：三份冻结样本严格得到 57 `MATCHED` + 6 `EXCLUDED`，且 27/27
  candidate `MATCH`、27/27 PointStatus `PASS`。R6 未满足该断言。
- 打乱 actual 输入顺序不改变按 parser 文档顺序形成的对应。
- 任一组少一个/多一个 anchor 时，该组不得部分匹配。
- 任一 TABLE_CELL 缺 cell ref、ref 指向错误 cell 或重复 identity 时 fail closed。
- 多 occurrence 组缺失或逆序人工页码时 fail closed。
- 同点、同粒度、同基数、同值的错误文本替换必须 `NOT_MATCHED`，不得仅凭 ordinal
  通过。
- 旧 strict-context comparator 自测仍保留，不被 v2 fallback 静默放宽。

## 回滚边界

仅回滚 test-only harness 中 bridge v2 枚举、算法、自测和正式入口选择；生产实现与 57 个 SourceAnchor 不受影响。

## Memory Writeback

随 `TASK-034` / `TASK-036` / `MILESTONE-MVP-002` 收口统一写回；本 TASK_SPEC 中间状态不单独改写长期项目记忆。

## 实现与验证记录

- test-only `mvp-e2e-occurrence-bridge-v2-monotonic-bijection` 已实现，并覆盖
  parser 顺序稳定、同基数单调双射、数量/页码/identity/cell ref fail-closed
  和旧 strict comparator 保留。
- 非 formal harness：17/17 通过。
- C1/C2 相关 10 个测试类联合复跑：413/413 通过。
- 正式 R6B 输出：
  `outputs/task-034-mvp-e2e-acceptance-v2/`；`overallVerdict=FAIL`。
- 当前失败不是 bridge 放宽即可解决：production result 只有 49 个可靠 anchor，
  且 9 个 payment ratio 点没有可接受 candidate/PointStatus；002 PREPAYMENT
  另有一个未选 payment branch 的多余 anchor。不得使用 ordinal、evidence text
  或 candidate value 反搜把 10 个 group mismatch 伪造为命中。
- 独立 D1 测试审计发现 v2 对“同点/同粒度/同基数但错误 anchor 替换”可假阳性；
  因此 v2 结论不得用于 R7 解锁。R7 必须实现并通过上节 v3 post-pair oracle。
