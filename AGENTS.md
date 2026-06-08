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

* 每次任务结束后必须输出 Next Task Handoff。
* 如果存在明确的下一执行任务，必须输出 Next Task Handoff Prompt。
* Next Task Handoff Prompt 必须放在独立 fenced code block 中，方便复制到新 Codex 窗口。
* Prompt 开头使用系统时间 YY/MM/DD HH:mm:ss。
* 如果下一步只是建议、征求意见、需要人工确认或没有明确任务编号，不得放入代码块。

## 项目记忆写入

* 不依赖聊天上下文保存关键决策，长期信息必须写入项目记忆文件。
* 任务结束必须更新 CURRENT\_CONTEXT.md、changelog/当前月份.md 和当前 TASK 文件。
* 没有当前 TASK 时需说明原因。
* 只记录已确认事实、任务结果、风险、待确认事项和后续任务线索。
* 不确定内容统一标记为“待确认”。
* 不得补全架构文档未明确的信息。
* 涉及架构、数据库、审核链路、模型职责、SAP/OA 集成、权限或治理流程的重大变化，必须先记录 ADR。
* 详细规则见 docs/context-management.md。

## 文件职责

* PROJECT\_BRIEF.md：项目目标、用户角色、业务边界和核心流程。
* PRD.md：产品需求定义。
* CURRENT\_CONTEXT.md：当前阶段、约束和关注事项。
* ROADMAP.md：版本规划和阶段拆分。
* tasks/：可执行任务包。

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

