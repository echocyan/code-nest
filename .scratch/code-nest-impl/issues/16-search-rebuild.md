# 16: ES 零停机重建

**What to build:** 运维者调用 `POST /actuator/search-rebuild` 重建 ES 索引，重建期间搜索不中断，重建期间发生的文章变更也不会丢失。

**Blocked by:** 15

Status: closed

- [x] **重建流程**：
  1. 新建 `article_v{n+1}`。
  2. 按 id 游标每批 500 篇，用 bulk 写入。
  3. 原子切换别名。
  4. 通过 `ArticleApi` 查询 `updated_at` 晚于重建开始时间的文章，写入新索引（依赖外部版本号，重复写入不会出错）。
  5. 删除旧索引。
- [x] **并发保护**：同一时刻只允许一个重建任务运行。
- [x] **测试**：
  - 重建前后，搜索结果一致。
  - 重建过程中编辑的文章，重建完成后能搜到最新内容。
  - 重建期间持续发起搜索，不会报错。

## Comments

- **实现**：`ArticleIndex.rebuild()`，由 `SearchRebuildEndpoint`（`POST /actuator/search-rebuild`，只在 es 档装配）同步调用，返回新索引名、导入数与追补数；已有重建在运行时返回 409。
- **流程细节**：
  - 开始时间取当前时间向下取整到秒：`updated_at` 是秒级 `DATETIME`，写入时四舍五入。
  - 全量导入直接写新索引名（不经别名）；导入完先 refresh 再切换别名，否则切换后的约 1 秒内最后几批搜不到。
  - 切换别名用一次 `_aliases` 请求：移除旧索引上的别名、挂到新索引。
  - 追补用 `ArticleApi.listSnapshotsUpdatedSince(since, afterId, limit)`，草稿和已删除的文章也返回，逐篇按最新状态写入或删除（经别名，即新索引）。导入期间的变更由消费者写进了旧索引；切换后到达的消息直接写新索引。
  - `article.updated_at` 上没有索引，追补按主键游标扫描，只在手动重建时执行一次。
- **互斥**：Redis 锁 `search:rebuild:lock`（`SET NX EX`，1 小时过期，按 token 释放）。
- **测试**：`SearchEsApiTest` 用 `@MockitoSpyBean` 包住 `ArticleApi`，在导入读完测试文章那一批后插入操作：重建期间编辑、删除文章并等同步写入旧索引，重建后断言搜到最新内容；在导入中再次触发重建断言 409。另测重建前后搜索响应一致、旧索引被删除，以及重建期间持续搜索都成功。
