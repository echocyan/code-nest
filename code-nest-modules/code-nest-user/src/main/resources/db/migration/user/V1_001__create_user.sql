CREATE TABLE `user`
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    username      VARCHAR(20)  NOT NULL COMMENT '登录名，注册后不可修改',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    nickname      VARCHAR(20)  NOT NULL,
    avatar_url    VARCHAR(512) NULL,
    bio           VARCHAR(200) NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '用户';
