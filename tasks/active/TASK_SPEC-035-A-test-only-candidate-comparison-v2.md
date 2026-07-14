# TASK SPEC — TASK_SPEC-035-A test-only candidate comparison v2
# 适用环境：Claude Code（DeepSeek 模型）— 统一执行环境
# 同一本地项目文件夹下与 CODEX 协作

> **版本**：v1.0
> **状态**：Frozen / Independent Spec Audit GO / Ready for Coding-Plan Submission / NO CODE UNTIL CODEX GO
> **创建日期**：2026-07-14
> **起草**：Codex
> **执行环境**：Claude Code（DeepSeek 模型）
> **TASK_SPEC 类型**：execution
> **父任务**：TASK-035
> **关联 ADR**：N/A；ADR-016 属于 TASK-036 生产链路，明确不在本规格范围
> **所在分支**：`codex/task-035-a-candidate-comparison-v2`

---

## 0. 任务摘要

**一句话任务**：仅在现有 TASK-034 test-only harness 内实现 `mvp-e2e-candidate-comparison-v2` typed projection 与输出字段，自测精确 grammar 和 expected 独立性；不修改生产 candidate、人工 ground truth、正式 v1 证据或多 occurrence anchor 链路。

### 0.1 角色与执行门禁

- 本规格只关联父任务 `TASK-035`；不得直接执行父任务或 `TASK-036`。
- Codex 冻结规格、审查编码前规格映射计划、实现报告和 `git diff`。
- Claude Code / DeepSeek 在 Codex 明确 `GO / IMPLEMENTATION AUTHORIZED` 前不得修改任何文件。
- Claude Code / DeepSeek 不得 commit、push、merge 或切换分支。
- 独立 agent 只做只读核查，不承担实现。

### 0.2 编码前规格映射计划（硬门禁）

修改代码前必须先输出并停止：

```text
验收断言映射：
- §8 每条 Must Pass 对应的真实输入、代码路径、测试方法和可证伪结果

关键字段 / 信号计算：
- reviewPointCode -> CandidateComparisonProfile 的固定映射
- expectedCandidateValueRaw / actualCandidateValueRaw 的真实来源
- expectedCandidateComparable / actualCandidateComparable 的独立 projection、失败条件和 canonical serialization
- candidateComparison 的 MATCH / MISMATCH / NOT_OBSERVABLE 计算条件

明确不修改路径：
- 生产 parser / CandidateResolver / EvidenceSlot / SourceAnchor / Review Engine / Result API
- human-anchor fixtures / expected JSON / DOCX / XLSX / matrix
- outputs/task-034-mvp-e2e-acceptance/**

范围外风险：
- 57 条 occurrence 多 anchor 缺口由 TASK-036 承接，本规格不处理
- v2 实现完成不等于正式 E2E PASS，正式重跑仍需 Codex 单独执行

预计测试变更：
- 唯一允许修改测试文件中的具体测试名、场景、断言及预期回归命令
```

Codex 未明确放行前，不得进入实现；事后测试通过不能替代该门禁。

### 0.3 文件访问范围

```text
✅ 允许修改：
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/Task034MvpE2EAcceptanceHarnessTest.java

👀 允许只读参考：
  AGENTS.md
  CURRENT_CONTEXT.md
  PRD.md
  docs/ARCHITECTURE.md
  docs/VERIFY.md
  tasks/active/TASK-035-mvp-e2e-candidate-comparison-contract-rebaseline.md
  tasks/active/TASK_SPEC-035-A-test-only-candidate-comparison-v2.md
  tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md
  tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md
  packages/test-fixtures/human-anchors/**
  packages/test-fixtures/expected/**
  outputs/task-034-mvp-e2e-acceptance/**
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/**

⛔ 禁止访问：
  .env
  .env.*
  secrets/
  credentials/
  config/production/
```

除唯一允许修改文件外，所有已读取路径均不得写入。

---

## 1. 上下文注入（DeepSeek 必读）

