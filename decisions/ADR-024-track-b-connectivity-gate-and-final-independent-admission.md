# ADR-024：Track B 连通门禁与最终独立准入评测

状态：Accepted / Connectivity Gate GO / Human Seal Frozen / Final Claim
Terminal BLOCKED / Provider Admission NOT_ESTABLISHED

日期：2026-08-03

## 背景

`TASK-EVAL-003` successor 已完成人工封印并创建唯一正式 DeepSeek claim，但第一个
call 返回 `AUTHENTICATION_FAILED`。claim 已消费、未解盲且不得重试。后续 L0
connectivity/Provider 诊断发现，旧脚本通过递归目录扫描并取第一个 `sk-…`，实际选中
旧项目归档中的历史或示例 KEY；显式选择正确的仓库外 Secret 后：

* `GET https://api.deepseek.com/models` 返回 200；
* 实际模型为 `deepseek-v4-flash`、`deepseek-v4-pro`；
* `deepseek-v4-flash` 合成请求返回 `finish_reason=stop` 与精确 `PONG`；
* `deepseek-v4-pro` 在 thinking disabled、non-streaming、JSON mode 下返回
  `finish_reason=stop`，严格 JSON 解析为 `{"status":"PONG"}`。

该事实证明 Provider、官方 endpoint 与目标 V4 model 可用，但临时 L0 console 不是
hash-bound admission 证据，也不能使已失败 claim 恢复。项目负责人随后批准一个新的、
最终的独立 admission 机会，并要求先建立明确的 connectivity gate 和最小证据记录。

## 适用范围

* 影响模块：Track B 评测治理、DeepSeek connectivity evidence、Secret Reference。
* 影响阶段：`MILESTONE-MVP-002-TRACK-B-FINAL`。
* 是否影响外部 API：否；只调用官方 DeepSeek API，不修改 CQCP 公共 API。
* 是否影响数据库或版本快照：否。
* 是否影响模型、规则、prompt 或证据选择：只冻结评测调用门禁；不改变审核职责、
  prompt/schema、CandidateResolver、EvidenceSlot、SourceAnchor、Finding/SYS 或后端裁判。

## 决策

### 1. 显式 Secret Reference 替代 KEY 自动发现

正式工具只接受进程环境注入的 `DEEPSEEK_API_KEY` 和稳定别名
`DEEPSEEK_OFFICIAL_EVAL`。调用工具不得递归扫描目录、从多个候选中自动选择 KEY，
也不得把 Secret 路径、内容、内容 hash 或尾部字符写入仓库、console、receipt 或模型
artifact。

部署或本地操作者负责把正确的仓库外 Secret 注入进程；仓库只记录
`secretReferenceAlias` 与 `secretConfigured=true`。

### 2. connectivity gate 与 admission 完全分离

在生成任何正式 model input、dispatch 或 claim 前，必须对 exact endpoint/model 执行
一次独立 connectivity gate：

1. endpoint 固定为 `https://api.deepseek.com:443`；禁用 redirect、系统代理和任意 URL；
2. `/models` 必须返回 200，且精确包含 `deepseek-v4-pro`；
3. 使用全合成 prompt 对 `deepseek-v4-pro` 发起 non-streaming、thinking disabled、
   strict JSON 请求；
4. 只接受 HTTP 200、响应 model 一致、`finish_reason=stop`、严格 JSON schema valid 和
   精确合成 sentinel；
5. 任一步失败均不得创建正式 claim。

connectivity 成功只证明认证、路由和最小 schema 通路可用，不证明 role/candidate/
anchor/abstention admission，不放行 Provider A0/A1/A2。

### 3. 只记录最小 hash-bound connectivity evidence

正式 gate 只写一份任务专用、不可变 JSON evidence，记录：

* evidence schema/version、时间、endpoint origin；
* `secretReferenceAlias`、`secretConfigured=true`；
* `/models` HTTP 类别与排序后的 model IDs；
* 合成请求配置 hash、目标/响应 model、finish reason、strict JSON assertion；
* redirect/proxy/network、raw key/request/response/reasoning 未持久化声明；
* `formalAdmissionAffected=false`。

不保存 raw prompt、raw request、raw response、reasoning、Authorization header、KEY 或
Secret 内容 hash。该 evidence 不扩展为通用 claim/lease/receipt 平台。

### 4. TASK-EVAL-004 是新的最终独立评测，不是 TASK-EVAL-003 重试

新 integration unit 为 `MILESTONE-MVP-002-TRACK-B-FINAL`，父任务为
`TASK-EVAL-004`。新 12-packet corpus 的 taskId、executionId、packetId、sampleId、
候选值与 evidence text 必须同时与以下三套语料 disjoint：

1. 已解盲旧 18-packet corpus；
2. `TASK-EVAL-002` 失败 12-packet holdout；
3. `TASK-EVAL-003` 认证失败 successor。

三套历史 corpus、seal、dispatch、claim、opinion 和 blocked receipt 全部只读保留。
不得重试、补跑、迁移 identity、复用人工 decisions 或伪装成未执行。

### 5. 人工封印与正式执行契约保持严格

新 corpus 仍为 5 MEDIUM + 4 CONFLICTED eligible 和 3 zero-call controls。人工答案
必须在任何 evaluator 访问前逐项确认并 seal。正式执行仍使用冻结 prompt/schema、
`deepseek-v4-pro`、9 个单 packet calls、strict JSON、thinking disabled、non-streaming、
现有预算和 HTTP-started 零重试边界。

Codex 与 DeepSeek 只输出 role/candidate/anchor/abstention 模型意见，不产生或改变最终
Finding/verdict。schema、可靠 anchor、role、candidate、abstention 和 controls 必须
全部 100%，否则 admission 为 `NOT_ESTABLISHED`。

