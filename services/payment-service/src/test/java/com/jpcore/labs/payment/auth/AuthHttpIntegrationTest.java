package com.jpcore.labs.payment.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired AuthService auth;

    @Test
    void loginWithoutAuthorizationHeaderReturnsToken() {
        String username = "http-admin-" + UUID.randomUUID();
        auth.createUser(username, "http-test-password", UserRole.ADMIN);
        var response = http.postForEntity("/api/auth/login",
                Map.of("username", username, "password", "http-test-password"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("accessToken", "expiresIn");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    void inheritedInvalidBearerTokenCannotBlockValidLogin() {
        String username = "inherited-token-" + UUID.randomUUID();
        auth.createUser(username, "http-test-password", UserRole.ADMIN);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("invalid-inherited-token");
        var valid = new HttpEntity<>(Map.of("username", username, "password", "http-test-password"), headers);
        var response = http.postForEntity("/api/auth/login", valid, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
        var invalid = new HttpEntity<>(Map.of("username", username, "password", "wrong-password"), headers);
        var rejected = http.postForEntity("/api/auth/login", invalid, Map.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rejected.getBody()).containsEntry("detail", "Invalid credentials");
        assertThat(http.exchange("/api/payments", HttpMethod.POST, valid, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void serviceLoginAlsoUsesBodyCredentialsInsteadOfInheritedBearer() {
        var headers = new HttpHeaders();
        headers.setBearerAuth("invalid-inherited-token");
        var body = Map.of("clientId", "payment-processor-service", "clientSecret", "test-processor-secret");
        var response = http.postForEntity("/api/auth/service-token", new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
    }

    @Test
    void missingFieldsAndMalformedJsonKeepTheirBadRequestStatusThroughErrorDispatch() {
        var missing = http.postForEntity("/api/auth/login", Map.of(), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var malformed = http.postForEntity("/api/auth/login", new HttpEntity<>("{", headers), Map.class);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    void wrongPasswordReturnsExplicitCredentialErrorWithoutBearerChallenge() {
        var response = http.postForEntity("/api/auth/login",
                Map.of("username", "missing-http-user", "password", "wrong-password"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("detail", "Invalid credentials");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    void paymentRequestsStillRequireBearerAuthentication() {
        assertThat(http.postForEntity("/api/payments", Map.of(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
