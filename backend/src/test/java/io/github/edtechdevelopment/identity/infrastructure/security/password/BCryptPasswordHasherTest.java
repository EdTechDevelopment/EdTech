package io.github.edtechdevelopment.identity.infrastructure.security.password;

import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityPasswordProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptPasswordHasherTest {

    private static final String RAW_PASSWORD = "Strong!42";

    private final BCryptPasswordHasher passwordHasher =
            new BCryptPasswordHasher(new IdentityPasswordProperties(4));

    @Test
    void hashesAndMatchesPassword() {
        PasswordHash passwordHash = passwordHasher.hash(RAW_PASSWORD);

        assertNotEquals(RAW_PASSWORD, passwordHash.value());
        assertTrue(passwordHasher.matches(RAW_PASSWORD, passwordHash));
        assertFalse(passwordHasher.matches("Wrong!42", passwordHash));
    }

    @Test
    void usesRandomSaltForEveryHash() {
        PasswordHash firstHash = passwordHasher.hash(RAW_PASSWORD);
        PasswordHash secondHash = passwordHasher.hash(RAW_PASSWORD);

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.matches(RAW_PASSWORD, firstHash));
        assertTrue(passwordHasher.matches(RAW_PASSWORD, secondHash));
    }

    @Test
    void rejectsPasswordLongerThanBcryptLimit() {
        assertThrows(IllegalArgumentException.class, () -> passwordHasher.hash("a".repeat(73)));
    }
}
