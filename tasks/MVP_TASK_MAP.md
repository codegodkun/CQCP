# MVP 开发任务地图

更新日期：2026-06-12

## 1. 文档目的

本文档用于在当前 CQCP 仓库上下文下，梳理 MVP 后续开发任务地图，并为后续引入 `Claude Code + DeepSeek` 作为局部执行层预留协作边界。

本文档只回答以下问题：

* MVP 还剩哪些关键任务。
* 这些任务的优先级和依赖关系是什么。
* 哪些任务必须由 Codex 主控。
* 哪些任务在边界明确后可以派发给 `Claude Code + DeepSeek`。
* 哪些任务在继续前必须先经过人工确认。

本文档不做以下事情：

* 不创建业务代码。
* 不创建 `TASK_SPEC`。
* 不启动 `Claude Code + DeepSeek`。
* 不修改 `AGENTS.md`、`CURRENT_CONTEXT.md`、`PRD.md`、`docs/ARCHITECTURE.md`。
* 不替代 `TASK`、`ADR` 或 `CURRENT_CONTEXT.md` 的正式事实来源。

## 2. 当前阶段摘要

基于 `CURRENT_CONTEXT.md`、`PRD.md`、`docs/ARCHITECTURE.md` 和现有任务记录，当前项目已完成：

* 纯技术脚手架与环境级验证：`TASK-006`
* Flyway V1 核心 schema 基线：`TASK-015`
* MVP 开发前 4 个最小验证闭环：`TASK-016`
* 首批 expected fixtures 基线：`TASK-017`
* 最小确定性 `Review Engine` 与点级结果草案：`TASK-018`

当前项目所处阶段不是“从 0 开始定义 MVP”，而是：

* MVP 范围、主链路、结果契约、领域模型、数据库基线、首批 9 个 core review point 已冻结。
* 后端已经具备最小 parser spike、model gateway contract、tuning export、admin diagnostics、review engine draft。
* 下一阶段的主线应从“单点能力验证”转到“链路合成与最小可用产品闭环”。

当前最直接的后续主线：

1. `Result Composer + ReviewResultSnapshot`
2. `Task Execution` 最小状态机
3. Result URL 查询与最小公开结果读取
4. 普通结果页最小展示
5. 管理台任务详情最小诊断
6. parser / candidate / evidence 主链路接入
7. Gemma 局部辅助接入
8. MVP 端到端验证

## 3. MVP 剩余任务总表

