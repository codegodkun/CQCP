# TASK SPEC — TASK_SPEC-DEBT-001-A resolveTextEvidence 文本候选信号修复
# 适用环境：Claude Code（DeepSeek 模型）— 统一执行环境
# 同一本地项目文件夹下与 CODEX 协作

> **版本**：v0.1
> **状态**：Implemented（Codex Review Intake：A. 可以合并；独立只读审计：GO；未 commit / 未 push）
> **创建日期**：2026-06-25
> **起草**：CODEX
> **执行环境**：Claude Code（DeepSeek 模型）
> **TASK_SPEC 类型**：execution
> **父任务**：TASK-DEBT-001
> **关联 ADR**：ADR-014, ADR-015
> **所在分支**：master

---

## 0. 任务摘要

**执行模式**：Claude Code 软件壳 + DeepSeek API 推理，运行于本地项目文件夹。

**一句话任务**：
修复 `ParserBackedReviewInputPreparer.resolveTextEvidence()` 构造 PARTY_A / PARTY_B 文本候选时三类信号硬编码为 `true` 的问题，使 `roleLabelSignal`、`valueFormatSignal`、`blockAttributionSignal` 分别由真实输入条件计算；本任务不修改 ratio、amount、parser provenance、early return、TABLE_CELL、审核点枚举或结果快照契约。

### 0.1 角色与执行门禁

- 本 `TASK_SPEC` 关联父任务 `tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`。
- Codex 只负责冻结规格、审查编码前规格映射计划、审查实现报告和 `git diff`；不得直接编写本任务业务代码。
- Claude Code / DeepSeek 只能修改授权文件，不得 commit，不得 push。
- Claude Code / DeepSeek 在修改任何文件前，必须先输出 §0.2 的“编码前规格映射计划”，等待 Codex 明确放行。
- 独立 agent 后续只做只读流程核查，不得承担本任务实现。
- 不得用 `CURRENT_CONTEXT.md` 自述替代真实代码、测试、原始 console 输出或 commit 证据。

### 0.2 编码前规格映射计划（实现前硬门禁）

Claude Code / DeepSeek 在修改任何代码前，必须先输出以下计划并等待 Codex 明确放行：

```text
验收断言映射：
- 断言 1：如何证明 resolveTextEvidence() 不再向 candidateForBlock(...) 传入连续 true, true, true
- 断言 2：如何证明 roleLabelSignal 来自当前命中行/当前 block 的真实 label 或 alias 命中
- 断言 3：如何证明 valueFormatSignal 来自清洗后的候选值是否真实存在且满足文本值要求
- 断言 4：如何证明 blockAttributionSignal 来自候选值与当前 block / anchor 的可归属关系
- 断言 5：如何证明非 HIGH 文本候选不得进入确定性裁判

关键字段 / 信号计算：
- roleLabelSignal：真实输入来源、计算条件、失败条件
- valueFormatSignal：真实输入来源、计算条件、失败条件
- blockAttributionSignal：真实输入来源、计算条件、失败条件

明确不修改路径：
- ratio / amount 候选收集
- collectPatternCandidates()
- parser provenance 常量透传
- resolveRatioEvidence() early return
- TABLE_CELL 真实 DOCX 覆盖
- ReviewPointCode / MinimalCandidateResolver 五档语义

范围外风险：
- 列出本任务不会处理但可能影响后续的风险

预计测试变更：
- 说明新增或修改的测试文件、场景和断言
```

Codex 未明确放行前，不得进入实现。事后测试通过不能替代该门禁。

### 0.3 文件访问范围

```text
✅ 允许修改：
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java

👀 允许只读参考：
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
  apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolverTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngineTest.java
  packages/test-fixtures/

⛔ 禁止访问：
  .env
  .env.*
  secrets/
  credentials/
  config/production/
  db/migrations/
  deploy/
  infra/

⛔ 禁止写入：
  apps/admin-web/
  packages/api-contracts/
  decisions/
  docs/ARCHITECTURE.md
  docs/backend.md
  docs/ai-review.md
  PRD.md
  CURRENT_CONTEXT.md
  changelog/
  tasks/
  package.json
  package-lock.json
  Dockerfile
  docker-compose*.yml
  compose*.yml
  deploy/
```

