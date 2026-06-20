# TASK-027：EvidenceSlot / SourceAnchor 正式治理

状态：进行中（ADR 已接受，前置兼容任务待完成，禁止进入实现）

类型：A 类主链路任务 / 架构治理与后端实现

优先级：P0

负责人：Codex

创建日期：2026-06-20

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`docs/ARCHITECTURE.md`、`decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`

## 背景

`TASK-025` 已接通 parser-backed candidate / evidence 最小主链路，`TASK-026` 已完成最小 `CandidateResolver` 五档置信度与 `HIGH` admission gate 治理。

当前实现仍以 `PointEvidence` 和 `SourceAnchorSummary` 表达最小证据结果，尚未形成架构要求的正式 `EvidenceSlot Preflight`、逐 slot coverage、截断后校验和完整 `SourceAnchor` 定位资产。直接继续实现会触碰 `EvidenceSlot` 机制、核心审核链路和结果证据契约，因此必须先通过 ADR 冻结边界。

## 目标

* 先完成并接受 `ADR-015`，冻结 `EvidenceSlot / SourceAnchor` 的正式职责、最小字段、执行顺序和降级规则。
* 在 ADR 接受后，把当前最小 candidate admission 提升为可追溯的 `EvidenceSlot Preflight`。
* 为业务 `PASS / ERROR / WARNING` 建立 required slot 与可靠 `SourceAnchor` 的强制门禁。
* 保持 `SYS-*` 系统诊断与业务 Finding 分流，证据不足时不得静默回灌全文。
* 用首批 9 个审核点和既有 parser-backed fixture 验证正式 evidence 生命周期。

## 非目标

* 不接入 Gemma Provider，不进入 `TASK-028`。
* 不拆分 `ParserBackedReviewInputPreparer`，不进入 `TASK-032`。
* 不新增审核点、`ReviewPointFamily`、`CandidateRole` 或合同类型。
* 不修改数据库基线、Docker、部署拓扑或 SAP/OA 集成。
* 不把页码级或精确字符范围定位设为 MVP 硬依赖。
* 不重做公开结果页或管理台 UI。

## 输入

* 相关文档：`docs/ARCHITECTURE.md`、`docs/backend.md`、`docs/ai-review.md`
* 相关架构章节：9.2、9.6、10.1、10.4、10.5、10.9、结果合成与 `SourceAnchor` 定义
* 相关 ADR：`decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`、`decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* 上游任务：`tasks/done/TASK-025-parser-candidate-evidence-mainline-integration.md`、`tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/MVP_TASK_MAP.md`
* `docs/ARCHITECTURE.md`
* `docs/backend.md`
* `docs/ai-review.md`
* `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
* `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`

### Optional Context

