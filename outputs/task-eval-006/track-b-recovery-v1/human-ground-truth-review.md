# TASK-EVAL-006 Track B recovery 12-packet 人工确认表

状态：**待人工确认；当前不是 ground truth**

语料 SHA-256：`3a988a9ab09008645dd812b47c1ad3f4c6a9aac699a590a66bd6a17cf1010554`

确认范围：12 个新建、合成、最小 EvidencePacket；9 个为可调用
MEDIUM/CONFLICTED 歧义，3 个分别验证 deterministic HIGH、invalid bundle 与
无可靠 anchor 的 zero-call。

请逐项核对候选原文、角色、选择和 abstention。只有项目负责人明确确认后，
Codex 才会封存人工 ground truth。确认前不得创建 model input、不得派发 Codex
blind evaluator，也不得发起任何模型调用或 DeepSeek 网络调用。

| Case | 类别 | 建议选择 | 建议理由 | 人工决定 |
|---|---|---|---|---|
| TB6-MED-001 | MEDIUM | OCC-002 | 当前生效条款直接约定合同甲方；过往示例或流程附件不属于当前目标 role。 | 待确认 |
| TB6-MED-002 | MEDIUM | OCC-001 | 当前生效条款直接约定合同乙方；过往示例或流程附件不属于当前目标 role。 | 待确认 |
| TB6-MED-003 | MEDIUM | OCC-002 | 当前生效条款直接约定合同含税总金额；过往示例或流程附件不属于当前目标 role。 | 待确认 |
| TB6-MED-004 | MEDIUM | OCC-001 | 当前生效条款直接约定预付款比例；过往示例或流程附件不属于当前目标 role。 | 待确认 |
| TB6-MED-005 | MEDIUM | OCC-002 | 当前生效条款直接约定质保金比例；过往示例或流程附件不属于当前目标 role。 | 待确认 |
| TB6-CON-001 | CONFLICTED | OCC-001 | 当前生效条款直接约定合同甲方；过往示例或流程附件不属于当前目标 role。 | 待确认 |
| TB6-CON-002 | CONFLICTED | OCC-002 | 当前生效条款直接约定合同税额；过往示例或流程附件不属于当前目标 role。 | 待确认 |
| TB6-CON-003 | CONFLICTED | OCC-002 | 当前生效条款直接约定进度款比例；过往示例或流程附件不属于当前目标 role。 | 待确认 |
| TB6-CON-004 | CONFLICTED | OCC-002 | 当前生效条款直接约定结算款比例；过往示例或流程附件不属于当前目标 role。 | 待确认 |
| TB6-CTL-HIGH-001 | HIGH | 无（拒答） | 确定性 HIGH 必须保持 zero-call。 | 待确认 |
| TB6-CTL-INVALID-001 | MEDIUM | 无（拒答） | 不可靠 TABLE_CELL identity 必须 fail closed。 | 待确认 |
| TB6-CTL-NOANCHOR-001 | MEDIUM | 无（拒答） | 无可靠 anchor 必须保持 zero-call。 | 待确认 |

## 候选原文与 SourceAnchor 逐项核对

### TB6-MED-001

- 类别：`MEDIUM`
- 新 packetId：`EP-8bc0ead85e0b2a0e930aba68448fc1c0fbf589ec29727b013f316df787e9bad6`
- 建议角色：`PARTY_A`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb6-med-001-b02`
- 建议 abstention：`false`
- 建议理由：当前生效条款直接约定合同甲方；过往示例或流程附件不属于当前目标 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-MED-001-01-PARTY_A | 过往示例台账记录「澄曜-TB6-MED-001-01-PARTY_A」，并注明不适用于本合同当前合同甲方。 | tb6-med-001-b01 |
| OCC-002 | 澄曜-TB6-MED-001-02-PARTY_A | 双方在当前生效条款中明确：本合同合同甲方确定为「澄曜-TB6-MED-001-02-PARTY_A」。 | tb6-med-001-b02 |

### TB6-MED-002

- 类别：`MEDIUM`
- 新 packetId：`EP-ec38279e30374c954f83ba210378f131afb7eda1a088b5b6558386f84236829a`
- 建议角色：`PARTY_B`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tb6-med-002-b01`
- 建议 abstention：`false`
- 建议理由：当前生效条款直接约定合同乙方；过往示例或流程附件不属于当前目标 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-MED-002-01-PARTY_B | 双方在当前生效条款中明确：本合同合同乙方确定为「澄曜-TB6-MED-002-01-PARTY_B」。 | tb6-med-002-b01 |
| OCC-002 | 澄曜-TB6-MED-002-02-PARTY_B | 过往示例台账记录「澄曜-TB6-MED-002-02-PARTY_B」，并注明不适用于本合同当前合同乙方。 | tb6-med-002-b02 |

