# TASK-016：MVP 开发与验证

状态：已完成
类型：开发验证 / 集成测试计划 / MVP 验收

优先级：P0
负责人：待确认
创建日期：2026-06-10

来源：`CURRENT_CONTEXT.md` 当前下一步、`TASK-004-word-parser-baseline-plan`、`TASK-005-model-gateway-and-budget-baseline`、`TASK-006-scaffold-only-after-adr`

## 背景

在 Word parser、模型网关与预算、脚手架、最小 OpenAPI 和 Flyway 初始迁移冻结并落地后，需要有一组明确的 MVP 开发验证任务包，保证首轮开发不是只做代码搭建，而是围绕样例、契约、调优包和管理台摘要进行可验证推进。

本任务用于定义 MVP 开发验证阶段的目标、最小测试计划和验证顺序。

## 目标

- 明确 MVP 开发验证范围和顺序。
- 输出最小测试计划和验收目标。
- 覆盖 Word parser、模型客户端、AI 调优包、管理台点级诊断与执行摘要四类验证主题。
- 为后续实现任务提供可执行验收口径。

## 非目标

- 不在本任务中直接实现所有开发项。
- 不扩展 Pilot / Production Readiness 能力。
- 不引入新审核点、新合同类型或新治理模块。

## 输入

- 相关文档：`AGENTS.md`、`CURRENT_CONTEXT.md`、`PRD.md`
- 相关架构章节：`docs/ARCHITECTURE.md` 中审核链路、结果契约、TuningPacket、任务状态机章节
- 相关 ADR：`ADR-010`、`ADR-011`、`ADR-012`
- 上游任务：`TASK-004`、`TASK-005`、`TASK-006`、`TASK-014`、`TASK-015`

## 依赖

- 强依赖：`TASK-004-word-parser-baseline-plan`
- 强依赖：`TASK-005-model-gateway-and-budget-baseline`
- 强依赖：`TASK-006-scaffold-only-after-adr`
- 强依赖：`TASK-014-minimal-openapi-contract`
- 强依赖：`TASK-015-flyway-v1-bootstrap`

## 范围

### 包含

- MVP 测试计划。
- 验证目标与通过标准。
- 以下开发验证主题：
  - Word parser Spike / 测试样例生成
  - 模型客户端契约测试
  - AI 调优包导出验证
  - 管理台点级诊断和执行摘要验证
- 最小样例准备要求和验证顺序。

### 不包含

- 不定义 Pilot/Production 完整压测计划。
- 不定义复杂安全测试、BI 测试或多租户测试。
- 不实现完整 CI 流程扩展。

## 约束

- 开始任务前必须阅读 `AGENTS.md`、`CURRENT_CONTEXT.md` 和本任务包。
- 所有验证都必须遵循 MVP 范围冻结边界。
- 不得让模型直接决定最终业务 finding。
- 不得在 Evidence 不可靠时生成业务 finding。
- 不确定内容统一标记为“待确认”。

## 需要阅读的记忆文件

- 必读：`AGENTS.md`
- 必读：`CURRENT_CONTEXT.md`
- 必读：本任务包
- 按需：`docs/backend.md`
- 按需：`docs/database.md`
- 按需：`docs/ai-review.md`
- 按需：`docs/deployment.md`
- 必读：`decisions/ADR-010-technology-stack-freeze.md`
- 必读：`decisions/ADR-012-domain-model-freeze.md`

## 交付物

- MVP 测试计划
- 验证目标清单
- 样例准备要求
- 开发验证主题拆分说明
- 更新后的项目记忆与任务完成记录

## 验收标准

- 能明确说明 MVP 首轮需要验证的开发主题和顺序。
- 能明确每类验证的最小通过标准。
- 依赖关系与 `TASK-004 / TASK-005 / TASK-006 / TASK-014 / TASK-015` 一致。
- 不把 Pilot / Production 能力混入 MVP 首轮开发验证。

