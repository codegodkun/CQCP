# TASK-EVAL-005 Track B fifth 12-packet 人工确认表

状态：**待人工确认；当前不是 ground truth**

语料 SHA-256：`17343dd34820c847676ed681a8a4e6ee9f669c60cf3c0257aa2363ba4bba8f5e`

确认范围：12 个新建、合成、最小 EvidencePacket；9 个为可调用
MEDIUM/CONFLICTED 歧义，3 个分别验证 deterministic HIGH、invalid bundle 与
无可靠 anchor 的 zero-call。

请逐项核对候选原文、角色、选择和 abstention。只有项目负责人明确确认后，
Codex 才会封存人工 ground truth。确认前不得创建 model input、不得派发 Codex
blind evaluator，也不得发起任何模型调用或 DeepSeek 网络调用。

| Case | 类别 | 建议选择 | 建议理由 | 人工决定 |
|---|---|---|---|---|
| TB5-MED-001 | MEDIUM | OCC-001 | “本合同合同甲方”直接标识目标 role；附属参考记录不属于该 role。 | 待确认 |
| TB5-MED-002 | MEDIUM | OCC-002 | “本合同合同乙方”直接标识目标 role；附属参考记录不属于该 role。 | 待确认 |
| TB5-MED-003 | MEDIUM | OCC-001 | “本合同合同含税总金额”直接标识目标 role；附属参考记录不属于该 role。 | 待确认 |
| TB5-MED-004 | MEDIUM | OCC-002 | “本合同预付款比例”直接标识目标 role；附属参考记录不属于该 role。 | 待确认 |
| TB5-MED-005 | MEDIUM | OCC-001 | “本合同质保金比例”直接标识目标 role；附属参考记录不属于该 role。 | 待确认 |
| TB5-CON-001 | CONFLICTED | OCC-002 | “本合同合同甲方”直接标识目标 role；附属参考记录不属于该 role。 | 待确认 |
| TB5-CON-002 | CONFLICTED | OCC-001 | “本合同合同税额”直接标识目标 role；附属参考记录不属于该 role。 | 待确认 |
| TB5-CON-003 | CONFLICTED | OCC-002 | “本合同进度款比例”直接标识目标 role；附属参考记录不属于该 role。 | 待确认 |
| TB5-CON-004 | CONFLICTED | OCC-001 | “本合同结算款比例”直接标识目标 role；附属参考记录不属于该 role。 | 待确认 |
| TB5-CTL-HIGH-001 | HIGH | 无（拒答） | 确定性 HIGH 必须保持 zero-call。 | 待确认 |
| TB5-CTL-INVALID-001 | MEDIUM | 无（拒答） | 不可靠 TABLE_CELL identity 必须 fail closed。 | 待确认 |
| TB5-CTL-NOANCHOR-001 | MEDIUM | 无（拒答） | 无可靠 anchor 必须保持 zero-call。 | 待确认 |

## 候选原文与 SourceAnchor 逐项核对

### TB5-MED-001

- 类别：`MEDIUM`
- 新 packetId：`EP-84165b6480d8ea36e838c4b1233892d36755410741cc27c2b40a5ebb419cd6d2`
- 建议角色：`PARTY_A`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tb5-med-001-b01`
- 建议 abstention：`false`
- 建议理由：“本合同合同甲方”直接标识目标 role；附属参考记录不属于该 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-MED-001-01-PARTY_A | 经双方确认，本合同合同甲方为「玄穹-TB5-MED-001-01-PARTY_A」。 | tb5-med-001-b01 |
| OCC-002 | 玄穹-TB5-MED-001-02-PARTY_A | 附属参考记录（非合同甲方）载明代号「玄穹-TB5-MED-001-02-PARTY_A」。 | tb5-med-001-b02 |

### TB5-MED-002

- 类别：`MEDIUM`
- 新 packetId：`EP-5831f3912116f499540c351af939c4a7aebc0579cd59a70f82c24138382bfbad`
- 建议角色：`PARTY_B`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb5-med-002-b02`
- 建议 abstention：`false`
- 建议理由：“本合同合同乙方”直接标识目标 role；附属参考记录不属于该 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-MED-002-01-PARTY_B | 附属参考记录（非合同乙方）载明代号「玄穹-TB5-MED-002-01-PARTY_B」。 | tb5-med-002-b01 |
| OCC-002 | 玄穹-TB5-MED-002-02-PARTY_B | 经双方确认，本合同合同乙方为「玄穹-TB5-MED-002-02-PARTY_B」。 | tb5-med-002-b02 |

