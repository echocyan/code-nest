package com.echocyan.codenest.search.controller;

import com.echocyan.codenest.search.service.ArticleIndex;
import com.echocyan.codenest.search.service.ArticleIndex.RebuildResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 手动触发搜索索引重建：{@code POST /actuator/search-rebuild}，只在管理端口上提供，只在 es 档装配。
 */
@Component
@Endpoint(id = "search-rebuild")
@ConditionalOnProperty(name = "search.mode", havingValue = "es")
@RequiredArgsConstructor
class SearchRebuildEndpoint {

    private final ArticleIndex articleIndex;

    /**
     * 同步执行，重建结束后返回。
     *
     * @return 200 与新索引名、导入和追补的文章数；已有重建在运行时 409
     */
    @WriteOperation
    public WebEndpointResponse<RebuildResult> rebuild() {
        return articleIndex.rebuild()
                .map(WebEndpointResponse::new)
                .orElseGet(() -> new WebEndpointResponse<>(HttpStatus.CONFLICT.value()));
    }
}
