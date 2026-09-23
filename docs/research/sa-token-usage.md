# Sa-Token 1.46 在 Spring Boot 4 下的用法调研

> 调研日期：2026-09-23
> 目标环境：Java 21 / Spring Boot 4.1.1 / Maven / `cn.dev33:sa-token-spring-boot4-starter:1.46.0` / Redis 8.6

## 来源与版本说明

- 所有结论优先依据 **`v1.46.0` tag**（commit `ae58d1cc22878654c55a55c58f69f691ad5fba1c`，2026-08-19）的文档源码与代码。`dev` 分支（调研时 HEAD `f86324b`）相比 v1.46.0 已改动 1300+ 文件，所以只在 v1.46.0 缺少相应内容时才引用 `dev`，并会明确标注。
- 链接简写：
  - `DOC/...` = `https://raw.githubusercontent.com/dromara/Sa-Token/v1.46.0/sa-token-doc/...`
  - `SRC/...` = `https://github.com/dromara/Sa-Token/blob/v1.46.0/...`
  - `DEV/...` = `https://github.com/dromara/Sa-Token/blob/dev/...`
- `sa-token-doc-new/` 目录只存在于 `dev`，v1.46.0 tag 中没有。本文以 v1.46.0 的 `sa-token-doc/` 为准。
- 标注 **[未验证]** 的内容：没有在官方源码或文档中找到直接依据，或属于推断。

---

## 0. 依赖速查（Boot 4）

```xml
<!-- Spring Boot 4：用 starter-webmvc 替代已废弃的 starter-web -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
  <groupId>cn.dev33</groupId>
  <artifactId>sa-token-spring-boot4-starter</artifactId>
  <version>1.46.0</version>
</dependency>
<!-- Redis 持久化：没有 boot4 专属版本，直接用通用的 sa-token-redis-template -->
<dependency>
  <groupId>cn.dev33</groupId>
  <artifactId>sa-token-redis-template</artifactId>
  <version>1.46.0</version>
</dependency>
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-pool2</artifactId>
</dependency>
```

依据：官方 Boot 4 示例的 pom。它使用 `spring-boot-starter-parent 4.0.3`、`spring-boot-starter-webmvc`、`spring-boot-starter-aspectj`（注释写明“webmvc 替代 deprecated 的 starter-web，aspectj 替代 starter-aop”），再加上 `sa-token-spring-boot4-starter` 和 `sa-token-redis-template`（`SRC/sa-token-demo/sa-token-demo-springboot4-redis/pom.xml`）。

`sa-token-spring-boot4-starter` 本身的依赖只有 `sa-token-spring-boot-webmvc-v3v4-common` 和 `sa-token-jackson3`（`SRC/sa-token-starter/sa-token-spring-boot4-starter/pom.xml`）。它不会带入 web starter，web starter 要自己引入。

---

## 1. 登录认证核心 API

来源：`DOC/use/login-auth.md`；方法签名见 `SRC/sa-token-core/src/main/java/cn/dev33/satoken/stp/StpUtil.java`。

| API | 作用 |
|---|---|
| `StpUtil.login(Object id)` | 会话登录。id 建议用 `long`/`int`/`String`，不能传 `User` 这类复杂对象。登录后会生成 token 和 Session，并**默认通过 Cookie 写回前端** |
| `StpUtil.login(id, deviceType)` / `login(id, boolean isLastingCookie)` / `login(id, long timeout)` / `login(id, SaLoginParameter)` | 重载，可指定设备、Cookie 持久性、有效期等 |
| `StpUtil.isLogin()` | 返回布尔值，表示是否已登录 |
| `StpUtil.checkLogin()` | 未登录时抛 `NotLoginException` |
| `StpUtil.getLoginId()` / `getLoginIdAsLong()` / `AsInt()` / `AsString()` | 获取当前账号 id。未登录时抛 `NotLoginException` |
| `StpUtil.getLoginIdDefaultNull()` / `getLoginId(T defaultValue)` | 未登录时返回 null 或默认值 |
| `StpUtil.getTokenValue()` / `getTokenName()` / `getTokenInfo()` / `getTokenTimeout()` | 查询 token 信息 |
| `StpUtil.getLoginIdByToken(token)` | 按 token 反查账号 id，找不到时返回 null |
| `StpUtil.logout()` | 注销当前会话 |
| `StpUtil.logout(loginId)` / `kickout(loginId)` | 按账号注销，或踢人下线 |
| `StpUtil.createLoginSession(id)` / `getOrCreateLoginSession(id)` | 只创建登录会话数据并返回 token，**不写 Cookie** |

