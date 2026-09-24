package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.ai.PaymentAiAnalyzer;
import com.jpcore.labs.payment.ai.PaymentAnalysisInput;
import com.jpcore.labs.payment.ai.PaymentAnalysisResponse;
import org.springframework.stereotype.Service;

@Service
public class PaymentAnalysisService {

    private final PaymentService paymentService;
    private final PaymentAiAnalyzer paymentAiAnalyzer;

    public PaymentAnalysisService(PaymentService paymentService, PaymentAiAnalyzer paymentAiAnalyzer) {
        this.paymentService = paymentService;
        this.paymentAiAnalyzer = paymentAiAnalyzer;
    }

    public PaymentAnalysisResponse analyze(String paymentId) {
        PaymentResponse payment = paymentService.findById(paymentId);
        PaymentAnalysisInput input = new PaymentAnalysisInput(
                payment.amount(), payment.currency(), payment.description(), payment.status()
        );
        return paymentAiAnalyzer.analyze(input);
    }
}
