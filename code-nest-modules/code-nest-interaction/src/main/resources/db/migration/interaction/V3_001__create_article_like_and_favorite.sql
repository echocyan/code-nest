-- 关系表：取消时物理删除；唯一键保证每个用户对同一篇文章至多一行
CREATE TABLE article_like
(
    id         BIGINT   NOT NULL PRIMARY KEY,
    user_id    BIGINT   NOT NULL,
    article_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_article (user_id, article_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '点赞';

CREATE TABLE favorite
(
    id         BIGINT   NOT NULL PRIMARY KEY,
    user_id    BIGINT   NOT NULL,
    article_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_article (user_id, article_id),
    KEY idx_user_id (user_id, id) COMMENT '我的收藏按 id 倒序翻页'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '收藏';
