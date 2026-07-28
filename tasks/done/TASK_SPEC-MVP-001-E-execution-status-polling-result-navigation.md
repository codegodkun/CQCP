# TASK_SPEC-MVP-001-E：状态页、轮询与结果跳转

状态：DONE / ARCHIVED_WITH_FEATURE / DUAL_AUDIT_GO / FEATURE_PR_PENDING

TASK_SPEC 类型：`execution`

父 TASK：`TASK-MVP-001`

父 TASK Level：`L2 Feature`（受控管理台用户闭环）

Integration unit：`FEATURE-MVP-001`

执行方：Claude Code / DeepSeek

所在分支：`codex/feature-mvp-001-contract-review-loop`

规格输入基线 commit：`9fcf8574bcefe8884ec2cd8ec93cb71a8c5fbecb`

## 0. 任务摘要

在现有 `apps/admin-web` 中接通：

```text
/review/new
-> /review/tasks/{taskId}/executions/{executionId}
-> GET /api/review/tasks/{taskId}/executions/{executionId}
-> /review/results/{taskId}?executionId={executionId}
```

本规格新增 execution 状态页，按冻结 C API 轮询公开状态和当前阶段，在终态停止；
`SUCCESS / PARTIAL_SUCCESS` 仅在 `snapshotAvailable=true` 且后端 `resultUrl`
通过同源和 identity 校验后自动进入正式结果路由；`FAILED`、404、网络错误、畸形响应和
不一致状态必须停留并显示业务化提示。

同时让 D 的创建成功结果进入状态页，并为现有普通结果页增加正式路由、execution identity
核对、结构化输入白名单展示；保留既有 `/?taskId=...` 兼容入口和现有证据摘要。

本规格不修改后端、OpenAPI、数据库、worker、状态机、审核链路、fixture/expected、
review-assets、Compose 或依赖，不实现浏览器真实 E2E；Docker Compose Demo 验收由 F 承接。

### 0.1 角色与门禁

* Codex 冻结规格、审查编码前规格映射计划、实现报告和真实 diff。
* Claude Code / DeepSeek 修改任何文件前，必须先提交 §0.2 计划并暂停。
* Codex 明确 `GO_TO_IMPLEMENT` 前不得编码。
* 执行方不得 commit、push、切分支、创建 PR、修改父 TASK 或扩大文件范围。
* 本 TASK_SPEC 只作为 Feature 内局部执行与 Review Intake 单位，不单独创建 PR。

### 0.2 编码前规格映射计划

必须逐项输出并等待 Codex 审查：

