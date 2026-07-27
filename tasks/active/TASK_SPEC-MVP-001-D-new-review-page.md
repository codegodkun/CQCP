# TASK_SPEC-MVP-001-D：新建审核页面

状态：SPEC_ACCEPTED / PRE_CODE_PLAN_REQUIRED

TASK_SPEC 类型：`execution`

父 TASK：`TASK-MVP-001`

父 TASK Level：`L2 Feature`（受控管理台用户入口）

Integration unit：`FEATURE-MVP-001`

执行方：Claude Code / DeepSeek

所在分支：`codex/feature-mvp-001-contract-review-loop`

规格冻结基线 commit：`0eb9d9e312c6698fb4532e623362bea091165f87`

## 0. 任务摘要

在现有 `apps/admin-web` 中实现：

```text
/review/new
```

页面允许受控管理台用户选择一份 `.docx` 文件、录入冻结 OpenAPI 的完整结构化字段，
或一键填入 `CQCP-MVP-DOCX-001` 已接受样本的结构化字段，然后按冻结契约向：

```text
POST /api/review/tasks
```

发送 `multipart/form-data`。页面在本规格内只展示 `202` 创建结果，不实现 execution
状态轮询、状态页或结果跳转；这些由 `TASK_SPEC-MVP-001-E` 承接。

本规格不修改后端、OpenAPI、数据库、Compose、结果页、审核链路或 fixture，不安装依赖，
不读取本地文件路径，不把 `callerType`、模型配置或版本字段放入请求。

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
- 每条验收断言的 UI 状态、真实输入、请求字段、响应/错误结果和测试。

表单与条件字段：
- OpenAPI 全部通用字段、MONTHLY 条件字段和 MILESTONE 条件字段的控件映射。
- string/amount/taxRate/ratio 的 required、trim、scale、range 校验。
- paymentMethod 切换后如何只提交当前分支字段，禁止提交隐藏字段空值。
- currency 固定 CNY、contractType 固定 ENGINEERING 的展示与请求来源。

Demo 预填：
- CQCP-MVP-DOCX-001 goldenExpected.structuredFields 的逐字段映射。
- 为什么只预填结构化字段，不能伪造或程序化预填浏览器 File。
- 预填值是已接受 Demo 输入，不声明为被测系统独立正确性证据。

multipart：
- FormData 的 file 与 metadata 两个且仅两个 part。
- metadata 使用 application/json Blob；不得手工设置 multipart Content-Type boundary。
- metadata 顶层只含可选 businessDocumentId、contractType、structuredFields。
- 禁止 callerType、modelProfileCode、版本字段和额外 part。

提交并发：
- UI loading/disabled 与同步 in-flight guard 如何共同保证双击只发送一次 fetch。
- 请求完成、失败和组件卸载时状态如何安全收口。

错误与安全：
- 400 fieldErrors 如何映射到字段和页面汇总。
- 409/429/503 BusinessErrorResponse、网络失败、非 JSON 和异常响应的业务化提示。
- 不展示 raw response、stack trace、endpoint、secret、绝对路径或文件内容。

明确不修改：
- 后端、OpenAPI、migration、Compose、A/B/C、状态页、轮询、结果跳转、
  结果页、review-assets、fixture/expected、TASK-034/036/C2、依赖和 lockfile。

预计测试：
- 路由与完整字段渲染；
- DOCX/空文件/大小和条件字段校验；
- Demo 预填；
- multipart 精确 part 与 metadata；
- 双击单请求；
- 202、400、409、429、503、网络/异常响应；
- admin-web lint、test、build 全量回归。
```

### 0.3 文件访问范围

```text
✅ 允许新增：
  apps/admin-web/src/reviewCreation/types.ts
  apps/admin-web/src/reviewCreation/api.ts
  apps/admin-web/src/reviewCreation/demoPreset.ts
  apps/admin-web/src/reviewCreation/ReviewTaskCreationPage.tsx
  apps/admin-web/src/reviewCreation/ReviewTaskCreationPage.test.tsx

