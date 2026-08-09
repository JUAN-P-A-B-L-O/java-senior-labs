package com.jpcore.labs.payment.payment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentResultListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REQUESTED_EVENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String PAYMENT_ID = "22222222-2222-2222-2222-222222222222";

    @Test
    void completesPaymentWhenPaymentProcessedIsReceived() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentResultProcessedEventService processedEventService = mock(PaymentResultProcessedEventService.class);
        PaymentResultListener listener = new PaymentResultListener(paymentService, processedEventService);
        PaymentProcessedMessage message = new PaymentProcessedMessage(EVENT_ID, REQUESTED_EVENT_ID, PAYMENT_ID);
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(false);

        listener.listen(message);

        verify(paymentService).completePayment(PAYMENT_ID);
        verify(processedEventService).markProcessed(EVENT_ID);
    }

    @Test
    void failsPaymentWhenPaymentProcessingFailedIsReceived() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentResultProcessedEventService processedEventService = mock(PaymentResultProcessedEventService.class);
        PaymentResultListener listener = new PaymentResultListener(paymentService, processedEventService);
        PaymentProcessingFailedMessage message = new PaymentProcessingFailedMessage(
                EVENT_ID,
                REQUESTED_EVENT_ID,
                PAYMENT_ID,
                "denied"
        );
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(false);

        listener.listen(message);

        verify(paymentService).failPayment(PAYMENT_ID);
        verify(processedEventService).markProcessed(EVENT_ID);
    }

    @Test
    void ignoresAlreadyProcessedResultEvent() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentResultProcessedEventService processedEventService = mock(PaymentResultProcessedEventService.class);
        PaymentResultListener listener = new PaymentResultListener(paymentService, processedEventService);
        PaymentProcessedMessage message = new PaymentProcessedMessage(EVENT_ID, REQUESTED_EVENT_ID, PAYMENT_ID);
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(true);

        listener.listen(message);

        verify(paymentService, never()).completePayment(PAYMENT_ID);
        verify(processedEventService, never()).markProcessed(EVENT_ID);
    }
}
