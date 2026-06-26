# CURRENT_CONTEXT.md

更新日期：2026-06-26

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
- `TASK-025` 已达到 fixture 级验收 DoD 并归档
- `TASK-026` 已完成，后续只登记 `TASK-032` 重构，不提前执行
- `TASK-027-C` / `TASK-027-D` 前置兼容任务与 `TASK-027` 最小主实现均已完成并已本地提交
- `TASK-027` 主实现已完成并归档；完成的是 ADR-015 边界内的最小主实现落地，不是完整 `EvidenceBundle` 平台化
- `TASK-027-C` OpenAPI 契约对齐 / 文档更新任务已冻结为实现前置
- `TASK-027-D` snapshot / persistence 兼容任务已冻结为实现前置
- `TASK-027-C` 已完成最小保守对齐：`packages/api-contracts/openapi.yaml` 已从旧 `/api/review/results/{taskId}` 对齐到真实 `GET /api/v1/tasks/{taskId}/result`，并把 `notConcludedDetail`、`missingOptionalSlots[]`、`sourceAnchors` 仅作为 optional / compatibility / diagnostic-only 字段文档化
- 保持 `TASK-028` / `TASK-031` / `TASK-032` 的边界，不提前吞并
- `TASK-EVAL-001` 已完成实现前 Review Intake，结论为 `NEEDS-SPLIT`；父任务原 DoD 不降级，不直接进入完整实现
- `TASK-EVAL-001-A` 已完成并 push，提交为 `4bac2f4`
- 已确认《CQCP 五类问题整改计划 v3：角色分工与执行门禁补强版》需要正式入仓并纳入任务治理
- `TASK-GOV-003` 已完成、已独立审计、已 push，并完成远程同步确认；归档文件为 `tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`
- Git 历史显示 `TASK-EVAL-001-B` 已在 `672d97f695756249a871da53ad2821eb5146997f` 提交并进入 `origin/master`；提交前独立复核流程曾缺失，后续已由事后独立只读复核、定向测试复跑和父任务归档前独立审计补偿，但不能追溯性等同于提交前复核
- `TASK-EVAL-001` 已生成回滚前后文档 diff：原先的条件归档状态已按用户要求回滚为暂停归档；DoD #12 仍未通过、未补足，A/B 历史 commit / push 授权记录无法完整核实并作为永久治理债务保留
- `TASK-028` / `TASK-031` / `TASK-032` 仍禁止进入；后续必须按 v3 计划的角色分工执行
- `TASK-DOC-002` 已完成并归档：readonly-review 正式模板、模板路由补充、`TASK_SPEC 类型` 字段与最小 R 型 readonly-review 已收口
- `TASK-GOV-004` 已建档为 active 治理任务，用于把 PR 化多 Agent 开发治理方案 v2 转为可追踪任务；当前 Governance Mode 仍为 `LEGACY_MANUAL`，不得写 `PR_MANUAL_REVIEW` 或 `PR_REQUIRED_CHECKS` 已生效

## 活跃任务

