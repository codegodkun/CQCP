# TASK_SPEC-036-D2：Model Assist Eligibility、Family Plan 与 EvidencePacket seam

状态：IMPLEMENTED / TRACK B ZERO-CALL PASS / MILESTONE FINAL AUDIT PENDING

父任务：`TASK-036`

Task Level：`L3 高风险治理`

执行者：主 Codex

创建日期：2026-07-29

## 目标

在不接入 Provider、不进入 `REVIEWING_MODEL`、不改变 deterministic verdict 的前提下，
实现 ADR-019 和 `docs/ARCHITECTURE.md` 第 22.5 节要求的纯规则
`ModelAssistEligibilityEvaluator`、`FamilyModelCallPlan` 与 runtime-isomorphic
局部 `EvidencePacket` seam，使 TASK-EVAL-002 Track B 能使用与未来运行时相同的数据
结构评估 role/candidate/anchor/abstention。

## Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`
* `tasks/active/TASK_SPEC-036-C2-consistency-set-execution-activation.md`
* `tasks/active/TASK-EVAL-002-blind-semantic-evaluation.md`
* `decisions/ADR-019-blind-evaluation-and-model-activation-boundary.md`
* `docs/ARCHITECTURE.md` 第 22.5 节
* `docs/ai-review.md` 的 EvidencePacket / Track B 边界

## Optional Context

* `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
* `outputs/task-034-mvp-e2e-acceptance-v3/`

## Out of Scope

* DeepSeek/Gemma adapter、网络调用、重试、breaker、artifact 数据库持久化。
* `REVIEWING_MODEL` stage 接线、PUBLIC binding/profile、普通 `MVP_DEMO_MOCK` binding。
* 修改 deterministic `CandidateResolver`、EvidenceSlot、PointStatus、Finding 或 SYS enum。
* 修改人工 ground truth、expected、DOCX、parser identity、公共 API 或数据库。

## 冻结契约

1. Eligibility 是纯规则函数，不修改 role resolution、不调用模型：
   * deterministic `HIGH` 默认零调用；
   * `UNKNOWN/no candidate`、invalid bundle、预算不完整、无可靠 anchor 均零调用；
   * `MEDIUM/LOW` 仅在 required/critical slot、`gemmaIfAmbiguous` 和最低候选门槛满足时 eligible；
   * `CONFLICTED` 仅在至少两个候选具有可靠 anchor、允许 context 与局部结构关系时 eligible。
2. `FamilyModelCallPlan` 跨 shard 去重 role/block，遵守硬预算；无法完整装入某 role
   的局部证据时把 role 放入 `uncoveredRoles`，不得隐式增加调用或回灌全文。
3. `RuntimeEvidencePacket` 至少包含 family/review point、requested role、candidate
   occurrences、可靠 SourceAnchor、slot coverage/abstention、预算/截断元数据和严格
   model-output contract；不得包含 expected、human ground truth、最终 verdict/Finding、
   完整合同或 raw prompt。Track B 为保持 runtime-isomorphic，SourceAnchor 中的
   `blockId/previewElementRef` 是有意保留的运行身份；Track A 的 opaque location
   规则不适用于本 packet。
4. R7 三份样本必须从 same-run `ReviewEngineInput.pointEvidences` 生成 Track B 包，不从
   human expected 或 query verdict 反推。当前 v29 九点均为 deterministic `HIGH` 且资产
   未声明 model assist，因此 27 点必须全部 `ZERO_CALL_REQUIRED`；这证明 abstention
   和零 eligible call，不证明 Provider 可处理真实 eligible ambiguity。
5. Track B Codex 意见与未来 DeepSeek 意见只评估
   `role/candidate/anchor/abstention`；不得输出 Finding。Track A 结果不得填充 Track B。

## 可证伪验收断言

1. Eligibility 覆盖 HIGH/MEDIUM/LOW/CONFLICTED/UNKNOWN、invalid bundle、预算、
   anchor/context 正反矩阵；reasonCodes 稳定且非空。
2. Family plan 覆盖跨 shard 去重、硬预算、uncovered role 与零 eligible call。
3. Packet builder 在相同输入上 byte-stable；泄漏扫描拒绝 expected/human/verdict/finding
   字段；所有 candidate anchor 可追溯到输入 occurrence。
4. 三份 R7 same-run 输入生成恰好 27 个 packet、3 个 sample family-plan 集合；
   `modelCallsAllowed=false`、27/27 `ZERO_CALL_REQUIRED`，没有全文 fallback。
5. TASK-034 R7、既有 413、D1 30、合并 443、Track B seam tests 与完整后端门禁全部通过。
6. 未取得样本公网外发授权时 DeepSeek runner 仍在网络前返回
   `EXTERNAL_EGRESS_NOT_AUTHORIZED`。

## 回滚边界

删除新增 eligibility/plan/packet seam、对应测试和 Track B 产物即可；deterministic
execution、v15/v29 assets、R7 result 和普通 Demo binding 不发生变化。

## STOP 条件

* 需要修改 Finding/SYS、CandidateResolver、EvidenceSlot、公共 API、数据库或
  `REVIEWING_MODEL` 状态机。
* 需要把全文写入 packet、放宽可靠 anchor、让模型覆盖 HIGH verdict，或以 Track A
  表现代替 Track B admission。

## 审计与 Git

实现由主 Codex 完成。里程碑冻结后由两个 `fork_turns="none"` 的全新 Codex
subagents 与 CC AUDIT 对同一 hash 从零只读审计。未经单独授权不得 commit、push、
PR 或 merge。

## 实现记录

* 已实现纯规则 `ModelAssistEligibilityEvaluator`、跨 shard/role/block 去重且硬预算
  的 `FamilyModelCallPlan`，以及 byte-stable、禁止 expected/human/verdict/Finding/
  全文 fallback 的 `RuntimeEvidencePacket`。
* `ModelAssistRuntimeSeamTest` 精确 `20/0/0/0`。R7 same-run
  `ReviewEngineInput.pointEvidences` 生成 3 个 sample package、27 个 packet、
  9 个 family plan、57 个 candidate occurrence；所有 anchor reliable。
* 当前 v29 三样本 27 点均为 deterministic HIGH，故 27/27
  `ZERO_CALL_REQUIRED`、`modelCallsAllowed=false`。三个全新
  `fork_turns="none"` Codex agent 按预冻结 dispatch 独立返回 abstention；
  v2 seal 绑定 R7/manifest/prompt/dispatch/agent/task/time/opinion bytes。
* 结论固定为 `NOT_ESTABLISHED_ZERO_ELIGIBLE_SAMPLE`：本语料证明正确零调用，
  不证明 guarded assist 质量，不放行 Provider 或 `REVIEWING_MODEL`。
