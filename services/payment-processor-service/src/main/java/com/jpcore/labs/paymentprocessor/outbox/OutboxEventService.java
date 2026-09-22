package com.jpcore.labs.paymentprocessor.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpcore.labs.paymentprocessor.payment.PaymentLogContext;
import com.jpcore.labs.paymentprocessor.payment.PaymentProcessedMessage;
import com.jpcore.labs.paymentprocessor.payment.PaymentProcessingFailedMessage;
import com.jpcore.labs.paymentprocessor.payment.TraceContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    public static final String PAYMENT_PROCESSED_KAFKA = "PaymentProcessedKafka";
    public static final String PAYMENT_PROCESSED = "PaymentProcessed";
    public static final String PAYMENT_PROCESSING_FAILED = "PaymentProcessingFailed";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final TraceContextProvider traceContextProvider;

    public OutboxEventService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            TraceContextProvider traceContextProvider
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.traceContextProvider = traceContextProvider;
    }

    @Transactional
    public OutboxEventEntity savePaymentProcessed(UUID requestedEventId, String paymentId) {
        return savePaymentProcessed(requestedEventId, null, paymentId, null);
    }

    @Transactional
    public OutboxEventEntity savePaymentProcessed(UUID requestedEventId, UUID traceId, String paymentId) {
        return savePaymentProcessed(requestedEventId, traceId, paymentId, null);
    }

    @Transactional
    public OutboxEventEntity savePaymentProcessed(
            UUID requestedEventId,
            UUID traceId,
            String paymentId,
            String fallbackTraceParent
    ) {
        try (PaymentLogContext ignored = PaymentLogContext.with(traceId, paymentId)) {
            PaymentProcessedMessage message = new PaymentProcessedMessage(
                    UUID.randomUUID(),
                    traceId,
                    requestedEventId,
                    paymentId,
                    currentOrFallbackTraceParent(fallbackTraceParent),
                    org.slf4j.MDC.get("requestedBy")
            );
            OutboxEventEntity outboxEvent = save(message.eventId(), UUID.fromString(paymentId), PAYMENT_PROCESSED, message);
            PaymentProcessedMessage kafkaMessage = new PaymentProcessedMessage(
                    UUID.randomUUID(), message.traceId(), message.requestedEventId(),
                    message.paymentId(), message.traceParent(), message.requestedBy()
            );
            save(kafkaMessage.eventId(), UUID.fromString(paymentId), PAYMENT_PROCESSED_KAFKA, kafkaMessage);
            log.info("Result event saved to outbox. eventId={} eventType={} requestedEventId={}",
                    outboxEvent.getEventId(),
                    outboxEvent.getEventType(),
                    requestedEventId
            );
            return outboxEvent;
        }
    }

    @Transactional
    public OutboxEventEntity savePaymentProcessingFailed(UUID requestedEventId, String paymentId, String reason) {
        return savePaymentProcessingFailed(requestedEventId, null, paymentId, reason, null);
    }

    @Transactional
    public OutboxEventEntity savePaymentProcessingFailed(UUID requestedEventId, UUID traceId, String paymentId, String reason) {
        return savePaymentProcessingFailed(requestedEventId, traceId, paymentId, reason, null);
    }

    @Transactional
    public OutboxEventEntity savePaymentProcessingFailed(
            UUID requestedEventId,
            UUID traceId,
            String paymentId,
            String reason,
            String fallbackTraceParent
    ) {
        try (PaymentLogContext ignored = PaymentLogContext.with(traceId, paymentId)) {
            PaymentProcessingFailedMessage message = new PaymentProcessingFailedMessage(
                    UUID.randomUUID(),
                    traceId,
                    requestedEventId,
                    paymentId,
                    reason,
                    currentOrFallbackTraceParent(fallbackTraceParent),
                    org.slf4j.MDC.get("requestedBy")
            );
            OutboxEventEntity outboxEvent = save(message.eventId(), UUID.fromString(paymentId), PAYMENT_PROCESSING_FAILED, message);
            log.info("Result event saved to outbox. eventId={} eventType={} requestedEventId={}",
                    outboxEvent.getEventId(),
                    outboxEvent.getEventType(),
                    requestedEventId
            );
            return outboxEvent;
        }
    }

    @Transactional(readOnly = true)
    public List<OutboxEventEntity> findWaitingPublish() {
        return outboxEventRepository.findByStatusAndEventTypeInOrderByCreatedAtAsc(
                OutboxEventStatus.WAITING_PUBLISH, List.of(PAYMENT_PROCESSED, PAYMENT_PROCESSING_FAILED));
    }

    @Transactional(readOnly = true)
    public List<OutboxEventEntity> findWaitingKafkaPublish() {
        return outboxEventRepository.findByStatusAndEventTypeInOrderByCreatedAtAsc(
                OutboxEventStatus.WAITING_PUBLISH, List.of(PAYMENT_PROCESSED_KAFKA));
    }

    public Object toMessage(OutboxEventEntity outboxEvent) {
        try {
            if (PAYMENT_PROCESSED.equals(outboxEvent.getEventType())
                    || PAYMENT_PROCESSED_KAFKA.equals(outboxEvent.getEventType())) {
                return objectMapper.readValue(outboxEvent.getPayload(), PaymentProcessedMessage.class);
            }
            if (PAYMENT_PROCESSING_FAILED.equals(outboxEvent.getEventType())) {
                return objectMapper.readValue(outboxEvent.getPayload(), PaymentProcessingFailedMessage.class);
            }
            throw new IllegalStateException("Unknown outbox event type: " + outboxEvent.getEventType());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize outbox event payload", exception);
        }
    }

    @Transactional
    public void markPublished(OutboxEventEntity outboxEvent) {
        outboxEvent.markPublished(Instant.now());
        outboxEventRepository.saveAndFlush(outboxEvent);
    }

    private OutboxEventEntity save(UUID eventId, UUID aggregateId, String eventType, Object message) {
        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                eventId,
                aggregateId,
                eventType,
                toPayload(message),
                OutboxEventStatus.WAITING_PUBLISH
        );

        return outboxEventRepository.saveAndFlush(outboxEvent);
    }

    private String toPayload(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outbox event payload", exception);
        }
    }

    private String currentOrFallbackTraceParent(String fallbackTraceParent) {
        String currentTraceParent = traceContextProvider.currentTraceParent();
        if (currentTraceParent != null) {
            return currentTraceParent;
        }
        return fallbackTraceParent;
    }
}
