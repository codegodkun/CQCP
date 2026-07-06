# TASK_SPEC-030-B：Review assets schema 与 manifest 引用校验

状态：Done / Claude Code 执行 / PR #20 已合并 / 归档 Review Intake GO / 待父任务归档执行

类型：execution

父任务：`TASK-030`

创建日期：2026-07-05

起草：Codex

执行建议：Claude Code / DeepSeek

## 0. 任务摘要

新增 Review assets 只读校验脚本，用于校验 `packages/review-assets/` 下首批静态资产 JSON 的必需元数据字段、`RuleSetVersion` manifest 模块引用和禁止性 runtime 声明。

本任务只做 schema / 必需字段 / manifest 引用校验，不启用 runtime loader，不改变业务审核逻辑，不修改测试、fixture、expected JSON、OpenAPI、数据库、Docker 或 workflow。

## 1. Required Context

执行前必须读取：

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- `tasks/active/TASK-030-review-assets-versioning-governance.md`
- `tasks/active/TASK_SPEC-030-A-review-assets-static-source-manifest.md`
- 本任务文件
- `docs/DEVELOPMENT.md`
- `docs/VERIFY.md`

## 2. 允许修改范围

允许新增或修改：

- `scripts/validate-review-assets.mjs`
- `packages/review-assets/README.md`
- `tasks/active/TASK_SPEC-030-B-review-assets-schema-manifest-validation.md`
- `tasks/active/TASK-030-review-assets-versioning-governance.md`
- `CURRENT_CONTEXT.md`
- `changelog/2026-07.md`

允许只读参考：

- `packages/review-assets/**/*.json`
- `package.json`
- `scripts/check-pr-authorization-evidence.mjs`

禁止修改：

- `apps/`
- `tests/`
- 任何 fixture / expected JSON
- OpenAPI 文件
- 数据库 migration
- Docker / compose / deploy 配置
- GitHub workflow
- ADR / PRD
- `tasks/MVP_TASK_MAP.md`，除非 Codex 另行判断任务地图层面发生变化

## 3. 架构红线

- 不启用 runtime loader。
- 不让生产审核链路读取 `packages/review-assets/`。
- 不改变 `EvidenceSlot`、`CandidateResolver`、`ReviewPointFamily`、`SYS-*` / Finding 分流或模型职责边界。
- 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
- 不创建 `TASK-033`。
- 不接入 Gemma / A30。

## 4. 行为规格

新增脚本建议路径：

```text
scripts/validate-review-assets.mjs
```

脚本行为：

1. 默认扫描 `packages/review-assets/` 下所有 `.json` 文件。
2. 解析 JSON，解析失败时退出码为 `1`。
3. 每个 JSON 必须包含顶层字段：
   - `assetId`
   - `assetType`
   - `version`
   - `status`
   - `source`
   - `changeReason`
4. `assetId` 不得重复。
5. `source.type` 必须等于 `code-current-mapping`。
6. `source.runtimeBinding` 必须等于 `NOT_BOUND`。
7. 任一 JSON 如存在 `runtimePolicy.loaderEnabled`，其值必须为 `false`。
8. 任一 JSON 不得出现正向生产绑定声明，例如：
   - `runtimeBinding: "BOUND"`
   - `runtimeBinding: "ENABLED"`
   - `loaderEnabled: true`
9. 必须找到一个 `assetType = RULE_SET` 的 manifest。
10. RuleSet manifest 的 `moduleVersions` 必须引用以下模块：
    - `reviewPointDefinitions`
    - `patternLibrary`
    - `fieldLexicon`
    - `promptTemplates`
    - `contractTypeProfiles`
    - `evidenceSelectors`
11. 每个 `moduleVersions.*.path` 必须能解析到存在的 JSON 文件。
12. 每个 `moduleVersions.*.assetId` 和 `version` 必须与被引用文件顶层 `assetId`、`version` 一致。
13. 校验通过时输出简短成功信息，退出码为 `0`。

可选行为：

- 支持传入自定义资产根目录：`node scripts/validate-review-assets.mjs packages/review-assets`。
- 不要求写入 `package.json` script，避免扩大根项目命令表；如实现者认为需要新增 npm script，必须先 STOP 等 Codex 决策。

## 5. 验收标准

Must Pass：

- 新增 `scripts/validate-review-assets.mjs`，且不引入第三方依赖。
- 对当前 `packages/review-assets/` 运行脚本，退出码为 `0`。
- 脚本覆盖必需元数据字段、重复 `assetId`、`source.type`、`source.runtimeBinding`、`loaderEnabled` 和 RuleSet manifest 引用一致性。
- README 或任务记录说明该校验只是静态仓库校验，不是 runtime loader。
- 未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 未进入 `TASK-028` / `TASK-031` / `TASK-032`，未创建 `TASK-033`。

Must Not：

- 不 stage。
- 不 commit。
- 不 push。
- 不修改 `package.json`，除非先 STOP 并经 Codex 放行。
- 不新增依赖。
- 不新增 workflow 或 CI job。
- 不把校验脚本接入生产 runtime。

## 6. 指定验证命令

必须运行：

```powershell
node scripts/validate-review-assets.mjs
git status --short
git diff --name-status
git diff --stat
git diff --check
```

禁止运行：

- 数据库 migration
- Docker compose up / down
- 任何外部网络命令
- `git add`
- `git commit`
- `git push`

