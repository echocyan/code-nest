CREATE TABLE article
(
    id           BIGINT       NOT NULL PRIMARY KEY,
    author_id    BIGINT       NOT NULL,
    category_id  BIGINT       NOT NULL,
    title        VARCHAR(100) NOT NULL,
    summary      VARCHAR(200) NOT NULL COMMENT '作者未填写时截取正文开头',
    cover_url    VARCHAR(512) NULL,
    status       TINYINT      NOT NULL COMMENT '0 草稿，1 已发布',
    published_at DATETIME     NULL,
    version      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁，每次编辑、发布 +1',
    deleted      TINYINT      NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    KEY idx_author_status_published (author_id, status, published_at),
    KEY idx_category_status_published (category_id, status, published_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '文章';

CREATE TABLE article_content
(
    article_id BIGINT     NOT NULL PRIMARY KEY,
    content    MEDIUMTEXT NOT NULL COMMENT 'Markdown 原文',
    created_at DATETIME   NOT NULL,
    updated_at DATETIME   NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '文章正文，与 article 垂直拆分';

CREATE TABLE article_tag
(
    article_id BIGINT   NOT NULL,
    tag_id     BIGINT   NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (article_id, tag_id),
    KEY idx_tag_article (tag_id, article_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '文章与标签的关联';
