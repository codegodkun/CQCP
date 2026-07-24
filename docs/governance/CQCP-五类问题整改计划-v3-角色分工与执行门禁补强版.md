# CQCP 五类问题整改计划 v3：角色分工与执行门禁补强版

更新日期：2026-06-21
适用范围：TASK-025 / TASK-026 / TASK-027 / TASK-EVAL-001 收口前治理整改
执行要求：后续 CQCP 相关整改、收口、审计、TASK_SPEC 派发和归档动作，均应按本计划的角色分工与执行门禁执行。
范围收敛：本计划的强门禁主要适用于 `TASK-EVAL-001` 遗留高风险链路、父任务归档、`TASK_SPEC`、expected / fixture、`EvidenceSlot` / `CandidateResolver` / `SourceAnchor`、workflow / CI / required checks / branch protection 等场景；不自动泛化到普通文档状态同步、changelog 补录、路径修正或 post-merge 状态写回。

---

## 0. 总体结论

本轮整改不是单纯“修几个 bug”，而是同时处理五类不同层级的问题：

1. 已确认的代码缺陷与覆盖盲区；
2. 项目记忆中“已完成”自述的可信度问题；
3. Codex / Claude Code / DeepSeek 角色分离机制未真正落地的问题；
4. TASK-EVAL-001-B 中 anchor expected 值可能依赖 parser 输出的循环验证问题；
5. 后续父任务归档前缺少持续独立抽查制度的问题。

这五类不能混在一个任务、一个 commit 或一个角色里处理。

当前最重要的判断是：

- `CURRENT_CONTEXT.md` 不能直接作为完成凭证，只能作为待核验线索；
- `TASK-EVAL-001-B` 暂不提交；
- `TASK-EVAL-001` 暂不归档；
- `TASK-028` 暂不进入；
- 先把已确认问题写入项目可追踪记录，再恢复角色分离试点，再处理评测基线可信度；
- 执行前必须核实 `TASK-EVAL-001` 父任务 DoD 原文，避免对 `TABLE_CELL` 是否阻塞归档产生自由解释；
- 角色分离试点不仅要做事后审查，还必须加入编码前规格映射计划。

---

## 1. 强制执行原则

### 1.1 后续必须按本计划角色分工执行

后续所有 CQCP 相关整改动作，必须明确：

- 主执行者是谁；
- 审查者是谁；
- 是否允许改代码；
- 是否允许改文档；
- 是否允许 commit；
- 是否允许 push；
- 必须产出的证据是什么。

不得出现以下情况：

- Codex 自己写代码、自己测试、自己宣布通过；
- Claude Code / DeepSeek 直接接父 TASK；
- Claude Code / DeepSeek 兼任项目记忆审计者；
- 独立 agent 被要求执行实现任务；
- AI agent 替代人工定义合同 anchor 标准答案；
- 用户未授权时 push；
- 使用 `git add .`；
- 将治理规则、业务代码修复、评测重标注混在同一个 commit。

---

## 2. 角色定义

### 2.1 Codex

Codex 是项目总控，不再直接承担所有代码修复。

Codex 的职责：

- 读取项目上下文；
- 判断任务边界；
- 编写冻结的 TASK_SPEC；
- 明确授权文件、禁止文件、验收断言；
- 审查 Claude Code / DeepSeek 的实现报告；
- 对照 TASK_SPEC 逐条判断满足 / 不满足；
- 决定是否接纳；
- 负责最终提交前判断；
- 在用户明确授权后才可 push。

Codex 不应：

- 自己写代码后再自己宣布通过；
- 用 CURRENT_CONTEXT.md 自述替代真实代码 / 测试 / commit 证据；
- 在没有独立审查的情况下归档父任务；
- 未经用户明确授权 push；
- 将未验证事实写成已完成。

---

### 2.2 Claude Code / DeepSeek

Claude Code / DeepSeek 是局部执行者，只能执行 Codex 已冻结的 TASK_SPEC。

Claude Code / DeepSeek 的职责：

- 只处理 TASK_SPEC 中授权的文件；
- 不扩展任务范围；
- 不自行修改任务边界；
- 不 commit；
- 不 push；
- 自己运行指定测试；
- 贴出真实命令和原始 console 输出；
- 输出实现报告。

Claude Code / DeepSeek 不应：

- 接父 TASK；
- 自行决定修哪些问题；
- 兼任项目记忆审计者；
- 判断 Codex 项目记忆是否可信；
- 替代人工定义合同标注标准答案；
- 使用 `git add .`。

---

### 2.3 独立 agent

独立 agent 是由用户另开会话、使用没有项目记忆负担的模型或 agent 实例执行的只读核查者。

独立 agent 的职责：

- 读真实仓库；
- 跑真实命令；
- 查真实 diff；
- 输出原始证据；
- 不依赖 Codex 自述；
- 不修代码；
- 不写业务逻辑；
- 不承担 Claude Code / DeepSeek 的实现者角色。

独立 agent 用于：

