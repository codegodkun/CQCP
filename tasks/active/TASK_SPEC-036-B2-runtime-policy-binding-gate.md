# TASK SPEC — TASK_SPEC-036-B2 consistency policy runtime binding 与激活门禁

> **版本**：v0.2
> **状态**：SPEC_FROZEN / Independent Spec Audit GO / Code Blocked by B1 Commit / No Code Authorization
> **创建日期**：2026-07-16
> **起草**：Codex
> **执行环境**：Claude Code（DeepSeek 模型）
> **TASK_SPEC 类型**：execution
> **父任务**：TASK-036
> **关联 ADR**：ADR-016（Accepted）
> **前置批次**：TASK_SPEC-036-A、TASK_SPEC-036-B1
> **所在分支**：`codex/task-036-consistency-set-runtime`

## 0. 任务摘要与冻结判断

本批只建立 `v20260715.1` consistency policy 的**单一 runtime 读取、类型化绑定与 fail-closed 激活门禁**。B2 不实现真实 occurrence collector、不改变 CandidateResolver / EvidenceSlot / PointEvidence / SourceAnchor / 确定性裁判，也不允许任何 execution 在 C 未就绪时把 `v20260715.1` 记录为已生效规则集。

冻结判断：B2 可以先冻结和审计，但**不得开始编码**，直到 B1 的五路径已接纳实现形成精确 commit、工作区可重建且 Codex 另行审查编码前规格映射计划并给出 `GO / IMPLEMENTATION AUTHORIZED`。

- Codex 冻结规格、审查编码前规格映射计划、实现报告、测试和 `git diff`。
- 独立 agent 只读审计本规格；结论未为 `GO` 前不得派发编码计划。
- Claude Code / DeepSeek 必须先提交编码前规格映射计划并停止；Codex 明确放行后才能修改文件。
- Claude Code / DeepSeek 不得 commit、push、merge、切换分支或修改本规格。
- B2 通过不解锁 C 或正式 MVP E2E；C 仍须单独冻结、审计、实现和接纳。

## 0.1 与 B1 的同根因分批关系

一致点：

- 只接受 B1 冻结的 `v20260715.1`、九个审核点、`maxCandidates=8`、`occurrenceBudget=64`、scope/exclusion、canonicalization/unit 与 anchor identity；B2 不另设默认值。
- `packages/review-assets` 继续是策略内容真源；B2 不在 Java 中复制九点矩阵、预算或 scope 列表。
- legacy `v20260705.1` 行为保持不变；历史 execution / snapshot 不回填、不重算。
- 禁止样本 ID、`occurrenceNo`、人工 included 标记、fixture / expected 值进入 runtime policy。

差异点：

- B1 只负责静态资产和仓库校验；B2 才允许生产模块读取同一资产并形成不可变类型化 snapshot。
- B1 明确 `NOT_BOUND / loaderEnabled=false / productionEffect=NONE`；B2 不直接把这些字段改成“已生产生效”，而是新增独立的 runtime activation gate，并在 C readiness 未提供前拒绝新版本绑定。
- B1 的 validator 是发布前静态门禁；B2 的 loader 必须在 runtime 侧重复执行安全关键不变量，不能把 Node validator 的历史成功当作可信输入。

差异理由：静态可发布资产与运行时可信绑定属于不同故障域；同时 C 尚未实现，B2 必须防止版本字符串先于真实执行语义生效。

## 0.2 编码前规格映射计划

执行方必须逐条说明并停止等待 Codex Review：

```text
验收断言映射：§8 每条断言对应的生产输入、失败方式、测试和可证伪结果
真实输入路径：packages/review-assets 如何进入 api-server runtime classpath，如何按精确 version 选择 manifest，如何解析其 reviewPointDefinitions 引用
类型化映射：RuleSet identity、module reference、九点 consistencyPolicy、双预算、scope/exclusion、canonicalization/unit、anchor identity 的 Java 字段
激活门禁：legacy 请求、新版本请求、未知版本、资产损坏、C readiness 缺失/false 的精确结果
明确不修改：collector/resolver/evidence/engine/result/API/DB/fixture/expected/DOCX/XLSX/matrix/workflow/ADR/ARCHITECTURE
范围外风险：真实 occurrence 收集、可靠异值裁判、多 anchor 输出、execution 创建入口尚未接线
预计测试：合法加载、不可变快照、未知版本、引用错配、字段缺失、预算/九点变异、C readiness fail-closed、legacy 无回归
```

