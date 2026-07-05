# Review Assets

本目录用于承载版本化审核资产源定义，不承载后端最终裁判代码。

当前首批文件由 `TASK_SPEC-030-A` 建档，性质为"代码现状映射"：

- 只记录当前仓库中已经存在的审核点、规则、词库、正则、prompt 版本引用、合同类型画像和 EvidenceSelector / CandidateResolver 行为边界。
- 不声明这些文件已经被生产 runtime loader 读取。
- 不改变后端确定性裁判、`EvidenceSlot`、`CandidateResolver`、`SYS-*` / Finding 分流或模型职责边界。
- 不接入 Gemma / A30，不新增数据库表、OpenAPI 契约、Docker、workflow 或测试 fixture。

首批资产文件：

- `rule-sets/ruleset-v20260705.1.json`
- `review-point-definitions/review-points-v20260705.1.json`
- `pattern-libraries/pattern-library-v20260705.1.json`
- `field-lexicons/field-lexicon-v20260705.1.json`
- `prompts/prompts-v20260705.1.json`
- `contract-type-profiles/contract-type-profile-v20260705.1.json`
- `evidence-selectors/evidence-selector-v20260705.1.json`

## 版本命名规则

所有 Review asset 的 `version` 字段使用 `vYYYYMMDD.N` 格式：

- `v` 前缀固定，不可省略。
- `YYYYMMDD` 为资产首次创建或最近一次升级的日期。
- `.N` 为同日内的修订序号，从 `1` 开始。

当前首批资产均为 `v20260705.1`，表示 2026-07-05 首次建档。

## 新增或升级资产

新增资产或升级已有资产时，必须在资产 JSON 中更新以下字段：

| 字段 | 说明 |
|------|------|
| `assetId` | 仓库内稳定唯一标识；新增时分配新 ID，升级时保持现有 ID 不变 |
| `version` | 按版本命名规则更新为新版本号（如 `v20260712.1`） |
| `changeReason` | 本次新增或变更的原因摘要，不写完整合同或敏感内容 |
| `source` | `type` 保持 `code-current-mapping`；`description` 更新为当前变更说明 |
| `status` | 当前静态建档阶段保持 `DRAFT`；如需引入 `ACTIVE` 或发布状态语义，必须另行定界并评审 runtime loader / 发布治理影响 |

升级资产时还应注意：

- 如变更影响 RuleSetVersion manifest 的模块版本引用，必须同步更新 `moduleVersions` 中对应模块的 `version`。
- 如新增或升级某模块被 RuleSetVersion 选用的资产文件，必须同步更新 `moduleVersions` 中对应既有模块的 `path`、`assetId`、`version`，并通过 `node scripts/validate-review-assets.mjs` 校验引用一致性。

## RuleSetVersion manifest 引用规则

`rule-sets/ruleset-v20260705.1.json`（`assetType: RULE_SET`）是本目录的聚合 manifest，其 `moduleVersions` 必须引用以下六个模块：

- `reviewPointDefinitions`
- `patternLibrary`
- `fieldLexicon`
- `promptTemplates`
- `contractTypeProfiles`
- `evidenceSelectors`

每个模块引用必须包含三个字段，且与被引用文件顶层字段一致：

| 字段 | 说明 |
|------|------|
| `path` | 相对于 `rule-sets/` 目录的被引用文件路径 |
| `assetId` | 必须等于被引用文件顶层的 `assetId` |
| `version` | 必须等于被引用文件顶层的 `version` |

## 静态校验

运行以下命令可对当前目录下所有 JSON 资产执行静态校验：

```bash
node scripts/validate-review-assets.mjs
```

校验内容：

- 必需顶层字段（`assetId`、`assetType`、`version`、`status`、`source`、`changeReason`）
- `assetId` 唯一性
- `source.type` 必须为 `code-current-mapping`，`source.runtimeBinding` 必须为 `NOT_BOUND`
- `runtimePolicy.loaderEnabled` 不得为 `true`
- RuleSet manifest `moduleVersions` 六模块引用存在且 `path`/`assetId`/`version` 一致

该校验只是静态仓库校验，不是 runtime loader。当前 runtime loader 未启用，资产只做静态源定义和代码现状映射，不声明生产运行时已绑定 RuleSetVersion。

如后续要让审核 runtime 从本目录加载资产、引入发布审批或数据库持久化，必须重新判断是否触发 ADR。
