package com.jpcore.labs.payment.payment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PaymentController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest request) {
        return new PaymentResponse(
                UUID.randomUUID().toString(),
                request.amount(),
                request.currency(),
                request.description(),
                PaymentStatus.CREATED
        );
    }
}
