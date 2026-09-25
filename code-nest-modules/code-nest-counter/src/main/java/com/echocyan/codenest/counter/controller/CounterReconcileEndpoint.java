package com.echocyan.codenest.counter.controller;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.service.CounterReconcileService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 手动触发计数对账：{@code POST /actuator/counter-reconcile}，只在管理端口上提供。
 */
@Component
@Endpoint(id = "counter-reconcile")
@RequiredArgsConstructor
class CounterReconcileEndpoint {

    private final CounterReconcileService counterReconcileService;

    /**
     * 同步执行，对账结束后返回。
     *
     * @return 200 与各指标被修正的对象数；其他实例正在对账时 409
     */
    @WriteOperation
    public WebEndpointResponse<Map<CounterMetric, Long>> reconcile() {
        return counterReconcileService.reconcile()
                .map(WebEndpointResponse::new)
                .orElseGet(() -> new WebEndpointResponse<>(HttpStatus.CONFLICT.value()));
    }
}
