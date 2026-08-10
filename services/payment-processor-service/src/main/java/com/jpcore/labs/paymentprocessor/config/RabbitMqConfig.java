package com.jpcore.labs.paymentprocessor.config;

import com.jpcore.labs.paymentprocessor.payment.PaymentProcessedMessage;
import com.jpcore.labs.paymentprocessor.payment.PaymentProcessingFailedMessage;
import com.jpcore.labs.paymentprocessor.payment.PaymentRequestedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);

    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_PROCESS_QUEUE = "payment.process.queue";
    public static final String PAYMENT_PROCESS_ROUTING_KEY = "payment.process";
    public static final String PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE = "payment.process.dlx";
    public static final String PAYMENT_PROCESS_DEAD_LETTER_QUEUE = "payment.process.dlq";
    public static final String PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY = "payment.process.dlq";
    public static final String PAYMENT_RESULT_QUEUE = "payment.result.queue";
    public static final String PAYMENT_PROCESSED_ROUTING_KEY = "payment.processed";
    public static final String PAYMENT_PROCESSING_FAILED_ROUTING_KEY = "payment.processing.failed";
    public static final String PAYMENT_RESULT_DEAD_LETTER_EXCHANGE = "payment.result.dlx";
    public static final String PAYMENT_RESULT_DEAD_LETTER_QUEUE = "payment.result.dlq";
    public static final String PAYMENT_RESULT_DEAD_LETTER_ROUTING_KEY = "payment.result.dlq";

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentProcessQueue() {
        return QueueBuilder.durable(PAYMENT_PROCESS_QUEUE)
                .deadLetterExchange(PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding paymentProcessBinding(Queue paymentProcessQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentProcessQueue)
                .to(paymentExchange)
                .with(PAYMENT_PROCESS_ROUTING_KEY);
    }

    @Bean
    public DirectExchange paymentProcessDeadLetterExchange() {
        return new DirectExchange(PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentProcessDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_PROCESS_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding paymentProcessDeadLetterBinding(
            Queue paymentProcessDeadLetterQueue,
            DirectExchange paymentProcessDeadLetterExchange
    ) {
        return BindingBuilder.bind(paymentProcessDeadLetterQueue)
                .to(paymentProcessDeadLetterExchange)
                .with(PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Queue paymentResultQueue() {
        return QueueBuilder.durable(PAYMENT_RESULT_QUEUE)
                .deadLetterExchange(PAYMENT_RESULT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_RESULT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding paymentProcessedBinding(Queue paymentResultQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentResultQueue)
                .to(paymentExchange)
                .with(PAYMENT_PROCESSED_ROUTING_KEY);
    }

    @Bean
    public Binding paymentProcessingFailedBinding(Queue paymentResultQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentResultQueue)
                .to(paymentExchange)
                .with(PAYMENT_PROCESSING_FAILED_ROUTING_KEY);
    }

    @Bean
    public DirectExchange paymentResultDeadLetterExchange() {
        return new DirectExchange(PAYMENT_RESULT_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentResultDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_RESULT_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding paymentResultDeadLetterBinding(
            Queue paymentResultDeadLetterQueue,
            DirectExchange paymentResultDeadLetterExchange
    ) {
        return BindingBuilder.bind(paymentResultDeadLetterQueue)
                .to(paymentResultDeadLetterExchange)
                .with(PAYMENT_RESULT_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter messageConverter = new Jackson2JsonMessageConverter();
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setIdClassMapping(Map.of(
                "PaymentRequested", PaymentRequestedMessage.class,
                "com.jpcore.labs.payment.payment.PaymentRequestedMessage", PaymentRequestedMessage.class,
                "PaymentProcessed", PaymentProcessedMessage.class,
                "PaymentProcessingFailed", PaymentProcessingFailedMessage.class
        ));
        classMapper.afterPropertiesSet();
        messageConverter.setClassMapper(classMapper);
        return messageConverter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String correlationId = correlationData != null ? correlationData.getId() : "unknown";
            if (ack) {
                log.info("Payment result message confirmed by broker. correlationId={}", correlationId);
                return;
            }
            log.error("Payment result message was not confirmed by broker. correlationId={} cause={}", correlationId, cause);
        });
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "Payment result message returned by broker. replyCode={} replyText={} exchange={} routingKey={}",
                returned.getReplyCode(),
                returned.getReplyText(),
                returned.getExchange(),
                returned.getRoutingKey()
        ));
        return rabbitTemplate;
    }
}
