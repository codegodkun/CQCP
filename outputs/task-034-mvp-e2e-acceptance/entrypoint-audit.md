# TASK-034 Phase 0 执行入口审计

日期：2026-07-14

审计基线：`origin/master` merge commit `96a0099`；实际只读检查与定向测试在 tree 相同的 `7062ace` worktree 执行。

门禁结论：`NO_GO_TEST_ONLY_HARNESS_REQUIRED`

父任务结果：`STOPPED_FOR_TASK_SPEC_034_A`

## 结论摘要

当前仓库已有真实 DOCX parser-backed 状态机测试、结果快照、结果查询服务/控制器和 63 条人工 occurrence 数据契约，但不存在一个无需修改生产代码即可执行的单一入口，同时完成：

1. 001/002/003 真实 DOCX 输入；
2. parser、索引、证据构建、Review Engine、状态机和快照；
3. 对同一 task 的结果查询；
4. 每个审核点 actual `candidateValue` 的可观测输出；
5. 57 条纳入 occurrence 的逐条 actual anchor 比较和 6 条排除 occurrence 的 `EXCLUDED` 保留。

因此不得运行正式 MVP E2E。前端依赖缺失是附带环境问题，不改变入口本身已不满足门禁的判断，也不把结论改写为 `BLOCKED_ENVIRONMENT`。

## 六项门禁证据

| # | 门禁项 | 现有直接证据 | 判断 |
|---|---|---|---|
| 1 | 输入为真实仓库 DOCX | `TaskExecutionStateMachineTest` 的 parser-backed 路径从 expected JSON 解析 `sourceDocx`，构造 `TaskExecutionDocumentReference`，并调用状态机；001/002/003 均在现有正向 fixture 集合中。 | `PARTIAL`：真实输入存在，但没有正式单一验收入口。 |
| 2 | 调用当前 parser 并保留 block/row/cell 定位 | `TaskExecutionStateMachine.runPreparationStages` 依次调用 `parse → index → plan → build`；`PointEvidence` / `SourceAnchorSummary` 可携带 `blockId`、`locationLevel`、`previewElementRef`。 | `PASS_COMPONENT` |
| 3 | 经过审核状态机并形成快照 | `TaskExecutionStateMachine.execute` 进入 `REVIEWING_RULES`、`COMPOSING`，保存 `ReviewResultSnapshot`。定向状态机测试通过。 | `PASS_COMPONENT` |
| 4 | 查询同一 task 的结果 | `GET /api/v1/tasks/{taskId}/result` 存在；但 controller 测试 mock 了 service，service 测试虽串联状态机和 store，却使用预构造 `ReviewEngineInput`，未输入 DOCX。生产代码也没有提交/执行任务的 POST 入口。 | `FAIL` |
| 5 | 输出 PointStatus、candidateValue、证据摘要、anchor、URL/SYS | `PointReviewResult` 有 `pointStatus` 和 anchors，`SourceAnchorSummary` 有证据摘要与定位，snapshot 有 diagnostics；但 actual `candidateValue` 只存在于内部 `PointEvidence`，`PointReviewResult` 和 `ReviewResultSnapshot` 均不保存它。 | `FAIL` |
| 6 | 显式比较 63 条 occurrence，保持 57/6 | `HumanAnchorGroundTruthFixtureTest` 只验证 XLSX→fixture 的 63/57/6 数据契约；`EvidenceOverlapEvaluator` 消费 parser-backed canonical keys。README 明确 human fixture 不含 canonical key，人工位置→parser canonical key bridge 尚不存在。 | `FAIL` |

`PASS_COMPONENT` 只表示组件存在，不满足 `GO_EXISTING_ENTRYPOINT` 所要求的端到端单入口。

## 关键代码事实

- `TaskExecutionStateMachine.java:57-59`：是否走 parser 取决于 `documentReference`。
- `TaskExecutionStateMachine.java:159-193`：真实准备阶段为 `parse → index → plan → build`。
- `TaskExecutionStateMachine.java:316-328`：`forDocument` 可接收 DOCX 引用。
- `ResultComposer.java:23-37`、`ResultComposer.java:106-131`：快照只合成 point results、diagnostics、anchors 和结构化字段快照。
- `MinimalReviewEngine.java:665-680`：actual `candidateValue` 位于 `PointEvidence`。
- `MinimalReviewEngine.java:767-777`：`PointReviewResult` 不含 `candidateValue`。
- `TaskResultQueryController.java:9-21`：只提供 `GET /api/v1/tasks/{taskId}/result`。
- `TaskResultQueryControllerTest.java:26-33`：查询 controller 测试 mock 了 service。
- `TaskResultQueryServiceTest.java:23-31`：同 task 查询测试使用预构造 input，不走 DOCX parser。
- `HumanAnchorGroundTruthFixtureTest.java:115-163`、`:223-234`：只验证 63 条 fixture 和 57/6 数量。
- `packages/test-fixtures/README.md:109-113`：human fixture 不含 parser actual 派生定位，bridge 不在既有范围。

## 验证结果

后端定向测试合并复跑：27 tests / 0 failures / 0 errors / 0 skipped。

| 测试类 | tests | failures | errors |
|---|---:|---:|---:|
| `HumanAnchorGroundTruthFixtureTest` | 10 | 0 | 0 |
| `ParserBackedEvidenceOverlapBaselineTest` | 4 | 0 | 0 |
| `TaskExecutionStateMachineTest` | 10 | 0 | 0 |
| `TaskResultQueryControllerTest` | 3 | 0 | 0 |

环境版本：OpenJDK 21.0.11、Gradle 8.10.2、Node 24.16.0、npm 11.13.0。

前端基础命令保留原始环境失败：

- `npm.cmd run test:admin-web`：`vitest` 未安装/不可识别。
- `npm.cmd run build:admin-web`：`tsc` 未安装/不可识别。

未安装依赖、未修改环境以规避失败；Phase 0 已因入口能力缺失判为 NO-GO。

## 停止点与后续门禁

- 未运行 001/002/003 正式 MVP E2E。
- 未生成 Phase 1 run manifest、sample results 或 occurrence comparison。
- 已冻结 `tasks/active/TASK_SPEC-034-A-test-only-e2e-harness.md`。
- `TASK_SPEC-034-A` 仅允许 test-only harness；Claude Code / DeepSeek 必须先提交编码前规格映射计划，Codex 明确 `GO` 前不得实现。
- 未修改生产 parser、CandidateResolver、EvidenceSlot、SourceAnchor、Review Engine、公共 API、数据库、workflow、ADR、DOCX、XLSX、matrix、fixture 或 expected JSON。
