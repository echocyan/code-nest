package com.echocyan.codenest.user.service.impl;

import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import com.echocyan.codenest.user.convert.UserConverter;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.service.UserService;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class UserApiImpl implements UserApi {

    private final UserService userService;
    private final UserConverter userConverter;

    @Override
    public Map<Long, UserBrief> getBriefs(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(userIds).stream()
                .map(userConverter::toBrief)
                .collect(Collectors.toMap(UserBrief::id, Function.identity()));
    }

    @Override
    public boolean exists(long userId) {
        return userService.lambdaQuery().eq(User::getId, userId).exists();
    }
}
