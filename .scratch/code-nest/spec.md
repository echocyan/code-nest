# 码巢 code-nest 项目规格

Status: open
Label: ready-for-agent

> 本规格汇编自 [map.md](map.md) 的 14 张决策票。每节的细节以对应票的 `## Answer` 为准；本文与票冲突时，以票为准并回头修正本文。术语以根目录
> `CONTEXT.md` 为准，模块边界遵守 ADR-0001。

## Problem Statement

一名软件工程专业的学生在找 Java 后端实习，简历上需要一个能经得起面试追问的项目。常见的"社区/商城"练手项目通常只有
CRUD，面试官一问"遇到了什么难点、怎么证明你的方案有效"就答不上来。学生需要的项目要满足四点：

- 业务不复杂，能讲清楚。
- 有几个真实存在的高并发、一致性问题，每个都能按 STAR 讲出来：S 场景，T 任务，A 方案与取舍，R 用压测数据对比优化前后。
- 工程上像样：模块边界清晰，有集成测试，本地一条命令就能拉起中间件。
- 规模可控，不追求企业级大而全。

## Solution

码巢是一个面向开发者的技术社区，只有后端，接口文档用 OpenAPI 提供。

**业务**：

- 用户注册登录，发布技术文章（草稿/已发布，一个分类，至多 5 个标签）。
- 文章下有两级评论（评论/回复）。
- 读者可以点赞、收藏文章，可以关注作者并阅读关注 Feed。
- 另有通知、全文搜索和热榜。

**技术亮点**：

- 四个主打亮点：
    - 计数系统：Redis 异步计数，替代热点行更新。
    - Feed 推拉结合。
    - 基于 Outbox 的 ES 搜索同步。
    - Caffeine + Redis 两级缓存与缓存治理。
- 三个配角：MQ 可靠性底座、热榜、限流。
- 每个主打亮点都保留"基线"与"优化"两套实现，用配置开关切换。
- 用 k6 在资源受限的 docker 环境中压测对比，结果按 STAR 写入 benchmark 文档，作为简历和面试的底稿。

**工程形态**：

- Maven 多模块的模块化单体，跨模块调用由 ArchUnit 强制约束。
- 中间件由 docker compose 管理，应用在 IDEA 里本地运行。
- 用 Testcontainers 做集成测试，不做 CI。

## User Stories

### 账号与认证

1. 作为访客，我希望用用户名和密码注册，注册后自动登录，这样可以马上开始使用。
2. 作为访客，我希望用户名只能用 4–20 位字母、数字、下划线，并且全站唯一，这样主页地址和 @ 提及不会冲突。
3. 作为访客，我希望密码为 8–32 位可打印 ASCII 字符，并以 BCrypt 存储，这样账号相对安全。
4. 作为用户，我希望用用户名和密码登录，拿到一个 7 天有效的 token，这样一周内不用反复登录。
5. 作为用户，我希望请求时用 `Authorization: Bearer <token>` 携带凭证，这样客户端的接入方式符合行业惯例。
6. 作为用户，我希望多台设备同时登录、各自持有 token，这样手机和电脑可以同时使用。
7. 作为用户，我希望退出登录只让当前设备失效，这样其他设备不受影响。
8. 作为未登录的访客，我希望访问需要登录的接口时收到 401 和明确的错误码，这样客户端知道该跳转登录页。
9. 作为用户，我希望查看和修改自己的昵称、头像 URL、简介，这样可以完善个人资料。
10. 作为用户，我希望用户名注册后不可修改，昵称默认等于用户名、之后可以修改，这样身份标识稳定。
11. 作为访客，我希望查看任意用户的主页，看到资料以及粉丝数、关注数、文章数、获赞总数，这样可以判断要不要关注他。

### 文章

12. 作为作者，我希望新建一篇草稿，填写标题、Markdown 正文、摘要、封面 URL、一个分类和至多 5 个标签，这样可以慢慢打磨再发布。
13. 作为作者，我希望不填摘要时，系统自动截取正文开头作为摘要，这样列表页总有内容可显示。
14. 作为作者，我希望分类和标签从系统预置列表里选，这样社区的内容组织保持整洁。
15. 作为访客，我希望获取全部分类和标签，这样可以按它们筛选文章。
16. 作为作者，我希望把草稿发布出去，发布后它进入粉丝 Feed、搜索和热榜，这样读者能看到。
17. 作为作者，我希望编辑文章时带上版本号，别处已经改过就提示冲突，这样两台设备同时编辑不会互相覆盖。
18. 作为作者，我希望发布后仍能编辑，编辑后搜索结果和详情页尽快更新，但不会再推送一次 Feed，这样粉丝不会被重复打扰。
19. 作为作者，我希望删除自己的文章，删除后它从详情、列表、Feed、搜索、热榜中消失，这样撤回的内容不再可见。
20. 作为作者，我希望只能编辑和删除自己的文章，这样别人不能篡改我的内容。
21. 作为读者，我希望查看文章详情，包括正文、作者信息，以及点赞、收藏、评论、浏览数，这样能完整阅读并看出它的受欢迎程度。
22. 作为作者，我希望自己的草稿只有自己能打开，其他人打开同一个 ID 得到 404，这样未完成的内容不会泄露。
23. 作为读者，我希望每次打开文章都计入浏览量，这样文章有大致的阅读热度。
24. 作为读者，我希望按分类或标签浏览最新发布的文章并按页翻阅，这样可以发现某个主题下的新内容。
25. 作为读者，我希望查看某位作者发布的全部文章，按时间倒序、游标翻页，这样可以追着一个作者读。
26. 作为作者，我希望查看我的草稿列表，这样能找到未完成的文章继续写。
27. 作为读者，我希望访问不存在的文章 ID 时快速得到 404，这样恶意枚举 ID 不会压垮数据库。

