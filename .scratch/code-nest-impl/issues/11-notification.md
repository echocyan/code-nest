# 11: 通知

**What to build:** 在以下情况下，用户会收到站内通知：文章被点赞、文章被评论、自己被回复、自己被关注。自己触发的操作不产生通知。同一个人反复点赞，或反复关注，只产生一条通知。用户可以游标翻阅通知、查看未读数（上限 99+）、标记单条或全部已读。详见决策票 13。

**Blocked by:** 05, 06, 07, 10

Status: open

- [ ] **生产端事件**：
  - interaction 在真的点赞成功后发出 `like.created`，带 articleId、userId、authorId。
  - article 发出 `comment.created`，带 commentId、articleId、userId、rootId、replyToUserId、articleAuthorId。
  - social 发出 `follow.created`，带 followerId、authorId。
  - 三者都在业务事务内通过 `publish` 发出。
- [ ] **notification 模块**：新增模块，建 notification 表（`V5_`），索引按规格建立；定义 5xxxx 错误码。
- [ ] **消费者 `notification.create`**：加 `@IdempotentConsumer`，按事件类型确定接收者；触发者与接收者是同一人时跳过。点赞和关注带 dedup_key，写入用 `INSERT IGNORE`。
- [ ] **`GET /notifications?cursor=`**：游标分页。
  - 读取时组装触发者信息、文章标题、评论摘要。
  - 内容已被删除时显示"该内容已删除"。
- [ ] **`GET /notifications/unread-count`**：最多数到 100，前端显示 99+。
- [ ] **标记已读**：`PUT /notifications/{id}/read` 只能操作自己的通知，否则返回 404；`PUT /notifications/read-all`。
- [ ] **HTTP 测试**：用 Awaitility 等待异步结果，覆盖四类通知、自己触发不通知、反复点赞只有一条、已读和未读、已删除内容的展示。