### 1.1 项目定位

CQCP 是合同质量控制中台 V2；生产链路坚持 parser-backed evidence、SYS/Finding 分流、后端确定性裁判和可追溯 SourceAnchor。本规格只修正 test-only 正式验收的比较表示，不改变生产审核语义。

### 1.2 本任务在链路中的位置

```text
人工 human-anchor raw expected（只读）
                  +
同 run PointEvidence actual candidate（只读）
                  ↓
【test-only candidate comparison v2】← 本规格
                  ↓
SampleAcceptanceResult JSON / overall verdict

生产 parser / resolver / review / snapshot（不修改）
occurrence anchor bridge（不修改，由 TASK-036 承接）
```

### 1.3 架构红线

| 标识 | 内容 | 触发行为 |
|---|---|---|
| RED-035-A-01 | expected projection 只读取 fixture raw 与 `reviewPointCode` 固定 profile，不读取 actual、PointStatus、anchor 或 verdict | STOP |
| RED-035-A-02 | actual 只来自现有同 run `ReviewEngineInput.pointEvidences` | STOP |
| RED-035-A-03 | 不修改生产 candidate shape、CandidateResolver、Review Engine、snapshot/API | STOP |
| RED-035-A-04 | 不修改 fixture/expected/DOCX/XLSX/matrix，不把 comparable 回写 ground truth | STOP |
| RED-035-A-05 | 不运行正式模式，不改写 TASK-034 v1 正式输出 | STOP |
| RED-035-A-06 | 不处理 occurrence coverage，不通过 candidateValue 搜索 anchor | STOP |

### 1.4 冻结比较契约

版本：`mvp-e2e-candidate-comparison-v2`。权威全文见父任务“冻结比较契约”，实现不得放宽。

固定 profile 映射：

| reviewPointCode | profile |
|---|---|
| PARTY_A_NAME_CONSISTENCY / PARTY_B_NAME_CONSISTENCY | TEXT_STRIP_EXACT_V1 |
| CONTRACT_TOTAL_AMOUNT_CONSISTENCY | CNY_DECIMAL_V1 |
| TAX_AMOUNT_FORMULA_CONSISTENCY | TAX_AMOUNT_COMPONENT_V1 |
| PREPAYMENT_RATIO_CONSISTENCY / PROGRESS_PAYMENT_RATIO_CONSISTENCY / COMPLETION_PAYMENT_RATIO_CONSISTENCY / SETTLEMENT_PAYMENT_RATIO_CONSISTENCY / WARRANTY_RETENTION_RATIO_CONSISTENCY | PERCENTAGE_POINT_V1 |

每个点输出字段固定为：

```text
expectedCandidateValueRaw
actualCandidateValueRaw
expectedCandidateComparable
actualCandidateComparable
candidateComparisonProfile
candidateComparison
```

- raw 必须逐字保留；不得被 strip、逗号删除或换行归一化后的值覆盖。
- comparable JSON 类型只能为 string 或 null。
- 文本 comparable 为 Java `String.strip()` 等价结果；空结果为 projection failure。
- 数值 comparable 为 `BigDecimal.stripTrailingZeros().toPlainString()` 等价 string；任何数值零统一为 `"0"`。
- 任一 raw 为 null/strip 后空值或任一侧 projection failure：`NOT_OBSERVABLE`。
- 双方 projection 成功：comparable 相等为 `MATCH`，否则 `MISMATCH`。

精确数值 grammar（ASCII 数字、完整匹配）：

```text
UNSIGNED_INTEGER := 0 | [1-9][0-9]*
GROUPED_INTEGER  := [1-9][0-9]{0,2}(,[0-9]{3})+
CNY_BODY         := -?(UNSIGNED_INTEGER | GROUPED_INTEGER)(.[0-9]{1,2})?
PERCENTAGE_BODY  := UNSIGNED_INTEGER(.[0-9]{1,2})?, numeric range 0..100
PERCENTAGE_RATE_BODY := UNSIGNED_INTEGER(.[0-9]{1,4})?, numeric range 0..100
```

