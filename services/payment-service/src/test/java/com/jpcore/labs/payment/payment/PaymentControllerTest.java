package com.jpcore.labs.payment.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.security.test.context.support.WithMockUser(roles = "COMUM")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentRequestedPublisher paymentRequestedPublisher;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void createPaymentReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "create-payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 100.50,
                                  "currency": "BRL",
                                  "description": "Test payment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.amount").value(100.50))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.description").value("Test payment"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void createPaymentWithRepeatedCompletedIdempotencyKeyAndSameBodyReturnsCreatedPayment() throws Exception {
        String requestBody = """
                {
                  "amount": 150.25,
                  "currency": "BRL",
                  "description": "Repeated payment"
                }
                """;

        String firstResponse = mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "repeated-payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(firstResponse).isNotBlank();

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "repeated-payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().json(firstResponse));
    }

    @Test
    void getPaymentsReturnsAllPayments() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "first-payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 100.50,
                                  "currency": "BRL",
                                  "description": "First payment"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "second-payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 200.75,
                                  "currency": "USD",
                                  "description": "Second payment"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[?(@.description == 'First payment')]").exists())
                .andExpect(jsonPath("$[?(@.description == 'Second payment')]").exists());
    }

    @Test
    void getPaymentReturnsPaymentById() throws Exception {
        String response = mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "find-payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 125.75,
                                  "currency": "BRL",
                                  "description": "Find payment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.amount").value(125.75))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.description").value("Find payment"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void getPaymentReturnsNotFoundWhenPaymentDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentId}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPaymentWithInvalidAmountReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "invalid-amount-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 0,
                                  "currency": "BRL",
                                  "description": "Test payment"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPaymentWithBlankCurrencyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "blank-currency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 100.50,
                                  "currency": " ",
                                  "description": "Test payment"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPaymentWithoutIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 100.50,
                                  "currency": "BRL",
                                  "description": "Test payment"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
