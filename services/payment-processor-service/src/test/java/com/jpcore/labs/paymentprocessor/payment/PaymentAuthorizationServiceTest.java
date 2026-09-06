package com.jpcore.labs.paymentprocessor.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class PaymentAuthorizationServiceTest {

    private PaymentAuthorizationService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        service = new PaymentAuthorizationService(restClientBuilder.build(), "http://authorization-service/api/authorizations");
    }

    @Test
    void returnsTrueWhenHttpAuthorizationApprovesPayment() {
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

        boolean authorized = service.authorize(paymentRequestedMessage());

        assertThat(authorized).isTrue();
        server.verify();
    }

    @Test
    void throwsExceptionWhenHttpAuthorizationDeniesPayment() {
        server.expect(requestTo("http://authorization-service/api/authorizations"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"authorized\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.authorize(paymentRequestedMessage()))
                .isInstanceOf(PaymentAuthorizationException.class)
                .hasMessage("Payment authorization failed for paymentId=payment-123");
        server.verify();
    }

    @Test
    void throwsUnavailableExceptionWhenAuthorizationServiceFails() {
        server.expect(requestTo("http://authorization-service/api/authorizations"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.authorize(paymentRequestedMessage()))
                .isInstanceOf(PaymentAuthorizationUnavailableException.class)
                .hasMessage("Payment authorization unavailable for paymentId=payment-123");
        server.verify();
    }

    @Test
    void retriesUnavailableAuthorizationApiWithConfiguredBackoff() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer retryServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        AtomicInteger backoffCount = new AtomicInteger();
        PaymentAuthorizationService retryService = new PaymentAuthorizationService(
                restClientBuilder.build(),
                "http://authorization-service/api/authorizations",
                3,
                Duration.ofMillis(1000),
                duration -> {
                    assertThat(duration).isEqualTo(Duration.ofMillis(1000));
                    backoffCount.incrementAndGet();
                }
        );

        retryServer.expect(requestTo("http://authorization-service/api/authorizations"))
                .andExpect(method(POST))
                .andRespond(withServerError());
        retryServer.expect(requestTo("http://authorization-service/api/authorizations"))
                .andExpect(method(POST))
                .andRespond(withServerError());
        retryServer.expect(requestTo("http://authorization-service/api/authorizations"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"authorized\":true}", MediaType.APPLICATION_JSON));

        boolean authorized = retryService.authorize(paymentRequestedMessage());

        assertThat(authorized).isTrue();
        assertThat(backoffCount).hasValue(2);
        retryServer.verify();
    }

    @Test
    void configuresAuthorizationClientTimeout() {
        SimpleClientHttpRequestFactory requestFactory =
                PaymentAuthorizationService.requestFactory(Duration.ofSeconds(2));

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
