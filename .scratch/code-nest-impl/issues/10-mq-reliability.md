# 10: MQ 可靠性底座

**What to build:** framework 提供可靠的跨模块事件投递：业务代码只调用 `DomainEventPublisher.publish(event)`，事件就会以"至少一次"的语义送达；消费端加上 `@IdempotentConsumer` 就能保证幂等；失败的消息重试 3 次后进入死信队列。详见决策票 05。

**Blocked by:** 01

Status: closed

- [x] **拓扑**：topic 交换机 `codenest.events`，死信交换机 `codenest.dlx`；每个消费队列都有对应的 `<queue>.dlq`。全部持久化，队列用 classic 类型。提供一种声明新消费队列的简便方式。
- [x] **表结构**：`mq_outbox`、`mq_consume_record`（`V0_`）。
- [x] **`@DomainEvent("<module>.<event>")`**：事件类用它声明路由键。消息的 messageId 放在 AMQP `message_id` 属性，事件类型放在 `type` 属性，body 是事件的 JSON。
- [x] **`publish` 的两条路径**：
  - 有活跃事务时，写入 Outbox，在 afterCommit 中发送，收到 publisher confirm 后标记为 SENT；事务回滚则不发送。
  - 没有事务时，直接发送，靠 confirm 加重试保证送达。
  - 在 Javadoc 里写明这个隐式行为。
- [x] **补发与清理**：
  - 定时任务用 `SELECT … FOR UPDATE SKIP LOCKED` 扫描到期的 PENDING 记录，按指数退避补发。
  - 累计失败 10 次标记为 FAILED，并打告警日志。
  - SENT 记录保留 7 天后清理。
- [x] **`@IdempotentConsumer`**：在同一个事务里插入消费记录并执行业务；唯一键冲突时跳过。
- [x] **重试与死信**：本地重试 3 次（1s、2s、4s），之后进入 DLQ，并打告警日志。
- [x] **组件级集成测试**：
  - 有事务和无事务两条发送路径。
  - 事务回滚后不发送。
  - broker 不可用期间写入的事件，在恢复后由补发任务送达。
  - 同一 messageId 只处理一次。
  - 重试耗尽后进入 DLQ。

## Comments

- **代码位置**：全部在 `code-nest-framework` 的 `framework.mq` 包；迁移为 `V0_001__create_mq_tables.sql`。
- **声明消费队列**：在消费模块里注册 `@Bean Declarables xxxQueue() { return EventQueues.declare("<queue>", "<routingKey>", ...); }`，一并声明 `<queue>.dlq` 及其在 `codenest.dlx`（direct 交换机）上的绑定。
- **消息格式**：`type` 属性是事件类的全限定名。消费端的 MessageConverter 按它反序列化，只接受标注了 `@DomainEvent` 的类，所以一个队列可以用多个 `@RabbitHandler` 按事件类型分派。
- **发送细节**：
  - 有事务时，Outbox 记录的首次补发时间是写入后 10 秒，给 afterCommit 发送留出时间；confirm 回调切到应用线程池再标记 SENT。
  - 补发每 5 秒扫一次，每批 100 条；退避从 10 秒开始翻倍，上限 30 分钟。
  - 没有事务时同步等待 confirm（5 秒），共尝试 3 次，仍失败则抛 `AmqpException`。
  - 没有队列绑定的路由键，broker 照样 ack，消息被丢弃。
- **幂等**：`@IdempotentConsumer` 的 consumer 取消费队列名；messageId 与队列名来自监听器执行前记下的当前消息，所以注解只能用在 `@RabbitListener` 方法上，别处调用直接报错。
- **重试**：用 Boot 的 `spring.rabbitmq.listener.simple.retry` 配置（`application.yaml`），重试耗尽由 `MqConfig` 的 MessageRecoverer 打 ERROR 日志并拒绝消息。
- **测试**：`framework/mq` 下两个测试类，测试用的事件、队列和监听器在 `support/probe/MqProbe`。broker 不可用用 `rabbitmqctl stop_app` / `start_app` 模拟，容器端口不变。
