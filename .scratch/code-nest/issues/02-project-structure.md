# 工程结构与测试基础设施

Type: grilling
Status: resolved
Blocked by:

## Question

Maven 多模块怎么划分（按层还是按业务模块、各模块依赖方向）？公共模块放什么（统一返回体、全局异常与错误码、ID 生成策略、分页、MyBatis-Plus 基础配置）？包命名约定？docker compose 放在哪、如何与 `spring-boot-docker-compose` 配合？集成测试用 Testcontainers 还是复用本地 compose 起的中间件？

## Answer

1. **模块结构**：按业务划分，业务模块放在聚合模块 `code-nest-modules` 下：
   ```
   code-nest/                    parent pom，统一管理依赖版本
   ├── code-nest-common          纯 Java：统一返回体、异常与错误码、分页结构、工具类
   ├── code-nest-framework       基础设施：MyBatis-Plus、Redis、MQ 可靠性底座、ES 客户端、Sa-Token、限流注解、全局异常处理
   ├── code-nest-modules/        聚合 pom，也是业务模块的父 pom，统一声明它们共用的依赖
   │   ├── code-nest-user            用户、认证
   │   ├── code-nest-article         文章、标签、评论与回复、热榜（热榜放哪可在热榜票里调整）
   │   ├── code-nest-counter         计数系统（[领域与数据模型](03-domain-data-model.md)为消除依赖环而拆出）
   │   ├── code-nest-interaction     点赞、收藏
   │   ├── code-nest-social          关注、Feed
   │   ├── code-nest-notification    通知
   │   └── code-nest-search          搜索、ES 同步
   ├── code-nest-app             启动类、配置文件、端到端集成测试
   └── code-nest-loadtest        造数程序、k6 脚本、压测结果（由[压测方案](12-load-test.md)追加，不打进应用 jar）
   ```
   依赖方向为 app → 业务模块 → framework → common。业务模块之间只能单向依赖，不能成环。
2. **跨模块调用**：一个模块只能使用另一个模块 `api` 包里的门面接口和 DTO（如 `ArticleApi.exists(id)`）。Service、Mapper、Entity 都属于模块内部，由 ArchUnit 测试强制执行，理由见 [ADR-0001](../../../docs/adr/0001-modular-monolith-api-package-seam.md)。需要反向通知时用事件：进程内走 Spring `ApplicationEvent`，需要可靠投递的走 MQ，具体规则由消息可靠性底座票决定。
3. **模块内分包**：`com.echocyan.codenest.<module>` 下分为 `api/ controller/ service/ mapper/ entity/ dto/ vo/ convert/`，不设 `model/` 中间层。Service 按 MyBatis-Plus 惯例写成 `IService` 接口加 `service/impl/` 下的 `ServiceImpl` 实现，查询用链式 Lambda 构造器（03 号实现票之后追加，见规格“Service 写法”）。对外发布的事件类放在 `api/event/`；按[消息可靠性底座](05-mq-reliability.md)的结论，不再单设 `event/` 包。启动类放在 `com.echocyan.codenest`。
4. **返回体与错误码**：body 统一为 `{code, message, data}`，同时按语义设置 HTTP 状态码（400/401/403/404/429/500）。业务错误码按模块分段：用户 1xxxx，文章 2xxxx，其余模块依次往后排。
5. **ID**：使用 MyBatis-Plus `ASSIGN_ID`（雪花 Long）。JSON 输出时 Long 全局序列化为字符串。Feed 游标可以直接用雪花 ID 的时间有序性。
6. **分页**：common 提供页码分页 `PageResult` 和游标分页 `CursorResult{list, nextCursor, hasMore}`，每个接口用哪种由各业务票决定。
7. **开发环境**：中间件放在根目录的 `compose.yaml` 里（mysql:8.4、redis:8.6、rabbitmq:4.3.5-management、es）。ES 用 `docker/elasticsearch/Dockerfile`，在 ES 镜像上安装 IK 插件（版本原定 8.19.21，实现时升级为 9.4.5，见 map Notes）。由 `spring-boot-docker-compose` 自动拉起并注入连接信息，`lifecycle-management: start-only`。应用在 IDEA 里本地运行。应用容器化放到压测方案票决定。
8. **集成测试**：用 Testcontainers 加 `@ServiceConnection`，所有测试类共享同一组容器（singleton）。ES 容器复用带 IK 的 Dockerfile。纯逻辑部分写普通 JUnit 单元测试。
9. **表结构**：用 Flyway 做版本化迁移，每个模块在自己的资源目录下放脚本。版本号全局唯一，以模块号作前缀（user `V1_001__`、article `V2_001__`……），与错误码分段对应。
10. **其他**：API 文档用 SpringDoc OpenAPI；对象转换用 MapStruct，并配好它与 Lombok 的注解处理器顺序；MyBatis-Plus 换成 `mybatis-plus-spring-boot4-starter`。
