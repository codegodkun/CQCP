# TASK SPEC — TASK_SPEC-DEBT-001-B collectPatternCandidates valueFormatSignal 修复
# 适用环境：Claude Code（DeepSeek 模型）— 统一执行环境
# 同一本地项目文件夹下与 CODEX 协作

> **版本**：v0.1
> **状态**：Merged via PR #4（post-merge 记录完成；父任务未归档，未进入下一任务）
> **创建日期**：2026-06-26
> **起草**：CODEX
> **执行环境**：Claude Code（DeepSeek 模型）
> **TASK_SPEC 类型**：execution
> **父任务**：TASK-DEBT-001
> **关联 ADR**：ADR-014, ADR-015
> **所在分支**：master

---

## 0. 任务摘要

**执行模式**：Claude Code 软件壳 + DeepSeek API 推理，运行于本地项目文件夹。

**一句话任务**：修复 `ParserBackedReviewInputPreparer.collectPatternCandidates()` 在构造 pattern 候选时将 `valueFormatSignal` 硬编码为 `true` 的问题，使该信号来自匹配候选值的真实格式判断；不修改 resolver 五档语义、ratio early return、parser provenance、TABLE_CELL、fixture / expected JSON / DOCX、OpenAPI、数据库或 Docker。

### 0.1 角色与执行门禁

- 本 `TASK_SPEC` 关联父任务 `TASK-DEBT-001`；Claude Code / DeepSeek 不得直接执行父任务。
- Codex 负责冻结规格、审查编码前规格映射计划、审查实现报告和 `git diff`；Codex 不直接编写本任务业务代码。
- 前置门禁：只有在 `TASK_SPEC-DEBT-001-A` post-push 独立只读核查结论为 `GO` 后，B 批次才允许进入“编码前规格映射计划”阶段。
- 当前前置门禁状态：2026-06-27 独立只读 post-push 复审已给出 `GO`；本规格已冻结到编码前规格映射计划阶段，但尚未授权实现。
- 如果 `TASK_SPEC-DEBT-001-A` post-push 独立只读核查尚未 `GO`，本 B 批次只能保持 `Draft` / `Ready for Review`，不得派发 Claude Code / DeepSeek 实现，也不得要求其输出编码前规格映射计划。
- Claude Code / DeepSeek 只有在 Codex 明确放行“编码前规格映射计划”后，才允许修改 §0.3 中允许修改的文件。
- Claude Code / DeepSeek 不得 commit，不得 push。
- 独立 agent 只做只读事实核查，不得实现或修复代码。
- 不得用 `CURRENT_CONTEXT.md` 自述替代真实代码、测试、原始 console 输出和 commit 证据。

### 0.2 编码前规格映射计划（实现前硬门禁）

Claude Code / DeepSeek 在修改任何文件前，必须先：

1. 读取本 `TASK_SPEC`。
2. 读取父任务 `TASK-DEBT-001`。
3. 读取 `TASK_SPEC-DEBT-001-A`，明确本批次与 A 批次同根因的一致点和差异点。
4. 读取 `TASK_SPEC-DEBT-001-A` post-push 独立只读核查结论；如结论不是 `GO` 或无法定位该核查结论，立即 STOP。
5. 确认当前分支和 `git status --short`。
6. 输出“编码前规格映射计划”后停止，等待 Codex 明确放行。

映射计划必须覆盖以下断言：

1. 断言 1：如何证明 `collectPatternCandidates()` 不再向 `candidateForMatch(...)` 传入硬编码 `true` 作为 `valueFormatSignal`。
2. 断言 2：如何证明 amount pattern 候选的 `valueFormatSignal` 来自 `stripTrailingZeros(...)` 后的候选值是否为真实可解析数值格式，而不是来自常量。
3. 断言 3：如何证明 ratio pattern 候选的 `valueFormatSignal` 来自 `stripTrailingZeros(...)` 后的候选值是否为真实可解析百分比格式，且在通用百分比格式范围内。
4. 断言 4：如何证明本批次与 A 批次保持“信号来自真实输入条件”的一致原则，同时说明差异：A 批次是文本公司名格式，本批次是 amount / ratio 数值格式，不得复用 `isPartyNameValueValid(...)` 判断数值候选。
5. 断言 5：如何证明非 HIGH pattern 候选仍继续通过 `resolveFromCandidates(...)` 和 `MinimalCandidateResolver`，不得绕过 resolver 或直接进入确定性裁判。
6. 测试前置判断：`ratio=150%` 或等价输入中的捕获值 `150` 是否能通过当前 pattern 真实进入 `collectPatternCandidates()` 的 `candidateForMatch(...)` 路径；如果不能进入该路径，不得硬写无效测试，必须 STOP 或提出替代测试设计并等待 Codex 决策。
7. amount 非法格式判断：是否能构造“正则可捕获但 amount 格式非法”的真实候选路径；如果不能构造，必须在计划中说明原因，且不得用无法进入候选路径的输入伪造覆盖。

