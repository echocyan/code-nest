package com.echocyan.codenest.framework.mq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 可靠投递依赖的 RabbitMQ 配置缺失时启动失败，不会静默地丢消息。
 */
class MqConfigTest {

    private static RabbitProperties reliable() {
        RabbitProperties properties = new RabbitProperties();
        properties.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        properties.setPublisherReturns(true);
        properties.getTemplate().setMandatory(true);
        properties.getListener().getSimple().getRetry().setEnabled(true);
        return properties;
    }

    @Test
    void acceptsReliableSettings() {
        assertThatCode(() -> MqConfig.requireReliabilitySettings(reliable())).doesNotThrowAnyException();
    }

    @Test
    void rejectsDefaultSettingsListingWhatIsMissing() {
        assertThatThrownBy(() -> MqConfig.requireReliabilitySettings(new RabbitProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publisher-confirm-type")
                .hasMessageContaining("publisher-returns")
                .hasMessageContaining("template.mandatory")
                .hasMessageContaining("listener.simple.retry.enabled");
    }
}
