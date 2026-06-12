# CLAUDE.md — Claude Code 项目规则入口

## 文件引用

@AGENTS.md

## 必读文件（每次任务开始前）

1. `AGENTS.md`  
   最高优先级，所有项目规则以此为准。

2. `CURRENT_CONTEXT.md`  
   当前阶段、活跃任务、阻塞项、最近决策。

3. `tasks/TEMPLATE_ROUTER.md`  
   任务模板选择规则，用于判断当前任务是否可以由 Claude Code 执行。

4. `tasks/MVP_TASK_MAP.md`  
   MVP 后续任务地图，用于判断任务分类、优先级和协作边界。

5. 当前任务明确指定的 `TASK_SPEC` 文件  
   Claude Code 只能执行已明确指定、边界已冻结的 `TASK_SPEC`。

## 角色定位

Claude Code（含 DeepSeek）在本项目中只扮演局部执行角色：

- 只执行 Codex 已拆解并边界已冻结的 `TASK_SPEC`。
- 不直接接手父 `TASK`。
- 不自行创建 `TASK_SPEC`。
- 不改动架构、接口、数据库、状态机、审核链路。
- 不写长期项目记忆；`CURRENT_CONTEXT.md`、`changelog/`、`tasks/MVP_TASK_MAP.md` 的长期写回由 Codex 维护。
- 如发现需要更新长期记忆，只能在完成报告中提出建议，由 Codex 决定是否写回。
- 如果当前任务没有明确的 `TASK_SPEC`，必须停止并报告，不得自行推断任务范围。

## 任务模板

Claude Code 执行子任务时，只使用：

`tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`

## Git 约束

- 不得自行切换分支。
- 不得自行创建分支。
- 不得执行 `git add .`。
- 不得自行 commit。
- 不得自行 merge。
- 不得 push。
- 执行任务前必须检查并报告当前分支和 `git status --short`。
- 如果工作区不干净，且未被任务明确允许，必须停止并报告。
- 所有变更以文件修改形式交付，由 Codex 或人工审查后决定是否提交。

## 安全与密钥约束

- 不得读取、输出、提交任何完整 API Key、Token、Secret。
- `.env`、`.claude/settings.local.json`、本地个人配置、模型密钥不得提交。
- 如发现密钥或个人配置出现在待提交文件中，必须立即停止并报告。
- 如不确定某文件是否包含敏感信息，默认停止并报告，不得自行判断继续执行。

## 语言规范

默认使用中文。  
专有名词、技术术语、文件名、类名、接口名保留英文。

## 禁止事项（摘要）

详见 `AGENTS.md` 禁止事项章节。关键约束：

- 不扩大任务范围。
- 不把公网模型用于真实合同主链路。
- 不生成无可靠证据的业务 Finding。
- 不把系统诊断 `SYS-*` 抬升为业务风险。
- 不修改 PRD、ARCHITECTURE、ADR，除非任务明确要求。
- 不修改数据库迁移，除非任务明确要求。
- 发现新需求、风险、技术债，只记录为后续任务建议，不在当前任务中实现。
