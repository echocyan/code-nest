# Feed 推拉结合

Type: grilling
Status: resolved
Blocked by: 03

## Question

关注 Feed 如何生成：普通作者发文写扩散到粉丝收件箱、大 V 发文读扩散——收件箱的 Redis 结构与容量上限、大 V 阈值、读取时如何合并推与拉两路并分页（游标）、关注/取关/删文后的修正、冷用户（长期不登录）是否跳过推送？

## Answer

**要解决的问题**：纯拉模式下，关注的人越多，读 Feed 越慢；纯推模式下，大 V 每发一篇文章都要写入所有粉丝的收件箱，形成写扩散风暴。所以普通作者走推模式，大 V 走拉模式。

1. **策略**：
   - 普通作者发文后，文章写进每个粉丝的**收件箱**。
   - 大 V 发文不推送，粉丝读 Feed 时再去拉取。
   - 每个作者都有一个**发件箱**，保存最近 100 篇文章，有三个用途：读 Feed 时拉取大 V 的文章；新关注某人时补齐他的文章；重建冷用户的收件箱。发件箱由 social 模块消费 `article.published` 维护，这样 social 不需要反复调用 `ArticleApi` 查询。
2. **冷用户**：
   - 收件箱 key 的 TTL 为 7 天，用户每次读 Feed 时续期。
   - 推送时只写入收件箱 key 仍然存在的粉丝；key 已过期说明用户 7 天没来过，直接跳过。
   - 用户回来读 Feed 时如果收件箱不存在，就从他关注的所有普通作者的发件箱里取文章，合并出最新 500 条，重建收件箱。
   - 不需要另外维护活跃用户名单。
3. **大 V 阈值**：
   - 配置项 `feed.big-author-threshold`，默认 5000，粉丝数从 `CounterApi` 读取。
   - 作者跨过阈值时不迁移历史数据，之后按新身份处理。同一篇文章可能既在收件箱里又被拉取到，读 Feed 时按 articleId 去重。
4. **结构**：
   - 收件箱 `feed:inbox:{userId}`、发件箱 `feed:outbox:{authorId}`，都是 ZSet。
   - member 是 articleId，**score 也用 articleId**。雪花 ID 本身按时间有序且唯一，分页时不会因为 score 相同而跳过或重复。
   - 收件箱上限 500 条，发件箱上限 100 条，写入后用 `ZREMRANGEBYRANK` 裁掉超出的部分。
   - 分页游标是 articleId（取小于游标的条目）。Feed 最多只能往回翻约 500 条。
5. **读 Feed**（`GET /feed?cursor=&size=`）：
   1. 通过 `follow` 表的覆盖索引，查出我关注的作者 ID。
   2. 用 `CounterApi.get` 批量读取这些作者的粉丝数，据此识别出大 V。
   3. 读取收件箱：不存在就先重建，然后续期，再取游标之前的 size 条。
   4. 对每个大 V 的发件箱，同样取游标之前的 size 条。
   5. 合并第 3、4 步的结果，按 articleId 去重、倒序，取前 size 条；`nextCursor` 设为这一页最后一条的 articleId。
   6. 用 `ArticleApi` 批量获取文章摘要，过滤掉已删除或非发布状态的文章，以及作者已不在关注列表中的文章（取关后的残留）。过滤后一页可能不足 size 条，这是允许的。
   7. 通过 `UserApi` 和 `CounterApi` 补全作者信息和计数。

   关注列表要不要缓存，由多级缓存票根据压测结果决定。[多级缓存与缓存治理](09-multilevel-cache.md)的结论是暂不缓存；如果压测中它成为瓶颈，由压测方案票决定。
6. **推送**：
   - 消费者 `social.feed-push` 订阅 `article.published`，先把文章写入作者的发件箱。
   - 如果作者是大 V，到此结束。
   - 如果作者不是大 V，按 `IDX(author_id, follower_id)` 每页取 1000 个粉丝，用 pipeline 对收件箱 key 存在的粉丝执行 `ZADD` 并裁剪到上限。
   - 普通作者的粉丝数低于阈值，所以不需要拆成子任务。
   - `ZADD` 本身是幂等的，所以不加 `@IdempotentConsumer`。
7. **修正**（原则：写入时尽量修正，读取时兜底过滤）：
   - **关注**：消费 `follow.created`。如果被关注的是普通作者且我的收件箱存在，就把他的发件箱合并进我的收件箱。大 V 不需要处理，读的时候会拉取。
   - **取关**：消费 `follow.deleted`，按对方发件箱里的文章从我的收件箱中尽量 `ZREM`。更早的、不在发件箱里的残留，由读取时过滤。
   - **删文**：消费 `article.deleted`，把文章从作者的发件箱中移除。粉丝收件箱里的不逐个删除，读取时过滤。
   - **编辑**：不需要处理。Feed 只存 ID，文章内容在读取时实时查询。
8. **基线对比**：配置项 `feed.mode` 切换两种实现：
   - `pull`：基线，调用 `ArticleApi.listByAuthors(authorIds, cursor, limit)`，由 article 模块执行 `author_id IN (…) AND status = 1 ORDER BY id DESC LIMIT n`。
   - `push-pull`：优化后的推拉结合。

   压测场景：一个关注了几百个作者的用户读 Feed，比较两种模式的 P99；再观察大 V 发文时的写扩散量。

**这张票对其他模块提出的接口要求**：article 模块发布 `article.published` 和 `article.deleted` 事件，并提供 `ArticleApi.listByAuthors` 和批量查询文章摘要的接口；social 模块发布 `follow.created` 和 `follow.deleted` 事件。[搜索与数据同步](08-search-sync.md)另外要求 article 模块发布 `article.updated` 事件，并提供按 id 游标遍历已发布文章的接口。