## 执行结论

### 1. MVP 开发前验证顺序

1. 先确认样例与运行前置条件，而不是直接写业务逻辑。
2. 先完成 Word parser Spike 与样例解析验证，确认 `.docx -> 结构化块/表格/控件/预览定位` 闭环。
3. 再完成模型客户端契约测试，确认 `ModelProfile / ModelCallIntent / schema validation / SYS-MODEL-*` 的最小行为。
4. 再完成 AI 调优包导出验证，确认 `TuningPacket / PointDiagnostic / ExecutionSummary` 只作为诊断产物，不回写正式结果。
5. 最后完成管理台点级诊断与执行摘要验证，确认页面和 API 展示的是业务解释与摘要诊断，而不是 prompt 或 raw output。

### 2. 当前仓库已具备的前置基线

- `TASK-004` 已冻结 Word parser MVP 边界，覆盖 `.docx`、表格、控件、附件区域和 preview 定位降级口径。
- `TASK-005` 已冻结 ModelGateway、预算、timeout、retry 和 circuit breaker 基线。
- `TASK-006` 已建立 `apps/admin-web`、`apps/api-server`、`deploy/compose` 和 `packages/test-fixtures` 骨架，并暴露前后端最小命令入口。
- `TASK-014` 已生成最小 OpenAPI 契约到 `packages/api-contracts/`。
- `TASK-015` 已生成 Flyway V1 核心迁移，数据库最小对象边界已冻结。
- `packages/test-fixtures/` 已落地 4 份 `.docx` 样例与 `cqcp-mvp-sample-matrix.xlsx`，可作为 `GOLDEN_EXPECTED / NEGATIVE_CANDIDATE / expected.json` 的前置数据基线。

### 3. 当前不能跳过的前置门槛

- `packages/test-fixtures/` 的首批 `expected/*.json` 已落地，可作为 parser Spike 与后续验证输入基线。
- `apps/admin-web` 的 `build/lint` 已通过，`test` 在提权条件下通过；默认沙箱下仍受目录读取权限模型阻塞。
- `apps/api-server` 的 `gradle test/build` 已在提权条件下通过；后续执行仍需显式设置 `JAVA_HOME/PATH` 与 `CQCP_DB_*`。
- Docker PostgreSQL 15 容器回放验证已由 `TASK-015` 完成；当前数据库、前后端脚手架与首批 expected fixtures 均已具备后续实现前置基线。

## MVP 最小测试计划

### A. Word parser Spike / 样例解析验证

目标：
验证 `.docx` 样例能产出符合 MVP 边界的结构化解析对象，并保留后续证据定位所需的 provenance 字段。

输入：
- `docs/word-parser-mvp-boundary.md`
- `docs/backend.md`
- `packages/test-fixtures/` 中的合成 `.docx` 样例或由业务方确认可用于验证的 `.docx` 样例

最小通过标准：
- 至少覆盖正文段落、标题路径、1 张表格、1 组控件或符号勾选、1 个附件/附录区域。
- 能验证 `contextType / sourceOrigin / sourceExtractionMode / blockConfidence` 字段未被省略。
- 至少覆盖 1 个 preview 定位降级场景，且最低仍满足 `BLOCK_LEVEL` 或表格 `row/cell` 级定位口径。
- 仅有 `PREVIEW_ONLY` 产物时，验证计划明确要求输出解析失败或低置信降级，不进入业务 finding。

### B. 模型客户端契约测试

目标：
验证模型接入层只承担局部辅助，不绕过后端裁判，也不把无效输出带入业务结果。

输入：
- `docs/model-gateway-budget-baseline.md`
- `docs/ai-review.md`
- `apps/api-server` 后续模型客户端与 mock/profile 配置实现

