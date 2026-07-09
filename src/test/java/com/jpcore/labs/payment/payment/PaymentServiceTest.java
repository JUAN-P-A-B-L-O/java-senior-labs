package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.idempotency.IdempotencyEntity;
import com.jpcore.labs.payment.idempotency.IdempotencyRequestBlockedException;
import com.jpcore.labs.payment.idempotency.IdempotencyRepository;
import com.jpcore.labs.payment.idempotency.IdempotencyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        PaymentResponse response = paymentService.createPayment(request, "service-payment-key");

        IdempotencyEntity createdIdempotency = idempotencyRepository.findAll()
                .stream()
                .filter(idempotency -> !idempotencyKeysBefore.contains(idempotency.getIdempotencyKey()))
                .findFirst()
                .orElseThrow();

        assertThat(response.id()).isNotBlank();
        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(createdIdempotency.getIdempotencyKey()).isEqualTo("service-payment-key");
        assertThat(createdIdempotency.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(createdIdempotency.getRequestBodyHash()).hasSize(64);
    }

    @Test
    void createPaymentBlocksExistingSameRequestWhenStatusIsNotFailed() {
        PaymentRequest request = new PaymentRequest(
                new BigDecimal("55.00"),
                "BRL",
                "Duplicated payment"
        );

        paymentService.createPayment(request, "blocked-payment-key");

        assertThatThrownBy(() -> paymentService.createPayment(request, "blocked-payment-key"))
                .isInstanceOf(IdempotencyRequestBlockedException.class);
    }

    @Test
    void createPaymentAllowsExistingSameRequestWhenStatusIsFailed() {
        PaymentRequest request = new PaymentRequest(
                new BigDecimal("75.00"),
                "BRL",
                "Retry failed payment"
        );
        IdempotencyEntity failedIdempotency = new IdempotencyEntity(
                "failed-payment-key",
                "4a4e96ca1cff65b0349435dc1dc870af28568bf46b62db3cc4206dcf17af73f7",
                IdempotencyStatus.FAILED,
                Instant.now().plusSeconds(60)
        );
        idempotencyRepository.save(failedIdempotency);

        PaymentResponse response = paymentService.createPayment(request, "failed-payment-key");

        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
    }
}
