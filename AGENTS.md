# AGENTS.md

## 原则

- 简洁优先：遵守 KISS/YAGNI/fail-fast，避免无必要抽象
- 注释：编写中文注释，遵循 Javadoc 规范
- 提交信息：Conventional Commits 格式，描述和正文使用简体中文，类型和作用域保留英文
- 提交范围：每次提交只包含一个逻辑变更
- 编码约定：分包、Service 写法、事件、错误码与 Flyway 编号见 `.scratch/code-nest/spec.md` 的实现决策，实现前先读
- 文档同步：文档需与想法、决策和代码保持同步，只写当前状态：修改时就地改写成最新结论，不留旧值、改动来由或确认过程；内容遵循所在文档的体裁和要求

## 测试

- 构建与测试都在同一次 reactor 构建里带上依赖模块，例如 `./mvnw -pl code-nest-app -am test -Dtest=FollowApiTest -Dsurefire.failIfNoSpecifiedTests=false`。
- 所有测试类共用同一组容器和同一个库（MySQL、Redis、RabbitMQ、ES），测试数据用唯一的用户、标签等隔离，不能假设表或索引为空，也不要清库。
- 模式开关的测试矩阵：每档一个 `@TestPropertySource` 注解（参照 `support/RedisAsyncCounter`），给受该开关影响的已有 HTTP 测试类各加一个继承它的子类。
- 不同档的 Spring 上下文会同时存活并共用同一个 RabbitMQ：一个上下文发出的消息可能被另一个上下文的消费者消费。按档装配的消费者、本地缓存失效等逻辑，写测试时要考虑这一点。
- Do not backfill tests after implementing business code.
- Tautological tests considered harmful.
- Change-detector tests considered harmful.
- Do not create regression tests for bug fixes without a genuine gap in behavior testing.

## Agent skills

### Issue tracker

Issues and specs live as local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
