# ADR-018：Model Profile Secret Reference 与 Provider Readiness

状态：Accepted / 实施与最终验证完成 / 最终独立审计待完成

日期：2026-07-28

## 背景

ADR-006 允许 Model Profile 表达本地、公网 OpenAI-compatible 与 mock provider；ADR-017 只为 `MVP_DEMO_MOCK` 建立 provider-specific readiness，并明确 LOCAL/PUBLIC 尚无权威 Secret/endpoint readiness source。若直接在管理台保存 API KEY 或任意 endpoint，将引入 Secret 泄漏、SSRF、历史配置漂移和“READY 即已发布”的错误语义。

## 适用范围

* 影响模块：Model Profile 管理、PostgreSQL schema、Admin API、部署 Secret、endpoint allowlist、connectivity test。
* 是否影响外部 API：新增 Admin API，不改变外部任务创建 API。
* 是否影响数据库：是，新增 immutable config version 的管理与 connectivity test 审计表。
* 是否改变审核语义：否，不接入 Provider runtime，不改变 Finding/SYS 或最终裁判。
* 是否激活公网模型：否。

## 决策

### 1. Model Profile 内容不可变，生命周期单独切换

`model_profile_config_version` 继续以 `(profile_code, config_version)` 表示不可变内容。以下字段属于 content：

```text
profileCode
configVersion
displayName
providerType
endpointAlias
modelName
usageScope
secretRequired
secretRef
timeoutSeconds
retryCount
effectiveFrom
```

以下字段属于 lifecycle：

```text
enabled
isDefaultForNewTask
readinessStatus
```

任何 content 变化必须插入新 `configVersion`。更新 API 的语义是：锁定 profile 域、停用旧 lifecycle、插入新版本；不得原地更新旧 content。失败时整事务回滚。

MVP-002 只允许创建：

```text
providerType = PUBLIC_OPENAI_COMPATIBLE
usageScope = EVALUATION
enabled = false
isDefaultForNewTask = false
secretRequired = true
```

它不创建 execution binding，不影响 `MVP_DEMO_MOCK`。

### 2. 使用 server-side Secret Reference

Model Profile 只保存受限 `secretRef`，首版格式固定为：

```text
env:CQCP_MODEL_DEEPSEEK_API_KEY
file:/run/secrets/cqcp-model-deepseek-api-key
```

规则：

* 通用解析器只识别 `env:CQCP_MODEL_*` 或受控 Secret root 下的绝对 `file:`，
  但首版 `deepseek-official` 在应用 allowlist 与数据库 CHECK 中只能使用上述两个
  provider-specific 精确引用；`env:CQCP_DB_PASSWORD`、其他模型环境变量和无关文件
  均拒绝，避免把任意进程 Secret 变成 Provider Bearer token。
* 文件必须是受控根目录的直接子普通文件，不是 symlink/reparse point；内容按
  strict UTF-8 解码，大小不超过 16 KiB。
* Linux 通过根目录 `SecureDirectoryStream` 相对 `NOFOLLOW_LINKS` 打开；Windows
  通过 `NOFOLLOW_LINKS + NOSHARE_DELETE` 打开最终文件并复核根目录/文件 identity。
  无法证明稳定打开、根目录或文件发生替换时均 fail closed，不退化为普通路径读取。
* Secret 解析只发生在服务端内存中，不写入数据库、响应、URL、日志、Snapshot、stage log、TuningPacket 或异常。
* API 只返回 `secretConfigured` 布尔值；不存在 KEY 读取、浏览器写入或旧值恢复接口。
* 轮换通过部署环境替换同一 Secret 完成。

`secretConfigured=true` 只表示当前进程能解析到非空 Secret，不表示 endpoint 可用、profile READY、execution 已发布或数据外发已授权。

### 3. endpointAlias 只能引用服务端 allowlist

服务端配置 `cqcp.model-gateway.endpoints` 维护 alias 到固定 HTTPS base URI 的映射。数据库和 API 只接受 alias，不接受 URL。首版 DeepSeek alias 为：

```text
deepseek-official -> https://api.deepseek.com
```

启动时验证：

* URI 必须是 absolute HTTPS。
* 不含 user-info、query、fragment。
* `deepseek-official` 的 scheme、host、port 与 path 必须严格等于
  `https://api.deepseek.com`，部署环境不能把该 alias 改成其他公网 URL。
* 禁止 loopback、link-local、site-local、multicast、unspecified 与 literal private address。
  IPv4 按 IANA special-purpose registry 精确拒绝特殊/文档范围，包括
  `192.0.0.0/24`、`192.0.2.0/24`、`192.88.99.0/24`、
  `198.51.100.0/24`，并保留相邻普通公网段可用。
  IPv6 采用 fail-closed global-unicast 策略：只接纳 `2000::/3`，并继续拒绝其中的
  IETF special、documentation、NAT64/IPv4 translation 与 `2002::/16` 6to4 等
  transition range；不得只依赖 `InetAddress.isSiteLocalAddress()`。
* DNS 的全部返回地址先统一校验；任一地址属于私网/保留/转换范围即拒绝。HTTP transport
  使用 request-scoped resolver 固定到同一组已校验地址，同时保留原 hostname 做 TLS
  校验，连接阶段不得再次走系统 DNS，阻断 DNS rebinding。