```text
AC1~AC20 映射：
- 每条验收断言的真实输入、UI 状态、请求次数、停止条件、跳转结果和测试。

路由与 identity：
- 创建成功后如何由 response.taskId/executionId 生成状态页内部路由。
- 状态页 path params 如何逐段 encode/decode，并精确用于 C API。
- 正式结果路由如何同时核对 route taskId、query executionId 和 snapshot identity。
- 既有 /?taskId=... 入口如何保持兼容。

状态响应白名单：
- ReviewExecutionStatusResponse 全部 required 字段与可选 supersededReason 的显式重建。
- PublicTaskStatus、ExecutionStage、ProviderType、SupersededReason 的允许值和 fail closed。
- terminal、status、snapshotAvailable 的组合一致性校验。
- 禁止 raw response、SYS-*、diagnostics、prompt、endpoint URL、secret、stack trace。

轮询：
- 首次立即请求；非终态成功响应后 1000ms 固定间隔继续。
- 如何保证任一时刻至多一个 status request，不发生重叠请求。
- terminal、404、网络/HTTP 错误、畸形/未知响应、identity 不匹配和 unmount 的停止条件。
- 手动“重新查询”如何只触发一次请求并恢复非终态轮询。

结果跳转：
- SUCCESS/PARTIAL_SUCCESS + terminal=true + snapshotAvailable=true 才能尝试跳转。
- resultUrl 必须是同源内部 /review/results/{taskId}?executionId={executionId}，
  且无 username/password/hash；任何 identity 或 origin 不匹配均停留并业务化提示。
- React StrictMode 下如何保证同一 execution 最多自动 navigate 一次。
- FAILED 停留，不读取或展示后端内部失败详情。

结果页：
- 正式 route taskId 与 query executionId 如何注入现有查询。
- 返回 snapshot.executionId 与 URL executionId 不一致时如何 fail closed。
- structuredFieldsSnapshot 只展示冻结业务字段白名单，未知 key 不渲染。
- 现有审核点、证据摘要、SourceAnchor 最小定位和根路由兼容如何回归。

明确不修改：
- 后端、OpenAPI、migration、worker、状态机、A/B/C、审核语义、
  review-assets、fixture/expected、TASK-034/036/C2、Compose、依赖和 lockfile。

预计测试：
- 创建成功进入状态路由；
- 状态路由/API identity；
- 首次立即请求、1000ms 轮询、无重叠、终态/错误/unmount 停止；
- 13 currentStage 与 5 public status 映射和未知值 fail closed；
- SUCCESS/PARTIAL_SUCCESS 安全跳转、FAILED 停留、恶意/错 identity resultUrl 拒绝；
- 404、网络、5xx、非 JSON、额外敏感字段零泄露和手动重试；
- 正式结果路由、snapshot identity、结构化字段白名单和旧入口回归；
- admin-web lint、test、build 全量回归。
```

### 0.3 文件访问范围

```text
✅ 允许新增：
  apps/admin-web/src/reviewStatus/types.ts
  apps/admin-web/src/reviewStatus/api.ts
  apps/admin-web/src/reviewStatus/ReviewExecutionStatusPage.tsx
  apps/admin-web/src/reviewStatus/ReviewExecutionStatusPage.test.tsx

✅ 允许修改：
  apps/admin-web/src/reviewCreation/ReviewTaskCreationPage.tsx
  apps/admin-web/src/reviewCreation/ReviewTaskCreationPage.test.tsx
  apps/admin-web/src/publicResult/types.ts
  apps/admin-web/src/publicResult/PublicResultPage.tsx
  apps/admin-web/src/App.tsx
  apps/admin-web/src/App.test.tsx
  apps/admin-web/src/styles.css
  tasks/active/TASK_SPEC-MVP-001-E-execution-status-polling-result-navigation.md

👀 允许只读：
  AGENTS.md
  CURRENT_CONTEXT.md
  PRD.md
  tasks/active/TASK-MVP-001-contract-review-user-loop-demo.md
  tasks/active/TASK_SPEC-MVP-001-C-execution-status-query-api.md
  tasks/active/TASK_SPEC-MVP-001-D-new-review-page.md
  docs/ARCHITECTURE.md
  docs/frontend.md
  docs/VERIFY.md
  packages/api-contracts/openapi.yaml
  apps/admin-web/package.json
  apps/admin-web/vite.config.js
  apps/admin-web/src/main.tsx
  apps/admin-web/src/publicResult/api.ts
  apps/admin-web/src/adminDiagnostics/**

⛔ 禁止访问：
  .env
  .env.*
  secrets/
  credentials/
  config/production/
  packages/test-fixtures/**
  outputs/
```

## 1. 冻结路由契约

### 1.1 创建页到状态页

D 创建接口返回合法 `202` 后，必须导航到：

```text
/review/tasks/{encodeURIComponent(taskId)}/executions/{encodeURIComponent(executionId)}
```

只使用显式解析后的 `taskId`、`executionId`，不从 raw response 或 `resultUrl` 反推。
同一次创建响应只导航一次；失败响应仍停留在创建页。

### 1.2 状态页

新增精确路由：

```text
/review/tasks/:taskId/executions/:executionId
```

