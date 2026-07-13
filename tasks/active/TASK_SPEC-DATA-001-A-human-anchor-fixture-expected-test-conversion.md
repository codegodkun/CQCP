# TASK SPEC — TASK_SPEC-DATA-001-A 人工 anchor fixture / expected JSON / 测试转换
# 适用环境：Claude Code（DeepSeek 模型）— 统一执行环境
# 同一本地项目文件夹下与 CODEX 协作

> **版本**：v0.1
> **状态**：CODING-PLAN MAPPING NO-GO / REVISION REQUIRED / NO IMPLEMENTATION AUTHORIZATION
> **创建日期**：2026-07-12
> **起草**：CODEX
> **执行环境**：Claude Code（DeepSeek 模型）
> **TASK_SPEC 类型**：execution
> **父任务**：TASK-DATA-001
> **关联 ADR**：N/A（本任务不改变架构、审核链路或 SourceAnchor 契约）
> **执行分支 / worktree**：`codex/task-data-001-a-human-anchor-conversion` / `C:\Users\1\Documents\CQCP-worktrees\task-data-001-a`（基线 commit：`a2e9c085cd4a073d7f9dcba55cf891ace3d556da`）

---

## 0. 任务摘要

**执行模式**：Claude Code 软件壳 + DeepSeek API 推理，运行于本地项目文件夹。

**一句话任务**：把 `TASK-DATA-001-human-anchor-template.xlsx` 中三份 DOCX 已接受的 63 条人工逐出处 anchor，转换为 3 份规范化 human-anchor fixture JSON，在现有 001-003 expected JSON 中增加可追溯引用，并新增定向测试验证 Excel → fixture → expected 引用的完整性与独立性；不修改 parser、Review Engine、DOCX、sample matrix 或现有 parser-backed canonical anchor 基线，不运行完整 MVP E2E。

### 0.1 角色与执行门禁

- 本 `TASK_SPEC` 关联父任务 `TASK-DATA-001`；Claude Code / DeepSeek 不得直接执行父任务。
- 当前只授权读取本规格并输出 §0.2 的“编码前规格映射计划”。**不得直接修改 fixture、expected JSON、测试或其他文件。**
- Codex 必须先审查并明确放行编码前规格映射计划，之后执行者才可修改 §0.3 的允许文件。
- Codex 负责审查实现报告和 `git diff`，不得由执行者自行宣布验收通过。
- Claude Code / DeepSeek 不得 commit，不得 push。
- 2026-07-13，Codex 已从审计通过的 `a2e9c085cd4a073d7f9dcba55cf891ace3d556da` 基线提供干净隔离 worktree；当前只允许在该 worktree 读取规格并输出 §0.2 计划，不得在 `master` 主工作区执行本规格。
- 独立 agent 只做后续只读事实核查，不得实施或修复本规格。

### 0.2 编码前规格映射计划（实现前硬门禁）

执行者在修改任何文件前必须输出以下内容并停止，等待 Codex 明确放行：

```text
验收断言映射：
- 逐条说明如何证明 001/002/003 分别转换 22/19/22 条，共 63 条。
- 逐条说明如何证明 63 条与 XLSX 的字段值完全一致。
- 说明如何证明 6 条排除项不进入一致性评价，但仍保留可追溯记录。
- 说明如何证明现有 expectedCanonicalAnchors[] 未被人工 fixture 或 parser actual 倒填改写。

关键字段 / 信号计算：
- includedInConsistencyEvaluation：只能由 XLSX notes 的已接受“排除”判断确定。
- acceptedOccurrenceCount / includedOccurrenceCount / excludedOccurrenceCount：如何从 fixture 记录计算并与冻结数量核对。
- sourceWorkbook / sourceSheet / occurrenceNo：如何形成 XLSX → fixture → expected 的追溯链。

明确不修改路径：
- DOCX、sample matrix、XLSX 人工源文件、parser、CandidateResolver、EvidenceSlot、SourceAnchor、Review Engine。

范围外风险：
- 人工可见位置没有 parser blockId / rowIndex / cellIndex；说明为什么本任务不得从 actual 输出反推 canonical key。
- 说明本任务只验证转换完整性，不声明完整 MVP E2E 或 parser attribution 已通过。

预计测试变更：
- HumanAnchorGroundTruthFixtureTest 的具体测试方法、输入文件和断言。
- 现有 ParserBackedEvidenceOverlapBaselineTest 的回归命令；默认不修改该文件。
```

