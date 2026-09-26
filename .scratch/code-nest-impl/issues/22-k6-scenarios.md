# 22: k6 场景与指标采集

**What to build:** 用 `grafana/k6` 镜像跑 A–D 四个压测场景，一条命令完成一整组"切换开关 → 预热 → 稳态压测 → 采集服务端指标"，结果保存为 JSON。

**Blocked by:** 21

Status: closed

- [x] **场景 A 计数**：200 个 VU，用不同账号对同一篇文章反复点赞、取消。对比 `counter.mode` 两档。压测结束、落库完成后，断言计数等于 `COUNT(*)`。
- [x] **场景 B Feed**：200 个 VU 以重度用户身份读 Feed 首页，对比 `feed.mode` 两档。另外测量一个有 4999 个粉丝的普通作者发文后，推送完成的耗时。
- [x] **场景 C 搜索**：50 个 VU 用词库中的随机关键词搜索，对比 `search.mode` 两档。
- [x] **场景 D 缓存**：300 个 VU 访问文章详情，请求按 Zipf 分布集中在 Top 10 文章上。对比 `cache.mode` 三档。
- [x] **执行节奏**：每组先预热 30 秒，再稳态压测 2 分钟。脚本支持连续跑 3 次，并取中位数。
- [x] **指标采集**：压测前后各采集一次 MySQL `SHOW GLOBAL STATUS` 和 Redis `INFO commandstats`，输出差值，重点关注 `Innodb_row_lock_waits`、`Com_select` 和各命令的调用次数。
- [x] **结果存储**：结果写入 loadtest 模块的 results 目录，文件名为 `<日期>-<场景>.json`。

## Comments

- **入口**：`code-nest-loadtest/bench.sh <a|b|c|d> [轮数]`，`WARMUP`、`DURATION` 可覆盖时长。k6 是 `compose.loadtest.yaml` 里 profile 为 `k6` 的服务（`grafana/k6:2.3.0`，挂载 loadtest 目录），以当前用户 `compose run`，经 Nginx 访问 `/api/v1`。差值与中位数用宿主机的 `jq` 计算。
- **阶段**：每个脚本分 prepare、warmup、steady 三次 k6 run。k6 每次运行都执行 `setup()`，`lib.js` 的 `prepareWith` 让它只在 prepare 阶段发请求，结果经 `handleSummary` 的 `setup_data` 写到 `target/k6/`，其他阶段直接读回；登录等准备请求不计入 steady 的指标和服务端差值。prepare 阶段的迭代跑空函数 `idle`。
- **切换开关**：停应用 → `FLUSHALL` → 以新档启动 → 重启 Nginx（应用容器重启后 IP 可能变化，Nginx 只在启动时解析）。每档从同样的 Redis 状态开始，也避免计数在两档之间切换时 Redis 里留下旧值；token 随之清空，所以每档重新 prepare。
- **场景取值**：A 用 `user_20000` 起的 200 个账号（不写文章），点赞最新发布的一篇文章；A 压测后每 6 秒比较一次 `article_stat.like_count` 与点赞行数，连续两次相等算落库完成、再采集，60 秒内做不到就退出。B 的 200 个 VU 轮流使用 100 个重度用户。D 的候选是最新发布的 1000 篇，Zipf 指数 1.2，前 10 篇约占 57%。
- **推送耗时**：推送会跳过收件箱不存在的粉丝，造数后收件箱都不存在，所以 prepare 阶段让 author_4999 的 4999 个粉丝各读一次 Feed（约 2 分钟）。推送按粉丝 ID 升序，从发出发布请求起轮询 ID 最大的粉丝的 Feed 直到出现新文章，测完删文。只在 push-pull 档测。
- **冒烟验证**：`WARMUP=5s DURATION=10s`–`15s` 下四个场景都跑通，错误率 0；A 两档的计数断言通过；B 的推送耗时约 330ms。
