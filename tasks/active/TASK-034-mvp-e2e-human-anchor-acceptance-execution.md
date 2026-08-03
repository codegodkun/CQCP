# TASK-034：MVP E2E 人工 anchor 正式验收执行

状态：Active / Final Core R7 `PASS` and Sealed / Core subject `90c4ae9a…`
三审全零 GO / PR #37 merged as `115be530…` / Provider 路径终态 BLOCKED /
Milestone 仅待终态证据保存与收口，不存在成功路径 Final Audit

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

### Phase 0 执行记录（2026-07-14）

唯一门禁结论：`NO_GO_TEST_ONLY_HARNESS_REQUIRED`。

Phase 0 当时的父任务结果：`STOPPED_FOR_TASK_SPEC_034_A`；该阶段未进入 Phase 1，未运行 001/002/003 正式 MVP E2E。后续状态见 Phase 1 执行记录。

| # | 能力 | 直接证据 | 结论 |
|---|---|---|---|
| 1 | 真实 DOCX 输入 | `TaskExecutionStateMachineTest` 的 parser-backed 路径使用 expected JSON 的 `sourceDocx` 构造 `TaskExecutionDocumentReference`，真实调用状态机。 | 组件存在，但没有正式单一验收入口。 |
| 2 | parser 与 block/row/cell 定位 | `TaskExecutionStateMachine.runPreparationStages` 依次执行 `parse → index → plan → build`；`SourceAnchorSummary` 可携带 `blockId`、`locationLevel`、`previewElementRef`。 | 组件证据通过。 |
| 3 | 状态机与结果快照 | `execute` 经 `REVIEWING_RULES`、`COMPOSING` 保存 `ReviewResultSnapshot`；定向测试通过。 | 组件证据通过。 |
| 4 | 同 task 结果查询 | `GET /api/v1/tasks/{taskId}/result` 存在；controller 测试 mock service，service 串联测试使用预构造 `ReviewEngineInput`，没有真实 DOCX；生产代码没有提交/执行任务入口。 | 缺失。 |
| 5 | PointStatus/candidate/证据/anchor/URL/SYS | snapshot 可见 PointStatus、证据摘要、anchor、diagnostics；actual `candidateValue` 只存在于内部 `PointEvidence`，未进入 `PointReviewResult` 或 snapshot/query 输出。 | 缺失。 |
| 6 | 63 条 occurrence 显式比较 | 现有测试只验证 XLSX→fixture 的 63/57/6 契约；human fixture 不含 parser canonical key，人工位置→actual anchor bridge 不存在。 | 缺失。 |

上述任一缺失即不得判为 `GO_EXISTING_ENTRYPOINT`。不能把真实 DOCX 状态机测试、预构造 input 查询测试、mock controller 测试和 parser-backed canonical overlap 手工拼接为一次完整执行。

验证证据：

* 后端四类定向测试合并复跑：27 tests / 0 failures / 0 errors / 0 skipped，其中 human fixture 10、parser-backed overlap 4、状态机 10、结果查询 controller 3。
* 环境版本：OpenJDK 21.0.11、Gradle 8.10.2、Node 24.16.0、npm 11.13.0。
* `npm.cmd run test:admin-web` 环境失败：`vitest` 不可识别；`npm.cmd run build:admin-web` 环境失败：`tsc` 不可识别。未安装依赖或修改环境规避。
* 前端依赖缺失不是本次唯一门禁结论的依据；入口在代码契约层已不满足六项，因此结论不是 `BLOCKED_ENVIRONMENT`。
* 完整证据：`outputs/task-034-mvp-e2e-acceptance/entrypoint-audit.md`。

已冻结并完成 `tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md`。Claude Code / DeepSeek 的编码前规格映射计划经 Codex 放行后完成实现；Codex Review Intake 与独立只读复核均接纳，随后由 Codex 进入 Phase 1。

## Phase 1：正式执行

仅当 Phase 0 为 `GO_EXISTING_ENTRYPOINT`，或后续 `TASK_SPEC-034-A` 已按门禁落地并通过 Codex Review Intake 后执行：

