# 12: 计数 redis-async

**What to build:** `counter.mode=redis-async` 下，大量用户同时点赞同一篇热门文章时，不再在 MySQL 的同一计数行上排队等锁。计数先在 Redis 里原子累加，异步批量落回 MySQL，最终与关系表一致。已有的 HTTP 测试在两档下都通过；在 redis-async 档，断言计数时要等计数最终生效。详见决策票 06。

**Blocked by:** 05, 06, 07, 10

Status: open

- [ ] **`increment` 的 redis-async 实现**：通过 `DomainEventPublisher` 发出计数变更事件，与调用方的业务事务一起走 Outbox；counter 模块自己消费这些事件。
- [ ] **Redis 结构**：每个对象一个 Hash（article、user、comment 三类），不设 TTL；待落库集合与 dedup key 按规格设计。
- [ ] **消费端 Lua**：原子执行 MISS 判断 → 按 messageId 做 `SET NX EX 86400` 去重 → `HINCRBY`（结果最小为 0）→ `SADD` 加入待落库集合。收到 MISS 时从 MySQL 读出计数，只在 key 仍不存在时回填，然后重跑脚本。
- [ ] **浏览量**：在请求内直接执行 `HINCRBY` 并标记为待落库，不走 MQ。
- [ ] **落库**：每 5 秒 `SPOP` 取出最多 1000 个 ID，pipeline 读出 Hash，用批量 `INSERT … ON DUPLICATE KEY UPDATE` 写入绝对值。
- [ ] **读取**：pipeline 批量 `HMGET`，缺失的对象从 MySQL 批量回填。
- [ ] **`reset`**：同时修正 Redis 和 MySQL。
- [ ] **组件级测试**：
  - 落库后，数值等于关系表的 `COUNT(*)`。
  - 重复消息不会重复计数。
  - 清空 Redis 后，懒加载恢复的值正确。
- [ ] **HTTP 矩阵**：03、05、06、07 相关的 HTTP 测试在两档下都通过。
