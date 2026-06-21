# TASK-GOV-003：五类问题整改与角色执行门禁

状态：Active（治理规则已入仓，后续整改与审计尚未完成）

类型：治理父任务 / Codex 主控

优先级：P0

负责人：Codex

创建日期：2026-06-21

治理依据：`docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`

## 背景

`TASK-EVAL-001-B` 已形成实现与验证结果，但外部审查进一步识别出代码缺陷、项目记忆可信度、角色分离、评测 expected 来源和父任务归档审计五类治理问题。继续提交、归档或进入后续实现，会把未经独立核验的完成声明和评测基线带入后续任务。

本任务把 v3 整改计划正式纳入仓库任务治理体系，冻结角色、证据、审计和执行门禁。它只建立治理任务与长期硬门禁，不修复业务代码，也不替代后续每个整改 TASK_SPEC。

## 当前阻塞原因

1. 五类问题此前主要存在于外部对话文档，尚未形成仓库内统一治理入口。
2. `CURRENT_CONTEXT.md` 含有大量完成断言，但尚未由独立 agent 逐条核验 commit、测试命令和原始输出。
3. `TASK-EVAL-001-B` 的 expected anchor 可能依赖 parser 内部标识符，现有 1.0 / 1.0 / 1 结果不能单独证明 anchor 客观正确。
4. `TABLE_CELL` 目前只有 mock / test-only 覆盖，尚无独立人工标注驱动的真实 `.docx` 端到端覆盖。
5. Codex、Claude Code / DeepSeek、独立 agent 和人工标注者的职责边界尚未全部固化到执行模板。
6. 父任务归档前缺少强制独立 agent 只读审计门禁。

## 五类问题划分

| 类别 | 问题性质 | 当前处理边界 |
|---|---|---|
| 第一类 | 4 条代码缺陷 + 1 条覆盖盲区 | 本任务只登记和冻结后续处理顺序，不修代码 |
| 第二类 | `CURRENT_CONTEXT.md` 完成声明可信度 | 必须由独立 agent 逐条认领审计 |
| 第三类 | 角色分离机制未真正落地 | Codex 写规格和审查；Claude Code / DeepSeek 只执行冻结 TASK_SPEC |
| 第四类 | anchor expected 可能存在循环验证 | expected 来源必须披露；真实 anchor 标准答案由人工独立标注 |
| 第五类 | 父任务归档前缺少持续独立抽查 | 每个父任务归档前必须完成独立 agent 只读审计 |

第一类的五条具体问题以 v3 计划第 5 节为准：

1. `resolveTextEvidence()` 三个 confidence signal 硬编码为 `true`。
2. `collectPatternCandidates()` 的 `valueFormatSignal` 硬编码为 `true`。
3. `SOURCE_ORIGIN` / `SOURCE_EXTRACTION_MODE` / `CONTEXT_TYPE` 覆盖 parser 真实 provenance。
4. `resolveRatioEvidence()` 语义候选非空时 early return，跳过 fallback 候选收集。
5. `TABLE_CELL` 级 SourceAnchor 尚无真实 `.docx` fixture 端到端覆盖；该项是覆盖盲区，不得写成已确认代码 bug。

## 角色分工

### Codex

- 读取上下文、冻结任务边界、编写 TASK_SPEC、审查实现报告与真实 diff。
- 对照可证伪验收断言逐条给出满足 / 不满足及证据。
- 在角色分离试点中不得直接编写业务代码。
- 不得以 `CURRENT_CONTEXT.md` 自述替代代码、测试、commit 或原始 console 证据。
- 只有用户明确授权后才可 commit / push；push 必须单独授权。

### Claude Code / DeepSeek

- 只能执行 Codex 已冻结且关联现有父 TASK 的局部 TASK_SPEC。
- 不得直接承接父 TASK，不得扩大范围，不得修改未授权文件。
- 编码前必须先提交规格映射计划，经 Codex 审查放行后才能改代码。
- 必须运行 TASK_SPEC 指定测试并提交真实命令、原始输出、diff 和实现报告。
- 不得 commit，不得 push，不得兼任项目记忆审计者。

### 独立 agent

- 只读核查真实仓库、工作区、diff、commit 和证据。
- 不实现、不修代码、不写业务逻辑、不替代 Claude Code / DeepSeek。
- 负责 `CURRENT_CONTEXT.md` 认领审计、流程真实性抽查、循环验证核查和父任务归档前审计。

### 人工标注者

- 独立于 parser 输出定义真实合同 anchor 标准答案。
- AI agent 不得替代人工完成标准答案定义。

### 用户 / 外部审查

- 用户决定是否授权 commit / push，并调度独立 agent。
- 外部审查判断报告的逻辑和证据是否可信，不替代真实仓库核查。

## 执行者矩阵

| 工作 | 主执行者 | 审查者 | 可改代码 | 可 commit | 可 push |
|---|---|---|---|---|---|
| 治理任务与模板固化 | Codex | 用户 / 外部审查；归档前独立 agent | 否 | 本次用户已授权治理提交 | 否 |
| `CURRENT_CONTEXT.md` 逐条认领审计 | 独立 agent | 外部审查 / Codex intake | 否 | 否 | 否 |
| 局部修复 TASK_SPEC 起草 | Codex | 用户 / 外部审查 | 否 | 否 | 否 |
| 冻结 TASK_SPEC 实现 | Claude Code / DeepSeek | Codex + 独立 agent 抽查 | 仅授权文件 | 否 | 否 |
| expected / fixture 人工标准答案 | 人工标注者 | Codex + 独立 agent | 否 | 否 | 否 |
| TASK-EVAL-001-B 提交前复核 | 独立 agent | 外部审查 / Codex intake | 否 | 否 | 否 |
| 父任务归档前审计 | 独立 agent | 外部审查 / Codex intake | 否 | 否 | 否 |

