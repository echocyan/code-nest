-- 点赞时记下文章作者，获赞数可以直接按作者重新统计；文章作者不会变，已删除文章上的点赞同样计入
ALTER TABLE article_like
    ADD COLUMN author_id BIGINT NOT NULL COMMENT '被点赞文章的作者' AFTER article_id,
    ADD KEY idx_article_id (article_id) COMMENT '对账按文章统计点赞数',
    ADD KEY idx_author_id (author_id) COMMENT '对账按作者统计获赞数';

-- 一次性回填已有的点赞行；运行时代码仍不跨模块联表
UPDATE article_like l JOIN article a ON a.id = l.article_id
SET l.author_id = a.author_id;

ALTER TABLE favorite
    ADD KEY idx_article_id (article_id) COMMENT '对账按文章统计收藏数';
