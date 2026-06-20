# MVP 任务地图

更新日期：2026-06-20

## 当前结论

- `TASK-024` 已完成并已 push
- `TASK-025` 已完成 fixture 级验收收口并归档
- `TASK-026`：最小 `CandidateResolver` / 置信度分级 / evidence admission 闸门已完成并归档
- `TASK-026` 已完成真实 parser 主链路非 `HIGH` 可达性治理
- `TASK-027` 最小主实现已完成并归档
- `TASK-EVAL-001` Review Intake 结论为 `NEEDS-SPLIT`；原 DoD 不降级
- `TASK-EVAL-001-A` 已建档为当前下一前置任务；`TASK-EVAL-001-B` 依赖 A 完成后再启动
- `TASK-028` 必须等待 `TASK-EVAL-001` 最低评测基线完成后再进入
- `TASK-031` 仍未进入，继续禁止抢跑
- `TASK-032` 已登记为后续重构任务，不在本轮实现

## 已完成主链路任务

| 任务 | 名称 | 类别 | 当前状态 |
|---|---|---|---|
| `TASK-019` | Result Composer + ReviewResultSnapshot 最小合成 | A | 已完成 |
| `TASK-020` | Task Execution 最小状态机 | A | 已完成 |
| `TASK-021` | Result URL 查询 API | A | 已完成 |
| `TASK-022` | Persistent Result Query Adapter | A | 已完成 |
| `TASK-023` | 公开结果页最小展示 | B | 已完成 |
| `TASK-024` | 管理台诊断详情最小展示 | B | 已完成 |
| `TASK-025` | Parser / Candidate / Evidence 主链路接入 | A | 已完成并归档 |

## 当前后续任务

| 任务 | 名称 | 类别 | 当前状态 | 说明 |
|---|---|---|---|---|
| `TASK-026` | 最小 CandidateResolver 置信度治理 | A | 已完成并归档 | 文件：`tasks/done/TASK-026-minimal-candidate-resolver-confidence-governance.md`；已通过真实 parser 主链路 fixture 覆盖 `MEDIUM / LOW / CONFLICTED`，`HIGH` 才可进入确定性裁判 |
| `TASK-027` | EvidenceSlot / SourceAnchor 正式治理 | A | 已完成并归档 | `ADR-015` 已接受；`TASK-027-C`、`TASK-027-D` 与主实现提交 `b85f4dd` 均已完成；完成的是最小主实现落地，不是完整 `EvidenceBundle` 平台化 |
| `TASK-EVAL-001` | Parser-backed 证据重合度评测基线 | A | NEEDS-SPLIT | 父任务原 DoD 不降级；拆为 A 可观测性前置与 B overlap baseline |
| `TASK-EVAL-001-A` | SourceAnchor row/cell observability | A | 已建档 / 待实现 | 当前下一任务；补齐真实 row/cell anchor 在 reviewengine 结果链路的可观测性 |
| `TASK-EVAL-001-B` | Evidence overlap baseline | A | 未启动 / 依赖 A | 暂不创建实现文件；负责 expected anchor、evaluator、4 正 + 4 负及完整指标 |
| `TASK-028` | Gemma Provider 最小接入 | A | 未开始 / 等待评测基线 | 仅作为未来 `MEDIUM` 档辅助通道；依赖 `TASK-EVAL-001` 最低评测基线完成 |
| `TASK-029` | MVP 端到端验证收口 | A | 未开始 | 依赖 `TASK-025` ~ `TASK-028` |
| `TASK-030` | Review assets 版本化治理 | A | 未开始 | 后续治理任务 |
| `TASK-031` | Result API / Admin API mapper 补洞 | B | 未开始 | 当前明确不进入 |
| `TASK-032` | ParserBackedReviewInputPreparer 按 ReviewPointFamily 拆分 | A | 未开始 | `TASK-026` 收口后启动，消除单体类隐式耦合 |

## 任务边界与依赖

### `TASK-025`

- 定位：fixture 级验收任务
- 退出线：
  - 4 个正向 fixture + 4 个负向 fixture
  - `PointStatus + candidateValue + blockId + evidenceSummary`
- 不再继续开放式补规则
- 若后续出现新表达变体，必须新开轻量任务，不回流到 `TASK-025`

### `TASK-026`

