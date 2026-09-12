package com.jpcore.labs.payment.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final ApiUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final String servicePassword;

    public AuthService(ApiUserRepository users, PasswordEncoder passwords, JwtEncoder tokens,
            @Value("${AUTH_PROCESSOR_PASSWORD:}") String servicePassword) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.servicePassword = servicePassword.isBlank() ? null : passwords.encode(servicePassword);
    }

    @Transactional
    public void bootstrapAdmin(String password) {
        if (!users.existsById("admin")) createUser("admin", password, UserRole.ADMIN);
    }

    @Transactional
    public UserResponse createUser(String username, String password, UserRole role) {
        if (users.existsById(username)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        users.save(new ApiUser(username, passwords.encode(password), role));
        return new UserResponse(username, role);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String username, String password) {
        ApiUser user = users.findById(username).orElseThrow(this::invalidCredentials);
        if (!passwords.matches(password, user.getPasswordHash())) throw invalidCredentials();
        return token(username, user.getRole().name(), "USER", 3600);
    }

    public TokenResponse serviceToken(String clientId, String clientSecret) {
        if (!"payment-processor-service".equals(clientId) || servicePassword == null
                || !passwords.matches(clientSecret, servicePassword)) throw invalidCredentials();
        return token(clientId, "SERVICE", "SERVICE", 300);
    }

    private TokenResponse token(String subject, String role, String type, long lifetime) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("payment-service").subject(subject)
                .audience(List.of("payment-lab")).issuedAt(now).expiresAt(now.plusSeconds(lifetime))
                .id(UUID.randomUUID().toString()).claim("roles", List.of(role)).claim("identity_type", type).build();
        return new TokenResponse(tokens.encode(JwtEncoderParameters.from(claims)).getTokenValue(), "Bearer", lifetime);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    public record UserResponse(String username, UserRole role) { }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) { }
}
