package com.echocyan.codenest.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.interaction.entity.Favorite;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 已收藏过时什么也不做。
     *
     * @return 实际插入的行数，0 表示已存在
     */
    @Insert("""
            INSERT IGNORE INTO favorite (id, user_id, article_id, created_at, updated_at)
            VALUES (#{id}, #{userId}, #{articleId}, #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(Favorite favorite);
}
