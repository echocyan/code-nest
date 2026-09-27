# 10: 关注 Feed（推拉结合）

**What to build:** 已登录用户可以读取关注 Feed：按时间倒序、游标翻页，看到自己关注的作者发布的文章。关注了几百人的重度用户读 Feed 依然很快，大 V 发文也不会引发写扩散风暴：普通作者发文时推送到粉丝的收件箱，大 V 的文章在读取时拉取；冷用户回来时重建收件箱；关注、取关、删文后 Feed 表现正确。详见决策票 07。

**Blocked by:** 05, 09

Status: closed

- [x] **`GET /feed?cursor=&size=`**：以 articleId 作为游标，返回 `CursorResult`，列表项含文章摘要、作者信息和计数。没有关注任何人时返回空列表。
- [x] **生产端事件**：article 在发布或删除时发出 `article.published` 和 `article.deleted`；social 发出 `follow.created` 和 `follow.deleted`。
- [x] **Redis 结构**：每个作者一个发件箱（上限 100 条）、每个读者一个收件箱（上限 500 条，TTL 7 天）。member 和 score 都是 articleId。
- [x] **推送**：消费者 `social.feed-push` 先写作者的发件箱。作者不是大 V 时，按每页 1000 个粉丝，给收件箱仍存在的粉丝执行 pipeline `ZADD` 并裁剪到上限。
- [x] **大 V 阈值**：配置项 `feed.big-author-threshold`，默认 5000，粉丝数读自 social 维护的 ZSet `feed:followers`（关注、取关后按 `follow` 表重新统计，启动时缺失就重建）。
- [x] **读取**：
  1. 通过 follow 表的覆盖索引查出关注的作者。
  2. 用一条 `ZRANGEBYSCORE feed:followers` 识别出其中的大 V。
  3. 读取收件箱；不存在就用普通作者的发件箱重建，然后续期。
  4. 拉取各大 V 的发件箱。
  5. 合并、去重、按游标截取。
  6. 过滤已删除、非发布状态和已取关作者的文章，再补全作者信息和计数。
- [x] **修正**：关注普通作者时，把对方的发件箱合并进我的收件箱；取关时按对方发件箱从我的收件箱里 `ZREM`；删文时从发件箱里移除；作者降为普通作者时，把他的发件箱并入粉丝的收件箱。
- [x] **发件箱启动重建**：重建完成标记 `feed:outbox:ready` 不存在时，按全部已发布文章重建各作者的发件箱。
- [x] **HTTP 测试**：
  - 关注后能看到对方的文章，取关后看不到；草稿和删除的文章不出现。
  - 多页游标连续翻页时，不重复、不遗漏。
  - 大 V 与普通作者的文章混排、收件箱过期后重建、关注后立即能看到对方的历史文章、发件箱和粉丝数丢失后重启能恢复。

## Comments

- **模块依赖**：social 的 pom 加上 article，文章摘要经 `ArticleApi` 获取。
- **结构**：
  - `service/FeedStore`：推拉结合的 Redis 存储，对外只有 `addArticle`、`removeArticle`、`follow`、`unfollow`、`read` 和启动重建。`read` 返回一页按 ID 倒序、去重的文章 ID 和游标，不过滤。key 前缀（应用里为 `feed`）与大 V 阈值是构造参数。内部由同包、不对外的 `FeedBoxes`（发件箱、收件箱的 Redis 读写，写操作都是 Lua 脚本）和 `BigAuthors`（维护 `feed:followers` 并据此识别大 V）组成。
  - `FeedService` 查出关注的全部作者（只读 (follower_id, author_id) 唯一索引），交给 `FeedStore.read` 取一页文章 ID，经 `ArticleApi.getBriefs` 回查后滤掉已删除、非发布状态和已取关作者的文章，再经 `UserApi`、`CounterApi` 补全作者信息和计数。没有关注任何人时直接返回空。
  - `listener/FeedPushListener`（`social.feed-push`，订阅 `article.published`）、`listener/FeedFixListener`（`social.feed-fix`，订阅 `follow.created`、`follow.deleted`、`article.deleted`），只把事件转给 `FeedStore`。
  - 推送按粉丝 ID 升序翻页（`FollowService.listFollowerIds`，走 (author_id, follower_id) 索引）。
