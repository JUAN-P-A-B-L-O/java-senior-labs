package com.jpcore.labs.payment.payment;

import java.util.UUID;

public record PaymentProcessingFailedMessage(
        UUID eventId,
        UUID traceId,
        UUID requestedEventId,
        String paymentId,
        String reason,
        String traceParent,
        String requestedBy
) {
    public PaymentProcessingFailedMessage(UUID eventId, UUID traceId, UUID requestedEventId, String paymentId, String reason, String traceParent) {
        this(eventId, traceId, requestedEventId, paymentId, reason, traceParent, null);
    }

    public PaymentProcessingFailedMessage(
            UUID eventId,
            UUID traceId,
            UUID requestedEventId,
            String paymentId,
            String reason
    ) {
        this(eventId, traceId, requestedEventId, paymentId, reason, null);
    }
}
