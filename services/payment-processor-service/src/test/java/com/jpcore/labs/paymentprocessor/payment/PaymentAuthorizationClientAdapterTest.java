package com.jpcore.labs.paymentprocessor.payment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentAuthorizationClientAdapterTest {

    @Test
    void postsPaymentAuthorizationRequest() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        PaymentAuthorizationClientAdapter client = new PaymentAuthorizationClientAdapter(
                restClientBuilder.build(),
                "http://authorization-service/api/authorizations",
                PaymentAuthorizationClientAdapter.retry(1, Duration.ZERO),
                PaymentAuthorizationClientAdapter.circuitBreaker(3, 3, 50.0f)
        );

        server.expect(requestTo("http://authorization-service/api/authorizations"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "eventId": "11111111-1111-1111-1111-111111111111",
                          "traceId": "33333333-3333-3333-3333-333333333333",
                          "paymentId": "payment-123",
                          "amount": 10,
                          "currency": "BRL",
                          "description": "test payment"
                        }
                        """))
                .andRespond(withSuccess("{\"authorized\":true}", MediaType.APPLICATION_JSON));

        PaymentAuthorizationClient.AuthorizationResponse response = client.authorize(paymentRequestedMessage());

        assertThat(response.authorized()).isTrue();
        server.verify();
    }

    @Test
    void retriesUnavailableAuthorizationApiWithConfiguredBackoff() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        Retry retry = PaymentAuthorizationClientAdapter.retry(3, Duration.ofMillis(1));
        CircuitBreaker circuitBreaker = PaymentAuthorizationClientAdapter.circuitBreaker(3, 3, 50.0f);
        PaymentAuthorizationClientAdapter client = new PaymentAuthorizationClientAdapter(
                restClientBuilder.build(),
                "http://authorization-service/api/authorizations",
                retry,
                circuitBreaker
        );

        server.expect(times(2), requestTo("http://authorization-service/api/authorizations"))
                .andExpect(method(POST))
                .andRespond(withServerError());
        server.expect(requestTo("http://authorization-service/api/authorizations"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"authorized\":true}", MediaType.APPLICATION_JSON));

        PaymentAuthorizationClient.AuthorizationResponse response = client.authorize(paymentRequestedMessage());

        assertThat(response.authorized()).isTrue();
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
        assertThat(retry.getRetryConfig().getIntervalBiFunction().apply(1, null)).isEqualTo(1L);
        server.verify();
    }

    @Test
    void configuresAuthorizationClientTimeout() {
        SimpleClientHttpRequestFactory requestFactory =
                PaymentAuthorizationClientAdapter.requestFactory(Duration.ofSeconds(2));

        assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout")).isEqualTo(2000);
        assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(2000);
    }

    @Test
    void configuresAuthorizationApiCircuitBreaker() {
        CircuitBreaker circuitBreaker = PaymentAuthorizationClientAdapter.circuitBreaker(3, 3, 50.0f);

        assertThat(circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(3);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
    }

    @Test
    void opensAuthorizationApiCircuitBreakerAfterThreeApiErrors() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        CircuitBreaker circuitBreaker = PaymentAuthorizationClientAdapter.circuitBreaker(3, 3, 50.0f);
        PaymentAuthorizationClientAdapter client = new PaymentAuthorizationClientAdapter(
                restClientBuilder.build(),
                "http://authorization-service/api/authorizations",
                PaymentAuthorizationClientAdapter.retry(1, Duration.ZERO),
                circuitBreaker
        );

        server.expect(times(3), requestTo("http://authorization-service/api/authorizations"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.authorize(paymentRequestedMessage())).isInstanceOf(RestClientException.class);
        assertThatThrownBy(() -> client.authorize(paymentRequestedMessage())).isInstanceOf(RestClientException.class);
        assertThatThrownBy(() -> client.authorize(paymentRequestedMessage())).isInstanceOf(RestClientException.class);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> client.authorize(paymentRequestedMessage())).isInstanceOf(CallNotPermittedException.class);
        server.verify();
    }

    @Test
    void rejectsExcessCallsWithoutContactingApiOrRecordingCircuitBreakerOutcome() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker breaker = PaymentAuthorizationClientAdapter.circuitBreaker(3, 3, 50.0f);
        RateLimiter limiter = PaymentAuthorizationClientAdapter.rateLimiter(1, Duration.ofHours(1), Duration.ZERO);
        PaymentAuthorizationClientAdapter client = new PaymentAuthorizationClientAdapter(
                builder.build(), "http://authorization-service/api/authorizations",
                PaymentAuthorizationClientAdapter.retry(3, Duration.ZERO), breaker, limiter
        );
        server.expect(requestTo("http://authorization-service/api/authorizations"))
                .andRespond(withSuccess("{\"authorized\":true}", MediaType.APPLICATION_JSON));

        assertThat(client.authorize(paymentRequestedMessage()).authorized()).isTrue();
        assertThatThrownBy(() -> client.authorize(paymentRequestedMessage()))
                .isInstanceOf(RequestNotPermitted.class);

        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        server.verify();
    }

    @Test
    void retryAttemptsConsumeRateLimitPermits() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker breaker = PaymentAuthorizationClientAdapter.circuitBreaker(3, 3, 50.0f);
        PaymentAuthorizationClientAdapter client = new PaymentAuthorizationClientAdapter(
                builder.build(), "http://authorization-service/api/authorizations",
                PaymentAuthorizationClientAdapter.retry(3, Duration.ZERO), breaker,
                PaymentAuthorizationClientAdapter.rateLimiter(2, Duration.ofHours(1), Duration.ZERO)
        );
        server.expect(times(2), requestTo("http://authorization-service/api/authorizations"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.authorize(paymentRequestedMessage()))
                .isInstanceOf(RequestNotPermitted.class);

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }

    @Test
    void openCircuitDoesNotConsumeRateLimitPermits() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker breaker = PaymentAuthorizationClientAdapter.circuitBreaker(3, 3, 50.0f);
        breaker.transitionToOpenState();
        RateLimiter limiter = PaymentAuthorizationClientAdapter.rateLimiter(1, Duration.ofHours(1), Duration.ZERO);
        PaymentAuthorizationClientAdapter client = new PaymentAuthorizationClientAdapter(
                builder.build(), "http://authorization-service/api/authorizations",
                PaymentAuthorizationClientAdapter.retry(3, Duration.ZERO), breaker, limiter
        );

        assertThatThrownBy(() -> client.authorize(paymentRequestedMessage()))
                .isInstanceOf(CallNotPermittedException.class);

        assertThat(limiter.getMetrics().getAvailablePermissions()).isEqualTo(1);
        server.verify();
    }

    private PaymentRequestedMessage paymentRequestedMessage() {
        return new PaymentRequestedMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
    }
}
