package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 一条待发送或已发送的事件，id 兼作消息的 messageId。
 */
@Getter
@Setter
@TableName("mq_outbox")
public class MqOutbox extends AuditableEntity {

    private Long id;

    private String routingKey;

    /** 事件类的全限定名。 */
    private String eventType;

    /** 事件的 JSON。 */
    private String payload;

    private OutboxStatus status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;
}
