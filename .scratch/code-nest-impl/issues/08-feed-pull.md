# 08: Feed（pull 基线）

**What to build:** 已登录用户可以读取关注 Feed：按时间倒序、游标翻页，看到自己关注的作者发布的文章。本票实现 `feed.mode=pull` 基线：读取时查出关注列表，再调用 `ArticleApi.listByAuthors` 做 IN 查询。

**Blocked by:** 04, 07

Status: open

- [ ] **`GET /feed?cursor=&size=`**：以 articleId 作为游标，返回 `CursorResult`，列表项含文章摘要、作者信息和计数。
- [ ] **关注列表**：通过 follow 表的覆盖索引查出；pull 实现调用 `listByAuthors`。
- [ ] **可见性**：只包含已发布、未删除的文章；没有关注任何人时返回空列表。
- [ ] **模式开关**：定义 `feed.mode` 配置项和 Feed 读取接口，装配 pull 实现，为 14 票的 push-pull 实现预留位置。
- [ ] **HTTP 测试**：
  - 关注后能看到对方的文章，取关后看不到；删除的文章不出现。
  - 多页游标连续翻页时，不重复、不遗漏。
