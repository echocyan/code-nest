# 09: 搜索（mysql-like 基线）

**What to build:** 访客可以按关键词搜索已发布的文章，支持按分类、标签筛选，支持按相关度或最新发布排序，页码分页，最多翻到第 50 页。本票实现 `search.mode=mysql-like` 基线（`LIKE '%kw%'`），并搭好 search 模块和统一的搜索接口。

**Blocked by:** 03

Status: closed

- [x] **search 模块**：新增模块，定义 6xxxx 错误码；不建表。
- [x] **`GET /search/articles?q=&categoryId=&tagId=&sort=RELEVANCE|LATEST&page=&size=`**：匿名可访问，返回 `PageResult`。
  - 结果项含文章摘要和作者昵称（通过 `UserApi` 获取）。
  - `q` 为空时返回 400；`from + size > 1000` 时返回 400 和明确的错误码。
- [x] **mysql-like 实现**：通过 `ArticleApi` 新增的查询能力完成，search 不直接查 article 的表。
  - 匹配标题、摘要、正文。
  - RELEVANCE 在基线里退化为按发布时间排序，这一点要写明。
  - 基线不做高亮，相应字段返回 null。
- [x] **模式开关**：定义 `search.mode` 和搜索接口，为 15 票的 ES 实现预留位置。
- [x] **HTTP 测试**：覆盖匹配、筛选、页码上限、只返回已发布文章。

## Comments

- **错误码**：只定义了 60001（翻页超出上限，400）。`q` 为空或空白、`sort` 不是 RELEVANCE/LATEST、`page`/`size` 越界都走通用的 90400。
- **接口细节**：
  - `sort` 默认 RELEVANCE；`page` 从 1 开始，`size` 为 1–50、默认 20；`page × size`（即 `from + size`）超过 1000 返回 60001，size 为 20 时最多到第 50 页。
  - 结果项为 `{article: ArticleBrief, author: UserBrief, titleHighlight, contentHighlight}`；作者通过 `UserApi` 批量补全，mysql-like 下两个高亮字段为 null。
  - 关键词去掉首尾空白后匹配；`%`、`_`、`\` 转义后按字面匹配。
- **模式开关**：`search.mode` 选择 `ArticleSearcher` 的实现，当前只有 `mysql-like`（`MysqlLikeArticleSearcher`）。翻页上限校验和作者补全在 `SearchServiceImpl`，两档共用。
- **mysql-like 实现**：`ArticleApi.searchPublished` 在 article 模块内查询：标题、摘要用 `LIKE`，正文经 `id IN (SELECT article_id FROM article_content WHERE content LIKE ?)` 匹配；分类、标签筛选与最新文章列表相同。不分词、无相关度，RELEVANCE 与 LATEST 都按 `published_at` 倒序、同秒按 ID 倒序；每次查询都要扫描正文表，留给压测对比。
