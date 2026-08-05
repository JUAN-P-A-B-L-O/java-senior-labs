package com.jpcore.labs.paymentprocessor.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRequestedListenerTest {

    @Test
    void authorizesPaymentWhenMessageIsReceived() {
        PaymentAuthorizationService paymentAuthorizationService = mock(PaymentAuthorizationService.class);
        PaymentRequestedListener listener = new PaymentRequestedListener(paymentAuthorizationService);
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
        when(paymentAuthorizationService.authorize(message)).thenReturn(true);

        listener.listen(message);

        verify(paymentAuthorizationService).authorize(message);
    }

    @Test
    void throwsExceptionWhenAuthorizationFails() {
        PaymentAuthorizationService paymentAuthorizationService = mock(PaymentAuthorizationService.class);
        PaymentRequestedListener listener = new PaymentRequestedListener(paymentAuthorizationService);
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
        PaymentAuthorizationException exception = new PaymentAuthorizationException(
                "Payment authorization failed for paymentId=payment-123"
        );
        when(paymentAuthorizationService.authorize(message)).thenThrow(exception);

        assertThatThrownBy(() -> listener.listen(message))
                .isSameAs(exception);

        verify(paymentAuthorizationService).authorize(message);
    }
}
