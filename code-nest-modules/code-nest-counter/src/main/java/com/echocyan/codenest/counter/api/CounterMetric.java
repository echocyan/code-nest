package com.echocyan.codenest.counter.api;

/**
 * 计数指标，名称以所属对象类型开头。
 */
public enum CounterMetric {

    ARTICLE_LIKE(CounterTarget.ARTICLE),
    ARTICLE_FAVORITE(CounterTarget.ARTICLE),
    ARTICLE_COMMENT(CounterTarget.ARTICLE),
    /**
     * 近似计数：不按访客去重，也不对账。
     */
    ARTICLE_VIEW(CounterTarget.ARTICLE),
    USER_FOLLOWER(CounterTarget.USER),
    USER_FOLLOWING(CounterTarget.USER),
    USER_ARTICLE(CounterTarget.USER),
    USER_LIKE_RECEIVED(CounterTarget.USER),
    COMMENT_REPLY(CounterTarget.COMMENT);

    private final CounterTarget target;

    CounterMetric(CounterTarget target) {
        this.target = target;
    }

    public CounterTarget target() {
        return target;
    }
}
