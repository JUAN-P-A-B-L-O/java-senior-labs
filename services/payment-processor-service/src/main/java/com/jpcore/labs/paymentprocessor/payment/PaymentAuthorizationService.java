package com.jpcore.labs.paymentprocessor.payment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class PaymentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationService.class);

    private final PaymentAuthorizationClient authorizationClient;

    public PaymentAuthorizationService(PaymentAuthorizationClient authorizationClient) {
        this.authorizationClient = authorizationClient;
    }

    public boolean authorize(PaymentRequestedMessage message) {
        try (PaymentLogContext ignored = PaymentLogContext.with(message.traceId(), message.paymentId())) {
            log.info("Authorization started. eventId={}", message.eventId());

            PaymentAuthorizationClient.AuthorizationResponse response;

            try {
                response = authorizationClient.authorize(message);
            } catch (RestClientException | CallNotPermittedException exception) {
                log.warn("Authorization service unavailable. eventId={} paymentId={} message={}",
                        message.eventId(),
                        message.paymentId(),
                        exception.getMessage()
                );
                throw new PaymentAuthorizationUnavailableException(
                        "Payment authorization unavailable for paymentId=" + message.paymentId(),
                        exception
                );
            }

            if (response != null && Boolean.TRUE.equals(response.authorized())) {
                log.info("Authorization succeeded. eventId={}", message.eventId());
                return true;
            }

            log.warn("Authorization failed. eventId={}", message.eventId());
            throw new PaymentAuthorizationException("Payment authorization failed for paymentId=" + message.paymentId());
        }
    }
}