### 评论与回复

28. 作为读者，我希望对一篇已发布的文章发表评论，这样可以参与讨论。
29. 作为读者，我希望在某条评论下发表回复，并可以指定回复该评论下的某个人，这样讨论能有针对性。
30. 作为读者，我希望同一评论下的回复平铺展示，并标明"回复 @某人"，这样不会陷入无限嵌套。
31. 作为读者，我希望按游标翻阅一篇文章的评论，每条评论带回复数，这样能先看概况再展开。
32. 作为读者，我希望按游标翻阅某条评论下的回复，这样能展开查看完整讨论。
33. 作为评论者，我希望删除自己的评论或回复，这样可以撤回说错的话。
34. 作为读者，我希望一条评论被删除后，如果下面还有回复，就显示"该评论已删除"，回复继续保留，这样上下文不会断掉。
35. 作为作者，我希望文章的评论数和评论的回复数随评论增删而变化，这样数字真实可信。

### 点赞与收藏

36. 作为读者，我希望给文章点赞和取消点赞，而且同一篇文章我最多只能点赞一次，这样表达认可又不会被刷数。
37. 作为读者，我希望重复点赞或重复取消都返回成功且没有副作用，这样网络重试不会出错。
38. 作为读者，我希望收藏和取消收藏文章，放进我唯一的收藏列表，这样方便以后再读。
39. 作为读者，我希望按收藏时间倒序、游标翻阅我的收藏，这样能找到最近收藏的文章。
40. 作为读者，我希望批量查询一组文章里我是否点过赞、是否收藏过，这样列表页能正确显示按钮状态。
41. 作为作者，我希望获赞总数随读者的点赞和取消而变化，这样主页数据真实。
42. 作为热门文章的作者，我希望成百上千人同时点赞时，接口依然快速、计数最终准确，这样文章爆火时系统不会卡死。

### 关注与 Feed

43. 作为读者，我希望关注和取关作者，重复操作是幂等的，这样可以订阅感兴趣的人。
44. 作为访客，我希望查看某个用户的粉丝列表和关注列表，游标翻页，这样可以发现同好。
45. 作为读者，我希望批量查询我是否关注了一组用户，这样列表页能正确显示关注按钮。
46. 作为读者，我希望读关注 Feed，按时间倒序看到我关注的作者发布的文章，游标翻页，这样一处就能追更。
47. 作为关注了几百人的重度用户，我希望 Feed 首页依然很快，这样关注多不会变成负担。
48. 作为粉丝众多的大 V，我希望发文时系统不因为向几万粉丝写收件箱而变慢，这样发文体验不受粉丝数影响。
49. 作为读者，我希望刚关注一位作者后，他最近的文章马上出现在我的 Feed 里，这样关注立刻有反馈。
50. 作为读者，我希望取关后，那位作者的文章不再出现在我的 Feed 里，这样 Feed 只包含我关心的人。
51. 作为读者，我希望已删除的文章不出现在 Feed 里，某一页因此少于 size 条也可以接受，这样不会点进 404。
52. 作为 7 天以上没来的回归用户，我希望打开 Feed 时依然能看到最新文章，这样回来的体验不受影响。

### 通知

53. 作为作者，我希望文章被点赞、被评论时收到通知，这样知道有人在关注我的内容。
54. 作为评论者，我希望有人回复我时收到通知，这样能及时回应。
55. 作为作者，我希望被人关注时收到通知，这样知道多了一位粉丝。
56. 作为用户，我希望自己给自己点赞、回复自己时不产生通知，这样通知列表没有噪音。
57. 作为用户，我希望某人反复点赞又取消、反复关注又取关，我都只收到一条通知，这样不会被刷屏。
58.
作为用户，我希望按时间倒序、游标翻阅通知，看到触发者的昵称和头像、文章标题或评论摘要；内容已删除时显示"该内容已删除"，这样通知有足够的上下文。
59. 作为用户，我希望看到未读通知数，超过 99 显示 99+，这样知道有没有新消息。
60. 作为用户，我希望把单条或全部通知标为已读，打开列表本身不会自动标记已读，这样由我自己控制已读状态。

### 搜索

61. 作为读者，我希望用中文关键词搜索文章，匹配标题、摘要、正文，标题命中的排得更靠前，这样能找到最相关的内容。
62. 作为读者，我希望关键词正好是某个标签名时，打了这个标签的文章排得更靠前，这样主题搜索更准确。
63. 作为读者，我希望按分类、标签筛选搜索结果，并在相关度排序和最新发布之间切换，这样可以缩小范围。
64. 作为读者，我希望搜索结果里的命中词被高亮，并附带正文片段，这样一眼看出为什么命中。
65. 作为读者，我希望文章发布、编辑、删除后，搜索结果在秒级内反映变化，这样搜到的内容不会过时。
66. 作为读者，我希望搜索结果里的作者昵称永远是最新的，这样作者改名后不会显示旧名字。
67. 作为读者，我希望搜索最多翻到第 50 页，更深的请求会被拒绝并给出明确错误，这样系统不被深分页拖垮。

### 热榜

