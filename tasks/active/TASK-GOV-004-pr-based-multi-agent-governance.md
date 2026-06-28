# TASK-GOV-004：PR 化多 Agent 开发治理与机制化门禁

状态：Active（Phase 5 第一阶段机制化门禁已配置并完成验证；未归档）

类型：Governance

优先级：高

负责人：Codex 总控

创建日期：2026-06-23

来源：`CURRENT_CONTEXT.md`、`tasks/MVP_TASK_MAP.md`、用户提供的《CQCP 基于 PR 的多 Agent 开发治理方案与实施计划 v2》

## 背景

当前 CQCP 的 v3 角色分离与证据门禁已经形成文档规则，但仍依赖 Codex 遵守、用户判断和独立 agent 审计。尚未通过 GitHub branch protection、required status checks、真实 CI 与 PR 拦截记录形成机制化硬门禁。

本任务用于把 PR 化治理方案 v2 转为项目可追踪任务，逐步建立 `PR + CI + 独立审查 + 机制化门禁` 的治理管道。

## 当前 Governance Mode

当前可标注为：

```text
PR_REQUIRED_CHECKS
```

不得写为：

```text
PR_MANUAL_REVIEW
```

不得使用“PR 治理已启用”这类笼统措辞；必须写明当前仅为第一阶段 CI required checks 机制化门禁。

原因：

* `master` 已配置 GitHub branch protection。
* 第一阶段 required checks 为 `Backend Gradle tests` 与 `Admin web lint, tests, and build`，source 为 GitHub Actions app `app_id: 15368`。
* required checks 采用 `strict: true`。
* 已启用 `required_pull_request_reviews`，`required_approving_review_count: 0`，用于强制变更通过 PR，不新增人工 approval 数量要求。
* 已启用 `enforce_admins: true`，并通过 direct push 测试确认普通 direct push 被拒绝。
* `CQCP Code Review` / `CQCP Spec & Docs Review` 的 Check Run 或 Commit Status 发布身份与 source 尚未确定，未纳入第一阶段 required checks。
* `TASK-GOV-004` 仍未归档；Phase 5 post-implementation 独立只读审计已返回 `GO`，当前仅可准备归档 PR。

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

Phase 4 外部独立审查试运行记录（2026-06-28）：

* 审查对象：`origin/master@3917848134543a2d99be44bfca6508f99d9a0bbf`（`docs(governance): prepare TASK-GOV-004 phase 4 review spec`）。
* 审查环境：`C:\Users\1\Documents\CQCP_AUDIT\CQCP`。
* Code Review：GLM 5.2，结论 `GO`；确认目标提交仅修改 4 个治理/文档文件，未修改代码、测试、workflow、OpenAPI、DB、Docker、ADR、PRD 或 GitHub 设置。
* Spec & Docs Review：GLM 5.2，结论 `GO`；确认四份文档一致表述 Phase 4 规格已准备、`LEGACY_MANUAL`、`PR_REQUIRED_CHECKS` 尚未生效、branch protection / required checks / ruleset 未配置、`TASK-GOV-004` 未归档、未进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 两份审查均记录真实只读 git / rg 命令与 console 输出，并声明未修改文件、未 stage、未 commit、未 push。
* Non-blocking：Code Review 报告发现 `CURRENT_CONTEXT.md` 下一步任务编号重复；本轮写回已修正。
* Codex Review Intake Decision：`GO`，Phase 4 外部独立审查试运行可视为完成。
* 治理边界：该 `GO` 不代表进入 Phase 5，不代表 branch protection、ruleset、required checks 或 `PR_REQUIRED_CHECKS` 已生效；Governance Mode 仍为 `LEGACY_MANUAL`。

### Phase 5：Protected Branch + Required Checks

目标：

* 将审查结论从文档规则升级为 GitHub 机制化门禁。
* 正式进入 `PR_REQUIRED_CHECKS` 前必须取得完整证据。

Phase 5 Review Intake Decision（2026-06-28）：

