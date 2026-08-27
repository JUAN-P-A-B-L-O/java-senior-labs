package com.jpcore.labs.payment.payment;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

@Component
class TraceContextProvider {

    private final Tracer tracer;

    TraceContextProvider(Tracer tracer) {
        this.tracer = tracer;
    }

    String currentTraceParent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }

        TraceContext context = span.context();
        String traceFlags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
        return "00-" + context.traceId() + "-" + context.spanId() + "-" + traceFlags;
    }
}
