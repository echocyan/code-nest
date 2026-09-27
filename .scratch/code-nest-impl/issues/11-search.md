# 11: 搜索与 ES 同步

**What to build:** 访客可以按关键词搜索已发布的文章：中文分词、相关度排序、标签加权和高亮，支持按分类、标签筛选，按相关度或最新发布排序，页码分页，最多翻到第 50 页。文章发布、编辑、删除后，搜索结果在秒级内同步更新，而且旧数据永远不会覆盖新数据。应用启动时如果别名不存在，就自动建索引并全量导入；导入中断或多个实例同时启动都不会留下残缺的索引。详见决策票 08。

**Blocked by:** 03, 05

Status: closed

- [x] **search 模块**：新增模块，定义 6xxxx 错误码；不建表。
- [x] **`GET /search/articles?q=&categoryId=&tagId=&sort=RELEVANCE|LATEST&page=&size=`**：匿名可访问，返回 `PageResult`。
  - 结果项是文章列表项加高亮。
  - `q` 为空时返回 400；`from + size > 1000` 时返回 400 和明确的错误码。
- [x] **生产端事件**：article 发出 `article.published`、`article.updated`、`article.deleted`。编辑、发布、删除时 `version` 都会 +1。
- [x] **ES 客户端**：用 `elasticsearch-java`（Spring Boot 自动配置的 `ElasticsearchClient`）。
- [x] **索引结构**：真实索引 `article_v{n}` 加别名 `article`。写入用 ik_max_word 分词、查询用 ik_smart；字段按规格定义。
- [x] **同步消费者 `search.article-sync`**：用 `ArticleApi` 回查最新状态，已发布就写入 ES，否则从 ES 删除。写入和删除都带 `version_type=external`；返回 409 说明是旧版本，直接忽略。
- [x] **启动时建索引**：别名不存在时，新建索引，并通过 `ArticleApi` 按 id 游标分批全量导入，导入完成后才挂上别名。
- [x] **查询**：
  - `multi_match` 匹配 title^3、summary^1.5、content^1；关键词与某个标签名完全一致时额外加分。
  - 分类、标签作为 filter；排序支持 RELEVANCE 和 LATEST。
  - 高亮用 `<em>`，content 取 1 个约 100 字的片段。
- [x] **HTTP 测试**：覆盖匹配、筛选、页码上限、只返回已发布文章、中文分词、相关度排序、标签加权、高亮。发布、编辑、删除后，用 Awaitility 等待搜索结果变化。
- [x] **乱序测试**：模拟旧版本的数据在新版本之后写入，断言 ES 里保留的是新数据。

## Comments

- **错误码**：只定义了 60001（翻页超出上限，400）。`q` 为空或空白、`sort` 不是 RELEVANCE/LATEST、`page`/`size` 越界都走通用的 90400。
- **接口细节**：
  - `sort` 默认 RELEVANCE；`page` 从 1 开始，`size` 为 1–50、默认 20；`page × size`（即 `from + size`）超过 1000 返回 60001，size 为 20 时最多到第 50 页。
  - 结果项是文章列表项（见 06 号票）的全部字段加 `titleHighlight`、`contentHighlight`，平铺在同一层（`@JsonUnwrapped`）；未命中的高亮字段为 null。
  - 关键词去掉首尾空白后查询。
- **结构**：翻页上限校验和列表项组装在 `SearchServiceImpl`，ES 查询在 `EsArticleSearcher`，索引的建立、同步与重建在 `ArticleIndex`。
- **事件**：`ArticlePublishedEvent`、`ArticleUpdatedEvent`、`ArticleDeletedEvent`（`article/api/event/`）都只带 `articleId`、`authorId`。发布只在草稿首次发布时发出；编辑、删除对草稿也发出，由消费者回查状态决定。删除改为带版本号的更新，乐观锁插件把 `version` +1。
- **ArticleApi**：`findSnapshot(id)` 返回正文、标签、状态、版本号，已删除的文章也返回（`deleted=true`），同步方据此拿到删除后的版本号；`listPublishedSnapshots(afterId, limit)` 按 ID 正序遍历已发布文章。
- **索引**：mapping 在 search 模块的 `search/article-index.json`，单分片、无副本、`dynamic: strict`。除规格字段外多存一个 `tagIds`：`tags` 存标签名（lowercase normalizer）用于加分，`tagIds` 用于按标签筛选。写入带 `require_alias`，别名不存在时不会误建名为 `article` 的索引。
- **同步**：`ArticleIndex.sync` 回查快照，`searchable()` 就写入，否则删除；写入和删除都带 `version_type=external`，409 忽略，删除不存在的文档不报错。队列 `search.article-sync` 由 `ArticleSyncListener` 声明。
- **启动建索引**：`ArticleIndex` 实现 `SmartInitializingSingleton`，在 MQ 消费者启动前检查别名；不存在就在重建锁内再检查一次，仍不存在则执行一次 `rebuild` 的流程（见 12 号票）：新建 `article_v{n}`（n 取已有最大值 +1），按 500 篇一批 bulk 导入，除 409 外的失败直接抛出、阻止启动；导入完成才挂别名，所以中断后别名仍不存在，下次启动重来，残留的索引由那次重建删除。
  - 多个实例同时启动时，抢不到锁的实例跳过、照常启动；别名出现前它的搜索和同步都会失败，这期间的变更由重建最后的追补写入。
  - `ArticleIndex` 的别名是构造参数，索引名前缀和重建锁都由它派生，应用里用 `article`。
- **查询**（`EsArticleSearcher`）：
  - `multi_match` best_fields，`operator=and`（分词后的每个词都要出现在同一字段）；`tags` 上 `term` 加权 5。
  - RELEVANCE 按 `_score`、发布时间、ID 倒序；LATEST 按发布时间、ID 倒序。
  - 高亮经 HTML 转义；title 整体高亮，content 用 plain 高亮器取 1 个 100 字片段（unified 按句切分，无标点的长句会整句返回）；未命中的字段为 null。
  - 只从 ES 取命中 ID 和高亮，列表项经 `ArticleApi.listPublishedItems` 组装，同步尚未跟上的已删除文章被滤掉（total 仍按 ES 计）。
- **测试**：`SearchApiTest` 的搜索结果用 `eventually` 等待，需要确定顺序时按 LATEST；乱序测试直接调用 `ArticleIndex` 写入旧版本并读 ES 文档断言。`ArticleIndexStartupTest` 另建 `ArticleIndex`，用随机别名和桩化的 `ArticleApi`，覆盖导入中断后下次启动补全、两个实例同时启动都成功且只留一个索引。
