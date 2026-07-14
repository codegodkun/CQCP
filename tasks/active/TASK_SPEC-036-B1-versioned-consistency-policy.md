# TASK SPEC — TASK_SPEC-036-B1 版本化 consistency policy 静态源与发布前校验

> **版本**：v0.1  
> **状态**：SPEC_FROZEN / Independent Spec Audit GO / Pre-coding Plan Pending / No Code Authorization  
> **创建日期**：2026-07-15  
> **起草**：Codex  
> **执行环境**：Claude Code（DeepSeek 模型）  
> **TASK_SPEC 类型**：execution  
> **父任务**：TASK-036  
> **关联 ADR**：ADR-016（Accepted）  
> **所在分支**：`codex/task-036-consistency-set-runtime`

## 0. 任务摘要与门禁

只建立不可变、可校验但**未绑定 runtime** 的 consistency policy 静态来源。不得修改 Java 生产代码，不得让新语义对任何任务生效。

- Codex 冻结规格、审查编码前规格映射计划、实现报告、测试和 diff。
- 独立 agent 只读审计本规格；结论未为 `GO` 前不得派发编码计划。
- Claude Code / DeepSeek 必须先提交编码前规格映射计划并停止；Codex 明确 `GO / IMPLEMENTATION AUTHORIZED` 后才能修改文件。
- Claude Code / DeepSeek 不得 commit、push、merge、切换分支或修改本规格。
- B1 通过不解锁正式 E2E；B2/C 仍须分别冻结和审计。

### 0.1 编码前规格映射计划

必须覆盖：

```text
验收断言映射：§8 每条断言的真实 JSON、校验路径、负向变体和可证伪结果
关键字段计算：cardinalityMode、min/maxCandidates、occurrenceBudget、scope/exclusion、canonicalization/unit、anchor identity、module reference
明确不修改：全部 Java、数据库、API、fixture/expected、DOCX/XLSX/matrix、workflow、ADR/ARCHITECTURE
范围外风险：runtime loader、collector、readiness、业务 verdict、snapshot/query 集成
预计测试：合法全目录、legacy 兼容、至少六类非法 consistency policy
```

### 0.2 文件边界

允许修改或新增：

- `packages/review-assets/rule-sets/ruleset-v20260715.1.json`
- `packages/review-assets/review-point-definitions/review-points-v20260715.1.json`
- `packages/review-assets/README.md`
- `scripts/validate-review-assets.mjs`
- `scripts/validate-review-assets.test.mjs`

允许只读：现有 review-assets、ADR-016、ARCHITECTURE、TASK-036、TASK_SPEC-036-A。

禁止修改：除上述五个路径外的全部文件；尤其是 `apps/`、数据库、OpenAPI、fixture、expected、DOCX、XLSX、matrix、outputs、workflow、ADR 与 ARCHITECTURE。

## 1. 冻结静态契约

### 1.1 新版本身份

- RuleSetVersion 文件：`ruleset-v20260715.1.json`
- `assetId=cqcp.ruleset.mvp.consistency-set.v20260715.1`
- `version=v20260715.1`
- ReviewPointDefinition 文件：`review-points-v20260715.1.json`
- `assetId=cqcp.review-points.mvp.consistency-set.v20260715.1`
- `version=v20260715.1`
- 两文件均保持 `status=DRAFT`、`source.type=architecture-approved-policy`、`source.runtimeBinding=NOT_BOUND`。该 source type 表示内容来自 Accepted ADR-016 的待绑定策略，不得伪称已经映射当前生产行为。
- RuleSetVersion 保持 `runtimePolicy.loaderEnabled=false`、`databasePersistence=false`、`productionEffect=NONE`。
- 既有 `v20260705.1` 文件不得修改。

### 1.2 RuleSet module 引用

新 RuleSetVersion 的 `moduleVersions.reviewPointDefinitions` 必须引用新 review-points 文件；其余五个模块继续精确引用现有 `v20260705.1` 文件。引用的 path、assetId、version 必须与目标一致。

### 1.3 九个审核点 policy

新 review-points 文件保留现有九个 `reviewPointCode`，每个点增加 `consistencyPolicy`：

```json
{
  "cardinalityMode": "CONSISTENCY_SET",
  "minCandidates": 1,
  "maxCandidates": 8,
  "occurrenceBudget": 64,
  "scopePolicy": {
    "version": "consistency-scope-v20260715.1",
    "includedRegionTypes": ["BODY", "APPENDIX"],
    "strongExcludedContextTypes": ["TOC", "HEADER_FOOTER", "DELETED", "VOIDED"],
    "requiredAttributionSignals": ["SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR", "ROLE_LABEL", "REGION_CONTEXT", "ANCHOR_IDENTITY"]
  },
  "canonicalizationPolicy": {
    "version": "consistency-canonicalization-v20260715.1",
    "valueType": "TEXT | DECIMAL",
    "unit": "NONE | CNY | PERCENT"
  },
  "anchorIdentityPolicy": {
    "version": "mvp-occurrence-identity-v1",
    "blockIdentity": ["reviewPointCode", "blockId"],
    "tableCellIdentity": ["reviewPointCode", "blockId", "previewElementRef"]
  }
}
```

