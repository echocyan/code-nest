# 18: 缓存 two-level 档

**What to build:** `cache.mode=two-level` 下，热门文章详情由各实例的 Caffeine 本地缓存直接返回。一个实例上的文章更新后，其他实例的本地缓存很快失效。并发请求同一个 key 时，每个实例只会加载一次。不存在的 ID 被布隆过滤器直接拦截。

**Blocked by:** 17

Status: open

- [ ] **Caffeine 本地缓存**：`TwoLevelCache` 的 L1，最多 10000 条，写入 60 秒后过期。读取顺序为 L1 → Redis → DB，利用 Caffeine 的同 key 合并加载防击穿。
- [ ] **失效广播**：`evict` 时通过 Redis Pub/Sub 频道 `cache:invalidate` 广播，各实例收到后清除本地缓存；漏收时依靠 60 秒 TTL 兜底。
- [ ] **布隆过滤器**：用 Redis 8 原生的 `bf:article`。
  - 文章创建时加入 ID；应用启动时如果过滤器不存在，就按全部文章 ID 重建。
  - 过滤器判定为"一定不存在"的 ID 直接返回 404。
  - 已删除的文章靠空值缓存兜底。
- [ ] **组件级测试**：
  - 两个 `TwoLevelCache` 实例之间的失效广播。
  - 并发请求同一个 key，loader 只执行一次。
  - 布隆过滤器拦截不存在的 ID。
  - 过滤器丢失后，重启时会重建。
- [ ] **HTTP 矩阵**：相关测试在 `two-level` 档下通过。
