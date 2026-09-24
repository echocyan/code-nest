# 认证与鉴权方案

Type: grilling
Status: resolved
Blocked by: 01, 03

## Question

基于 Sa-Token：注册/登录方式（账号密码？邮箱？）、密码存储、token 风格与有效期、会话存 Redis 的方式、是否区分普通用户与管理员角色、哪些接口允许匿名访问、如何在业务代码与集成测试中取得当前用户？

## Answer

1. **登录方式**：只支持用户名加密码，注册成功后自动登录。
   - 用户名 4–20 位，限字母、数字、下划线，唯一且注册后不可修改。唯一性不区分大小写（MySQL 默认排序规则），`Alice` 与 `alice` 视为同一个用户名，登录时也不区分大小写（实现 02 号票时确认）。
   - 密码 8–32 位，限可打印 ASCII 字符（BCrypt 最多只接受 72 字节，实现 02 号票时补充）。
   - 昵称默认等于用户名，之后可以修改。
2. **密码存储**：用 BCrypt，只引入 `spring-security-crypto`，不引入完整的 Spring Security。
3. **token**：通过配置把默认的 `satoken: <uuid>` 改成 `Authorization: Bearer <uuid>`（参考 Sa-Token 文档 `docs/up/token-prefix.md`、`docs/up/token-style.md`）：
   ```yaml
   sa-token:
     token-name: Authorization
     token-prefix: Bearer        # 前缀和 token 之间必须有一个空格；不带前缀会直接认证失败，需在接口文档里写明
     token-style: uuid
     timeout: 604800             # 固定 7 天
     active-timeout: -1          # 长时间不访问也不冻结
     is-concurrent: true         # 允许多端同时登录
     is-share: false             # 每个设备各自拿一个 token
     is-read-cookie: false
     is-read-body: false
   ```
   - 退出登录只让当前设备的 token 失效。
   - 不做 refresh token。
   - `token-name` 同时是 Sa-Token 在 Redis 里的 key 前缀，所以 key 形如 `Authorization:login:token:...`，这是预期行为。
4. **会话**：用 `sa-token-redis-template` 加 `commons-pool2`，与业务共用同一个 Redis 实例。
   - Session 里只存 loginId（即用户 ID），不存 User 对象。这样不用处理 1.46 的 JSON 白名单问题，也不会因为 Session 里的用户信息过期而显示旧数据。
   - 需要用户信息时通过 `UserApi` 查询。
5. **角色**：不区分角色，`user` 表不加 role 字段，不实现 `StpInterface`。
   - "只能编辑或删除自己的文章、评论"这类资源归属检查，写在业务代码里。
6. **匿名访问**：用 `SaInterceptor` 让所有接口默认要求登录，公开接口单独加 `@SaIgnore`。
   - 公开接口：文章详情和列表、评论列表、搜索、热榜、用户主页、注册、登录。
   - 公开接口里需要识别当前用户时（例如作者看自己的草稿），用 `currentUserIdOrNull()`。
   - 未登录时抛出的 `NotLoginException` 由全局异常处理转成 401。
7. **获取当前用户**：framework 模块提供 `AuthContext.currentUserId()` 和 `currentUserIdOrNull()`，内部调用 Sa-Token。实现时另加了 `login(userId)` 和 `logout()`，使业务模块完全不接触 Sa-Token。
   - 只有 Controller 调用这两个方法；Controller 把 userId 作为参数传给 Service，Service 层不接触 Sa-Token。
   - MQ 消费者从消息体里拿 userId，不需要模拟登录上下文。
   - 集成测试基类提供 `register`、`login`（返回 token）和 `withToken(token)`：先调接口拿 token，之后的请求都带上 `Authorization: Bearer <token>`（实现时由 `loginAs(user)` 调整而来，多端登录测试需要直接拿到 token）。
