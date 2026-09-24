package com.jpcore.labs.payment.ai;

public record PaymentAnalysisResponse(
        String summary,
        String risk,
        String recommendedAction
) {
}
