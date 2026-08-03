# TASK-EVAL-003 Track B successor 12-packet 人工确认表

状态：**待人工确认；当前不是 ground truth**

语料 SHA-256：`a609005418a970c45f4494cd95c94f64ee7704bec5dcdcf42cb6a11dfd066de3`

确认范围：12 个新建、合成、最小 EvidencePacket；9 个为可调用
MEDIUM/CONFLICTED 歧义，3 个分别验证 deterministic HIGH、invalid bundle 与
无可靠 anchor 的 zero-call。

请逐项核对候选原文、角色、选择和 abstention。只有项目负责人明确确认后，
Codex 才会封存人工 ground truth。确认前不得创建 model input、不得派发 Codex
blind evaluator，也不得发起任何模型调用或 DeepSeek 网络调用。

| Case | 类别 | 建议选择 | 建议理由 | 人工决定 |
|---|---|---|---|---|
| TBS-MED-001 | MEDIUM | OCC-002 | 发包人标签直接标识合同甲方，联系人不是合同主体。 | 待确认 |
| TBS-MED-002 | MEDIUM | OCC-001 | 承揽人（乙方）是合同主体，收款户名不是乙方主体字段。 | 待确认 |
| TBS-MED-003 | MEDIUM | OCC-001 | 合同含税总金额对应合同总价，预算控制额不属于合同总价。 | 待确认 |
| TBS-MED-004 | MEDIUM | OCC-001 | 预付款条款中的14%对应目标角色，7%属于履约担保。 | 待确认 |
| TBS-MED-005 | MEDIUM | OCC-001 | 质量保证金2%对应质保金比例，9%属于违约金上限。 | 待确认 |
| TBS-CON-001 | CONFLICTED | OCC-002 | 承包人（乙方）标签直接指向青屿设备安装有限公司。 | 待确认 |
| TBS-CON-002 | CONFLICTED | OCC-002 | 税额标签直接对应42120，而非不含税金额或价税合计。 | 待确认 |
| TBS-CON-003 | CONFLICTED | OCC-001 | 月度进度款74%对应目标角色；86%是完工累计比例，附件说明不参与比较。 | 待确认 |
| TBS-CON-004 | CONFLICTED | OCC-001 | 结算审定后累计支付89%对应结算支付比例，11%是余款。 | 待确认 |
| TBS-CTL-HIGH-001 | HIGH | 无（拒答） | 确定性 HIGH 必须零调用。 | 待确认 |
| TBS-CTL-INVALID-001 | MEDIUM | 无（拒答） | TABLE_CELL identity 不合法，bundle 必须 fail closed。 | 待确认 |
| TBS-CTL-NOANCHOR-001 | MEDIUM | 无（拒答） | TOC context 不构成可靠业务证据 anchor。 | 待确认 |

## 候选原文与 SourceAnchor 逐项核对

### TBS-MED-001

- 类别：`MEDIUM`
- 新 packetId：`EP-3cdefb27b04471d321ad206f60c2777181be04912f89408e369cd88aad465abc`
- 建议角色：`PARTY_A`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tbs-med-001-b02`
- 建议 abstention：`false`
- 建议理由：发包人标签直接标识合同甲方，联系人不是合同主体。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 邵宁 | 甲方经办联系人：邵宁，负责资料传递。 | tbs-med-001-b01 |
| OCC-002 | 晴川精密制造有限公司 | 发包人（甲方）：晴川精密制造有限公司。 | tbs-med-001-b02 |

### TBS-MED-002

- 类别：`MEDIUM`
- 新 packetId：`EP-44b2ff1afc0afb5d0945a1791bd05eb58242aa7947987b2752598d022a08953c`
- 建议角色：`PARTY_B`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbs-med-002-b01`
- 建议 abstention：`false`
- 建议理由：承揽人（乙方）是合同主体，收款户名不是乙方主体字段。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 峦境机电工程有限公司 | 承揽人（乙方）：峦境机电工程有限公司。 | tbs-med-002-b01 |
| OCC-002 | 峦境资金结算部 | 收款户名：峦境资金结算部。 | tbs-med-002-b02 |

### TBS-MED-003