说明：`MinimalCandidateResolverTest` 和 `MinimalReviewEngineTest` 是必跑回归测试与只读参考，不属于允许修改文件；如实现需要修改它们，必须 STOP。

说明：`docs/ARCHITECTURE.md`、`docs/backend.md`、`docs/ai-review.md`、`CURRENT_CONTEXT.md` 和 `tasks/` 在本任务中只读，不得写入。`tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md` 只允许读取自身内容，不允许 Claude Code / DeepSeek 修改自身规格。

---

## 1. 上下文注入（DeepSeek 必读）

### 1.1 项目定位

本项目是 Contract Quality Control Platform / 合同质量控制中台 V2。当前核心链路为：

```text
Word-first parser
-> CandidateIndex / candidate collection
-> CandidateResolver
-> EvidenceSlot preflight
-> 后端确定性裁判
-> ReviewResultSnapshot
```

平台只提供风险提示、证据和审核结果 URL，不批准、不拒绝、不阻断、不修改审批流。

### 1.2 本任务在链路中的位置

```text
DocxWordParserSpike（只读，不在本任务范围）
      ↓
【ParserBackedReviewInputPreparer.resolveTextEvidence】← 本任务
      ↓
MinimalCandidateResolver（只读，不改五档语义）
      ↓
MinimalReviewEngine / EvidenceSlot preflight（只读，不改裁判）
      ↓
ReviewResultSnapshot（只读，不改契约）
```

### 1.3 架构红线

| 红线标识 | 内容 | 本任务相关性 |
|----------|------|-------------|
| RED-001 | `CandidateResolver` 只做保守高置信角色归属；非 `HIGH` 不得进入确定性裁判。 | 本任务只修文本候选输入信号，不得放宽 `HIGH` admission gate。 |
| RED-002 | `SYS-*` 系统诊断必须与业务 Finding 分流；证据不足不得生成业务 Finding。 | 文本候选降级后应产生 `NOT_CONCLUDED` / SYS 诊断，不得硬造 ERROR。 |
| RED-003 | EvidenceSlot 不足或 anchor 不可靠时不得静默回灌全文。 | `blockAttributionSignal=false` 或无可靠 anchor 时不得让候选伪装成完整证据。 |
| RED-004 | 模型输出不得直接形成最终 `PASS / ERROR / WARNING`。 | 本任务不得调用模型，不得新增模型辅助路径。 |

违反任一红线时，立即输出：

```text
[STOP: 红线冲突 — <红线名称> — <冲突描述> — 需要 CODEX 决策]
```

### 1.4 冻结契约

```text
- ReviewPointCode 不新增、不删除、不重命名。
- EvidenceConfidenceLevel 五档保持：HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN。
- MinimalCandidateResolver 判定语义保持不变。
- EvidenceStatus / PointStatus / NotConcludedReasonCode 不新增枚举。
- `HIGH` 才能进入 confirmed evidence；非 HIGH 保持 unresolved evidence。
- 本任务不改变 sourceOrigin / sourceExtractionMode / contextType 透传策略。
- 本任务不改变 SourceAnchor / previewElementRef / locationLevel 契约。
- 本任务不创建、修改或删除 fixture / expected JSON。
```

### 1.5 本任务领域术语

| 术语 | 定义 |
|------|------|
| `roleLabelSignal` | 当前候选是否有可解释的角色标签或 alias 命中，例如甲方、乙方、发包方、承包方等。 |
| `valueFormatSignal` | 当前候选值是否真实存在且满足文本值的最低格式要求。文本值不得是空白、纯占位符或清洗后无有效字符。 |
| `blockAttributionSignal` | 当前候选是否能可靠归属于当前 parser block，并具备可追溯 block anchor。 |
| `HIGH admission` | 只有 `MinimalCandidateResolver` 返回 `HIGH` 时，候选才能进入 confirmed evidence 和后端确定性裁判。 |
| 文本候选 | `resolveTextEvidence()` 为 PARTY_A / PARTY_B 构造的 `EvidenceCandidate`。 |

---

## 2. 接口契约（Interface Contract）

### 2.1 输入

```java
// 现有输入，签名不得修改
private PointEvidence resolveTextEvidence(
        ReviewPointCode reviewPointCode,
        String candidateRole,
        List<String> labelHints,
        List<Pattern> blockPatterns,
        EvidenceBuildPlan plan)
```