### TB6-MED-003

- 类别：`MEDIUM`
- 新 packetId：`EP-30c729f876f03831a713f44ccff193cc657acfdeec48bb94100600537a2f6617`
- 建议角色：`CONTRACT_TOTAL_AMOUNT`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb6-med-003-table-01`
- 建议 abstention：`false`
- 建议理由：当前生效条款直接约定合同含税总金额；过往示例或流程附件不属于当前目标 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-MED-003-01-CONTRACT_TOTAL_AMOUNT | 过往示例台账记录「澄曜-TB6-MED-003-01-CONTRACT_TOTAL_AMOUNT」，并注明不适用于本合同当前合同含税总金额。 | tb6-med-003-table-01 / table:tb6_med_003/row:1/cell:2 |
| OCC-002 | 澄曜-TB6-MED-003-02-CONTRACT_TOTAL_AMOUNT | 双方在当前生效条款中明确：本合同合同含税总金额确定为「澄曜-TB6-MED-003-02-CONTRACT_TOTAL_AMOUNT」。 | tb6-med-003-table-01 / table:tb6_med_003/row:2/cell:2 |

### TB6-MED-004

- 类别：`MEDIUM`
- 新 packetId：`EP-fd05eaaebdf34d96005d1a1c14f26ae2d173545e34450a48a2ebc6d108aea243`
- 建议角色：`PREPAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tb6-med-004-b01`
- 建议 abstention：`false`
- 建议理由：当前生效条款直接约定预付款比例；过往示例或流程附件不属于当前目标 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-MED-004-01-PREPAYMENT_RATIO | 双方在当前生效条款中明确：本合同预付款比例确定为「澄曜-TB6-MED-004-01-PREPAYMENT_RATIO」。 | tb6-med-004-b01 |
| OCC-002 | 澄曜-TB6-MED-004-02-PREPAYMENT_RATIO | 过往示例台账记录「澄曜-TB6-MED-004-02-PREPAYMENT_RATIO」，并注明不适用于本合同当前预付款比例。 | tb6-med-004-b02 |

### TB6-MED-005

- 类别：`MEDIUM`
- 新 packetId：`EP-ef6e602b815b1ee427fb3024ca1676970d274ac5aae9c198173336c2be09a2a7`
- 建议角色：`WARRANTY_RETENTION_RATIO`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb6-med-005-b02`
- 建议 abstention：`false`
- 建议理由：当前生效条款直接约定质保金比例；过往示例或流程附件不属于当前目标 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-MED-005-01-WARRANTY_RETENTION_RATIO | 过往示例台账记录「澄曜-TB6-MED-005-01-WARRANTY_RETENTION_RATIO」，并注明不适用于本合同当前质保金比例。 | tb6-med-005-b01 |
| OCC-002 | 澄曜-TB6-MED-005-02-WARRANTY_RETENTION_RATIO | 双方在当前生效条款中明确：本合同质保金比例确定为「澄曜-TB6-MED-005-02-WARRANTY_RETENTION_RATIO」。 | tb6-med-005-b02 |

### TB6-CON-001

- 类别：`CONFLICTED`
- 新 packetId：`EP-bb8a60c0e9b6baacf090f02dd90b0231bb4c6346f2759799fad29ddf6b32f29c`
- 建议角色：`PARTY_A`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tb6-con-001-b01`
- 建议 abstention：`false`
- 建议理由：当前生效条款直接约定合同甲方；过往示例或流程附件不属于当前目标 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-CON-001-01-PARTY_A | 双方在当前生效条款中明确：本合同合同甲方确定为「澄曜-TB6-CON-001-01-PARTY_A」。 | tb6-con-001-b01 |
| OCC-002 | 澄曜-TB6-CON-001-02-PARTY_A | 过往示例台账记录「澄曜-TB6-CON-001-02-PARTY_A」，并注明不适用于本合同当前合同甲方。 | tb6-con-001-b01 |

### TB6-CON-002

- 类别：`CONFLICTED`
- 新 packetId：`EP-8a95c7861aa5edff14afcf91cf7d7c1bf35c1d3d4a419122f1d37dce3a4f8fa1`
- 建议角色：`TAX_AMOUNT`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb6-con-002-table-01`
- 建议 abstention：`false`
- 建议理由：当前生效条款直接约定合同税额；过往示例或流程附件不属于当前目标 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-CON-002-01-TAX_AMOUNT | 过往示例台账记录「澄曜-TB6-CON-002-01-TAX_AMOUNT」，并注明不适用于本合同当前合同税额。 | tb6-con-002-table-01 / table:tb6_con_002/row:1/cell:1 |
| OCC-002 | 澄曜-TB6-CON-002-02-TAX_AMOUNT | 双方在当前生效条款中明确：本合同合同税额确定为「澄曜-TB6-CON-002-02-TAX_AMOUNT」。 | tb6-con-002-table-01 / table:tb6_con_002/row:1/cell:2 |
| OCC-003 | 澄曜-TB6-CON-002-03-TAX_AMOUNT | 过往示例台账记录「澄曜-TB6-CON-002-03-TAX_AMOUNT」，并注明不适用于本合同当前合同税额。 | tb6-con-002-table-01 / table:tb6_con_002/row:1/cell:3 |