Codex 未明确放行前不得进入实现。

### 0.3 文件访问范围

```text
✅ 允许创建：
  packages/test-fixtures/human-anchors/CQCP-MVP-DOCX-001.json
  packages/test-fixtures/human-anchors/CQCP-MVP-DOCX-002.json
  packages/test-fixtures/human-anchors/CQCP-MVP-DOCX-003.json
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/HumanAnchorGroundTruthFixtureTest.java

✅ 允许修改：
  packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json
  packages/test-fixtures/expected/CQCP-MVP-DOCX-002.json
  packages/test-fixtures/expected/CQCP-MVP-DOCX-003.json
  packages/test-fixtures/README.md

👀 允许只读参考：
  AGENTS.md
  CURRENT_CONTEXT.md
  docs/VERIFY.md
  tasks/active/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md
  outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx
  packages/test-fixtures/expected/CQCP-MVP-DOCX-004.json
  packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx
  packages/test-fixtures/docx/2、达利安造船厂四方城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx
  packages/test-fixtures/docx/3、星辰建设集团南山科技园项目二标段土建总承包工程合同_缩减版.docx
  apps/api-server/build.gradle.kts
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedEvidenceOverlapBaselineTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/EvidenceOverlapEvaluator.java

⛔ 禁止写入：
  outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx
  packages/test-fixtures/cqcp-mvp-sample-matrix.xlsx
  packages/test-fixtures/docx/
  packages/test-fixtures/expected/CQCP-MVP-DOCX-004.json
  apps/api-server/src/main/
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedEvidenceOverlapBaselineTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/EvidenceOverlapEvaluator.java
  parser、CandidateResolver、EvidenceSlot、SourceAnchor、Review Engine 相关实现或既有测试
  OpenAPI、数据库、migration、Docker、Compose、workflow、ADR、PRD
  CURRENT_CONTEXT.md、changelog/、tasks/（执行者不得写项目记忆或任务文件）
```

---

## 1. 上下文注入

### 1.1 项目定位

CQCP 是合同质量控制中台 V2。本任务位于测试数据治理层，只把已接受的人工 DOCX anchor 转换为可版本化、可测试、可追溯的 fixture；不改变生产审核链路。

### 1.2 本任务在链路中的位置

```text
DOCX 人工阅读与 ZK 最终接受（TASK-DATA-001，已完成）
  -> XLSX 63 条 ACCEPTED_HUMAN_GROUND_TRUTH（只读源）
  -> 【规范化 human-anchor fixture + expected 引用 + 转换完整性测试】
  -> 后续正式 parser / evidence / MVP E2E 验收（不在本任务范围）
```

### 1.3 架构红线

| 红线标识 | 内容 | 本任务相关性 |
|---|---|---|
| RED-001 | 人工 ground truth 必须独立于 parser / AI / 被测系统 actual 输出。 | fixture 字段只能来自已接受 XLSX，不得从测试 actual 倒填。 |
| RED-002 | 同一 table / row 不得宽松替代 cell 命中。 | 本任务保留人工 `anchorGranularity` 和 table/row/cell context，不自行降级。 |
| RED-003 | parser-backed canonical baseline 只证明一致性，不证明独立正确性。 | 现有 `expectedCanonicalAnchors[]` 不得改写或重新标记来源。 |
| RED-004 | EvidenceSlot、SourceAnchor、CandidateResolver 和 Review Engine 属于受治理边界。 | 本任务只改测试数据与新测试，不修改生产实现。 |

### 1.4 冻结契约

- 人工源文件：`outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx`。
- 人工源 sheet：`anchor明细待确认`。
- 样本范围：仅 `CQCP-MVP-DOCX-001` / `002` / `003`。
- 冻结数量：001 = 22，002 = 19，003 = 22，总计 63。
- 63 条的 `comparisonResult = MATCH`。
- 63 条的 `groundTruthSource = MANUAL_DOCX_REVIEW`。
- 63 条的 `status = ACCEPTED_HUMAN_GROUND_TRUTH`。
- 排除项：总计 6 条，每份样本 2 条；保留记录，但 `includedInConsistencyEvaluation = false`。
- 纳入项：001 = 20，002 = 17，003 = 20，总计 57。
- data owner：`ZK`。
- `expectedCanonicalAnchors[]` 继续使用现有 parser-backed baseline，不属于本次人工 fixture 转换字段。
- 现有冻结 canonical baseline 必须保持：
  - 001：`TABLE_ROW:block-4:0`
  - 002：`BLOCK:block-38`
  - 003：`TABLE_ROW:block-4:1`