## 0.3 前置证据门禁

编码前必须同时满足：

1. B1 实现已有精确 commit id，且该 commit 只包含 B1 接纳的五路径与治理文档。
2. `git status --short` 中不存在无法归属的代码或资产变更；如仍有用户改动，执行方必须只读列明并停止。
3. B1 两组冻结命令在该 commit 基线上通过：`node --test scripts/validate-review-assets.test.mjs` 与 `node scripts/validate-review-assets.mjs`。
4. Codex 已审查编码前规格映射计划并单独给出实现授权。

本规格冻结与独立规格审计不补足以上编码门禁。

## 1. 文件边界

允许修改或新增：

- `apps/api-server/build.gradle.kts`
- `apps/api-server/Dockerfile`
- `.dockerignore`
- `deploy/compose/compose.yml`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuntimeRuleSetLoader.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuntimeRuleSetSnapshot.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ConsistencyPolicySnapshot.java`
- `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuleSetActivationGate.java`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/RuntimeRuleSetLoaderTest.java`
- `apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/RuleSetActivationGateTest.java`

实现接纳后的治理写回由 Codex 另行处理，不属于执行方允许路径。

允许只读：

- B1 五个允许路径及其精确 commit。
- `TASK-036`、`TASK_SPEC-036-A`、`TASK_SPEC-036-B1`、ADR-016、`docs/ARCHITECTURE.md`。
- 当前 execution / snapshot / preparer / resolver / engine 代码与相关测试。

禁止修改：

- `packages/review-assets/**`（B2 消费 B1，不改写 B1）。
- `ParserBackedReviewInputPreparer.java`、`MinimalCandidateResolver.java`、`MinimalReviewEngine.java`、`ResultComposer.java`、`TaskExecutionStateMachine.java` 及全部既有生产 Java。
- OpenAPI、数据库、migration、fixture、expected JSON、DOCX、XLSX、matrix、outputs、除上述两个 Docker 构建路径外的部署文件、workflow、ADR、ARCHITECTURE、PRD。
- 任何新 endpoint、Spring 配置入口、环境变量、数据库表或管理台控件。

如实现证明必须修改上述禁止路径才能形成真实 runtime binding，必须停止并由 Codex 重拆规格，不得越界。

## 2. Runtime 资产来源与单一真源

### 2.1 Classpath 打包

`apps/api-server/build.gradle.kts` 只允许把仓库 `packages/review-assets` 作为只读构建输入打包到固定 classpath 前缀：

```text
cqcp/review-assets/
```

不得复制或生成第二份 JSON 到 `src/main/resources`；不得在构建脚本改写 JSON；不得联网下载资产。单元测试必须能从与生产相同的 classpath 前缀加载。

容器构建必须同步保持可重建：

- `deploy/compose/compose.yml` 仅把 `api-server.build.context` 从 `../../apps/api-server` 提升为仓库根 `../..`，并显式指定 `dockerfile: apps/api-server/Dockerfile`；不得改变服务、端口、环境变量、依赖或 volume。
- `apps/api-server/Dockerfile` 的 build stage 固定在 `/workspace/apps/api-server` 执行，分别 `COPY apps/api-server` 与 `COPY packages/review-assets` 到能满足 Gradle 相对路径的 `/workspace` 对应位置；`gradle bootJar` 后必须在同一 build stage 用 `jar tf` + 精确整行匹配分别断言两条 `BOOT-INF/classes/cqcp/review-assets/...v20260715.1.json` entry，任一缺失即令镜像构建失败；runtime stage、基础镜像与 entrypoint 不变。
- 新增仓库根 `.dockerignore`，使用默认排除、精确放行 `apps/api-server/**` 与 `packages/review-assets/**` 的 allowlist，并在放行后再次排除 `apps/api-server/.gradle/**` 与 `apps/api-server/build/**`；禁止把本地 Gradle 缓存、旧 build、`.git`、`node_modules`、outputs、合同样本或其他 workspace 内容送入 api-server build context。
- 镜像内 boot JAR 必须包含 `cqcp/review-assets/rule-sets/ruleset-v20260715.1.json` 与其 review-points 引用；不得依赖容器运行时 bind mount 或宿主机工作目录。

### 2.2 精确选择与引用解析

