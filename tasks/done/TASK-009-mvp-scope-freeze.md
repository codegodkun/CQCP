# TASK-009：MVP 范围冻结

状态：已完成

类型：规划 / 范围冻结 / 项目记忆

优先级：P0

负责人：Codex

创建日期：2026-06-09

来源：用户明确要求“新建 TASK-009 MVP Scope Freeze，并开始”、`CURRENT_CONTEXT.md`、`PRD.md`、`ROADMAP.md`、`docs/ARCHITECTURE.md`、`TASK-001` 至 `TASK-008`

## 背景

`TASK-001` 已形成 MVP / Pilot / Production Readiness 分层规划；`TASK-002` 至 `TASK-008` 已陆续确认 MVP 入口、首批审核点、首批结构化字段、合同类型、证据定位、模型边界、调优治理、DefinitionTermIndex 和诊断契约。

当前仓库仍保留部分旧表述，例如“最终仍待人工确认”“V1 MVP 范围待确认”。本任务用于把已确认的 MVP 事实收敛为冻结基线，避免后续任务继续把 MVP 范围当作开放问题。

## 目标

- 新建并执行 `TASK-009` 任务包。
- 将 MVP 范围状态从“规划基线 / 待确认”收敛为“冻结基线”。
- 更新 `CURRENT_CONTEXT.md`、`ROADMAP.md`、`PRD.md`、`TASK-001` 和 `changelog/2026-06.md` 中与范围冻结直接相关的表述。
- 明确保留仍待确认的技术实现事项，例如正式技术栈、OpenAPI、数据库 ERD、模型 endpoint、部署拓扑和样本文件规范。

## 非目标

- 不写业务代码。
- 不创建前端、后端或数据库脚手架。
- 不安装依赖。
- 不设计完整 OpenAPI、ERD 或 UI 视觉方案。
- 不改变已 Accepted ADR 的核心架构边界。
- 不新增 MVP 能力，不把 Pilot / Production Readiness 能力提前纳入 MVP。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`PRD.md`、`ROADMAP.md`、`docs/ARCHITECTURE.md`、`docs/context-management.md`
- 相关 ADR：`ADR-004`、`ADR-005`、`ADR-006`、`ADR-007`、`ADR-008`、`ADR-009`
- 上游任务：`TASK-001`、`TASK-002`、`TASK-003`、`TASK-007`、`TASK-008`

## 范围

### 包含

- 创建 `tasks/active/TASK-009-mvp-scope-freeze.md`。
- 汇总已确认的第一轮 MVP 冻结内容。
- 清理当前记忆文件中与 MVP 范围冻结冲突的旧待确认表述。
- 调整当前下一步和禁止推进事项：MVP 范围、入口、首批任务包和首批审核点基线不再作为阻塞项；技术栈、项目目录、关键 ADR、数据库迁移策略和模型接入方式仍是 scaffold 前置条件。

### 不包含

- 不执行 `TASK-004`、`TASK-005` 或 `TASK-006`。
- 不创建最小 OpenAPI 契约任务文件。
- 不修改业务链路、模型职责、EvidenceSlot、ReviewPointFamily 或 CandidateResolver 的既有架构规则。
- 不归档历史任务文件。

## 约束

- 必须遵守 `AGENTS.md` 和 `docs/context-management.md`。
- 以 `docs/ARCHITECTURE.md` 为最高架构依据。
- 不确定内容标记为“待确认”。
- 不把技术栈、数据库、OpenAPI、模型 endpoint 或部署拓扑写成已确认事实。
- 任何改变核心审核链路、模型职责边界、SYS/Finding 边界、EvidenceSlot、ReviewPointFamily 或 CandidateResolver 的内容都必须先记录 ADR；本任务不做此类变更。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`PRD.md`
- 必读：`ROADMAP.md`
- 必读：`docs/ARCHITECTURE.md`
- 按需：`docs/context-management.md`
- 按需：`TASK-001` 至 `TASK-008`

## 交付物

- `tasks/active/TASK-009-mvp-scope-freeze.md`
- `CURRENT_CONTEXT.md`
- `ROADMAP.md`
- `PRD.md`
- `tasks/active/TASK-001-v1-mvp-scope-gate.md`
- `changelog/2026-06.md`

## 验收标准

- `CURRENT_CONTEXT.md` 明确 MVP 范围已冻结。
- `ROADMAP.md` 中 MVP 分层不再标记为“最终仍待人工确认”。
- `PRD.md` 不再把当前 MVP 范围冻结本身列为待确认事项。
- `TASK-001` 补充说明其早期遗留待确认项已由后续任务和本任务收口。
- 技术栈、OpenAPI、数据库、模型 endpoint、部署拓扑等仍保持“待确认”。
- 未创建业务代码、脚手架、依赖或数据库变更。

## 测试与验证

- 使用 `rg` 检查 `最终仍待人工确认`、`V1 MVP 范围` 等旧阻塞表述是否已清理或转为历史说明。
- 使用 `git status --short` 检查变更范围。

## 文档更新要求

- 更新 `CURRENT_CONTEXT.md`：是。
- 更新 `ROADMAP.md`：是。
- 更新 `PRD.md`：是。
- 更新 `changelog/2026-06.md`：是。
- 更新 `TASK-001`：是，补充范围冻结后续说明。
- 新增或更新 ADR：否。本任务冻结既有已确认范围，不改变架构边界。

## Next Task Handoff

- 任务完成后必须判断是否存在明确下一任务。
- 如果存在明确下一任务，输出可直接复制到新窗口执行的 `Next Task Handoff Prompt`。
- 如果不存在明确任务编号，不输出代码块。
- 如果只是建议或征求意见，不得放入代码块。

## 风险

- 如果冻结范围后仍保留旧“待确认”措辞，后续任务可能继续重复范围讨论。
- 如果冻结范围写得过宽，可能把 Pilot / Production Readiness 能力误纳入 MVP。

## 待确认

- 正式技术栈、框架、工程目录和测试策略。
- 数据库最终 ERD、迁移策略和字段版本化落地方式。
- 最小 OpenAPI 契约完整细节、认证方式和外部系统调用策略。
- 本地 A30/Gemma endpoint、模型版本、部署形态和容量边界。
- 评测环境、样本文件命名规范、保存目录和预期结果文件格式。

## 完成记录

- 完成日期：2026-06-09。
- 变更文件：`tasks/active/TASK-009-mvp-scope-freeze.md`、`CURRENT_CONTEXT.md`、`ROADMAP.md`、`PRD.md`、`tasks/active/TASK-001-v1-mvp-scope-gate.md`、`changelog/2026-06.md`。
- 测试结果：完成 `rg` 文本检查和 `git status --short` 变更范围检查；本任务为文档任务，未运行代码测试。
- 遗留问题：项目目录、数据库迁移细节、本地模型服务接入方式、最小 OpenAPI 契约、`ADR-002` 与 `ADR-003` 仍待确认；技术栈已由 `ADR-010` 冻结。
- 备注：未创建业务代码，未搭建脚手架，未安装依赖，未修改数据库。
