package io.github.edtechdevelopment.tutoring.application.idempotency.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record PayloadFingerprint(String value) {

    public PayloadFingerprint {
        Objects.requireNonNull(value, "Fingerprint must not be null");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Fingerprint must be a lowercase SHA-256 value");
        }
    }

    public static PayloadFingerprint from(String canonicalPayload) {
        Objects.requireNonNull(canonicalPayload, "Canonical payload must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new PayloadFingerprint(HexFormat.of().formatHex(digest.digest(
                    canonicalPayload.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