Codex 未明确放行前，不得修改任何文件。

### 0.3 文件访问范围

本节文件写入限制适用于 Claude Code / DeepSeek 执行阶段。Codex 在冻结前修订本 `TASK_SPEC` 草案不视为 Claude Code / DeepSeek 越界。

```text
✅ 允许修改文件：
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java

👀 允许只读参考文件：
  AGENTS.md
  CURRENT_CONTEXT.md
  PRD.md
  docs/ARCHITECTURE.md
  docs/backend.md
  docs/ai-review.md
  decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md
  decisions/ADR-015-evidence-slot-source-anchor-governance.md
  tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md
  tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolverTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngineTest.java

⛔ 同文件内禁止触碰的方法 / 逻辑：
  resolveTextEvidence()
  isPartyNameValueValid(...)
  resolveRatioEvidence() 的 early return / 候选合并拓扑
  resolveNumericEvidence() 的 fallback 拓扑
  collectStructuredAmountTupleCandidates()
  collectWholeTextCandidates()
  collectSemanticRatioCandidates()
  collectRoleBlockPercentFallbackCandidates()
  collectWeakPaymentClauseFallbackCandidates()
  parser provenance 常量透传
  TABLE_CELL 真实 DOCX 覆盖

⛔ 禁止写入路径：
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolverTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngineTest.java
  fixture / expected JSON / DOCX
  OpenAPI
  数据库
  Docker / Compose
  CURRENT_CONTEXT.md
  changelog/
  tasks/
```

---

## 1. 上下文注入

### 1.1 项目定位

CQCP 是合同质量控制中台 V2。当前审核主链路遵循 `Word-first -> parser-backed candidate collection -> CandidateResolver -> Evidence admission -> 后端确定性裁判 -> ResultSnapshot`。

### 1.2 本任务在链路中的位置

```text
Word parser / EvidenceBuildPlan（不在本任务范围）
  -> ParserBackedReviewInputPreparer.collectPatternCandidates()【本任务】
  -> resolveFromCandidates(...) / MinimalCandidateResolver（只读，不修改语义）
  -> MinimalReviewEngine 确定性裁判（只读，不修改）
```

### 1.3 架构红线

| 红线标识 | 内容 | 本任务相关性 |
|---|---|---|
| RED-001 | SYS-* 系统诊断必须与业务风险 Finding 分流 | 非 HIGH 候选不得伪装为可确定裁判证据 |
| RED-002 | EvidencePacket 不足时不得静默回灌全文，也不得生成无可靠证据的业务 Finding | `valueFormatSignal=false` 只能通过 resolver 降级，不得绕过 admission gate |
| RED-003 | 结构化比对和最终确定性裁判由后端完成，模型不得直接决定 Finding | 本任务不接入模型，不调用外部 API |
| RED-004 | CandidateResolver 五档语义是已冻结治理边界 | 本任务只能改变候选输入信号，不能改 resolver 判定规则 |

### 1.4 冻结契约

- `EvidenceCandidate` 的三信号语义继续使用既有字段：
  - `roleLabelSignal`
  - `valueFormatSignal`
  - `blockAttributionSignal`
- `MinimalCandidateResolver` 的五档语义继续保持：
  - `HIGH`
  - `MEDIUM`
  - `LOW`
  - `CONFLICTED`
  - `UNKNOWN`
- `HIGH` 才允许进入确定性裁判的 admission gate 不变。
- `SourceAnchor`、parser provenance、TABLE_CELL 真实 DOCX 覆盖不在本任务范围内。

