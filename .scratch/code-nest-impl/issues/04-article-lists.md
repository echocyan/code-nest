# 04: 文章列表

**What to build:** 读者可以浏览最新发布的文章，按分类或标签筛选，页码分页；可以查看某位作者已发布的文章，游标分页；作者可以查看自己的草稿列表，游标分页。

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] **`GET /articles?categoryId=&tagId=&page=&size=`**：匿名可访问，只返回已发布的文章，按 `published_at` 倒序，返回 `PageResult`。
- [ ] **`GET /users/{id}/articles?cursor=&size=`**：匿名可访问，以 articleId 作为游标，返回 `CursorResult`。
- [ ] **`GET /users/me/drafts?cursor=&size=`**：需要登录，只返回自己的草稿。
- [ ] **分页参数**：`size` 默认 20，最大 50；超出范围时返回 400。
- [ ] **列表项内容**：文章摘要、作者简要信息、计数。
- [ ] **查询索引**：确认使用了 IDX(author_id, status, published_at) 和 IDX(category_id, status, published_at)。按标签筛选时经由 article_tag 的反向索引。
- [ ] **`ArticleApi.listByAuthors(authorIds, cursor, limit)`**：供 Feed 的 pull 基线使用，本票一并实现，并用测试覆盖。
- [ ] **HTTP 测试**：覆盖筛选、分页边界、草稿不出现在公开列表中、已删除的文章不出现。
