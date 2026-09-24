# 20: 限流

**What to build:** 注册、登录、发文、评论和回复、点赞和收藏、关注和取关、搜索这些接口，都按用户或 IP 限流。超限时返回 429 和 `Retry-After`。Redis 故障时放行请求。限流有总开关。详见决策票 11。

**Blocked by:** 05, 06, 07, 09

Status: open

- [ ] **滑动窗口 Lua**：用 ZSet 实现，依次执行 `ZREMRANGEBYSCORE`、`ZCARD`、`ZADD`、`PEXPIRE`，时间取自 Redis `TIME`。为窗口边界写纯逻辑测试或脚本级测试。
- [ ] **`@RateLimit(key, limit, window, dimension)`**：可以重复标注，由注册在 `SaInterceptor` 之后的 `HandlerInterceptor` 执行。
- [ ] **限流维度**：需要登录的接口按用户 ID。匿名接口按 IP：默认取 `remoteAddr`，只有请求来自配置中的可信代理时才读取 `X-Forwarded-For`。
- [ ] **限额**：按规格标注到各接口，全部可配置。
- [ ] **超限响应**：HTTP 429，错误码 90429，`Retry-After` 根据窗口中最早一条记录的时间计算。
- [ ] **降级与开关**：Redis 不可用时放行请求，并打告警日志。`rate-limit.enabled` 为总开关。
- [ ] **HTTP 测试**：
  - 超限后返回 429，并带 `Retry-After`。
  - 不同用户、不同 IP 互不影响。
  - 请求不来自可信代理时，伪造的 XFF 不起作用。
  - 关闭开关后不再限流。
  - 其他测试默认关闭限流，避免互相干扰。