- `TASK-GOV-004` 已建档为 active 治理任务，文件为 `tasks/active/TASK-GOV-004-pr-based-multi-agent-governance.md`。本任务仅建立 Phase 0 / Phase 1 口径和 Phase 0-6 实施路径，不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、ADR、PRD、`.github/workflows` 或 GitHub 设置。
- `TASK-GOV-003` 已完成并归档：治理提交 `515196e` 已独立审计、push，且远程同步确认 `0 0`。
- 当前 active 债务记录任务：`TASK-DEBT-001`，文件为 `tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`；已按统一七字段模板登记 4 条代码缺陷和 1 条覆盖盲区。第一批 `resolveTextEvidence()` 修复规格 `tasks/active/TASK_SPEC-DEBT-001-A-resolve-text-evidence-signals.md` 已完成角色分离试点、commit 与 push：提交 `3223d6760a977fe9deaf722e63b50bcbb6ce3611 fix(reviewengine): compute text evidence confidence signals`。GitHub 云端 post-push 独立只读核查确认远端 `master` 指向该 commit，但结论为 `NEEDS-FIX`：远端文档仍有 “未 commit / 未 push” 过期表述，且仓库内尚无可定位的 post-push 独立只读审计 `GO` 记录。第二批 `tasks/active/TASK_SPEC-DEBT-001-B-collect-pattern-candidates-value-format-signal.md` 已创建为 Draft，尚未冻结、尚未派发实现。
- `TASK-EVAL-001` 已按用户要求回滚为暂停归档，文件已从 `tasks/done/TASK-EVAL-001-evidence-overlap-evaluation.md` 移回 `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`；当前仅保留未提交 diff，不进入任何形式归档。
- `TASK-EVAL-001-A` 已完成并 push：`4bac2f4 feat(reviewengine): expose table row and cell source anchors`。
- Git 历史显示 `TASK-EVAL-001-B` 对应 commit 为 `672d97f695756249a871da53ad2821eb5146997f`。据用户提供的独立 agent 事后复核报告摘要，提交前独立复核流程曾缺失，事后复核建议为 `ACCEPT WITH CONDITIONS`。
- 据用户提供的独立 agent 定向测试复跑报告摘要，审计基线为 `CQCP_AUDIT` clean clone、HEAD `829796f2a18a87f1155eea96ed991a5fd0748b99`，四组定向测试分别报告 8/8、4/4、8/8、10/10，合计 `30/30 PASS`，且测试前后工作区干净。该结论的凭证应以独立 agent 原始报告和 console 输出为准，本文件仅记录摘要，不作为完成凭证。
- 父任务归档前独立审计随后给出 `GO WITH CONDITIONS`，Codex Review Intake Decision 为 `GO TO ARCHIVE WITH CONDITIONS`。
  【回滚批注】以上为历史记录，该决定已按用户要求回滚，不再作为当前状态依据。
- `ADR-015` 已人工接受：`decisions/ADR-015-evidence-slot-source-anchor-governance.md`。
- `TASK-027` 已完成最小主实现落地；A/B 两份 readonly-review、`TASK-027-C`、`TASK-027-D` 均已完成并作为有效前置输入。
- `TASK-027` 本轮完成范围仅限 `EvidenceSlot / SourceAnchor / coverage / SYS-*` 在现有审核链路中的兼容增强；未扩大到完整 `EvidenceBundle` 平台化实现。
- `TASK-027-C` 已完成并已本地提交：`8e09dc6 docs(contract): align TASK-027-C result API contract documentation`。
- `TASK-027-D` 已完成并已本地提交：`ed63184 fix(reviewengine): tolerate forward-compatible review result snapshots`；`PersistentTaskResultStore` 继续使用局部 tolerant-read `ObjectMapper` 副本读取 `review_result_snapshot` JSON，历史快照不回填、当前不需要数据库迁移。
- `TASK-027` 主实现已完成并已本地提交：`b85f4dd feat(reviewengine): add minimal evidence slot preflight gating`。
- `TASK-GOV-003` 收口确认工作区干净，`master` 与 `origin/master` 为 `0 0`，远端最新提交为 `515196e`。
- 当前分支状态：`master...origin/master`，本地 `master` 与 `origin/master` 已对齐。
- `git rev-list --left-right --count origin/master...HEAD` 当前为 `0 0`。
- `TASK-027` 相关 5 个本地提交已完成 `push`，当前不存在待同步提交。
- `TASK-028` / `TASK-031` / `TASK-032` 仍未开始，继续禁止抢跑。
- 下一步只允许执行一次只读 Review Intake，确认 v3 计划中的下一事项边界；不得直接派发 Claude Code / DeepSeek 实现任务。
- 开源案例 / benchmark 只读调研已完成：未发现可直接照搬的开源合同审查系统；中文开源项目仅适合作为 UI / 上传 / 报告 / 预览 / 配置交互参考，不能继承其审核架构；国际 benchmark 主要用于评测方法参考。基于该结论建立的 `TASK-EVAL-001` 已回滚为暂停归档；仍未进入 `TASK-028`。

## 最近完成

- `TASK-GOV-003` 已完成、已独立审计、已 push、远程同步确认；规范化 v3 原文 SHA-256 核验一致。
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
- `TASK_SPEC-DEBT-001-A` 已完成第一批 `resolveTextEvidence()` 文本候选信号修复审查收口：
  - `resolveTextEvidence()` 不再向 `candidateForBlock(...)` 传入连续 `true, true, true`
  - `roleLabelSignal` 基于当前 block 的 label / alias 命中计算
  - `valueFormatSignal` 基于清洗后文本候选值计算，覆盖 `"甲方：—"`、`"甲方：123"` 降级与 `"甲方：A1"` 可确认
  - `blockAttributionSignal` 基于非空 `blockId`
  - `ParserBackedReviewInputPreparerEvidenceTest`、`MinimalCandidateResolverTest`、`MinimalReviewEngineTest` 在 `apps/api-server` 下通过

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

