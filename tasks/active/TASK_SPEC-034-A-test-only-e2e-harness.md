# TASK SPEC — TASK_SPEC-034-A test-only MVP E2E harness
# 适用环境：Claude Code（DeepSeek 模型）— 统一执行环境
# 同一本地项目文件夹下与 CODEX 协作

> **版本**：v0.1
> **状态**：Implemented / Codex Review Intake ACCEPT / Independent Audit ACCEPT / Formal Phase 1 executed
> **创建日期**：2026-07-14
> **起草**：CODEX
> **执行环境**：Claude Code（DeepSeek 模型）
> **TASK_SPEC 类型**：execution
> **父任务**：TASK-034
> **关联 ADR**：ADR-015（只读遵守；本规格不修改架构边界）
> **目标实现分支**：`codex/task-034-a-test-only-e2e-harness`

---

## 0. 任务摘要

**一句话任务**：新增一个 test-only MVP E2E harness，以单次真实 DOCX 执行串联当前 parser、审核状态机、结果快照和同 task 结果查询，并输出可与 63 条人工 occurrence 显式比较的数据；不修改任何生产实现、公开契约或人工 ground truth。

### 0.1 角色与执行门禁

- 本规格关联父任务 `TASK-034`，不替代父任务。
- Codex 只冻结规格、审查编码前规格映射计划、实现报告与 diff；不直接实现 harness。
- Claude Code / DeepSeek 在 Codex 明确给出 `GO / IMPLEMENTATION AUTHORIZED` 前，只能提交编码前规格映射计划，不得修改文件。
- Claude Code / DeepSeek 不得 commit、push、merge。
- harness 实现完成后必须先经 Codex Review Intake 和独立 agent 只读复核；在此之前不得运行 001/002/003 正式验收模式。

### 0.2 编码前规格映射计划（硬门禁）

实现前必须逐项回答并等待 Codex 明确 `GO`：

```text
验收断言映射：
- 如何证明同一 run 输入真实 DOCX，并经过 parse/index/plan/build/review/compose/query。
- 如何证明查询到的 taskId/executionId 与本次执行完全相同。
- 如何从同一次 run 观察 actual candidateValue，而不修改生产 snapshot/API。
- 如何把同一次 run 的 actual SourceAnchor 映射到人工 occurrence，且不使用宽松同表/同行替代。
- 如何保证 63 行、57 纳入、6 EXCLUDED，且排除项不影响 pointStatus。

关键字段 / 信号计算：
- pointStatus、actualCandidateValue、evidenceSummary、actualAnchorReference、sysDiagnostics、coverageResult 的真实来源。
- NOT_MATCHED、NOT_OBSERVABLE、EXCLUDED 的互斥条件。

明确不修改路径：
- 本规格 §0.3 所有只读和禁止写入路径。

范围外风险：
- 生产 snapshot 不含 candidateValue；只能以 test-only 同 run 观察方式解决，不能改生产契约。
- human fixture 不含 canonical key；不得把 parser key 倒填回 fixture。

预计测试：
- harness 自测、63/57/6 契约、自同一 run 查询、字段/枚举/schema、正式模式默认禁用、既有四类回归。
```

### 0.3 文件访问范围

```text
✅ 允许新增/修改：
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/Task034MvpE2eAcceptanceHarnessTest.java
  apps/api-server/src/test/resources/task-034/**

👀 允许只读参考：
  apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/**
  apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/**
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/HumanAnchorGroundTruthFixtureTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedEvidenceOverlapBaselineTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachineTest.java
  apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskResultQueryControllerTest.java
  packages/test-fixtures/docx/**
  packages/test-fixtures/human-anchors/**
  packages/test-fixtures/expected/**
  outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx
  tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md
  outputs/task-034-mvp-e2e-acceptance/entrypoint-audit.md
  docs/ARCHITECTURE.md
  decisions/ADR-015-evidence-slot-source-anchor-governance.md

⛔ 禁止写入：
  apps/api-server/src/main/**
  apps/api-server/build.gradle.kts
  apps/admin-web/**
  packages/test-fixtures/docx/**
  packages/test-fixtures/human-anchors/**
  packages/test-fixtures/expected/**
  packages/test-fixtures/cqcp-mvp-sample-matrix.xlsx
  outputs/task-data-001-anchor-template/**
  packages/api-contracts/**
  deploy/**
  .github/workflows/**
  decisions/**
  docs/ARCHITECTURE.md
  PRD.md
  数据库 migration、环境变量、secret、credential 路径
```

