# 07: 关注与粉丝/关注列表

**What to build:** 用户可以关注、取关其他用户，两个操作都幂等，不能关注自己。任何人可以游标翻阅某个用户的粉丝列表和关注列表；已登录用户可以批量查询自己是否关注了一组用户。双方的粉丝数、关注数随之变化。

**Blocked by:** 03

Status: open

- [ ] **social 模块**：新增模块，建 follow 表（`V4_`），唯一键 (follower_id, author_id)，另建索引 IDX(author_id, follower_id)；定义 4xxxx 错误码。
- [ ] **`PUT`/`DELETE` `/users/{id}/follow`**：幂等；关注自己返回 400；目标用户不存在返回 404。只有真的插入或删除了一行，才更新被关注者的粉丝数和关注者的关注数。
- [ ] **`GET /users/{id}/followers?cursor=`、`GET /users/{id}/followings?cursor=`**：匿名可访问，列表项含用户简要信息。
- [ ] **`GET /users/follow-states?ids=`**：批量返回是否已关注。
- [ ] **HTTP 测试**：覆盖幂等性、计数变化、两种列表的分页。
