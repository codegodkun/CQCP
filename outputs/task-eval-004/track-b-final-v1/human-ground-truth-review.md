# TASK-EVAL-004 Track B final 12-packet 人工确认表

状态：**待人工确认；当前不是 ground truth**

语料 SHA-256：`ed216d435b82af250c39e0ba927f75ceaa233f70ae9aa516fa4adad42aa3ca95`

确认范围：12 个新建、合成、最小 EvidencePacket；9 个为可调用
MEDIUM/CONFLICTED 歧义，3 个分别验证 deterministic HIGH、invalid bundle 与
无可靠 anchor 的 zero-call。

请逐项核对候选原文、角色、选择和 abstention。只有项目负责人明确确认后，
Codex 才会封存人工 ground truth。确认前不得创建 model input、不得派发 Codex
blind evaluator，也不得发起任何模型调用或 DeepSeek 网络调用。

| Case | 类别 | 建议选择 | 建议理由 | 人工决定 |
|---|---|---|---|---|
| TBF-MED-001 | MEDIUM | OCC-002 | 委托方（甲方）标签直接标识合同甲方，现场联系人不是合同主体。 | 待确认 |
| TBF-MED-002 | MEDIUM | OCC-001 | 承包方（乙方）是合同主体，结算中心只是收款信息。 | 待确认 |
| TBF-MED-003 | MEDIUM | OCC-001 | 签约含税价对应合同总金额，采购控制价不是签约金额。 | 待确认 |
| TBF-MED-004 | MEDIUM | OCC-001 | 启动预付款13.5%对应目标角色，6.5%属于履约担保。 | 待确认 |
| TBF-MED-005 | MEDIUM | OCC-001 | 质保金2.75%对应目标角色，8.25%是逾期责任上限。 | 待确认 |
| TBF-CON-001 | CONFLICTED | OCC-002 | 发包人（甲方）标签直接指向牧星能源装备有限公司。 | 待确认 |
| TBF-CON-002 | CONFLICTED | OCC-002 | 税额标签直接对应66168，而非不含税金额或价税合计。 | 待确认 |
| TBF-CON-003 | CONFLICTED | OCC-001 | 月度计量款73.5%对应进度款；87.5%是验收累计比例，附件编号不参与。 | 待确认 |
| TBF-CON-004 | CONFLICTED | OCC-001 | 结算确认后累计支付91.25%对应结算支付比例，8.75%是保留余款。 | 待确认 |
| TBF-CTL-HIGH-001 | HIGH | 无（拒答） | 确定性 HIGH 必须零调用。 | 待确认 |
| TBF-CTL-INVALID-001 | MEDIUM | 无（拒答） | TABLE_CELL identity 不合法，bundle 必须 fail closed。 | 待确认 |
| TBF-CTL-NOANCHOR-001 | MEDIUM | 无（拒答） | TOC context 不构成可靠业务证据 anchor。 | 待确认 |

## 候选原文与 SourceAnchor 逐项核对

### TBF-MED-001

- 类别：`MEDIUM`
- 新 packetId：`EP-e6ae080ccbfac3f0672d642fa9bd366e7d348526c93f3f9790c15babc87f89f8`
- 建议角色：`PARTY_A`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tbf-med-001-b02`
- 建议 abstention：`false`
- 建议理由：委托方（甲方）标签直接标识合同甲方，现场联系人不是合同主体。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 陆遥 | 甲方现场联系人：陆遥，负责进场协调。 | tbf-med-001-b01 |
| OCC-002 | 砺川智能装备有限公司 | 委托方（甲方）：砺川智能装备有限公司。 | tbf-med-001-b02 |

### TBF-MED-002

- 类别：`MEDIUM`
- 新 packetId：`EP-eb34bfed16412773da7048b37be1d931ef721f27ae6eaedbcba7594489e2344a`
- 建议角色：`PARTY_B`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbf-med-002-b01`
- 建议 abstention：`false`
- 建议理由：承包方（乙方）是合同主体，结算中心只是收款信息。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 朔浦机电安装有限公司 | 承包方（乙方）：朔浦机电安装有限公司。 | tbf-med-002-b01 |
| OCC-002 | 朔浦结算中心 | 收款账户户名：朔浦结算中心。 | tbf-med-002-b02 |

### TBF-MED-003

- 类别：`MEDIUM`
- 新 packetId：`EP-7f97ea0079bf69874f833bbf210c752b2a0088d977498ca9e2e2168d8a12fc3d`
- 建议角色：`CONTRACT_TOTAL_AMOUNT`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbf-med-003-table-01`
- 建议 abstention：`false`
- 建议理由：签约含税价对应合同总金额，采购控制价不是签约金额。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 684730 | 签约含税价（元）684730 | tbf-med-003-table-01 / table:tbf_med_003/row:1/cell:2 |
| OCC-002 | 711800 | 采购控制价（元）711800 | tbf-med-003-table-01 / table:tbf_med_003/row:2/cell:2 |

### TBF-MED-004

- 类别：`MEDIUM`
- 新 packetId：`EP-72fa22fb6e8edc9eca448805b90e6f867b6960c45fe6149f10b3630125669dc2`
- 建议角色：`PREPAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbf-med-004-b01`
- 建议 abstention：`false`
- 建议理由：启动预付款13.5%对应目标角色，6.5%属于履约担保。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 13.5 | 合同生效后支付签约价的13.5%作为启动预付款。 | tbf-med-004-b01 |
| OCC-002 | 6.5 | 履约保函金额为签约价的6.5%。 | tbf-med-004-b02 |

### TBF-MED-005

- 类别：`MEDIUM`
- 新 packetId：`EP-71a41c31400c9fea5c6c8463beb1eaaa31aaebf905baf72c8ad14789d64e8578`
- 建议角色：`WARRANTY_RETENTION_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbf-med-005-b01`
- 建议 abstention：`false`
- 建议理由：质保金2.75%对应目标角色，8.25%是逾期责任上限。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 2.75 | 最终结算价的2.75%留作工程质量保证金。 | tbf-med-005-b01 |
| OCC-002 | 8.25 | 逾期责任累计最高为签约价的8.25%。 | tbf-med-005-b02 |

### TBF-CON-001

- 类别：`CONFLICTED`
- 新 packetId：`EP-bde97418954b63a37c24177d9eef75fa64d3ca443b935316e9d58da3737298c4`
- 建议角色：`PARTY_A`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tbf-con-001-b01`
- 建议 abstention：`false`
- 建议理由：发包人（甲方）标签直接指向牧星能源装备有限公司。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 临川项目管理有限公司 | 发包人（甲方）：牧星能源装备有限公司；全过程咨询单位：临川项目管理有限公司。 | tbf-con-001-b01 |
| OCC-002 | 牧星能源装备有限公司 | 发包人（甲方）：牧星能源装备有限公司；全过程咨询单位：临川项目管理有限公司。 | tbf-con-001-b01 |

