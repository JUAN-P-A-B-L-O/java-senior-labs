package com.jpcore.labs.paymentauthorization.authorization;

import java.math.BigDecimal;
import java.util.UUID;

public record AuthorizationRequest(
        UUID eventId,
        UUID traceId,
        String paymentId,
        BigDecimal amount,
        String currency,
        String description
) {
}
