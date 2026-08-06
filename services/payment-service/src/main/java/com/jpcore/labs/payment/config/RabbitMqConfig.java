package com.jpcore.labs.payment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_PROCESS_QUEUE = "payment.process.queue";
    public static final String PAYMENT_PROCESS_ROUTING_KEY = "payment.process";
    public static final String PAYMENT_PROCESS_DEAD_LETTER_EXCHANGE = "payment.process.dlx";
    public static final String PAYMENT_PROCESS_DEAD_LETTER_QUEUE = "payment.process.dlq";
    public static final String PAYMENT_PROCESS_DEAD_LETTER_ROUTING_KEY = "payment.process.dlq";

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
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
