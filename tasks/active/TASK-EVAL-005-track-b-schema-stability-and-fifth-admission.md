# TASK-EVAL-005：Track B schema 稳定性诊断与第五套独立 admission

状态：历史终态 BLOCKED / `SEALED_NO_GO_MODEL_MISMATCH` / Provider admission `NOT_ESTABLISHED` / 未来恢复边界由 ADR-026 部分替代

类型：Evaluation / Model Governance / Prompt-Schema Stability

Task Level：`L3 高风险治理`

Integration unit：`MILESTONE-MVP-002-TRACK-B-SCHEMA-STABILITY`

优先级：P0

负责人：Codex

创建日期：2026-08-03

来源：项目负责人对新重大评测范围的明确批准、ADR-019、ADR-022、ADR-024、
TASK-EVAL-004 terminal blocked evidence

## 背景

TASK-EVAL-004 已在第 8 个 `deepseek-v4-pro` 单包 call 因
`OPINION_SCHEMA_INVALID` fail closed。claim 已消费、无自动重试、无完整 DeepSeek
opinion且未解盲。项目负责人批准最后一次有限路径：先在全新合成输入上诊断 schema
稳定性，按证据创建和冻结新的 prompt/schema 版本，再建立第五套完全独立 corpus。

## 目标

- 以有限、可证伪的合成诊断区分 prompt 单包措辞、输出示例、模型非确定性和 validator
  拒绝类别。
- 在不保存 raw response/reasoning/Secret 的前提下形成可重建的聚合诊断 evidence。
- 只有某个新版本在两个独立 12-call pass 上 24/24 strict accepted，才冻结为正式
  admission prompt/schema。
- 建立与前四套及诊断集完全 disjoint 的第五套 12-packet corpus，在人工先封印后执行
  唯一 `deepseek-v4-pro` 9×1 admission。
- 全部维度 100% 且三方全零 GO 才建立 Provider admission；否则终止且不建第六套。

## Task Context

### Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- 本任务包
- `docs/ARCHITECTURE.md` 第 12、22.7、22.8、22.12 节
- `docs/ai-review.md`
- `decisions/ADR-019-blind-evaluation-and-model-activation-boundary.md`
- `decisions/ADR-022-mvp002-standing-egress-grant-and-derived-receipt.md`
- `decisions/ADR-024-track-b-connectivity-gate-and-final-independent-admission.md`
- `decisions/ADR-025-track-b-schema-stability-diagnosis-and-fifth-admission.md`
- `tasks/active/TASK-EVAL-004-track-b-final-independent-admission.md`

### Optional Context

