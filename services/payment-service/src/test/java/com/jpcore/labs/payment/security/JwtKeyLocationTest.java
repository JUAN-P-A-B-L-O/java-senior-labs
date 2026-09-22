package com.jpcore.labs.payment.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class JwtKeyLocationTest {
    @TempDir Path directory;

    @Test
    void resolvesKeysFromProjectRootAndServiceWorkingDirectory() throws Exception {
        Path root = Files.createDirectory(directory.resolve("project"));
        Path auth = Files.createDirectory(root.resolve(".local-auth"));
        Path module = Files.createDirectories(root.resolve("services/payment-service"));
        for (String filename : new String[] {"public.pem", "private.pem"}) {
            Path key = Files.writeString(auth.resolve(filename), "key fixture");
            assertThat(SecurityConfig.keyResource("", filename, root).getFile().toPath()).isEqualTo(key);
            assertThat(SecurityConfig.keyResource("", filename, module).getFile().toPath()).isEqualTo(key);
        }
    }

    @Test
    void explicitLocationsTakePrecedenceAndAreNotSilentlyReplaced() throws Exception {
        Path key = Files.writeString(directory.resolve("custom.pem"), "custom fixture");
        assertThat(SecurityConfig.keyResource(key.toUri().toString(), "public.pem", directory).getFile().toPath())
                .isEqualTo(key);
        assertThat(SecurityConfig.keyResource("classpath:test-public.pem", "public.pem", directory).exists()).isTrue();
        assertThat(SecurityConfig.keyResource(directory.resolve("missing.pem").toUri().toString(), "public.pem", directory).exists())
                .isFalse();
    }

    @Test
    void missingLocalKeyReportsHowToConfigureIt() throws Exception {
        Files.createDirectory(directory.resolve(".local-auth"));
        assertThatThrownBy(() -> SecurityConfig.keyResource("", "public.pem", directory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing JWT key").hasMessageContaining("scripts/setup-local-auth.sh");
    }
}
