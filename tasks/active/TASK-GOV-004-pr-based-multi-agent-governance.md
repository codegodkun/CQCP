# TASK-GOV-004：PR 化多 Agent 开发治理与机制化门禁

状态：Active（Phase 4：手动独立 Code Review + Spec & Docs Review 规格已准备，尚未执行审查，未归档）

类型：Governance

优先级：高

负责人：Codex 总控

创建日期：2026-06-23

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、用户提供的《CQCP 基于 PR 的多 Agent 开发治理方案与实施计划 v2》

## 背景

当前 CQCP 的 v3 角色分离与证据门禁已经形成文档规则，但仍依赖 Codex 遵守、用户判断和独立 agent 审计。尚未通过 GitHub branch protection、required status checks、真实 CI 与 PR 拦截记录形成机制化硬门禁。

本任务用于把 PR 化治理方案 v2 转为项目可追踪任务，逐步建立 `PR + CI + 独立审查 + 机制化门禁` 的治理管道。

## 当前 Governance Mode

当前只能标注为：

```text
LEGACY_MANUAL
```

不得写为：

```text
PR_MANUAL_REVIEW
PR_REQUIRED_CHECKS
```

不得使用“PR 治理已启用”这类笼统措辞。

原因：

* 本轮不能证明 PR 流程、CI、独立审查回写和 GitHub 机制化门禁已经形成闭环。
* 未达到 Phase 5 全部机制化验收证据前，不得写 `PR_REQUIRED_CHECKS`。
* 文档规则不是机制门禁。
* 以上仅是当前现状基线，不代表 `TASK-GOV-004` Phase 1 已实施。

## 当前只读证据

建档前只读核查结果：

* `gh` CLI 已安装并完成登录，具备基础只读能力。
* `gh repo view` 本轮存在 EOF 传输不稳定，因此只能作为有限证据，不能单独作为最终结论依据。
* 主仓库 `git status --short` 为空，工作区干净。
* 主仓库 `git status -sb` 为 `## master...origin/master`。
* `git rev-list --left-right --count origin/master...HEAD` 为 `0 0`，本地 `master` 与 `origin/master` 对齐。
* `master` 未启用 branch protection；相关 API 返回 `404 Branch not protected`。
* repository ruleset 当前按“未配置或无证据”处理。
* GitHub Actions workflows 数量为 `0`。
* check-runs 数量为 `0`。
* commit statuses 返回 `state: "pending"` 且 `statuses / total_count` 为空；这不能证明存在实际 status、pending check 或 required check。
* default branch 尚未被 PR / review / checks 机制强制保护。
* 当前仍不能采信 branch protection、ruleset、required status checks、direct push 拒绝或测试 PR 拦截已经生效。
* 以上仅是当前现状基线，不代表 `TASK-GOV-004` Phase 1 已实施。

## 目标

* 建立 `PR + CI + 独立审查 + 机制化门禁` 的治理管道。
* 将治理状态显式分档为 `LEGACY_MANUAL / PR_MANUAL_REVIEW / PR_REQUIRED_CHECKS`。
* 将 PR 化治理方案 v2 的实施路径写入项目任务系统，形成可追踪、可验收、可审计的阶段任务。

## 非目标

* 不替代五类问题整改 v3。
* 不修复已知代码缺陷。
* 不修改 fixture。
* 不修改 expected JSON。
* 不修改 ADR / PRD。
* 不进入 `TASK-EVAL-001-B`。
* 不归档 `TASK-EVAL-001`。
* 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 不修改五类问题整改 v3 的既有门禁。

## 输入

* 相关文档：`CURRENT_CONTEXT.md`
* 相关任务地图：`tasks/MVP_TASK_MAP.md`
* 上游治理任务：`tasks/done/TASK-GOV-003-five-class-remediation-and-role-gates.md`
* 外部方案：`C:\Users\1\Downloads\CQCP-PR治理方案-v2.md`

## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* 本任务包
* `tasks/MVP_TASK_MAP.md`
* `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`

### Optional Context

