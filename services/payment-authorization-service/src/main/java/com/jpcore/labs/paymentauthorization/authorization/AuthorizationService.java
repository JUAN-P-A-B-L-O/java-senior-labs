package com.jpcore.labs.paymentauthorization.authorization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);
    private static final Duration SIMULATED_AUTHORIZATION_DELAY = Duration.ofSeconds(5);

    private final BooleanSupplier authorizationDecision;
    private final Duration authorizationDelay;

    public AuthorizationService() {
        this(() -> ThreadLocalRandom.current().nextBoolean(), SIMULATED_AUTHORIZATION_DELAY);
    }

    AuthorizationService(BooleanSupplier authorizationDecision, Duration authorizationDelay) {
        this.authorizationDecision = authorizationDecision;
        this.authorizationDelay = authorizationDelay;
    }

    public AuthorizationResponse authorize(AuthorizationRequest request) {
        log.info("Authorization requested. eventId={} paymentId={}", request.eventId(), request.paymentId());
        waitForAuthorizationSimulation();

        boolean authorized = authorizationDecision.getAsBoolean();
        log.info("Authorization completed. eventId={} paymentId={} authorized={}",
                request.eventId(),
                request.paymentId(),
                authorized
        );
        return new AuthorizationResponse(authorized);
    }

    private void waitForAuthorizationSimulation() {
        try {
            Thread.sleep(authorizationDelay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthorizationSimulationInterruptedException("Payment authorization interrupted", exception);
        }
    }
}
