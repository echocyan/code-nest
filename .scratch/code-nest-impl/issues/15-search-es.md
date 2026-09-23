# 15: ES 搜索同步与查询

**What to build:** `search.mode=es` 下，中文搜索有分词、相关度排序、标签加权和高亮。文章发布、编辑、删除后，搜索结果在秒级内同步更新，而且旧数据永远不会覆盖新数据。应用启动时如果别名不存在，就自动建索引并全量导入。详见决策票 08。

**Blocked by:** 09, 10

**Status:** ready-for-agent

- [ ] **生产端事件**：article 发出 `article.updated`，发布和删除事件复用已有的（没有就补上）。编辑、发布、删除时 `version` 都会 +1。
- [ ] **ES 客户端**：用 `elasticsearch-java`（Spring Boot 自动配置的 `ElasticsearchClient`）。
- [ ] **索引结构**：真实索引 `article_v{n}` 加别名 `article`。写入用 ik_max_word 分词、查询用 ik_smart；字段按规格定义。
- [ ] **同步消费者 `search.article-sync`**：用 `ArticleApi` 回查最新状态，已发布就写入 ES，否则从 ES 删除。写入和删除都带 `version_type=external`；返回 409 说明是旧版本，直接忽略。
- [ ] **启动时建索引**：别名不存在时，新建索引，并通过 `ArticleApi` 按 id 游标分批全量导入。
- [ ] **查询**：
  - `multi_match` 匹配 title^3、summary^1.5、content^1；关键词与某个标签名完全一致时额外加分。
  - 分类、标签作为 filter；排序支持 RELEVANCE 和 LATEST。
  - 高亮用 `<em>`，content 取 1 个约 100 字的片段。
  - 作者昵称通过 `UserApi` 补全。
- [ ] **HTTP 矩阵**：两档搜到的是同一批文章。相关度排序、标签加权、高亮只在 es 档断言。发布、编辑、删除后，用 Awaitility 等待搜索结果变化。
- [ ] **乱序测试**：模拟旧版本的数据在新版本之后写入，断言 ES 里保留的是新数据。