- CURRENT_CONTEXT.md 逐条认领审计；
- TASK_SPEC 流程是否真实发生的抽查；
- 父任务归档前只读审计；
- 评测逻辑是否存在循环验证的核查；
- 工作区状态、commit、测试输出的事实核验。

---

### 2.4 外部审查 / ChatGPT

外部审查不直接访问仓库，不直接写代码，不直接派发任务给 Claude Code / DeepSeek。

外部审查的职责：

- 审查 Codex 或独立 agent 的报告是否经得起推敲；
- 识别报告中的逻辑漏洞、证据不足、措辞过度；
- 给用户提供流程建议、风险判断和可复制提示词；
- 不把项目记忆当作事实本身；
- 不替代真实代码核查。

---

### 2.5 人工标注者

人工标注者负责定义真实合同中的标准答案，尤其是 anchor 正确位置。

人工标注者的职责：

- 打开真实 `.docx`；
- 不参考 parser 输出；
- 判断候选值实际出现在哪个段落 / 表格 / 行 / 单元格；
- 形成独立于 parser 的标准答案；
- 将标准答案交给 Codex / Claude Code 转化为 fixture 或 expected JSON。

人工标注者不能被 AI agent 完全替代。

---

## 3. 任务执行者矩阵

| 步骤 | 任务 | 主执行者 | 审查 / 验收者 | 是否允许改代码 | 是否允许改文档 | 是否允许 commit | 是否允许 push | 产出物 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| Step 0 | 工作区与 TASK-EVAL-001 父任务 DoD 只读确认 | Codex | 用户 / 外部审查抽查 | 否 | 否 | 否 | 否 | `git status`、ahead/behind、未提交文件清单、父任务 DoD 原文 | 只读；DoD 未贴出前不得判断 TABLE_CELL 是否阻塞归档 |
| Step 1 | 写入 5 条已确认问题标准记录 | Codex | 用户 / 外部审查抽查 | 否 | 是 | 需用户确认 | 否 | 任务文件 / CURRENT_CONTEXT / changelog 中的标准记录 | 不修代码 |
| Step 2 | CURRENT_CONTEXT.md 逐条认领审计 | 独立 agent | 外部审查二次审查 | 否 | 否 | 否 | 否 | 每条断言的 commit / 测试 / console 输出 / 待确认标记 | 不能由 Codex 做 |
| Step 3 | 第一批 TASK_SPEC 草案：resolveTextEvidence | Codex | 用户 / 外部审查审 TASK_SPEC | 否 | 是 | 否 | 否 | 冻结 TASK_SPEC 草案 | Codex 只写规格，不写代码 |
| Step 4 | 执行第一批 TASK_SPEC | Claude Code / DeepSeek | Codex 初审 | 是，仅授权文件 | 否，除非 TASK_SPEC 授权 | 否 | 否 | 编码前规格映射说明、实现 diff、测试输出、实现报告 | 先提交规格映射说明，经 Codex 放行后再改代码；不 commit / 不 push |
| Step 5 | 第一批实现审查 | Codex | 独立 agent 抽查 | 否 | 可写审查记录 | 需用户确认 | 否 | 逐条“满足 / 不满足”审查报告 | Codex 不能只写“测试通过” |
| Step 6 | 第一批流程真实性抽查 | 独立 agent | 外部审查二次审查 | 否 | 否 | 否 | 否 | 是否真的由 CC 执行、Codex 是否逐条审查 | 检查角色分离是否被绕过 |
| Step 7 | 第二批 TASK_SPEC：collectPatternCandidates | Codex | 用户 / 外部审查审 TASK_SPEC | 否 | 是 | 否 | 否 | 冻结 TASK_SPEC 草案 | 必须包含同根因一致性检查 |
| Step 8 | 执行第二批 TASK_SPEC | Claude Code / DeepSeek | Codex 初审 + 独立 agent 抽查 | 是，仅授权文件 | 否，除非 TASK_SPEC 授权 | 否 | 否 | 编码前规格映射说明、同根因一致性说明、实现 diff、测试输出、实现报告 | 独立 agent 必须核实一致性说明是否真实且站得住 |
| Step 9 | provenance 后续任务边界判断 | Codex | 外部审查 | 否 | 是 | 需用户确认 | 否 | TASK_SPEC 草案或后续任务草案 | 先不修，先定边界 |
| Step 10 | early return 后续任务边界判断 | Codex | 外部审查 | 否 | 是 | 需用户确认 | 否 | TASK-032 / 独立 pipeline task 草案 | 先不修，必须追踪 |
| Step 11 | 人工 table-cell anchor 标注 | 用户 / 团队人工标注者 | Codex 形式审查 + 外部审查逻辑审查 | 否 | 是，标注说明 | 否 | 否 | 人工标准答案 | 不能由 AI 替代定义标准答案 |
| Step 12 | 将人工标注转成 fixture / expected JSON / 测试 | Claude Code / DeepSeek | Codex 初审 + 独立 agent 抽查 | 仅测试 / fixture 授权文件 | 是，若 TASK_SPEC 授权 | 否 | 否 | fixture、expected JSON、测试输出 | 不能倒填 parser 输出 |
| Step 13 | TASK-EVAL-001-B 提交前复核 | 独立 agent | 外部审查二次审查 | 否 | 否 | 否 | 否 | B 是否可提交的只读报告 | 通过前不提交 |
| Step 14 | TASK-EVAL-001-B 提交 | Codex | 用户确认 | 否，除非已完成授权变更 | 是 | 是，用户确认后 | 否，除非用户明确授权 | commit | commit 与 push 分开 |
| Step 15 | TASK-EVAL-001 父任务归档前审计 | 独立 agent | 外部审查二次审查 | 否 | 否 | 否 | 否 | 父任务归档 GO / NO-GO | 没有独立审计不得归档 |
| Step 16 | TASK-EVAL-001 父任务归档 | Codex | 用户确认 | 否 | 是 | 是，用户确认后 | 否，除非用户明确授权 | 归档 commit | 不进入 TASK-028 |
| Step 17 | 治理规则固化 TASK-GOV-003 | Codex 起草 | 外部审查 + 独立 agent 抽查 | 否 | 是 | 是，用户确认后 | 否，除非用户明确授权 | AGENTS / VERIFY / TASK_SPEC 模板更新 | 文档治理独立提交 |
| Step 18 | TASK-028 / TASK-029 / TASK-030 顺序重评估 | Codex | 外部审查 / 用户拍板 | 否 | 是，Review Intake 文档 | 需用户确认 | 否，除非用户明确授权 | 下一任务 Review Intake | 只有前面收口后才做 |

