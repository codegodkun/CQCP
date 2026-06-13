# docs/deployment.md

## 一期执行与运维边界

MVP 部署基线冻结如下：

- 使用 Docker Compose 作为本地开发与 MVP 部署编排入口
- 前端以静态资源形式交付，由 Nginx 承载
- 后端以 Spring Boot API 服务运行
- 健康检查与最小 metrics 通过 Spring Boot Actuator 暴露
- LibreOffice headless 以容器化转换服务方式提供文档转换能力

一期不做复杂并行，但必须异步。

一期单 worker 不承诺高并发 SLA。默认运营边界：

- `maxActiveQueuedTasks = 20`
- `maxExecutionsPerTask = 3`
- `warnQueueDepth >= 10`
- `rejectOrDelayDeepReviewWhenQueueDepth >= 10`

超过边界时，外部 API 可返回 `REVIEW_QUEUE_OVERLOADED` 或将 `DEEP_REVIEW` 降级为 `STANDARD`。

这些值属于平台运行配置，不属于业务规则集。

管理台至少监控：

- `queueDepth`
- `oldestQueuedAge`
- `estimatedWaitTime`
- `averageExecutionDuration by budget profile`
- `modelCircuitBreakerState`

`estimatedWaitTime` 根据最近执行时长和当前队列估算，并显示为非承诺值。

这些启动值必须通过上线前负载演练验证。系统不承诺 DEEP_REVIEW SLA，但必须避免 STANDARD 任务长期饥饿。

## 本地开发、验证与测试标准环境

从 `INFRA-001` 完成后，CQCP 默认且唯一被承认的本地开发、验证和测试路径为 Docker Compose。

标准开发环境由 Docker 管理：

- PostgreSQL：Docker Compose 服务 `postgres`，容器内端口 `5432`，本机暴露端口 `54329`
- 后端：Docker Compose 服务 `api-server`，容器内端口 `8080`，本机暴露端口 `18080`
- 前端：Docker Compose 服务 `admin-web`，容器内 Nginx 端口 `80`，本机暴露端口 `15173`

标准端口：

- 前端访问地址：`http://localhost:15173`
- 后端健康检查地址：`http://localhost:18080/actuator/health`
- PostgreSQL 本机端口：`54329`

标准 Compose project name 固定为 `cqcp`，推荐通过 `deploy/env/.env.example` 中的 `COMPOSE_PROJECT_NAME=cqcp` 注入。若不使用该环境文件，必须显式使用 `docker compose -p cqcp ...`，避免生成 `compose_default` 这类通用网络名。

标准启动命令：

```bash
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml up -d --build
```

停止命令：

```bash
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml down
```

重建命令：

```bash
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml build --no-cache
```

查看日志命令：

```bash
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml logs -f
```

查看单个服务日志示例：

```bash
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml logs -f api-server
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml logs -f admin-web
docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml logs -f postgres
```

从该约定生效后，不再保留“PostgreSQL Docker + 后端本机 Spring Boot + 前端本机 Vite”的混合模式作为备用开发方式。本机 Java / Node / Gradle / Vite 启动仅允许用于临时故障定位，不得作为 TASK 验收依据；如果 Docker Compose 标准环境无法启动，应暂停业务开发并优先修复 Docker 环境。

前端容器通过 Nginx `/api/` 反向代理访问 `api-server:8080`，不得在业务代码中写死 `localhost:18080`。后端数据库连接通过 `CQCP_DB_URL`、`CQCP_DB_USERNAME`、`CQCP_DB_PASSWORD` 注入，Compose 中使用容器网络地址 `postgres:5432`，不得写死本机数据库端口。

PostgreSQL 数据使用 CQCP 专属 named volume：`cqcp_postgres_data`。不得使用 `postgres-data`、`db-data` 等通用裸名。

如果本机仍存在历史测试容器 `cqcp-postgres-test` 并占用宿主机 `54329`，标准 Compose 启动前必须先人工确认处理方式：停止、迁移或删除该旧测试容器。不得在未确认数据用途前直接删除；该旧容器存在时，Compose 的 `postgres` 服务会因端口冲突无法启动。

当前 Docker 构建仍依赖 Docker Hub 基础镜像：

- `postgres:15`
- `gradle:8.10.2-jdk21`
- `eclipse-temurin:21-jre`
- `node:20-alpine`
- `nginx:1.27-alpine`

如果 Docker Hub 镜像拉取失败，不得假装 Docker Compose 已成功，也不得继续推进业务 TASK。处理建议：

1. 先确认本机 Docker Desktop 正常运行，并重新执行标准启动命令。
2. 如仍失败，检查 Docker Hub 网络访问、代理、镜像加速器或企业网络策略。
3. 如需要切换基础镜像来源、引入内部镜像仓库或锁定镜像 digest，应单独创建 INFRA 后续任务记录，不在业务 TASK 中顺手处理。

## Stage Timeout

stage timeout 一期默认值：

- `PARSING: 10m`
- `INDEXING: 5m`
- `PLANNING: 2m`
- `BUILDING_EVIDENCE: 5m`
- `REVIEWING_RULES: 3m`
- `REVIEWING_MODEL: 20m`
- `COMPOSING: 3m`

这些值属于平台运行配置，不属于业务 `RuleSetVersion`。调整 timeout 不改变审核语义，但必须记录运维变更原因。

单次模型调用必须有独立 `modelCallTimeout`，一期默认 4m。

`modelValidationReserve` 默认 `30s`，允许范围 `15s..120s`，随运行配置版本和变更原因审计。

