# AGENTS.md

本仓库是 Contract Quality Control Platform（合同质量控制中台 V2）项目仓库。

## 架构约束

当前生效架构文档：

* docs/ARCHITECTURE.md

所有实现、任务拆分和技术方案必须遵循该文档。

涉及以下内容变更时，必须先记录 ADR：

* 核心审核链路
* 模型职责边界
* SYS/Finding 边界
* EvidenceSlot 机制
* ReviewPointFamily
* CandidateResolver

如与其他文档冲突，以 ARCHITECTURE.md 为准；架构变更必须通过 ADR 记录。

## 文档优先级

1\. AGENTS.md

2\. docs/ARCHITECTURE.md

3\. PROJECT\_BRIEF.md

4\. PRD.md

5\. ROADMAP.md

6\. CURRENT\_CONTEXT.md

7\. tasks/\*

8\. changelog/\*

历史设计稿、归档文档和讨论记录仅供参考，不作为当前实现依据。

## 语言规范

项目默认使用中文（界面、文档、任务、注释）。

专有名词和技术术语保留英文。

除非明确要求，不主动生成英文内容。

## 命令行执行环境

* 后续所有任务的仓库命令必须在 PowerShell 7（`pwsh`）中执行，不得使用 Windows PowerShell 5.1（`powershell.exe`）作为外层 shell。
* 每个任务开始执行命令前，必须确认 `$PSVersionTable.PSVersion.Major -ge 7`；PowerShell 7 不可用时应立即停止并报告，不得静默回退到 Windows PowerShell 5.1。
* 如果执行工具不能直接选择 shell，必须显式使用 `pwsh -NoLogo -NoProfile -Command <command>` 包裹仓库命令。
* `git`、`docker`、`gradle`、`npm` 等程序以及 Docker 容器内的 Linux 命令可以由 PowerShell 7 调用；本条约束的是宿主机仓库操作的外层 shell。

## 长期工作规则

* 本项目以 PROJECT\_BRIEF.md、CURRENT\_CONTEXT.md、ROADMAP.md、docs/、tasks/、decisions/ 和 changelog/ 作为长期项目记忆，不依赖聊天上下文保存关键决策。
* 开始任何实现前，先阅读 AGENTS.md、CURRENT\_CONTEXT.md 和对应任务包；再根据任务需要按需读取相关 docs/\*.md。
* 当前仓库不得默认继承旧 ai-contract-review 项目的代码、目录、页面、任务板、历史规则或样本。
* 旧项目只能作为可选历史参考源；如需迁入经验、样本、规则或反例，必须通过单独任务定点提取。
* 不确定的信息统一标记为“待确认”，不要补全架构文档未明确的信息。
* SYS-\* 系统诊断必须与业务风险 Finding 分流。
* EvidencePacket 不足时不得静默回灌全文，也不得生成无可靠证据的业务 Finding。
* Gemma/A30 仅负责局部抽取、证据选择和复杂语义辅助；结构化比对和最终确定性裁判由后端完成。
* 普通结果页应围绕“审核点 → 证据 → 原文定位”解释，不只展示模型结论。
* 所有规则、正则、Prompt、模型、合同类型画像和 EvidenceSelector 必须版本化。
* 一期优先采用成熟开源方案，避免自研底层文档解析和过度平台化设计。
* 本项目定位为质量控制辅助，只提供风险提示、证据和审核结果 URL，不批准、不拒绝、不阻断、不修改审批流。

## Task Context Rule



每个 TASK 应维护：

\- Required Context

\- Optional Context

\- Out of Scope



执行任务时优先读取 Required Context。

不得默认全量读取项目文档。

## Task Level 与收口单位

任务、执行规格和 Git 集成不是同一个单位。所有新工作先按风险和交付形态选择 Task Level：

* `L0 探索`：只读盘点、诊断、可行性调查或不进入主线的 throwaway spike。默认不创建 TASK、不 commit、不执行完整 Memory Writeback；只有形成已确认决策、风险或后续执行边界时才写入项目记忆。
* `L1 小文档`：无行为变化的状态摘要、changelog 补录、链接/路径修正、post-merge 状态引用和项目记忆压缩。默认不创建 TASK，由 Codex 自查并合并为文档批处理。
* `L2 Feature`：一个可演示用户能力或完整技术能力。使用一个父 TASK，可包含多个局部 TASK_SPEC；默认以一个 Feature 分支、一个 PR 和一次 merge 收口。
* `L3 高风险治理`：架构、核心审核语义、模型职责、SYS/Finding、EvidenceSlot、ReviewPointFamily、CandidateResolver、SourceAnchor、评测正确性、生产激活、数据库/API/CI/安全等高风险变化。必须有父 TASK；触发架构规则时先记录 ADR；按可回滚风险边界收口并执行独立只读审计。

收口规则：

