# 15: ES 搜索同步与查询

**What to build:** `search.mode=es` 下，中文搜索有分词、相关度排序、标签加权和高亮。文章发布、编辑、删除后，搜索结果在秒级内同步更新，而且旧数据永远不会覆盖新数据。应用启动时如果别名不存在，就自动建索引并全量导入。详见决策票 08。

**Blocked by:** 09, 10

Status: closed

- [x] **生产端事件**：article 发出 `article.updated`，发布和删除事件复用已有的（没有就补上）。编辑、发布、删除时 `version` 都会 +1。
- [x] **ES 客户端**：用 `elasticsearch-java`（Spring Boot 自动配置的 `ElasticsearchClient`）。
- [x] **索引结构**：真实索引 `article_v{n}` 加别名 `article`。写入用 ik_max_word 分词、查询用 ik_smart；字段按规格定义。
- [x] **同步消费者 `search.article-sync`**：用 `ArticleApi` 回查最新状态，已发布就写入 ES，否则从 ES 删除。写入和删除都带 `version_type=external`；返回 409 说明是旧版本，直接忽略。
- [x] **启动时建索引**：别名不存在时，新建索引，并通过 `ArticleApi` 按 id 游标分批全量导入。
- [x] **查询**：
  - `multi_match` 匹配 title^3、summary^1.5、content^1；关键词与某个标签名完全一致时额外加分。
  - 分类、标签作为 filter；排序支持 RELEVANCE 和 LATEST。
  - 高亮用 `<em>`，content 取 1 个约 100 字的片段。
  - 作者昵称通过 `UserApi` 补全。
- [x] **HTTP 矩阵**：两档搜到的是同一批文章。相关度排序、标签加权、高亮只在 es 档断言。发布、编辑、删除后，用 Awaitility 等待搜索结果变化。
- [x] **乱序测试**：模拟旧版本的数据在新版本之后写入，断言 ES 里保留的是新数据。

## Comments

- **事件**：`ArticlePublishedEvent`、`ArticleUpdatedEvent`、`ArticleDeletedEvent`（`article/api/event/`）都只带 `articleId`、`authorId`。发布只在草稿首次发布时发出；编辑、删除对草稿也发出，由消费者回查状态决定。删除改为带版本号的更新，乐观锁插件把 `version` +1。
- **ArticleApi**：`findSnapshot(id)` 返回正文、标签、状态、版本号，已删除的文章也返回（`deleted=true`），同步方据此拿到删除后的版本号；`listPublishedSnapshots(afterId, limit)` 按 ID 正序遍历已发布文章。
- **索引**：mapping 在 search 模块的 `search/article-index.json`，单分片、无副本、`dynamic: strict`。除规格字段外多存一个 `tagIds`：`tags` 存标签名（lowercase normalizer）用于加分，`tagIds` 用于按标签筛选。写入带 `require_alias`，别名不存在时不会误建名为 `article` 的索引。
- **同步**：`ArticleIndex.sync` 回查快照，`searchable()` 就写入，否则删除；写入和删除都带 `version_type=external`，409 忽略，删除不存在的文档不报错。队列 `search.article-sync` 两档都声明（article 事件发送要求有队列绑定），消费者只在 es 档装配；mysql-like 档下消息在队列里积压，切到 es 档后补上。
- **启动建索引**：`ArticleIndex` 实现 `SmartInitializingSingleton`，在 MQ 消费者启动前检查别名；不存在就新建 `article_v{n}`（n 取已有最大值 +1）并挂别名，再按 500 篇一批 bulk 导入，除 409 外的失败直接抛出，阻止启动。
- **查询**（`EsArticleSearcher`）：
  - `multi_match` best_fields，`operator=and`（分词后的每个词都要出现在同一字段），与基线的子串匹配更接近；`tags` 上 `term` 加权 5。
  - RELEVANCE 按 `_score`、发布时间、ID 倒序；LATEST 按发布时间、ID 倒序。
  - 高亮经 HTML 转义；title 整体高亮，content 用 plain 高亮器取 1 个 100 字片段（unified 按句切分，无标点的长句会整句返回）；未命中的字段为 null。
  - 只从 ES 取命中 ID 和高亮，文章摘要经 `ArticleApi.getBriefs` 回查，同步尚未跟上的已删除文章被滤掉（total 仍按 ES 计）。
- **测试**：`SearchApiTest` 两档共用，搜索结果用 `eventually` 等待，需要确定顺序时按 LATEST；`SearchEsApiTest`（`@EsSearch`）另测相关度、标签加权、高亮、中文分词，乱序测试直接调用 `ArticleIndex` 写入旧版本并读 ES 文档断言。