```text
GO TO SPEC PREP ONLY
```

判断：

* Phase 3 已具备最小 CI workflow 与真实 PR 运行记录，可作为 required checks 命名与来源评估的输入。
* Phase 4 已完成外部独立 Code Review 与 Spec & Docs Review 试运行 `GO`，可作为后续机制化审查信号设计的输入。
* 当前仍未配置 branch protection、ruleset 或 required checks；Review Intake 只允许准备 Phase 5 规格，不允许直接实施 GitHub 设置。
* 当前不得写 `PR_REQUIRED_CHECKS` 已生效；Governance Mode 仍为 `LEGACY_MANUAL`。
* 当前不得归档 `TASK-GOV-004`，不得进入 `TASK-028` / `TASK-031` / `TASK-032`。

Phase 5 规格准备范围：

* required checks 命名与 source。
* Code Review / Spec & Docs Review 状态发布方式。
* branch protection / ruleset 证据采集方式。
* 测试 PR 拦截证据要求。
* direct push 拒绝证据要求。
* 管理员 bypass 状态说明。

Phase 5 规格准备不包含：

* 不配置 GitHub branch protection。
* 不创建或修改 repository ruleset。
* 不设置 required status checks。
* 不发布 Check Run 或 Commit Status。
* 不创建测试 PR。
* 不执行 direct push 拦截测试。
* 不把 Governance Mode 写为 `PR_REQUIRED_CHECKS`。

#### Required Checks 命名与 Source 规格

第一批候选 required checks 仅来自已存在的 GitHub Actions CI job：

| required check name | source | 来源依据 | 当前状态 |
|---|---|---|---|
| `Backend Gradle tests` | GitHub Actions / `.github/workflows/ci.yml` | workflow job `backend.name` | 已在 PR #5 final head run `28288707273` 成功运行；尚未设为 required |
| `Admin web lint, tests, and build` | GitHub Actions / `.github/workflows/ci.yml` | workflow job `admin-web.name` | 已在 PR #5 final head run `28288707273` 成功运行；尚未设为 required |

后续机制化审查 required checks 仅作为 Phase 5 后续实施候选，当前不发布：

| candidate check name | source 要求 | 发布方式候选 | 当前状态 |
|---|---|---|---|
| `CQCP Code Review` | 待确认：仓库级自动化身份 / GitHub App / 明确可审计 token source，不得使用无法区分来源的个人手工状态作为 required source | GitHub Check Run 优先；Commit Status 仅作为备选 | 规格候选，未发布 |
| `CQCP Spec & Docs Review` | 待确认：仓库级自动化身份 / GitHub App / 明确可审计 token source，不得使用无法区分来源的个人手工状态作为 required source | GitHub Check Run 优先；Commit Status 仅作为备选 | 规格候选，未发布 |

约束：

* required checks 名称必须精确匹配 GitHub 实际 check run / status context，不能只写文档名。
* source 必须能通过 GitHub UI 或 API 取证；若 GitHub required check 支持限定 App source，则必须记录 App / source。
* 若后续使用 Commit Status，必须记录 `context`、`state`、`target_url`、发布者身份和 head SHA。
* 若后续使用 Check Run，必须记录 `name`、`conclusion`、`app`、`html_url`、head SHA 和发布时间。
* 独立审查信号在未形成可审计发布者身份前，不得列入 required checks。

#### Branch Protection / Ruleset 证据采集规格

Phase 5 实施后必须采集以下只读证据；当前仅定义采集方式，不执行配置：

```powershell
gh api repos/codegodkun/CQCP/branches/master/protection
gh api repos/codegodkun/CQCP/rulesets
gh api repos/codegodkun/CQCP/commits/{headSha}/check-runs
gh api repos/codegodkun/CQCP/commits/{headSha}/status
```

最低证据要求：

