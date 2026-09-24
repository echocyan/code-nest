# 17: 缓存 redis 档

**What to build:** `cache.mode=redis` 下，文章详情、用户简要信息、文章摘要由 Redis 缓存，热门文章的详情接口不再每次都查数据库。文章编辑或删除后，缓存可靠失效，读者不会长期看到旧内容。不存在的文章 ID 不会反复打到数据库。本票同时建立 `TwoLevelCache` 组件的 Redis 一级实现。详见决策票 09。

**Blocked by:** 03, 10

Status: open

- [ ] **`TwoLevelCache`**：只有三个方法：`get(key, loader)`、`getAll(keys, batchLoader)`（批量读取走 `MGET`）、`evict(key)`。
  - Redis TTL 为 30 分钟，加 0–5 分钟随机抖动。
  - 空值缓存 60 秒。
- [ ] **接入范围**：
  - article 的文章详情缓存元数据和正文，不含计数，计数仍然单独组装。
  - `UserApi` 和 `ArticleApi` 的批量摘要查询走缓存。
- [ ] **缓存失效**：
  - 编辑、发布、删除，以及用户修改资料后，在 afterCommit 中 `evict`。
  - 新增消费者 `article.cache-evict`，订阅 `article.updated` 和 `article.deleted` 做第二次删除；这两个事件如果不存在就补上。
- [ ] **模式开关**：定义 `cache.mode`，本票实现 `none` 和 `redis` 两档。
- [ ] **HTTP 矩阵**：03、04 相关的测试在两档下都通过。另外断言：编辑后详情立即是新内容；修改昵称后摘要里的昵称更新；不存在的 ID 重复访问都返回 404。
- [ ] **文档**：写明 Cache-Aside 残留的竞态窗口。
