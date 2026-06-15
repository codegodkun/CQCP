# MVP 任务地图

更新日期：2026-06-15

## 2026-06-15 任务地图更新

- `TASK-024` 已完成方案 A 前端最小实现。
- `TASK-024` 当前只消费现有 `ReviewResultSnapshot` 字段，不补后端字段、不新增管理台诊断接口。
- `TASK-024` 已完成实现前探查。
- `TASK-024` 已确认采用方案 A，只消费现有 `ReviewResultSnapshot` 字段推进前端最小实现。
- 已确认 `TASK-024` 若只消费现有 `ReviewResultSnapshot` 字段，可继续留在前端最小实现边界内。
- 已确认 `TASK-024` 若要求 `contractName / currentStage / model summary / stageLogs`，则存在字段缺口，需先判断是否进入 `TASK-031` 或单独管理台接口任务。
- `TASK-031` 仍未进入，当前只作为字段缺口处理方向保留。

## 1. 目的

本文档只记录 MVP 层面的任务顺序、任务分类、协作边界和模板选择，不记录具体实现细节。

如任务状态、依赖关系、协作边界或优先级发生变化，Codex 需要同步更新本文档。

## 2. 已完成主链路

| 任务 | 名称 | 优先级 | 类别 | 模板 | 当前状态 | 备注 |
|---|---|---|---|---|---|---|
| `TASK-019` | Result Composer + ReviewResultSnapshot 最小合成 | P0 | A | `TASK` | 已完成 | 结果快照正式边界已形成 |
| `TASK-020` | Task Execution 最小状态机 | P0 | A | `TASK` | 已完成 | 最小串行执行闭环已形成 |
| `TASK-021` | Result URL 查询接口最小实现 | P0 | A | `TASK` | 已完成并提交 | commit：`c5e4ddd` |
| `TASK-022` | Persistent Result Query Adapter 最小持久化查询适配层 | P0 | A | `TASK` | 已完成并提交 | commit：`1a206d7`，父任务建档 commit：`dde34dd` |
| `TASK-023` | 普通结果页最小展示 | P1 | B | `TASK` | 已完成并提交 | commit：`b7ab8db`，父任务建档 commit：`13d9783` |
| `TASK-024` | 管理台诊断详情最小展示 | P1 | B | `TASK` | 已完成最小实现 | 仅消费现有 `ReviewResultSnapshot` 字段，待人工审阅/提交 |

## 3. 当前后续任务

| 任务 | 名称 | 优先级 | 类别 | 模板 | 依赖 | 当前状态 | 说明 |
|---|---|---|---|---|---|---|---|
| `TASK-025` | Parser / Candidate / Evidence 主链路接入 | P0 | A | `TASK` | `TASK-018`、`TASK-019`、`TASK-004`、相关 ADR | 未开始 | 触碰主审核链路与证据机制 |
| `TASK-026` | CandidateResolver 机制落地 | P0 | A | `TASK / ADR` | `TASK-025`、相关 ADR | 未开始 | 涉及 CandidateResolver 边界 |
| `TASK-027` | EvidenceSlot / SourceAnchor 机制落地 | P0 | A | `TASK / ADR` | `TASK-025`、`TASK-026`、相关 ADR | 未开始 | 涉及定位与证据覆盖边界 |
| `TASK-028` | Gemma Provider 最小接入 | P1 | A | `TASK` | `TASK-005`、`TASK-025`、`TASK-026` | 未开始 | 涉及 provider 行为与失败分支 |
| `TASK-029` | MVP 端到端验证收口 | P0 | A | `TASK` | `TASK-019` ~ `TASK-028` | 未开始 | 端到端主链路验证与收口 |
| `TASK-030` | Review assets 版本化治理 | P1 | A | `TASK` | `TASK-019`、`ADR-012`、`ADR-013` | 未开始 | 规则、prompt、模型、资产版本化治理 |
| `TASK-031` | Result API / Admin API mapper 补洞 | P2 | B | `TASK_SPEC` | `TASK-021`、`TASK-023`、`TASK-024` | 未开始 | 仅在公开/管理台视图字段不足时进入 |

## 4. 远期待确认项

以下编号仍保留在地图中，但在当前资料下只保留方向，不补全未确认细节：

| 任务 | 优先级 | 类别 | 模板 | 当前状态 | 说明 |
|---|---|---|---|---|---|
| `TASK-032` | P0 | C | `ADR / TASK` | 待人工确认 | 涉及 MVP 范围扩展，方向待确认 |
| `TASK-033` | P0 | C | `ADR / TASK` | 待人工确认 | 涉及 SAP/OA 正式联调与 caller policy |
| `TASK-034` | P1 | C | `TASK / ADR` | 待人工确认 | 涉及 AGENTS / 协作治理进一步演进 |

## 5. 协作边界

### 5.1 A 类任务

适用范围：
- 触碰主链路、快照、状态机、结果契约、证据机制、模型职责、数据库边界
- 需要 Codex 主控定义边界、明确依赖、决定是否触发 ADR

当前属于 A 类的主线任务：
- `TASK-019`
- `TASK-020`
- `TASK-021`
- `TASK-022`
- `TASK-025`
- `TASK-026`
- `TASK-027`
- `TASK-028`
- `TASK-029`
- `TASK-030`

### 5.2 B 类任务

适用范围：
- 页面、视图、管理台展示、mapper / view-model 补洞等局部任务
- 允许在父任务边界冻结后继续拆分，但不能越权改主链路与治理边界

当前属于 B 类的任务：
- `TASK-023`
- `TASK-024`
- `TASK-031`

补充约束：
- `TASK-023` 是公开结果页
- `TASK-024` 是管理台诊断详情
- `TASK-031` 仅用于 Result API / Admin API mapper 补洞
- 三者必须保持边界分离，不得相互混入

### 5.3 C 类任务

适用范围：
- 会扩大 MVP 范围或引入新的治理决策、集成边界或正式联调范围
- 进入前必须先人工确认，必要时先记 ADR

当前属于 C 类的任务：
- `TASK-032`
- `TASK-033`
- `TASK-034`

## 6. 当前建议顺序

当前主链路已完成到 `TASK-023`。后续建议顺序为：

1. `TASK-024` 已按方案 A 完成最小前端实现；下一步只在出现新增字段诉求时再评估 `TASK-031`。
2. 在未出现新字段缺口前，不提前进入 `TASK-031`。
3. `TASK-025 ~ TASK-028` 属于下一轮主链路深化，不应被 B 类任务顺手触发。

## 7. 模板与分派规则

- 父任务、边界冻结、治理变更，使用 `tasks/TASK-000-template.md`
- 只有在父任务边界已冻结时，才允许创建 `TASK_SPEC`
- `TASK-031` 当前仍是预留的 `TASK_SPEC` 方向，不得在 `TASK-024` 字段缺口未确认前提前创建
- Claude Code / DeepSeek 只能执行已冻结边界的局部任务，不能直接接手父 `TASK`

## 8. 当前风险

- `TASK-024` 若跳过探查直接编码，容易把管理台诊断、公开页展示和 mapper 补洞混写。
- `TASK-024` 当前已确认存在潜在字段缺口；若不先确认边界，后续很容易被迫进入后端接口扩展。
- `TASK-031` 若早于 `TASK-024` 决策进入，容易把“字段缺口”误扩展成结果契约返工。
