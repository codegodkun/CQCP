# TASK-MVP-002：审核任务清单与左右审核工作台

状态：已实现 / 45568f… blocking causes 已整改 / 最终同 HEAD 证据与三审待重建

类型：Feature / API / Frontend / SourceAnchor 消费

Task Level：`L3 高风险治理`

Integration unit：`MILESTONE-MVP-002-CORE`

优先级：P0

负责人：Codex

创建日期：2026-07-28

来源：`CQCP-TASK-MVP-002-handoff-20260728.md`、`docs/ARCHITECTURE.md` 第 14 节、ADR-015、ADR-020

## 背景

现有 Demo 已能上传合同并查看结果，但缺少按 execution 展示的任务清单、合同原文与审核点并排核对能力。现有正式结果 URL 携带 `executionId`，结果查询却读取同 task 最新快照，存在历史 execution 身份错配风险。

## 目标

* 提供 execution 级任务清单和稳定分页、搜索、状态分组。
* 精确按 `taskId + executionId` 查询历史结果，不回退到 latest。
* 只读消费 parser-issued identity 与既有 `SourceAnchor`，实现左右工作台定位和原始 DOCX 下载。

## 非目标

* 不修改 anchor 生成、CandidateResolver、EvidenceSlot、Finding/SYS、审核状态机或最终裁判。
* 不持久化文档 preview，不引入 PDF/OCR、虚构页码、全文搜索定位或历史 parser 兼容层。
* 不把平台变成审批、拒绝或合同编辑系统。

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `docs/ARCHITECTURE.md` 第 14 节、22.6 节
* `docs/frontend.md`
* `docs/backend.md`
* `docs/database.md`
* `decisions/ADR-015-evidence-slot-source-anchor-governance.md`
* `decisions/ADR-020-review-workbench-management-access-boundary.md`

### Optional Context

* `tasks/done/TASK-MVP-001-contract-review-user-loop-demo.md`
* `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`

### Out of Scope

* 本父 TASK 不实现 `TASK-036` consistency-set runtime；当前 Milestone worktree
  中另由 TASK-036 C1/C2/D1/D2 承接，不能算作本工作台父 TASK 的实现范围。
* `TASK-028 / TASK-031 / TASK-032`。
* Model Provider 激活和真实合同外发。

## 范围

### 包含

* `GET /api/review/tasks`
* `GET /api/v1/tasks/{taskId}/result?executionId=...`
* `GET /api/review/tasks/{taskId}/executions/{executionId}/document-preview`
* `GET /api/review/tasks/{taskId}/executions/{executionId}/document`
* `/review/tasks` 与 `/review/results/{taskId}?executionId=...`

### 不包含

* 新数据库表或 parser/anchor identity 迁移。
* 按 evidence text、candidate value 或模糊文本搜索反向定位。

## 约束

* 每条任务列表记录必须代表一个 execution，统计、状态、stage 与模型绑定均来自同一 execution。
* 状态组固定为 `PROCESSING`、`COMPLETED`、`FAILED`。
* preview 仅在 execution 的 `parserVersion` 与当前 parser release 精确一致时按需重解析，否则 fail closed。
* 文件读取必须验证 task/execution 归属、规范化路径、存储根目录、普通文件和上传 SHA-256；跨 task、路径穿越、symlink/reparse point 和 CR/LF 文件名必须拒绝。
* React 只以文本节点渲染合同内容。
* execution 清单、parser-backed preview 与原始 DOCX 下载必须由后端验证
  Management Bearer；read-only token 只能读取工作台，不能访问 `/api/admin/**`。
  token 不得进入 URL、页面持久化存储或证据文件。
* `BLOCK_LEVEL` 明示降级；`UNAVAILABLE` 不提供伪跳转。
* 发生需要持久化 preview、改变 anchor identity 或状态机的需求时停止并另建 ADR。

## 交付物

* 后端查询、preview 和下载 API。
* execution-aware 结果查询。
* 审核任务列表和左右工作台页面。
* OpenAPI、单元/集成测试、Compose 与浏览器验收证据。

## 可证伪验收断言

1. 同一 task 有两个 execution 时，带旧 `executionId` 的结果查询返回旧 execution 快照；不存在的 execution 返回 404，绝不返回 latest。
2. 清单分页在相同排序键下稳定；搜索值通过参数绑定，不拼接 SQL；每行统计来自该行 execution。
3. 非当前 parserVersion 的 preview 请求返回稳定的 409/不可预览错误且不重解析。
4. 下载内容 SHA-256 与 task 上传时保存的 SHA-256 一致。
5. 构造跨 task 文件引用、`..`、reparse point 或含 CR/LF 文件名时，API 拒绝读取。
6. 恶意 `<script>` 合同文本在浏览器中显示为文本且不执行。
7. 点击有 `previewElementRef` 的 anchor 定位精确元素；仅有 `blockId` 时以块级定位并显示提示；无 anchor 时无跳转控件。
8. 未认证清单、preview 和下载在进入 service 前返回稳定 `401`；read-only token
   可以读取工作台但访问 Admin API 返回 `403`。
