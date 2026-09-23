# 消息可靠性底座

Type: grilling
Status: open
Blocked by: 03

## Question

项目内所有 RabbitMQ 消息共用的可靠性约定是什么：生产端用 publisher confirm 还是 Outbox 本地消息表（或两者组合）、消费端幂等如何实现、重试与死信队列策略、交换机/队列/路由键命名、消息体格式？这是计数系统与搜索同步的共同底座。