最小通过标准：
- 能覆盖 `LOCAL / PUBLIC_OPENAI_COMPATIBLE / MOCK` 三类 provider 中至少 1 个真实执行入口和 1 个 mock 入口。
- 能记录 `ModelCallIntent` 最小字段，并区分 family primary call 与 point supplement call。
- schema invalid、超时、不可用、冲突等失败场景均映射为对应 `SYS-MODEL-*`，不生成业务 finding。
- 明确复用边界只允许发生在同 `taskId + executionId` 语义内。

### C. AI 调优包导出验证

目标：
验证调优治理链路只导出诊断材料，不修改正式审核结果，也不扩大敏感信息暴露面。

输入：
- `docs/ai-review.md`
- `ADR-012`
- `ADR-002`

最小通过标准：
- 至少覆盖 `SINGLE_POINT` 与 `FOCUSED` 两种导出模式。
- 导出物中包含 `ExecutionSummary`、`PointDiagnostic[]`、必要证据摘要和版本引用。
- 不包含完整合同、完整 prompt、完整 raw output、endpoint secret 或 stack trace。
- 导出验证明确区分“可复制给外部 AI 的诊断材料”与“不会回写当前 ReviewResultSnapshot”的治理边界。

### D. 管理台点级诊断与执行摘要验证

目标：
验证管理台展示的是可执行摘要和业务化诊断，而不是纯 UI 空壳。

输入：
- `docs/frontend.md`
- `docs/backend.md`
- `ADR-002`
- `ADR-003`

最小通过标准：
- 任务详情顶部概览至少覆盖 `taskId / executionId / stage / 审核模型 / 点级摘要计数`。
- 点级结果能区分 `PASS / ERROR / WARNING / NOT_CONCLUDED / SKIPPED`，并保留业务化原因。
- 管理台可查看摘要级 `SYS-*`、模型调用状态、预算诊断和阶段日志，但不展示完整 prompt 或完整 raw output。
- 执行摘要必须能反映解析低置信、模型不可用、证据不足、结果合成失败等业务化状态。

## 最小样例准备要求

- 样例目录优先使用现有 `packages/test-fixtures/`；最终命名规范仍为“待确认”，本任务不写死新规范。
- MVP 首轮样例至少包含 1 份合成 `.docx` 工程采购合同，用于覆盖标题、正文、表格、控件、附件区域和基础预览定位。
- 现有 4 份 `.docx` 样例与 `cqcp-mvp-sample-matrix.xlsx` 应优先作为首轮输入，不再重复创建平行样例池。
- 至少准备 1 份冲突证据或候选归属歧义样例，用于验证 `NOT_CONCLUDED / EVIDENCE_AMBIGUOUS / SYS-ROLE-CONFLICT` 路径；若现有样例矩阵已覆盖，应直接复用矩阵定义。
- 至少准备 1 份解析降级样例，用于验证 `LOW_CONFIDENCE / PREVIEW_ONLY / BLOCK_LEVEL` 相关边界。
- 至少准备 1 份 AI 调优包导出样例，用于验证 redacted 诊断材料不泄露完整敏感上下文。
- 非合成样例若进入仓库或评测环境，必须先由业务方或 data owner 确认“可用于项目验证”；在确认前统一视为“待确认”。

## 建议后续实现切分

- 先补样例与预期结果，再进入业务实现任务。
- Word parser Spike、模型客户端契约测试、AI 调优包导出、管理台诊断摘要建议拆成独立后续实现任务。
- `TASK-006` 与 `TASK-015` 的环境级补验证仍是后续实现前的必做校验，不应被跳过。

## 2026-06-12 最小闭环推进记录

### 本轮完成

- 在 `apps/api-server/build.gradle.kts` 增加 `org.apache.poi:poi-ooxml`，仅用于 `TASK-016` 的 `.docx` parser Spike 验证。
- 新增 `apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/WordParserSpikeDocument.java`，定义最小解析产物：
  - `Metadata`
  - `DocumentBlock`
  - `TableBlock`
  - `FormControlBlock`
  - `ParseQualityReport`
