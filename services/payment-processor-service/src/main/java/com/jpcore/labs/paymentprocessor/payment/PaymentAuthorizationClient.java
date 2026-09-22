package com.jpcore.labs.paymentprocessor.payment;

interface PaymentAuthorizationClient {

    AuthorizationResponse authorize(PaymentRequestedMessage message);

    record AuthorizationResponse(Boolean authorized) {
    }
}