- **接口细节**：`GET /feed?cursor=&size=` 需要登录；size 默认 20、最大 50；列表项为 `{id, title, summary, coverUrl, publishedAt, author, counts}`，与文章列表项相比不含分类和状态。
- **事件**：`ArticlePublishedEvent`、`ArticleDeletedEvent` 在 article 的 `api/event`，`FollowDeletedEvent` 在 social 的 `api/event`，都只带两个 ID。发布只在草稿转为已发布时发出；删除草稿也会发出 `article.deleted`。
- **大 V 识别**：`BigAuthors` 在 ZSet `feed:followers` 里存作者 ID → 粉丝数，关注、取关后按 follow 表重新统计，用脚本写入并取回旧值；读 Feed 时一条 `ZRANGEBYSCORE` 取出全部大 V，不必逐个读取关注的几百个作者的粉丝数。存粉丝数而不是大 V 名单，阈值不同的实例可以共用。
- **大 V 身份变化**：升为大 V 时已推送的文章留在收件箱，读取时去重。这次写入让作者降为普通作者时，按粉丝 ID 分页，用 pipeline 把他的发件箱并入每个粉丝已存在的收件箱（与关注时并入同一个脚本），否则他当大 V 期间的文章既不在收件箱、也不再被拉取；收件箱不存在的粉丝读取时从发件箱重建，已包含这些文章。
- **启动重建**：`FeedStore` 实现 `SmartInitializingSingleton`，启动时：`feed:outbox:ready` 不存在时经 `ArticleApi.listPublishedStates` 按文章 ID 正序遍历已发布文章，pipeline 写入各作者的发件箱；`feed:followers:ready` 不存在时按关注表分批统计粉丝数。两者都在全部导入完成后才写标记，中途失败下次启动重来。造数直接写库、不经事件，派生的发件箱和粉丝数都由这条路径生成。
- **Redis 细节**：
  - 只在收件箱存在时写入的脚本（推送、关注时并入）不会造出没有 TTL 的收件箱；并入用 `ZADD`，保留原有 TTL。
  - 重建把普通作者的发件箱逐个并入、每并入一个就裁剪一次，最后设 TTL；发件箱都为空时收件箱仍不存在，下次读取再重建。
  - 续期用 `EXPIRE` 的返回值判断收件箱是否存在。
  - score 精度：见决策票 07。`readBefore` 在同一个 pipeline 里对每个 ZSet 取"score 等于游标的一组"和"score 小于游标的前 limit 条"，再在 Java 里过滤出小于游标的 ID。
- **读取**：两路合并后多取一条判断是否还有下一页，`nextCursor` 取过滤前这一页的最后一个 ID，所以过滤不影响翻页。
- **推送与重建不会漏文章**：推送先写发件箱、再逐个判断收件箱是否存在；重建是一个原子脚本。重建在写发件箱之前执行时，收件箱已存在、推送会写入；在之后执行时，重建本身就带上了这篇文章。
- **已知缺陷**：发布与删除在两个队列里消费，删除先被处理时，已删的文章会被写回发件箱、占一个名额，直到被更新的文章挤出；读取时会被过滤掉。
- **测试**：
  - `FeedApiTest` 经 HTTP 覆盖关注、取关、草稿与删除过滤、多页翻阅、首次读取后发布的文章经推送出现、关注后能看到对方的历史文章；Feed 最终一致，翻页断言用 `eventually`。
  - `FeedStoreTest` 另建 `FeedStore`，用随机 key 前缀、大 V 阈值 2，关注关系与文章经桩提供。覆盖大 V 发文不遍历粉丝、读取时拉取，没有收件箱的读者首次读取时重建，关注并入、取关移除、删文移出发件箱，大 V 降为普通作者后当大 V 期间的文章仍在 Feed 里，启动重建发件箱与粉丝数（第一次中途失败、第二次补全），score 相同的 ID 翻页不重不漏。
