# 消息可靠性底座

Type: grilling
Status: resolved
Blocked by: 03

## Question

项目内所有 RabbitMQ 消息共用的可靠性约定是什么：生产端用 publisher confirm 还是 Outbox 本地消息表（或两者组合）、消费端幂等如何实现、重试与死信队列策略、交换机/队列/路由键命名、消息体格式？这是计数系统与搜索同步的共同底座。

## Answer

1. **适用范围**：模块之间的异步副作用一律走 RabbitMQ，例如文章发布后同步搜索、推送 Feed、更新计数、生成通知。Spring `ApplicationEvent` 只在模块内部使用。点赞、收藏走哪条写入路径，由计数系统票决定。
2. **生产端（Outbox + publisher confirm）**：
   - 业务数据和一条 `mq_outbox` 记录在同一个本地事务里写入。
   - 事务提交后在 `afterCommit` 中立即发送；收到 broker 的 confirm 后，把记录标记为 SENT。
   - 定时任务每隔几秒扫描到期的 PENDING 记录并补发。查询用 `SELECT … FOR UPDATE SKIP LOCKED`，多实例之间不会重复处理同一行。
   - 补发失败按指数退避，重算 `next_retry_at`。失败累计 10 次后标记为 FAILED 并告警，之后由人工处理。
   - SENT 记录保留 7 天后清理。
   - 投递语义为"至少一次"，重复消息交给消费端幂等处理。
   - `mq_outbox` 属于基础设施表，归 framework 模块。字段：`id`（雪花 ID，兼作 messageId）、`routing_key`、`event_type`、`payload`（JSON）、`status`（PENDING/SENT/FAILED）、`retry_count`、`next_retry_at`、`created_at`、`updated_at`。
3. **发送接口**：只有一个方法 `DomainEventPublisher.publish(event)`。
   - 当前有活跃事务时写入 Outbox，事务提交后发送；没有事务时直接发送，并用 confirm 加重试保证送达。
   - 这是隐式行为：必须在 Javadoc 里写明，并用测试覆盖两条路径。
   - 事件类用 `@DomainEvent("<module>.<event>")` 声明路由键。
4. **消费端幂等**：
   - 会写 MySQL 的消费者，在 `@RabbitListener` 方法上加 `@IdempotentConsumer`。
   - 它在同一个本地事务里执行两件事：插入 `mq_consume_record(message_id, consumer)`（这两列建唯一索引），以及执行业务方法。
   - 唯一键冲突说明消息已处理过，直接跳过。
   - 本身就幂等的消费者（ES 按 ID 覆盖写、Redis `ZADD` 等）不加这个注解。
   - 不用 Redis `SETNX` 做幂等。
5. **重试与死信**：
   - 用 Spring AMQP 做本地重试，共 3 次，间隔 1s、2s、4s。
   - 重试耗尽后转入死信交换机 `codenest.dlx`，路由到本队列对应的 `<queue>.dlq`。
   - 死信只保存并打告警日志，不自动再消费，由人工排查后重放。
6. **命名与拓扑**：
   - 只用一个 topic 交换机 `codenest.events`。
   - 路由键格式为 `<生产模块>.<事件>`，例如 `article.published`、`comment.created`、`follow.created`。
   - 队列名格式为 `<消费模块>.<用途>`，例如 `search.article-sync`、`social.feed-push`。
   - 交换机、队列、消息都持久化；队列用 classic 类型，不用 quorum，因为单节点下 quorum 没有收益。
7. **消息体**：
   - messageId 放在 AMQP 属性 `message_id`，事件类型放在属性 `type`，不另外包一层信封。
   - body 是事件记录类的 JSON。
   - 事件只带 ID 和少量常用字段（如 `articleId`、`authorId`）。消费者需要最新的完整数据时，回查对方的 `XxxApi`。这样即使消息乱序到达，结果也正确。
8. **事件类位置**：对外发布的事件类放在生产模块的 `api/event/` 下，属于模块接口的一部分，已补充到 ADR-0001 的 Consequences 中。模块内部使用的 `ApplicationEvent` 不对外暴露。