### 1.5 本任务领域术语

| 术语 | 定义 |
|---|---|
| pattern 候选 | `collectPatternCandidates()` 通过正则从 block text 中捕获的 amount / ratio 候选 |
| amount pattern 候选 | `CONTRACT_TOTAL_AMOUNT_CONSISTENCY`、`TAX_AMOUNT_FORMULA_CONSISTENCY` 使用 pattern 收集的金额候选 |
| ratio pattern 候选 | 付款比例审核点使用 pattern 收集的百分比候选 |
| valueFormatSignal | 候选值本身是否满足该候选类型的基本格式要求 |
| 同根因一致性 | 与 A 批次一致：不能用硬编码常量伪造候选信号，必须从真实输入计算 |

### 1.6 本任务允许覆盖的 ReviewPointCode

本任务只允许覆盖 `collectPatternCandidates()` 已实际服务的下列 `ReviewPointCode`。

| 候选类别 | 允许覆盖的 ReviewPointCode | 说明 |
|---|---|---|
| amount pattern 候选 | `CONTRACT_TOTAL_AMOUNT_CONSISTENCY` | `resolveNumericEvidence(...)` 通过 `TOTAL_AMOUNT_PATTERNS` 调用 `collectPatternCandidates(...)` 的 pattern 候选 |
| amount pattern 候选 | `TAX_AMOUNT_FORMULA_CONSISTENCY` | `resolveNumericEvidence(...)` 通过 `TAX_AMOUNT_PATTERNS` 调用 `collectPatternCandidates(...)` 的 pattern 候选 |
| ratio pattern 候选 | `PREPAYMENT_RATIO_CONSISTENCY` | `resolveRatioEvidence(...)` 通过 `PREPAYMENT_PATTERNS` 调用 `collectPatternCandidates(...)` 的 direct pattern 候选 |
| ratio pattern 候选 | `PROGRESS_PAYMENT_RATIO_CONSISTENCY` | `resolveRatioEvidence(...)` 通过 `PROGRESS_PAYMENT_PATTERNS` 调用 `collectPatternCandidates(...)` 的 direct pattern 候选 |
| ratio pattern 候选 | `COMPLETION_PAYMENT_RATIO_CONSISTENCY` | `resolveRatioEvidence(...)` 通过 `COMPLETION_PAYMENT_PATTERNS` 调用 `collectPatternCandidates(...)` 的 direct pattern 候选 |
| ratio pattern 候选 | `SETTLEMENT_PAYMENT_RATIO_CONSISTENCY` | `resolveRatioEvidence(...)` 通过 `SETTLEMENT_PAYMENT_PATTERNS` 调用 `collectPatternCandidates(...)` 的 direct pattern 候选 |
| ratio pattern 候选 | `WARRANTY_RETENTION_RATIO_CONSISTENCY` | `resolveRatioEvidence(...)` 通过 `WARRANTY_PAYMENT_PATTERNS` 调用 `collectPatternCandidates(...)` 的 direct pattern 候选 |

仍属于禁止范围的 ratio / amount 路径：

- `resolveNumericEvidence(...)` 的 structured amount tuple fallback 拓扑不得修改。
- `collectStructuredAmountTupleCandidates(...)` 不得修改。
- `resolveRatioEvidence(...)` 的 semantic candidate early return 不得修改。
- `collectSemanticRatioCandidates(...)` 不得修改。
- `collectWholeTextCandidates(...)` 不得修改。
- `collectRoleBlockPercentFallbackCandidates(...)` 不得修改。
- `collectWeakPaymentClauseFallbackCandidates(...)` 不得修改。
- `isExpectedRatioValue(...)` 的 review-point-specific 过滤语义不得修改。

---

## 2. 接口契约

### 2.1 输入

本任务不新增公开接口。核心输入是既有私有方法参数：

```java
private List<EvidenceCandidate> collectPatternCandidates(
        ReviewPointCode reviewPointCode,
        String candidateRole,
        List<WordParserSpikeDocument.DocumentBlock> blocks,
        List<String> labelHints,
        List<Pattern> patterns,
        boolean requireRoleLabelSignal)
```

### 2.2 输出

