package com.jpcore.labs.paymentprocessor.payment;

import java.util.UUID;

public record PaymentProcessedMessage(
        UUID eventId,
        UUID requestedEventId,
        String paymentId
) {
}
