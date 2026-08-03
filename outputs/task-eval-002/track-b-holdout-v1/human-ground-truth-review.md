# TASK-EVAL-002 Track B 12-packet holdout 人工确认表

状态：**待人工确认；当前不是 ground truth**

语料 SHA-256：`15d1f845d65c5018363bba324d0527f36ff07ddf0703f188f1d557711a9e2b24`

确认范围：12 个新建、合成、最小 EvidencePacket；9 个为可调用
MEDIUM/CONFLICTED 歧义，3 个分别验证 deterministic HIGH、invalid bundle 与
无可靠 anchor 的 zero-call。

请逐项核对候选原文、角色、选择和 abstention。只有项目负责人明确确认后，
Codex 才会封存人工 ground truth。确认前不得创建 model input、不得派发 Codex
blind evaluator，也不得发起任何模型调用或 DeepSeek 网络调用。

| Case | 类别 | 建议选择 | 建议理由 | 人工决定 |
|---|---|---|---|---|
| TBH-MED-001 | MEDIUM | OCC-002 | 甲方角色应归属明确标注的采购人公司，不取授权代表个人。 | 待确认 |
| TBH-MED-002 | MEDIUM | OCC-001 | 乙方角色应取明确的承包人合同主体，不取收款账户名称。 | 待确认 |
| TBH-MED-003 | MEDIUM | OCC-001 | 合同总金额应取含税总价486320元，不取预算控制上限。 | 待确认 |
| TBH-MED-004 | MEDIUM | OCC-001 | 预付款比例是12%，履约保证金比例6%不属于该角色。 | 待确认 |
| TBH-MED-005 | MEDIUM | OCC-001 | 质量保证金比例是4%，逾期违约金上限8%不是质保金。 | 待确认 |
| TBH-CON-001 | CONFLICTED | OCC-002 | 同句候选中，采购人（甲方）标签直接指向沧澜机电有限公司。 | 待确认 |
| TBH-CON-002 | CONFLICTED | OCC-002 | 税额角色应选择明确标注的37800元，不取不含税价或价税合计。 | 待确认 |
| TBH-CON-003 | CONFLICTED | OCC-001 | 进度款比例为72%，竣工累计支付88%属于另一角色，附件说明不参与局部比较。 | 待确认 |
| TBH-CON-004 | CONFLICTED | OCC-001 | 结算付款比例是累计支付至96%，4%属于质保金。 | 待确认 |
| TBH-CTL-HIGH-001 | HIGH | 无（拒答） | deterministic HIGH 必须零调用，模型不得覆盖。 | 待确认 |
| TBH-CTL-INVALID-001 | MEDIUM | 无（拒答） | TABLE_CELL identity 非法导致 bundle invalid，必须零调用。 | 待确认 |
| TBH-CTL-NOANCHOR-001 | MEDIUM | 无（拒答） | 候选只来自 TOC，不能形成可靠正文 anchor，必须零调用。 | 待确认 |

## 候选原文与 SourceAnchor 逐项核对

### TBH-MED-001

- 类别：`MEDIUM`
- 新 packetId：`EP-8404074ec1edb454d1acd1c8bdfa948e66c8d65a07a50d1eb7eb96d7b599a5f8`
- 建议角色：`PARTY_A`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tbh-med-001-b02`
- 建议 abstention：`false`
- 建议理由：甲方角色应归属明确标注的采购人公司，不取授权代表个人。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 宋妍 | 甲方授权代表：宋妍，负责现场联络。 | tbh-med-001-b01 |
| OCC-002 | 澄海新材有限公司 | 采购人（甲方）：澄海新材有限公司。 | tbh-med-001-b02 |

### TBH-MED-002

- 类别：`MEDIUM`
- 新 packetId：`EP-f0a897b30c36ce8c495bd07a6af18cd399a90a507384a344932ec081d1048146`
- 建议角色：`PARTY_B`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbh-med-002-b01`
- 建议 abstention：`false`
- 建议理由：乙方角色应取明确的承包人合同主体，不取收款账户名称。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 岭峰工程技术有限公司 | 承包人（乙方）：岭峰工程技术有限公司。 | tbh-med-002-b01 |
| OCC-002 | 岭峰结算中心 | 收款账户名称：岭峰结算中心。 | tbh-med-002-b02 |

### TBH-MED-003

