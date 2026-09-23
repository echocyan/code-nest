package com.echocyan.codenest.common.result;

import java.util.List;
import java.util.function.Function;

/**
 * 游标分页结果。nextCursor 作为下一页请求的 cursor 参数；没有下一页时 hasMore 为 false。
 */
public record CursorResult<T>(List<T> list, Long nextCursor, boolean hasMore) {

    public static <T> CursorResult<T> empty() {
        return new CursorResult<>(List.of(), null, false);
    }

    public <R> CursorResult<R> map(Function<? super T, ? extends R> mapper) {
        return new CursorResult<>(list.stream().<R>map(mapper).toList(), nextCursor, hasMore);
    }
}
