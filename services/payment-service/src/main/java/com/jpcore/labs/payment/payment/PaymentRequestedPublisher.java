package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.outbox.OutboxEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentRequestedPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestedPublisher.class);

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
        log.info("PaymentRequested saved to outbox. paymentId={} eventId={}", payment.getId(), eventId);
    }
}
