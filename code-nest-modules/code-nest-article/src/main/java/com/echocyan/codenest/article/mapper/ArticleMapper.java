package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 按 ID 查询，已删除的文章也返回；自定义 SQL 不会被追加逻辑删除条件。
     */
    @Select("SELECT * FROM article WHERE id = #{id}")
    Article selectByIdIncludingDeleted(long id);
}
