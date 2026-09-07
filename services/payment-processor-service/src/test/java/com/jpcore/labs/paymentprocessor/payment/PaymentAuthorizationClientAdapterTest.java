package com.jpcore.labs.paymentprocessor.payment;

import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
                PaymentAuthorizationClientAdapter.retry(1, Duration.ZERO)
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
        PaymentAuthorizationClientAdapter client = new PaymentAuthorizationClientAdapter(
                restClientBuilder.build(),
                "http://authorization-service/api/authorizations",
                retry
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
