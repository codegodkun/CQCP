# CURRENT_CONTEXT.md

更新日期：2026-06-20

## 当前阶段

CQCP 当前处于 MVP 主链路接通与 parser-backed evidence 收口阶段。

已完成：
- 结果快照与结果合同
- 最小执行状态机
- `GET /api/v1/tasks/{taskId}/result`
- 持久化结果查询适配
- `TASK-023` 公开结果页最小实现
- `TASK-024` 管理台诊断详情最小实现
- parser-backed 主链路最小接入与 fixture 级验收收口
- 最小 `CandidateResolver` / evidence admission 闸门

当前重点：
- `TASK-025` 已达到 fixture 级验收 DoD，进入归档
- `TASK-026` 已完成，后续只登记 `TASK-032` 重构，不提前执行
- `TASK-027-C` / `TASK-027-D` 前置兼容任务已完成并已本地提交，当前已允许进入 `TASK-027` 主实现
- `TASK-027` 当前进入最小主实现阶段：只在现有审核链路内落地 ADR-015 的 `EvidenceSlot / SourceAnchor / coverage / SYS-*` 兼容增强
- `TASK-027-C` OpenAPI 契约对齐 / 文档更新任务已冻结为实现前置
- `TASK-027-D` snapshot / persistence 兼容任务已冻结为实现前置
- `TASK-027-C` 已完成最小保守对齐：`packages/api-contracts/openapi.yaml` 已从旧 `/api/review/results/{taskId}` 对齐到真实 `GET /api/v1/tasks/{taskId}/result`，并把 `notConcludedDetail`、`missingOptionalSlots[]`、`sourceAnchors` 仅作为 optional / compatibility / diagnostic-only 字段文档化
- 保持 `TASK-028` / `TASK-031` / `TASK-032` 的边界，不提前吞并
- `TASK-DOC-002` 已完成并归档：readonly-review 正式模板、模板路由补充、`TASK_SPEC 类型` 字段与最小 R 型 readonly-review 已收口

## 活跃任务

- 当前 active TASK：`tasks/active/TASK-027-evidence-slot-source-anchor-governance.md`。
- `ADR-015` 已人工接受：`decisions/ADR-015-evidence-slot-source-anchor-governance.md`。
- `TASK-027` 当前已由 Codex 直接进入主实现；A/B 两份 readonly-review、`TASK-027-C`、`TASK-027-D` 均已完成并作为有效前置输入。
- `TASK-027` 本轮仅允许最小实现：`EvidenceSlot / SourceAnchor / coverage / SYS-*` 在现有审核链路中的兼容增强；仍不得派发 Claude Code / DeepSeek，不得扩大到平台化实现。
- `TASK-027-C` 已完成并已本地提交：`8e09dc6 docs(contract): align TASK-027-C result API contract documentation`。
- `TASK-027-D` 已完成并已本地提交：`ed63184 fix(reviewengine): tolerate forward-compatible review result snapshots`；`PersistentTaskResultStore` 继续使用局部 tolerant-read `ObjectMapper` 副本读取 `review_result_snapshot` JSON，历史快照不回填、当前不需要数据库迁移。
- `TASK-DOC-002` 已完成、已提交、已 push；当前最新提交为 `ed63184 fix(reviewengine): tolerate forward-compatible review result snapshots`。

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
- `TASK-025` 最终收口结果
  - 4 个正向 fixture：`CQCP-MVP-DOCX-001`、`CQCP-MVP-DOCX-002`、`CQCP-MVP-DOCX-003`、`CQCP-MVP-DOCX-004`
  - 负向 fixture 矩阵：4 个 expected fixture 文件中的 `negativeCandidates` 全量展开校验，当前共 14 个负向矩阵行
  - `TaskExecutionStateMachineTest` 校验真实 parser-backed execution 的 `PointStatus`
  - `ParserBackedReviewInputPreparerEvidenceTest` 校验 `candidateValue`、`blockId`、`evidenceSummary` 包含关键值与关键词
  - `MinimalReviewEngine` 本轮仅新增 `StructuredFieldSet.fromMap(...)`，用于 parser-backed request 构造结构化字段输入
  - `TaskExecutionStateMachine` 本轮仅接入 parser-backed preparation stages 与 fixture 验收路径，未进入通用状态机能力扩展

## 已接受 ADR