1. 执行前记录 commit、分支、`git status --short`、Java / Node 环境和实际命令。
2. 按 001、002、003 固定顺序运行；每份样本使用独立 taskId，禁止复用上一次快照。
3. 保存 parser、任务状态机、结果快照和结果查询的关键阶段证据。
4. 对每个 review point 保存 actual 字段，并与人工 expected 值比较。
5. 对 57 条纳入 occurrence 逐条记录 actual anchor 覆盖结果；对 6 条排除 occurrence 记录 `EXCLUDED`。
6. 保存负向或冲突验证时，只能使用仓库中已冻结的 negative / conflict 数据；不得临时制造 expected。
7. 执行后再次记录 `git status --short`，证明验收未改动受保护数据与实现文件。

### Phase 1 执行记录（2026-07-14）

* test-only harness 实现提交：`99bea3a6a3ce0cbecf337e76692aac3a6c428228`；manifest `Instant` 序列化最小修复提交：`46a625a5eb5aee8ff5a31f86bb7300fb2d8e703a`。
* harness 实现与序列化修复均经 Codex diff / 测试审查和独立 agent 只读复核接纳；未修改生产 parser、`CandidateResolver`、`EvidenceSlot`、`SourceAnchor`、Review Engine、公共 API、数据库、workflow、ADR、DOCX、XLSX、matrix、fixture 或 expected JSON。
* 正式运行使用分支 `codex/task-034-a-test-only-e2e-harness`、commit `46a625a5eb5aee8ff5a31f86bb7300fb2d8e703a`、OpenJDK 21.0.11、Gradle 8.10.2，并通过双 system property 门禁开启 formal mode。
* 实际入口为 test-only JUnit 方法 `formalAcceptanceEntryPointIsPropertyAndInputGated`；命令使用 `--rerun-tasks --tests "*.formalAcceptanceEntryPointIsPropertyAndInputGated"`。测试先写出完整验收证据，再因总结果 `FAIL` 触发 JUnit 断言失败；这属于验收失败，不是环境阻塞。
* 首次正式尝试在写 manifest 时因 Jackson 未注册 Java Time module 失败，只留下截断文件；该文件已删除，最小修复增加 `findAndRegisterModules()` 与非正式 round-trip 自测后重新从干净输出目录执行。最终交付物均来自修复后的单次正式运行。
* 3 个样本均完成 parser → index → plan → build → review → compose → 同 store 查询；每份样本使用唯一 taskId / executionId，snapshot 均为 `SUCCESS`。
* review point 共 27 项，真实 `PointStatus` 全部为 `PASS`；candidate comparison 为 9 `MATCH`、18 `MISMATCH`。名称与总金额 3 类点在每个样本均命中；税额复合 expected 与 actual 标量、以及 5 类百分比 expected 含 `%` 而 actual 为无 `%` 标量，按冻结的 strip-only 规则判为不一致。
* occurrence 严格输出 63 行：57 条纳入全部为 `NOT_OBSERVABLE`，6 条排除全部为 `EXCLUDED`；样本计数为 22/19/22、纳入为 20/17/20、排除均为 2。actual point anchors 存在，但多数 block anchor 缺 `previewElementRef`，表格名称 anchor 仅有 row 定位而无 cell index，无法建立冻结规则要求的精确 occurrence bridge，未使用宽松匹配伪造命中。
* 三份结果均无业务 Finding、无 `SYS-*` / query diagnostic；`SYS-*` 与 Finding 分流要求得到保持。没有系统失败可替代上述 candidate / anchor 验收失败。
* 精确提交检查中，`occurrence-comparison.csv` 的两处多行 `actualEvidenceText` 保留了真实 parser 文本末尾空格，导致 `git diff --check` 仅对该数据工件报告 2 条 trailing whitespace；未手工清洗或改写正式证据。排除该 CSV 后其余 staged 文档与 JSON diff check 通过。
* 最终判定：`FAIL`。父任务保持 active；不得据此进入 `TASK-028` / `TASK-031` / `TASK-032`。

### C2 / bridge v2 正式 R6 执行记录（2026-07-29）

* 当前 worktree 已接入 `TASK-036 C2`：execution 冻结
  `ruleSetVersion=v20260715.1` 时进入三参数 consistency-set build；普通
  `MVP_DEMO_MOCK / v20260705.1` binding 未改变。
* `TASK_SPEC-034-B` 仅在 test-only harness 增加单调双射 occurrence bridge；
  不修改人工 fixture、生产 SourceAnchor、CandidateResolver 或裁判。
