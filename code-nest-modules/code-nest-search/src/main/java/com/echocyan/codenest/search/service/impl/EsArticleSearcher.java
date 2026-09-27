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
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.service.ArticleIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 在已发布文章中按关键词检索：在 {@link ArticleIndex} 中用 IK 分词，按相关度排序并高亮。调用方已校验翻页深度。
 *
 * <p>标题、摘要、正文的权重为 3、1.5、1，分词后的每个词都要出现在同一个字段里；关键词与文章的某个标签名完全一致
 * （不区分大小写）时额外加分。分类、标签只做筛选，不参与打分。高亮文本经 HTML 转义，命中词用 {@code <em>} 包裹，
 * 字段未命中时为 null。
 */
@Service
@RequiredArgsConstructor
class EsArticleSearcher {

    /**
     * 标签名命中时的加权。
     */
    private static final float TAG_BOOST = 5;

    /**
     * 正文高亮片段的长度（字符数）。
     */
    private static final int CONTENT_FRAGMENT_SIZE = 100;

    private final ElasticsearchClient client;

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

    /**
     * @param categoryId 为 null 时不按分类筛选
     * @param tagId      为 null 时不按标签筛选
     */
    PageResult<Match> search(String keyword, Long categoryId, Long tagId, SearchSort sort, long page, long size) {
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
        List<Match> list = response.hits().hits().stream()
                .map(hit -> new Match(Long.parseLong(hit.id()), highlightOf(hit, "title"), highlightOf(hit, "content")))
                .toList();
        long total = Objects.requireNonNull(response.hits().total()).value();
        return new PageResult<>(list, total, page, size);
    }

    /**
     * 一条命中结果。
     *
     * @param titleHighlight   高亮后的标题，未命中时为 null
     * @param contentHighlight 正文的高亮片段，未命中时为 null
     */
    record Match(long articleId, String titleHighlight, String contentHighlight) {
    }
}
