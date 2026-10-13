package io.github.edtechdevelopment.tutoring.infrastructure.cursor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.util.Objects;

@ConfigurationProperties("tutoring.cursor")
public record TutoringCursorProperties(Resource signingKey) {
    public TutoringCursorProperties {
        Objects.requireNonNull(signingKey, "Cursor signing key must be configured");
    }
}