### TB5-MED-003

- 类别：`MEDIUM`
- 新 packetId：`EP-10f7d720318a92b4755111b9ba91e780e42f1232889dd5c21e8aad4357dc4723`
- 建议角色：`CONTRACT_TOTAL_AMOUNT`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tb5-med-003-table-01`
- 建议 abstention：`false`
- 建议理由：“本合同合同含税总金额”直接标识目标 role；附属参考记录不属于该 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-MED-003-01-CONTRACT_TOTAL_AMOUNT | 经双方确认，本合同合同含税总金额为「玄穹-TB5-MED-003-01-CONTRACT_TOTAL_AMOUNT」。 | tb5-med-003-table-01 / table:tb5_med_003/row:1/cell:2 |
| OCC-002 | 玄穹-TB5-MED-003-02-CONTRACT_TOTAL_AMOUNT | 附属参考记录（非合同含税总金额）载明代号「玄穹-TB5-MED-003-02-CONTRACT_TOTAL_AMOUNT」。 | tb5-med-003-table-01 / table:tb5_med_003/row:2/cell:2 |

### TB5-MED-004

- 类别：`MEDIUM`
- 新 packetId：`EP-e43b8433e4485af3f1a6eef4b7c1f743c465adeef98d5034705df9330ceec239`
- 建议角色：`PREPAYMENT_RATIO`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb5-med-004-b02`
- 建议 abstention：`false`
- 建议理由：“本合同预付款比例”直接标识目标 role；附属参考记录不属于该 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-MED-004-01-PREPAYMENT_RATIO | 附属参考记录（非预付款比例）载明代号「玄穹-TB5-MED-004-01-PREPAYMENT_RATIO」。 | tb5-med-004-b01 |
| OCC-002 | 玄穹-TB5-MED-004-02-PREPAYMENT_RATIO | 经双方确认，本合同预付款比例为「玄穹-TB5-MED-004-02-PREPAYMENT_RATIO」。 | tb5-med-004-b02 |

### TB5-MED-005

- 类别：`MEDIUM`
- 新 packetId：`EP-5d69a7916c3ea077e12b662b5904e2de77e9e9de467be8b23223558844fda5fd`
- 建议角色：`WARRANTY_RETENTION_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tb5-med-005-b01`
- 建议 abstention：`false`
- 建议理由：“本合同质保金比例”直接标识目标 role；附属参考记录不属于该 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-MED-005-01-WARRANTY_RETENTION_RATIO | 经双方确认，本合同质保金比例为「玄穹-TB5-MED-005-01-WARRANTY_RETENTION_RATIO」。 | tb5-med-005-b01 |
| OCC-002 | 玄穹-TB5-MED-005-02-WARRANTY_RETENTION_RATIO | 附属参考记录（非质保金比例）载明代号「玄穹-TB5-MED-005-02-WARRANTY_RETENTION_RATIO」。 | tb5-med-005-b02 |

### TB5-CON-001

- 类别：`CONFLICTED`
- 新 packetId：`EP-1c4bd4f0cc79234a8d0938fddaa6652ea36d7c3652b5e1bafaeccf10540b3811`
- 建议角色：`PARTY_A`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb5-con-001-b01`
- 建议 abstention：`false`
- 建议理由：“本合同合同甲方”直接标识目标 role；附属参考记录不属于该 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-CON-001-01-PARTY_A | 附属参考记录（非合同甲方）载明代号「玄穹-TB5-CON-001-01-PARTY_A」。 | tb5-con-001-b01 |
| OCC-002 | 玄穹-TB5-CON-001-02-PARTY_A | 经双方确认，本合同合同甲方为「玄穹-TB5-CON-001-02-PARTY_A」。 | tb5-con-001-b01 |

### TB5-CON-002

- 类别：`CONFLICTED`
- 新 packetId：`EP-23b06c3d7bd61056f7b5ad6019fcdf8a6291d95cd098f04db917b76023b74a7a`
- 建议角色：`TAX_AMOUNT`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tb5-con-002-table-01`
- 建议 abstention：`false`
- 建议理由：“本合同合同税额”直接标识目标 role；附属参考记录不属于该 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-CON-002-01-TAX_AMOUNT | 经双方确认，本合同合同税额为「玄穹-TB5-CON-002-01-TAX_AMOUNT」。 | tb5-con-002-table-01 / table:tb5_con_002/row:1/cell:1 |
| OCC-002 | 玄穹-TB5-CON-002-02-TAX_AMOUNT | 附属参考记录（非合同税额）载明代号「玄穹-TB5-CON-002-02-TAX_AMOUNT」。 | tb5-con-002-table-01 / table:tb5_con_002/row:1/cell:2 |
| OCC-003 | 玄穹-TB5-CON-002-03-TAX_AMOUNT | 附属参考记录（非合同税额）载明代号「玄穹-TB5-CON-002-03-TAX_AMOUNT」。 | tb5-con-002-table-01 / table:tb5_con_002/row:1/cell:3 |