---

## 4. 五类问题总览

| 类别 | 性质 | 是否一次性完成 | 主执行者 | 审查者 |
|---|---|---|---|---|
| 第一类：已确认问题记录与修复 | 代码缺陷 + 覆盖盲区 | 可分批完成 | Codex / CC | Codex + 独立 agent |
| 第二类：项目记忆逐条认领审计 | CURRENT_CONTEXT 可信度问题 | 一次性扫描 | 独立 agent | 外部审查 |
| 第三类：角色分离机制落地 | 流程治理问题 | 长期生效 | Codex 起草，CC 试点 | 独立 agent |
| 第四类：anchor 独立标注 | 评测设计问题 | 至少先完成 1 个 fixture | 人工标注者 | Codex + 独立 agent |
| 第五类：持续独立抽查制度 | 长期制度 | 持续执行 | 用户调度，独立 agent 执行 | 外部审查 |

---

## 5. 第一类：已确认问题记录与修复

### 5.1 命名修正

不要再称为“5 个 bug”。

应称为：

> 4 条代码缺陷 + 1 条覆盖盲区。

原因：

`TABLE_CELL` 未被真实 DOCX fixture 触发，不代表生产代码一定错误。它是验证覆盖不足，不是已确认代码 bug。

---

### 5.2 五条已确认问题

| # | 类型 | 问题 | 状态 | 当前处理 |
|---|---|---|---|---|
| 1 | 代码缺陷 | `resolveTextEvidence()` 三个信号硬编码为 `true` | 未修复，未记录 | 第一批 TASK_SPEC 试点修复 |
| 2 | 代码缺陷 | `collectPatternCandidates()` 中 `valueFormatSignal` 硬编码为 `true` | 未修复，未记录 | 第二批修复，必须对照第一批原则 |
| 3 | 代码缺陷 / 债务 | provenance 被硬编码覆盖：`SOURCE_ORIGIN` / `SOURCE_EXTRACTION_MODE` / `CONTEXT_TYPE` | 未修复，未记录 | 先记录，后续单独任务 |
| 4 | 代码缺陷 / pipeline 债务 | `resolveRatioEvidence()` 语义候选非空时 early return，跳过 fallback 候选 | 未修复，专项测试证实真实可达，未记录 | 先记录，推迟到 TASK-032 或独立 pipeline task |
| 5 | 覆盖盲区 | `TABLE_CELL` 级 SourceAnchor 未被真实 `.docx` fixture 端到端触发 | 仅 mock / test-only 覆盖 | 依赖人工 anchor 标注后再补 fixture / 测试 |

---

### 5.3 统一记录模板

每一条都必须写入项目可追踪文件，不得只停留在对话中。

建议字段：

```text
Finding:
验证:
影响:
修复方向:
推迟理由:
目标任务:
当前 fixture 覆盖:
```

示例：

```text
Finding:
resolveRatioEvidence() 语义候选非空时 early return，跳过 fallback 候选收集。

验证:
已通过专项测试确认代码路径真实可达。

影响:
语义候选值与 fallback 候选值冲突时，resolver 看不到完整候选集，CONFLICTED 可能被误判为 HIGH。

修复方向:
重构候选收集为“全部收集 → 合并去重 → 统一送 resolver”，消除 early return。

推迟理由:
改动面超出当前最小修复范围；与 TASK-032 候选收集重构重叠。

目标任务:
TASK-032 或 Codex 后续新建独立候选收集 pipeline task。

当前 fixture 覆盖:
当前 4 正 4 负 fixture 未触发，需新增覆盖。
```