字段说明：

- `reviewPointCode`：当前审核点，仅限 PARTY_A / PARTY_B 调用路径。
- `candidateRole`：当前候选角色，例如 `PARTY_A` / `PARTY_B`。
- `labelHints`：当前文本候选允许的 label / alias 提示词。
- `blockPatterns`：从行文本中抽取候选值的正则集合。
- `plan`：已由 parser 和 index 阶段构造的 evidence build plan。

### 2.2 输出

```java
// 现有输出，签名不得修改
PointEvidence
```

输出约束：

- `HIGH` 候选输出 `EvidenceStatus.CONFIRMED`。
- 非 `HIGH` 候选输出 `EvidenceStatus.AMBIGUOUS` 或 `MISSING`，并保留现有 diagnostic 语义。
- 无可靠 block anchor 时不得输出可确定业务结论。

### 2.3 错误处理约定

| 情形 | 处理方式 |
|------|----------|
| 没有候选 | 保持 `UNKNOWN` / `SYS_INDEX_INCOMPLETE` 路径。 |
| 候选有 label 但 value format 不满足 | 不得进入 `HIGH`；应由 resolver 降级为非 `HIGH`。 |
| 候选 value 可读但 block attribution 不可靠 | 不得进入 `HIGH`；不得生成 confirmed evidence。 |
| 需要修改 resolver 五档语义才可实现 | STOP，不得自行修改 resolver。 |

### 2.4 禁止触碰的接口和模块

```text
❌ MinimalCandidateResolver.resolve(...) 签名和五档判定语义
❌ ReviewPointCode 枚举
❌ PointEvidence / EvidenceCandidate record 字段
❌ MinimalReviewEngine 裁判逻辑
❌ sourceOrigin / sourceExtractionMode / contextType provenance 透传逻辑
❌ resolveRatioEvidence()
❌ collectPatternCandidates()
❌ packages/test-fixtures/expected/*.json
❌ packages/test-fixtures/docx/*.docx
```

---

## 3. 行为规格（Behavior Spec）

### 3.1 Happy Path

1. `resolveTextEvidence()` 遍历 parser block 和 block 内 line。
2. 对每个正则命中的 line，抽取 `matchedValue` 并生成清洗后的 `value`。
3. 计算 `roleLabelSignal`：
   - 必须基于当前命中 line 或当前 block 中是否存在 `labelHints` / `blockPatterns` 所代表的真实角色标签。
   - 不得写死为 `true`。
4. 计算 `valueFormatSignal`：
   - 必须基于清洗后的 `value` 是否非空、非纯占位符、包含有效文本字符。
   - 不得写死为 `true`。
5. 计算 `blockAttributionSignal`：
   - 必须基于候选值是否来自当前 block，以及 `block.blockId()` 是否非空。
   - 不得写死为 `true`。
6. 使用计算出的三个信号构造 `EvidenceCandidate`。
7. 继续复用 `resolveFromCandidates(...)` 和 `MinimalCandidateResolver`，不得绕过 resolver。
8. 现有正向 / 负向 fixture 的 PARTY_A / PARTY_B 结果不得发生非预期变化。

### 3.2 Edge Cases

| 场景 | 期望行为 |
|------|----------|
| line 命中角色正则且清洗后 value 为正常公司名 | 三个信号均可为 `true`，可进入 `HIGH`。 |
| line 命中角色正则但清洗后 value 为空或纯占位符 | `valueFormatSignal=false`，不得进入 `HIGH`。 |
| block 无非空 `blockId` | `blockAttributionSignal=false`，不得进入 `HIGH`。 |
| block 含 label hint 但实际命中 line 无法证明角色标签 | `roleLabelSignal=false` 或 STOP 说明当前契约无法区分，不得继续写死 true。 |
| 多个文本候选冲突 | 保持 resolver 冲突语义，不得自行裁决。 |

### 3.3 禁止操作