前后端分离时，常见做法是登录后返回 `StpUtil.getTokenInfo()` 或 `getTokenValue()`，前端每次请求在 header `satoken`（即 `token-name`）里带上它。示例：`SRC/sa-token-demo/sa-token-demo-springboot4-redis/src/main/java/com/pj/test/LoginController.java`。

**1.46 新增约束**：新增配置项 `allowLoginIdColon`，默认 `false`，即**默认禁止 loginId 包含冒号**，属于不向下兼容的变更（`DOC/more/update-log.md` v1.46.0；`DOC/use/config.md` 第 141 行）。

---

## 2. 配置项（`sa-token.*`）

来源：`DOC/use/config.md`（所有可配置项表格）、`DOC/fun/token-timeout.md`、`DOC/up/token-style.md`。

| 配置（yaml 写法） | 默认值 | 说明 |
|---|---|---|
| `token-name` | `satoken` | token 名称。同时用作 Cookie 名、header 名，以及**持久化 key 前缀** |
| `timeout` | `2592000`（30 天） | token 的绝对有效期，单位秒，`-1` 表示永不过期。可以用 `StpUtil.renewTimeout(s)` 续期 |
| `active-timeout` | `-1` | 最低活跃频率，单位秒。超过这个时间没有访问，token 会被**冻结**，抛 `NotLoginException`，场景值 `-3`。每次直接或间接调用 `getLoginId()` / `getTokenSession()` 时自动续签 |
| `dynamic-active-timeout` | `false` | 是否允许为单个 token 动态指定 activeTimeout |
| `is-concurrent` | `true` | 是否允许同一账号多地同时登录。设为 `false` 时，新登录会挤掉旧登录 |
| `is-share` | `false` | 多人登录同一账号时是否共用一个 token。如果 login 时带了 Extra 数据，即使设为 true 也会新建 token |
| `replaced-range` | `CURR_DEVICE_TYPE` | `is-concurrent=false` 时顶人下线的范围 |
| `replaced-login-exit-mode` | `OLD_DEVICE` | `is-concurrent=false` 时决定谁下线。`NEW_DEVICE` 表示拒绝本次新登录（1.45 新增“重复登录处理策略”） |
| `max-login-count` | `12` | 同一账号的最大登录数，只在 `is-concurrent=true` 且 `is-share=false` 时生效 |
| `overflow-logout-mode` | `LOGOUT` | 登录数超出上限时，按什么方式让多出的客户端下线 |
| `token-style` | `uuid` | 可选值：`uuid`、`simple-uuid`、`random-32`、`random-64`、`random-128`、`tik` |
| `token-prefix` | null | 例如设为 `Bearer`，前端就要提交 `satoken: Bearer xxx` |
| `is-read-header` / `is-read-cookie` / `is-read-body` | `true` | 从哪些位置读取 token。`is-read-cookie=false` 后，login 时也不会再写 Cookie |
| `is-write-header` | `false` | 登录后是否把 token 写入响应头 |
| `logout-range` | `TOKEN` | `StpUtil.logout()` 的注销范围。设为 `ACCOUNT` 会注销该账号的所有端 |
| `token-session-check-login` | `true` | 获取 Token-Session 时是否要求已登录 |
| `auto-renew` | `true` | 是否在调用 `getLoginId()` 时自动做过期检查和续签 |
| `allow-login-id-colon` | `false` | 1.46 新增，见上文 |
| `is-log` / `is-print` | `false` / `true` | 是否打印操作日志 / 是否打印启动字符画 |

