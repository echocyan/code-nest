package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.Counts;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 热度公式：{@code (3·点赞 + 5·收藏 + 4·评论 + 0.1·浏览) / (发布小时数 + 2)^1.5}。
 */
class HotFormulaTest {

    private static final HotFormula DEFAULT = new HotFormula(3, 5, 4, 0.1, 1.5);

    private static Counts counts(long like, long favorite, long comment, long view) {
        return new Counts(Map.of(
                CounterMetric.ARTICLE_LIKE, like,
                CounterMetric.ARTICLE_FAVORITE, favorite,
                CounterMetric.ARTICLE_COMMENT, comment,
                CounterMetric.ARTICLE_VIEW, view));
    }

    @Test
    void weighsInteractionsAndDividesByAge() {
        Counts counts = counts(10, 2, 5, 100);

        // (30 + 10 + 20 + 10) / (2 + 2)^1.5 = 70 / 8
        assertThat(DEFAULT.score(counts, 2)).isCloseTo(8.75, within(1e-9));
    }

    @Test
    void decaysAsArticleAges() {
        Counts counts = counts(9, 0, 0, 0);

        // 27 / (0 + 2)^1.5 ≈ 9.546；27 / (7 + 2)^1.5 = 1
        assertThat(DEFAULT.score(counts, 0)).isCloseTo(9.5459, within(1e-4));
        assertThat(DEFAULT.score(counts, 7)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void usesConfiguredWeightsAndGravity() {
        HotFormula formula = new HotFormula(1, 2, 3, 4, 2);

        // (1 + 2 + 3 + 4) / (3 + 2)^2 = 10 / 25
        assertThat(formula.score(counts(1, 1, 1, 1), 3)).isCloseTo(0.4, within(1e-9));
    }
}
