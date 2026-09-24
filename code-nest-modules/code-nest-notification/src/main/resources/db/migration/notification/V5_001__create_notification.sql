-- 通知只存 ID，展示信息读取时组装；点赞、关注的 dedup_key 保证同一动作只通知一次
CREATE TABLE notification
(
    id           BIGINT      NOT NULL PRIMARY KEY,
    recipient_id BIGINT      NOT NULL,
    actor_id     BIGINT      NOT NULL,
    type         TINYINT     NOT NULL COMMENT '1 点赞，2 评论，3 回复，4 关注',
    article_id   BIGINT      NULL,
    comment_id   BIGINT      NULL COMMENT '评论或回复本身的 ID',
    dedup_key    VARCHAR(64) NULL COMMENT '点赞 L:{actor}:{article}，关注 F:{actor}:{author}，评论与回复为 NULL',
    is_read      TINYINT     NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,
    UNIQUE KEY uk_dedup_key (dedup_key),
    KEY idx_recipient_id (recipient_id, id) COMMENT '通知列表按 id 倒序翻页',
    KEY idx_recipient_read (recipient_id, is_read) COMMENT '未读数'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '通知';
