package com.jpcore.labs.paymentprocessor.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RequestLogContextFilterTest {
    @AfterEach
    void cleanup() { MDC.clear(); SecurityContextHolder.clearContext(); }

    @Test
    void authenticatedIdentityIsAvailableAndRestoredEvenOnFailure() {
        var jwt = Jwt.withTokenValue("secret-token").header("alg", "RS256").subject("alice").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        MDC.put("requestedBy", "outer");
        assertThatThrownBy(() -> new RequestLogContextFilter().doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> {
                    assertThat(MDC.get("requestedBy")).isEqualTo("alice");
                    assertThat(MDC.getCopyOfContextMap().values()).doesNotContain("secret-token");
                    throw new jakarta.servlet.ServletException("test failure");
                })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(MDC.get("requestedBy")).isEqualTo("outer");
    }

    @Test
    void anonymousRequestDoesNotInheritIdentityOrTrustHeaders() throws Exception {
        MDC.put("requestedBy", "previous-user");
        var request = new MockHttpServletRequest();
        request.addHeader("requestedBy", "spoofed-user");
        new RequestLogContextFilter().doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(MDC.get("requestedBy")).isNull());
        assertThat(MDC.get("requestedBy")).isEqualTo("previous-user");
    }

    @Test
    void cleansUpIdentityAfterSuccessfulRequest() throws Exception {
        var jwt = Jwt.withTokenValue("secret").header("alg", "RS256").subject("alice").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        new RequestLogContextFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> assertThat(MDC.get("requestedBy")).isEqualTo("alice"));
        assertThat(MDC.get("requestedBy")).isNull();
    }
}