缺失、空白或 decode 失败的 path identity 不发请求，显示“状态页地址无效”。不得为了容错
改为按 taskId 或 executionId 单独查询。

### 1.3 正式结果页

新增：

```text
/review/results/:taskId?executionId=:executionId
```

保留既有：

```text
/?taskId=:taskId
```

正式路由必须要求非空 `executionId`；结果 API 返回后必须满足：

```text
snapshot.taskId === route taskId
snapshot.executionId === query executionId
```

否则不得展示 snapshot，显示“结果身份校验失败，请返回状态页重新查询”。

## 2. 冻结状态 API 与响应

### 2.1 请求

```text
GET /api/review/tasks/{encodeURIComponent(taskId)}/executions/{encodeURIComponent(executionId)}
Accept: application/json
```

不设置认证、caller、model/version header，不追加 query，不读取结果 API 或管理台 API 代替。

### 2.2 显式类型和枚举

```text
PublicTaskStatus:
  QUEUED
  PROCESSING
  SUCCESS
  PARTIAL_SUCCESS
  FAILED

ExecutionStage:
  CREATED
  QUEUED
  PARSING
  INDEXING
  PLANNING
  BUILDING_EVIDENCE
  REVIEWING_RULES
  REVIEWING_MODEL
  COMPOSING
  SUCCESS
  PARTIAL_SUCCESS
  FAILED
  CANCELLED

ProviderType:
  LOCAL
  PUBLIC_OPENAI_COMPATIBLE
  MOCK

SupersededReason:
  TYPE_CORRECTION
  BUDGET_UPGRADE
  MANUAL_RERUN
  RULESET_RERUN
  MODEL_UPGRADE
  PARSER_UPGRADE
  ADMIN_RECOVERY
```

成功响应必须显式重建且只保留：

```text
taskId
executionId
status
currentStage?
terminal
snapshotAvailable
resultUrl
reviewModel:
  modelProfileCode
  providerType
  modelName
  endpointAlias
  modelConfigVersion
createdAt
updatedAt
superseded
supersededReason?
```

所有 OpenAPI required 字段必须存在且类型正确；未知枚举、空 required string、非法 date-time、
额外 identity 或组合不一致均 fail closed。raw JSON 对象不得直接进入 React state。

### 2.3 组合一致性

```text
QUEUED / PROCESSING:
  terminal=false

SUCCESS / PARTIAL_SUCCESS / FAILED:
  terminal=true

SUCCESS / PARTIAL_SUCCESS 自动跳转前:
  snapshotAvailable=true

FAILED:
  不要求 snapshotAvailable=true
```

`currentStage` 与公开 status 独立展示，但必须属于允许枚举。任何不一致响应停止自动轮询和跳转，
显示“状态数据暂不可用，请重新查询”。

## 3. 冻结轮询行为

1. 状态页首次 mount 立即发起一次请求。
2. 仅在最近一次合法响应为 `QUEUED/PROCESSING + terminal=false` 时，于该请求完成
   **1000ms 后**安排下一次请求。
3. 任一时刻最多一个 status fetch；慢请求期间不得因 timer 再发请求。
4. `SUCCESS/PARTIAL_SUCCESS/FAILED + terminal=true` 立即停止。
5. 404、其他非 2xx、网络失败、非 JSON、schema/enum/identity/组合失败立即停止自动轮询。
6. 失败后显示“重新查询”；一次点击只发一次请求。若得到合法非终态响应，恢复 §3.2。
7. route identity 变化时取消旧 timer、忽略旧请求完成结果，并为新 identity 立即查询。
8. unmount 后取消 timer；旧 promise resolve/reject 不得触发 state update、navigate 或新 timer。
9. 不使用指数退避、WebSocket、SSE、全局后台 poller 或跨页面缓存持久化。

状态页至少展示：

