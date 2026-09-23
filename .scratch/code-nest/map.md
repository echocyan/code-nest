# 码巢 code-nest：从空仓库到可交付的项目规格

Label: wayfinder:map

## Destination

一份 **锁定的项目规格**：业务范围、四个主打技术亮点（计数系统、Feed
推拉结合、搜索与同步、多级缓存）及配角（消息可靠性、热榜、限流）的方案、工程结构、数据模型、测试与压测方案全部定下，可直接交给
`to-spec` → `to-tickets` → 实现。

## Notes

- **用途**：求职简历上的 Java 后端项目，每个技术亮点都要能按 STAR 讲清，并用压测数据给出前后对比（R）。
- **分工**：全部代码由 Claude 编写，用户 review 与决策。
- **技术栈**：Java 21、Maven、Spring Boot 4.1.1、MySQL、MyBatis-Plus、Redis、Sa-Token、RabbitMQ、Elasticsearch。
- **中间件版本**：以用户本地 Docker 镜像为准——`mysql:8.4`、`redis:8.6`、`rabbitmq:4.3.5-management`、`elasticsearch:8.19.21`。IK 分词插件用 8.19.21 对应版本：https://get.infini.cloud/elasticsearch/analysis-ik/8.19.21 。其他额外镜像/插件先问用户。不做逐项兼容性调研，兼容问题在实现中暴露再处理。
- **形态**：纯后端 + OpenAPI 文档；Maven 多模块的模块化单体；本地 docker compose 一键起中间件；无 CI，但要有集成测试。
- **业务范围**：用户、文章（标签/分类）、两级评论、点赞/收藏、关注 + Feed、通知、搜索、热榜。
- **语言**：规格/票/ADR 用中文；代码标识符与 commit message 用英文；注释中文、克制。
- **术语**：以根目录 `CONTEXT.md` 为准。
- **每个 grilling 票**：调用 Skill `grilling` 和 `domain-modeling`；方案涉及深模块/接缝时加 `codebase-design`。

## Decisions so far

<!-- 每个已关闭的票一行：[票名](link)：一句话结论 -->

- [Sa-Token 用法调研](issues/01-sa-token-usage.md)：Redis 用 `sa-token-redis-template` 且避开 Jackson 2；注解鉴权需注册 `SaInterceptor`；MockMvc 测试需挂上下文 Filter；Session 存对象需注册 JSON 白名单；Boot 4.1.1 兼容性不专门验证，实现中暴露再处理
- [工程结构与测试基础设施](issues/02-project-structure.md)：按业务划分的 Maven 多模块（common / framework / modules/* / app），跨模块只能调用对方 `api` 包（ArchUnit 强制）；返回体 `{code,message,data}` 加语义化 HTTP 状态码；雪花 ID；两种分页；compose 管中间件、应用在 IDEA 运行；Testcontainers；Flyway；SpringDoc；MapStruct
- [领域与数据模型](issues/03-domain-data-model.md)：分类单选、标签多选且均为系统预置；文章只有草稿和已发布两种状态；评论与回复同表；内容软删除、关系硬删除；只有文章能点赞；计数放在独立计数表，并拆出无依赖的 counter 模块以消除依赖环；不建外键；附完整表结构草案

## Not yet specified

- **通知模块的实现**：通知如何产生、聚合（如"张三等 5 人赞了你"）、存储与未读数，取决于消息可靠性底座与计数系统的方案。
- **API 设计规范**：URL 风格与命名约定；各接口的分页方式由业务票分别决定后，再看是否需要统一收敛。
- **种子数据生成**：压测需要的用户/文章/关系规模与生成方式，随压测方案一起浮现。
- **STAR 叙事素材**：每个亮点的"问题—方案—数据"如何沉淀，待亮点方案与压测方案都定后再看是否需要单独的票。

## Out of scope

- 前端页面：不需要，用 OpenAPI 文档 + 压测报告展示即可。
- 微服务 / Spring Cloud：引入大量与亮点无关的基础设施。
- 签到、积分、UV 统计；后台管理与审核：与主打亮点无关。
- 实时推送（SSE/WebSocket）：用户明确不做。
- CI 与云部署：本地 docker compose 运行即可。
- 图片上传与对象存储、注销账号、评论点赞、收藏夹：[领域与数据模型](issues/03-domain-data-model.md)里为控制业务复杂度删掉，都不带来技术亮点。
- 自建号段发号器（如 Leaf）：[工程结构与测试基础设施](issues/02-project-structure.md)已选用 MyBatis-Plus 雪花 ID，发号器不是主打亮点。
