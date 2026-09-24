package com.echocyan.codenest.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.user.UserErrorCode;
import com.echocyan.codenest.user.dto.UpdateProfileRequest;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.vo.UserProfileVO;

/**
 * 用户注册、登录校验与资料。
 */
public interface UserService extends IService<User> {

    /**
     * 注册新用户，昵称默认等于用户名。用户名是否重复由唯一索引判定，并发注册同名时也只有一个成功。
     *
     * @throws BizException {@link UserErrorCode#USERNAME_TAKEN}
     */
    User register(String username, String password);

    /**
     * 校验用户名与密码。用户不存在与密码错误返回同一个错误，不暴露用户名是否已注册。
     *
     * @throws BizException {@link UserErrorCode#BAD_CREDENTIALS}
     */
    User authenticate(String username, String password);

    /**
     * 用户资料与四项计数。
     *
     * @throws BizException {@link UserErrorCode#USER_NOT_FOUND}
     */
    UserProfileVO getProfile(long id);

    /**
     * 整体替换可修改的资料字段，null 会写入数据库。
     */
    void updateProfile(long userId, UpdateProfileRequest request);
}
