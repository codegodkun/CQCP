# PROJECT_BRIEF.md

来源：`2026-06-06-contract-quality-control-platform-v2-design.md`，状态为 Draft for implementation planning review。

## 项目目标

V2 是一个全新的 Contract Quality Control Platform / 合同质量控制中台项目，采用新仓库、新工程上下文启动。

平台接收外部系统或管理台发起的合同审核任务，统一完成：

- 合同解析。
- 证据索引。
- 审核点计划。
- 规则/正则/Gemma 混合审核。
- 后端裁判。
- 结果快照。
- 质量优化闭环。

平台只提供风险提示、证据和审核结果 URL，供 SAP/OA/人工审批参考。平台不批准、不拒绝、不阻断、不修改审批流。

## 用户角色

- 外部系统调用方：SAP、OA、采购系统或其他业务系统，通过 API 提交合同审核任务。
- 管理台内部用户：上传 Word 合同、录入结构化字段、选择合同类型和规则集后发起审核。
- 质量验证用户：通过评测入口运行内部质量验证任务，用于样本集和候选优化评测。
- 管理员：批准规则、prompt、pattern、候选方案发布；维护受控配置和审计。
- P0 / 质量平台负责人：参与生产保护、紧急变更、架构复审、重新启用审核点等高风险治理动作。
- 人工审批人：在 SAP/OA/线下流程中参考平台结果，继续负责审批判断。

待确认：这些角色的正式组织名称、账号体系、权限矩阵和人员归属。

## 业务边界

一期目标是企业内部低频、高质量审核，重点跑通单份中文 Word 合同的审核质量。

一期支持：

- 中文 `.docx` / `.doc` 合同。
- 6 万字左右长合同。
- 合同正文、附件、表格、标题、目录、Word 控件、复选框、单选框。
- 外部系统或管理台提供的结构化字段。
- 工程合同、材料供货合同、费用合同、杂项合同的粗分类。
- 规则/正则/Gemma 混合路由。
- 本地 A30/Gemma 生产辅助。
- 公网模型用于离线质量优化，不直接决定生产 finding。
- 异步任务、单 worker 顺序处理、结果 URL、最终结果快照。
- 普通结果页左侧合同原文、右侧审核点，点击右侧审核点定位左侧证据。

一期不做：

- PDF/OCR 作为主输入能力。
- 大规模多合同并行调度。
- RabbitMQ/Kafka 或分布式 worker。
- 多租户。
- 复杂企业 IAM。
- 开放式聊天问答审合同。
- 自动发布 prompt、正则、规则。
- 复杂 BI 报表。
- 全文向量 RAG 主链路。

PDF 可作为 Word 转换后的内部预览形态使用，但不作为一期用户输入主能力。

## 核心流程

所有入口必须进入同一套审核链路，不允许 SAP-only 或管理台-only 审核路径。

核心链路：

1. 入口层接收外部系统、管理台或评测任务。
2. 异步任务层创建 Task、Execution、Result URL 和状态机。
3. Word 解析层通过 POI / LibreOffice / docx4j 产出合同证据模型。
4. 解析产物形成 DocumentBlock、TableBlock、FormControlBlock、SectionTree、RegionIndex、ContractIndex 和 ParseQualityReport。
5. 系统确定合同类型画像，外部或人工优先，系统建议只作为 hint。
6. 构建 CandidateIndex，保留 context/source provenance 信号。
7. 生成 ReviewExecutionPlan。
8. CandidateResolver 按计划解析角色置信度。
9. EvidenceSlot Preflight 检查 required slots、coverage 和 SYS 诊断。
10. FamilyEvidencePlan 和 FamilyModelCallPlan 形成族级证据、token 分配和模型输入。
11. 混合路由执行规则、正则和 Gemma。
12. Gemma 输出 GemmaExtractionArtifact，经过 schema 校验和受控诊断。
13. 后端执行确定性裁判。
14. 结果合成 Finding、SYS 和 SourceAnchor。
15. 可选 GlobalConsistencyCheck 做冲突诊断，不自动裁决。
16. 生成 ReviewResultSnapshot。
17. 普通结果 URL 展示业务化结果和原文定位。
18. 质量工作台沉淀失败归因、候选优化、评测和发布。

## 核心原则

- 一次解析、一套索引、多审核点共享证据。
- 审核点不重复扫描 6 万字全文。
- Gemma 不吃整份合同，不一次性裁判全部审核点。
- 能确定的交给规则/正则/后端裁判。
- 不能确定的交给 Gemma 做局部抽取或语义辅助。
- 最终 `PASS`、`WARNING`、`ERROR`、`SYS-*` 由后端统一合成。
- 证据不足时输出 `SYS-*`，不得回灌全文，不得伪造业务 finding。

## 成功标准

一期成功标准：
- 单份中文 Word 合同可以完成完整审核链路。
- 结果页可以展示审核点、证据和原文定位。
- 证据不足时输出 SYS 诊断，不生成业务 finding。
- 本地 Gemma/A30 只做局部抽取或语义辅助。
- 后端统一合成 PASS/WARNING/ERROR/SYS。

## 最高优先级

一期优先级：
1. Word 解析质量
2. 证据索引与定位
3. 审核点计划
4. 规则/正则/Gemma 混合路由
5. 结果快照与结果页
6. 质量治理闭环