```text
❌ 不得修改 §0.3 允许修改范围之外的任何文件
❌ 不得写入 §0.3 允许只读参考、禁止访问或禁止写入中的文件
❌ 不得运行数据库 migration 命令
❌ 不得调用任何外部 API 或模型 endpoint
❌ 不得引入冻结契约之外的字段 key、错误码或枚举值
❌ 不得修改 MinimalCandidateResolver 五档语义
❌ 不得修改 MinimalReviewEngine 裁判逻辑
❌ 不得修改 ratio / amount 候选路径
❌ 不得修改 parser provenance 常量覆盖问题
❌ 不得修改 fixture / expected JSON / DOCX
❌ 不得把审核点规则模块化重构混入本任务
```

---

## 4. 架构空白处理规则

如果实现必须依赖下列内容，立即输出：

```text
[STOP: 架构空白 — <缺少什么> — 需要 CODEX 决策]
```

禁止自行补全：

```text
❌ 不得新增领域模型
❌ 不得新增数据库字段或表
❌ 不得新增 API 字段或端点
❌ 不得新增状态枚举值
❌ 不得新增 ReviewPointFamily
❌ 不得新增 CandidateRole 命名空间
❌ 不得新增未批准的 EvidenceSlot 抽象
❌ 不得新增未批准的依赖库
❌ 不得重构整个 ParserBackedReviewInputPreparer
```

---

## 5. 技术栈约束

| 能力 | 指定库/工具 | 说明 |
|------|------------|------|
| 后端实现 | Java | 使用现有 Spring / Gradle 工程，不新增依赖。 |
| 单元测试 | JUnit 5 + AssertJ | 复用现有测试风格。 |
| Word parser 测试替身 | 现有 test double | 可在授权测试文件中新增局部 parser stub。 |

**语言**：Java / 中文注释与文档。
**数据库**：不涉及。
**测试框架**：Gradle + JUnit 5 + AssertJ。

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
  ❌ 不得执行 git checkout .（覆盖工作区内容）
  ❌ 不得删除未在 §0.3 允许修改中明确列出的文件
  ❌ 不得在 Codex 尚未审查并放行编码前规格映射计划时修改代码

完成后必须输出：
  - git status --short 结果
  - git diff --stat 结果
  - 实际修改、新增、删除的文件完整列表
```

---

## 7. 版本化要求

- [ ] 不新增 `RuleSetVersion` 字段。
- [ ] 不新增审核点版本。
- [ ] 不修改 fixture / expected JSON。
- [ ] 若新增测试 helper 方法，只作为代码测试能力，不得宣称形成新规则资产。

---

## 8. 验收标准（Acceptance Criteria）

### Must Pass（必须全部通过）

- [ ] `resolveTextEvidence()` 中不得再出现 `candidateForBlock(..., true, true, true)` 或等价的三信号无条件全 true 写法。
- [ ] `roleLabelSignal` 必须由当前命中 line / block 的真实 label 或 alias 命中计算，不得常量化。
- [ ] `valueFormatSignal` 必须由清洗后的候选值计算；空白、纯占位符或无有效文本字符不得为 `true`。
- [ ] `blockAttributionSignal` 必须由当前 block 归属和非空 `blockId` / anchor 条件计算，不得常量化。
- [ ] 正常 PARTY_A / PARTY_B 正向 fixture 仍输出 `EvidenceStatus.CONFIRMED`，候选值和 anchor 不回退。
- [ ] 至少新增一个专项测试证明文本候选在 `valueFormatSignal=false` 或 `blockAttributionSignal=false` 时不会进入 `HIGH` confirmed evidence。
- [ ] 非 `HIGH` 文本候选仍通过现有 `MinimalCandidateResolver` 和 `resolveFromCandidates(...)` 处理，不得绕过 resolver。
- [ ] 本任务不得改变 ratio / amount 审核点的现有测试结果。
- [ ] 本任务不得修改 `MinimalCandidateResolver` 五档语义。

### 同根因分批修复一致性

- [ ] 本任务是否与已完成批次属于同一语义根因：否。本任务是第一批。
- [ ] 本任务必须在实现报告中沉淀三信号计算原则，供第二批 `collectPatternCandidates()` 对照。
- [ ] 后续第二批如采用不同判断标准，必须在第二批 TASK_SPEC 中说明差异理由并由 Codex 接受或拒绝。

### 评测 / fixture / expected 正确答案来源

- [ ] 本任务是否涉及评测、fixture 或 expected JSON：不涉及 fixture / expected JSON；涉及测试。
- [ ] 新增测试的 expected 值必须由测试输入直接推导，不得依赖被测系统输出倒填。
- [ ] 如使用 parser test double，输入 block、blockId、line text 和期望状态必须在测试中显式声明。
- [ ] 本任务测试只能证明代码路径和回归行为，不得声明真实合同 anchor 独立正确性。

### Must Not

- [ ] ❌ 修改了 §0.3 允许修改范围之外的文件
- [ ] ❌ 写入了 §0.3 允许只读参考、禁止访问或禁止写入中的文件
- [ ] ❌ 引入了 §5 之外的新依赖
- [ ] ❌ 补全了 §4 禁止补全的架构内容
- [ ] ❌ 执行了 §6 禁止的 Git 操作
- [ ] ❌ 修改 fixture / expected JSON / DOCX
- [ ] ❌ 修改 `collectPatternCandidates()`、`resolveRatioEvidence()` 或 provenance 常量透传
- [ ] ❌ 将审核点规则模块化重构混入本任务

### 测试场景覆盖

- [ ] 现有正向 fixture：PARTY_A / PARTY_B 仍 confirmed。
- [ ] 现有负向 fixture：PARTY_A / PARTY_B 仍绑定 golden document evidence，不受 structured field negative matrix 干扰。
- [ ] 新增文本候选非 HIGH 场景：至少覆盖 `valueFormatSignal=false` 或 `blockAttributionSignal=false`。
- [ ] 目标回归测试通过：`ParserBackedReviewInputPreparerEvidenceTest`。
- [ ] 目标回归测试通过：`MinimalCandidateResolverTest`。
- [ ] 目标回归测试通过：`MinimalReviewEngineTest`。

---

## 9. 测试与验证命令

```text
必须运行：
  - gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"
  - gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"
  - gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest"

