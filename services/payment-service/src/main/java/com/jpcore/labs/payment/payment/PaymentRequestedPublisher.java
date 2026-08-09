package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.outbox.OutboxEventService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentRequestedPublisher {

    private final OutboxEventService outboxEventService;

    public PaymentRequestedPublisher(OutboxEventService outboxEventService) {
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
        outboxEventService.savePaymentRequested(message);
        System.out.println("cheguei2");

    }
}
