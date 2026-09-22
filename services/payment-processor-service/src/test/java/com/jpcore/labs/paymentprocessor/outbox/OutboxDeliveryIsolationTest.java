package com.jpcore.labs.paymentprocessor.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpcore.labs.paymentprocessor.payment.TraceContextProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({OutboxEventService.class, ObjectMapper.class})
class OutboxDeliveryIsolationTest {

    @Autowired
    private OutboxEventService service;

    @MockBean
    private TraceContextProvider traceContextProvider;

    @Test
    void rabbitPublicationDoesNotMarkKafkaPublishedAndFailedPaymentsStayOnRabbit() {
        var rabbit = service.savePaymentProcessed(UUID.randomUUID(), UUID.randomUUID().toString());
        var failed = service.savePaymentProcessingFailed(UUID.randomUUID(), UUID.randomUUID().toString(), "denied");

        assertThat(service.findWaitingPublish()).extracting(OutboxEventEntity::getEventId)
                .containsExactlyInAnyOrder(rabbit.getEventId(), failed.getEventId());
        assertThat(service.findWaitingKafkaPublish()).hasSize(1);
        var kafka = service.findWaitingKafkaPublish().getFirst();
        assertThat(kafka.getAggregateId()).isEqualTo(rabbit.getAggregateId());

        service.markPublished(rabbit);

        assertThat(service.findWaitingPublish()).extracting(OutboxEventEntity::getEventId)
                .containsExactly(failed.getEventId());
        assertThat(service.findWaitingKafkaPublish()).extracting(OutboxEventEntity::getEventId)
                .containsExactly(kafka.getEventId());
    }
}
