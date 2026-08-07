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
    void savesPaymentRequestedEventAsPendingWithPayload() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventService service = new OutboxEventService(repository, new ObjectMapper());
        PaymentRequestedMessage message = new PaymentRequestedMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
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
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getPayload()).contains("\"eventId\":\"11111111-1111-1111-1111-111111111111\"");
        assertThat(savedEvent.getPayload()).contains("\"paymentId\":\"22222222-2222-2222-2222-222222222222\"");
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
                OutboxEventStatus.PENDING
        );

        service.markPublished(outboxEvent);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEvent.getPublishedAt()).isNotNull();
        verify(repository).saveAndFlush(outboxEvent);
    }
}
