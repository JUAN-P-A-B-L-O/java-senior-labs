package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.idempotency.IdempotencyService;
import com.jpcore.labs.payment.idempotency.IdempotencyEntity;
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

@Service
public class PaymentService {

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

        PaymentEntity payment = new PaymentEntity(
                request.amount(),
                request.currency(),
                request.description(),
                PaymentStatus.PROCESSING
        );

        PaymentEntity savedPayment = paymentRepository.saveAndFlush(payment);
        idempotencyService.complete(idempotency, savedPayment.getId());
      
        paymentRequestedPublisher.publish(savedPayment);

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
        PaymentEntity payment = paymentRepository.findById(java.util.UUID.fromString(paymentId))
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.markCompleted();
        }
    }

    @Transactional
    public void failPayment(String paymentId) {
        PaymentEntity payment = paymentRepository.findById(java.util.UUID.fromString(paymentId))
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.markFailed();
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