* taskId、executionId；
* 公开状态中文标签；
* 当前阶段中文标签；
* createdAt、updatedAt；
* reviewModel 的 modelProfileCode、providerType、modelName、modelConfigVersion；
* superseded=true 时的业务化提示和合法 supersededReason。

不得展示真实 endpoint URL、prompt、raw output、SYS-*、diagnostic、stack trace、secret 或
任意后端额外字段。`endpointAlias` 可被响应解析器校验，但本规格不要求在普通状态页显示。

## 4. 冻结终态与跳转

### 4.1 成功和部分成功

仅同时满足：

```text
status in {SUCCESS, PARTIAL_SUCCESS}
terminal === true
snapshotAvailable === true
resultUrl 通过 §4.2
```

才自动 `navigate(resultUrl, { replace: true })`。同一 execution 最多自动调用一次 navigate，
React StrictMode effect 重放不得产生第二次。

### 4.2 resultUrl 安全校验

把 `resultUrl` 相对当前 `window.location.origin` 解析后必须满足：

```text
origin === window.location.origin
username === ""
password === ""
hash === ""
pathname === /review/results/{当前 taskId 的规范编码}
searchParams 只有 executionId
searchParams.get("executionId") === 当前 executionId
```

协议相对 URL、绝对外域 URL、不同 task/execution、额外 query、hash、畸形 URL 均拒绝。
拒绝后停留，显示“结果地址校验失败，请稍后重新查询”，不得把原 URL 显示到 DOM 或日志。

### 4.3 失败

`FAILED + terminal=true` 停止轮询，停留显示：

```text
审核执行失败，请返回新建审核页面重新发起。
```

提供“返回新建审核”内部链接；不请求 stage log，不展示 failure reason、SYS-* 或 raw response。

## 5. 冻结错误映射

```text
404 REVIEW_EXECUTION_NOT_FOUND:
  未找到指定审核任务或执行，请确认页面地址。

其他 HTTP 非 2xx:
  状态查询暂不可用，请稍后重新查询。

网络失败:
  无法连接审核服务，请检查网络后重新查询。

非 JSON / 缺字段 / 未知枚举 / identity 或组合不一致:
  状态数据暂不可用，请重新查询。
```

只消费已知 404 的 `code`，不得展示后端 `message/reason/detail/fieldErrors` 原文。
任何 response 中注入的 `secret/stackTrace/rawOutput/endpoint/diagnostics/SYS-*` 不得进入返回
对象、React state、DOM、console 或测试快照。

## 6. 冻结结果页增强

### 6.1 正式 identity

正式路由使用 §1.3 identity；旧根路由继续按现有 taskId 查询，不新增 execution 强校验，
以保持 TASK-023 兼容。两种模式不得互相改写 URL。

### 6.2 结构化输入白名单

`ReviewResultSnapshot` 类型补入：

```text
structuredFieldsSnapshot: Record<string, string>
```

正式和兼容结果页都只展示下列 key；未知 key 完全忽略：

```text
contractName
partyAName
partyBName
projectName
contractTotalAmount
taxExcludedAmount
taxAmount
taxRate
pricingMode
paymentMethod
invoiceType
currency
prepaymentRatio
progressPaymentRatio
completionPaymentRatio
settlementPaymentRatio
warrantyRetentionRatio
milestonePaymentTerms
```

使用稳定中文标签；金额显示原快照字符串并标注“元”，比例显示原快照字符串并标注“%”，
枚举映射为 D 已冻结的中文文案；不做重新计算，不把未知值当 HTML，不展示任意额外 key。

### 6.3 既有结果能力

保留并回归：

* 审核点全状态统计；
* 业务化结果卡；
* 证据摘要；
* SourceAnchor block 级最小定位；
* SYS-*、promptVersion、diagnostics code 等敏感字段不展示；
* 404/409/网络业务化提示。

本规格不实现 DOCX 正文预览、字符级高亮、多 anchor 可视化或结果语义变化。

