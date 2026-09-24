package com.echocyan.codenest.user.api;

import java.util.Collection;
import java.util.Map;

/**
 * user 模块对其他模块的门面。
 */
public interface UserApi {

    /**
     * 批量查询用户简要信息。
     *
     * @return 以用户 ID 为 key；不存在的用户不出现在结果中
     */
    Map<Long, UserBrief> getBriefs(Collection<Long> userIds);

    /**
     * 用户是否存在。
     */
    boolean exists(long userId);
}