Boot 4 示例的 `application.yml`：`SRC/sa-token-demo/sa-token-demo-springboot4-redis/src/main/resources/application.yml`，用法与上表一致。

---

## 3. 会话模型：Account-Session / Token-Session / Custom-Session

来源：`DOC/use/session.md`、`DOC/fun/session-model.md`。

| 类型 | 归属 | 获取方式 | 典型用途 |
|---|---|---|---|
| **Account-Session** | 每个**账号 id** 一个。同一账号的所有端（PC、APP）共享 | `StpUtil.getSession()`、`getSession(boolean create)`、`getSessionByLoginId(id[, create])`、`getSessionBySessionId(sid)` | 跨端共享的用户数据，例如缓存 user 对象 |
| **Token-Session** | 每个 **token** 一个。同一账号在不同端 token 不同，所以 Token-Session 也不同 | `StpUtil.getTokenSession()`、`getTokenSessionByToken(token)`、`getAnonTokenSession()`（未登录也可用） | 按端隔离的数据 |
| **Custom-Session** | 以**自定义 key** 为 SessionId | `SaSessionCustomUtil.getSessionById("goods-10001"[, create])`、`isExists`、`deleteSessionById` | 给任意业务对象（例如商品）挂缓存。默认有效期为全局 `timeout`，可以用 `session.updateTimeout(s)` 修改 |

三者返回的都是 `SaSession`，读写 API 相同：`set` / `get` / `getModel(key, Class)` / `getInt` / `getLong` / `has` / `delete` / `clear` / `keys` / `update` / `logout`。1.46 新增了类型安全的 `getList` / `getSet` / `getMap`（`DOC/more/update-log.md`）。

要点：
- `SaSession` 与 `HttpSession` 无关，不要混用（`DOC/use/session.md` §7）。
- **往 Session 里存业务实体并持久化到 Redis 时，需要注册 JSON 类型白名单**，否则读回会报 `无法反序列化的类型：xxx，请先将其注册到 JSON 全局类型白名单`。详见第 5 节。

---

## 4. 权限与角色校验

### 4.1 `StpInterface`

来源：`DOC/use/jur-auth.md`；Boot 4 示例 `SRC/sa-token-demo/sa-token-demo-springboot4-redis/src/main/java/com/pj/satoken/StpInterfaceImpl.java`。

```java
@Component
public class StpInterfaceImpl implements StpInterface {
    @Override public List<String> getPermissionList(Object loginId, String loginType) { ... }
    @Override public List<String> getRoleList(Object loginId, String loginType) { ... }
}
```

- 编程式 API：`StpUtil.hasPermission` / `checkPermission` / `checkPermissionAnd` / `checkPermissionOr`，`hasRole` / `checkRole` / `checkRoleAnd` / `checkRoleOr`。校验失败时分别抛 `NotPermissionException` / `NotRoleException`。
- 支持通配符：`art.*`、`*.delete`，单独一个 `"*"` 表示拥有全部权限，角色同理（`DOC/use/jur-auth.md` §6）。
- **框架默认不缓存权限数据**，每次校验都会调用 `StpInterface`。如果需要缓存，官方示例的做法是在实现类里借助 `SaManager.getSaTokenDao().getObject/setObject` 自行缓存（`DOC/fun/jur-cache.md`）。
- 1.46：`StpInterface.isDisabled` 增加了 `loginType` 参数，属于不向下兼容的变更（`DOC/more/update-log.md`）。

### 4.2 注解鉴权与拦截器注册

来源：`DOC/use/at-check.md`。

