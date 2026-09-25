package com.echocyan.codenest.support.probe;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仅存在于测试类路径，用来从 HTTP 层验证 framework 的 Web 约定。
 */
@SaIgnore
@RestController
@RequestMapping("/probe")
public class ConventionProbeController {

    @GetMapping("/sample")
    public Result<Sample> sample() {
        return Result.ok(
                new Sample(1234567890123456789L, LocalDateTime.of(2026, 9, 23, 10, 0), Status.PUBLISHED, null));
    }

    @PostMapping("/echo")
    public Result<NameBody> echo(@Valid @RequestBody NameBody body) {
        return Result.ok(body);
    }

    @GetMapping("/size")
    public Result<Integer> size(@RequestParam @Max(50) int size) {
        return Result.ok(size);
    }

    @GetMapping("/page")
    public Result<PageResult<Long>> page() {
        return Result.ok(new PageResult<>(List.of(1234567890123456789L), 42, 1, 20));
    }

    @GetMapping("/header")
    public Result<String> header(@RequestHeader("X-Required") String value) {
        return Result.ok(value);
    }

    @GetMapping("/forbidden")
    public Result<Void> forbidden() {
        throw new BizException(CommonErrorCode.FORBIDDEN);
    }

    @GetMapping("/boom")
    public Result<Void> boom() {
        throw new IllegalStateException("boom");
    }

    public enum Status {DRAFT, PUBLISHED}

    public record Sample(Long id, LocalDateTime at, Status status, String nothing) {
    }

    public record NameBody(@NotBlank String name, LocalDateTime at) {
    }
}
