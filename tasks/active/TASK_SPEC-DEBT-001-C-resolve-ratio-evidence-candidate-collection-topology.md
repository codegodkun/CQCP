# TASK SPEC — TASK_SPEC-DEBT-001-C resolveRatioEvidence 候选收集拓扑修复
# 适用环境：Claude Code（DeepSeek 模型）— 统一执行环境
# 同一本地项目文件夹下与 CODEX 协作

> **版本**：v0.1
> **状态**：Frozen for coding-preflight only（冻结至编码前规格映射阶段，未派发、未实现）
> **创建日期**：2026-06-29
> **起草**：CODEX
> **执行环境**：Claude Code（DeepSeek 模型）
> **TASK_SPEC 类型**：execution
> **父任务**：TASK-DEBT-001
> **关联 ADR**：ADR-014, ADR-015
> **所在分支**：codex/task-debt-001-c-spec-freeze（base: master）

---

## 0. 任务摘要

**执行模式**：Claude Code 软件壳 + DeepSeek API 推理，运行于本地项目文件夹。

**一句话任务**：修复 `ParserBackedReviewInputPreparer.resolveRatioEvidence()` 在非预付款审核点中只要 `semanticCandidates` 非空就提前 `return`、跳过 direct pattern / whole-text fallback / role-block fallback / weak fallback 候选收集的问题；实现方向限定为“完整候选收集 -> 合并去重 -> 统一送入 `MinimalCandidateResolver`”。本任务不修改 resolver 五档语义、parser provenance、TABLE_CELL、fixture / expected JSON / DOCX、OpenAPI、数据库、Docker、workflow、ADR、PRD，也不进入 `TASK-028` / `TASK-031` / `TASK-032`。

### 0.1 角色与执行门禁

- 本 `TASK_SPEC` 关联父任务 `TASK-DEBT-001`；Claude Code / DeepSeek 不得直接执行父任务。
- 本文件当前仅冻结至“编码前规格映射计划”阶段；这不是实现授权，不得据此直接修改代码、测试、fixture、expected JSON 或任何实现文件。
- Codex 负责冻结规格、审查编码前规格映射计划、审查实现报告和 `git diff`；Codex 不直接编写本任务业务代码。
- Claude Code / DeepSeek 下一步最多只能输出 §0.2 要求的“编码前规格映射计划”并停止；只有在 Codex 后续明确审查该计划并放行实现后，才允许修改 §0.3 中允许修改的文件。
- Claude Code / DeepSeek 不得 commit，不得 push。
- 独立 agent 只做只读事实核查，不得实现或修复代码。
- 不得用 `CURRENT_CONTEXT.md` 自述替代真实代码、测试、原始 console 输出和 commit 证据。

### 0.2 编码前规格映射计划（实现前硬门禁）

Claude Code / DeepSeek 在修改任何文件前，必须先：

1. 读取本 `TASK_SPEC`。
2. 读取父任务 `TASK-DEBT-001`。
3. 读取 `TASK_SPEC-DEBT-001-A` 与 `TASK_SPEC-DEBT-001-B`，明确本批次与前两批的关系：
   - A / B 处理候选信号硬编码；
   - C 处理 ratio 候选收集拓扑，不是同一字段信号修复。
4. 确认当前分支和 `git status --short`。
5. 输出“编码前规格映射计划”后停止，等待 Codex 明确放行。

映射计划必须覆盖以下断言：

1. 断言 1：如何证明 `resolveRatioEvidence()` 不再在 `semanticCandidates` 非空且非 `PREPAYMENT_RATIO_CONSISTENCY` 时提前 `return resolveFromCandidates(...)`。
2. 断言 2：如何证明 semantic candidates、direct pattern candidates、whole-text fallback candidates 会先进入同一个候选集合，再统一送入 `resolveFromCandidates(...)`。
3. 断言 3：如何证明候选合并使用保序去重策略，推荐基于 `LinkedHashSet<EvidenceCandidate>`，不得重复计算同一 record 候选。
4. 断言 4：如何证明 `collectRoleBlockPercentFallbackCandidates(...)` 仍只在 semantic / direct pattern / whole-text 候选均为空后才触发。
5. 断言 5：如何证明 `collectWeakPaymentClausePercentFallbackCandidates(...)` 仍只在候选集合为空且审核点不是 `PREPAYMENT_RATIO_CONSISTENCY` 时触发。
6. 断言 6：如何证明所有最终候选仍统一通过 `MinimalCandidateResolver`，不得绕过 resolver 或直接进入确定性裁判。
7. 断言 7：如何构造真实可证伪测试，覆盖“semantic 候选与 fallback / pattern 候选同时存在且值不同，最终应由 resolver 看到完整候选并给出冲突或非 HIGH”的场景。