68. 作为读者，我希望看到一个综合点赞、收藏、评论、浏览和发布时间的热榜，每页 20 条、最多 5 页，这样能发现近期最受欢迎的文章。
69. 作为读者，我希望热榜大约每 5 分钟更新一次，旧文章随时间自然下沉，这样榜单有新鲜感。
70. 作为读者，我希望已删除的文章不出现在热榜上，这样不会点进 404。

### 限流防刷

71. 作为站点运营者，我希望同一 IP 每小时最多注册 5 次、每分钟最多登录 10 次，这样可以抵御批量注册和撞库。
72. 作为站点运营者，我希望每个用户每小时最多发 10 篇文章，评论和回复每分钟最多 10 条、每天最多 200 条，这样可以抑制灌水。
73. 作为站点运营者，我希望点赞、收藏每分钟最多 60 次，关注、取关每分钟最多 30 次，匿名搜索每个 IP 每分钟最多 60
    次，这样可以防止脚本刷接口。
74. 作为客户端开发者，我希望超限时收到 429 和 `Retry-After` 响应头，这样可以提示用户何时重试。
75. 作为站点运营者，我希望 Redis 故障时限流自动放行并打告警日志，这样限流组件不会拖垮主业务。
76. 作为站点运营者，我希望限流额度可配置，并有一个总开关，这样压测时可以关闭。
77. 作为站点运营者，我希望只在请求来自可信代理时才信任 `X-Forwarded-For`，这样用户无法伪造 IP 绕过限流。

### 运维与可靠性

78. 作为运维者，我希望业务写入成功后，跨模块的副作用（计数、搜索同步、Feed 推送、通知、缓存二次删除）即使在 MQ
    短暂不可用时也最终会执行，这样数据不会悄悄丢失。
79. 作为运维者，我希望重复投递的消息不会造成重复通知或重复计数，这样"至少一次"投递是安全的。
80. 作为运维者，我希望消费反复失败的消息进入死信队列并打告警日志，而不是无限重试，这样坏消息不会阻塞队列。
81. 作为运维者，我希望通过管理端点手动触发 ES 全量重建，重建期间搜索不中断，这样可以安全地调整 mapping。
82. 作为运维者，我希望通过管理端点手动触发计数对账，并且每周自动执行一次，这样 Redis 和 MySQL 的计数偏差会被修正。
83. 作为运维者，我希望 Redis 数据丢失后，计数能从 MySQL 懒加载恢复，布隆过滤器在启动时重建，Feed 收件箱在首次读取时重建，这样缓存层是可重建的。
84. 作为运维者，我希望应用启动时发现搜索别名不存在，就自动建索引并全量导入，这样新环境开箱即用。
85. 作为运维者，我希望多实例部署时，热榜计算、Outbox 补发、计数落库不会被重复执行或互相冲突，这样可以水平扩展。

### 开发者与求职者

86. 作为开发者，我希望一条命令就拉起 MySQL、Redis、RabbitMQ 和带 IK 分词的 ES，然后在 IDEA 里直接运行应用，这样本地开发没有门槛。
87. 作为开发者，我希望运行测试时自动用 Testcontainers 启动全部中间件，而且所有测试类共享同一组容器，这样集成测试可靠且不太慢。
88. 作为开发者，我希望有一条 ArchUnit 测试，模块之间越过 `api` 包调用或出现依赖环时构建失败，这样模块边界不会被侵蚀。
89. 作为前端或面试官，我希望通过 OpenAPI 页面看到全部接口和字段说明，这样不看代码也能理解和试用系统。
90. 作为求职者，我希望每个主打亮点都能用配置切换"基线"和"优化"两套实现，这样可以在同一环境下做公平对比。
91. 作为求职者，我希望一条命令就能造出 10 万用户、10 万篇文章、约 500 万条关注关系，造数结果可以复现，这样压测数据有说服力。
92. 作为求职者，我希望用 2 个应用实例加 Nginx、在资源受限的容器里跑 k6 压测，每组 3 次取中位数，并采集服务端状态差值，这样结论是可信的。
93. 作为求职者，我希望得到一份按 STAR 组织的 benchmark 文档，每个亮点都有场景、任务、方案取舍和前后数据，这样可以直接拿来写简历、准备面试。

## Implementation Decisions

### 工程结构（见[工程结构与测试基础设施](issues/02-project-structure.md)、ADR-0001）

- **模块划分**：
    - 父 pom 统一管理依赖版本。
    - `code-nest-common`：纯 Java，放统一返回体、`ErrorCode` 接口与业务异常、`PageResult`、
      `CursorResult{list, nextCursor, hasMore}`、工具类。
    - `code-nest-framework`：基础设施，包括 MyBatis-Plus、Redis、MQ 可靠性底座、ES 客户端、Sa-Token、`AuthContext`、
      `TwoLevelCache`、`@RateLimit`、全局异常处理、Jackson 配置。
    - `code-nest-modules`：聚合 pom，下设 user、article、counter、interaction、social、notification、search 七个业务模块。
    - `code-nest-app`：启动类、配置、端到端集成测试。
    - `code-nest-loadtest`：造数程序、k6 脚本和结果，不打进应用 jar，也不受 ADR-0001 约束。
- **业务模块依赖**（单向无环，全部依赖 framework 和 common）：
    - user、counter 不依赖其他业务模块
    - article → user、counter
    - interaction → article、counter
    - social → user、article、counter
    - notification → user、article
    - search → article、user
- **跨模块调用**：只能调用对方 `api` 包里的门面接口和 DTO。不联表；需要别的模块的数据时，由服务层批量调用对方的 API
  后组装。对外发布的事件类放在 `api/event/`。
