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
- `TASK-027-C` / `TASK-027-D` 前置兼容任务与 `TASK-027` 最小主实现均已完成并已本地提交
- `TASK-027` 主实现已完成，当前进入归档收口阶段；本轮完成的是 ADR-015 边界内的最小主实现落地，不是完整 `EvidenceBundle` 平台化
- `TASK-027-C` OpenAPI 契约对齐 / 文档更新任务已冻结为实现前置
- `TASK-027-D` snapshot / persistence 兼容任务已冻结为实现前置
- `TASK-027-C` 已完成最小保守对齐：`packages/api-contracts/openapi.yaml` 已从旧 `/api/review/results/{taskId}` 对齐到真实 `GET /api/v1/tasks/{taskId}/result`，并把 `notConcludedDetail`、`missingOptionalSlots[]`、`sourceAnchors` 仅作为 optional / compatibility / diagnostic-only 字段文档化
- 保持 `TASK-028` / `TASK-031` / `TASK-032` 的边界，不提前吞并
- `TASK-DOC-002` 已完成并归档：readonly-review 正式模板、模板路由补充、`TASK_SPEC 类型` 字段与最小 R 型 readonly-review 已收口

## 活跃任务

- 当前 active TASK：无；`TASK-027` 已进入归档收口，归档文件为 `tasks/done/TASK-027-evidence-slot-source-anchor-governance.md`。
- `ADR-015` 已人工接受：`decisions/ADR-015-evidence-slot-source-anchor-governance.md`。
- `TASK-027` 已完成最小主实现落地；A/B 两份 readonly-review、`TASK-027-C`、`TASK-027-D` 均已完成并作为有效前置输入。
- `TASK-027` 本轮完成范围仅限 `EvidenceSlot / SourceAnchor / coverage / SYS-*` 在现有审核链路中的兼容增强；未扩大到完整 `EvidenceBundle` 平台化实现。
- `TASK-027-C` 已完成并已本地提交：`8e09dc6 docs(contract): align TASK-027-C result API contract documentation`。
- `TASK-027-D` 已完成并已本地提交：`ed63184 fix(reviewengine): tolerate forward-compatible review result snapshots`；`PersistentTaskResultStore` 继续使用局部 tolerant-read `ObjectMapper` 副本读取 `review_result_snapshot` JSON，历史快照不回填、当前不需要数据库迁移。
- `TASK-027` 主实现已完成并已本地提交：`b85f4dd feat(reviewengine): add minimal evidence slot preflight gating`。
- 当前 `git status --short` 为空，工作区干净。
- 当前分支状态：`master...origin/master`，本地 `master` 与 `origin/master` 已对齐。
- `git rev-list --left-right --count origin/master...HEAD` 当前为 `0 0`。
- `TASK-027` 相关 5 个本地提交已完成 `push`，当前不存在待同步提交。
- `TASK-028` / `TASK-031` / `TASK-032` 仍未开始，继续禁止抢跑。
- 开源案例 / benchmark 只读调研已完成：未发现可直接照搬的开源合同审查系统；中文开源项目仅适合作为 UI / 上传 / 报告 / 预览 / 配置交互参考，不能继承其审核架构；国际 benchmark 主要用于评测方法参考。建议后续优先评估 `TASK-EVAL-001`（parser-backed fixtures 的 evidence overlap evaluation），但本轮不改变架构、不创建 ADR、不创建任务文件、不进入 `TASK-028`。

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
- `TASK-027` 已完成最小主实现并进入归档收口；`TASK-028` / `TASK-031` / `TASK-032` 当前仍不得进入
- 位置切片（`paymentClauseBlocks` 按 `MONTHLY` / `MILESTONE` 分段）在当前 4 正 4 负 fixture 上对最终判定结果无可观测影响，实际候选范围限定主要依赖内容关键词过滤（`isRatioRoleBlock` / `isExpectedRatioValue`）；后续若新增表达差异较大的合同样本，应重新验证位置切片是否真正生效，不应假设其已被验证有效
- `UNKNOWN` 仍仅由 `MinimalCandidateResolverTest` 隔离单测覆盖，尚未由真实 parser 主链路 fixture 触发