Codex 未明确放行前，不得修改任何文件。

### 0.3 文件访问范围

本节文件写入限制适用于 Claude Code / DeepSeek 执行阶段。冻结至编码前规格映射阶段仍不构成实现授权；后续实现仍只限本节允许的两个代码/测试文件，且必须等编码前规格映射计划被 Codex 审查放行。

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
  tasks/active/TASK_SPEC-DEBT-001-B-collect-pattern-candidates-value-format-signal.md
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolverTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngineTest.java
  packages/test-fixtures/

⛔ 同文件内禁止触碰的方法 / 逻辑：
  resolveTextEvidence()
  isPartyNameValueValid(...)
  collectPatternCandidates() 的 valueFormatSignal 计算
  resolveNumericEvidence() 的 fallback 拓扑
  collectStructuredAmountTupleCandidates()
  collectSemanticRatioCandidates() 内部候选识别规则
  collectWholeTextCandidates() 内部候选识别规则
  collectRoleBlockPercentFallbackCandidates() 内部候选识别规则
  collectWeakPaymentClausePercentFallbackCandidates() 内部候选识别规则
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
  workflow
  ADR / decisions/
  PRD.md
  CURRENT_CONTEXT.md
  changelog/
  tasks/
```

---

## 1. 上下文注入

### 1.1 项目定位

CQCP 是合同质量控制中台 V2。当前审核主链路遵循：

```text
Word-first
-> parser-backed candidate collection
-> CandidateResolver
-> Evidence admission
-> 后端确定性裁判
-> ReviewResultSnapshot
```

模型只能提供局部辅助候选，不得直接形成业务 Finding；本任务不涉及模型。

### 1.2 本任务在链路中的位置

```text
Word parser / EvidenceBuildPlan（只读，不在本任务范围）
  -> ParserBackedReviewInputPreparer.resolveRatioEvidence()【本任务仅调整候选收集拓扑】
  -> resolveFromCandidates(...) / MinimalCandidateResolver（只读，不修改语义）
  -> MinimalReviewEngine 确定性裁判（只读，不修改）
```

### 1.3 架构红线

| 红线标识 | 内容 | 本任务相关性 |
|---|---|---|
| RED-001 | `CandidateResolver` 五档语义是已冻结治理边界。 | 本任务只能让 resolver 看到完整候选集合，不能改变 resolver 判定规则。 |
| RED-002 | `HIGH` 才允许进入确定性裁判。 | semantic / fallback 冲突时不得绕过 resolver 直接 admission。 |
| RED-003 | EvidencePacket 不足时不得静默回灌全文，也不得生成无可靠证据的业务 Finding。 | fallback 候选只能作为候选进入 resolver，不得直接形成业务 Finding。 |
| RED-004 | `SYS-*` 系统诊断必须与业务风险 Finding 分流。 | 非 HIGH 或冲突结果应保持 unresolved / SYS 诊断路径。 |

### 1.4 冻结契约

- `EvidenceCandidate` record 字段不变。
- `EvidenceConfidenceLevel` 五档保持：`HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN`。
- `MinimalCandidateResolver.resolve(...)` 签名和判定语义不变。
- `PointEvidence`、`EvidenceStatus`、`NotConcludedReasonCode` 不新增字段或枚举值。
- `resolveFromCandidates(...)` 继续作为 ratio 候选进入 resolver 的统一入口。
- `PREPAYMENT_RATIO_CONSISTENCY` 的现有非提前 return 行为不得回退。
- 本任务不定义新的 `ReviewPointFamily`。
- 本任务不修改 parser provenance、SourceAnchor、TABLE_CELL、fixture 或 expected JSON。

### 1.5 本任务领域术语

| 术语 | 定义 |
|---|---|
| semantic candidates | `collectSemanticRatioCandidates(...)` 根据付款方式、位置片段和语义角色条件收集的 ratio 候选。 |
| direct pattern candidates | `collectPatternCandidates(...)` 通过 direct ratio patterns 从 block text 捕获的候选。 |
| whole-text fallback candidates | `collectWholeTextCandidates(...)` 使用 fallback patterns 从整个 block text 捕获的候选。 |
| role-block fallback candidates | `collectRoleBlockPercentFallbackCandidates(...)` 在前序候选为空时使用 role block 内百分比回退收集的候选。 |
| weak fallback candidates | `collectWeakPaymentClausePercentFallbackCandidates(...)` 在非预付款且前序候选为空时使用付款片段弱回退收集的候选。 |
| 候选收集拓扑 | 各候选来源的收集顺序、是否短路、如何合并去重以及何时交给 resolver 的控制流。 |

---

## 2. 接口契约

### 2.1 输入

本任务不新增公开接口。核心输入是既有私有方法参数：

```java
private PointEvidence resolveRatioEvidence(
        ReviewPointCode reviewPointCode,
        TaskExecutionRequest request,
        EvidenceBuildPlan plan,
        List<String> labelHints,
        List<Pattern> directPatterns,
        List<Pattern> fallbackPatterns)
