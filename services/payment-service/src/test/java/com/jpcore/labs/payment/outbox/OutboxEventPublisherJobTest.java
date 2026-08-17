package com.jpcore.labs.payment.outbox;

import com.jpcore.labs.payment.config.RabbitMqConfig;
import com.jpcore.labs.payment.payment.PaymentRequestedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPublisherJobTest {

    @Test
    void publishesWaitingEventsAndMarksAsPublished() {
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxEventPublisherJob job = new OutboxEventPublisherJob(outboxEventService, rabbitTemplate);
        OutboxEventEntity outboxEvent = outboxEvent();
        PaymentRequestedMessage message = paymentRequestedMessage(outboxEvent);
        when(outboxEventService.findWaitingPublish()).thenReturn(List.of(outboxEvent));
        when(outboxEventService.toPaymentRequestedMessage(outboxEvent)).thenReturn(message);
        ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.forClass(CorrelationData.class);

        job.publishWaitingEvents();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.PAYMENT_EXCHANGE),
                eq(RabbitMqConfig.PAYMENT_PROCESS_ROUTING_KEY),
                eq(message),
                correlationDataCaptor.capture()
        );
        assertThat(correlationDataCaptor.getValue().getId()).isEqualTo(outboxEvent.getEventId().toString());
        verify(outboxEventService).markPublished(outboxEvent);
    }

    @Test
    void keepsEventWaitingWhenPublishFails() {
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxEventPublisherJob job = new OutboxEventPublisherJob(outboxEventService, rabbitTemplate);
        OutboxEventEntity outboxEvent = outboxEvent();
        PaymentRequestedMessage message = paymentRequestedMessage(outboxEvent);
        when(outboxEventService.findWaitingPublish()).thenReturn(List.of(outboxEvent));
        when(outboxEventService.toPaymentRequestedMessage(outboxEvent)).thenReturn(message);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(RabbitMqConfig.PAYMENT_EXCHANGE),
                        eq(RabbitMqConfig.PAYMENT_PROCESS_ROUTING_KEY),
                        eq(message),
                        org.mockito.ArgumentMatchers.any(CorrelationData.class)
                );

        job.publishWaitingEvents();

        verify(outboxEventService, never()).markPublished(outboxEvent);
    }

    private OutboxEventEntity outboxEvent() {
        return new OutboxEventEntity(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "PaymentRequested",
                "{}",
                OutboxEventStatus.WAITING_PUBLISH
        );
    }

    private PaymentRequestedMessage paymentRequestedMessage(OutboxEventEntity outboxEvent) {
        return new PaymentRequestedMessage(
                outboxEvent.getEventId(),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                outboxEvent.getAggregateId().toString(),
                new BigDecimal("100.50"),
                "BRL",
                "test payment"
        );
    }
}
