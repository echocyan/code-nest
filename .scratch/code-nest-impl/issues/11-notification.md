# 11: 通知

**What to build:** 在以下情况下，用户会收到站内通知：文章被点赞、文章被评论、自己被回复、自己被关注。自己触发的操作不产生通知。同一个人反复点赞，或反复关注，只产生一条通知。用户可以游标翻阅通知、查看未读数（上限 99+）、标记单条或全部已读。详见决策票 13。

**Blocked by:** 05, 06, 07, 10

Status: closed

- [x] **生产端事件**：
  - interaction 在真的点赞成功后发出 `like.created`，带 articleId、userId、authorId。
  - article 发出 `comment.created`，带 commentId、articleId、userId、rootId、replyToUserId、articleAuthorId。
  - social 发出 `follow.created`，带 followerId、authorId。
  - 三者都在业务事务内通过 `publish` 发出。
- [x] **notification 模块**：新增模块，建 notification 表（`V5_`），索引按规格建立；定义 5xxxx 错误码。
- [x] **消费者 `notification.create`**：加 `@IdempotentConsumer`，按事件类型确定接收者；触发者与接收者是同一人时跳过。点赞和关注带 dedup_key，写入用 `INSERT IGNORE`。
- [x] **`GET /notifications?cursor=`**：游标分页。
  - 读取时组装触发者信息、文章标题、评论摘要。
  - 内容已被删除时显示"该内容已删除"。
- [x] **`GET /notifications/unread-count`**：最多数到 100，前端显示 99+。
- [x] **标记已读**：`PUT /notifications/{id}/read` 只能操作自己的通知，否则返回 404；`PUT /notifications/read-all`。
- [x] **HTTP 测试**：用 Awaitility 等待异步结果，覆盖四类通知、自己触发不通知、反复点赞只有一条、已读和未读、已删除内容的展示。

## Comments

- **模块依赖**：notification → user、article、interaction、social；引用 interaction、social 只为它们 `api/event/` 下的事件类。
- **事件**：
  - `LikeCreatedEvent`、`CommentCreatedEvent`、`FollowCreatedEvent` 分别放在 interaction、article、social 的 `api/event/`，只在真的插入一行后发出。
  - `comment.created` 的 replyToUserId 是被回复的人：回复时 @ 了谁就是谁，没有 @ 时是被回复评论的作者；评论为 null。消费者按 rootId 是否为 0 区分评论和回复。
- **消费者**：`listener/NotificationListener`，一个 `notification.create` 队列，用三个 `@RabbitHandler` 按事件类型分派，每个都加 `@IdempotentConsumer`。
- **写入**：`INSERT IGNORE` 是 Mapper 上的自定义 SQL，不走自动填充，ID 和时间由 `send` 设置。实体字段叫 `isRead`（`read` 是 MySQL 保留字）。
- **接口细节**：
  - `GET /notifications?cursor=&size=`：size 默认 20、最大 50；列表项为 `{id, type, actor, articleId, articleTitle, commentId, commentSummary, read, createdAt}`，type 为 LIKE、COMMENT、REPLY、FOLLOW。
  - 文章已删除时 articleTitle 和 commentSummary 都显示"该内容已删除"；评论或回复已删除时 commentSummary 显示"该内容已删除"。
  - `CommentBrief.summary` 是内容开头 100 个字（按码点截取）。
  - 未读数用 `SELECT COUNT(*) FROM (… LIMIT 100)`，返回整数。
  - 单条已读对不存在或别人的通知都返回 50001（404）；已读的再标一次返回 200。
- **测试**：Awaitility 最多等 10 秒（101 条评论的用例等 30 秒）。断言"没有通知"时，先制造一条后续通知并等它出现；消费者按顺序处理，前面的消息此时都已处理完。