## 7. 验收标准

### Must Pass

1. 创建接口合法 202 后精确进入当前 task/execution 状态路由，失败不跳转。
2. 状态页首次 mount 立即请求 C 的精确双 identity endpoint。
3. `QUEUED/PROCESSING + terminal=false` 在每次请求完成 1000ms 后轮询，慢请求不重叠。
4. `SUCCESS/PARTIAL_SUCCESS/FAILED + terminal=true` 停止轮询，timer 不再产生请求。
5. 5 个公开 status 与 13 个 currentStage 均有显式中文映射；未知/null fail closed。
6. status response 仅重建 §2.2 字段；嵌套 reviewModel 仅五字段，额外 sentinel 零泄露。
7. taskId/executionId 与路由不一致、required 缺失、非法 date-time 或组合不一致停止并提示。
8. 404、其他 HTTP、网络、非 JSON 分别满足 §5，均停止自动轮询并可手动重试一次。
9. 手动重试获得合法非终态响应后恢复轮询；连点只产生一次在途请求。
10. route identity 变化或 unmount 后旧请求不得更新 UI、导航或恢复旧 timer。
11. SUCCESS 和 PARTIAL_SUCCESS 各自在 snapshotAvailable=true 且安全 URL 时只 navigate 一次。
12. snapshotAvailable=false、FAILED、恶意外域 URL、错 task/execution、额外 query/hash 均不跳转。
13. FAILED 显示固定业务化失败提示和返回新建审核入口，不读 stage log/diagnostics。
14. 状态页展示 identity、公开状态、currentStage、时间和模型摘要，不显示 endpointAlias URL 或敏感字段。
15. `/review/results/:taskId?executionId=...` 可加载现有结果；snapshot identity 完全匹配才展示。
16. 正式结果路由 identity 不匹配 fail closed；既有 `/?taskId=...` 入口保持兼容。
17. 结构化输入只展示 §6.2 白名单和稳定中文标签；注入未知 key/sentinel 不进入 DOM。
18. 现有审核点、证据摘要、SourceAnchor 定位和敏感字段隐藏回归全部通过。
19. 真实 `App` 路由测试覆盖 `/review/new`、状态路由、正式结果路由、`/` 和管理台诊断。
20. admin-web 全量 test、lint、production build、review-assets validator、`git diff --check`
    通过；实现报告与真实 status/diff 一致。

### Must Not

* 不修改后端、OpenAPI、migration、worker、状态机、审核引擎、结果快照语义或 API。
* 不新增依赖、service worker、WebSocket/SSE、全局轮询器、认证或外部导航。
* 不从 `currentStage` 推测公开 status，不从 status 推测 snapshotAvailable。
* 不请求 task result 代替 status API，不读取 diagnostics/stage log。
* 不展示 raw response、resultUrl 原文、SYS-*、prompt、endpoint URL、secret、stack trace。
* 不修改 fixture/expected、review-assets、TASK-034、TASK-036/C2 或正式 E2E 证据。
* 不 commit、不 push、不创建 PR。

### Expected 来源

Expected 来自冻结 OpenAPI、C 的已接受状态查询实现、D 的已接受创建响应、父 TASK 路由闭环和
现有 TASK-023 普通结果页。测试只证明前端状态映射、轮询、身份校验、跳转和展示契约；
不把系统输出当作独立质量 ground truth，不声明正式 MVP E2E 已通过。

## 8. 测试与验证命令

```powershell
Push-Location apps/admin-web
npm.cmd run lint
npm.cmd run test
npm.cmd run build
Pop-Location

node scripts/validate-review-assets.mjs
git diff --check
git status --short
git diff --stat
```

* 测试使用 Vitest fake timers、受控 deferred promise 和 MemoryRouter，不启动浏览器或服务端。
* 不执行依赖安装、Docker、网络请求、真实模型 endpoint 或 production build 部署。
* production build 只验证静态构建成功，不发布或上传 artifact。

