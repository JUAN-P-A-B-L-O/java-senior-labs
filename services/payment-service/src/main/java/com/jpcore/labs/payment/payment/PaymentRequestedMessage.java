package com.jpcore.labs.payment.payment;

import java.math.BigDecimal;

public record PaymentRequestedMessage(
        String paymentId,
        BigDecimal amount,
        String currency,
        String description
) {
}
