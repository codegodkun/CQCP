# VERIFY.md

## 目的

本文档定义 CQCP 的任务验收与提交前验证标准，避免把“看起来完成”误当成“可以提交”。

## 通用验证原则

- 先验证任务边界，再验证实现结果
- 只运行与当前任务直接相关的验证，不做无关的大范围测试
- 验证结论必须可复述，不能只说“应该没问题”
- 有失败、有不确定、有越界风险时，不应提交
- 验证深度按 Task Level 和实际风险决定，不因文件少或 `docs:` 前缀自动降级

## Task Level 验证矩阵

| Level | 最低验证 | 独立审计 |
|---|---|---|
| `L0 探索` | 核对事实来源，明确“已验证 / 推断 / 待确认”；throwaway 产物不得进入主线 | 默认不需要 |
| `L1 小文档` | `git diff --check`、链接/路径/事实核对、Codex 自查 | 默认不需要 |
| `L2 Feature` | 完成态 diff、定向测试、必要回归、CI、Feature 验收 | 风险触发 |
| `L3 高风险治理` | 冻结 diff/head SHA、强门禁定向测试、必要回归、CI、逐条验收 | 必须 |

独立审计验证的是冻结内容，不是 push 这个传输动作。审计后 diff 未变化时，push 后核对远端 head SHA、PR diff 和 CI 即可；审计后受审内容变化时，原审计结论失效。

## 标准验证环境

- Docker Compose 是唯一标准验证环境
- 当前实际入口为 `deploy/compose/compose.yml`
- 环境变量样例为 `deploy/env/.env.example`
- 如果本地手工运行结果与 Compose 结果不一致，以 Compose 验证为准

## 页面类任务验收清单

- 页面是否能正常进入目标路由
- 页面是否能在桌面端和移动端最小可用
- 是否只展示当前任务允许展示的字段
- 是否没有泄露内部诊断、敏感字段或调试信息
- 是否补充了必要的前端测试或构建验证

## API 类任务验收清单

- 接口路径、状态码和错误语义是否与任务约定一致
- 是否没有顺手改变既有契约
- 是否覆盖关键成功 / 失败 / 冲突分支
- 是否验证了调用方可见行为，而不只验证内部实现

## 数据库 / migration 类任务验收清单

- migration 是否与任务范围完全一致
- 是否验证了新旧数据兼容性
- 是否确认未误改历史 migration
- 是否验证了启动或回归场景不会因 schema 变更直接失败

## 管理台任务验收清单

- 是否与公开结果页边界分离
- 是否只展示允许暴露给管理台的诊断信息
- 是否没有混入 prompt、raw output、stack trace、secret、admin logs
- 是否验证了查询入口、摘要、状态和诊断展示

## 公开结果页任务验收清单

- 是否围绕“审核点 -> 证据 -> 原文定位”展示
- 是否与 `SYS-*` 系统诊断分流
- 是否没有暴露内部调试细节
- 是否保持对外结果语义稳定

## 文档类任务验收清单

- Markdown 结构是否清晰
- 文档路径、文件链接和命令示例是否合理
- 是否没有把任务状态硬编码到不应承载状态的入口文档
- 是否没有误改代码、数据库、Docker 或架构文件
- 是否完成必要的 Memory Writeback
- 是否正确选择 L0/L1/L2/L3，避免把治理语义变化误标为“小文档”
- 是否没有为普通状态、push/checks 或不改变门禁的 post-merge 事实制造独立收口提交

## MVP 端到端样本验收规格

`TASK-033` 负责冻结 MVP 端到端样本验收规格，任务文件为 `tasks/done/TASK-033-mvp-e2e-sample-acceptance-spec-freeze.md`。

验收规格必须满足：

- 样本从 `packages/test-fixtures/README.md` 已登记的 DOCX 样本池中选择，不临时新增或修改样本。
- 每份样本必须冻结 `sampleId`、DOCX 路径、合同类型、审核点范围、expected 来源、anchor 要求和可观测输出。
- expected 来源必须逐项说明；parser actual、AI 输出或被测系统输出不得倒填为人工 anchor 标准答案。
- parser-backed overlap 指标只能证明与当前 expected JSON 的一致性和回归稳定性，不证明独立人工 ground truth 正确。
- 真实 DOCX `TABLE_CELL` anchor 必须依赖 `TASK-DATA-001` 或等效人工标注任务产出的独立人工标准答案。
- 本规格不归档 `TASK-EVAL-001`，不补足 DoD #12，不解除 `TASK-028` / `TASK-031` / `TASK-032` 门禁。
- 正式提交前必须进行独立 agent 只读复核，复核不得修改文件。

