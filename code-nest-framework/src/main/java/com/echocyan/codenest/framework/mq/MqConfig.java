package com.echocyan.codenest.framework.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/**
 * 消息可靠性底座的公共配置。本地重试次数与间隔在 {@code spring.rabbitmq.listener.simple.retry} 中配置，
 * 重试耗尽后由这里的 {@link MessageRecoverer} 打告警日志并拒绝消息，broker 把它转入死信队列。
 */
@Slf4j
@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class MqConfig {

    @Bean
    public TopicExchange eventExchange() {
        return EventQueues.exchange();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return EventQueues.deadLetterExchange();
    }

    /**
     * 监听器方法的参数按消息的 {@code type} 属性从 JSON 反序列化，与 Web 层共用同一套 Jackson 约定。
     */
    @Bean
    public MessageConverter eventMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setClassMapper(new DomainEventClassMapper());
        return converter;
    }

    @Bean
    public MessageRecoverer deadLetterRecoverer() {
        return (message, cause) -> {
            MessageProperties properties = message.getMessageProperties();
            log.error("Retries exhausted, moving message to dead letter queue: queue={}, messageId={}, type={}",
                    properties.getConsumerQueue(), properties.getMessageId(), properties.getType(), cause);
            throw new AmqpRejectAndDontRequeueException("Retries exhausted", cause);
        };
    }

    /**
     * 在监听器执行前记下当前消息，供 {@link IdempotentConsumer} 取 messageId 与消费队列名。
     */
    @Bean
    public ContainerCustomizer<SimpleMessageListenerContainer> consumingMessageRecorder() {
        return container -> container.addAfterReceivePostProcessors(IdempotentConsumerAspect::remember);
    }
}