注解：`@SaCheckLogin`、`@SaCheckRole`、`@SaCheckPermission`（支持 `mode = SaMode.AND/OR` 和 `orRole`）、`@SaCheckSafe`、`@SaCheckHttpBasic`、`@SaCheckHttpDigest`、`@SaCheckDisable`、`@SaCheckSign`、`@SaCheckOr`（多个条件满足其一即可）、`@SaIgnore`（优先级最高，同时能豁免路由拦截）。这些注解都可以加在类上。

**注解鉴权默认关闭，必须注册 `SaInterceptor`**：

```java
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
```

Boot 4 示例的写法完全相同（`SRC/sa-token-demo/sa-token-demo-springboot4-redis/src/main/java/com/pj/satoken/SaTokenConfigure.java`）。Boot 4 使用的 `SaInterceptor` 类在 `sa-token-spring-boot-webmvc-v3v4-common` 模块中（`SRC/sa-token-starter/sa-token-spring-boot-webmvc-v3v4-common/src/main/java/cn/dev33/satoken/interceptor/SaInterceptor.java`）。

- 拦截器模式下，注解只对 Controller 方法生效（源码中 `handler instanceof HandlerMethod` 才检查注解）。如果要在 Service 层使用注解，需要引入 `sa-token-spring-aop`，但**拦截器和 AOP 两种模式不能同时集成**，否则同一个注解会被校验两次（`DOC/plugin/aop-at.md`）。
- **[未验证] Boot 4 风险**：v1.46.0 的 `sa-token-spring-aop` 依赖 `spring-boot-starter-aop`（`SRC/sa-token-plugin/sa-token-spring-aop/pom.xml`）。Boot 4 官方示例的注释称该 starter 已被 `spring-boot-starter-aspectj` 取代。旧 artifact 在 Boot 4.1.1 BOM 中是否仍然可以解析，需要实际验证。

### 4.3 路由拦截 `SaInterceptor` + `SaRouter`

来源：`DOC/use/route-check.md`。

```java
registry.addInterceptor(new SaInterceptor(handler -> {
    SaRouter.match("/**").notMatch("/user/doLogin").check(r -> StpUtil.checkLogin());
    SaRouter.match("/admin/**", r -> StpUtil.checkRoleOr("admin", "super-admin"));
})).addPathPatterns("/**");
```

- 可以按 path、HTTP method、布尔条件或 lambda 匹配。`SaRouter.stop()` 退出匹配链后请求继续进入 Controller；`SaRouter.back(x)` 则直接返回结果给前端。
- 带 auth 函数的 `SaInterceptor` **默认同时开启注解鉴权**，可以用 `.isAnnotation(false)` 关闭（§7）。`setBeforeAuth` 在注解鉴权之前执行。
- 执行顺序是：`beforeAuth` → 注解鉴权 → `auth`（源码 `preHandle`）。
- `SaServletFilter`（全局过滤器）是另一种方案。**过滤器中抛出的异常不会进入 `@RestControllerAdvice`**，必须通过 `setError` 自行处理（`DOC/up/global-filter.md` 第 78 行）。

---

## 5. Redis 持久化（Boot 4）

### 5.1 用哪个 artifact

- **`cn.dev33:sa-token-redis-template:1.46.0` + `commons-pool2`。没有 boot4 专属的版本**。
  - 官方 Boot 4 示例 `sa-token-demo-springboot4-redis` 用的就是它（`SRC/sa-token-demo/sa-token-demo-springboot4-redis/pom.xml`）。
  - 源码 javadoc 写明“可用环境: SpringBoot2、SpringBoot3、SpringBoot4”（`SRC/sa-token-plugin/sa-token-redis-template/src/main/java/cn/dev33/satoken/dao/SaTokenDaoForRedisTemplate.java` 第 36 行）。
  - 官方集成文档：`DOC/up/integ-redis.md` §1。
  - v1.46.0 的 `sa-token-plugin/` 目录中只有两个 Boot 4 专属插件：`sa-token-alone-redis-by-spring-boot4` 和 `sa-token-jackson3`。