`apps/api-server/src/test/resources/task-034/**` 只允许新增最小 synthetic/self-test 资源；不得复制或改写正式 DOCX、人工 occurrence 或 expected 值。

---

## 1. 上下文注入

### 1.1 项目定位

CQCP 是合同质量控制中台 V2，生产主链路为真实合同解析、候选/证据准备、后端确定性审核、结果快照与结果 URL；平台只提供风险提示、证据和审核结果，不批准、不拒绝、不阻断审批流。

### 1.2 本任务位置

```text
真实 DOCX（冻结，只读）
  ↓
生产 parser / index / evidence build（只读调用）
  ↓
生产 TaskExecutionStateMachine / Review Engine / ResultComposer（只读调用）
  ↓
生产 InMemoryTaskResultStore / TaskResultQueryService（只读调用）
  ↓
【test-only TASK-034 harness：同 run 观察 + 63 occurrence 比较 + 输出】
  ↓
TASK-034 Phase 1（仅在实现接纳与独立复核后由 Codex 运行）
```

### 1.3 架构红线

| 红线 | 内容 |
|---|---|
| `RED-034-A-01` | 所有入口必须进入同一审核链路；不得用多个测试输出手工拼接冒充一次执行。 |
| `RED-034-A-02` | `SYS-*` 与业务 Finding 分流；系统失败不得伪装成合同风险。 |
| `RED-034-A-03` | Evidence 不足不得回灌全文或伪造业务结论。 |
| `RED-034-A-04` | 后端负责最终确定性裁判；test harness 只能观察和比较，不能改写 PointStatus。 |
| `RED-034-A-05` | human ground truth 独立于 parser/AI/actual；不得倒填 canonical key 或改 expected。 |

### 1.4 冻结契约

- 正式样本固定为 `CQCP-MVP-DOCX-001/002/003`，正式执行顺序固定为 001→002→003。
- occurrence 固定为 22/19/22，共 63；纳入 20/17/20，共 57；排除每份 2，共 6。
- 排除项输出只能为 `EXCLUDED`，不得影响名称一致性的 `PointStatus`。
- 纳入项输出只能为 `MATCHED`、`NOT_MATCHED` 或 `NOT_OBSERVABLE`。
- `candidateComparison` 只能为 `MATCH`、`MISMATCH`、`NOT_OBSERVABLE`。
- 每份样本使用唯一 taskId/executionId，查询必须返回同一对 ID。
- 结果查询证据固定为 `/api/v1/tasks/{taskId}/result` 的等价 service 查询；不得新增 HTTP endpoint。
- 正式输出 schema 以父任务“比较与判定口径”为准；harness 不得新增会改变业务语义的枚举。

### 1.5 术语

| 术语 | 定义 |
|---|---|
| 同一次 run | 一个样本从 `TaskExecutionRequest.forDocument` 开始，经当前状态机保存快照，并由同一 store 按相同 taskId 查询。 |
| test-only observer | 只位于测试源码、用于捕获同一次 run 内 parser/build 产物的观察机制；不得改变生产对象或执行顺序。 |
| occurrence bridge | 以人工文本/位置描述与同 run actual anchor/解析元素做显式映射；不回写人工 fixture。 |
| 正式模式 | 使用 001/002/003 并写出父任务 Phase 1 交付物的模式；默认必须禁用。 |

---

## 2. 接口契约

### 2.1 输入

```java
record HarnessRunInput(
    List<String> sampleIds,       // 正式模式只能为 001,002,003 且顺序固定
    Path fixtureRoot,             // packages/test-fixtures
    Path outputRoot,              // outputs/task-034-mvp-e2e-acceptance
    boolean formalMode            // 默认 false；Codex 接纳后才可 true
) {}
```

### 2.2 输出

```java
record HarnessRunResult(
    List<SampleAcceptanceResult> samples,
    List<OccurrenceComparisonRow> occurrences,
    int includedCount,
    int excludedCount
) {}
```

每个 sample result 必须包含：`sampleId`、`taskId`、`executionId`、查询路径、查询到的 snapshot 标识、每个启用审核点的 expected/actual candidate、candidateComparison、pointStatus、evidenceSummary、actual anchors、SYS diagnostics。

每个 occurrence row 必须包含父任务冻结的 10 个字段：`occurrenceNo`、`includedInConsistencyEvaluation`、`humanAnchorText`、`humanLocationDescription`、`humanAnchorGranularity`、`actualEvidenceText`、`actualAnchorGranularity`、`actualAnchorReference`、`coverageResult`、`notes`。

