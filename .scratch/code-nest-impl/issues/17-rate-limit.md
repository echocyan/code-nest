# 17: 限流

**What to build:** 注册、登录、发文、评论和回复、点赞和收藏、关注和取关、搜索这些接口，都按用户或 IP 限流。超限时返回 429 和 `Retry-After`。Redis 故障时放行请求。限流有总开关。详见决策票 11。

**Blocked by:** 07, 08, 09, 11

Status: closed

- [x] **滑动窗口 Lua**：用 ZSet 实现，依次执行 `ZREMRANGEBYSCORE`、`ZCARD`、`ZADD`、`PEXPIRE`，时间取自 Redis `TIME`。为窗口边界写纯逻辑测试或脚本级测试。
- [x] **`@RateLimit("<规则名>")`**：可以重复标注，由注册在 `SaInterceptor` 之后的 `HandlerInterceptor` 执行。规则写在配置里，共用额度的接口引用同一个规则名。
- [x] **限流维度**：需要登录的接口按用户 ID。匿名接口按 IP：默认取 `remoteAddr`，只有请求来自配置中的可信代理时才读取 `X-Forwarded-For`。
- [x] **限额**：按规格标注到各接口，全部可配置。
- [x] **超限响应**：HTTP 429，错误码 90429，`Retry-After` 根据窗口中最早一条记录的时间计算。
- [x] **降级与开关**：Redis 不可用时放行请求，并打告警日志。`rate-limit.enabled` 为总开关。
- [x] **HTTP 测试**：
  - 超限后返回 429，并带 `Retry-After`。
  - 不同用户、不同 IP 互不影响。
  - 请求不来自可信代理时，伪造的 XFF 不起作用。
  - 关闭开关后不再限流。
  - 其他测试默认关闭限流，避免互相干扰。

## Comments

- **组成**（`framework/ratelimit`）：`@RateLimit`（容器注解 `RateLimits`）、`RateLimitRules`、`RateLimitInterceptor`、`SlidingWindowRateLimiter`、`RateLimitedException`；`RateLimitConfig` 只在 `rate-limit.enabled=true` 时注册拦截器，order 1，排在 `SaInterceptor`（order 0）之后。
- **规则解析与校验**：`RateLimitConfig` 注册拦截器时按 Bean 类型找出全部 `@Controller`（不创建 Bean），交给 `RateLimitRules` 按注解上的规则名从 `rate-limit.rules` 取出规则。规则名未配置或 limit、window、dimension 缺项、按用户计数的规则用在 `@SaIgnore` 的方法或类上，都抛出 `IllegalStateException`，消息里列出规则名和方法，应用启动失败。拦截器按方法查解析好的规则。
- **脚本**：一条 Lua 同时处理一次请求的全部额度（key 为 `rate-limit:{额度名}:{user|ip}:{值}`）：先清理并计数，全部有余量才给每个额度 `ZADD` 和 `PEXPIRE`，被拒绝的请求不占任何额度。等待时间取各超限额度中最晚腾出名额的那个；调低限额后窗口内可能多于限额，按第 `count - limit` 条记录计算。`Retry-After` 为等待毫秒数向上取整到秒。
- **限额**：规则写在 `rate-limit.rules.<规则名>.{limit,window,dimension}`，测试可以只覆盖其中的 `limit`。发文限的是发布接口（`POST /articles/{id}/publish`），新建草稿不限。评论与回复、点赞与收藏（含取消）、关注与取关各共用同名额度。
- **IP**：`rate-limit.trusted-proxies` 是精确匹配的 IP 列表，默认为空。对端是可信代理时，从右往左取 `X-Forwarded-For` 中第一个不在列表里的地址。
- **降级**：`tryAcquire` 抛出 `DataAccessException` 时放行，打 WARN 日志。未写测试：共用的 Redis 容器不能停。
- **测试**：
  - `IntegrationTest` 默认 `rate-limit.enabled=false`；测试客户端关闭了 HttpClient 的自动重试，否则它会按 `Retry-After` 自动重试 429。
  - `@RateLimitEnabled` 把本机设为可信代理并调低限额，`RateLimitApiTest` 每个测试用随机的 XFF 当客户端 IP；`SlidingWindowRateLimiterTest` 共用这个上下文，直接调用限流器测窗口边界。
  - `RateLimitUntrustedProxyApiTest` 单独一个上下文，不配可信代理，验证伪造的 XFF 不起作用；`RateLimitDisabledApiTest` 在默认上下文里验证关闭开关后不限流。
  - `RateLimitRulesTest` 是普通 JUnit，用一组标注了 `@RateLimit` 的方法覆盖按规则名取出配置、共用规则名的接口得到同一条规则、规则名未配置时报错、按用户计数的规则用在匿名接口上时报错。
  - 同一个 JVM 里因此有多个 Spring 上下文。Spring 会暂停不活跃的上下文、停掉它的 Redis 连接；Sa-Token 的存储层是静态的，由测试用的 `SaTokenDaoRebinding` 在上下文恢复时指回当前上下文。
