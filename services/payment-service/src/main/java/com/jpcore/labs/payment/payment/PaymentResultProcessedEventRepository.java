package com.jpcore.labs.payment.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentResultProcessedEventRepository extends JpaRepository<PaymentResultProcessedEventEntity, UUID> {

    boolean existsByEventId(UUID eventId);
}
