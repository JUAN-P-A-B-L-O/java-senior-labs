package com.jpcore.labs.paymentprocessor.payment;

public class PaymentAuthorizationUnavailableException extends RuntimeException {

    public PaymentAuthorizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
