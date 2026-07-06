# TASK_SPEC-030-C：Review assets 版本治理与变更流程最小建档

状态：Done / Claude Code 执行 / PR #20 已合并 / 待父任务归档前独立只读审计

类型：execution（文档级治理建档）

父任务：`TASK-030`

创建日期：2026-07-05

执行日期：2026-07-05

执行者：Claude Code (Opus 4.5)

## 0. 任务摘要

在 `packages/review-assets/README.md` 中补充版本命名规则、资产新增/升级流程、RuleSetVersion manifest 引用规则和校验命令说明；新建本 `TASK_SPEC-030-C` 任务记录；更新父任务 `TASK-030` 执行记录、`CURRENT_CONTEXT.md` 和 `changelog/2026-07.md`。

本任务只做文档和治理记录，不修改 JSON 资产、校验脚本、业务代码、测试或任何基础设施。

## 1. Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `tasks/active/TASK-030-review-assets-versioning-governance.md`
- `tasks/active/TASK_SPEC-030-A-review-assets-static-source-manifest.md`
- `tasks/active/TASK_SPEC-030-B-review-assets-schema-manifest-validation.md`
- `packages/review-assets/README.md`
- `scripts/validate-review-assets.mjs`

## 2. 允许修改范围

- `tasks/active/TASK_SPEC-030-C-review-assets-version-governance-docs.md`（新建）
- `packages/review-assets/README.md`
- `tasks/active/TASK-030-review-assets-versioning-governance.md`
- `CURRENT_CONTEXT.md`
- `changelog/2026-07.md`

## 3. 禁止范围

- 不修改 7 个 JSON 资产文件。
- 不修改 `scripts/validate-review-assets.mjs`。
- 不修改 `package.json` / npm scripts / workflow。
- 不修改业务代码、测试、fixture、expected JSON。
- 不修改 OpenAPI、数据库、Docker、workflow、ADR、PRD。
- 不修改 `tasks/MVP_TASK_MAP.md`。
- 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 不创建 `TASK-033`。
- 不启用 runtime loader。
- 不声明生产运行时已绑定 RuleSetVersion。

## 4. 行为规格

### 4.1 README.md 补充内容

1. **版本命名规则**：说明 asset version 使用 `vYYYYMMDD.N` 格式（与现有 JSON 中 `v20260705.1` 一致）。
2. **新增或升级资产规则**：说明新增或升级资产时必须更新对应 `assetId`、`version`、`changeReason`、`source`。
3. **RuleSetVersion manifest 引用规则**：说明 `moduleVersions` 必须引用各模块文件的 `path`、`assetId`、`version`，且与被引用文件顶层字段一致。
4. **校验命令**：说明运行 `node scripts/validate-review-assets.mjs` 进行静态校验。
5. **runtime loader 状态**：明确当前 runtime loader 未启用，资产只做静态源定义和代码现状映射。

### 4.2 父任务 TASK-030 执行记录

在父任务文件末尾追加 `TASK_SPEC-030-C` 执行记录，包含范围、交付物、验收结果和范围外确认。

### 4.3 CURRENT_CONTEXT.md 与 changelog

只写摘要和引用路径，不复制长篇说明。

## 5. 验收标准

### Must Pass

- `packages/review-assets/README.md` 包含版本命名规则（`vYYYYMMDD.N`）、新增/升级资产流程、RuleSetVersion manifest 引用规则、校验命令和 runtime loader 未启用声明。
- 新建 `tasks/active/TASK_SPEC-030-C-review-assets-version-governance-docs.md`。
- 父任务 `TASK-030` 追加 `TASK_SPEC-030-C` 执行记录。
- `CURRENT_CONTEXT.md` 和 `changelog/2026-07.md` 追加摘要和引用路径。
- 未修改 7 个 JSON 资产文件、`scripts/validate-review-assets.mjs`、业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR、PRD。
- `node scripts/validate-review-assets.mjs` 退出码为 0。
- 未 stage、未 commit、未 push。

## 6. 指定验证命令

```
node scripts/validate-review-assets.mjs
git status --short
git diff --name-status
git diff --stat
git diff --check
```

## 7. 编码前规格映射计划

已提交并经 Codex 审查放行（2026-07-05）。Codex 补充约束：