---

## 6. 执行前置：工作区与父任务 DoD 只读确认

任何写入动作前，必须先由 Codex 执行只读状态确认。

必须输出：

```bash
git status --short
git status -sb
git rev-list --left-right --count origin/master...HEAD
git log --oneline -8
```

同时必须读取并贴出：

```text
tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md 中父任务 DoD 原文
```

要求：

- 不能假设工作区干净；
- 如果已有 `TASK-EVAL-001-B` 未提交变更，必须先列出具体文件；
- 不得把评测实现、债务记录、代码修复、治理规则混进同一个工作区；
- 写记录前必须说明本次将修改哪些文件；
- 不使用 `git add .`；
- 不 push；
- 未贴出 `TASK-EVAL-001` 父任务 DoD 原文前，不得判断 `TABLE_CELL` 真实 DOCX 覆盖缺口是否阻塞父任务归档。

---

## 7. 第一阶段：只记录 5 条已确认问题

### 7.1 目标

把已确认问题写入项目可追踪记录。

这一步只做记录，不做代码修复，不改治理规则，不归档任务。

---

### 7.2 建议文件

由 Codex 判断最终文件，但建议候选：

- `CURRENT_CONTEXT.md`
- `changelog/2026-06.md`
- `tasks/MVP_TASK_MAP.md`
- `tasks/active/TASK-EVAL-001-evidence-overlap-evaluation.md`
- `tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`
- 后续如需新建，可创建 `TASK-DEBT-001` 或 `TASK-GOV-003`，但本阶段不默认新建。

---

### 7.3 验收标准

写入后必须能回答：

1. 5 条是否全部出现；
2. 每条是否包含统一模板七项；
3. 是否明确区分“已修复”“未修复”“推迟”；
4. 是否没有把 `TABLE_CELL` 写成代码 bug；
5. 是否没有把 `TASK-EVAL-001-B` 的 1.0 / 1.0 / 1 过度解释为 anchor 客观正确；
6. 是否没有写入未经核实的调研结论；
7. `git diff --check` 通过；
8. `git status --short` 输出清晰，且只包含授权文件。

---

## 8. 第二阶段：CURRENT_CONTEXT.md 逐条认领审计

### 8.1 目标

把 `CURRENT_CONTEXT.md` 从“详细但可信度未知的项目记忆”变成“每条完成断言都能溯源”的可信基线。

---

### 8.2 执行者

必须由独立 agent 执行。

不能由 Codex 执行。
不建议由 Claude Code / DeepSeek 执行。

---

### 8.3 审计方式

将 `CURRENT_CONTEXT.md` 中所有“已完成 / 已确认 / 已修复 / 已通过 / 已 push / 已归档 / 已建立”的断言拆成条目。

每条必须给出：

1. 对应 commit hash；
2. 对应测试方法名；
3. 真实测试命令；
4. 原始 console 输出；
5. 涉及文件；
6. 当前是否仍然属实。

三项核心证据缺任一项，该条降级为：

```text
待确认
```

不得继续保留确定性措辞。

---

### 8.4 阻塞关系

该审计阻塞：

- `TASK-EVAL-001` 父任务归档；
- `TASK-028` Review Intake；
- 任何“基于 CURRENT_CONTEXT.md 已完成状态”的下一阶段推进。

该审计不阻塞：

- 已确认 5 条问题的记录动作；
- 单点 TASK_SPEC 试点；
- 人工 anchor 标注启动。

---

## 9. 第三阶段：角色分离试点

### 9.1 试点原则

第一次试点不选业务风险最高的问题，而选风险最低、范围最小、判断最客观的问题。

因此第一批不修 R44 provenance。

第一批选择：

```text
resolveTextEvidence() 三个信号硬编码为 true
```

理由：

- 范围小；
- 判断客观；
- 不牵动 ratio pipeline；
- 不牵动 parser provenance；
- 不需要人工 anchor 标注；
- 最适合验证 Codex → CC → Codex → 独立 agent 的流程是否真实跑通。

R44 provenance 虽然风险更高，但不适合作为第一次流程试点。否则一旦失败，无法判断是流程问题，还是任务本身复杂度导致失败。

---

### 9.2 第一批 TASK_SPEC：resolveTextEvidence

流程：

```text
Codex 编写冻结 TASK_SPEC
→ Claude Code / DeepSeek 执行
→ Claude Code / DeepSeek 贴实现报告和真实测试输出
→ Codex 对照 TASK_SPEC 逐条审查
→ 独立 agent 抽查流程是否真实发生
```

在 Claude Code / DeepSeek 动手改代码前，必须先提交一段简短“编码前规格映射说明”，说明：

