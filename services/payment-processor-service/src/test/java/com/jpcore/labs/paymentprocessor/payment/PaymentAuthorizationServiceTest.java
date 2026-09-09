package com.jpcore.labs.paymentprocessor.payment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAuthorizationServiceTest {

    @Test
    void returnsTrueWhenAuthorizationClientApprovesPayment() {
        PaymentAuthorizationService service = new PaymentAuthorizationService(
                ignored -> new PaymentAuthorizationClient.AuthorizationResponse(true)
        );

        boolean authorized = service.authorize(paymentRequestedMessage());

        assertThat(authorized).isTrue();
    }

    @Test
    void throwsExceptionWhenAuthorizationClientDeniesPayment() {
        PaymentAuthorizationService service = new PaymentAuthorizationService(
                ignored -> new PaymentAuthorizationClient.AuthorizationResponse(false)
        );

        assertThatThrownBy(() -> service.authorize(paymentRequestedMessage()))
                .isInstanceOf(PaymentAuthorizationException.class)
                .hasMessage("Payment authorization failed for paymentId=payment-123");
    }

    @Test
    void throwsUnavailableExceptionWhenAuthorizationClientFails() {
        PaymentAuthorizationService service = new PaymentAuthorizationService(
                ignored -> {
                    throw new ResourceAccessException("connection refused");
                }
        );

        assertThatThrownBy(() -> service.authorize(paymentRequestedMessage()))
                .isInstanceOf(PaymentAuthorizationUnavailableException.class)
                .hasMessage("Payment authorization unavailable for paymentId=payment-123")
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    void throwsTemporarilyUnavailableExceptionWhenAuthorizationCircuitBreakerIsOpen() {
        CircuitBreaker circuitBreaker = PaymentAuthorizationClientAdapter.circuitBreaker(3, 3, 50.0f);
        PaymentAuthorizationService service = new PaymentAuthorizationService(
                ignored -> {
                    throw CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
                }
        );

        assertThatThrownBy(() -> service.authorize(paymentRequestedMessage()))
                .isInstanceOf(AuthorizationTemporarilyUnavailableException.class)
                .hasMessage("Payment authorization temporarily unavailable: circuit breaker open for paymentId=payment-123")
                .hasCauseInstanceOf(CallNotPermittedException.class);
    }

    private PaymentRequestedMessage paymentRequestedMessage() {
        return new PaymentRequestedMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
    }
}
