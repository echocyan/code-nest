# 01: 工程骨架与分类/标签

**What to build:** 把 IDEA 初始化的单模块工程改造成规格定义的多模块工程，搭好中间件环境、通用约定和测试基础设施，并用"获取预置分类和标签"这条最简单的链路贯通全链路：Flyway 种子数据 → MyBatis-Plus → Controller → HTTP 测试。完成后，访客可以匿名调用 `GET /api/v1/categories` 和 `GET /api/v1/tags`。

**Blocked by:** None (can start immediately)

**Status:** done

- [x] **Maven 结构**：父 pom、common、framework、modules 聚合（先建 article 模块）、app、loadtest（空壳）。依赖方向为 app → 业务模块 → framework → common。
- [x] **依赖**：MyBatis-Plus 改用 `mybatis-plus-spring-boot4-starter`；配置 MapStruct 与 Lombok 注解处理器的先后顺序；引入 SpringDoc。
- [x] **中间件环境**：根目录 compose 定义 mysql:8.4、redis:8.6（开启 AOF everysec）、rabbitmq:4.3.5-management、ES 9.4.5。ES 由一个装了 IK 9.4.5 插件的 Dockerfile 构建。`spring-boot-docker-compose` 以 `start-only` 模式拉起中间件，在 IDEA 里可以直接运行应用。
- [x] **common**：`{code, message, data}` 返回体、`ErrorCode` 接口与通用错误码（0、90400/90401/90403/90404/90429/99999）、业务异常、`PageResult`、`CursorResult`。
- [x] **framework 基础**：
  - 全局异常处理，按语义设置 HTTP 状态码，参数校验失败返回 400。
  - Jackson 约定：Long 输出为字符串，时间为带偏移的 ISO-8601（Asia/Shanghai），枚举为大写字符串，空值输出 null。
  - MyBatis-Plus 的雪花 ID、`created_at`/`updated_at` 自动填充、逻辑删除配置。
- [x] **路由**：所有业务接口挂在 `/api/v1` 下。
- [x] **Flyway**：各模块在自己的资源目录下放迁移脚本，版本前缀用模块号。article 模块建 category、tag 表，并写入种子数据。
- [x] **分类与标签接口**：`GET /categories` 按 sort 排序返回；`GET /tags` 返回全部标签。
- [x] **测试基础设施**：
  - app 模块的集成测试基类用 Testcontainers 加 `@ServiceConnection`，四个中间件的容器以单例共享，ES 复用带 IK 的 Dockerfile。
  - 为上面两个接口写 HTTP 测试。
- [x] **ArchUnit 规则**：业务模块之间只能依赖对方的 `api` 包；模块依赖无环。
- [x] OpenAPI 页面可以访问。

## Comments

- 实现于 `12ead28`，并按 code review 修正（见后续提交）。与规格的出入：
  - ES 服务端与客户端统一为 9.4.5：原定服务端是 8.19.21，但 Boot 4.1.1 管理的客户端是 9.x，实测 9.x 客户端连 8.19 服务端会报 `media_type_header_exception`。经用户决定，服务端升级到 9.x，客户端直接用 Boot 的自动配置（Rest5Client 加独立的 `Jackson3JsonpMapper`，Web 层的 JSON 约定不会影响索引文档）。
  - `spring.flyway.out-of-order: true`：每个模块独立编号，需要允许低编号模块在后面补迁移脚本。
  - 预置的分类和标签使用固定的小整数 ID，不用雪花 ID，便于造数和测试引用。
  - Spring MVC 自带的请求类异常（405、415、缺少请求头等）沿用各自的 HTTP 状态码，body 中的 code 为 90400，404 时为 90404。
  - ArchUnit 除了 api 包规则和无环规则，还按规格中的模块 DAG 校验依赖方向。
