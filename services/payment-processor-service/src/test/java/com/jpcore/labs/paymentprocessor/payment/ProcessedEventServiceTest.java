package com.jpcore.labs.paymentprocessor.payment;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessedEventServiceTest {

    @Test
    void checksIfEventWasAlreadyProcessed() {
        ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
        ProcessedEventService processedEventService = new ProcessedEventService(processedEventRepository);
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        processedEventService.isProcessed(eventId);

        verify(processedEventRepository).existsByEventId(eventId);
    }

    @Test
    void ignoresDuplicateWhenMarkingProcessedEvent() {
        ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
        ProcessedEventService processedEventService = new ProcessedEventService(processedEventRepository);
        when(processedEventRepository.save(any(ProcessedEventEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicated event"));

        assertThatCode(() -> processedEventService.markProcessed(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .doesNotThrowAnyException();
    }
}
