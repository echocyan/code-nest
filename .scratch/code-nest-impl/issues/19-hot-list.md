# 19: 热榜

**What to build:** 读者可以查看热榜：综合点赞、收藏、评论、浏览和发布时间排出 Top 100，每页 20 条、最多 5 页。热榜每 5 分钟重算一次，已删除的文章不出现在榜上。详见决策票 10。

**Blocked by:** 05, 06

Status: closed

- [x] **热度公式**：`(3·like + 5·fav + 4·comment + 0.1·view) / (hours + 2)^1.5`，各项权重和重力系数都可配置。为公式写纯单元测试。
- [x] **定时重算**：每 5 分钟执行一次，用 `SET hot:lock NX EX 240` 保证同一时刻只有一个实例在算。
  - 候选集是最近 7 天发布的文章，通过 `ArticleApi` 新增的能力取得。
  - 每批 500 个调用 `CounterApi` 取计数。
  - 取前 100 名写入 `hot:articles:tmp`，再用 `RENAME` 替换 `hot:articles`。
- [x] **`GET /hot-articles?page=`**：匿名可访问，每页 20 条，最多 5 页，超出返回 400。列表项含摘要和计数；已删除的文章在读取时过滤。
- [x] **测试**：可以手动触发一次重算。断言互动多的文章排在前面、已删除的文章不出现、页码上限生效。

## Comments

- **公式**：`HotFormula`（`article/service/impl`）是纯函数，入参为 `Counts` 与发布至今的小时数（带小数）；权重与重力系数取自 `hot.weight.{like,favorite,comment,view}`、`hot.gravity`。
- **候选集**：`ArticleApi.getPublishedSince(since)` 返回文章 ID 到发布时间，只查 id、published_at 两列，走 `idx_status_published`。
- **重算**（`HotArticleServiceImpl.refresh`）：
  - 定时任务用 cron `0 */5 * * * *`，各实例在同一时刻触发，抢到 `hot:lock`（NX EX 240，值为随机 token）的实例计算，其余跳过。算完即按 token 释放锁，EX 240 只用于实例崩溃时兜底；因此错开几秒触发的实例可能再算一次，结果相同，无害。
  - 候选集全部打分后排序取前 100（含热度为 0 的文章），先删除残留的 `hot:articles:tmp` 再写入，最后 `RENAME` 为 `hot:articles`；候选集为空时直接删除 `hot:articles`。
  - 启动时不立即计算，新环境最多等 5 分钟出现榜单。
- **读取**：`GET /hot-articles?page=`，`page` 默认 1、范围 1–5，越界返回 400 / 90400；每页固定 20 条，不接受 `size`。`ZREVRANGE` 取一页 ID 后经 `ArticleService.listPublishedItems` 按榜单顺序补全摘要、分类、作者与计数，已删除或非发布状态的文章被滤掉，一页可能不足 20 条；`total` 取 `ZCARD`，含被滤掉的文章。摘要目前直接查库，等 17 号票给文章摘要加缓存时一并切换。
- **测试**：`HotArticleApiTest` 与 `HotArticleRedisAsyncApiTest` 注入 `HotArticleService` 手动重算，重算与断言放在 `eventually` 里，覆盖计数异步生效和锁被其他上下文的定时任务占住两种情况；共用库里有其他测试的文章，只断言本测试文章的相对顺序。
