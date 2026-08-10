package com.jpcore.labs.paymentprocessor.config;

import com.jpcore.labs.paymentprocessor.payment.PaymentRequestedMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
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
    private Queue paymentResultQueue;

    @Autowired
    private Queue paymentResultDeadLetterQueue;

    @Autowired
    private DirectExchange paymentProcessDeadLetterExchange;

    @Autowired
    private DirectExchange paymentResultDeadLetterExchange;

    @Autowired
    private Binding paymentProcessDeadLetterBinding;

    @Autowired
    private Binding paymentProcessedBinding;

    @Autowired
    private Binding paymentProcessingFailedBinding;

    @Autowired
    private Binding paymentResultDeadLetterBinding;

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
        Map<String, Object> resultArguments = paymentResultQueue.getArguments();

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
        assertThat(paymentResultQueue.getName()).isEqualTo(RabbitMqConfig.PAYMENT_RESULT_QUEUE);
        assertThat(resultArguments)
                .containsEntry("x-dead-letter-exchange", RabbitMqConfig.PAYMENT_RESULT_DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMqConfig.PAYMENT_RESULT_DEAD_LETTER_ROUTING_KEY);
        assertThat(paymentProcessedBinding.getRoutingKey()).isEqualTo(RabbitMqConfig.PAYMENT_PROCESSED_ROUTING_KEY);
        assertThat(paymentProcessingFailedBinding.getRoutingKey())
                .isEqualTo(RabbitMqConfig.PAYMENT_PROCESSING_FAILED_ROUTING_KEY);
        assertThat(paymentResultDeadLetterExchange.getName())
                .isEqualTo(RabbitMqConfig.PAYMENT_RESULT_DEAD_LETTER_EXCHANGE);
        assertThat(paymentResultDeadLetterQueue.getName()).isEqualTo(RabbitMqConfig.PAYMENT_RESULT_DEAD_LETTER_QUEUE);
        assertThat(paymentResultDeadLetterBinding.getExchange())
                .isEqualTo(RabbitMqConfig.PAYMENT_RESULT_DEAD_LETTER_EXCHANGE);
    }

    @Test
    void paymentResultPublisherConfirmsAreConfigured() {
        assertThat(environment.getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualTo("correlated");
        assertThat(environment.getProperty("spring.rabbitmq.publisher-returns", Boolean.class)).isTrue();
    }

    @Test
    void convertsStablePaymentRequestedTypeIdToLocalMessageContract() {
        Jackson2JsonMessageConverter messageConverter =
                (Jackson2JsonMessageConverter) new RabbitMqConfig().jsonMessageConverter();
        Message amqpMessage = paymentRequestedAmqpMessage("PaymentRequested");

        Object result = messageConverter.fromMessage(amqpMessage);

        assertThat(result).isInstanceOf(PaymentRequestedMessage.class);
        PaymentRequestedMessage message = (PaymentRequestedMessage) result;
        assertThat(message.paymentId()).isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(message.currency()).isEqualTo("BRL");
    }

    @Test
    void convertsLegacyProducerClassTypeIdToLocalMessageContract() {
        Jackson2JsonMessageConverter messageConverter =
                (Jackson2JsonMessageConverter) new RabbitMqConfig().jsonMessageConverter();
        Message amqpMessage = paymentRequestedAmqpMessage("com.jpcore.labs.payment.payment.PaymentRequestedMessage");

        Object result = messageConverter.fromMessage(amqpMessage);

        assertThat(result).isInstanceOf(PaymentRequestedMessage.class);
    }

    private Message paymentRequestedAmqpMessage(String typeId) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("__TypeId__", typeId);
        return new Message(
                """
                        {
                          "eventId": "11111111-1111-1111-1111-111111111111",
                          "paymentId": "22222222-2222-2222-2222-222222222222",
                          "amount": 100.50,
                          "currency": "BRL",
                          "description": "test payment"
                        }
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                properties
        );
    }
}
