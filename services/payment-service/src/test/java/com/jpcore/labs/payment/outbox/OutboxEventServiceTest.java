package com.jpcore.labs.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpcore.labs.payment.payment.PaymentRequestedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventServiceTest {

    @Test
    void savesPaymentRequestedEventAsWaitingPublishWithPayload() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventService service = new OutboxEventService(repository, new ObjectMapper());
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "22222222-2222-2222-2222-222222222222",
                new BigDecimal("100.50"),
                "BRL",
                "test payment"
        );
        when(repository.saveAndFlush(any(OutboxEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);

        OutboxEventEntity result = service.savePaymentRequested(message);

        verify(repository).saveAndFlush(captor.capture());
        OutboxEventEntity savedEvent = captor.getValue();
        assertThat(result).isSameAs(savedEvent);
        assertThat(savedEvent.getEventId()).isEqualTo(message.eventId());
        assertThat(savedEvent.getAggregateId()).isEqualTo(UUID.fromString(message.paymentId()));
        assertThat(savedEvent.getEventType()).isEqualTo("PaymentRequested");
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.WAITING_PUBLISH);
        assertThat(savedEvent.getPayload()).contains("\"eventId\":\"11111111-1111-1111-1111-111111111111\"");
        assertThat(savedEvent.getPayload()).contains("\"traceId\":\"33333333-3333-3333-3333-333333333333\"");
        assertThat(savedEvent.getPayload()).contains("\"paymentId\":\"22222222-2222-2222-2222-222222222222\"");
    }

    @Test
    void findsWaitingPublishEvents() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventService service = new OutboxEventService(repository, new ObjectMapper());

        service.findWaitingPublish();

        verify(repository).findByStatusOrderByCreatedAtAsc(OutboxEventStatus.WAITING_PUBLISH);
    }

    @Test
    void deserializesPayloadToPaymentRequestedMessage() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventService service = new OutboxEventService(repository, new ObjectMapper());
        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "PaymentRequested",
                """
                        {
                          "eventId": "11111111-1111-1111-1111-111111111111",
                          "traceId": "33333333-3333-3333-3333-333333333333",
                          "paymentId": "22222222-2222-2222-2222-222222222222",
                          "amount": 100.50,
                          "currency": "BRL",
                          "description": "test payment"
                        }
                        """,
                OutboxEventStatus.WAITING_PUBLISH
        );

        PaymentRequestedMessage message = service.toPaymentRequestedMessage(outboxEvent);

        assertThat(message.eventId()).isEqualTo(outboxEvent.getEventId());
        assertThat(message.traceId()).isEqualTo(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        assertThat(message.paymentId()).isEqualTo(outboxEvent.getAggregateId().toString());
        assertThat(message.amount()).isEqualByComparingTo("100.50");
        assertThat(message.currency()).isEqualTo("BRL");
        assertThat(message.description()).isEqualTo("test payment");
    }

    @Test
    void marksEventAsPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventService service = new OutboxEventService(repository, new ObjectMapper());
        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "PaymentRequested",
                "{}",
                OutboxEventStatus.WAITING_PUBLISH
        );

        service.markPublished(outboxEvent);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEvent.getPublishedAt()).isNotNull();
        verify(repository).saveAndFlush(outboxEvent);
    }
}
