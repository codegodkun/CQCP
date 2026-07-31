# TASK-MODEL-001：Model Profile、Secret Reference 与 Readiness

状态：已实现 / junction 与单 sentinel 证据已满足 / 冻结 45568f… 审计 NO_GO 后整改中

类型：Model Governance / Security / Database / Admin API

Task Level：`L3 高风险治理`

Integration unit：`MILESTONE-MVP-002-CORE`

优先级：P0

负责人：Codex

创建日期：2026-07-28

来源：ADR-006、ADR-017、ADR-018

## 背景

主线只有 migration seed 的 `MVP_DEMO_MOCK`，没有公网 provider 的权威 Secret/endpoint readiness source，也没有安全的 Model Profile 管理契约。任何公网配置必须先解决 immutable version、Secret Reference、endpoint allowlist、稳定错误分类和默认关闭。

## 目标

* 提供不可变 config version 的 Model Profile 创建/版本切换与只读列表。
* 使用 server-side Secret Reference 和 endpoint alias allowlist 建立 provider-specific readiness。
* 提供不接收 endpoint、KEY 或 prompt 的受控 connectivity test。

## 非目标

* 不在浏览器输入、读取或轮换 raw KEY。
* 不启用 PUBLIC profile，不改变 `MVP_DEMO_MOCK` 默认 binding。
* 不实现真实审核 Provider 或进入 `REVIEWING_MODEL`。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `docs/ARCHITECTURE.md`
* `docs/backend.md`
* `docs/database.md`
* `docs/ai-review.md`
* `docs/deployment.md`
* `decisions/ADR-006-model-profile-switching-and-public-provider-scope.md`
* `decisions/ADR-017-execution-binding-release-and-demo-profile-readiness.md`
* `decisions/ADR-018-model-profile-secret-reference-and-provider-readiness.md`

### Optional Context

* `docs/model-gateway-budget-baseline.md`

### Out of Scope

* 平台 IAM、双人审批、DLP、自助 Secret 管理、任意 endpoint、生产 profile。
* `TASK-MODEL-002` Provider runtime。

## 范围

### 包含

* `GET /api/admin/model-profiles`
* `POST /api/admin/model-profiles`
* `PUT /api/admin/model-profiles/{profileCode}`：创建新 config version
* `POST /api/admin/model-profiles/{profileCode}/connectivity-tests`
* V3 migration、repository、service、稳定错误与脱敏测试。

### 不包含

* KEY write/read API。
* PUBLIC profile 的 execution binding、默认启用或生产使用。

## 约束

* immutable content 变化必须创建新 `configVersion`；lifecycle 切换在单事务内完成。
* KEY 只由仓库外环境/Secret 文件注入；数据库和应用输出只暴露 `secretConfigured`。
* `endpointAlias` 只能来自服务端 allowlist；redirect 与私网目标 fail closed。
* Connectivity transport 禁止 redirect 和自动 retry；一次管理操作最多发起一次
  `/models` 请求，Provider runtime 的 retry 策略不适用于该接口。
* 第一版 DeepSeek model ID 只允许 `deepseek-v4-pro`、`deepseek-v4-flash`。
* PUBLIC profile 只能是 `EVALUATION`、disabled、unbound；`READY` 不等于 execution 发布。

## 交付物

* ADR-018、V3 migration、Admin API、前端配置列表、测试与文档。

## 可证伪验收断言

1. 更新 immutable 字段后数据库保留旧 config row，并生成不同 `configVersion`；任何 execution 历史引用不漂移。
2. Secret sentinel 不出现在 HTTP 响应、应用日志、数据库、结果 Snapshot、stage log、TuningPacket 或异常。
3. 请求体提交 endpoint、KEY、prompt 或未知字段被拒绝；任意 URL 不能触发网络请求。
4. 缺失 Secret、401/403/429/5xx、timeout、redirect、模型不存在和畸形响应分别映射到稳定类别且不透传第三方 body/stack。
5. 创建 PUBLIC profile 时 `usageScope != EVALUATION`、`enabled=true`、`isDefault=true` 或旧 model ID 均被拒绝。
6. 既有 `MVP_DEMO_MOCK` binding、default 和 readiness 不变。
7. 401/403/429/5xx 等 connectivity 响应均只产生一次 transport 请求，Apache
   HttpClient 内建自动重试必须显式关闭。
8. IPv6 NAT64/IPv4 translation、6to4、IETF special 与 documentation 地址均在
   transport 前拒绝；已知 global-unicast 公网正例仍可通过。

## 测试与验证

* Repository/Migration/Controller/Secret redaction 定向测试。
* 全量 Gradle、`bootJar`、admin-web Vitest/lint/build。
* Compose PostgreSQL 查询 immutable row、唯一 lifecycle 与 Secret 缺失状态。

## 回滚边界

* 停用新增 PUBLIC profile config；V3 表保留审计历史，不回写 V1/V2 execution。

## 文档更新要求

* 在 L3 门禁和 Milestone 收口更新架构、数据库、后端、AI、部署、父 TASK、CURRENT_CONTEXT 与 changelog。

## 风险

* SSRF、Secret 泄漏、错误透传、profile lifecycle 竞态。

## 待确认

无。

## 完成记录

* 实现日期：2026-07-28。
* 变更范围：Flyway V3、immutable Model Profile Admin API、server-side Secret
  Reference、endpoint alias allowlist、provider-specific readiness、受控 `/models`
  connectivity test、管理台页面、OpenAPI 与安全回归。