## 9. Git 工作区

编码前计划阶段必须满足：

```text
PowerShell Major >= 7
branch = codex/feature-mvp-001-contract-review-loop
HEAD = Codex 派发 prompt 中给出的 E 规格冻结 commit
git status --short = clean
```

计划阶段不得修改任何文件。Codex 接受计划并明确 `GO_TO_IMPLEMENT` 后，执行方才可修改
§0.3 allowlist，并只允许向本 TASK_SPEC 的“实现报告”章节追加真实报告。

禁止：

* `git commit`
* `git push`
* `git switch/checkout`
* `git reset`
* `git clean`
* `git restore`
* `git stash`

## 10. STOP 条件

出现任一项立即停止：

* 需要后端、OpenAPI、migration、worker、状态机、审核语义或依赖变化；
* 需要放宽 resultUrl 同源/identity 校验才能跳转；
* 需要用 currentStage 推测公开 status 或用 status 推测 snapshot；
* 需要读取 stage log、diagnostics、prompt、raw output、endpoint URL 或 secret；
* 需要隐藏/改写现有 TASK-023 业务结果或修改 snapshot 语义；
* 无法用现有 C API 和结果 API 完成双 identity 页面闭环；
* 基线不 clean、测试需要真实网络/模型/生产凭据或出现 allowlist 外 dirty。

## 11. 编码前执行指令

把以下内容作为 Claude Code 新会话的首条任务：

```text
你只执行 CQCP 父任务 TASK-MVP-001 下已冻结的局部规格
tasks/active/TASK_SPEC-MVP-001-E-execution-status-polling-result-navigation.md。

第一阶段只做编码前规格映射，不修改任何文件：
1. 完整读取 AGENTS.md、CURRENT_CONTEXT.md、父 TASK、当前 TASK_SPEC 及 §0.3 只读上下文。
2. 确认 PowerShell 7、当前分支、Codex prompt 给出的 E 规格冻结 HEAD 与 clean status
   精确满足 §9。
3. 按 §0.2 和 AC1~AC20 输出逐项计划，重点证明：
   - 精确 route/API/snapshot 三方 identity；
   - 1000ms 非重叠轮询、全部停止条件、手动重试和 unmount 安全；
   - terminal/status/snapshotAvailable 组合 fail closed；
   - SUCCESS/PARTIAL_SUCCESS 同源 resultUrl 安全校验和 StrictMode 单次跳转；
   - FAILED/404/网络/畸形响应业务化且 sentinel 零泄露；
   - 正式结果路由、结构化字段白名单和旧入口回归。
4. 列出精确预计修改文件、明确不修改路径和预计测试。
5. 发现歧义或 STOP 条件立即停止。
6. 输出 E_PRE_CODE_PLAN 后暂停，等待 Codex 明确 GO_TO_IMPLEMENT；不得提前编码。

不得 commit、push、切分支或创建 PR。
```

## 12. 实现报告

执行方完成后在本节追加：

```text
E_IMPLEMENTATION_REPORT

Gate:
- PS Major / branch / HEAD / initial status

Changes:
- 文件 -> AC -> 行为

Polling Evidence:
- request identity / interval / non-overlap / stop / retry / unmount

Navigation Evidence:
- resultUrl same-origin / identity / once-only / reject paths

Result Evidence:
- formal route / snapshot identity / structured field whitelist / legacy route

Test Evidence:
- focused tests
- full admin-web tests
- lint
- build
- validator
- git diff --check

Security Evidence:
- raw/sentinel/non-allowlisted structured fields zero disclosure

Final Git State:
- git status --short
- git diff --stat
- no commit / push / merge
```

### E_IMPLEMENTATION_REPORT（2026-07-27）

**Gate**

