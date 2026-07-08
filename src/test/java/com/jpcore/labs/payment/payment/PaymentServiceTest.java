package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.idempotency.IdempotencyEntity;
import com.jpcore.labs.payment.idempotency.IdempotencyRepository;
import com.jpcore.labs.payment.idempotency.IdempotencyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Test
    void createPaymentCreatesIdempotencyBeforePayment() {
        Set<String> idempotencyKeysBefore = idempotencyRepository.findAll()
                .stream()
                .map(IdempotencyEntity::getIdempotencyKey)
                .collect(Collectors.toSet());
        PaymentRequest request = new PaymentRequest(
                new BigDecimal("100.50"),
                "BRL",
                "Test payment"
        );

        PaymentResponse response = paymentService.createPayment(request);

        IdempotencyEntity createdIdempotency = idempotencyRepository.findAll()
                .stream()
                .filter(idempotency -> !idempotencyKeysBefore.contains(idempotency.getIdempotencyKey()))
                .findFirst()
                .orElseThrow();

        assertThat(response.id()).isNotBlank();
        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(createdIdempotency.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(createdIdempotency.getRequestBodyHash()).hasSize(64);
    }
}
