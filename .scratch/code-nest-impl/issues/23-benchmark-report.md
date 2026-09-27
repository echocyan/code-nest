# 23: 执行压测并撰写 benchmark

**What to build:** 实际跑完 A–D 四个场景，按 STAR 结构写出 benchmark 文档，作为简历和面试的底稿。同时按 30% 规则决定关注列表要不要加缓存。

**Blocked by:** 22

Status: closed

- [x] 每组跑 3 次，取中位数；原始结果 JSON 提交到仓库。
- [x] **benchmark 文档**：
  - 开头说明测试环境：单机、资源上限、数据规模，并注明"关注的是相对提升"。
  - 每个亮点一节 S/T/A/R：A 部分链接到对应的决策票；R 给出 QPS、P99、错误率和服务端指标差值。
- [x] **已知缺陷**：如实写明计数 SPOP 窗口、对账 ±1 误差、Cache-Aside 竞态、Pub/Sub 漏收、Feed 某一页可能少于 size 条。
- [x] **30% 规则**：在 push-pull 模式下，测量关注列表查询占 Feed 读取耗时的比例。
  - 达到或超过 30% 时，加上 Redis Set 缓存（在关注、取关时维护），补测一轮并写入文档。
  - 否则在文档里记录"不加缓存"的依据。

## Comments

- **结果**：`docs/benchmark.md`，原始 JSON 在 `code-nest-loadtest/results/`。场景 B、D 另在 `COUNTER_MODE=redis-async` 下补测：sync-db 档下 D 的每次浏览都要写热点计数行，三档都被行锁卡住；B 的补测用来确认大 V 识别修复在两种计数档下都成立。
- **30% 规则**：`FeedServiceImpl` 记 Timer `feed.read` 与 `feed.read.follow-list`，管理端口开放 `metrics`，`bench.sh` 在场景 B 采集差值。push-pull 下占比 27.5%（sync-db）、2.1%（redis-async），不加缓存。
- **大 V 识别**：压测发现 push-pull 读 Feed 要逐个读取约 500 个关注作者的粉丝数，比 pull 慢。改为 `BigAuthors` 维护 ZSet `feed:followers`（作者 ID → 粉丝数，关注、取关后按 follow 表重新统计，启动时缺失就重建），存粉丝数而不是大 V 名单，阈值不同的测试上下文可以共用。`FeedOutboxLoader` 改名 `FeedLoader`，`rebuildOutboxesIfAbsent` 改为 `rebuildIfAbsent`，一并重建两者；测试 `FeedPushPullApiTest.lostFollowerCountsAreRebuiltOnRestart`。
- **bench.sh**：每轮压测前等有消费者的 MQ 队列清空（场景 A 积压的通知曾在 B、C 压测期间持续消费）；结果 JSON 增加 `fixed` 记录其余开关，非默认档追加到文件名。修复前的 B 结果改名为 `*-before-fix.json`。
- **相关度抽查**：10 个关键词在两档下各取前 5 条，结果写在 benchmark 的 C 节。