- 新增 `apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/DocxWordParserSpike.java`：
  - 基于 Apache POI 读取 `.docx`
  - 输出正文块、标题块、表格行、符号勾选控件和解析质量摘要
  - 保留 `contextType / sourceOrigin / sourceExtractionMode / blockConfidence / previewAnchorLevel`
  - 以 `附件/附录` 标题识别附录区域
- 新增 `apps/api-server/src/test/java/com/cqcp/apiserver/wordparser/DocxWordParserSpikeTest.java`：
  - 读取 `packages/test-fixtures/expected/*.json`
  - 一一映射 4 份 `.docx` 样例
  - 校验解析产物非空、provenance 字段不缺失、项目名与甲方名称可在解析文本中找到
  - 聚合校验 4 份样例整体覆盖标题、表格行、附件区域和符号勾选

### 本轮验证结果

- 在 `apps/api-server` 下显式设置：
  - `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`
  - `PATH` 前置 `C:\Gradle\gradle-8.10.2\bin`
  - `CQCP_DB_URL=jdbc:postgresql://localhost:54329/cqcp_app_test`
  - `CQCP_DB_USERNAME=postgres`
  - `CQCP_DB_PASSWORD=postgres`
- 以 `require_escalated` 执行：
  - `gradle test` -> `BUILD SUCCESSFUL in 7s`
  - `gradle build` -> `BUILD SUCCESSFUL in 1s`

### 本轮范围控制

- 未实现 Review Engine。
- 未实现结构化字段正式抽取。
- 未实现模型调用、TuningPacket 导出或管理台业务页面。
- 未修改 schema、migration SQL 或新增 Repository / Mapper。

### 当前结论

- `TASK-016` 的第一个最小闭环“Word parser Spike”已具备可执行验证结果。
- 该结果只证明 `.docx -> 结构块/表格/符号勾选/解析质量摘要` 的最小技术闭环已跑通，不代表完整合同审核平台已开始开发。
- 在本轮模型契约验证完成后，`TASK-016` 后续只剩 2 个验证主题：AI 调优包导出验证、管理台点级诊断与执行摘要验证。

## 2026-06-12 模型客户端契约测试推进记录

### 本轮完成

- 新增 `apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/*` 最小模型契约层，包含：
  - `ModelProviderType`
  - `ModelCallIntent`
  - `ModelProfile`
  - `ModelDiagnosticCode`
  - `NotConcludedReason`
  - `ModelProvider`
  - `ModelProviderResponse`
  - `ModelGatewayArtifact`
  - `ModelGatewayResult`
  - `ModelProviderException`
  - `ModelGatewayService`
  - `MockModelProvider`
- 仅实现 `MOCK` provider，不接真实 `LOCAL` 或 `PUBLIC_OPENAI_COMPATIBLE` endpoint。
- 在 `ModelGatewayService` 中验证：
  - `ModelCallIntent` 与 `ModelProfile` 最小字段透传
  - mock success 返回 artifact
  - schema invalid -> `SYS_MODEL_OUTPUT_INCOMPLETE`
  - timeout -> `SYS_MODEL_TIMEOUT`
  - unavailable -> `SYS_MODEL_UNAVAILABLE`
  - conflict -> `SYS_MODEL_CONFLICT`
- 所有失败路径统一停留在诊断层，映射为业务侧 `MODEL_UNAVAILABLE` 式未结论原因，不生成业务 finding。

### 本轮测试

- 新增 `apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/ModelGatewayServiceTest.java`
- 覆盖以下场景：
  - mock success
  - schema invalid
  - timeout
  - unavailable
  - conflict
  - disabled profile

### 本轮验证结果

