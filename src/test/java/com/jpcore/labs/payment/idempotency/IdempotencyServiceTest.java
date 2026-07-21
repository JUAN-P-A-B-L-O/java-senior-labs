package com.jpcore.labs.payment.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void findByKeyReturnsRepositoryResult() {
        IdempotencyEntity idempotency = new IdempotencyEntity(
                "payment-key",
                "request-hash",
                IdempotencyStatus.PROCESSING,
                Instant.parse("2026-07-07T21:30:00Z")
        );

        when(idempotencyRepository.findById("payment-key")).thenReturn(Optional.of(idempotency));

        Optional<IdempotencyEntity> result = idempotencyService.findByKey("payment-key");

        assertThat(result).contains(idempotency);
    }

    @Test
    void createProcessingStoresProcessingRecord() {
        Instant expiresAt = Instant.parse("2026-07-07T21:30:00Z");
        ArgumentCaptor<IdempotencyEntity> captor = ArgumentCaptor.forClass(IdempotencyEntity.class);

        when(idempotencyRepository.saveAndFlush(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        IdempotencyEntity result = idempotencyService.createProcessing("payment-key", "request-hash", expiresAt);

        verify(idempotencyRepository).saveAndFlush(captor.getValue());
        assertThat(result.getIdempotencyKey()).isEqualTo("payment-key");
        assertThat(result.getRequestBodyHash()).isEqualTo("request-hash");
        assertThat(result.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void createProcessingBlocksWhenIdempotencyKeyAlreadyExists() {
        Instant expiresAt = Instant.parse("2026-07-07T21:30:00Z");

        when(idempotencyRepository.saveAndFlush(any(IdempotencyEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        assertThatThrownBy(() -> idempotencyService.createProcessing("payment-key", "request-hash", expiresAt))
                .isInstanceOf(IdempotencyRequestBlockedException.class)
                .hasMessageContaining("payment-key");
    }

    @Test
    void isExpiredReturnsTrueWhenExpiresAtIsNowOrBefore() {
        IdempotencyEntity idempotency = new IdempotencyEntity(
                "payment-key",
                "request-hash",
                IdempotencyStatus.PROCESSING,
                Instant.parse("2026-07-07T21:30:00Z")
        );

        assertThat(idempotencyService.isExpired(idempotency, Instant.parse("2026-07-07T21:30:00Z"))).isTrue();
        assertThat(idempotencyService.isExpired(idempotency, Instant.parse("2026-07-07T21:30:01Z"))).isTrue();
        assertThat(idempotencyService.isExpired(idempotency, Instant.parse("2026-07-07T21:29:59Z"))).isFalse();
    }
}
