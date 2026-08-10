package com.jpcore.labs.paymentprocessor.payment;

public record AuthorizationResponse(
        String status,
        AuthorizationData data
) {
    public boolean isAuthorized() {
        return data != null && data.authorization();
    }

    public record AuthorizationData(boolean authorization) {
    }
}
