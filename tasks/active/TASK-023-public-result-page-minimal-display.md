# TASK-023：普通结果页最小展示
状态：已完成最小实现，待提交
类型：B 类前端父任务

优先级：P1
负责人：Codex
创建日期：2026-06-13

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`docs/frontend.md`、`docs/ARCHITECTURE.md`、`decisions/ADR-002-v1-result-and-diagnostic-contract.md`、`tasks/active/TASK-021-result-url-query-api.md`、`tasks/active/TASK-022-persistent-result-query-adapter.md`

## 任务命名规范

* 任务文件名使用英文 slug，格式为 `TASK-xxx-english-slug.md`。
* 任务文件标题使用中文，格式为 `# TASK-xxx：中文任务名`。
* 任务正文以中文为主。

## 背景

`TASK-021` 已冻结普通结果查询 API 的最小公开契约，`TASK-022` 已把结果查询来源切换到持久化 `query adapter`，当前已经具备“基于 `taskId` 读取历史 `ReviewResultSnapshot`”的结果读取前提。

按照 `AGENTS.md`、`docs/ARCHITECTURE.md`、`docs/frontend.md` 与 `ADR-002` 的共同约束，普通结果页必须围绕“审核点 -> 证据 -> 原文定位”解释审核结果，只暴露业务化结果，不暴露 `SYS-*` 技术诊断、prompt、raw output、endpoint、stack trace 或管理台诊断详情。

因此，`TASK-023` 的目标不是扩展审核链路或修改结果契约，而是在现有结果查询与快照边界之上，冻结普通结果页最小展示范围、输入依赖、展示安全边界、验收口径与后续拆分方向，为后续实现提供稳定父任务边界。

## 目标

* 冻结普通结果页最小展示父任务边界，明确该任务只消费既有 `ReviewResultSnapshot` / Result API 能力，不倒推修改审核链路。
* 明确普通结果页最小展示必须覆盖的业务对象：顶部摘要、审核点卡片、证据解释、原文定位、`NOT_CONCLUDED` 人工核对提示、`SKIPPED` 适用性原因。
* 明确普通结果页默认展示进入 `ReviewExecutionPlan` 的全部点级结果：`PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED`。
* 明确普通结果页与 `TASK-024` 管理台最小诊断、`TASK-031` Result API / Admin API mapper 的边界分工。
* 为后续实现或拆分提供父任务级验收标准、风险提示和暂停条件。

## 非目标

* 不写前端页面代码、路由代码、状态管理代码或样式代码。
* 不写后端代码，不修改 `TASK-021` / `TASK-022` 已冻结的 API 语义。
* 不修改数据库迁移、表结构、`PRD.md`、`docs/ARCHITECTURE.md` 或 Docker 配置。
* 不创建 `TASK_SPEC`，不进入 `TASK-023` 具体实现。
* 不把管理台任务详情、诊断明细、预算诊断或 correction/deep review 入口混入普通结果页范围。

## 输入

