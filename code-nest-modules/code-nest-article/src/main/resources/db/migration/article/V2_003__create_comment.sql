CREATE TABLE comment
(
    id               BIGINT        NOT NULL PRIMARY KEY,
    article_id       BIGINT        NOT NULL,
    user_id          BIGINT        NOT NULL,
    root_id          BIGINT        NOT NULL COMMENT '0 表示评论，否则是挂在该评论下的回复',
    reply_to_user_id BIGINT        NULL COMMENT '回复 @某人；为空表示回复评论本身',
    content          VARCHAR(1000) NOT NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    created_at       DATETIME      NOT NULL,
    updated_at       DATETIME      NOT NULL,
    KEY idx_article_root_id (article_id, root_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '评论与回复';
