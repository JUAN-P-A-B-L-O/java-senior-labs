package com.jpcore.labs.payment.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class PaymentMetrics {

    private final Counter paymentsCreated;
    private final Counter paymentsCompleted;
    private final Counter paymentsFailed;
    private final Timer paymentProcessingDuration;

    PaymentMetrics(MeterRegistry meterRegistry) {
        this.paymentsCreated = Counter.builder("payments_created")
                .description("Total number of payments created")
                .register(meterRegistry);
        this.paymentsCompleted = Counter.builder("payments_completed")
                .description("Total number of payments completed")
                .register(meterRegistry);
        this.paymentsFailed = Counter.builder("payments_failed")
                .description("Total number of payments failed")
                .register(meterRegistry);
        this.paymentProcessingDuration = Timer.builder("payment_processing_duration")
                .description("Time from payment creation to terminal status")
                .register(meterRegistry);
    }

    void recordCreated() {
        paymentsCreated.increment();
    }

    void recordCompleted(Duration processingDuration) {
        paymentsCompleted.increment();
        paymentProcessingDuration.record(processingDuration);
    }

    void recordFailed(Duration processingDuration) {
        paymentsFailed.increment();
        paymentProcessingDuration.record(processingDuration);
    }
}
