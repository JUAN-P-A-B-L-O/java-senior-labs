package com.jpcore.labs.payment.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(@Value("${security.jwt.public-key:}") String keyLocation) throws Exception {
        NimbusJwtDecoder decoder;
        try (var input = keyResource(keyLocation, "public.pem").getInputStream()) {
            decoder = NimbusJwtDecoder.withPublicKey(RsaKeyConverters.x509().convert(input)).build();
        }
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains("payment-lab")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("payment-service"), audience));
        return decoder;
    }

    public static Resource keyResource(String location, String filename) {
        return keyResource(location, filename, java.nio.file.Path.of(System.getProperty("user.dir")));
    }

    static Resource keyResource(String location, String filename, java.nio.file.Path workingDirectory) {
        if (location != null && !location.isBlank()) {
            return new org.springframework.core.io.DefaultResourceLoader().getResource(location);
        }
        for (var directory = workingDirectory.toAbsolutePath().normalize(); directory != null; directory = directory.getParent()) {
            var authDirectory = directory.resolve(".local-auth");
            if (java.nio.file.Files.isDirectory(authDirectory)) {
                var key = authDirectory.resolve(filename);
                if (!java.nio.file.Files.isRegularFile(key)) {
                    throw new IllegalStateException("Missing JWT key " + key
                            + ". Run scripts/setup-local-auth.sh or configure JWT_PUBLIC_KEY and JWT_PRIVATE_KEY explicitly.");
                }
                return new org.springframework.core.io.FileSystemResource(key);
            }
        }
        throw new IllegalStateException("Cannot locate .local-auth/" + filename + " from " + workingDirectory
                + ". Run scripts/setup-local-auth.sh from the project, or configure an absolute JWT key location.");
    }

    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain loginSecurityFilterChain(HttpSecurity http) throws Exception {
        // Credentials in the request body authenticate these endpoints, not an existing JWT.
        return http.securityMatchers(matchers -> matchers.requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/auth/login", "/api/auth/service-token"))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(request -> request.anyRequest().permitAll())
                .build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(roles);
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(request -> request
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/auth/users").hasRole("ADMIN")
                        .requestMatchers("/api/payments", "/api/payments/**").hasAnyRole("ADMIN", "COMUM")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/ai/test").hasAnyRole("ADMIN", "COMUM")
                        .anyRequest().denyAll())
                .addFilterAfter(new RequestLogContextFilter(),
                        org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication)))
                .build();
    }
}
