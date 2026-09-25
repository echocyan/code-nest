package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.counter.api.IdCount;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 作者 ID 大于 afterId 的各作者未删除的已发布文章数，按作者 ID 升序。
     */
    @Select("""
            SELECT author_id, COUNT(*) FROM article
            WHERE author_id > #{afterId} AND status = 1 AND deleted = 0
            GROUP BY author_id ORDER BY author_id LIMIT #{limit}
            """)
    List<IdCount> countPublishedByAuthor(long afterId, int limit);

    /**
     * 按 ID 查询，已删除的文章也返回；自定义 SQL 不会被追加逻辑删除条件。
     */
    @Select("SELECT * FROM article WHERE id = #{id}")
    Article selectByIdIncludingDeleted(long id);

    /**
     * {@code updated_at} 不早于 since、ID 大于 afterId 的文章，已删除的也返回，按 ID 正序。
     */
    @Select("""
            SELECT * FROM article
            WHERE updated_at >= #{since} AND id > #{afterId}
            ORDER BY id LIMIT #{limit}
            """)
    List<Article> selectUpdatedSinceIncludingDeleted(LocalDateTime since, long afterId, int limit);
}
