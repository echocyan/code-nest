package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.counter.api.IdCount;
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
}
