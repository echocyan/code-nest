# 04: 文章列表

**What to build:** 读者可以浏览最新发布的文章，按分类或标签筛选，页码分页；可以查看某位作者已发布的文章，游标分页；作者可以查看自己的草稿列表，游标分页。

**Blocked by:** 03

Status: closed

- [x] **`GET /articles?categoryId=&tagId=&page=&size=`**：匿名可访问，只返回已发布的文章，按 `published_at` 倒序，返回 `PageResult`。
- [x] **`GET /users/{id}/articles?cursor=&size=`**：匿名可访问，以 articleId 作为游标，返回 `CursorResult`。
- [x] **`GET /users/me/drafts?cursor=&size=`**：需要登录，只返回自己的草稿。
- [x] **分页参数**：`size` 默认 20，最大 50；超出范围时返回 400。
- [x] **列表项内容**：文章摘要、作者简要信息、计数。
- [x] **查询索引**：确认使用了 IDX(author_id, status, published_at) 和 IDX(category_id, status, published_at)。按标签筛选时经由 article_tag 的反向索引。
- [x] **`ArticleApi.listByAuthors(authorIds, cursor, limit)`**：供 Feed 的 pull 基线使用，本票一并实现，并用测试覆盖。
- [x] **HTTP 测试**：覆盖筛选、分页边界、草稿不出现在公开列表中、已删除的文章不出现。

## Comments

- **排序**：
  - 最新文章按 `published_at` 倒序，同一秒发布的再按 ID 倒序。
  - 作者文章、我的草稿、`listByAuthors` 以文章 ID 作游标，所以按文章 ID 倒序。雪花 ID 约等于创建时间，与 Feed 用 articleId 作 score 的做法一致；代价是很早建好、很晚才发布的草稿会排在靠后的位置。经用户确认保持这一做法，规格中的用户故事 25 已改为按文章 ID 倒序。
- **`listByAuthors` 的测试**：查询写在 `ArticleService.listPublishedByAuthors`，门面 `ArticleApi.listByAuthors` 只负责把结果转成 `ArticleBrief`。`GET /users/{id}/articles` 复用同一个查询（只传一个作者），所以 HTTP 测试覆盖的是这个查询，没有覆盖门面里的转换。按"各模块的 `XxxApi` 不单独测试"的约定，门面不单独测试。
- **对象转换**：列表项和详情都用 `ArticleConverter` 的多源映射（MapStruct）组装。审查时发现 03 号票的详情原来是手工拼的，一并改掉了。
- **查询索引**：用 2 万篇文章做了 EXPLAIN。
  - 按分类筛选：走 `idx_category_status_published`，倒序扫描索引，没有 filesort。
  - 按标签筛选：先走 `article_tag` 的 `idx_tag_article` 取出文章 ID，再按主键回表，最后 filesort（标签下的文章数有限）。
  - 作者文章、草稿、`listByAuthors`：走 `idx_author_status_published`，因为按 ID 排序需要 filesort。单个作者的文章不多，可以接受；这也是 Feed pull 基线的一部分，留给压测对比。
  - 发现一处缺口：不带筛选的最新文章（首页）原来全表扫描加 filesort。已用 `V2_003` 补上 `idx_status_published (status, published_at)`，补上之后倒序扫描索引，没有 filesort。领域数据模型票已同步。
- **接口细节**：
  - `page` 从 1 开始；超过末页返回空列表，`total` 照常返回。
  - `size` 为 1–50；`page`、`size` 超出范围或不是数字都返回 400（90400）。
  - `/users/{id}/articles` 对不存在的用户返回空列表，不返回 404，因为用户不存在的错误码属于 user 模块。
  - 列表项包含 id、标题、摘要、封面、分类、状态、发布时间、作者简要信息和四项计数，不含正文和标签。
  - 按标签筛选用 `inSql` 拼接子查询。tagId 是 Long 类型，不会注入。
