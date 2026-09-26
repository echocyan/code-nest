# 21: 压测环境与造数

**What to build:** 用一条命令起一套资源受限的压测环境：2 个应用实例，前面由 Nginx 做负载均衡，加上全部中间件。再用一条命令造出可复现的 10 万级数据，派生数据（计数、ES、布隆过滤器、Feed）通过系统自带的重建路径生成。详见决策票 12。

**Blocked by:** 11, 13, 14, 16, 18, 19, 20

Status: closed

- [x] **应用镜像**：多阶段 Dockerfile，运行时基础镜像为 `eclipse-temurin:21-jre`，`-Xmx1g`。
- [x] **压测 compose**：叠加在本地 compose 之上，包含 2 个应用实例和 Nginx。
  - 各容器的 CPU 和内存上限按决策票 12 设定。
  - 模式开关通过环境变量注入；限流关闭；Nginx 配置为可信代理。
- [x] **造数程序**：放在 loadtest 模块，是一个用 JDBC 多行批量 insert 的 Java main，使用固定随机种子。
  - 规模：10 万用户、10 万篇文章、约 500 万条关注关系、10 个大 V（各 2 万粉丝）、100 个重度用户（各关注 500 人）。
  - 点赞、收藏、评论按随机比例生成；点赞行要写入被点赞文章的 `author_id`。
  - 正文由技术词库加句子模板生成。
  - 造数账号的密码统一，方便 k6 登录。
- [x] **派生数据**：造数完成后，调用管理端口上的计数对账端点和 search-rebuild 端点，再重启应用以重建布隆过滤器；Feed 收件箱在首次读取时懒重建。
- [x] **使用说明**：写一份简短的操作步骤，并记录造数耗时和各表的最终行数。

## Comments

- **发件箱重建**：Feed 发件箱原本只在消费 `article.published` 时写入，造数直接写库后全是空的。新增启动重建：`feed:outbox:ready` 不存在时，`FeedFanoutService.rebuildOutboxesIfAbsent` 经 `ArticleApi.listPublishedStates` 按文章 ID 正序遍历已发布文章，pipeline 写入各作者的发件箱。由 `FeedOutboxLoader` 在启动时调用，HTTP 测试见 `FeedPushPullApiTest.lostOutboxesAreRebuiltOnRestart`。
- **镜像**：根目录 `Dockerfile`，构建阶段 `eclipse-temurin:21-jdk`（用户已同意）加 BuildKit 缓存挂载，`jarmode=tools` 分层解开；打出的 jar 不含 docker-compose 与 devtools。
- **compose**：`compose.loadtest.yaml` 把项目名设为 `code-nest-loadtest`，数据卷与本地开发环境分开。中间件加了健康检查，app-2 等 app-1 健康后才启动，避免两个实例同时跑 Flyway、同时建 ES 索引。MySQL 另设 `innodb_buffer_pool_size=1G`。应用容器设 `TZ=Asia/Shanghai`，定时对账的 cron 才按北京时间。Nginx 固定 IP 172.30.0.10，作为 `rate-limit.trusted-proxies`。
- **造数**：`code-nest-loadtest` 的 `Seeder`，`rewriteBatchedStatements=true` 让驱动把每批 1000 行改写成多行 insert。ID 用雪花格式（创建时间 + 行序号）；关注表超过 2^22 行，按行序号单调分配时间保证唯一。词库放在 `vocabulary.txt`，搜索压测可以复用。
- **派生数据**：`seed.sh` 以 es 档启动、造数、`FLUSHALL` 后重启两个实例、对账、search-rebuild。对账逐个对象提交，默认要 1 小时以上，超过对账锁 1 小时的有效期；脚本在造数与对账期间设置 `innodb_flush_log_at_trx_commit=2`、`sync_binlog=0`，对账降到约 4 分钟，结束后用 `trap` 恢复。
- **耗时与行数**：见 `code-nest-loadtest/README.md`，整套流程约 11 分钟。
