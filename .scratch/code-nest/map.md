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
- **压测镜像**：用户已同意使用 `grafana/k6`、`eclipse-temurin:21-jre`、`nginx`。
- **中间件版本**：以用户本地 Docker 镜像为准——`mysql:8.4`、`redis:8.6`、`rabbitmq:4.3.5-management`、`elasticsearch:9.4.5`（与 Boot 4.1.1 管理的 9.x 客户端一致）。IK 分词插件用对应版本：https://get.infini.cloud/elasticsearch/analysis-ik/9.4.5 。其他额外镜像/插件先问用户。不做逐项兼容性调研，兼容问题在实现中暴露再处理。
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
- [认证与鉴权方案](issues/04-auth.md)：只支持用户名加密码登录，密码用 BCrypt；token 以 `Authorization: Bearer <uuid>` 传递，有效期 7 天，允许多端登录；Session 只存用户 ID；不区分角色；默认要求登录，公开接口加 `@SaIgnore`；只有 Controller 通过 `AuthContext` 取当前用户
- [消息可靠性底座](issues/05-mq-reliability.md)：跨模块副作用走 MQ；生产端用 Outbox 加 confirm，补发任务用 SKIP LOCKED 防止多实例重复；业务代码只调用 `publish(event)` 一个方法；消费端用 `@IdempotentConsumer` 和消费记录表做幂等；重试 3 次后进死信；只用一个 topic 交换机；事件是只带 ID 的轻量事件，放在 `api/event/`
- [计数系统](issues/06-counter-system.md)：点赞关系同步写库，只对热点计数行做优化；各模块经 `CounterApi.increment`（走 Outbox）上报计数，counter 不依赖任何业务模块；Redis Hash 存计数，Lua 原子完成幂等去重、冷 key 判断和累加；每 5 秒 SPOP 待落库集合，把绝对值批量写入 MySQL；由掌握真实数据的模块每周对账；浏览量是近似计数，直接加 Redis；`sync-db` 与 `redis-async` 两种实现可切换，用于压测对比
- [Feed 推拉结合](issues/07-feed.md)：普通作者推到粉丝收件箱，大 V 在读取时拉取；每个作者有发件箱（最近 100 篇）；收件箱 key 7 天过期，推送时跳过冷用户，用户回来时再重建；ZSet 的 score 直接用雪花 articleId，并作为分页游标；写入时尽量修正、读取时兜底过滤；`pull` 与 `push-pull` 两种实现可切换，用于压测对比
- [搜索与数据同步](issues/08-search-sync.md)：通过 Outbox + MQ 同步，消费者回查文章最新状态；`article.version` 兼作 ES 外部版本号，防止旧数据覆盖新数据；索引走别名，可零停机重建；写入用 ik_max_word，查询用 ik_smart；作者昵称和计数不进 ES；最多翻 50 页；`mysql-like` 与 `es` 两种实现可切换，用于压测对比
- [多级缓存与缓存治理](issues/09-multilevel-cache.md)：文章详情用 Caffeine+Redis 两级缓存，用户和文章摘要只用 Redis；业务代码只通过 `TwoLevelCache` 的 3 个方法访问缓存；提交后删缓存，再由 MQ 可靠地二次删除，本地缓存靠 Pub/Sub 广播失效；用 Redis 8 原生布隆过滤器加空值缓存防穿透；用 Caffeine 合并加载防击穿，不加分布式锁；TTL 加随机抖动防雪崩；`cache.mode` 三档可切换对比
- [热榜](issues/10-hot-list.md)：采用 Hacker News 式时间衰减公式，每 5 分钟由一个实例批量重算最近 7 天发布的文章，结果先写临时 ZSet，再用 RENAME 原子替换正式 ZSet，保留 Top 100；已删除的文章在读取时过滤；只有一个榜单
- [限流防刷](issues/11-rate-limit.md)：用 Redis ZSet 加 Lua 实现滑动窗口日志，时间取 Redis `TIME`；已登录按用户、匿名按 IP 限流，只对可信代理解析 XFF；通过可重复的 `@RateLimit` 注解声明，由拦截器在 `SaInterceptor` 之后执行；超限返回 429 和 `Retry-After`；Redis 故障时放行；有总开关
- [压测方案](issues/12-load-test.md)：k6，2 实例加 Nginx，每个容器限定 CPU 与内存；新增 loadtest 模块，用 JDBC 造 10 万级数据，派生数据走系统自带的重建路径生成；四组开关分别压测对比，每组 3 次取中位数，并采集服务端状态差值；结果按 STAR 写入 `docs/benchmark.md`；关注列表是否加缓存按 30% 规则决定
- [通知模块](issues/13-notification.md)：点赞、评论、回复、关注这四类事件产生通知，自己触发的不通知；不做聚合，同一动作靠 `dedup_key` 唯一键去重，防止反复操作刷屏；取消操作不撤回通知；表里只存 ID，展示信息读取时组装；未读数直接 COUNT，最多显示 99+；列表用游标分页
- [API 设计规范](issues/14-api-conventions.md)：所有接口挂在 `/api/v1` 下；开关型动作用幂等的 PUT/DELETE；时间用 ISO-8601，ID 用字符串，枚举用大写字符串；模块编号同时决定错误码号段和 Flyway 前缀；给出 39 个业务接口和 2 个管理端点的完整清单，评估过删减方案后全部保留

