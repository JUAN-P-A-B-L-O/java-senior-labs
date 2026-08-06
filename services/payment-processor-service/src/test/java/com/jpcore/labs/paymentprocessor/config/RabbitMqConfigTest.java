package com.jpcore.labs.paymentprocessor.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RabbitMqConfigTest {

    @Autowired
    private Environment environment;

    @Autowired
    private Queue paymentProcessQueue;

    @Autowired
    private Queue paymentProcessDeadLetterQueue;

    @Autowired
    private DirectExchange paymentProcessDeadLetterExchange;

    @Autowired
    private Binding paymentProcessDeadLetterBinding;

    @Test
    void paymentProcessConsumerRetryIsConfigured() {
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.default-requeue-rejected", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.max-attempts", Integer.class))
                .isEqualTo(3);
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.initial-interval"))
                .isEqualTo("2s");
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.multiplier", Integer.class))
                .isEqualTo(1);
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.max-interval"))
                .isEqualTo("2s");
    }

    @Test
    void paymentProcessDeadLetterQueueIsConfigured() {
        Map<String, Object> arguments = paymentProcessQueue.getArguments();

        assertThat(paymentProcessQueue.getName()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_QUEUE);
        assertThat(arguments)
                .containsEntry("x-dead-letter-exchange", RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY);
        assertThat(paymentProcessDeadLetterExchange.getName())
                .isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE);
        assertThat(paymentProcessDeadLetterQueue.getName())
                .isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_QUEUE);
        assertThat(paymentProcessDeadLetterBinding.getExchange())
                .isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE);
        assertThat(paymentProcessDeadLetterBinding.getRoutingKey())
                .isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY);
        assertThat(paymentProcessDeadLetterBinding.getDestination())
                .isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_QUEUE);
    }
}
