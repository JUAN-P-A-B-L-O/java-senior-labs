package com.jpcore.labs.payment.payment;

import java.util.UUID;

public record PaymentProcessedMessage(
        UUID eventId,
        UUID requestedEventId,
        String paymentId
) {
}