* branch protection 证据必须包含 required status checks / required pull request reviews / enforce admins 或等价字段。
* ruleset 证据必须包含 target branch、enforcement、rules、bypass actors 和 required checks 相关规则。
* 若采用 branch protection 与 ruleset 二选一，必须说明另一种机制为何不适用或未启用。
* 若 GitHub API 返回 403 / 404 / EOF / 空结果，必须记录为“待确认”或“无可采信证据”，不得写成已配置。
* 文档自述、`CURRENT_CONTEXT.md` 摘要或任务文件记录不得替代 GitHub API / UI 证据。

#### 测试 PR 拦截证据规格

Phase 5 实施后必须用测试 PR 或等价安全 PR 证明 required checks 能阻止合并；当前不创建测试 PR。

最低证据要求：

* 测试 PR 编号、base branch、head branch、head SHA。
* 至少一个 required check 未通过或 pending 时，GitHub UI / API 显示不可合并的证据。
* `mergeable_state`、branch protection / ruleset evaluation 或 PR checks 页面截图 / API 摘要。
* 修复或补齐 required check 后，PR 状态从 blocked 变为可合并的证据。
* 测试 PR 不得混入业务代码；建议只使用无害文档变更或专用治理验证分支。
* 测试 PR 可关闭不合并；是否合并必须另行取得用户授权。

#### Direct Push 拒绝证据规格

Phase 5 实施后必须证明 default branch direct push 被拒绝；当前不执行 push 测试。

最低证据要求：

* 使用专用临时分支或安全流程设计 direct push 拦截测试，避免污染 `master`。
* 记录执行者、命令、目标分支、被拒绝的原始错误输出。
* 不得通过真实业务变更测试 direct push 拒绝。
* 如管理员仍可 bypass，必须记录 bypass actor、条件、审计方式和为什么仍不能写成普通贡献者可绕过。

#### PR_REQUIRED_CHECKS 切换条件

只有同时满足以下条件，才能把 Governance Mode 从 `LEGACY_MANUAL` 切换为 `PR_REQUIRED_CHECKS`：

* branch protection 或 ruleset 真实配置证据已采集。
* required checks 名称与 source 已采集，且与实际 check run / status context 精确一致。
* CI required checks 已在真实 PR head SHA 上成功和失败路径均可观察。
* Code Review / Spec & Docs Review 已以 Check Run 或 Commit Status 形式发布，且 source 可审计。
* 测试 PR 被 required checks 阻止合并的证据已采集。
* direct push 被拒绝的证据已采集。
* 管理员 bypass 状态已说明。
* `CURRENT_CONTEXT.md`、本任务文件、`tasks/MVP_TASK_MAP.md`、`changelog/` 均一致记录证据引用与边界。

#### Phase 5 规格充分性审查（2026-06-28）

Codex Review Intake Decision：

```text
GO TO USER IMPLEMENTATION DECISION
```

含义：

* 现有 Phase 5 规格足以支持用户进入“是否授权后续实施”的决策。
* 该结论不等于实施授权，不等于配置 GitHub 设置，不等于发布 Check Run / Commit Status。
* 该结论不允许写 `PR_REQUIRED_CHECKS` 已生效；Governance Mode 仍为 `LEGACY_MANUAL`。

审查依据：

* 现有 CI workflow 的真实 job name 为 `Backend Gradle tests` 与 `Admin web lint, tests, and build`，与规格中的第一批 required checks 候选一致。
* GitHub required status checks 可以来自 checks 或 commit statuses；required checks 未全部通过前不能合入受保护分支。
* GitHub 支持为 required status check 指定 expected source；如果选择任意来源，必须额外人工核验状态来源。
* GitHub Check Run 写入需要 GitHub App；若当前没有 GitHub App，外部审查信号更现实的短期实现路线是 Commit Status，但其发布身份和 source 约束弱于 Check Run。
* GitHub ruleset 支持 bypass actors；branch protection 默认不约束管理员，除非启用不允许 bypass 或等价约束。

实施决策前必须由用户确认的事项：

