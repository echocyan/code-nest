# 多级缓存与缓存治理

Type: grilling
Status: open
Blocked by: 03

## Question

文章详情等热点读如何缓存：Caffeine 本地缓存 + Redis 的两级结构、缓存 key 与过期策略、更新时的一致性（先更新库再删缓存？延迟双删？多实例本地缓存如何失效——Redis Pub/Sub 广播？）、穿透（布隆过滤器/空值）、击穿（互斥锁 vs 逻辑过期）、雪崩（随机过期）、热点 key 探测？
