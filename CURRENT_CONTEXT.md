# CURRENT_CONTEXT.md

更新日期：2026-06-19

## 当前阶段

CQCP 当前处于 MVP 主链路接通与 parser-backed evidence 收口阶段。

已完成：
- 结果快照与结果合同
- 最小执行状态机
- `GET /api/v1/tasks/{taskId}/result`
- 持久化结果查询适配
- `TASK-023` 公开结果页最小实现
- `TASK-024` 管理台诊断详情最小实现
- parser-backed 主链路最小接入
- 最小 `CandidateResolver` / evidence admission 闸门

当前重点：
- 完成 `TASK-025` 的 fixture 级验收收口
- `TASK-026` 已完成，后续只登记 `TASK-032` 重构，不提前执行
- 保持 `TASK-027` / `TASK-028` / `TASK-032` 的边界，不提前吞并

## 活跃任务

- `TASK-025`
  - 文件：`tasks/active/TASK-025-parser-candidate-evidence-mainline-integration.md`
  - 状态：进行中
  - 定位：fixture 级验收收口任务
  - 当前 DoD：4 个正向 fixture + 4 个负向 fixture 全部通过
    - `PointStatus`
    - `candidateValue`
    - `blockId`
    - `evidenceSummary` 包含值与关键词
- `TASK-026`
  - 文件：`tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
  - 状态：已完成并归档
  - 定位：最小 `CandidateResolver`、置信度分级、同 role 竞争检测、真实 parser 主链路非 `HIGH` 可达性、`HIGH` admission gate

## 最近完成

- parser-backed 主链路已不再写死 `DEFAULT_CONFIDENCE = "HIGH"`
- 新增 `MinimalCandidateResolver`
  - `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN`
  - 空候选、部分归属、同 role 竞争等最小判定已落地
- `ParserBackedReviewInputPreparer` 已改为
  - 先收集原始候选
  - 再由 resolver 决定是否允许 evidence admission
  - 非 `HIGH` 不再进入确定性裁判
- 新增更严格的 parser evidence 回归测试
  - 正向与负向 fixture 均校验 `PointStatus + candidateValue + blockId + evidenceSummary`
- 已确认并修复 `ParserBackedReviewInputPreparer.paymentClauseBlocks()` 死代码 bug
  - 第一层修复：`if (request != null)` 改为 `if (request == null)` 兜底
  - 第二层修复：同步修正付款切片起始命中词，并让 `sliceBlocks()` 保留命中的起始块
  - 结果：4 个正向 fixture + 4 个负向 fixture 的 9 个 review point 最终 `PointStatus` 与修复前一致，无净变化
- 新增真实 parser 主链路冲突 fixture：`CQCP-MVP-DOCX-001-progress-conflict.docx`
  - 同一付款比例块内同时出现 `70%` 与 `75%`
  - 经 `parse() -> index() -> plan() -> build()` 实际返回 `CONFLICTED`
  - 端到端断言为 `EvidenceStatus.AMBIGUOUS` / `SYS_ROLE_CONFLICT`
- `TASK-026` 已建立独立 active task 文件，并开始治理真实 parser 主链路的 `MEDIUM / LOW` 可达性
- 已调整 `ParserBackedReviewInputPreparer` 的付款比例候选信号建模
  - role block 弱回退不再伪装成完整 block 归属，唯一候选进入 `MEDIUM`
  - 付款片段内缺少 role 标签的弱百分比候选进入 `LOW`
  - `MinimalCandidateResolver` 五档定义和 `HIGH` admission gate 未改变
- 新增真实 parser 主链路非 `HIGH` fixture
  - `CQCP-MVP-DOCX-001-progress-medium.docx`：触发 `MEDIUM` / `SYS_EVIDENCE_MEDIUM_CONFIDENCE`
  - `CQCP-MVP-DOCX-001-progress-low.docx`：触发 `LOW` / `SYS_EVIDENCE_LOW_CONFIDENCE`
- `TASK-026` 验证结果
  - 目标回归：`MinimalCandidateResolverTest`、`ParserBackedReviewInputPreparerEvidenceTest`、`TaskExecutionStateMachineTest` 通过
  - 全量 `gradle test` 仍失败于既有 `CqcpApiServerApplicationTests.contextLoads` PostgreSQL host 解析问题

## 已接受 ADR

- `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- `decisions/ADR-003-task-execution-snapshot-model.md`
- `decisions/ADR-005-first-review-points-selection.md`
- `decisions/ADR-008-definition-term-index.md`
- `decisions/ADR-012-domain-model-freeze.md`
- `decisions/ADR-013-v1-core-schema-bootstrap.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`

## 当前阻塞项

- 全量 `gradle test` 仍失败于既有 `CqcpApiServerApplicationTests` 数据库连接，不作为本轮代码失败结论
- `TASK-026` 当前只治理最小 resolver 与 candidate 信号 admission，尚未进入完整 `EvidenceSlot / SourceAnchor` 正式治理
- 位置切片（`paymentClauseBlocks` 按 `MONTHLY` / `MILESTONE` 分段）在当前 4 正 4 负 fixture 上对最终判定结果无可观测影响，实际候选范围限定主要依赖内容关键词过滤（`isRatioRoleBlock` / `isExpectedRatioValue`）；后续若新增表达差异较大的合同样本，应重新验证位置切片是否真正生效，不应假设其已被验证有效
- `UNKNOWN` 仍仅由 `MinimalCandidateResolverTest` 隔离单测覆盖，尚未由真实 parser 主链路 fixture 触发

## 待确认事项

- 待确认：`TASK-027` 的正式 `EvidenceSlot / SourceAnchor` 设计是否还需新增 ADR
- 待确认：`TASK-032` 是否在 `TASK-026` 正式完成后立即启动，拆分 `ParserBackedReviewInputPreparer`

## 下一步任务

1. 继续完成 `TASK-025` fixture 级验收收口和归档判断。
2. `TASK-032` 已登记为后续 `ParserBackedReviewInputPreparer` 物理拆分类重构，但本轮不执行。
3. 继续保持 `TASK-027` / `TASK-028` 未开始，除非后续任务明确要求。

## 参考路径

- `tasks/active/TASK-025-parser-candidate-evidence-mainline-integration.md`
- `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
- `tasks/MVP_TASK_MAP.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
- `docs/backend.md`
- `docs/ai-review.md`
- `changelog/2026-06.md`
