package com.jpcore.labs.payment.idempotency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;

    public IdempotencyService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyEntity> findByKey(String idempotencyKey) {
        return idempotencyRepository.findById(idempotencyKey);
    }

    @Transactional
    public IdempotencyEntity createProcessing(
            String idempotencyKey,
            String requestBodyHash,
            Instant expiresAt
    ) {
        IdempotencyEntity idempotency = new IdempotencyEntity(
                idempotencyKey,
                requestBodyHash,
                IdempotencyStatus.PROCESSING,
                expiresAt
        );

        return idempotencyRepository.save(idempotency);
    }

    public boolean isExpired(IdempotencyEntity idempotency, Instant now) {
        return !idempotency.getExpiresAt().isAfter(now);
    }
}