* `changelog/2026-06.md`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ResultComposer.java`
* 相关 review engine 测试

### Out of Scope

* `TASK-028` Gemma `MEDIUM` 档辅助通道。
* `TASK-032` 按 `ReviewPointFamily` 物理拆分类重构。
* 新 API、数据库迁移或前端页面实现。
* 新规则、正则、合同样本扩面或开放式补规则。
* `EvidenceBundle / FamilyEvidencePlan` 的完整平台化实现。

## 范围

### 包含

* `ADR-015` 的起草、评审、修订与接受。
* 首批审核点的最小 `EvidenceSlot` 定义及版本归属。
* required / optional slot、accepted roles、候选基数、置信度门槛和 fallback 边界。
* `CandidateResolver -> EvidenceSlot Preflight -> deterministic verdict` 的单向执行门禁。
* slot coverage、缺失、歧义、解析低置信和预算截断的 `SYS-*` 映射。
* 正式 `SourceAnchor` 最小契约与 block / section 级定位；表格行/单元格通过 `regionType` 与 `previewElementRef` 表达。
* 既有 fixture 的回归验证和必要的新增治理测试。

### 不包含

* ADR 未接受前的业务代码修改。
* Gemma 辅助消歧实现。
* `ReviewPointFamily` taxonomy 变化。
* 复杂 token budget、跨族 bundle 或模型调用预算实现。
* 对历史结果快照做不可逆迁移。

## 阶段门禁

### 阶段 1：ADR 前置

* 起草 `ADR-015`。
* 复核其与 `docs/ARCHITECTURE.md`、`ADR-014`、首批 9 个审核点的一致性。
* 在 ADR 被人工接受前，不拆 execution 型 `TASK_SPEC`，不修改业务代码。

### 阶段 2：任务边界冻结

* `ADR-015` 已被人工接受，但当前仍未进入实现。
* 根据已接受 ADR 明确实际修改文件、测试矩阵和兼容策略。
* `TASK-027-A-openapi-contract-readonly-review` 已回收并被 Codex 接受为有效前置输入。
* `TASK-027-B-snapshot-persistence-readonly-review` 已回收并被 Codex 接受为有效前置输入。
* 已冻结 `TASK-027-C`：OpenAPI 契约对齐 / 文档更新任务，作为实现前置。
* 已冻结 `TASK-027-D`：snapshot / persistence 兼容任务，作为实现前置。
* 在 `TASK-027-C` / `TASK-027-D` 完成并经 Codex 审查前，不得进入 evidence / SourceAnchor / slot preflight 实现。
* execution 型 `TASK_SPEC` 继续冻结，不得提前派发。
* 如 ADR 结论影响任务顺序、依赖或协作边界，同步更新 `tasks/MVP_TASK_MAP.md`。

### 阶段 3：实现与验收

* 仅在阶段 1、2 完成后进入。
* 使用测试先行方式实现正式 slot preflight 与 anchor 契约。
* 不吞并 `TASK-028` 或 `TASK-032`。

## 约束

* `ReviewExecutionPlan -> CandidateResolver -> EvidenceSlot Preflight` 的顺序不得反转。
* `CandidateResolver` 仍只做保守的角色归属；`EvidenceSlot` 负责点级证据需求与 coverage 判定。
* 只有 required slot 满足门槛且具备可靠 `SourceAnchor` 时，才允许产出业务 `PASS / ERROR / WARNING`。
* required slot 缺失、歧义、低置信或被关键截断时，必须输出对应 `SYS-*` 并映射为 `NOT_CONCLUDED`。
* 不适用审核点输出 `SKIPPED`，不要求 `EvidenceSlot` 或 `SourceAnchor`。
* `SourceAnchor` 一期最低保证 block / section 级定位；表格行/单元格通过 `regionType` 与 `previewElementRef` 表达；缺少精确字符范围本身不得导致 `NOT_CONCLUDED`。
* `resolverPolicy=gemmaIfAmbiguous` 在本任务中只定义资格与降级语义，不实现模型调用。
* 所有 slot 定义和 evidence policy 必须进入可追溯版本，不得成为无版本的运行时常量。

## 交付物

* `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* 已冻结的 `TASK-027` 父任务包
* ADR 接受后的最小正式 `EvidenceSlot` / `SourceAnchor` 后端实现
* 对应单元测试、parser-backed 回归测试和结果合成回归测试
* `CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-06.md` 记忆写回

## 验收标准

### ADR 前置阶段

* ADR 明确 slot 定义、运行时 coverage、`SYS-*` 映射、anchor 契约、版本归属与兼容策略。
* ADR 明确不进入 `TASK-028` / `TASK-032`。
* ADR 状态在人工接受前保持 `Draft`。
* 业务代码保持不变。

### 最终实现阶段

* 首批 9 个审核点均有明确的 required / optional slot 定义。
* required slot 未满足时不能产出业务 Finding 或 `PASS`。
* `HIGH` candidate 只有通过 slot preflight 后才能进入确定性裁判。
* `MEDIUM / LOW / CONFLICTED / UNKNOWN` 保持可解释降级，不被 slot fallback 提升为伪 `HIGH`。
* `PASS / ERROR / WARNING` 结果均带至少 block / section 级 `SourceAnchor`；表格证据能够定位到对应行/单元格引用。
* `SKIPPED` 不要求 anchor；`NOT_CONCLUDED` 可保留诊断候选定位，但不得伪装成可靠业务证据。
* 既有 `TASK-025 / TASK-026` 目标回归保持通过。

## 测试与验证

ADR 前置阶段只执行文档一致性检查：

* `git diff --check`
* 检查 ADR 与 `docs/ARCHITECTURE.md`、`ADR-014` 是否冲突
* 检查未修改业务代码