逐点 canonicalization 固定如下：

- PARTY_A、PARTY_B：`valueType=TEXT`、`unit=NONE`。
- CONTRACT_TOTAL_AMOUNT、TAX_AMOUNT_FORMULA：`valueType=DECIMAL`、`unit=CNY`。
- 五个 ratio 点：`valueType=DECIMAL`、`unit=PERCENT`。

PARTY_A 的 `scopePolicy` 还必须声明：

```json
"strongExcludedSemanticContexts": [
  "CONTRACT_TITLE_NAME_MENTION",
  "AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION"
]
```

该字段表达通用业务上下文，不得出现样本 ID、occurrenceNo、fixture 路径或人工 included 标记。其他八点该数组固定为空数组。

## 2. 校验器行为

`validate-review-assets.mjs` 必须：

1. 允许同目录存在多个 RuleSetVersion，不再要求“恰好一个”；至少一个仍是硬门禁。
2. 对每个 RuleSetVersion 分别校验六个 module reference。
3. 对含 `consistencyPolicy` 的 review point 校验：
   - `cardinalityMode` 只能为 `CONSISTENCY_SET`；
   - `minCandidates` 精确为 1；
   - `maxCandidates` 为整数且至少 2；
   - `occurrenceBudget` 为正整数且不小于 `maxCandidates`；
   - 三个 policy object 均存在且 `version` 为非空字符串；
   - `includedRegionTypes` 精确为 `[BODY, APPENDIX]`，`strongExcludedContextTypes` 精确为 `[TOC, HEADER_FOOTER, DELETED, VOIDED]`，`requiredAttributionSignals` 精确为规格冻结的六项及顺序；
   - PARTY_A 的 semantic exclusion 精确为两枚冻结值及顺序，其他八点精确为空数组；
   - canonical `valueType` 仅 TEXT/DECIMAL，unit 仅 NONE/CNY/PERCENT；
   - 每个 reviewPointCode 的 valueType/unit 必须逐点精确匹配 §1.3，不只校验枚举组合；
   - anchor identity 两个数组精确等于冻结字段及顺序；
   - 九个新版本审核点全部且仅有一个 policy，reviewPointCode 不重复。
4. 新 RuleSetVersion 必须存在完整 `runtimePolicy`，并精确满足 `loaderEnabled=false`、`databasePersistence=false`、`productionEffect=NONE`；任一字段缺失或不同均失败。legacy 继续保持现有约束。
5. source 类型分类不得依赖文件名或 `source.type` 自我声明，固定按资产内容与 manifest 引用关系计算：
   - `REVIEW_POINT_DEFINITION` 中任一点含 `consistencyPolicy`，该定义资产即为 policy 资产；其 `source.type` 必须是 `architecture-approved-policy`。
   - RuleSet 的 `moduleVersions.reviewPointDefinitions` 引用了上述 policy 资产，该 RuleSet 即为 policy 资产；其 `source.type` 必须是 `architecture-approved-policy`。
   - 其他现有资产均为 legacy，`source.type` 必须是 `code-current-mapping`。
   - 禁止根据路径、文件名、version 日期或 source.type 本身反推分类。
   - 两类资产都拒绝任何 `runtimeBinding != NOT_BOUND`。
6. consistency policy 的序列化文本不得出现 `CQCP-MVP-DOCX`、`occurrenceNo`、`includedInConsistencyEvaluation`、`human-anchors`、`fixtures` 或具体 `.docx/.xlsx` 路径标记。
7. 所有错误一次性收集后退出 1；成功退出 0。

不得在校验器中根据文件名跳过通用字段校验；新版本九点完整性可以由 manifest 指向的新定义文件触发。

## 3. 测试规格

新增 Node 内置 `node:test` 测试，不引入依赖。测试通过临时目录复制/变异资产，不修改仓库资产。

必须覆盖：