### TB6-CON-003

- 类别：`CONFLICTED`
- 新 packetId：`EP-80517b20dd53dd86e69afafa55788f05c6c07d10c9072a857a697f4729190ac1`
- 建议角色：`PROGRESS_PAYMENT_RATIO`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb6-con-003-b02`
- 建议 abstention：`false`
- 建议理由：当前生效条款直接约定进度款比例；过往示例或流程附件不属于当前目标 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-CON-003-01-PROGRESS_PAYMENT_RATIO | 过往示例台账记录「澄曜-TB6-CON-003-01-PROGRESS_PAYMENT_RATIO」，并注明不适用于本合同当前进度款比例。 | tb6-con-003-b01 |
| OCC-002 | 澄曜-TB6-CON-003-02-PROGRESS_PAYMENT_RATIO | 双方在当前生效条款中明确：本合同进度款比例确定为「澄曜-TB6-CON-003-02-PROGRESS_PAYMENT_RATIO」。 | tb6-con-003-b02 |
| OCC-003 | 澄曜-TB6-CON-003-03-PROGRESS_PAYMENT_RATIO | 旧流程附件只记录审批序号，不表达本合同当前进度款比例。 | tb6-con-003-b03 |

### TB6-CON-004

- 类别：`CONFLICTED`
- 新 packetId：`EP-338b3fd03abe4f58d1dfb9a38e3f56cac71ba77396417f9fe64da9f47efee89b`
- 建议角色：`SETTLEMENT_PAYMENT_RATIO`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tb6-con-004-b01`
- 建议 abstention：`false`
- 建议理由：当前生效条款直接约定结算款比例；过往示例或流程附件不属于当前目标 role。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-CON-004-01-SETTLEMENT_PAYMENT_RATIO | 过往示例台账记录「澄曜-TB6-CON-004-01-SETTLEMENT_PAYMENT_RATIO」，并注明不适用于本合同当前结算款比例。 | tb6-con-004-b01 |
| OCC-002 | 澄曜-TB6-CON-004-02-SETTLEMENT_PAYMENT_RATIO | 双方在当前生效条款中明确：本合同结算款比例确定为「澄曜-TB6-CON-004-02-SETTLEMENT_PAYMENT_RATIO」。 | tb6-con-004-b01 |

### TB6-CTL-HIGH-001

- 类别：`HIGH`
- 新 packetId：`EP-ea71919261bfccb64c0e90ec428c4ac33fdcb44a45fcfb397657bcf9f1285b14`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`DETERMINISTIC_HIGH_ZERO_CALL`）
- 建议理由：确定性 HIGH 必须保持 zero-call。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-CTL-HIGH-001-01-CONTRACT_TOTAL_AMOUNT | 过往示例台账记录「澄曜-TB6-CTL-HIGH-001-01-CONTRACT_TOTAL_AMOUNT」，并注明不适用于本合同当前合同含税总金额。 | tb6-ctl-high-001-b01 |

### TB6-CTL-INVALID-001

- 类别：`MEDIUM`
- 新 packetId：`EP-d0a3abf4188f8cf3eda476a2dd159f7641e00e1eca0c07f5eb88e45e0a71a3df`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`BUNDLE_INVALID`）
- 建议理由：不可靠 TABLE_CELL identity 必须 fail closed。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-CTL-INVALID-001-01-PREPAYMENT_RATIO | 过往示例台账记录「澄曜-TB6-CTL-INVALID-001-01-PREPAYMENT_RATIO」，并注明不适用于本合同当前预付款比例。 | tb6-ctl-invalid-001-table-01 / table:tb6_ctl_invalid/row:x/cell:2 |

### TB6-CTL-NOANCHOR-001

- 类别：`MEDIUM`
- 新 packetId：`EP-7242020b6d991cfd5bc5e24b4d823436f79524bafee89a666ca818e26ad2f22b`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`RELIABLE_ANCHOR_MISSING`）
- 建议理由：无可靠 anchor 必须保持 zero-call。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 澄曜-TB6-CTL-NOANCHOR-001-01-PARTY_B | 过往示例台账记录「澄曜-TB6-CTL-NOANCHOR-001-01-PARTY_B」，并注明不适用于本合同当前合同乙方。 | tb6-ctl-noanchor-001-b01 |
