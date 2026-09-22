package com.jpcore.labs.payment.payment;

import java.util.UUID;

public record PaymentProcessedMessage(
        UUID eventId,
        UUID traceId,
        UUID requestedEventId,
        String paymentId,
        String traceParent,
        String requestedBy
) {
    public PaymentProcessedMessage(UUID eventId, UUID traceId, UUID requestedEventId, String paymentId, String traceParent) {
        this(eventId, traceId, requestedEventId, paymentId, traceParent, null);
    }

    public PaymentProcessedMessage(
            UUID eventId,
            UUID traceId,
            UUID requestedEventId,
            String paymentId
    ) {
        this(eventId, traceId, requestedEventId, paymentId, null);
    }
}
