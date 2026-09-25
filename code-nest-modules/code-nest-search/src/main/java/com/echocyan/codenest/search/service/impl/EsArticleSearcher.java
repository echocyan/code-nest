package com.echocyan.codenest.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.HighlighterEncoder;
import co.elastic.clients.elasticsearch.core.search.HighlighterType;
import co.elastic.clients.util.NamedValue;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.service.ArticleIndex;
import com.echocyan.codenest.search.service.ArticleSearcher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 优化实现：在 {@link ArticleIndex} 中用 IK 分词检索，按相关度排序并高亮。
 *
 * <p>标题、摘要、正文的权重为 3、1.5、1，分词后的每个词都要出现在同一个字段里；关键词与文章的某个标签名完全一致
 * （不区分大小写）时额外加分。分类、标签只做筛选，不参与打分。高亮文本经 HTML 转义，命中词用 {@code <em>} 包裹，
 * 字段未命中时为 null。
 *
 * <p>文章摘要按命中的 ID 从 article 模块回查，同步尚未跟上的已删除文章不会出现在结果里。
 */
@Service
@ConditionalOnProperty(name = "search.mode", havingValue = "es")
@RequiredArgsConstructor
class EsArticleSearcher implements ArticleSearcher {

    /** 标签名命中时的加权。 */
    private static final float TAG_BOOST = 5;

    /** 正文高亮片段的长度（字符数）。 */
    private static final int CONTENT_FRAGMENT_SIZE = 100;

    private final ElasticsearchClient client;
    private final ArticleApi articleApi;

    @Override
    public PageResult<Hit> search(String keyword, Long categoryId, Long tagId, SearchSort sort, long page,
                                  long size) {
        SearchResponse<Void> response;
        try {
            response = client.search(search -> search
                    .index(ArticleIndex.ALIAS)
                    .from((int) ((page - 1) * size))
                    .size((int) size)
                    .source(source -> source.fetch(false))
                    .query(query -> query.bool(bool -> filtered(matching(bool, keyword), categoryId, tagId)))
                    .sort(sortOf(sort))
                    .highlight(highlight -> highlight
                            .preTags("<em>")
                            .postTags("</em>")
                            .encoder(HighlighterEncoder.Html)
                            .fields(NamedValue.of("title", HighlightField.of(field -> field.numberOfFragments(0))))
                            // plain 高亮器按字符数切片段；默认的 unified 按句切分，没有标点的长句会整句返回
                            .fields(NamedValue.of("content", HighlightField.of(field -> field
                                    .type(HighlighterType.Plain)
                                    .fragmentSize(CONTENT_FRAGMENT_SIZE)
                                    .numberOfFragments(1))))), Void.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // ES 的 Hit 与 ArticleSearcher.Hit 同名，这里用 var
        var hits = response.hits().hits();
        Map<Long, ArticleBrief> articles = articleApi.getBriefs(
                hits.stream().map(hit -> Long.valueOf(hit.id())).toList());
        List<Hit> list = new ArrayList<>();
        for (var hit : hits) {
            ArticleBrief article = articles.get(Long.valueOf(hit.id()));
            if (article != null && article.status() == ArticleStatus.PUBLISHED) {
                list.add(new Hit(article, highlightOf(hit, "title"), highlightOf(hit, "content")));
            }
        }
        long total = Objects.requireNonNull(response.hits().total()).value();
        return new PageResult<>(list, total, page, size);
    }

    private static BoolQuery.Builder matching(BoolQuery.Builder bool, String keyword) {
        return bool
                .must(must -> must.multiMatch(match -> match
                        .query(keyword)
                        .fields("title^3", "summary^1.5", "content")
                        .operator(Operator.And)))
                .should(should -> should.term(term -> term.field("tags").value(keyword).boost(TAG_BOOST)));
    }

    private static BoolQuery.Builder filtered(BoolQuery.Builder bool, Long categoryId, Long tagId) {
        if (categoryId != null) {
            bool.filter(filter -> filter.term(term -> term.field("categoryId").value(categoryId)));
        }
        if (tagId != null) {
            bool.filter(filter -> filter.term(term -> term.field("tagIds").value(tagId)));
        }
        return bool;
    }

    /**
     * 同分或同一时间发布时，依次按发布时间、ID 倒序，保证翻页稳定。
     */
    private static List<SortOptions> sortOf(SearchSort sort) {
        List<SortOptions> options = new ArrayList<>();
        if (sort == SearchSort.RELEVANCE) {
            options.add(SortOptions.of(option -> option.score(score -> score.order(SortOrder.Desc))));
        }
        options.add(SortOptions.of(option -> option.field(field -> field.field("publishedAt").order(SortOrder.Desc))));
        options.add(SortOptions.of(option -> option.field(field -> field.field("id").order(SortOrder.Desc))));
        return options;
    }

    private static String highlightOf(co.elastic.clients.elasticsearch.core.search.Hit<Void> hit, String field) {
        List<String> fragments = hit.highlight().get(field);
        return fragments == null || fragments.isEmpty() ? null : fragments.getFirst();
    }
}
