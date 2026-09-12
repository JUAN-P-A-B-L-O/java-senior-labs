package com.jpcore.labs.payment.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestedMessage(
        UUID eventId,
        UUID traceId,
        String paymentId,
        BigDecimal amount,
        String currency,
        String description,
        String traceParent,
        String callerToken
) {
    public PaymentRequestedMessage(UUID eventId, UUID traceId, String paymentId, BigDecimal amount,
            String currency, String description, String traceParent) {
        this(eventId, traceId, paymentId, amount, currency, description, traceParent, null);
    }

    // Bearer credentials must never appear in logs, including record toString().
    @Override
    public String toString() { return "PaymentRequestedMessage[eventId=" + eventId + ", paymentId=" + paymentId + "]"; }

    public PaymentRequestedMessage(
            UUID eventId,
            UUID traceId,
            String paymentId,
            BigDecimal amount,
            String currency,
            String description
    ) {
        this(eventId, traceId, paymentId, amount, currency, description, null, null);
    }
}
