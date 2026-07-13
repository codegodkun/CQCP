# TASK-034：MVP E2E 人工 anchor 正式验收执行

状态：待开始 / Phase 0 执行入口门禁未完成

类型：Codex 主控正式验收任务

优先级：P0

负责人：Codex

创建日期：2026-07-13

来源：`TASK-033`、`TASK-DATA-001`、用户确认的 PR #28 合并与下一阶段计划

建档独立只读复核：`GO` / 无 blocking findings

## 背景

`TASK-033` 已冻结 MVP 端到端样本验收规格，`TASK-DATA-001` 已完成 3 份真实 DOCX 的人工 anchor 准备、63 条人工 ground truth 接受、fixture / expected JSON 引用和定向测试转换，并已通过 PR #28、PR #29、PR #30 完成实现合并、状态写回和父任务归档。

当前仓库能够分别证明真实 DOCX parser、parser-backed 审核状态机和结果查询契约存在，但这些分立入口不自动证明已有一个可直接执行的正式入口，能够把 3 份真实 DOCX 串行送入 parser、审核链路、结果快照与结果查询，并产出可与人工 occurrence 对比的证据。本任务首先执行只读入口审计；只有入口门禁为 `GO`，才运行正式验收。

## 目标

* 冻结并执行 `CQCP-MVP-DOCX-001` / `002` / `003` 的真实 DOCX MVP 链路验收。
* 以 63 条 `ACCEPTED_HUMAN_GROUND_TRUTH` occurrence 为独立人工 anchor 基线，其中 57 条纳入一致性判断，6 条合同标题或前言名称只保留追溯、不参与名称一致性裁判。
* 对每份样本保存 `PointStatus`、`candidateValue`、证据摘要、anchor 粒度、人工原文定位、结果 URL 或 `SYS-*` 诊断。
* 明确区分链路通过、业务不一致、证据未命中、系统诊断和环境阻塞，不把任何一种结果静默改写为通过。
* 形成只针对本次 3 份真实 DOCX 和当前版本链路的可复核结论。

## 非目标

* 不修改 DOCX、人工 XLSX、`cqcp-mvp-sample-matrix.xlsx`、human-anchor fixture 或 `expected/*.json`。
* 不从 parser、AI、测试或被测系统 actual 输出倒填人工 ground truth。
* 不修改生产 parser、`CandidateResolver`、`EvidenceSlot`、`SourceAnchor`、Review Engine 或结果契约。
* 不新增或修改公共 API、数据库、workflow、ADR 或生产数据结构。
* 不归档 `TASK-EVAL-001`，不补足其 DoD #12。
* 不进入或解除 `TASK-028` / `TASK-031` / `TASK-032` 门禁。
* 不把本次验收解释为全部合同类型、全部审核点、生产环境或模型能力的普遍正确性证明。

## 输入

