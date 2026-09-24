package com.echocyan.codenest.user.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.user.dto.UpdateProfileRequest;
import com.echocyan.codenest.user.service.UserService;
import com.echocyan.codenest.user.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "我的资料")
    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        return Result.ok(userService.getProfile(AuthContext.currentUserId()));
    }

    @Operation(summary = "修改我的资料", description = "整体替换昵称、头像、简介；用户名不可修改")
    @PutMapping("/me")
    public Result<Void> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(AuthContext.currentUserId(), request);
        return Result.ok();
    }

    @SaIgnore
    @Operation(summary = "用户主页", description = "资料与粉丝数、关注数、文章数、获赞数")
    @GetMapping("/{id}")
    public Result<UserProfileVO> get(@PathVariable long id) {
        return Result.ok(userService.getProfile(id));
    }
}
