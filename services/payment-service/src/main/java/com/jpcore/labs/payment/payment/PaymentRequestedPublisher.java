package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.config.RabbitMqConfig;
import com.jpcore.labs.payment.outbox.OutboxEventEntity;
import com.jpcore.labs.payment.outbox.OutboxEventService;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentRequestedPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxEventService outboxEventService;

    public PaymentRequestedPublisher(RabbitTemplate rabbitTemplate, OutboxEventService outboxEventService) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxEventService = outboxEventService;
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
        OutboxEventEntity outboxEvent = outboxEventService.savePaymentRequested(message);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.PAYMENT_EXCHANGE,
                RabbitMqConfig.PAYMENT_PROCESS_ROUTING_KEY,
                message,
                correlationData
        );
        outboxEventService.markPublished(outboxEvent);
    }
}
