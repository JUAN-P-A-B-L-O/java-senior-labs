package com.jpcore.labs.payment.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final PaymentAiAnalyzer paymentAiAnalyzer;

    public AiController(PaymentAiAnalyzer paymentAiAnalyzer) {
        this.paymentAiAnalyzer = paymentAiAnalyzer;
    }

    @GetMapping("/test")
    public String testCall() {
        return paymentAiAnalyzer.testCall();
    }
}
