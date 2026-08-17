package com.jpcore.labs.paymentprocessor.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAuthorizationServiceTest {

    private PaymentAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentAuthorizationService(() -> true);
    }

    @Test
    void returnsTrueWhenRandomAuthorizationApprovesPayment() {
        boolean authorized = service.authorize(paymentRequestedMessage());

        assertThat(authorized).isTrue();
    }

    @Test
    void throwsExceptionWhenRandomAuthorizationDeniesPayment() {
        service = new PaymentAuthorizationService(() -> false);

        assertThatThrownBy(() -> service.authorize(paymentRequestedMessage()))
                .isInstanceOf(PaymentAuthorizationException.class)
                .hasMessage("Payment authorization failed for paymentId=payment-123");
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
