# 通知模块

Type: grilling
Status: open
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