- `roleLabelSignal` 准备依据什么真实命中条件计算；
- `valueFormatSignal` 准备依据什么文本值条件计算；
- `blockAttributionSignal` 准备依据什么 block / anchor 条件计算；
- 哪些路径明确不改，尤其不改 ratio / provenance / early return。

Codex 必须先审查该说明是否符合 TASK_SPEC，放行后 Claude Code / DeepSeek 才能进入代码修改。

TASK_SPEC 必须包含：

- 父任务关联；
- 目标；
- 授权文件；
- 禁止文件；
- 具体断言；
- 测试命令；
- DoD；
- 不允许 commit；
- 不允许 push；
- 不允许扩展到 ratio / provenance / early return。

验收断言必须可证伪，例如：

```text
构造 PARTY_A / PARTY_B 文本候选时，不再出现 candidateForBlock(..., true, true, true)。
roleLabelSignal 必须基于真实 role label / alias 命中。
valueFormatSignal 必须基于候选值是否真实存在且符合文本值要求。
blockAttributionSignal 必须基于候选值是否来自当前 block 且 blockId / anchor 可用。
非 HIGH 候选不得进入确定性裁判。
```

---

### 9.3 Codex 审查要求

Codex 审查时必须逐条输出：

```text
TASK_SPEC 断言 1：满足 / 不满足，证据：
TASK_SPEC 断言 2：满足 / 不满足，证据：
TASK_SPEC 断言 3：满足 / 不满足，证据：
```

不能只写：

```text
diff 看起来合理，测试通过
```

---

### 9.4 独立 agent 抽查

独立 agent 必须核查：

1. 是否真的存在 TASK_SPEC；
2. 是否真的由 CC / DeepSeek 执行；
3. CC / DeepSeek 是否贴了真实命令输出；
4. Codex 是否逐条对照断言审查；
5. Codex 是否没有自己直接改代码；
6. git diff 是否只包含授权文件。

---

## 10. 第四阶段：第二批同根因修复

### 10.1 问题

第二批处理：

```text
collectPatternCandidates() 中 valueFormatSignal 硬编码为 true
```

该问题与第一批 `resolveTextEvidence()` 同属一个抽象根因：

```text
候选信号硬编码，而不是基于真实证据计算。
```

---

### 10.2 新增硬性要求：同根因一致性检查

第二批 TASK_SPEC 必须明确要求：

```text
本次修复必须对照第一批 resolveTextEvidence 的信号计算原则。
必须说明 roleLabelSignal / valueFormatSignal / blockAttributionSignal 的语义是否保持一致。
如采用不同判断标准，必须说明差异理由。
Codex 审查时必须接受或拒绝该差异理由。
```

---

### 10.3 防止新路径割裂

第二批审查时必须检查：

- 是否只是把 `true` 换成另一种伪信号；
- 是否为了某条路径写了特例；
- 是否与第一批文本路径的信号原则冲突；
- 是否改变了 MinimalCandidateResolver 五档语义；
- 是否改变了 admission gate；
- 是否让非 HIGH 候选进入确定性裁判。

---

### 10.4 第二批独立 agent 抽查追加项

第二批不得只复用第一批抽查清单。除第 9.4 节的通用抽查项外，独立 agent 还必须额外核查：

1. 第二批 TASK_SPEC 是否明确写入“同根因一致性检查”；
2. Claude Code / DeepSeek 的实现报告中是否真的说明了与第一批 `resolveTextEvidence()` 信号计算原则的一致或差异；
3. 该说明是否具体到 `roleLabelSignal` / `valueFormatSignal` / `blockAttributionSignal`，而不是一句“已保持一致”；
4. Codex 是否对该一致性说明逐条审查并给出接受 / 拒绝理由；
5. 如果存在差异，差异理由是否与候选类型差异相关，而不是为了让某个路径通过测试写出的特例；
6. 独立 agent 应判断该一致性说明是否经得起推敲，不能只接受 Codex 的自述。

---

## 11. 第五阶段：provenance 与 early return 分流

### 11.1 provenance

问题：

```text
SOURCE_ORIGIN / SOURCE_EXTRACTION_MODE / CONTEXT_TYPE 仍为常量，并无条件覆盖 parser 真实输出。
```

处理建议：

- 当前不混入第一批 / 第二批试点；
- 先写入债务记录；
- 后续单独任务处理；
- 候选目标任务：TASK-032 或独立 parser provenance task。

原因：

- 涉及 parser provenance 数据契约；
- 影响 SourceAnchor / contextType / TOC / diagnostic evidence；
- 风险较高，不适合作为首个流程试点。

---

### 11.2 early return

问题：

```text
resolveRatioEvidence() 语义候选非空时 early return，跳过 fallback 候选收集。
```

处理建议：

- 当前不修；
- 必须写入标准推迟记录；
- 目标任务：TASK-032 或独立候选收集 pipeline task。

原因：

