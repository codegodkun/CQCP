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

## Git 操作规则

- 禁止 `git add .`
- 提交前必须运行 `git status --short`
- 每次 commit 只包含当前任务范围
- 未经用户明确授权不得 `push`
- 不得把无关代码、日志、输出文件、缓存文件顺手混入提交
- 如需暂存局部改动，优先使用只针对单个文件的精确操作

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
