-- 计数表以业务 ID 为主键；某个对象还没有计数行时，读取按全 0 处理
CREATE TABLE article_stat
(
    article_id     BIGINT   NOT NULL PRIMARY KEY,
    like_count     BIGINT   NOT NULL DEFAULT 0,
    favorite_count BIGINT   NOT NULL DEFAULT 0,
    comment_count  BIGINT   NOT NULL DEFAULT 0,
    view_count     BIGINT   NOT NULL DEFAULT 0,
    created_at     DATETIME NOT NULL,
    updated_at     DATETIME NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '文章计数';

CREATE TABLE user_stat
(
    user_id             BIGINT   NOT NULL PRIMARY KEY,
    follower_count      BIGINT   NOT NULL DEFAULT 0,
    following_count     BIGINT   NOT NULL DEFAULT 0,
    article_count       BIGINT   NOT NULL DEFAULT 0,
    like_received_count BIGINT   NOT NULL DEFAULT 0,
    created_at          DATETIME NOT NULL,
    updated_at          DATETIME NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '用户计数';

CREATE TABLE comment_stat
(
    comment_id  BIGINT   NOT NULL PRIMARY KEY,
    reply_count BIGINT   NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '评论计数';
