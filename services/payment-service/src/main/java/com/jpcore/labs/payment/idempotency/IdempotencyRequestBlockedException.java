package com.jpcore.labs.payment.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyRequestBlockedException extends RuntimeException {

    public IdempotencyRequestBlockedException(String idempotencyKey) {
        super("Idempotency request is already in progress or completed: " + idempotencyKey);
    }
}