```

### 2.2 输出

输出仍为 `PointEvidence`。允许改变的是：非预付款审核点在 `semanticCandidates` 非空时，不再跳过后续候选来源；最终候选集合统一进入 `resolveFromCandidates(...)`，由 `MinimalCandidateResolver` 决定 `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN`。

### 2.3 错误处理约定

| 情形 | 处理方式 |
|---|---|
| `semanticCandidates` 非空 | 不得提前 return；继续收集 direct pattern 和 whole-text fallback 候选。 |
| semantic / pattern / whole-text 均为空 | 继续保持现有 role-block fallback 触发条件。 |
| 前述候选仍为空且非预付款 | 继续保持现有 weak fallback 触发条件。 |
| 候选来源存在不同值 | 不得自行裁判；统一交给 resolver。 |
| 需要修改 resolver 才能表达期望 | STOP，不得修改 resolver。 |

### 2.4 禁止触碰的接口和模块

```text
MinimalCandidateResolver.resolve(...)
MinimalReviewEngine
resolveTextEvidence()
isPartyNameValueValid(...)
collectPatternCandidates() 的 valueFormatSignal 计算
resolveNumericEvidence()
parser provenance / SourceAnchor / TABLE_CELL 相关逻辑
fixture / expected JSON / DOCX
OpenAPI / DB / Docker / workflow
```

---

## 3. 行为规格

### 3.1 Happy Path

1. 保留 `paymentMethod` 和 `blocks` 的现有计算逻辑。
2. 保留 `collectSemanticRatioCandidates(...)` 的内部识别逻辑，不修改其候选语义。
3. 删除或等价消除以下短路：

```java
if (!semanticCandidates.isEmpty() && reviewPointCode != ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY) {
    return resolveFromCandidates(reviewPointCode, roleOf(reviewPointCode), semanticCandidates);
}
```

4. 建立一个保序去重候选集合，推荐使用 `LinkedHashSet<EvidenceCandidate>`：
   - 先加入 semantic candidates；
   - 再加入 direct pattern candidates；
   - 再加入 whole-text fallback candidates。
5. 仅当上述集合为空时，才调用 `collectRoleBlockPercentFallbackCandidates(...)` 并加入结果。
6. 仅当集合仍为空且审核点不是 `PREPAYMENT_RATIO_CONSISTENCY` 时，才调用 `collectWeakPaymentClausePercentFallbackCandidates(...)` 并加入结果。
7. 最终将 `List.copyOf(candidates)` 或等价保序去重列表传入 `resolveFromCandidates(...)`。
8. 不改变任何候选内部信号计算、review-point-specific ratio value 过滤、resolver 语义或确定性裁判逻辑。

### 3.2 Edge Cases

| 场景 | 期望行为 |
|---|---|
| 非预付款审核点 semantic candidates 非空，direct pattern 也产生相同候选 | 最终候选去重后只保留一份等价 `EvidenceCandidate`。 |
| 非预付款审核点 semantic candidates 非空，direct pattern / whole-text fallback 产生不同候选值 | resolver 看到完整候选集合，并按现有语义给出 `CONFLICTED` 或非 `HIGH`；不得只接受 semantic 候选。 |
| `PREPAYMENT_RATIO_CONSISTENCY` | 保持当前不提前 return 的行为，仍收集 direct pattern / whole-text fallback。 |
| semantic / direct / whole-text 均为空 | role-block fallback 仍可触发。 |
| role-block fallback 后仍为空且非预付款 | weak payment clause fallback 仍可触发。 |
| 新测试无法真实触发 semantic + fallback 冲突路径 | STOP 或提交替代测试设计，等待 Codex 决策；不得伪造无效覆盖。 |

### 3.3 禁止操作

```text
❌ 不得修改 §0.3 “允许修改”范围之外的任何文件
❌ 不得修改 resolver 五档语义
❌ 不得修改确定性裁判逻辑
❌ 不得修改 parser provenance / TABLE_CELL / SourceAnchor 逻辑
❌ 不得新增或修改 fixture / expected JSON / DOCX
❌ 不得新增依赖
❌ 不得新增 API、数据库字段、migration、Docker 配置或 workflow
❌ 不得进入 TASK-028 / TASK-031 / TASK-032
❌ 不得 commit
❌ 不得 push
```

---

## 4. 架构空白处理规则

如果实现中发现必须定义新的 `ReviewPointFamily`、候选类型体系、EvidenceSlot 字段、SourceAnchor 字段、parser provenance 契约、公开 API 字段或 fixture 标注口径，立即输出：

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
| 候选合并去重 | JDK 标准库 | 推荐 `LinkedHashSet`，不引入新依赖 |

**语言**：Java。
**数据库**：本任务不涉及 DB。
**测试框架**：沿用现有 Gradle / JUnit 测试。

---

## 6. Git 工作区规则

```text
执行前：
  ✅ 必须确认当前分支为 Codex 明确指定的执行分支；如仍在 master，必须先由 Codex 明确说明本地实现后将通过 PR-only 流程转入 PR，不得直接 push master
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

