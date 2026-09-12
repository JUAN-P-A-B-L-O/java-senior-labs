package com.jpcore.labs.payment.auth;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import com.jpcore.labs.payment.security.SecurityConfig;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class AuthConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    JwtEncoder jwtEncoder(
            @Value("${security.jwt.public-key:}") String publicKey,
            @Value("${JWT_PRIVATE_KEY:}") String privateKey) throws Exception {
        try (var pub = SecurityConfig.keyResource(publicKey, "public.pem").getInputStream(); var priv = SecurityConfig.keyResource(privateKey, "private.pem").getInputStream()) {
            var key = new RSAKey.Builder(RsaKeyConverters.x509().convert(pub))
                    .privateKey(RsaKeyConverters.pkcs8().convert(priv)).keyID("payment-service").build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        }
    }

    @Bean
    ApplicationRunner bootstrapAdmin(AuthService auth,
            @Value("${AUTH_ADMIN_PASSWORD:}") String password) {
        return args -> { if (!password.isBlank()) auth.bootstrapAdmin(password); };
    }
}
