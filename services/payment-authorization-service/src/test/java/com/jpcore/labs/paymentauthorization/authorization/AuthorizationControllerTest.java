package com.jpcore.labs.paymentauthorization.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthorizationController.class)
class AuthorizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthorizationService authorizationService;

    @Test
    void authorizesPayment() throws Exception {
        when(authorizationService.authorize(any(AuthorizationRequest.class)))
                .thenReturn(new AuthorizationResponse(true));

        mockMvc.perform(post("/api/authorizations")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventId": "11111111-1111-1111-1111-111111111111",
                                  "traceId": "33333333-3333-3333-3333-333333333333",
                                  "paymentId": "payment-123",
                                  "amount": 10,
                                  "currency": "BRL",
                                  "description": "test payment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorized").value(true));
    }
}