说明：本 `TASK_SPEC` 已冻结至编码前规格映射阶段，但尚未派发实现。未来真正进入编码前映射计划前，必须确认执行分支由 Codex 明确指定且工作区干净。

---

## 7. 版本化要求

- 本任务不新增规则版本、Prompt 版本、模型版本、合同类型画像或 EvidenceSelector。
- 本任务不修改 fixture / expected JSON / DOCX。
- 如实现者认为需要新增版本化资产，立即 STOP。

---

## 8. 验收标准

### Must Pass

- [ ] `resolveRatioEvidence()` 不再包含“semantic candidates 非空且非预付款时立即 `return resolveFromCandidates(...)`”的短路逻辑。
- [ ] semantic candidates、direct pattern candidates、whole-text fallback candidates 会合并到同一个保序去重候选集合。
- [ ] 候选集合为空时才触发 `collectRoleBlockPercentFallbackCandidates(...)`。
- [ ] 候选集合仍为空且审核点不是 `PREPAYMENT_RATIO_CONSISTENCY` 时才触发 `collectWeakPaymentClausePercentFallbackCandidates(...)`。
- [ ] 最终所有候选统一通过 `resolveFromCandidates(...)` 和 `MinimalCandidateResolver`，不得绕过 resolver。
- [ ] 新增或调整测试证明 semantic 候选存在时，pattern / fallback 候选仍会被纳入 resolver 输入。
- [ ] 新增或调整测试证明 semantic 候选与后续候选值冲突时，不会只采用 semantic 候选并误入 `HIGH`。
- [ ] `PREPAYMENT_RATIO_CONSISTENCY` 的现有 positive 行为不回退。
- [ ] 现有 `MEDIUM / LOW / CONFLICTED` ratio fixture 行为不被非预期破坏。
- [ ] `MinimalCandidateResolverTest` 和 `MinimalReviewEngineTest` 仍通过。

### 同根因分批修复一致性

- [ ] 本任务与 `TASK_SPEC-DEBT-001-A` / `TASK_SPEC-DEBT-001-B` 不属于同一字段信号硬编码根因。
- [ ] 本任务对应 `TASK-DEBT-001` 中 DEBT-001-04：ratio candidate collection pipeline 拓扑问题。
- [ ] 本任务不得把 A / B 的信号计算原则扩展为新的 ratio 业务规则。
- [ ] Codex 必须在后续审查记录中确认：C 批次差异理由成立，属于候选集合完整性修复，不是信号语义修复。

### 评测 / fixture / expected 正确答案来源

- [ ] 本任务不修改真实 fixture / expected JSON / DOCX。
- [ ] 新增测试如使用 parser double，expected 值必须由测试输入、候选收集顺序和 `MinimalCandidateResolver` 规则独立推导，不得倒填被测系统输出。
- [ ] 若 expected 仅证明一致性或回归稳定性，必须明确说明，不得宣称人工 anchor 独立正确性。

### Must Not