- 在 `apps/api-server` 下显式设置：
  - `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`
  - `PATH` 前置 `C:\Gradle\gradle-8.10.2\bin`
  - `CQCP_DB_URL=jdbc:postgresql://localhost:54329/cqcp_app_test`
  - `CQCP_DB_USERNAME=postgres`
  - `CQCP_DB_PASSWORD=postgres`
- 以 `require_escalated` 执行：
  - `gradle test` -> `BUILD SUCCESSFUL in 7s`
  - `gradle build` -> `BUILD SUCCESSFUL in 1s`

### 本轮范围控制

- 未接真实 `Gemma`、`DeepSeek` 或公网兼容 provider。
- 未实现 retry、breaker、operational call attempts 的持久化。
- 未实现 `GemmaExtractionArtifact` 全量字段、TuningPacket、PointDiagnostic 或 Review Engine 集成。
- 未修改 schema、migration SQL、Repository、Mapper 或业务裁判逻辑。

### 当前结论

- `TASK-016` 的第二个最小闭环“模型客户端契约测试”已具备可执行验证结果。
- 当前只证明模型接入层的最小契约、mock success 与 `SYS-MODEL-*` 失败映射边界已跑通，不代表真实模型环境已接入。
- `TASK-016` 后续只剩 2 个验证主题：AI 调优包导出验证、管理台点级诊断与执行摘要验证。

## 2026-06-12 AI 调优包导出验证推进记录

### 本轮完成

- 新增 `apps/api-server/src/main/java/com/cqcp/apiserver/tuning/*` 最小导出层，包含：
  - `ExportMode`
  - `VersionRefs`
  - `ExecutionSummary`
  - `PointDiagnostic`
  - `TuningPacketSource`
  - `ExportConfig`
  - `TuningPacketExport`
  - `TuningPacketExportService`
- 仅实现两种导出模式：
  - `SINGLE_POINT`
  - `FOCUSED`
- 在 `TuningPacketExportService` 中验证：
  - 导出物包含 `ExecutionSummary`、`PointDiagnostic[]` 与 `VersionRefs`
  - 导出物写入治理边界说明：`AI Advice != Production Change`
  - 显式声明排除：
    - `FULL_CONTRACT_TEXT`
    - `FULL_PROMPT`
    - `FULL_RAW_OUTPUT`
    - `ENDPOINT_SECRET`
    - `STACK_TRACE`
- 对带敏感 payload 的诊断对象直接拒绝导出，不允许形成对外 AI 调优包。

### 本轮测试

- 新增 `apps/api-server/src/test/java/com/cqcp/apiserver/tuning/TuningPacketExportServiceTest.java`
- 覆盖以下场景：
  - `SINGLE_POINT` 导出
  - `FOCUSED` 导出
  - 敏感 payload 拒绝导出
  - 空选择拒绝导出

### 本轮验证结果

- 在 `apps/api-server` 下显式设置：
  - `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`
  - `PATH` 前置 `C:\Gradle\gradle-8.10.2\bin`
  - `CQCP_DB_URL=jdbc:postgresql://localhost:54329/cqcp_app_test`
  - `CQCP_DB_USERNAME=postgres`
  - `CQCP_DB_PASSWORD=postgres`
- 以 `require_escalated` 执行：
  - `gradle test` -> `BUILD SUCCESSFUL in 7s`
  - `gradle build` -> `BUILD SUCCESSFUL in 1s`

### 本轮范围控制

- 未接外部 AI。
- 未保存外部 AI 建议文本。
- 未实现 `FULL` 导出模式。
- 未接数据库中的 `tuning_packet / point_diagnostic` 表。
- 未实现管理台 `AI 调优包` tab 或任何前端页面。
- 未修改 schema、migration SQL、Repository、Mapper 或正式审核链路。

### 当前结论

- `TASK-016` 的第三个最小闭环“AI 调优包导出验证”已具备可执行验证结果。
- 当前只证明导出模式、诊断摘要边界与敏感内容拒绝导出的最小契约已跑通，不代表完整调优治理链路已接通。
- `TASK-016` 后续只剩 1 个验证主题：管理台点级诊断与执行摘要验证。

