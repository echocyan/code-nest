CREATE TABLE category
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    name       VARCHAR(32) NOT NULL,
    sort       INT         NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '文章分类（系统预置）';

CREATE TABLE tag
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    name       VARCHAR(32) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '文章标签（系统预置）';

-- 预置数据使用固定的小整数 ID，便于造数和测试引用
INSERT INTO category (id, name, sort)
VALUES (1, '后端', 10),
       (2, '前端', 20),
       (3, '移动开发', 30),
       (4, '人工智能', 40),
       (5, '数据库', 50),
       (6, '运维与云原生', 60),
       (7, '架构设计', 70),
       (8, '开发工具', 80),
       (9, '职业成长', 90);

INSERT INTO tag (id, name)
VALUES (1, 'Java'),
       (2, 'Spring Boot'),
       (3, 'JVM'),
       (4, '并发编程'),
       (5, 'MySQL'),
       (6, 'Redis'),
       (7, 'RabbitMQ'),
       (8, 'Kafka'),
       (9, 'Elasticsearch'),
       (10, 'MyBatis'),
       (11, '微服务'),
       (12, '分布式'),
       (13, '高并发'),
       (14, '性能优化'),
       (15, '设计模式'),
       (16, '算法'),
       (17, 'Go'),
       (18, 'Python'),
       (19, 'JavaScript'),
       (20, 'TypeScript'),
       (21, 'Vue'),
       (22, 'React'),
       (23, 'Docker'),
       (24, 'Kubernetes'),
       (25, 'Linux'),
       (26, 'Git'),
       (27, '计算机网络'),
       (28, '安全'),
       (29, '大模型'),
       (30, '面试');