- 004 expected JSON 不在本任务范围。

### 1.5 领域术语

| 术语 | 定义 |
|---|---|
| human-anchor fixture | 从已接受 XLSX 逐行无损转换的独立人工 anchor JSON。 |
| occurrence | 一个 DOCX 可见出处，对应唯一 `occurrenceNo`。 |
| 排除项 | 为追溯而保留、但不参与甲乙方名称一致性判断的合同标题或前言名称。 |
| parser-backed canonical baseline | 现有 expected JSON 中由 canonical key 表达的回归基线，不等同于人工可见位置 ground truth。 |
| 转换完整性 | XLSX 与 fixture 在冻结字段、数量、状态和排除语义上一致。 |

---

## 2. 数据契约

### 2.1 human-anchor fixture 顶层结构

每份 `packages/test-fixtures/human-anchors/<sampleId>.json` 必须满足：

```json
{
  "schemaVersion": "v1",
  "sampleId": "CQCP-MVP-DOCX-001",
  "sourceDocx": "docx/...docx",
  "sourceWorkbook": "outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx",
  "sourceSheet": "anchor明细待确认",
  "dataOwner": "ZK",
  "groundTruthSource": "MANUAL_DOCX_REVIEW",
  "status": "ACCEPTED_HUMAN_GROUND_TRUTH",
  "occurrences": []
}
```

### 2.2 occurrence 结构

每条 occurrence 必须包含且只从 XLSX 对应行转换以下字段：

```text
occurrenceNo
reviewPointCode
reviewPointName
expectedCandidateValue
observedCandidateValue
comparisonResult
anchorGranularity
humanAnchorText
humanLocationDescription
tableContext
rowContext
cellContext
groundTruthSource
dataOwner
independenceStatement
status
includedInConsistencyEvaluation
exclusionReason
```

转换规则：

- `includedInConsistencyEvaluation = false`：仅当 XLSX `notes` 以已接受的“排除：”语义开头。
- 排除项 `exclusionReason` 必须保存排除原因；纳入项必须为 `null`。
- 不把 XLSX `notes` 中普通确认话术复制为新的业务字段。
- JSON 必须使用 UTF-8、2 空格缩进、文件末尾换行。
- occurrence 顺序必须与 XLSX 行顺序一致。
- 禁止加入 `blockId`、`rowIndex`、`cellIndex`、`previewElementRef`、`expectedCanonicalAnchors` 或任何 parser actual 派生字段。

### 2.3 expected JSON 引用结构

只允许在 001-003 的 `goldenExpected` 下新增：

```json
"humanAnchorGroundTruth": {
  "schemaVersion": "v1",
  "fixture": "human-anchors/CQCP-MVP-DOCX-001.json",
  "sourceWorkbook": "outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx",
  "sourceSheet": "anchor明细待确认",
  "groundTruthSource": "MANUAL_DOCX_REVIEW",
  "dataOwner": "ZK",
  "status": "ACCEPTED_HUMAN_GROUND_TRUTH",
  "acceptedOccurrenceCount": 22,
  "includedOccurrenceCount": 20,
  "excludedOccurrenceCount": 2
}
```

各样本数量必须使用 §1.4 冻结值。不得复制 63 条 occurrence 到 expected JSON，避免双份明细漂移。

### 2.4 错误处理约定

| 情形 | 处理方式 |
|---|---|
| XLSX 缺失、sheet 缺失或列名不匹配 | STOP，不得猜测列号或补字段。 |
| 任一行不是 `MATCH / MANUAL_DOCX_REVIEW / ACCEPTED_HUMAN_GROUND_TRUTH` | STOP，不得跳过或自动修正。 |
| 数量不是 22 / 19 / 22 或排除项不是每份 2 条 | STOP，报告差异 occurrenceNo。 |
| fixture 与 XLSX 任一冻结字段不一致 | 测试失败，修正转换；不得修改 XLSX 迎合 fixture。 |
| 需要 canonical key 才能完成 fixture | STOP；本规格禁止从 actual 输出倒填。 |
| 现有 expected canonical baseline 测试失败 | STOP 并报告，不得修改 parser 或 canonical baseline 规避失败。 |