可选运行（环境允许时执行）：
  - gradle test --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest"

禁止运行（未经 CODEX 明确授权）：
  - gradle test 全量
  - 任何数据库 migration 命令
  - docker compose up / down
  - 任何需要外部网络的命令
  - 任何修改环境变量或生产配置文件的命令
```

测试失败时必须区分代码失败与环境失败。环境失败必须输出：

```text
[STOP: 环境失败 — <命令> — <错误摘要> — 需要人工确认]
```

---

## ══════════ DeepSeek 执行指令块 ══════════

```text
你正在使用 Claude Code 在本地项目文件夹中执行子任务，推理模型为 DeepSeek。
本次任务由 CODEX 规划，执行完成后由 CODEX 审查。

你在 Claude Code 场景下的主动停止、主动提问和边界遵守行为不应被默认信任，
因此本任务通过显式 STOP 指令约束所有边界行为。遇到触发条件时必须停止并输出，
不得自行判断"这个情况应该可以继续"。

【第一步：执行前必读文件】
在做任何实现之前，必须先读取以下文件。
如果任一文件不存在、无法读取，或内容与本 Task Spec 冲突，立即 STOP：

  - AGENTS.md
  - CURRENT_CONTEXT.md
  - PRD.md
  - docs/ARCHITECTURE.md
  - docs/backend.md
  - docs/ai-review.md
  - decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md
  - decisions/ADR-015-evidence-slot-source-anchor-governance.md
  - 父 TASK 文件：tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md
  - 当前 TASK_SPEC 文件：tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md

不得只依赖本 prompt 的摘要内容进行实现。

【第二步：确认 Git 工作区】
读取文件后，执行：
  git branch --show-current
  git status --short

确认：
  - 当前分支为 master
  - git status --short 输出为空

任一条件不满足，立即 STOP：
  [STOP: Git 工作区异常 — <具体描述> — 需要人工处理后再继续]

【第三步：提交编码前规格映射计划】
在修改任何文件前，先输出 TASK_SPEC §0.2 要求的编码前规格映射计划。
输出后停止，等待 Codex 明确放行。
Codex 未放行前，不得修改代码或测试。

【第四步：执行任务】
Codex 放行后，按照 Task Spec 第 0~4 节执行。
文件访问严格按 §0.3 四类边界：允许修改 / 允许只读参考 / 禁止访问 / 禁止写入。

