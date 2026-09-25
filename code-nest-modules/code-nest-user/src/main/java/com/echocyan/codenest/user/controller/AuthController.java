package com.echocyan.codenest.user.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.RateLimit;
import com.echocyan.codenest.user.dto.LoginRequest;
import com.echocyan.codenest.user.dto.RegisterRequest;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.service.UserService;
import com.echocyan.codenest.user.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.echocyan.codenest.framework.ratelimit.RateLimit.Dimension.IP;

@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    private static LoginVO loginAs(User user) {
        return new LoginVO(user.getId(), AuthContext.login(user.getId()));
    }

    @SaIgnore
    @Operation(summary = "注册", description = "注册成功后自动登录")
    @RateLimit(key = "register-per-hour", limit = "${rate-limit.limits.register-per-hour}", window = "1h",
            dimension = IP)
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(loginAs(userService.register(request.username(), request.password())));
    }

    @SaIgnore
    @Operation(summary = "登录", description = "之后的请求以 Authorization: Bearer <token> 携带凭证")
    @RateLimit(key = "login-per-minute", limit = "${rate-limit.limits.login-per-minute}", window = "1m", dimension = IP)
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(loginAs(userService.authenticate(request.username(), request.password())));
    }

    @Operation(summary = "退出登录", description = "只让当前设备的 token 失效")
    @PostMapping("/logout")
    public Result<Void> logout() {
        AuthContext.logout();
        return Result.ok();
    }
}
