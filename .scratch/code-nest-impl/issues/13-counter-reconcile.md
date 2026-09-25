# 13: 计数对账

**What to build:** 运维者可以手动触发计数对账（`POST /actuator/counter-reconcile`），系统也每周自动执行一次。各模块从自己的关系表或内容表重新统计精确计数，通过 `CounterApi.reset` 修正 Redis 和 MySQL。浏览量不参与对账。

**Blocked by:** 12

Status: closed

- [x] **各模块的对账范围**：
  - interaction：点赞数、收藏数、获赞数。
  - article：评论数、回复数、文章数。
  - social：粉丝数、关注数。
- [x] **对账方式**：分页执行 `GROUP BY` 重新统计，再调用 `reset`。另外要处理"计数表里有值、但关系表里已经没有对应行"的情况，把这类计数修正为 0。
- [x] **触发方式**：自定义 Actuator 端点加每周定时任务。多实例部署时同一时刻只有一个实例在对账。
- [x] **文档**：写明对账与并发写入之间的 ±1 误差。
- [x] **测试**：人为篡改计数后触发对账，数值恢复正确。在 `sync-db` 和 `redis-async` 两档下都要通过。loadtest 的造数也会依赖这个端点。

## Comments

- **结构**：
  - 各模块实现 `counter.api.CounterSource`：声明自己负责的指标，按 `countAfter(metric, afterId, limit)` 返回一页 `GROUP BY` 结果（计数大于 0 的对象，按 ID 升序）。实现者是持有对应表的 ServiceImpl：`ArticleLikeServiceImpl`（点赞数、获赞数）、`FavoriteServiceImpl`、`CommentServiceImpl`（评论数、回复数）、`ArticleServiceImpl`（文章数）、`FollowServiceImpl`。
  - counter 模块的 `CounterReconcileServiceImpl` 逐个指标驱动翻页，与 `CounterApi.get` 读到的当前值、MySQL 计数表里的值分别比较，任一不一致就调用 `CounterApi.reset`。redis-async 档下待落库标记丢失（Redis 对、MySQL 旧）也能修正。同一指标注册了两个来源时启动失败。
- **统计口径**（与增量上报一致）：
  - 点赞数、收藏数：关系行数，文章删除后的关系行照样计入。
  - 获赞数：`article_like.author_id` 分组计数。点赞时记下文章作者，获赞数不用跨模块查文章，已删除文章上的点赞同样计入。已有的点赞行由迁移脚本一次性回填。
  - 评论数：该文章未删除的评论与回复总数；回复数：该评论下未删除的回复数（评论删除后回复仍计入文章评论数）。
  - 文章数：未删除的已发布文章数。
  - 粉丝数、关注数：`follow` 按 `author_id`、`follower_id` 分组计数。
- **计数表有值、关系表无行**（以 MySQL 计数表为准）：一页结果覆盖 ID 区间 (afterId, 本页最后一个 ID]，最后一页覆盖到 `Long.MAX_VALUE`。同一区间内计数表该列不为 0、但本页里没有的对象，精确计数按 0 处理。
- **索引**：`article_like(article_id)`、`article_like(author_id)`、`favorite(article_id)`、`comment(root_id)` 供分页 `GROUP BY` 使用；`follow`、`article`、`comment(article_id, …)` 用已有索引。
- **触发**：
  - `POST /actuator/counter-reconcile` 同步执行，返回各指标被修正的对象数；其他实例正在对账时返回 409。
  - Actuator 只在管理端口（`management.server.port`，默认 8081）上提供，不经过 Sa-Token 鉴权，不能对外暴露。
  - 定时任务每周一 04:00 执行。
  - 互斥用 Redis 锁 `counter:reconcile:lock`（`SET NX EX`，1 小时过期，按 token 释放），拿不到锁的实例直接跳过。
- **已知缺陷**（写在 `CounterReconcileServiceImpl` 的 Javadoc）：
  - 统计与修正之间的并发写入可能留下 ±1 误差，由下一次对账修正。
  - redis-async 档：在途的计数事件在修正之后到达，会在修正值上再累加一次。
  - redis-async 档：Redis 里不为 0、MySQL 里仍为 0、关系表里又没有对应行的对象不在修正范围内。
- **测试**：`CounterReconcileApiTest` 篡改全部 8 项指标（含关系表里没有对应行的对象），经管理端口触发对账后从 HTTP 接口断言恢复；`CounterReconcileRedisAsyncApiTest` 在 redis-async 档重跑。
