package com.echocyan.codenest.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.interaction.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 文章 ID 大于 afterId 的各文章收藏数，按文章 ID 升序。
     */
    @Select("""
            SELECT article_id, COUNT(*) FROM favorite
            WHERE article_id > #{afterId}
            GROUP BY article_id ORDER BY article_id LIMIT #{limit}
            """)
    List<IdCount> countByArticle(long afterId, int limit);
}
