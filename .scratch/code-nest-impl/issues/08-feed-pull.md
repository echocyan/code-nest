# 08: Feed（pull 基线）

**What to build:** 已登录用户可以读取关注 Feed：按时间倒序、游标翻页，看到自己关注的作者发布的文章。本票实现 `feed.mode=pull` 基线：读取时查出关注列表，再调用 `ArticleApi.listByAuthors` 做 IN 查询。

**Blocked by:** 04, 07

Status: closed

- [x] **`GET /feed?cursor=&size=`**：以 articleId 作为游标，返回 `CursorResult`，列表项含文章摘要、作者信息和计数。
- [x] **关注列表**：通过 follow 表的覆盖索引查出；pull 实现调用 `listByAuthors`。
- [x] **可见性**：只包含已发布、未删除的文章；没有关注任何人时返回空列表。
- [x] **模式开关**：定义 `feed.mode` 配置项和 Feed 读取接口，装配 pull 实现，为 14 票的 push-pull 实现预留位置。
- [x] **HTTP 测试**：
  - 关注后能看到对方的文章，取关后看不到；删除的文章不出现。
  - 多页游标连续翻页时，不重复、不遗漏。

## Comments

- **模块依赖**：social 的 pom 加上 article，Feed 只经 `ArticleApi.listByAuthors` 取文章。
- **结构**：
  - `FeedService` 查出关注的全部作者（只读 (follower_id, author_id) 唯一索引），交给 `FeedReader` 取一页文章，再经 `UserApi`、`CounterApi` 补全作者信息和计数。没有关注任何人时直接返回空，不调用 `FeedReader`。
  - `FeedReader` 由 `feed.mode` 选择实现，参数带上关注的作者 ID，供 14 票的 push-pull 实现复用；`PullFeedReader` 对应 `pull`，多查一条判断是否还有下一页。
- **接口细节**：`GET /feed?cursor=&size=` 需要登录；size 默认 20、最大 50；列表项为 `{id, title, summary, coverUrl, publishedAt, author, counts}`，与文章列表项相比不含分类和状态。
- **测试**：`FeedApiTest` 覆盖关注、取关、草稿与删除过滤和多页翻阅；`FeedRedisAsyncApiTest` 在 redis-async 计数档重跑。
