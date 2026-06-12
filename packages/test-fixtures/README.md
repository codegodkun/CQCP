# CQCP MVP Test Fixtures

本目录存放 CQCP MVP 第一批测试样例，用于 Word parser、结构化字段抽取、审核点判断、证据定位，以及 `expected/*.json` 夹具维护。

## 当前作用

`cqcp-mvp-sample-matrix.xlsx` 当前承担以下职责：

1. 记录 4 份合同样例的 `GOLDEN_EXPECTED` 正确数据。
2. 记录 `NEGATIVE_CANDIDATE` 错误候选数据。
3. 说明哪些错误属于单点错误，哪些会联动触发公式错误。
4. 作为后续生成 `expected/*.json` 的来源基础。

## 文件说明

- `docx/`：4 份 `.docx` 合同样例文件。
- `cqcp-mvp-sample-matrix.xlsx`：样例矩阵，承载正确数据、错误候选数据与联动说明。
- `expected/`：第一批 expected result JSON 文件目录。

## 当前样例

| sampleId | 文件 | 说明 |
|---|---|---|
| CQCP-MVP-DOCX-001 | 1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx | 工程采购合同缩减版 |
| CQCP-MVP-DOCX-002 | 2、达利安造船厂四方城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx | 产品采购合同缩减版 |
| CQCP-MVP-DOCX-003 | 3、星辰建设集团南山科技园项目二标段土建总承包工程合同_缩减版.docx | 工程采购合同缩减版 |
| CQCP-MVP-DOCX-004 | 4、东海船舶工业集团滨海城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx | 产品采购合同缩减版 |

## Expected 夹具

当前已生成：

- `expected/CQCP-MVP-DOCX-001.json`
- `expected/CQCP-MVP-DOCX-002.json`
- `expected/CQCP-MVP-DOCX-003.json`
- `expected/CQCP-MVP-DOCX-004.json`

每份 `expected/*.json` 当前包含：

- `sampleId`
- `sourceDocx`
- `matrixWorkbook`
- `matrixSheet`
- `goldenExpected`
- `negativeCandidates`

其中：

- `goldenExpected` 保存该样例的 `GOLDEN_EXPECTED` 基线。
- `negativeCandidates` 保存该样例的 `NEGATIVE_CANDIDATE` 候选数据。
- 每个 negative candidate 同时保存：
  - `allDiffs`
  - `singlePointDiffs`
  - `formulaLinkedEffects`

## 映射关系

- 4 份 `.docx` 样例与 4 份 `expected/*.json` 一一对应。
- `cqcp-mvp-sample-matrix.xlsx` 的 `合同清单` sheet 是当前唯一来源表。
- `GOLDEN_EXPECTED` 行写入对应样例的 `goldenExpected`。
- `NEGATIVE_CANDIDATE` 行按合同文件名聚合后写入同一样例的 `negativeCandidates[]`。
- matrix 中“同时导致税额公式不一致”的描述当前写入 `formulaLinkedEffects`；“金额数据连环错误”当前写入 `DATA_CASCADE`。

## 使用边界

- CQCP 不负责判断样例是否完成脱敏合规处理。
- 样例进入本目录前，应由业务方或 data owner 判断其是否可用于项目验证。
- 当前 `expected/*.json` 只表达第一批 MVP 测试夹具基线，不等于最终 parser / engine 运行态输出格式。