输出仍为 `List<EvidenceCandidate>`。唯一允许改变的是：`candidateForMatch(...)` 的 `valueFormatSignal` 参数不再是硬编码 `true`，而是由 `reviewPointCode` 和 `candidateValue` 的真实格式判断计算。

### 2.3 错误处理约定

| 情形 | 处理方式 |
|---|---|
| `candidateValue == null || candidateValue.isBlank()` | 保持现有行为：跳过，不生成候选 |
| review point 不属于当前 amount / ratio pattern 覆盖集合 | 不得自行发明规则；输出 STOP |
| 需要修改方法签名或跨模块类型 | 输出 STOP，不得自行改接口 |

### 2.4 禁止触碰的接口和模块

```text
MinimalCandidateResolver.resolve(...)
MinimalReviewEngine
resolveTextEvidence()
resolveRatioEvidence() early return / candidate merge topology
collectStructuredAmountTupleCandidates()
collectWholeTextCandidates()
collectSemanticRatioCandidates()
collectRoleBlockPercentFallbackCandidates()
collectWeakPaymentClauseFallbackCandidates()
SourceAnchor / parser provenance / TABLE_CELL 相关逻辑
```

---

## 3. 行为规格

### 3.1 Happy Path

1. 在 `collectPatternCandidates()` 中保留现有 block 循环、`roleLabelSignal` 计算、`requireRoleLabelSignal` 过滤、pattern matcher 遍历和 `candidateValue` 提取方式。
2. 保留现有 `candidateValue == null || candidateValue.isBlank()` 跳过逻辑。
3. 在构造候选前，根据 `reviewPointCode` 与 `candidateValue` 计算 `valueFormatSignal`。
4. amount review point 仅限 `CONTRACT_TOTAL_AMOUNT_CONSISTENCY`、`TAX_AMOUNT_FORMULA_CONSISTENCY`；其 `valueFormatSignal` 必须基于 `candidateValue` 是否可解析为数值金额格式，不得只因为正则命中就恒为 `true`。
5. ratio review point 仅限 `PREPAYMENT_RATIO_CONSISTENCY`、`PROGRESS_PAYMENT_RATIO_CONSISTENCY`、`COMPLETION_PAYMENT_RATIO_CONSISTENCY`、`SETTLEMENT_PAYMENT_RATIO_CONSISTENCY`、`WARRANTY_RETENTION_RATIO_CONSISTENCY`；其 `valueFormatSignal` 必须基于 `candidateValue` 是否可解析为百分比数值格式，且处于通用百分比格式范围内；review-point-specific expected value 过滤继续由既有 `isExpectedRatioValue(...)` 负责，不得把本任务扩展为业务比例语义改造。
6. 调用 `candidateForMatch(...)` 时继续传入现有 `roleLabelSignal` 和现有 `blockAttributionSignal` 表达式；本任务只修复 `valueFormatSignal`。
7. 所有候选仍通过 `resolveFromCandidates(...)` 进入 `MinimalCandidateResolver`。

### 3.2 Edge Cases

| 场景 | 期望行为 |
|---|---|
| 正则捕获值为空或 blank | 不生成候选，保持现有行为 |
| amount 捕获值可解析为 decimal 数值 | `valueFormatSignal=true` |
| amount 捕获值不可解析为 decimal 数值 | `valueFormatSignal=false`；如果正则实际不会捕获此类值，测试应说明无法通过当前 pattern 进入候选 |
| ratio 捕获值为 `150` | 编码前映射计划必须先证明该值能经当前 pattern 进入 `candidateForMatch(...)`；若能进入，则 `valueFormatSignal=false`，不得进入 HIGH；若不能进入，不得硬写无效测试，必须 STOP 或提出替代测试设计 |
| ratio 捕获值为 `70` | `valueFormatSignal=true`，现有正向路径不应被破坏 |
| ratio 捕获值为 `100` | 通用格式可为 true，但是否符合具体审核点继续由既有 `isExpectedRatioValue(...)` 决定；本任务不得改该语义 |

### 3.3 禁止操作

