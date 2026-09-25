package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;

/**
 * 请求超出限额，由全局异常处理转换为 429 并设置 {@code Retry-After}。
 */
public class RateLimitedException extends BizException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(CommonErrorCode.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * 至少再等多少秒才可能放行。
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