- `TASK-EVAL-001` 暂停归档 diff 尚未 commit / push；后续 commit 与 push 必须分别重新取得用户明确授权。
- `TASK-EVAL-001` DoD #12 未通过、未补足：A/B 历史 commit / push 授权记录无法完整核实。该缺口作为历史流程治理债务永久保留，不追溯否定已 push 内容、独立审计结论或 `30/30 PASS`，也不得成为后续绕过授权门禁的先例。
- `TASK-DEBT-001` 已记录 `resolveTextEvidence()` signal 硬编码、`collectPatternCandidates()` valueFormatSignal 硬编码、parser provenance 常量覆盖、ratio early return 和 TABLE_CELL 真实 DOCX 覆盖盲区；具体证据与后续分流以任务文件为准。第一批 `TASK_SPEC-DEBT-001-A` 已通过 Codex 审查和提交前独立只读审计，并已 commit / push 为 `3223d6760a977fe9deaf722e63b50bcbb6ce3611`；post-push 独立只读核查当前为 `NEEDS-FIX`，不是 `GO`。第二批 `TASK_SPEC-DEBT-001-B` 仅完成 Draft 起草，尚未冻结或派发，provenance、ratio early return 和 TABLE_CELL 仍未获实现授权。
- Step 2 原始逐条认领报告未入库，作为治理债务保留；父任务归档判断依据为归档前独立审计对本父任务相关关键断言的重新覆盖，不得表述为原始 Step 2 报告已入库。
  【回滚批注】经独立核实，上述“重新覆盖审计”未找到可追溯的原始报告记录，该依据不能支撑归档判断。
