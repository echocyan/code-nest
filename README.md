# 码巢 code-nest

开发者技术社区后端。技术栈：Java 21、Spring Boot 4、MySQL、MyBatis-Plus、Redis、Sa-Token、RabbitMQ、Elasticsearch。

## 模块

| 模块 | 职责 |
|---|---|
| `code-nest-common` | 纯 Java：统一返回体、错误码、分页结构 |
| `code-nest-framework` | 基础设施：Web 约定、MyBatis-Plus、Redis、MQ、ES 客户端 |
| `code-nest-modules/*` | 业务模块；跨模块只能调用对方的 `api` 包（ADR-0001，ArchUnit 强制） |
| `code-nest-app` | 启动类、配置、端到端集成测试 |
| `code-nest-loadtest` | 造数与压测（不打进应用 jar） |

## 本地运行

需要 Docker。在 IDEA 中运行 `code-nest-app` 的 `CodeNestApplication` 即可：`spring-boot-docker-compose` 会按根目录 `compose.yaml` 拉起 MySQL、Redis、RabbitMQ 与带 IK 分词的 ES，并自动注入连接信息。

- 工作目录需为 `code-nest-app`（IDEA 与 `spring-boot:run` 的默认值），compose 文件以 `../compose.yaml` 引用。
- 也可以运行测试源码里的 `TestCodeNestApplication`，改用 Testcontainers 拉起中间件。

接口文档：<http://localhost:8080/swagger-ui.html>

## 测试

```bash
./mvnw test
```

集成测试通过 Testcontainers 启动全部中间件，首次运行会构建带 IK 插件的 ES 镜像。