- loader 输入必须是精确版本字符串；B2 只允许 `v20260715.1` 进入 policy loader。
- 固定入口为 `cqcp/review-assets/rule-sets/ruleset-v20260715.1.json`；不得扫描后选择“最新版本”，不得按目录顺序或日期猜测版本。
- 必须解析 manifest 的 `moduleVersions.reviewPointDefinitions.path`，规范化后确认仍位于 `cqcp/review-assets/` 内，并精确指向 `review-point-definitions/review-points-v20260715.1.json`。
- 禁止绝对路径、`..` 逃逸、URL、磁盘 fallback 或工作目录 fallback。
- manifest 与被引用资产的 `assetId / assetType / version / source.type` 必须精确匹配 B1 冻结值。

## 3. 类型化不可变 snapshot

`RuntimeRuleSetLoader.load("v20260715.1")` 成功时返回深不可变 `RuntimeRuleSetSnapshot`，至少包含：

- `assetId`、`version`、`reviewPointDefinitionsAssetId`、`reviewPointDefinitionsVersion`。
- 按现有 `reviewengine` 包内 `ReviewPointCode` 键控且不可修改的九点 policy map。B2 新类与测试必须留在同一 package，直接复用唯一现有 enum；不得复制第二个 enum，不得退回自由字符串 key，也不得为此移动或公开既有 enum。
- 每点的 `cardinalityMode`、`minCandidates`、`maxCandidates`、`occurrenceBudget`。
- scope policy version、included region、strong excluded context、required attribution signals、semantic exclusions。
- canonicalization policy version、value type、unit。
- anchor identity policy version、block identity、table-cell identity。

构造后不得暴露可变 `Map` / `List` / Jackson tree；调用方修改返回集合必须失败或不影响内部 snapshot。

Java enum / record 名称可以按冻结文件名实现，但不得增加“缺失时使用默认值”的构造路径。未知字段可拒绝；安全关键字段缺失、重复、空值或类型错误必须拒绝加载。

## 4. Runtime 重校验

loader 必须独立验证，不依赖 Node validator 已运行：

1. 顶层 RuleSet 与 ReviewPointDefinition identity、version、status、source 和 module reference 精确匹配。
2. 九个 `ReviewPointCode` 全部且仅出现一次，不多不少。
3. 每点 `cardinalityMode=CONSISTENCY_SET`、`minCandidates=1`、`maxCandidates=8`、`occurrenceBudget=64`，不允许 runtime 放宽。
4. scope/exclusion、required attribution signals 及顺序精确等于 B1；PARTY_A 两项 semantic exclusion 精确存在，其他八点精确为空。
5. 两个 TEXT/NONE、两个 DECIMAL/CNY、五个 DECIMAL/PERCENT 的逐点矩阵精确匹配。
6. block/table-cell identity 字段及顺序精确匹配 B1。
7. 资产中不得出现样本 ID、`occurrenceNo`、人工 included 标记、fixture / expected / DOCX / XLSX 引用。
8. classpath 中打包的 B1 manifest 内容仍保持 `DRAFT / NOT_BOUND / loaderEnabled=false / databasePersistence=false / productionEffect=NONE`。B2 的 runtime 可加载性不得通过篡改这些发布状态字段伪装。

其中两个顶层资产均须精确校验：

```text
status=DRAFT
source.type=architecture-approved-policy
source.paths=[
  decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md,
  docs/ARCHITECTURE.md,
  tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md
]
source.runtimeBinding=NOT_BOUND
```

两个资产的 `source.bindingNote` 还必须分别精确为：

```text
RuleSet: B1 静态 policy 源；未绑定 runtime loader，不对任何任务生效。
ReviewPointDefinition: B1 静态 consistency policy 源；未绑定 runtime，不对任何任务生效。审核点定义来自 ADR-016 冻结的九点多出处一致性语义。
```

不得只校验 RuleSet manifest、只校验 `source.type`，或把任意非空 bindingNote 当作冻结值。

任一失败必须抛出固定类型 `RuleSetLoadException`（可以作为 `RuntimeRuleSetLoader.java` 内的 package-private 类型），并携带以下冻结 `RuleSetLoadReason`；消息只包含版本、资产相对路径与 reason code，不输出完整 JSON：