### 2.3 错误处理

| 情形 | 行为 |
|---|---|
| 正式模式未获显式 system property/参数 | 测试明确 `SKIPPED` 或 STOP，不生成正式输出。 |
| taskId/executionId 查询污染 | 立即失败，不生成 PASS。 |
| 无法从同一次 run 观察 candidate/anchor | 输出 `NOT_OBSERVABLE`；若整项不满足父任务，则正式验收 FAIL，不人工补值。 |
| 纳入/排除计数不是 57/6 或总数不是 63 | 立即失败并停止写出成功摘要。 |
| 需要修改生产类/契约 | `[STOP: 文件越界或架构空白 — 需要修改生产实现 — 交回 Codex]`。 |

### 2.4 禁止触碰的接口和模块

```text
❌ TaskExecutionStateMachine、ParserBackedReviewInputPreparer、MinimalReviewEngine、ResultComposer 的生产签名与行为
❌ ReviewResultSnapshot、PointReviewResult、PointEvidence、SourceAnchorSummary 的生产字段
❌ TaskResultQueryController / TaskResultQueryService 公共行为
❌ EvidenceOverlapEvaluator 既有 parser-backed baseline 语义
❌ human-anchor fixture、expected JSON、XLSX、DOCX
```

---

## 3. 行为规格

### 3.1 Happy Path

1. 正式模式关闭时，只运行 harness 自测，不写父任务 Phase 1 输出。
2. 正式模式开启时，按 001→002→003 加载真实 DOCX、structured fields 与 human fixture。
3. 每份样本创建唯一 taskId/executionId、独立 `InMemoryTaskResultStore` 和当前生产组件实例。
4. 通过 `TaskExecutionRequest.forDocument` 调用当前 `TaskExecutionStateMachine.execute`。
5. test-only observer 只能捕获同一次状态机 run 内实际 parse/build 产物；禁止第二次 parser 或 Review Engine 调用来补 candidate/anchor。
6. 通过同一 store 的 `TaskResultQueryService.getResult(taskId)` 查询，并断言查询 snapshot 与 run snapshot 的 taskId、executionId 和内容一致。
7. 从同 run 的 `ReviewEngineInput.pointEvidences` 读取 actual candidateValue，从查询 snapshot 读取 PointStatus、anchors、diagnostics。
8. 对每条人工 occurrence：先按 inclusion 冻结 6 条 `EXCLUDED`；纳入项按 reviewPointCode 关联 actual point anchors，再以 actual anchor 解析元素中的精确规范化文本和粒度做比较。不得以“同一 block/row/table”宽松替代 cell 命中。
9. 无法可靠建立 bridge 时输出 `NOT_OBSERVABLE`；有可靠 actual anchor 但人工文本/粒度不命中时输出 `NOT_MATCHED`。
10. 形成 3 个 sample result 与固定 63 行 comparison；正式写出由父任务 Phase 1 命令负责。

### 3.2 Edge Cases

| 场景 | 期望行为 |
|---|---|
| 同一审核点多个人工出处 | 逐 occurrence 输出，不因已有一个命中而省略其他出处。 |
| candidateValue 正确但 anchor 未命中 | candidate 可 `MATCH`，occurrence 必须 `NOT_MATCHED`/`NOT_OBSERVABLE`，不得整项伪装通过。 |
| actual anchor 是 cell，人工粒度是 BLOCK，或反向 | 按精确粒度规则判断，不自动升级/降级。 |
| parser anchor 缺 blockId 或 previewElementRef | 不能推测 row/cell，输出 `NOT_OBSERVABLE`。 |
| SYS diagnostic | 保存到 `sysDiagnostics`，不写入 findings。 |
| 6 条排除项 | 不查询 actual 覆盖来改变 PointStatus，只输出 `EXCLUDED`。 |

### 3.3 禁止操作

```text
❌ 不得修改 §0.3 允许范围之外的文件
❌ 不得新增依赖或修改 Gradle 配置
❌ 不得新增 API、DTO、生产字段、数据库、workflow 或 ADR
❌ 不得复制/修订/生成 human ground truth
❌ 不得从 actual 输出倒填人工位置或 expected
❌ 不得用第二次 parser/review run 补齐同 run 缺失字段
❌ 不得用字符串模糊包含、同表、同行或同 block 宽松规则伪造 TABLE_CELL 命中
❌ 不得在 Codex 接纳实现和独立复核前开启正式模式
❌ 不得 commit、push、merge
```