- 修复会牵动候选收集 pipeline；
- 需要“全部收集 → 合并去重 → 统一送 resolver”；
- 与 TASK-032 拆分类 / pipeline 重构重叠；
- 不适合作为小修。

---

## 12. 第六阶段：TABLE_CELL 覆盖补强

### 12.1 性质

`TABLE_CELL` 不是代码 bug，而是覆盖盲区。

当前状态：

- block anchor 已有真实 DOCX fixture 覆盖；
- table-row anchor 已有真实 DOCX fixture 覆盖；
- table-cell anchor 仅 mock / test-only 覆盖；
- 尚无真实 `.docx` 端到端触发。

---

### 12.2 排序依赖

`TABLE_CELL` 不进入代码修复流水线。

它必须依赖第四类人工 anchor 标注完成后才能启动。

前置条件：

```text
至少一个真实 .docx 中的 table-cell anchor 标准答案，由人工独立于 parser 输出标注完成。
```

---

### 12.3 人工标注要求

人工标注者必须：

1. 打开真实 `.docx`；
2. 不查看 parser 输出；
3. 记录候选值；
4. 记录候选值所在表格；
5. 记录所在行；
6. 记录所在单元格；
7. 给出可人工复核的位置说明。

输出示例：

```text
fixture:
CQCP-MVP-DOCX-XXX.docx

reviewPointCode:
PARTY_A_NAME_CONSISTENCY

candidateValue:
奔腾公司

人工位置:
首页基本信息表，第 1 行，第 2 个单元格

人工说明:
该单元格为发包人/甲方名称字段，内容为“奔腾公司”。

是否参考 parser 输出:
否
```

---

### 12.4 后续执行

人工标注完成后：

```text
CC 将人工标准答案写入 expected JSON / fixture
Codex 审查 expected 内容是否忠实反映人工标注
独立 agent 核查是否存在 parser 输出倒填 expected
运行 evidence overlap baseline
```

如果人工标注与 parser 输出不一致：

```text
不得直接改 expected 迁就 parser
必须记录差异
由 Codex 判断是 parser 问题、标注问题，还是 fixture 选择问题
```

若差异指向 parser 真实缺陷，应立即进入分流判断：

- 记录差异证据和人工标注依据；
- 判断严重程度和影响范围；
- 决定是否需要插入到当前执行顺序中优先处理；
- 不得默认把该问题排到所有既定批次之后；
- 不得在未解释差异前继续宣称 table-cell anchor 已被真实 DOCX 验证。

---

## 13. 第七阶段：TASK-EVAL-001-B 重新判断

### 13.1 当前判断

`TASK-EVAL-001-B` 暂不提交。

原因：

- 5 条已确认问题尚未写入可追踪记录；
- `TASK-EVAL-001-B` 的 1.0 / 1.0 / 1 指标解释边界尚需修正；
- `TABLE_CELL` 真实 DOCX 覆盖盲区需明确标注；
- CURRENT_CONTEXT 仍需逐条认领审计；
- 父任务归档前必须有独立 agent 审计。

---

### 13.2 B 可提交前条件

至少满足：

1. 5 条问题已写入标准记录；
2. `TASK-EVAL-001-B` 文档明确写明：
   - 1.0 / 1.0 / 1 是真实计算；
   - 但 expected blockId 来自 parser 内部标识符；
   - 当前主要证明 parser-backed 输出与 expected JSON 的一致性和回归稳定性；
   - 不单独证明 parser anchor 位置客观正确；
   - candidateValue 有独立 matrix 标注；
   - TABLE_CELL 真实 DOCX fixture 覆盖不足；
3. B 的工作区变更清单清楚；
4. 定向测试有真实输出；
5. `git diff --check` 通过；
6. `git status --short` 中只有授权文件；
7. 独立 agent 已完成 B 提交前只读审计。

---

### 13.3 父任务归档前条件

`TASK-EVAL-001` 父任务归档前，至少满足：

1. `TASK-EVAL-001-B` 已提交；
2. 父任务 DoD 未被降级；
3. cell-level baseline 的阻塞判断必须以 Step 0 读取的父任务 DoD 原文为准，不得自由解释：
   - 若父任务 DoD 明确要求真实 `.docx` cell-level fixture 或真实 cell-level baseline，则必须完成至少一个人工标注 + 真实 DOCX table-cell fixture 后才可归档；
   - 若父任务 DoD 只要求 evaluator 支持 `TABLE_CELL` canonical key，而未要求真实 cell fixture，则可以不因真实 DOCX table-cell 覆盖缺口阻塞归档，但必须在任务文档中写明“真实 DOCX table-cell 覆盖未完成”，并建立后续覆盖补强任务；
   - 在父任务 DoD 原文未贴出、未审查前，默认不得归档；
4. CURRENT_CONTEXT 父任务相关断言已经独立 agent 核验；
5. 父任务归档前独立审计通过；
6. `TASK-028` 仍未进入；
7. 工作区干净。

---

## 14. 第八阶段：治理规则固化

### 14.1 固化位置

建议写入：

