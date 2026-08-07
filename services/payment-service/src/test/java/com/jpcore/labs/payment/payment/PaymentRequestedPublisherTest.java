package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.config.RabbitMqConfig;
import com.jpcore.labs.payment.outbox.OutboxEventEntity;
import com.jpcore.labs.payment.outbox.OutboxEventService;
import com.jpcore.labs.payment.outbox.OutboxEventStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

class PaymentRequestedPublisherTest {

    @Test
    void savesOutboxEventBeforePublishingPaymentRequestedMessageWithEventId() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "PaymentRequested",
                "{}",
                OutboxEventStatus.PENDING
        );
        when(outboxEventService.savePaymentRequested(any(PaymentRequestedMessage.class)))
                .thenReturn(outboxEvent);
        PaymentRequestedPublisher publisher = new PaymentRequestedPublisher(rabbitTemplate, outboxEventService);
        PaymentEntity payment = new PaymentEntity(
                new BigDecimal("100.50"),
                "BRL",
                "test payment",
                PaymentStatus.CREATED
        );
        UUID paymentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ReflectionTestUtils.setField(payment, "id", paymentId);
        ArgumentCaptor<PaymentRequestedMessage> messageCaptor = ArgumentCaptor.forClass(PaymentRequestedMessage.class);
        ArgumentCaptor<PaymentRequestedMessage> outboxMessageCaptor = ArgumentCaptor.forClass(PaymentRequestedMessage.class);
        ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.forClass(CorrelationData.class);

        publisher.publish(payment);

        InOrder inOrder = inOrder(outboxEventService, rabbitTemplate);
        inOrder.verify(outboxEventService).savePaymentRequested(outboxMessageCaptor.capture());
        inOrder.verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.PAYMENT_EXCHANGE),
                eq(RabbitMqConfig.PAYMENT_PROCESS_ROUTING_KEY),
                messageCaptor.capture(),
                correlationDataCaptor.capture()
        );
        inOrder.verify(outboxEventService).markPublished(outboxEvent);
        PaymentRequestedMessage message = messageCaptor.getValue();
        assertThat(message.eventId()).isNotNull();
        assertThat(outboxMessageCaptor.getValue()).isEqualTo(message);
        assertThat(correlationDataCaptor.getValue().getId()).isEqualTo(message.eventId().toString());
        assertThat(message.paymentId()).isEqualTo(paymentId.toString());
        assertThat(message.amount()).isEqualByComparingTo("100.50");
        assertThat(message.currency()).isEqualTo("BRL");
        assertThat(message.description()).isEqualTo("test payment");
    }
}
