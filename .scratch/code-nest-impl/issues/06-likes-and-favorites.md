# 06: 点赞与收藏

**What to build:** 读者可以点赞、取消点赞、收藏、取消收藏已发布的文章，所有操作都幂等。可以按收藏时间倒序、游标翻阅自己的收藏，也可以批量查询一组文章是否已点赞、已收藏。文章的点赞数、收藏数和作者的获赞数随之变化。

**Blocked by:** 03

Status: closed

- [x] **interaction 模块**：新增模块，建 article_like、favorite 表（`V3_`），定义 3xxxx 错误码。
- [x] **接口**：`PUT` 和 `DELETE` `/articles/{id}/like`，`PUT` 和 `DELETE` `/articles/{id}/favorite`。
  - 重复操作返回 200，不产生任何变化。
  - 对草稿、已删除或不存在的文章操作时返回 404。
- [x] **计数上报**：只有真的插入或删除了一行，才调用 `CounterApi.increment`。点赞影响文章点赞数和作者获赞数；收藏影响文章收藏数。
- [x] **`GET /users/me/favorites?cursor=`**：按 favorite.id 倒序，列表项含文章摘要；已删除的文章会被过滤掉。
- [x] **`GET /articles/states?ids=`**：返回每篇文章的 `{liked, favorited}`，查询走唯一索引。ids 数量有上限，超出返回 400。
- [x] **HTTP 测试**：覆盖幂等性、计数变化、并发点赞同一篇文章后计数正确。

## Comments

- **错误码**：只定义了 30001（文章不存在，404），草稿、已删除、不存在的文章都返回它。ids 超出上限走通用的 90400。
- **接口细节**：
  - `GET /users/me/favorites?cursor=&size=`：size 默认 20、最大 50；nextCursor 是收藏记录的 ID。列表项为 `{article: ArticleBrief, favoritedAt}`。
  - 已删除的文章在组装时滤掉，所以一页可能不足 size 条；是否翻完以 hasMore 为准。
  - `GET /articles/states?ids=`：需要登录，ids 最多 50 个（与列表最大 size 一致）。返回以文章 ID 为 key 的 `{liked, favorited}`，不存在的文章按两个 false 返回。
  - 取消点赞、取消收藏同样要求文章已发布；文章删除后无法再取消，关系行留在表里，由对账处理计数。
- **幂等与并发**：插入直接 `save`，捕获 `DuplicateKeyException` 视为已存在（MySQL 只回滚这一条语句，事务继续）；删除按影响行数判断。只有插入或删除成功才调用 `CounterApi.increment`，并发重复请求也只计一次。
- **并发取舍**：先查文章状态再写关系行，期间文章被删除仍可能点赞成功；计数由对账修正，可以接受。
- **测试**：断言计数时用 `eventually`（Awaitility）等计数最终生效；`LikeAndFavoriteRedisAsyncApiTest` 在 redis-async 档重跑同一组测试。