* 安全结果：所有 Admin API 要求后端 Bearer 鉴权；raw KEY 无写入/读取 API；
  `deepseek-official` 只接受两个精确 provider-specific Secret Reference，endpoint
  固定为官方 origin；PUBLIC profile 强制 `EVALUATION / disabled / unbound`。
  redirect、非 HTTPS、localhost、私网/保留 IP、混合 DNS answer、DNS rebinding、
  symlink/reparse point、畸形响应与错误透传均 fail closed。
* Compose/浏览器：`DEEPSEEK_EVAL_ACCEPTANCE` 被保存为 disabled EVALUATION；
  缺少部署 Secret 时连通测试返回 `SECRET_MISSING` 且零网络调用；
  `MVP_DEMO_MOCK` 仍为 enabled/default DEMO。direct/proxied 未认证 Admin API 均
  为 `401`，已认证非 Admin 为 `403`；浏览器不显示或持久化 token。
* PostgreSQL：真实数据库验证 immutable 两版本、历史内容不变、insert 失败时 lifecycle
  整体 rollback，以及数据库 CHECK 拒绝 raw/任意 `env:`/任意 `file:` 引用。
* 第二轮审计整改：Connectivity Apache client 显式
  `disableAutomaticRetries()`；transport-level 测试对 401/403/404/429/5xx 和成功
  响应逐项断言 `/models` 请求次数严格为 1。
* 第三轮审计整改：IPv6 地址只接纳 `2000::/3` global unicast，并拒绝 IETF
  special、documentation、NAT64/IPv4 translation、6to4 等 conversion/transition
  range；相关 literal 与 DNS answer 回归已通过。
* 本轮 Secret 文件读取加固：只接纳受控根目录直接子文件、strict UTF-8 和
  16 KiB 上限；Linux 使用 `SecureDirectoryStream` 相对安全打开，Windows 使用
  `NOFOLLOW_LINKS + NOSHARE_DELETE` 文件句柄并复核 root/file identity。根目录
  替换、嵌套路径、超限与畸形 UTF-8 均有 fail-closed 回归。
* IPv4 allowlist 按 IANA special-purpose registry 修正掩码：拒绝
  `192.0.0.0/24`、`192.0.2.0/24`、`192.88.99.0/24`、
  `198.51.100.0/24` 等特殊/文档范围，同时回归证明相邻普通公网段不会被误拒。
  Flyway V3 以数据库 CHECK 固定 PUBLIC DeepSeek profile 的 provider/model/
  purpose/disabled/default/secret 约束；管理页在 profile GET 失败时保持表单禁用，
  401/403 清除内存 token。
* 旧冻结和旧测试计数均已失效；后端 `380/380`、admin-web `68/68`、盲评脚本
  `11/11` 只覆盖 D1/D2/R7/Track B 增量之前的 subject。Compose/浏览器既有
  验收证据保留；当前 Milestone pre-human-confirmation subject 的统一验证已
  通过：后端连续两次 53 suites、`878/878`，admin-web `68/68`、盲评
  `26/26`、review-assets `100/100`，D1 `443/443`、D2 `20/20`，其余强制
  步骤全部成功。实际 Track B admission 后仍须全量重跑；冻结身份以新的
  verified manifest 为准。
* 上述安全整改后的定向复核为 `GO / P0=P1=P2=blocking=0`；最新一次全新隔离
  PostgreSQL 后端全量为 `887/887`、`bootJar` 通过，admin-web 最近完整运行
  `70/70`、lint/build 通过。最终冻结前仍须在不再变化的 subject 上完整重跑。
* 审计结果：历次冻结均因至少一份 Codex 审计 `NO_GO` 或受审 subject 后续变化
  而作废；冻结包
  `0c77803c50e7625931d75bfdd5880fdae1308d401539a61717efe87eef54781b`
  的两份 Codex 审计共同确认 Provider gate 最终 Memory hash 绑定没有实际落地。
  生成器现直接计算 `CURRENT_CONTEXT.md` SHA-256，测试独立重算并逐字比较；最终
  Memory Writeback 后必须重建 gate。整改后统一验证 20/20 步退出码 0，CC AUDIT
  与两个全新 Codex 审计必须对
  当前 manifest 从零执行。最终结论只读取
  `outputs/task-mvp-002/audit/final-decision.json`，不以本任务自述替代。
* 遗留问题：三份样本的逐 input hash runtime evaluation 外发授权已经满足，
  DeepSeek Track A 也已完成；但 Track B zero-call 只证明 deterministic HIGH
  不调用模型，独立 eligible-ambiguity corpus 尚待人工 ground truth 确认，
  Provider admission 仍未满足。`READY` 仍不等于 execution 发布；连通测试成功
  或 Track A 表现也不能替代 Provider activation 门禁。
* 2026-07-31 中途收敛：本任务进入先行 Core integration unit，仅补 Secret root
  directory junction fail-closed 和单一 fake sentinel 横跨 HTTP/header/log/
  exception/DB 的不可泄漏证据，并证明 Snapshot/stage log/TuningPacket 在该
  connectivity 路径不可达；不新增 DLP、redaction manifest 或 raw KEY 管理能力。
  Core 完整验证与正式三方全零审计通过前保持 active。
* Integration unit：`MILESTONE-MVP-002`
* 独立审计触发依据：Secret、数据库、Admin API 和公网 provider readiness。