| Reason code | 精确适用场景 |
|---|---|
| `REFERENCE_PATH_INVALID` | absolute / URL / `..` 逃逸或规范化后离开固定 classpath 前缀 |
| `REFERENCE_TARGET_MISMATCH` | review-points 相对路径不是 B1 冻结目标 |
| `RESOURCE_NOT_FOUND` | 固定 manifest 不存在，或 reference 已通过 path 与 target 校验后冻结目标资源不存在 |
| `RESOURCE_JSON_INVALID` | JSON 不可解析、根类型错误或安全关键字段类型错误 |
| `ASSET_IDENTITY_INVALID` | assetId / assetType / version / module reference identity 任一不匹配 |
| `ASSET_RELEASE_STATE_INVALID` | status、source 四项或 RuleSet runtimePolicy 冻结值任一不匹配 |
| `REVIEW_POINT_SET_INVALID` | 九点缺失、多出、重复、空对象或 code 非法 |
| `CONSISTENCY_BUDGET_INVALID` | cardinality / min / max / occurrenceBudget 任一不匹配 |
| `SCOPE_POLICY_INVALID` | scope version、region、context、attribution、semantic exclusion 任一不匹配 |
| `CANONICALIZATION_POLICY_INVALID` | canonicalization version 或逐点 valueType/unit 矩阵不匹配 |
| `ANCHOR_IDENTITY_POLICY_INVALID` | anchor version 或 block/table-cell identity 不匹配 |
| `FORBIDDEN_POLICY_CONTENT` | 命中样本、人工标记、fixture/expected/DOCX/XLSX 禁止内容 |

同一输入有多个错误时必须按上表自上而下返回第一个适用 reason；测试不得按实现自行选择原因码。loader 必须先做 path 语法/逃逸校验，再做冻结 target 匹配，二者都通过后才读取被引用资源；因此越界或错目标即使客观上不存在，也不得提前返回 `RESOURCE_NOT_FOUND`。固定 manifest 缺失因尚无 reference 可校验，直接返回 `RESOURCE_NOT_FOUND`。

## 5. 激活门禁

`RuleSetActivationGate` 是纯 Java、显式调用、无全局 mutable state 的门禁：

```text
request(version, consistencyRuntimeReady)
```

精确语义：

- `v20260705.1`：返回 `LEGACY_ALLOWED`，不调用 policy loader，不改变现有 execution 行为。
- `v20260715.1` + `consistencyRuntimeReady=false`：返回/抛出 `POLICY_NOT_READY`，不得返回 policy snapshot，不得允许调用方把该版本记录为 execution 已绑定版本。
- `v20260715.1` + `consistencyRuntimeReady=true`：加载并返回 B1 的不可变 snapshot，结果仅表示“policy 资产已通过 runtime 绑定门禁”；B2 自身没有任何生产调用点传入 `true`。
- 未知、空白或 null 版本：`UNKNOWN_RULE_SET_VERSION`。
- 资产加载或校验失败：`POLICY_ASSET_INVALID`，不得 fallback 到 legacy。

`consistencyRuntimeReady` 是 C 后续接线所需的显式输入，不得在 B2 中通过环境变量、文件存在、Spring profile、测试 sample 或 manifest `loaderEnabled` 自动推断。B2 不新增生产调用点，因此当前新 execution 仍不得绑定 `v20260715.1`。

## 6. 与现有 execution / snapshot 的边界

- B2 不修改 `VersionReferences`、`TaskExecutionRecord`、任务创建、状态机或 Result Composer。
- B2 不把 loader 返回值写入 execution / snapshot；C 必须在真实 collector/readiness/verdict 全部接线后再冻结“何时传入 ready=true、何时绑定版本”的原子切换。
- legacy execution 的 `ruleSetVersion` 字符串和行为保持不变。
- 历史 snapshot 不重新解释，不用新 policy 补写 anchors。
- 因 B2 没有生产调用点，其验收只能声明 loader/gate foundation 正确，不得声明生产激活完成。

## 7. 明确不做

- 不收集任何新 occurrence，不调用 A carrier，不改变候选去重或 semantic value grouping。
- 不实现 `CONSISTENCY_SET_READY / BUDGET_TRUNCATED / PARTIAL / NOT_CONCLUDED` 状态计算。
- 不实现可靠异值 ERROR、同值 PASS、多 anchor 输出或 SYS/Finding 分流变化。
- 不把 B1 `runtimePolicy` 字段改成 true / bound / production effect。
- 不新增 runtime 默认版本、自动升级、“latest”别名或 legacy fallback。
- 不运行正式 TASK-034 E2E，不修改 57/57 expected 或正式证据。

## 8. 可证伪验收断言

