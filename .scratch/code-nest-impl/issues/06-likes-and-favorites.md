# 06: 点赞与收藏

**What to build:** 读者可以点赞、取消点赞、收藏、取消收藏已发布的文章，所有操作都幂等。可以按收藏时间倒序、游标翻阅自己的收藏，也可以批量查询一组文章是否已点赞、已收藏。文章的点赞数、收藏数和作者的获赞数随之变化。

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] **interaction 模块**：新增模块，建 article_like、favorite 表（`V3_`），定义 3xxxx 错误码。
- [ ] **接口**：`PUT` 和 `DELETE` `/articles/{id}/like`，`PUT` 和 `DELETE` `/articles/{id}/favorite`。
  - 重复操作返回 200，不产生任何变化。
  - 对草稿、已删除或不存在的文章操作时返回 404。
- [ ] **计数上报**：只有真的插入或删除了一行，才调用 `CounterApi.increment`。点赞影响文章点赞数和作者获赞数；收藏影响文章收藏数。
- [ ] **`GET /users/me/favorites?cursor=`**：按 favorite.id 倒序，列表项含文章摘要；已删除的文章会被过滤掉。
- [ ] **`GET /articles/states?ids=`**：返回每篇文章的 `{liked, favorited}`，查询走唯一索引。ids 数量有上限，超出返回 400。
- [ ] **HTTP 测试**：覆盖幂等性、计数变化、并发点赞同一篇文章后计数正确。