```text
❌ 不得修改 §0.3 “允许修改”范围之外的任何文件
❌ 不得修改 resolver 五档语义
❌ 不得修改确定性裁判逻辑
❌ 不得修改 ratio early return 或候选合并拓扑
❌ 不得修改 parser provenance / TABLE_CELL / SourceAnchor 逻辑
❌ 不得新增或修改 fixture / expected JSON / DOCX
❌ 不得新增依赖
❌ 不得新增 API、数据库字段、migration、Docker 配置
❌ 不得 commit
❌ 不得 push
```

---

## 4. 架构空白处理规则

如果实现中发现必须定义新的 ReviewPointFamily、候选类型体系、EvidenceSlot 字段、SourceAnchor 字段、parser provenance 契约或公开 API 字段，立即输出：

```text
[STOP: 架构空白 — <缺少内容> — 需要 CODEX 决策]
```

不得自行补全。

---

## 5. 技术栈约束

| 能力 | 指定工具 | 说明 |
|---|---|---|
| 后端实现 | Java / Gradle | 使用现有项目技术栈 |
| 测试 | JUnit / Gradle test | 只运行 §9 指定测试 |
| 数值解析 | JDK 标准库 | 不引入新依赖 |

**语言**：Java。
**数据库**：本任务不涉及 DB。
**测试框架**：沿用现有 Gradle / JUnit 测试。

---

## 6. Git 工作区规则

```text
执行前：
  ✅ 必须确认当前分支为：master
  ✅ 必须确认 git status --short 输出为空
  ✅ 如工作区不干净，立即 STOP，不得自行处理未提交内容

执行中：
  ❌ 不得执行 git commit
  ❌ 不得执行 git push
  ❌ 不得自行切换分支
  ❌ 不得执行 git reset --hard
  ❌ 不得执行 git clean -fd
  ❌ 不得执行 git checkout . 覆盖工作区
  ❌ 不得在 Codex 未放行编码前规格映射计划时修改代码

完成后必须输出：
  - git status --short
  - git diff --stat
  - 实际修改、新增、删除的文件完整列表
```

---

## 7. 版本化要求

- 本任务不新增规则版本、Prompt 版本、模型版本、合同类型画像或 EvidenceSelector。
- 如实现者认为需要新增版本化资产，立即 STOP。

---

## 8. 验收标准

### Must Pass

- [ ] `collectPatternCandidates()` 不再向 `candidateForMatch(...)` 传入硬编码 `true` 作为 `valueFormatSignal`。
- [ ] `valueFormatSignal` 由 `candidateValue` 的真实格式判断计算。
- [ ] amount pattern 候选的格式判断不复用 `isPartyNameValueValid(...)`。
- [ ] ratio pattern 候选的格式判断不复用 `isPartyNameValueValid(...)`。
- [ ] 编码前规格映射计划已证明 `ratio=150%` 或替代输入能真实进入 `collectPatternCandidates()` -> `candidateForMatch(...)` 路径；如不能证明，必须 STOP 或提出替代测试设计，不得硬写无效测试。
- [ ] 在已证明可进入候选路径的前提下，`ratio=150%` 这类正则可捕获但不符合通用百分比格式范围的候选不得进入 HIGH。
- [ ] 现有正常 amount / ratio 正向 fixture 仍通过。
- [ ] 非 HIGH pattern 候选仍通过 `resolveFromCandidates(...)` 和 `MinimalCandidateResolver`，不得绕过 resolver。
- [ ] `MinimalCandidateResolverTest` 仍通过。
- [ ] `MinimalReviewEngineTest` 仍通过。

### 同根因分批修复一致性

- [ ] 本任务与 `TASK_SPEC-DEBT-001-A` 属同一根因：候选三信号不得由无条件常量伪造。
- [ ] 一致点：信号必须来自真实输入条件。
- [ ] 差异点：A 批次处理文本公司名，B 批次处理 amount / ratio 数值候选；差异理由必须在映射计划和实现报告中说明。
- [ ] Codex 必须在审查记录中明确接受或拒绝该差异理由。

### 评测 / fixture / expected 正确答案来源

- [ ] 新增测试不得修改真实 fixture / expected JSON / DOCX。
- [ ] 新增测试如使用 parser double，expected 值必须由输入文本、信号计算规则和 `MinimalCandidateResolver` 规则独立推导，不得倒填被测系统输出。
- [ ] 若 expected 仅证明一致性或回归稳定性，必须明确说明，不得宣称人工 anchor 独立正确性。