- `AGENTS.md`
- `docs/context-management.md` 或同类流程文档
- `docs/VERIFY.md`
- `tasks/TASK_SPEC_TEMPLATE_CLAUDECODE_DEEPSEEK.md`
- `tasks/TASK_SPEC_REVIEW_TEMPLATE_READONLY.md`
- `changelog/2026-06.md`

具体文件由 Codex 只读判断后冻结。

---

### 14.2 必须固化的规则

#### 规则零：编码前规格映射计划

在 Claude Code / DeepSeek 执行任何代码修改前，必须先提交简短规格映射计划。

该计划至少说明：

- 它如何理解 TASK_SPEC 的验收断言；
- 关键字段或信号将如何从真实输入计算；
- 哪些路径明确不修改；
- 哪些风险不在本任务内处理；
- 预计新增或修改哪些测试。

Codex 必须先审查规格映射计划，确认没有理解偏差后，才允许 Claude Code / DeepSeek 进入实现。

该规则用于事前预防，不能被事后测试通过替代。

---

#### 规则一：跨路径复核

当在某条代码路径上发现一个缺陷并完成修复后，必须主动追问：

```text
同样抽象级别的缺陷，在本模块其他未检查路径上是否也存在？
```

输出结果必须写入任务收尾记录：

```text
已确认不存在
存在且已修
存在但显式推迟
```

抽象级别以缺陷语义根因为准，不以报错堆栈位置为准。

---

#### 规则二：声明能力清查

涉及声明能力、评测指标、`EvidenceSlot` / `CandidateResolver` / `SourceAnchor` 或核心任务归档前，必须对系统中声明存在的枚举、状态、诊断码、SourceAnchor 粒度和关键字段取值做清查。普通低风险文档状态同步不触发本清查。

每个取值标注为：

```text
真实 fixture 端到端触发
test-only / mock / isolated unit test 验证
代码存在但无测试触发
待确认
```

首批对象：

- EvidenceConfidenceLevel: HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN
- SourceAnchor: BLOCK / TABLE_ROW / TABLE_CELL / SECTION / UNAVAILABLE
- PointStatus: PASS / WARNING / ERROR / NOT_CONCLUDED / SKIPPED
- SYS-* / NotConcludedReasonCode
- EvidenceSlot coverage status
- CandidateResolver failure reason

---

#### 规则三：父任务归档前独立审计

`L3 高风险治理`、正式 Milestone、风险触发型 `L2 Feature`，以及已有任务明确要求独立审计的父任务，归档前必须经过一次独立 agent 只读审计。

上述范围没有独立审计，不得归档。未触发风险门禁的普通 `L2 Feature` 可以由 Codex 审查与 CI 验证后，在 Feature 或 Milestone 收口时归档。

普通低风险文档状态同步、changelog 补录、路径修正和 post-merge 状态写回不属于父任务归档，不默认触发独立 agent 审计。

独立审计绑定冻结 diff 或明确 head SHA；内容未变化时，不因 commit、push 或 checks 状态更新重复执行完整审计。人工 ground truth、expected / fixture、核心审核链路、生产激活、数据库 / API / CI / 安全，以及任务文件已有的显式强门禁，仍按高风险范围执行，不得用 Task Level 追溯解除。

审计至少确认：

- 当前工作区状态；
- 任务 DoD 是否真实满足；
- 所有“已完成”断言是否有 commit / test / console 输出；
- 是否存在未记录债务；
- 是否存在循环验证；
- 是否存在 Codex 自写自审绕过 TASK_SPEC 的情况。

---

#### 规则四：TASK_SPEC 验收断言必须可证伪

Codex 写 TASK_SPEC 时，验收标准必须是具体断言，而不是描述性目标。

禁止：

```text
修复信号硬编码问题
```

应写：

```text
构造缺少 role label 的候选时，roleLabelSignal 必须为 false；
构造格式不合法的候选时，valueFormatSignal 必须为 false；
非 HIGH 候选不得进入确定性裁判。
```

---

#### 规则五：评测正确答案来源检查

所有评测 / fixture / expected JSON 审查都必须回答：

```text
正确答案是怎么定出来的？
是否依赖被测系统自身输出？
是否存在 parser 输出倒填 expected 的循环验证？
```

如存在循环验证，必须明确写明该评测只证明一致性，不证明独立正确性。

---

## 15. 建议最终执行顺序

### Step 0：Codex 只读确认工作区与父任务 DoD

输出 git 状态、ahead/behind、未提交文件清单，并贴出 `TASK-EVAL-001` 父任务 DoD 原文。

未贴出 DoD 原文前，不得判断 `TABLE_CELL` 真实 DOCX 覆盖缺口是否阻塞父任务归档。

---

### Step 1：Codex 写入 5 条已确认问题标准记录

只做记录，不修代码。

完成后由用户 / 外部审查抽查。

---

### Step 2：独立 agent 执行 CURRENT_CONTEXT.md 逐条认领审计