---

## 4. 架构空白处理

如必须修改生产 snapshot 才能观察 candidateValue，必须 STOP；本规格只授权 test-only observer。若现有测试依赖无法对 final/package-private 对象实施同 run 观察，也必须 STOP，由 Codex重新判断方案，不得修改生产类或增加反射型生产 hook。

不得新增领域模型、生产枚举、API 字段、数据库字段、抽象层、外部服务或模型调用。

---

## 5. 技术栈约束

| 能力 | 指定工具 | 说明 |
|---|---|---|
| 语言 | Java 21 | 与 api-server 当前 toolchain 一致。 |
| 测试 | JUnit 5、AssertJ、Spring Boot Test 现有依赖 | 不新增依赖。 |
| JSON | 现有 Jackson | 不引入新序列化库。 |
| DOCX | 当前 `DocxWordParserSpike` / Apache POI | 只读调用生产 parser。 |
| 存储/查询 | `InMemoryTaskResultStore` + `TaskResultQueryService` | 无数据库、无 HTTP server。 |

---

## 6. Git 工作区规则

```text
执行前：
  ✅ 当前分支必须为 codex/task-034-a-test-only-e2e-harness
  ✅ git status --short 必须为空
  ✅ 必须先提交编码前规格映射计划并等待 Codex GO

执行中：
  ❌ 不得 commit / push / merge
  ❌ 不得切换分支
  ❌ 不得 reset --hard / clean -fd / checkout .

完成后：
  ✅ 输出 git status --short、git diff --name-status、git diff --stat、git diff --check
  ✅ 列出实际修改路径和测试原始摘要
```

---

## 7. 版本化要求

- harness 输出 schema 固定标识 `task034-acceptance-v1`。
- run manifest 必须保存 commit、分支、Java/Gradle 版本和 snapshot 的全部 version references。
- 本规格不新增或修改生产版本字段。

---

## 8. 验收标准

### Must Pass

- [ ] 只新增 §0.3 允许的 test-only 文件，生产 tree、fixture、expected、DOCX、XLSX、matrix 均无 diff。
- [ ] 正式模式默认禁用；未显式开启时不会运行 001/002/003 或写出 Phase 1 产物。
- [ ] 自测证明一次 run 内真实调用状态机并由同一 store/query service 取回完全相同 taskId/executionId 的 snapshot。
- [ ] 同 run observer 不触发第二次 parser/review 调用，并能观察 actual candidateValue 与实际 parsed/anchor 数据；做不到则 STOP，不得降级实现。
- [ ] comparator 自测覆盖 `MATCHED`、`NOT_MATCHED`、`NOT_OBSERVABLE`、`EXCLUDED`，并拒绝以同 row/table/block 宽松替代 TABLE_CELL。
- [ ] 加载既有 human fixtures 时严格得到 63/57/6、22/19/22、20/17/20、2/2/2；不修改输入。
- [ ] 输出模型能为每个审核点表达 candidateComparison、PointStatus、evidenceSummary、anchors、SYS diagnostics 和查询路径。
- [ ] `SYS-*` 不进入业务 findings，排除 occurrence 不改变名称一致性 PointStatus。
- [ ] 既有四个定向测试保持 27/27，0 failures，0 errors。

### 同根因分批修复一致性

- [ ] 与 `TASK_SPEC-DATA-001-A` 一致点：人工来源独立、63/57/6 不变、fixture/expected 不倒填。
- [ ] 差异点：本规格只增加 actual 执行与比较 harness，不创建或修订 ground truth。

### 评测 / fixture / expected 来源

- [ ] expected candidate 与人工 occurrence 继续只读来自已接受 XLSX/human fixture；不得依赖本 harness actual。
- [ ] parser-backed `expectedCanonicalAnchors[]` 只能用于既有回归，不得替代 human occurrence bridge。

### Must Not

- [ ] ❌ 修改生产代码、现有测试、Gradle 配置、fixture、expected、DOCX、XLSX、matrix、API、数据库、workflow 或 ADR。
- [ ] ❌ 在实现接纳和独立复核前运行正式模式。
- [ ] ❌ 通过第二次执行、手工拼接、actual 倒填或宽松匹配伪造完整链路。
- [ ] ❌ commit、push、merge。

---

## 9. 测试与验证命令

