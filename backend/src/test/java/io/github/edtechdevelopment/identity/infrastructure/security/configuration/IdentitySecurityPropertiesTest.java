package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentitySecurityPropertiesTest {

    @Test
    void acceptsChosenSecuritySettings() {
        assertDoesNotThrow(() -> new IdentityPasswordProperties(10));
        assertDoesNotThrow(IdentitySecurityPropertiesTest::validTokenProperties);
    }

    @Test
    void bindsChosenTokenDurationsFromConfigurationFormat() {
        MapConfigurationPropertySource propertySource = new MapConfigurationPropertySource(Map.of(
                "identity.token.verification-ttl", "5m",
                "identity.token.verification-entropy-bytes", "32",
                "identity.token.access-ttl", "15m",
                "identity.token.refresh-family-ttl", "30d",
                "identity.token.refresh-entropy-bytes", "32",
                "identity.token.issuer", "edtech-backend",
                "identity.token.audience", "edtech-api"
        ));

        IdentityTokenProperties properties = new Binder(propertySource)
                .bind("identity.token", Bindable.of(IdentityTokenProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertEquals(Duration.ofMinutes(15), properties.accessTtl());
        assertEquals(Duration.ofDays(30), properties.refreshFamilyTtl());
    }

    @Test
    void rejectsInvalidSecuritySettings() {
        assertThrows(IllegalArgumentException.class, () -> new IdentityPasswordProperties(3));
        assertThrows(IllegalArgumentException.class, () -> new IdentityPasswordProperties(32));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IdentityTokenProperties(
                        Duration.ZERO,
                        32,
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        32,
                        "edtech-backend",
                        "edtech-api"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IdentityTokenProperties(
                        Duration.ofMinutes(5),
                        31,
                        Duration.ofMinutes(15),
                        Duration.ofDays(30),
                        32,
                        "edtech-backend",
                        "edtech-api"
                )
        );
    }

    private static IdentityTokenProperties validTokenProperties() {
        return new IdentityTokenProperties(
                Duration.ofMinutes(5),
                32,
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                32,
                "edtech-backend",
                "edtech-api"
        );
    }
}