```text
PowerShell Major: 7
branch: codex/feature-mvp-001-contract-review-loop
HEAD: adf19b33f1be7350727abe33dea0535948e97362
initial status: clean
```

**Changes**

* `reviewStatus/types.ts`：显式定义 5 个公开状态、13 个执行阶段、3 个 ProviderType、
  7 个 SupersededReason、公开状态白名单 DTO 与安全错误类型。
* `reviewStatus/api.ts`：精确双 identity GET；显式重建顶层和 `reviewModel` 白名单；校验
  required string/boolean、枚举、status/terminal 组合、RFC3339 格式及真实日历日期；
  404 只识别 `REVIEW_EXECUTION_NOT_FOUND`；网络、HTTP、非 JSON/schema 分流且不返回 raw。
* `reviewStatus/ReviewExecutionStatusPage.tsx`：规范 path identity、首次微任务立即请求、
  每次合法非终态请求完成后 1000ms 定时、同步 in-flight guard、route generation、AbortController、
  cleanup、手动重试；终态停止；同源 resultUrl 精确 identity 校验与单次 replace 导航；
  FAILED、superseded 和固定安全错误 UI。
* `ReviewTaskCreationPage.tsx`：合法 202 仅用显式解析的 taskId/executionId 生成规范编码状态路由，
  忽略 response resultUrl，不再显示中间成功卡。
* `PublicResultPage.tsx` / `publicResult/types.ts`：新增正式结果路由模式和 query-key execution
  隔离；正式 snapshot task/execution 双 identity fail closed；兼容 `/?taskId=...` 保持；
  `structuredFieldsSnapshot` 仅按 18 个冻结 key、稳定中文标签、金额/比例单位和 D 枚举映射展示，
  未知 key 完全忽略。
* `App.tsx` / `styles.css`：接入状态路由、正式结果路由及响应式状态/结构化字段样式。
* 三个测试文件覆盖 AC1~AC19 的路由、解析、轮询、停止、重试、竞态、跳转、identity、
  结果白名单、旧入口和敏感字段零泄露。

**Polling Evidence**

```text
首次请求: StrictMode 下 1 次精确 endpoint
interval: 合法 QUEUED/PROCESSING 请求完成 1000ms 后
non-overlap: 5 秒慢请求期间仍为 1 次；同步 ref 阻止手动连点
stop: 3 种终态、404/HTTP/network/schema/identity 错误均无后续 timer
retry: 单次重新查询复用同一请求函数，合法非终态恢复相同轮询路径
route/unmount: generation 忽略旧完成；AbortController + timer cleanup
```

**Navigation Evidence**

* 仅 `SUCCESS/PARTIAL_SUCCESS + terminal=true + snapshotAvailable=true` 尝试跳转。
* URL 必须非协议相对、无反斜杠、同 origin、无认证信息/hash、精确规范 task pathname，
  且只有一个匹配 executionId；外域、错 identity、重复/额外 query 和 hash 全部拒绝。
* `navigateOnceRef` 在导航前写入当前 executionId；StrictMode 测试证明只请求并跳转一次。
* `FAILED` 固定提示并提供 `/review/new` Link，不读取或展示失败详情。

**Result Evidence**

* `/review/results/:taskId?executionId=...` 精确加载现有结果 API；缺 executionId 不请求。
* 正式模式只有 snapshot taskId 和 executionId 同时匹配才渲染；不匹配完全隐藏 snapshot。
* 18 个结构化字段按固定顺序和中文标签展示；未知 `unknownSecret` sentinel 不进入 DOM。
* `/?taskId=...`、审核点统计、业务卡、证据摘要、SourceAnchor 定位及诊断隐藏回归通过。

**Test Evidence**

```text
focused status/creation/App: 62 passed / 0 failed
full admin-web test:          62 passed / 0 failed / 0 skipped（3 files）
admin-web lint:               0 errors / 0 warnings
production build:             success（1489 modules；仅既有 chunk-size 提示）
review-assets validator:      7/7 passed
git diff --check:             0 issues
```