| 任务编号 | 任务名称 | 目标 | 优先级 | 分类 | 是否核心链路 | Codex 责任 | Claude Code + DeepSeek 责任 | 是否需要人工确认 | 推荐模板 | 依赖关系 | 当前状态 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| TASK-019 | Result Composer + ReviewResultSnapshot 最小合成 | 将 `TASK-018` 的 `PointReviewResult[] / ReviewSummary / ReviewCompleteness / PointDiagnostic[]` 合成为正式 `ReviewResultSnapshot` 内存对象或最小持久化草案，冻结点级结果到快照结构的映射 | P0 | A | 是 | 定义快照边界、字段映射、状态口径、与 ADR/数据库的一致性；决定是否新增/拆分子任务 | 边界明确后，可局部补 DTO、mapper、测试，但不主导任务 | 否 | TASK | 依赖 `TASK-018`、`ADR-002`、`ADR-003`、`ADR-012`、`ADR-013` | 已完成 |
| TASK-020 | Task Execution 最小状态机 | 补齐 `Task / Execution / Stage` 的最小状态迁移、阶段日志和与 snapshot 的衔接，支持最小串行执行闭环 | P0 | A | 是 | 主导状态机边界、阶段顺序、失败/部分成功口径、与 execution 模型一致性 | 边界明确后，可补日志映射、状态测试、简单仓储代码 | 否 | TASK | 依赖 `TASK-019`、`ADR-003`、`ADR-012`、`TASK-015` | 已完成 |
| TASK-021 | Result URL 查询接口最小实现 | 提供最小结果查询路径，支持按 `taskId` 读取正式 snapshot，并遵守 `ADR-002` 的公开可见性边界；历史 `executionId` 选择留待后续任务 | P0 | A | 是 | 主导公开接口边界、结果可见字段、错误码和 URL 选择规则 | 可在接口签名冻结后补 controller/service/test | 否 | TASK | 依赖 `TASK-019`、`TASK-020`、`TASK-014`、`ADR-002`、`ADR-003` | 已实现待提交 |
| TASK-022 | 普通结果页最小展示 | 实现普通结果页最小 UI，围绕“审核点 → 证据 → 原文定位”展示 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED` | P1 | B | 否 | 冻结页面展示边界、字段解释口径、避免暴露技术诊断 | 按冻结接口实现页面组件、状态展示、空态/错误分支和前端测试 | 否 | TASK | 依赖 `TASK-021`、`ADR-002`、`PRD.md` 结果页要求 | 待创建 |
| TASK-023 | 管理台任务详情最小诊断 | 将当前 admin diagnostics contract 接入最小真实任务详情，展示阶段日志、审核模型、点级计数、摘要级诊断 | P1 | B | 否 | 冻结管理台最小诊断边界、决定真实接口输入输出 | 补详情页组件、接口适配、错误分支、前端测试和简单后端 view mapper | 否 | TASK | 依赖 `TASK-020`、`TASK-021`、`TASK-016` 已有诊断摘要基线 | 待创建 |
| TASK-024 | Parser / Candidate / Evidence 主链路接入 | 把当前 fixture 驱动的最小 `Review Engine` 输入，替换为来自 parser/candidate/evidence 的真实上游输入，形成正式主链路接入 | P0 | A | 是 | 主导主链路边界、替换策略、EvidenceSlot/CandidateResolver 接口；判断是否需要 ADR | 在 Codex 划定明确子范围后，可承担局部实现或测试子任务，但不能独立定义接口 | 否 | TASK | 依赖 `TASK-018`、`TASK-019`、`TASK-004`、`ADR-005`、`ARCHITECTURE` 主链路 | 待创建 |
| TASK-025 | CandidateResolver 最小真实接入 | 落地最小真实候选归属逻辑，覆盖首批 9 个审核点需要的 role resolution 与冲突边界 | P0 | A | 是 | 主导 `CandidateResolver` 最小边界、冲突/不足证据策略、与 `SYS-*` 的关系 | 明确接口后可补 unit test、简单 resolver 实现分支 | 否 | TASK / ADR（如改边界） | 依赖 `TASK-024`、`ADR-005`、`ARCHITECTURE` CandidateResolver 边界 | 待创建 |
| TASK-026 | EvidenceSlot / SourceAnchor 最小真实接入 | 把真实 evidence coverage、source anchor 和 `NOT_CONCLUDED` 原因接入结果合成与页面消费层 | P0 | A | 是 | 主导 `EvidenceSlot` 最小对象、coverage 与原文定位边界，判断是否触发 ADR | 可承担局部 mapper、测试、非边界性实现 | 否 | TASK / ADR（如改边界） | 依赖 `TASK-024`、`TASK-025`、`ADR-002`、`ARCHITECTURE` | 待创建 |
| TASK-027 | Gemma 局部辅助最小接入 | 在明确仅局部辅助的前提下，把 `MOCK` gateway 扩展到最小真实 Gemma 辅助路径，覆盖 timeout / unavailable / conflict 的真实链路映射 | P1 | A | 是 | 主导模型职责边界、允许进入模型的场景、降级规则和诊断边界 | 在接口冻结后可补 provider 接线、测试、配置读取与错误分支 | 否 | TASK | 依赖 `TASK-005`、`TASK-024`、`TASK-025`、`docs/ai-review.md` | 待创建 |
| TASK-028 | MVP 端到端最小闭环验证 | 基于现有 4 份 fixtures 和 MVP 首批链路，验证从任务创建/执行到结果读取/展示的最小端到端闭环 | P0 | A | 是 | 冻结验收口径、验证场景、判定“链路跑通”的标准 | 在验收口径明确后，可补集成测试、样例装配、前端联调验证脚本 | 否 | TASK | 依赖 `TASK-019` ~ `TASK-027` 中最小必要子集 | 待创建 |
| TASK-029 | Review assets 与规则版本引用最小落地 | 让快照、执行和结果读取显式绑定最小规则/词库/prompt/模型版本引用，满足 MVP 可追溯要求 | P1 | A | 是 | 主导版本引用范围、哪些进入快照、哪些留待 Pilot | 可补 version ref DTO、mapper、简单持久化字段映射 | 否 | TASK | 依赖 `TASK-019`、`ADR-012`、`ADR-013`、`packages/review-assets/` | 待创建 |
| TASK-030 | Result API / Admin API 非核心分支补齐 | 在核心链路和公开/管理台边界已冻结后，补错误分支、空态、字段映射、简单 mapper、测试 | P2 | B | 否 | 只做最终审查与验收，确保不越界到契约/边界变更 | 适合局部执行：补 mapper、补测试、补错误分支、补页面小组件 | 否 | TASK_SPEC | 依赖 `TASK-021`、`TASK-022`、`TASK-023` | 待创建 |
| TASK-031 | MVP 范围变更 / 新审核点 / 新输入格式评估 | 处理新增审核点、`.doc` 正式开放、PDF/OCR、新 provider、公网模型、权限边界变化等超出现有 MVP 基线的提议 | P0 | C | 是 | 负责识别是否越界、是否需要新 TASK / ADR / 人工确认 | 不执行；仅在人工确认和 Codex 新边界后才可能拆子任务 | 是 | ADR / 暂不创建 | 无固定依赖，按提议触发 | 按需触发 |
| TASK-032 | SAP/OA 正式联调范围确认 | 明确是否进入 MVP、接口收敛范围、caller policy 与外部状态语义 | P0 | C | 否 | 主导范围判断与接口边界，必要时走 ADR | 不执行；仅在人工确认后再拆后续任务 | 是 | ADR / 暂不创建 | 依赖产品/人工决策 | 按需触发 |
| TASK-033 | AGENTS / 协作路由机制更新 | 在 MVP 任务地图形成后，按实际协作机制决定是否更新 `AGENTS.md`、是否新增路由文档、是否引入父 TASK 与 TASK_SPEC 的正式路由规则 | P1 | C | 否 | 主导协作治理规则、文档优先级和执行边界 | 不执行；等待人工确认协作机制方案 | 是 | TASK / ADR / 暂不创建 | 依赖本地图文档与人工确认 | 按需触发 |

## 4. 任务分类规则

### 4.1 A 类：Codex 主控任务

适用范围：

* 涉及架构边界、核心审核链路、主数据流方向的任务。
* 涉及 `Task / Execution / ReviewResultSnapshot / Result URL / SourceAnchor / EvidenceSlot / CandidateResolver` 的任务。
* 涉及公开 API 契约、数据库基线、状态机、结果契约、模型职责边界的任务。
* 涉及 ADR 或长期项目记忆写回的任务。

执行原则：

* 先由 Codex 产出父 TASK，冻结目标、边界、依赖与验收标准。
* 未经 Codex 明确拆解，不直接下放给 `Claude Code + DeepSeek`。
* 如果任务内部可以被切成局部实现块，应由 Codex 在父 TASK 完成后，再决定是否拆 `TASK_SPEC`。

本地图中的 A 类重点：

* `TASK-019`
* `TASK-020`
* `TASK-021`
* `TASK-024`
* `TASK-025`
* `TASK-026`
* `TASK-027`
* `TASK-028`
* `TASK-029`

### 4.2 B 类：Claude Code + DeepSeek 可执行任务

适用范围：

* 已有明确接口、输入输出、错误码和边界。
* 只需要局部实现，而不需要重新定义链路和契约。
* 补 mapper、补测试、补页面组件、补错误分支、补局部 bug。

执行原则：

* 必须先有 Codex 产出的父 TASK。
* 必须在边界冻结后，再决定是否生成 `TASK_SPEC`。
* `Claude Code + DeepSeek` 只执行局部实现，不做架构重定义，不写长期项目记忆。

本地图中的 B 类重点：

* `TASK-022`
* `TASK-023`
* `TASK-030`

### 4.3 C 类：必须人工确认任务

适用范围：

* 涉及 MVP 边界变化、产品范围扩大或长期治理边界变化。
* 涉及新增审核点、新输入格式、新模型提供方、公网模型、数据库基线调整、权限边界、SAP/OA 正式联调范围。
* 涉及是否修改协作治理文件，例如 `AGENTS.md`、未来的路由机制文件等。

执行原则：

* 先人工确认方向，再由 Codex 决定是否创建 TASK 或 ADR。
* 在人工确认前，推荐模板只能是 `ADR` 或 `暂不创建`，不直接进入实现。

本地图中的 C 类重点：

* `TASK-031`
* `TASK-032`
* `TASK-033`

## 5. 推荐推进顺序

建议按以下顺序推进 MVP 主线：

1. `TASK-019 Result Composer + ReviewResultSnapshot`
2. `TASK-020 Task Execution 最小状态机`
3. `TASK-021 Result URL 查询接口最小实现`
4. `TASK-022 普通结果页最小展示`
5. `TASK-023 管理台任务详情最小诊断`
6. `TASK-024 Parser / Candidate / Evidence 主链路接入`
7. `TASK-025 CandidateResolver 最小真实接入`
8. `TASK-026 EvidenceSlot / SourceAnchor 最小真实接入`
9. `TASK-027 Gemma 局部辅助最小接入`
10. `TASK-028 MVP 端到端最小闭环验证`
11. `TASK-029 Review assets 与规则版本引用最小落地`
12. `TASK-030 非核心分支补齐`

说明：

* `TASK-024 ~ TASK-027` 是“从当前最小验证实现过渡到真实主链路”的关键区段，不建议被误降级为普通局部编码任务。
* `TASK-022 / TASK-023 / TASK-030` 虽可局部委派，但前提是 `TASK-019 ~ TASK-021` 已冻结结果与接口边界。
* `TASK-031 ~ TASK-033` 不是当前 MVP 主线实现任务，而是范围/协作治理的变更入口。

## 6. 对协作机制的当前判断

### 6.1 当前适合立即下放给 Claude Code + DeepSeek 的任务

当前阶段不建议直接下放新的主线任务。

原因：

* 下一步首要任务 `TASK-019` 仍属于快照边界与结果结构冻结区，必须由 Codex 主控。
* `TASK-020` 属于状态机与 execution 边界，也必须由 Codex 主控。
* 在 `TASK-019 / TASK-020 / TASK-021` 完成前，适合派发的任务仍主要是“局部补实现任务”，而不是新的主线父任务。

### 6.2 当前适合未来以 TASK_SPEC 派发的任务类型

待 `TASK-019 ~ TASK-021` 冻结后，以下类型适合形成 `TASK_SPEC`：

* 普通结果页局部组件实现
* 管理台任务详情局部组件实现
* API mapper / view model / DTO 映射
* 错误分支补齐
* 前端/后端局部测试补齐

### 6.3 当前不应下放的任务类型

* 审核结果正式结构
* 状态机
* 数据库基线调整
* 对外接口契约
* `CandidateResolver / EvidenceSlot / ReviewPointFamily` 边界
* Gemma 职责边界
* 任何可能触发 ADR 的事项

## 7. 当前结论

当前最合理的协作模式是：

* Codex 继续担任项目总控、任务拆解者、边界定义者和最终审查者。
* 先由 Codex 建立并执行 `TASK-019`、`TASK-020`、`TASK-021` 这条主线父任务。
* 待这些父任务冻结接口和边界后，再从其中切出 B 类局部任务，用 `TASK_SPEC` 派发给 `Claude Code + DeepSeek`。
* 对于涉及范围变化或协作治理变化的内容，先走人工确认，不在本阶段直接下放。

## 8. to-issues 触发规则

* 本文档不直接拆分 issue，也不直接生成 `TASK_SPEC`。
* 当某个任务从本地图中被选中，并正式创建为 `tasks/active/` 下的父 `TASK` 后，Codex 再判断是否需要执行 to-issues 拆分。
* 如该父任务存在跨模块、多个交付物、前后端联动、核心链路拆分，或后续可能派发给 `Claude Code + DeepSeek` 的情况，应先输出 issue 草案，再决定哪些 issue 转为 `TASK_SPEC`。
* 流程为：

```text
MVP_TASK_MAP.md → 父 TASK → to-issues 草案 → 确认 issue → TASK_SPEC → Claude Code + DeepSeek 执行
```

* 不得在 `MVP_TASK_MAP.md` 阶段直接拆 issue，避免任务地图变成执行 backlog。
