package com.jpcore.labs.paymentprocessor.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpcore.labs.paymentprocessor.payment.PaymentProcessedMessage;
import com.jpcore.labs.paymentprocessor.payment.PaymentProcessingFailedMessage;
import com.jpcore.labs.paymentprocessor.payment.TraceContextProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class OutboxEventServiceTest {

    private static final UUID REQUESTED_EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRACE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String PAYMENT_ID = "22222222-2222-2222-2222-222222222222";

    @Test
    void savesPaymentProcessedEventAsWaitingPublish() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        TraceContextProvider traceContextProvider = mock(TraceContextProvider.class);
        OutboxEventService service = new OutboxEventService(repository, new ObjectMapper(), traceContextProvider);
        when(traceContextProvider.currentTraceParent())
                .thenReturn("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        when(repository.saveAndFlush(any(OutboxEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);

        OutboxEventEntity result;
        try (var ignored = com.jpcore.labs.paymentprocessor.payment.PaymentLogContext.with(TRACE_ID, PAYMENT_ID, "alice")) {
            result = service.savePaymentProcessed(REQUESTED_EVENT_ID, TRACE_ID, PAYMENT_ID);
        }
        assertThat(((PaymentProcessedMessage) service.toMessage(result)).requestedBy()).isEqualTo("alice");

        verify(repository, times(2)).saveAndFlush(captor.capture());
        assertThat(result).isSameAs(captor.getAllValues().getFirst());
        OutboxEventEntity kafkaEvent = captor.getAllValues().getLast();
        assertThat(kafkaEvent.getEventType()).isEqualTo(OutboxEventService.PAYMENT_PROCESSED_KAFKA);
        assertThat(kafkaEvent.getEventId()).isNotEqualTo(result.getEventId());
        assertThat(kafkaEvent.getStatus()).isEqualTo(OutboxEventStatus.WAITING_PUBLISH);
        PaymentProcessedMessage kafkaMessage = (PaymentProcessedMessage) service.toMessage(kafkaEvent);
        assertThat(kafkaMessage.requestedBy()).isEqualTo("alice");
        assertThat(kafkaMessage.eventId()).isEqualTo(kafkaEvent.getEventId());
        assertThat(kafkaMessage.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(kafkaMessage.requestedEventId()).isEqualTo(REQUESTED_EVENT_ID);
        assertThat(kafkaMessage.traceId()).isEqualTo(TRACE_ID);
        assertThat(kafkaMessage.traceParent()).isEqualTo(traceContextProvider.currentTraceParent());
        assertThat(result.getEventId()).isNotNull();
        assertThat(result.getAggregateId()).isEqualTo(UUID.fromString(PAYMENT_ID));
        assertThat(result.getEventType()).isEqualTo(OutboxEventService.PAYMENT_PROCESSED);
        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.WAITING_PUBLISH);
        assertThat(result.getPayload()).contains("\"traceId\":\"33333333-3333-3333-3333-333333333333\"");
        assertThat(result.getPayload()).contains("\"traceParent\":\"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01\"");
        assertThat(result.getPayload()).contains("\"requestedEventId\":\"11111111-1111-1111-1111-111111111111\"");
    }

    @Test
    void savesPaymentProcessingFailedEventAsWaitingPublish() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        TraceContextProvider traceContextProvider = mock(TraceContextProvider.class);
        OutboxEventService service = new OutboxEventService(repository, new ObjectMapper(), traceContextProvider);
        when(traceContextProvider.currentTraceParent())
                .thenReturn("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        when(repository.saveAndFlush(any(OutboxEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEventEntity result;
        try (var ignored = com.jpcore.labs.paymentprocessor.payment.PaymentLogContext.with(TRACE_ID, PAYMENT_ID, "alice")) {
            result = service.savePaymentProcessingFailed(REQUESTED_EVENT_ID, TRACE_ID, PAYMENT_ID, "denied");
        }
        assertThat(((PaymentProcessingFailedMessage) service.toMessage(result)).requestedBy()).isEqualTo("alice");

        assertThat(result.getEventId()).isNotNull();
        assertThat(result.getAggregateId()).isEqualTo(UUID.fromString(PAYMENT_ID));
        assertThat(result.getEventType()).isEqualTo(OutboxEventService.PAYMENT_PROCESSING_FAILED);
        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.WAITING_PUBLISH);
        assertThat(result.getPayload()).contains("\"traceId\":\"33333333-3333-3333-3333-333333333333\"");
        assertThat(result.getPayload()).contains("\"traceParent\":\"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01\"");
        assertThat(result.getPayload()).contains("\"reason\":\"denied\"");
        verify(repository).saveAndFlush(any(OutboxEventEntity.class));
    }

    @Test
    void deserializesOutboxPayloadByEventType() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        TraceContextProvider traceContextProvider = mock(TraceContextProvider.class);
        OutboxEventService service = new OutboxEventService(repository, new ObjectMapper(), traceContextProvider);
        OutboxEventEntity processed = new OutboxEventEntity(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString(PAYMENT_ID),
                OutboxEventService.PAYMENT_PROCESSED,
                """
                        {
                          "eventId": "44444444-4444-4444-4444-444444444444",
                          "traceId": "33333333-3333-3333-3333-333333333333",
                          "requestedEventId": "11111111-1111-1111-1111-111111111111",
                          "paymentId": "22222222-2222-2222-2222-222222222222"
                        }
                        """,
                OutboxEventStatus.WAITING_PUBLISH
        );
        OutboxEventEntity failed = new OutboxEventEntity(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                UUID.fromString(PAYMENT_ID),
                OutboxEventService.PAYMENT_PROCESSING_FAILED,
                """
                        {
                          "eventId": "55555555-5555-5555-5555-555555555555",
                          "traceId": "33333333-3333-3333-3333-333333333333",
                          "requestedEventId": "11111111-1111-1111-1111-111111111111",
                          "paymentId": "22222222-2222-2222-2222-222222222222",
                          "reason": "denied"
                        }
                        """,
                OutboxEventStatus.WAITING_PUBLISH
        );

        assertThat(service.toMessage(processed)).isInstanceOf(PaymentProcessedMessage.class);
        assertThat(service.toMessage(failed)).isInstanceOf(PaymentProcessingFailedMessage.class);
    }
}