- 全部真实资产合法，且同时存在 legacy 与新 RuleSetVersion。
- legacy `v20260705.1` 仍合法且没有 consistency policy。
- policy/legacy 两类 `source.type` 互换、缺失或非法值分别失败；分类必须由 policy 内容和 RuleSet 引用关系得出。
- 只破坏 legacy RuleSet 的任一 module path/assetId/version、新 RuleSet 保持合法时失败并定位 legacy manifest。
- 只破坏新 RuleSet 的任一 module path/assetId/version、legacy 保持合法时失败并定位新 manifest。
- 缺 `occurrenceBudget` 失败。
- `maxCandidates=1` 失败。
- `occurrenceBudget < maxCandidates` 失败。
- 缺任一 policy version 失败。
- PARTY_A/B 或其他点使用了非本点冻结 canonical pair 失败。
- scope/exclusion/attribution 数组缺项、增项或乱序失败。
- PARTY_A 缺少/增加 semantic exclusion，或其他点出现非空 semantic exclusion 失败。
- anchor identity 字段或顺序不精确失败。
- 新定义缺任一九点或重复 reviewPointCode 失败。
- `runtimePolicy` 缺失，或任一 runtime binding/loader/databasePersistence/productionEffect 激活失败。
- policy 中出现样本 ID、occurrenceNo、人工 included 标记或 fixture/文件路径失败。

## 4. 架构与范围红线

- B1 仅实现 ADR-016 的版本化静态 policy 来源，不实现 runtime loader。
- 不新增 Java 类型、枚举、状态、API 字段、数据库表或依赖。
- 不读取人工 fixture 作为生产配置输入，不用 actual 输出生成 policy。
- 不改变 `CandidateResolver`、EvidenceSlot、SourceAnchor、Review Engine 或 snapshot/query 行为。
- 不运行 TASK-034 formal property/input。
- 发现需要改变上述边界时必须 STOP。

## 5. 技术与 Git 约束

- Node.js 内置模块；无第三方依赖。
- 当前分支必须为 `codex/task-036-consistency-set-runtime`。
- 编码前 `git status --short` 必须为空。
- 不得 commit、push、merge、reset、clean 或切分支。

## 6. Must Pass

- [ ] 新旧两个 RuleSetVersion及全部九个 JSON 资产通过真实校验。
- [ ] 新 RuleSetVersion 只引用新 review-points，其他模块保持 v20260705.1。
- [ ] 九点均具备冻结 policy、固定预算和正确 unit。
- [ ] PARTY_A 仅使用通用 semantic context exclusion；无样本/人工标记硬编码。
- [ ] 上述全部非法 policy 类别均以非零退出且错误可定位。
- [ ] legacy 资产未修改且仍通过。
- [ ] `runtimeBinding=NOT_BOUND`、`loaderEnabled=false`、`databasePersistence=false`、`productionEffect=NONE` 均由正负测试证明。
- [ ] 实际 diff 仅五个允许路径。

## 7. Must Not

- [ ] 不修改 Java、测试 fixture、expected、DOCX、XLSX、matrix、outputs、workflow、ADR、ARCHITECTURE。
- [ ] 不引入依赖，不激活 runtime，不新增发布审批或数据库机制。
- [ ] 不运行正式 MVP E2E，不声明 57/57 coverage。

## 8. 验证命令

获得 Codex 实现放行后必须运行：

```powershell
node --test scripts/validate-review-assets.test.mjs
node scripts/validate-review-assets.mjs
git diff --check
git status --short
git diff --stat
```

禁止运行数据库、Docker、网络、正式 E2E 或任何写入 fixture/output 的命令。

## 9. 同根因与 expected 来源

- 与 TASK_SPEC-036-A 同属 occurrence provenance 根因；A 提供未激活 carrier，B1 只提供未激活版本化策略。
- B1 不测试人工 expected 正确性。测试 expected 来自 ADR-016 和本规格的 schema/预算约束，不依赖被测系统输出。
- `maxCandidates=8`、`occurrenceBudget=64` 是本 RuleSetVersion 的显式保护上限，不等同于样本 occurrence 数，也不得据此倒推人工标准答案。

## 10. 后续联动

- B1 经 Codex Review Intake 和独立实现审计接纳后，才允许冻结 B2 runtime loader/binding/activation 规格。
- B1 不解锁 C，不解锁 TASK-034 正式重跑，不进入 TASK-028/029/031/032。

## 11. 规格审计与 Codex Review Intake

- 首轮独立只读规格审计：`NO_GO`。阻断项为未激活 runtimePolicy 校验不完整、scope/canonicalization 未逐点精确、source type 分类可自我声明，以及多 RuleSet 未证明逐个校验。
- Codex Review Intake：`ACCEPT_FINDINGS`。已补齐 `false/false/NONE`、逐点精确 policy、样本硬编码禁入、基于 policy 内容与 manifest 引用的 source 分类，以及分别破坏 legacy/new manifest 的负测。
- 第二轮独立增量复审：`GO`，无剩余 blocking finding，无需补充 ADR。
- Non-blocking：PARTY_A 专属合同名称语义排除与 PARTY_B 空数组存在跨合同泛化风险；B2/C 必须依靠可靠 role attribution，无法区分时进入 `SYS-* / NOT_CONCLUDED`，不得仅按标题/前言位置直接排除。
- Codex Decision：`SPEC_FROZEN / INDEPENDENT_SPEC_AUDIT_GO / PRE-CODING_PLAN_PENDING / NO CODE AUTHORIZATION`。