- 类别：`MEDIUM`
- 新 packetId：`EP-7aea36ff77362afb1bd77fcfcc4b6d6bb1445d59565d231ea9f55884591ce15c`
- 建议角色：`CONTRACT_TOTAL_AMOUNT`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbs-med-003-table-01`
- 建议 abstention：`false`
- 建议理由：合同含税总金额对应合同总价，预算控制额不属于合同总价。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 538640 | 合同含税总金额（元）538640 | tbs-med-003-table-01 / table:tbs_med_003/row:1/cell:2 |
| OCC-002 | 560000 | 项目预算控制额（元）560000 | tbs-med-003-table-01 / table:tbs_med_003/row:2/cell:2 |

### TBS-MED-004

- 类别：`MEDIUM`
- 新 packetId：`EP-572aaa4abf8837ec08861c36da48afd4bb39793e9543485087e02141fe4c7e47`
- 建议角色：`PREPAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbs-med-004-b01`
- 建议 abstention：`false`
- 建议理由：预付款条款中的14%对应目标角色，7%属于履约担保。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 14 | 合同签署后支付合同价的14%作为预付款。 | tbs-med-004-b01 |
| OCC-002 | 7 | 履约担保比例为合同价的7%。 | tbs-med-004-b02 |

### TBS-MED-005

- 类别：`MEDIUM`
- 新 packetId：`EP-47757b9d55c63c098b08bcd0bc249cbc886b138cae6e77f9e0df4790a2691593`
- 建议角色：`WARRANTY_RETENTION_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbs-med-005-b01`
- 建议 abstention：`false`
- 建议理由：质量保证金2%对应质保金比例，9%属于违约金上限。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 2 | 审定结算价的2%作为质量保证金。 | tbs-med-005-b01 |
| OCC-002 | 9 | 延期违约金累计上限为合同价的9%。 | tbs-med-005-b02 |

### TBS-CON-001

- 类别：`CONFLICTED`
- 新 packetId：`EP-69680a99d97685f3402dd0bc0b5d21f4b79d120d8cb2690bea70a1455e11663c`
- 建议角色：`PARTY_B`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tbs-con-001-b01`
- 建议 abstention：`false`
- 建议理由：承包人（乙方）标签直接指向青屿设备安装有限公司。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 栖岸监理咨询有限公司 | 承包人（乙方）：青屿设备安装有限公司；监理协调单位：栖岸监理咨询有限公司。 | tbs-con-001-b01 |
| OCC-002 | 青屿设备安装有限公司 | 承包人（乙方）：青屿设备安装有限公司；监理协调单位：栖岸监理咨询有限公司。 | tbs-con-001-b01 |

### TBS-CON-002

- 类别：`CONFLICTED`
- 新 packetId：`EP-817c93ed5adc46b221da8aa31b6d515696f96ca30a9da9e23f505e40c5fc0685`
- 建议角色：`TAX_AMOUNT`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tbs-con-002-table-01`
- 建议 abstention：`false`
- 建议理由：税额标签直接对应42120，而非不含税金额或价税合计。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 468000 | 不含税金额468000元，税额42120元，价税合计510120元。 | tbs-con-002-table-01 / table:tbs_con_002/row:1/cell:1 |
| OCC-002 | 42120 | 不含税金额468000元，税额42120元，价税合计510120元。 | tbs-con-002-table-01 / table:tbs_con_002/row:1/cell:2 |
| OCC-003 | 510120 | 不含税金额468000元，税额42120元，价税合计510120元。 | tbs-con-002-table-01 / table:tbs_con_002/row:1/cell:3 |

### TBS-CON-003

- 类别：`CONFLICTED`
- 新 packetId：`EP-33a2e677325010e90c1be897f672c694246bbd8676a5fef61581702a2116fed8`
- 建议角色：`PROGRESS_PAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbs-con-003-b01`
- 建议 abstention：`false`
- 建议理由：月度进度款74%对应目标角色；86%是完工累计比例，附件说明不参与比较。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 74 | 月度进度款按核定完成产值的74%支付。 | tbs-con-003-b01 |
| OCC-002 | 86 | 完工验收后累计支付至合同价的86%。 | tbs-con-003-b02 |
| OCC-003 | 101 | 另附税务登记说明，不参与付款比例比较。 | tbs-con-003-b03 |

### TBS-CON-004

- 类别：`CONFLICTED`
- 新 packetId：`EP-235e78754e072e666562eafb2c1ba4f74598b099520972d26275f45d1b8e4704`
- 建议角色：`SETTLEMENT_PAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbs-con-004-b01`
- 建议 abstention：`false`
- 建议理由：结算审定后累计支付89%对应结算支付比例，11%是余款。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 89 | 结算审定后累计支付至结算价的89%，余款11%按保修约定处理。 | tbs-con-004-b01 |
| OCC-002 | 11 | 结算审定后累计支付至结算价的89%，余款11%按保修约定处理。 | tbs-con-004-b01 |

### TBS-CTL-HIGH-001

- 类别：`HIGH`
- 新 packetId：`EP-b5c24d5469dbd66dddc67c11c4321d92a21c2476d3f2e585802343abfb49f303`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`DETERMINISTIC_HIGH_ZERO_CALL`）
- 建议理由：确定性 HIGH 必须零调用。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 612340 | 合同含税总价为612340元。 | tbs-ctl-high-001-b01 |

### TBS-CTL-INVALID-001

- 类别：`MEDIUM`
- 新 packetId：`EP-d701ff3c85a8a25d904b43044f162cacd3773117816062bc489efbd5e23e6e83`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`BUNDLE_INVALID`）
- 建议理由：TABLE_CELL identity 不合法，bundle 必须 fail closed。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 17 | 预付款比例17% | tbs-ctl-invalid-001-table-01 / table:tbs_ctl_invalid/row:x/cell:2 |

### TBS-CTL-NOANCHOR-001

- 类别：`MEDIUM`
- 新 packetId：`EP-6895a2c73d28e86b7c6d0537cd7a3e685bb35bfb8514832a62459bb78186bade`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`RELIABLE_ANCHOR_MISSING`）
- 建议理由：TOC context 不构成可靠业务证据 anchor。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 汀澜运维服务有限公司 | 目录索引：汀澜运维服务有限公司。 | tbs-ctl-noanchor-001-b01 |