`.` 为字面小数点。禁止 `+`、指数、前导/尾随小数点、非法前导零、全角数字、内部空白、宽松 locale；percentage 禁止符号和千分位。ratio expected 完整匹配 `PERCENTAGE_BODY%`，actual 完整匹配 `PERCENTAGE_BODY`，不得执行 `0.7 -> 70` 缩放。

tax expected 在整串 `strip()` 后必须完整匹配以下四行；允许全 LF 或全 CRLF，CRLF 只可为 projection 解析转 LF，raw 不改：

```text
1、taxRate=PERCENTAGE_RATE_BODY%；
2、totalAmount=CNY_BODY；
3、netAmount=CNY_BODY；
4、taxAmount=CNY_BODY。
```

大小写、序号、标点、赋值符、顺序均精确；内部空白行、额外行、混合/裸 CR、缺失/重复 label 或任一 component 失败均为 projection failure。只取第四行 `taxAmount` comparable，其他三行只验证复合结构。

### 1.5 术语

| 术语 | 定义 |
|---|---|
| raw | 未改写的 fixture expected 或同 run actual candidate 字符串 |
| projection | 按固定 profile 独立把单侧 raw 转为 comparable 的 test-only 计算 |
| comparable | 用于比较并写入输出的 canonical string 或 null |
| profile | 由 reviewPointCode 唯一选择的版本化比较规则 |
| 循环验证 | 从 actual、PointStatus 或 verdict 反向选择/修正 expected 或 profile |

---

## 2. 接口契约

以下为 test class 内部冻结接口；如现有 Java 约束要求调整签名，先 STOP，不得自行改名扩展：

```java
private static CandidateComparisonResult compareCandidate(
        ReviewPointCode reviewPointCode,
        String expectedCandidateValueRaw,
        String actualCandidateValueRaw);

private record CandidateComparisonResult(
        String expectedCandidateComparable,
        String actualCandidateComparable,
        CandidateComparisonProfile candidateComparisonProfile,
        CandidateComparison candidateComparison) {}

private enum CandidateComparisonProfile {
    TEXT_STRIP_EXACT_V1,
    CNY_DECIMAL_V1,
    TAX_AMOUNT_COMPONENT_V1,
    PERCENTAGE_POINT_V1
}
```

`PointAcceptanceResult` 必须把旧 `expectedCandidateValue / actualCandidateValue` 替换为以下顺序字段，其余字段语义保持不变：

```java
ReviewPointCode reviewPointCode,
String expectedCandidateValueRaw,
String actualCandidateValueRaw,
String expectedCandidateComparable,
String actualCandidateComparable,
CandidateComparisonProfile candidateComparisonProfile,
CandidateComparison candidateComparison,
PointStatus pointStatus,
List<String> evidenceSummary,
List<SourceAnchorSummary> actualAnchors,
List<PointDiagnostic> sysDiagnostics
```

错误处理：

| 情形 | 处理 |
|---|---|
| null / strip 后空值 / grammar failure | 对应 comparable=null；整体 NOT_OBSERVABLE |
| 双方成功但值不同 | MISMATCH |
| 未映射的 reviewPointCode | 抛出明确 `IllegalArgumentException`；不得 fallback 到文本比较 |
| tax composite 不完整 | expected comparable=null；NOT_OBSERVABLE |

---

## 3. 行为规格

### 3.1 Happy Path

1. `expectedCandidatesFromHumanFixture` 继续只读汇总唯一人工 raw expected。
2. `buildSampleResult` 继续从同 run `PointEvidence.candidateValue()` 取得 actual raw。
3. 按 `reviewPointCode` 唯一选择 profile；分别 project expected 和 actual。
4. 构造冻结的 v2 字段并保留 PointStatus、evidence、anchors、SYS diagnostics。
5. `samplePasses` 继续只看 `candidateComparison` 等现有门槛，不读取 comparable 之外的新捷径。

