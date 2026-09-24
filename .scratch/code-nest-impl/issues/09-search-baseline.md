# 09: 搜索（mysql-like 基线）

**What to build:** 访客可以按关键词搜索已发布的文章，支持按分类、标签筛选，支持按相关度或最新发布排序，页码分页，最多翻到第 50 页。本票实现 `search.mode=mysql-like` 基线（`LIKE '%kw%'`），并搭好 search 模块和统一的搜索接口。

**Blocked by:** 03

Status: open

- [ ] **search 模块**：新增模块，定义 6xxxx 错误码；不建表。
- [ ] **`GET /search/articles?q=&categoryId=&tagId=&sort=RELEVANCE|LATEST&page=&size=`**：匿名可访问，返回 `PageResult`。
  - 结果项含文章摘要和作者昵称（通过 `UserApi` 获取）。
  - `q` 为空时返回 400；`from + size > 1000` 时返回 400 和明确的错误码。
- [ ] **mysql-like 实现**：通过 `ArticleApi` 新增的查询能力完成，search 不直接查 article 的表。
  - 匹配标题、摘要、正文。
  - RELEVANCE 在基线里退化为按发布时间排序，这一点要写明。
  - 基线不做高亮，相应字段返回 null。
- [ ] **模式开关**：定义 `search.mode` 和搜索接口，为 15 票的 ES 实现预留位置。
- [ ] **HTTP 测试**：覆盖匹配、筛选、页码上限、只返回已发布文章。
