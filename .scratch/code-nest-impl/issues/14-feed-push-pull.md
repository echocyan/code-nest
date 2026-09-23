# 14: Feed push-pull

**What to build:** `feed.mode=push-pull` 下，关注了几百人的重度用户读 Feed 依然很快，大 V 发文也不会引发写扩散风暴。普通作者发文时推送到粉丝的收件箱，大 V 的文章在读取时拉取；冷用户回来时重建收件箱；关注、取关、删文后 Feed 表现正确。已有的 Feed HTTP 测试在两档下都通过。详见决策票 07。

**Blocked by:** 08, 10

**Status:** ready-for-agent

- [ ] **生产端事件**：article 在发布或删除时发出 `article.published` 和 `article.deleted`（如果后续票已经加过，就直接复用）；social 发出 `follow.created` 和 `follow.deleted`。
- [ ] **Redis 结构**：每个作者一个发件箱（上限 100 条）、每个读者一个收件箱（上限 500 条，TTL 7 天）。member 和 score 都是 articleId。
- [ ] **推送**：消费者 `social.feed-push` 先写作者的发件箱。作者不是大 V 时，按每页 1000 个粉丝，给收件箱仍存在的粉丝执行 pipeline `ZADD` 并裁剪到上限。
- [ ] **大 V 阈值**：配置项 `feed.big-author-threshold`，默认 5000，粉丝数从 `CounterApi` 读取。
- [ ] **读取**：
  1. 用 `CounterApi` 识别出关注列表中的大 V。
  2. 读取收件箱；不存在就用普通作者的发件箱重建，然后续期。
  3. 拉取各大 V 的发件箱。
  4. 合并、去重、按游标截取。
  5. 过滤已删除、非发布状态和已取关作者的文章。
- [ ] **修正**：关注普通作者时，把对方的发件箱合并进我的收件箱；取关时按对方发件箱从我的收件箱里 `ZREM`；删文时从发件箱里移除。
- [ ] **HTTP 矩阵**：08 票的测试在两档下都通过。另外补充 push-pull 专属场景：大 V 与普通作者的文章混排、收件箱过期后重建、关注后立即能看到对方的历史文章。
