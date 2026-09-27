package com.echocyan.codenest.framework.mq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 消费队列按处理方法的事件参数订阅：订阅了哪些事件只由方法签名决定，对不上时启动失败。
 */
class EventQueuesTest {

    private static List<String> routingKeysOf(Declarables declarables, String queue) {
        return declarables.getDeclarablesByType(Binding.class).stream()
                .filter(binding -> binding.getDestination().equals(queue)
                        && binding.getExchange().equals(EventQueues.EXCHANGE))
                .map(Binding::getRoutingKey)
                .toList();
    }

    @Test
    void handlersOfAClassLevelListenerDecideTheBindings() {
        Declarables declarables = EventQueues.declare("test.handlers", HandlersListener.class);

        assertThat(routingKeysOf(declarables, "test.handlers")).containsExactlyInAnyOrder("test.created", "test.deleted");
    }

    @Test
    void onlyMethodsListeningToTheQueueDecideTheBindings() {
        Declarables declarables = EventQueues.declare("test.methods", MethodsListener.class);

        assertThat(routingKeysOf(declarables, "test.methods")).containsExactly("test.created");
    }

    @Test
    void listenerWithoutMatchingHandlersIsRejected() {
        assertThatThrownBy(() -> EventQueues.declare("test.other", HandlersListener.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.other");
        assertThatThrownBy(() -> EventQueues.declare("test.other", MethodsListener.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.other");
    }

    @Test
    void handlerWithoutAnEventParameterIsRejected() {
        assertThatThrownBy(() -> EventQueues.declare("test.untyped", UntypedListener.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DomainEvent");
    }

    @DomainEvent("test.created")
    record Created() {
    }

    @DomainEvent("test.deleted")
    record Deleted() {
    }

    @RabbitListener(queues = "test.handlers")
    @SuppressWarnings("unused")
    static class HandlersListener {

        @RabbitHandler
        void onCreated(Created event) {
        }

        @RabbitHandler
        void onDeleted(Deleted event, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        }
    }

    @SuppressWarnings("unused")
    static class MethodsListener {

        @RabbitListener(queues = "test.methods")
        void onCreated(Created event) {
        }

        @RabbitListener(queues = "test.elsewhere")
        void onDeleted(Deleted event) {
        }
    }

    @SuppressWarnings("unused")
    static class UntypedListener {

        @RabbitListener(queues = "test.untyped")
        void onMessage(String body) {
        }
    }
}
