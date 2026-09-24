# 02: 注册、登录与个人资料

**What to build:** 访客可以用用户名和密码注册（注册后自动登录）、登录，拿到 `Authorization: Bearer <uuid>` 格式的 token。已登录用户可以退出当前设备、查看和修改自己的资料。任何人都可以查看用户主页，本票只返回资料部分，计数由 03 补上。认证方案见决策票 04 和 Sa-Token 调研笔记。

**Blocked by:** 01

Status: closed

- [x] **user 模块**：新增模块，建 `user` 表（Flyway 前缀 `V1_`），定义 1xxxx 号段的错误码。
- [x] **注册校验**：
  - 用户名 4–20 位，限字母、数字、下划线，唯一，重复时返回明确的错误码。
  - 密码 8–32 位，限可打印 ASCII 字符，用 spring-security-crypto 的 BCrypt 存储。
  - 昵称默认等于用户名。
- [x] **Sa-Token**：
  - 按决策票 04 的配置使用 Bearer 前缀和 uuid 风格；token 有效期 7 天，无活跃超时；允许多端登录，不共享 token；不从 cookie 或 body 读 token。
  - 存储用 `sa-token-redis-template` 加 `commons-pool2`，不引入 Jackson 2。
  - Session 里只存用户 ID。
- [x] **拦截与匿名访问**：注册 `SaInterceptor`，接口默认要求登录，公开接口标 `@SaIgnore`。未登录时返回 401 和错误码 90401。
- [~] **`AuthContext`**：framework 提供 `currentUserId()` 和 `currentUserIdOrNull()`，只在 Controller 使用。
- [x] **接口**：
  - `POST /auth/register`、`POST /auth/login`、`POST /auth/logout`（只让当前 token 失效，其他设备的 token 仍然有效）。
  - `GET /users/me`、`PUT /users/me`（可以修改昵称、头像 URL、简介，不能修改用户名）。
  - `GET /users/{id}`（匿名可访问，用户不存在时返回 404）。
- [~] **`UserApi`**：提供按 ID 批量查询用户简要信息、判断用户是否存在，供后续模块使用。
- [x] **测试基类**：提供 `loginAs(user)`。MockMvc 测试要挂上 Sa-Token 的上下文 Filter，或改用真实 HTTP。
- [x] **HTTP 测试**：覆盖注册、登录、登出、多端登录、401、参数校验、修改资料。

## Comments

- 推迟到 03 号票的两项（标 `[~]`）：
  - `AuthContext.currentUserIdOrNull()`：本票的接口都用不到。
  - `UserApi`：本票没有调用方，按 TDD 写不出先失败的测试。
  - 03 号票第一个用到它们（作者看自己的草稿、文章详情带作者信息），已转入 03 号票的验收项。
- 实现细节：
  - 登录失败时，用户不存在和密码错误返回同一个错误码 10002（HTTP 401），不暴露用户名是否已注册。
  - 用户名重复返回 10001（HTTP 409），由唯一索引判定，并发注册同名时也只有一个成功。唯一性和登录都不区分大小写（MySQL 默认排序规则）。
  - `AuthContext` 另加了 `login(userId)` 和 `logout()`，使 user 模块也不直接接触 Sa-Token。
  - 错误码枚举 `UserErrorCode` 放在模块根包，这一约定已写入规格的"模块内分包"一节。
  - 头像地址必须以 http:// 或 https:// 开头，防止存入 `javascript:` 之类的地址。
  - `PUT /users/me` 整体替换可修改的字段：没传的头像、简介会被清空；请求里的 username 会被忽略。
  - `SaInterceptor` 只挂在 `/api/v1/**` 上，而且只检查 Controller 方法。不存在的路径仍然返回 404，Swagger 和 Actuator 不受影响。
  - OpenAPI 文档声明了 Bearer 鉴权方案，写明了缺少前缀会被视为未登录。只有没标 `@SaIgnore` 的接口会被标注为需要登录，与拦截器的规则一致。
  - 测试基类没有做成 `loginAs(user)`，而是提供 `register`、`login`（都返回 token）和 `withToken(token)`：多端登录的测试需要直接拿到 token。
  - 手动确认过登录态存在 Redis 中，key 前缀为 `Authorization:`。