### 6. 有限轮次与审计门禁

本次批准只增加一个 `TASK-EVAL-004` 正式 admission subject。若 connectivity gate、
human seal、正式模型执行、解盲或三方审计任一步失败，本轮立即停止；不得自动创建
第四套语料。

正式审计仍为同一 freeze 上的 CC AUDIT 与两个全新 Codex auditors。Codex auditors
必须 `fork_turns="none"`、`gpt-5.6-sol`、`reasoning_effort="xhigh"`，且不得复用
blind evaluator。三份报告必须全部 `GO / P0=P1=P2=blocking=0`。

只有 admission 与三审同时 GO，才恢复 TASK-MODEL-002 A0+A1；PUBLIC profile 继续
`EVALUATION / disabled / unbound`，普通 `MVP_DEMO_MOCK` 与后端裁判边界不变。

## 备选方案

### 方案 A：用正确 KEY 重跑 TASK-EVAL-003 claim

不采用。HTTP 已开始且 one-time claim 已消费，换 KEY 重跑会改写第一次行为事实。

### 方案 B：把 L0 临时 console 直接视为正式 evidence

不采用。它没有冻结脚本、请求配置 hash、不可变路径和机器 verify，不能进入正式审计包。

### 方案 C：跳过 Track B 直接实现 Provider

不采用。connectivity、Track A 与 deterministic seam 均不能替代 eligible ambiguity
admission。

## 选择理由

* 根因是 Secret 选择流程错误，已由 exact endpoint/model 合成探针证伪 Provider
  不可用假设。
* 显式 Secret Reference 能消除递归扫描造成的错误凭据选择，同时不暴露 KEY。
* 新 corpus 保持模型未见和人工先封印，避免在失败样本上调参重测。
* 单一任务专用 connectivity evidence 提供审计事实，但不继续扩建治理平台。

## 影响

### 正向影响

* 正式 claim 前即可发现认证、模型 ID、thinking/JSON 配置问题。
* 失败历史保持不可变，新的 admission 仍可证伪、有限且独立。
* Secret 不落盘与 Provider disabled/unbound 边界不变。

### 代价与风险

* 需要第四套全新 synthetic corpus 和一次新的人工逐项确认。
* connectivity 成功仍不能保证 9 个正式意见全部满足严格 schema 与人工答案。
* 新任务失败后，本 Milestone 的 Provider 路径再次进入终态阻塞。

## 不做什么

* 不修改或重跑 TASK-EVAL-002/TASK-EVAL-003。
* 不建设通用 Secret 发现器、claim/lease/receipt 或审计传输平台。
* 不实现 Provider A0/A1/A2/A3，不进入 `REVIEWING_MODEL`。
* 不发送真实未脱敏合同、人工 ground truth、CQCP actual/expected 或 Finding/verdict。

## 回滚与迁移

* connectivity gate 前可删除尚未执行的 TASK-EVAL-004 preseal/tooling，不影响历史证据。
* connectivity evidence 生成后只读保留；它不产生 admission claim。
* 正式 claim 产生后，对应 corpus 和执行链永久不可回滚为未执行。
* 无数据库迁移或历史 Snapshot 兼容影响。

## 验证方式

* Secret 必须显式注入；缺失/别名错误在网络前 fail closed。
* `/models` 与 exact `deepseek-v4-pro` strict JSON synthetic probe。
* connectivity evidence create/verify、hash、不可覆盖和 Secret/raw response absence tests。
* 四语料 identity/value/text disjointness、human preseal、9×1/3 zero-call、不可重放 tests。
* immutable freeze 与三方全零审计。

## 后续动作

1. 创建 `TASK-EVAL-004`。
2. 实现并运行正式 connectivity gate，生成 hash-bound evidence。
3. gate GO 后生成第四套独立 corpus、review 和 challenge。
4. 人工确认后执行唯一正式 admission。

## 关联

* ADR-018、ADR-019、ADR-022、ADR-023
* TASK-EVAL-002、TASK-EVAL-003、TASK-EVAL-004
* TASK-034、TASK-036、TASK-MODEL-002

## 接受记录

* 2026-08-03：项目负责人批准“找到正确 KEY、验证官方 endpoint/模型、以合成数据完成
  connectivity 后，再建立新的独立正式 admission 与最小证据记录”方案。
* 2026-08-03：正式 gate evidence SHA
  `22d38b1183dc9fd53e5212fbb2b9dd2077234967cea503c8b4e624c50b80d1ac`；
  `/models` 返回 `deepseek-v4-flash/deepseek-v4-pro`，exact pro synthetic probe 为
  HTTP 200、`finish_reason=stop`、strict JSON sentinel matched，且
  `formalAdmissionAffected=false`、实际 KEY 泄漏文件数为 0。
* 2026-08-03：项目负责人绑定 challenge `TBF1-0fc18b…`、challenge/corpus/review
  SHA 确认 12 条 decisions；human seal SHA `8716d66d…`。唯一正式 DeepSeek claim
  SHA `62a7451e…` 已消费，第 8 个单包 call 因
  `OPINION_SCHEMA_INVALID` fail closed，terminal blocked receipt SHA
  `8b113c74…`，前 7 个 calls 完成、无自动重试。依本 ADR 有限轮次边界，不重试、
  不解盲、不进入 A0/A1/A2，Provider admission 保持 `NOT_ESTABLISHED`。
* 2026-08-03：项目负责人另行接受 ADR-025 / TASK-EVAL-005。该新重大范围只允许使用
  全新诊断集和第五套独立 corpus，不恢复、重试或补跑本 ADR 的 TASK-EVAL-004 claim；
  本 ADR 的终态证据与 `NOT_ESTABLISHED` 结论保持不变。