---

## 3. 行为规格

### 3.1 Happy Path

1. 从 XLSX 通过表头名称定位字段，不得依赖固定列号作为唯一语义依据。
2. 仅选择 sampleId 001-003 且状态为 `ACCEPTED_HUMAN_GROUND_TRUTH` 的 63 行。
3. 按 §2.1-2.2 生成 3 份 fixture JSON，保留 occurrence 顺序。
4. 根据已接受的排除说明计算 `includedInConsistencyEvaluation` 和 `exclusionReason`。
5. 在 001-003 expected JSON 中仅新增 §2.3 引用对象。
6. 更新 `packages/test-fixtures/README.md`，说明人工 fixture 来源、63/57/6 数量、独立性和 canonical baseline 边界。
7. 新增 `HumanAnchorGroundTruthFixtureTest`，读取 XLSX、fixture 和 expected JSON，执行 §8 全部断言。
8. 运行 §9 定向测试和既有 parser-backed baseline 回归。

### 3.2 Edge Cases

| 场景 | 期望行为 |
|---|---|
| XLSX 百分比显示为 `70%` | JSON 保存人工可见语义字符串 `70%`，不得转换成 `0.7`。 |
| TABLE_CELL 行 | `tableContext` / `rowContext` / `cellContext` 均必须为非空且不能为“不适用”。 |
| BLOCK 行 | 三个 table context 字段按 XLSX 原值保留，不自行构造表格定位。 |
| 同一审核点多出处 | 每个 `occurrenceNo` 独立保存，不合并成数组内单条长文本。 |
| 标题/前言名称排除 | 保留 occurrence，设 `includedInConsistencyEvaluation=false`，不能删除。 |
| expected JSON 已有 `evidenceEvaluation.positiveCases` | 原对象和 canonical key 原样保留，只增加 sibling `humanAnchorGroundTruth`。 |

### 3.3 禁止操作

```text
❌ 不得修改 XLSX、DOCX 或 cqcp-mvp-sample-matrix.xlsx
❌ 不得修改或重排现有 goldenExpected.structuredFields、displayValues、negativeCandidates
❌ 不得修改现有 evidenceEvaluation.positiveCases 或 expectedCanonicalAnchors[]
❌ 不得从 parser / AI / 被测系统 actual 输出生成或修正人工 fixture
❌ 不得修改 parser、CandidateResolver、EvidenceSlot、SourceAnchor 或 Review Engine
❌ 不得新增生产代码、API、数据库、Docker、workflow、ADR 或 PRD
❌ 不得运行完整 MVP E2E
❌ 不得归档 TASK-EVAL-001，不得补足 DoD #12
❌ 不得进入 TASK-028 / TASK-031 / TASK-032
❌ 不得 commit，不得 push
```

---

## 4. 架构空白处理规则

出现以下任一需求立即 STOP：

```text
[STOP: 架构空白 — 需要修改 expected schema 的既有字段或运行时 loader — 需要 CODEX 决策]
[STOP: 独立性冲突 — 需要 parser actual 才能确定 expected 值或 canonical key — 不得继续]
[STOP: 源数据冲突 — XLSX 与 TASK-DATA-001 冻结数量或状态不一致 — 需要人工确认]
[STOP: 范围扩大 — 需要修改生产代码、DOCX、matrix 或第 4 份样本 — 需要 CODEX 决策]
```

不得自行新增领域模型、运行时枚举、API 字段、数据库字段、依赖或架构抽象。

---

## 5. 技术栈约束

| 能力 | 指定工具 | 说明 |
|---|---|---|
| XLSX 测试读取 | Apache POI 5.3.0 | 已是 `apps/api-server` implementation 依赖，不新增依赖。 |
| JSON 测试读取 | Jackson `ObjectMapper` | 沿用现有测试模式。 |
| 测试 | JUnit 5 + AssertJ | 沿用 Spring Boot test 依赖。 |
| JSON 生成 | 执行环境现有能力或人工转换 | 不提交新生成器，不安装依赖；最终由测试逐字段校验。 |

**语言**：Java 21（测试）；JSON UTF-8。
**数据库**：不涉及。
**新增依赖**：禁止。

---

## 6. Git 工作区规则

