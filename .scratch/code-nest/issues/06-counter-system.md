# 计数系统（点赞/收藏/粉丝数）

Type: grilling
Status: open
Blocked by: 03, 05

## Question

点赞/收藏/粉丝数等高频计数如何设计：Redis 中的数据结构（谁点过赞的关系 + 计数值）、写入路径（先 Redis 后异步批量落库？）、如何保证每用户对同一对象至多一次、Redis 与 MySQL 的最终一致与对账、Redis 数据丢失后的重建？优化前（直写 MySQL）与优化后如何对比？