1. 机制路线：使用 branch protection、repository ruleset，还是二者组合；如二者组合，需定义冲突时以哪个证据为准。
2. required checks 第一阶段范围：是否先只要求两个 CI checks，还是同时等待 `CQCP Code Review` / `CQCP Spec & Docs Review` 机制化状态发布能力。
3. 外部审查状态发布方式：短期采用 Commit Status，还是先准备 GitHub App 后再采用 Check Run。
4. 发布身份与 source：禁止使用无法审计的个人手工状态作为长期 required source；若临时使用，必须标记为过渡方案。
5. branch 更新策略：required checks 采用 strict 还是 loose；是否要求 PR branch 与 base 保持最新。
6. 管理员 bypass 策略：是否关闭 bypass，或保留并建立单独审计记录。
7. 测试 PR 策略：使用哪个无害文档变更、是否关闭不合并、谁授权创建与清理。
8. direct push 拒绝测试策略：如何避免污染 `master`，由谁执行，原始失败输出如何入库。

阻塞实施的开放项：

* `CQCP Code Review` / `CQCP Spec & Docs Review` 的最终发布身份、token source、运行宿主和报告 URL 尚未确定。
* 未决定第一阶段是否只把两个 CI job 设为 required checks。
* 未决定 branch protection 与 repository ruleset 的取舍。
* 未决定 admin bypass 策略。

结论边界：

* 可进入用户实施决策。
* 不可直接实施。
* 不可归档 `TASK-GOV-004`。
* 不可进入 `TASK-028` / `TASK-031` / `TASK-032`。

机制化门禁验收证据，缺一不可：

* branch protection / ruleset 真实配置证据。
* required status checks 名称与 source。
* CI workflow 真实运行记录。
* Code Review / Spec & Docs Review Check Run 或 Commit Status。
* 测试 PR 被 required checks 阻止合并的证据。
* direct push 被拒绝的证据。
* 管理员 bypass 状态说明。

未满足上述全部证据前，不得写 `PR_REQUIRED_CHECKS`。

#### Phase 5 第一阶段实施记录（2026-06-28）

Codex Review Intake Decision：

```text
GO TO PR_REQUIRED_CHECKS FIRST-STAGE ACTIVE
```

实施范围：

* 用户已明确授权配置 GitHub branch protection / required checks，并允许创建测试 PR 和执行 direct push 拒绝测试。
* 机制路线采用 GitHub branch protection；repository ruleset 当前保持未配置，`gh api repos/codegodkun/CQCP/rulesets` 返回 `[]`。
* 第一阶段仅将两个 CI check 设为 required checks：
  * `Backend Gradle tests`
  * `Admin web lint, tests, and build`
* 两个 required checks 的 source 均为 GitHub Actions app，GitHub API 返回 `app_id: 15368`。
* `required_status_checks.strict` 为 `true`。
* `required_pull_request_reviews.required_approving_review_count` 为 `0`，用于强制通过 PR，不新增人工 approval 数量要求。
* `enforce_admins.enabled` 为 `true`。
* `allow_force_pushes.enabled` 为 `false`。
* `allow_deletions.enabled` 为 `false`。
* `CQCP Code Review` / `CQCP Spec & Docs Review` 的 Check Run 或 Commit Status 发布身份、source、运行宿主和报告 URL 尚未确定，未纳入第一阶段 required checks；后续如纳入 required checks，必须另行配置并验证。

测试 PR 证据：

* PR #6：`https://github.com/codegodkun/CQCP/pull/6`。
* PR #6 head commit：`602656f5a21291c15c6e8761da5e0f9c64b8726f`。
* PR #6 在 required checks `in_progress` 时，`mergeable_state` 为 `blocked`。
* PR #6 head commit 的两个 check run 后续均为 `completed / success`：
  * `Backend Gradle tests` completed at `2026-06-28T08:20:29Z`。
  * `Admin web lint, tests, and build` completed at `2026-06-28T08:19:46Z`。
* checks success 后，PR #6 `mergeable_state` 变为 `clean`。

direct push 测试与清理证据：