## 2026-06-12 管理台点级诊断与执行摘要验证推进记录

### 本轮完成

- 新增 `apps/api-server/src/main/java/com/cqcp/apiserver/admindiagnostics/AdminTaskDiagnosticsSource.java`，定义最小诊断摘要输入对象。
- 新增 `apps/api-server/src/main/java/com/cqcp/apiserver/admindiagnostics/AdminTaskDiagnosticsView.java`，定义管理台最小展示契约，覆盖：
  - `taskId / executionId / contractName / stage`
  - `modelSummary`
  - `plannedPointCount / passCount / errorCount / warningCount / notConcludedCount / skippedCount`
  - `pointResults`
  - `stageLogs`
  - `summaryDiagnosticCodes`
  - `excludedSensitiveFields`
- 新增 `apps/api-server/src/main/java/com/cqcp/apiserver/admindiagnostics/AdminTaskDiagnosticsViewFactory.java`，将执行级诊断输入映射为管理台摘要视图，并显式排除：
  - `promptPreview`
  - `rawOutput`
  - `endpointSecret`
  - `stackTrace`
- 新增 `apps/api-server/src/test/java/com/cqcp/apiserver/admindiagnostics/AdminTaskDiagnosticsViewFactoryTest.java`，验证点级计数、点级状态、日志、诊断码与敏感字段排除边界。
- 新增 `apps/admin-web/src/AdminTaskDiagnosticsPreview.tsx`，落地最小静态/测试可验证组件，只展示概览、点级摘要、阶段日志和诊断码。
- 更新 `apps/admin-web/src/App.tsx`，在现有 scaffold 页面中挂载最小诊断摘要预览，不引入复杂路由或真实业务联调。
- 更新 `apps/admin-web/src/App.test.tsx`，校验：
  - `TASK-016 管理台诊断摘要预览`
  - `PASS 5`
  - `SYS_MODEL_TIMEOUT`
  - `模型暂时不可用，未形成正式结论。`
  - `FULL_PROMPT` 不出现在页面
- 更新 `apps/admin-web/src/styles.css`，补充最小预览布局样式。

### 本轮验证结果

- 在 `apps/api-server` 下显式设置：
  - `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`
  - `PATH` 前置 `C:\Gradle\gradle-8.10.2\bin`
  - `CQCP_DB_URL=jdbc:postgresql://localhost:54329/cqcp_app_test`
  - `CQCP_DB_USERNAME=postgres`
  - `CQCP_DB_PASSWORD=postgres`
- 以 `require_escalated` 执行：
  - `gradle test` -> `BUILD SUCCESSFUL`
  - `gradle build` -> `BUILD SUCCESSFUL`
- 在 `apps/admin-web` 下以 `require_escalated` 执行：
  - `npm.cmd run build` -> 通过
  - `npm.cmd run lint` -> 通过
  - `npm.cmd run test` -> 通过

### 本轮范围控制

- 未实现真实管理台任务详情 API。
- 未实现复杂路由、完整交互或真实业务联调。
- 未实现 Review Engine、Word parser 正式字段抽取或真实模型调用。
- 未修改 schema、migration SQL、Repository 或 Mapper。

### 当前结论

- `TASK-016` 的第四个最小闭环“管理台点级诊断与执行摘要验证”已具备可执行验证结果。
- 当前只证明管理台诊断摘要契约与最小展示路径已跑通，不代表完整管理台任务详情、真实接口联调或审核业务链路已实现。
- `TASK-016` 定义的 4 个 MVP 开发前验证主题现已全部完成本轮最小闭环验证。

## 测试与验证

- 本任务本身为测试计划任务，不实现业务代码。
- 验证方式为人工审查测试计划完整性、依赖完整性和 MVP 边界一致性。

