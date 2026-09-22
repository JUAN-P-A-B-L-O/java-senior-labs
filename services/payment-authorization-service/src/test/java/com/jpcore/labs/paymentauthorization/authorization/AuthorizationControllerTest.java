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

@org.springframework.context.annotation.Import(com.jpcore.labs.paymentauthorization.security.SecurityConfig.class)
@WebMvcTest(AuthorizationController.class)
class AuthorizationControllerTest {

    @MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthorizationService authorizationService;

    @org.springframework.security.test.context.support.WithMockUser(roles = "SERVICE")
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
    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(post("/api/authorizations").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preservesTheAuthenticatedCallerForEveryAllowedRole() throws Exception {
        for (String role : java.util.List.of("ADMIN", "COMUM", "SERVICE")) {
            when(authorizationService.authorize(any(AuthorizationRequest.class))).thenAnswer(invocation -> {
                var caller = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                org.assertj.core.api.Assertions.assertThat(caller.getName()).isEqualTo("caller-" + role);
                return new AuthorizationResponse(true);
            });
            mockMvc.perform(post("/api/authorizations")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                            .jwt(jwt -> jwt.subject("caller-" + role))
                            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType("application/json").content("{\"paymentId\":\"payment-123\",\"amount\":10,\"currency\":\"BRL\"}"))
                    .andExpect(status().isOk());
        }
    }
}
