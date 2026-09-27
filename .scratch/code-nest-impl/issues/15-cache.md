# 15: 多级缓存

**What to build:** 文章详情由各实例的 Caffeine 本地缓存加 Redis 两级缓存，用户简要信息、文章摘要由 Redis 缓存，热门文章的详情接口不再每次都查数据库。文章编辑或删除后，缓存可靠失效，读者不会长期看到旧内容；一个实例上的更新会让其他实例的本地缓存很快失效。并发请求同一个 key 时，每个实例只加载一次。不存在的文章 ID 被布隆过滤器直接拦截，不会反复打到数据库。详见决策票 09。

**Blocked by:** 03, 05

Status: closed

- [x] **`TwoLevelCache`**：只有三个方法：`get(key, loader)`、`getAll(keys, batchLoader)`（批量读取走 `MGET`）、`evict(key)`。
  - Redis TTL 为 30 分钟，加 0–5 分钟随机抖动。
  - 空值缓存 60 秒。
- [x] **Caffeine 本地缓存**：两级缓存的 L1，最多 10000 条，写入 60 秒后过期。读取顺序为 L1 → Redis → DB，利用 Caffeine 的同 key 合并加载防击穿。
- [x] **失效广播**：`evict` 时通过 Redis Pub/Sub 频道 `cache:invalidate` 广播，各实例收到后清除本地缓存；漏收时依靠 60 秒 TTL 兜底。
- [x] **布隆过滤器**：用 Redis 8 原生的 `bf:article`。
  - 文章创建时加入 ID；应用启动时如果过滤器不存在，就按全部文章 ID 重建。
  - 过滤器判定为"一定不存在"的 ID 直接返回 404。
  - 已删除的文章靠空值缓存兜底。
- [x] **接入范围**：
  - article 的文章详情缓存元数据和正文，不含计数，计数仍然单独组装。
  - `UserApi` 和 `ArticleApi` 的批量摘要查询走缓存。
- [x] **缓存失效**：
  - 编辑、发布、删除，以及用户修改资料后，在 afterCommit 中 `evict`。
  - 消费者 `article.cache-evict` 订阅 `article.updated` 和 `article.deleted` 做第二次删除。
- [x] **组件级测试**：
  - 两个 `TwoLevelCache` 实例之间的失效广播。
  - 并发请求同一个 key，loader 只执行一次。
  - 布隆过滤器拦截不存在的 ID。
  - 过滤器丢失后，重启时会重建。
- [x] **HTTP 测试**：编辑后详情立即是新内容；修改昵称后摘要里的昵称更新；不存在的 ID 重复访问都返回 404。
- [x] **文档**：写明 Cache-Aside 残留的竞态窗口。

## Comments

- **组件**（framework 的 `cache` 包）：
  - `TwoLevelCache<V>` 以 ID（`long`）为 key，缓存值以 JSON 存入 Redis，key 为 `cache:<name>:<id>`。`get`、`getAll` 的加载函数对不存在的对象返回 null 或不放进结果；`getAll` 对未命中的 ID 只调用一次 batchLoader，用 pipeline 回填。
  - 空值缓存存 JSON `null`，TTL 60 秒；正常值 TTL 为 30 分钟加 0–5 分钟随机抖动。Redis 读写失败不降级，异常直接抛出。
  - `TwoLevelCaches.create(name, type)` 创建只用 Redis 的缓存，`createTwoLevel(name, type, bloomFilter)` 创建两级缓存，各模块把它们注册成 Bean。只有 `article:detail` 是两级缓存；`article:brief`（`ArticleCacheConfig`）、`user:brief`（`UserCacheConfig`）只用 Redis。
  - `get` 的读取顺序：本地缓存 → 布隆过滤器 → Redis → 加载函数。同 key 合并加载靠 Caffeine 的 `get(key, mappingFunction)`，只在单个实例内生效，同一时刻落到数据库的查询数最多等于实例数。空值不放进本地缓存，已删除的文章每次经布隆过滤器和 Redis 的空值缓存返回。`getAll` 经 Caffeine 的批量加载先读本地缓存，不经过布隆过滤器。
  - 本地缓存返回的是同一个对象，调用方不能修改。
  - `evict` 的顺序：删除 Redis → 清除本实例的本地缓存 → 在 `cache:invalidate` 上发布 Redis key。先删 Redis 再清本地，本实例不会从 Redis 读回旧值。有活跃事务时整体推迟到 afterCommit 执行，删除失败只记日志；没有事务时立即删除，失败直接抛出。写方只需在写库后调用，不用自己注册事务回调。
  - 订阅由 `CacheInvalidationConfig` 注册的 `RedisMessageListenerContainer` 完成，`TwoLevelCaches` 按 key 前缀把消息分发给同名的本地缓存。本实例发出的广播也会收到，重复清除无害。