- [ ] ❌ 修改 §0.3 允许修改范围之外的文件。
- [ ] ❌ 修改 `resolveTextEvidence()`、`isPartyNameValueValid(...)` 或 `collectPatternCandidates()` 的 valueFormatSignal 计算。
- [ ] ❌ 修改 parser provenance / TABLE_CELL / SourceAnchor 逻辑。
- [ ] ❌ 修改 resolver 五档语义或确定性裁判逻辑。
- [ ] ❌ 修改 fixture / expected JSON / DOCX / OpenAPI / DB / Docker / workflow。
- [ ] ❌ 进入 `TASK-028` / `TASK-031` / `TASK-032`。
- [ ] ❌ 执行 commit 或 push。

### 测试场景覆盖

- [ ] 覆盖 semantic candidates 非空时仍继续收集 direct pattern / whole-text fallback 的路径。
- [ ] 覆盖 semantic 与后续候选冲突时 resolver 不返回 `HIGH` confirmed evidence 的路径。
- [ ] 覆盖 prepayment ratio 正向路径不回退。
- [ ] 覆盖现有 ratio negative fixture 不回退。

---

## 9. 测试与验证命令

必须运行：

```text
gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest"
```

可选运行（Codex 后续明确授权后才执行）：

```text
gradle test --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest"
```

禁止运行（未获 Codex 明确授权）：

```text
gradle test
任何数据库 migration 命令
docker compose up / down
任何需要外部网络的命令
任何修改环境变量或配置文件的命令
```

测试失败时必须区分代码失败与环境失败，并在实现报告中附真实命令和原始 console 输出摘要。

---

## DeepSeek 执行指令块

> **重要批注**：以下执行指令块仅供本 TASK_SPEC 后续由 Codex 明确冻结并放行后使用；不构成本轮派发，不允许据此直接开始实现。

```text
你正在使用 Claude Code 在本地项目文件夹中执行子任务，推理模型为 DeepSeek。本次任务由 CODEX 规划，执行完成后由 CODEX 审查。

任务：TASK_SPEC-DEBT-001-C resolveRatioEvidence 候选收集拓扑修复。

第一步：只读准备。
在做任何实现之前，必须先读取：
  - AGENTS.md
  - CURRENT_CONTEXT.md
  - tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md
  - tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md
  - tasks/active/TASK_SPEC-DEBT-001-B-collect-pattern-candidates-value-format-signal.md
  - tasks/active/TASK_SPEC-DEBT-001-C-resolve-ratio-evidence-candidate-collection-topology.md
  - apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java
  - apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java

第二步：确认 Git 工作区。
执行：
  git branch --show-current
  git status --short

确认：
  - 当前分支为 Codex 明确指定的执行分支；如仍在 master，必须先等待 Codex 明确说明本地实现后将通过 PR-only 流程转入 PR，不得直接 push master
  - git status --short 输出为空

任一条件不满足，立即输出：
  [STOP: Git 工作区异常 — 当前分支或工作区状态不满足 TASK_SPEC 要求 — 等待 Codex 决策]

第三步：输出编码前规格映射计划并停止。
在修改任何文件前，输出 TASK_SPEC §0.2 要求的编码前规格映射计划，必须覆盖 7 条断言、不修改路径、范围外风险和预计测试。

输出后停止，等待 Codex 明确放行。

Codex 未放行前：
  - 不得修改任何文件
  - 不得 stage
  - 不得 commit
  - 不得 push
  - 不得进入 provenance / TABLE_CELL / TASK-028 / TASK-031 / TASK-032

第四步：仅在 Codex 明确放行后实现。
Codex 明确输出放行结论前，不得修改任何文件。放行后，只允许修改：
  - apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java
  - apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java

实现必须遵守：
  - 只修复 resolveRatioEvidence() 的候选收集拓扑
  - 不修改 collectSemanticRatioCandidates() 内部候选识别规则
  - 不修改 collectPatternCandidates() 的 valueFormatSignal 计算
  - 不修改 resolveTextEvidence()
  - 不修改 isPartyNameValueValid(...)
  - 不修改 provenance / TABLE_CELL / SourceAnchor 逻辑
  - 不修改 MinimalCandidateResolver / MinimalReviewEngine
  - 不修改 fixture / expected JSON / DOCX / OpenAPI / DB / Docker / workflow

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

### 2026-07-01 implementation report

Implementation branch: `codex/task-debt-001-c-implementation`.

Modified files:

- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`