* 在仅配置 required checks、尚未启用 PR-only 约束时，已通过 checks 的 PR #6 head commit 曾可 direct push 到 `master`；该结果证明仅 required checks 不足以形成 PR-only 门禁。
* 随后通过 PR #7 清理临时测试文件：`https://github.com/codegodkun/CQCP/pull/7`。
* PR #7 head commit：`103913498fb52ba620bfd18c8190140de9b957ca`。
* PR #7 两个 required checks 均为 `completed / success`：
  * `Backend Gradle tests` completed at `2026-06-28T10:15:37Z`。
  * `Admin web lint, tests, and build` completed at `2026-06-28T10:14:43Z`。
* PR #7 已合并，merge commit 为 `7d980abe1399df2a3f226b2fba921894618e6c6e`。
* `master` 上临时测试文件 `tmp/TASK-GOV-004-phase5-gate-test.md` 经 GitHub Contents API 验证返回 `404 Not Found`，表示已清理。
* 补充启用 PR-only 约束后，direct push probe commit `feff2ac24f977ec00e32802ac0ed6af7a59782fe` 被 GitHub 拒绝，原始拒绝要点为：
  * `GH006: Protected branch update failed for refs/heads/master.`
  * `Changes must be made through a pull request.`
  * `2 of 2 required status checks are expected.`
* Phase 5 gate-test 远端测试分支已删除；cleanup 测试分支已不存在。

结论边界：

* 第一阶段 `PR_REQUIRED_CHECKS` 已生效，含 PR-only 与两个 CI required checks。
* 该结论不表示 `CQCP Code Review` / `CQCP Spec & Docs Review` 已机制化发布为 required checks。
* 该结论不表示 `TASK-GOV-004` 可直接归档；该阶段完成时仍需归档前独立只读审计，后续 Phase 5 post-implementation 独立只读审计已返回 `GO`。
* 不进入 `TASK-028` / `TASK-031` / `TASK-032`。

#### Phase 5 post-implementation 独立只读审计（2026-06-28）

独立只读审计结论：

```text
GO
```

阻塞问题：

* 无。

Non-blocking：

* PR #6 当前 GitHub API 显示为 `merged: true`，且 `merge_commit_sha` 等于 head commit `602656f...`；这是此前 direct push 测试导致的历史状态。
* PR #6 的“pending checks 阻塞 `mergeable_state`”属于瞬时状态，当前 PR API 无法直接重放；该证据只能由任务文件 / changelog 记录、check-runs 时间线和 branch protection 语义共同支撑。

审计核查要点：

* 本地 `master` 与 `origin/master` 对齐，HEAD 为 `7d980abe1399df2a3f226b2fba921894618e6c6e Merge pull request #7 ...`。
* 本地未提交 diff 仅涉及 4 个文档：`CURRENT_CONTEXT.md`、`changelog/2026-06.md`、`tasks/MVP_TASK_MAP.md`、本任务文件。
* 四份文档一致记录 `PR_REQUIRED_CHECKS`（第一阶段 CI required checks）、`TASK-GOV-004` 未归档、未进入 `TASK-028` / `TASK-031` / `TASK-032`，且 `CQCP Code Review` / `CQCP Spec & Docs Review` 尚未纳入 required checks。
* GitHub branch protection 复核通过：`strict: true`；required checks 为 `Backend Gradle tests` 与 `Admin web lint, tests, and build`；两项 source 均为 GitHub Actions `app_id: 15368`；`required_approving_review_count: 0`；`enforce_admins.enabled: true`；`allow_force_pushes.enabled: false`；`allow_deletions.enabled: false`。
* `gh api repos/codegodkun/CQCP/rulesets` 返回 `[]`。
* PR #6 存在，head 为 `602656f5a21291c15c6e8761da5e0f9c64b8726f`，相关 check-runs 包含两项 required checks 且均 completed / success。
* PR #7 已 merged，merge commit 为 `7d980abe1399df2a3f226b2fba921894618e6c6e`，head `103913498fb52ba620bfd18c8190140de9b957ca` 两项 required checks 均 completed / success。
* `tmp/TASK-GOV-004-phase5-gate-test.md?ref=master` GitHub Contents API 返回 404。
* `refs/heads/codex/task-gov-004-phase5-gate-test-20260628` GitHub ref API 返回 404，`git ls-remote` 也无输出。
* 任务文件和 changelog 均记录 direct push probe commit `feff2ac24f977ec00e32802ac0ed6af7a59782fe` 与 GH006 拒绝要点。
* 独立只读审计声明：未修改文件、未 stage、未 commit、未 push、未配置 GitHub。

