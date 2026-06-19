# ADR-014：最小 CandidateResolver 置信度闸门

状态：已接受
日期：2026-06-18

## 背景

在 parser-backed 主链路最小接入后，`ParserBackedReviewInputPreparer` 仍存在以下问题：
- evidence 默认写死为 `HIGH`
- 同 role 候选竞争没有最小检测
- 负向与正向 fixture 的验收长期只看 `PointStatus`，未钉住证据值

这会导致系统对不确定证据输出伪造的高置信结论，违背“证据不足不生成业务 finding”的基本约束。

## 决策

本 ADR 只覆盖最小 `CandidateResolver` / 轻量 evidence admission 闸门，不扩展到完整 `EvidenceSlot / SourceAnchor` 生命周期治理。

### 1. 固定 5 档置信度

- `HIGH`
- `MEDIUM`
- `LOW`
- `CONFLICTED`
- `UNKNOWN`

### 2. 固定边界定义

- `UNKNOWN`
  - 目标证据范围内没有任何候选
- `LOW`
  - 有候选，但既没有足够角色归属信号，也没有形成接近可判定的唯一候选
- `MEDIUM`
  - 有唯一候选，且命中了部分角色归属信号，但仍缺一项关键确定性条件，因此不能进入裁判
- `CONFLICTED`
  - 同一 review point / role 下存在多个竞争候选
- `HIGH`
  - 唯一候选且满足该 review point 的最小确定性条件，可进入最终裁判

### 3. 固定最小可执行判定规则

- 候选列表为空 -> `UNKNOWN`
- 候选列表非空，且存在同 role 竞争 -> `CONFLICTED`
- 候选列表非空，存在唯一候选，且同时命中
  - role 标签信号
  - 值格式信号
  - block 归属信号
  -> `HIGH`
- 候选列表非空，存在唯一候选，命中了 role 标签信号，但缺失值格式确认或 block 归属确认之一 -> `MEDIUM`
- 其余非空情况 -> `LOW`

### 4. 固定行为闸门

- 只有 `HIGH` 可以进入确定性裁判并产出业务 `PASS / ERROR / WARNING`
- `MEDIUM / LOW / CONFLICTED / UNKNOWN` 一律产出 `SYS-*`
- 上述四类统一映射为 `NOT_CONCLUDED`

### 5. 固定执行顺序

parser-backed evidence 构建时必须遵循以下顺序：

1. 先收集原始候选集合
2. 先执行最小 resolver，对原始候选做竞争检测与置信度计算
3. 只有 resolver 输出 `HIGH`，才允许进入值选择与最终 evidence 组装
4. fallback 不得在 resolver 之前删除、替换或屏蔽原始候选
5. 若原始候选层面已构成竞争，不能再被 fallback “修回” `HIGH`

### 6. 适用范围

本 ADR 同时覆盖：
- 文本类 evidence
  - `PARTY_A_NAME_CONSISTENCY`
  - `PARTY_B_NAME_CONSISTENCY`
- 数值 / 比例类 evidence
  - 合同总金额
  - 税额
  - 预付款比例
  - 进度款比例
  - 竣工款比例
  - 结算款比例
  - 质保金比例

两类路径不要求共享完全一致的实现细节，但必须共享同一套置信度语义和 admission gate。

澄清说明：
- `paymentClauseBlocks` 的位置切片机制当前属于“已实现但未被现有 4 正 4 负样本证明生效”的状态。
- 现阶段样本可观测到的候选范围限定主要仍来自内容关键词过滤，而不是位置切片本身。
- 后续若新增表达差异较大的合同样本，应重新验证位置切片是否真正贡献了候选收敛。

### 7. 对历史 PASS 回退的处理

若真实置信度上线后，某些历史 `PASS` 回退为 `NOT_CONCLUDED`：

1. 先判断该合同原文是否客观存在候选歧义
2. 若存在真实歧义，则接受其变为 `NOT_CONCLUDED`
3. 若原文并不歧义，而是 resolver 未识别出唯一候选，则修 resolver
4. 不允许默认新增启发式或 regex 只为把该点“修回 PASS”

## 后果

正面影响：
- 去除伪造的 `HIGH`
- evidence admission 有了最小可解释闸门
- 后续 `TASK-027` / `TASK-028` 有了稳定的上游语义

负面影响：
- 一部分历史样例可能因真实歧义而从 `PASS` 回退到 `NOT_CONCLUDED`
- `ParserBackedReviewInputPreparer` 短期内仍是单体类，需要后续 `TASK-032` 拆分

## 不包含

本 ADR 不包含：
- 完整 `EvidenceSlot / SourceAnchor` 治理
- Gemma provider 接入
- 新 API / 数据库 / Docker 改动