* 临时 R5 曾得到 27/27 candidate `MATCH`、27/27 PointStatus `PASS` 和
  57 个 anchors；复核发现它把 `paymentMethod` 带入 C1 full scan，违反冻结的
  payment-method-neutral 契约。R5 已明确失效，不能作为门禁证据。
* 恢复 C1 冻结语义后的正式 R6 使用
  `1035739b751386176e47c6871738a62bff86de02+WORKTREE-C2-R6` 标识，输出目录为
  `outputs/task-034-mvp-e2e-acceptance-v2/`。
* R6 三份样本均完成 parser → state machine → v15 runtime build → review →
  snapshot → exact query；snapshot 均为 `PARTIAL_SUCCESS`。
* 27 个审核点中 18 个 candidate `MATCH` / PointStatus `PASS`，9 个 candidate
  `NOT_OBSERVABLE` / PointStatus `NOT_CONCLUDED`。9 个未结论点均为 payment
  ratio role conflict，输出 `SYS_ROLE_CONFLICT`，没有业务 Finding。
* production result 共提供 49 个可靠 SourceAnchor，无法与 57 条纳入人工
  occurrence 形成逐组同基数、同粒度单调双射。首轮 comparator 误用 global
  shape flag，导致一个 group 缺口污染全部 57 条；修复为逐 group fail-closed
  后的 R6B 结果为 47 `MATCHED` + 10 `NOT_OBSERVABLE` + 6 `EXCLUDED`。
* C1/C2 相关 10 个测试类联合复跑 413/413 通过；formal 方法先写出完整证据，
  后按设计因 `overallVerdict=FAIL` 返回非零。
* R6 最终判定：`FAIL`。当前缺口是 active/inactive payment branch 的版本化归属
  语义，不得通过恢复 v15 的 `paymentMethod` 过滤、修改人工 expected 或放宽
  occurrence bridge 伪造通过。

### D1 / v29 正式 R7 执行记录（2026-07-29）

* `TASK_SPEC-036-D1` 新增不可变 `v20260729.1` assets、版本化 inactive payment
  branch classifier 与 point-local ratio grammar；没有原地修改 v15，也没有读取
  structured `paymentMethod`、sampleId、文件名、fixture 或人工 expected 决定 scope。
* 正式入口通过 `JAVA_TOOL_OPTIONS` 把双 property 与 commit/branch/Gradle metadata
  注入测试 JVM；原始 console、JUnit XML 和 R7 工件由
  `r7-evidence-seal.json` 绑定。仅向 Gradle 进程传 `-D`、导致测试 JVM走
  SKIPPED 分支的尝试不计为正式复跑证据。
  seal 中的 `command` 是跨 shell 的规范化 provenance 字符串，不是 Windows
  host 的复制执行文本；实际 host 外层为 PowerShell 7，等价执行方式是先设置
  `$env:JAVA_TOOL_OPTIONS`，再调用 `gradle ...`，并在 `finally` 中删除该环境变量。
* R7 三份样本均为 `SUCCESS`，27/27 candidate `MATCH`、27/27
  `PointStatus=PASS`、20/17/20 共 57 个可靠 SourceAnchor；occurrence 为
  57 `MATCHED` + 6 human ground-truth `EXCLUDED`，0 `SYS-*`、0 Finding，
  `overallVerdict=PASS`。
* 第九轮审计发现原 harness 的结果读取仍是 `getResult(taskId)` latest 语义，不能
  证明正式 URL 的 `executionId` 身份。R8 证据重封在每个正式 execution 后写入
  `executionId + "-latest-decoy"` 的更新 snapshot；latest 查询必须命中 decoy，
  `getResult(taskId, executionId)` 必须命中原 snapshot，manifest/sample 均记录
  `queryMode=EXACT_EXECUTION_ID` 与 `exactExecutionIsolationVerified=true`。
* production branch exclusion 与人工 exclusion 分账：`production-branch-scope-ledger.json`
  有 70 条 `EXCLUDED/SEMANTIC_EXCLUDED`，分别来自 001/002/003 的 4/6/4
  inactive blocks × 5 ratio points；不得把这 70 条写成人工 exclusion。
