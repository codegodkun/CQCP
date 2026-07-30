# CURRENT_CONTEXT.md

更新时间：2026-07-31

## 当前阶段

当前处于 `MILESTONE-MVP-002-CORE` 收口阶段，唯一基线为
`origin/master@1035739b751386176e47c6871738a62bff86de02`。

先行 Core integration unit 包含：

- M1：execution 任务清单、精确结果查询、parser-backed preview、原始 DOCX 下载和
  左右审核工作台；
- M2：immutable Model Profile config、server-side Secret Reference、endpoint
  allowlist、readiness/connectivity 与管理页；
- M3 已完成部分：Track A Codex/DeepSeek 盲评、Track B runtime-isomorphic seam、
  R7 zero-call 与旧 18 packet run-v3 `NO_GO_MODEL_MISMATCH` 证据；
- TASK-034 v29/R7 与 TASK-036 B1/B2/C1/C2/D1/D2 seam。

Provider A0、standing/CC 审计传输、A1 adapter、A2 shadow 和 guarded assist A3 均不
进入 Core source diff。PUBLIC profile 保持 `EVALUATION / disabled / unbound`，
普通 Demo 继续使用 `MVP_DEMO_MOCK`；模型不得直接改变 Finding/verdict。

## 已确认事实

- Windows document read/download directory junction、Secret root junction 和单一
  Secret sentinel 定向证据已补齐；最新定向组合为 34/34 通过。
- 单一 sentinel 测试扫描 HTTP body/header、异常、日志和数据库，并证明
  execution/Snapshot/stage/TuningPacket writer 在 connectivity 边界不可达。
- backend/CI 测试不再隐式依赖 `outputs/**`：盲评 source、Track B corpus、人工
  anchor workbook 与 D1 oracle 已迁入 `apps/api-server/src/test/resources/`。
  Node evidence validator 明确消费外部派生证据，并由 formal verification/freeze
  逐文件绑定 hash；`outputs/**` 不进入 Core source diff。
- Core-only 脚本 allowlist、Provider-free import closure、验证入口与冻结入口已建立；
  它们不执行模型公网调用，也不导入 Provider contract、standing grant 或 CC send
  lease。
- 旧 Track B run-v3 解盲结果为 Codex 15/15、DeepSeek 6/15、controls 3/3，
  `providerAdmission=NOT_ESTABLISHED`。同一已解盲 corpus 不得再次作为独立 admission。

## 当前活跃任务

- `tasks/active/TASK-MVP-002-review-workbench.md`
- `tasks/active/TASK-MODEL-001-model-profile-secret-readiness.md`
- `tasks/active/TASK-EVAL-002-blind-semantic-evaluation.md`
- `tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md`
- `tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md`

## 当前阻塞项

1. Core-only 完整验证、Compose/browser 重建和 immutable freeze 尚未在最终 candidate
   commit 上完成。
2. 同一 Core subject 的 CC AUDIT 与两个全新 `fork_turns="none"` Codex auditor
   尚未全部达到 `GO / P0=P1=P2=blocking=0`。
3. CI、PR 与 merge 尚未完成；没有主线 merge 就不能声明 TASK-036 seam 已集成。
4. 新 Track B admission holdout 尚未创建：固定 12 packet（9 eligible + 3
   controls），人工 ground truth 必须在模型访问前封印，正式运行只允许一次。

## 下一步

1. 形成 clean Core candidate commit；运行
   `scripts/mvp002/run-core-verification.ps1`。
2. 冻结 `freeze-core-audit-package.mjs` 生成的同一 subject，执行 CC AUDIT 与两个
   全新 Codex 独立审计。
3. 三审全零 GO 后 push、创建 PR、等待 CI；满足既有授权条件后 merge。
4. Core merge 后建立并执行一次新的 Track B holdout；失败即停止并重新收敛。

不得声明 Production Ready，不得宣称 TASK-028/031/032 已解锁。
