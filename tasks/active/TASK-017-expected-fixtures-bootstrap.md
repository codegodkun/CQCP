# TASK-017：Expected 夹具首批落地

状态：已完成

类型：测试夹具 / 数据基线整理 / MVP 开发前验证

优先级：P0

负责人：待确认

创建日期：2026-06-11

来源：`CURRENT_CONTEXT.md` 当前下一步、`TASK-016-mvp-development-validation`、`packages/test-fixtures/README.md`

## 任务命名规范

* 任务文件名使用英文 slug，格式为 `TASK-xxx-english-slug.md`。
* 任务文件标题必须使用中文，格式为 `# TASK-xxx：中文任务名称`。
* 任务正文必须以中文为主。
* 英文 slug 仅作为稳定文件路径和工程标识。

## 背景

`TASK-016` 已完成 MVP 开发前验证基线整理，并明确 `packages/test-fixtures/` 现有 4 份 `.docx` 样例和 `cqcp-mvp-sample-matrix.xlsx` 已可作为首轮验证输入。

当前样例矩阵已承担以下职责：

* 记录 4 份合同的 `GOLDEN_EXPECTED` 正确数据。
* 记录 `NEGATIVE_CANDIDATE` 错误候选数据。
* 标注哪些错误属于单点错误，哪些会联动触发公式错误。
* 作为后续生成 `expected/*.json` 的来源基础。

后续在进入 Word parser Spike、Review Engine 或更高层验证前，需要先把这批样例矩阵转成第一批可执行的 `expected/*.json` 夹具，形成稳定输入与预期结果基线。

## 目标

* 基于现有 4 份 `.docx` 样例和 `cqcp-mvp-sample-matrix.xlsx`，生成第一批 `expected/*.json` 可执行夹具。
* 明确 `expected/*.json` 的最小结构、命名方式和与样例矩阵的映射关系。
* 更新 `packages/test-fixtures/README.md`，说明样例文件、矩阵和 `expected/*.json` 的对应关系。
* 更新 `CURRENT_CONTEXT.md` 与 `changelog/2026-06.md`，写回该批测试夹具基线。

## 非目标

* 不实现 Word parser。
* 不实现 Review Engine。
* 不实现模型调用、ModelGateway 或 AI 调优包导出逻辑。
* 不实现管理台能力。
* 不新增或替换样例数量，范围固定为当前 4 份 `.docx` 与 1 份样例矩阵。
* 不把 `expected/*.json` 扩展为完整评测平台或通用样例 DSL。

## 输入

* 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`PRD.md`
* 相关架构章节：`docs/ARCHITECTURE.md` 中结果契约、证据定位、业务 Finding 与 `SYS-*` 分流章节
* 相关 ADR：`ADR-002`、`ADR-010`、`ADR-011`、`ADR-012`
* 上游任务：`TASK-014`、`TASK-015`、`TASK-016`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `packages/test-fixtures/README.md`
* `tasks/active/TASK-016-mvp-development-validation.md`

### Optional Context

* `PRD.md`
* `docs/backend.md`
* `docs/frontend.md`
* `docs/context-management.md`
* `decisions/ADR-002-v1-result-and-diagnostic-contract.md`
* `decisions/ADR-012-domain-model-freeze.md`

### Out of Scope

* Word parser 的结构化提取实现。
* 审核点业务裁判实现。
* 模型契约测试或 mock server 对接。
* 新样例采集、样例脱敏、样例扩容。
* 管理台页面、结果页或 API 功能开发。

## 范围

### 包含

* 定义 `expected/*.json` 第一版最小结构。
* 为当前 4 份样例生成对应的第一批 `expected/*.json`。
* 约定样例文件名、matrix 行/列与 `expected/*.json` 的映射关系。
* 在 README 中写清楚：
  * 4 份 `.docx` 样例的作用。
  * `cqcp-mvp-sample-matrix.xlsx` 的作用。
  * `expected/*.json` 的作用。
  * 三者的对应关系与维护边界。
* 完成项目记忆写回。

### 不包含

* 不实现自动从 Excel 直接转换 JSON 的脚本，除非本任务明确需要最小生成工具且不越界。
* 不扩展到第 5 份或更多样例。
* 不引入新的审核点、合同类型、预算 profile 或治理对象。
* 不修改 OpenAPI、Flyway、领域模型或架构 ADR。

