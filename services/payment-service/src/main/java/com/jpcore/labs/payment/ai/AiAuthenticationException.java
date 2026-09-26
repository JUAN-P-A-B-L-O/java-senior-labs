package com.jpcore.labs.payment.ai;

public class AiAuthenticationException extends RuntimeException {

    public AiAuthenticationException() {
        super("AI service is currently unavailable.");
    }
}
