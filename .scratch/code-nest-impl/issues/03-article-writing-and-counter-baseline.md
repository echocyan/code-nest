# 03: 文章写作与详情（含计数基线）

**What to build:** 作者可以新建草稿、编辑（带版本号，版本不一致时返回冲突）、发布、删除自己的文章。任何人可以查看已发布文章的详情，包括正文、作者简要信息和四项计数；每次查看浏览量 +1；草稿只有作者本人能看到。同时建立 counter 模块和它的 `sync-db` 实现，用户主页补上四项计数。

**Blocked by:** 02

Status: closed

- [x] **counter 模块**：
  - 建 `article_stat`、`user_stat`、`comment_stat` 表（`V7_`）。
  - `CounterApi` 提供 `increment(metric, targetId, delta)`、批量 `get`、`reset`，用 `counter.mode=sync-db` 装配。
  - `sync-db` 实现在调用方的事务里直接执行 `UPDATE … + delta`，没有计数行时插入；计数最小为 0；读取时查不到的对象按全 0 返回。
- [x] **article 表结构**：article、article_content、article_tag 三张表，`version` 字段用 `@Version`，文章用 `@TableLogic` 软删除。
- [x] **`POST /articles`**：新建草稿，包含标题、正文、摘要、封面 URL、分类、至多 5 个标签。分类或标签不存在、标签超过 5 个时返回 400。不填摘要时截取正文前 N 个字。
- [x] **`PUT /articles/{id}`**：带 version 做乐观锁，冲突时返回 409；只有作者本人能编辑，其他人返回 403。
- [x] **`POST /articles/{id}/publish`**：设置 `published_at`，状态变为 PUBLISHED；作者文章数 +1。
- [x] **`DELETE /articles/{id}`**：软删除；只有作者本人能删；删除已发布的文章时，作者文章数 -1。
- [x] **`GET /articles/{id}`**：匿名可访问。
  - 草稿对作者以外的人（包括匿名访客）返回 404。
  - 返回作者简要信息（通过 `UserApi` 获取）和四项计数（通过 `CounterApi` 获取）。
  - 每次查看，浏览量 +1。
- [x] **用户主页**：`GET /users/{id}` 返回粉丝数、关注数、文章数、获赞数；user 模块依赖 counter。
- [x] **门面与当前用户**：
  - user 模块提供 `UserApi`：按 ID 批量查询用户简要信息、判断用户是否存在。
  - framework 的 `AuthContext` 提供 `currentUserIdOrNull()`，供公开接口识别当前用户，例如作者本人查看草稿。
- [x] **`ArticleApi` 首批能力**：判断文章是否存在、查询文章状态与作者、批量查询文章摘要。
- [x] **HTTP 测试**：覆盖上述全部行为，包括 403、404、409。

## Comments

- **错误码**：counter 没有需要返回给客户端的错误，不定义 7xxxx 错误码。
- **接口细节**：
  - 编辑接口是 `PUT /articles/{id}?version=`：version 放在查询参数里，请求体与新建共用 `ArticleRequest`。成功后返回新的版本号，新建和发布也返回版本号。
  - 重复发布一篇已发布的文章，返回 200，不改动任何数据，也不会重复增加文章数。
  - 只有已发布的文章会计入浏览量，作者查看自己的草稿不计。
  - 没填摘要时，截取去掉首尾空白后的正文前 100 个字符。按码点截取，不会切断 emoji。
  - 封面地址与头像一样，必须是 http(s) 地址；标签 ID 重复时会合并。
  - 非作者对文章做写操作返回通用的 403（90403）。版本冲突返回 20002，分类、标签不存在分别返回 20003、20004（均为 HTTP 400）。
  - `GET /users/me` 也带上四项计数，与 `GET /users/{id}` 返回同一个结构。
- **门面**：
  - `ArticleApi` 提供 `findState`（为空表示文章不存在）和 `getBriefs`，没有单独提供 `exists`。
  - 状态枚举 `ArticleStatus` 放在 `api` 包，因为门面对外暴露了它。
- **计数**：
  - `CounterApi.get` 保证每个传入的 ID 都有结果；没有计数行的对象，各项都是 0。
  - sync-db 实现用 `INSERT … ON DUPLICATE KEY UPDATE col = GREATEST(col + delta, 0)`，一条语句同时完成"没有就插入、有就累加、最小为 0"。
- **并发**：删除文章时按读到的版本号删除。期间文章被发布或删除，就返回 409，所以作者文章数总是按真实状态增减。详情由多次查询拼成，并发编辑时可能读到新旧混合的内容；这一点可以接受。
