# 搜索与数据同步

Type: grilling
Status: resolved
Blocked by: 03, 05

## Question

文章全文搜索如何实现：ES 索引 mapping（中文分词用 IK 插件，8.19.21 版本已确认可用：https://get.infini.cloud/elasticsearch/analysis-ik/8.19.21 ；分词器选择 ik_max_word / ik_smart）、搜索字段与权重、高亮、排序；MySQL → ES 的同步方式（基于消息可靠性底座的 Outbox + MQ，还是 Canal 订阅 binlog）、全量重建与增量同步、删除与更新的顺序问题？

## Answer

1. **同步方式：Outbox + MQ**，复用[消息可靠性底座](05-mq-reliability.md)。
   - article 模块发布三个事件：`article.published`、`article.updated`（本票新增）、`article.deleted`。
   - search 模块的消费者 `search.article-sync` 收到事件后，用 `ArticleApi` **回查最新状态**：已发布就整篇写入 ES；已删除或变回草稿就从 ES 删除。
   - 这一步本身幂等，所以不加 `@IdempotentConsumer`。
   - 不用 Canal，原因有三：要多部署一个中间件；search 会直接依赖 article 的表结构，违反 ADR-0001；还要在 binlog 层面把多张表拼装成一篇文章。
2. **乱序与并发写：用 ES 外部版本号**。
   - 回查最新状态已经解决了"事件乱序"。但还剩一种情况：消费者 A 读到旧版本，消费者 B 读到新版本并先写入，A 随后写入就会用旧数据覆盖新数据。
   - 解决方法：`article` 表增加 `version` 字段（MyBatis-Plus `@Version`），每次编辑、发布、删除都加 1。写入和删除 ES 时都带上 `version_type=external`；ES 返回 409 说明来的是旧版本，直接忽略，不重试。
   - 这个字段同时充当编辑文章时的乐观锁。
3. **Mapping**：
   - 真实索引名为 `article_v{n}`，业务代码只通过别名 `article` 访问。
   - 写入时用 `ik_max_word` 分词，查询时用 `ik_smart`。
   - 字段：

     | 字段 | 类型 |
     |---|---|
     | `id` | long |
     | `title`、`summary`、`content` | text；content 直接存 Markdown 原文，不做清洗 |
     | `tags`（标签名，小写归一化）、`tagIds`、`categoryId`、`authorId` | keyword |
     | `publishedAt` | date |

   - **不放进 ES 的数据**：
     - 作者昵称：可以修改，结果展示时通过 `UserApi` 批量补全。
     - 各类计数：变化太频繁，所以搜索不支持按热度排序。
4. **查询**：
   - 用 `multi_match` 匹配 `title`、`summary`、`content`，权重分别为 3、1.5、1；关键词与某个标签名完全一致时额外加分。
   - 分类和标签作为 `filter` 条件，不参与打分，可被 ES 缓存。
   - 排序支持两种：相关度（默认）和发布时间倒序。
   - 高亮：`title` 整体高亮；`content` 只取 1 个约 100 字的片段；高亮标签用 `<em>`。
   - 分页：页码分页，返回 `PageResult`，且 `from + size ≤ 1000`，避免深分页。`search_after` 只作为面试时可以提的扩展方案，不实现。
5. **全量重建**：
   - 启动时如果别名不存在，自动建索引并做一次全量导入。
   - 手动重建通过管理端口上的自定义 Actuator 端点 `POST /actuator/search-rebuild` 触发。
   - 重建流程：
     1. 新建 `article_v{n+1}`。
     2. 通过 `ArticleApi` 按 id 游标分批读取已发布文章，每批 500 篇，用 bulk 写入。
     3. 原子切换别名。
     4. 追补：把 `updated_at` 晚于重建开始时间的文章再写入一次。依赖外部版本号，重复写入不会出错。
     5. 删除旧索引。
6. **客户端与基线**：
   - 客户端直接用官方的 `elasticsearch-java`（Spring Boot 自动配置的 `ElasticsearchClient`），不用 Spring Data ES 的 Repository。
   - 配置项 `search.mode` 可以在 `mysql-like`（基线，`LIKE '%kw%'` 全表扫描，没有分词也没有相关度排序）和 `es` 之间切换。
   - 压测时在 10 万篇文章的数据量下，对比两种模式的延迟和结果质量。

**这张票对其他模块的接口要求**：
- article 模块发布 `article.updated` 事件。
- `ArticleApi` 提供按 id 游标遍历已发布文章的接口，以及按 `updated_at` 查询在某个时间点之后有变更的文章的接口。