遇到以下任何情况，立即停止，输出 STOP，不得自行决策继续：
  - 发现需要修改禁止范围内的文件
  - 发现需要写入"允许只读参考"或"禁止写入"中的文件
  - 发现接口契约有歧义或缺失
  - 发现需要引入新的依赖库
  - 发现实现方向与架构红线冲突
  - 发现需要新增领域模型、字段、枚举或抽象层
  - 发现需要调用任何模型 endpoint
  - 工作区出现预期之外的状态变化

STOP 输出格式（严格遵守）：
  [STOP: <类型> — <具体描述> — <建议或问题>]
  类型：红线冲突 / 接口歧义 / 契约缺失 / 架构空白 / 依赖缺失 /
        文件越界 / Git异常 / 环境失败 / 其他

【第五步：运行测试】
按照 Task Spec §9 指定的命令运行测试。
- 必须运行的命令全部执行，不得跳过
- 禁止运行的命令不得执行
- 测试失败时区分代码失败和环境失败，并记录完整命令输出

【第六步：完成后输出实现报告】
完成所有实现和测试后，必须依次执行并输出：
  git status --short
  git diff --stat

然后输出以下报告（格式严格，不得省略任何节）：

IMPLEMENTATION_REPORT_START
---
Git 状态：
  当前分支：[输出]
  git status --short：[输出，干净则写 "clean"]
  git diff --stat：[输出]
  实际修改/新增/删除文件：
    - [文件路径]：[新增 / 修改 / 删除]
    （严格区分"允许修改"范围内外）

测试执行结果：
  必须运行的命令：
    - [命令]：[✅ 通过 / ❌ 代码失败 / ⚠️ 环境失败]
    - 失败时附输出摘要：[摘要]
  可选命令（若执行）：
    - [命令]：[结果]
  环境失败记录：
    - [命令]：[错误摘要] | 判断依据：[为什么判断为环境失败而不是代码失败]
    （无环境失败则写：无）

假设列表：
  - [假设]：[内容] | 原因：[原因]
  （无假设则写：无）

歧义列表：
  - [歧义]：[描述] | 我的处理：[描述] | 建议确认：[问题]
  （无歧义则写：无）

未实现 / 降级实现：
  - [项目]：[原因] | 建议后续：[建议]
  （全部实现则写：无）

STOP 记录：
  - [STOP 原文] | 处理结果：[等待人工 / 已按指示继续]
  （无 STOP 则写：无）

自检结果（对照 Task Spec §8 逐条）：
  Must Pass：
    - [验收项]：✅ 通过 / ❌ 未通过 / ⚠️ 部分通过
  Must Not：
    - 文件"允许修改"范围约束：✅ 未越界 / ❌ 有越界
    - 文件"允许只读"未被写入：✅ 未写入 / ❌ 有写入
    - 新依赖引入：✅ 无 / ❌ 有
    - 架构空白补全：✅ 无 / ❌ 有
    - Git 操作合规：✅ 合规 / ❌ 违规
    - 禁止命令未执行：✅ 未执行 / ❌ 有执行

需要 CODEX 审查的决策点：
  - [决策点]：[描述]
  （无则写：无）
---
IMPLEMENTATION_REPORT_END
```

---

## 10. 实现报告（DeepSeek 完成后填写）

Claude Code / DeepSeek 已完成实现并提交实现报告摘要：

- 修改文件：
  - `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java`
  - `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java`
- 实现摘要：
  - `resolveTextEvidence()` 不再向 `candidateForBlock(...)` 传入连续 `true, true, true`。
  - `roleLabelSignal` 改为基于 `labelHints.stream().anyMatch(block.text()::contains)` 的 block-level label / alias 命中。
  - `valueFormatSignal` 改为基于清洗后的候选值与 `isPartyNameValueValid(...)` 计算；纯占位符和纯数字不得进入 `HIGH`。
  - `blockAttributionSignal` 改为基于 `block.blockId()` 非 null 且非 blank。
  - 新增 parser test double 与专项测试，覆盖 `"甲方：—"`、`"甲方：123"`、`"甲方：A1"` 与空 `blockId` 场景。
- 执行方声明：
  - 未修改 `MinimalCandidateResolver` 五档语义。
  - 未修改 `MinimalReviewEngine` 裁判逻辑。
  - 未修改 ratio / amount / provenance / early return / TABLE_CELL 路径。
  - 未修改 fixture / expected JSON / DOCX。
  - 未 commit，未 push。
- 必跑测试：
  - `gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"`：通过。
  - `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"`：通过。
  - `gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest"`：通过。

