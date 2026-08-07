package com.jpcore.labs.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpcore.labs.payment.payment.PaymentRequestedMessage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxEventService {

    private static final String PAYMENT_REQUESTED = "PaymentRequested";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public OutboxEventEntity savePaymentRequested(PaymentRequestedMessage message) {
        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                message.eventId(),
                UUID.fromString(message.paymentId()),
                PAYMENT_REQUESTED,
                toPayload(message),
                OutboxEventStatus.PENDING
        );

        return outboxEventRepository.saveAndFlush(outboxEvent);
    }

    public void markPublished(OutboxEventEntity outboxEvent) {
        outboxEvent.markPublished(Instant.now());
        outboxEventRepository.saveAndFlush(outboxEvent);
    }

    private String toPayload(PaymentRequestedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outbox event payload", exception);
        }
    }
}