### TB5-CON-003

- 类别：`CONFLICTED`
- 新 packetId：`EP-b0e11f150650818fbd48bf8f48c2312e303de96885343acfcd5ed035ea3b78e1`
- 建议角色：`PROGRESS_PAYMENT_RATIO`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb5-con-003-b02`
- 建议 abstention：`false`
- 建议理由：“本合同进度款比例”直接标识目标 role；附属参考记录不属于该 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-CON-003-01-PROGRESS_PAYMENT_RATIO | 附属参考记录（非进度款比例）载明代号「玄穹-TB5-CON-003-01-PROGRESS_PAYMENT_RATIO」。 | tb5-con-003-b01 |
| OCC-002 | 玄穹-TB5-CON-003-02-PROGRESS_PAYMENT_RATIO | 经双方确认，本合同进度款比例为「玄穹-TB5-CON-003-02-PROGRESS_PAYMENT_RATIO」。 | tb5-con-003-b02 |
| OCC-003 | 玄穹-TB5-CON-003-03-PROGRESS_PAYMENT_RATIO | 附属参考记录仅载明流程编号，不构成目标比例候选证据。 | tb5-con-003-b03 |

### TB5-CON-004

- 类别：`CONFLICTED`
- 新 packetId：`EP-170929d81f5ec1c0f1098331aa74973b7ea563a0dbcffcd8f53c36e4dbae9c51`
- 建议角色：`SETTLEMENT_PAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tb5-con-004-b01`
- 建议 abstention：`false`
- 建议理由：“本合同结算款比例”直接标识目标 role；附属参考记录不属于该 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-CON-004-01-SETTLEMENT_PAYMENT_RATIO | 经双方确认，本合同结算款比例为「玄穹-TB5-CON-004-01-SETTLEMENT_PAYMENT_RATIO」。 | tb5-con-004-b01 |
| OCC-002 | 玄穹-TB5-CON-004-02-SETTLEMENT_PAYMENT_RATIO | 附属参考记录（非结算款比例）载明代号「玄穹-TB5-CON-004-02-SETTLEMENT_PAYMENT_RATIO」。 | tb5-con-004-b01 |

### TB5-CTL-HIGH-001

- 类别：`HIGH`
- 新 packetId：`EP-9219f6a7a9ba1844be227b844ad60cae589b9e7208d140cfe11e36eec16cde5d`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`DETERMINISTIC_HIGH_ZERO_CALL`）
- 建议理由：确定性 HIGH 必须保持 zero-call。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-CTL-HIGH-001-01-CONTRACT_TOTAL_AMOUNT | 附属参考记录（非合同含税总金额）载明代号「玄穹-TB5-CTL-HIGH-001-01-CONTRACT_TOTAL_AMOUNT」。 | tb5-ctl-high-001-b01 |

### TB5-CTL-INVALID-001

- 类别：`MEDIUM`
- 新 packetId：`EP-882d93f9711a7e27f7a808065181deb3ffe5f071127da7e14921039fbd94454e`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`BUNDLE_INVALID`）
- 建议理由：不可靠 TABLE_CELL identity 必须 fail closed。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-CTL-INVALID-001-01-PREPAYMENT_RATIO | 附属参考记录（非预付款比例）载明代号「玄穹-TB5-CTL-INVALID-001-01-PREPAYMENT_RATIO」。 | tb5-ctl-invalid-001-table-01 / table:tb5_ctl_invalid/row:x/cell:2 |

### TB5-CTL-NOANCHOR-001

- 类别：`MEDIUM`
- 新 packetId：`EP-3be226f9ac2a6552218e3ac5cdbc1ddc378422cb673d67edfed249d5efa1d36e`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`RELIABLE_ANCHOR_MISSING`）
- 建议理由：无可靠 anchor 必须保持 zero-call。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 玄穹-TB5-CTL-NOANCHOR-001-01-PARTY_B | 附属参考记录（非合同乙方）载明代号「玄穹-TB5-CTL-NOANCHOR-001-01-PARTY_B」。 | tb5-ctl-noanchor-001-b01 |
