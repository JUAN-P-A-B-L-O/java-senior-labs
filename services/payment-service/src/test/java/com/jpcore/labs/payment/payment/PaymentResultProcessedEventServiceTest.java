package com.jpcore.labs.payment.payment;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentResultProcessedEventServiceTest {

    @Test
    void checksIfResultEventWasProcessed() {
        PaymentResultProcessedEventRepository repository = mock(PaymentResultProcessedEventRepository.class);
        PaymentResultProcessedEventService service = new PaymentResultProcessedEventService(repository);
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(repository.existsByEventId(eventId)).thenReturn(true);

        assertThat(service.isProcessed(eventId)).isTrue();
    }

    @Test
    void marksResultEventAsProcessed() {
        PaymentResultProcessedEventRepository repository = mock(PaymentResultProcessedEventRepository.class);
        PaymentResultProcessedEventService service = new PaymentResultProcessedEventService(repository);
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ArgumentCaptor<PaymentResultProcessedEventEntity> captor =
                ArgumentCaptor.forClass(PaymentResultProcessedEventEntity.class);

        service.markProcessed(eventId);

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
    }

    @Test
    void ignoresDuplicateWhenMarkingProcessedResultEvent() {
        PaymentResultProcessedEventRepository repository = mock(PaymentResultProcessedEventRepository.class);
        PaymentResultProcessedEventService service = new PaymentResultProcessedEventService(repository);
        when(repository.save(any(PaymentResultProcessedEventEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        service.markProcessed(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }
}