实现阶段的具体测试命令和测试文件在 ADR 接受、任务边界冻结后补入本任务包；当前不得提前虚构实现细节。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是。
* 是否需要更新 `docs/*.md`：当前否；只有发现架构正文确需变更时，另行提出并先评审。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要新增或更新 ADR：是，先起草并接受 `ADR-015`。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：是，记录父任务当前受前置兼容任务门禁约束。

## Next Task Handoff

* `ADR-015` 已接受，但当前下一步不是进入业务实现。
* 当前只能先执行 `TASK-027-C` 与 `TASK-027-D` 两个前置兼容任务。
* 在 `TASK-027-C` / `TASK-027-D` 完成并经 Codex 审查前，不生成 execution 型 `TASK_SPEC` 或实现型 Handoff Prompt。

## 风险

* 当前 `PointEvidence` / `SourceAnchorSummary` 是过渡结构，若直接扩字段可能把运行时 slot 状态、结果契约和持久化耦合在一起。
* `SourceAnchor` 正式化可能影响结果快照兼容性，必须先决定新增字段的兼容策略。
* 首批 9 个审核点若一次性定义过多 slot，会造成 MVP 过度平台化；必须坚持最小 required slot。
* 若在本任务中顺手接入 Gemma 或拆分单体类，会模糊 `TASK-028 / TASK-032` 边界。

## 待确认

* 待 `TASK-027-C` 确认 `missingOptionalSlots[]` 的最小 external contract 承载方式与 `notConcludedDetail` 的 optional / diagnostic-only 语义。
* 待 `TASK-027-D` 确认 `PersistentTaskResultStore` 的 `ObjectMapper` 未知字段策略，以及新增字段 tolerant read 的最小实现方式。

## 前置审查回收

### `TASK-027-A` 回收结论

* 已完成并被 Codex 接受为有效前置审查输入。
* 已确认外部 Result API 真实实现为 `GET /api/v1/tasks/{taskId}/result`。
* 已确认 `packages/api-contracts/openapi.yaml` 与真实实现和 DTO 已分叉。
* 已确认 `PointStatus` 五值稳定集合保持不变。
* 已确认 `notConcludedReason` 六值稳定语义集合保持不变。
* 已确认 `notConcludedDetail`、`missingOptionalSlots[]`、正式化 `sourceAnchors` 尚未形成真实对外承载位。
* 结论：OpenAPI 契约对齐 / 文档更新任务阻断 `TASK-027` 直接进入实现。

### `TASK-027-B` 回收结论

* 已完成并被 Codex 接受为有效前置审查输入。
* 已确认 `review_result_snapshot` 表的 JSONB 容器能力本身不是硬阻塞。
* 已确认 Java 读模型仍是固定 record。
* 已确认历史快照兼容读取策略尚未被证明。
* 已确认新增字段与 `ObjectMapper` 未知字段策略仍需收口。
* 已确认当前不需要数据库迁移。
* 结论：snapshot / persistence 兼容任务阻断 `TASK-027` 直接进入 persistence 相关实现。

## 冻结的前置任务

### `TASK-027-C` OpenAPI 契约对齐 / 文档更新任务

* 目标：
  * 对齐 `packages/api-contracts/openapi.yaml` 与真实 Result API 实现 `GET /api/v1/tasks/{taskId}/result`
  * 明确 `PointStatus` 五值保持不变
  * 明确 `notConcludedReason` 六值保持不变
  * 明确 `notConcludedDetail` 的 optional / diagnostic-only 语义
  * 明确 `sourceAnchors` 的兼容新增 / 字段稳定化边界
  * 明确 `missingOptionalSlots[]` 的对外承载方式
  * 不改业务裁判逻辑
  * 不改数据库
  * 不进入 `TASK-027` evidence 实现
* 允许修改范围：
  * `packages/api-contracts/openapi.yaml`
  * 如存在 API contract fixture / example，可最小修改与 Result API 文档直接相关的示例
  * `CURRENT_CONTEXT.md`
  * `tasks/active/TASK-027-evidence-slot-source-anchor-governance.md`
  * `changelog/2026-06.md`
