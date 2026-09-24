# 12: 计数 redis-async

**What to build:** `counter.mode=redis-async` 下，大量用户同时点赞同一篇热门文章时，不再在 MySQL 的同一计数行上排队等锁。计数先在 Redis 里原子累加，异步批量落回 MySQL，最终与关系表一致。已有的 HTTP 测试在两档下都通过；在 redis-async 档，断言计数时要等计数最终生效。详见决策票 06。

**Blocked by:** 05, 06, 07, 10

Status: closed

- [x] **`increment` 的 redis-async 实现**：通过 `DomainEventPublisher` 发出计数变更事件，与调用方的业务事务一起走 Outbox；counter 模块自己消费这些事件。
- [x] **Redis 结构**：每个对象一个 Hash（article、user、comment 三类），不设 TTL；待落库集合与 dedup key 按规格设计。
- [x] **消费端 Lua**：原子执行 MISS 判断 → 按 messageId 做 `SET NX EX 86400` 去重 → `HINCRBY`（结果最小为 0）→ `SADD` 加入待落库集合。收到 MISS 时从 MySQL 读出计数，只在 key 仍不存在时回填，然后重跑脚本。
- [x] **浏览量**：在请求内直接执行 `HINCRBY` 并标记为待落库，不走 MQ。
- [x] **落库**：每 5 秒 `SPOP` 取出最多 1000 个 ID，pipeline 读出 Hash，用批量 `INSERT … ON DUPLICATE KEY UPDATE` 写入绝对值。
- [x] **读取**：pipeline 批量 `HMGET`，缺失的对象从 MySQL 批量回填。
- [x] **`reset`**：同时修正 Redis 和 MySQL。
- [x] **组件级测试**：
  - 落库后，数值等于关系表的 `COUNT(*)`。
  - 重复消息不会重复计数。
  - 清空 Redis 后，懒加载恢复的值正确。
- [x] **HTTP 矩阵**：03、05、06、07 相关的 HTTP 测试在两档下都通过。

## Comments

- **代码位置**：全部在 counter 模块的 `service.impl` 包，事件是 `counter.event.CounterChangedEvent`（路由键 `counter.changed`，消费队列 `counter.update`）。
  - `RedisAsyncCounterService`：`CounterApi` 的 redis-async 实现。
  - `RedisCounterStore`：Lua 脚本、懒加载回填、定时落库。
  - `CounterChangedListener`：消费计数变更事件。
  - `CounterTables`：计数表读写，两种实现共用。
- **Redis 结构**：
  - Hash 字段名是去掉对象类型前缀的指标名，如 `like`、`like_received`；落库列名为字段名加 `_count`。
  - Hash 要么不存在，要么含该类型的全部字段；读取时有字段为空就当作不存在。
- **Lua 细节**：
  - 去重先 `EXISTS` 判断，累加并 `SADD` 之后才 `SET … EX 86400`，脚本中途出错时不会留下去重标记。
  - MISS 后回填再重跑一次，仍然 MISS 就抛异常，交给 MQ 重试。
  - 浏览量走同一个脚本，不带去重 key。
- **读取**：缺失的对象从 MySQL 批量读出，用 pipeline 逐个执行"仅在 key 不存在时写入"的回填脚本；本次返回从 MySQL 读出的值。
- **落库**：
  - 每 5 秒一轮，依次处理三类对象。每类反复 `SPOP` 1000 个，直到取出的不足 1000 个。
  - 批量写入用 `INSERT … VALUES … AS new ON DUPLICATE KEY UPDATE col = new.col`，每行是对象 ID 加各列的值，列顺序与 `CounterTables.metrics` 一致。
  - Hash 已不存在的对象跳过；写库失败时把这批 ID 放回待落库集合。
  - 已知缺陷：多个实例时，一个批次读出旧值后，同一对象再次变更并被另一实例先写入新值，前者随后会用旧值覆盖。与崩溃丢失标记一样，由下次变更或对账修正。
- **reset**：先在 Hash 存在时改 Redis 并标记待落库，再改 MySQL；Hash 不存在时只改 MySQL，下次访问回填。
- **测试**：
  - HTTP 矩阵：`ArticleRedisAsyncApiTest`、`CommentRedisAsyncApiTest`、`LikeAndFavoriteRedisAsyncApiTest`、`FollowRedisAsyncApiTest` 继承原测试类，标注 `@RedisAsyncCounter`（即 `counter.mode=redis-async`），在同一 JVM 的第二个 Spring 上下文里重跑。断言计数统一用 `IntegrationTest.eventually`。
  - 用户主页的计数由上述测试覆盖，`UserProfileApiTest` 不断言计数，不进矩阵。
  - Spring 会暂停不活跃的上下文、停掉它的 Redis 连接。Sa-Token 的存储层是静态的，由测试用的 `SaTokenDaoRebinding` 在上下文恢复时指回当前上下文。
  - 组件级测试在 `RedisAsyncCounterTest`：重复消息经默认交换机直接投递到 `counter.update`；另外覆盖了 reset。
