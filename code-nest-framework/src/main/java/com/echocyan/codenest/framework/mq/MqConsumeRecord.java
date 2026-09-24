package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 某条消息已被某个消费者处理过，(messageId, consumer) 唯一。
 */
@Getter
@Setter
@TableName("mq_consume_record")
public class MqConsumeRecord extends AuditableEntity {

    private Long id;

    private String messageId;

    /** 消费队列名。 */
    private String consumer;
}
