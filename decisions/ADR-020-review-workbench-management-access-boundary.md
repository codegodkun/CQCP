# ADR-020：审核工作台管理访问边界

状态：Accepted / 实施与最终验证完成 / 最终独立审计待完成

日期：2026-07-28

## 背景

MVP-002 新增 execution 级任务清单、parser-backed 合同预览和原始 DOCX 下载。它们
属于内部审核工作台能力：任务清单可枚举合同名、task/execution identity 与结果 URL，
preview/download 直接暴露合同正文和原始文件。只校验 `taskId + executionId` 的数据归属
不能证明调用方有权读取合同，且 opaque identity 不能替代身份认证。

项目一期不建设账号体系、组织权限矩阵或复杂 IAM，但 `docs/ARCHITECTURE.md` 已要求
“权限可以简单，但不能假安全；管理台至少登录保护”。因此必须冻结一个最小、后端强制
的访问边界。

## 决策

### 1. 受保护接口

以下接口必须通过部署侧管理 Bearer token 认证：

```text
GET|HEAD /api/review/tasks
GET|HEAD /api/review/tasks/{taskId}/executions/{executionId}/document-preview
GET|HEAD /api/review/tasks/{taskId}/executions/{executionId}/document
```

未携带或无法识别的 token 返回稳定 `401`，并在进入 repository/service 前停止。
Token 只允许通过 `Authorization: Bearer` header 传递，不允许放入 URL、响应、数据库、
日志、localStorage 或其他浏览器持久化。

Filter 的路径判定必须先使用与 Spring MVC 一致的 application path 语义移除每个
segment 的 matrix parameter。`/api;v=1/review/tasks`、`/api/review/tasks;v=1` 或
`.../document;v=1` 仍属于受保护接口，不得因原始 `requestURI` 字符串不同而跳过鉴权。
Spring MVC 会把 `HEAD` 自动映射到对应的 `GET` handler；Filter 必须把两种方法视为
同一敏感读取边界，匿名 `HEAD` 也必须在进入 controller/service 前返回 `401`。

### 2. 最小角色

沿用部署侧两个 Secret：

```text
CQCP_ADMIN_API_TOKEN
CQCP_ADMIN_READONLY_TOKEN
```

两者都可只读访问任务清单、preview 和 download；只有 Admin token 可访问
`/api/admin/**` 写接口。只读 token 请求 `/api/admin/**` 继续返回 `403`。未配置匹配
token 时受保护接口 fail closed。

该划分是 MVP 的最小内部访问边界，不等于完整 IAM、用户账号、租户授权或生产
Production Readiness。

### 3. 前端行为

管理访问 token 只保存在当前 React provider 的内存状态。任务清单在认证前不得发起
列表请求；普通结果摘要可继续按既有兼容契约读取，但合同 preview 和原始下载在认证前
禁用。下载使用带 Authorization header 的 fetch + Blob，不使用带 token 的 URL。

刷新、关闭页面或显式退出会清除 token。Model Profile 管理仍要求 Admin token，不因
只读工作台身份而放宽。

### 4. 保持不变

以下既有公共兼容接口和语义不因本 ADR 改变：

```text
POST /api/review/tasks
GET /api/review/tasks/{taskId}/executions/{executionId}
GET /api/v1/tasks/{taskId}/result?executionId=...
```

它们仍只返回既有创建、公开状态和普通结果摘要。若后续要把普通结果或创建接口纳入
caller identity/API key、签名 URL、细粒度合同 ACL，必须另立 TASK/ADR，不在本次
审计整改中推断完成。

## 可证伪验收断言

1. 匿名请求三个受保护资源的 GET/HEAD 均返回 `401`，且对应 service 零调用。
2. 未知 token 返回 `401`；只读 token 与 Admin token 均可读取任务清单。
3. 只读 token 访问 `/api/admin/**` 返回 `403`，Admin token 可访问。
4. 前端认证前不请求任务清单/preview；认证后只在 Authorization header 发送 token。
5. 下载不使用 token query parameter，页面文本、URL 和 localStorage 均不存在 token。
6. OpenAPI YAML/JSON 对三个接口声明 Bearer security 与 `401`。
7. 匿名 matrix-parameter 变体仍返回 `401`，且对应 service 零调用。
8. 匿名 HEAD 及其 matrix-parameter 变体在进入 service 前返回 `401`，不暴露合同
   下载响应头或任务清单元数据。

## 回滚

回滚时应整体撤销三个 GET 的 filter 路由、前端内存访问控件和 OpenAPI security。
不得只移除前端输入框而保留匿名后端，也不得只隐藏页面而保留可匿名调用的接口。

## 后果

* 直接打开任务清单或正式结果页时，普通结果摘要仍可见，但清单、合同原文和下载需要
  当前页面会话内重新输入管理访问 token。
* MVP 不新增账号表、登录 Session、Cookie、OAuth、复杂 RBAC 或多租户。
* Compose 和浏览器验收必须覆盖匿名 `401`、只读访问、token 不持久化和 header 下载。
