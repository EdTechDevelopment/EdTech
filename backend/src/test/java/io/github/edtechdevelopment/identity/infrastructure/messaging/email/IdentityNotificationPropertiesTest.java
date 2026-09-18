package io.github.edtechdevelopment.identity.infrastructure.messaging.email;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityNotificationPropertiesTest {

    @Test
    void acceptsConfiguredFrontendLocation() {
        assertDoesNotThrow(() -> new IdentityNotificationProperties(
                URI.create("http://frontend.example:3000"),
                "/verify-email"
        ));
        assertDoesNotThrow(() -> new IdentityNotificationProperties(
                URI.create("https://app.edtech.example/"),
                "/verify-email"
        ));
    }

    @Test
    void rejectsInvalidFrontendBaseUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties("ftp://frontend.example:3000", "/verify-email")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties("http://frontend.example:3000/application", "/verify-email")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties("http://frontend.example:3000?source=backend", "/verify-email")
        );
    }

    @Test
    void rejectsInvalidVerificationPath() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties("http://frontend.example:3000", "verify-email")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties("http://frontend.example:3000", "/verify-email?token=value")
        );
    }

    private static IdentityNotificationProperties properties(String frontendBaseUrl, String verificationPath) {
        return new IdentityNotificationProperties(URI.create(frontendBaseUrl), verificationPath);
    }
}
