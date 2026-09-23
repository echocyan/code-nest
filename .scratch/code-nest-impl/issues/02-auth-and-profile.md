# 02: 注册、登录与个人资料

**What to build:** 访客可以用用户名和密码注册（注册后自动登录）、登录，拿到 `Authorization: Bearer <uuid>` 格式的 token。已登录用户可以退出当前设备、查看和修改自己的资料。任何人都可以查看用户主页，本票只返回资料部分，计数由 03 补上。认证方案见决策票 04 和 Sa-Token 调研笔记。

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] **user 模块**：新增模块，建 `user` 表（Flyway 前缀 `V1_`），定义 1xxxx 号段的错误码。
- [ ] **注册校验**：
  - 用户名 4–20 位，限字母、数字、下划线，唯一，重复时返回明确的错误码。
  - 密码 8–32 位，用 spring-security-crypto 的 BCrypt 存储。
  - 昵称默认等于用户名。
- [ ] **Sa-Token**：
  - 按决策票 04 的配置使用 Bearer 前缀和 uuid 风格；token 有效期 7 天，无活跃超时；允许多端登录，不共享 token；不从 cookie 或 body 读 token。
  - 存储用 `sa-token-redis-template` 加 `commons-pool2`，不引入 Jackson 2。
  - Session 里只存用户 ID。
- [ ] **拦截与匿名访问**：注册 `SaInterceptor`，接口默认要求登录，公开接口标 `@SaIgnore`。未登录时返回 401 和错误码 90401。
- [ ] **`AuthContext`**：framework 提供 `currentUserId()` 和 `currentUserIdOrNull()`，只在 Controller 使用。
- [ ] **接口**：
  - `POST /auth/register`、`POST /auth/login`、`POST /auth/logout`（只让当前 token 失效，其他设备的 token 仍然有效）。
  - `GET /users/me`、`PUT /users/me`（可以修改昵称、头像 URL、简介，不能修改用户名）。
  - `GET /users/{id}`（匿名可访问，用户不存在时返回 404）。
- [ ] **`UserApi`**：提供按 ID 批量查询用户简要信息、判断用户是否存在，供后续模块使用。
- [ ] **测试基类**：提供 `loginAs(user)`。MockMvc 测试要挂上 Sa-Token 的上下文 Filter，或改用真实 HTTP。
- [ ] **HTTP 测试**：覆盖注册、登录、登出、多端登录、401、参数校验、修改资料。