- **模块内分包**：`api/`（含 `api/event/`）、`controller/`、`service/`、`mapper/`、`entity/`、`dto/`、`vo/`、`convert/`。
- **依赖选型**：
    - MyBatis-Plus 用 `mybatis-plus-spring-boot4-starter`。
    - Sa-Token 用 `sa-token-spring-boot4-starter`，存储用 `sa-token-redis-template` 加 `commons-pool2`，避开会引入 Jackson
      2 的 `sa-token-redis-jackson`。
    - API 文档用 SpringDoc。
    - 对象转换用 MapStruct，配好它与 Lombok 注解处理器的先后顺序。
    - 密码哈希只引入 `spring-security-crypto`。
- **中间件版本**：以用户本地镜像为准，即 `mysql:8.4`、`redis:8.6`、`rabbitmq:4.3.5-management`、`elasticsearch:9.4.5`（原定 8.19.21，因 Boot 4 管理的 ES 客户端为 9.x 而统一升级）。ES
  在官方镜像上安装 IK 9.4.5 插件。需要新增镜像或插件时先问用户。已同意的镜像有 `grafana/k6`、`eclipse-temurin:21-jre`、
  `nginx`。
- **本地环境**：根目录的 compose 文件管理中间件，由 `spring-boot-docker-compose` 以 `start-only` 模式拉起并注入连接信息。

### 通用约定（见[API 设计规范](issues/14-api-conventions.md)）

- **路径**：
    - 接口统一挂在 `/api/v1` 下，Actuator 端点除外。
    - 资源名用复数、kebab-case；只对有明确从属关系的资源使用嵌套路径。
    - 当前用户用 `me` 表示。
- **开关型动作**：点赞、收藏、关注都用 `PUT` 开启、`DELETE` 关闭，两者都幂等。只有数据库里真的插入或删除了一行，才上报计数、发出事件。
- **返回体**：统一为 `{code, message, data}`，同时按语义设置 HTTP 状态码（200/400/401/403/404/409/429/500）。
    - 版本冲突返回 409，错误码归 article 号段。
- **JSON**：
    - 字段用 camelCase；时间用带偏移的 ISO-8601（Asia/Shanghai）。
    - Long 型 ID 全局序列化为字符串；枚举输出大写字符串。
    - 字段为空时返回 `null`，不省略该字段。
- **分页参数**：
    - 页码分页用 `page`（从 1 开始）加 `size`；游标分页用 `cursor` 加 `size`。
    - `size` 默认 20，最大 50。
    - 各接口的翻页上限：搜索 `from + size ≤ 1000`；热榜 5 页；Feed 约 500 条。
- **模块编号**：同时决定错误码号段和 Flyway 版本前缀：

  | 编号 | 模块 |
    |---|---|
  | 0 | framework（0 表示成功） |
  | 1 | user |
  | 2 | article |
  | 3 | interaction |
  | 4 | social |
  | 5 | notification |
  | 6 | search（无表） |
  | 7 | counter |
  | 9 | 通用 |

  通用错误码：90400 参数错误、90401 未登录、90403 无权限、90404 不存在、90429 请求过于频繁、99999 系统错误。
- **ID 与建表**：
    - 主键为 MyBatis-Plus `ASSIGN_ID` 雪花 ID；计数表例外，以业务 ID 为主键。
    - 不建物理外键；字符集 utf8mb4，引擎 InnoDB，时间字段 `DATETIME`。
    - 所有表都有 `created_at`、`updated_at`，自动填充。
    - 只有内容类表（文章、评论）有 `deleted` 字段，用 `@TableLogic` 软删除；关系类表取消时物理删除。
- **Flyway**：每个模块在自己的资源目录下放迁移脚本；分类和标签的种子数据也用 Flyway 写入。

### 数据模型（见[领域与数据模型](issues/03-domain-data-model.md)，完整字段与索引以票为准）

- **user 模块**：`user`。
- **article 模块**：
    - `category`、`tag`、`article`（含 `version` 字段）、`article_content`（正文与主表垂直拆分）。
    - `article_tag`：主键 (article_id, tag_id)，外加反向索引。
    - `comment`：用 `root_id = 0` 区分评论和回复，回复带 `reply_to_user_id`。
- **counter 模块**：`article_stat`、`user_stat`、`comment_stat`。
- **interaction 模块**：`article_like`、`favorite`，两张表都有 (user_id, article_id) 唯一键。
- **social 模块**：`follow`，唯一键 (follower_id, author_id)，另建索引 (author_id, follower_id)。
- **notification 模块**：`notification`。
    - `dedup_key` 可为空，建唯一索引。
    - 列表查询用 IDX (recipient_id, id)，未读数用 IDX (recipient_id, is_read)。
- **framework 模块**：`mq_outbox`、`mq_consume_record`。

### 认证与鉴权（见[认证与鉴权方案](issues/04-auth.md)、Sa-Token 调研笔记）

- **token 格式**：通过 Sa-Token 配置改为 `Authorization: Bearer <uuid>`。
    - token 有效期固定 7 天，没有活跃超时；允许多端登录，不共享 token。
    - 不从 cookie 或 body 读取 token。
- **Session**：只存 loginId；不做角色，不实现 `StpInterface`。
- **鉴权**：注册 `SaInterceptor`，所有接口默认要求登录，公开接口标 `@SaIgnore`。
    - 公开接口：文章详情和列表、分类和标签、作者文章、评论和回复列表、热榜、搜索、用户主页、粉丝和关注列表、注册、登录。
    - 资源归属（只能改删自己的内容）在业务代码里检查。