- 它依赖 `spring-boot-starter-data-redis`。这个 pom 继承的是 boot2 的版本管理（`SRC/sa-token-plugin/pom.xml` 导入 `sa-token-spring-boot2-dependencies`），但项目使用 `spring-boot-starter-parent 4.1.1` 时，Boot 的 dependencyManagement 会覆盖传递依赖的版本。本项目 pom 也已显式声明 `spring-boot-starter-data-redis`。
- **不要在 Boot 4 下使用 `sa-token-redis-jackson`**：它只是一个聚合包，依赖 `sa-token-jackson`（Jackson 2，`com.fasterxml`）加 `sa-token-redis-template`（`SRC/sa-token-plugin/sa-token-redis-jackson/pom.xml`）。Boot 4 starter 默认已经引入 `sa-token-jackson3`（`DOC/plugin/json-extend.md` 第 24 行）。**[推断]** 两个 JSON 插件的 `install()` 都是“只有当前还是默认实现时才替换”（`SaTokenPluginForJackson3.java` / `SaTokenPluginForJackson.java`），同时引入两者时，最终生效哪一个取决于 SPI 加载顺序，结果不确定。
- 如果要把权限缓存与业务缓存放到不同的 Redis：Boot 4 必须用 **`sa-token-alone-redis-by-spring-boot4`**，而不是 `sa-token-alone-redis`（`DOC/plugin/alone-redis.md` 第 21–22 行；v1.46.0 新增，见 `DOC/more/update-log.md`）。
- 如果用 Redisson：Boot 4 **不要引入 `redisson-spring-boot-starter`**，因为 3.45 版本仍引用已被移除的 `RedisAutoConfiguration`。应该改为引入 `redisson` 并自己注册 Bean（`DOC/plugin/alone-redisson.md` 第 17 行）。

### 5.2 序列化

- `SaTokenDaoForRedisTemplate` 内部自己构建 `StringRedisTemplate`，数据以**字符串**形式写入 Redis。对象（例如 `SaSession`）先经 `SaJsonTemplate` 转成 JSON（源码第 43–66 行）。Boot 4 下默认的 `SaJsonTemplate` 是 `sa-token-jackson3` 提供的 `SaJsonTemplateForJackson3`（Jackson 3，`tools.jackson.core`）。它**不使用**你自己配置的 `RedisTemplate` 序列化器，只复用 `RedisConnectionFactory`。
- 可以换成其他 JSON 插件：`sa-token-fastjson2`、`sa-token-fory-json`、`sa-token-snack4` 等（`DOC/plugin/json-extend.md`）。也可以换成 JDK 序列化：`SaManager.setSaSerializerTemplate(new SaSerializerTemplateForJdkUseBase64())`（`DOC/up/integ-redis.md` §2.2），或使用 `sa-token-redis-template-jdk-serializer`。
- **JSON 全局类型白名单（1.46 新增，修复了 RCE 漏洞）**：Jackson 系插件会写入类型信息，业务实体默认不在白名单里。注册方式有三种（`DOC/plugin/json-extend.md` “JSON 全局类型白名单机制”）：
  1. 实体类实现 `cn.dev33.satoken.json.SaJsonType` 标记接口（官方推荐）；
  2. 在 `SpringApplication.run` **之前**调用 `cn.dev33.satoken.strategy.SaJsonStrategy.instance.registerAllowType(SysUser.class)`。JSON 插件初始化之后就不能再注册；
  3. 在 `resources/META-INF/satoken/sa-json-type.list` 中逐行写上全限定类名。
  - fastjson / fastjson2 / fory-json / snack3 默认不写类型信息，一般不会遇到这个问题，但读取时要用 `getModel(key, Class)` 指定类型。

### 5.3 配置

