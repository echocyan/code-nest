-- 本地消息表：与业务数据同一事务写入，提交后发送，补发任务兜底
CREATE TABLE mq_outbox
(
    id            BIGINT       NOT NULL PRIMARY KEY COMMENT '兼作消息的 messageId',
    routing_key   VARCHAR(128) NOT NULL,
    event_type    VARCHAR(255) NOT NULL COMMENT '事件类的全限定名',
    payload       JSON         NOT NULL,
    status        TINYINT      NOT NULL COMMENT '0 待发送，1 已发送，2 发送失败',
    retry_count   INT          NOT NULL DEFAULT 0,
    next_retry_at DATETIME     NOT NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    KEY idx_status_next_retry (status, next_retry_at) COMMENT '补发扫描到期的待发送记录',
    KEY idx_status_created (status, created_at) COMMENT '清理过期的已发送记录'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT 'MQ 本地消息表';

-- 消费记录：唯一键保证同一条消息在同一个消费者上只处理一次
CREATE TABLE mq_consume_record
(
    id         BIGINT       NOT NULL PRIMARY KEY,
    message_id VARCHAR(64)  NOT NULL,
    consumer   VARCHAR(128) NOT NULL COMMENT '消费队列名',
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    UNIQUE KEY uk_message_consumer (message_id, consumer)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT 'MQ 消费记录';