1. 版本命名规则必须与现有 JSON 保持一致：`vYYYYMMDD.N`（当前为 `v20260705.1`），不写无 `v` 前缀格式。
2. 本任务只更新文档和 TASK_SPEC 记录，不修改 JSON 资产文件和校验脚本。
3. README 必须继续明确当前资产只是静态源定义/代码现状映射，runtime loader 未启用。
4. TASK-030、CURRENT_CONTEXT.md、changelog/2026-07.md 只写摘要和引用路径。

## 8. 实现记录

### 8.1 README.md 更新

`packages/review-assets/README.md` 新增以下章节：

- **版本命名规则**：说明 `vYYYYMMDD.N` 格式，以当前 `v20260705.1` 为例。
- **新增或升级资产**：说明必须更新 `assetId`、`version`、`changeReason`、`source`。
- **RuleSetVersion manifest 引用规则**：说明 `moduleVersions` 各模块必须包含 `path`、`assetId`、`version`，且与被引用文件顶层字段一致。
- **静态校验**（已有段落，补充校验命令和用途说明）。
- 保留原有"代码现状映射"和"runtime loader 未启用"声明。

### 8.2 父任务 TASK-030 执行记录

在 `TASK-030` 文件末尾追加 `TASK_SPEC-030-C` 执行记录，记录范围、交付物、验收结果。

### 8.3 项目记忆更新

- `CURRENT_CONTEXT.md`：在 TASK-030 条目后追加 TASK_SPEC-030-C 摘要行和引用路径。
- `changelog/2026-07.md`：顶部追加 TASK_SPEC-030-C 条目。

### 8.4 范围确认

本轮未修改：

- 7 个 JSON 资产文件
- `scripts/validate-review-assets.mjs`
- `package.json`
- 业务代码、测试、fixture、expected JSON
- OpenAPI、数据库、Docker、workflow、ADR、PRD
- `tasks/MVP_TASK_MAP.md`

本轮未进入：

- `TASK-028` / `TASK-031` / `TASK-032`
- `TASK-033` 创建
- Gemma / A30 接入
- Runtime loader 启用

## 9. Codex 审查记录

### 2026-07-05 初轮审查

结论：NEEDS_FIX。

原因：`packages/review-assets/README.md` 有两处文案需要收紧：

1. `status` 字段说明暗示升级资产可直接设为 `ACTIVE`，与当前静态建档阶段不一致。
2. 新增文件/`moduleVersions` 说明暗示可随意向 `moduleVersions` 补入新模块，未明确当前六个模块是固定集。

修正要求：

1. `status` 文案改为明确当前阶段保持 `DRAFT`；如引入 `ACTIVE` 需另行定界。
2. 新增/升级模块文案改为明确是更新既有模块引用，而非补入新模块。

### 2026-07-05 复核修正

已按 Codex 审查要求完成两处修正：

1. `status` 行：改为 "当前静态建档阶段保持 `DRAFT`；如需引入 `ACTIVE` 或发布状态语义，必须另行定界并评审 runtime loader / 发布治理影响"。
2. 新增/升级段：改为 "如新增或升级某模块被 RuleSetVersion 选用的资产文件，必须同步更新 `moduleVersions` 中对应既有模块的 `path`、`assetId`、`version`"。

修正后验证：
- `node scripts/validate-review-assets.mjs` 仍通过（7 文件全通过）。
- `git diff --check` 无格式问题。
- 未修改 JSON 资产、校验脚本、业务代码或任何禁止范围。

### 最终审查

结论：可以接纳为 `TASK_SPEC-030-C` 完成，待提交。

依据：

- `packages/review-assets/README.md` 已包含版本命名规则（`vYYYYMMDD.N`）、新增 / 升级资产流程、RuleSetVersion manifest 引用规则、静态校验命令和 runtime loader 未启用声明。
- 初轮审查指出的两处文案问题已修正：当前静态建档阶段保持 `DRAFT`，新增或升级资产时只更新 `moduleVersions` 中对应既有模块引用，不扩展 manifest 模块集。
- `node scripts/validate-review-assets.mjs` 退出码 0，7 个资产 JSON 仍全部通过。
- `git diff --check` 无格式问题。
- 本任务未修改 7 个 JSON 资产文件、`scripts/validate-review-assets.mjs`、业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 本任务未进入 `TASK-028` / `TASK-031` / `TASK-032`，未创建 `TASK-033`，未接入 Gemma / A30，未启用 runtime loader。
- 本任务未 stage、未 commit、未 push。
