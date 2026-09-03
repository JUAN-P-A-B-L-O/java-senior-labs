package com.jpcore.labs.paymentprocessor.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationService.class);

    private final RestClient restClient;
    private final String authorizationUrl;

    @Autowired
    public PaymentAuthorizationService(
            RestClient.Builder restClientBuilder,
            @Value("${payment-processor.authorization-url}") String authorizationUrl
    ) {
        this(restClientBuilder.build(), authorizationUrl);
    }

    PaymentAuthorizationService(RestClient restClient, String authorizationUrl) {
        this.restClient = restClient;
        this.authorizationUrl = authorizationUrl;
    }

    public boolean authorize(PaymentRequestedMessage message) {
        try (PaymentLogContext ignored = PaymentLogContext.with(message.traceId(), message.paymentId())) {
            log.info("Authorization started. eventId={}", message.eventId());
            AuthorizationResponse response;
            try {
                response = restClient.post()
                        .uri(authorizationUrl)
                        .body(AuthorizationRequest.from(message))
                        .retrieve()
                        .body(AuthorizationResponse.class);
            } catch (RestClientException exception) {
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

    private record AuthorizationRequest(
            UUID eventId,
            UUID traceId,
            String paymentId,
            BigDecimal amount,
            String currency,
            String description
    ) {
        private static AuthorizationRequest from(PaymentRequestedMessage message) {
            return new AuthorizationRequest(
                    message.eventId(),
                    message.traceId(),
                    message.paymentId(),
                    message.amount(),
                    message.currency(),
                    message.description()
            );
        }
    }

    private record AuthorizationResponse(Boolean authorized) {
    }
}
