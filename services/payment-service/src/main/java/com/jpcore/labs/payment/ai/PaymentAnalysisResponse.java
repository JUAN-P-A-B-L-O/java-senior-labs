package com.jpcore.labs.payment.ai;

public record PaymentAnalysisResponse(
        String summary,
        PaymentRisk risk,
        RecommendedAction recommendedAction
) {
}