✅ 允许修改：
  apps/admin-web/src/App.tsx
  apps/admin-web/src/App.test.tsx
  apps/admin-web/src/styles.css
  tasks/active/TASK_SPEC-MVP-001-D-new-review-page.md

👀 允许只读：
  AGENTS.md
  CURRENT_CONTEXT.md
  PRD.md
  tasks/active/TASK-MVP-001-contract-review-user-loop-demo.md
  tasks/active/TASK_SPEC-MVP-001-A-task-creation-upload-persistence.md
  tasks/active/TASK_SPEC-MVP-001-C-execution-status-query-api.md
  docs/ARCHITECTURE.md
  docs/frontend.md
  docs/backend.md
  docs/database.md
  docs/VERIFY.md
  packages/api-contracts/openapi.yaml
  packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json
  apps/admin-web/package.json
  apps/admin-web/vite.config.js
  apps/admin-web/src/main.tsx
  apps/admin-web/src/publicResult/**
  apps/admin-web/src/adminDiagnostics/**

⛔ 禁止访问：
  .env
  .env.*
  secrets/
  credentials/
  config/production/
  packages/test-fixtures/docx/**
  packages/test-fixtures/human-anchors/**
  outputs/
```

执行测试读取 `CQCP-MVP-DOCX-001.json` 只用于核对预填来源；不得修改或从测试运行时
动态导入该 expected 文件。生产 bundle 必须自包含冻结的 Demo 预填常量。

## 1. 冻结 UI 契约

### 1.1 路由与页面范围

新增精确路由：

```text
GET /review/new -> ReviewTaskCreationPage
```

必须保留现有：

```text
/                  -> PublicResultPage
/admin/diagnostics -> AdminDiagnosticPage
```

本规格不得新增 execution 状态路由，不得修改普通结果页的 `taskId` 查询方式。

页面标题使用“新建合同审核”，并在上传区域明显展示：

```text
当前仅支持 DOCX，DOC 待后续开发。
```

### 1.2 文件输入

* 浏览器文件选择提示 `accept=".docx"`，但不能只依赖 `accept`。
* 提交前必须再次校验文件名以不区分大小写的 `.docx` 结尾。
* 空文件拒绝；大于 `25 MiB`（`26,214,400` bytes）拒绝。
* `.doc`、`.pdf`、无扩展名和伪装为其他扩展名的文件不得发请求。
* 前端不负责解析 ZIP/DOCX 内容；后端继续负责 `INVALID_DOCUMENT_CONTENT`。
* 不显示或发送浏览器本地绝对路径，不读取或预览文件正文。

业务提示至少区分：

```text
请选择 DOCX 文件
仅支持 DOCX 文件
文件不能为空
文件不能超过 25 MiB
```

### 1.3 完整结构化字段

固定字段：

| 字段 | 页面行为 | 请求值 |
|---|---|---|
| `contractType` | 只读展示“工程采购合同” | `ENGINEERING` |
| `currency` | 只读展示“人民币（CNY）” | `CNY` |
| `businessDocumentId` | 可选文本 | 空白时省略 metadata 顶层字段 |

通用必填字段：

| key | 中文标签 | 类型 |
|---|---|---|
| `contractName` | 合同名称 | 非空字符串，最大 255 |
| `partyAName` | 甲方名称 | 非空字符串 |
| `partyBName` | 乙方名称 | 非空字符串 |
| `projectName` | 项目名称 | 非空字符串 |
| `contractTotalAmount` | 合同总金额（含税） | 元，最多 2 位小数 |
| `taxExcludedAmount` | 不含税金额 | 元，最多 2 位小数 |
| `taxAmount` | 合同税额 | 元，最多 2 位小数 |
| `taxRate` | 税率 | 0~100，最多 4 位小数 |
| `pricingMode` | 计价方式 | 冻结枚举 |
| `paymentMethod` | 付款方式 | 冻结枚举 |
| `invoiceType` | 发票类型 | 冻结枚举 |

枚举中文映射：

```text
pricingMode:
  FIXED_TOTAL_PRICE       -> 固定总价
  PROVISIONAL_TOTAL_PRICE -> 暂定总价

paymentMethod:
  MONTHLY   -> 按月度付款
  MILESTONE -> 按节点付款

invoiceType:
  VAT_GENERAL -> 增值税普通发票
  VAT_SPECIAL -> 增值税专用发票
```

`MONTHLY` 条件必填：

```text
prepaymentRatio
progressPaymentRatio
completionPaymentRatio
settlementPaymentRatio
warrantyRetentionRatio
```

五个比例均为 0~100、最多 2 位小数。

`MILESTONE` 条件必填：

```text
prepaymentRatio
milestonePaymentTerms
```

`milestonePaymentTerms` 使用多行文本且 trim 后非空。切换付款方式时，隐藏的不适用字段
不得出现在 `structuredFields` 中，不得发送 `null`、空字符串或占位值。

### 1.4 数字转换

表单在提交前先对用户输入字符串做精度与范围校验，再转换为 JSON number。不得先转
JavaScript number 再用转换后的字符串推断小数位，否则会把非法输入如 `100.000`
静默归一化为合法 `100`。

所有字符串在请求前 trim。空白必填字符串按缺失处理。

## 2. Demo 样本预填

页面提供显式按钮：

```text
填入 Demo 样本字段
```

按钮只填入以下已接受输入：

```json
{
  "contractType": "ENGINEERING",
  "structuredFields": {
    "contractName": "奔腾公司企鹅岛项目三标段土建总承包工程合同",
    "partyAName": "奔腾公司",
    "partyBName": "前水公司",
    "projectName": "企鹅岛",
    "contractTotalAmount": 8848,
    "taxExcludedAmount": 7830.09,
    "taxAmount": 1017.91,
    "taxRate": 13,
    "pricingMode": "FIXED_TOTAL_PRICE",
    "paymentMethod": "MONTHLY",
    "prepaymentRatio": 0,
    "progressPaymentRatio": 70,
    "completionPaymentRatio": 80,
    "settlementPaymentRatio": 97,
    "warrantyRetentionRatio": 3,
    "invoiceType": "VAT_SPECIAL",
    "currency": "CNY"
  }
}
```

来源仅为：

```text
packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json
  .goldenExpected.structuredFields
```

页面同时提示用户手工选择：

```text
1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx
```

浏览器安全模型不允许程序化预填 File；实现不得 fetch、打包或自动上传仓库 fixture。
预填不覆盖用户已选择的文件。

## 3. 冻结请求契约

### 3.1 FormData

请求必须是：

```text
POST /api/review/tasks
body = FormData
```

FormData 仅包含：

```text
file     -> 用户选择的 File
metadata -> Blob(JSON.stringify(metadata), { type: "application/json" })
```

不得手工设置 `Content-Type: multipart/form-data`；必须让浏览器生成 boundary。

metadata 精确形状：

```json
{
  "businessDocumentId": "optional",
  "contractType": "ENGINEERING",
  "structuredFields": {}
}
```

禁止加入：

```text
callerType
callerId
modelProfileCode
providerType
ruleSetVersion
parserVersion
promptVersion
schemaVersion
任意执行/模型/预算版本字段
任意第三个 multipart part
```

### 3.2 202 响应

成功响应必须校验并展示：

```json
{
  "taskId": "string",
  "executionId": "string",
  "status": "QUEUED",
  "resultUrl": "string"
}
```

若响应不是 JSON、缺少必填字段、字段类型不对或 `status` 不是 `QUEUED`，按安全的
“任务创建响应无效，请稍后重试”处理，不渲染 raw body。

D 不自动导航、不轮询、不自行拼接 result URL。展示后端返回的 `resultUrl` 文本用于
核对即可；E 接纳前不提供会进入不存在路由的按钮。

## 4. 提交并发与交互状态

* 首次提交同步取得 in-flight guard，随后才进入异步校验/请求。
* guard 为 true 时所有再次点击直接返回，不得创建第二个 FormData 或 fetch。
* 提交按钮在 in-flight 期间展示 loading 且 disabled。
* 请求成功或失败后释放 guard；用户可在修正输入后重试。
* 页面不得把失败请求误显示为已创建。
* Demo 预填、付款方式切换和重新选文件在提交中 disabled，避免请求内容与 UI 不一致。

只依赖 React state 的异步更新不足以证明双击安全；必须有同一事件循环内立即生效的
同步 guard，并由测试对一次 fetch 作精确断言。

## 5. 错误映射

### 5.1 客户端校验

客户端错误应显示在对应字段附近，并提供页面级摘要。至少覆盖：

* 文件缺失、扩展名、空文件、大小；
* 通用 required；
* paymentMethod 条件 required；
* 金额/税率/比例的数字、精度和范围；
* contractName 255 长度；
* milestonePaymentTerms 空白。

客户端不得静默补全除 `contractType=ENGINEERING`、`currency=CNY` 外的必填字段。

### 5.2 400 ValidationErrorResponse

只解析：

```text
code=VALIDATION_ERROR
message
fieldErrors[].field
fieldErrors[].code
fieldErrors[].message
```

后端 field path 例如：

```text
file
metadata
contractType
businessDocumentId
structuredFields.contractName
structuredFields.prepaymentRatio
```

已知路径映射到对应控件；未知路径仍显示在页面级错误列表，但不得动态创建字段或回显
raw JSON。必须显示全部 `fieldErrors`，不得只显示第一条。

### 5.3 409 / 429 / 503 与网络错误

对符合 `BusinessErrorResponse` 的 409/429/503，展示业务 `message`；可展示受控
`reason`，但不得展示完整对象。

默认提示：

```text
409 -> 当前执行配置不可用，请联系管理员
429 -> 当前任务较多，请稍后重试
503 -> 文档存储暂不可用，请稍后重试
网络失败 -> 无法连接审核服务，请检查网络后重试
其他/异常响应 -> 任务创建失败，请稍后重试
```

不得展示 response raw text、HTML、stack trace、endpoint、secret、绝对路径或浏览器
异常对象的原文。

## 6. 验收断言

### AC1 路由

`/review/new` 渲染“新建合同审核”；现有 `/` 和 `/admin/diagnostics` 路由回归通过。

### AC2 上传提示

页面可见精确文案“当前仅支持 DOCX，DOC 待后续开发。”

### AC3 文件前端门禁

有效非空 `.docx` 可提交；缺失、`.doc`、`.pdf`、空文件和大于 25 MiB 均阻止 fetch
并显示对应业务提示。

### AC4 固定字段

contractType 只读为 ENGINEERING/工程采购合同；currency 只读为 CNY/人民币。页面没有
caller、model 或 version 输入控件。

### AC5 通用字段完整

11 个 OpenAPI 通用必填字段全部可见、可验证并进入 structuredFields；金额标签必须是
“合同总金额（含税）”。

### AC6 MONTHLY 条件字段

MONTHLY 显示并要求五个比例，不显示 milestonePaymentTerms；请求不包含
milestonePaymentTerms。

### AC7 MILESTONE 条件字段

MILESTONE 显示并要求 prepaymentRatio 与 milestonePaymentTerms，隐藏四个月度专属
比例；请求不包含这四个字段。

### AC8 数字精度与范围

金额超过 2 位、taxRate 超过 4 位、比例超过 2 位或百分比超出 0~100 均阻止 fetch；
合法边界 0 与 100 可提交。

### AC9 Demo 预填

点击按钮后逐字段等于 §2 冻结值，paymentMethod 为 MONTHLY；不生成 File、不覆盖已选
File，并显示指定样本文件名提示。

### AC10 multipart 精确性

测试解析 FormData，证明只有 file、metadata；metadata Blob content type 为
application/json，JSON 形状与当前分支字段精确一致，且没有任何禁止字段。

### AC11 浏览器 boundary

fetch options 不设置 Content-Type header。

### AC12 202 展示

合法 202 只显示 taskId、executionId、QUEUED 和后端 resultUrl；不导航、不轮询、不
拼接 URL。

### AC13 防重复提交

同一事件循环双击/连续触发提交仅产生一次 fetch；提交中控件 disabled。

### AC14 400 全量错误

一个包含至少两个 fieldErrors 的 400 响应在对应字段/汇总中全部可见；不显示 raw
JSON，不丢弃第二条错误。

### AC15 409/429/503

三个状态分别显示业务化提示；符合白名单的业务 message 可显示，不泄露额外字段。

### AC16 网络与异常响应

fetch reject、非 JSON、缺字段和 HTML 错误页均显示安全默认提示，不显示 sentinel
异常文本、HTML、stack 或 endpoint。

### AC17 重试

失败后 guard 与 loading 释放；修正输入后可以再次提交并成功。成功前不残留旧成功卡片。

### AC18 响应白名单

202/400/business error 的解析器只消费冻结字段；响应内额外 `secret`、`stackTrace`、
`rawOutput`、`endpoint` sentinel 不得出现在 DOM。

### AC19 范围

diff 只包含 §0.3 allowlist；无 package/lockfile、后端、OpenAPI、migration、Compose、
fixture/expected、结果页或状态页修改。

### AC20 回归

admin-web lint、全部 test 和 production build 通过；review-assets validator 与
`git diff --check` 通过。

## 7. 测试与验证

执行方必须在 PowerShell 7 中运行：

```powershell
if ($PSVersionTable.PSVersion.Major -lt 7) {
  throw "PowerShell 7 is required"
}

npm.cmd run lint:admin-web
npm.cmd run test:admin-web
npm.cmd run build:admin-web
node scripts/validate-review-assets.mjs
git diff --check
git status --short
git diff --stat
```

至少提供：

* 新页面测试数 / failures / skipped；
* admin-web 全量测试数 / failures / skipped；
* lint 与 build 原始结论；
* validator 7/7；
* 最终 `git status --short` 与 `git diff --stat`。

禁止通过更新 snapshot、删除断言、降低字段精确性或修改 fixture/expected 让测试通过。

## 8. STOP 条件

命中任一项立即停止并回报：

1. 需要修改 OpenAPI、后端、migration、Compose 或安装依赖。
2. 需要实现状态页、轮询、结果跳转或修改普通结果页。
3. 需要把 fixture DOCX/expected 打进 production bundle 或自动替用户选择 File。
4. 需要新增 caller/model/version 输入或请求字段。
5. 现有 API 不能接受精确 file + application/json metadata 两 part。
6. 需要展示 raw response、stack、endpoint、secret、文件绝对路径或文件正文。
7. 需要进入 TASK-034、TASK-036/C2、review-assets、fixture/expected 或审核语义。
8. 工作区出现 allowlist 外修改或基线不 clean。
9. 任何测试需要网络、真实模型 endpoint 或生产凭据。

## 9. 实现报告

执行方完成后在本节追加：

```text
D_IMPLEMENTATION_REPORT

Gate:
- PS Major / branch / HEAD / initial status

Changes:
- 文件 -> AC -> 行为

Request Evidence:
- FormData parts
- metadata exact shape
- forbidden fields absent
- duplicate-submit fetch count

Test Evidence:
- focused tests
- full admin-web tests
- lint
- build
- validator
- git diff --check

Security Evidence:
- raw/sentinel non-disclosure
- local path/file content non-disclosure

Final Git State:
- git status --short
- git diff --stat
- no commit / push / merge
```

## 10. Codex Review Intake

Codex 将独立核验：

* 真实 diff 与 allowlist；
* 完整 OpenAPI 字段和条件分支；
* multipart 两 part 与禁止字段；
* 双击单请求；
* 202/400/409/429/503/网络错误安全映射；
* Demo 预填来源和 production bundle 边界；
* 全量 admin-web lint/test/build。

未通过 Review Intake 前不得进入 E。

## 11. 后续联动

D 经 Codex Review Intake 接纳后解锁 `TASK_SPEC-MVP-001-E`（状态页、轮询与结果跳转）。
D 不实现 worker、状态 API、状态页、结果跳转或结果页增强。