一次把 test/lint/build 串为同一 PowerShell 行时遇到 Windows 临时文件锁 `EPERM`；随后按规格
逐条重跑，test、lint、build 均成功。该环境瞬时锁不计为测试断言失败。

**Security Evidence**

* 状态 raw object 仅存在于 parser 局部，显式重建后才进入 React state。
* 测试注入 `secret`、`stackTrace`、`rawOutput`、`endpoint`、`diagnostics`、`SYS-*`、
  HTML/网络异常 sentinel，返回对象和 DOM 均零泄露。
* 状态页校验但不显示 endpointAlias；不输出 resultUrl 原文；结果页未知结构化 key 不渲染。

**Final Git State**

```text
modified: App.tsx/App.test.tsx、reviewCreation 两文件、publicResult 两文件、styles.css、
          当前 TASK_SPEC 实现报告
untracked: reviewStatus/types.ts、api.ts、ReviewExecutionStatusPage.tsx、
           ReviewExecutionStatusPage.test.tsx
allowlist 外 dirty: 无
commit/push/merge: 未执行
```

**Independent Read-only Audit**

```text
P0: 0
P1: 0
P2: 1（AC9 手动重试恢复轮询缺少 1000ms 直接断言）
resolution: 已新增 fake-timer 回归；重试返回合法非终态后 999ms 仍为 2 次请求，
            1000ms 精确发起第 3 次请求
auditor changes: 0
```

## 13. Codex Review Intake

Codex 规格决策：

```text
ACCEPT_SPEC / PRE_CODE_PLAN_REQUIRED / NO_IMPLEMENTATION_YET
```

Codex 实现 Review Intake（2026-07-27）：

```text
ACCEPT_IMPLEMENTATION / GO_TO_COMMIT / F_UNLOCKED
```

核验结果：

* 实际 dirty 路径全部位于 §0.3 allowlist；未修改后端、OpenAPI、migration、worker、
  状态机、审核语义、依赖、fixture/expected、TASK-034 或 TASK-036/C2。
* 5 个公开 status、13 个 currentStage、双 identity、1000ms 非重叠轮询、终态与错误
  停止、手动重试恢复、route/unmount 竞态、同源 resultUrl 与单次跳转均有可证伪测试。
* 正式结果路由仅在 task/execution identity 完全匹配时展示；18 个结构化字段使用白名单，
  旧 `/?taskId=...` 入口、审核点、证据摘要和 SourceAnchor 行为保持回归。
* 独立只读审计发现的 AC9 P2 测试缺口已补齐：合法非终态重试后 999ms 不请求，
  1000ms 精确恢复下一次请求。
* Codex 于 2026-07-27 重跑 admin-web 全量 `62/62`、lint、production build、
  review-assets validator `7/7` 与 `git diff --check`，全部通过。React Router v7 future
  flag 和 Vite chunk-size 均为既有非阻塞提示。

Codex 将独立核验：

* 真实 diff 与 allowlist；
* route/API/snapshot identity；
* polling interval、无重叠、停止、重试与 unmount；
* terminal/status/snapshotAvailable 组合；
* resultUrl 同源、identity 和单次跳转；
* FAILED/404/网络/畸形响应安全；
* 正式结果路由、结构化字段白名单和旧入口回归；
* admin-web 全量 lint/test/build。

上述实现 Review Intake 已完成并接纳，允许进入 F。

Git 收口：

```text
commit: 539c9e6d026fcac112831118022b451e7632c40c
message: feat(review): add execution polling and result navigation
push: 未执行
```

## 14. 后续联动

E 已经 Codex Review Intake 接纳，`TASK_SPEC-MVP-001-F`（Docker Compose 真实 Demo 验收）
现已解锁。
E 不修改后端、worker、审核语义或正式 TASK-034 E2E。
