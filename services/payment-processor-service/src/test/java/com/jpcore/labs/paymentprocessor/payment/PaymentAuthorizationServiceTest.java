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
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentAuthorizationServiceTest {

    private static final String AUTHORIZATION_URL = "https://util.devi.tools/api/v2/authorize";

    private MockRestServiceServer server;
    private PaymentAuthorizationService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        service = new PaymentAuthorizationService(restClientBuilder, AUTHORIZATION_URL);
    }

    @Test
    void returnsTrueWhenAuthorizationEndpointApprovesPayment() {
        server.expect(requestTo(AUTHORIZATION_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{ \"status\" : \"success\", \"data\" : { \"authorization\" : true } }",
                        MediaType.APPLICATION_JSON
                ));

        boolean authorized = service.authorize(paymentRequestedMessage());

        assertThat(authorized).isTrue();
        server.verify();
    }

    @Test
    void throwsExceptionWhenAuthorizationEndpointDeniesPayment() {
        server.expect(requestTo(AUTHORIZATION_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{ \"status\" : \"fail\", \"data\" : { \"authorization\" : false } }",
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> service.authorize(paymentRequestedMessage()))
                .isInstanceOf(PaymentAuthorizationException.class)
                .hasMessage("Payment authorization failed for paymentId=payment-123");

        server.verify();
    }

    @Test
    void throwsExceptionWhenAuthorizationEndpointFails() {
        server.expect(requestTo(AUTHORIZATION_URL))
                .andExpect(method(GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.authorize(paymentRequestedMessage()))
                .isInstanceOf(PaymentAuthorizationUnavailableException.class)
                .hasMessage("Payment authorization unavailable for paymentId=payment-123");

        server.verify();
    }

    private PaymentRequestedMessage paymentRequestedMessage() {
        return new PaymentRequestedMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
    }
}
