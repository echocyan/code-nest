# 18: 缓存 two-level 档

**What to build:** `cache.mode=two-level` 下，热门文章详情由各实例的 Caffeine 本地缓存直接返回。一个实例上的文章更新后，其他实例的本地缓存很快失效。并发请求同一个 key 时，每个实例只会加载一次。不存在的 ID 被布隆过滤器直接拦截。

**Blocked by:** 17

Status: closed

- [x] **Caffeine 本地缓存**：`TwoLevelCache` 的 L1，最多 10000 条，写入 60 秒后过期。读取顺序为 L1 → Redis → DB，利用 Caffeine 的同 key 合并加载防击穿。
- [x] **失效广播**：`evict` 时通过 Redis Pub/Sub 频道 `cache:invalidate` 广播，各实例收到后清除本地缓存；漏收时依靠 60 秒 TTL 兜底。
- [x] **布隆过滤器**：用 Redis 8 原生的 `bf:article`。
  - 文章创建时加入 ID；应用启动时如果过滤器不存在，就按全部文章 ID 重建。
  - 过滤器判定为"一定不存在"的 ID 直接返回 404。
  - 已删除的文章靠空值缓存兜底。
- [x] **组件级测试**：
  - 两个 `TwoLevelCache` 实例之间的失效广播。
  - 并发请求同一个 key，loader 只执行一次。
  - 布隆过滤器拦截不存在的 ID。
  - 过滤器丢失后，重启时会重建。
- [x] **HTTP 矩阵**：相关测试在 `two-level` 档下通过。

## Comments

- **组件**（framework 的 `cache` 包）：
  - `TwoLevelCaches.create(name, type)` 创建只用 Redis 的缓存，`createTwoLevel(name, type, bloomFilter)` 创建两级缓存。两级缓存只在 two-level 档下有本地缓存，其他档与 `create` 相同。只有 `article:detail` 是两级缓存；`article:brief`、`user:brief` 在 two-level 档下仍只用 Redis。
  - `get` 的读取顺序：本地缓存 → 布隆过滤器 → Redis → 加载函数。同 key 合并加载靠 Caffeine 的 `get(key, mappingFunction)`，只在单个实例内生效，同一时刻落到数据库的查询数最多等于实例数。空值不放进本地缓存，已删除的文章每次经布隆过滤器和 Redis 的空值缓存返回。`getAll` 经 Caffeine 的批量加载先读本地缓存，不经过布隆过滤器。
  - 本地缓存返回的是同一个对象，调用方不能修改。
  - `evict` 的顺序：删除 Redis → 清除本实例的本地缓存 → 在 `cache:invalidate` 上发布 Redis key。先删 Redis 再清本地，本实例不会从 Redis 读回旧值。有事务时整体推迟到 afterCommit。
  - 订阅由 `CacheInvalidationConfig` 只在 two-level 档装配的 `RedisMessageListenerContainer` 完成，`TwoLevelCaches` 按 key 前缀把消息分发给同名的本地缓存。本实例发出的广播也会收到，重复清除无害。
- **布隆过滤器**（`BloomFilter`，key 为 `bf:<name>`）：
  - 用 Lua 调 `BF.RESERVE`、`BF.MADD`、`BF.EXISTS`，误判率 0.001，预计容量 100 万，超出后 Redis 自动扩容。
  - 过滤器不存在时：`add` 什么也不做，查询一律放行，直到下次启动时重建。`add` 不自动建出只含新 ID 的过滤器，否则重启时会误以为已存在而跳过重建。
  - `rebuildIfAbsent` 先 `BF.RESERVE`，已存在就跳过，多个实例同时启动时只有一个会导入。导入期间过滤器已存在但不完整，其他实例会把尚未导入的文章判定为不存在并返回 404。只在 Redis 数据丢失后的启动期间出现。
  - 各档都维护过滤器（创建时加入、启动时重建），只在 two-level 档读取。只在这一档维护的话，从别的档切过来时过滤器里会缺少期间创建的文章。
- **接入**：
  - `ArticleCacheConfig` 注册 `articleBloomFilter`（`bf:article`），文章详情缓存改用 `createTwoLevel`。
  - `ArticleServiceImpl.create` 在写库的事务里加入 ID，事务回滚只会留下一个误判。
  - `ArticleBloomFilterLoader`（`SmartInitializingSingleton`）启动时按 `ArticleService.listIdsAfter` 每批 1000 个 ID 重建，包括草稿，不包括已删除的文章。
- **测试**：
  - `TwoLevelCacheTest`（`@TwoLevelCacheMode`）：用同名的两个 `TwoLevelCache` 代表两个实例，它们只经 Pub/Sub 互相通知。另外覆盖并发加载只执行一次、布隆过滤器拦截、过滤器被删除后再次 `rebuildIfAbsent` 会重建。各用例的缓存名和过滤器名都用 UUID 保证唯一。
  - `@TwoLevelCacheMode` 的矩阵子类与 redis 档相同：`ArticleTwoLevelCacheApiTest`、`ArticleListTwoLevelCacheApiTest`、`CommentTwoLevelCacheApiTest`、`LikeAndFavoriteTwoLevelCacheApiTest`、`FollowTwoLevelCacheApiTest`、`FeedTwoLevelCacheApiTest`、`NotificationTwoLevelCacheApiTest`、`SearchTwoLevelCacheApiTest`。文章创建后能读到详情，依赖创建时加入了过滤器。
  - 所有档共用一个 Redis，`bf:article` 由最先启动的上下文重建，之后各档创建的文章都会加入。two-level 档的上下文会收到其他 two-level 上下文的失效广播，只会多清一次本地缓存。
