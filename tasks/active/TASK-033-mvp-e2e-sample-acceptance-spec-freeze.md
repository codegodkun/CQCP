# TASK-033：MVP 端到端样本验收规格冻结

状态：Active / 规格冻结中 / 独立只读复核 GO / 待 PR merge / 不实现

类型：Codex 主控验收规格任务

优先级：P0

负责人：Codex

创建日期：2026-07-07

来源：用户授权、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`docs/VERIFY.md`、`packages/test-fixtures/README.md`

## 背景

`TASK-GOV-006` 已通过 PR #18 完成云端 PR 触发验证，并通过 PR #23 修正远端状态表述。本任务承接下一阶段 MVP 端到端样本验收的规格冻结工作。

当前仓库已有 `packages/test-fixtures/` 下的 4 份 DOCX 样本、`cqcp-mvp-sample-matrix.xlsx` 和 4 份 `expected/*.json`。这些资产可作为 MVP 端到端验收规格的输入基础，但本任务不修改样本、fixture、expected JSON 或测试代码。

本任务不是派发给 CC-DS 执行环境的实现 `TASK_SPEC`。本任务只冻结验收规格和证据口径，不运行完整验收，不写业务代码，不创建自动化测试。

## 目标

* 冻结 MVP 端到端样本验收的任务边界、输入字段、样本选择原则、验收命令和证据口径。
* 明确 expected 来源的独立性要求，防止把 AI 输出、parser 输出或被测系统 actual 输出倒填为人工 anchor 标准答案。
* 明确 `TASK-DATA-001` 与真实 DOCX `TABLE_CELL` 人工 anchor 标准答案准备的关系。
* 为后续正式端到端验收任务提供可证伪的验收断言。

## 非目标

* 不写业务代码。
* 不改测试代码。
* 不改 fixture 实体内容。
* 不改 `expected/*.json`。
* 不修改 parser、`CandidateResolver`、`EvidenceSlot` 或 `SourceAnchor`。
* 不运行完整 MVP 端到端验收。
* 不归档 `TASK-EVAL-001`。
* 不补足 `TASK-EVAL-001` DoD #12。
* 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 不迁移 historical active 文件。
* 不授权 Gemma Provider、parser provenance、`SourceAnchor` 或 `TABLE_CELL` coverage 实现。

## 输入

* 相关文档：
  * `AGENTS.md`
  * `CURRENT_CONTEXT.md`
  * `tasks/MVP_TASK_MAP.md`
  * `docs/VERIFY.md`
  * `packages/test-fixtures/README.md`
* 相关任务：
  * `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
  * `tasks/active/TASK-GOV-006-submit-authorization-evidence-gate.md`
  * `tasks/done/TASK-030-review-assets-versioning-governance.md`
  * `tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`
* 相关 ADR：
  * `decisions/ADR-014-minimal-candidate-resolver-confidence-gating.md`
  * `decisions/ADR-015-evidence-slot-source-anchor-governance.md`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/MVP_TASK_MAP.md`
* `docs/VERIFY.md`
* `packages/test-fixtures/README.md`

### Optional Context

