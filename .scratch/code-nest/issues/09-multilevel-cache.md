# 多级缓存与缓存治理

Type: grilling
Status: resolved
Blocked by: 03

## Question

文章详情等热点读如何缓存：Caffeine 本地缓存 + Redis 的两级结构、缓存 key 与过期策略、更新时的一致性（先更新库再删缓存？延迟双删？多实例本地缓存如何失效——Redis Pub/Sub 广播？）、穿透（布隆过滤器/空值）、击穿（互斥锁 vs 逻辑过期）、雪崩（随机过期）、热点 key 探测？

## Answer

1. **缓存范围**：
   - **文章详情**（元数据 + 正文）：Caffeine + Redis 两级缓存，归 article 模块。缓存里不放计数，计数通过 `CounterApi` 另行组装，所以点赞不会让文章缓存失效。
   - **用户简要信息**（`UserApi` 的批量查询）和**文章摘要**（`ArticleApi` 的批量查询）：只用 Redis 一级缓存，批量查询走 `MGET`。
   - **关注列表**：暂不缓存，因为走的是覆盖索引。
   - **"是否点赞/收藏"**：不缓存，直接查唯一索引。
2. **组件**：framework 模块提供 `TwoLevelCache`，只有三个方法：`get(key, loader)`、`getAll(keys, batchLoader)`、`evict(key)`。两级读取顺序、空值缓存、布隆过滤器、TTL 抖动、本地缓存失效广播都封装在组件内部。不用 Spring Cache `@Cacheable`，因为它很难表达批量查询和穿透防护。
3. **一致性**：采用 Cache-Aside。
   - **第一次删除**：先更新数据库，事务提交后（afterCommit）删除 Redis 中的 key，并广播本地缓存失效。
   - **第二次删除**：article 模块新增消费者 `article.cache-evict`，订阅 `article.updated` 和 `article.deleted` 后再删一次。这两个事件通过 Outbox 发出并带重试，第二次删除一定会执行；MQ 的投递延迟天然起到了"延迟双删"中延迟的作用。
   - **本地缓存失效**：通过 Redis Pub/Sub 频道 `cache:invalidate` 广播要失效的 key，各实例收到后清除本地缓存。Pub/Sub 不保证送达，漏收的实例依靠本地 TTL（60 秒）兜底。
   - **遗留问题**："读请求未命中后去数据库读到旧值，写请求删除缓存后，读请求才把旧值回填"这一竞态依然存在，不过窗口极小，最坏情况也只持续到 TTL 过期。这一点需要在文档里说明。
4. **过期与容量**：
   - Caffeine：最多 10000 条，写入 60 秒后过期（expireAfterWrite）。
   - Redis：TTL 为 30 分钟，再加 0–5 分钟的随机抖动，防止大量 key 同时过期造成雪崩。
5. **穿透**：布隆过滤器加空值缓存。
   - 布隆过滤器用 Redis 8 原生的 `BF.ADD` / `BF.EXISTS`，key 为 `bf:article`。文章创建时把 ID 加进去；过滤器判定为"一定不存在"的 ID 直接返回 404。
   - 应用启动时如果过滤器不存在，就按全部文章 ID 重建。
   - 文章删除后，过滤器仍会判定它可能存在，这时由空值缓存兜底：缓存一个空标记，TTL 60 秒。
6. **击穿**：依靠 Caffeine `get(key, loader)` 的同 key 合并加载，loader 先查 Redis、再查数据库。这样同一时刻落到数据库的查询数最多等于实例数，所以不引入分布式锁，也不用逻辑过期。面试时可以对比这三种方案各自适用的场景。
7. **热点 key**：不做专门的探测。Caffeine 的 W-TinyLFU 淘汰策略会让高频 key 自然留在本地缓存中。
8. **基线对比**：`cache.mode` 分三档：`none`（直接查数据库）、`redis`（只用 Redis 一级）、`two-level`（两级缓存）。压测场景是热门文章详情接口，对比三种模式下的 QPS 和 P99。
