package io.github.edtechdevelopment.identity.domain.verification.model;

import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.verification.exception.InvalidEmailVerificationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class EmailVerification {

    private final UUID id;
    private final UUID userId;
    private final Email targetEmail;
    private final VerificationTokenHash tokenHash;
    private final VerificationPurpose purpose;
    private final Instant expiresAt;
    private Instant consumedAt;
    private Instant invalidatedAt;
    private final Instant createdAt;

    private EmailVerification(
            UUID id, UUID userId, Email targetEmail, VerificationTokenHash tokenHash, VerificationPurpose purpose,
            Instant createdAt, Instant expiresAt, Instant consumedAt, Instant invalidatedAt) {

        this.id = requireValue(id, "Verification id must not be null");
        this.userId = requireValue(userId, "User id must not be null");
        this.targetEmail = requireValue(targetEmail, "Target email must not be null");
        this.tokenHash = requireValue(tokenHash, "Verification token hash must not be null");
        this.purpose = requireValue(purpose, "Verification purpose must not be null");
        this.createdAt = requireValue(createdAt, "Creation time must not be null");
        this.expiresAt = requireValue(expiresAt, "Expiration time must not be null");
        this.consumedAt = consumedAt;
        this.invalidatedAt = invalidatedAt;
        validateState();
    }

    public static EmailVerification create(
            UUID id, UUID userId, Email targetEmail, VerificationTokenHash tokenHash,
            VerificationPurpose purpose, Instant createdAt, Instant expiresAt) {

        return new EmailVerification(
                id, userId, targetEmail, tokenHash, purpose,
                createdAt, expiresAt, null, null);
    }

    public static EmailVerification reconstitute(
            UUID id, UUID userId, Email targetEmail, VerificationTokenHash tokenHash, VerificationPurpose purpose,
            Instant createdAt, Instant expiresAt, Instant consumedAt, Instant invalidatedAt) {

        return new EmailVerification(
                id, userId, targetEmail, tokenHash,
                purpose, createdAt, expiresAt, consumedAt, invalidatedAt);
    }

    public boolean isActiveAt(Instant checkedAt) {
        requireValue(checkedAt, "Verification check time must not be null");
        return consumedAt == null && invalidatedAt == null && !checkedAt.isBefore(createdAt) && checkedAt.isBefore(expiresAt);
    }

    public void consume(Instant consumedAt) {
        requireValue(consumedAt, "Consumption time must not be null");
        if (!isActiveAt(consumedAt)) {
            throw new InvalidEmailVerificationException("Only an active email verification can be consumed");
        }
        this.consumedAt = consumedAt;
    }

    public void invalidate(Instant invalidatedAt) {
        requireValue(invalidatedAt, "Invalidation time must not be null");

        if (invalidatedAt.isBefore(createdAt)) {
            throw new InvalidEmailVerificationException("Invalidation time must not be before creation time");
        }
        if (consumedAt != null) {
            throw new InvalidEmailVerificationException("A consumed email verification cannoprivate final Instant createdAt;t be invalidated");
        }
        if (this.invalidatedAt != null) {
            throw new InvalidEmailVerificationException("Email verification is already invalidated");
        }
        this.invalidatedAt = invalidatedAt;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public Email targetEmail() {
        return targetEmail;
    }

    public VerificationTokenHash tokenHash() {
        return tokenHash;
    }

    public VerificationPurpose purpose() {
        return purpose;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<Instant> consumedAt() {
        return Optional.ofNullable(consumedAt);
    }

    public Optional<Instant> invalidatedAt() {
        return Optional.ofNullable(invalidatedAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    private void validateState() {
        if (!expiresAt.isAfter(createdAt)) {
            throw new InvalidEmailVerificationException(
                    "Expiration time must be after creation time"
            );
        }
        if (consumedAt != null && invalidatedAt != null) {
            throw new InvalidEmailVerificationException(
                    "Email verification cannot be both consumed and invalidated"
            );
        }
        if (consumedAt != null && consumedAt.isBefore(createdAt)) {
            throw new InvalidEmailVerificationException(
                    "Consumption time must not be before creation time"
            );
        }
        if (consumedAt != null && !consumedAt.isBefore(expiresAt)) {
            throw new InvalidEmailVerificationException(
                    "Consumption time must be before expiration time"
            );
        }
        if (invalidatedAt != null && invalidatedAt.isBefore(createdAt)) {
            throw new InvalidEmailVerificationException(
                    "Invalidation time must not be before creation time"
            );
        }
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new InvalidEmailVerificationException(message);
        }
        return value;
    }
}
