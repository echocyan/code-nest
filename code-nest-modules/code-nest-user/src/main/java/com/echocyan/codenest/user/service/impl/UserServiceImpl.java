package com.echocyan.codenest.user.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.framework.cache.TwoLevelCache;
import com.echocyan.codenest.user.UserErrorCode;
import com.echocyan.codenest.user.api.UserBrief;
import com.echocyan.codenest.user.convert.UserConverter;
import com.echocyan.codenest.user.dto.UpdateProfileRequest;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.mapper.UserMapper;
import com.echocyan.codenest.user.service.UserService;
import com.echocyan.codenest.user.vo.UserCountsVO;
import com.echocyan.codenest.user.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final UserConverter userConverter;
    private final CounterApi counterApi;
    private final TwoLevelCache<UserBrief> briefCache;

    @Override
    public User register(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(username);
        try {
            save(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(UserErrorCode.USERNAME_TAKEN);
        }
        return user;
    }

    @Override
    public User authenticate(String username, String password) {
        User user = lambdaQuery().eq(User::getUsername, username).one();
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(UserErrorCode.BAD_CREDENTIALS);
        }
        return user;
    }

    @Override
    public UserProfileVO getProfile(long id) {
        User user = getById(id);
        if (user == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND);
        }
        Counts counts = counterApi.get(CounterTarget.USER, List.of(id)).get(id);
        return userConverter.toProfileVO(user, new UserCountsVO(
                counts.get(CounterMetric.USER_FOLLOWER),
                counts.get(CounterMetric.USER_FOLLOWING),
                counts.get(CounterMetric.USER_ARTICLE),
                counts.get(CounterMetric.USER_LIKE_RECEIVED)));
    }

    @Override
    public void updateProfile(long userId, UpdateProfileRequest request) {
        // 传入空实体是为了触发 updated_at 的自动填充
        lambdaUpdate()
                .set(User::getNickname, request.nickname())
                .set(User::getAvatarUrl, request.avatarUrl())
                .set(User::getBio, request.bio())
                .eq(User::getId, userId)
                .update(new User());
        briefCache.evict(userId);
    }
}