### Must Not

- [ ] ❌ 修改 §0.3 允许修改范围之外的文件。
- [ ] ❌ 修改 `resolveTextEvidence()` 或 A 批次新增的 `isPartyNameValueValid(...)`。
- [ ] ❌ 修改 `resolveRatioEvidence()` early return。
- [ ] ❌ 修改 `collectPatternCandidates()` 以外的候选收集拓扑。
- [ ] ❌ 修改 resolver 五档语义或确定性裁判逻辑。
- [ ] ❌ 修改 provenance / TABLE_CELL / fixture / expected JSON / DOCX / OpenAPI / DB / Docker。
- [ ] ❌ 执行 commit 或 push。

### 测试场景覆盖

- [ ] 新增或调整测试证明 `ratio=150%` 不能因 pattern 命中而获得 HIGH。
- [ ] 新增或调整测试证明有效 ratio pattern 候选仍可进入原有正向路径。
- [ ] 现有 amount 正向路径回归仍通过。
- [ ] 如无法构造“正则可捕获但 amount 格式非法”的候选，必须在实现报告中说明原因；不得用无法进入候选路径的输入伪造覆盖。

---

## 9. 测试与验证命令

必须运行：

```text
gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest"
```

禁止运行（未获 Codex 明确授权）：

```text
任何数据库 migration 命令
docker compose up / down
任何需要外部网络的命令
任何修改环境变量或配置文件的命令
```

测试失败时必须区分代码失败与环境失败，并在实现报告中附真实命令和原始 console 输出摘要。

---

## DeepSeek 执行指令块

```text
你正在使用 Claude Code 在本地项目文件夹中执行子任务，推理模型为 DeepSeek。本次任务由 CODEX 规划，执行完成后由 CODEX 审查。

任务：TASK_SPEC-DEBT-001-B collectPatternCandidates valueFormatSignal 修复。

第一步：只读准备。
在做任何实现之前，必须先读取：
  - AGENTS.md
  - CURRENT_CONTEXT.md
  - tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md
  - tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md
  - TASK_SPEC-DEBT-001-A post-push 独立只读核查记录（如不在上述文件中，必须先只读定位；无法定位则 STOP）
  - tasks/active/TASK_SPEC-DEBT-001-B-collect-pattern-candidates-value-format-signal.md
  - apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java
  - apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java

如果 `TASK_SPEC-DEBT-001-A` post-push 独立只读核查结论不是 GO，或无法定位该核查结论，立即输出：
  [STOP: 前置门禁未满足 — TASK_SPEC-DEBT-001-A post-push 独立只读核查未 GO 或无法定位 — 等待 Codex 决策]

第二步：确认 Git 工作区。
执行：
  git branch --show-current
  git status --short

确认：
  - 当前分支为 master
  - git status --short 输出为空

第三步：输出编码前规格映射计划并停止。
在修改任何文件前，输出 TASK_SPEC §0.2 要求的编码前规格映射计划，必须覆盖 5 条断言、同根因一致性、差异理由、不修改路径和预计测试。

输出后停止，等待 Codex 明确放行。

Codex 未放行前：
  - 不得修改任何文件
  - 不得 stage
  - 不得 commit
  - 不得 push
  - 不得进入 provenance / early return / TABLE_CELL / TASK-028 / TASK-031 / TASK-032

第四步：仅在 Codex 明确放行后实现。
Codex 明确输出放行结论前，不得修改任何文件。放行后，只允许修改：
  - apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java
  - apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java

实现必须遵守：
  - 只修复 collectPatternCandidates() 传入 candidateForMatch(...) 的 valueFormatSignal 计算
  - 不修改 resolveTextEvidence()
  - 不修改 isPartyNameValueValid(...)
  - 不修改 resolveRatioEvidence() early return / 候选合并拓扑
  - 不修改 resolveNumericEvidence() fallback 拓扑
  - 不修改 provenance / TABLE_CELL / SourceAnchor 逻辑
  - 不修改 MinimalCandidateResolver / MinimalReviewEngine
  - 不修改 fixture / expected JSON / DOCX / OpenAPI / DB / Docker

第五步：运行 §9 三条指定测试。
必须运行并记录原始 console 输出摘要：
  - gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"
  - gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"
  - gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest"

第六步：输出实现报告。
完成实现和测试后，必须先执行：
  git status --short
  git diff --stat

然后按以下格式输出：

IMPLEMENTATION_REPORT_START
---
Git 状态：
  当前分支：[输出 git branch --show-current]
  git status --short：[原始输出；干净则写 clean]
  git diff --stat：[原始输出]
  实际修改/新增/删除文件：
    - [文件路径]：[修改 / 新增 / 删除]

测试执行结果：
  必须运行的命令：
    - gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"：[通过 / 代码失败 / 环境失败]
      原始 console 输出摘要：[粘贴关键输出]
    - gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"：[通过 / 代码失败 / 环境失败]
      原始 console 输出摘要：[粘贴关键输出]
    - gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest"：[通过 / 代码失败 / 环境失败]
      原始 console 输出摘要：[粘贴关键输出]

STOP 记录：
  - [STOP 原文] | 处理结果：[等待 Codex / 已按 Codex 指示继续]
  （无 STOP 则写：无）

自检结果（对照 Task Spec §8 逐条）：
  Must Pass：
    - [验收项]：[通过 / 未通过 / 部分通过] | 证据：[文件/测试/推导]
  Must Not：
    - 文件范围：[未越界 / 有越界]
    - 禁止方法 / 逻辑：[未触碰 / 有触碰]
    - 新依赖：[无 / 有]
    - 禁止 Git 操作：[未执行 / 有执行]

需要 Codex 审查的决策点：
  - [决策点描述]
  （无则写：无）
---
IMPLEMENTATION_REPORT_END
```

