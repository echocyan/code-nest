package com.echocyan.codenest.common.result;

import java.util.List;
import java.util.function.Function;

/**
 * 页码分页结果，page 从 1 开始。
 */
public record PageResult<T>(List<T> list, long total, long page, long size) {

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(list.stream().<R>map(mapper).toList(), total, page, size);
    }
}
