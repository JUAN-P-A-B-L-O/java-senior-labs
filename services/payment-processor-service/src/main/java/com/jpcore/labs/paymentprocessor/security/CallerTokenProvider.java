package com.jpcore.labs.paymentprocessor.security;

import com.jpcore.labs.paymentprocessor.payment.PaymentRequestedMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class CallerTokenProvider {
    private final JwtDecoder decoder;
    private final RestClient client;
    private final String tokenUrl;
    private final String password;
    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    public CallerTokenProvider(JwtDecoder decoder, RestClient.Builder builder,
            @Value("${AUTH_TOKEN_URL:http://localhost:8080/api/auth/service-token}") String tokenUrl,
            @Value("${AUTH_PROCESSOR_PASSWORD:}") String password) {
        this(decoder, httpClient(builder), tokenUrl, password);
    }

    CallerTokenProvider(JwtDecoder decoder, RestClient client, String tokenUrl, String password) {
        this.decoder = decoder;
        this.client = client;
        this.tokenUrl = tokenUrl;
        this.password = password;
    }

    private static RestClient httpClient(RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return builder.requestFactory(factory).build();
    }

    public String tokenFor(PaymentRequestedMessage message) {
        if (message.callerToken() != null) {
            // Invalid or expired user credentials must never become service privileges.
            decoder.decode(message.callerToken());
            return message.callerToken();
        }
        return serviceToken();
    }

    private synchronized String serviceToken() {
        if (Instant.now().isBefore(expiresAt.minusSeconds(30))) return cachedToken;
        if (password.isBlank()) throw new IllegalStateException("AUTH_PROCESSOR_PASSWORD is required for service authentication");
        TokenResponse response = client.post().uri(tokenUrl)
                .body(Map.of("clientId", "payment-processor-service", "clientSecret", password))
                .retrieve().body(TokenResponse.class);
        if (response == null) throw new IllegalStateException("Missing service token response");
        var jwt = decoder.decode(response.accessToken());
        cachedToken = response.accessToken();
        expiresAt = jwt.getExpiresAt();
        return cachedToken;
    }

    private record TokenResponse(String accessToken, String tokenType, long expiresIn) { }
}
