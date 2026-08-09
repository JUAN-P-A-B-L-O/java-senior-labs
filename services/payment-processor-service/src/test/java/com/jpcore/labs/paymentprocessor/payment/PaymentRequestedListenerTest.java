package com.jpcore.labs.paymentprocessor.payment;

import com.jpcore.labs.paymentprocessor.config.RabbitMqConfig;
import com.jpcore.labs.paymentprocessor.outbox.OutboxEventService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRequestedListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PAYMENT_ID = "22222222-2222-2222-2222-222222222222";

    @Test
    void authorizesPaymentWhenMessageIsReceived() {
        PaymentAuthorizationService paymentAuthorizationService = mock(PaymentAuthorizationService.class);
        ProcessedEventService processedEventService = mock(ProcessedEventService.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        PaymentRequestedListener listener = new PaymentRequestedListener(
                paymentAuthorizationService,
                processedEventService,
                outboxEventService
        );
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                EVENT_ID,
                PAYMENT_ID,
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(false);
        when(paymentAuthorizationService.authorize(message)).thenReturn(true);

        listener.listen(message);

        verify(paymentAuthorizationService).authorize(message);
        verify(outboxEventService).savePaymentProcessed(EVENT_ID, PAYMENT_ID);
        verify(processedEventService).markProcessed(EVENT_ID);
    }

    @Test
    void ignoresPaymentWhenEventWasAlreadyProcessed() {
        PaymentAuthorizationService paymentAuthorizationService = mock(PaymentAuthorizationService.class);
        ProcessedEventService processedEventService = mock(ProcessedEventService.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        PaymentRequestedListener listener = new PaymentRequestedListener(
                paymentAuthorizationService,
                processedEventService,
                outboxEventService
        );
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                EVENT_ID,
                PAYMENT_ID,
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(true);

        listener.listen(message);

        verify(paymentAuthorizationService, never()).authorize(message);
        verify(outboxEventService, never()).savePaymentProcessed(EVENT_ID, PAYMENT_ID);
        verify(processedEventService, never()).markProcessed(EVENT_ID);
    }

    @Test
    void publishesPaymentProcessingFailedWhenAuthorizationFails() {
        PaymentAuthorizationService paymentAuthorizationService = mock(PaymentAuthorizationService.class);
        ProcessedEventService processedEventService = mock(ProcessedEventService.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        PaymentRequestedListener listener = new PaymentRequestedListener(
                paymentAuthorizationService,
                processedEventService,
                outboxEventService
        );
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                EVENT_ID,
                PAYMENT_ID,
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
        PaymentAuthorizationException exception = new PaymentAuthorizationException(
                "Payment authorization failed for paymentId=" + PAYMENT_ID
        );
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(false);
        when(paymentAuthorizationService.authorize(message)).thenThrow(exception);

        listener.listen(message);

        verify(paymentAuthorizationService).authorize(message);
        verify(outboxEventService).savePaymentProcessingFailed(
                EVENT_ID,
                PAYMENT_ID,
                "Payment authorization failed for paymentId=" + PAYMENT_ID
        );
        verify(processedEventService).markProcessed(EVENT_ID);
    }

    @Test
    void convertsPublishedPaymentRequestedMessageAndReachesListener() {
        PaymentAuthorizationService paymentAuthorizationService = mock(PaymentAuthorizationService.class);
        ProcessedEventService processedEventService = mock(ProcessedEventService.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        PaymentRequestedListener listener = new PaymentRequestedListener(
                paymentAuthorizationService,
                processedEventService,
                outboxEventService
        );
        Jackson2JsonMessageConverter messageConverter =
                (Jackson2JsonMessageConverter) new RabbitMqConfig().jsonMessageConverter();
        MessageProperties properties = new MessageProperties();
        properties.setHeader("__TypeId__", "PaymentRequested");
        Message amqpMessage = new Message(
                """
                        {
                          "eventId": "11111111-1111-1111-1111-111111111111",
                          "paymentId": "22222222-2222-2222-2222-222222222222",
                          "amount": 10,
                          "currency": "BRL",
                          "description": "test payment"
                        }
                        """.getBytes(StandardCharsets.UTF_8),
                properties
        );
        PaymentRequestedMessage message = (PaymentRequestedMessage) messageConverter.fromMessage(amqpMessage);
        when(processedEventService.isProcessed(EVENT_ID)).thenReturn(false);
        when(paymentAuthorizationService.authorize(message)).thenReturn(true);

        listener.listen(message);

        verify(paymentAuthorizationService).authorize(message);
        verify(outboxEventService).savePaymentProcessed(EVENT_ID, PAYMENT_ID);
        verify(processedEventService).markProcessed(EVENT_ID);
    }
}
