# 搜索与数据同步

Type: grilling
Status: open
Blocked by: 03, 05

## Question

文章全文搜索如何实现：ES 索引 mapping（中文分词用 IK 插件，8.19.21 版本已确认可用：https://get.infini.cloud/elasticsearch/analysis-ik/8.19.21 ；分词器选择 ik_max_word / ik_smart）、搜索字段与权重、高亮、排序；MySQL → ES 的同步方式（基于消息可靠性底座的 Outbox + MQ，还是 Canal 订阅 binlog）、全量重建与增量同步、删除与更新的顺序问题？
