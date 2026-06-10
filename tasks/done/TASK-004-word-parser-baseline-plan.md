# TASK-004：Word 解析基线方案

状态：已完成
类型：规划 / 技术基线
优先级：P0
负责人：Codex
创建日期：2026-06-07
完成日期：2026-06-10

来源：Superpowers planning / design review / task decomposition 审查结论、`docs/backend.md`

## 背景

Word 解析质量是 V1 的最高风险之一。架构文档建议组合使用 Apache POI、LibreOffice、docx4j 和 Tika，但第一轮 MVP 需要先明确 `.docx/.doc`、正文、表格、标题、控件、附件和预览定位的第一版能力边界，避免一开始就实现完整解析平台。

## 目标

- 明确 `.docx/.doc` MVP 解析能力边界
- 定义正文、表格、标题、控件、附件、预览定位的第一版验收标准
- 明确哪些解析能力进入 MVP，哪些延后到 Pilot
- 明确 parser provenance 字段在 MVP 中的最小要求

## 非目标

- 不实现解析器
- 不引入或安装解析依赖
- 不创建文件上传或预览服务
- 不自研底层文档解析
- 不把 PDF/OCR 作为一期用户输入主能力

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`ROADMAP.md`
- 相关架构章节：Word-first 文档解析、`ParseQualityReport`、`SourceAnchor`
- 相关 ADR：`ADR-010-technology-stack-freeze`
- 上游任务：`TASK-001-v1-mvp-scope-gate`

## 范围

### 包含

- 明确 MVP 是否从 `.docx` 开始，`.doc` 是否通过 LibreOffice 转换进入
- 明确第一版 `ContractDocument`、`DocumentBlock`、`TableBlock`、`FormControlBlock` 和 `ParseQualityReport` 的最小字段
- 明确 `sourceOrigin`、`sourceExtractionMode`、`contextType` 和置信度的最小贯穿要求
- 明确预览定位等级的 MVP 验收
- 明确解析失败、低置信和 preview-only 的降级行为

### 不包含

- 不写 Java、Python 或其他解析代码
- 不生成真实预览资产
- 不做 OCR
- 不处理所有 Word 边界格式

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包
- 一期优先稳定开源库，避免自研底层文档解析
- `sourceOrigin=PREVIEW_ONLY` 的内容不得进入业务裁判
- `TEXT_FALLBACK` 不得单独支持 HIGH role、确定性 `ERROR` 或 `PROVEN_REQUIRED_CLAUSE_ABSENT`

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`docs/backend.md`
- 按需：`docs/frontend.md`
- 按需：`docs/database.md`
- 按需：原始架构文档 Word-first 解析章节

## 交付物

- Word parser MVP 能力边界说明
- 第一版解析产物字段清单
- 解析质量门槛与降级行为说明
- 预览定位验收标准
- 后续 parser spike 或实现任务建议

## 验收标准

- 能明确回答 MVP 是否支持 `.doc`
- 能明确正文、表格、控件、附件和预览定位哪些进入第一轮
- 能明确解析失败或低置信时不得输出业务 finding
- 能为后续 scaffold 和 parser spike 提供依赖边界

## 测试与验证

- 本任务为规划任务，不运行代码测试
- 验证方式是用合成 Word 场景清单检查每类解析能力是否有 MVP/Pilot 归属

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`
- 必须更新 `changelog/2026-06.md`
- 是否需要更新 `docs/backend.md`、`docs/frontend.md` 或 `ROADMAP.md`：是，已更新 `docs/backend.md`
- 是否需要新增或更新 ADR：否，本次为已冻结架构范围内的文档收口，未改变核心审核链路、模型职责或 SYS/Finding 边界

## 风险

- 解析能力定得过宽会拖慢 MVP
- 解析能力定得过窄会无法验证证据定位和审核闭环
- `.doc` 转换依赖部署环境，可能影响技术栈和部署 ADR

## 待确认

- preview 最终采用 PDF、HTML 还是双格式并存
- 嵌入式附件 metadata 是否进入 MVP 只读诊断范围
- 表单控件中极少见 Word 控件类型的统一降级策略

## 后续可能派生任务

- Word parser spike 任务
- 预览资产存储方案任务
- SourceAnchor 定位策略任务
- ParseQualityReport 测试样例任务

## 完成记录

- 完成日期：2026-06-10
- 变更文件：`docs/word-parser-mvp-boundary.md`、`docs/backend.md`、`CURRENT_CONTEXT.md`、`changelog/2026-06.md`、`tasks/done/TASK-004-word-parser-baseline-plan.md`
- 测试结果：未运行代码测试；已完成文档一致性核对，确认 `.docx/.doc`、正文、表格、控件、附件和 preview 定位边界已明确
- 遗留问题：preview 最终格式和嵌入式附件 metadata 可见性仍待确认
- 备注：本次冻结结论明确 MVP 对外仅支持 `.docx`，`.doc` 转换链路仅保留为后续阶段技术预留