## 7. 编码前规格映射计划门禁

Claude Code / DeepSeek 在修改任何文件前，必须先输出编码前规格映射计划并等待 Codex 放行，至少包含：

- 验收断言如何映射到脚本逻辑。
- 真实输入来自哪些 JSON 文件。
- manifest path 如何解析。
- 明确不修改路径。
- 预计验证命令。

Codex 未明确放行前不得实现。

## 8. DeepSeek / Claude Code 执行指令块

```text
你正在执行 CQCP 的 TASK_SPEC-030-B。

你必须先读取：
- AGENTS.md
- CURRENT_CONTEXT.md
- tasks/active/TASK-030-review-assets-versioning-governance.md
- tasks/active/TASK_SPEC-030-A-review-assets-static-source-manifest.md
- tasks/active/TASK_SPEC-030-B-review-assets-schema-manifest-validation.md
- docs/DEVELOPMENT.md
- docs/VERIFY.md

在修改任何文件前，先输出“编码前规格映射计划”，等待 Codex 放行。

允许修改：
- scripts/validate-review-assets.mjs
- packages/review-assets/README.md
- tasks/active/TASK_SPEC-030-B-review-assets-schema-manifest-validation.md
- tasks/active/TASK-030-review-assets-versioning-governance.md
- CURRENT_CONTEXT.md
- changelog/2026-07.md

禁止修改：
- apps/
- tests/
- fixture / expected JSON
- OpenAPI
- 数据库 migration
- Docker / deploy / compose
- GitHub workflow
- ADR / PRD
- package.json，除非先 STOP 并经 Codex 放行

禁止：
- 不启用 runtime loader
- 不改变业务审核逻辑
- 不进入 TASK-028 / TASK-031 / TASK-032
- 不创建 TASK-033
- 不接入 Gemma / A30
- 不 stage
- 不 commit
- 不 push

实现目标：
- 新增无第三方依赖的 Node 脚本 scripts/validate-review-assets.mjs。
- 校验 packages/review-assets/**/*.json 的必需字段、assetId 唯一、source.type=code-current-mapping、source.runtimeBinding=NOT_BOUND、loaderEnabled 不为 true、RuleSet manifest moduleVersions 引用存在且 assetId/version 一致。
- 运行 node scripts/validate-review-assets.mjs 必须通过。

完成后输出：
1. git status --short
2. git diff --name-status
3. git diff --stat
4. git diff --check
5. node scripts/validate-review-assets.mjs 输出
6. 修改文件清单
7. 是否仍属于 TASK-030 范围
```

## 9. Claude Code 执行记录：2026-07-05

### 9.1 实现

新增 `scripts/validate-review-assets.mjs`，无第三方依赖，仅使用 `node:fs` 和 `node:path`。

脚本行为：

- 递归扫描 `packages/review-assets/` 下所有 `.json` 文件（7 个）。
- 校验每个 JSON 的必需顶层字段、`assetId` 唯一性、`source.type`、`source.runtimeBinding`、`runtimePolicy.loaderEnabled`。
- 校验唯一 `assetType=RULE_SET` manifest 的 `moduleVersions` 六模块引用（`reviewPointDefinitions`、`patternLibrary`、`fieldLexicon`、`promptTemplates`、`contractTypeProfiles`、`evidenceSelectors`）。
- 对每个模块引用校验 path 解析到存在的文件，且 `assetId`/`version` 与目标文件一致。
- 收集全部错误后统一输出，再 `process.exit(1)`；通过时退出码 0。
- 支持可选 `[assets-root]` 参数。

### 9.2 验收结果

- `node scripts/validate-review-assets.mjs` 退出码 0，输出 "Review assets validation passed. 7 file(s) checked, 7 JSON file(s) scanned."
- `git diff --check` 无格式问题。
- 未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 未进入 `TASK-028` / `TASK-031` / `TASK-032`，未创建 `TASK-033`，未接入 Gemma / A30。
- 未 stage、未 commit、未 push。

### 9.3 项目记忆更新

- `scripts/validate-review-assets.mjs`：新增。
- `packages/review-assets/README.md`：新增静态校验说明。
- `tasks/active/TASK_SPEC-030-B-review-assets-schema-manifest-validation.md`：本执行记录。
- `tasks/active/TASK-030-review-assets-versioning-governance.md`：父任务记录。
- `CURRENT_CONTEXT.md`：状态更新。
- `changelog/2026-07.md`：追加记录。

## 10. Codex 审查记录

### 2026-07-05 收口审查

结论：可以接纳为 `TASK_SPEC-030-B` 完成，待提交。

依据：

- `scripts/validate-review-assets.mjs` 已按规格校验 7 个 Review assets JSON 的必需字段、`assetId` 唯一性、`source.type`、`source.runtimeBinding`、`runtimePolicy.loaderEnabled` 和 RuleSet manifest 六模块引用一致性。
- `node scripts/validate-review-assets.mjs` 退出码 0，输出 `Review assets validation passed. 7 file(s) checked, 7 JSON file(s) scanned.`
- `git diff --check` 无格式问题。
- 本任务未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、workflow、ADR 或 PRD。
- 本任务未进入 `TASK-028` / `TASK-031` / `TASK-032`，未创建 `TASK-033`，未接入 Gemma / A30，未启用 runtime loader。
- 本任务未 stage、未 commit、未 push。