- **布隆过滤器**（`BloomFilter`，key 为 `bf:<name>`）：
  - 用 Lua 调 `BF.RESERVE`、`BF.MADD`、`BF.EXISTS`，误判率 0.001，预计容量 100 万，超出后 Redis 自动扩容。
  - 全量导入完成后才写入标记 `bf:<name>:ready`。过滤器或标记任一不存在时查询一律放行，不拦截。
  - `rebuildIfAbsent` 在过滤器和标记都存在时跳过，否则分批导入全部 ID，最后写标记。导入期间不会误判；导入中断时标记不存在，下次启动会重新导入。多个实例同时启动时各自导入一遍，重复加入无害。
  - `add` 在过滤器不存在时先删除标记，再按同样的参数新建过滤器。导入期间创建的文章不会漏掉；运行期间过滤器丢失时退回放行，直到下次启动重建。
  - 已知局限：Redis 从较早的快照恢复时，过滤器和标记都在，但缺少快照之后创建的文章，它们会返回 404，需要手动删除标记后重启。
- **接入**：
  - 文章详情缓存 `CachedArticleDetail`：文章实体、正文、分类、标签。作者经 `UserApi`、计数经 `CounterApi` 每次另行组装。已删除的文章与不存在的 ID 一样缓存为空值。
  - `ArticleApi.getBriefs`、`UserApi.getBriefs` 走 `getAll`。`findState`、各列表查询不走缓存。
  - 编辑、发布、删除都调用 `ArticleService.evictCache`，同时删除详情和摘要；`UserServiceImpl.updateProfile` 删除用户摘要。
  - 消费者 `ArticleCacheEvictListener` 消费 `article.cache-evict`（绑定 `article.updated`、`article.deleted`），再调用一次 `evictCache`。发布不做第二次删除。事件经 Outbox 一定会投递，但消费重试耗尽后进入死信队列，这时第二次删除不会执行。删除本身幂等，所以不加 `@IdempotentConsumer`。
  - `ArticleCacheConfig` 注册 `articleBloomFilter`（`bf:article`）与两个缓存。`ArticleServiceImpl.create` 在写库的事务里加入 ID，事务回滚只会留下一个误判。`ArticleBloomFilterLoader`（`SmartInitializingSingleton`）启动时按 `ArticleService.listIdsAfter` 每批 1000 个 ID 重建，包括草稿，不包括已删除的文章。
- **Cache-Aside 残留的竞态**：读请求未命中，从数据库读到旧值后停顿；这期间写请求更新数据库并删除缓存；读请求随后把旧值回填。旧值最多保留到 TTL 过期，即 30–35 分钟。只有读库比"写库加删缓存"还慢时才会出现，窗口极小。经 MQ 的第二次删除能覆盖回填发生在它之前的情况。发布没有第二次删除，所以残留时间以 TTL 为上限。这段说明也写在 `TwoLevelCache` 的类注释里。
- **测试**：
  - 编辑、发布、删除相关的测试先读一次详情，把它写进缓存，再断言写操作后详情立即更新。另外断言改昵称后详情里的作者昵称更新、编辑后收藏列表里的标题更新、不存在的 ID 连续访问都返回 404。文章创建后能读到详情，依赖创建时加入了过滤器。
  - `TwoLevelCacheTest`：另建一个 `TwoLevelCaches` 和它自己的订阅容器代表另一个实例，两边的本地缓存只经 Pub/Sub 互相通知。另外覆盖：并发加载只执行一次；布隆过滤器拦截；过滤器被删除后，再次 `rebuildIfAbsent` 会重建；导入中断后，重启会重新导入。各用例的缓存名和过滤器名都用 UUID 保证唯一。
  - 测试的多个 Spring 上下文共用一个 Redis：`bf:article` 由最先启动的上下文重建，之后各上下文创建的文章都会加入；上下文之间也会收到彼此的失效广播，只会多清一次本地缓存。
