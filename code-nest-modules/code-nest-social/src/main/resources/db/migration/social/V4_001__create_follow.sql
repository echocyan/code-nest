-- 关系表：取关时物理删除；唯一键保证同一对用户至多一行
CREATE TABLE follow
(
    id          BIGINT   NOT NULL PRIMARY KEY,
    follower_id BIGINT   NOT NULL,
    author_id   BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL,
    UNIQUE KEY uk_follower_author (follower_id, author_id),
    KEY idx_author_follower (author_id, follower_id) COMMENT 'Feed 推送时按粉丝分页',
    KEY idx_author_id (author_id, id) COMMENT '粉丝列表按 id 倒序翻页',
    KEY idx_follower_id (follower_id, id) COMMENT '关注列表按 id 倒序翻页'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '关注';
