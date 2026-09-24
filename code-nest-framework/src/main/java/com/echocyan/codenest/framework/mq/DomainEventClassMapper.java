package com.echocyan.codenest.framework.mq;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.ClassMapper;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.util.ClassUtils;

/**
 * 用 AMQP {@code type} 属性里的事件类全限定名确定反序列化目标，只接受标注了 {@link DomainEvent} 的类。
 * 这样一个队列可以用多个 {@code @RabbitHandler} 按事件类型分派。
 */
class DomainEventClassMapper implements ClassMapper {

    @Override
    public void fromClass(Class<?> clazz, MessageProperties properties) {
        properties.setType(clazz.getName());
    }

    @Override
    public Class<?> toClass(MessageProperties properties) {
        String type = properties.getType();
        if (type == null) {
            throw new MessageConversionException("Message has no type property");
        }
        Class<?> clazz;
        try {
            clazz = ClassUtils.forName(type, DomainEventClassMapper.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new MessageConversionException("Unknown event type " + type, e);
        }
        if (!clazz.isAnnotationPresent(DomainEvent.class)) {
            throw new MessageConversionException(type + " is not annotated with @DomainEvent");
        }
        return clazz;
    }
}