## 文档更新要求

- 必须更新 `CURRENT_CONTEXT.md`
- 必须更新 `changelog/2026-06.md`
- 必须更新本任务完成记录
- 是否需要更新 `docs/backend.md`、`docs/ai-review.md`：待确认
- 是否需要新增或更新 ADR：否

## 风险

- 若没有统一验证计划，scaffold 之后容易进入只搭工程不验业务闭环的状态。
- 若样例准备、契约测试和调优包验证顺序混乱，会导致问题定位成本升高。
- 若把管理台验证做成纯 UI 验证，会遗漏点级诊断和执行摘要的业务解释责任。

## 待确认

- 样例文件命名规范和存放目录的最终格式。
- MVP 阶段是否先采用合成样例，再接入由业务方确认可用于验证的非合成样例。
- 管理台点级诊断与执行摘要验证是否拆成独立后续实现任务。

## 完成记录

- 完成日期：2026-06-12
- 最近更新：2026-06-12
- 变更文件：`tasks/active/TASK-016-mvp-development-validation.md`、`CURRENT_CONTEXT.md`、`changelog/2026-06.md`、`apps/api-server/build.gradle.kts`、`apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/*`、`apps/api-server/src/test/java/com/cqcp/apiserver/wordparser/*`、`apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/*`、`apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/*`、`apps/api-server/src/main/java/com/cqcp/apiserver/tuning/*`、`apps/api-server/src/test/java/com/cqcp/apiserver/tuning/*`、`apps/api-server/src/main/java/com/cqcp/apiserver/admindiagnostics/*`、`apps/api-server/src/test/java/com/cqcp/apiserver/admindiagnostics/*`、`apps/admin-web/src/AdminTaskDiagnosticsPreview.tsx`、`apps/admin-web/src/App.tsx`、`apps/admin-web/src/App.test.tsx`、`apps/admin-web/src/styles.css`
- 测试结果：
  - 人工审查通过：验证主题、依赖关系、最小通过标准与 `TASK-004 / TASK-005 / TASK-006 / TASK-014 / TASK-015` 一致。
  - 静态核对通过：已确认 `apps/admin-web` 暴露 `build/lint/test` 命令，`apps/api-server` 暴露 `gradle test/build` 建议入口，`packages/test-fixtures/` 已包含 4 份 `.docx` 样例、`cqcp-mvp-sample-matrix.xlsx` 与 `expected/*.json`。
  - 运行时验证通过：`DocxWordParserSpikeTest` 已基于 4 份现有样例夹具通过，`gradle test/build` 在提权条件下通过。
  - 运行时验证通过：`ModelGatewayServiceTest` 已覆盖 mock success、schema invalid、timeout、unavailable、conflict、disabled profile，`gradle test/build` 在提权条件下通过。
  - 运行时验证通过：`TuningPacketExportServiceTest` 已覆盖 `SINGLE_POINT`、`FOCUSED`、敏感 payload 拒绝导出与空选择拒绝导出，`gradle test/build` 在提权条件下通过。
  - 运行时验证通过：`AdminTaskDiagnosticsViewFactoryTest` 已覆盖管理台最小诊断摘要契约；`apps/admin-web` 的 `build/lint/test` 已在提权条件下通过。
- 遗留问题：
  - 默认沙箱下前端 `npm test` 仍受目录读取权限模型阻塞。
  - 当前 parser Spike 尚未实现正式结构化字段抽取、preview 资产联动与 Review Engine 接口。
  - 当前模型契约层尚未接入真实 provider，也未落地 retry / breaker / 调用记录持久化。
  - 当前 AI 调优包导出层尚未接数据库实体、未接外部 AI 建议保存、未实现 `FULL` 模式。
- 备注：本任务已完成“验证计划整理 + 4 个最小闭环推进”收口；本轮未触发 ADR，未扩大到完整审核业务开发。
