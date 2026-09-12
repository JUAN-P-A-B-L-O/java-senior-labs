package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.outbox.OutboxEventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentRequestedPublisherTest {

    @Test
    void savesPaymentRequestedMessageToOutboxWithEventId() {
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        TraceContextProvider traceContextProvider = mock(TraceContextProvider.class);
        PaymentRequestedPublisher publisher = new PaymentRequestedPublisher(outboxEventService, traceContextProvider);
        PaymentEntity payment = new PaymentEntity(
                new BigDecimal("100.50"),
                "BRL",
                "test payment",
                PaymentStatus.PROCESSING
        );
        UUID paymentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID traceId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        ReflectionTestUtils.setField(payment, "id", paymentId);
        ArgumentCaptor<PaymentRequestedMessage> messageCaptor = ArgumentCaptor.forClass(PaymentRequestedMessage.class);
        String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        org.mockito.Mockito.when(traceContextProvider.currentTraceParent()).thenReturn(traceParent);

        publisher.publish(payment, traceId);

        verify(outboxEventService).savePaymentRequested(messageCaptor.capture());
        PaymentRequestedMessage message = messageCaptor.getValue();
        assertThat(message.eventId()).isNotNull();
        assertThat(message.traceId()).isEqualTo(traceId);
        assertThat(message.paymentId()).isEqualTo(paymentId.toString());
        assertThat(message.amount()).isEqualByComparingTo("100.50");
        assertThat(message.currency()).isEqualTo("BRL");
        assertThat(message.description()).isEqualTo("test payment");
        assertThat(message.traceParent()).isEqualTo(traceParent);
    }
    @Test
    void preservesCallerTokenInOutboxAndRedactsItFromToString() {
        var outbox = mock(OutboxEventService.class);
        var publisher = new PaymentRequestedPublisher(outbox, mock(TraceContextProvider.class));
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("private-user-token")
                .header("alg", "RS256").subject("comum").build();
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            var payment = new PaymentEntity(BigDecimal.ONE, "BRL", "identity", PaymentStatus.PROCESSING);
            ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
            publisher.publish(payment);
            var captor = ArgumentCaptor.forClass(PaymentRequestedMessage.class);
            verify(outbox).savePaymentRequested(captor.capture());
            assertThat(captor.getValue().callerToken()).isEqualTo("private-user-token");
            assertThat(captor.getValue().toString()).doesNotContain("private-user-token");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
