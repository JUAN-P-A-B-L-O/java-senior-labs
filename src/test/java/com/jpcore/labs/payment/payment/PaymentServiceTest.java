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
import java.util.UUID;
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
    void createPaymentCompletesIdempotencyWithPaymentId() {
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
        assertThat(createdIdempotency.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(createdIdempotency.getRequestBodyHash()).hasSize(64);
        assertThat(createdIdempotency.getPaymentId()).isEqualTo(UUID.fromString(response.id()));
    }

    @Test
    void createPaymentReturnsExistingPaymentWhenIdempotencyIsCompleted() {
        PaymentRequest request = new PaymentRequest(
                new BigDecimal("55.00"),
                "BRL",
                "Duplicated payment"
        );

        PaymentResponse firstResponse = paymentService.createPayment(request, "completed-payment-key");
        PaymentResponse secondResponse = paymentService.createPayment(request, "completed-payment-key");

        assertThat(secondResponse).isEqualTo(firstResponse);
    }

    @Test
    void createPaymentBlocksExistingSameRequestWhenStatusIsProcessing() {
        PaymentRequest request = new PaymentRequest(
                new BigDecimal("55.00"),
                "BRL",
                "Duplicated payment"
        );
        IdempotencyEntity processingIdempotency = new IdempotencyEntity(
                "blocked-payment-key",
                "751ae27d7facc0c1b8406e25dd6cd8d31d0abad55cfcfecc4ff4986b47756327",
                IdempotencyStatus.PROCESSING,
                Instant.now().plusSeconds(60)
        );
        idempotencyRepository.save(processingIdempotency);

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