调整 `modelValidationReserve` 的依据是脱敏后的 artifact output size 与 schema validation p99，不做每次调用的历史预测或动态自适应。没有足够样本时保留默认值，不凭单次慢请求调整。

每个 stage 必须幂等。已完成 stage 不重复执行；重试从最后一个未完成或失败 stage 继续，所有 stage 输出必须按 `taskId + executionId + stageName + attempt` 记录。

## 模型重试与 Circuit Breaker

模型重试策略固定：

- `maxAttempts = 3`
- `backoff = exponential factor 2, initial 30s, max 5m`
- no successful artifact after retries -> `SYS-MODEL-UNAVAILABLE`

模型 endpoint 使用任务间共享 circuit breaker：

- 3 consecutive endpoint failures in 2m -> OPEN。
- OPEN duration: 2m。
- OPEN -> new model assists fail fast as `SYS-MODEL-UNAVAILABLE`。
- HALF_OPEN -> allow one probe call immediately after OPEN duration。
- probe success -> CLOSED。
- probe failure -> OPEN for another 2m。

circuit breaker 以 `endpointId + modelVersion` 为作用域。

## Canary 与故障注入

fault injection 在测试或受控预生产环境执行，不对真实生产合同任务注入故障。

预生产使用与生产相同的模型协议、timeout、schema 和主要配置版本；环境差异必须记录。

生产上线后通过小流量 canary 被动观察延迟、timeout 和 breaker 指标，不向真实任务注入破坏性故障。

canary 至少记录：

- model call latency p50/p90/p99。
- timeout rate。
- 5xx rate。
- retry attempts。
- breaker open/half-open 次数与持续时间。
- `REVIEWING_MODEL` stage timeout rate。
- queue wait。
- `operationalCallAttempts/successfulModelCallsUsed` 比率。

canary 指标按 `endpointId + modelVersion` 分组，并与预生产基线及生产首日基线对比。

canary execution 必须携带：

- `trafficClass=CANARY`
- `canaryReleaseId`
- 目标 `endpointId/modelVersion`

`trafficClass` 只用于路由、指标分组和扩大/停止流量决策，不改变审核规则、证据预算或结果语义。

## 质量治理

治理状态：

- `QUALITY_GUARDRAIL_TRIGGERED`: 生产保护状态。
- `ARCH_REVIEW_REQUIRED`: 架构复审状态。
- `QUALITY_REVIEW_REQUIRED`: 样本/质量复核状态。

生产保护高于架构复审。禁用、降级、回滚、冻结发布需要管理员或 P0 确认。严重泄露、安全风险或系统级错误可自动冻结发布，但不得自动修改已发布业务规则。

架构复审 SLA：

- initial triage: 2 business days。
- decision target: 5 business days。

紧急变更：

- `EmergencyChange` 包含 reason、approver、expiry、reviewBy、audit log。
- 14 天内同一审核点超过 2 次紧急变更，触发 `ARCH_REVIEW_REQUIRED` 并冻结新候选发布。
- `EmergencyChangeLevel2` 需要 P0 + admin 批准，max ttl 7 days，必须包含 rollback plan，并创建 ARCH review item。

`GUARDRAIL_DISABLED` 恢复流程：

1. root cause recorded。
2. candidate fix evaluated on current dataset。
3. P0/admin approval。
4. publish new RuleSetVersion。
5. run focused smoke/evaluation。
6. explicitly re-enable review point。

不得仅通过清除状态位恢复。

## 样本集生命周期

统一时间线：

- `reviewBy` 到期 -> `ruleSetDatasetStatus=EXPIRED`，继续运行已发布规则集，但禁止新候选发布。
- EXPIRED 60 天 -> 管理台和 P0 预警“30 天后将阻止正常新任务”。
- EXPIRED 90 天 -> `QUALITY_GUARDRAIL_TRIGGERED`，阻止正常新任务，仅允许受限评测或明确临时批准。
- 已完成快照和已开始 execution -> 不追溯失效。

超过 90 天后的新普通任务 API 行为：

```text
HTTP 409
code=RULE_SET_NOT_AVAILABLE
reason=DATASET_EXPIRED_GUARDRAIL
retryable=false
operatorActionRequired=true
```

临时运行必须先创建 `TemporaryRuleSetRunApproval`，且 `approvedByP0` 和 `approvedByAdmin` 均为必填，`expiresAt` 最长 24h。

## 上线前样本与验证

目标样本集最低建议：

- at least 50 contracts before pilot。
- each priority contract type >= 10 samples where available。
- include long contract, complex table, merged cell, control, missing-clause and conflicting-evidence cases。
- include both expected-correct and expected-risk variants。
- at least 30 samples must be de-identified real contracts, approved historical cases, or authorized original contracts in an isolated evaluation environment。

预算默认值验证：

- run target sample set。
- record token distribution p50/p90/max。
- record model call count p50/p90/max。
- record budget-related SYS-* rate。
- record queue wait estimate under STANDARD and DEEP_REVIEW。

若预算相关 `SYS-*` 在核心审核点上高频出现，不得直接进入生产试点。

## 基线冻结文档

- 运行时的模型 timeout、retry 和 circuit breaker 基线以 `docs/model-gateway-budget-baseline.md` 为准
- Word parser 的 `.docx/.doc` MVP 边界和 preview 定位口径以 `docs/word-parser-mvp-boundary.md` 为准

## 待确认

- 部署拓扑。
- 环境划分。
- GPU/A30 资源池和模型网关。
- 日志、指标和告警平台。
- 备份与恢复演练方案。
- secrets 和加密密钥管理。
- 预生产和生产模型协议、timeout、schema、主要配置版本如何保持一致。
- canary 扩大或停止流量的责任人。
