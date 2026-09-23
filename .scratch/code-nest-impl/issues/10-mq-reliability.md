# 10: MQ 可靠性底座

**What to build:** framework 提供可靠的跨模块事件投递：业务代码只调用 `DomainEventPublisher.publish(event)`，事件就会以"至少一次"的语义送达；消费端加上 `@IdempotentConsumer` 就能保证幂等；失败的消息重试 3 次后进入死信队列。详见决策票 05。

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] **拓扑**：topic 交换机 `codenest.events`，死信交换机 `codenest.dlx`；每个消费队列都有对应的 `<queue>.dlq`。全部持久化，队列用 classic 类型。提供一种声明新消费队列的简便方式。
- [ ] **表结构**：`mq_outbox`、`mq_consume_record`（`V0_`）。
- [ ] **`@DomainEvent("<module>.<event>")`**：事件类用它声明路由键。消息的 messageId 放在 AMQP `message_id` 属性，事件类型放在 `type` 属性，body 是事件的 JSON。
- [ ] **`publish` 的两条路径**：
  - 有活跃事务时，写入 Outbox，在 afterCommit 中发送，收到 publisher confirm 后标记为 SENT；事务回滚则不发送。
  - 没有事务时，直接发送，靠 confirm 加重试保证送达。
  - 在 Javadoc 里写明这个隐式行为。
- [ ] **补发与清理**：
  - 定时任务用 `SELECT … FOR UPDATE SKIP LOCKED` 扫描到期的 PENDING 记录，按指数退避补发。
  - 累计失败 10 次标记为 FAILED，并打告警日志。
  - SENT 记录保留 7 天后清理。
- [ ] **`@IdempotentConsumer`**：在同一个事务里插入消费记录并执行业务；唯一键冲突时跳过。
- [ ] **重试与死信**：本地重试 3 次（1s、2s、4s），之后进入 DLQ，并打告警日志。
- [ ] **组件级集成测试**：
  - 有事务和无事务两条发送路径。
  - 事务回滚后不发送。
  - broker 不可用期间写入的事件，在恢复后由补发任务送达。
  - 同一 messageId 只处理一次。
  - 重试耗尽后进入 DLQ。
