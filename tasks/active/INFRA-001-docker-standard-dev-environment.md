# INFRA-001：CQCP Docker 唯一标准开发环境收口

状态：阻塞
类型：INFRA 父任务

优先级：P0
负责人：Codex
创建日期：2026-06-13

来源：用户明确要求；docs/deployment.md 当前仍保留混合本地启动口径，需要收口为 Docker Compose 唯一标准开发、验证、测试环境。

## 背景

当前项目由 Codex 主控，后续可能引入 Claude Code + DeepSeek 执行局部 TASK_SPEC。为了避免 Codex、Claude Code、DeepSeek、本机进程和 Docker 容器之间产生环境混乱，从本任务完成后，CQCP 必须建立 Docker Compose 作为唯一标准开发、验证、测试环境。

## 目标

* 检查并补齐 Docker Compose 服务：PostgreSQL、后端 `api-server`、前端 `admin-web`。
* 固定推荐端口：
  * 前端：`15173`
  * 后端：`18080`
  * PostgreSQL：`54329`
* 固定 Compose project name：`cqcp`。
* 确认后端容器通过容器网络连接 PostgreSQL 容器。
* 确认前端容器通过容器网络访问后端 API。
* 确认前端业务代码不写死 `localhost:18080`。
* 确认后端数据库连接通过环境变量配置，不写死本机端口。
* 评估是否需要补齐 Gradle Wrapper。
* 更新部署文档，明确 Docker Compose 是唯一标准开发、验证、测试方式。
* 如 Docker Hub 镜像拉取仍失败，明确记录阻塞原因和处理建议。

## 非目标

* 不进入 `TASK-020`。
* 不修改业务功能逻辑。
* 不修改 `PRD.md`。
* 不修改 `docs/ARCHITECTURE.md`。
* 不创建 `TASK_SPEC`。
* 不启动 Claude Code + DeepSeek。
* 不引入 Kubernetes、云部署、CI/CD。
* 不把混合模式写成备用开发方案。
* 不 push。

## Task Context

### Required Context

* AGENTS.md
* CURRENT_CONTEXT.md
* docs/deployment.md
* deploy/compose/compose.yml
* apps/admin-web/package.json
* apps/api-server/build.gradle.kts
* .gitignore
* 本任务包

### Optional Context

* apps/admin-web/Dockerfile
* apps/api-server/Dockerfile
* apps/admin-web/vite.config.js
* apps/api-server/src/main/resources/application.yml
* deploy/env/.env.example

### Out of Scope

* 业务审核链路。
* Result URL / 普通结果页 / 管理台 UI 业务实现。
* 数据库迁移。
* 模型调用、EvidenceSlot、ReviewPointFamily、CandidateResolver。

## 范围

### 包含

* Docker Compose 服务端口与容器互联配置。
* Compose project name 与 CQCP 专属网络/数据卷命名。
* 前端容器 Nginx API 反向代理配置。
* 前端开发端口配置与标准端口对齐。
* 部署文档与项目记忆写回。
* Docker Compose 启动验证或阻塞记录。

### 不包含

* 不新增复杂启动脚本。
* 不新增云部署或 CI/CD。
* 不改业务功能逻辑。
* 不提交业务 TASK 代码。

## 约束

* Docker Compose 是唯一标准开发、验证、测试方式。
* 如果 Docker Compose 启动失败，应暂停业务开发，优先修复 Docker 环境。
* 非 Docker 启动只允许作为临时故障定位手段，不得作为任务验收依据。
* 启动标准 Compose 前必须确认宿主机 `54329` 未被旧测试容器占用。
* 不使用 `git add .`。

## 交付物

* `deploy/compose/compose.yml`
* `apps/admin-web/Dockerfile`
* `apps/admin-web/nginx.conf.template`
* `apps/admin-web/vite.config.js`
* `deploy/env/.env.example`
* `docs/deployment.md`
* `CURRENT_CONTEXT.md`
* `changelog/2026-06.md`
* 本任务包

## 验收标准

* 可以用 Docker Compose 启动 CQCP 标准开发环境，或明确记录 Docker Hub 镜像拉取阻塞。
* PostgreSQL、后端、前端都由 Docker Compose 管理。
* 前端访问地址为 `http://localhost:15173`。
* 后端健康检查地址为 `http://localhost:18080/actuator/health`。
* PostgreSQL 暴露端口为 `54329`。
* 文档明确 Docker Compose 是唯一标准开发、验证、测试方式。
* 文档明确非 Docker 启动只允许临时排障，不作为备用开发方式。
* 不触碰业务功能范围。

