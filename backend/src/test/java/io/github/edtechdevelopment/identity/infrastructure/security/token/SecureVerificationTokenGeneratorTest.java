package io.github.edtechdevelopment.identity.infrastructure.security.token;

import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityTokenProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureVerificationTokenGeneratorTest {

    private final SecureVerificationTokenGenerator tokenGenerator =
            new SecureVerificationTokenGenerator(new IdentityTokenProperties(Duration.ofMinutes(5), 32));

    @Test
    void generatesUrlSafeTokenFromConfiguredEntropy() {
        String token = tokenGenerator.generate();

        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void generatesDifferentTokens() {
        assertNotEquals(tokenGenerator.generate(), tokenGenerator.generate());
    }
}
