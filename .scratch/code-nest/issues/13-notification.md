# 通知模块

Type: grilling
Status: resolved
Blocked by:

## Question

（背景：依赖[消息可靠性底座](05-mq-reliability.md)与[计数系统](06-counter-system.md)；占位表结构见[领域与数据模型](03-domain-data-model.md)。实时推送不在范围内。）

通知如何产生、存储与展示：
- 由哪些事件产生：点赞、评论、回复、关注。如何避免"给自己发通知"？
- 是否聚合（例如"张三等 5 人赞了你的文章"），聚合放在写入时还是读取时？
- 表结构是否冗余展示字段，还是读取时通过 `UserApi` / `ArticleApi` 组装？
- 未读数怎么维护：Redis 计数还是 `COUNT` 查询？"全部已读"和"单条已读"如何处理？
- 取消点赞后，已经产生的通知是否撤回？
- 列表如何分页？

## Answer

1. **通知来源**：消费者 `notification.create` 订阅以下事件，写入 MySQL，并加 `@IdempotentConsumer` 保证幂等：

   | 事件 | 接收者 |
   |---|---|
   | `like.created` | 文章作者 |
   | `comment.created`（评论） | 文章作者 |
   | `comment.created`（回复） | `reply_to_user_id` |
   | `follow.created` | 被关注者 |

   - 触发者和接收者是同一人时，不发通知。
   - 事件里直接带上所需 ID，消费者不需要回查其他模块：
     - `like.created`：articleId、userId、authorId
     - `comment.created`：commentId、articleId、userId、rootId、replyToUserId、articleAuthorId
     - `follow.created`：followerId、authorId
2. **不聚合，但去重防刷屏**：
   - `dedup_key` 字段可为空，建唯一索引：点赞写 `L:{actorId}:{articleId}`，关注写 `F:{actorId}:{authorId}`，评论和回复留 NULL。
   - 写入用 `INSERT IGNORE`。同一个人反复点赞、取消，或反复关注、取关，都只会产生一条通知。
3. **撤回与已删除内容**：
   - 取消点赞、取关不撤回通知。
   - 表里只存 ID，展示信息在读取时组装：`UserApi` 提供触发者的昵称和头像，`ArticleApi` 提供文章标题和评论摘要。
   - 内容已被删除时，显示"该内容已删除"。
   - 本票新增一项接口要求：`ArticleApi` 要能按 ID 批量查询评论摘要。
4. **未读与已读**：
   - 未读数直接 `COUNT` 查询，走 `IDX(recipient_id, is_read)`，最多数到 100，前端显示"99+"。不用 Redis 计数。
   - 已读有两个显式接口：`PUT /notifications/{id}/read` 标记单条，`PUT /notifications/read-all` 全部标记。打开列表不会自动标记已读。
5. **列表与索引**：
   - 按 id 倒序做游标分页，返回 `CursorResult`；不按类型分 Tab；通知不设保留期。
   - 索引为 `IDX(recipient_id, id)`（列表）和 `IDX(recipient_id, is_read)`（未读数）。不用 `IDX(recipient_id, is_read, id)`：`is_read` 夹在中间，列表按 id 排序时会产生 filesort。
