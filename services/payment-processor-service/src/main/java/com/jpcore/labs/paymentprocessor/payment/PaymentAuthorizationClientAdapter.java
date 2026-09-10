package com.jpcore.labs.paymentprocessor.payment;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Component
class PaymentAuthorizationClientAdapter implements PaymentAuthorizationClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationClientAdapter.class);

    private final RestClient restClient;
    private final String authorizationUrl;
    private final Retry authorizationRetry;
    private final CircuitBreaker authorizationCircuitBreaker;
    private final RateLimiter authorizationRateLimiter;
    private final Bulkhead authorizationBulkhead;

    @Autowired
    PaymentAuthorizationClientAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${payment-processor.authorization-url}") String authorizationUrl,
            @Value("${payment-processor.authorization-timeout}") Duration authorizationTimeout,
            @Value("${payment-processor.authorization-max-attempts}") int authorizationMaxAttempts,
            @Value("${payment-processor.authorization-backoff}") Duration authorizationBackoff,
            @Value("${payment-processor.authorization-circuit-breaker-sliding-window-size}") int circuitBreakerSlidingWindowSize,
            @Value("${payment-processor.authorization-circuit-breaker-minimum-calls}") int circuitBreakerMinimumCalls,
            @Value("${payment-processor.authorization-circuit-breaker-failure-rate-threshold}") float circuitBreakerFailureRateThreshold,
            @Value("${payment-processor.authorization-rate-limit-for-period}") int rateLimitForPeriod,
            @Value("${payment-processor.authorization-rate-limit-refresh-period}") Duration rateLimitRefreshPeriod,
            @Value("${payment-processor.authorization-rate-limit-timeout}") Duration rateLimitTimeout,
            @Value("${payment-processor.authorization-bulkhead-max-concurrent-calls}") int bulkheadMaxConcurrentCalls,
            @Value("${payment-processor.authorization-bulkhead-max-wait-duration}") Duration bulkheadMaxWaitDuration
    ) {
        this(
                restClientBuilder
                        .requestFactory(requestFactory(authorizationTimeout))
                        .build(),
                authorizationUrl,
                retry(authorizationMaxAttempts, authorizationBackoff),
                circuitBreaker(
                        circuitBreakerSlidingWindowSize,
                        circuitBreakerMinimumCalls,
                        circuitBreakerFailureRateThreshold
                ),
                rateLimiter(rateLimitForPeriod, rateLimitRefreshPeriod, rateLimitTimeout),
                bulkhead(bulkheadMaxConcurrentCalls, bulkheadMaxWaitDuration)
        );
    }

    PaymentAuthorizationClientAdapter(RestClient restClient, String authorizationUrl, Retry authorizationRetry) {
        this(restClient, authorizationUrl, authorizationRetry, circuitBreaker(3, 3, 50.0f));
    }

    PaymentAuthorizationClientAdapter(
            RestClient restClient,
            String authorizationUrl,
            Retry authorizationRetry,
            CircuitBreaker authorizationCircuitBreaker
    ) {
        this(restClient, authorizationUrl, authorizationRetry, authorizationCircuitBreaker,
                rateLimiter(5, Duration.ofSeconds(1), Duration.ZERO));
    }

    PaymentAuthorizationClientAdapter(
            RestClient restClient,
            String authorizationUrl,
            Retry authorizationRetry,
            CircuitBreaker authorizationCircuitBreaker,
            RateLimiter authorizationRateLimiter
    ) {
        this(restClient, authorizationUrl, authorizationRetry, authorizationCircuitBreaker,
                authorizationRateLimiter, bulkhead(2, Duration.ZERO));
    }

    PaymentAuthorizationClientAdapter(
            RestClient restClient,
            String authorizationUrl,
            Retry authorizationRetry,
            CircuitBreaker authorizationCircuitBreaker,
            RateLimiter authorizationRateLimiter,
            Bulkhead authorizationBulkhead
    ) {
        this.restClient = restClient;
        this.authorizationUrl = authorizationUrl;
        this.authorizationRetry = authorizationRetry;
        this.authorizationCircuitBreaker = authorizationCircuitBreaker;
        this.authorizationRateLimiter = authorizationRateLimiter;
        this.authorizationBulkhead = authorizationBulkhead;
    }

    static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    static Retry retry(int maxAttempts, Duration backoff) {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(backoff)
                .retryExceptions(RestClientException.class)
                .build();
        Retry retry = Retry.of("paymentAuthorizationApi", retryConfig);
        retry.getEventPublisher()
                .onRetry(event -> log.warn(
                        "Authorization API retry scheduled. attempt={} maxAttempts={} waitInterval={} cause={}",
                        event.getNumberOfRetryAttempts(),
                        maxAttempts,
                        backoff,
                        event.getLastThrowable().getMessage()
                ));
        return retry;
    }

    static CircuitBreaker circuitBreaker(int slidingWindowSize, int minimumCalls, float failureRateThreshold) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRateThreshold)
                .ignoreExceptions(RequestNotPermitted.class, BulkheadFullException.class)
                .recordExceptions(RestClientException.class)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("paymentAuthorizationApi", circuitBreakerConfig);
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "Authorization API circuit breaker state changed. transition={}",
                        event.getStateTransition()
                ));
        return circuitBreaker;
    }

    static RateLimiter rateLimiter(int limitForPeriod, Duration refreshPeriod, Duration timeout) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(refreshPeriod)
                .timeoutDuration(timeout)
                .build();
        return RateLimiter.of("paymentAuthorizationApi", config);
    }

    static Bulkhead bulkhead(int maxConcurrentCalls, Duration maxWaitDuration) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(maxWaitDuration)
                .build();
        return Bulkhead.of("paymentAuthorizationApi", config);
    }

    @Override
    public AuthorizationResponse authorize(PaymentRequestedMessage message) {
        return authorizationRetry.executeSupplier(
                CircuitBreaker.decorateSupplier(authorizationCircuitBreaker,
                        Bulkhead.decorateSupplier(authorizationBulkhead,
                                RateLimiter.decorateSupplier(authorizationRateLimiter,
                                        () -> requestAuthorization(message))))
        );
    }

    private AuthorizationResponse requestAuthorization(PaymentRequestedMessage message) {
        return restClient.post()
                .uri(authorizationUrl)
                .body(AuthorizationRequest.from(message))
                .retrieve()
                .body(AuthorizationResponse.class);
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
}
