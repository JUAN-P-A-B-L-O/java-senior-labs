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
        String traceParent
) {
    public PaymentRequestedMessage(
            UUID eventId,
            UUID traceId,
            String paymentId,
            BigDecimal amount,
            String currency,
            String description
    ) {
        this(eventId, traceId, paymentId, amount, currency, description, null);
    }
}
