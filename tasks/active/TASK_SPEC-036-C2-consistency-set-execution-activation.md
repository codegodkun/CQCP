# TASK_SPEC-036-C2：Consistency Set execution 激活

状态：IMPLEMENTED / C1-C2 TEST GATE PASS / TASK-034 FORMAL R6 FAIL（历史）/
v29 R7 PASS / MILESTONE FINAL AUDIT PENDING

父任务：`TASK-036`

Task Level：`L3 高风险治理`

执行者：主 Codex

创建日期：2026-07-29

## 目标

把已通过自身门禁的 B1 versioned policy、B2 fail-closed loader/gate 和 C1 inactive
runtime core 接入 execution。只允许 execution 已冻结
`ruleSetVersion=v20260715.1` 时启用 `CONSISTENCY_SET`；现有
`MVP_DEMO_MOCK / v20260705.1` 继续走 legacy 路径。

ADR-016 已冻结该语义、版本、SYS/Finding 与 SourceAnchor 边界，本规格不新增架构
决策；若实现需要修改 Finding/SYS、EvidenceSlot、CandidateResolver 语义或公共 API，
必须停止并另行 ADR。

## Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`
* `tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md`
* `tasks/active/TASK_SPEC-036-B2-runtime-policy-binding-gate.md`
* `tasks/active/TASK_SPEC-036-C1-consistency-set-runtime-core.md`
* `decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md`
* `docs/ARCHITECTURE.md` 第 10.1、22.1.1、22.4 节

## Optional Context

* `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
* `tasks/active/TASK-035-mvp-e2e-candidate-comparison-contract-rebaseline.md`

## Out of Scope

* 修改 `MVP_DEMO_MOCK` binding、PUBLIC model profile 或默认 Demo 版本。
* DeepSeek/Gemma、`REVIEWING_MODEL`、Provider、模型辅助资格或 Finding 覆盖。
* 修改人工 anchor、fixture、expected 或历史 TASK-034 v1 输出。
* 数据库 migration、公开 API/enum 或历史 snapshot 重算。

## 冻结契约

1. 新增 code-owned immutable `ConsistencyRuntimeRelease`，唯一 accepted version 为
   `v20260715.1`；readiness 由该 release 显式提供，不从环境变量、Spring profile、
   文件存在或资产自述推断。
2. `TaskExecutionStateMachine` 必须按 execution 已复制的
   `versionReferences.ruleSetVersion` 调用 `RuleSetActivationGate`：
   * `v20260705.1 / LEGACY_ALLOWED`：调用 legacy 两参数 `build`。
   * `v20260715.1 / READY`：调用携带 B2 snapshot 的 C1 三参数 `build`。
   * `POLICY_NOT_READY / POLICY_ASSET_INVALID / UNKNOWN_RULE_SET_VERSION`：在
     `BUILDING_EVIDENCE` fail closed，不 fallback。
3. 已构建输入在进入裁判前必须与 execution version 一致：
   legacy execution 不接受 runtime snapshot；consistency execution 必须有同版本
   snapshot；错配 fail closed。
4. `SingleReviewWorker` 不再硬编码只接受 `v20260705.1`，但不自行决定 readiness；
   统一交给 state machine gate。
5. B1 资产继续保持 `DRAFT / NOT_BOUND / false/false/NONE`；普通 Demo binding 和
   Task Creation resolver 不变，因此本 C2 不会让新普通任务自动绑定新版本。

## 可证伪验收断言

1. legacy execution 精确调用两参数 build，结果与当前 legacy 回归一致，runtime
   snapshot 为 null。
2. `v20260715.1` execution 精确调用三参数 build，snapshot 覆盖九点且最终
   snapshot 记录相同 ruleSetVersion。
3. readiness=false、未知版本、畸形/缺失 classpath 资产均在
   `BUILDING_EVIDENCE` 失败并进入 execution fail path；不得调用 legacy build。
4. prebuilt input 的 legacy/new version 与 runtime snapshot 四种匹配/错配均有测试，
   错配不得进入 `MinimalReviewEngine.review`。
5. worker 可把 v15 execution 交给 state machine；gate 失败时 execution 失败，不读取
   PUBLIC Secret、不调用模型。
6. 全仓搜索证明没有修改 `ExecutionBindingCatalog` 的普通
   `MVP_DEMO_MOCK/v20260705.1` 约束，没有 `REVIEWING_MODEL` wiring。
7. C1 六类测试、B2 loader/gate、state machine、worker、result composer、完整后端
   测试与 `bootJar` 全部通过。
8. TASK-034 正式复跑必须使用 v15 execution 与三参数 build；复跑输出不得覆盖 v1
   证据，必须写入新的版本化输出目录。

## 回滚边界

删除 C2 release/selection 接线并恢复 worker 的 legacy guard 即可回滚；B1/B2/C1
资产和 inactive core 可保留，不改历史 execution 或 snapshot。

## 停止条件

* 需要改变 ADR-016 frozen semantics、公共 API/DB、人工 ground truth 或默认 Demo
  binding。
* 无法证明 execution version 与 runtime snapshot 一致。
* legacy 全量回归失败且不能在本规格路径内解释、修复。

## 审计与 Git

实现和测试由主 Codex 完成。里程碑冻结后由两个 `fork_turns="none"` 的全新 Codex
subagents 与 CC AUDIT 对同一 hash 从零只读审计。未经单独授权不得 commit、push、
PR 或 merge。

## 实现与验证记录

- `ConsistencyRuntimeRelease`、state-machine version gate、prebuilt input
  version/snapshot 一致性检查与 worker 接线已实现。
- legacy `v20260705.1` 继续走两参数 build；`v20260715.1` 只在 code-owned
  readiness 为 READY 时走三参数 build；普通 `MVP_DEMO_MOCK` binding 未改变。
- 2026-07-29 联合复跑 C1/C2 相关 10 个测试类：413/413 通过。
- TASK-034 正式 R6 确认 execution 使用 `v20260715.1` 并生成 v2 输出，但总判定
  `FAIL`：18/27 candidate `MATCH`、9/27 `NOT_OBSERVABLE`，18/27
  PointStatus `PASS`、9/27 `NOT_CONCLUDED`，49 个可靠 anchors 对 57 条纳入
  occurrence。
- 临时 R5 因把 `paymentMethod` 带入 C1 full scan、违反 C1
  payment-method-neutral 冻结契约而失效；本记录不得引用 R5 作为验收通过证据。