* `tasks/TEMPLATE_ROUTER.md`
* `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`
* `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
* `changelog/2026-06.md`

### Out of Scope

* 业务代码
* 测试代码
* fixture
* expected JSON
* OpenAPI
* 数据库迁移
* Docker / Compose
* `PRD.md`
* `docs/ARCHITECTURE.md`
* `decisions/ADR-*`
* GitHub branch protection / ruleset 配置

## 范围

### 包含

* 建立本治理父任务。
* 冻结当前 Governance Mode 基线。
* 记录 Phase 0 至 Phase 6 的实施顺序。
* 记录 Phase 5 机制化门禁验收证据清单。
* 记录 `CQCP / CQCP-work / CQCP_AUDIT` 的目录结构口径。
* 记录后续审计命令必须在被审计 clone 中执行。

### 不包含

* 不创建 PR 模板。
* 不修改 GitHub 设置。
* 不创建或移动本地目录。
* 不派发 Claude Code / DeepSeek 实现任务。
* 不由 Codex 充当 Code Review Agent 或 Spec & Docs Review Agent。

## 目录结构口径

当前采用以下目录结构口径：

```text
C:\Users\1\Documents\CQCP
C:\Users\1\Documents\CQCP-work
C:\Users\1\Documents\CQCP_AUDIT
C:\Users\1\Documents\CQCP_AUDIT\CQCP
```

定义：

* `C:\Users\1\Documents\CQCP` 是主仓库。
* `C:\Users\1\Documents\CQCP-work` 是未来执行 agent 工作区，当前尚未建立。
* `C:\Users\1\Documents\CQCP_AUDIT` 是审计环境根目录。
* `C:\Users\1\Documents\CQCP_AUDIT\CQCP` 是被审计 git clone。
* 当前结构可以接受：外层 `CQCP_AUDIT` 承载 `.claude` 配置，避免与仓库 clone 根目录冲突。
* 不得使用 `git worktree` 创建 `C:\Users\1\Documents\CQCP_AUDIT\CQCP`；该目录必须保持为独立 `git clone`。
* `audit-scratch` 建议放在 `C:\Users\1\Documents\CQCP_AUDIT\audit-scratch`，不放进 `C:\Users\1\Documents\CQCP_AUDIT\CQCP`，以避免污染被审计 clone 的工作区。

后续审计提示词必须使用以下执行目录与命令：

```powershell
cd C:\Users\1\Documents\CQCP_AUDIT\CQCP
git status --short
git status -sb
git fetch origin
git log -1 --format="%H %ci"
```

仍需只读核实：

* `C:\Users\1\Documents\CQCP_AUDIT\.claude\settings.json` 是否实际限制写入。
* `.claude/settings.json` 是否实际限制 `git add`、`git commit`、`git push`、`git merge`、`git rebase`、`git reset` 等 git 写操作。
* 审计临时写入是否仅允许进入外层 `C:\Users\1\Documents\CQCP_AUDIT\audit-scratch`。

## Phase 0-6 实施顺序

### Phase 0：治理状态基线与任务边界冻结

目标：

* 明确当前 Governance Mode 为 `LEGACY_MANUAL`。
* 明确 PR 化治理方案 v2 尚未形成机制门禁。
* 冻结本任务目标、非目标、允许文件与禁止文件。
* 记录当前只读证据。

验收：

* 本任务文件存在。
* `CURRENT_CONTEXT.md`、`changelog/2026-06.md`、`tasks/MVP_TASK_MAP.md` 记录本任务状态与边界。
* 未修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、ADR、PRD、`docs/ARCHITECTURE.md`、`.github/workflows` 或 GitHub 设置。

### Phase 1：目录结构与审计环境核实

目标：

* 核实 `CQCP`、`CQCP-work`、`CQCP_AUDIT` 的实际路径与职责。
* 确认 `CQCP_AUDIT\CQCP` 是独立 git clone，不是 worktree。
* 确认审计命令在 `CQCP_AUDIT\CQCP` 执行。
* 只读核实 `.claude/settings.json` 权限边界。

验收：

* 输出 `CQCP_AUDIT\CQCP` 的 `git status --short`、`git status -sb`、`git fetch origin`、`git log -1 --format="%H %ci"`。
* 输出 `CQCP_AUDIT\.claude\settings.json` 的权限核查结论。
* 未创建、移动或删除目录。

### Phase 2：Draft PR 流程

目标：

* 让后续任务通过工作分支与 Draft PR 进入审查。
* PR 描述包含 Review Baseline、Governance Mode、允许文件、禁止文件、测试证据和 expected / fixture 来源说明。

验收：

* 至少一个测试性 Draft PR 流程可被真实创建和审查。
* 在未进入 Phase 5 前，只能标注为 `PR_MANUAL_REVIEW` 的候选阶段，不得写 `PR_REQUIRED_CHECKS`。

### Phase 3：基础 GitHub Actions CI

目标：

* 添加基础 CI workflow，先覆盖标准构建与测试命令。
* CI 不调用模型 API，不承担 Code Review 或 Spec & Docs Review 判断。

验收：

* CI workflow 文件存在并通过真实 PR 运行。
* 有 GitHub Actions 运行记录。
* CI 失败时不能建议合并。

本轮执行边界（2026-06-27）：

* 新增 `.github/workflows/ci.yml` 作为最小 CI workflow。
* backend job 使用 Java 21、`gradle/actions/setup-gradle@v4`、Gradle 8.10.2 和 PostgreSQL 16 service，执行 `apps/api-server` 下的 `gradle test`。
* admin-web job 使用 Node.js 24、根目录 `npm ci`，执行 `npm run lint:admin-web`、`npm run test:admin-web`、`npm run build:admin-web`。
* CI 不配置模型 API、secrets、Code Review Agent、Spec & Docs Review Agent、branch protection、ruleset 或 required checks。
* 在真实 PR 运行记录出现前，不得写成 CI 已通过；在 Phase 5 全部证据满足前，不得写成 `PR_REQUIRED_CHECKS`。

Post-merge 事实记录（2026-06-27）：

* PR #5 已 merged。
* merge commit：`455d2e3b7a4d8397087deb127a649a6f92aa19a0`。
* PR head commit：`50f0befadbd17e7ea80cc2a9d90d38365753f4de`。
* PR #5 final head GitHub Actions run `28288707273` completed / success。
* `Backend Gradle tests`：success。
* `Admin web lint, tests, and build`：success。
* Phase 3 minimal GitHub Actions CI 已落地。
* Governance Mode 仍为 `LEGACY_MANUAL`。
* `PR_REQUIRED_CHECKS` 尚未生效。
* branch protection / required checks / ruleset 未配置。
* `TASK-GOV-004` 未归档。
* 未进入 `TASK-028` / `TASK-031` / `TASK-032`。
* PR #4 的历史 CI 豁免不得追溯写成 CI PASS。

### Phase 4：手动独立 Code Review + Spec & Docs Review

目标：

* 在 `CQCP_AUDIT` 审计环境中手动触发独立审查。
* Code Review 与 Spec & Docs Review 必须满足模型厂商独立性和真实命令执行能力要求。

验收：

* 审查结论统一为 `GO / NEEDS-FIX / NO-GO`。
* 审查结论回写到 PR 评论或后续可转为 Check Run / Commit Status。
* Codex 不得充当 Code Review Agent 或 Spec & Docs Review Agent。

Phase 4 规格（2026-06-27 准备，尚未执行）：

* 触发条件：
  * 仅在已有 PR、明确 head commit、CI run 结果和授权的审查范围后触发。
  * 审查对象必须限定为 PR diff、相关任务文件、必要上下文和真实命令输出；不得要求审查 agent 修改文件、提交、push、merge 或配置 GitHub 设置。
  * 对业务代码变更、治理文档变更和归档 PR 均可触发 Phase 4，但每次必须单独声明允许文件、禁止文件、审查基线和目标结论。
* 审查环境：
  * 审查命令必须在 `C:\Users\1\Documents\CQCP_AUDIT\CQCP` 被审计 clone 中执行。
  * 临时记录仅允许写入审计环境外层 scratch 区；不得污染被审计 clone 工作区。
  * 审查前后必须记录 `git status --short`、`git status -sb`、`git rev-parse HEAD` 和必要的 PR / CI 只读查询结果。
* Code Review 证据：
  * 覆盖 PR diff 是否只修改授权文件。
  * 覆盖实现或文档是否满足任务验收断言。
  * 覆盖是否存在范围外修改、完成态夸大、缺失测试、证据不足或与治理边界冲突。
  * 如涉及代码，必须列出实际运行或核验的测试命令、结果和失败归因；不得用文档自述替代命令证据。
* Spec & Docs Review 证据：
  * 覆盖 `AGENTS.md`、当前 TASK、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/` 中相关状态是否一致。
  * 覆盖 `Governance Mode`、`PR_REQUIRED_CHECKS`、branch protection / ruleset / required checks 等治理状态是否被准确表述。
  * 覆盖是否错误追溯 PR #4 CI 状态、是否错误归档任务、是否提前进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 结论格式：
  * `GO`：无阻塞问题，可进入用户决策或下一授权动作。
  * `NEEDS-FIX`：存在必须修正项；必须列出文件、位置、事实依据和最小修正建议。
  * `NO-GO`：事实链、范围边界或验证证据不成立；不得合并、归档或进入下一阶段。
  * 可附 `NON-BLOCKING` 项，但不得用 non-blocking 掩盖完成态夸大、范围外修改或证据缺失。
