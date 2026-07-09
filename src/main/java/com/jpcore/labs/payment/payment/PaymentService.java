package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.idempotency.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class PaymentService {

    private static final Duration IDEMPOTENCY_EXPIRATION = Duration.ofHours(24);

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;

    public PaymentService(PaymentRepository paymentRepository, IdempotencyService idempotencyService) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey) {
        String requestBodyHash = requestHash(request);

        idempotencyService.validateBeforeCreate(idempotencyKey, requestBodyHash);
        idempotencyService.createProcessing(
                idempotencyKey,
                requestBodyHash,
                Instant.now().plus(IDEMPOTENCY_EXPIRATION)
        );

        PaymentEntity payment = new PaymentEntity(
                request.amount(),
                request.currency(),
                request.description(),
                PaymentStatus.CREATED
        );

        PaymentEntity savedPayment = paymentRepository.save(payment);

        return toResponse(savedPayment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments() {
        return paymentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
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
