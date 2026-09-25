# 14: Feed push-pull

**What to build:** `feed.mode=push-pull` 下，关注了几百人的重度用户读 Feed 依然很快，大 V 发文也不会引发写扩散风暴。普通作者发文时推送到粉丝的收件箱，大 V 的文章在读取时拉取；冷用户回来时重建收件箱；关注、取关、删文后 Feed 表现正确。已有的 Feed HTTP 测试在两档下都通过。详见决策票 07。

**Blocked by:** 08, 10

Status: closed

- [x] **生产端事件**：article 在发布或删除时发出 `article.published` 和 `article.deleted`（如果后续票已经加过，就直接复用）；social 发出 `follow.created` 和 `follow.deleted`。
- [x] **Redis 结构**：每个作者一个发件箱（上限 100 条）、每个读者一个收件箱（上限 500 条，TTL 7 天）。member 和 score 都是 articleId。
- [x] **推送**：消费者 `social.feed-push` 先写作者的发件箱。作者不是大 V 时，按每页 1000 个粉丝，给收件箱仍存在的粉丝执行 pipeline `ZADD` 并裁剪到上限。
- [x] **大 V 阈值**：配置项 `feed.big-author-threshold`，默认 5000，粉丝数从 `CounterApi` 读取。
- [x] **读取**：
  1. 用 `CounterApi` 识别出关注列表中的大 V。
  2. 读取收件箱；不存在就用普通作者的发件箱重建，然后续期。
  3. 拉取各大 V 的发件箱。
  4. 合并、去重、按游标截取。
  5. 过滤已删除、非发布状态和已取关作者的文章。
- [x] **修正**：关注普通作者时，把对方的发件箱合并进我的收件箱；取关时按对方发件箱从我的收件箱里 `ZREM`；删文时从发件箱里移除。
- [x] **HTTP 矩阵**：08 票的测试在两档下都通过。另外补充 push-pull 专属场景：大 V 与普通作者的文章混排、收件箱过期后重建、关注后立即能看到对方的历史文章。

## Comments

- **事件**：`ArticlePublishedEvent`、`ArticleDeletedEvent` 在 article 的 `api/event`，`FollowDeletedEvent` 在 social 的 `api/event`，都只带两个 ID。发布只在草稿转为已发布时发出；删除草稿也会发出 `article.deleted`。
- **代码位置**：都在 social 模块。
  - `listener/FeedPushListener`（`social.feed-push`，订阅 `article.published`）、`listener/FeedFixListener`（`social.feed-fix`，订阅 `follow.created`、`follow.deleted`、`article.deleted`），都调用 `FeedFanoutService`。
  - `service/impl/FeedBoxes`：发件箱、收件箱的 Redis 读写，写操作都是 Lua 脚本；`BigAuthors`：按 `CounterApi` 的粉丝数识别大 V；`PushPullFeedReader`：`feed.mode=push-pull` 的 `FeedReader`。
  - 推送按粉丝 ID 升序翻页（`FollowService.listFollowerIds`，走 (author_id, follower_id) 索引）。
- **不按档装配**：两个消费者与 `feed.mode` 无关，两档都维护发件箱和收件箱。pull 档不读收件箱，也就不会创建收件箱，推送时只是跳过所有粉丝。队列不能按档声明：发送开启了 mandatory，路由键没有队列绑定时消息会被退回。
- **Redis 细节**：
  - 只在收件箱存在时写入的脚本（推送、关注时并入）不会造出没有 TTL 的收件箱；并入用 `ZADD`，保留原有 TTL。
  - 重建把普通作者的发件箱逐个并入、每并入一个就裁剪一次，最后设 TTL；发件箱都为空时收件箱仍不存在，下次读取再重建。
  - 续期用 `EXPIRE` 的返回值判断收件箱是否存在。
  - score 精度：见决策票 07。`readBefore` 在同一个 pipeline 里对每个 ZSet 取"score 等于游标的一组"和"score 小于游标的前 limit 条"，再在 Java 里过滤出小于游标的 ID。
- **读取**：两路合并后多取一条判断是否还有下一页，`nextCursor` 取过滤前这一页的最后一个 ID，所以过滤不影响翻页。
- **已知缺陷**：读者重建收件箱时读完发件箱、写入收件箱之前，推送恰好判断收件箱不存在而跳过，这篇文章会缺失到下次重建。作者跨过大 V 阈值前后的文章可能既不在收件箱、也不被拉取（决策票 07 接受不迁移）。
- **测试**：`FeedPushPullApiTest` 继承 `FeedApiTest`，标注 `@PushPullFeed`（`feed.mode=push-pull`，大 V 阈值降为 2），另外覆盖大 V 与普通作者混排、收件箱过期后重建（删掉收件箱 key 模拟过期）、关注后能看到对方的历史文章。push-pull 档的 Feed 最终一致，`FeedApiTest` 的翻页断言改用 `eventually`。