- 定位：最小 `CandidateResolver` 治理任务
- 覆盖范围：
  - `HIGH / MEDIUM / LOW / CONFLICTED / UNKNOWN`
  - 同 role 候选竞争检测
  - 真实 parser 主链路非 `HIGH` 可达性治理
  - 只有 `HIGH` 可进入确定性裁判
- 依赖：
  - `TASK-025` 主链路接入已完成
- 不包含：
  - 完整 `EvidenceSlot / SourceAnchor`
  - Gemma 接入

### `TASK-027`

- 定位：完整 `EvidenceSlot / SourceAnchor` 正式治理
- 依赖：
  - `TASK-026`
- 当前状态：
  - `decisions/ADR-015-evidence-slot-source-anchor-governance.md` 已接受
  - `TASK-027-A` / `TASK-027-B` 已完成并被 Codex 接受为有效前置输入
  - `TASK-027-C` 已完成并本地提交 `8e09dc6`
  - `TASK-027-D` 已完成并本地提交 `ed63184`
  - `TASK-027` 最小主实现已完成并本地提交 `b85f4dd`
  - 当前进入归档收口；未进入完整 `EvidenceBundle` 平台化
- 说明：
  - 负责把当前最小 resolver 提升为正式 evidence 生命周期与定位资产

### `TASK-028`

- 定位：Gemma `MEDIUM` 档辅助通道
- 依赖：
  - `TASK-026`
  - `TASK-027`
  - `TASK-EVAL-001` 最低评测基线
- 说明：
  - 仅作为复杂语义辅助，不承担最终确定性裁判

### `TASK-EVAL-001`

- 定位：parser-backed fixture 的证据定位质量评测基线
- 依赖：
  - `TASK-025`
  - `TASK-026`
  - `TASK-027`
- 最低范围：
  - block / table-row / cell level overlap
  - 至少 4 个正向 fixture + 4 个负向 / 冲突 fixture
  - `candidateValue` 正确但 `SourceAnchor` 错误时必须失败
- 不包含：
  - 生产审核语义变更
  - 模型比较或新模型接入
  - parser 替换、检索方案引入或字符级 span 强制评分
- Review Intake：
  - 结论为 `NEEDS-SPLIT`
  - 原 DoD 不降级
  - A 先解决 SourceAnchor row/cell observability
  - B 依赖 A 完成并经 Codex 验收

### `TASK-EVAL-001-A`

- 定位：SourceAnchor row/cell observability 前置任务
- 最低范围：
  - 稳定表达 block + table row
  - cell 仅在具备真实稳定 `cellIndex` 时表达
  - 优先复用 ADR-015 `previewElementRef`
  - 保持旧 block-level 快照兼容读取
- 禁止：
  - 不实现 overlap evaluator
  - 不修改 expected JSON / DOCX fixture
  - 不通过 candidateValue 搜索 cells 伪造 cell anchor
  - 不改变业务 Finding、EvidenceSlot admission 或 CandidateResolver gate

### `TASK-EVAL-001-B`

- 定位：父任务的 evidence overlap baseline 实现阶段
- 依赖：`TASK-EVAL-001-A` 完成并经 Codex 验收
- 最低范围：
  - expected JSON anchor 标注
  - test-only evaluator
  - block / row / cell canonical key
  - 4 正向 + 4 负向/冲突
  - `expectedRecall / actualPrecision / requiredHitRate`
  - `missingExpectedBlocks / unexpectedMatchedBlocks / attributionFailureReason`
- 当前状态：暂不创建实现文件，不得提前启动

## 协作边界

### A 类任务

适用范围：
- 主审核链路
- 状态机
- 证据机制
- 模型职责边界
- 结果快照与契约

规则：
- 由 Codex 主控
- 涉及核心审核链路、`EvidenceSlot`、`CandidateResolver`、`ReviewPointFamily` 等边界时，先记录 ADR

### B 类任务

适用范围：
- 公开页
- 管理台
- 视图层 mapper / view-model

规则：
- 不得反向扩写主链路
- `TASK-031` 不得提前启动

## 当前建议顺序

1. 执行 `TASK-EVAL-001-A`，先补齐 SourceAnchor row/cell observability
2. A 完成并经 Codex 验收后执行 `TASK-EVAL-001-B`
3. `TASK-028` 等待 B 完成父任务最低评测基线后再进入
4. `TASK-029` / `TASK-030` 的后续排序在评测基线完成后重新确认
5. 不进入 `TASK-031` / `TASK-032`
