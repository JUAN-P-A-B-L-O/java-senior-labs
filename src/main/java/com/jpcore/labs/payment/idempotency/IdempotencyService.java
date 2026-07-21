package com.jpcore.labs.payment.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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

    @Transactional(readOnly = true)
    public void validateBeforeCreate(String idempotencyKey, String requestBodyHash) {
        findByKey(idempotencyKey)
                .filter(idempotency -> idempotency.getRequestBodyHash().equals(requestBodyHash))
                .filter(idempotency -> idempotency.getStatus() == IdempotencyStatus.PROCESSING)
                .ifPresent(idempotency -> {
                    throw new IdempotencyRequestBlockedException(idempotencyKey);
                });
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findCompletedPaymentId(String idempotencyKey, String requestBodyHash) {
        return findByKey(idempotencyKey)
                .filter(idempotency -> idempotency.getRequestBodyHash().equals(requestBodyHash))
                .filter(idempotency -> idempotency.getStatus() == IdempotencyStatus.COMPLETED)
                .map(IdempotencyEntity::getPaymentId);
    }

    @Transactional
    public IdempotencyEntity createProcessing(
            String idempotencyKey,
            String requestBodyHash,
            Instant expiresAt
    ) {
        Optional<IdempotencyEntity> existingIdempotency = findByKey(idempotencyKey);
        if (existingIdempotency.isPresent()
                && existingIdempotency.get().getStatus() == IdempotencyStatus.FAILED) {
            IdempotencyEntity idempotency = existingIdempotency.get();
            idempotency.markProcessing(requestBodyHash, expiresAt);
            return idempotencyRepository.saveAndFlush(idempotency);
        }

        IdempotencyEntity idempotency = new IdempotencyEntity(
                idempotencyKey,
                requestBodyHash,
                IdempotencyStatus.PROCESSING,
                expiresAt
        );

        try {
            return idempotencyRepository.saveAndFlush(idempotency);
        } catch (DataIntegrityViolationException exception) {
            throw new IdempotencyRequestBlockedException(idempotencyKey);
        }
    }

    @Transactional
    public void complete(IdempotencyEntity idempotency, UUID paymentId) {
        idempotency.markCompleted(paymentId);
        idempotencyRepository.save(idempotency);
    }

    public boolean isExpired(IdempotencyEntity idempotency, Instant now) {
        return !idempotency.getExpiresAt().isAfter(now);
    }
}