1. Gradle test runtime 从 `cqcp/review-assets/` classpath 前缀读取 B1 原文件；删除打包配置或改为工作目录 fallback 时测试失败。
2. 精确加载 `v20260715.1` 得到九点深不可变 snapshot；任一外部集合修改不改变第二次读取结果。
3. manifest 引用越界返回 `REFERENCE_PATH_INVALID`，合法前缀内错 path 返回 `REFERENCE_TARGET_MISMATCH`，错 assetId/version/type 返回 `ASSET_IDENTITY_INVALID`；均不得 fallback。
4. 九点少一个、多一个、重复一个或追加空对象返回 `REVIEW_POINT_SET_INVALID`；任一点双预算变异返回 `CONSISTENCY_BUDGET_INVALID`。
5. scope/exclusion/attribution/semantic exclusion 变异返回 `SCOPE_POLICY_INVALID`；逐点 canonical/unit 变异返回 `CANONICALIZATION_POLICY_INVALID`；anchor identity 变异返回 `ANCHOR_IDENTITY_POLICY_INVALID`。
6. B1 status/source/runtimePolicy 任一发布状态改为非冻结值时返回 `ASSET_RELEASE_STATE_INVALID`；B2 不修改 B1 五路径。禁止内容返回 `FORBIDDEN_POLICY_CONTENT`。
7. `request(v20260705.1, false)` 允许 legacy 且不触发 policy load；测试使用损坏 policy 资产仍能证明 legacy 分支不读取它。
8. `request(v20260715.1, false)` 固定拒绝，且没有可取得的 snapshot；`true` 仅在资产完整时返回 snapshot。
9. 未知/null/空白版本固定拒绝；新版本资产无效时固定 fail-closed，不回退 legacy。
10. 全仓搜索与 diff 证明没有生产调用点传入 `consistencyRuntimeReady=true`，没有修改 execution/snapshot/preparer/resolver/engine/result/API/DB。
11. 既有后端测试通过，证明 legacy 路径无回归；B1 Node tests 与 validator 继续通过，证明消费方没有改写静态源。
12. 所有声明均仅为 loader/gate foundation；若实现报告声称生产多出处一致性已激活、57/57 已覆盖或正式 E2E 可重跑，验收失败。
13. `docker compose ... config` 证明 api-server 使用仓库根 context 与显式 Dockerfile；Docker build stage 自身对镜像待复制 boot JAR 执行两条精确 entry 断言，正常构建通过，删除 review-assets COPY 或任一资源打包配置后构建失败；本地 boot JAR 列表也能精确看到两份 `v20260715.1` 资产。
14. 新 Java 类直接使用唯一现有 `ReviewPointCode`；全仓不存在第二个同名或等价 runtime review-point enum/string key 表。

## 9. 测试与原始证据

执行方至少提交原始命令与退出码：

```powershell
node --test scripts/validate-review-assets.test.mjs
node scripts/validate-review-assets.mjs
Set-Location apps/api-server
gradle test --tests "com.cqcp.apiserver.reviewengine.RuntimeRuleSetLoaderTest" --tests "com.cqcp.apiserver.reviewengine.RuleSetActivationGateTest"
gradle test
gradle bootJar
$bootJar = (Get-ChildItem build/libs/*.jar | Select-Object -First 1).FullName
jar tf $bootJar | Select-String "BOOT-INF/classes/cqcp/review-assets/(rule-sets/ruleset-v20260715.1.json|review-point-definitions/review-points-v20260715.1.json)"
Set-Location ../..
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml config
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml build api-server
git diff --check
git status --short
```

测试中的变异资产必须由测试临时资源或受控 in-memory/classloader fixture 构造，不得修改 `packages/review-assets` 真源后再恢复。expected 值直接来自本规格与 B1 冻结常量，不得从 loader 实际输出反向生成；因此可声明独立契约验证，不是循环一致性验证。

## 10. 实现报告要求

实现报告必须包含：

- 编码前规格映射计划与 Codex 放行引用。
- 实际修改文件清单；逐项确认无禁止路径修改。
- classpath 真源、精确 version 选择、引用规范化、深不可变与 fail-closed 的实现说明。
- §8 十四条断言逐条证据。
- B1 Node 原始结果、定向 Gradle、全量 Gradle、boot JAR 资产列表、Compose config、api-server 镜像构建、`git diff --check`、`git status --short`。
- 明确声明：未 commit、未 push、未运行正式 E2E、未生产激活、未解锁 C。

## 11. 独立审计要求

规格审计至少核查：