- 前四套 Track B 只读 corpus/evidence。
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`

### Out of Scope

- 重试、补跑、解盲或修改 TASK-EVAL-004 claim/corpus。
- Provider A0/A1/A2/A3、`REVIEWING_MODEL`、PUBLIC binding、数据库 migration。
- 修改 EvidenceSlot、CandidateResolver、SourceAnchor、Finding/SYS、状态机或 verdict。
- raw response/reasoning 持久化、人工 ground truth 外发、真实未脱敏合同。
- 第六套 corpus、通用评测/审计传输平台。

## 分阶段范围与停止点

### D0：治理与诊断契约冻结

- 接受 ADR-025，登记 TASK-EVAL-005 和任务地图。
- 冻结失败分类、调用预算、单变量顺序、Secret/网络/零重试边界。
- 验收：文档互相引用一致；TASK-EVAL-004 仍终态不可变；无网络调用。

### D1：baseline 诊断

- 建立与前四套完全 disjoint 的 12 个合成 schema 诊断 input。
- 用 TASK-EVAL-004 的旧冻结 prompt/schema 配置执行恰好 12 个单包 calls。
- 只落盘聚合分类、单 call request/config hash 和 accepted 布尔值。
- 验收：12 个 calls 都有终态、无重试、raw response/content/reasoning/Secret 泄漏为 0。
- 停止：网络/认证/UNKNOWN_SIDE_EFFECT 失败立即终止当前 pass，不自动重发。

### D2：候选版本与稳定性门禁

- Candidate 1 只将 prompt 收敛为单包指令并加入完整合法 JSON 示例；严格 validator
  字段、enum、anchor、coverage 均不变。
- 先运行 12-call qualification；只有 12/12 才运行第二个 12-call confirmation。
- 若失败且聚合证据支持，最多创建 Candidate 2；不得增加第三候选。
- 验收：唯一版本累计 24/24 strict accepted、所有失败类别 0，版本和 bytes hash 冻结。
- 停止：最多 60 个诊断 calls；无合格版本即终止，不创建第五套 corpus。

### E1：第五套 corpus 与人工 challenge

- 生成 5 MEDIUM + 4 CONFLICTED eligible 和 3 zero-call controls。
- 与前四套及诊断集 identity/value/text overlap 全部为 0。
- proposal 与 source/corpus 物理分离；生成 hash-bound review/challenge。
- 停止：只向项目负责人提交实际 hash 和 12 条 proposedExpected；确认前不得创建
  model input、dispatch、claim 或访问 evaluator。

### E2：人工封印与唯一正式 admission

- 项目负责人绑定实际 challenge/corpus/review SHA 接受、修改或拒答 12 条答案。
- human seal 必须早于 Codex/DeepSeek input 与任何网络 attempt。
- 全新 Codex blind evaluator 只读 exact input；DeepSeek 固定 `deepseek-v4-pro`、
  thinking disabled、non-streaming、strict JSON、1500 max tokens、9×1 calls。
- 3 controls 必须保持 zero-call；HTTP-started/schema/content/finish/timeout/
  UNKNOWN_SIDE_EFFECT 均零重试。
- 验收：schema、reliable anchor、role、candidate、abstention、controls 全部 100%。
- 停止：任一失败即终止、不解盲、不建第六套、不启动 A0/A1/A2。

### E3：验证、冻结与三审

- 只在 admission 100% 后运行完整验证并冻结同一 clean immutable subject。
- 派发 CC AUDIT 和两个全新 `fork_turns="none"`、`gpt-5.6-sol/xhigh` Codex auditors；
  blind evaluator 不得复用为 auditor。
- 验收：三份均 `GO / P0=0 / P1=0 / P2=0 / blocking=0` 且绑定同一 hash。
- 任一 finding 或 subject 漂移立即停止；不得组合旧 GO。

## 关键验收断言

1. TASK-EVAL-004 的 claim/receipt/hash 未改变，也没有新增网络 attempt。
2. 诊断 input 与前四套 corpus identity/value/text overlap 全部为 0。
3. baseline=12 calls；候选最多 2 个；总诊断 calls `<=60`，HTTP-started 零重试。
4. diagnosis artifact 不含 raw request/response/content/reasoning、Secret、人工答案。
5. 冻结版本在两个 12-call pass 上 24/24 strict accepted，所有失败分类为 0。
6. 第五套与前四套及诊断集完全 disjoint，人工 seal 早于任何 evaluator/model access。
7. 正式 call set 精确 9×1，3 controls zero-call；全部维度 100%。
8. 模型不生成或改变 Finding/verdict，PUBLIC 仍 disabled/unbound。
9. admission/验证/三审任一步失败均不创建第六套且不启动 A0/A1/A2。

## 回滚边界

- 网络前可删除未执行的诊断 tooling；已执行 evidence 只读保留。
- 未通过 24/24 的 prompt/schema 版本不得成为正式版本。
- 正式 claim 创建后不可恢复为未执行；失败后任务终态 BLOCKED。
- 无数据库、公共 API 或生产 binding 回滚。

## Memory Writeback

- D0、D2、人工封印、正式 admission 和最终审计为关键写回点。
- 更新本 TASK、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、
  `docs/ai-review.md`、ADR-025 和 `changelog/2026-08.md`。
- 不得声明 Production Ready 或解锁 TASK-028/031/032。

## 当前待确认

- 无。本次唯一 admission 已终态 NO-GO；不得恢复本 claim。项目负责人已另行批准
  TASK-EVAL-006 / ADR-026 的独立有限恢复，该恢复不修改本任务证据。

## 阶段完成记录

### D0：治理冻结

- ADR-025、任务包、ARCHITECTURE、ai-review、MVP_TASK_MAP、CURRENT_CONTEXT 与
  changelog 已同步；`git diff --check` 通过。
- TASK-EVAL-004 claim/receipt 保持不可变，无重试、补跑或解盲。

### D1：baseline 诊断

- 合成诊断 input SHA `d67f0444f0dff37e624b5f438028af243ea636c0e1dfa390bff5d038ad9ec7d8`；
  相对前四套 corpus 的 identity/value/evidence overlap 全部为 0。
- 旧 prompt SHA `8a06b5177e7df8ca6097aaa752e2b089a0aa28e4f313ef158438627427aafe10`；
  12 个独立单包 calls 为 12/12 strict accepted，结果 SHA
  `790b2a73d50fef1b15e8c0f0e7cf614674e4cae030c2b792a3b580d61b785fbb`。
- 该结果与 TASK-EVAL-004 第 8 call schema invalid 共同证明旧版本具有偶发性；单次
  baseline 不能恢复历史 claim，也不能作为 24/24 稳定性门禁。

### D2：Candidate 1 与 stability freeze

- 唯一变化为单包专用 prompt、精确一个 opinion 和完整合法 JSON 示例；模型、
  `json_object`、thinking disabled、non-streaming、1500 tokens 与 validator 语义未放宽。
- Candidate 1 prompt SHA
  `6a29b74fc890c3492e1658c08144a875325dbf11acd9012883d80e1e9d0f1bcc`；qualification
  12/12（result SHA `5ab709bec35cf5dab45b85f4a4feb629520697d7098374a2d0db4fcaa47229e4`），
  confirmation 12/12（result SHA
  `97c19a15822cf5f4d319a9f862b01cb7b26d7f0828f686883d8ae3f66402f3bc`）。
- 新冻结 schema version 为 `track-b-role-candidate-anchor-abstention-v2`，request builder
  version 为 `track-b-single-packet-provider-request-builder-v2`；24/24 accepted、0 rejected，
  总诊断 36/60，未创建 Candidate 2。
- stability freeze SHA
  `44468d84327f001f50bcbf1f38d2e63d30b750227d7c00b3cde0f1bca1e16bd5`；
  `fifthCorpusCreationAllowed=true`，但 `formalAdmissionAffected=false`、A0/A1/A2 仍禁止。
- 精确 Node tests `5/5 PASS`；实际 KEY 泄漏文件数 0，artifact 中 raw Provider 字段
  匹配数 0。

### E1：第五套 corpus 与人工 challenge

- source SHA
  `d8dd7df34991c97773fa96ef8efeb2bc11c3d55e36ddf87715fba8d4861cda15`，
  proposal SHA
  `a5dd576ecf7c4e6c19d41da3d5dde51985f9fe018db981f0446d0c774aa1d914`。
- corpus SHA
  `17343dd34820c847676ed681a8a4e6ee9f669c60cf3c0257aa2363ba4bba8f5e`，
  review draft SHA
  `0ab07f6b2c21640b18a9c6570432066499ca9e946548d56290d9e9f2a41a0a93`，
  review document SHA
  `3c3ff92893757de42c134fa1747b018a67d76f283b52b28c64ef51244d62e2ae`，
  preseal manifest SHA
  `b49a6e8ac5cdde971c9a41d19f469e77d4880efbb4f19985e6d085870dcc7b28`。
- 相对前四套 corpus 及 schema diagnostic input 的 task/execution/packet/sample
  identity、candidate values、evidence texts overlap 全部为 0；5 MEDIUM + 4
  CONFLICTED eligible、3 zero-call controls。
- challenge `TB51-5deddd744f6f432f8f7760bd51899e16`，SHA
  `f934a0e8bc8707690d5039523c256d7c78c3996b489132b8945c24f586aa8751`，
  到期 `2026-08-05T05:59:55.260Z`。
- 当前 `modelInputCreated=false / evaluatorAccessAllowed=false /
  admissionNetworkCallAllowed=false`；E1 精确回归连同诊断 suite 为 `8/8 PASS`。

### E2：人工封印、dispatch 与 Codex 盲评

- 项目负责人绑定 challenge SHA `f934a0e8bc8707690d5039523c256d7c78c3996b489132b8945c24f586aa8751`、
  corpus SHA `17343dd34820c847676ed681a8a4e6ee9f669c60cf3c0257aa2363ba4bba8f5e`、
  review SHA `0ab07f6b2c21640b18a9c6570432066499ca9e946548d56290d9e9f2a41a0a93`
  接受全部 12 条 proposedExpected；decisions SHA `4d468dc3f3a91441d735e2fac2dda0ede8dfb0fd43a29a2e52b4d84cdba6bdc6`、
  confirmation SHA `bdb56738768b7b5ca01a413a8a58d45d0f1252818ad2138399c76f3bc7ebdf37`、
  human seal SHA `85391e4573911f8e0147eb2ff2dda4908e79afe8029190d1c97fa2b73a96e4e3`。
- formal dispatch 绑定 model input `e1516fb0d395b075b0649be0a5ef7e17334171598f01d832a25109de8601045b`、
  provider call set `365e17222248e180cb07492ad36d85d0825182dfb6b4d0c9ceb7ab3d0e9e3a04`、
  dispatch `31e9d71842d31a6d512d78def3a798cd3dbc483b656cebe20bf25fc2acd3ae42`、
  derived receipt `6e9f69550dec070a121e759bf208d61f4fc6c062d8674c50a0805cb7db1197e8`；
  9 calls/9 eligible/3 controls excluded，无网络。
- 全新 `fork_turns=none` Codex blind evaluator 返回 9/9 strict accepted；opinion SHA
  `95ecd582e4579b8539c84db553fc61a609f8e83a117ccd6ec59efe928d9766fe`、receipt SHA
  `71e692646bc5fde29913a06369c6caa59a82c2df6abde7bb127e1d58f9ea70de`。
  随后唯一正式 DeepSeek 9×1 执行全部 strict accepted，无自动重试；claim SHA
  `f9086db1b9b179c03a10bd83c05765f2a54d82d59c177f33c0295d17625ec4f5`、opinion SHA
  `0e4f96fc65bcca8790ba6368e8bdf16f71ebdb92cecb7afe6b2b39170a5c1fc7`。

### E2 终态：解盲 NO-GO

- 两份 blind opinion 的合同、claim、调用 receipt 均在读取人工 seal 前验证通过；3 个
  controls 全部 zero-call，ground truth compatible。
- Codex 的 schema/reliable-anchor/role/candidate/anchor/abstention 全部 100%。DeepSeek
  schema 与 reliable-anchor 为 100%，但其在全部 4 个 CONFLICTED packet 上与人工答案
  不一致，role/candidate/anchor/abstention 各为 5/9（55.56%）。
- unblind report SHA `dbfe7d05e736dae3edd2bc495362d53caa0d2d2bfbc28662c438b53742c2465c`；
  admission seal SHA `520d7b38c46b0fc69bf2afb2726f01cadb1991b3b386d481c70b145274d451be`，
  状态 `SEALED_NO_GO_MODEL_MISMATCH`、Provider admission `NOT_ESTABLISHED`、
  `sameHoldoutRetryAllowed=false`。seal verify 重建通过。
- Phase-appropriate Node 回归 `15/15 PASS`，实际 KEY 泄漏文件数 0，禁止 Provider raw
  字段匹配数 0；未生成 freeze 或 audit 目录。
- 按 ADR-025 和项目负责人明确边界，本任务到此终止：不得重试、补跑、调 prompt、换
  模型/KEY、创建第六套，亦不得启动 A0/A1/A2 或成功路径三审。

## Next Task Handoff

- 本任务保持历史终态；下一执行任务为
  `tasks/active/TASK-EVAL-006-track-b-provider-conversation-recovery.md`。不得恢复或重试
  本任务 claim；A0/A1/A2 仍须等待新 admission 与三方全零 GO。
