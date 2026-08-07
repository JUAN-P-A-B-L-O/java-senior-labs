package com.jpcore.labs.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMqConfigTest {

    private final RabbitMqConfig rabbitMqConfig = new RabbitMqConfig();

    @Test
    void paymentProcessDeadLetterQueueIsConfigured() {
        Queue paymentProcessQueue = rabbitMqConfig.paymentProcessQueue();
        DirectExchange deadLetterExchange = rabbitMqConfig.paymentProcessDeadLetterExchange();
        Queue deadLetterQueue = rabbitMqConfig.paymentProcessDeadLetterQueue();
        Binding deadLetterBinding = rabbitMqConfig.paymentProcessDeadLetterBinding(
                deadLetterQueue,
                deadLetterExchange
        );
        Map<String, Object> arguments = paymentProcessQueue.getArguments();

        assertThat(paymentProcessQueue.getName()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_QUEUE);
        assertThat(arguments)
                .containsEntry("x-dead-letter-exchange", RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY);
        assertThat(deadLetterExchange.getName()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE);
        assertThat(deadLetterQueue.getName()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_QUEUE);
        assertThat(deadLetterBinding.getExchange()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE);
        assertThat(deadLetterBinding.getRoutingKey()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY);
        assertThat(deadLetterBinding.getDestination()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_QUEUE);
    }

    @Test
    void rabbitTemplateIsConfiguredForPublisherConfirms() {
        RabbitTemplate rabbitTemplate = rabbitMqConfig.rabbitTemplate(
                mock(ConnectionFactory.class),
                rabbitMqConfig.jsonMessageConverter()
        );

        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "mandatoryExpression")).isNotNull();
        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "confirmCallback")).isNotNull();
        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "returnsCallback")).isNotNull();
    }
}