- **取当前用户**：`AuthContext.currentUserId()` 和 `currentUserIdOrNull()` 只在 Controller 调用，userId 作为参数传入
  Service。MQ 消费者从消息体里取 userId。
- **Sa-Token 使用注意**：
    - loginId 里不能出现冒号。
    - MockMvc 测试需要挂上 Sa-Token 的上下文 Filter。

### 消息可靠性底座（见[消息可靠性底座](issues/05-mq-reliability.md)）

- **使用范围**：模块之间的异步副作用一律走 RabbitMQ；Spring `ApplicationEvent` 只在模块内部使用。
- **生产端接口**：只有一个方法 `DomainEventPublisher.publish(event)`。
    - 当前有活跃事务时：写入 `mq_outbox`，在 afterCommit 中发送，收到 publisher confirm 后标记为 SENT。
    - 没有事务时：直接发送，靠 confirm 加重试保证送达。
    - 这是隐式行为，要在 Javadoc 里写明。
    - 事件类用 `@DomainEvent("<module>.<event>")` 声明路由键。
- **补发**：定时任务用 `SELECT … FOR UPDATE SKIP LOCKED` 扫描到期的 PENDING 记录，按指数退避补发。
    - 累计失败 10 次标记为 FAILED 并告警。
    - SENT 记录保留 7 天。
- **消费端幂等**：会写 MySQL 的消费者加 `@IdempotentConsumer`，在同一个事务里插入
  `mq_consume_record(message_id, consumer)` 唯一键并执行业务逻辑；唯一键冲突就跳过。本身幂等的消费者不加这个注解。
- **重试与死信**：本地重试 3 次，间隔 1s、2s、4s；之后转入 `codenest.dlx`，路由到 `<queue>.dlq`，打告警日志，不自动重放。
- **拓扑**：只有一个 topic 交换机 `codenest.events`。
    - 路由键格式 `<生产模块>.<事件>`，队列名格式 `<消费模块>.<用途>`。
    - 全部持久化，队列用 classic 类型。
- **消息格式**：messageId 放在 AMQP `message_id` 属性，事件类型放在 `type` 属性，body 是事件记录类的 JSON。
    - 事件只带 ID 和少量常用字段；需要最新完整数据时，消费者回查对方的 API。
- **事件与队列清单**：
    - 业务事件：
        - `article.published`、`article.updated`、`article.deleted`
        - `comment.created`（带 commentId、articleId、userId、rootId、replyToUserId、articleAuthorId）
        - `like.created`（带 articleId、userId、authorId）
        - `follow.created`（带 followerId、authorId）、`follow.deleted`
        - counter 模块内部使用的计数变更事件
    - 队列：
        - `search.article-sync`
        - `social.feed-push`，以及处理关注、取关、删文修正的队列
        - `article.cache-evict`
        - `notification.create`
        - counter 的计数消费队列

### 计数系统（主打亮点 A，见[计数系统](issues/06-counter-system.md)）

- **要解决的问题**：瓶颈不在点赞关系行，而在热点计数行的 `UPDATE … +1` 排队等行锁。
- **点赞关系**：同步写 `article_like`，由唯一索引保证每个用户对同一篇文章至多点赞一次。
- **`CounterApi`**：只有三个方法：
    - `increment(metric, targetId, delta)`：在调用方事务里经 Outbox 发出计数事件。
    - `get`：批量读取。
    - `reset(metric, id, value)`：对账修正。
- **指标与上报方**：
    - 文章：点赞、收藏、评论、浏览。
    - 用户：粉丝、关注、文章、获赞。
    - 评论：回复。
    - 评论数、回复数、文章数由 article 上报；获赞数由 interaction 上报。
- **浏览量**：近似计数，请求内直接执行 `HINCRBY` 并标记为待落库，不走 MQ，也不对账。
- **`counter.mode` 开关**：
    - `sync-db`（基线）：在调用方事务里直接更新计数行，读取直接读 MySQL。
    - `redis-async`（优化）：
        - Redis 结构：每个对象一个 Hash（`counter:{article|user|comment}:{id}`），不设 TTL；Redis 开启 AOF everysec。
        - 消费端用一段 Lua 原子完成：Hash 不存在就返回 MISS → 用 `SET NX EX 86400` 按 messageId 去重 → `HINCRBY`（结果不低于
          0）→ 把对象 ID 加入待落库集合。
        - 收到 MISS 时从 MySQL 读出计数，仅在 key 仍不存在时回填，然后重跑脚本。
        - 落库：每 5 秒用 `SPOP` 取出最多 1000 个待落库 ID，pipeline 读出 Hash，用批量 `INSERT … ON DUPLICATE KEY UPDATE`
          写入绝对值。
        - 读取：pipeline 批量 `HMGET`，缺失的从 MySQL 回填。
- **对账**：谁掌握真实数据就由谁对账：
    - 点赞、收藏、获赞由 interaction 负责。
    - 评论、回复、文章数由 article 负责。
    - 粉丝、关注由 social 负责。
    - 方式是分页 `GROUP BY` 重新统计，再调用 `reset`。
    - 每周定时执行，也可以通过 `POST /actuator/counter-reconcile` 手动触发。
- **已知缺陷**（写进文档）：`SPOP` 之后、写库之前实例崩溃会丢失待落库标记；对账与并发写入之间可能有 ±1 的误差。