实现阶段必须运行，但不得开启正式模式：

```powershell
Set-Location apps/api-server
gradle test --tests "com.cqcp.apiserver.reviewengine.Task034MvpE2eAcceptanceHarnessTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.HumanAnchorGroundTruthFixtureTest" --tests "com.cqcp.apiserver.reviewengine.ParserBackedEvidenceOverlapBaselineTest" --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest" --tests "com.cqcp.apiserver.reviewengine.TaskResultQueryControllerTest"
Set-Location ../..
git diff --check
git status --short
git diff --name-status
git diff --stat
```

禁止运行：正式模式命令、全部 001/002/003 Phase 1、数据库 migration、Docker Compose、外部 API/模型、依赖安装、网络命令。

测试失败必须区分代码失败与环境失败；环境失败输出 STOP，不得安装依赖规避。

---

## DeepSeek 执行指令块

```text
你正在 Claude Code 中执行 TASK_SPEC-034-A，推理模型为 DeepSeek。

第一步只读：完整读取 AGENTS.md、CURRENT_CONTEXT.md、docs/ARCHITECTURE.md、
decisions/ADR-015-evidence-slot-source-anchor-governance.md、
tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md、
tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md、
outputs/task-034-mvp-e2e-acceptance/entrypoint-audit.md。

第二步执行 git branch --show-current 和 git status --short。
分支不是 codex/task-034-a-test-only-e2e-harness 或工作区不干净时立即 STOP。

第三步只输出 §0.2 编码前规格映射计划并停止。Codex 未明确回复 GO 前不得修改文件。

获得 GO 后，只可修改 §0.3 两个 test-only 路径。遇到需要修改生产类、现有 fixture/expected、
新增依赖、第二次 parser/review 补数据、宽松匹配或正式运行 001/002/003 时立即 STOP。

完成后输出：分支、git status --short、git diff --name-status、git diff --stat、git diff --check、
测试命令与原始结果摘要、假设、歧义、STOP 记录、对 §8 的逐条自检。不得 commit、push、merge。
```

---

## 10. 实现报告

Claude Code / DeepSeek 的编码前规格映射计划经 Codex `GO / IMPLEMENTATION AUTHORIZED` 后完成实现。实际修改仅为 `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/Task034MvpE2EAcceptanceHarnessTest.java`。

实现提交为 `99bea3a6a3ce0cbecf337e76692aac3a6c428228`；正式首次写 manifest 暴露 Java Time module 未注册后，执行同一规格下的最小序列化修复，提交为 `46a625a5eb5aee8ff5a31f86bb7300fb2d8e703a`。修复只增加 Jackson module 自动注册及非正式 manifest round-trip 自测。

最终验证：harness 13/13，既有四类定向回归 27/27。未修改生产 tree、既有 fixture / expected、DOCX、XLSX、matrix、API、数据库、workflow 或 ADR。

---

## 11. CODEX 审查记录

当前结论：`ACCEPT / IMPLEMENTATION AND SERIALIZATION FIX REVIEWED / INDEPENDENT READ-ONLY AUDIT ACCEPT`。

Codex 已审查同 run 对象身份、阶段顺序、formal 双门禁、63/57/6、candidate 来源、occurrence 一对一映射、SYS/Finding 分流、输出完整性和失败后留证行为。独立 agent 对最终实现与序列化修复均给出 `ACCEPT`，允许父任务进入 Phase 1。正式运行最终结果为 `FAIL`，属于父任务验收结论，不回滚本 test-only harness 的实现接纳。

---

## 12. 后续任务联动

| 后续任务 | 依赖 | 状态 |
|---|---|---|
| `TASK-034` Phase 1 | 本规格实现获 Codex 接纳，且独立只读复核无 blocking finding | 已执行，结果 `FAIL` |
| `TASK-028` / `TASK-031` / `TASK-032` | 不由本规格解锁 | 🔒 |

---

## 附录：引用

| 文档 | 关键内容 |
|---|---|
| `AGENTS.md` | 角色分离、证据门禁、expected 独立性 |
| `docs/ARCHITECTURE.md` | 同一审核链路、结果快照、SYS/Finding 分流、SourceAnchor |
| `TASK-034` | Phase 0 STOP、63 occurrence 和正式输出口径 |
| `TASK-DATA-001` | 人工 ground truth 独立来源和 63/57/6 |
| `entrypoint-audit.md` | 现有入口缺口与 NO-GO 事实依据 |
