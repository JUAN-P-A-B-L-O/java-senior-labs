package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentRequestedPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PaymentRequestedPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(PaymentEntity payment) {
        UUID eventId = UUID.randomUUID();
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                eventId,
                payment.getId().toString(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription()
        );
        CorrelationData correlationData = new CorrelationData(eventId.toString());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.PAYMENT_EXCHANGE,
                RabbitMqConfig.PAYMENT_PROCESS_ROUTING_KEY,
                message,
                correlationData
        );
    }
}
