# Review Assets

本目录用于承载版本化审核资产源定义，不承载后端最终裁判代码。

当前首批文件由 `TASK_SPEC-030-A` 建档，性质为"代码现状映射"：

- 只记录当前仓库中已经存在的审核点、规则、词库、正则、prompt 版本引用、合同类型画像和 EvidenceSelector / CandidateResolver 行为边界。
- 不声明这些文件已经被生产 runtime loader 读取。
- 不改变后端确定性裁判、`EvidenceSlot`、`CandidateResolver`、`SYS-*` / Finding 分流或模型职责边界。
- 不接入 Gemma / A30，不新增数据库表、OpenAPI 契约、Docker、workflow 或测试 fixture。

## 资产分类：legacy 与 policy

本目录资产按**内容与 manifest 引用关系**分为两类，不由文件名或 `source.type` 自我声明决定：

| 分类 | source.type | 判定规则 |
|------|-------------|----------|
| **legacy** | `code-current-mapping` | 无 consistencyPolicy 的 `REVIEW_POINT_DEFINITION`；引用 legacy review points 的 `RULE_SET`；其他所有非 `RULE_SET`、非 `REVIEW_POINT_DEFINITION` 的资产。 |
| **policy** | `architecture-approved-policy` | 任一点含 `consistencyPolicy` 的 `REVIEW_POINT_DEFINITION`；通过 `moduleVersions.reviewPointDefinitions` 引用上述 policy 定义文件的 `RULE_SET`。 |

两类资产均要求 `source.runtimeBinding=NOT_BOUND`。policy 资产使用 `architecture-approved-policy` 表示其内容来自已接受的 ADR 等冻结架构决策，不等于已绑定生产 runtime。

## 版本命名规则

所有 Review asset 的 `version` 字段使用 `vYYYYMMDD.N` 格式：

- `v` 前缀固定，不可省略。
- `YYYYMMDD` 为资产首次创建或最近一次升级的日期。
- `.N` 为同日内的修订序号，从 `1` 开始。

## 已有资产

### Legacy（code-current-mapping）

- `rule-sets/ruleset-v20260705.1.json`
- `review-point-definitions/review-points-v20260705.1.json`
- `pattern-libraries/pattern-library-v20260705.1.json`
- `field-lexicons/field-lexicon-v20260705.1.json`
- `prompts/prompts-v20260705.1.json`
- `contract-type-profiles/contract-type-profile-v20260705.1.json`
- `evidence-selectors/evidence-selector-v20260705.1.json`

首批 legacy 资产均为 `v20260705.1`，表示 2026-07-05 首次建档。

### Policy（architecture-approved-policy）

- `rule-sets/ruleset-v20260715.1.json` — 引用 policy review points 的 RuleSetVersion manifest。
- `review-point-definitions/review-points-v20260715.1.json` — 含 `consistencyPolicy` 的九点审核点定义。

policy 资产由 `TASK_SPEC-036-B1` 建档，冻结 ADR-016 版本化 consistency policy：

- 九个审核点均声明 `cardinalityMode=CONSISTENCY_SET`、`minCandidates=1`、`maxCandidates=8`、`occurrenceBudget=64`。
- 逐点冻结 `canonicalizationPolicy`（valueType/unit）与 `scopePolicy`（region types、exclusion、attribution signals）。
- 冻结 `anchorIdentityPolicy`（BLOCK 按 reviewPointCode+blockId，TABLE_CELL 按 reviewPointCode+blockId+previewElementRef）。
- **未绑定 runtime**：`runtimeBinding=NOT_BOUND`、`runtimePolicy.loaderEnabled=false`、`runtimePolicy.databasePersistence=false`、`runtimePolicy.productionEffect=NONE`。
- **B1 不构成 runtime loader 或生产激活**。B2 才进入 runtime binding/activation。

## 新增或升级资产

新增资产或升级已有资产时，必须在资产 JSON 中更新以下字段：

| 字段 | 说明 |
|------|------|
| `assetId` | 仓库内稳定唯一标识；新增时分配新 ID，升级时保持现有 ID 不变 |
| `version` | 按版本命名规则更新为新版本号 |
| `changeReason` | 本次新增或变更的原因摘要，不写完整合同或敏感内容 |
| `source` | legacy 资产 `type` 为 `code-current-mapping`；policy 资产 `type` 为 `architecture-approved-policy` |
| `status` | 当前静态建档阶段保持 `DRAFT`；如需引入 `ACTIVE` 或发布状态语义，必须另行定界并评审 runtime loader / 发布治理影响 |

升级资产时还应注意：

- 如变更影响 RuleSetVersion manifest 的模块版本引用，必须同步更新 `moduleVersions` 中对应模块的 `version`。
- 如新增或升级某模块被 RuleSetVersion 选用的资产文件，必须同步更新 `moduleVersions` 中对应既有模块的 `path`、`assetId`、`version`，并通过 `node scripts/validate-review-assets.mjs` 校验引用一致性。

## RuleSetVersion manifest 引用规则

本目录允许多个 `RULE_SET` manifest 共存，每个均引用以下六个模块：

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

每个 RuleSetVersion manifest 均独立校验六个 module reference；一个 manifest 的引用错误不影响其他 manifest 的独立校验结果。

## 静态校验

运行以下命令可对当前目录下所有 JSON 资产执行静态校验：

```bash
node scripts/validate-review-assets.mjs
```

运行测试套件：

```bash
node --test scripts/validate-review-assets.test.mjs
```

校验内容：

- 必需顶层字段（`assetId`、`assetType`、`version`、`status`、`source`、`changeReason`）
- `assetId` 唯一性
- 基于内容与 manifest 引用关系的 `source.type` 分类（legacy: `code-current-mapping` / policy: `architecture-approved-policy`），禁止自我声明
- `source.runtimeBinding` 必须为 `NOT_BOUND`
- policy RuleSetVersion 的 `runtimePolicy` 必须存在且 `loaderEnabled=false`、`databasePersistence=false`、`productionEffect=NONE`
- legacy 资产的 `runtimePolicy.loaderEnabled` 不得为 `true`
- 每个 RuleSet manifest `moduleVersions` 六模块引用独立校验，`path`/`assetId`/`version` 与目标文件一致
- 含 consistencyPolicy 的审核点的 policy 字段完整性与合法性
- consistencyPolicy 中禁止出现样本 ID、occurrenceNo、人工标记、fixture 路径或文件扩展名引用

该校验只是静态仓库校验，不是 runtime loader。当前 runtime loader 未启用，资产只做静态源定义和代码现状映射，不声明生产运行时已绑定 RuleSetVersion。

如后续要让审核 runtime 从本目录加载资产、引入发布审批或数据库持久化，必须重新判断是否触发 ADR。
