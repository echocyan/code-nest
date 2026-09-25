package com.echocyan.codenest.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.social.entity.Follow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    /**
     * 用户 ID 大于 afterId 的各用户粉丝数，按用户 ID 升序。
     */
    @Select("""
            SELECT author_id, COUNT(*) FROM follow
            WHERE author_id > #{afterId}
            GROUP BY author_id ORDER BY author_id LIMIT #{limit}
            """)
    List<IdCount> countByAuthor(long afterId, int limit);

    /**
     * 用户 ID 大于 afterId 的各用户关注数，按用户 ID 升序。
     */
    @Select("""
            SELECT follower_id, COUNT(*) FROM follow
            WHERE follower_id > #{afterId}
            GROUP BY follower_id ORDER BY follower_id LIMIT #{limit}
            """)
    List<IdCount> countByFollower(long afterId, int limit);
}
