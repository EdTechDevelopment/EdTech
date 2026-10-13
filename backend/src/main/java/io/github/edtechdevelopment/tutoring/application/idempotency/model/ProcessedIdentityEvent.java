package io.github.edtechdevelopment.tutoring.application.idempotency.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProcessedIdentityEvent(
        UUID eventId,
        UUID userId,
        String eventType,
        int fingerprintVersion,
        PayloadFingerprint payloadFingerprint,
        Instant processedAt
) {

    public ProcessedIdentityEvent {
        Objects.requireNonNull(eventId, "Event id must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(eventType, "Event type must not be null");
        Objects.requireNonNull(payloadFingerprint, "Payload fingerprint must not be null");
        Objects.requireNonNull(processedAt, "Processing time must not be null");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("Event type must not be blank");
        }
        if (fingerprintVersion < 1) {
            throw new IllegalArgumentException("Fingerprint version must be positive");
        }
    }
}
