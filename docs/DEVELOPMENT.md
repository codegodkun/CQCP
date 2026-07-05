# DEVELOPMENT.md

## 目的

本文档定义 CQCP 的标准开发流程，约束任务启动、环境使用、角色分工、Git 操作和停止条件。

如与 [AGENTS.md](../AGENTS.md) 或 [docs/ARCHITECTURE.md](ARCHITECTURE.md) 冲突，以前两者为准。

## 开发前检查

开始任何任务前，至少执行以下检查：

1. 运行 `git status --short`
2. 确认当前分支
3. 确认任务边界、允许修改范围和禁止修改范围
4. 读取 `AGENTS.md`
5. 读取 `CURRENT_CONTEXT.md`
6. 读取当前任务包；如果没有对应任务文件，必须明确说明原因
7. 按任务类型按需读取相关模块文档，而不是一次性读取全部文档

如果工作区不干净，先判断这些改动是否属于当前任务；不能默认混入提交。

## Docker Compose 使用原则

- Docker Compose 是 CQCP 唯一标准开发 / 验证 / 测试环境
- 当前仓库实际 Compose 文件为 `deploy/compose/compose.yml`
- 当前环境变量样例为 `deploy/env/.env.example`
- 不得自行引入第二套“临时本地流程”替代标准环境
- 如果本地命令与 Compose 行为冲突，以 Compose 环境下的结果为准

推荐命令：

```powershell
docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example up -d --build
docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example ps
docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example logs -f admin-web api-server postgres
docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example down
```

## 任务开始流程

1. 先确认当前任务是否已有冻结边界
2. 判断应使用 `TASK` 还是 `TASK_SPEC`
3. 只读取该任务 Required Context
4. 核对本轮允许修改文件
5. 如需实施，先说明将改哪些文件，再动手
6. 完成后执行验证、项目记忆写回和交付摘要

## TASK / TASK_SPEC 使用边界

- 父任务、主任务、边界冻结、治理任务：使用 `TASK`
- 只有父任务边界已冻结时，才允许创建 `TASK_SPEC`
- `TASK_SPEC` 必须关联一个已存在的父 `TASK`
- Claude Code / DeepSeek 不得直接执行父 `TASK`
- Codex 必须审查 `TASK_SPEC` 的实现结果和 `git diff`

## 角色分工

- Codex：总控、边界冻结、实施审查、最终验收、提交把关
- Claude Code：只能执行已冻结边界的局部任务
- DeepSeek：只能执行已冻结边界的局部任务

任何需要修改架构、审核链路、模型职责、EvidenceSlot、ReviewPointFamily、CandidateResolver 的事项，必须先进入 ADR 或明确方案评审。

## 完成态复核优先规则

后续 CQCP 任务推进采用“完成态复核优先”，避免把复核变成任务推进本身。

- 普通任务执行过程中不反复请求外部复核；Codex 负责冻结 `TASK_SPEC`、边界和验收标准，执行层按冻结规格完成。
- 普通任务默认只做一次完成态复核；执行完成后一次性提交 `diff`、测试输出、`git status`、风险说明和 Memory Writeback 状态供 Codex 判断是否接纳。
- 独立 agent 只在完成态或关键门禁点介入，只做只读事实核查，不参与每个小步骤，不替代 Codex 总控职责。
- 低风险文档动作默认由 Codex 自查；证据不足、工作区不清或触发高风险节点时，再请求独立只读复核。

以下高风险节点必须追加独立 agent 只读复核：

- 父任务归档。
- `push` / `merge`。
- 进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 涉及 `EvidenceSlot` / `CandidateResolver` / `SourceAnchor`。
- 涉及 expected fixture / 评测指标。
- 涉及 workflow / CI / required checks / branch protection。
- Codex 自己写代码又自己审查。
- 工作区状态或远程同步状态不清。

## Git 操作规则

- 禁止 `git add .`
- 提交前必须运行 `git status --short`
- 每次 commit 只包含当前任务范围
- 未经用户明确授权不得 `push`
- 不得把无关代码、日志、输出文件、缓存文件顺手混入提交
- 如需暂存局部改动，优先使用只针对单个文件的精确操作

## PR 授权证据规则

- 每个 PR 必须填写 `.github/pull_request_template.md` 中的 `CQCP Authorization Evidence` 区块。
- PR body 至少记录任务编号、当前 Governance Mode、允许文件、禁止文件、commit 授权、push 授权、merge 授权、测试证据、独立审计状态、Memory Writeback 和范围外确认。
- 供自动检查的字段值必须写在冒号后同一行；需要长说明时可先写同一行摘要，再在下方补充细节。
- `Authorization evidence check` 只检查 PR body 文本字段是否存在且非空；它不证明用户授权、测试、独立审计或 Memory Writeback 已真实发生。
- commit、push、merge 仍必须按任务当次状态取得用户明确授权；不得用 PR 模板默认文字替代真实授权。
- 如字段内容为 `待确认`、`TBD`、`TODO`、`N/A`、空白或无法独立审查的占位描述，PR body 检查应失败。
- 将 `Authorization evidence check` 纳入 required status checks、修改 branch protection 或配置 repository ruleset，必须另行定界任务并取得用户授权。

## 文档修改规则

- 任务完成后需要更新 `CURRENT_CONTEXT.md`
- 任务完成后需要记录到当月 `changelog`
- 只有架构变化才允许更新 `docs/ARCHITECTURE.md`
- 重大决策应进入 ADR，而不是直接散落在普通文档中
- 文档必须以已确认事实为准；不确定项统一标记为“待确认”

## 停止条件

出现以下任一情况，应停止继续实施并先报告：

- 工作区不干净且与当前任务无关
- 任务边界不清
- 需要修改禁止范围文件
- Docker 验证失败且无法判断原因
- 发现当前任务会顺手扩大到架构、接口、数据库或治理边界之外
