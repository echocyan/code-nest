# 压测方案

Type: grilling
Status: resolved
Blocked by: 06, 07, 08, 09, 10

## Question

（背景：各亮点都已预留可切换的基线，对比对象如下：`counter.mode` 取 sync-db / redis-async，场景是热门文章被大量并发点赞；
`feed.mode` 取 pull / push-pull，场景是关注几百人的用户读 Feed，外加大 V 发文时的写扩散；`search.mode` 取 mysql-like /
es，数据量 10 万篇文章；`cache.mode` 取 none / redis / two-level，场景是热门文章详情。压测期间用 `rate-limit.enabled=false`
关闭限流。另外，Feed 票遗留了一个问题：关注列表是否需要缓存，视压测结果而定。）

如何为每个主打亮点产出可信的前后对比数据：压测工具（k6 vs JMeter）、造数规模与方式、每个亮点的基线版本（优化前）如何保留或切换、测什么指标（QPS、P99、DB
负载）、结果如何记录成简历可用的数字？压测时应用如何容器化（Dockerfile、每个实例的 CPU 和内存上限、多实例加 Nginx
负载均衡，以便演示多实例本地缓存失效）？

## Answer

1. **工具**：用 k6。脚本用 JS 编写、纳入版本管理，结果导出 JSON 汇总。用户已同意新增三个镜像：`grafana/k6`、
   `eclipse-temurin:21-jre`、`nginx`。
2. **环境**：
    - 在 `compose.yaml` 之上叠加 `compose.loadtest.yaml`，启动两个应用实例，前面由 Nginx 做负载均衡。各模式开关通过环境变量注入。
    - 应用镜像用多阶段 Dockerfile 构建，运行时基础镜像为 `eclipse-temurin:21-jre`，不用 buildpacks。
    - 资源上限（机器共 18 核、15GB）：

      | 服务             | CPU  | 内存             |
           |------------------|------|------------------|
      | 应用（每个实例） | 2 核 | 1.5G（`-Xmx1g`） |
      | MySQL            | 2 核 | 2G               |
      | ES               | 2 核 | 2G（堆 1g）      |
      | Redis            | 1 核 | 1G               |
      | RabbitMQ         | 1 核 | 1G               |
      | Nginx            | 1 核 | —                |

      剩余 CPU 留给 k6。
    - 所有对比都在同一套环境下进行，因此缓存对比自然覆盖了多实例下本地缓存失效的情况。
    - 报告里要写明：压测在单机完成，关注的是相对提升，不是绝对性能。
3. **造数**：
    - 规模：
        - 10 万用户、10 万篇文章。
        - 平均每人关注 50 人，`follow` 表约 500 万行。
        - 10 个大 V，每人 2 万粉丝。
        - 100 个重度用户，每人关注 500 个作者。
        - 点赞、收藏、评论按随机比例生成。
    - 正文用"技术词库 + 句子模板"生成中文文本，使用固定随机种子，保证可复现。
    - 新增 Maven 模块 `code-nest-loadtest`，存放造数程序（Java main）、k6 脚本和结果，不打进应用 jar。造数程序用 JDBC 多行批量
      insert 直接写 MySQL。它属于工具代码，不受 ADR-0001 约束。
    - 派生数据都走系统自带的重建路径生成，造数过程顺带验证了这些路径：
        - 计数：执行各模块的对账。
        - ES：调用 `search-rebuild` 端点。
        - 布隆过滤器、Feed 发件箱：应用启动时重建。
        - Feed 收件箱：首次读取时懒重建。
4. **场景与指标**：每组先预热 30 秒，再稳态压测 2 分钟，跑 3 次取中位数。服务端指标用脚本在压测前后各采集一次
   `SHOW GLOBAL STATUS` 和 Redis `INFO commandstats`，取差值。不引入 Prometheus/Grafana。

   | 亮点   | 场景                                              | 对比的开关                            | 指标                                                                          |
      |--------|---------------------------------------------------|---------------------------------------|-------------------------------------------------------------------------------|
   | A 计数 | 200 个 VU 用不同账号，对同一篇文章反复点赞、取消  | `counter.mode` sync-db / redis-async  | QPS、P99、错误率、`Innodb_row_lock_waits` 增量；落库后断言计数等于 `COUNT(*)` |
   | B Feed | 200 个 VU 以重度用户身份读 Feed 首页              | `feed.mode` pull / push-pull          | QPS、P99；普通作者（4999 粉丝）发文后推送完成的耗时                           |
   | C 搜索 | 50 个 VU 用词库中的随机关键词搜索                 | `search.mode` mysql-like / es         | P99；人工抽查 10 个关键词的相关度                                             |
   | D 缓存 | 300 个 VU 访问文章详情，按 Zipf 分布集中在 Top 10 | `cache.mode` none / redis / two-level | QPS、P99、`Com_select` 增量、Redis 命令数                                     |

   压测时设置 `rate-limit.enabled=false`，关闭限流。
5. **结果记录**：
    - 原始结果存为 `code-nest-loadtest/results/<日期>-<场景>.json`，附服务端指标差值。
    - 汇总写入 `docs/benchmark.md`：先说明测试环境，再给每个亮点写一节 S/T/A/R（A
      部分链接到对应的决策票）。这份文档就是简历和面试用的底稿，因此迷雾中的"STAR 叙事素材"就此解决。
6. **关注列表缓存（Feed 票遗留）**：先按规则决定要不要做，不提前拍板。规则是：在 `push-pull` 模式下，如果关注列表查询占 Feed
   读取耗时的 30% 以上，就加一层 Redis Set 缓存（关注、取关时维护），然后补测一轮；否则不加。
