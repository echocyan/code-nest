# 13: 计数对账

**What to build:** 运维者可以手动触发计数对账（`POST /actuator/counter-reconcile`），系统也每周自动执行一次。各模块从自己的关系表或内容表重新统计精确计数，通过 `CounterApi.reset` 修正 Redis 和 MySQL。浏览量不参与对账。

**Blocked by:** 12

Status: open

- [ ] **各模块的对账范围**：
  - interaction：点赞数、收藏数、获赞数。
  - article：评论数、回复数、文章数。
  - social：粉丝数、关注数。
- [ ] **对账方式**：分页执行 `GROUP BY` 重新统计，再调用 `reset`。另外要处理"计数表里有值、但关系表里已经没有对应行"的情况，把这类计数修正为 0。
- [ ] **触发方式**：自定义 Actuator 端点加每周定时任务。多实例部署时同一时刻只有一个实例在对账。
- [ ] **文档**：写明对账与并发写入之间的 ±1 误差。
- [ ] **测试**：人为篡改计数后触发对账，数值恢复正确。在 `sync-db` 和 `redis-async` 两档下都要通过。loadtest 的造数也会依赖这个端点。