### TBF-CON-002

- 类别：`CONFLICTED`
- 新 packetId：`EP-524f3f513707469dd51a3d53e5c8a80e66d50cbf5911169afc458b8e2febec18`
- 建议角色：`TAX_AMOUNT`
- 建议 occurrence：`OCC-002`
- 建议 anchor：`tbf-con-002-table-01`
- 建议 abstention：`false`
- 建议理由：税额标签直接对应66168，而非不含税金额或价税合计。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 735200 | 未税金额735200元，税额66168元，含税合计801368元。 | tbf-con-002-table-01 / table:tbf_con_002/row:1/cell:1 |
| OCC-002 | 66168 | 未税金额735200元，税额66168元，含税合计801368元。 | tbf-con-002-table-01 / table:tbf_con_002/row:1/cell:2 |
| OCC-003 | 801368 | 未税金额735200元，税额66168元，含税合计801368元。 | tbf-con-002-table-01 / table:tbf_con_002/row:1/cell:3 |

### TBF-CON-003

- 类别：`CONFLICTED`
- 新 packetId：`EP-1f76b7382bc7a5948e3da4212b5feffb32b6b44ce65012a30c2bceee81d2ad5b`
- 建议角色：`PROGRESS_PAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbf-con-003-b01`
- 建议 abstention：`false`
- 建议理由：月度计量款73.5%对应进度款；87.5%是验收累计比例，附件编号不参与。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 73.5 | 月度计量款按确认完成量的73.5%支付。 | tbf-con-003-b01 |
| OCC-002 | 87.5 | 阶段验收后累计支付至签约价的87.5%。 | tbf-con-003-b02 |
| OCC-003 | 103.5 | 附件中的备案序号仅作资料索引，不属于付款比例。 | tbf-con-003-b03 |

### TBF-CON-004

- 类别：`CONFLICTED`
- 新 packetId：`EP-9ff3339d3ff1a6ede08ab082dd8bfac4541e3d5e613275ceae2ea5e58b27b32f`
- 建议角色：`SETTLEMENT_PAYMENT_RATIO`
- 建议 occurrence：`OCC-001`
- 建议 anchor：`tbf-con-004-b01`
- 建议 abstention：`false`
- 建议理由：结算确认后累计支付91.25%对应结算支付比例，8.75%是保留余款。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 91.25 | 结算确认后累计支付至结算价的91.25%，其余8.75%按保修约定处理。 | tbf-con-004-b01 |
| OCC-002 | 8.75 | 结算确认后累计支付至结算价的91.25%，其余8.75%按保修约定处理。 | tbf-con-004-b01 |

### TBF-CTL-HIGH-001

- 类别：`HIGH`
- 新 packetId：`EP-3a75f7d9a67682d3a9c1cd7266f5b87b4a05a500439e8e7f3b3e40be66a41f7c`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`DETERMINISTIC_HIGH_ZERO_CALL`）
- 建议理由：确定性 HIGH 必须零调用。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 623450 | 合同含税总金额明确为623450元。 | tbf-ctl-high-001-b01 |

### TBF-CTL-INVALID-001

- 类别：`MEDIUM`
- 新 packetId：`EP-7fcf3a9764bd6a9f37f41f6bc1bd84d01704b7301486d80aa292744e990509e6`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`BUNDLE_INVALID`）
- 建议理由：TABLE_CELL identity 不合法，bundle 必须 fail closed。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 16.5 | 预付款比例16.5% | tbf-ctl-invalid-001-table-01 / table:tbf_ctl_invalid/row:x/cell:2 |

### TBF-CTL-NOANCHOR-001

- 类别：`MEDIUM`
- 新 packetId：`EP-81883b329b58c72818e88d2e328fc2eb6cb65685c1d3be851f817cb6b63a0446`
- 建议角色：`无（拒答）`
- 建议 occurrence：`无`
- 建议 anchor：`无`
- 建议 abstention：`true`（`RELIABLE_ANCHOR_MISSING`）
- 建议理由：TOC context 不构成可靠业务证据 anchor。

| occurrenceId | candidateValue | evidenceText | SourceAnchor |
|---|---|---|---|
| OCC-001 | 鹭湾设施维护有限公司 | 目录条目：鹭湾设施维护有限公司。 | tbf-ctl-noanchor-001-b01 |
