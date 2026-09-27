# 04: 计数系统

**What to build:** 建立 counter 模块。各模块在自己的事务里上报计数变更；大量用户同时点赞同一篇热门文章时，不会在 MySQL 的同一计数行上排队等锁：计数先在 Redis 里原子累加，异步批量落回 MySQL，最终与关系表一致。用户主页补上粉丝数、关注数、文章数、获赞数。详见决策票 06。

**Blocked by:** 02, 03

Status: closed

- [x] **counter 模块**：建 `article_stat`、`user_stat`、`comment_stat` 表（`V7_`）。`CounterApi` 提供 `increment(metric, targetId, delta)`、批量 `get`、`reset`。
- [x] **`increment`**：通过 `DomainEventPublisher` 发出计数变更事件，与调用方的业务事务一起走 Outbox；counter 模块自己消费这些事件。
- [x] **Redis 结构**：每个对象一个 Hash（article、user、comment 三类），不设 TTL；待落库集合与 dedup key 按规格设计。
- [x] **消费端 Lua**：原子执行 MISS 判断 → 按 messageId 做 `SET NX EX 86400` 去重 → `HINCRBY`（结果最小为 0）→ `SADD` 加入待落库集合。收到 MISS 时从 MySQL 读出计数，只在 key 仍不存在时回填，然后重跑脚本。
- [x] **浏览量**：在请求内直接执行 `HINCRBY` 并标记为待落库，不走 MQ。
- [x] **落库**：每 5 秒 `SPOP` 取出最多 1000 个 ID，pipeline 读出 Hash，用批量 `INSERT … ON DUPLICATE KEY UPDATE` 写入绝对值。
- [x] **读取**：pipeline 批量 `HMGET`，缺失的对象从 MySQL 批量回填。
- [x] **`reset`**：同时修正 Redis 和 MySQL。
- [x] **用户主页**：`GET /users/{id}` 返回粉丝数、关注数、文章数、获赞数；user 模块依赖 counter。
- [x] **组件级测试**：重复消息不会重复计数；`reset` 同时修正 Redis 和 MySQL。

## Comments

- **错误码**：counter 没有需要返回给客户端的错误，不定义 7xxxx 错误码。
- **代码位置**：全部在 counter 模块的 `service.impl` 包，事件是 `counter.event.CounterChangedEvent`（路由键 `counter.changed`，消费队列 `counter.update`）。
  - `RedisAsyncCounterService`：`CounterApi` 的实现。
  - `RedisCounterStore`：Lua 脚本、懒加载回填、定时落库。
  - `CounterChangedListener`：消费计数变更事件。
  - `CounterTables`：计数表读写。
- **Redis 结构**：
  - Hash 字段名是去掉对象类型前缀的指标名，如 `like`、`like_received`；落库列名为字段名加 `_count`。
  - 新增指标后，已有的 Hash 里没有新字段。字段缺失与 Hash 不存在同样处理：累加前返回 MISS，读取和落库前从 MySQL 回填，回填只写入缺失的字段（`HSETNX`），已有的值不变。
- **Lua 细节**：
  - 去重先 `EXISTS` 判断，累加并 `SADD` 之后才 `SET … EX 86400`，脚本中途出错时不会留下去重标记。
  - 要累加的字段不存在（含 Hash 不存在）就返回 MISS；回填后再重跑一次，仍然 MISS 就抛异常，交给 MQ 重试。
  - 浏览量走同一个脚本，不带去重 key。
- **读取**：`CounterApi.get` 保证每个传入的 ID 都有结果，没有计数的对象各项都是 0。Hash 不存在或缺字段的对象从 MySQL 批量读出，用 pipeline 逐个执行"只写入缺失字段"的回填脚本，再从 Redis 读一次返回。
- **落库**：
  - 每 5 秒一轮，依次处理三类对象。每类反复 `SPOP` 1000 个，直到取出的不足 1000 个。
  - 批量写入用 `INSERT … VALUES … AS new ON DUPLICATE KEY UPDATE col = new.col`，每行是对象 ID 加各列的值，列顺序与 `CounterTables.metrics` 一致。
  - 读取走与 `CounterApi.get` 相同的回填逻辑，Hash 或字段缺失的对象先从 MySQL 补齐再整行写回；写库失败时把这批 ID 放回待落库集合。
  - 已知缺陷：实例在 `SPOP` 之后、写库之前崩溃，这批对象的待落库标记会丢失。多个实例时，一个批次读出旧值后，同一对象再次变更并被另一实例先写入新值，前者随后会用旧值覆盖。两者都由下次变更或对账修正。
- **reset**：先在 Hash 存在时改 Redis 并标记待落库，再改 MySQL；Hash 不存在时只改 MySQL，下次访问回填。
- **用户主页**：`GET /users/me` 也带上四项计数，与 `GET /users/{id}` 返回同一个结构。
- **测试**：
  - 计数异步生效，HTTP 测试断言计数统一用 `IntegrationTest.eventually`。
  - 组件级测试在 `RedisAsyncCounterTest`：重复消息经默认交换机直接投递到 `counter.update`；另外覆盖 reset。落库与懒加载要经点赞产生计数，见 08 号票。
