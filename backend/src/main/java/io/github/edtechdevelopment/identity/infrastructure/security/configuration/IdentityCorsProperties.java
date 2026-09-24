package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "identity.security.cors")
public record IdentityCorsProperties(List<String> allowedOrigins) {

    public IdentityCorsProperties {
        Objects.requireNonNull(allowedOrigins, "Allowed CORS origins must not be null");
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("At least one CORS origin must be configured");
        }
        if (allowedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank())) {
            throw new IllegalArgumentException("Allowed CORS origins must not contain blank values");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