---

## 10. 实现报告

实现 PR #4 已合并到 `master`。

- merge commit：`da724ba49c6a33347641950101a84a84dfa8c000`
- PR head commit：`6513e669ac251f881c4d06ea2574ce6e9c7c9d69`
- 修复范围：`ParserBackedReviewInputPreparer.collectPatternCandidates()` 的 pattern 候选 `valueFormatSignal` 计算。
- 未进入范围：parser provenance、ratio early return、TABLE_CELL、fixture / expected JSON / DOCX、OpenAPI、数据库、Docker、`.github/workflows`。
- CI 状态：临时豁免，原因是当前无 GitHub Actions workflow / checks 可运行；不得写成 CI PASS。
- 父任务状态：`TASK-DEBT-001` 未归档。
- 后续任务状态：未进入 `TASK-028` / `TASK-031` / `TASK-032`。

---

## 11. CODEX 审查记录

- 审计依据：`CQCP_AUDIT GO`。
- PR #4 post-merge 复核：本地 `master` 与 `origin/master` 对齐，`git status --short` 为空，`origin/master...HEAD` 为 `0 0`。
- 本记录仅收口 `TASK_SPEC-DEBT-001-B` 实现 PR，不归档父任务，不进入下一任务。
- 后续治理候选：`TASK-GOV-004 Phase 3：GitHub Actions CI minimal setup`，仅作为候选 / 待 Review Intake；不得写成已启动、已批准或已实施。

---

## 12. 后续任务联动

| 后续任务 | 依赖本任务的产物 | 状态 |
|---|---|---|
| TASK-DEBT-001 后续 provenance 分流 | 无直接解锁；需另行冻结 TASK_SPEC | 🔒 |
| TASK-DEBT-001 后续 ratio early return 分流 | 无直接解锁；需另行冻结 TASK_SPEC | 🔒 |
| TASK-DEBT-001 后续 TABLE_CELL 覆盖分流 | 无直接解锁；依赖人工 anchor 标注 | 🔒 |

---

## 附录：本任务引用的文档

| 文档 | 关键章节 |
|---|---|
| AGENTS.md | 角色分离与证据门禁、任务模板选择规则、项目记忆写入 |
| CURRENT_CONTEXT.md | 当前阶段、活跃任务、阻塞项和下一步任务 |
| tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md | DEBT-001-02 |
| tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md | 同根因第一批修复原则 |
| decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md | CandidateResolver 五档语义 |
| decisions/ADR-015-evidence-slot-source-anchor-governance.md | EvidenceSlot / SourceAnchor 治理边界 |