Codex Review Intake Decision：

```text
GO TO ARCHIVE DECISION PREP ONLY
```

结论边界：

* Phase 5 第一阶段 post-implementation 独立只读审计已通过。
* 当前只允许准备 `TASK-GOV-004` 归档决策；不得在本步直接归档。
* `CQCP Code Review` / `CQCP Spec & Docs Review` 机制化状态发布仍待后续单独定界。
* 不进入 `TASK-028` / `TASK-031` / `TASK-032`。

#### Phase 5 归档决策准备 / Codex Review Intake（2026-06-28）

Codex Review Intake Decision：

```text
GO TO ARCHIVE PR PREP ONLY
```

判断依据：

* Phase 5 第一阶段 GitHub branch protection / required checks 已配置并通过真实 PR、required checks、cleanup PR 与 direct push probe 验证。
* Phase 5 post-implementation 独立只读审计结论为 `GO`，无阻塞问题。
* Non-blocking 项已明确记录：PR #6 当前 API 状态无法直接重放 pending checks 阻塞瞬时状态，不阻塞归档 PR 准备。
* 当前 Governance Mode 可标注为 `PR_REQUIRED_CHECKS`（第一阶段 CI required checks）。

结论边界：

* 可以准备 `TASK-GOV-004` 归档 PR 的文档与证据说明。
* 不得在本步直接归档 `TASK-GOV-004`，不得移动 `tasks/active -> tasks/done`。
* `CQCP Code Review` / `CQCP Spec & Docs Review` 尚未机制化发布为 required checks；后续如需纳入，必须另行定界、授权、配置并验证。
* 不进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 当前 4 个本地文档 diff 仍未 stage / commit / push；后续 stage、commit、push 或创建归档 PR 均需用户明确授权。

#### 归档准备 PR 创建记录（2026-06-28）

* 用户已授权 stage / commit / push 当前 4 个治理文档 diff 并准备归档 PR。
* 分支：`codex/task-gov-004-archive-pr-prep`。
* 提交：`c0e1d2c7f25131b71abcae68f737c494466f52ed docs(governance): prepare TASK-GOV-004 archive PR`。
* Draft PR：`https://github.com/codegodkun/CQCP/pull/8`。
* PR #8 base：`master`；head：`codex/task-gov-004-archive-pr-prep`。
* PR #8 仅为归档准备 PR，不移动 `tasks/active -> tasks/done`，不直接归档 `TASK-GOV-004`。
* PR #8 当前 required checks 已由 GitHub Actions 触发，查询时 `Backend Gradle tests` 与 `Admin web lint, tests, and build` 均为 `in_progress`，source 为 GitHub Actions `app_id: 15368`。
* PR #8 未发布 `CQCP Code Review` / `CQCP Spec & Docs Review` Check Run 或 Commit Status。

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

下一步：等待 PR #8 的 required checks 完成后，按归档 PR 流程进行只读审查与用户合并/归档决策；不得直接归档 `TASK-GOV-004`，不得进入 `TASK-028` / `TASK-031` / `TASK-032`。`CQCP Code Review` / `CQCP Spec & Docs Review` 机制化状态发布仍待后续单独定界。

## 风险

