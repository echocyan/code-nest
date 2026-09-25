# 17: 缓存 redis 档

**What to build:** `cache.mode=redis` 下，文章详情、用户简要信息、文章摘要由 Redis 缓存，热门文章的详情接口不再每次都查数据库。文章编辑或删除后，缓存可靠失效，读者不会长期看到旧内容。不存在的文章 ID 不会反复打到数据库。本票同时建立 `TwoLevelCache` 组件的 Redis 一级实现。详见决策票 09。

**Blocked by:** 03, 10

Status: closed

- [x] **`TwoLevelCache`**：只有三个方法：`get(key, loader)`、`getAll(keys, batchLoader)`（批量读取走 `MGET`）、`evict(key)`。
  - Redis TTL 为 30 分钟，加 0–5 分钟随机抖动。
  - 空值缓存 60 秒。
- [x] **接入范围**：
  - article 的文章详情缓存元数据和正文，不含计数，计数仍然单独组装。
  - `UserApi` 和 `ArticleApi` 的批量摘要查询走缓存。
- [x] **缓存失效**：
  - 编辑、发布、删除，以及用户修改资料后，在 afterCommit 中 `evict`。
  - 新增消费者 `article.cache-evict`，订阅 `article.updated` 和 `article.deleted` 做第二次删除；这两个事件如果不存在就补上。
- [x] **模式开关**：定义 `cache.mode`，本票实现 `none` 和 `redis` 两档。
- [x] **HTTP 矩阵**：03、04 相关的测试在两档下都通过。另外断言：编辑后详情立即是新内容；修改昵称后摘要里的昵称更新；不存在的 ID 重复访问都返回 404。
- [x] **文档**：写明 Cache-Aside 残留的竞态窗口。

## Comments

- **组件**（framework 的 `cache` 包）：
  - `TwoLevelCache<V>` 以 ID（`long`）为 key，缓存值以 JSON 存入 Redis，key 为 `cache:<name>:<id>`。`get`、`getAll` 的加载函数对不存在的对象返回 null 或不放进结果；`getAll` 对未命中的 ID 只调用一次 batchLoader，用 pipeline 回填。
  - 空值缓存存 JSON `null`，TTL 60 秒；正常值 TTL 为 30 分钟加 0–5 分钟随机抖动。
  - `evict` 有活跃事务时推迟到 afterCommit 执行，删除失败只记日志；没有事务时立即删除，失败直接抛出。写方只需在写库后调用，不用自己注册事务回调。
  - 缓存由 `TwoLevelCaches.create(name, type)` 创建，各模块把它注册成 Bean：`article:detail`、`article:brief`（`ArticleCacheConfig`），`user:brief`（`UserCacheConfig`）。
  - `cache.mode` 绑定到枚举 `CacheMode`，取值不合法时启动失败。`none` 档下 `get`、`getAll` 直接调用加载函数，`evict` 不做任何事。Redis 读写失败不降级，异常直接抛出。
- **接入**：
  - 文章详情缓存 `CachedArticleDetail`：文章实体、正文、分类、标签。作者经 `UserApi`、计数经 `CounterApi` 每次另行组装。已删除的文章与不存在的 ID 一样缓存为空值。
  - `ArticleApi.getBriefs`、`UserApi.getBriefs` 走 `getAll`。`findState`、`listByAuthors`、各列表查询不走缓存。
  - 编辑、发布、删除都调用 `ArticleService.evictCache`，同时删除详情和摘要；`UserServiceImpl.updateProfile` 删除用户摘要。
  - 消费者 `ArticleCacheEvictListener` 消费 `article.cache-evict`（绑定 `article.updated`、`article.deleted`），再调用一次 `evictCache`。发布不做第二次删除。事件经 Outbox 一定会投递，但消费重试耗尽后进入死信队列，这时第二次删除不会执行。删除本身幂等，所以不加 `@IdempotentConsumer`。
  - 消费者两档都装配，`none` 档下什么也不做；只在 redis 档装配的话，`none` 档部署会让消息在队列里一直积压。测试里两档的上下文共用 RabbitMQ，redis 档的消息可能被 `none` 档取走，第二次删除因此跳过。测试只依赖 afterCommit 中的第一次删除，不受影响。
- **Cache-Aside 残留的竞态**：读请求未命中，从数据库读到旧值后停顿；这期间写请求更新数据库并删除缓存；读请求随后把旧值回填。旧值最多保留到 TTL 过期，即 30–35 分钟。只有读库比"写库加删缓存"还慢时才会出现，窗口极小。经 MQ 的第二次删除能覆盖回填发生在它之前的情况。发布没有第二次删除，所以残留时间以 TTL 为上限。这段说明也写在 `TwoLevelCache` 的类注释里。
- **测试**：
  - `UserProfileApiTest` 不读缓存（`getProfile` 直接查库），`CounterReconcileApiTest` 只断言计数（不在缓存里），所以都不进矩阵。
  - `@RedisCache`（`cache.mode=redis`）的矩阵子类：`ArticleRedisCacheApiTest`、`ArticleListRedisCacheApiTest`、`CommentRedisCacheApiTest`、`LikeAndFavoriteRedisCacheApiTest`、`FollowRedisCacheApiTest`、`FeedRedisCacheApiTest`、`NotificationRedisCacheApiTest`、`SearchRedisCacheApiTest`。这些类都经过用户摘要或文章摘要的查询。
  - 编辑、发布、删除相关的测试先读一次详情，把它写进缓存，再断言写操作后详情立即更新。另外断言改昵称后详情里的作者昵称更新、编辑后收藏列表里的标题更新、不存在的 ID 连续访问都返回 404。
  - `ArticleListApiTest` 在两档下各跑一遍，同一标签下会有另一档写入的文章。因此只断言按时间倒序的开头几篇，总数以测试前读到的值为基准。
