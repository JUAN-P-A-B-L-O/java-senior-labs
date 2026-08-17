package com.jpcore.labs.paymentprocessor.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class PaymentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationService.class);

    private final RestClient restClient;
    private final String authorizationUrl;

    public PaymentAuthorizationService(
            RestClient.Builder restClientBuilder,
            @Value("${payment-processor.authorization-url}") String authorizationUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.authorizationUrl = authorizationUrl;
    }

    public boolean authorize(PaymentRequestedMessage message) {
        log.info("Authorization started. paymentId={} traceId={} eventId={}",
                message.paymentId(),
                message.traceId(),
                message.eventId()
        );
        try {
            AuthorizationResponse response = restClient.get()
                    .uri(authorizationUrl)
                    .retrieve()
                    .body(AuthorizationResponse.class);

            if (response != null && response.isAuthorized()) {
                log.info("Authorization succeeded. paymentId={} traceId={} eventId={}",
                        message.paymentId(),
                        message.traceId(),
                        message.eventId()
                );
                return true;
            }

            log.warn("Authorization failed. paymentId={} traceId={} eventId={}",
                    message.paymentId(),
                    message.traceId(),
                    message.eventId()
            );
            throw new PaymentAuthorizationException("Payment authorization failed for paymentId=" + message.paymentId());
        } catch (RestClientException exception) {
            log.warn("Authorization unavailable. paymentId={} traceId={} eventId={} error={}",
                    message.paymentId(),
                    message.traceId(),
                    message.eventId(),
                    exception.getMessage()
            );
            throw new PaymentAuthorizationUnavailableException(
                    "Payment authorization unavailable for paymentId=" + message.paymentId(),
                    exception
            );
        }
    }
}
