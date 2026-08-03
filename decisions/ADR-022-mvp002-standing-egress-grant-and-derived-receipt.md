# ADR-022：MVP-002 持续公网外发授权与派生绑定

状态：Accepted / Machine Gate Implemented / Exercised / Track B Terminal NO-GO / Grant Not Revoked

日期：2026-08-02

## 背景

MILESTONE-MVP-002 的 Track A、Track B、connectivity、Provider validation、
EVALUATION shadow 和独立只读审计需要有限的 DeepSeek 公网调用。逐次聊天确认会把
调用身份与授权证据割裂；反过来，无限期或不绑定实际输入的宽泛授权又无法证明某次
外发处于既有边界内。

项目负责人已明确授予本 Milestone 的持续授权，并要求主 Codex 将其写入 ADR、父
TASK 和机器门禁，采用“standing grant + 每次自动派生 hash-bound receipt”。该授权
不替代 Track B 人工 ground truth 的独立逐项确认，也不授权真实未脱敏合同或 Secret
外发。

## 决策

### 1. 授权生命周期

持续授权只对 `MILESTONE-MVP-002` 生效，直至：

- Milestone 完成；或
- 项目负责人明确撤销。

在上述期间，符合本 ADR 全部边界的调用不再逐次请求聊天确认。静态机器真源为：

`scripts/blind-evaluation/mvp002-standing-egress-grant.json`

任何字段缺失、扩展、顺序外模型、`revoked=true` 或安全布尔值放宽均 fail closed。

### 2. 允许的目的与数据

允许目的仅限：Track A/Track B EVALUATION、connectivity test、Provider validation、
EVALUATION shadow 和独立只读审计。

允许数据仅限用户已确认范围的脱敏 EVALUATION、合成语料和冻结审计材料。用户负责
输入脱敏把关；CQCP 本轮不建设程序化脱敏平台。无论用户把关如何，以下内容仍不得
进入模型 payload：

- 人工 ground truth；
- CQCP actual/expected；
- 最终 Finding/verdict；
- Secret、raw KEY；
- 真实未脱敏生产合同；
- raw Provider response 或 `reasoning_content`。

### 3. 网络、模型与 Secret

- Provider 只允许 `https://api.deepseek.com:443`；禁止 redirect、代理、任意 endpoint
  和私网地址。
- 模型仅允许 `deepseek-v4-pro` 与 `deepseek-v4-flash`；CC AUDIT 固定使用
  `deepseek-v4-flash`。
- KEY 只从此前指定的仓库外 Secret Reference 读取并仅驻留进程内，不进入仓库、
  数据库、响应、日志、Snapshot、stage log、评测 artifact 或审计包。
- 调用必须 strict JSON、non-streaming；不得持久化 raw response 或 reasoning。
- `UNKNOWN_SIDE_EFFECT` 不得自动重发。

### 4. Eligibility 与业务边界

deterministic HIGH、invalid bundle、无可靠 anchor、无 eligible candidate 和预算不完整
均为 zero-call。模型不得直接生成或改变最终 Finding/verdict；后端裁判边界不变。

Track B 的人工答案必须在任何 evaluator 访问前独立封印。standing grant 不构成人工
ground truth 确认，也不能把已解盲旧语料重新变为独立 admission。

### 5. 每次派生 hash-bound receipt

每次实际执行必须在网络前从静态 grant 自动派生不可变 receipt，至少绑定：

```text
actual input SHA-256
dispatch SHA-256
provider call set SHA-256
每个 outbound request SHA-256
endpoint origin
model
call count / input count
createdAt
```

实际执行结果继续使用现有结构化调用 receipt/claim 证据；不新增通用审计传输平台、
lease 或可复用跨任务授权协议。receipt 不保存 raw request、raw response、KEY 或
reasoning。

## Track B holdout 的具体收窄

新 12-packet holdout 固定为 9 个 eligible packet 和 3 个 zero-call control。与旧
18-packet run-v3 不同，每个 eligible packet 使用一个运行时同构的单包请求，形成
`9 calls / 9 inputs / 3 excluded controls`；不得按 family 合并，避免评测输入拓扑偏离
未来 runtime seam。

## 影响

- 优点：无需逐次聊天确认，同时每次调用仍绑定实际字节和调用集合。
- 代价：静态 grant、派生 receipt 和执行结果必须共同验证；任一漂移即停止。
- 不影响：PUBLIC profile 仍 `EVALUATION / disabled / unbound`；A0/A1/A2 尚未开始；
  普通 `MVP_DEMO_MOCK` 与最终裁判不变。

## 回滚

项目负责人撤销授权时将 grant 标记为 revoked 并停止新执行；已生成证据只读保留。
Track B 或 Provider 失败时不得放宽本 ADR，应按阶段停止规则记录 `NOT_ESTABLISHED`。

## 验证

- 静态 grant 精确字段与安全布尔负向测试。
- 未存在人工 ground-truth seal 时不得创建实际 model input、dispatch 或 receipt。
- seal 存在后离线派生必须固定为 9 个单包请求并排除 3 个 controls。
- 真实 runner 必须验证 grant/receipt/dispatch/request hash 后才能打开网络。
- 独立三方审计检查 payload 排除、Secret 不落盘、zero-call 与一次正式执行。

## 接受记录

- 项目负责人已明确授予 MILESTONE-MVP-002 持续公网外发授权，并要求采用
  “standing grant + 每次自动派生 hash-bound receipt”；无需逐次聊天确认。
- 项目负责人另行明确：用户负责输入脱敏把关，本程序不建设脱敏平台。
- 本 ADR 只记录上述已接受边界，不授权绕过人工 ground truth、Provider
  disabled/unbound、模型职责或最终三审门禁。
- 2026-08-02：首个 12-packet holdout 已在本 grant 下派生精确 receipt 并发起正式
  调用；第 2 个 call schema invalid 后 fail closed，无自动重试、无 accepted DeepSeek
  opinion。该失败不撤销 standing grant。项目负责人已授权 TASK-EVAL-003 successor，
  后续授权内调用无需逐轮确认，仍须每次派生实际 hash-bound receipt。
- 2026-08-03：TASK-EVAL-004 final 执行绑定 model input `bd648dd1…`、dispatch
  `141d5344…`、call set `9881d520…` 与 derived receipt `f17018cf…`。唯一 claim
  在第 8 个 call schema invalid 后终态 BLOCKED，未自动重试；standing grant 不授权
  改写或重跑该 claim。
- 2026-08-03：TASK-EVAL-005 第五套唯一正式 admission 完成 9×1 strict schema calls，
  解盲后因 4 个 CONFLICTED packet 语义不一致终态
  `SEALED_NO_GO_MODEL_MISMATCH / NOT_ESTABLISHED`。ADR-025 已使第六套、A0/A1/A2 和
  新的 Provider evaluation call 均不具备任务资格。standing grant 仍是尚未由项目
  负责人撤销的授权上限，不等于调用资格，也不得被用来绕过该终态 deny；其撤销/终止
  方式作为 Milestone 阻塞收口待决事项保留。
