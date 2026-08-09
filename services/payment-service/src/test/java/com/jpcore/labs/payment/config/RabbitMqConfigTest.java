package com.jpcore.labs.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import com.jpcore.labs.payment.payment.PaymentRequestedMessage;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMqConfigTest {

    private final RabbitMqConfig rabbitMqConfig = new RabbitMqConfig();

    @Test
    void paymentProcessDeadLetterQueueIsConfigured() {
        Queue paymentProcessQueue = rabbitMqConfig.paymentProcessQueue();
        Queue paymentResultQueue = rabbitMqConfig.paymentResultQueue();
        DirectExchange deadLetterExchange = rabbitMqConfig.paymentProcessDeadLetterExchange();
        Queue deadLetterQueue = rabbitMqConfig.paymentProcessDeadLetterQueue();
        DirectExchange resultDeadLetterExchange = rabbitMqConfig.paymentResultDeadLetterExchange();
        Queue resultDeadLetterQueue = rabbitMqConfig.paymentResultDeadLetterQueue();
        Binding deadLetterBinding = rabbitMqConfig.paymentProcessDeadLetterBinding(
                deadLetterQueue,
                deadLetterExchange
        );
        Binding paymentProcessedBinding = rabbitMqConfig.paymentProcessedBinding(
                paymentResultQueue,
                rabbitMqConfig.paymentExchange()
        );
        Binding paymentProcessingFailedBinding = rabbitMqConfig.paymentProcessingFailedBinding(
                paymentResultQueue,
                rabbitMqConfig.paymentExchange()
        );
        Binding resultDeadLetterBinding = rabbitMqConfig.paymentResultDeadLetterBinding(
                resultDeadLetterQueue,
                resultDeadLetterExchange
        );
        Map<String, Object> arguments = paymentProcessQueue.getArguments();
        Map<String, Object> resultArguments = paymentResultQueue.getArguments();

        assertThat(paymentProcessQueue.getName()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_QUEUE);
        assertThat(arguments)
                .containsEntry("x-dead-letter-exchange", RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY);
        assertThat(deadLetterExchange.getName()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE);
        assertThat(deadLetterQueue.getName()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_QUEUE);
        assertThat(deadLetterBinding.getExchange()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE);
        assertThat(deadLetterBinding.getRoutingKey()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY);
        assertThat(deadLetterBinding.getDestination()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESS_DEAD_LETTER_QUEUE);
        assertThat(paymentResultQueue.getName()).isEqualTo(RabbitMqConfig.PAYMENT_RESULT_QUEUE);
        assertThat(resultArguments)
                .containsEntry("x-dead-letter-exchange", RabbitMqConfig.PAYMENT_RESULT_DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMqConfig.PAYMENT_RESULT_DEAD_LETTER_ROUTING_KEY);
        assertThat(paymentProcessedBinding.getRoutingKey()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESSED_ROUTING_KEY);
        assertThat(paymentProcessingFailedBinding.getRoutingKey())
                .isEqualTo(RabbitMqConfig.PAYMENT_PROCESSING_FAILED_ROUTING_KEY);
        assertThat(resultDeadLetterQueue.getName()).isEqualTo(RabbitMqConfig.PAYMENT_RESULT_DEAD_LETTER_QUEUE);
        assertThat(resultDeadLetterBinding.getExchange()).isEqualTo(RabbitMqConfig.PAYMENT_RESULT_DEAD_LETTER_EXCHANGE);
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

    @Test
    void paymentRequestedMessagesUseStableTypeId() {
        Jackson2JsonMessageConverter messageConverter =
                (Jackson2JsonMessageConverter) rabbitMqConfig.jsonMessageConverter();
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "22222222-2222-2222-2222-222222222222",
                new BigDecimal("100.50"),
                "BRL",
                "test payment"
        );

        Message amqpMessage = messageConverter.toMessage(message, null);

        assertThat(amqpMessage.getMessageProperties().getHeaders())
                .containsEntry("__TypeId__", "PaymentRequested");
    }
}