### 3.2 必测 Edge Cases

| profile | 场景 |
|---|---|
| TEXT | strip 后相等、大小写不同、内部空白不同、null、空白串 |
| CNY | 合法/非法千分位、负号、拒绝正号、前导零、`.70`、`70.`、指数、全角数字、内部空白、0/1/2/3 位小数、末尾零 canonicalization |
| PERCENTAGE | 0、100、100.01、0/1/2/3 位小数、expected 单一 `%`、actual 含 `%`、千分位/符号/指数、明确 `0.7` 不缩放为 `70` |
| TAX | 正常 LF/CRLF、首尾空白行、内部空白行、大小写、顺序、赋值符、终止符、缺失/重复 label、混合/裸 CR、额外行、taxRate 4/5 位小数、金额 2/3 位小数 |
| 独立性 | 固定 expected raw，改变 actual raw 时 expected comparable 不变 |
| 输出 | raw 逐字保留；comparable 为 string/null；profile 与 comparison 序列化稳定 |

至少新增/替换为以下三个精确测试方法，便于命令级发现性与执行数量核对：

```text
candidateComparisonV2CoversFrozenProfilesAndFailureSemantics
candidateComparisonV2ExpectedProjectionIsIndependentFromActual
candidateComparisonV2OutputPreservesRawAndSerializesComparable
```

### 3.3 禁止操作

```text
❌ 修改唯一允许文件之外的任何路径
❌ 修改正式 v1 outputs 或运行 formal property/input
❌ 修改人工 fixture / expected JSON 使测试通过
❌ 使用 actual、PointStatus、anchor、overall verdict 选择 expected profile/component
❌ 使用 double/float、宽松 NumberFormat、locale 或模糊 regex
❌ 把 parse failure 处理成 MISMATCH 或 MATCH；必须 NOT_OBSERVABLE
❌ 修改 occurrence comparison / anchor bridge 语义
❌ 新增依赖、生产 hook、公共 API 字段、数据库或 workflow
```

---

## 4. 架构空白处理

若单文件 test-only 实现做不到、需要公共 helper/生产类型/新依赖/新字段/fixture 变化，立即输出：

`[STOP: 架构空白 — <缺少内容> — 需要 Codex 决策]`

不得自行新增抽象层、production utility、API、状态枚举、配置、资源文件或依赖。

---

## 5. 技术栈约束

| 能力 | 指定工具 | 说明 |
|---|---|---|
| Java | Java 21 标准库 `String`、`BigDecimal`、`Pattern` | 禁止 double/float 与 locale parser |
| 测试 | JUnit 5、AssertJ | 使用既有依赖 |
| JSON | 现有 Jackson | 只验证 record 序列化，不新增库 |

数据库、网络、模型、Docker：均不涉及。

---

## 6. Git 工作区规则

```text
执行前：
  ✅ 当前分支必须为 codex/task-035-a-candidate-comparison-v2
  ✅ git status --short 必须为空
  ✅ 先提交编码前规格映射计划并等待 Codex GO

执行中：
  ❌ 不得 commit / push / merge / 切换分支
  ❌ 不得 reset --hard / clean -fd / checkout .

完成后：
  ✅ 输出 git status --short、git diff --name-status、git diff --stat、git diff --check
  ✅ 列出测试原始摘要、假设、歧义和 STOP 记录
```

---

## 7. 版本化要求

- 新比较契约标识固定为 `mvp-e2e-candidate-comparison-v2`；不得覆盖 v1 正式证据。
- 每个点输出固定 profile enum 与 raw/comparable 字段。
- 本规格不新增生产 RuleSetVersion、snapshot 或 API 版本。

---

## 8. 验收标准

### Must Pass

