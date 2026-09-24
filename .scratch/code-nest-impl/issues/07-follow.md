# 07: 关注与粉丝/关注列表

**What to build:** 用户可以关注、取关其他用户，两个操作都幂等，不能关注自己。任何人可以游标翻阅某个用户的粉丝列表和关注列表；已登录用户可以批量查询自己是否关注了一组用户。双方的粉丝数、关注数随之变化。

**Blocked by:** 03

Status: closed

- [x] **social 模块**：新增模块，建 follow 表（`V4_`），唯一键 (follower_id, author_id)，另建索引 (author_id, follower_id)、(author_id, id)、(follower_id, id)；定义 4xxxx 错误码。
- [x] **`PUT`/`DELETE` `/users/{id}/follow`**：幂等；关注自己返回 400；目标用户不存在返回 404。只有真的插入或删除了一行，才更新被关注者的粉丝数和关注者的关注数。
- [x] **`GET /users/{id}/followers?cursor=`、`GET /users/{id}/followings?cursor=`**：匿名可访问，列表项含用户简要信息。
- [x] **`GET /users/follow-states?ids=`**：批量返回是否已关注。
- [x] **HTTP 测试**：覆盖幂等性、计数变化、两种列表的分页。

## Comments

- **错误码**：40001 不能关注自己（400），40002 用户不存在（404）；关注和取关都先做这两项检查。ids 超出上限走通用的 90400。
- **接口细节**：
  - 两种列表按关注时间倒序，nextCursor 是关注记录的 ID，size 默认 20、最大 50；列表项为 `{user: UserBrief, followedAt}`。
  - 粉丝列表走 (author_id, id)，关注列表走 (follower_id, id)；(author_id, follower_id) 留给 Feed 推送按粉丝分页。
  - 用户不存在时两种列表返回空，与作者文章列表一致。
  - `GET /users/follow-states?ids=`：需要登录，ids 最多 50 个（与 `/articles/states` 一致）。返回以用户 ID 为 key 的布尔值。
- **幂等与并发**：插入直接 `save`，捕获 `DuplicateKeyException` 视为已关注；删除按影响行数判断。只有插入或删除成功才调用 `CounterApi.increment`（USER_FOLLOWER 记在被关注者，USER_FOLLOWING 记在关注者）。
- **模块依赖**：social 目前只依赖 user、counter；article 在 Feed 票用到时再加。
- **测试**：断言计数时用 `eventually`（Awaitility）等计数最终生效；`FollowRedisAsyncApiTest` 在 redis-async 档重跑同一组测试。