* 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`docs/frontend.md`、`docs/context-management.md`
* 相关架构章节：`docs/ARCHITECTURE.md` 第 14 章普通结果页及相关结果解释章节
* 相关 ADR：`decisions/ADR-002-v1-result-and-diagnostic-contract.md`、`decisions/ADR-003-task-execution-snapshot-model.md`、`decisions/ADR-012-domain-model-freeze.md`
* 上游任务：`TASK-021`、`TASK-022`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务文件
* `docs/frontend.md`
* `docs/context-management.md`
* `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
* `tasks/active/TASK-021-result-url-query-api.md`
* `tasks/active/TASK-022-persistent-result-query-adapter.md`

### Optional Context

* `PRD.md`
* `docs/backend.md`
* `docs/sap.md`
* `docs/ARCHITECTURE.md`
* `decisions/ADR-003-task-execution-snapshot-model.md`
* `decisions/ADR-012-domain-model-freeze.md`

### Out of Scope

* `TASK-024` 管理台任务详情最小诊断
* `TASK-031` Result API / Admin API mapper 调整
* 预算诊断、`requiresHigherBudget`、`recommendedBudgetProfile`
* “申请深度审核”入口
* correction execution、合同类型修正入口、授权访问控制
* 新增审核链路、模型职责、EvidenceSlot、CandidateResolver 机制变更

## 范围

### 包含

* 普通结果页父任务边界与交付目标冻结。
* 页面最小展示结构冻结：
  * 左侧合同原文/预览。
  * 右侧审核点结果卡片。
  * 点击审核点或证据时，驱动左侧原文定位。
* 页面最小业务摘要冻结：
  * `plannedPointCount`
  * `passCount`
  * `errorCount`
  * `warningCount`
  * `notConcludedCount`
  * `skippedCount`
* 点级卡片最小展示冻结：
  * 审核点名称/展示编码/状态
  * 业务解释
  * 证据定位入口
  * 原文定位反馈
  * `NOT_CONCLUDED` 原因与人工核对提示
  * `SKIPPED` 适用性原因
* 普通结果页展示安全边界冻结：
  * 不展示 `SYS-*`
  * 不展示 prompt、raw output、endpoint、stack trace、admin logs、secret
  * 不展示管理台诊断明细、预算诊断、`notConcludedDetail`
* 页面与后续任务的边界说明冻结：
  * 普通结果页只读既有结果契约
  * 诊断深挖交由 `TASK-024`
  * 若实现阶段发现公开视图字段不足，仅记录到后续任务，不在本父任务阶段扩 scope

### 不包含

* 任意前端组件、页面、路由、样式、测试实现
* 任意后端 DTO、mapper、controller、service 调整
* 任意数据库表、JSONB 字段、Flyway migration 修改
* 任意管理台 UI、任务详情页、调优包展示
* 任意对外权限、结果 URL 加密分发、单点登录或访问控制增强

## 约束

* 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务文件。
* 按需阅读 `docs/frontend.md`、`docs/context-management.md`、`ADR-002` 与相关上游任务，不默认全量读取项目文档。
* 普通结果页必须围绕“审核点 -> 证据 -> 原文定位”解释，不得只展示结论标签。
* 普通结果页默认展示进入 `ReviewExecutionPlan` 的全部点级结果，不得默认隐藏 `PASS` 或 `SKIPPED`。
* `ERROR / WARNING` 计入业务风险数量；`PASS / NOT_CONCLUDED / SKIPPED / SYS-*` 不计入业务风险数量。
* 被配置停用且未进入执行计划的审核点，不进入普通结果页主摘要分母，也不在结果页输出 `SKIPPED` 或 `NOT_CONCLUDED`。
* 第一轮 MVP 普通结果页不展示 `requiresHigherBudget`、`recommendedBudgetProfile`，也不提供“申请深度审核”入口。
* 普通结果页只展示业务化 `NOT_CONCLUDED` 原因，不展示内部 `SYS-*` 技术码。
* 历史结果页必须读取快照中的当次展示字段，不读取当前最新配置覆盖历史。
* 若定位能力仅降级到预览页/章节，页面需提示定位降级；若未达到审核点要求的最低定位等级，不得伪装为可靠结论。
* 如需改变结果契约、审核链路、模型职责、SYS/Finding 边界或证据机制，必须先走 ADR；本任务当前不触发 ADR。

## 交付物

* `tasks/active/TASK-023-public-result-page-minimal-display.md` 父任务文件
* `CURRENT_CONTEXT.md` 中关于 `TASK-023` 的当前阶段与下一步摘要
* `changelog/2026-06.md` 的父任务建档记录
* 视需要更新的 `tasks/MVP_TASK_MAP.md` 任务地图状态

## 验收标准

* 父任务文件完整定义目标、非目标、边界、输入依赖、约束、验收标准和后续风险。
* 文档明确 `TASK-023` 只针对普通结果页最小展示，不包含 `TASK-024` 管理台诊断和 `TASK-031` mapper 补洞。
* 文档明确当前阶段不写任何前端、后端、数据库、Docker 或架构文档修改。
* 文档明确普通结果页的公开面只消费既有 `TASK-021` / `TASK-022` 结果查询能力和快照字段。
* Memory Writeback 完成：`CURRENT_CONTEXT.md`、`changelog/2026-06.md`、本任务文件已更新；`tasks/MVP_TASK_MAP.md` 如有地图层变化则同步。

## 测试与验证

* 父任务建档阶段已完成；本轮在既有边界内完成最小前端实现与验证。
* 已执行：
  * `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example ps`
  * `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example exec postgres pg_isready -U cqcp -d cqcp`
  * `npm.cmd run test`
  * `npm.cmd run build`
  * `npm.cmd run lint`

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是
* 是否需要更新 `docs/*.md`：否
* 是否需要更新 `changelog/当前月份.md`：是
* 是否需要新增或更新 ADR：否
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：视任务地图状态是否需要同步为“已建档，待实现”

## Next Task Handoff

* 本任务完成后，允许后续窗口在同一父任务边界内继续推进 `TASK-023` 实现准备或实现执行。
* 若后续仅做实现前准备，仍不得顺手扩展到 `TASK-024`、`TASK-031`、预算诊断或管理台诊断详情。
* 若后续实现阶段发现需要补公开视图字段或 mapper 适配，应先记录风险并评估是否转入 `TASK-031`，不得直接回写架构边界。

## 风险

* 普通结果页实现可能发现当前公开结果视图缺少某些纯展示字段；该类缺口属于后续实现风险，不在本父任务建档阶段解决。
* `SourceAnchor` 粒度、预览定位降级和冲突证据展示可能在实现阶段暴露前端交互复杂度，但当前只冻结展示边界，不提前设计实现细节。
* `TASK-024` 与 `TASK-031` 若边界失守，容易把管理台诊断细节或 mapper 返工混入普通结果页父任务。

## 待确认

* 待确认：普通结果页在 `admin-web` 内的最终路由命名与页面文件拆分方案，由后续实现阶段在不突破本任务边界前提下确定。
* 待确认：后续是否需要把 `TASK-023` 进一步拆为实现型 `TASK_SPEC`，本轮不创建。

## 完成记录

* 完成日期：2026-06-13
* 变更文件：
  * `tasks/active/TASK-023-public-result-page-minimal-display.md`
  * `apps/admin-web/src/App.tsx`
  * `apps/admin-web/src/App.test.tsx`
  * `apps/admin-web/src/styles.css`
  * `apps/admin-web/src/publicResult/api.ts`
  * `apps/admin-web/src/publicResult/types.ts`
  * `apps/admin-web/src/publicResult/PublicResultPage.tsx`
  * `CURRENT_CONTEXT.md`
  * `changelog/2026-06.md`
  * `tasks/MVP_TASK_MAP.md`
* 测试结果：
  * `npm.cmd run test`：通过（4 tests passed）
  * `npm.cmd run build`：通过
  * `npm.cmd run lint`：通过
  * `docker compose ... ps`：3 个服务 `Up`
  * `docker compose ... pg_isready`：`accepting connections`
* 遗留问题：
  * 当前公开页只能基于 `SourceAnchorSummary` 提供 block 级定位摘要，暂无合同全文预览能力。
  * `TASK-024` 管理台诊断详情与 `TASK-031` mapper 补洞仍未进入。
* 备注：
  * 本轮未修改后端、数据库迁移、`PRD.md`、`docs/ARCHITECTURE.md` 或 Docker 配置。
  * 本轮未创建 `TASK_SPEC`，未分配 Claude Code / DeepSeek。