Implementation summary:

- Removed the non-`PREPAYMENT_RATIO_CONSISTENCY` early return when `semanticCandidates` is non-empty in `resolveRatioEvidence()`.
- Candidate collection now uses `LinkedHashSet<EvidenceCandidate>` for ordered deduplication.
- Candidate topology is `semantic candidates -> direct pattern candidates -> whole-text fallback candidates -> role-block fallback only if previous candidates are empty -> weak fallback only if still empty and non-prepayment`.
- Final candidates still go through `resolveFromCandidates(...)` and `MinimalCandidateResolver`.
- `PREPAYMENT_RATIO_CONSISTENCY` behavior is preserved.
- No resolver semantics, deterministic review engine logic, `resolveTextEvidence()`, `collectPatternCandidates()` `valueFormatSignal`, parser provenance, `SourceAnchor`, `TABLE_CELL`, fixture, expected JSON, DOCX, OpenAPI, database, Docker, workflow, ADR or PRD changes were made.

Test coverage added:

- `ParserBackedReviewInputPreparerEvidenceTest.progressPaymentRatioSemanticAndWholeTextFallbackConflictProducesAmbiguous()`.
- The test uses a parser double with `paymentMethod = MONTHLY`, semantic progress candidate `70%`, and a `节点...85%` block that is excluded from semantic progress matching but captured by whole-text fallback.
- Expected `AMBIGUOUS / CONFLICTED / SYS_ROLE_CONFLICT` is derived from test input, candidate collection topology and `MinimalCandidateResolver` conflict rules. It is not a real DOCX anchor correctness claim.

Verification:

- `gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"` run from `apps/api-server`: `BUILD SUCCESSFUL`.
- `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"` run from `apps/api-server`: `BUILD SUCCESSFUL`.
- `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest"` run from `apps/api-server`: `BUILD SUCCESSFUL`.
- `git diff --check`: no output.

---

## 11. CODEX 审查记录

### 2026-07-01 Review Intake

Codex Review Intake Decision: `GO`.

Basis:

- Implementation matches the frozen candidate collection topology.
- Modified files are limited to the two authorized paths.
- Final candidate set still enters `resolveFromCandidates(...)` / `MinimalCandidateResolver`.
- Required tests passed from `apps/api-server`.
- `git diff --check` returned no output.

Independent read-only audit:

- Audit agent decision: `GO`.
- Findings: no blocking issues.
- Scope check: no out-of-scope file or logic changes detected.
- Residual risk: the new test is a parser-double topology regression test and does not claim independent real DOCX anchor correctness.

Current status:

- `TASK_SPEC-DEBT-001-C` implementation and pre-commit gates are accepted.
- This does not archive parent `TASK-DEBT-001`.
- This does not unlock parser provenance, `TABLE_CELL`, `TASK-028`, `TASK-031` or `TASK-032`.

---

## 12. 后续任务联动

| 后续任务 | 依赖本任务的产物 | 状态 |
|---|---|---|
| TASK-DEBT-001 后续 provenance 分流 | 无直接解锁；需另行冻结 TASK_SPEC 或 ADR 判断 | 🔒 |
| TASK-DEBT-001 后续 TABLE_CELL 覆盖分流 | 无直接解锁；依赖人工 anchor 标注 | 🔒 |
| TASK-028 Gemma Provider 最小接入 | 不由本任务解锁 | 🔒 |
| TASK-031 Result API / Admin API mapper 补洞 | 不由本任务解锁 | 🔒 |
| TASK-032 ParserBackedReviewInputPreparer 重构 | 不由本任务解锁 | 🔒 |

---

## 附录：本任务引用的文档

| 文档 | 关键章节 |
|---|---|
| AGENTS.md | 角色分离与证据门禁、任务模板选择规则 |
| CURRENT_CONTEXT.md | 当前 active 债务记录任务、后续门禁 |
| tasks/MVP_TASK_MAP.md | TASK-DEBT-001 后续分流与 TASK-028 / TASK-031 / TASK-032 禁止进入状态 |
| tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md | DEBT-001-04 标准记录 |
| tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md | A 批次已完成信号修复边界 |
| tasks/active/TASK_SPEC-DEBT-001-B-collect-pattern-candidates-value-format-signal.md | B 批次已合并边界 |
| decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md | CandidateResolver 五档语义 |
| decisions/ADR-015-evidence-slot-source-anchor-governance.md | EvidenceSlot / SourceAnchor 治理边界 |
