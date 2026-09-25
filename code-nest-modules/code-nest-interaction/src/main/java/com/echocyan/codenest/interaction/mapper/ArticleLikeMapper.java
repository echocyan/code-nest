package com.echocyan.codenest.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.interaction.entity.ArticleLike;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleLikeMapper extends BaseMapper<ArticleLike> {

    /**
     * 文章 ID 大于 afterId 的各文章点赞数，按文章 ID 升序。
     */
    @Select("""
            SELECT article_id, COUNT(*) FROM article_like
            WHERE article_id > #{afterId}
            GROUP BY article_id ORDER BY article_id LIMIT #{limit}
            """)
    List<IdCount> countByArticle(long afterId, int limit);

    /**
     * 作者 ID 大于 afterId 的各作者获赞数，按作者 ID 升序。
     */
    @Select("""
            SELECT author_id, COUNT(*) FROM article_like
            WHERE author_id > #{afterId}
            GROUP BY author_id ORDER BY author_id LIMIT #{limit}
            """)
    List<IdCount> countByAuthor(long afterId, int limit);
}
