# ADR-025：Track B schema 稳定性诊断与第五套独立 admission

状态：Accepted / Schema Stability GO 24/24 / Fifth Admission `SEALED_NO_GO_MODEL_MISMATCH` / Provider `NOT_ESTABLISHED` / Terminal

日期：2026-08-03

## 背景

TASK-EVAL-004 的第四套独立 Track B corpus 已完成人工先封印、9×1 dispatch 和全新
Codex blind opinion。唯一 DeepSeek claim 在第 8 个单包调用返回
`OPINION_SCHEMA_INVALID`，已 fail closed、消费且不得重试；未形成完整 DeepSeek
opinion，也未解盲。该事实不能区分 prompt 单包措辞、输出示例缺失、模型随机漂移或
validator 具体拒绝类别。

DeepSeek 官方 JSON Output 契约要求 `response_format={"type":"json_object"}`、prompt
明确要求 JSON 并给出期望 JSON 示例，同时提示 content 仍可能偶发为空。因此仅靠
connectivity 成功或前 7 个 call 成功，不能证明 9×1 admission 稳定。

项目负责人明确批准一个新的、有限的 TASK-EVAL-005：先用全新合成诊断输入按证据
修改、版本化并冻结 prompt/schema，再创建与前四套及诊断集完全独立的第五套
12-packet corpus。若第五套正式 admission 再失败，Track B Provider 路径终止，不再
创建第六套。

## 适用范围

- 影响模块：Track B 评测 prompt/schema、DeepSeek strict-output 诊断与 admission。
- 影响阶段：`MILESTONE-MVP-002-TRACK-B-SCHEMA-STABILITY`。
- 是否影响外部 API：否；只使用固定 `api.deepseek.com:443` 评测接口。
- 是否影响数据库或版本快照：否。
- 是否影响模型、规则、prompt 或证据选择：只允许评测 prompt/schema 版本变化；不改
  EvidenceSlot、CandidateResolver、SourceAnchor、Finding/SYS 或后端裁判。

## 决策

### 1. 历史执行不可变

TASK-EVAL-002/003/004 的 corpus、human seal、dispatch、claim、blocked receipt 和既有
opinion 全部只读。尤其不得重试、补跑、解盲或用新 prompt/schema 处理 TASK-EVAL-004
输入；诊断结果不得追溯改写其 `NOT_ESTABLISHED` 结论。

### 2. 诊断与 admission 物理分离

先创建只用于 schema 稳定性的全新合成诊断集。诊断集：

- 不含前四套 packet 的 identity、candidate value 或 evidence text；
- 不建立 Provider admission，不产生人工 ground truth，也不得进入第五套 corpus；
- 只评估 JSON/envelope/严格字段/类型/enum/packet coverage/anchor 自洽；
- 可在进程内读取响应完成验证，但只落盘聚合类别、计数、配置 hash、请求 hash、模型、
  时间和调用数；不得保存 raw request、raw response、content、reasoning 或 Secret；
- 每个 HTTP-started call 零重试；`UNKNOWN_SIDE_EFFECT` 永远 fail closed。

诊断预算固定为：旧冻结版本 baseline 12 个单包 calls；最多两个候选版本，每个先执行
12 calls，只有 12/12 strict accepted 才执行同版本第二个 12-call confirmation。单一候选
必须累计 24/24 strict accepted，且两个 pass 的所有拒绝分类均为 0，才能成为冻结版本。
总诊断上限为 60 calls；达到上限仍无合格版本即终止，不创建第五套 corpus。

### 3. 单变量、版本化修改

修改顺序固定为：

1. 先验证旧 prompt 在单包输入下的覆盖措辞与 schema 失败率；
2. 第一候选只收敛为单包专用指令，并加入完整合法 JSON 输出示例；
3. 只有证据显示仍失败时，第二候选才允许进一步收窄 schema 表达或 validator 诊断分类，
   不得删除字段、放宽 enum、可靠 anchor、coverage 或 100% 门禁；
4. `deepseek-v4-pro`、thinking disabled、non-streaming、`json_object`、1500 max tokens、
   timeout 和零重试保持不变。