- [ ] 唯一 diff 文件是 §0.3 允许的 test-only harness。
- [ ] 9 个 reviewPointCode 全部且仅映射到 4 个冻结 profile；未知 code 不 fallback。
- [ ] 每个点输出 6 个冻结 comparison 字段，raw 逐字保留，comparable 仅 string/null。
- [ ] CNY、percentage、tax grammar 与父任务逐项一致，不使用二进制浮点或 locale 宽松解析。
- [ ] null、空值或任一 projection failure 均为 `NOT_OBSERVABLE`。
- [ ] expected projection 不读取 actual；直接测试证明改变 actual 不改变 expected comparable。
- [ ] tax profile 只从完整人工四行 expected 取唯一 `taxAmount`，不从 actual 选择 component。
- [ ] ratio `70%` / `70` 可得到相同 comparable，`0.7` 不隐式缩放为 `70`。
- [ ] 不预先修改 TASK-034 v1 9/18 结果，不运行正式 E2E。
- [ ] harness 定向测试命令实际发现并执行至少 15 个测试（包含上述三个精确方法），0 failures / 0 errors / 0 skipped；既有四类回归命令实际执行 27 个测试且全部通过。不得只凭 Gradle 零退出判断。

### 同根因分批修复一致性

- [ ] 与 `TASK_SPEC-034-A` 一致：同 run actual、人工 expected 独立、test-only、formal 双门禁、fixture/expected 只读。
- [ ] 差异：034-A 的 strip-only v1 比较保留为历史证据；本规格只在后续 harness 代码中实现 v2 typed projection，原因来自 TASK-035 冻结契约。
- [ ] Codex 必须在实现审查中确认该差异符合 TASK-035，而非掩盖生产错误。

### 评测 / fixture / expected 来源

- [ ] 涉及评测 expected，但不修改 fixture/expected JSON。
- [ ] expected raw 来源为 ZK 人工确认并已接受的 human-anchor fixture，独立于 parser/actual。
- [ ] profile provenance 来自 PRD 与既存架构；profile 选择只读 reviewPointCode。
- [ ] actual 来源为同 run `PointEvidence`；不得倒填 expected。
- [ ] 当前正式 sample output 只用于失败事实与 grammar 兼容性核查，不作为 expected 正确答案来源。

### Must Not

- [ ] ❌ 允许文件外存在 diff。
- [ ] ❌ 修改 production、fixture、expected、DOCX、XLSX、matrix、outputs、API、数据库、workflow、ADR 或 ARCHITECTURE。
- [ ] ❌ 新增依赖或执行网络/模型/正式 E2E。
- [ ] ❌ actual-derived expected、profile fallback、宽松 parsing、比例缩放或 MISMATCH 掩盖 projection failure。
- [ ] ❌ commit、push、merge、切分支或破坏性 Git 命令。

---

## 9. 测试与验证命令

获得 Codex 实现放行后必须运行，且不得设置正式模式 property/input：

```powershell
Set-Location apps/api-server
gradle test --rerun-tasks --tests "com.cqcp.apiserver.reviewengine.Task034MvpE2eAcceptanceHarnessTest"
# 立即读取 build/test-results/test/TEST-*.xml 并汇总 tests/failures/errors/skipped；tests 必须 >=15，其他三项必须为 0。
gradle test --rerun-tasks --tests "com.cqcp.apiserver.reviewengine.HumanAnchorGroundTruthFixtureTest" --tests "com.cqcp.apiserver.reviewengine.ParserBackedEvidenceOverlapBaselineTest" --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest" --tests "com.cqcp.apiserver.reviewengine.TaskResultQueryControllerTest"
# 再次读取 TEST-*.xml 并汇总；tests 必须 =27，failures/errors/skipped 必须为 0。
Set-Location ../..
git diff --check
git status --short
git diff --name-status
git diff --stat
```

禁止：正式模式、001/002/003 Phase 1、数据库 migration、Docker Compose、外部 API/模型、依赖安装、网络命令。

---

## DeepSeek 执行指令块

