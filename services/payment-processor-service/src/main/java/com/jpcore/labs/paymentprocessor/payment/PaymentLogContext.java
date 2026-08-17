package com.jpcore.labs.paymentprocessor.payment;

import org.slf4j.MDC;

import java.util.UUID;

public final class PaymentLogContext implements AutoCloseable {

    private static final String TRACE_ID = "traceId";
    private static final String PAYMENT_ID = "paymentId";

    private final String previousTraceId;
    private final String previousPaymentId;

    private PaymentLogContext(String traceId, String paymentId) {
        this.previousTraceId = MDC.get(TRACE_ID);
        this.previousPaymentId = MDC.get(PAYMENT_ID);
        putOrRemove(TRACE_ID, traceId);
        putOrRemove(PAYMENT_ID, paymentId);
    }

    public static PaymentLogContext with(UUID traceId, String paymentId) {
        return new PaymentLogContext(traceId == null ? null : traceId.toString(), paymentId);
    }

    public static PaymentLogContext with(UUID traceId, UUID paymentId) {
        return with(traceId, paymentId == null ? null : paymentId.toString());
    }

    @Override
    public void close() {
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