## 提交前 Checklist

- 确认 Task Level、Integration unit 和授权包络仍有效
- 运行 `git diff --check`
- 运行 `git status --short`
- 确认只包含当前 Feature 或 L3 风险边界的授权文件
- 确认没有日志、缓存、输出物或临时文件
- 确认没有使用 `git add .`
- 确认未包含未经授权的 `push`
- 如创建或更新 PR，确认 PR body 已填写 `CQCP Authorization Evidence` 区块
- 如涉及 commit / push / merge，确认 PR body 或交付摘要记录了真实授权包络、适用步骤和失效条件，不以模板默认文字替代授权

## 完成态复核材料

L2 普通 Feature 默认只在完成态做一次复核；L3 在冻结门禁和最终收口时复核。提交完成态复核时，材料至少包括：

- `git status --short`
- `git status -sb`
- `git diff --name-status`
- `git diff --stat`
- `git diff --check`
- 实际测试命令和 console 输出
- 修改文件清单
- 是否越界
- 是否更新必要项目记忆

完成态复核用于判断是否接纳、是否提交、是否合并、是否进入下一任务；不得用复核替代任务执行、规格冻结或 Codex Review Intake Decision。

同一父 TASK 下多个 TASK_SPEC 可以共享一个 Feature 完成态复核，但材料必须逐项映射每个 TASK_SPEC 的可证伪验收断言。共享复核不允许隐藏未完成规格或范围外变化。

## PR 授权证据检查

`Authorization evidence check` 是 PR body 文本门禁，最低验证以下字段存在且非占位：

- `Task`
- `Governance mode`
- `Allowed files`
- `Forbidden files`
- `Commit authorization`
- `Push authorization`
- `Merge authorization`
- `Test evidence`
- `Independent review`
- `Memory writeback`
- `Out-of-scope confirmation`

该检查不替代真实命令输出、独立只读审计、Codex Review Intake Decision、用户授权、GitHub branch protection 或 required status checks 配置。字段真实性仍需在 Review Intake、独立审计和用户决策中核查。

自动检查只读取冒号后同一行的字段值；多行补充说明可以存在，但不能替代同一行摘要值。

## 验证失败时如何处理

- 先记录失败现象和影响范围
- 判断是环境问题、边界问题还是实现问题
- 如果无法快速确认原因，停止扩大范围
- 需要用户决策时，明确列出阻塞点，不自行推进到下一任务

## 哪些情况禁止提交

- 工作区存在与当前任务无关的未提交改动
- 验证失败且原因不明
- 需要修改禁止范围文件才能“补救”
- 本次提交混入代码、配置、迁移或临时产物
- 架构变更未记录 ADR
- 任务结论仍依赖“待确认”信息
- Task Level 被低估，或 Feature / L3 风险边界尚未冻结
- 授权包络已经因文件范围、行为语义、head diff 或风险等级变化而失效

## 独立审计触发

以下内容必须保持独立只读审计：

- L3 父任务归档、正式 Milestone 验收和生产激活；
- 核心审核链路、模型职责、SYS/Finding、EvidenceSlot、ReviewPointFamily、CandidateResolver、SourceAnchor；
- ReviewResultSnapshot、Task/Execution 状态机语义；
- 数据库 migration、breaking API/OpenAPI、权限、安全和敏感诊断；
- 人工 ground truth、fixture、expected JSON、评测口径和指标计算；
- RuleSet、模型或 EvidenceSelector 的生产激活；
- workflow、CI、required checks、branch protection 和授权门禁；
- Codex 自己实现代码后又负责接纳；
- 工作区、提交来源、远程 head SHA 或审计基线不清。

普通 L1 和未触发上述风险的 L2 不强制独立 agent，由 Codex Review Intake、测试和 CI 收口。已有任务文件明确要求的独立审计继续有效。