* 既有 10 类回归 `413/0/0/0`、D1 `30/0/0/0`、合并
  `443/0/0/0`；R7 formal XML `1/0/0/0`。R8 evidence seal SHA-256 为
  `05593a35090477a31bde3e43309342f560c87157e007fb60515e55d9c8471579`，
  run manifest SHA-256 为
  `55702828c1d1b2fbc9b4817e926b58438978c987d592cb225968941a1c2ada83`。
  最终统一验证已通过，当前 subject 的
  新冻结与三审仍待完成，因此父任务保持 active。
* R7 只证明冻结的三份脱敏样本与 v29 契约通过，不构成 Production Ready，不放行
  DeepSeek Provider，也不解锁 `TASK-028 / TASK-031 / TASK-032`。

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

当前明确下一执行单元为 `MILESTONE-MVP-002` 收口：完成 D1/D2 与 Track B 项目记忆
写回、统一验证、重新冻结，并让 CC AUDIT 与两个全新 Codex subagent 对完全相同的
hash 从零审计。R7 已执行，不再等待 TASK-035/TASK-036 实现授权；未单独授权
commit、push、PR 或 merge。

## 风险

* 正式运行已证明同一次执行串联成立，但不代表 candidate 表示与人工 expected 的比较契约一致。
* human anchor 是自然语言位置描述，actual canonical anchor 缺少精确 block/cell reference；57 条纳入项因此全部 `NOT_OBSERVABLE`，不得通过宽松文本包含关系伪造命中。
* 27 个 `PointStatus=PASS` 只反映当前生产裁判，不等于 27 个 candidate comparison 或 57 条 occurrence coverage 通过。
* 完整合同原文不应复制到验收输出，避免扩大数据暴露面。

## 待确认

* 已确认：`TASK_SPEC-034-A` test-only harness 能在不修改生产类、不二次执行 parser/review、不宽松匹配的条件下完成同 run 观察与正式输出。
* 已确认：人工 expected 与 actual candidate 的表示契约存在 18/27 不一致；不得通过修改 expected 或放宽 strip-only 比较消除失败。
* 已确认：当前 actual anchor 精度不足以建立 57 条纳入 occurrence 的可靠 bridge，结果为 57/57 `NOT_OBSERVABLE`。
* 已确认：candidate 表示差异由 `TASK-035` 以 test-only、版本化 typed projection
  重基线，不修改生产 `CandidateResolver`，不需要 ADR；`TASK_SPEC-035-A` 已实现并
  随 PR #32 合并。
* 已确认：多出处 evidence 缺口由 `TASK-036` / `ADR-016` Draft 承接；根因是 occurrence-insensitive dedup 与 selected-candidate 投影双重折叠，只补 `cellIndex` 不能解决 27 actual anchors 对 57 纳入 occurrence 的基数缺口；Draft 独立最终审计 `GO`。
* 已确认：现有 point result / snapshot / OpenAPI / JSONB query 使用列表结构，可承载一点评多 anchors；若实现发现事实不同必须停止并拆兼容任务。
* 已确认：用户授权创建/派发 `TASK_SPEC-035-A`；用户接受 `ADR-016` 并已完成 ARCHITECTURE 同步。
* 已确认：TASK-035、TASK-036 B1/B2/C1/C2/D1/D2 已进入主线；最终 Core
  v29/R7 工件判定 `PASS` 并完成三方全零 GO，父任务仅等待 Milestone 最终跨 TASK
  审计。
* 已确认：R6B 为 18 `MATCH` / 9 `NOT_OBSERVABLE`、18 `PASS` /
  9 `NOT_CONCLUDED`、49 actual anchors / 57 included occurrences；
  occurrence 为 47 `MATCHED` / 10 `NOT_OBSERVABLE` / 6 `EXCLUDED`，最终
  `FAIL`。
* 已确认：active/inactive payment branch attribution 使用新
  `v20260729.1 / consistency-scope-v20260729.1` 冻结；v15 语义和历史 R6B
  工件保持不变。

## 完成记录

* 完成日期：未完成；Phase 1 于 2026-07-14、C2/R6 与 v29/R7 于 2026-07-29
  执行结束。R7 工件为 `PASS`，父任务因当前增量最终三审尚未完成而保持 active。
