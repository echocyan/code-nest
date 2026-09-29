# 码巢 code-nest

一个面向开发者的技术社区后端。

## 功能

- **用户**：注册、登录、个人资料与用户主页
- **文章**：草稿与发布、分类与标签、两级评论
- **互动**：点赞、收藏
- **关注**：关注作者，阅读关注 Feed
- **通知**：被点赞、评论、回复、关注时收到通知
- **搜索**：按关键词搜索文章，支持分类、标签筛选
- **热榜**：按互动数据和发布时间定期计算

## 技术栈

| 类别       | 技术                            |
|------------|---------------------------------|
| 框架       | Java 21、Spring Boot 4.1        |
| 数据库     | MySQL 8.4、MyBatis-Plus、Flyway |
| 缓存       | Redis 8.6、Caffeine             |
| 消息队列   | RabbitMQ 4.3                    |
| 搜索       | Elasticsearch 9.4（IK 分词）    |
| 认证       | Sa-Token                        |
| 测试与压测 | JUnit 5、Testcontainers、k6     |

## 主要设计

- **计数**：点赞等计数经 Outbox 投递到 MQ，在 Redis 中用 Lua 原子去重和累加，定时批量写回 MySQL，并定期对账修正。
- **Feed**：普通作者发文推送到粉丝的收件箱，大 V 的文章在读取时从发件箱拉取，两者合并后按文章 ID 分页。
- **搜索**：文章变更经 Outbox 和 MQ 同步到 ES，用版本号防止旧数据覆盖新数据；索引通过别名访问，重建时不影响搜索。
- **缓存**：Caffeine 与 Redis 两级缓存，写后删除缓存并通过 Pub/Sub 通知各实例清理本地缓存；用布隆过滤器、空值缓存和随机 TTL 应对穿透和雪崩。
- **消息可靠性**：事务内写 Outbox、提交后发送，失败由定时任务补发；消费端按消息 ID 幂等，多次重试失败后进入死信队列。
- **其他**：基于 Redis 的滑动窗口限流、定时计算的热榜、异步生成的通知。

系统架构设计见[架构总览](docs/architecture.md)，表、Redis key、MQ 队列与 ES 索引见[存储与消息清单](docs/data.md)。

## 项目结构

项目是一个 Maven 多模块的模块化单体，业务模块之间只能通过对方的 `api` 包交互。

```text
code-nest
├── code-nest-common                 # 公共定义：统一返回体、错误码、分页结构
├── code-nest-framework              # 基础设施
│   ├── auth                         #   Sa-Token 认证
│   ├── cache                        #   两级缓存、布隆过滤器
│   ├── jackson                      #   JSON 序列化（Long 转字符串等）
│   ├── lock                         #   Redis 分布式锁
│   ├── mq                           #   事件发布、Outbox 补发、消费幂等
│   ├── mybatis                      #   MyBatis-Plus 配置
│   ├── openapi                      #   接口文档
│   ├── ratelimit                    #   滑动窗口限流
│   └── web                          #   全局异常处理、接口前缀
├── code-nest-modules                # 业务模块
│   ├── code-nest-user               #   用户与认证
│   ├── code-nest-article            #   文章、分类与标签、评论、热榜
│   ├── code-nest-interaction        #   点赞与收藏
│   ├── code-nest-social             #   关注与 Feed
│   ├── code-nest-notification       #   通知
│   ├── code-nest-search             #   搜索与 ES 同步
│   └── code-nest-counter            #   计数
├── code-nest-app                    # 启动类、配置与集成测试
├── code-nest-loadtest               # 造数程序、k6 脚本与压测结果
├── docker/elasticsearch             # 带 IK 分词插件的 ES 镜像
├── docs                             # 文档：架构总览、存储与消息清单、压测报告、架构决策记录
├── compose.yaml                     # 本地开发用的中间件
├── compose.loadtest.yaml            # 压测环境：2 个应用实例、Nginx 与 k6
└── Dockerfile                       # 应用镜像
```

业务模块内部大致按以下方式分包（以 article 为例）：

```text
com.echocyan.codenest.article
├── api            # 对其他模块开放的接口、DTO 与领域事件
├── controller     # HTTP 接口
├── service        # 业务逻辑，实现类在 impl 下
├── mapper         # MyBatis-Plus Mapper
├── entity         # 数据库实体
├── dto / vo       # 请求与响应对象
├── convert        # MapStruct 对象转换
└── listener       # MQ 消费者
```

模块之间的依赖是单向的，例如 article 依赖 user 和 counter，notification 依赖 article、interaction 和 social；counter 不依赖任何业务模块。这些约束由 ArchUnit 测试检查。

## 本地运行

需要 JDK 21 和 Docker。

在 IDEA 中运行 `code-nest-app` 模块的 `CodeNestApplication`，Spring Boot 会按根目录的 `compose.yaml` 自动拉起 MySQL、Redis、RabbitMQ 和 Elasticsearch，并注入连接配置。首次启动需要构建带 IK 分词插件的 ES 镜像，会稍慢一些。

启动后访问 <http://localhost:8080/swagger-ui.html> 查看接口文档。业务接口的前缀是 `/api/v1`，登录后在请求头中携带 `Authorization: Bearer <token>`。

## 配置

限流额度、热榜权重、大 V 阈值等配置见 [application.yaml](code-nest-app/src/main/resources/application.yaml)。

## 测试

```bash
./mvnw test
```

测试以 HTTP 接口级别的集成测试为主，通过 Testcontainers 启动全部中间件。

## 压测

```bash
./code-nest-loadtest/seed.sh       # 启动压测环境并生成数据
./code-nest-loadtest/bench.sh a    # 运行场景 a；可选 a/b/c/d
```

详细说明见 [code-nest-loadtest/README.md](code-nest-loadtest/README.md)。

## 压测结果

| 场景 | 压测内容                                   | 结果                                               |
|------|--------------------------------------------|----------------------------------------------------|
| 计数 | 200 并发对同一篇文章反复点赞、取消         | QPS 499，P99 1.4s，落库后计数与点赞行数一致        |
| Feed | 200 并发以关注 500 人的用户读 Feed 首页    | QPS 816，P99 1.1s；4999 个粉丝的推送 160ms         |
| 搜索 | 50 并发在 10 万篇文章中按关键词搜索        | QPS 187，P99 409ms                                 |
| 缓存 | 300 并发读文章详情，访问集中在少数热门文章 | QPS 4280，P99 274ms，每千次请求约 11 次 MySQL 查询 |

测试环境为单机，2 个应用实例加 Nginx，各容器限制了 CPU 和内存，数据量为 10 万用户、10 万篇文章、500 万条关注关系，详见 [压测报告](docs/benchmark.md)。
