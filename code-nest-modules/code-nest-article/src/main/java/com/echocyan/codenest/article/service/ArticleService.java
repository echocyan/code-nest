package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.echocyan.codenest.article.ArticleErrorCode;
import com.echocyan.codenest.article.convert.CategoryConverter;
import com.echocyan.codenest.article.convert.TagConverter;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.entity.ArticleContent;
import com.echocyan.codenest.article.entity.ArticleStatus;
import com.echocyan.codenest.article.entity.ArticleTag;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.mapper.ArticleContentMapper;
import com.echocyan.codenest.article.mapper.ArticleMapper;
import com.echocyan.codenest.article.mapper.ArticleTagMapper;
import com.echocyan.codenest.article.mapper.CategoryMapper;
import com.echocyan.codenest.article.mapper.TagMapper;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.user.api.UserApi;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章的写作、发布、删除与详情。
 */
@Service
@RequiredArgsConstructor
public class ArticleService {

    /** 自动摘要截取的正文字符数。 */
    private static final int AUTO_SUMMARY_LENGTH = 100;

    private final ArticleMapper articleMapper;
    private final ArticleContentMapper articleContentMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final CategoryConverter categoryConverter;
    private final TagConverter tagConverter;
    private final UserApi userApi;

    /**
     * 新建草稿。
     *
     * @throws BizException 分类或标签不存在
     */
    @Transactional
    public Article create(long authorId, ArticleRequest request) {
        List<Long> tagIds = tagIdsOf(request);
        checkCategoryAndTags(request.categoryId(), tagIds);
        Article article = articleOf(request);
        article.setAuthorId(authorId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setVersion(0);
        articleMapper.insert(article);

        ArticleContent content = new ArticleContent();
        content.setArticleId(article.getId());
        content.setContent(request.content());
        articleContentMapper.insert(content);
        insertTags(article.getId(), tagIds);
        return article;
    }

    /**
     * 整体替换文章内容，状态和发布时间不变。
     *
     * @param version 客户端读到的版本号，与数据库不一致时视为冲突
     * @throws BizException 文章不存在、不是作者本人、版本冲突、分类或标签不存在
     */
    @Transactional
    public Article update(long id, long userId, int version, ArticleRequest request) {
        getOwned(id, userId);
        List<Long> tagIds = tagIdsOf(request);
        checkCategoryAndTags(request.categoryId(), tagIds);
        Article article = articleOf(request);
        article.setId(id);
        article.setVersion(version);
        updateOrConflict(article);

        ArticleContent content = new ArticleContent();
        content.setArticleId(id);
        content.setContent(request.content());
        articleContentMapper.updateById(content);
        articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, id));
        insertTags(id, tagIds);
        return article;
    }

    /**
     * 发布草稿。已发布的文章重复发布不产生变化。
     *
     * @throws BizException 文章不存在、不是作者本人、版本冲突
     */
    @Transactional
    public Article publish(long id, long userId) {
        Article article = getOwned(id, userId);
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            return article;
        }
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(DateTimes.now());
        updateOrConflict(article);
        return article;
    }

    /**
     * 软删除文章。
     *
     * @throws BizException 文章不存在、不是作者本人
     */
    @Transactional
    public void delete(long id, long userId) {
        getOwned(id, userId);
        articleMapper.deleteById(id);
    }

    /**
     * 查询当前用户自己的文章，用于写操作前的归属检查。
     *
     * @throws BizException {@link ArticleErrorCode#ARTICLE_NOT_FOUND}、{@link CommonErrorCode#FORBIDDEN}
     */
    private Article getOwned(long id, long userId) {
        Article article = articleMapper.selectById(id);
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
        if (articleMapper.updateById(article) == 0) {
            throw new BizException(ArticleErrorCode.VERSION_CONFLICT);
        }
    }

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

    private void insertTags(long articleId, List<Long> tagIds) {
        for (Long tagId : tagIds) {
            ArticleTag articleTag = new ArticleTag();
            articleTag.setArticleId(articleId);
            articleTag.setTagId(tagId);
            articleTagMapper.insert(articleTag);
        }
    }

    /**
     * 作者填写的摘要；没填时截取正文开头。按码点截取，避免切开 emoji 等代理对。
     */
    private static String summaryOf(ArticleRequest request) {
        if (request.summary() != null && !request.summary().isBlank()) {
            return request.summary();
        }
        String content = request.content().strip();
        return content.substring(0, content.offsetByCodePoints(0,
                Math.min(AUTO_SUMMARY_LENGTH, content.codePointCount(0, content.length()))));
    }

    /**
     * @throws BizException {@link ArticleErrorCode#CATEGORY_NOT_FOUND}、{@link ArticleErrorCode#TAG_NOT_FOUND}
     */
    private void checkCategoryAndTags(long categoryId, List<Long> tagIds) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(ArticleErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!tagIds.isEmpty() && tagMapper.selectCount(Wrappers.<Tag>lambdaQuery().in(Tag::getId, tagIds)) < tagIds.size()) {
            throw new BizException(ArticleErrorCode.TAG_NOT_FOUND);
        }
    }

    private static List<Long> tagIdsOf(ArticleRequest request) {
        return request.tagIds() == null ? List.of() : request.tagIds().stream().distinct().toList();
    }

    /**
     * 文章详情。草稿只有作者本人能看到，对其他人表现为不存在。
     *
     * @param viewerId 当前访客，匿名时为 null
     * @throws BizException {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    public ArticleDetailVO getDetail(long id, Long viewerId) {
        Article article = articleMapper.selectById(id);
        if (article == null
                || article.getStatus() == ArticleStatus.DRAFT && !Objects.equals(article.getAuthorId(), viewerId)) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        String content = articleContentMapper.selectById(id).getContent();
        List<Long> tagIds = articleTagMapper.selectList(Wrappers.<ArticleTag>lambdaQuery()
                        .eq(ArticleTag::getArticleId, id))
                .stream().map(ArticleTag::getTagId).toList();
        List<Tag> tags = tagIds.isEmpty() ? List.of()
                : tagMapper.selectList(Wrappers.<Tag>lambdaQuery().in(Tag::getId, tagIds).orderByAsc(Tag::getId));
        return new ArticleDetailVO(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getCoverUrl(),
                content,
                article.getStatus(),
                article.getPublishedAt(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.getVersion(),
                categoryConverter.toVO(categoryMapper.selectById(article.getCategoryId())),
                tagConverter.toVOs(tags),
                userApi.getBriefs(List.of(article.getAuthorId())).get(article.getAuthorId()));
    }
}
