# 计数系统（点赞/收藏/粉丝数）

Type: grilling
Status: resolved
Blocked by: 03, 05

## Question

（背景：计数由独立的 `code-nest-counter` 模块负责，通过 `CounterApi` 对外提供；计数表和计数字段见[领域与数据模型](03-domain-data-model.md)。评论不能点赞。）

点赞/收藏/粉丝数等高频计数如何设计：Redis 中的数据结构（谁点过赞的关系 + 计数值）、写入路径（先 Redis 后异步批量落库？）、如何保证每用户对同一对象至多一次、Redis 与 MySQL 的最终一致与对账、Redis 数据丢失后的重建？优化前（直写 MySQL）与优化后如何对比？

## Answer

**瓶颈判断**：点赞关系表每次插入的是不同的行，不会互相争锁。真正的瓶颈在热点行：热门文章的所有点赞都要执行 `UPDATE article_stat SET like_count = like_count + 1`，全部排队等同一行的行锁。

1. **点赞关系**：同步写 MySQL `article_like`，唯一索引保证每个用户对同一篇文章至多点赞一次，"是否点赞"始终准确。批量查询"当前用户是否点赞"直接查唯一索引，要不要加缓存由多级缓存票决定。
2. **计数上报**：
   - 业务模块在自己的事务里调用 `CounterApi.increment(metric, targetId, delta)`。例如点赞时调用两次：文章点赞数 +1，作者获赞数 +1。
   - `increment` 内部通过 `DomainEventPublisher` 发出计数变更事件，经 Outbox 投递给 counter 模块自己的消费者。因此 counter 不依赖任何业务模块，符合 ADR-0001。
   - `CounterApi` 只有三个方法：`increment`、`get`（批量）、`reset`（对账修正）。
   - 计数事件与业务事件分开发送，例如 `like.created` 由通知模块消费。
3. **精确计数与近似计数**：
   - 精确计数（点赞、收藏、评论、回复、粉丝、关注、文章、获赞）都能从关系表或内容表重新算出，所以走 Outbox，消费端做幂等，并定期对账。
   - 浏览量是近似计数，没有可对账的真实数据来源，丢几次也可以接受。它在请求内直接执行 `HINCRBY` 并标记为待落库，不走 MQ，也不对账。
4. **两种实现**：`counter.mode` 切换 `CounterApi` 的两个实现，用于压测对比。
   - `sync-db`（基线）：在调用方的事务里直接执行 `UPDATE … + delta`，读取也直接读 MySQL。
   - `redis-async`（优化）：见下面第 5–9 条。
   - 压测场景：大量用户同时点赞同一篇热门文章，比较两种实现的 QPS 和 P99。
5. **Redis 结构**：
   - 每个对象一个 Hash：
     - `counter:article:{id}`：like、favorite、comment、view
     - `counter:user:{id}`：follower、following、article、like_received
     - `counter:comment:{id}`：reply
   - Hash 不设 TTL，100 万篇文章估计约 100MB。
   - 辅助 key：待落库集合 `counter:dirty:{type}`；去重 key `counter:dedup:{messageId}`，过期时间 24 小时。
   - Redis 开启 AOF，刷盘策略 `everysec`。
6. **消费端 Lua（原子执行）**：
   1. 计数 Hash 不存在，返回 `MISS`。
   2. 执行 `SET counter:dedup:{messageId} NX EX 86400`，key 已存在则返回 `DUP`。
   3. `HINCRBY`，结果最小为 0。
   4. `SADD counter:dirty:{type} {id}`。

   收到 `MISS` 时：从 MySQL 读出该对象的计数（没有记录就当作全 0），仅在 key 仍不存在时写入 Redis，然后重跑脚本。

   为什么这样设计：
   - 幂等必须放在 Redis 里、和计数更新一起原子执行，不能用基于 MySQL 的 `@IdempotentConsumer`。
   - key 不存在时直接 `HINCRBY`，会从 0 开始累加，把已有的计数冲掉。
7. **落库**：
   - 每 5 秒一次，对 `counter:dirty:{type}` 执行 `SPOP`，每批最多 1000 个 ID，再用 pipeline 读出这些对象的 Hash。
   - 用批量 `INSERT … ON DUPLICATE KEY UPDATE` 写入**绝对值**。重复写结果不变，所以落库是幂等的；同一对象的多次变更也合并成了一次写入。
   - `SPOP` 是原子操作，多个实例同时落库时不会重复处理同一个对象。
   - 已知缺陷：实例在 `SPOP` 之后、写库之前崩溃，这批对象的待落库标记会丢失。它们在 MySQL 中暂时停在旧值，直到下次变更或对账时修正。
8. **读取**：用 pipeline 批量 `HMGET`。Redis 里没有的对象，从 MySQL 批量读出，按第 6 条"仅在 key 不存在时写入"的规则回填 Redis。
9. **对账与恢复**：
   - 谁掌握真实数据就由谁对账：点赞、收藏、获赞由 interaction 负责；评论、回复、文章数由 article 负责；粉丝、关注由 social 负责。
   - 对账方式：分页执行 `GROUP BY` 重新统计，再调用 `CounterApi.reset(metric, id, value)` 同时修正 Redis 和 MySQL。每周定时执行一次，也可以手动触发，不追踪哪些对象发生过变更。
   - Redis 丢数据时，最多丢失约 5 秒尚未落库的增量，再加上 AOF 每秒刷盘可能丢失的部分。之后访问时从 MySQL 懒加载，精确计数在下次对账时修正。
   - 对账与并发写入可能产生 ±1 的误差，下一次对账会修正，需在文档中说明。
