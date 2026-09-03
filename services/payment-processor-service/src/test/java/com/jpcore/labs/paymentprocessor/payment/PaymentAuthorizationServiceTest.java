package com.jpcore.labs.paymentprocessor.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

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