- `TASK-EVAL-001-B` 的 `1.0 / 1.0 / 1` 是当前 canonical anchor 集合的真实计算，只证明 parser-backed 输出与 expected JSON 的一致性和回归稳定性；expected blockId、rowIndex、cellIndex 仍依赖 parser 内部稳定标识，candidateValue 来源于独立登记的 matrix，不能单独证明 parser anchor 位置客观正确，也不得表述为独立人工标注准确率。
- evaluator 已支持 `TABLE_CELL` canonical key，test-only / mock 覆盖已存在；真实 DOCX positive baseline 的 TABLE_CELL 端到端覆盖仍未完成，继续由 `TASK-DEBT-001` 和后续人工 anchor 标注任务追踪，不得宣称真实 DOCX TABLE_CELL 已验证。
- 后续治理缺口：当前 v3 门禁仍依赖文档规则、Codex 遵守、用户判断和独立 agent 审计，尚未通过 GitHub branch protection / required status checks 形成机制化硬门禁，当前门禁不具备 GitHub 机制强制能力。`TASK-GOV-004` 已建档为 active 治理任务用于分阶段治理该缺口；当前 Governance Mode 仍只能标注为 `LEGACY_MANUAL`。本轮只记录任务边界、目录口径与 Phase 0-6 路径，不修改 CI、GitHub Actions、branch protection、ruleset 或仓库设置。
- `TASK-GOV-004` 建档前只读证据：主仓库工作区干净，`master` 与 `origin/master` 对齐，本地无 `.github` 目录；截至 2026-06-23 复核，`gh` CLI 可正常使用，公开 GitHub REST API 可返回数据。此处仅记录本次时点性核查结果，后续环境状态仍可能变化，因此不得写成永久可用结论。
- `TASK-GOV-004` Phase 1 目录口径已确认：`C:\Users\1\Documents\CQCP_AUDIT` 是审计环境根目录，`C:\Users\1\Documents\CQCP_AUDIT\CQCP` 是被审计 git clone；后续审计 git 命令必须在 `CQCP_AUDIT\CQCP` 执行，不得写成在 `CQCP_AUDIT` 根目录直接执行。`audit-scratch` 建议位于 `C:\Users\1\Documents\CQCP_AUDIT\audit-scratch`，不放进被审计 clone。
- 全量 `gradle test` 仍失败于既有 `CqcpApiServerApplicationTests` 数据库连接，不作为本轮代码失败结论
- `TASK-026` 只治理最小 resolver 与 candidate 信号 admission，尚未进入完整 `EvidenceSlot / SourceAnchor` 正式治理
- `TASK-027-A` 回收结论已确认：外部 Result API 真实实现为 `GET /api/v1/tasks/{taskId}/result`，`packages/api-contracts/openapi.yaml` 与真实实现和 DTO 已分叉；`PointStatus` 五值稳定、`notConcludedReason` 六值稳定，但 `notConcludedDetail`、`missingOptionalSlots[]`、正式化 `sourceAnchors` 尚未形成真实对外承载位，因此 OpenAPI 契约对齐 / 文档更新任务阻断 `TASK-027` 直接进入实现
- `TASK-027-B` 回收结论已确认：`review_result_snapshot` 表的 JSONB 容器能力本身不是硬阻塞，当前不需要数据库迁移；但 Java 读模型仍是固定 record，历史快照兼容读取与 `ObjectMapper` 未知字段策略尚未被证明，因此 snapshot / persistence 兼容任务阻断 `TASK-027` 直接进入 persistence 相关实现
- `TASK-027-C` 已完成最小保守对齐，但只解决 Result API path / schema 文档分叉，不提供 `notConcludedDetail`、`missingOptionalSlots[]` 或 ADR-015 完整 `SourceAnchor` 的真实代码承载
- `TASK-027-D` 已完成最小兼容收口：`review_result_snapshot` 继续复用现有 JSONB 列，不引入数据库迁移；`PersistentTaskResultStore` 对快照 JSON 采用局部 `FAIL_ON_UNKNOWN_PROPERTIES=false` 的 tolerant read 策略，从而允许 ADR-015 后续兼容新增字段在不回填历史数据的前提下被旧快照读取逻辑安全忽略
- `TASK-027` 已完成最小主实现并归档；`TASK-028` / `TASK-031` / `TASK-032` 当前仍不得进入
- `TASK-EVAL-001-A` 已将 parser 真实 `tableId + rowIndex + cellIndex + joined-text range` 贯穿到 `PointEvidence / SourceAnchorSummary / ResultComposer / Query / Persistence`；跨 cell 或无法唯一映射的命中降级为 row anchor，未通过 candidateValue 搜索推断 cell
- 位置切片（`paymentClauseBlocks` 按 `MONTHLY` / `MILESTONE` 分段）在当前 4 正 4 负 fixture 上对最终判定结果无可观测影响，实际候选范围限定主要依赖内容关键词过滤（`isRatioRoleBlock` / `isExpectedRatioValue`）；后续若新增表达差异较大的合同样本，应重新验证位置切片是否真正生效，不应假设其已被验证有效
- `UNKNOWN` 仍仅由 `MinimalCandidateResolverTest` 隔离单测覆盖，尚未由真实 parser 主链路 fixture 触发

## 调研结论统一收口

- 当前未发现可直接替代 CQCP 主链路的成熟开源架构；当前主链路继续保持：`Word-first -> CandidateIndex -> CandidateResolver -> EvidenceSlot -> SourceAnchor -> 小模型局部辅助 -> 后端确定性裁判 -> ReviewResultSnapshot`。
- 国际 benchmark 仅作为审核点、证据召回、证据重合度和失败模式的评测方法参考；中文开源项目仅作为 UI、报告、预览和配置交互参考；不继承“模型直接审查整份合同并输出业务结论”的主链路。
- 更强模型可能改变候选收集、角色归属、复杂语义消歧和脆弱正则的使用比例，但不取消治理层：`SourceAnchor`、`EvidenceSlot admission`、`SYS / Finding` 分流、`NOT_CONCLUDED`、不可变快照、版本追溯及后端确定性公式/结构化裁判继续保留。
- 模型输出必须经过后端 verifier 和 `CandidateResolver`，不得直接形成业务 Finding。模型自报 confidence 不得直接成为 `HIGH` admission；模型只能提供候选、理由、anchor、uncertainty reason、alternative candidates 等可验证信号。
- `TASK-EVAL-001` 父任务的历史 Review Intake Decision 曾为 `GO TO ARCHIVE WITH CONDITIONS`；父任务归档前独立审计随后给出 `GO WITH CONDITIONS`。
  【回滚批注】以上为历史记录，该决定已按用户要求回滚，不再作为当前状态依据；DoD #1 至 #11 已确认，DoD #12 未通过且作为永久治理债务保留，不得写成 12/12 全部通过。