每个候选产生新的 `promptVersion/schemaVersion/requestBuilderVersion` 与 byte hash；不能
以覆盖原文件的方式修改历史版本。官方稳定 endpoint 当前不提供 `json_schema`
response format；不得为取得 strict tool-call beta 而切换 `/beta` endpoint 或引入工具调用。

### 4. 仅保存最小诊断投影

validator 对失败只生成以下稳定聚合类别：

```text
ENVELOPE_INVALID
FINISH_REASON_NOT_STOP
CONTENT_EMPTY
CONTENT_NOT_JSON
ROOT_FIELDS_INVALID
OPINION_COUNT_INVALID
OPINION_FIELDS_INVALID
FIELD_TYPE_OR_ENUM_INVALID
PACKET_COVERAGE_INVALID
OCCURRENCE_OR_ANCHOR_INVALID
ABSTENTION_CONTRACT_INVALID
UNKNOWN_VALIDATION_FAILURE
```

artifact 不记录 response content、字段值、reasoning、provider body 或底层堆栈；单 call
只记录 request hash、配置 hash、分类和 accepted 布尔值。Secret 仍只在进程内使用。

### 5. 第五套独立 admission

只有诊断稳定性门禁 GO 后，才冻结新 prompt/schema 并生成第五套 12-packet corpus：

- 5 MEDIUM + 4 CONFLICTED eligible，3 zero-call controls；
- 相对前四套 corpus 和诊断集，taskId/executionId/packetId/sampleId、candidate values、
  evidence texts 全部 0 overlap；
- 人工 proposed decisions 与 corpus 物理分离，项目负责人必须在任何 evaluator/model
  input/network attempt 前绑定实际 challenge/corpus/review SHA 确认 12 条答案；
- 正式 DeepSeek 固定 `deepseek-v4-pro`、9×1 calls、3 controls zero-call、一次 claim；
- schema、可靠 anchor、role、candidate、abstention、controls 全部 100%，模型不得输出
  或改变 Finding/verdict。

正式 admission 的 HTTP-started/schema/content/finish/timeout/`UNKNOWN_SIDE_EFFECT`
全部零重试。失败即 `providerAdmission=NOT_ESTABLISHED` 并终止 Track B，不创建第六套。

### 6. 后续激活和审计边界

TASK-EVAL-005 admission GO 后仍须对同一 immutable subject 完整 verification/freeze，
并由 CC AUDIT 与两个全新 `fork_turns="none"`、`gpt-5.6-sol/xhigh` Codex auditors
全部给出 `GO / P0=P1=P2=blocking=0`。在此之前 Provider A0/A1/A2 不启动，PUBLIC
profile 保持 `EVALUATION / disabled / unbound`，普通 Demo 和后端最终裁判不变。

## 备选方案

### 方案 A：直接用新 prompt 补跑 TASK-EVAL-004 第 8/9 call

不采用。其一次性 claim 已消费，补跑会改写历史行为并造成解盲污染。

### 方案 B：降低 schema 或允许单点失败

不采用。Track B 是 Provider admission，任何字段、anchor 或语义维度非 100% 均不能
建立准入。

### 方案 C：切换 beta strict tool call

不采用。它改变 endpoint、响应拓扑和运行时同构契约，超出本次 prompt/schema 稳定性
诊断边界。

## 选择理由

- 诊断集与最终 holdout 分离，避免在 admission 答案上调参。
- 有限调用预算和两阶段 confirmation 能证伪“单次偶然成功即稳定”。
- 聚合失败分类可定位 prompt/schema 问题，同时继续满足 raw response 不落盘。
- 第五套仍由人工先封印，保持 ground truth 独立性。

## 影响

### 正向影响

- 将 schema 偶发失败从不可区分的单一代码收敛为有限、可审计的证据。
- prompt/schema 版本选择与正式 admission 解耦。

### 代价与风险

- 最多增加 60 个合成诊断 calls 和一次第五套 9-call admission。
- 24/24 只证明本次有限诊断样本稳定，不等于生产可靠性或 SLA。
- 第五套任何失败都会终止本 Milestone 的 Track B Provider 路径。

## 不做什么

