# Word Parser MVP 边界

日期：2026-06-10

状态：Frozen for MVP

来源：`TASK-004-word-parser-baseline-plan`、`docs/ARCHITECTURE.md`、`docs/backend.md`、`ADR-008-definition-term-index.md`

## 1. 目标

本文档冻结 V1 MVP 阶段 Word parser 的能力边界、最小解析产物字段和降级规则，供后续 OpenAPI、scaffold、parser spike 与样例验证任务使用。

## 2. 输入边界

### 2.1 外部输入能力

- MVP 对外仅支持单份中文 `.docx`
- 管理台上传界面必须明确标注“当前仅支持 DOCX，DOC 待后续开发”
- 外部 API 与管理台都不得把 `.doc` 作为 MVP 正式输入能力

### 2.2 `.doc` 边界

- `.doc` 转 `.docx` 的 LibreOffice headless 转换链路保留为技术预留能力
- 该能力不进入第一轮 MVP 对外交付口径
- `.doc` 的正式启用延后到 Pilot 或后续独立任务

## 3. 解析对象边界

### 3.1 进入 MVP 的对象

- 正文段落
- 标题
- 目录项
- 表格、行、单元格
- Word 表单控件与符号勾选
- 以文档章节或附录形式存在的附件区域
- 预览定位映射

### 3.2 不进入 MVP 的对象

- PDF/OCR 输入
- 图片文字识别
- 独立嵌入二进制附件的内容解析
- 外链文件抓取
- 复杂 OLE 对象内容提取
- 扫描件或截图类附件理解

## 4. 附件边界

MVP 中“附件”定义为合同 Word 文档内可被结构化解析的附件区域，包括：

- “附件一 / 附件二 / 附录 / 补充条款”等标题块
- 附件区域中的正文段落
- 附件区域中的表格

MVP 不把嵌入式文件、外部链接文件、图片扫描件视为可裁判附件正文。此类内容可保留 preview 或 parser diagnostic，但不得直接进入业务裁判。

## 5. 最小解析产物

### 5.1 ContractDocument

```text
ContractDocument
- metadata
- blocks: DocumentBlock[]
- tables: TableBlock[]
- controls: FormControlBlock[]
- sections: SectionTree
- regions: RegionIndex
- indexes: ContractIndex
- parseQualityReport: ParseQualityReport
```

### 5.2 DocumentBlock

```text
DocumentBlock
- blockId
- type: HEADING / PARAGRAPH / TABLE_ROW / APPENDIX_TITLE / TOC_ITEM
- text
- normalizedText
- sectionPath
- regionType
- contextType: NORMAL / EXAMPLE / EXPLANATION / HISTORY / HEADER_FOOTER / DELETED / VOIDED / TOC
- sourceOrigin: NATIVE_WORD / LIBREOFFICE_CONVERSION / PREVIEW_ONLY
- sourceExtractionMode: STRUCTURED / TEXT_FALLBACK / PREVIEW
- sourceFileId
- tableId?
- rowIndex?
- blockConfidence
```

### 5.3 TableBlock

```text
TableBlock
- tableId
- sourceFileId
- sectionPath
- regionType
- rows: TableRowBlock[]
- hasMergedCells
- hasNestedTable
- tableConfidence
- warnings
```

### 5.4 FormControlBlock

```text
FormControlBlock
- blockId
- controlType: CHECKBOX / RADIO / CONTENT_CONTROL / SYMBOL_CHECK / TEXT_FIELD
- label
- value
- nearbyText
- tableId?
- rowIndex?
- sectionPath
- controlConfidence
- warnings
```

### 5.5 ParseQualityReport

最小字段要求：

```text
ParseQualityReport
- fileType
- parser
- language
- textLength
- blockCount
- headingCount
- tableCount
- formControlCount
- appendixRegionCount
- tocDetected
- parseStatus: GOOD / PARTIAL / LOW_CONFIDENCE / FAILED
- confidenceSummary
- lowConfidenceRegionCount
- lowConfidenceBlockCount
- lowConfidenceTableCount
- warnings[]
```

## 6. Provenance 与置信度

以下字段必须贯穿解析产物、索引、候选证据和结果定位：

- `contextType`
- `sourceOrigin`
- `sourceExtractionMode`
- `blockConfidence`
- `tableConfidence`
- `controlConfidence`
- `regionConfidence`

约束：

- `sourceOrigin=PREVIEW_ONLY` 不得进入业务裁判
- `TEXT_FALLBACK` 不得单独支撑 `HIGH` 角色归属
- `TEXT_FALLBACK` 不得单独支撑确定性 `ERROR`
- `TEXT_FALLBACK` 不得生成 `PROVEN_REQUIRED_CLAUSE_ABSENT`

## 7. DefinitionTermIndex 边界

- `DefinitionTermIndex` 不属于 Parser
- Parser 只负责产出结构化 blocks / tables / controls / sections
- `DefinitionTermIndex` 在解析后与 `CandidateIndex` 并行构建
- Parser 必须保留足够的结构化 block 与 source anchor，供 `DefinitionTermIndex` 后续构建与按需注入

## 8. 预览定位边界

### 8.1 MVP 目标

MVP 需要支持“审核点 -> 证据 -> 原文定位”的预览闭环，但不要求所有证据都具备字符级高亮。

### 8.2 定位等级

```text
EXACT_TEXT_RANGE
BLOCK_LEVEL
PAGE_LEVEL
SECTION_LEVEL
UNAVAILABLE
```

### 8.3 MVP 最低验收

- 普通结果页最低必须支持 `BLOCK_LEVEL`
- 表格证据最低必须支持 `row` 或 `cell` 级
- 无法给出字符级范围本身不得导致 `NOT_CONCLUDED`
- 仅当审核点要求的最小定位等级未满足时，才可因定位不足导致该点无法裁判

### 8.4 映射约束

- 预览位置来自同一 source file 生成的 PDF/HTML preview
- 不是原始 Word 二进制内部坐标
- 结构化 block 必须可关联 `previewAssetId + previewPage/elementRef + blockId`

## 9. 降级规则

### 9.1 ParseStatus

- `GOOD`：进入完整审核
- `PARTIAL`：允许审核，但受影响点需保留低置信提示
- `LOW_CONFIDENCE`：只跑基础规则，复杂点输出 `SYS-PARSE-LOW-CONFIDENCE`
- `FAILED`：不输出业务 finding

### 9.2 Preview-only

若仅成功生成预览，但未得到可裁判结构化文本：

- 允许保留 preview asset
- 允许保留 parser diagnostic
- 不得进入 CandidateIndex
- 不得进入业务裁判

## 10. 样例验收口径

TASK-004 完成后的样例核对至少要覆盖：

- 普通正文
- 标题与章节路径
- 至少一张普通表格
- 至少一种表单控件或符号勾选
- 至少一个附件/附录区域
- 至少一种预览定位降级场景

## 11. 后续任务接口

本冻结结论直接服务于：

- `TASK-014-minimal-openapi-contract`
- `TASK-006-scaffold-only-after-adr`
- 后续 Word parser spike 任务
- 样例生成与解析验证任务

## 12. 待确认

- 预览最终采用 PDF、HTML 还是双格式并存
- 嵌入式附件 metadata 是否进入 MVP 只读诊断范围
- 表单控件中极少见 Word 控件类型是否统一降级为 `TEXT_FIELD` 或 `warnings`