* `TASK_SPEC` 是局部执行和 Review Intake 单位，不自动等于独立 commit、push、PR 或 merge。
* commit 只承载有意义、可理解、可回滚的变化；不得为计划已接纳、审查已完成、已 push、checks 已通过或普通状态同步机械创建提交。
* `push` 是传输动作。冻结 diff/head SHA 未变化时，不因 push 本身重复完整内容审计。
* Feature 是默认 PR 集成单位；Milestone 是默认任务归档和项目记忆收口单位。
* post-merge 状态只有在会改变下一执行门禁时才即时写回；否则并入下一相关 Feature 或 Milestone 文档批次。
* `tasks/active/` 到 `tasks/done/` 的物理迁移默认在 Feature 或 Milestone 收口时批量完成；任务文件中的 `状态` 字段是开发期间的实时状态真源。
* GitHub 是 PR、checks 和 merge 状态的事实源。项目记忆只记录影响后续工作的结果、风险、门禁和必要引用，不复制完整 Git 流水账。

## 角色分离与证据门禁

* Codex 负责父任务边界、冻结 `TASK_SPEC`、实现报告与 `git diff` 审查、Review Intake Decision 和提交前判断。
* 在角色分离试点中，Codex 可以编写 `TASK_SPEC` 和审查实现，但不得直接编写业务代码后再自行宣布通过。
* Claude Code / DeepSeek 只能执行 Codex 已冻结且关联现有父 `TASK` 的局部 `TASK_SPEC`，不得直接承接父 `TASK`，不得 commit，不得 push。
* Claude Code / DeepSeek 执行代码修改前，必须先提交“编码前规格映射计划”，说明验收断言理解、关键字段或信号的真实输入计算方式、明确不修改路径、范围外风险和预计测试；经 Codex 审查放行后才能实现。
* 独立 agent 只做只读事实核查，不得实现、修复代码或编写业务逻辑。
* L3 父任务、正式 Milestone 和触发高风险条件的 L2 父任务归档前，必须经过独立 agent 只读审计；普通 L2 Feature 在风险未触发时可由 Codex Review Intake + CI 收口。已有任务文件明确要求的独立归档审计继续有效，不追溯解除。
* 低风险文档动作（状态摘要、changelog 补录、路径修正、post-merge 状态写回、无行为变化的项目记忆压缩）默认由 Codex 自查并可合并式批量处理；不单独建 TASK，不默认派独立 agent。
* `TASK_SPEC` 的验收断言必须可证伪，不得只写“完成优化”“修复问题”等描述性目标。
* 同根因分批修复时，后续 `TASK_SPEC` 必须对照前一批修复原则，明确一致点、差异点及差异理由，并由 Codex 审查接受或拒绝。
* 评测、fixture、expected JSON 必须说明 expected 值的来源、是否依赖被测系统输出、是否存在循环验证；如依赖被测系统输出，只能声明一致性，不得声明独立正确性。
* 不得使用 `CURRENT_CONTEXT.md` 自述替代真实代码、测试、原始 console 输出和 commit 证据；无法核验的完成声明必须标记为“待确认”。
* 人工合同 anchor 标准答案必须独立于 parser 输出形成，AI agent 不得替代人工定义标准答案。
* 详细治理依据见 `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md` 和 `tasks/active/TASK-GOV-003-five-class-remediation-and-role-gates.md`。

## 任务模板选择规则

* Codex 创建任务前，必须先判断任务类型与边界是否已经冻结。
* 项目主任务、父任务和需要 Codex 主控的任务，使用 `tasks/TASK-000-template.md`。
* 派发给 Claude Code + DeepSeek 的局部执行子任务，使用 `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`。
* 每个 `TASK_SPEC` 必须关联一个已存在的父 `TASK`；`TASK_SPEC` 不是项目主任务模板。
* Claude Code + DeepSeek 不得直接执行父 `TASK`，只能执行 Codex 已拆解并定界的 `TASK_SPEC`。
* Codex 必须审查 `TASK_SPEC` 的实现报告和 `git diff` 后，才能接受执行结果。
* 如不确定使用哪个模板，先查看 `tasks/TEMPLATE_ROUTER.md` 和 `tasks/MVP_TASK_MAP.md`。

## 每个任务的执行流程

### 任务开始前

必须读取：

* AGENTS.md
* CURRENT\_CONTEXT.md
* 当前 tasks/active/TASK-xxx.md

然后根据任务类型按需读取：

* 前端任务：docs/frontend.md
* 后端任务：docs/backend.md
* 数据库任务：docs/database.md
* AI 审核任务：docs/ai-review.md
* SAP/OA 集成任务：docs/sap.md
* 部署任务：docs/deployment.md

不得一次性读取全部文档。

### 任务执行中

* 只完成当前任务包定义的范围。
* 不顺手实现任务外功能。
* 发现新需求、新风险、新技术债，记录为后续任务。
* 不在当前任务中扩大范围。
* 涉及架构、数据库、审核链路、模型职责、SAP/OA 集成、权限或治理流程的重大变化，必须先提出方案并记录 ADR。

### 任务完成后

按 docs/context-management.md 执行项目记忆写回，并在交付摘要中说明 Memory Writeback。