* 回写方式：
  * Phase 4 当前仅允许手动回写到 PR 评论、审计报告摘要或任务记忆文档；尚不发布 GitHub Check Run 或 Commit Status。
  * 回写必须包含审查对象、head commit、CI run、结论、阻塞项、non-blocking 项和审查环境状态。
  * Codex 只能基于独立审查结果给出 Review Intake Decision；Codex 不得把自身检查冒充独立 Code Review 或 Spec & Docs Review。
* 治理边界：
  * Phase 4 规格准备不代表 Phase 4 已执行。
  * Phase 4 手动审查不代表 branch protection、ruleset、required checks 或 `PR_REQUIRED_CHECKS` 已生效。
  * 进入 Phase 5 前仍必须取得机制化门禁验收证据清单中的全部证据。
  * 当前 Governance Mode 仍为 `LEGACY_MANUAL`。

### Phase 5：Protected Branch + Required Checks

目标：

* 将审查结论从文档规则升级为 GitHub 机制化门禁。
* 正式进入 `PR_REQUIRED_CHECKS` 前必须取得完整证据。

机制化门禁验收证据，缺一不可：

* branch protection / ruleset 真实配置证据。
* required status checks 名称与 source。
* CI workflow 真实运行记录。
* Code Review / Spec & Docs Review Check Run 或 Commit Status。
* 测试 PR 被 required checks 阻止合并的证据。
* direct push 被拒绝的证据。
* 管理员 bypass 状态说明。

