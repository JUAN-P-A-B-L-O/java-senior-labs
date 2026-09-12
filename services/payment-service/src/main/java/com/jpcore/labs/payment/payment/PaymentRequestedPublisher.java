package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.outbox.OutboxEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentRequestedPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestedPublisher.class);

    private final OutboxEventService outboxEventService;
    private final TraceContextProvider traceContextProvider;

    public PaymentRequestedPublisher(OutboxEventService outboxEventService, TraceContextProvider traceContextProvider) {
        this.outboxEventService = outboxEventService;
        this.traceContextProvider = traceContextProvider;
    }

    private String callerToken() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt
                ? jwt.getToken().getTokenValue() : null;
    }

    public void publish(PaymentEntity payment) {
        publish(payment, UUID.randomUUID());
    }

    public void publish(PaymentEntity payment, UUID traceId) {
        UUID eventId = UUID.randomUUID();

        PaymentRequestedMessage message = new PaymentRequestedMessage(
                eventId,
                traceId,
                payment.getId().toString(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription(),
                traceContextProvider.currentTraceParent(),
                callerToken()
        );
        outboxEventService.savePaymentRequested(message);
        log.info("PaymentRequested saved to outbox. eventId={}", eventId);
    }
}