9. Admin/工作台路径加入任意 segment matrix parameter 后仍经过同一 Bearer 门禁；
   极大 `page * size` offset 组合返回稳定 `400` 而不是 `500`。
10. 任务清单、preview 与 download 的匿名 HEAD（含 matrix-parameter 变体）在进入
    service 前返回 `401`，不得暴露下载响应头或清单元数据。

## 测试与验证

* Gradle 定向测试、全量测试、`bootJar`。
* admin-web Vitest、lint、build。
* OpenAPI YAML/JSON 一致性。
* Compose PostgreSQL 真实上传、清单、终态、定位和下载浏览器验收。

## 回滚边界

* 删除新增只读 API 与前端路由即可回滚；不改 V1/V2 数据结构和核心审核语义。

## 文档更新要求

* 在 Milestone 收口更新 `CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-07.md`、`docs/frontend.md`、`docs/backend.md`。
* 冻结 diff 后执行 CC AUDIT 与两个全新 Codex 独立只读审计。三方 raw
  audit 必须符合 `task-mvp-002-final-audit-raw-v1`，由工具链逐字段绑定
  `decision/counts/checks/findings/conclusion` 到 execution receipt 与规范
  report；dispatch/receipt 使用仓库外 Ed25519 private key 和冻结 public key
  验证 root orchestrator attestation；聚合前重新验证冻结闭包，不能只接受自述
  GO、非空文本或未签名的本地回执。

## 风险

* 文件归属校验不足可能导致合同越权读取。
* execution 身份错配可能导致历史结果串读。
* preview 重解析可能因 parser 漂移制造伪定位。

## 待确认

无。

## 完成记录

* 实现范围：execution-aware result query、execution 级任务清单、parser-backed
  preview、原始 DOCX 下载、左右审核工作台、OpenAPI、前后端测试与模块文档。
* 身份与定位：带 `executionId` 的结果查询精确匹配，不回退 latest；定位只消费
  parser-issued block/cell identity 与 `sourceAnchors[]`，支持多 anchor、
  `BLOCK_LEVEL` 与 `UNAVAILABLE`，不按证据文本反向搜索。
* 文件安全：跨 task 引用、CR/LF、路径穿越、symlink/reparse point、size/SHA 均
  fail closed。Linux 使用 `SecureDirectoryStream`；Windows 使用拒绝 reparse 且
  no-share-delete 的原生目录/文件句柄。Windows directory junction 的 read/download
  正例与 service parser-zero-call 证据已补齐。
* 浏览器与 Compose：真实 DOCX 上传、execution 清单、SUCCESS 终态、定位、认证下载
  SHA 与恶意合同文本按 React 文本节点渲染均已有可重建验证路径；`outputs/**`
  只作为 hash-bound 派生证据，不进入 Core source diff。
* 2026-07-31 Core 收敛：本任务与 TASK-MODEL-001、已完成 M3 证据、TASK-034 R7、
  TASK-036 seam 组成先行 integration unit。Provider A0、Track B 新 holdout、A1/A2
  不进入本 Core diff。
* 2026-07-31 审计整改：Worker DOCX snapshot、current-HEAD R7、浏览器原始事件/
  DOM、Compose transport 计数、Provider-free Core 边界和任务叙事冲突均已修复；
  旧 Track B v2 evidence 改用 hash-equivalent 历史 fixture 验证，不覆盖当前 R7。
  最终 candidate 的 Compose/browser、完整验证、freeze 与三审仍须从零重建。
* 2026-07-31 第二轮冻结 `ea19a52a…` 已由 CC AUDIT 与两个全新 Codex auditor
  从零审计并统一 `NO_GO`。六项 finding 为 occurrence `contextType` provenance
  丢失、Core app 全路径默认放行与 Provider 排除常量、Compose provenance 使用
  错误 override、D1/D2/backend 原始 JUnit 被覆盖、D1/D2 计数文档滞后及
  `CURRENT_CONTEXT.md` 叙事滞后。该 freeze 与 verification 已显式封存为
  invalidated 历史证据；主 Codex 仅修复这六项，不启动 Provider。
* Integration unit：`MILESTONE-MVP-002-CORE`。
* 独立审计触发依据：SourceAnchor、API、安全与历史 snapshot 正确性均属于 L3。
