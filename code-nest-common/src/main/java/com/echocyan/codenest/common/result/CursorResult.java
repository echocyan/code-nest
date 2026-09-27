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

    /**
     * 把多查了一条的结果转为游标分页：多出的那条只用来判断是否还有下一页，下一页的游标是本页最后一条的 ID。
     *
     * @param rows 按游标方向排好序、最多 size + 1 条
     * @param idOf 取一条记录的 ID，即游标
     */
    public static <T> CursorResult<T> ofOverfetched(List<T> rows, int size, Function<? super T, Long> idOf) {
        if (rows.size() <= size) {
            return new CursorResult<>(rows, null, false);
        }
        List<T> page = rows.subList(0, size);
        return new CursorResult<>(page, idOf.apply(page.getLast()), true);
    }

    public <R> CursorResult<R> map(Function<? super T, ? extends R> mapper) {
        return new CursorResult<>(list.stream().<R>map(mapper).toList(), nextCursor, hasMore);
    }
}
