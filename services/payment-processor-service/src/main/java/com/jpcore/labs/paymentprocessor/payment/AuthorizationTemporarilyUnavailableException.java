package com.jpcore.labs.paymentprocessor.payment;

public class AuthorizationTemporarilyUnavailableException extends PaymentAuthorizationUnavailableException {

    public AuthorizationTemporarilyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
