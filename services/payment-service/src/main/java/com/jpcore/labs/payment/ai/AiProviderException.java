package com.jpcore.labs.payment.ai;

public class AiProviderException extends RuntimeException {

    public AiProviderException() {
        super("AI service is currently unavailable.");
    }
}