```text
你正在 Claude Code 中执行 TASK_SPEC-035-A，推理模型为 DeepSeek。

第一步只读：完整读取 AGENTS.md、CURRENT_CONTEXT.md、PRD.md、docs/ARCHITECTURE.md、
tasks/active/TASK-035-mvp-e2e-candidate-comparison-contract-rebaseline.md、
tasks/active/TASK_SPEC-035-A-test-only-candidate-comparison-v2.md、
tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md、
tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md。

第二步执行 git branch --show-current 和 git status --short。
分支不是 codex/task-035-a-candidate-comparison-v2 或工作区不干净时立即 STOP。

第三步只输出 §0.2 编码前规格映射计划并停止。Codex 未明确回复
GO / IMPLEMENTATION AUTHORIZED 前不得修改任何文件、运行测试或进入实现。

获得 GO 后，只可修改 §0.3 唯一 test-only 文件。任何生产、fixture、expected、outputs、
正式 E2E、occurrence anchor、依赖或契约越界需求都必须 STOP。

完成后输出分支、git status --short、git diff --name-status、git diff --stat、git diff --check、
测试原始摘要、假设、歧义、STOP 记录和对 §8 的逐条自检。不得 commit、push、merge。
```

---

## 10. 实现报告

待 Claude Code / DeepSeek 获 Codex 放行并完成实现后填写；当前仅允许提交编码前规格映射计划。

## 11. CODEX 审查记录

当前结论：`PENDING_PRE_CODING_PLAN_REVIEW`。

Codex 将先审查编码前规格映射计划；只有明确 `GO / IMPLEMENTATION AUTHORIZED` 后才允许编码。实现完成后再按 A/B/C 审查代码、测试和 diff。

规格冻结审计记录：

- 首轮独立只读审计：`NO_GO`。阻断项为错误大小写的 harness class filter 导致 `No tests found`，以及正式重跑门禁未逐字要求 TASK-035/TASK-036 双路线完成。
- Codex Review Intake：`ACCEPT_FINDINGS`。已在当前环境验证实际类名 `Task034MvpE2eAcceptanceHarnessTest` 配合 `--rerun-tasks` 可执行当前 13 个 harness tests，0 failures/errors/skipped；规格已改为实现后至少 15 个并读取 XML 计数。
- 命令验证期间未带 filter 的模块全量测试执行 103 tests / 1 failure；失败为 `CqcpApiServerApplicationTests.contextLoads` 连接 PostgreSQL hostname 时 `UnknownHostException`，属于环境依赖，不作为本 test-only 规格的定向证明，也未据此放宽验收。
- 正式重跑门禁已修订为 `TASK-035 接纳 + TASK-036/ADR-016 全部自身门禁完成 + Codex 单独正式重跑授权`。
- 第二轮独立只读复审：`GO`，两项 blocking finding 均关闭，无新增问题。
- Codex Review Intake：`SPEC_FROZEN / READY_FOR_CODING_PLAN_SUBMISSION / NO_CODE_AUTHORIZATION`。只放行编码前规格映射计划，不等于实现授权。

## 12. 后续任务联动

| 后续动作 | 门禁 |
|---|---|
| TASK_SPEC-035-A 实现 | 编码前规格映射计划经 Codex GO |
| TASK-035 实现接纳 | 代码、测试、实现报告、git diff 经 Codex Review Intake 与独立只读复核 |
| TASK-034 正式重跑 | `TASK-035` 接纳 + `TASK-036 / ADR-016` 全部自身门禁完成 + Codex 单独正式重跑授权；缺一不可 |

## 附录：引用文档

| 文档 | 关键内容 |
|---|---|
| AGENTS.md | 角色分离、expected 独立性、Git 门禁 |
| PRD.md | CNY、百分比、税额公式口径 |
| docs/ARCHITECTURE.md | CandidateRole.TAX_AMOUNT、后端确定性裁判边界 |
| TASK-035 | comparison v2 权威冻结契约 |
| TASK_SPEC-034-A | 现有 test-only harness 与同 run 来源 |