* 低风险文档批处理可以把多个状态摘要、changelog 补录和 post-merge 写回合并为一次 Memory Writeback，不要求每个小状态变化单独收尾。
* L0 默认不写回；L1 按批次写回；L2 在 Feature 收口时写回；L3 在关键门禁和最终收口时写回。TASK_SPEC 的中间状态不要求逐项同步全部长期记忆。
* 每次任务结束后必须输出 Next Task Handoff 说明，但这不等于创建新任务。
* 只有存在明确的下一执行任务文件、任务编号、目标和边界时，才输出 Next Task Handoff Prompt。
* Next Task Handoff Prompt 必须放在独立 fenced code block 中，方便复制到新 Codex 窗口。
* Prompt 开头使用系统时间 YY/MM/DD HH:mm:ss。
* 如果下一步只是建议、征求意见、需要人工确认或没有明确任务编号，不得放入代码块。

## 项目记忆写入

* 不依赖聊天上下文保存关键决策，长期信息必须写入项目记忆文件。
* L2 Feature 与 L3 高风险治理收口必须更新 `CURRENT_CONTEXT.md`、`changelog/当前月份.md` 和父 TASK；L0/L1/TASK_SPEC 中间状态按 Task Level 与写回批次处理，不再机械要求每次都更新三个文件。
* 低风险文档同步、状态摘要、changelog 补录、路径修正和 post-merge 状态写回可合并式批量写入；没有明确当前 TASK 时说明“本次为低风险文档批处理，无新 TASK”。
* CURRENT\_CONTEXT.md 只保存当前阶段、活跃任务、已完成任务、已接受 ADR、当前阻塞项、待确认事项和下一步任务的摘要及引用路径，不复制其他文档的详细内容。如出现大量历史内容堆积，应定期进行瘦身。
* 没有当前 TASK 时需说明原因。
* 只记录已确认事实、任务结果、风险、待确认事项和后续任务线索。
* 不确定内容统一标记为“待确认”。
* 不得补全架构文档未明确的信息。
* 涉及架构、数据库、审核链路、模型职责、SAP/OA 集成、权限或治理流程的重大变化，必须先记录 ADR。
* 如后续父 TASK 拆解、to-issues 草案、TASK_SPEC 派发、人工确认或 ADR 结论影响 MVP 任务顺序、任务分类、当前状态、依赖关系或协作边界，Codex 必须同步更新 `tasks/MVP_TASK_MAP.md`。
* `tasks/MVP_TASK_MAP.md` 只记录任务地图层面的变化，不记录具体实现细节。
* 具体实现结果仍写入父 TASK、`CURRENT_CONTEXT.md` 和 `changelog/当前月份.md`。
* 详细规则见 docs/context-management.md。

## 文档更新规则

* `README.md` 只作为项目入口和导航，不承载实时任务状态。
* 当前阶段、跨任务阻塞项和下一步以 `CURRENT_CONTEXT.md` 为准；开发期间的任务内状态以对应 TASK 文件的 `状态` 字段为准。
* L2 Feature 与 L3 收口必须更新 `CURRENT_CONTEXT.md` 并追加当月 `changelog`；L0 默认不写回，L1 按文档批次写回。
* `docs/DEVELOPMENT.md` 是开发流程、角色分工和 Git 规则入口。
* `docs/VERIFY.md` 是验收规则、验证清单和提交前检查入口。
* 只有架构变化才允许更新 `docs/ARCHITECTURE.md`。
* 重大技术或架构决策应进入 ADR / `decisions/`，不得散落在普通文档里。
* 不确定事项必须标记为“待确认”，不得写成已完成事实。
* 文档更新不得顺手扩大到代码、数据库、Docker 或架构改动。

## 文件职责

* PROJECT\_BRIEF.md：项目目标、用户角色、业务边界和核心流程。
* PRD.md：产品需求定义。
* CURRENT\_CONTEXT.md：当前阶段、约束和关注事项。
* ROADMAP.md：版本规划和阶段拆分。
* tasks/：可执行任务包、任务模板和任务协作规则。

  * `TASK-000-template.md`：Codex 项目主任务 / 父任务模板。
  * `TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`：Claude Code + DeepSeek 执行子任务模板。
  * `TEMPLATE_ROUTER.md`：任务模板选择和协作路由说明。
  * `MVP_TASK_MAP.md`：MVP 后续任务地图、任务分类和协作边界。
  * active/：进行中任务。
  * done/：已完成任务。
* docs/：模块级长期知识和架构文档。
* decisions/：ADR 架构决策记录。
* changelog/：项目记忆和变更记录。

## 禁止事项

* 未经任务明确要求，不创建业务代码、脚手架或依赖。
* 不把公网模型用于真实生产合同主链路或直接决定生产 Finding。
* 不把 PDF/OCR 作为一期用户输入主能力。
* 不在一期引入 RabbitMQ、Kafka、分布式 Worker、多租户、复杂 IAM、开放式聊天审合同、复杂 BI 或全文向量 RAG 主链路。
