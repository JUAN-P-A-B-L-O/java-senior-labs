package com.jpcore.labs.paymentprocessor.payment;

import org.slf4j.MDC;

import java.util.UUID;

public final class PaymentLogContext implements AutoCloseable {

    private static final String TRACE_ID = "traceId";
    private static final String PAYMENT_ID = "paymentId";

    private final String previousRequestedBy;
    private final String previousTraceId;
    private final String previousPaymentId;

    private PaymentLogContext(String traceId, String paymentId, String requestedBy) {
        this.previousRequestedBy = MDC.get("requestedBy");
        putOrRemove("requestedBy", requestedBy == null ? null : requestedBy.replaceAll("[^a-zA-Z0-9._@-]", "_"));
        this.previousTraceId = MDC.get(TRACE_ID);
        this.previousPaymentId = MDC.get(PAYMENT_ID);
        putOrRemove(TRACE_ID, traceId);
        putOrRemove(PAYMENT_ID, paymentId);
    }

    public static PaymentLogContext with(UUID traceId, String paymentId) {
        return new PaymentLogContext(traceId == null ? null : traceId.toString(), paymentId, MDC.get("requestedBy"));
    }

    public static PaymentLogContext with(UUID traceId, UUID paymentId) {
        return with(traceId, paymentId == null ? null : paymentId.toString());
    }

    public static PaymentLogContext with(UUID traceId, String paymentId, String requestedBy) {
        return new PaymentLogContext(traceId == null ? null : traceId.toString(), paymentId, requestedBy);
    }

    public static PaymentLogContext with(UUID traceId, UUID paymentId, String requestedBy) {
        return with(traceId, paymentId == null ? null : paymentId.toString(), requestedBy);
    }

    public void requestedBy(String requestedBy) {
        putOrRemove("requestedBy", requestedBy == null ? null : requestedBy.replaceAll("[^a-zA-Z0-9._@-]", "_"));
    }

    @Override
    public void close() {
        putOrRemove("requestedBy", previousRequestedBy);
        putOrRemove(TRACE_ID, previousTraceId);
        putOrRemove(PAYMENT_ID, previousPaymentId);
    }

    private static void putOrRemove(String key, String value) {
        if (value == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }
}