## 调研结论统一收口

- 当前未发现可直接替代 CQCP 主链路的成熟开源架构；当前主链路继续保持：`Word-first -> CandidateIndex -> CandidateResolver -> EvidenceSlot -> SourceAnchor -> 小模型局部辅助 -> 后端确定性裁判 -> ReviewResultSnapshot`。
- 国际 benchmark 仅作为审核点、证据召回、证据重合度和失败模式的评测方法参考；中文开源项目仅作为 UI、报告、预览和配置交互参考；不继承“模型直接审查整份合同并输出业务结论”的主链路。
- 更强模型可能改变候选收集、角色归属、复杂语义消歧和脆弱正则的使用比例，但不取消治理层：`SourceAnchor`、`EvidenceSlot admission`、`SYS / Finding` 分流、`NOT_CONCLUDED`、不可变快照、版本追溯及后端确定性公式/结构化裁判继续保留。
- 模型输出必须经过后端 verifier 和 `CandidateResolver`，不得直接形成业务 Finding。模型自报 confidence 不得直接成为 `HIGH` admission；模型只能提供候选、理由、anchor、uncertainty reason、alternative candidates 等可验证信号。
- `TASK-EVAL-001` 是下一优先候选任务，目标是冻结并建立 parser-backed fixtures 的 block / table-row / cell level evidence overlap 最低评测基线；本文件只记录候选顺序，不代表已创建任务或进入实现。
- `TASK-028` 应等待 `TASK-EVAL-001` 父任务完成边界冻结并形成最低评测基线后再进入，以免在缺少 evidence precision / recall 基线时比较模型或扩大模型辅助范围。
- A30 24GB 下的后续模型候选池不应预先局限于 1B / 4B / 7B，可在 `TASK-028` 前置调研中评估 Gemma 4 26B A4B、Gemma 4 31B、Qwen3 30B-A3B、Qwen3 32B 及其适用量化版本。待确认：这些候选的 Q4 权重来源、A30 24GB 实际显存占用、上下文预算、吞吐和 CQCP 中文合同质量均尚未专项验证，不得写成已确认可部署或优于当前方案。
- Docling、BM25、Ragas、Outlines、OpenTelemetry、Label Studio 等只作为 `TASK-EVAL-001` 之后的 parser adapter、候选召回、评测、结构化输出、观测和标注治理候选，不在当前 MVP 抢跑引入。

## 待确认事项

- 待确认：`TASK-EVAL-001` 与 `TASK-029` / `TASK-030` 的最终任务顺序，以及是否需要同步调整 `tasks/MVP_TASK_MAP.md`。
- 待确认：Gemma 4 26B A4B / 31B、Qwen3 30B-A3B / 32B 的具体权重、量化格式、license、A30 24GB 可运行性和 CQCP 样本评测方案。
## 下一步任务

1. 先由 Codex 创建并冻结 `TASK-EVAL-001` 父任务边界；当前尚未创建任务文件，也未进入实现。
2. `TASK-EVAL-001` 的最低目标应为 block / table-row / cell level evidence overlap 基线，不把 character-level scoring 扩大为 MVP 硬要求。
3. `TASK-028` 等待 `TASK-EVAL-001` 最低评测基线完成后再进入；`TASK-031` / `TASK-032` 继续不得抢跑。
4. 后续如任务优先级、依赖关系或协作边界被正式确认，再同步更新 `tasks/MVP_TASK_MAP.md`。

## 参考路径

- `tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`
- `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
- `tasks/MVP_TASK_MAP.md`
- `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
- `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
- `tasks/done/TASK-027-evidence-slot-source-anchor-governance.md`
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
