package com.jpcore.labs.paymentprocessor.payment;

import java.util.UUID;

public record PaymentProcessingFailedMessage(
        UUID eventId,
        UUID traceId,
        UUID requestedEventId,
        String paymentId,
        String reason,
        String traceParent
) {
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
