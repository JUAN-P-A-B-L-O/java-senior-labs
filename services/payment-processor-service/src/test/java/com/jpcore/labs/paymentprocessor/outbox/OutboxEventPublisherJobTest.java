package com.jpcore.labs.paymentprocessor.outbox;

import com.jpcore.labs.paymentprocessor.config.RabbitMqConfig;
import com.jpcore.labs.paymentprocessor.payment.PaymentProcessedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPublisherJobTest {

    @Test
    void publishesWaitingPaymentProcessedEventsAndMarksAsPublished() {
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxEventPublisherJob job = new OutboxEventPublisherJob(outboxEventService, rabbitTemplate);
        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                OutboxEventService.PAYMENT_PROCESSED,
                "{}",
                OutboxEventStatus.WAITING_PUBLISH
        );
        PaymentProcessedMessage message = new PaymentProcessedMessage(
                outboxEvent.getEventId(),
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                outboxEvent.getAggregateId().toString()
        );
        when(outboxEventService.findWaitingPublish()).thenReturn(List.of(outboxEvent));
        when(outboxEventService.toMessage(outboxEvent)).thenReturn(message);
        ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.forClass(CorrelationData.class);

        job.publishWaitingEvents();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.PAYMENT_EXCHANGE),
                eq(RabbitMqConfig.PAYMENT_PROCESSED_ROUTING_KEY),
                eq(message),
                correlationDataCaptor.capture()
        );
        assertThat(correlationDataCaptor.getValue().getId()).isEqualTo(outboxEvent.getEventId().toString());
        verify(outboxEventService).markPublished(outboxEvent);
    }
}