- 不重试或补跑 TASK-EVAL-004。
- 不保存 raw response、reasoning、Secret 或人工 ground truth payload。
- 不启动 A0/A1/A2/A3，不进入 `REVIEWING_MODEL`。
- 不建设通用评测平台或第六套 corpus。

## 回滚与迁移

- 诊断网络前可删除未执行的 TASK-EVAL-005 tooling；已执行诊断 evidence 只读保留。
- 新 prompt/schema 未通过 24/24 时不得发布为 admission 版本。
- 无数据库迁移；历史 Snapshot 和四套 Track B evidence 不变。

## 验证方式

- 诊断集与前四套 identity/value/text disjointness。
- baseline/candidate/confirmation 精确调用计数、零重试、60-call 总上限。
- 聚合分类可重建且 artifact 无 raw request/response/content/reasoning/Secret。
- 冻结版本 hash、第五套 preseal/human seal/9×1/zero-call/unblind 门禁。
- 同一 freeze 的 CC AUDIT 与两个全新 Codex auditors 全零 GO。

## 后续动作

1. 创建 TASK-EVAL-005 与 schema 诊断反馈环。
2. 运行 baseline，按证据最多形成两个候选版本并冻结唯一合格版本。
3. 稳定性 GO 后生成第五套 corpus/challenge，等待项目负责人确认人工答案。
4. 执行唯一正式 admission；失败终止，成功才进入 verification/freeze/三审。

## 关联

- ADR-018、ADR-019、ADR-022、ADR-023、ADR-024
- TASK-EVAL-002、TASK-EVAL-003、TASK-EVAL-004、TASK-EVAL-005
- TASK-034、TASK-036、TASK-MODEL-002

## 接受记录

- 2026-08-03：项目负责人明确批准 TASK-EVAL-005、必要 ADR、全新合成 schema 诊断、
  按证据版本化并冻结 prompt/schema，以及第五套完全独立 12-packet corpus；人工
  ground truth 继续在模型访问前确认。
- 同次批准固定正式 admission 为 `deepseek-v4-pro`、9×1 calls、3 zero-call controls、
  全维 100%，并明确 TASK-EVAL-004 不重试、不补跑，失败后不建第六套且不得提前启动
  A0/A1/A2。
- 2026-08-03：旧 prompt baseline 在 12 个合成单包 calls 上为 12/12；结合
  TASK-EVAL-004 第 8 call 失败，结论为旧版本存在偶发 schema 不稳定，不能以一次
  baseline 恢复历史 claim。Candidate 1 只增加单包精确措辞与完整 JSON 示例，随后
  qualification/confirmation 各 12/12，合计 24/24、0 rejection；总诊断 36/60，未创建
  Candidate 2。冻结 prompt SHA `6a29b74f…`、schema v2 与 stability freeze SHA
  `44468d84…`，仅放行第五套 corpus 创建，不建立正式 admission。
- 2026-08-03：第五套 preseal 已冻结，corpus SHA `17343dd3…`、review draft SHA
  `0ab07f6b…`、review document SHA `3c3ff928…`，相对前四套及诊断集
  identity/value/text overlap 全部为 0。challenge
  `TB51-5deddd744f6f432f8f7760bd51899e16` / SHA `f934a0e8…` 等待项目负责人确认
  12 条 proposedExpected；确认前 model input、evaluator access 与 admission network
  均禁止。
- 2026-08-03：项目负责人绑定实际 challenge/corpus/review SHA 确认全部 12 条人工
  decisions。唯一正式 DeepSeek 9×1 执行 strict schema 9/9 accepted，claim `f9086db1…`、
  opinion `0e4f96fc…`；Codex blind opinion `95ecd582…`。解盲后 Codex 全维 100%，
  DeepSeek 在 4 个 CONFLICTED packet 上 mismatch，role/candidate/anchor/abstention 各
  55.56%。终态 seal `520d7b38…` 为 `SEALED_NO_GO_MODEL_MISMATCH`，Provider admission
  `NOT_ESTABLISHED`。本 ADR 的失败边界已触发：不重试、不建第六套、不启动 A0/A1/A2。
