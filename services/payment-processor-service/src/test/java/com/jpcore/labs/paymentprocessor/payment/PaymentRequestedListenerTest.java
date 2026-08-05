package com.jpcore.labs.paymentprocessor.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

        listener.listen(message);

        verify(paymentAuthorizationService).authorize(message);
    }
}
