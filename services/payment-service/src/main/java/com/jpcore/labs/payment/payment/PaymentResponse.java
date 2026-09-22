package com.jpcore.labs.payment.payment;

import java.io.Serializable;
import java.math.BigDecimal;

public record PaymentResponse(
        String id,
        BigDecimal amount,
        String currency,
        String description,
        PaymentStatus status
) implements Serializable {
}
