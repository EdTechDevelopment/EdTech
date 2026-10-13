package io.github.edtechdevelopment.workflows.infrastructure.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.util.Objects;

@ConfigurationProperties("workflows.idempotency")
public record WorkflowFingerprintProperties(Resource fingerprintKey) {

    public WorkflowFingerprintProperties {
        Objects.requireNonNull(fingerprintKey, "Fingerprint key must not be null");
    }
}

