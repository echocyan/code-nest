package com.echocyan.codenest.user.convert;

import com.echocyan.codenest.user.api.UserBrief;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.vo.UserCountsVO;
import com.echocyan.codenest.user.vo.UserProfileVO;
import org.mapstruct.Mapper;

@Mapper
public interface UserConverter {

    UserProfileVO toProfileVO(User user, UserCountsVO counts);

    UserBrief toBrief(User user);
}