未满足上述全部证据前，不得写 `PR_REQUIRED_CHECKS`。

### Phase 6：归档流程 PR 化

目标：

* 任务归档必须经过 PR、CI、独立审查和用户批准。
* `tasks/active -> tasks/done`、`CURRENT_CONTEXT.md`、`changelog/`、`tasks/MVP_TASK_MAP.md` 的状态变更必须进入 PR 审查。

验收：

* 归档 PR 中明确 CI、Code Review、Spec & Docs Review、Governance Mode、用户批准和归档建议。
* 文档状态不得先于真实证据写成 Done / Archived / Completed。

## 约束

* 文档规则不是机制门禁。
* 未达到 Phase 5 全部证据前，不得写 `PR_REQUIRED_CHECKS`。
* Codex 不得充当 Code Review Agent 或 Spec & Docs Review Agent。
* CQCP_AUDIT 独立审计必须基于真实命令、commit、PR、CI、GitHub 设置证据。
* 不混入 `TASK-EVAL-001-B`、`TASK-028`、`TASK-031`、`TASK-032`。
* 不修改五类问题整改 v3 的既有门禁。
* 不把 `CURRENT_CONTEXT.md` 或本任务文件中的自述当作机制生效证据。
* 不得在未取得用户明确授权时 stage、commit、push、merge 或修改 GitHub 设置。

## 交付物

* `tasks/active/TASK-GOV-004-pr-based-multi-agent-governance.md`
* `CURRENT_CONTEXT.md` 中的活跃任务与阻塞项摘要
* `changelog/2026-06.md` 中的建档记录
* `tasks/MVP_TASK_MAP.md` 中的任务地图更新

## 验收标准

* 任务文件完整记录当前 `LEGACY_MANUAL` 状态和证据边界。
* 明确 Phase 0-6 实施顺序。
* 明确 Phase 5 机制化门禁证据清单。
* 明确 `CQCP_AUDIT` 实际路径口径。
* 未修改禁止文件。
* `git diff --check` 通过。

## 测试与验证

本任务当前进入 Phase 3，新增基础 GitHub Actions CI workflow。业务代码、测试代码、fixture 和 expected JSON 不在本轮修改范围内。

必须执行的验证：

* `git diff --name-status`
* `git diff --stat`
* `git diff --check`
* `git status --short`
* 本地可行时运行 backend / admin-web 对应命令；如因既有环境依赖失败，必须记录为待 PR CI 验证或既有环境问题，不得写成通过。

## 文档更新要求

