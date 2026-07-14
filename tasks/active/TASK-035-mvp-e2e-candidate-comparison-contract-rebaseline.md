# TASK-035：MVP E2E candidate comparison 契约重基线

状态：Active / Contract Frozen / TASK_SPEC-035-A Implemented / Codex ACCEPT / Independent Implementation Audit GO / Commit 52d73b3 / Formal E2E Not Run

类型：A 类 Evaluation / Governance 父任务

优先级：P0

负责人：Codex

创建日期：2026-07-14

来源：`TASK-034` Phase 1 正式 MVP E2E `FAIL`

## 背景

`TASK-034` 已对 `CQCP-MVP-DOCX-001/002/003` 执行正式 MVP E2E。27 个审核点中，`PointStatus` 均为 `PASS`，candidate comparison 为 9 `MATCH` / 18 `MISMATCH`。

只读核查确认，18 个差异全部属于 raw 表示形态不同：

* 15 个比例点：人工 expected 使用 `70%` 等展示值，生产 actual 使用 `70` 等百分比数值。
* 3 个税额公式点：人工 expected 是 `taxRate / totalAmount / netAmount / taxAmount` 复合说明，生产 actual candidate 是 `taxAmount` 标量。

`PRD.md` 已冻结税率和付款比例使用百分比数值传输，`13` 表示 `13%`；税额公式由后端使用结构化 total / excluded / tax / rate 做确定性裁判。现有 actual 与该生产口径一致，因此不得把 18 个差异直接定性为 `CandidateResolver` 缺陷，也不得修改人工 fixture 消除差异。

本任务只重基线 test-only 正式验收的 candidate comparison 契约，保留 raw expected / actual，增加独立、可追溯、版本化的比较投影。

## 目标

* 冻结 `mvp-e2e-candidate-comparison-v2` 比较契约。
* 区分 raw human expected、raw production actual 与用于比较的 typed projection。
* 保证 expected projection 只依赖人工 fixture raw 与已冻结 comparison profile；profile provenance 只来自 PRD 与既存架构，不依赖本次 actual 输出。
* 为后续局部 test-only `TASK_SPEC` 提供可证伪断言，不修改生产 candidate 语义。

## 非目标

* 不修改生产 parser、`CandidateResolver`、`EvidenceSlot`、`SourceAnchor`、Review Engine 或结果 API。
* 不修改 DOCX、XLSX、matrix、human-anchor fixture 或 expected JSON。
* 不处理 57 条 occurrence 的多 anchor 缺口；该问题由 `TASK-036` 承接。
* 不把 `PointStatus=PASS` 等同于 candidate / anchor 验收通过。
* 不重新运行正式 MVP E2E。

## 输入

* 正式证据：`outputs/task-034-mvp-e2e-acceptance/sample-results/*.json`。
* 人工来源：`packages/test-fixtures/human-anchors/CQCP-MVP-DOCX-00{1,2,3}.json`。
* 生产口径：`PRD.md` 第 8.2 节数值口径与税额公式规则。
* 当前 harness：`apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/Task034MvpE2EAcceptanceHarnessTest.java`。
* 上游任务：`TASK-034`、`TASK-DATA-001`、`TASK-033`。

`TAX_AMOUNT_COMPONENT_V1` 的先验来源不是本次 actual 输出：`docs/ARCHITECTURE.md` 已定义 `CandidateRole.TAX_AMOUNT`，`ParserBackedReviewInputPreparer` 为该审核点构建 `tax_amount` evidence，`MinimalReviewEngine` 的 required slot 也是 `tax_amount`。因此该 profile 固定选择人工 expected 的 `taxAmount` component，不允许根据 actual 内容动态选择 component。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
* `tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md`
* `PRD.md` 第 8.2、8.6、8.9 节
* `docs/ARCHITECTURE.md` 的 CandidateResolver、EvidenceSlot 与结果快照边界
* `outputs/task-034-mvp-e2e-acceptance/`

### Optional Context