说明：完整原始 console 输出保留在本轮执行上下文中；长期任务文档只记录必要事实和验证摘要。

---

## 11. CODEX 审查记录

Codex 已完成实现报告、`git diff` 与测试复核。

```text
TASK_SPEC 断言 1：满足，证据：resolveTextEvidence() 已传入 roleLabelSignal / valueFormatSignal / blockAttributionSignal 三个计算变量，不再保留连续 true, true, true。
TASK_SPEC 断言 2：满足，证据：roleLabelSignal 来自 labelHints.stream().anyMatch(block.text()::contains)，符合本批接受的 block-level 粒度，未改成无条件常量 true。
TASK_SPEC 断言 3：满足，证据：valueFormatSignal 来自清洗后的 value 与 isPartyNameValueValid(...)；测试覆盖 "甲方：—"、"甲方：123" 降级，以及 "甲方：A1" 可确认。
TASK_SPEC 断言 4：满足，证据：blockAttributionSignal 来自 block.blockId() != null && !block.blockId().isBlank()；空 blockId 测试降级为 MEDIUM。
TASK_SPEC 断言 5：满足，证据：仍通过 resolveFromCandidates(...) 和 MinimalCandidateResolver；未绕过 resolver，非 HIGH 不进入 confirmed evidence。

最终结论：A. 可以合并
```

Codex 复跑验证：

```text
工作目录：apps/api-server
gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest"  BUILD SUCCESSFUL
gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest"                 BUILD SUCCESSFUL
gradle test --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest"                       BUILD SUCCESSFUL
```

独立只读审计结论：

```text
READONLY_AUDIT_REPORT：GO
结论摘要：仅两个允许文件修改；五条 TASK_SPEC 断言均满足；禁止范围未触碰；三组指定测试全部 BUILD SUCCESSFUL；建议 Codex 进入记忆写回与后续提交判断。
```

提交状态：

- 当前仍未 stage。
- 当前仍未 commit。
- 当前仍未 push。
- 后续如需提交，必须由用户单独授权 commit；如需 push，必须再次单独授权 push。

## 12. 后续任务联动

| 后续任务 | 依赖本任务的产物 | 状态 |
|----------|------------------|------|
| TASK_SPEC-DEBT-001-B collectPatternCandidates valueFormatSignal 修复 | 本任务沉淀的三信号计算原则和 Codex 审查结论 | A 批次已获 Codex 接受并经独立只读审计 GO；待用户授权后可进入 B 批次规格起草 |
| parser provenance 分流任务 | 不依赖本任务实现，只依赖后续 Review Intake | 未定界 |
| resolveRatioEvidence early return 分流任务 | 不依赖本任务实现，只依赖后续 Review Intake | 未定界 |
| TABLE_CELL 真实 DOCX 覆盖补强 | 依赖人工 anchor 标注 | 未定界 |
| 审核点规则写法模块化优化 | 依赖后续架构 / TASK-032 或独立父任务定界 | 未定界 |

---

## 附录：本任务引用的文档

| 文档 | 关键章节 |
|------|----------|
| AGENTS.md | 角色分离、证据门禁、TASK_SPEC 模板选择规则 |
| CURRENT_CONTEXT.md | 当前阻塞项、`TASK-DEBT-001` 五条记录状态 |
| docs/ARCHITECTURE.md | CandidateResolver、EvidenceSlot、SYS/Finding 分流 |
| docs/backend.md | ReviewExecutionPlan、MVP 审核执行逻辑、EvidenceSlot 约束 |
| docs/ai-review.md | 模型职责边界、候选归属和 SYS 诊断 |
| decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md | 五档置信度与 admission gate |
| decisions/ADR-015-evidence-slot-source-anchor-governance.md | EvidenceSlot / SourceAnchor 治理 |
| tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md | DEBT-001-01 标准记录 |
| docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md | Step 3 角色分离试点 |

---

本 Task Spec 由 Codex 起草。用户审核确认后，才可将 DeepSeek 执行指令块粘贴进 Claude Code 启动任务。
