# Sa-Token 用法调研

Type: research
Status: resolved
Blocked by:

## Question

Sa-Token 1.46（`sa-token-spring-boot4-starter`）在 Spring Boot 4 下怎么用：登录认证、token 风格与有效期配置、会话（SaSession / Token-Session）模型、权限与角色校验（注解 + 路由拦截）、集成 Redis 持久化与序列化、全局异常处理、以及在集成测试中如何模拟登录态？一手资料是官方文档的 Markdown 源码：https://github.com/dromara/Sa-Token/tree/dev/sa-token-doc 与 https://github.com/dromara/Sa-Token/tree/dev/sa-token-doc-new （如 `api/sa-session.md`、`up/basic-auth.md`、`docs/use/login-auth.md`）。

## Research

完整结论（逐条附来源链接）见 [docs/research/sa-token-usage.md](../../../docs/research/sa-token-usage.md)（已从 `research/sa-token-usage` 合入 main）。

## Answer

以 v1.46.0 tag 为准：
- **Redis 持久化**：用 `sa-token-redis-template:1.46.0` + `commons-pool2`，它同时支持 Boot 2/3/4，没有 Boot 4 专属版。不要引 `sa-token-redis-jackson`，它会带进 Jackson 2。连接配置复用 `spring.data.redis`；1.46 要求 Redis ≥ 6.0。
- **序列化**：Sa-Token 用自建的 `StringRedisTemplate`，对象由 Jackson 3 插件转成 JSON，不受项目 RedisTemplate 序列化配置影响。往 Session 里存的业务类型必须注册 JSON 白名单（实现 `SaJsonType`，或调 `registerAllowType`，或写 `sa-json-type.list`）。
- **注解鉴权**：必须注册 `SaInterceptor` 才生效。三类异常（`NotLoginException` / `NotRoleException` / `NotPermissionException`）用 `@RestControllerAdvice` 处理；Filter 里抛的异常要用 `setError` 处理。
- **测试**：MockMvc 要挂上 `SaTokenContextFilterForJakartaServlet`，先登录拿 token，之后请求带 header。非 Web 线程用 `SaTokenContextMockUtil`。
- **1.46 行为变化**：默认禁止 loginId 含冒号；`StpInterface.isDisabled` 新增 `loginType` 参数。
- **兼容性**：1.46.0 基于 Boot 4.0.3 构建。它与 Boot 4.1.1 的兼容性、`sa-token-spring-aop` 能否解析、`@AutoConfigureMockMvc` 能否自动挂上 Filter，都不专门验证，按用户决定在实现中暴露问题再处理。
