package com.jpcore.labs.paymentprocessor.security;

import com.jpcore.labs.paymentprocessor.payment.PaymentRequestedMessage;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CallerTokenProviderTest {
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void forwardsValidatedUserTokenWithoutServiceLogin() {
        when(decoder.decode("user-token")).thenReturn(jwt("user-token"));
        var provider = new CallerTokenProvider(decoder, builder.build(), "http://auth/token", "secret");
        assertThat(provider.tokenFor(message("user-token"))).isEqualTo("user-token");
        verify(decoder).decode("user-token");
        server.verify();
    }

    @Test
    void invalidUserTokenNeverFallsBackToServiceIdentity() {
        when(decoder.decode("expired")).thenThrow(new JwtException("Expired"));
        var provider = new CallerTokenProvider(decoder, builder.build(), "http://auth/token", "secret");
        assertThatThrownBy(() -> provider.tokenFor(message("expired"))).isInstanceOf(JwtException.class);
        server.verify();
    }

    @Test
    void obtainsAndCachesServiceIdentityOnlyWithoutUserContext() {
        server.expect(requestTo("http://auth/token"))
                .andExpect(content().json("{\"clientId\":\"payment-processor-service\",\"clientSecret\":\"secret\"}"))
                .andRespond(withSuccess("{\"accessToken\":\"service-token\",\"tokenType\":\"Bearer\",\"expiresIn\":300}", MediaType.APPLICATION_JSON));
        when(decoder.decode("service-token")).thenReturn(jwt("service-token"));
        var provider = new CallerTokenProvider(decoder, builder.build(), "http://auth/token", "secret");
        assertThat(provider.tokenFor(message(null))).isEqualTo("service-token");
        assertThat(provider.tokenFor(message(null))).isEqualTo("service-token");
        server.verify();
    }

    private Jwt jwt(String token) {
        return Jwt.withTokenValue(token).header("alg", "RS256").subject("caller")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }
    private PaymentRequestedMessage message(String token) {
        return new PaymentRequestedMessage(UUID.randomUUID(), UUID.randomUUID(), "id", BigDecimal.ONE, "BRL", "test", null, token);
    }
}