### Feed 推拉结合（主打亮点 B，见[Feed 推拉结合](issues/07-feed.md)）

- **大 V 判定**：粉丝数 ≥ `feed.big-author-threshold`（默认 5000，从 `CounterApi` 读取）。作者跨过阈值时不迁移历史数据。
- **Redis 结构**：
    - 每个作者一个发件箱 ZSet（最近 100 篇）；每个读者一个收件箱 ZSet（上限 500 条，TTL 7 天，读取时续期）。
    - member 和 score 都用雪花 articleId，articleId 同时是分页游标。
- **推送**：`social.feed-push` 消费 `article.published`。
    - 先写作者的发件箱；作者是大 V 就到此为止。
    - 普通作者：按粉丝索引每页取 1000 个粉丝，只给收件箱 key 仍存在的粉丝执行 pipeline `ZADD` 并裁剪到上限。
- **读取流程**：
    1. 用覆盖索引查出我关注的作者。
    2. 用 `CounterApi` 识别其中的大 V。
    3. 读取收件箱；不存在就用普通作者的发件箱重建。
    4. 拉取各大 V 的发件箱。
    5. 合并、去重、倒序取 size 条。
    6. 用 `ArticleApi` 批量获取摘要，过滤已删除或非发布状态的文章，以及已取关作者的文章。
    7. 补全作者信息和计数。
- **修正**：
    - 关注：把对方（普通作者）的发件箱合并进我的收件箱。
    - 取关：按对方的发件箱，从我的收件箱里尽量移除他的文章。
    - 删文：从作者的发件箱中移除该文章。
    - 其余情况都靠读取时过滤兜底。
- **`feed.mode` 开关**：`pull`（基线，调用 `ArticleApi.listByAuthors`，执行 `IN` 查询）/ `push-pull`（优化）。
- **关注列表缓存**：先不做。压测后如果关注列表查询占 Feed 读取耗时的 30% 以上，再加 Redis Set 缓存。

### 搜索与同步（主打亮点 C，见[搜索与数据同步](issues/08-search-sync.md)）

- **同步**：`search.article-sync` 订阅三个文章事件，用 `ArticleApi` 回查最新状态：已发布就整篇写入 ES，已删除或草稿就从 ES
  删除。
    - 写入和删除都以 `article.version` 作为外部版本号（`version_type=external`）；ES 返回 409 说明是旧版本，直接忽略。
    - 这一步本身幂等，不加 `@IdempotentConsumer`。
    - 不用 Canal。
- **索引**：真实索引名 `article_v{n}`，业务代码只通过别名 `article` 访问。写入用 ik_max_word，查询用 ik_smart。
    - 字段：id、title、summary、content（Markdown 原文）、tags/categoryId/authorId（keyword）、publishedAt。
    - 作者昵称和计数不进 ES。
- **查询**：
    - `multi_match` 匹配 title^3、summary^1.5、content^1；关键词与标签名完全一致时额外加分。
    - 分类、标签作为 filter 条件；排序支持 RELEVANCE / LATEST。
    - 高亮用 `<em>`，content 只取 1 个约 100 字的片段。
    - 页码分页，`from + size ≤ 1000`。
- **重建**：
    - 启动时如果别名不存在，自动建索引并全量导入。
    - 也可以通过 `POST /actuator/search-rebuild` 手动触发：新建下一版本索引 → 按 id 游标每批 500 篇 bulk 写入 →
      原子切换别名 → 用 `updated_at` 追补重建期间的变更 → 删除旧索引。
- **客户端**：直接用 `elasticsearch-java`，不用 Spring Data Repository。
- **`search.mode` 开关**：`mysql-like`（基线）/ `es`（优化）。

### 多级缓存（主打亮点 D，见[多级缓存与缓存治理](issues/09-multilevel-cache.md)）

- **缓存范围**：
    - 文章详情（元数据加正文，不含计数）：两级缓存。
    - `UserApi` 和 `ArticleApi` 的批量摘要查询：只用 Redis，批量读取走 `MGET`。
    - 关注列表、"是否点赞/收藏/关注"：不缓存。
- **`TwoLevelCache`**：只有三个方法：`get(key, loader)`、`getAll(keys, batchLoader)`、`evict(key)`。两级读取、空值缓存、布隆过滤器、TTL
  抖动、失效广播都封装在组件内部。不用 `@Cacheable`。
- **一致性（Cache-Aside）**：
    - 先更新数据库，事务提交后删除 Redis 中的 key，并通过 Pub/Sub 频道 `cache:invalidate` 广播本地缓存失效。
    - `article.cache-evict` 消费更新和删除事件，做第二次删除。
    - 残留的竞态窗口写进文档。
- **过期与容量**：
    - Caffeine：最多 10000 条，写入 60 秒后过期。
    - Redis：TTL 30 分钟，加 0–5 分钟随机抖动。
- **防穿透**：Redis 8 原生布隆过滤器 `bf:article`（文章创建时加入，启动时如果不存在就重建），加空值缓存 60 秒。
- **防击穿**：依靠 Caffeine 的同 key 合并加载，不加分布式锁。
- **热点 key**：交给 W-TinyLFU 淘汰策略，不做专门探测。
- **`cache.mode` 开关**：`none` / `redis` / `two-level`。

### 热榜（配角，见[热榜](issues/10-hot-list.md)）

