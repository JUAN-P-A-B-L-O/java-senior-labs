package com.jpcore.labs.paymentprocessor.outbox;

import com.jpcore.labs.paymentprocessor.payment.PaymentProcessedMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KafkaOutboxEventPublisherJobTest {

    private final OutboxEventService service = mock(OutboxEventService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final KafkaOutboxEventPublisherJob job =
            new KafkaOutboxEventPublisherJob(service, template, "payment.processed", Duration.ofMillis(10));
    private final OutboxEventEntity event = new OutboxEventEntity(
            UUID.randomUUID(), UUID.randomUUID(), OutboxEventService.PAYMENT_PROCESSED_KAFKA,
            "{\"eventId\":\"test-event\"}", OutboxEventStatus.WAITING_PUBLISH);

    private void prepare() {
        when(service.findWaitingKafkaPublish()).thenReturn(List.of(event));
        when(service.toMessage(event)).thenReturn(new PaymentProcessedMessage(
                event.getEventId(), UUID.randomUUID(), UUID.randomUUID(), event.getAggregateId().toString(),
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishesJsonWithPaymentKeyAndTraceHeaderThenMarksPublished() {
        prepare();
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        job.publishWaitingEvents();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass((Class) ProducerRecord.class);
        verify(template).send(captor.capture());
        var record = captor.getValue();
        assertThat(record.topic()).isEqualTo("payment.processed");
        assertThat(record.key()).isEqualTo(event.getAggregateId().toString());
        assertThat(record.value()).isEqualTo(event.getPayload());
        assertThat(new String(record.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo("PaymentProcessed");
        verify(service).markPublished(event);
        verify(service, never()).findWaitingPublish();
    }

    @Test
    void failedSendRemainsPendingAndCanBeRetried() {
        prepare();
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));

        job.publishWaitingEvents();
        verify(service, never()).markPublished(any());

        job.publishWaitingEvents();
        verify(service).markPublished(event);
        verify(template, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void unacknowledgedSendRemainsPending() {
        prepare();
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        when(template.send(any(ProducerRecord.class))).thenReturn(pending);

        job.publishWaitingEvents();

        verify(service, never()).markPublished(any());
        pending.complete(null);
        verify(service, never()).markPublished(any());
    }

    @Test
    void synchronousSendFailureRemainsPending() {
        prepare();
        when(template.send(any(ProducerRecord.class))).thenThrow(new IllegalStateException("metadata unavailable"));

        job.publishWaitingEvents();

        verify(service, never()).markPublished(any());
    }
}
