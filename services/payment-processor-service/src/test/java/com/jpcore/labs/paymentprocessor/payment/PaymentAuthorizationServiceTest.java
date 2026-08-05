package com.jpcore.labs.paymentprocessor.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

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
    void returnsFalseWhenAuthorizationEndpointDeniesPayment() {
        server.expect(requestTo(AUTHORIZATION_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{ \"status\" : \"fail\", \"data\" : { \"authorization\" : false } }",
                        MediaType.APPLICATION_JSON
                ));

        boolean authorized = service.authorize(paymentRequestedMessage());

        assertThat(authorized).isFalse();
        server.verify();
    }

    @Test
    void returnsFalseWhenAuthorizationEndpointFails() {
        server.expect(requestTo(AUTHORIZATION_URL))
                .andExpect(method(GET))
                .andRespond(withServerError());

        boolean authorized = service.authorize(paymentRequestedMessage());

        assertThat(authorized).isFalse();
        server.verify();
    }

    private PaymentRequestedMessage paymentRequestedMessage() {
        return new PaymentRequestedMessage(
                "payment-123",
                BigDecimal.TEN,
                "BRL",
                "test payment"
        );
    }
}
