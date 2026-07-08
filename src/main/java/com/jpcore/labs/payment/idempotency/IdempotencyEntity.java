package com.jpcore.labs.payment.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_body_hash", nullable = false, length = 128)
    private String requestBodyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotencyStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyEntity() {
    }

    public IdempotencyEntity(
            String idempotencyKey,
            String requestBodyHash,
            IdempotencyStatus status,
            Instant expiresAt
    ) {
        this.idempotencyKey = idempotencyKey;
        this.requestBodyHash = requestBodyHash;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestBodyHash() {
        return requestBodyHash;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