```text
执行前：
  ✅ 必须位于 Codex 后续明确提供的 codex/task-data-001-a-human-anchor-conversion 分支或隔离 worktree
  ✅ git status --short 必须为空
  ✅ 必须确认已接受 XLSX 在该基线中存在
  ✅ 当前 master 脏工作区不满足执行条件，必须 STOP

执行中：
  ❌ 不得 commit
  ❌ 不得 push
  ❌ 不得自行切换分支
  ❌ 不得 reset --hard、clean -fd 或 checkout .
  ❌ 不得处理或回退用户侧 DOCX 003 变更
  ❌ 未经 Codex 放行编码前规格映射计划，不得修改任何文件

完成后必须输出：
  - git status --short
  - git diff --name-status
  - git diff --stat
  - git diff --check
  - 实际修改文件清单
  - 测试原始摘要
```

---

## 7. 版本化要求

- human-anchor fixture `schemaVersion = v1`。
- expected 引用对象 `schemaVersion = v1`。
- `sourceWorkbook`、`sourceSheet`、`dataOwner`、`groundTruthSource`、`status` 必须固化。
- 不新增散列算法或版本生成器；后续若需要内容 hash，另行定界。
- 任何数量或人工源变更必须回到人工标注流程，不得直接改 fixture 作为“新版本”。

---

## 8. 验收标准

### Must Pass

- [ ] 恰好新增 3 份 human-anchor fixture，sampleId 分别为 001/002/003。
- [ ] fixture occurrence 数量分别为 22/19/22，总计 63，`occurrenceNo` 全局唯一且与 XLSX 一致。
- [ ] 63 条的 `comparisonResult`、`groundTruthSource`、`status` 分别全部为 `MATCH`、`MANUAL_DOCX_REVIEW`、`ACCEPTED_HUMAN_GROUND_TRUTH`。
- [ ] 每条 fixture 的 review point、候选值、观察值、粒度、原文、位置描述和 table/row/cell context 与 XLSX 对应行逐字段相等。
- [ ] 排除项恰好 6 条，每份样本 2 条；纳入项恰好 57 条，分别为 20/17/20。
- [ ] 6 条排除项保留 occurrence 且 `includedInConsistencyEvaluation=false`；其余 57 条为 `true`。
- [ ] TABLE_CELL 行三个 context 字段均可人工复核；不得用 parser id 替代。
- [ ] 001-003 expected JSON 只新增 `goldenExpected.humanAnchorGroundTruth`，引用、来源、状态和数量与 fixture 一致。
- [ ] 001-003 现有 `structuredFields`、`displayValues`、`negativeCandidates`、`evidenceEvaluation.positiveCases` 和 `expectedCanonicalAnchors[]` 语义不变。
- [ ] 004 expected JSON、全部 DOCX、sample matrix 和 XLSX 无改动。
- [ ] fixture 中不存在 `blockId`、`rowIndex`、`cellIndex`、`previewElementRef`、`expectedCanonicalAnchors` 或 parser / AI actual 派生字段。
- [ ] `HumanAnchorGroundTruthFixtureTest` 对 XLSX → fixture → expected 引用链执行真实读取和断言，不使用复制粘贴后的同一 JSON 同源自证。
- [ ] §9 两个定向测试命令通过，`git diff --check` 通过。

### 同根因分批修复一致性

- [ ] 本任务不是缺陷分批修复；不适用前批修复一致性。
- [ ] 与 `TASK-DATA-001` 一致点：独立人工来源、逐出处记录、排除项保留、TABLE_CELL 人工 context 不降级。
- [ ] 差异点：父任务产出 XLSX，本规格只做版本化 JSON 和测试转换，不重新定义人工答案。

### 评测 / fixture / expected 正确答案来源

- [ ] 本任务涉及 fixture 和 expected JSON。
- [ ] expected 人工 anchor 值唯一来源为 `ZK` 已接受的 XLSX 63 条记录。
- [ ] 不依赖 parser、AI 或被测系统 actual 输出确定人工 expected。
- [ ] 现有 parser-backed `expectedCanonicalAnchors[]` 只保留为历史回归基线，不声明为本次人工 ground truth。
- [ ] 不存在 actual → expected 的循环倒填。

### Must Not

