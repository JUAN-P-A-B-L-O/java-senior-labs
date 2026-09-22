package com.jpcore.labs.paymentauthorization.authorization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);
    private static final Duration SHORT_SIMULATED_AUTHORIZATION_DELAY = Duration.ofSeconds(1);
    private static final Duration LONG_SIMULATED_AUTHORIZATION_DELAY = Duration.ofSeconds(7);

    private final BooleanSupplier authorizationDecision;
    private final Supplier<Duration> authorizationDelay;

    @Autowired
    public AuthorizationService(
            @Value("${payment-authorization.long-delay-probability-percent:50}") int longDelayProbabilityPercent) {
        this(() -> ThreadLocalRandom.current().nextBoolean(),
                () -> simulatedAuthorizationDelay(longDelayProbabilityPercent,
                        () -> ThreadLocalRandom.current().nextInt(100)));
        if (longDelayProbabilityPercent < 0 || longDelayProbabilityPercent > 100) {
            throw new IllegalArgumentException("Long delay probability percent must be between 0 and 100");
        }
    }

    AuthorizationService(BooleanSupplier authorizationDecision, Duration authorizationDelay) {
        this(authorizationDecision, () -> authorizationDelay);
    }

    AuthorizationService(BooleanSupplier authorizationDecision, Supplier<Duration> authorizationDelay) {
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
            Thread.sleep(authorizationDelay.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthorizationSimulationInterruptedException("Payment authorization interrupted", exception);
        }
    }

    static Duration simulatedAuthorizationDelay(int longDelayProbabilityPercent, IntSupplier randomPercent) {
        if (randomPercent.getAsInt() < longDelayProbabilityPercent) {
            return LONG_SIMULATED_AUTHORIZATION_DELAY;
        }
        return SHORT_SIMULATED_AUTHORIZATION_DELAY;
    }
}
