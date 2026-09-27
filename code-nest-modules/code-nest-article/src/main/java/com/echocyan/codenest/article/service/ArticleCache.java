package com.echocyan.codenest.article.service;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.CategoryBrief;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.framework.cache.BloomFilter;
import com.echocyan.codenest.framework.cache.TwoLevelCache;
import com.echocyan.codenest.framework.cache.TwoLevelCaches;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 文章的缓存：详情是两级缓存，用布隆过滤器 {@code bf:article} 拦截不存在的文章 ID；摘要只用 Redis。
 * 读取与加载由 {@link ArticleReader} 负责。
 * <p>
 * 文章创建后调用 {@link #added}，编辑、发布、删除后调用 {@link #evict}，写方不需要知道有几份缓存。
 */
@Component
public class ArticleCache {

    private final BloomFilter bloomFilter;
    private final TwoLevelCache<Detail> details;
    private final TwoLevelCache<ArticleBrief> briefs;

    ArticleCache(TwoLevelCaches caches) {
        this.bloomFilter = caches.bloomFilter("article");
        this.details = caches.createTwoLevel("article:detail", Detail.class, bloomFilter);
        this.briefs = caches.create("article:brief", ArticleBrief.class);
    }

    /**
     * 读取文章详情。
     *
     * @param loader 未命中时从数据库加载，文章不存在或已删除时返回 null
     * @return 文章不存在或已删除时为 null
     */
    public Detail getDetail(long id, Function<Long, Detail> loader) {
        return details.get(id, loader);
    }

    /**
     * 批量读取文章摘要。
     *
     * @param loader 对未命中的 ID 调用一次，不存在的文章不放进结果
     * @return 以文章 ID 为 key；不存在的文章不出现在结果中
     */
    public Map<Long, ArticleBrief> getBriefs(Collection<Long> ids,
                                             Function<Collection<Long>, Map<Long, ArticleBrief>> loader) {
        return briefs.getAll(ids, loader);
    }

    /**
     * 文章创建后把 ID 加入布隆过滤器。可以在写库的事务里调用，事务回滚只会留下一个误判。
     */
    public void added(long id) {
        bloomFilter.add(id);
    }

    /**
     * 文章编辑、发布、删除后删除详情和摘要缓存；有活跃事务时推迟到提交后执行。重复执行无害。
     */
    public void evict(long id) {
        details.evict(id);
        briefs.evict(id);
    }

    /**
     * 布隆过滤器不存在（首次部署或 Redis 数据丢失）时按全部未删除文章（含草稿）的 ID 重建，见 {@link BloomFilter}。
     *
     * @param idsAfter 返回大于给定 ID 的下一批 ID，按 ID 升序；参数为 null 时从头开始，返回空列表表示结束
     */
    public void rebuildBloomFilterIfAbsent(Function<Long, List<Long>> idsAfter) {
        bloomFilter.rebuildIfAbsent(idsAfter);
    }

    /**
     * 缓存中的文章详情：元数据、正文、分类与标签。作者信息和计数经各自的门面另行组装，不放进缓存，
     * 所以点赞、改昵称不会让它失效。
     */
    public record Detail(Article article, String content, CategoryBrief category, List<TagVO> tags) {
    }
}
