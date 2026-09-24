package com.echocyan.codenest.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import com.echocyan.codenest.user.convert.UserConverter;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.mapper.UserMapper;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class UserApiImpl implements UserApi {

    private final UserMapper userMapper;
    private final UserConverter userConverter;

    @Override
    public Map<Long, UserBrief> getBriefs(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(userIds).stream()
                .map(userConverter::toBrief)
                .collect(Collectors.toMap(UserBrief::id, Function.identity()));
    }

    @Override
    public boolean exists(long userId) {
        return userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getId, userId));
    }
}