## 测试与验证

* 已通过：`docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml config`
  * project name：`cqcp`
  * network：`cqcp_default`
  * volume：`cqcp_postgres_data`
  * ports：`15173:80`、`18080:8080`、`54329:5432`
* 未执行：`docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml up -d --build`
  * 原因：旧容器 `cqcp-postgres-test` 正在运行并占用宿主机 `54329`。
* 未执行：后端健康检查 `http://localhost:18080/actuator/health`
* 未执行：前端访问 `http://localhost:15173`

## 文档更新要求

* 更新 `docs/deployment.md`：是。
* 更新 `CURRENT_CONTEXT.md`：是。
* 更新 `changelog/2026-06.md`：是。
* 新增或更新 ADR：否。本任务是部署执行标准收口，不改变核心审核链路、模型职责、SYS/Finding、EvidenceSlot、ReviewPointFamily 或 CandidateResolver。

## 风险

* Docker Hub 基础镜像拉取可能仍失败。若失败，本任务必须明确阻塞，不得继续推进业务 TASK。
* 当前 `apps/api-server` 没有 Gradle Wrapper；当前 Dockerfile 使用 `gradle:8.10.2-jdk21` builder 镜像，因此 Docker 构建本身不依赖 wrapper。是否改为 wrapper 构建属于后续 INFRA 优化，不在本任务中默认扩大。
* 旧容器 `cqcp-postgres-test` 已检查并删除，未删除任何 Docker volume。
* 标准 Compose 启动当前阻塞于 Docker Hub 基础镜像拉取：`gradle:8.10.2-jdk21` 与 `node:20-alpine` 获取匿名 token 超时。

## 待确认

* 是否需要后续引入内部镜像仓库、镜像加速器或锁定基础镜像 digest。
* 是否需要后续将 Compose 健康检查和服务依赖升级为显式 `healthcheck`。
* 是否需要后续改用镜像加速器、代理、内部镜像仓库或已缓存基础镜像，以解除 Docker Hub 拉取阻塞。

## 完成记录

* 完成日期：2026-06-13。
* 变更文件：
  * `deploy/compose/compose.yml`
  * `deploy/env/.env.example`
  * `apps/admin-web/Dockerfile`
  * `apps/admin-web/nginx.conf.template`
  * `apps/admin-web/vite.config.js`
  * `docs/deployment.md`
  * `CURRENT_CONTEXT.md`
  * `changelog/2026-06.md`
  * `tasks/active/INFRA-001-docker-standard-dev-environment.md`
* 验证结果：
  * `docker ps --filter "name=cqcp-postgres-test"` 初始确认旧容器仍在运行并占用 `54329`。
  * `docker inspect cqcp-postgres-test` 确认旧容器挂载匿名 Docker volume，环境变量显示 `POSTGRES_DB=cqcp_test`，未设置 `POSTGRES_USER`。
  * `docker exec cqcp-postgres-test psql -U cqcp -d cqcp -c "\dt"` 失败：角色 `cqcp` 不存在，说明旧容器与标准 Compose 用户/库口径不一致。
  * 基于容器实际环境，使用默认用户 `postgres` 只读检查 `cqcp_test`，确认仅有 V1 基线 6 张表，且 6 张表行数均为 0。
  * 已执行 `docker stop cqcp-postgres-test` 与 `docker rm cqcp-postgres-test`，仅删除旧容器，未删除 Docker volume。
  * `docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml config` 通过。
  * `docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml up -d --build` 未通过：Docker Hub 拉取 `gradle:8.10.2-jdk21` 与 `node:20-alpine` 元数据时获取匿名 token 超时。
  * `docker compose --env-file deploy/env/.env.example -f deploy/compose/compose.yml ps` 显示当前 CQCP Compose 无运行服务。
* 遗留问题：
  * Docker Hub 基础镜像拉取阻塞仍未解除，标准 CQCP Compose 环境尚未启动成功。
  * 前端访问、后端健康检查、PostgreSQL 标准容器状态均因 Compose 构建失败未能验证。
* 备注：本任务未修改业务功能逻辑，未进入 `TASK-020`，未创建 `TASK_SPEC`，未删除任何非 `cqcp-postgres-test` 的容器，未删除 Docker volume。