* 建档独立只读复核：2026-07-13，Decision 为 `GO`，无 blocking findings；复核未修改、stage、commit 或 push 任何文件。
* Phase 0 结论：`NO_GO_TEST_ONLY_HARNESS_REQUIRED`。
* `TASK_SPEC-034-A`：实现提交 `99bea3a6a3ce0cbecf337e76692aac3a6c428228`，序列化修复提交 `46a625a5eb5aee8ff5a31f86bb7300fb2d8e703a`；Codex Review Intake 与独立只读复核均为接纳。
* 实际执行入口 / 命令：formal 双门禁 + `gradle test --rerun-tasks --tests "*.formalAcceptanceEntryPointIsPropertyAndInputGated"`；完整链路执行后按设计以失败断言返回非零。
* 变更文件：仅 test-only harness、TASK-034 验收输出和项目记忆文档；未修改任何生产代码、fixture、expected 或受保护数据。
* 测试结果：harness 13/13、既有四类定向回归 27/27；正式方法 1/1 完成证据写出后因验收总结果 `FAIL` 失败。
* 样本结果：3 份均完成查询；27 个 `PointStatus` 全为 `PASS`，candidate 为 9 `MATCH` / 18 `MISMATCH`，Finding / SYS 均为 0。
* occurrence 统计：63 条、57 纳入、6 排除；57 条纳入均 `NOT_OBSERVABLE`，6 条排除均 `EXCLUDED`。
* 最终判定：`FAIL`。
* C2/R6B 增量：C1/C2 10 类测试 413/413；18/27 candidate `MATCH`、
  18/27 PointStatus `PASS`、49 actual anchors；57 条纳入均有逐条结果，
  其中 47 `MATCHED` / 10 `NOT_OBSERVABLE`，formal 总判定仍为 `FAIL`。
* D1/R7 增量：合并门禁 443/443；正式 R7 为 27/27 candidate `MATCH`、
  27/27 `PASS`、57 `MATCHED` / 6 human `EXCLUDED`、70 production ledger、
  0 SYS/Finding，且 R8 重封证明 execution-scoped exact query；正式工件判定 `PASS`。
* 遗留问题：需完成当前 subject 的新冻结与三审；不得把已失效 R5、历史 R6、
  官方 `/models` 连通成功或 Track A 模型意见当成 R7/Provider 证据。
* 2026-07-31 中途收敛：v29/R7 限定样本证据进入先行 Core integration unit，
  只随 Core 执行完整验证与三方全零审计；不重跑新的人工 ground truth，不把 R7
  `PASS` 当作 Track B admission 或 Provider 激活证据。
* 2026-08-02 候选 `97b55a0…` 的 Core verification 在 Formal R7 fail closed：
  57 条纳入 occurrence 为 `39 MATCHED / 18 NOT_OBSERVABLE`，18 条全部是人工
  `TABLE_CELL`。test-only harness 在公共 `BLOCK_LEVEL` 分支先验证 block ref，导致
  合法 parser-issued `table/.../cell/...` ref 未被解析；生产结果中的 cell identity
  未丢失。新增最小公共契约红测 `1/1 FAIL`，resolver 改为 preview ref 优先后
  `1/1 PASS`，完整 harness `19/19 PASS`。该修复不修改生产代码、人工答案、
  CandidateResolver、Finding/verdict 或 Provider；新的 Formal R7 只允许在 clean
  candidate 上运行。
* 2026-08-02 test-only 修复提交 `91d5e16…` 的 Formal R7 已重新达到
  `57 MATCHED + 6 EXCLUDED`，seal/verify SHA 为 `bf199763…`。完整 verification
  随后因 D1 实际 `452/0/0/0`、脚本仍固定 451 而停止；这是 evidence count gate
  滞后，不是 R7 或产品测试失败。当前仅同步 verification/freeze 计数门禁，Node
  定向 `11/11 PASS`；`91d5e16…` 的 R7 因后续 tracked 脚本变化而 superseded，
  下一 Formal R7 必须从新 clean candidate 生成。
* 2026-08-02：最终 Core subject `90c4ae9a…` 已从新 clean HEAD 重建 Formal R7、
  完整 verification 与 freeze，并取得 CC AUDIT、代码/架构 Codex auditor、
  测试/安全 Codex auditor 三份全零 GO；GitHub run `30748527866` 全绿，PR #37
  合并为 `115be530…`。限定三样本 R7 能力已进入主线；该证据仍不等于 Track B
  admission 或 Production Ready，父任务等待 Milestone 最终跨 TASK 审计。