* 上游规格：`tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`。
* 人工数据父任务：`tasks/done/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md`。
* 转换规格：`tasks/done/TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md`。
* 人工源工作簿：`outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx`，sheet `anchor明细待确认`。
* 人工 fixture：`packages/test-fixtures/human-anchors/CQCP-MVP-DOCX-00{1,2,3}.json`。
* parser-backed expected 引用：`packages/test-fixtures/expected/CQCP-MVP-DOCX-00{1,2,3}.json`。
* 验收规则入口：`docs/VERIFY.md`、`packages/test-fixtures/README.md`。
* 当前架构：`docs/ARCHITECTURE.md`。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`
* `tasks/done/TASK-DATA-001-mvp-e2e-human-anchor-preparation.md`
* `docs/VERIFY.md`
* `packages/test-fixtures/README.md`

### Optional Context

* `tasks/done/TASK_SPEC-DATA-001-A-human-anchor-fixture-expected-test-conversion.md`
* `docs/ARCHITECTURE.md`
* `docs/backend.md`
* `docs/context-management.md`
* 与 Phase 0 发现的真实执行入口直接相关的生产代码和测试

### Out of Scope

* 与 001/002/003 三份样本无关的样本扩充或数据修订
* Gemma / A30 Provider 接入或公网模型调用
* parser provenance、runtime loader、管理台 mapper 补洞或 ReviewPointFamily 重构
* `TASK-028`、`TASK-031`、`TASK-032` 的任何实现或规格派发

## 冻结样本

| sampleId | DOCX 路径 | 合同类型 / 用途 | 人工 occurrence | 纳入 / 排除 | data owner / 合规 |
|---|---|---|---:|---:|---|
| `CQCP-MVP-DOCX-001` | `packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx` | 工程采购正向样本 | 22 | 20 / 2 | `ZK` / 已确认可用且脱敏合规 |
| `CQCP-MVP-DOCX-002` | `packages/test-fixtures/docx/2、达利安造船厂四方城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx` | 产品采购表达差异 | 19 | 17 / 2 | `ZK` / 已确认可用且脱敏合规 |
| `CQCP-MVP-DOCX-003` | `packages/test-fixtures/docx/3、星辰建设集团南山科技园项目二标段土建总承包工程合同_缩减版.docx` | 工程采购表达差异 | 22 | 20 / 2 | `ZK` / 已确认可用且脱敏合规 |
| **合计** |  |  | **63** | **57 / 6** |  |

冻结规则：

1. 63 条 occurrence 的唯一人工来源是已接受 XLSX 及其无损转换 fixture。
2. 57 条 `includedInConsistencyEvaluation = true` 的 occurrence 必须逐条进入覆盖统计，不得按 review point 聚合后静默丢失出处。
3. 6 条排除 occurrence 必须记录为 `EXCLUDED`，不得影响甲乙方名称一致性的 `PointStatus`。
4. human-anchor fixture 不含 parser canonical key；人工位置到 actual anchor 的映射必须作为验收结果显式记录，不能反向修改人工 fixture。
5. parser-backed `expectedCanonicalAnchors[]` 只能证明既有回归一致性，不等同于人工 ground truth。

## Phase 0：执行入口门禁

正式验收前，Codex 必须只读确认当前仓库是否存在一个无需修改生产代码即可执行的入口，并逐项给出文件、方法、命令或运行证据：

1. 输入是真实仓库 DOCX 路径，而不是预先构造的 parser output、`ReviewEngineInput` 或 actual JSON。
2. 真实调用当前 DOCX parser，并保留 block / table row / table cell 可观测定位。
3. 经过当前审核链路与任务状态机，形成 `ReviewResultSnapshot` 或等价当前结果对象。
4. 能通过当前结果查询入口读取同一 task 的结果；如使用 HTTP，记录 `/api/v1/tasks/{taskId}/result` 对应 URL。
5. 输出足以记录每个审核点的 `PointStatus`、`candidateValue`、证据摘要、anchor 定位和 `SYS-*` 诊断。
6. 能把 actual evidence 与 57 条纳入 occurrence 逐条对比，并把 6 条排除 occurrence 保持为 `EXCLUDED`。

入口门禁结论只能是：

* `GO_EXISTING_ENTRYPOINT`：六项均有直接证据，可进入 Phase 1。
* `NO_GO_TEST_ONLY_HARNESS_REQUIRED`：任一项缺失；立即停止正式验收，不得用手工拼接多个测试的输出冒充完整链路。
* `BLOCKED_ENVIRONMENT`：入口存在，但执行依赖的本地环境不可用；保存原始诊断，不修改 expected 或生产代码规避。

若结论为 `NO_GO_TEST_ONLY_HARNESS_REQUIRED`，本任务只允许由 Codex 冻结 `TASK_SPEC-034-A`：

* 仅允许新增 test-only E2E harness 及其直接测试资源。
* 必须先由 Claude Code / DeepSeek 提交编码前规格映射计划并经 Codex 明确 `GO`。
* 禁止修改生产 parser、`CandidateResolver`、`EvidenceSlot`、`SourceAnchor`、Review Engine、公共 API、数据库、workflow 或生产数据结构。
* harness 不得生成、修订或倒填人工 ground truth。
* `TASK_SPEC-034-A` 落地并经独立复核前，本任务保持 active，不运行正式验收。

## Phase 1：正式执行

仅当 Phase 0 为 `GO_EXISTING_ENTRYPOINT`，或后续 `TASK_SPEC-034-A` 已按门禁落地并通过 Codex Review Intake 后执行：

1. 执行前记录 commit、分支、`git status --short`、Java / Node 环境和实际命令。
2. 按 001、002、003 固定顺序运行；每份样本使用独立 taskId，禁止复用上一次快照。
3. 保存 parser、任务状态机、结果快照和结果查询的关键阶段证据。
4. 对每个 review point 保存 actual 字段，并与人工 expected 值比较。
5. 对 57 条纳入 occurrence 逐条记录 actual anchor 覆盖结果；对 6 条排除 occurrence 记录 `EXCLUDED`。
6. 保存负向或冲突验证时，只能使用仓库中已冻结的 negative / conflict 数据；不得临时制造 expected。
7. 执行后再次记录 `git status --short`，证明验收未改动受保护数据与实现文件。

## 比较与判定口径

### review point 级

每个样本的每个启用审核点至少记录：

| 字段 | 说明 |
|---|---|
| `sampleId` / `taskId` | 稳定样本与本次执行标识 |
| `reviewPointCode` | 冻结审核点代码 |
| `expectedCandidateValue` | 来自人工 ground truth，不从 actual 推断 |
| `actualCandidateValue` | 被测链路真实输出；空值也必须记录 |
| `candidateComparison` | `MATCH` / `MISMATCH` / `NOT_OBSERVABLE` |
| `pointStatus` | 当前链路真实 `PointStatus` |
| `evidenceSummary` | 当前结果中的证据摘要 |
| `sysDiagnostics` | `SYS-*` 列表；无则为空数组 |
| `resultUrl` | 可查询 URL；若入口不提供 HTTP，记录明确的等价查询证据 |

### occurrence 级

63 条 occurrence 均必须有一行结果：

| 字段 | 说明 |
|---|---|
| `occurrenceNo` | 人工 fixture 中的稳定编号 |
| `includedInConsistencyEvaluation` | 原样保留人工布尔值 |
| `humanAnchorText` / `humanLocationDescription` | 人工原文和页码 / 位置描述 |
| `humanAnchorGranularity` | 人工 `BLOCK` / `TABLE_CELL` 粒度 |
| `actualEvidenceText` | actual 证据原文；未输出时为空 |
| `actualAnchorGranularity` | actual 粒度；不可观察时为 `UNAVAILABLE` |
| `actualAnchorReference` | actual block / row / cell 定位；不得倒填回人工 fixture |
| `coverageResult` | `MATCHED` / `NOT_MATCHED` / `NOT_OBSERVABLE` / `EXCLUDED` |
| `notes` | 差异、降级、冲突或诊断说明 |

硬性判定：

* 排除项只有 `coverageResult = EXCLUDED` 才合规，且不得改变名称一致性结果。
* 纳入项不得标为 `EXCLUDED`，也不得因同一 review point 已有一个命中而省略其余 occurrence。
* `candidateValue` 正确但 anchor 未命中时，必须判为证据覆盖失败，不能判整项通过。
* actual 无法提供所需字段时使用 `NOT_OBSERVABLE`，不得推测或人工补值。
* `SYS-*` 必须与业务 Finding 分流；系统失败不得伪装为合同风险。

### 验收总结果

最终结论只能使用：

* `PASS`：链路完整执行，所有冻结断言均满足。
* `FAIL`：链路完整执行，但存在 candidate、status、anchor 覆盖或排除语义不符合。
* `BLOCKED_ENVIRONMENT`：已证明入口存在，但环境阻止执行。
* `STOPPED_FOR_TASK_SPEC_034_A`：现有入口不足，已按 Phase 0 停止。

不得使用“基本通过”“大体一致”替代逐项结果。

## 交付物

* 本任务文件的 Phase 0 / Phase 1 执行记录和最终判定。
* `outputs/task-034-mvp-e2e-acceptance/entrypoint-audit.md`。
* `outputs/task-034-mvp-e2e-acceptance/run-manifest.json`。
* `outputs/task-034-mvp-e2e-acceptance/sample-results/CQCP-MVP-DOCX-00{1,2,3}.json`。
* `outputs/task-034-mvp-e2e-acceptance/occurrence-comparison.csv`，固定 63 条数据行。
* `outputs/task-034-mvp-e2e-acceptance/console-summary.md`。
* 如 Phase 0 不通过：单独冻结的 `tasks/active/TASK_SPEC-034-A-*.md`，不提前创建实现。

输出目录只保存验收证据，不得复制完整合同文本或新增 ground truth；原文仅保存完成比较所需的最小片段。

## 验收标准

1. Phase 0 对六项入口能力逐项给出直接证据和唯一门禁结论。
2. 入口不足时正式验收确实停止，且没有修改任何生产实现或人工数据。
3. 实际运行样本严格为 001/002/003，数量为 3。
4. occurrence 比较结果严格为 63 条，样本计数为 22/19/22。
5. 纳入与排除计数严格为 57/6，6 条排除项不参与名称一致性裁判。
6. 每个启用审核点均有 expected / actual candidate、`PointStatus`、证据摘要、定位、URL 或诊断。
7. 57 条纳入 occurrence 均有覆盖结果，不存在静默遗漏；6 条排除 occurrence 均为 `EXCLUDED`。
8. 人工 expected 来源可追溯至已接受 XLSX / human-anchor fixture，未使用 parser、AI 或 actual 倒填。
9. 负向或冲突验证只使用既有冻结数据。
10. `SYS-*` 与业务 Finding 分流。
11. 基础测试与前端测试 / 构建按本任务命令通过，或保留可复核的环境失败证据。
12. 执行前后工作区没有 DOCX、XLSX、matrix、fixture、expected、生产代码或测试的非授权修改。
13. 最终结论不宣称解除 `TASK-EVAL-001`、`TASK-028`、`TASK-031` 或 `TASK-032` 门禁。

## 测试与验证

Phase 0 必须先确认以下 class / script 仍存在，再运行命令：

```powershell
Set-Location apps/api-server
gradle test --tests "com.cqcp.apiserver.reviewengine.HumanAnchorGroundTruthFixtureTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedEvidenceOverlapBaselineTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.TaskResultQueryControllerTest"

