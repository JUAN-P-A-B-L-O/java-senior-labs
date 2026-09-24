package com.jpcore.labs.payment.ai;

import com.jpcore.labs.payment.payment.PaymentStatus;

import java.math.BigDecimal;

public record PaymentAnalysisInput(
        BigDecimal amount,
        String currency,
        String description,
        PaymentStatus status
) {
}