阻塞 TASK-EVAL-001 归档和 TASK-028。

---

### Step 3：Codex 编写第一批 TASK_SPEC

主题：

```text
resolveTextEvidence() confidence signal hardcode cleanup
```

不实际派发前，先给用户 / 外部审查看 TASK_SPEC 草案。

---

### Step 4：Claude Code / DeepSeek 执行第一批 TASK_SPEC

先提交编码前规格映射说明，说明三类信号的计算计划；Codex 放行后再改代码。

只修授权文件。
贴真实测试输出。
不 commit，不 push。

---

### Step 5：Codex 审查第一批实现

逐条对照 TASK_SPEC 断言。
输出接受 / 拒绝。
不得只看 diff 是否合理。

---

### Step 6：独立 agent 抽查第一批流程

确认：

- CC 是否真的执行；
- Codex 是否真的审查；
- 是否有越权文件；
- 是否有未运行测试；
- 是否存在 Codex 自写自审。

---

### Step 7：第二批 TASK_SPEC

主题：

```text
collectPatternCandidates() valueFormatSignal hardcode cleanup
```

必须包含同根因一致性检查：

```text
对照第一批 resolveTextEvidence 的信号计算原则，说明一致或差异理由。
```

第二批独立 agent 抽查必须额外核查该一致性说明是否真实存在、内容是否具体、Codex 是否真正审查。

---

### Step 8：provenance 与 early return 分别建立后续任务

不混入前两批。

---

### Step 9：人工 anchor 标注

至少完成一个真实 `.docx` 的 table-cell 人工独立标注。

如果人工标注与 parser 输出不一致，先记录差异并判断严重程度，再决定是否插入新的修复任务；不得默认把该问题排到最后处理。

---

### Step 10：CC 将人工标注转为 fixture / expected JSON / 测试

Codex 审查。
独立 agent 检查是否循环验证。

---

### Step 11：重新判断 TASK-EVAL-001-B 是否可提交

满足条件后再提交。

---

### Step 12：TASK-EVAL-001 父任务归档前审计

独立 agent 执行。
通过后才归档。

---

### Step 13：治理规则固化

建 TASK-GOV-003 或等价治理任务。

---

### Step 14：重新评估 TASK-028 / TASK-029 / TASK-030 顺序

只有在前置治理和 TASK-EVAL-001 收口完成后再评估。

---

## 16. 禁止事项

在完成上述前，不得：

- 进入 TASK-028；
- 进入 TASK-031；
- 进入 TASK-032 实现；
- push；
- 归档 TASK-EVAL-001；
- 把 `CURRENT_CONTEXT.md` 当作完成凭证；
- 用 parser 输出倒填 expected 后宣称 anchor 已独立正确；
- 让 Codex 自己写代码、自己审查、自己宣布流程通过；
- 让 CC 兼任项目记忆审计者；
- 让 AI agent 替代人工定义合同 anchor 标准答案；
- 把治理规则、业务代码修复、评测重标注混在一个 commit；
- 使用 `git add .`。

---

## 17. commit 拆分建议

至少拆成以下独立提交，不混合。

### Commit 1：记录已确认问题

```text
docs(task): record verified review debts and coverage gaps
```

内容：

- 5 条问题标准记录；
- 不改生产代码；
- 不改测试逻辑。

---

### Commit 2：第一批 TASK_SPEC 试点修复

```text
fix(reviewengine): compute text evidence confidence signals
```

内容：

- 修 `resolveTextEvidence()`；
- 对应测试；
- 不改 ratio；
- 不改 provenance；
- 不改 early return。

---

### Commit 3：第二批同根因修复

```text
fix(reviewengine): compute pattern candidate format signals
```

内容：

- 修 `collectPatternCandidates()`；
- 对照第一批信号原则；
- 对应测试。

---

### Commit 4：人工 anchor 标注与 fixture 补强

```text
test(reviewengine): add independently annotated table cell anchor fixture
```

内容：

- 人工标注说明；
- expected JSON；
- 测试；
- 不改生产逻辑；
- 如果 parser 与人工标注不一致，应先记录差异并另开修复任务，且由 Codex 判断该新任务是否需要插入到既定顺序之前。

---

### Commit 5：治理规则固化

```text
docs(governance): add independent review and coverage gates
```

内容：

- AGENTS.md；
- context-management / VERIFY；
- TASK_SPEC 模板；
- readonly review 模板。

---

## 18. 最终判断

这套计划的核心原则是：

```text
判断什么是对的，不能由被判断对象自己完成。
```

因此：

- Codex 不能独自判断自己的代码和项目记忆是否正确；
- CC 不能同时做实现者和项目记忆审计者；
- AI agent 不能替代人工定义合同标准答案；
- 独立 agent 必须用于事实核查；
- 外部审查负责判断报告是否可信；
- 用户负责决定何时启动独立抽查和是否允许 push。

按这个顺序推进，才能同时解决当前代码债务、评测可信度和长期治理机制三个问题。
