package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.ArticleErrorCode;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.article.api.event.ArticleDeletedEvent;
import com.echocyan.codenest.article.api.event.ArticlePublishedEvent;
import com.echocyan.codenest.article.api.event.ArticleUpdatedEvent;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.entity.ArticleContent;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.mapper.ArticleMapper;
import com.echocyan.codenest.article.service.*;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.common.util.Texts;
import com.echocyan.codenest.counter.api.*;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService,
        CounterSource {

    /**
     * 自动摘要截取的正文字符数。
     */
    private static final int AUTO_SUMMARY_LENGTH = 100;

    private final ArticleContentService articleContentService;
    private final ArticleTagService articleTagService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CounterApi counterApi;
    private final DomainEventPublisher eventPublisher;
    private final ArticleCache articleCache;

    /**
     * 请求中可由作者修改的字段。
     */
    private static Article articleOf(ArticleRequest request) {
        Article article = new Article();
        article.setTitle(request.title());
        article.setSummary(summaryOf(request));
        article.setCoverUrl(request.coverUrl());
        article.setCategoryId(request.categoryId());
        return article;
    }

    private static ArticleContent contentOf(long articleId, ArticleRequest request) {
        ArticleContent content = new ArticleContent();
        content.setArticleId(articleId);
        content.setContent(request.content());
        return content;
    }

    /**
     * 作者填写的摘要；没填时截取正文开头。
     */
    private static String summaryOf(ArticleRequest request) {
        if (request.summary() != null && !request.summary().isBlank()) {
            return request.summary();
        }
        return Texts.head(request.content().strip(), AUTO_SUMMARY_LENGTH);
    }

    private static List<Long> tagIdsOf(ArticleRequest request) {
        return request.tagIds() == null ? List.of() : request.tagIds().stream().distinct().toList();
    }

    @Override
    @Transactional
    public Article create(long authorId, ArticleRequest request) {
        List<Long> tagIds = tagIdsOf(request);
        checkCategoryAndTags(request.categoryId(), tagIds);
        Article article = articleOf(request);
        article.setAuthorId(authorId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setVersion(0);
        save(article);
        articleCache.added(article.getId());

        articleContentService.save(contentOf(article.getId(), request));
        articleTagService.replaceTags(article.getId(), tagIds);
        return article;
    }

    @Override
    @Transactional
    public Article update(long id, long userId, int version, ArticleRequest request) {
        getOwned(id, userId);
        List<Long> tagIds = tagIdsOf(request);
        checkCategoryAndTags(request.categoryId(), tagIds);
        Article article = articleOf(request);
        article.setId(id);
        article.setVersion(version);
        updateOrConflict(article);

        articleContentService.updateById(contentOf(id, request));
        articleTagService.replaceTags(id, tagIds);
        articleCache.evict(id);
        eventPublisher.publish(new ArticleUpdatedEvent(id, userId));
        return article;
    }

    @Override
    @Transactional
    public Article publish(long id, long userId) {
        Article article = getOwned(id, userId);
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            return article;
        }
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(DateTimes.now());
        updateOrConflict(article);
        counterApi.increment(CounterMetric.USER_ARTICLE, userId, 1);
        articleCache.evict(id);
        eventPublisher.publish(new ArticlePublishedEvent(id, userId));
        return article;
    }

    @Override
    @Transactional
    public void delete(long id, long userId) {
        Article article = getOwned(id, userId);
        // 带上读到的版本号：期间被发布或删除时按冲突处理，保证按读到的状态增减文章数是正确的。
        // 乐观锁插件比对版本号并把它 +1，搜索同步以此让删除覆盖此前的写入
        Article deletion = new Article();
        deletion.setVersion(article.getVersion());
        boolean deleted = lambdaUpdate()
                .set(Article::getDeleted, 1)
                .eq(Article::getId, id)
                .update(deletion);
        if (!deleted) {
            throw new BizException(ArticleErrorCode.VERSION_CONFLICT);
        }
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            counterApi.increment(CounterMetric.USER_ARTICLE, userId, -1);
        }
        articleCache.evict(id);
        eventPublisher.publish(new ArticleDeletedEvent(id, userId));
    }

    @Override
    public Optional<Article> findPublished(long id) {
        return Optional.ofNullable(getById(id)).filter(article -> article.getStatus() == ArticleStatus.PUBLISHED);
    }

    @Override
    public Article getIncludingDeleted(long id) {
        return baseMapper.selectByIdIncludingDeleted(id);
    }

    @Override
    public List<Article> listUpdatedSinceIncludingDeleted(LocalDateTime since, Long afterId, int limit) {
        return baseMapper.selectUpdatedSinceIncludingDeleted(since, afterId == null ? 0 : afterId, limit);
    }

    @Override
    public Set<CounterMetric> metrics() {
        return Set.of(CounterMetric.USER_ARTICLE);
    }

    @Override
    public List<IdCount> countAfter(CounterMetric metric, long afterId, int limit) {
        return baseMapper.countPublishedByAuthor(afterId, limit);
    }

    /**
     * 查询当前用户自己的文章，用于写操作前的归属检查。
     *
     * @throws BizException {@link ArticleErrorCode#ARTICLE_NOT_FOUND}、{@link CommonErrorCode#FORBIDDEN}
     */
    private Article getOwned(long id, long userId) {
        Article article = getById(id);
        if (article == null) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        if (article.getAuthorId() != userId) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        return article;
    }

    /**
     * 按 ID 更新并比对版本号，成功后 article 中的版本号已是新值。
     *
     * @throws BizException {@link ArticleErrorCode#VERSION_CONFLICT}
     */
    private void updateOrConflict(Article article) {
        if (!updateById(article)) {
            throw new BizException(ArticleErrorCode.VERSION_CONFLICT);
        }
    }

    /**
     * @throws BizException {@link ArticleErrorCode#CATEGORY_NOT_FOUND}、{@link ArticleErrorCode#TAG_NOT_FOUND}
     */
    private void checkCategoryAndTags(long categoryId, List<Long> tagIds) {
        if (categoryService.getById(categoryId) == null) {
            throw new BizException(ArticleErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!tagIds.isEmpty() && tagService.lambdaQuery().in(Tag::getId, tagIds).count() < tagIds.size()) {
            throw new BizException(ArticleErrorCode.TAG_NOT_FOUND);
        }
    }

}
