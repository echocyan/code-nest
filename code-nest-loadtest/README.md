# 压测环境与造数

压测环境由 `compose.yaml` 加上 `compose.loadtest.yaml` 组成：MySQL、Redis、RabbitMQ、ES，2 个应用实例，前面一个 Nginx。

| 服务 | CPU | 内存 | 说明 |
|---|---|---|---|
| app-1、app-2 | 各 2 核 | 各 1.5G | `-Xmx1g`，管理端口分别映射到宿主机 8081、8082 |
| MySQL | 2 核 | 2G | `innodb_buffer_pool_size=1G` |
| ES | 2 核 | 2G | 堆 1g |
| Redis | 1 核 | 1G | |
| RabbitMQ | 1 核 | 1G | |
| Nginx | 1 核 | — | 宿主机 8080，轮询两个实例；固定 IP 172.30.0.10，应用把它当作可信代理 |

限流在压测环境中关闭。压测环境的 compose 项目名是 `code-nest-loadtest`，数据卷与本地开发环境分开；两者占用相同端口，不能同时运行。

## 从零造数

```bash
./code-nest-loadtest/seed.sh
```

脚本依次执行：

1. 以 `SEARCH_MODE=es` 构建镜像并启动环境，等待全部容器健康。app-2 等 app-1 健康后才启动，由 app-1 执行 Flyway 迁移、建好空的 ES 索引。应用容器的时区设为 `Asia/Shanghai`，日志与定时任务的 cron 按北京时间。
2. 临时放宽 MySQL 的提交刷盘（见下文），运行造数程序 `Seeder`，用 JDBC 批量 insert 写 MySQL，固定随机种子。它要求 `user` 表为空。
3. 清空 Redis 并重启两个实例：启动时重建布隆过滤器 `bf:article` 和各作者的 Feed 发件箱。
4. 调用 `POST :8081/actuator/counter-reconcile`，由对账生成全部计数。
5. 调用 `POST :8081/actuator/search-rebuild`，把已发布文章全量导入 ES。

Feed 收件箱不预先生成，读者首次读取 Feed 时从发件箱懒重建。

重新造数要先删除数据卷：

```bash
docker compose -f compose.yaml -f compose.loadtest.yaml down -v
./code-nest-loadtest/seed.sh
```

## 切换模式

模式开关通过同名环境变量注入，未设置的取默认的基线档：

```bash
COUNTER_MODE=redis-async FEED_MODE=push-pull SEARCH_MODE=es CACHE_MODE=two-level \
  docker compose -f compose.yaml -f compose.loadtest.yaml up -d --wait
```

## 造数账号

所有账号的密码都是 `loadtest123`。

| 用户名 | 人数 | 角色 |
|---|---|---|
| `bigv_00`–`bigv_09` | 10 | 大 V：各 20000 个粉丝、200 篇文章 |
| `heavy_000`–`heavy_099` | 100 | 重度用户：各关注 500 人（10 个大 V 加 490 个普通作者），不写文章 |
| `author_4999` | 1 | 普通作者：恰好 4999 个粉丝，比大 V 阈值少 1；50 篇文章 |
| `user_00000`–`user_19999` | 20000 | 普通作者：分摊其余文章 |
| `user_20000`–`user_99888` | 79889 | 普通用户 |

- 除重度用户外，每人另外随机关注 20–75 个普通作者。
- 文章发布时间分布在最近一年，约 5% 是草稿。正文是技术词库（`src/main/resources/vocabulary.txt`）加句子模板生成的 Markdown，每篇 1–5 个标签。
- 每人点赞 0–40 篇、收藏 0–10 篇别人的已发布文章。每篇已发布文章有 0–6 条评论，每条评论有 0–2 条回复。
- 计数表、通知表不直接写入：计数由对账生成，通知只在压测期间产生。

## 造数结果

在 18 核、15G 内存的机器上（WSL2）从空环境运行 `seed.sh`，共 11 分钟：

| 步骤 | 耗时 |
|---|---|
| 构建镜像（有缓存）并启动环境 | 1 分 19 秒 |
| 造数（含 Maven 编译；造数程序本身 3 分 54 秒） | 4 分 16 秒 |
| 清空 Redis、重启应用（重建布隆过滤器与发件箱） | 18 秒 |
| 计数对账 | 4 分 23 秒 |
| 重建搜索索引 | 35 秒 |

造数程序写入的行数：

| 表 | 行数 |
|---|---|
| `user` | 100,000 |
| `article` | 100,000（已发布 94,851） |
| `article_content` | 100,000 |
| `article_tag` | 299,027 |
| `follow` | 5,002,829 |
| `article_like` | 1,994,244 |
| `favorite` | 499,704 |
| `comment` | 569,971（评论与回复） |

对账写入计数的对象数：文章点赞 94,851、收藏 94,356、评论 81,440；用户粉丝 20,011、关注 100,000、文章 19,818、获赞 19,818；评论回复 189,875。

对账逐个对象单独提交。`seed.sh` 在造数与对账期间临时设置 `innodb_flush_log_at_trx_commit=2`、`sync_binlog=0`，提交时不等刷盘，结束后（包括中途失败）恢复为 1。不放宽时对账要 1 小时以上。
