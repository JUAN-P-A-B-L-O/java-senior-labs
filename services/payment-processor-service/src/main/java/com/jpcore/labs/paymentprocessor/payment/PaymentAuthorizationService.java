package com.jpcore.labs.paymentprocessor.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Service
public class PaymentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationService.class);

    private final RestClient restClient;
    private final String authorizationUrl;
    private final int authorizationMaxAttempts;
    private final Duration authorizationBackoff;
    private final BackoffSleeper backoffSleeper;

    @Autowired
    public PaymentAuthorizationService(
            RestClient.Builder restClientBuilder,
            @Value("${payment-processor.authorization-url}") String authorizationUrl,
            @Value("${payment-processor.authorization-timeout}") Duration authorizationTimeout,
            @Value("${payment-processor.authorization-max-attempts}") int authorizationMaxAttempts,
            @Value("${payment-processor.authorization-backoff}") Duration authorizationBackoff
    ) {
        this(restClientBuilder
                .requestFactory(requestFactory(authorizationTimeout))
                .build(), authorizationUrl, authorizationMaxAttempts, authorizationBackoff, Thread::sleep);
    }

    PaymentAuthorizationService(RestClient restClient, String authorizationUrl) {
        this(restClient, authorizationUrl, 1, Duration.ZERO, ignored -> {
        });
    }

    PaymentAuthorizationService(
            RestClient restClient,
            String authorizationUrl,
            int authorizationMaxAttempts,
            Duration authorizationBackoff,
            BackoffSleeper backoffSleeper
    ) {
        if (authorizationMaxAttempts < 1) {
            throw new IllegalArgumentException("authorizationMaxAttempts must be greater than zero");
        }
        this.restClient = restClient;
        this.authorizationUrl = authorizationUrl;
        this.authorizationMaxAttempts = authorizationMaxAttempts;
        this.authorizationBackoff = authorizationBackoff;
        this.backoffSleeper = backoffSleeper;
    }

    static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    public boolean authorize(PaymentRequestedMessage message) {
        try (PaymentLogContext ignored = PaymentLogContext.with(message.traceId(), message.paymentId())) {
            log.info("Authorization started. eventId={}", message.eventId());
            AuthorizationResponse response = authorizeWithRetry(message);

            if (response != null && Boolean.TRUE.equals(response.authorized())) {
                log.info("Authorization succeeded. eventId={}", message.eventId());
                return true;
            }

            log.warn("Authorization failed. eventId={}", message.eventId());
            throw new PaymentAuthorizationException("Payment authorization failed for paymentId=" + message.paymentId());
        }
    }

    private AuthorizationResponse authorizeWithRetry(PaymentRequestedMessage message) {
        RestClientException lastException = null;
        for (int attempt = 1; attempt <= authorizationMaxAttempts; attempt++) {
            try {
                return requestAuthorization(message);
            } catch (RestClientException exception) {
                lastException = exception;
                if (attempt < authorizationMaxAttempts) {
                    log.warn("Authorization service unavailable. eventId={} paymentId={} attempt={} maxAttempts={} message={}",
                            message.eventId(),
                            message.paymentId(),
                            attempt,
                            authorizationMaxAttempts,
                            exception.getMessage()
                    );
                    waitBeforeRetry(message, exception);
                }
            }
        }

        log.warn("Authorization service unavailable. eventId={} paymentId={} maxAttempts={} message={}",
                message.eventId(),
                message.paymentId(),
                authorizationMaxAttempts,
                lastException.getMessage()
        );
        throw new PaymentAuthorizationUnavailableException(
                "Payment authorization unavailable for paymentId=" + message.paymentId(),
                lastException
        );
    }

    private AuthorizationResponse requestAuthorization(PaymentRequestedMessage message) {
        return restClient.post()
                .uri(authorizationUrl)
                .body(AuthorizationRequest.from(message))
                .retrieve()
                .body(AuthorizationResponse.class);
    }

    private void waitBeforeRetry(PaymentRequestedMessage message, RestClientException cause) {
        try {
            backoffSleeper.sleep(authorizationBackoff);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentAuthorizationUnavailableException(
                    "Payment authorization unavailable for paymentId=" + message.paymentId(),
                    cause
            );
        }
    }

    interface BackoffSleeper {
        void sleep(Duration duration) throws InterruptedException;
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