- B2 是否错误地在 C 前允许新版本写入 execution。
- classpath 资产是否仍以 B1 文件为唯一真源，是否存在路径逃逸或磁盘 fallback。
- runtime 是否真正重校验安全关键字段，是否隐藏默认值或 legacy fallback。
- B1 → B2 一致点、差异点、差异理由是否明确且不改变 ADR-016。
- 文件边界是否足够执行且未夹带 collector/verdict/API/DB。
- 验收断言是否可证伪，测试 expected 是否独立于被测输出。

实现审计必须在 Codex 审查实现报告与 diff 后另行执行；规格审计 `GO` 不替代实现审计。

## 12. 独立规格审计与 Review Intake Decision

### 12.1 首轮独立只读审计

结论：`NO_GO`。Codex 接受全部三项 blocking finding：

1. 原 v0.1 只允许 Gradle 打包仓库级资产，但 Compose 的 api-server build context 仅为 `apps/api-server`，容器构建不可见 `packages/review-assets`，生产发布物不可重建。
2. 原测试命令使用仓库不存在的 `gradlew`；真实 README / CI 使用系统 `gradle`。
3. 原新类位于 `reviewassets` package，却要求使用定义在 `reviewengine` package 内的 package-private `ReviewPointCode`，且禁止修改既有 Java，文件边界不可执行。

4. 原规格要求“稳定原因码”却未冻结 reason-code 集合、变异映射和多错误优先级，无法证明 expected 独立于实现命名。

非阻断精确性 finding：原 §4.8 “manifest 在磁盘上”与禁止磁盘 fallback 口径冲突。

### 12.2 Codex 首轮 Review Intake

Decision：`ACCEPT_FINDINGS / REVISE_SPEC / NO_CODE_AUTHORIZATION`。

- 将容器构建的 context、Dockerfile 和根 `.dockerignore` 纳入最小允许路径，冻结只传入 api-server 与 review-assets 的构建上下文。
- 新类移入 `reviewengine` package，直接复用唯一现有 package-private enum，不复制类型、不扩大既有生产文件改动。
- 命令改为系统 `gradle`，并增加 Compose config、镜像构建及 JAR 资产存在性验收。
- “磁盘 manifest”修正为“classpath 中打包的 manifest 内容”。
- 冻结十二个 loader reason codes、逐类变异映射与多错误优先级；测试 expected 直接来自规格。
- 展开两个资产的 `status/source.paths/source.runtimeBinding/source.bindingNote` runtime 重校验要求。

### 12.3 增量独立复审与最终 Review Intake

增量独立只读审计结论：`GO`，无剩余 blocking / non-blocking finding。复审确认：

- B1 未 commit 仍由 §0.3 与 §13 阻断编码，但不阻断规格冻结。
- Docker/classpath 生产发布物可重建边界、唯一 `ReviewPointCode`、系统 Gradle 命令、路径校验顺序与镜像内 JAR 资产证据闭环均已冻结。
- reason-code 集合、适用场景、优先级和 expected 映射独立于实现。
- C 前继续 fail-closed：无生产调用点、无 execution/snapshot 写入、无 legacy fallback。
- 未提前实现 collector、readiness、裁判、多 anchor 或 SYS/Finding 行为，符合 ADR-016 与 ARCHITECTURE。

Codex Review Intake Decision：`SPEC_FROZEN / INDEPENDENT_SPEC_AUDIT_GO / CODE_BLOCKED_BY_B1_COMMIT / PRE_CODING_PLAN_NOT_AUTHORIZED / NO_IMPLEMENTATION_AUTHORIZATION`。

## 13. 风险与停止条件

- 若纳入本规格的最小 Docker context / Dockerfile 调整后，classpath 发布物仍无法重建或会包含 allowlist 外内容，停止并重拆构建资产任务。
- 若 B1 精确 commit 尚未形成，保持 `SPEC_FROZEN / CODE_BLOCKED`，不得让执行方在混合工作区编码。
- 若现有 execution 创建入口已在其他分支绑定新规则版本，标记为范围冲突并停止，不在 B2 顺手修复。
- 若 runtime loader 需要公共 API、数据库、Spring 配置或管理台才能工作，停止并提出新规格。
- 若 C 无法原子地同时提供真实 readiness 与版本绑定，B2 不得放宽 fail-closed 门禁。

## 14. Next Task Handoff

本规格完成独立审计且 B1 精确 commit 门禁满足后，下一步是 Claude Code / DeepSeek 提交 `TASK_SPEC-036-B2` 编码前规格映射计划并停止等待 Codex Review；当前不授权编码、commit、push、C 或正式 E2E。