详细 Step 0 至 Step 18 执行者矩阵以 v3 计划第 3 节为准。

## 当前禁止事项

在 `TASK-GOV-003` 完成并满足归档前要求之前：

- 不得归档 `TASK-EVAL-001`。
- 不得提交 `TASK-EVAL-001-B`。
- 不得进入 `TASK-028`，包括 Review Intake。
- 不得进入 `TASK-031`。
- 不得进入 `TASK-032` 实现。
- 不得派发 Claude Code / DeepSeek 实现任务。
- 不得把 `CURRENT_CONTEXT.md` 当作完成凭证。
- 不得 push。
- 不得使用 `git add .`。
- 不得由 Codex 在角色分离试点中直接写业务代码。
- 不得让独立 agent 执行实现。
- 不得用 parser 输出倒填 expected 后宣称 anchor 已独立正确。

上述门禁不得由 Codex 自行放宽；如需例外，必须等待用户明确确认。

## DoD

- [ ] v3 计划全文已保存到 `docs/governance/`；除为通过 `git diff --check` 移除 Markdown 行尾空格外，规范化文本与外部原文一致。
- [ ] 本 active governance task 已建立，并明确五类问题、角色、执行者矩阵和硬门禁。
- [ ] `tasks/MVP_TASK_MAP.md` 已登记本任务是 `TASK-EVAL-001` 归档和 `TASK-028` Review Intake 的前置治理任务。
- [ ] `CURRENT_CONTEXT.md` 已记录当前暂停状态，且未把未来整改写成已完成。
- [ ] `AGENTS.md` 已固化角色分离、证据和父任务归档前独立审计规则。
- [ ] execution TASK_SPEC 模板已要求编码前规格映射、可证伪断言、同根因一致性和 expected 来源披露。
- [ ] readonly-review 模板已要求核查角色分离、完成声明证据、循环验证和父任务归档 GO / NO-GO。
- [ ] `changelog/2026-06.md` 已记录本治理任务及本次不做范围。
- [ ] 本次 diff 只包含用户授权的文档文件，未修改生产代码、测试、fixture、OpenAPI、迁移、Docker / Compose、PRD、ARCHITECTURE 或 ADR。
- [ ] `git diff --check` 无 whitespace error。
- [ ] 本治理变更已独立提交，且未 push。
- [ ] `TASK-GOV-003` 归档前已由独立 agent 完成只读审计并形成可复核报告。

## 不做范围

- 不修复上述 4 条代码缺陷。
- 不新增或修改测试代码、fixture、expected JSON。
- 不修改 OpenAPI、数据库迁移、Docker / Compose。
- 不修改 `PRD.md`、`docs/ARCHITECTURE.md` 或 ADR。
- 不提交 `TASK-EVAL-001-B`，不归档 `TASK-EVAL-001`。
- 不进入 `TASK-028`、`TASK-031`、`TASK-032`。
- 不创建或派发任何 Claude Code / DeepSeek 实现 TASK_SPEC。
- 不执行 push。

## 验证方式

1. 对 v3 外部原文与仓库文件规范化换行并移除行尾空格后计算 SHA-256，结果必须一致。
2. 使用 `rg` 核对八项硬门禁、角色规则、编码前规格映射、可证伪断言、同根因一致性和 expected 来源规则。
3. 执行：

```bash
git diff --check
git diff --name-status
git status --short
git status -sb
```

4. 提交前只显式 stage 本任务授权文件，列出 staged 文件清单，不使用 `git add .`。
5. 提交后执行：

```bash
git status --short
git status -sb
git log --oneline -5
```

## 归档前要求

`TASK-GOV-003` 不因本次治理文件提交自动完成或归档。归档前必须同时满足：

1. 本任务 DoD 全部有真实证据支撑。
2. 独立 agent 已执行只读审计，至少核查：
   - v3 原文完整性；
   - 授权文件范围；
   - 硬门禁是否在任务地图、当前上下文、AGENTS 和模板中一致生效；
   - 是否存在以 `CURRENT_CONTEXT.md` 自述替代真实证据；
   - 是否有未记录例外或被自行放宽的门禁。
3. Codex 对独立审计报告单独给出 Review Intake Decision。
4. 用户明确确认是否允许归档。
5. 归档时仍不得自动解除 `TASK-EVAL-001-B`、`TASK-EVAL-001`、`TASK-028`、`TASK-031`、`TASK-032` 的后续专属门禁；解除条件必须按 v3 计划逐项满足并单独确认。

## Task Context

### Required Context

- `AGENTS.md`
- `CURRENT_CONTEXT.md`
- 本任务文件
- `docs/governance/CQCP-五类问题整改计划-v3-角色分工与执行门禁补强版.md`
- `tasks/MVP_TASK_MAP.md`
- `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
- `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
- `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`

### Optional Context

- `changelog/2026-06.md`
- 后续独立 agent 只读审计报告

### Out of Scope

- 生产代码、测试代码、fixture、OpenAPI、数据库迁移、Docker / Compose。
- `PRD.md`、`docs/ARCHITECTURE.md`、ADR。
- `TASK-EVAL-001-B` 提交、`TASK-EVAL-001` 归档。
- `TASK-028` / `TASK-031` / `TASK-032` 实现。

## Next Task Handoff

本任务提交后仍保持 Active。下一步不是业务实现，而是由用户另行授权并调度独立 agent 对本次治理 diff 执行只读审计；在审计和 Review Intake Decision 完成前，不派发任何实现任务。
