package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.ai.AiService;
import com.jpcore.labs.payment.ai.PaymentAnalysisInput;
import org.springframework.stereotype.Service;

@Service
public class PaymentAnalysisService {

    private final PaymentService paymentService;
    private final AiService aiService;

    public PaymentAnalysisService(PaymentService paymentService, AiService aiService) {
        this.paymentService = paymentService;
        this.aiService = aiService;
    }

    public String analyze(String paymentId) {
        PaymentResponse payment = paymentService.findById(paymentId);
        PaymentAnalysisInput input = new PaymentAnalysisInput(
                payment.amount(), payment.currency(), payment.description(), payment.status()
        );
        return aiService.analyze(input);
    }
}
