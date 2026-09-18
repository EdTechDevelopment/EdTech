package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentitySecurityPropertiesTest {

    @Test
    void acceptsChosenSecuritySettings() {
        assertDoesNotThrow(() -> new IdentityPasswordProperties(10));
        assertDoesNotThrow(() -> new IdentityTokenProperties(Duration.ofMinutes(5), 32));
    }

    @Test
    void rejectsInvalidSecuritySettings() {
        assertThrows(IllegalArgumentException.class, () -> new IdentityPasswordProperties(3));
        assertThrows(IllegalArgumentException.class, () -> new IdentityPasswordProperties(32));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IdentityTokenProperties(Duration.ZERO, 32)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IdentityTokenProperties(Duration.ofMinutes(5), 31)
        );
    }
}