Set-Location ../..
npm.cmd run test:admin-web
npm.cmd run build:admin-web
```

正式验收命令必须在 Phase 0 中记录，不能只用上述定向测试替代真实 DOCX 完整链路执行。

收口检查：

```powershell
git status --short
git diff --name-status
git diff --stat
git diff --check
```

## 文档更新要求

* `CURRENT_CONTEXT.md`：记录 TASK-034 状态、门禁结论和最终结果。
* `tasks/MVP_TASK_MAP.md`：记录任务顺序、依赖和不解除 028/031/032 的边界。
* `changelog/2026-07.md`：记录建档、Phase 0 结论和正式执行结果。
* `docs/*.md`：当前建档不修改；只有发现长期验收规则缺口时另行判断。
* ADR：当前建档不需要。若后续方案改变核心审核链路、结果契约或 EvidenceSlot / CandidateResolver / SourceAnchor 边界，必须停止并先记录 ADR。

## Next Task Handoff

本任务建档后可在新 Codex 窗口直接从 Phase 0 执行入口门禁开始。若 Phase 0 为 `NO_GO_TEST_ONLY_HARNESS_REQUIRED`，下一步不是继续正式验收，而是由 Codex 单独冻结 `TASK_SPEC-034-A` 并走编码前规格映射计划门禁。

## 风险

* 分立测试均通过不代表真实 DOCX、状态机和结果查询已被同一次执行串联。
* human anchor 是自然语言位置描述，actual canonical anchor 是结构化 key；映射不足会产生 `NOT_OBSERVABLE`，不得通过宽松文本包含关系伪造命中。
* 单个候选值正确不代表全文所有一致性出处都被覆盖。
* 结果查询若读取的不是本次 task 快照，会造成跨执行污染。
* 完整合同原文不应复制到验收输出，避免扩大数据暴露面。

## 待确认

* 待 Phase 0 确认：现有仓库是否已有满足六项条件的单一执行入口。
* 待 Phase 0 确认：当前结果对象能否直接导出 63 条 occurrence 级比较所需的 actual anchor；不能时必须 STOP 并冻结 `TASK_SPEC-034-A`。

## 完成记录

* 完成日期：待填写。
* 建档独立只读复核：2026-07-13，Decision 为 `GO`，无 blocking findings；复核未修改、stage、commit 或 push 任何文件。
* Phase 0 结论：待填写。
* 实际执行入口 / 命令：待填写。
* 变更文件：待填写。
* 测试结果：待填写。
* 样本结果：待填写。
* occurrence 统计：待填写。
* 最终判定：待填写。
* 遗留问题：待填写。
