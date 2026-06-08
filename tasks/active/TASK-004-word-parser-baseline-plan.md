# TASK-004：Word 解析基线方案

状态：待开始

类型：规划 / 技术基线

优先级：P0

负责人：待确认

创建日期：2026-06-07

来源：Superpowers planning / design review / task decomposition 审查结论、`docs/backend.md`

## 背景

Word 解析质量是 V1 的最高风险之一。架构文档建议组合 Apache POI、LibreOffice、docx4j 和 Tika，但第一轮 MVP 需要明确 `.docx/.doc`、正文、表格、标题、控件、附件和预览定位的第一版能力边界，避免一开始就实现完整解析平台。

## 目标

- 明确 `.docx/.doc` MVP 解析能力边界。
- 定义正文、表格、标题、控件、预览定位的第一版验收标准。
- 明确哪些解析能力进入 MVP，哪些延后到 Pilot。
- 明确 parser provenance 字段在 MVP 中的最小要求。

## 非目标

- 不实现解析器。
- 不引入或安装解析依赖。
- 不创建文件上传或预览服务。
- 不自研底层文档解析。
- 不把 PDF/OCR 作为一期用户输入主能力。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`ROADMAP.md`
- 相关架构章节：Word-first 文档解析、ParseQualityReport、SourceAnchor。
- 相关 ADR：`ADR-001-technical-stack-and-repo-layout` 草案。
- 上游任务：`TASK-001-v1-mvp-scope-gate`

## 范围

### 包含

- 明确 MVP 是否从 `.docx` 开始，`.doc` 是否通过 LibreOffice 转换进入。
- 明确第一版 `ContractDocument`、`DocumentBlock`、`TableBlock`、`FormControlBlock` 和 `ParseQualityReport` 的最小字段。
- 明确 `sourceOrigin`、`sourceExtractionMode`、`contextType` 和置信度的最小贯穿要求。
- 明确预览定位等级的 MVP 验收。
- 明确解析失败、低置信和 preview-only 的降级行为。

### 不包含

- 不写 Java、Python 或其他解析代码。
- 不生成真实预览资产。
- 不做 OCR。
- 不处理所有 Word 边界格式。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- 一期优先稳定开源库，避免自研底层文档解析。
- `sourceOrigin=PREVIEW_ONLY` 的内容不得进入业务裁判。
- `TEXT_FALLBACK` 不得单独支持 HIGH role、确定性 ERROR 或 `PROVEN_REQUIRED_CLAUSE_ABSENT`。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 必读：`docs/backend.md`
- 按需：`docs/frontend.md`
- 按需：`docs/database.md`
- 按需：原始架构文档 Word-first 解析章节

## 交付物

- Word parser MVP 能力边界说明。
- 第一版解析产物字段清单。
- 解析质量门槛与降级行为说明。
- 预览定位验收标准。
- 后续 parser spike 或实现任务建议。

## 验收标准

- 能明确回答 MVP 是否支持 `.doc`。
- 能明确正文、表格、标题、控件、附件和预览定位哪些进入第一轮。
- 能明确解析失败或低置信时不得输出业务 finding。
- 能为后续 scaffold 和 parser spike 提供依赖选择边界。

## 测试与验证

- 本任务为规划任务，不运行代码测试。
- 验证方式是用合成 Word 场景清单检查每类解析能力是否有 MVP/Pilot 归属。

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`。
- 必须更新 `changelog/2026-06.md`。
- 是否需要更新 `docs/backend.md`、`docs/frontend.md` 或 `ROADMAP.md`：待确认。
- 是否需要新增或更新 ADR：待确认。

## 风险

- 解析能力定得过宽会拖垮 MVP。
- 解析能力定得过窄会无法验证证据定位和审核闭环。
- `.doc` 转换依赖部署环境，可能影响技术栈和部署 ADR。

## 待确认

- MVP 是否必须支持 `.doc`。
- LibreOffice headless 是否可作为部署前提。
- 首批样本是否包含表格、控件和附件。
- 左侧结果页预览使用 PDF、HTML 还是其他形态。

## 后续可能派生任务

- Word parser spike 任务。
- 预览资产存储方案任务。
- SourceAnchor 定位策略任务。
- ParseQualityReport 测试样例任务。

## 完成记录

- 完成日期：待填写。
- 变更文件：待填写。
- 测试结果：待填写。
- 遗留问题：待填写。
- 备注：待填写。
