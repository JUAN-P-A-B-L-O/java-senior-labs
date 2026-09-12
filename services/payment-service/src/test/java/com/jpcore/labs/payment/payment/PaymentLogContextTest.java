package com.jpcore.labs.payment.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PaymentLogContextTest {
    @AfterEach void cleanup() { MDC.clear(); }

    @Test
    void nestedPaymentScopesPreserveUserAndLegacyEventsClearIt() {
        MDC.put("requestedBy", "outer");
        try (var event = PaymentLogContext.with(null, "payment", "alice")) {
            try (var nested = PaymentLogContext.with(UUID.randomUUID(), "payment")) {
                assertThat(MDC.get("requestedBy")).isEqualTo("alice");
            }
            try (var legacy = PaymentLogContext.with(null, "old-payment", null)) {
                assertThat(MDC.get("requestedBy")).isNull();
            }
            assertThat(MDC.get("requestedBy")).isEqualTo("alice");
        }
        assertThat(MDC.get("requestedBy")).isEqualTo("outer");
    }

    @Test
    void messagesRoundTripIdentityAndAcceptLegacyPayloads() throws Exception {
        var mapper = new ObjectMapper();
        for (var type : new Class<?>[] {PaymentRequestedMessage.class, PaymentProcessedMessage.class,
                PaymentProcessingFailedMessage.class}) {
            var message = mapper.readValue("{\"paymentId\":\"payment\",\"requestedBy\":\"alice\"}", type);
            assertThat(mapper.readTree(mapper.writeValueAsString(message)).get("requestedBy").asText())
                    .isEqualTo("alice");
            var legacy = mapper.readValue("{\"paymentId\":\"payment\"}", type);
            assertThat(mapper.readTree(mapper.writeValueAsString(legacy)).get("requestedBy").isNull()).isTrue();
        }
    }
}
