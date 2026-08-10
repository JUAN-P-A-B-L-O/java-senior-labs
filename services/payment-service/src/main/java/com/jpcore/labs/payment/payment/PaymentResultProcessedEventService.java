package com.jpcore.labs.payment.payment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentResultProcessedEventService {

    private final PaymentResultProcessedEventRepository repository;

    public PaymentResultProcessedEventService(PaymentResultProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isProcessed(UUID eventId) {
        return repository.existsByEventId(eventId);
    }

    @Transactional
    public void markProcessed(UUID eventId) {
        try {
            repository.save(new PaymentResultProcessedEventEntity(eventId, Instant.now()));
        } catch (DataIntegrityViolationException ignored) {
        }
    }
}