- **归属**：article 模块。
- **热度公式**：`hot = (3·点赞 + 5·收藏 + 4·评论 + 0.1·浏览) / (发布小时数 + 2)^1.5`，各权重和重力系数都可配置。
- **计算**：
    - 每 5 分钟执行一次，用 `SET hot:lock NX EX 240` 保证只有一个实例在算。
    - 候选集是最近 7 天发布的文章，以每批 500 个调用 `CounterApi` 取计数。
    - 取前 100 名写入临时 key，再用 `RENAME` 原子替换正式榜单。
- **读取**：每页 20 条、最多 5 页；已删除的文章在读取时过滤。
- **压测**：不做对比。

### 限流（配角，见[限流防刷](issues/11-rate-limit.md)）

- **算法**：滑动窗口日志，用 ZSet 加 Lua 原子执行；时间取 Redis `TIME`。
- **维度**：需要登录的接口按用户 ID；匿名接口按 IP，只对可信代理解析 `X-Forwarded-For`。
- **声明方式**：可重复标注的 `@RateLimit(key, limit, window, dimension)`，由注册在 `SaInterceptor` 之后的
  `HandlerInterceptor` 执行。
- **默认限额**：见用户故事 71–73，全部可配置。
- **开关**：`rate-limit.enabled`。
- **响应与降级**：超限返回 429、错误码 90429，并带 `Retry-After`；Redis 故障时放行（fail-open）。

### 通知（见[通知模块](issues/13-notification.md)）

- **来源**：`notification.create` 订阅 like.created、comment.created、follow.created，加 `@IdempotentConsumer`。
    - 接收者：点赞通知文章作者；评论通知文章作者；回复通知被回复的人；关注通知被关注者。
    - 触发者和接收者是同一人时不通知。
- **去重**：点赞用 `L:{actor}:{article}`、关注用 `F:{actor}:{author}` 作为 dedup_key，配合 `INSERT IGNORE`；评论和回复的
  dedup_key 为 NULL。
    - 取消操作不撤回通知。
- **展示**：表里只存 ID，读取时组装：`UserApi` 提供触发者信息，`ArticleApi` 提供文章标题和批量评论摘要（本票新增的接口要求）。
- **未读数**：`COUNT` 最多数到 100，前端显示 99+。
- **已读**：单条或全部，显式标记。

### 各模块 `api` 门面需要提供的能力（汇总自各票）

- **UserApi**：批量查询用户简要信息（走 Redis 缓存）；判断用户是否存在。
- **ArticleApi**：
    - 判断文章是否存在、查询状态与作者。
    - 批量查询文章摘要（走 Redis 缓存）。
    - `listByAuthors(authorIds, cursor, limit)`。
    - 按 id 游标遍历已发布文章；查询 `updated_at` 晚于某时间点的文章。
    - 查询最近 7 天发布的文章 ID。
    - 查询某篇文章的完整索引数据。
    - 批量查询评论摘要。
- **CounterApi**：`increment` / `get` / `reset`。
- interaction、social、notification、search 不对外提供门面，除非实现中发现确有需要。

### 接口清单

共 39 个业务接口和 2 个管理端点，完整表格见[API 设计规范](issues/14-api-conventions.md)。分布如下：

- **user**：注册、登录、登出、查看和修改我的资料、用户主页。
- **article**：
    - 分类、标签。
    - 新建草稿、编辑、发布、删除、详情。
    - 最新文章列表、作者文章、我的草稿。
    - 评论列表、发表评论，回复列表、发表回复，删除评论或回复。
    - 热榜。
- **interaction**：点赞和取消、收藏和取消、我的收藏、批量查询点赞和收藏状态。
- **social**：关注和取关、粉丝列表、关注列表、批量查询关注状态、Feed。
- **notification**：通知列表、未读数、单条已读、全部已读。
- **search**：搜索文章。
- **管理端口**：`search-rebuild`、`counter-reconcile`。

### 压测（见[压测方案](issues/12-load-test.md)）

- **环境**：
    - 在本地 compose 之上叠加一份压测 compose：2 个应用实例（多阶段 Dockerfile 构建，运行时镜像 `eclipse-temurin:21-jre`
      ），前面由 Nginx 做负载均衡。
    - 各容器限定 CPU 和内存：

      | 服务 | 资源上限 |
          |---|---|
      | 应用（每个实例） | 2 核 / 1.5G |
      | MySQL | 2 核 / 2G |
      | ES | 2 核 / 2G |
      | Redis | 1 核 / 1G |
      | RabbitMQ | 1 核 / 1G |
      | Nginx | 1 核 |

    - 各模式开关通过环境变量注入；压测时关闭限流。
- **造数**：loadtest 模块里的 JDBC 造数程序，使用固定随机种子。
    - 规模：10 万用户、10 万篇文章、约 500 万条关注关系；10 个大 V 各有 2 万粉丝；100 个重度用户各关注 500 人。
    - 派生数据走系统自带的重建路径生成：计数执行对账，ES 调用重建端点，布隆过滤器在启动时重建，收件箱在首次读取时懒重建。
- **场景**：
    - A 计数：热门文章被集中点赞。
    - B Feed：重度用户读 Feed，并测量普通作者发文后推送完成的耗时。
    - C 搜索：用随机关键词搜索。
    - D 缓存：访问文章详情，请求按 Zipf 分布集中在少数文章上。
- **执行与采集**：每组预热 30 秒，稳态压测 2 分钟，跑 3 次取中位数。压测前后各采集一次 `SHOW GLOBAL STATUS` 和
  `INFO commandstats`，取差值。
