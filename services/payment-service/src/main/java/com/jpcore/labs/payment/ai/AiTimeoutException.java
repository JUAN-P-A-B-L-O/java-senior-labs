package com.jpcore.labs.payment.ai;

public class AiTimeoutException extends RuntimeException {

    public AiTimeoutException() {
        super("AI service request timed out.");
    }
}
