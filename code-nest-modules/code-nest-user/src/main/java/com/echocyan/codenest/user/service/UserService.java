package com.echocyan.codenest.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.user.UserErrorCode;
import com.echocyan.codenest.user.dto.UpdateProfileRequest;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final UserMapper userMapper;

    /**
     * 注册新用户，昵称默认等于用户名。用户名是否重复由唯一索引判定，并发注册同名时也只有一个成功。
     *
     * @throws BizException {@link UserErrorCode#USERNAME_TAKEN}
     */
    public User register(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(username);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(UserErrorCode.USERNAME_TAKEN);
        }
        return user;
    }

    /**
     * 校验用户名与密码。用户不存在与密码错误返回同一个错误，不暴露用户名是否已注册。
     */
    public User authenticate(String username, String password) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(UserErrorCode.BAD_CREDENTIALS);
        }
        return user;
    }

    /**
     * 按 ID 查询用户。
     *
     * @throws BizException {@link UserErrorCode#USER_NOT_FOUND}
     */
    public User getById(long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 整体替换可修改的资料字段，null 会写入数据库。
     */
    public void updateProfile(long userId, UpdateProfileRequest request) {
        // 传入空实体是为了触发 updated_at 的自动填充
        userMapper.update(new User(), Wrappers.<User>lambdaUpdate()
                .set(User::getNickname, request.nickname())
                .set(User::getAvatarUrl, request.avatarUrl())
                .set(User::getBio, request.bio())
                .eq(User::getId, userId));
    }
}
