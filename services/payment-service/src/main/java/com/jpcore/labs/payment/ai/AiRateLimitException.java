package com.jpcore.labs.payment.ai;

public class AiRateLimitException extends RuntimeException {

    public AiRateLimitException() {
        super("AI service rate limit exceeded. Please try again later.");
    }
}
