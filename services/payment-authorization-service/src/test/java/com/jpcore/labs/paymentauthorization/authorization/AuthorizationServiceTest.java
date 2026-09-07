package com.jpcore.labs.paymentauthorization.authorization;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServiceTest {

    @Test
    void returnsAuthorizationDecision() {
        AuthorizationService service = new AuthorizationService(() -> true, Duration.ZERO);

        AuthorizationResponse response = service.authorize(authorizationRequest());

        assertThat(response.authorized()).isTrue();
    }

    @Test
    void selectsSimulatedAuthorizationDelay() {
        assertThat(AuthorizationService.simulatedAuthorizationDelay(() -> false)).isEqualTo(Duration.ofSeconds(1));
        assertThat(AuthorizationService.simulatedAuthorizationDelay(() -> true)).isEqualTo(Duration.ofSeconds(7));
    }

    private AuthorizationRequest authorizationRequest() {
        return new AuthorizationRequest(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
    }
}
