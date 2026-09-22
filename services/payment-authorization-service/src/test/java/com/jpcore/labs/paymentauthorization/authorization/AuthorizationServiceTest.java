package com.jpcore.labs.paymentauthorization.authorization;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AuthorizationServiceTest {

    @Test
    void returnsAuthorizationDecision() {
        AuthorizationService service = new AuthorizationService(() -> true, Duration.ZERO);

        AuthorizationResponse response = service.authorize(authorizationRequest());

        assertThat(response.authorized()).isTrue();
    }

    @Test
    void selectsSimulatedAuthorizationDelay() {
        assertThat(AuthorizationService.simulatedAuthorizationDelay(30, () -> 29)).isEqualTo(Duration.ofSeconds(7));
        assertThat(AuthorizationService.simulatedAuthorizationDelay(30, () -> 30)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void supportsNeverAndAlwaysUsingLongDelay() {
        assertThat(AuthorizationService.simulatedAuthorizationDelay(0, () -> 0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(AuthorizationService.simulatedAuthorizationDelay(100, () -> 99)).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void rejectsInvalidLongDelayProbability() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationService(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationService(101));
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
