# API Server Scaffold

本目录只承载 `TASK-006` 的纯技术脚手架：

- Java 21 + Spring Boot 3.2+ 基线
- MyBatis / Flyway 依赖入口
- Actuator 最小暴露
- 空的 migration 目录占位

本任务不实现：

- 审核业务链路
- OpenAPI 控制器
- 数据库表结构
- Word parser / Review Engine / ModelGateway 业务代码

建议命令：

- `gradle test`
- `gradle build`

如需可执行 wrapper，由后续具备 Java/Gradle 环境的任务补齐。

