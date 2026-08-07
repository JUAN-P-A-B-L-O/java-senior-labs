package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.config.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentRequestedPublisherTest {

    @Test
    void publishesPaymentRequestedMessageWithEventId() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        PaymentRequestedPublisher publisher = new PaymentRequestedPublisher(rabbitTemplate);
        PaymentEntity payment = new PaymentEntity(
                new BigDecimal("100.50"),
                "BRL",
                "test payment",
                PaymentStatus.CREATED
        );
        UUID paymentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ReflectionTestUtils.setField(payment, "id", paymentId);
        ArgumentCaptor<PaymentRequestedMessage> messageCaptor = ArgumentCaptor.forClass(PaymentRequestedMessage.class);
        ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.forClass(CorrelationData.class);

        publisher.publish(payment);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.PAYMENT_EXCHANGE),
                eq(RabbitMqConfig.PAYMENT_PROCESS_ROUTING_KEY),
                messageCaptor.capture(),
                correlationDataCaptor.capture()
        );
        PaymentRequestedMessage message = messageCaptor.getValue();
        assertThat(message.eventId()).isNotNull();
        assertThat(correlationDataCaptor.getValue().getId()).isEqualTo(message.eventId().toString());
        assertThat(message.paymentId()).isEqualTo(paymentId.toString());
        assertThat(message.amount()).isEqualByComparingTo("100.50");
        assertThat(message.currency()).isEqualTo("BRL");
        assertThat(message.description()).isEqualTo("test payment");
    }
}
