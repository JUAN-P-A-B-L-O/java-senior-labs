package com.jpcore.labs.payment.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestedMessage(
        UUID eventId,
        String paymentId,
        BigDecimal amount,
        String currency,
        String description
) {
}