* `CQCP Code Review` / `CQCP Spec & Docs Review` 尚未形成可审计 Check Run 或 Commit Status source，暂未纳入第一阶段 required checks。
* GitHub branch protection 配置是外部状态，后续仍需在归档前由独立只读审计复核。
* 如果后续修改 GitHub 设置，必须重新采集 branch protection / ruleset / required checks / direct push 拒绝证据。
* `CQCP_AUDIT` 的 `.claude/settings.json` 仍需只读核实，不能仅凭目录存在采信其限制已生效。

## 待确认

* 是否将外部 `C:\Users\1\Downloads\CQCP-PR治理方案-v2.md` 正式纳入 `docs/governance/`。
* 是否安装或启用 `gh` CLI，供 CQCP_AUDIT 后续使用 `gh api` 核实 GitHub 设置。
* Phase 3 的最小 CI 命令矩阵已在 `.github/workflows/ci.yml` 中落地，并已通过 PR #5 final head GitHub Actions run `28288707273` 验证后合并落地。
* Phase 5 第一阶段 branch protection / required checks / PR-only direct push 拒绝已配置并验证；repository ruleset 仍未配置。
* Phase 5 机制化审查信号的最终发布身份与 source 仍待确认；当前不发布 `CQCP Code Review` / `CQCP Spec & Docs Review` Check Run 或 Commit Status，也不把二者列为 required checks。

## 完成记录

* 完成日期：2026-06-27（Phase 3 workflow 文件新增；PR #5 已合并，minimal GitHub Actions CI 已落地；Phase 4 手动独立审查规格已准备；`TASK-GOV-004` 未归档）
* Phase 4 试运行写回日期：2026-06-28（GLM 5.2 外部 Code Review `GO`；GLM 5.2 外部 Spec & Docs Review `GO`；Codex Review Intake Decision `GO`）
* Phase 5 Review Intake 写回日期：2026-06-28（Codex Review Intake Decision `GO TO SPEC PREP ONLY`；已准备机制化门禁规格；未配置 GitHub 设置；未发布 required checks）
* Phase 5 规格充分性审查日期：2026-06-28（Codex Review Intake Decision `GO TO USER IMPLEMENTATION DECISION`；可进入实施前决策确认；不可直接实施）
* Phase 5 第一阶段实施日期：2026-06-28（GitHub branch protection 已配置；第一阶段 required checks 为 `Backend Gradle tests` 与 `Admin web lint, tests, and build`，source 为 GitHub Actions `app_id: 15368`；`strict: true`；启用 PR-only direct push 拒绝；PR #6 / PR #7 和 direct push probe 已形成证据；repository ruleset 未配置；`TASK-GOV-004` 未归档）
* Phase 5 post-implementation 独立只读审计日期：2026-06-28（结论 `GO`；无阻塞问题；Non-blocking 为 PR #6 当前 API 状态无法直接重放 pending checks 阻塞瞬时状态；`TASK-GOV-004` 未归档）
* Phase 5 归档决策准备 / Codex Review Intake 日期：2026-06-28（结论 `GO TO ARCHIVE PR PREP ONLY`；可准备归档 PR；不得在本步直接归档；后续 stage / commit / push / 创建 PR 仍需用户明确授权）
* 归档准备 PR 创建日期：2026-06-28（branch `codex/task-gov-004-archive-pr-prep`；commit `c0e1d2c7f25131b71abcae68f737c494466f52ed`；Draft PR #8 `https://github.com/codegodkun/CQCP/pull/8`；required checks 查询时为 `in_progress`；`TASK-GOV-004` 未归档）
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
* 遗留问题：`CQCP Code Review` / `CQCP Spec & Docs Review` 尚未形成可审计 Check Run 或 Commit Status source，未纳入第一阶段 required checks；`TASK-GOV-004` 未归档，未进入 `TASK-028` / `TASK-031` / `TASK-032`。
* 备注：本轮配置 GitHub branch protection / required checks，并创建测试 PR 与执行 direct push 拒绝测试；不修改业务代码、测试、fixture、expected JSON、OpenAPI、数据库、Docker、ADR 或 PRD。