* 是否需要更新 `CURRENT_CONTEXT.md`：是。
* 是否需要更新 `docs/*.md`：本轮不需要。
* 是否需要更新 `changelog/当前月份.md`：是。
* 是否需要新增或更新 ADR：否。本任务只治理开发流程，不改变核心审核链路、模型职责、SYS/Finding 边界、EvidenceSlot、ReviewPointFamily 或 CandidateResolver。

## Next Task Handoff

下一步：如获用户授权，仅可围绕 `TASK-GOV-004` Phase 4 规格执行一次手动独立 Code Review + Spec & Docs Review 试运行；不得配置 GitHub branch protection、ruleset 或 required checks，不得写 `PR_REQUIRED_CHECKS` 已生效，不得归档 `TASK-GOV-004`，不得进入 `TASK-028` / `TASK-031` / `TASK-032`。

## 风险

* GitHub API 当前无法核实配置，不能证明机制化门禁状态。
* `gh` CLI 不可用会阻碍 Phase 5 证据采集。
* 如果后续只写文档、不配置 GitHub 规则，治理仍停留在 `LEGACY_MANUAL` 或最多 `PR_MANUAL_REVIEW`，不能写 `PR_REQUIRED_CHECKS`。
* `CQCP_AUDIT` 的 `.claude/settings.json` 仍需只读核实，不能仅凭目录存在采信其限制已生效。

## 待确认

* 是否将外部 `C:\Users\1\Downloads\CQCP-PR治理方案-v2.md` 正式纳入 `docs/governance/`。
* 是否安装或启用 `gh` CLI，供 CQCP_AUDIT 后续使用 `gh api` 核实 GitHub 设置。
* Phase 3 的最小 CI 命令矩阵已在 `.github/workflows/ci.yml` 中落地，并已通过 PR #5 final head GitHub Actions run `28288707273` 验证后合并落地；这不代表 branch protection、required checks、ruleset 或 `PR_REQUIRED_CHECKS` 已生效。
* Phase 4 手动独立审查的实际试运行 PR、审查 agent 类型和回写载体。
* Phase 5 的 required checks 命名、source 和发布方式。

## 完成记录

* 完成日期：2026-06-27（Phase 3 workflow 文件新增；PR #5 已合并，minimal GitHub Actions CI 已落地；Phase 4 手动独立审查规格已准备但尚未执行；`TASK-GOV-004` 未归档）
* 变更文件：`.github/workflows/ci.yml`、`tasks/active/TASK-GOV-004-pr-based-multi-agent-governance.md`、`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、`changelog/2026-06.md`
* 测试结果：
  * `git diff --check`：通过（仅有 Git CRLF warning，无 whitespace error）。
  * `npm.cmd run lint:admin-web`：通过。
  * `npm.cmd run test:admin-web`：通过，1 个 test file / 6 tests passed；沙箱内曾因上级目录读取权限失败，非沙箱重跑通过。
  * `npm.cmd run build:admin-web`：通过；存在 Vite chunk size warning，不作为失败。
  * `gradle test`：本地失败于既有 `CqcpApiServerApplicationTests.contextLoads` PostgreSQL hostname / 本地数据库环境问题；本轮 CI workflow 已为 GitHub runner 配置 PostgreSQL 16 service 与 `CQCP_DB_URL=jdbc:postgresql://localhost:5432/cqcp`。
  * PR #5 首轮 GitHub Actions backend job 失败于 runner 默认 Gradle 9.6.0 下 JUnit Platform launcher 缺失；workflow 已改为显式使用本地验证过的 Gradle 8.10.2。
  * PR #5 第二轮 GitHub Actions run `28277974535`：`Backend Gradle tests` 成功，`Admin web lint, tests, and build` 成功，workflow conclusion 为 `success`。
  * PR #5 final head GitHub Actions run `28288707273`：`Backend Gradle tests` 成功，`Admin web lint, tests, and build` 成功，workflow conclusion 为 `success`。
  * PR #5 已 merged；merge commit 为 `455d2e3b7a4d8397087deb127a649a6f92aa19a0`，PR head commit 为 `50f0befadbd17e7ea80cc2a9d90d38365753f4de`。
* 遗留问题：Phase 4 手动独立审查尚未试运行；尚未配置 branch protection、ruleset 或 required checks；Governance Mode 仍为 `LEGACY_MANUAL`，`PR_REQUIRED_CHECKS` 尚未生效；`TASK-GOV-004` 未归档，未进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 备注：本轮只新增基础 CI workflow 与项目记忆记录，不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、ADR、PRD 或 GitHub 设置。