* `docs/DEVELOPMENT.md`
* `docs/backend.md`
* `docs/frontend.md`
* `docs/word-parser-mvp-boundary.md`
* `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
* `tasks/done/TASK-DEBT-001-review-engine-verified-defects-and-coverage-gap.md`
* `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`

### Out of Scope

* 业务代码、测试代码、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR、PRD。
* parser、`CandidateResolver`、`EvidenceSlot`、`SourceAnchor`。
* `TASK-EVAL-001` 归档或 DoD #12 补足。
* `TASK-028` / `TASK-031` / `TASK-032` 规格冻结、实现或 Review Intake。
* historical active 清理。

## 允许修改文件

本任务允许修改：

* `tasks/active/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`
* `docs/VERIFY.md`
* `CURRENT_CONTEXT.md`
* `tasks/MVP_TASK_MAP.md`
* `changelog/2026-07.md`

本任务使用既有 `docs/VERIFY.md` 作为验收规则入口，不新增 `docs/verify/` 目录。原因：当前只需要冻结验收口径并挂接任务引用，新增目录会扩大文档结构且不是必要前提。

## 禁止修改文件

本任务禁止修改：

* `apps/**`
* `packages/test-fixtures/docx/**`
* `packages/test-fixtures/expected/**`
* `packages/test-fixtures/cqcp-mvp-sample-matrix.xlsx`
* `packages/api-contracts/**`
* `deploy/**`
* `.github/workflows/**`
* `decisions/**`
* `PRD.md`
* `docs/ARCHITECTURE.md`
* 任何 `tasks/active` 到 `tasks/done` 的迁移

## 2-3 份 DOCX 样本选择原则

后续正式 MVP 端到端验收应从 `packages/test-fixtures/README.md` 已登记的 4 份 DOCX 样本中选择 2-3 份，不新增临时样本，不改现有样本内容。

选择原则：

1. 必须至少选择 1 份工程采购合同样本，用于覆盖当前主线合同结构与基础审核点。
2. 必须至少选择 1 份产品采购合同样本，用于覆盖不同合同类型表达差异。
3. 若选择第 3 份样本，应优先选择包含明显表格结构、能暴露证据定位风险的样本；但不得因此宣称真实 DOCX `TABLE_CELL` anchor 已完成验证。
4. 每份样本必须记录 `sampleId`、DOCX 路径、合同类型、验收用途、是否含表格、是否用于正向链路、是否用于负向或冲突验证。
5. 样本是否可用于项目验证、是否完成脱敏合规处理，应由业务方或 data owner 确认；Codex 不自行补全该事实。

当前候选样本池：

| sampleId | DOCX 文件 | 合同类型候选 | 本任务口径 |
|---|---|---|---|
| `CQCP-MVP-DOCX-001` | `packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx` | 工程采购合同缩减版 | 可作为工程采购正向样本候选 |
| `CQCP-MVP-DOCX-002` | `packages/test-fixtures/docx/2、达利安造船厂四方城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx` | 产品采购合同缩减版 | 可作为产品采购正向样本候选 |
| `CQCP-MVP-DOCX-003` | `packages/test-fixtures/docx/3、星辰建设集团南山科技园项目二标段土建总承包工程合同_缩减版.docx` | 工程采购合同缩减版 | 可作为工程采购表达差异候选 |
| `CQCP-MVP-DOCX-004` | `packages/test-fixtures/docx/4、东海船舶工业集团滨海城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx` | 产品采购合同缩减版 | 可作为产品采购表达差异候选 |

本任务不冻结最终样本三选组合；后续正式验收任务应在上述原则内明确选择 2-3 份样本，并记录选择理由。

## 输入字段定义

后续正式 MVP 端到端验收任务必须为每份样本冻结以下输入字段：

| 字段 | 必填 | 说明 |
|---|---|---|
| `sampleId` | 是 | 使用 `packages/test-fixtures/README.md` 中登记的稳定 ID。 |
| `docxPath` | 是 | DOCX 文件仓库路径，不得指向临时文件。 |
| `contractType` | 是 | 合同类型；无法从现有文档确认时写 `待确认`。 |
| `reviewScope` | 是 | 本样本纳入验收的审核点范围。 |
| `expectedSource` | 是 | expected 来源，例如人工矩阵、人工 anchor 标注、当前 parser-backed 一致性基线。 |
| `expectedSourceIndependence` | 是 | 说明 expected 是否独立于被测系统 actual 输出。 |
| `anchorRequirement` | 是 | `BLOCK` / `TABLE_ROW` / `TABLE_CELL` / `UNAVAILABLE`；真实 `TABLE_CELL` 必须依赖人工 anchor 标注。 |
| `negativeOrConflictInput` | 否 | 负向或冲突样本输入来源；若不使用，写 `不适用`。 |
| `expectedObservableOutput` | 是 | 可观测输出，例如 `PointStatus`、`candidateValue`、证据摘要、结果 URL、SYS 诊断。 |
| `acceptanceCommand` | 是 | 实际执行命令或明确标记为后续任务待创建。 |

## 验收命令冻结

本任务只冻结后续验收命令口径，不执行完整验收。

### 提交前文档验证命令

```powershell
git diff --check
git status --short
git diff --name-status
```

预期：

* `git diff --check` 退出码为 0；如仅出现 LF/CRLF warning，可记录为非阻塞。
* `git status --short` 只包含本任务允许修改文件。
* `git diff --name-status` 不出现代码、测试、fixture、expected JSON、workflow、ADR 或 PRD。

### 后续正式 MVP 端到端验收命令候选

后续正式验收任务应优先使用当前仓库已有测试入口，并在执行前只读确认 test class 仍存在：

```powershell
Set-Location apps/api-server
gradle test --tests "com.cqcp.apiserver.reviewengine.ParserBackedEvidenceOverlapBaselineTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest"
gradle test --tests "com.cqcp.apiserver.reviewengine.TaskResultQueryControllerTest"
```

如验收包含公开结果页或管理台展示，还应在仓库根目录运行：

```powershell
npm.cmd run test:admin-web
npm.cmd run build:admin-web
```

限制：

* 上述命令只是后续验收任务的候选命令冻结，不代表本任务已执行完整验收。
* 若后续发现命令与实际测试入口不一致，应先修正验收规格，不得伪造通过结果。
* 本任务不新增 E2E runner，不修改 CI，不新增 test class。

## 证据口径

后续正式 MVP 端到端验收必须提交以下证据：

1. 执行前后 `git status --short`。
2. 实际执行命令的原始 console 输出摘要。
3. 样本清单：`sampleId`、DOCX 路径、合同类型、验收用途。
4. 每个样本的可观测输出：`PointStatus`、`candidateValue`、证据摘要、原文定位或结果 URL。
5. 负向或冲突样本必须说明预期失败、冲突或 `SYS-*` 诊断，不得静默写成业务 Finding。
6. 若使用 parser-backed overlap 指标，只能声明与当前 expected JSON 的一致性和回归稳定性。
7. 若涉及真实 `TABLE_CELL` anchor，必须引用 `TASK-DATA-001` 产出的人工 anchor 标准答案；否则只能标记 `TABLE_CELL` 仍未完成真实 DOCX 端到端验证。
8. 不得使用 `CURRENT_CONTEXT.md` 自述替代测试、console、PR、commit 或独立审计证据。

## expected 来源说明

expected 来源必须逐项说明：

* `GOLDEN_EXPECTED` 业务字段：可引用 `packages/test-fixtures/cqcp-mvp-sample-matrix.xlsx`，但需说明其当前为样例矩阵来源。
* `NEGATIVE_CANDIDATE`：可引用同一矩阵中的错误候选数据和联动说明。
* `expected/*.json`：当前只能作为第一批 MVP 测试夹具基线；不得等同于最终生产 runtime 输出格式。
* parser-backed anchor 一致性 expected：只能声明“与当前 parser-backed 输出和 expected JSON 的一致性 / 回归稳定性”，不得声明独立人工 ground truth 正确。
* 人工 anchor 标准答案：必须由 `TASK-DATA-001` 或等效人工标注任务产出，且独立于 parser / AI 输出。
* AI 输出、parser actual 输出、被测系统运行结果不得倒填为人工标准答案。

## 与 TASK-DATA-001 的关系

`TASK-033` 只冻结 MVP 端到端样本验收规格，不产出真实 DOCX `TABLE_CELL` 人工 anchor 标准答案。

`TASK-DATA-001` 应承接以下事项：

* 定义真实 DOCX `TABLE_CELL` 人工 anchor 标注规则。
* 明确样本来源、data owner 或人工确认机制。
* 确保标准答案独立于 parser 输出和 AI 输出。
* 说明人工答案如何在后续任务中转换为 fixture / expected JSON / 测试。

若 `TASK-033` 后续选择含表格样本，该样本只能作为 `TASK-DATA-001` 输入候选；不得在没有人工 anchor 标注的情况下宣称 `TABLE_CELL` coverage 已完成。

## 交付物

* `tasks/active/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`
* `docs/VERIFY.md` 中的 MVP 端到端样本验收规格入口
* `CURRENT_CONTEXT.md` 项目记忆摘要
* `tasks/MVP_TASK_MAP.md` 任务地图记录
* `changelog/2026-07.md` 变更记录

## 验收标准

1. 本任务文件明确 `TASK-033` 是 Codex 主控验收规格冻结任务，不是 CC-DS 实现 `TASK_SPEC`。
2. 本任务文件明确不写业务代码、不改测试、不改 fixture、不改 expected JSON。
3. 本任务文件列出 2-3 份 DOCX 样本选择原则，并引用当前候选样本池。
4. 本任务文件定义后续正式验收必须冻结的输入字段。
5. 本任务文件列出后续验收命令候选，并明确本任务不运行完整验收。
6. 本任务文件定义证据口径，且不允许用 `CURRENT_CONTEXT.md` 自述替代真实证据。
7. 本任务文件说明 expected 来源，不把 AI/parser 输出当人工 anchor 标准答案。
8. 本任务文件说明 `TASK-DATA-001` 承接真实 DOCX `TABLE_CELL` 人工 anchor 标注。
9. 本任务文件明确不归档 `TASK-EVAL-001`，不补足 DoD #12，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
10. `docs/VERIFY.md` 已增加或引用 MVP 端到端样本验收规格口径。
11. `CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-07.md` 已完成项目记忆写回。
12. `git diff --check` 通过。
13. `git status --short` 只包含本任务允许修改的文档文件。

## 测试与验证

本任务为文档规格冻结，不运行业务测试或完整端到端验收。最低验证：

```powershell
git diff --check
git status --short
git diff --name-status
```

正式提交前必须进行独立 agent 只读复核；该复核已完成，结论为 `GO`，无 blocking findings。复核重点：

* 是否只修改允许文件。
* 是否没有修改代码、测试、fixture、expected JSON、workflow、ADR 或 PRD。
* 是否没有把 AI/parser 输出当作人工 anchor 标准答案。
* 是否没有解除 `TASK-EVAL-001` / `TASK-028` 门禁。
* 是否没有授权 Gemma Provider、parser provenance、`SourceAnchor` 或 `TABLE_CELL` coverage 实现。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是，记录 `TASK-033` 已建档及边界。
* 是否需要更新 `docs/VERIFY.md`：是，新增 MVP 端到端样本验收规格入口。
* 是否需要更新 `tasks/MVP_TASK_MAP.md`：是，记录任务地图层面的顺序和门禁。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要新增或更新 ADR：否。本任务不改变架构、审核链路、模型职责、EvidenceSlot、ReviewPointFamily 或 CandidateResolver。

## Next Task Handoff

下一步应等待 PR #24 merge 授权判断；当前独立只读复核已完成且结论为 `GO`。复核不授权实现、不授权 merge、不授权进入 `TASK-028` / `TASK-031` / `TASK-032`。

```text
26/07/07 21:39:14
本窗口是 TASK-033 / PR #24 merge 前最终授权核查窗口，不依赖历史聊天记录作为事实来源。请在只读模式下审计 CQCP 仓库的 TASK-033 建档与 PR #24 是否满足 merge 前边界。

禁止修改文件、stage、commit、push、merge、配置 GitHub 或运行会写文件的命令。

必须读取：
- AGENTS.md
- CURRENT_CONTEXT.md
- tasks/MVP_TASK_MAP.md
- docs/VERIFY.md
- packages/test-fixtures/README.md
- tasks/active/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md
- changelog/2026-07.md

核查问题：
1. 是否只修改允许文件？
2. 是否没有修改代码、测试、fixture、expected JSON、workflow、ADR 或 PRD？
3. TASK-033 是否明确不是 CC-DS 执行环境实现 TASK_SPEC？
4. 样本选择原则、输入字段、验收命令、证据口径和 expected 来源是否可证伪？
5. 是否没有把 AI/parser 输出当作人工 anchor 标准答案？
6. 是否明确 TASK-DATA-001 承接真实 DOCX TABLE_CELL 人工 anchor 标注？
7. 是否没有归档 TASK-EVAL-001、补足 DoD #12 或进入 TASK-028 / TASK-031 / TASK-032？

请输出：
- Decision: GO / NEEDS-FIX / NO-GO
- Blocking findings
- Non-blocking findings
- Evidence checked
```

## 风险

* 若后续把 parser-backed expected JSON 误读为人工 ground truth，会导致验收结论过度声明。
* 若没有 `TASK-DATA-001` 人工 anchor 标注就推进 `TABLE_CELL` coverage，会违反人工标准答案独立性要求。
* 若将本任务误读为 `TASK-EVAL-001` 收口或 `TASK-028` 前置通过，会绕过现有门禁。

## 待确认

* 待确认：后续正式 MVP 端到端验收最终选择哪 2-3 份 DOCX 样本。
* 待确认：每份样本是否已由业务方或 data owner 确认可用于项目验证。
* 待确认：是否创建 `TASK-DATA-001` 以准备真实 DOCX `TABLE_CELL` 人工 anchor 标准答案。

## 完成记录

* 建档日期：2026-07-07。正式提交前独立只读复核已完成，结论为 `GO`，无 blocking findings。
* 变更文件：`tasks/active/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`、`docs/VERIFY.md`、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-07.md`。
* 验证结果：本轮执行 `git diff --check`、`git status --short`、`git diff --name-status`，结果以交付摘要为准。
* 独立只读复核：已完成，Decision 为 `GO`。
* 遗留问题：等待 PR #24 merge 授权判断；本任务仍不授权实现、不归档 `TASK-EVAL-001`，不进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 备注：本任务仅冻结规格，不运行完整验收，不修改代码、测试、fixture、expected JSON、workflow、ADR 或 PRD。