- **结果**：原始结果存 JSON；汇总写入 benchmark 文档，每个亮点一节 S/T/A/R，并注明"单机压测，关注相对提升"。

## Testing Decisions

- **好测试的标准**：只断言外部可观察的行为，也就是接口的返回值、状态码、错误码，以及异步副作用最终在接口上呈现的结果。不断言表结构、Redis
  key、内部类和调用次数。模块内部重构时，测试不需要修改。
- **主要入口：HTTP 接口**。
    - 在 `code-nest-app` 里用 Testcontainers 启动 MySQL、Redis、RabbitMQ 和带 IK 的 ES，通过 `@ServiceConnection`
      注入连接信息，所有测试类共享同一组容器（singleton）。
    - 测试基类提供 `loginAs(user)`：调用登录接口拿到 token，后续请求自动带上 Bearer 头。
    - 41 个接口都走真实 HTTP 测试，覆盖正常路径、权限（401/403/404）、参数校验、幂等（重复 PUT/DELETE）、限流（429 和
      `Retry-After`）。
    - 异步结果用 Awaitility 反复调接口直到出现，例如：点赞后计数变化、发布后可以搜到、发文后出现在粉丝的 Feed
      里、收到通知、编辑后详情页更新。
    - 各模块的 `XxxApi` 不单独测试。
- **开关矩阵**：同一批 HTTP 测试在每组开关的每一档下都跑一遍，用参数化或 profile 切换：
    - `counter.mode`：sync-db / redis-async。
    - `feed.mode`：pull / push-pull。
    - `cache.mode`：none / redis / two-level。
    - `search.mode`：mysql-like / es。两档都要求搜到的是同一批文章；相关度排序、标签加权和高亮只在 `es` 下断言。
    - 目的是证明基线和优化两套实现对外表现一致，压测对比才成立。
- **只有以下几类行为在各自的公开接口上单独测**，因为从 HTTP 层看不到：
    1. **`DomainEventPublisher` 与 `@IdempotentConsumer`**：
        - 有事务和无事务两条发送路径。
        - 事务回滚后不发消息。
        - Broker 不可用期间写入的消息，恢复后由补发任务送达。
        - 同一 messageId 只处理一次。
        - 重试耗尽后进入死信队列。
    2. **计数的 Redis 落库与对账**：通过 `CounterApi` 触发落库，断言最终值等于关系表 `COUNT(*)`；覆盖 Redis 被清空后的懒加载恢复，以及
       `reset` 修正。
    3. **`TwoLevelCache`**：跨两个实例的 Pub/Sub 失效广播、空值缓存、布隆过滤器拦截、并发请求同一个 key
       只加载一次。单个应用上下文无法模拟两个实例，所以单独测。
    4. **纯逻辑单元测试**（普通 JUnit）：热度公式、滑动窗口的时间边界。
    5. **ArchUnit**：跨模块只能调用 `api` 包，模块依赖无环。
- **代码库现状**：还没有任何测试；上述测试基类和容器配置是第一批要建立的基础设施，后续测试都以它为范例。
- **压测不属于自动化测试**：压测由 k6 手动执行，结果写入 benchmark 文档。场景 A 结束后，另外断言"计数等于 `COUNT(*)`
  "，作为正确性校验。

## Out of Scope

以下内容不在范围内，各项理由见 [map.md](map.md) 的 Out of scope 一节：

- 前端页面。
- 微服务 / Spring Cloud。
- CI 与云部署。
- 实时推送（SSE/WebSocket）。
- 签到、积分、UV 统计。
- 后台管理与审核。
- 邮箱验证、短信登录、第三方 OAuth、refresh token、角色体系、登录失败锁定账号。
- 图片上传与对象存储、注销账号、评论点赞、收藏夹。
- 自建号段发号器。
- Canal 订阅 binlog、搜索建议与自动补全、拼音搜索、搜索按热度排序、`search_after` 深分页。
- 自建热点 key 探测、用分布式锁防缓存击穿。
- 多个榜单（日榜、周榜、总榜、分类榜）、按事件实时更新热度。
- 全局接口总限流。
- Prometheus/Grafana 监控栈。
- 通知聚合、按类型分 Tab、通知定期清理。

## Further Notes

- **兼容性**：不做逐项兼容性调研，兼容问题在实现中暴露后再处理。已知风险有：Boot 4.1.1 与 Sa-Token 1.46、MyBatis-Plus boot4
  starter 的兼容性；Jackson 3 与 Sa-Token 的兼容性。
- **语言**：代码标识符与 commit message 用英文；注释用中文，保持克制；文档、票、ADR 用中文。
- **建议的实现顺序**（供 to-tickets 参考）：
    1. 工程骨架与测试基础设施（多模块、compose、Testcontainers、ArchUnit）。
    2. common/framework 基础（返回体、异常、Jackson、Sa-Token、MyBatis-Plus）。
    3. user 模块与认证。
    4. article 的 CRUD 与评论。
    5. MQ 可靠性底座。
    6. counter，以及 interaction、social 的关系部分。
    7. 依次实现四个亮点的优化版。
    8. 热榜、限流、通知。
    9. loadtest 造数与 k6 脚本，写 benchmark 文档。
- 每个亮点先实现基线再实现优化版，两者都要通过同一套 HTTP 测试。
- 文档里需要如实写明的已知缺陷：计数在 SPOP 与写库之间的窗口；对账的 ±1 误差；Cache-Aside 残留的竞态；Pub/Sub 漏收时靠 60 秒
  TTL 兜底；Feed 某一页可能少于 size 条。
