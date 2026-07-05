# TASK_SPEC-030-A：Review assets 静态源文件与 manifest 最小建档

状态：Done / Codex 自执行 / 待提交

类型：execution（低风险静态资产建档）

父任务：`TASK-030`

创建日期：2026-07-05

执行日期：2026-07-05

执行者：Codex

## 0. 任务摘要

在 `packages/review-assets/` 下补首批静态资产源文件，覆盖 `rule-sets`、`review-point-definitions`、`pattern-libraries`、`field-lexicons`、`prompts`、`contract-type-profiles`、`evidence-selectors`。

本任务只做源定义和映射，不启用 runtime loader，不改变审核链路、模型职责、`SYS-*` / Finding 分流、`EvidenceSlot` 或 `CandidateResolver` 行为。

## 1. Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `tasks/active/TASK-030-review-assets-versioning-governance.md`
- `tasks/MVP_TASK_MAP.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPMENT.md`
- `docs/VERIFY.md`
- `docs/context-management.md`

## 2. 允许修改范围

- `packages/review-assets/README.md`
- `packages/review-assets/rule-sets/`
- `packages/review-assets/review-point-definitions/`
- `packages/review-assets/pattern-libraries/`
- `packages/review-assets/field-lexicons/`
- `packages/review-assets/prompts/`
- `packages/review-assets/contract-type-profiles/`
- `packages/review-assets/evidence-selectors/`
- `tasks/active/TASK_SPEC-030-A-review-assets-static-source-manifest.md`
- `tasks/active/TASK-030-review-assets-versioning-governance.md`
- `CURRENT_CONTEXT.md`
- `changelog/2026-07.md`

## 3. 禁止范围

- 不改业务代码。
- 不改测试、fixture、expected JSON。
- 不改 OpenAPI、数据库、Docker、workflow。
- 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 不接入 Gemma / A30。
- 不创建 `TASK-033`。
- 不启用 runtime loader。
- 不声明生产运行已绑定 `packages/review-assets/`。

## 4. 交付物

- `packages/review-assets/rule-sets/ruleset-v20260705.1.json`
- `packages/review-assets/review-point-definitions/review-points-v20260705.1.json`
- `packages/review-assets/pattern-libraries/pattern-library-v20260705.1.json`
- `packages/review-assets/field-lexicons/field-lexicon-v20260705.1.json`
- `packages/review-assets/prompts/prompts-v20260705.1.json`
- `packages/review-assets/contract-type-profiles/contract-type-profile-v20260705.1.json`
- `packages/review-assets/evidence-selectors/evidence-selector-v20260705.1.json`
- `packages/review-assets/README.md`
- 本任务文件、父任务记录、`CURRENT_CONTEXT.md`、`changelog/2026-07.md`

## 5. 验收断言

- 七类资产均至少包含 `assetId`、`assetType`、`version`、`status`、`source`、`changeReason`。
- 所有资产明确标注当前只是 `code-current-mapping`，`runtimeBinding` 为 `NOT_BOUND` 或等价说明。
- `RuleSetVersion` manifest 能引用 review point、pattern library、field lexicon、prompt、contract type profile 和 evidence selector 的模块版本。
- 不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow。
- 不进入 `TASK-028` / `TASK-031` / `TASK-032`，不接入 Gemma / A30，不创建 `TASK-033`。
- 完成 Memory Writeback。

## 6. 实现记录

### 6.1 资产建档

已新增 7 个 JSON 静态资产源文件，并更新 `packages/review-assets/README.md`。

所有资产均声明：

- 当前为“代码现状映射”。
- 生产 runtime 尚未绑定。
- 不改变后端确定性裁判和模型职责边界。

`ruleset-v20260705.1.json` 作为 RuleSetVersion manifest，引用以下模块版本：

- `cqcp.review-points.mvp.code-current.v20260705.1`
- `cqcp.pattern-library.mvp.code-current.v20260705.1`
- `cqcp.field-lexicon.mvp.code-current.v20260705.1`
- `cqcp.prompts.mvp.code-current.v20260705.1`
- `cqcp.contract-type-profile.mvp.code-current.v20260705.1`
- `cqcp.evidence-selector.mvp.code-current.v20260705.1`

### 6.2 范围确认

本任务未修改：

- 业务 Java 代码
- 测试
- fixture
- expected JSON
- OpenAPI
- 数据库 migration
- Docker / compose
- GitHub workflow
- ADR / PRD

本任务未进入：

- `TASK-028`
- `TASK-031`
- `TASK-032`
- `TASK-033`
- Gemma / A30 接入

## 7. 测试与验证

本任务为静态资产和文档建档，不运行后端 / 前端业务测试。

完成态验证命令：

- `Get-ChildItem packages/review-assets -Recurse -Filter *.json | ForEach-Object { Get-Content -Raw -Encoding UTF8 $_.FullName | ConvertFrom-Json | Out-Null }`
- `git diff --check`
- `git status --short`
- `git status -sb`
- `git diff --name-status`
- `git diff --stat`

验证结果以本轮最终交付摘要中的 console 摘要为准。

## 8. CODEX 审查记录

结论：A. 可以接纳为 `TASK_SPEC-030-A` 完成。

原因：

- 验收断言均可由新增 JSON 元数据、manifest 引用和 diff 范围核验。
- 未触发禁止范围。
- 本任务只做静态源定义，不改变架构边界，因此不需要 ADR。

角色分工复核补充：本次 TASK_SPEC-030-A 因范围仅为未绑定 runtime 的静态 Review assets 源文件建档，由 Codex 直接完成并作为一次性例外接受；后续更偏实现、代码修改或 runtime 行为变更的 TASK_SPEC 应按角色分工交由 Claude Code / DeepSeek 执行，Codex 负责冻结规格和审查。

## 9. 后续任务联动

本任务完成不自动解锁任何后续实现。

可选后续方向仍需用户另行确认和定界，例如 schema 校验、版本引用对齐、runtime loader 方案评审、model / budget profile 源定义等。
