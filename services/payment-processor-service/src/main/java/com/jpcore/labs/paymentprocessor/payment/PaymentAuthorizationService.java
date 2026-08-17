package com.jpcore.labs.paymentprocessor.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

@Service
public class PaymentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationService.class);

    private final BooleanSupplier authorizationDecision;

    public PaymentAuthorizationService() {
        this(() -> ThreadLocalRandom.current().nextBoolean());
    }

    PaymentAuthorizationService(BooleanSupplier authorizationDecision) {
        this.authorizationDecision = authorizationDecision;
    }

    public boolean authorize(PaymentRequestedMessage message) {
        log.info("Authorization started. paymentId={} traceId={} eventId={}",
                message.paymentId(),
                message.traceId(),
                message.eventId()
        );

        if (authorizationDecision.getAsBoolean()) {
            log.info("Authorization succeeded. paymentId={} traceId={} eventId={}",
                    message.paymentId(),
                    message.traceId(),
                    message.eventId()
            );
            return true;
        }

        log.warn("Authorization failed. paymentId={} traceId={} eventId={}",
                message.paymentId(),
                message.traceId(),
                message.eventId()
        );
        throw new PaymentAuthorizationException("Payment authorization failed for paymentId=" + message.paymentId());
    }
}