- [ ] ❌ 修改 §0.3 允许范围外文件。
- [ ] ❌ 修改人工 XLSX、DOCX、matrix 或 004 expected JSON。
- [ ] ❌ 修改现有 canonical baseline 以迎合测试 actual。
- [ ] ❌ 引入新依赖、生成器、生产代码或运行时 schema。
- [ ] ❌ 修改 parser / CandidateResolver / EvidenceSlot / SourceAnchor / Review Engine。
- [ ] ❌ 运行完整 MVP E2E、归档 TASK-EVAL-001 或进入 TASK-028/031/032。

### 测试场景覆盖

- [ ] 三样本数量、全局唯一 occurrenceNo 和冻结状态。
- [ ] 63 行逐字段 XLSX 对比。
- [ ] 6 条排除 / 57 条纳入语义。
- [ ] TABLE_CELL context 条件必填。
- [ ] expected 引用路径和计数。
- [ ] 禁止 parser 派生字段。
- [ ] 现有 parser-backed baseline 回归不变。

---

## 9. 测试与验证命令

在 `apps/api-server` 目录运行：

```powershell
gradle test --tests com.cqcp.apiserver.reviewengine.HumanAnchorGroundTruthFixtureTest
gradle test --tests com.cqcp.apiserver.reviewengine.ParserBackedEvidenceOverlapBaselineTest
```

在仓库根目录运行：

```powershell
git status --short
git diff --name-status
git diff --stat
git diff --check
```

禁止运行：完整 MVP E2E、完整无过滤测试套件、数据库 migration、Docker Compose、外部 API / 模型调用、网络安装命令。

测试失败时必须区分代码失败和环境失败；不得修改人工源、canonical baseline 或生产代码来消除失败。

---

## ══════════ DeepSeek 执行指令块 ══════════

```text
你正在 Claude Code + DeepSeek 环境执行 TASK_SPEC-DATA-001-A。

当前只允许：
1. 阅读本 TASK_SPEC、父 TASK 和 §0.3 只读文件；
2. 检查分支与 git status；
3. 输出 §0.2 编码前规格映射计划；
4. 停止并等待 Codex 明确放行。

当前禁止修改任何文件。必须位于 Codex 提供的 `codex/task-data-001-a-human-anchor-conversion` 隔离 worktree；若分支、worktree、基线 commit、XLSX 跟踪状态或 `git status --short` 任一不符合，输出：
[STOP: 工作区不干净 — TASK_SPEC-DATA-001-A 不得在当前工作区执行 — 需要 CODEX 准备隔离分支或 worktree]

任何时候如需从 parser / AI / actual 输出反推人工 expected 或 canonical key，立即输出：
[STOP: 独立性冲突 — actual 不得倒填人工 ground truth — 需要 CODEX 决策]

不得 commit，不得 push，不得扩大文件范围。
```

---

## 10. 实现报告

状态：待执行。

执行者完成后必须填写：

### 10.1 编码前规格映射计划与 Codex 放行证据

2026-07-12 Codex 门禁核对：

- 当前分支：`master`。
- `git status --short` 非空，包含 `TASK-DATA-001` 文档 / outputs、项目记忆和用户侧 DOCX 003 变更。
- 已接受 XLSX 在文件系统中存在，但 `git ls-files --error-unmatch outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx` 失败，说明该文件尚未进入可由 Git 重建的基线。
- 当前不具备“干净、隔离且包含已接受 XLSX”的执行基线。
- 依照 §0.1、§6 和 DeepSeek 执行指令块，本轮未向 Claude Code / DeepSeek 请求编码前规格映射计划，未授权实现。

门禁结论：`NO-GO / EXECUTION BASELINE NOT READY / PLAN REQUEST NOT DISPATCHED`。

2026-07-13 Codex 基线复核：

- `TASK-DATA-001` 提交前独立只读审计最终结论为 `GO`，允许的 7 个路径已精确提交。
- 可重建基线 commit：`a2e9c085cd4a073d7f9dcba55cf891ace3d556da`。
- 隔离分支 / worktree：`codex/task-data-001-a-human-anchor-conversion` / `C:\Users\1\Documents\CQCP-worktrees\task-data-001-a`。
- 已接受 XLSX 已进入 Git 基线；隔离 worktree 的 `git status --short` 为空。
- 当前只解除“计划请求未派发”前的基线阻塞，不构成 fixture、expected JSON、测试或实现修改授权。

