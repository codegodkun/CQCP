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

1. 先选择 `L0 探索 / L1 小文档 / L2 Feature / L3 高风险治理`
2. 确认当前任务是否已有冻结边界
3. 判断应使用 `TASK`、`TASK_SPEC` 还是不创建任务文件
4. 只读取该任务 Required Context
5. 核对本轮允许修改文件
6. 如需实施，先说明将改哪些文件，再动手
7. 按对应 Task Level 执行验证、收口和项目记忆写回

## Task Level

| Level | 适用场景 | TASK / TASK_SPEC | 默认集成方式 |
|---|---|---|---|
| `L0 探索` | 只读盘点、诊断、可行性调查、throwaway spike | 默认不创建；可记录在现有父 TASK | 不进入主线 |
| `L1 小文档` | 无行为变化的状态、链接、路径、changelog、记忆压缩 | 不单独创建 TASK | 文档批处理或并入下一 Feature/Milestone |
| `L2 Feature` | 一个可演示用户能力或完整技术能力 | 一个父 TASK，可含多个 TASK_SPEC | 一个 Feature 分支、一个 PR、一次 merge |
| `L3 高风险治理` | 架构、核心审核语义、评测、生产激活、数据库/API/CI/安全 | 独立父 TASK；必要时 ADR；可含多个 TASK_SPEC | 按可回滚风险边界独立 PR |

Task Level 由实际影响决定，不由文件数量或 `docs:` / `feat:` 前缀决定。文档修改如果改变治理规则、权限、审计门禁或生产激活边界，仍属于 L3。

## TASK / TASK_SPEC 使用边界

- 父任务、主任务、边界冻结、治理任务：使用 `TASK`
- 只有父任务边界已冻结时，才允许创建 `TASK_SPEC`
- `TASK_SPEC` 必须关联一个已存在的父 `TASK`
- Claude Code / DeepSeek 不得直接执行父 `TASK`
- Codex 必须审查 `TASK_SPEC` 的实现结果和 `git diff`
- `TASK_SPEC` 是执行和 Review Intake 单位，不自动创建独立分支、commit、push、PR 或 merge
- 同一父 TASK 下边界兼容、回滚关系一致的多个 TASK_SPEC，默认在同一 Feature 分支中收口

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
- 低风险文档同步、状态摘要、changelog 补录、路径修正和 post-merge 状态写回不需要单独建 TASK，可合并为一次文档批处理和一次 Memory Writeback。
- Next Task Handoff 只在存在明确下一任务文件、编号、目标和边界时输出可复制 Prompt；没有明确任务时只写建议、待确认或暂不生成 Prompt。

以下高风险节点必须追加独立 agent 只读复核：

- L3 父任务归档、正式 Milestone 收口和生产激活。
- L3 merge；L2 merge 在风险触发或现有任务明确要求时。
- 进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 涉及 `EvidenceSlot` / `CandidateResolver` / `SourceAnchor`。
- 涉及 expected fixture / 评测指标。
- 涉及 workflow / CI / required checks / branch protection。
- Codex 自己写代码又自己审查。
- 工作区状态或远程同步状态不清。
- 人工 anchor ground truth 可能被 AI/parser 输出替代或混淆。

普通 `push` 是传输动作，不单独触发完整内容审计。独立审计应绑定冻结 diff 或 head SHA；审计后内容未变化时，push 后只需核对远端 head SHA、PR diff 和 CI。审计后任何受审文件变化都会使原审计失效，必须重新判断复核范围。

普通 L2 Feature 在没有触发上述条件时，由 Codex Review Intake、相关测试和 CI 收口，不强制额外独立 agent。已有 TASK / TASK_SPEC 明确要求的独立审计继续有效。

## Git 操作规则

- 禁止 `git add .`
- 提交前必须运行 `git status --short`
- 每次 commit 只包含当前 Feature 或 L3 风险边界内的授权范围
- 未经用户明确授权不得 `push`
- 不得把无关代码、日志、输出文件、缓存文件顺手混入提交
- 如需暂存局部改动，优先使用只针对单个文件的精确操作
- commit 只在形成有意义、可理解、可回滚的变化时创建；计划接纳、审计开始、push/checks 状态和普通 post-merge 同步不应机械形成独立提交
- L2 默认一个 Feature 分支、一个 PR 和一次 merge；允许多个有语义的本地 commit，不为每个 TASK_SPEC 创建 PR
- L3 按可回滚风险边界拆 PR，不按每个执行步骤拆 PR
- 已声明且全部位于当前 Feature 允许范围内的 dirty 文件属于可解释工作状态；未知来源或范围外 dirty 文件仍触发停止

## 授权包络与 merge

- L1 可使用一次有明确文件范围的文档批处理授权；diff 未变化且 CI 满足时，可覆盖 commit、单次 push、PR 和预授权 merge。
- L2 可在边界冻结后取得一次 Feature 收口授权，覆盖允许范围内的完成态 commit、单次 push 和 PR 创建。merge 可以由用户在授权中预先约定“checks 成功且 diff 不变后合并”，否则保留最终确认。
- L3 可以一次授权冻结范围内的完成态 commit 和单次 push，但 merge 必须在独立审计、Codex Review Intake 和 CI 证据形成后单独确认，除非用户明确给出同等严格的条件式预授权。
- 授权包络一旦出现允许文件、行为语义、head diff 或风险等级变化即失效，必须重新确认。

## PR 授权证据规则

- 每个 PR 必须填写 `.github/pull_request_template.md` 中的 `CQCP Authorization Evidence` 区块。
- PR body 至少记录 Task Level、Integration unit、任务编号或 L1 批次说明、当前 Governance Mode、允许文件、禁止文件、commit 授权、push 授权、merge 授权、测试证据、独立审计状态、Memory Writeback 和范围外确认。
- 供自动检查的字段值必须写在冒号后同一行；需要长说明时可先写同一行摘要，再在下方补充细节。
- `Authorization evidence check` 只检查 PR body 文本字段是否存在且非空；它不证明用户授权、测试、独立审计或 Memory Writeback 已真实发生。
- commit、push、merge 必须位于本次真实授权包络内；可以使用一次有明确范围和失效条件的 Feature/批次授权，不要求为同一冻结 diff 的每个机械步骤重复询问。不得用 PR 模板默认文字替代真实授权。
- 如字段内容为 `待确认`、`TBD`、`TODO`、`N/A`、空白或无法独立审查的占位描述，PR body 检查应失败。
- 将 `Authorization evidence check` 纳入 required status checks、修改 branch protection 或配置 repository ruleset，必须另行定界任务并取得用户授权。

## 文档修改规则

- L2 Feature 和 L3 收口需要更新 `CURRENT_CONTEXT.md`、父 TASK 和当月 `changelog`
- L0 默认不写回；L1 按批次写回；TASK_SPEC 中间状态不要求逐项同步所有长期记忆
- 只有架构变化才允许更新 `docs/ARCHITECTURE.md`
- 重大决策应进入 ADR，而不是直接散落在普通文档中
- 文档必须以已确认事实为准；不确定项统一标记为“待确认”
- post-merge 状态只有在改变下一门禁时才即时写回，否则并入下一 Feature/Milestone 文档批次
- `tasks/active/` 到 `tasks/done/` 的物理迁移默认在 Feature/Milestone 收口时批量完成

## 停止条件

出现以下任一情况，应停止继续实施并先报告：

- 工作区不干净且与当前任务无关
- 任务边界不清
- 需要修改禁止范围文件
- Docker 验证失败且无法判断原因
- 发现当前任务会顺手扩大到架构、接口、数据库或治理边界之外
