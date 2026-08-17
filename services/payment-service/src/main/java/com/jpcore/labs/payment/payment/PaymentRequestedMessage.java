package com.jpcore.labs.payment.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestedMessage(
        UUID eventId,
        UUID traceId,
        String paymentId,
        BigDecimal amount,
        String currency,
        String description
) {
}