- 类别：`MEDIUM`
- 新 packetId：`EP-52f8eb6cba6bfcfb8eebfd9f61b7f89eca2590ab569b9f60e884b74d93de7359`
- 建议角色：`CONTRACT_TOTAL_AMOUNT`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbh-med-003-table-01`
- 建议 abstention：`false`
- 建议理由：合同总金额应取含税总价486320元，不取预算控制上限。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 486320 | 合同含税总价（元）486320 | tbh-med-003-table-01 / table:tbh_med_003/row:1/cell:2 |
| OCC-002 | 500000 | 预算控制上限（元）500000 | tbh-med-003-table-01 / table:tbh_med_003/row:2/cell:2 |

### TBH-MED-004

- 类别：`MEDIUM`
- 新 packetId：`EP-c0c435f0b8ea94ecb1c56a257880c2d3f13094e8edab48314317e835b9ecebff`
- 建议角色：`PREPAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbh-med-004-b01`
- 建议 abstention：`false`
- 建议理由：预付款比例是12%，履约保证金比例6%不属于该角色。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 12 | 合同生效后支付合同价的12%作为预付款。 | tbh-med-004-b01 |
| OCC-002 | 6 | 履约保证金为合同价的6%。 | tbh-med-004-b02 |

### TBH-MED-005

- 类别：`MEDIUM`
- 新 packetId：`EP-20997e8a0183552ea72fbe6228d899130472621ab42254c713ba529d5c5f129a`
- 建议角色：`WARRANTY_RETENTION_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbh-med-005-b01`
- 建议 abstention：`false`
- 建议理由：质量保证金比例是4%，逾期违约金上限8%不是质保金。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 4 | 结算价的4%留作质量保证金。 | tbh-med-005-b01 |
| OCC-002 | 8 | 逾期违约金累计最高不超过合同价的8%。 | tbh-med-005-b02 |

### TBH-CON-001

- 类别：`CONFLICTED`
- 新 packetId：`EP-331c1bd8f3f5cdcaf4d16d2c6a5f28d70e1218ae7a547774cf2c244a15473709`
- 建议角色：`PARTY_A`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tbh-con-001-b01`
- 建议 abstention：`false`
- 建议理由：同句候选中，采购人（甲方）标签直接指向沧澜机电有限公司。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 暮云项目管理有限公司 | 采购人（甲方）：沧澜机电有限公司；项目管理单位：暮云项目管理有限公司。 | tbh-con-001-b01 |
| OCC-002 | 沧澜机电有限公司 | 采购人（甲方）：沧澜机电有限公司；项目管理单位：暮云项目管理有限公司。 | tbh-con-001-b01 |

### TBH-CON-002

- 类别：`CONFLICTED`
- 新 packetId：`EP-c004c54cc4dfbedfb537d1848796e28067f1af1d247b37bd6cbc9d8f2e323104`
- 建议角色：`TAX_AMOUNT`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tbh-con-002-table-01`
- 建议 abstention：`false`
- 建议理由：税额角色应选择明确标注的37800元，不取不含税价或价税合计。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 420000 | 不含税价420000元，税额37800元，价税合计457800元。 | tbh-con-002-table-01 / table:tbh_con_002/row:1/cell:1 |
| OCC-002 | 37800 | 不含税价420000元，税额37800元，价税合计457800元。 | tbh-con-002-table-01 / table:tbh_con_002/row:1/cell:2 |
| OCC-003 | 457800 | 不含税价420000元，税额37800元，价税合计457800元。 | tbh-con-002-table-01 / table:tbh_con_002/row:1/cell:3 |

### TBH-CON-003

- 类别：`CONFLICTED`
- 新 packetId：`EP-eac27ad158d244ba1c4c0fa4c6ce19b338a0dc59237f12517faba083a04982d0`
- 建议角色：`PROGRESS_PAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbh-con-003-b01`
- 建议 abstention：`false`
- 建议理由：进度款比例为72%，竣工累计支付88%属于另一角色，附件说明不参与局部比较。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 72 | 月度进度款按已确认产值的72%支付；竣工后累计支付至88%。 | tbh-con-003-b01 |
| OCC-002 | 88 | 月度进度款按已确认产值的72%支付；竣工后累计支付至88%。 | tbh-con-003-b02 |
| OCC-003 | 100 | 另附开票说明，不参与本付款条款比较。 | tbh-con-003-b03 |

### TBH-CON-004

- 类别：`CONFLICTED`
- 新 packetId：`EP-40150ed3fca0a8f7312e731fceba5559447cc4d1f33ac8aa5e6827316acf5323`
- 建议角色：`SETTLEMENT_PAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbh-con-004-b01`
- 建议 abstention：`false`
- 建议理由：结算付款比例是累计支付至96%，4%属于质保金。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 96 | 结算审定后累计支付至结算价的96%，余款4%作为质保金。 | tbh-con-004-b01 |
| OCC-002 | 4 | 结算审定后累计支付至结算价的96%，余款4%作为质保金。 | tbh-con-004-b01 |

### TBH-CTL-HIGH-001

- 类别：`HIGH`
- 新 packetId：`EP-cd92c2ab461e4d13f0b64ed50e20c3977ec0f7fddea8a1f94db9b7d196ffa02d`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`DETERMINISTIC_HIGH_ZERO_CALL`）
- 建议理由：deterministic HIGH 必须零调用，模型不得覆盖。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 457800 | 合同含税总价为457800元。 | tbh-ctl-high-001-b01 |

### TBH-CTL-INVALID-001

- 类别：`MEDIUM`
- 新 packetId：`EP-4dc5e244c23e7b3e2d2e242570b64cb111ee069de457a1879473804a7f50c270`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`BUNDLE_INVALID`）
- 建议理由：TABLE_CELL identity 非法导致 bundle invalid，必须零调用。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 15 | 预付款比例15% | tbh-ctl-invalid-001-table-01 / table:tbh_ctl_invalid/row:x/cell:2 |

### TBH-CTL-NOANCHOR-001

- 类别：`MEDIUM`
- 新 packetId：`EP-b77884e4b174d4bd16b6e1177f71a2df5a9f9443261179183ce3759dfc35b82b`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`RELIABLE_ANCHOR_MISSING`）
- 建议理由：候选只来自 TOC，不能形成可靠正文 anchor，必须零调用。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 雁栖服务有限公司 | 目录索引：雁栖服务有限公司。 | tbh-ctl-noanchor-001-b01 |
