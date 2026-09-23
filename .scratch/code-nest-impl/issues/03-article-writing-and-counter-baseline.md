# 03: 文章写作与详情（含计数基线）

**What to build:** 作者可以新建草稿、编辑（带版本号，版本不一致时返回冲突）、发布、删除自己的文章。任何人可以查看已发布文章的详情，包括正文、作者简要信息和四项计数；每次查看浏览量 +1；草稿只有作者本人能看到。同时建立 counter 模块和它的 `sync-db` 实现，用户主页补上四项计数。

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] **counter 模块**：
  - 建 `article_stat`、`user_stat`、`comment_stat` 表（`V7_`），定义 7xxxx 错误码。
  - `CounterApi` 提供 `increment(metric, targetId, delta)`、批量 `get`、`reset`，用 `counter.mode=sync-db` 装配。
  - `sync-db` 实现在调用方的事务里直接执行 `UPDATE … + delta`，没有计数行时插入；计数最小为 0；读取时查不到的对象按全 0 返回。
- [ ] **article 表结构**：article、article_content、article_tag 三张表，`version` 字段用 `@Version`，文章和评论用 `@TableLogic` 软删除。
- [ ] **`POST /articles`**：新建草稿，包含标题、正文、摘要、封面 URL、分类、至多 5 个标签。分类或标签不存在、标签超过 5 个时返回 400。不填摘要时截取正文前 N 个字。
- [ ] **`PUT /articles/{id}`**：带 version 做乐观锁，冲突时返回 409；只有作者本人能编辑，其他人返回 403。
- [ ] **`POST /articles/{id}/publish`**：设置 `published_at`，状态变为 PUBLISHED；作者文章数 +1。
- [ ] **`DELETE /articles/{id}`**：软删除；只有作者本人能删；删除已发布的文章时，作者文章数 -1。
- [ ] **`GET /articles/{id}`**：匿名可访问。
  - 草稿对作者以外的人（包括匿名访客）返回 404。
  - 返回作者简要信息（通过 `UserApi` 获取）和四项计数（通过 `CounterApi` 获取）。
  - 每次查看，浏览量 +1。
- [ ] **用户主页**：`GET /users/{id}` 补上粉丝数、关注数、文章数、获赞数。
- [ ] **`ArticleApi` 首批能力**：判断文章是否存在、查询文章状态与作者、批量查询文章摘要。
- [ ] **HTTP 测试**：覆盖上述全部行为，包括 403、404、409。
