package com.jpcore.labs.paymentauthorization.authorization;

public class AuthorizationSimulationInterruptedException extends RuntimeException {

    public AuthorizationSimulationInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