HTTP client 禁止自动 redirect。响应中的 redirect 一律记为 `REDIRECT_REJECTED`，不跟随。

### 4. Provider-specific readiness

PUBLIC profile 的 readiness 由以下条件共同计算：

```text
configValid
endpointAliasResolved
modelAllowed
secretConfigured
latestConnectivityStatus == SUCCEEDED
enabledLifecycle（仅发布选择时检查）
executionBindingExists（仅任务创建时检查）
```

管理页面区分：

```text
CONFIGURED
SECRET_MISSING
NOT_TESTED
CONNECTIVITY_FAILED
READY_FOR_EVALUATION_CONFIG
```

`READY_FOR_EVALUATION_CONFIG` 仍不等于 execution 已发布；任务创建 resolver 必须继续校验 enabled/binding/purpose/deploymentScope。

首版 PUBLIC model allowlist 固定：

```text
deepseek-v4-pro
deepseek-v4-flash
```

`deepseek-chat`、`deepseek-reasoner` 和未知 model ID 拒绝保存。

### 5. Connectivity test 契约

`POST /api/admin/model-profiles/{profileCode}/connectivity-tests` 请求体必须为空。服务端只使用已保存的最新 profile、allowlist endpoint 和 Secret Reference：

1. `GET /models` 验证 endpoint、鉴权和目标 model ID 存在。
2. 不发送合同、prompt 或业务字段。
3. timeout 使用 profile 的保守上限，redirect disabled，不自动 retry。
4. 只保存稳定状态、HTTP 类别、目标 model 是否存在、耗时和时间戳。

稳定状态：

```text
SUCCEEDED
SECRET_MISSING
AUTHENTICATION_FAILED
RATE_LIMITED
UPSTREAM_5XX
TIMEOUT
REDIRECT_REJECTED
MODEL_NOT_FOUND
MALFORMED_RESPONSE
ENDPOINT_NOT_ALLOWED
NETWORK_ERROR
```

不得保存或返回第三方 response body、Authorization header、Secret、底层 stack trace 或解析失败原文。

### 6. Admin API

```text
GET  /api/admin/model-profiles
POST /api/admin/model-profiles
PUT  /api/admin/model-profiles/{profileCode}
POST /api/admin/model-profiles/{profileCode}/connectivity-tests
```

创建/更新请求不包含 raw KEY、URL、enabled 或 default 字段；MVP-002 创建的 PUBLIC config 永远 disabled/unbound。未知字段由 JSON binding 拒绝。

所有 `/api/admin/**` 必须先经过后端 Bearer 鉴权。Admin token 与只读身份 token
由部署环境注入，只允许出现在 `Authorization` header，不进入 URL、数据库、响应或
浏览器持久化；缺失/未知身份返回 `401`，已认证但非 Admin 身份返回 `403`。本阶段不
引入账号体系、登录页或细粒度 IAM，但不得再仅依赖反向代理/内网作为 Admin API
保护。

## 备选方案

### 方案 A：管理台保存加密 KEY

不采用。当前没有平台 IAM、KMS、审批、读取审计和可靠轮换体系。

### 方案 B：允许任意 OpenAI-compatible URL

不采用。会形成 SSRF 和 redirect 绕过风险。

### 方案 C：连通成功即启用 profile

不采用。混淆配置 readiness、数据授权与 execution 发布。

## 选择理由

* Secret Reference 把密钥生命周期留在部署 Secret 系统。
* alias allowlist 使网络目标可审计、可测试并默认关闭。
* immutable version 保证历史 execution 与配置变化隔离。
* connectivity test 不处理合同内容，能在不扩大业务外发范围的情况下验证基础配置。

## 影响

### 正向影响

* 建立公网 provider 的最小安全配置基础。
* 历史 config version 可追溯。
* 管理页面可区分 Secret、连通与发布状态。

### 代价与风险

* 运维必须配置 Secret 和 endpoint allowlist。
* connectivity test 仍会向 allowlist endpoint 发起无合同内容的 `/models` 请求。
* 本 ADR 不解决真实合同外发合规、DLP 或生产 IAM。

## 不做什么

* 不激活 PUBLIC execution。
* 不实现 `REVIEWING_MODEL` 或模型 artifact。
* 不允许 raw KEY 浏览器输入。
* 不声明 Production Ready。

## 回滚与迁移

* 停用/删除应用层 Admin 路由并保留新增表的历史记录。
* 删除部署 Secret 即令 PUBLIC profile fail closed；不影响 `MVP_DEMO_MOCK`。
* 不删除被 execution 引用的旧 config version。

## 验证方式

* migration、immutable version、事务回滚和唯一 lifecycle 测试。
* Secret sentinel 全输出面扫描。
* allowlist、redirect、私网、CR/LF、symlink/reparse point 测试。
* connectivity 稳定错误分类测试。
* 证明 `MVP_DEMO_MOCK` seed 与 binding 未变化。

## 关联

* ADR-006、ADR-017、ADR-019
* `TASK-MODEL-001`
* `TASK-MODEL-002`

## 待确认

无。

## 接受记录

* 2026-07-28：用户明确授权实施《CQCP MVP-002 审核工作台、模型安全与盲态评测计划》。
* 最终 L3 冻结包仍须通过 CC AUDIT 与两个全新 Codex 独立审计；审计不通过则本实现不得收口。