Boot 3+ 的前缀是 **`spring.data.redis`**，而不是 `spring.redis`（`DOC/up/integ-redis.md` §3 的提示；Boot 4 示例 yml 也用 `spring.data.redis`）：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 1
      timeout: 10s
      lettuce:
        pool: { max-active: 200, max-wait: -1ms, max-idle: 10, min-idle: 0 }
```

- 引入依赖后框架会**自动**把会话数据写入 Redis，上层 API 不变（`DOC/up/integ-redis.md` §3）。自动配置的注册位置：`SRC/sa-token-plugin/sa-token-redis-template/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，内容为 `cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate`。
- **Redis ≥ 6.0**：从 1.46 起，`update` 使用 `SET ... KEEPTTL`，低于 6.0 的 Redis 会报 `ERR syntax error`（`DOC/up/integ-redis.md` §1 WARNING）。本项目使用 Redis 8.6，不受影响。
- 1.46 同时修复了 update 时 TTL 偏移的问题，并把 `searchData` 从 `KEYS` 改为 `SCAN`（`DOC/more/update-log.md`）。
- 多个项目共用一个 Redis 时，隔离方式有：用不同的 db；改 `token-name`（它是 key 前缀，但也会改变前端传参名）；使用 alone-redis；重写 `SaTokenDaoForRedisTemplate#wrapKey` 并注册为 `@Primary` Bean（`DOC/up/integ-redis.md` §4）。
- Redis 插件的版本应与 starter 版本保持一致（`DOC/up/integ-redis.md` §3）。

---

## 6. 全局异常处理

