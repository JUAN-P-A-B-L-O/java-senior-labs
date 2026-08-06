package com.jpcore.labs.paymentprocessor.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRequestedListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void authorizesPaymentWhenMessageIsReceived() {
        PaymentAuthorizationService paymentAuthorizationService = mock(PaymentAuthorizationService.class);
        ProcessedEventService processedEventService = mock(ProcessedEventService.class);
        PaymentRequestedListener listener = new PaymentRequestedListener(paymentAuthorizationService, processedEventService);
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                EVENT_ID,
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(false);
        when(paymentAuthorizationService.authorize(message)).thenReturn(true);

        listener.listen(message);

        verify(paymentAuthorizationService).authorize(message);
        verify(processedEventService).markProcessed(EVENT_ID);
    }

    @Test
    void ignoresPaymentWhenEventWasAlreadyProcessed() {
        PaymentAuthorizationService paymentAuthorizationService = mock(PaymentAuthorizationService.class);
        ProcessedEventService processedEventService = mock(ProcessedEventService.class);
        PaymentRequestedListener listener = new PaymentRequestedListener(paymentAuthorizationService, processedEventService);
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                EVENT_ID,
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(true);

        listener.listen(message);

        verify(paymentAuthorizationService, never()).authorize(message);
        verify(processedEventService, never()).markProcessed(EVENT_ID);
    }

    @Test
    void throwsExceptionWhenAuthorizationFails() {
        PaymentAuthorizationService paymentAuthorizationService = mock(PaymentAuthorizationService.class);
        ProcessedEventService processedEventService = mock(ProcessedEventService.class);
        PaymentRequestedListener listener = new PaymentRequestedListener(paymentAuthorizationService, processedEventService);
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                EVENT_ID,
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
        PaymentAuthorizationException exception = new PaymentAuthorizationException(
                "Payment authorization failed for paymentId=payment-123"
        );
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(false);
        when(paymentAuthorizationService.authorize(message)).thenThrow(exception);

        assertThatThrownBy(() -> listener.listen(message))
                .isSameAs(exception);

        verify(paymentAuthorizationService).authorize(message);
        verify(processedEventService, never()).markProcessed(EVENT_ID);
    }
}
