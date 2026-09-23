# API 设计规范

Type: grilling
Status: resolved
Blocked by:

## Question

（背景：返回体、错误码分段、两种分页结构已在[工程结构与测试基础设施](02-project-structure.md)中定下；各接口的分页方式已由各业务票决定。）

对外 REST 接口的统一约定是什么：
- URL 风格：资源名用复数、路径前缀与版本号（如 `/api/v1`）、嵌套资源的写法（如 `/articles/{id}/comments`）。
- 点赞、收藏、关注这类"开关型"动作用哪种语义：`PUT`/`DELETE` 幂等表达，还是 `POST` 切换？
- 请求与响应的字段命名、时间格式、ID 统一为字符串。
- 分页参数命名。
- 各模块的错误码清单如何登记。
- 以及一份按模块列出的接口清单，作为 `to-spec` 的输入。

## Answer

1. **URL 风格**：
   - 所有接口统一挂在 `/api/v1` 前缀下，Actuator 管理端点除外。
   - 资源名用复数、kebab-case。
   - 只对有明确从属关系的资源使用嵌套路径，例如 `/articles/{id}/comments`、`/comments/{id}/replies`。
   - 当前用户用 `me` 表示。
2. **开关型动作**（点赞、收藏、关注）：
   - 用 `PUT` 开启、`DELETE` 关闭，两者都幂等，重复请求返回 200 且不产生任何变化。
   - 只有数据库里真的插入或删除了一行，才调用 `CounterApi.increment` 并发出事件。
   - 这与数据库唯一索引"至多一次"的语义一致。
3. **字段约定**：
   - JSON 字段用 camelCase。
   - 时间用带偏移的 ISO-8601 格式，例如 `2026-09-23T10:00:00+08:00`。
   - ID 一律用字符串。
   - 枚举输出为大写字符串，例如 `DRAFT`、`PUBLISHED`。
   - 字段为空时返回 `null`，不省略该字段。
4. **分页参数**：
   - 页码分页用 `page`（从 1 开始）和 `size`。
   - 游标分页用 `cursor`（第一页不传）和 `size`。
   - `size` 默认 20，最大 50。
   - 各接口的翻页上限沿用各自票里的规定。
5. **错误码与 Flyway 编号统一**：
   - 每个模块定义一个错误码枚举，实现 common 模块的 `ErrorCode` 接口。
   - 模块编号同时决定错误码号段和 Flyway 版本前缀：

     | 编号 | 模块 | 错误码 | Flyway 前缀 |
     |---|---|---|---|
     | 0 | framework（mq 表） | 0 = 成功 | `V0_` |
     | 1 | user | 1xxxx | `V1_` |
     | 2 | article | 2xxxx | `V2_` |
     | 3 | interaction | 3xxxx | `V3_` |
     | 4 | social | 4xxxx | `V4_` |
     | 5 | notification | 5xxxx | `V5_` |
     | 6 | search | 6xxxx | 无表 |
     | 7 | counter | 7xxxx | `V7_` |
     | 9 | 通用 | 9xxxx | — |

   - 通用错误码的后三位与 HTTP 状态码一致：90400 参数错误、90401 未登录、90403 无权限、90404 不存在、90429 请求过于频繁、99999 系统错误。
6. **接口清单**：共 39 个业务接口和 2 个管理端点。用户看过删减候选之后，决定全部保留。

   下表中，"匿"表示允许匿名访问（`@SaIgnore`），其余接口都需要登录。

   | 模块 | 方法 | 路径 | 说明 |
   |---|---|---|---|
   | user | POST | `/auth/register` | 注册后自动登录（匿） |
   | user | POST | `/auth/login` | 登录（匿） |
   | user | POST | `/auth/logout` | 退出当前设备 |
   | user | GET / PUT | `/users/me` | 查看、修改自己的资料 |
   | user | GET | `/users/{id}` | 用户主页，含资料和计数（匿） |
   | article | GET | `/categories`、`/tags` | 预置的分类和标签（匿） |
   | article | POST | `/articles` | 新建草稿 |
   | article | PUT | `/articles/{id}` | 编辑，用 version 做乐观锁 |
   | article | POST | `/articles/{id}/publish` | 发布 |
   | article | DELETE | `/articles/{id}` | 软删除 |
   | article | GET | `/articles/{id}` | 文章详情；草稿只有作者本人能看（匿） |
   | article | GET | `/articles?categoryId=&tagId=&page=` | 最新文章列表（匿） |
   | article | GET | `/users/{id}/articles?cursor=` | 作者的文章（匿） |
   | article | GET | `/users/me/drafts?cursor=` | 我的草稿 |
   | article | GET | `/articles/{id}/comments?cursor=` | 评论列表，每条带回复数（匿） |
   | article | POST | `/articles/{id}/comments` | 发表评论 |
   | article | GET | `/comments/{id}/replies?cursor=` | 回复列表（匿） |
   | article | POST | `/comments/{id}/replies` | 发表回复，可带 `replyToUserId` |
   | article | DELETE | `/comments/{id}` | 删除评论或回复 |
   | article | GET | `/hot-articles?page=` | 热榜（匿） |
   | interaction | PUT / DELETE | `/articles/{id}/like` | 点赞、取消点赞 |
   | interaction | PUT / DELETE | `/articles/{id}/favorite` | 收藏、取消收藏 |
   | interaction | GET | `/users/me/favorites?cursor=` | 我的收藏 |
   | interaction | GET | `/articles/states?ids=` | 批量查询是否已点赞、已收藏 |
   | social | PUT / DELETE | `/users/{id}/follow` | 关注、取关 |
   | social | GET | `/users/{id}/followers?cursor=`、`/users/{id}/followings?cursor=` | 粉丝列表、关注列表（匿） |
   | social | GET | `/users/follow-states?ids=` | 批量查询是否已关注 |
   | social | GET | `/feed?cursor=` | 关注 Feed |
   | notification | GET | `/notifications?cursor=` | 通知列表 |
   | notification | GET | `/notifications/unread-count` | 未读数（上限 99+） |
   | notification | PUT | `/notifications/{id}/read`、`/notifications/read-all` | 标为已读 |
   | search | GET | `/search/articles?q=&categoryId=&tagId=&sort=RELEVANCE\|LATEST&page=` | 搜索（匿） |
   | 管理端口 | POST | `/actuator/search-rebuild` | 全量重建 ES 索引 |
   | 管理端口 | POST | `/actuator/counter-reconcile` | 手动触发计数对账 |

   几处取舍：
   - 热榜用 `/hot-articles`，避免和 `/articles/{id}` 的路径产生歧义。
   - 回复通过 `/comments/{id}/replies` 发表，请求里不用再传 rootId。
   - 评论列表不内嵌回复，回复另行查询。
7. **复杂度评估**（用户关心这一点，这里记录下来）：
   - 约 30 个接口是薄薄的 CRUD，工作量主要集中在四个亮点、消息底座，以及每个亮点"优化前 / 优化后"两套可切换实现上。
   - 可删的外围功能都已列出：粉丝和关注列表、follow-states、单条已读、草稿列表合并、整个通知模块。用户决定全部保留。
