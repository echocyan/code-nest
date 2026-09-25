package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.Counts;

/**
 * 热度公式（仿 Hacker News）：{@code (点赞·like + 收藏·favorite + 评论·comment + 浏览·view) / (发布小时数 + 2)^gravity}。
 * 分母随时间增长，旧文章的热度持续下降。
 *
 * @param gravity 重力系数，越大衰减越快
 */
record HotFormula(double likeWeight, double favoriteWeight, double commentWeight, double viewWeight,
                  double gravity) {

    /**
     * @param hoursSincePublished 发布至今的小时数，可带小数
     */
    double score(Counts counts, double hoursSincePublished) {
        double weighted = likeWeight * counts.get(CounterMetric.ARTICLE_LIKE)
                + favoriteWeight * counts.get(CounterMetric.ARTICLE_FAVORITE)
                + commentWeight * counts.get(CounterMetric.ARTICLE_COMMENT)
                + viewWeight * counts.get(CounterMetric.ARTICLE_VIEW);
        return weighted / Math.pow(hoursSincePublished + 2, gravity);
    }
}
