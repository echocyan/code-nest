# 热榜

Type: grilling
Status: open
Blocked by: 06

## Question

（背景：点赞、收藏、评论、浏览量由 [计数系统](06-counter-system.md) 提供。读取时通过 `CounterApi.get` 批量获取；各模块上报计数变更时调用 `CounterApi.increment`。浏览量是近似计数。）

热度公式是什么（点赞/收藏/评论/浏览的权重、时间衰减方式，如 Hacker News 公式）？榜单用 Redis ZSet 实时更新还是定时任务批量计算？榜单周期（日榜/周榜/总榜）与候选集范围？