- `TASK-EVAL-001-A` 已补齐真实 row/cell anchor 的结果链路可观测性。
- `TASK-EVAL-001-B` 已建立 BLOCK / TABLE_ROW / TABLE_CELL canonical key 与 overlap baseline；4 个真实正向 fixture 的 `expectedRecall / actualPrecision / requiredHit` 均为 `1.0 / 1.0 / 1`，`requiredHitRate=1.0`。该结果的解释边界以当前阻塞项为准。
- 负向矩阵覆盖真实 `CONFLICTED / MEDIUM / LOW`、wrong block、same-row wrong cell、unexpected anchor、wrong row 与 unavailable anchor；candidateValue 正确但 anchor 错误时评测失败。
- `TASK-028` 应等待 `TASK-EVAL-001` 父任务完成边界冻结并形成最低评测基线后再进入，以免在缺少 evidence precision / recall 基线时比较模型或扩大模型辅助范围。
- A30 24GB 下的后续模型候选池不应预先局限于 1B / 4B / 7B，可在 `TASK-028` 前置调研中评估 Gemma 4 26B A4B、Gemma 4 31B、Qwen3 30B-A3B、Qwen3 32B 及其适用量化版本。待确认：这些候选的 Q4 权重来源、A30 24GB 实际显存占用、上下文预算、吞吐和 CQCP 中文合同质量均尚未专项验证，不得写成已确认可部署或优于当前方案。
- Docling、BM25、Ragas、Outlines、OpenTelemetry、Label Studio 等只作为 `TASK-EVAL-001` 之后的 parser adapter、候选召回、评测、结构化输出、观测和标注治理候选，不在当前 MVP 抢跑引入。

## 待确认事项

- 待确认：`TASK-EVAL-001` 完成后，`TASK-028` 与 `TASK-029` / `TASK-030` 的后续排序。
- 待确认：Gemma 4 26B A4B / 31B、Qwen3 30B-A3B / 32B 的具体权重、量化格式、license、A30 24GB 可运行性和 CQCP 样本评测方案。
## 下一步任务

1. 下一步如获用户授权，先执行 `TASK-GOV-004` Phase 1 只读目录与审计环境核实。
2. 如后续推进 `TASK-GOV-004` Phase 1，只允许只读核实目录结构和 `CQCP_AUDIT\.claude\settings.json` 权限边界，不移动目录、不创建目录、不修改文件。
3. `TASK-EVAL-001` 暂停归档如需 commit / push，仍必须分别重新取得用户明确授权。
4. `TASK_SPEC-DEBT-001-A` 已完成实现审查、提交前独立只读审计、commit 与 push；提交为 `3223d6760a977fe9deaf722e63b50bcbb6ce3611 fix(reviewengine): compute text evidence confidence signals`。GitHub 云端 post-push 独立只读核查当前结论为 `NEEDS-FIX`，原因是文档状态过期和仓库内无 post-push `GO` 记录。
5. `TASK-DEBT-001` 后续候选 `TASK_SPEC-DEBT-001-B collectPatternCandidates valueFormatSignal 修复` 已创建 Draft：`tasks/active/TASK_SPEC-DEBT-001-B-collect-pattern-candidates-value-format-signal.md`；当前尚未冻结或派发，不得直接实现。A post-push 独立只读核查 `GO` 前，不得进入 B 编码前规格映射计划。
6. 不进入 `TASK-028` / `TASK-031` / `TASK-032`；父任务暂停归档、`TASK-GOV-004` 建档、`TASK_SPEC-DEBT-001-A` 完成和 `TASK_SPEC-DEBT-001-B` 起草均不自动解除这些门禁。
7. 不派发新的 Claude Code / DeepSeek 实现任务；后续必须按 v3 计划角色分工执行，并先经过冻结 TASK_SPEC、编码前规格映射计划与 Codex 放行。

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
- `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
- `tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`
- `tasks/active/TASK-GOV-004-pr-based-multi-agent-governance.md`
- `tasks/active/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`
