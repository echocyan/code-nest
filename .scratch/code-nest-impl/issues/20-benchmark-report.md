# 20: 执行压测并撰写 benchmark

**What to build:** 实际跑完 A–D 四个场景，写出 benchmark 文档，完整记录场景、目标、方案取舍与压测数据。同时按 30% 规则决定关注列表要不要加缓存。

**Blocked by:** 19

Status: closed

- [x] 每个场景跑 3 次，取中位数；原始结果 JSON 提交到仓库。
- [x] **benchmark 文档**：
  - 开头说明测试环境：单机、资源上限、数据规模，并注明数字只反映这套受限环境下的表现。
  - 每个方案包含场景、目标、方案取舍与测试数据：方案部分链接到对应的决策票；数据给出 QPS、P99、错误率和服务端指标差值。
- [x] **已知缺陷**：如实写明计数 SPOP 窗口、对账 ±1 误差、Cache-Aside 竞态、Pub/Sub 漏收、Feed 某一页可能少于 size 条。
- [x] **30% 规则**：测量关注列表查询占 Feed 读取耗时的比例。
  - 达到或超过 30% 时，加上 Redis Set 缓存（在关注、取关时维护），补测一轮并写入文档。
  - 否则在文档里记录"不加缓存"的依据。
- [x] 根目录 README 的压测结果一节给出各场景的关键数字，链接到 benchmark 文档。

## Comments

- **30% 规则**：`FeedStore` 记 Timer `feed.read` 与 `feed.read.follow-list`，管理端口开放 `metrics`，`bench.sh` 在场景 B 采集差值。
- **结果**：`code-nest-loadtest/results/2026-09-27-*.json`。关注列表查询占 Feed 读取耗时 2.1%，不加缓存。