## Not yet specified

<!-- 迷雾已全部清空：种子数据和 STAR 素材在压测方案中解决；通知模块和 API 规范已转为独立的票。 -->

**已到终点**：所有决策票都已关闭，没有遗留的待决问题。下一步用 `to-spec` 把 Decisions so far 指向的各张票汇编成项目规格，再用 `to-tickets` 拆成实现票。

## Out of scope

- 前端页面：不需要，用 OpenAPI 文档 + 压测报告展示即可。
- 微服务 / Spring Cloud：引入大量与亮点无关的基础设施。
- 签到、积分、UV 统计；后台管理与审核：与主打亮点无关。
- 实时推送（SSE/WebSocket）：用户明确不做。
- CI 与云部署：本地 docker compose 运行即可。
- 邮箱验证、短信登录、第三方 OAuth、refresh token、角色体系：[认证与鉴权方案](issues/04-auth.md)只保留用户名加密码，需要接外部服务的都不做，也没有管理员可以操作的功能。
- Canal 订阅 binlog；搜索建议、自动补全、拼音搜索；搜索结果按热度排序：[搜索与数据同步](issues/08-search-sync.md)为了控制复杂度、守住模块边界，这些都不做。
- 自建热点 key 探测、用分布式锁防缓存击穿：[多级缓存与缓存治理](issues/09-multilevel-cache.md)已用 Caffeine 的 W-TinyLFU 和同 key 合并加载覆盖了这两类场景。
- 日榜、周榜、总榜、分类榜，按事件实时更新热度：[热榜](issues/10-hot-list.md)只保留一个 7 天候选的定时重算榜单，这些都不做。
- 全局接口总限流、登录失败锁定账号：[限流防刷](issues/11-rate-limit.md)只对具体的写操作和匿名接口限流。
- Prometheus/Grafana 监控栈：[压测方案](issues/12-load-test.md)用压测前后采集的服务端状态差值代替。
- 通知聚合、按类型分 Tab、通知定期清理：[通知模块](issues/13-notification.md)用去重防刷屏已经足够。
- 图片上传与对象存储、注销账号、评论点赞、收藏夹：[领域与数据模型](issues/03-domain-data-model.md)里为控制业务复杂度删掉，都不带来技术亮点。
- 自建号段发号器（如 Leaf）：[工程结构与测试基础设施](issues/02-project-structure.md)已选用 MyBatis-Plus 雪花 ID，发号器不是主打亮点。