## 约束

* 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
* 根据任务需要按需阅读相关文档。
* 不得默认全量读取项目文档。
* 只完成当前任务包定义的范围。
* 不顺手实现任务外功能。
* 不确定内容标记为“待确认”。
* `expected/*.json` 只表达样例预期结果与必要诊断基线，不提前承载未实现的运行态字段。
* 如涉及结果状态，必须遵循 `PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED` 与 `SYS-*` 分流边界。
* 不把样例矩阵中的错误候选直接写成后端已实现行为；只能写成测试夹具预期。

## 交付物

* `packages/test-fixtures/expected/*.json` 第一批夹具。
* 更新后的 `packages/test-fixtures/README.md`。
* 更新后的 `CURRENT_CONTEXT.md`。
* 更新后的 `changelog/2026-06.md`。
* 本任务完成记录。

## 验收标准

* 当前 4 份 `.docx` 样例都存在对应的 `expected/*.json` 文件。
* 每个 `expected/*.json` 都能表达：
  * 样例标识与源文件映射。
  * `GOLDEN_EXPECTED` 正确数据。
  * `NEGATIVE_CANDIDATE` 错误候选数据。
  * 单点错误与公式联动错误说明。
* README 能让后续任务明确知道 `.docx`、matrix 和 `expected/*.json` 的对应关系。
* 不引入第 5 份样例，不实现 parser / engine / model / admin 功能。
* Memory Writeback 完成且不把未确认内容写成已确认事实。

## 测试与验证

* 静态核对 4 份 `.docx` 与 4 份 `expected/*.json` 是否一一对应。
* 静态核对 `expected/*.json` 是否都能被 JSON 解析。
* 人工核对 README、matrix 职责说明与 `expected/*.json` 字段语义是否一致。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是。
* 是否需要更新 `docs/*.md`：否。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要新增或更新 ADR：否。

## Next Task Handoff

* 任务完成后必须判断是否存在明确下一任务。
* 如果存在明确下一任务，输出可直接复制到新窗口执行的 `Next Task Handoff Prompt`。
* 如果不存在明确下一任务，不输出代码块。
* 如果只是建议、征求意见、需要人工确认或没有明确任务编号，不得放入代码块。
* 没有任务编号时不得生成可执行 Handoff Prompt。

## 风险

* 若 `expected/*.json` 结构过早绑定未实现的运行时对象，后续 parser 或 engine 实现会被无谓牵制。
* 若 matrix 与 JSON 映射规则不清晰，后续会出现维护双份预期数据的不一致风险。
* 若把错误候选与联动关系写得过于隐式，后续无法稳定复用到自动验证。

## 待确认

* `expected/*.json` 是否只保留 MVP 首轮必需字段，还是同时预留部分扩展字段。
* matrix 中错误联动说明最终在 JSON 中采用自然语言字段、枚举字段还是结构化数组字段。

## 完成记录

* 完成日期：2026-06-11。
* 变更文件：
  * `packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json`
  * `packages/test-fixtures/expected/CQCP-MVP-DOCX-002.json`
  * `packages/test-fixtures/expected/CQCP-MVP-DOCX-003.json`
  * `packages/test-fixtures/expected/CQCP-MVP-DOCX-004.json`
  * `packages/test-fixtures/README.md`
  * `CURRENT_CONTEXT.md`
  * `changelog/2026-06.md`
  * `tasks/active/TASK-017-expected-fixtures-bootstrap.md`
* 测试结果：
  * 静态核对通过：4 份 `.docx` 样例已一一对应生成 4 份 `expected/*.json`。
  * JSON 解析通过：4 份 `expected/*.json` 均已完成 UTF-8 读取和 JSON 解析验证。
  * 人工核对通过：README、样例矩阵职责说明与 `expected/*.json` 字段语义一致。
* 遗留问题：
  * `expected/*.json` 仍是第一版基线，后续是否补充 review point 级预期字段仍待确认。
  * matrix 中部分“联动错误”目前按自然语言说明归档到 `formulaLinkedEffects`，后续是否进一步枚举化仍待确认。
* 备注：本任务未实现 Word parser、Review Engine、模型调用、管理台能力，未扩展样例数量。
