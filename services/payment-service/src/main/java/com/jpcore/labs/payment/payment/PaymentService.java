package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.idempotency.IdempotencyService;
import com.jpcore.labs.payment.idempotency.IdempotencyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Duration IDEMPOTENCY_EXPIRATION = Duration.ofHours(24);

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentRequestedPublisher paymentRequestedPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            IdempotencyService idempotencyService,
            PaymentRequestedPublisher paymentRequestedPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.paymentRequestedPublisher = paymentRequestedPublisher;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey) {
        String requestBodyHash = requestHash(request);

        Optional<PaymentResponse> completedPayment = idempotencyService.findCompletedPaymentId(idempotencyKey, requestBodyHash)
                .flatMap(paymentRepository::findById)
                .map(this::toResponse);

        if (completedPayment.isPresent()) {
            return completedPayment.get();
        }

        idempotencyService.validateBeforeCreate(idempotencyKey, requestBodyHash);
        IdempotencyEntity idempotency = idempotencyService.createProcessing(
                idempotencyKey,
                requestBodyHash,
                Instant.now().plus(IDEMPOTENCY_EXPIRATION)
        );
        UUID traceId = UUID.randomUUID();

        PaymentEntity payment = new PaymentEntity(
                request.amount(),
                request.currency(),
                request.description(),
                PaymentStatus.PROCESSING
        );

        PaymentEntity savedPayment = paymentRepository.saveAndFlush(payment);
        log.info("Payment created. paymentId={} traceId={} status={}",
                savedPayment.getId(),
                traceId,
                savedPayment.getStatus()
        );

        idempotencyService.complete(idempotency, savedPayment.getId());
      
        paymentRequestedPublisher.publish(savedPayment, traceId);

        return toResponse(savedPayment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments() {
        return paymentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void completePayment(String paymentId) {
        completePayment(paymentId, null);
    }

    @Transactional
    public void completePayment(String paymentId, UUID traceId) {
        PaymentEntity payment = paymentRepository.findById(java.util.UUID.fromString(paymentId))
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.markCompleted();
            log.info("Payment status updated. paymentId={} traceId={} status={}",
                    payment.getId(),
                    traceId,
                    payment.getStatus()
            );
        } else {
            log.warn("Payment status update ignored. paymentId={} traceId={} status={} requestedStatus={}",
                    payment.getId(),
                    traceId,
                    payment.getStatus(),
                    PaymentStatus.COMPLETED
            );
        }
    }

    @Transactional
    public void failPayment(String paymentId) {
        failPayment(paymentId, null);
    }

    @Transactional
    public void failPayment(String paymentId, UUID traceId) {
        PaymentEntity payment = paymentRepository.findById(java.util.UUID.fromString(paymentId))
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.markFailed();
            log.info("Payment status updated. paymentId={} traceId={} status={}",
                    payment.getId(),
                    traceId,
                    payment.getStatus()
            );
        } else {
            log.warn("Payment status update ignored. paymentId={} traceId={} status={} requestedStatus={}",
                    payment.getId(),
                    traceId,
                    payment.getStatus(),
                    PaymentStatus.FAILED
            );
        }
    }

    private PaymentResponse toResponse(PaymentEntity payment) {
        return new PaymentResponse(
                payment.getId().toString(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription(),
                payment.getStatus()
        );
    }

    private String requestHash(PaymentRequest request) {
        String requestContent = request.amount() + "|" + request.currency() + "|" + request.description();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(requestContent.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

}
