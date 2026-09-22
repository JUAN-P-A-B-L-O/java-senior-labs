package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.idempotency.IdempotencyEntity;
import com.jpcore.labs.payment.idempotency.IdempotencyRequestBlockedException;
import com.jpcore.labs.payment.idempotency.IdempotencyRepository;
import com.jpcore.labs.payment.idempotency.IdempotencyStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private PaymentRequestedPublisher paymentRequestedPublisher;

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
        assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(createdIdempotency.getIdempotencyKey()).isEqualTo("service-payment-key");
        assertThat(createdIdempotency.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(createdIdempotency.getRequestBodyHash()).hasSize(64);
        assertThat(createdIdempotency.getPaymentId()).isEqualTo(UUID.fromString(response.id()));
        verify(paymentRequestedPublisher).publish(any(PaymentEntity.class), any(UUID.class));
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
        verify(paymentRequestedPublisher, times(1)).publish(any(PaymentEntity.class), any(UUID.class));
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

        assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void completePaymentUpdatesProcessingPaymentToCompleted() {
        PaymentRequest request = new PaymentRequest(
                new BigDecimal("88.00"),
                "BRL",
                "Complete payment"
        );
        PaymentResponse response = paymentService.createPayment(request, "complete-payment-key");

        paymentService.completePayment(response.id());

        PaymentResponse updatedPayment = paymentService.getPayments()
                .stream()
                .filter(payment -> payment.id().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertThat(updatedPayment.status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void failPaymentUpdatesProcessingPaymentToFailed() {
        PaymentRequest request = new PaymentRequest(
                new BigDecimal("89.00"),
                "BRL",
                "Fail payment"
        );
        PaymentResponse response = paymentService.createPayment(request, "fail-payment-key");

        paymentService.failPayment(response.id());

        PaymentResponse updatedPayment = paymentService.getPayments()
                .stream()
                .filter(payment -> payment.id().equals(response.id()))
                .findFirst()
                .orElseThrow();
        assertThat(updatedPayment.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void recordsPaymentLifecycleMetrics() {
        double createdBefore = counterCount("payments_created");
        double completedBefore = counterCount("payments_completed");
        double failedBefore = counterCount("payments_failed");
        long durationCountBefore = timerCount("payment_processing_duration");

        PaymentResponse completedPayment = paymentService.createPayment(
                new PaymentRequest(new BigDecimal("90.00"), "BRL", "Metrics completed payment"),
                "metrics-completed-payment-key"
        );
        PaymentResponse failedPayment = paymentService.createPayment(
                new PaymentRequest(new BigDecimal("91.00"), "BRL", "Metrics failed payment"),
                "metrics-failed-payment-key"
        );

        paymentService.completePayment(completedPayment.id());
        paymentService.failPayment(failedPayment.id());

        assertThat(counterCount("payments_created")).isEqualTo(createdBefore + 2);
        assertThat(counterCount("payments_completed")).isEqualTo(completedBefore + 1);
        assertThat(counterCount("payments_failed")).isEqualTo(failedBefore + 1);
        assertThat(timerCount("payment_processing_duration")).isEqualTo(durationCountBefore + 2);
    }

    @Test
    void getPaymentsDoesNotUseCache() {
        PaymentResponse existingPayment = paymentService.createPayment(
                new PaymentRequest(new BigDecimal("92.00"), "BRL", "Cached payment"),
                "cached-payment-key"
        );

        assertThat(paymentService.getPayments())
                .extracting(PaymentResponse::id)
                .contains(existingPayment.id());

        assertThat(cacheManager.getCacheNames()).doesNotContain("payments");
    }

    @Test
    void getPaymentCachesPaymentByIdAndEvictsWhenStatusChanges() {
        PaymentResponse createdPayment = paymentService.createPayment(
                new PaymentRequest(new BigDecimal("94.00"), "BRL", "Cached payment by id"),
                "cached-payment-by-id-key"
        );

        PaymentResponse cachedPayment = paymentService.getPayment(createdPayment.id());

        Cache paymentCache = cacheManager.getCache("payment");
        assertThat(paymentCache).isNotNull();
        assertThat(cachedPayment).isEqualTo(createdPayment);
        assertThat(paymentCache.get(createdPayment.id())).isNotNull();

        paymentService.completePayment(createdPayment.id());

        assertThat(paymentCache.get(createdPayment.id())).isNull();
        assertThat(paymentService.getPayment(createdPayment.id()).status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    private double counterCount(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private long timerCount(String name) {
        return meterRegistry.get(name).timer().count();
    }
}