来源：`DOC/use/login-auth.md` §2、`DOC/use/jur-auth.md` §3–5；Boot 4 示例 `SRC/sa-token-demo/sa-token-demo-springboot4-redis/src/main/java/com/pj/current/GlobalException.java`；异常类 `SRC/sa-token-core/src/main/java/cn/dev33/satoken/exception/`。

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<?> notLogin(NotLoginException e) { /* 401；e.getType() 可区分场景 */ }
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<?> notRole(NotRoleException e) { /* 403；e.getRole() */ }
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<?> notPerm(NotPermissionException e) { /* 403；e.getPermission() */ }
}
```

- 所有异常都继承 `SaTokenException`（它是 `RuntimeException`）。三类异常都提供 `getLoginType()`，多账号体系下可以据此区分。
- `NotLoginException.getType()` 的场景值常量：`NOT_TOKEN="-1"`（没有 token）、`INVALID_TOKEN="-2"`、`TOKEN_TIMEOUT="-3"`、`BE_REPLACED="-4"`（被顶下线）、`KICK_OUT="-5"`、`TOKEN_FREEZE="-6"`（被 active-timeout 冻结）、`NO_PREFIX="-7"`（`NotLoginException.java` 第 45–73 行；另见 `DOC/fun/not-login-scene.md`）。
- 其他常用异常：`DisableServiceException`（账号被封禁，`@SaCheckDisable`）、`NotSafeException`（二级认证）等。
- 只有 `SaInterceptor` 和 Controller 内抛出的异常才会进入 `@RestControllerAdvice`。`SaServletFilter` 中的异常必须用 `setError` 处理（`DOC/up/global-filter.md` 第 78 行）。
- 官方示例返回 HTTP 200，把业务码放在 body 里。要不要返回 401/403 状态码由项目自行决定。

---

## 7. 集成测试（Spring Boot Test / MockMvc）中的登录态

### 7.1 关键机制：上下文由 Filter 注入

Boot 4 starter 通过 `SaTokenContextRegister` 注册 `FilterRegistrationBean<SaTokenContextFilterForJakartaServlet>`（url `/*`，dispatcher 为 REQUEST 加 ASYNC）。这个 Filter 在每次请求时把 request/response 放进 ThreadLocal 上下文（`SRC/sa-token-starter/sa-token-spring-boot-webmvc-v3v4-common/src/main/java/cn/dev33/satoken/spring/SaTokenContextRegister.java`、`.../filter/SaTokenContextFilterForJakartaServlet.java`）。`SaManager` 的默认上下文是 `SaTokenContextForThreadLocal`（`SaManager.java` 第 157–165 行）。

**因此，MockMvc 必须挂上这个 Filter。** 否则调用 `StpUtil.*` 会报 `SaTokenContextException: SaTokenContext 上下文尚未初始化`（报错文本见 `DOC/fun/async--mock.md`）。

### 7.2 方式 A：通过 HTTP 登录拿 token，再放进 header（官方 Boot 4 集成测试的做法）

官方 Boot 4 集成测试**只在 `dev` 分支上**（v1.46.0 中还没有）：`DEV/sa-token-testing/sa-token-integration-boot4/src/test/java/cn/dev33/satoken/integration/boot4/support/Boot4MockMvcSupport.java`，注释写着“Boot 4 需要手动按过滤器链组装 MockMvc”：

```java
MockMvcBuilders.webAppContextSetup(wac)
    .addFilters(
        contextFilter,                         // 从 FilterRegistrationBean 中取出 SaTokenContextFilterForJakartaServlet
        ctx.getBean(SaTokenCorsFilterForJakartaServlet.class),
        ctx.getBean(SaFirewallCheckFilterForJakartaServlet.class),
        ctx.getBean(SaServletFilter.class))    // 只有项目注册了 SaServletFilter 才需要
    .build();
```

测试写法（`DEV/.../boot4/auth/LoginAndAnnotationSmokeTest.java`）：先调用登录接口，从响应中取出 token，之后的请求都带上 `.header("satoken", token)`。

### 7.3 方式 B：`@AutoConfigureMockMvc`

Boot 4 中，`@AutoConfigureMockMvc` 位于 `org.springframework.boot.webmvc.test.autoconfigure`，属于 `spring-boot-webmvc-test` 模块（本项目已通过 `spring-boot-starter-webmvc-test` 引入）。已在本地 `spring-boot-webmvc-test-4.1.1.jar` 中确认该类和 `SpringBootMockMvcBuilderCustomizer$FilterRegistrationBeans` 都存在。Spring Boot 的这个 customizer 默认（`addFilters = true`）会把容器中的 `FilterRegistrationBean` 挂进 MockMvc，**理论上**不需要像方式 A 那样手动组装。**[未验证]** Sa-Token 官方没有给出这种写法的示例，需要在本项目中实测。

### 7.4 方式 C：不走 HTTP，直接造 token

- `StpUtil.createLoginSession(id)` 直接创建会话数据，返回 token，不写 Cookie。从源码看，`StpLogic.createLoginSession` 的步骤是参数校验 → 分配 token → Account-Session → token 映射 → 事件，没有直接读取 Web 上下文（`SRC/sa-token-core/src/main/java/cn/dev33/satoken/stp/StpLogic.java` 第 488–533 行）。测试中可以先调用它拿到 token，再把 token 放进 MockMvc 请求头。**[未验证：未实测]**
- 在非 Web 线程中（例如 Service 层单元测试）调用需要上下文的 API 时，用 `SaTokenContextMockUtil.setMockContext(() -> { StpUtil.setTokenValueToStorage(token); ... })` 模拟上下文（`DOC/fun/async--mock.md`；`SRC/sa-token-core/src/main/java/cn/dev33/satoken/context/mock/SaTokenContextMockUtil.java`，1.42.0 起提供）。

### 7.5 测试隔离提示

- 默认的 `SaTokenDao` 是内存实现。引入 `sa-token-redis-template` 后测试会连接真实 Redis，需要配合 Testcontainers 或 docker-compose，或者在测试 profile 中改用其他 Dao。**[建议，非官方来源]**
- `SaManager` 是静态单例，多个 Spring 上下文之间共享同一份全局状态。**[推断]**

---

## 8. Spring Boot 4 / 1.46 特有注意事项

1. **artifact 选择**：Boot 4 使用 `sa-token-spring-boot4-starter`。**不要**误引入 `sa-token-spring-boot-starter`（Boot 2）或 `-boot3-`，否则会报错（`DOC/more/common-questions.md` 第 729、745 行）。WebFlux 网关使用 `sa-token-reactor-spring-boot4-starter`。
2. **web / aop starter 改名**：Boot 4 用 `spring-boot-starter-webmvc` 替代 `spring-boot-starter-web`，用 `spring-boot-starter-aspectj` 替代 `spring-boot-starter-aop`（官方 Boot 4 示例 pom 的注释）。starter 本身不带入 web 依赖。
3. **Redis 插件**：通用的 `sa-token-redis-template` 可直接使用，没有 boot4 专属版本。**alone-redis 必须换成 `sa-token-alone-redis-by-spring-boot4`**。不要使用 `redisson-spring-boot-starter`（3.45）。避免使用 `sa-token-redis-jackson`（它会带入 Jackson 2）。
4. **JSON 使用 Jackson 3**：Boot 4 starter 默认引入 `sa-token-jackson3`（依赖 `tools.jackson.core:jackson-databind`，v1.46.0 管理的版本为 3.1.0，见 `SRC/sa-token-dependencies/pom.xml` 第 24 行）。Redis 中的 JSON 由它负责序列化。
5. **1.46 的不向下兼容变更**：
   - 默认禁止 loginId 含冒号（`allowLoginIdColon`）；
   - `StpInterface.isDisabled` 增加了 `loginType` 参数；
   - JSON 反序列化改为类型白名单制（Session 中的实体需要注册）；
   - 集成 JWT 时 `extraData` 不能包含保留字段；
   - 使用 Redisson 时默认 codec 改为 `StringCodec`，升级后旧缓存无法读取（`DOC/more/update-log.md` v1.46.0；`DOC/up/integ-redis.md` §5.3）。
6. **Redis ≥ 6.0**（KEEPTTL）。
7. **上游测试所用的 Boot 版本**：v1.46.0 的 `sa-token-spring-boot4-dependencies` 固定为 Spring Boot **4.0.3** / Spring **7.0.3**（`SRC/sa-token-special-dependencies/sa-token-spring-boot4-dependencies/pom.xml` 第 21–22 行）。本项目是 **4.1.1**。**[未验证]** 官方没有声明对 4.1.x 的兼容性。已确认的一点是：`sa-token-spring-boot-webmvc-v3v4-common` 使用的 `org.springframework.boot.web.servlet.FilterRegistrationBean` 在本地 `spring-boot-4.1.1.jar` 中仍位于该包下。
8. **MockMvc 需要挂上 `SaTokenContextFilterForJakartaServlet`**（见第 7 节）。v1.46.0 的 Context Filter 注册了 REQUEST 加 ASYNC 两种 dispatcher，修复了 SSE/异步返回时上下文未初始化的问题（`DOC/more/update-log.md`）。
9. 在 `@Async`、`@Scheduled`、MQ 消费者等非 Web 线程中调用 `StpUtil.isLogin()` 等 API 会报“上下文尚未初始化”，需要用 `SaTokenContextMockUtil` 配合 `setTokenValueToStorage(token)`（`DOC/fun/async--mock.md`）。本项目引入了 AMQP，需要留意这一点。

---

## 未能验证 / 待实测清单

- `sa-token-spring-aop` 在 Boot 4.1.1 下能否解析 `spring-boot-starter-aop`。
- `@AutoConfigureMockMvc` 自动挂载 Sa-Token 的 Filter 后，登录态是否正常（方式 B）。
- 在没有 Web 上下文时调用 `StpUtil.createLoginSession(id)`（方式 C）是否完全可用。
- Sa-Token 1.46.0 与 Spring Boot 4.1.1（上游测试基于 4.0.3）的整体兼容性。
- 同时引入 `sa-token-jackson` 与 `sa-token-jackson3` 时，哪个 JSON 插件生效（依赖 SPI 顺序）。
