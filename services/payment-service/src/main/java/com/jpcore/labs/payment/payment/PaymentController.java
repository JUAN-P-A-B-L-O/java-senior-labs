package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.ai.PaymentAnalysisResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentAnalysisService paymentAnalysisService;

    public PaymentController(PaymentService paymentService, PaymentAnalysisService paymentAnalysisService) {
        this.paymentService = paymentService;
        this.paymentAnalysisService = paymentAnalysisService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        return paymentService.createPayment(request, idempotencyKey);
    }

    @GetMapping("/payments")
    public List<PaymentResponse> getPayments() {
        return paymentService.getPayments();
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentResponse getPayment(@PathVariable String paymentId) {
        return paymentService.getPayment(paymentId);
    }

    @PostMapping("/payments/{id}/ai-analysis")
    public PaymentAnalysisResponse analyzePayment(@PathVariable("id") String id) {
        return paymentAnalysisService.analyze(id);
    }
}
