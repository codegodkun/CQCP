# PostgreSQL 只读核对摘要

核对时间：2026-07-28 09:25:25 +08:00

数据库：Docker Compose PostgreSQL 15，业务库 `cqcp`

Task：`TASK_2f2539c99d574d29b993c5733c20b4bd`

Execution：`EXEC_321f36a3ad37415993b5dcdd5c11c0cb`

## Task

- `source_type=DOCX_UPLOAD`
- `contract_type_code=ENGINEERING`
- `currency=CNY`
- `contract_name=奔腾公司企鹅岛项目三标段土建总承包工程合同`
- `result_url=/review/results/TASK_2f2539c99d574d29b993c5733c20b4bd?executionId=EXEC_321f36a3ad37415993b5dcdd5c11c0cb`
- `structured_fields_snapshot`：17 个字段
- `contract_metadata.originalFileName=1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx`
- `contract_metadata.sizeBytes=90276`
- `contract_metadata.documentReference=TASK_2f2539c99d574d29b993c5733c20b4bd/34c1b343fdf242638a660a389a1c62c3.docx`

源文件和持久化文件均为 90,276 bytes，SHA-256 均为：

`29E50BBB74E234FD4508C2765F1A74A57AB9043667234A7BF30E04F29F2EF2F5`

## Execution

- `status=SUCCESS`
- `current_stage=SUCCESS`
- `provider_type=MOCK`
- `model_profile_code=MVP_DEMO_MOCK`
- `model_name=cqcp-demo-mock`
- `endpoint_alias=mock-local`
- `contract_type_profile_version=v20260705.1`
- `rule_set_version=v20260705.1`
- `prompt_version=v20260705.1`
- `pattern_library_version=v20260705.1`
- `field_lexicon_version=v20260705.1`
- `evidence_selector_version=v20260705.1`
- `model_config_version=model-config-mvp-demo-mock-v20260724.1`
- `parser_version=parser-docx-word-v20260724.1`
- `schema_version=model-output-artifact-v20260724.1`
- `started_at=2026-07-28T01:11:15.299921Z`
- `finished_at=2026-07-28T01:11:15.947313Z`

## 阶段日志

| ID | 阶段 | 事件 | 状态 | attempt | durationMs |
|---:|---|---|---|---:|---:|
| 1 | PARSING | STARTED | RUNNING | 1 | — |
| 2 | PARSING | COMPLETED | SUCCESS | 1 | 560 |
| 3 | INDEXING | STARTED | RUNNING | 1 | — |
| 4 | INDEXING | COMPLETED | SUCCESS | 1 | 9 |
| 5 | PLANNING | STARTED | RUNNING | 1 | — |
| 6 | PLANNING | COMPLETED | SUCCESS | 1 | 6 |
| 7 | BUILDING_EVIDENCE | STARTED | RUNNING | 1 | — |
| 8 | BUILDING_EVIDENCE | COMPLETED | SUCCESS | 1 | 44 |
| 9 | REVIEWING_RULES | STARTED | RUNNING | 1 | — |
| 10 | REVIEWING_RULES | COMPLETED | SUCCESS | 1 | 6 |
| 11 | COMPOSING | STARTED | RUNNING | 1 | — |
| 12 | COMPOSING | COMPLETED | SUCCESS | 1 | 4 |

无阶段失败、重试或诊断码。

## ReviewResultSnapshot

- `status=SUCCESS`
- `plannedPointCount=9`
- `passCount=9`
- `errorCount=0`
- `warningCount=0`
- `notConcludedCount=0`
- `skippedCount=0`
- `point_results`：9
- `source_anchors`：8（税额与总金额审核点复用同一可靠证据）
- `structured_fields_snapshot`：17
- `reviewCoverageStatus=FULL_REVIEWED`
- `executablePointCount=9`
- `concludedPointCount=9`
- `confidenceLevel=HIGH`

完整脱敏结果见 `result-snapshot.json`。
