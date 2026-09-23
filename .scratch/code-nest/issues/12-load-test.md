# 压测方案

Type: grilling
Status: open
Blocked by: 06, 07, 08, 09, 10

## Question

（背景：各亮点都已预留可切换的基线，对比对象如下：`counter.mode` 取 sync-db / redis-async，场景是热门文章被大量并发点赞；`feed.mode` 取 pull / push-pull，场景是关注几百人的用户读 Feed，外加大 V 发文时的写扩散；`search.mode` 取 mysql-like / es，数据量 10 万篇文章；`cache.mode` 取 none / redis / two-level，场景是热门文章详情。压测期间用 `rate-limit.enabled=false` 关闭限流。另外，Feed 票遗留了一个问题：关注列表是否需要缓存，视压测结果而定。）

如何为每个主打亮点产出可信的前后对比数据：压测工具（k6 vs JMeter）、造数规模与方式、每个亮点的基线版本（优化前）如何保留或切换、测什么指标（QPS、P99、DB 负载）、结果如何记录成简历可用的数字？压测时应用如何容器化（Dockerfile、每个实例的 CPU 和内存上限、多实例加 Nginx 负载均衡，以便演示多实例本地缓存失效）？