- `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
- `decisions/ADR-003-task-execution-snapshot-model.md`
- `decisions/ADR-005-first-review-points-selection.md`
- `decisions/ADR-008-definition-term-index.md`
- `decisions/ADR-012-domain-model-freeze.md`
- `decisions/ADR-013-v1-core-schema-bootstrap.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
- `decisions/ADR-015-evidence-slot-source-anchor-governance.md`

## 当前阻塞项

- 全量 `gradle test` 仍失败于既有 `CqcpApiServerApplicationTests` 数据库连接，不作为本轮代码失败结论
- `TASK-026` 只治理最小 resolver 与 candidate 信号 admission，尚未进入完整 `EvidenceSlot / SourceAnchor` 正式治理
- `TASK-027-A` 回收结论已确认：外部 Result API 真实实现为 `GET /api/v1/tasks/{taskId}/result`，`packages/api-contracts/openapi.yaml` 与真实实现和 DTO 已分叉；`PointStatus` 五值稳定、`notConcludedReason` 六值稳定，但 `notConcludedDetail`、`missingOptionalSlots[]`、正式化 `sourceAnchors` 尚未形成真实对外承载位，因此 OpenAPI 契约对齐 / 文档更新任务阻断 `TASK-027` 直接进入实现
- `TASK-027-B` 回收结论已确认：`review_result_snapshot` 表的 JSONB 容器能力本身不是硬阻塞，当前不需要数据库迁移；但 Java 读模型仍是固定 record，历史快照兼容读取与 `ObjectMapper` 未知字段策略尚未被证明，因此 snapshot / persistence 兼容任务阻断 `TASK-027` 直接进入 persistence 相关实现
- `TASK-027-C` 已完成最小保守对齐，但只解决 Result API path / schema 文档分叉，不提供 `notConcludedDetail`、`missingOptionalSlots[]` 或 ADR-015 完整 `SourceAnchor` 的真实代码承载
- `TASK-027-D` 已完成最小兼容收口：`review_result_snapshot` 继续复用现有 JSONB 列，不引入数据库迁移；`PersistentTaskResultStore` 对快照 JSON 采用局部 `FAIL_ON_UNKNOWN_PROPERTIES=false` 的 tolerant read 策略，从而允许 ADR-015 后续兼容新增字段在不回填历史数据的前提下被旧快照读取逻辑安全忽略
- `TASK-027` 当前仍不得进入 `TASK-028` / `TASK-031` / `TASK-032`
- 位置切片（`paymentClauseBlocks` 按 `MONTHLY` / `MILESTONE` 分段）在当前 4 正 4 负 fixture 上对最终判定结果无可观测影响，实际候选范围限定主要依赖内容关键词过滤（`isRatioRoleBlock` / `isExpectedRatioValue`）；后续若新增表达差异较大的合同样本，应重新验证位置切片是否真正生效，不应假设其已被验证有效
- `UNKNOWN` 仍仅由 `MinimalCandidateResolverTest` 隔离单测覆盖，尚未由真实 parser 主链路 fixture 触发

## 待确认事项

- 待确认：`TASK-027-C` 是否仅需 OpenAPI 文档更新即可完成，还是还需要最小 contract fixture / example 同步
- 待确认：`TASK-032` 是否在 `TASK-026` 正式完成后立即启动，拆分 `ParserBackedReviewInputPreparer`
## 下一步任务

1. 完成 `TASK-027` 主实现最小收口，复核 `EvidenceSlot / SourceAnchor / coverage / SYS-*` 兼容增强是否满足 ADR-015 边界。
2. 保持 `TASK-028` / `TASK-031` / `TASK-032` 未开始；本轮不得进入主实现范围外扩展。
3. 主实现收口后，按 Docker Compose 状态核对 + reviewengine 定向 Gradle 测试结果决定是否建议本地提交。

## 参考路径

- `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
- `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
- `tasks/MVP_TASK_MAP.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
- `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
- `tasks/active/TASK-027-evidence-slot-source-anchor-governance.md`
- `docs/backend.md`
- `docs/ai-review.md`
- `changelog/2026-06.md`
- `tasks/done/TASK-DOC-002-multi-agent-delegation-and-readonly-review-governance.md`
- `tasks/done/TASK_SPEC-DOC-002-A-readonly-review-template-draft.md`
- `tasks/done/TASK_SPEC-DOC-002-B-template-router-and-task-spec-fields-draft.md`
- `tasks/done/TASK_SPEC-DOC-002-R-final-doc-governance-readonly-review.md`
- `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`
- `tasks/TEMPLATE_ROUTER.md`
- `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
