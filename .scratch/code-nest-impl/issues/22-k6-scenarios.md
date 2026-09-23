# 22: k6 场景与指标采集

**What to build:** 用 `grafana/k6` 镜像跑 A–D 四个压测场景，一条命令完成一整组"切换开关 → 预热 → 稳态压测 → 采集服务端指标"，结果保存为 JSON。

**Blocked by:** 21

**Status:** ready-for-agent

- [ ] **场景 A 计数**：200 个 VU，用不同账号对同一篇文章反复点赞、取消。对比 `counter.mode` 两档。压测结束、落库完成后，断言计数等于 `COUNT(*)`。
- [ ] **场景 B Feed**：200 个 VU 以重度用户身份读 Feed 首页，对比 `feed.mode` 两档。另外测量一个有 4999 个粉丝的普通作者发文后，推送完成的耗时。
- [ ] **场景 C 搜索**：50 个 VU 用词库中的随机关键词搜索，对比 `search.mode` 两档。
- [ ] **场景 D 缓存**：300 个 VU 访问文章详情，请求按 Zipf 分布集中在 Top 10 文章上。对比 `cache.mode` 三档。
- [ ] **执行节奏**：每组先预热 30 秒，再稳态压测 2 分钟。脚本支持连续跑 3 次，并取中位数。
- [ ] **指标采集**：压测前后各采集一次 MySQL `SHOW GLOBAL STATUS` 和 Redis `INFO commandstats`，输出差值，重点关注 `Innodb_row_lock_waits`、`Com_select` 和各命令的调用次数。
- [ ] **结果存储**：结果写入 loadtest 模块的 results 目录，文件名为 `<日期>-<场景>.json`。
