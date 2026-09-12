package com.jpcore.labs.payment.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired ApiUserRepository users;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test
    void testSignedPaymentEventsCannotBePublishedToTheRuntimeBroker() {
        assertThat(context.getBeansOfType(com.jpcore.labs.payment.outbox.OutboxEventPublisherJob.class)).isEmpty();
    }

    @Test
    void adminCreatesBothRolesAndBothCanCreatePaymentsWithRealTokens() throws Exception {
        String admin = "admin-" + UUID.randomUUID();
        auth.createUser(admin, "test-password-123", UserRole.ADMIN);
        String adminToken = login(admin);
        for (UserRole role : UserRole.values()) {
            String name = "user-" + UUID.randomUUID();
            mvc.perform(post("/api/auth/users").header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new AuthController.CreateUserRequest(name, "test-password-123", role))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value(role.name()))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
            String token = login(name);
            assertThat(decoder.decode(token).getSubject()).isEqualTo(name);
            assertThat(users.findById(name).orElseThrow().getPasswordHash()).startsWith("$2");
            mvc.perform(post("/api/payments").header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":10,\"currency\":\"BRL\",\"description\":\"JWT test\"}"))
                    .andExpect(status().isCreated());
            if (role == UserRole.COMUM) {
                mvc.perform(post("/api/auth/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void rejectsMissingMalformedExpiredWrongAudienceAndServiceTokensForPaymentCreation() throws Exception {
        mvc.perform(post("/api/payments")).andExpect(status().isUnauthorized());
        for (String token : List.of("invalid", signed("payment-lab", Instant.now().minusSeconds(120)),
                signed("wrong", Instant.now().plusSeconds(300)))) {
            mvc.perform(post("/api/payments").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        String service = auth.serviceToken("payment-processor-service", "test-processor-secret").accessToken();
        assertThat(decoder.decode(service).getClaimAsString("identity_type")).isEqualTo("SERVICE");
        mvc.perform(post("/api/payments").header("Authorization", "Bearer " + service))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"missing\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/service-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"payment-processor-service\",\"clientSecret\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthController.LoginRequest(username, "test-password-123"))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String signed(String audience, Instant expiry) {
        return encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder().issuer("payment-service")
                .subject("admin").audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(expiry).claim("roles", List.of("ADMIN")).build())).getTokenValue();
    }
}
