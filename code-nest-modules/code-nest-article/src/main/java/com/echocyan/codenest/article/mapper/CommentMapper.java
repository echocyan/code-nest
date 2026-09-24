package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.Comment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 自定义 SQL 不经过 {@code @TableLogic} 的自动过滤，已删除的行也会查到，用于"该评论已删除"的展示。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 按 ID 查询，包括已删除的。
     */
    @Select("SELECT * FROM comment WHERE id = #{id}")
    Comment selectByIdIncludingDeleted(long id);

    /**
     * 文章中 ID 小于 cursor 的评论，按 ID 倒序；已删除的评论只有下面还有回复时才返回。
     */
    @Select("""
            SELECT * FROM comment c
            WHERE c.article_id = #{articleId} AND c.root_id = 0 AND c.id < #{cursor}
              AND (c.deleted = 0 OR EXISTS (
                  SELECT 1 FROM comment r
                  WHERE r.article_id = c.article_id AND r.root_id = c.id AND r.deleted = 0))
            ORDER BY c.id DESC
            LIMIT #{limit}
            """)
    List<Comment> selectVisibleComments(long articleId, long cursor, int limit);
}