当时门禁结论：`GO TO CODING-PLAN MAPPING / NO IMPLEMENTATION AUTHORIZATION`。

2026-07-13 Claude Code / DeepSeek 编码前规格映射计划审查：

- 计划已由用户从隔离 worktree 回传；Codex 复核时主工作区和隔离 worktree 均为 clean，HEAD 为 `61dde2ee5703f2f6fa8010f31b449b9c9a223b25`，未发现计划阶段文件修改。
- 计划正确覆盖 22/19/22、63/57/6、XLSX → fixture → expected 追溯、TABLE_CELL context、canonical baseline 不倒填、两个定向测试和禁止修改路径。
- Codex Decision：`NO-GO / CODING-PLAN REVISION REQUIRED / NO IMPLEMENTATION AUTHORIZATION`。

必须修订后重新提交计划：

1. occurrence 冻结 schema 是 §2.2 的 18 个字段，不是计划所写的 20 个；白名单必须逐字段列出并以 18 个为准。
2. 排除项 `exclusionReason` 只能保存 XLSX 已接受的原始原因；如 `notes` 只有“排除：”而无原因，必须 STOP，不得生成“未记录原因”等新文本，也不得只 WARNING 后继续。
3. `humanAnchorGroundTruth` 只能新增为 `goldenExpected` 的 sibling；不得表述或实现为 `evidenceEvaluation.positiveCases` 内字段。`positiveCases` 必须原样不变。
4. 所有 candidate / context / text 字段均按 XLSX 人工可见字符串读取并写为 JSON string；必须明确使用 Apache POI `DataFormatter` 等等价的显示值读取方式，使 `70%` 不变为 `0.7`，`12800` 不写成 JSON number。
5. “既有 structuredFields / displayValues / negativeCandidates 未变”不得通过修改后的 expected JSON 自我比较证明。计划必须明确：新测试验证冻结 canonical key 与新引用；既有 parser-backed 测试做行为回归；Codex 通过 `git diff` 确认 expected patch 仅新增 `goldenExpected.humanAnchorGroundTruth`。
6. 补充 `sourceDocx` 的真实来源计算：每个 sample 的 XLSX `docxPath` 必须唯一一致，并转换为相对 `packages/test-fixtures/` 的 `docx/...docx`；不一致或前缀异常时 STOP，不得从 expected/parser 推断。

### 10.2 实际修改文件

待填写。

### 10.3 实现摘要与字段映射

待填写。

### 10.4 验收标准逐项自检

待填写。

### 10.5 测试命令与原始摘要

待填写。

### 10.6 风险、未完成项与 STOP 记录

待填写。

---

## 11. Codex Review Intake

当前 Decision：`NO-GO / CODING-PLAN REVISION REQUIRED / CLEAN ISOLATED BASELINE VERIFIED / NO IMPLEMENTATION AUTHORIZATION`。

首次 `NO-GO` 的解除条件及状态：

1. 已完成：人工 XLSX、父任务、项目记忆和用户侧 DOCX 003 修正已进入 commit `a2e9c085cd4a073d7f9dcba55cf891ace3d556da`。
2. 已完成：DOCX 003 的用户修改来源、三处可见文本、9 个 OOXML 部件和哈希已记录，未回退或隐藏。
3. 已完成：隔离分支 / worktree 已创建并确认 `git status --short` 为空。
4. 当前允许：要求 Claude Code / DeepSeek 按 §10.1 六项要求输出修订版 §0.2 编码前规格映射计划并停止；仍不得修改文件。

Codex 后续必须分别完成：

1. 等待 Claude Code / DeepSeek 按 §10.1 六项要求提交修订版编码前规格映射计划；Codex 重新审查并给出 `GO` 或 `NO-GO`。
2. 实现完成后审查实现报告、`git diff`、测试输出和范围。
3. 必要时派独立 agent 只读复核。
4. 单独给出接纳、返工或停止决定；执行者自述不自动代表通过。

---

## 12. Memory Writeback 与 Next Task Handoff

- 本规格建档由 Codex 同步 `TASK-DATA-001`、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md` 和 `changelog/2026-07.md`。
- 执行者不得修改长期项目记忆。
- 当前下一步是编码前规格映射计划，不是实现；不得把本文件直接当作实现授权。
- 本规格不归档 `TASK-DATA-001` 或 `TASK-EVAL-001`，不补足 DoD #12，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