* 禁止范围：
  * 不修改业务代码
  * 不修改数据库迁移
  * 不修改 `PRD.md`
  * 不修改 `docs/ARCHITECTURE.md`
  * 不修改 Docker / Compose
  * 不进入 `TASK-028` / `TASK-031` / `TASK-032`
* 验收：
  * OpenAPI 与真实 `GET /api/v1/tasks/{taskId}/result` 至少在路径、主要返回结构、`PointStatus`、`notConcludedReason`、`notConcludedDetail`、`sourceAnchors`、`missingOptionalSlots[]` 上完成契约对齐
  * 不删除、不重命名既有外部字段
  * 兼容新增字段明确为 optional
  * `git diff --check` 通过
  * 最终由 Codex `Review Intake Decision` 判断是否完成

### `TASK-027-C` 完成结论

* 已完成最小保守对齐。
* `packages/api-contracts/openapi.yaml` 已从旧 `/api/review/results/{taskId}` 对齐到真实 `GET /api/v1/tasks/{taskId}/result`。
* 结果契约已改为贴合当前真实 `ReviewResultSnapshot` 返回结构，而非旧的 `ReviewResultViewResponse` 包装视图。
* `PointStatus` 五值稳定集合保持不变。
* `notConcludedReason` 六值稳定语义集合保持不变。
* `notConcludedDetail` 已作为 optional / diagnostic-only compatibility 字段文档化；当前不代表真实 DTO 已承载。
* `missingOptionalSlots[]` 已作为 optional compatibility 字段文档化；当前不代表真实 DTO 已承载。
* `sourceAnchors` 已文档化为“当前最小稳定 `SourceAnchorSummary` 字段 + 兼容新增可选字段”，不声称 ADR-015 完整 `SourceAnchor` 已实现。
* 本任务未修改业务代码、未修改前端 `types.ts`、未修改数据库、未进入 `TASK-027-D` 或 `TASK-027` 主实现。

### `TASK-027-D` snapshot / persistence 兼容任务

* 目标：
  * 收口 `ReviewResultSnapshot` / persistence 对 ADR-015 新增语义的兼容策略
  * 明确 Java 读模型如何兼容新增字段
  * 明确 `ObjectMapper` 未知字段策略
  * 明确历史快照兼容读取策略
  * 明确是否需要后续数据库/快照兼容任务
  * 当前默认不做数据库迁移，除非另行 ADR / TASK 接受
* 允许修改范围：
  * `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/` 下与 `ReviewResultSnapshot`、`PersistentTaskResultStore`、result composition、snapshot DTO 直接相关的最小文件集
  * 与历史快照兼容读取直接相关的测试或 fixture
  * `CURRENT_CONTEXT.md`
  * `tasks/active/TASK-027-evidence-slot-source-anchor-governance.md`
  * `changelog/2026-06.md`
* 禁止范围：
  * 不修改数据库迁移
  * 不修改 `PRD.md`
  * 不修改 `docs/ARCHITECTURE.md`
  * 不修改 Docker / Compose
  * 不进入 `TASK-028` / `TASK-031` / `TASK-032`
  * 不接入 Gemma Provider
  * 不改 `PointStatus` 稳定集合
  * 不改 `notConcludedReason` 稳定语义集合
* 验收：
  * 历史 `ReviewResultSnapshot` 可兼容读取
  * 新增字段保持 optional / tolerant read
  * 不要求历史数据回填
  * 不引入数据库迁移
  * 若发现必须迁移，必须 STOP 并回交 Codex，不得直接实现迁移
  * `git diff --check` 通过
  * 最终由 Codex `Review Intake Decision` 判断是否完成

## 完成记录

* 完成日期：未完成。
* 变更文件：待任务完成后填写。
* 测试结果：`TASK-027-C` 当前仅完成 OpenAPI / 文档层对齐，并将通过 `git diff --check` 验证；尚未进入实现测试。
* 遗留问题：`TASK-027-D` 尚未执行，execution 型 `TASK_SPEC` 继续冻结。
* 备注：`ADR-015` 已人工接受；`TASK-027-C` 已完成但 `TASK-027-D` 仍未完成，因此仍不得进入 evidence / SourceAnchor / slot preflight 实现，不得进入 `TASK-028` / `TASK-031` / `TASK-032`。