* `tasks/done/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md`
* `tasks/done/TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md`
* `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
* `docs/VERIFY.md`

### Out of Scope

* 生产候选抽取、角色归属、置信度、slot coverage 或业务裁判。
* 多出处一致性、occurrence scope 和多 anchor 结果语义。
* 外部 API、数据库、workflow、前端、模型或依赖变更。

## 冻结比较契约

版本：`mvp-e2e-candidate-comparison-v2`。

每个审核点必须同时保留：

```text
expectedCandidateValueRaw
actualCandidateValueRaw
expectedCandidateComparable
actualCandidateComparable
candidateComparisonProfile
candidateComparison
```

raw 字段必须逐字保留来源，不得被 projection 覆盖。comparable 字段只能由下表规则计算：

`expectedCandidateComparable` / `actualCandidateComparable` 的 JSON 类型固定为 string 或 null：文本 profile 输出 stripped string；数值 profile 输出 `BigDecimal.stripTrailingZeros().toPlainString()` 等价 canonical decimal string，数值零统一为 `"0"`，禁止 JSON number、科学计数法或 locale 格式。projection 失败时对应 comparable 为 null。

| 审核点 | profile | expected projection | actual projection |
|---|---|---|---|
| `PARTY_A_NAME_CONSISTENCY` / `PARTY_B_NAME_CONSISTENCY` | `TEXT_STRIP_EXACT_V1` | Java `String.strip()` 等价首尾空白移除 | 同左 |
| `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` | `CNY_DECIMAL_V1` | 按下述 `CNY_BODY` 词法解析；合法千分位可去除；不四舍五入 | 同左 |
| `TAX_AMOUNT_FORMULA_CONSISTENCY` | `TAX_AMOUNT_COMPONENT_V1` | 人工复合 expected 必须完整匹配下述四行 grammar，再取唯一 `taxAmount` 并按 `CNY_BODY` 解析 | actual raw 作为 `taxAmount` 标量按 `CNY_BODY` 解析 |
| 五个 `*_RATIO_CONSISTENCY` | `PERCENTAGE_POINT_V1` | 完整匹配 `PERCENTAGE_BODY%`；`70% -> 70` | 完整匹配 `PERCENTAGE_BODY`；不得把 `0.7` 自动放大为 `70` |

### 精确词法

除 `TEXT_STRIP_EXACT_V1` 外，数值 profile 只允许以下预处理：对整个 raw 字符串执行一次 Java `String.strip()` 等价的首尾 Unicode 空白移除。raw 字段本身不改写。不得删除内部空白、货币符号、全角数字或其他字符；不得做 Unicode 数字归一化、locale 解析、指数解析或模糊搜索。

下列 grammar 中 `[0-9]` 仅表示 ASCII 数字：

```text
UNSIGNED_INTEGER := 0 | [1-9][0-9]*
GROUPED_INTEGER  := [1-9][0-9]{0,2}(,[0-9]{3})+
CNY_BODY         := -?(UNSIGNED_INTEGER | GROUPED_INTEGER)(.[0-9]{1,2})?
PERCENTAGE_BODY  := UNSIGNED_INTEGER(.[0-9]{1,2})?
```

实现时 `.` 必须按字面小数点解释。完整字符串必须与对应 grammar 全匹配；因此明确拒绝 `+70`、`.70`、`70.`、`7E1`、`01`、`1,00`、`1 000`、内部空白和金额超过 2 位小数。`CNY_BODY` 允许负号但不允许正号；是否为业务合法金额不由 test-only comparison 另行裁判。

`PERCENTAGE_BODY` 还必须在十进制数值上满足 `0 <= value <= 100`，最多 2 位小数，不允许正负号、千分位、指数、内部空白或末尾 `%`。只有 expected 侧在 body 后恰好带一个 ASCII `%`；actual 侧带 `%` 必须 projection 失败。该 profile 不接受 `0.7 -> 70` 的缩放。

`TAX_AMOUNT_COMPONENT_V1` 的 expected grammar 固定为以下四行，label 大小写敏感，序号、中文顿号、`=`、中文分号/句号和顺序均为字面量，不允许 label 两侧或赋值符两侧有空白：

```text
1、taxRate=PERCENTAGE_RATE_BODY%；
2、totalAmount=CNY_BODY；
3、netAmount=CNY_BODY；
4、taxAmount=CNY_BODY。
```

其中 `PERCENTAGE_RATE_BODY := UNSIGNED_INTEGER(.[0-9]{1,4})?`，并须满足 `0 <= value <= 100`；其 `%` 为 ASCII 百分号。整串 `strip()` 先执行，因此 raw 首尾纯空白或纯空白行可被移除并接受；strip 后的内部空白行或第五个非空行都属于额外行并失败。行分隔只允许全部使用 LF，或全部使用 CRLF；projection 可为解析目的把 CRLF 转为 LF，但不得改变 raw。必须完整匹配四行且恰好出现一次精确 `taxAmount` label；缺行、重复 label、大小写变化、其他赋值符、其他终止符、混合/裸 CR、额外行或任一 component 词法失败，expected projection 均失败。比较值只取第四行 `taxAmount`；其他三项只用于证明复合 expected 结构完整，不与 actual 比较。

共同规则：

1. 十进制比较使用数值相等，不比较无意义末尾零；不得使用二进制浮点数。
2. 千分位只按 `GROUPED_INTEGER` 合法分组时去除；任意逗号、任意文字删除或模糊包含均禁止。
3. `taxAmount` 只按上述完整、大小写敏感的四行 grammar 提取；不得从 actual 反推 label 或 component。
4. raw 任一侧为 null、空值或 projection 失败时，`candidateComparison=NOT_OBSERVABLE`。
5. 双方 projection 成功且数值/文本相等时为 `MATCH`，否则为 `MISMATCH`。
6. `PointStatus`、公式强弱校验结果和 occurrence coverage 不参与 candidate comparison 计算。

## 范围

### 包含

* 冻结上述 profile、字段和失败语义。
* 冻结 expected 独立性与循环验证禁止规则。
* 只允许后续 test-only harness / test resource 中增加 comparison v2 实现与自测。

### 不包含

* 修改现有人工 raw 值或把 comparable 值回写 fixture。
* 修改生产 `candidateValue` 格式以迎合验收。
* 把 tax formula 复合 expected 整体与 taxAmount 标量做字符串比较。
* 通过 locale、货币符号、中文大写金额或宽松正则扩展本任务。

## 约束

* 任何后续实现必须使用 Claude Code / DeepSeek 局部 `TASK_SPEC`，并先提交编码前规格映射计划。
* 本任务涉及评测 expected 解释，进入实现前必须追加独立 agent 只读复核。
* 如实现需要修改生产 candidate shape、`CandidateResolver` 或 Review Engine，立即停止并触发 ADR 评审。
* 不得把当前 18 个差异预先改写为 18 个 `MATCH`；只有 v2 实现和重新运行后才能记录实际结果。

## 交付物

* 本任务冻结的 `mvp-e2e-candidate-comparison-v2` 契约。
* 独立只读复核报告与 Codex Review Intake Decision。
* 后续经授权才可创建的 `TASK_SPEC-035-A` test-only 实现规格。

## 验收标准

1. 9 类审核点全部映射到唯一 comparison profile。
2. 当前 18 个差异都能由 raw 表示差异解释，不宣称生产 actual 错误。
3. raw expected / actual 不被修改或丢失。
4. expected projection 不读取 actual，不存在循环验证。
5. ratio 不接受 `0.7 -> 70` 的隐式缩放。
6. tax formula 只提取人工 expected 中唯一的 `taxAmount`，不从 actual 或 PointStatus 倒填。
7. projection 失败使用 `NOT_OBSERVABLE`，不伪造 `MATCH`。
8. 不修改生产代码、fixture、expected JSON、DOCX、XLSX、matrix、API、数据库、workflow 或 ADR。

## 测试与验证

本轮只执行文档与正式输出只读核对：

* 核对 3 份 sample JSON 的 27 个 candidate comparison。
* 核对 `PRD.md` 百分比数值与税额公式口径。
* `git diff --check`。
* `git status --short`。

后续实现测试必须覆盖：文本、合法/非法千分位、正负号、前导零、前导/尾随小数点、指数、ASCII/全角数字、内部空白、金额 0/1/2/3 位小数、末尾零、ratio 边界与 0/1/2/3 位小数、expected/actual `%` 差异、禁止比例缩放、四行 tax grammar 的大小写/赋值符/终止符/顺序/换行、taxAmount 唯一/缺失/重复、null 与 parse failure。另须直接断言：改变 actual raw 不会改变 expected comparable，证明 expected projection 不读取 actual。

## 文档更新要求

* `CURRENT_CONTEXT.md`：是。
* `tasks/MVP_TASK_MAP.md`：是。
* `changelog/2026-07.md`：是。
* `docs/*.md`：否。
* ADR：否；本任务只改变 test-only 验收比较，不改变生产职责。如触及生产则停止并另行 ADR。

## Next Task Handoff

`TASK_SPEC-035-A` 已按角色门禁完成编码前规格映射计划、Codex 实现放行、单文件实现、定向测试、Codex Review Intake、独立只读实现审计和精确提交。正式 MVP E2E 仍须等待 `TASK-036 / ADR-016` 全部自身实现门禁完成，并由 Codex 获得单独正式重跑授权；当前不得直接进入生产实现或正式重跑。

## 独立审计与 Codex Review Intake

* 2026-07-14 首轮独立审计：`NO_GO`。阻断项为数值 projection 词法未唯一冻结、`taxAmount` 提取 grammar 不确定。
* Codex Review Intake：`ACCEPT_FINDINGS`。本次已补齐唯一可证伪 grammar、精度/范围/字符集/空白/符号规则、expected/actual `%` 差异、完整四行 tax grammar、先验 provenance 和 expected 独立性测试要求。
* 2026-07-14 第二轮独立复审：`GO_WITH_FINDINGS`，无 blocking finding；三份 fixture 的 27 个唯一 expected 与三份正式结果的 27 个 actual 均通过对应 grammar，仅证明契约可执行，不提前改写 v1 结果。
* 三项非阻断 finding 已收口：统一 profile provenance，明确整串 strip 后首尾空白行/内部额外行语义，冻结 comparable 为 canonical decimal JSON string 或 null。
* 2026-07-14 最终 delta 只读核对：`FINAL_GO`，上述增量未引入 blocking finding。
* Codex Review Intake：`ACCEPT_REVIEW / CONTRACT_FROZEN`。
* 2026-07-14 用户明确授权创建并派发 `TASK_SPEC-035-A`；该授权首先只放行编码前规格映射计划。
* `TASK_SPEC-035-A` 首轮独立审计 `NO_GO` 的测试发现命令与正式重跑门禁已整改，第二轮复审 `GO`；Codex Review Intake 为 `SPEC_FROZEN / READY_FOR_CODING_PLAN_SUBMISSION / NO_CODE_AUTHORIZATION`。
* Claude Code / DeepSeek 编码前规格映射计划经 Codex 审查后获得 `GO / IMPLEMENTATION AUTHORIZED`；执行者仅修改冻结的 test-only harness，未运行正式模式，未 commit、push 或 merge。
* Codex 实质审查确认 raw 来源、四类 profile、精确 grammar、expected/actual 独立 projection、string/null 序列化和 `samplePasses` 门槛符合冻结契约；定向 XML 计数为 harness `15/15`、既有四类回归 `27/27`，均为 0 failures / 0 errors / 0 skipped。
* 2026-07-14 独立只读实现审计：`GO`，无 blocking 或 non-blocking finding；Codex Review Intake：`ACCEPT_IMPLEMENTATION / READY_FOR_PRECISE_COMMIT`。
* 用户授权后，Codex 仅提交唯一 harness 文件；实现提交为 `52d73b3`（`test: implement task 035 candidate comparison v2`）。未 push、未 merge、未改写 `TASK-034` v1 正式输出。

## 风险

* typed projection 如果过宽，会把真实格式错误吞成 `MATCH`。
* tax formula composite 与 taxAmount scalar 的关系如果没有固定 label，会产生不可复核的隐式选择。
* 只修正 candidate comparison 仍不能解决 57 条 occurrence 的多 anchor 缺口。

## 待确认

* 待后续正式重跑验证：按 v2 重新计算后 candidate comparison 的实际统计。
* 待人工另行授权：`TASK-036` 生产实现及其自身门禁；ADR-016 接受与 ARCHITECTURE 同步不构成实现授权。

## 完成记录

* 实现完成日期：2026-07-14；父任务保持 active，尚未执行归档流程。
* 实现文件：`apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/Task034MvpE2EAcceptanceHarnessTest.java`；提交 `52d73b3`。
* 测试结果：harness `15/15`、既有四类回归 `27/27`，均 0 failures / 0 errors / 0 skipped；`git diff --check` 通过。
* 审查结果：Codex `ACCEPT_IMPLEMENTATION`；独立只读实现审计 `GO`，无 findings。
* 遗留问题：正式 MVP E2E 未重跑；57 条 occurrence 的多 anchor 缺口仍由 `TASK-036` 承接且未获生产实现授权。
* 备注：本任务未修改生产链路、人工 fixture / expected 或 `TASK-034` 已留存的 v1 正式失败证据。
