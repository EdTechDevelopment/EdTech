package io.github.edtechdevelopment.notifications.domain.delivery.model;

import io.github.edtechdevelopment.notifications.domain.delivery.exception.InvalidVerificationEmailDeliveryException;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class VerificationEmailDelivery {

    private static final int MAX_EMAIL_LENGTH = 254;

    private final UUID id;
    private final String recipientEmail;
    private final URI confirmationUrl;
    private final VerificationEmailDeliveryPurpose purpose;
    private final DeliveryStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant sentAt;

    private VerificationEmailDelivery(
            UUID id,
            String recipientEmail,
            URI confirmationUrl,
            VerificationEmailDeliveryPurpose purpose,
            DeliveryStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt,
            Instant sentAt
    ) {
        this.id = requireValue(id, "Delivery id must not be null");
        this.recipientEmail = requireEmail(recipientEmail);
        this.confirmationUrl = requireConfirmationUrl(confirmationUrl);
        this.purpose = requireValue(purpose, "Delivery purpose must not be null");
        this.status = requireValue(status, "Delivery status must not be null");
        this.expiresAt = requireValue(expiresAt, "Delivery expiration time must not be null");
        this.createdAt = requireValue(createdAt, "Delivery creation time must not be null");
        this.updatedAt = requireValue(updatedAt, "Delivery update time must not be null");
        this.sentAt = sentAt;
        validateState();
    }

    public static VerificationEmailDelivery createPending(
            UUID id,
            String recipientEmail,
            URI confirmationUrl,
            VerificationEmailDeliveryPurpose purpose,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new VerificationEmailDelivery(
                id,
                recipientEmail,
                confirmationUrl,
                purpose,
                DeliveryStatus.PENDING,
                expiresAt,
                createdAt,
                createdAt,
                null
        );
    }

    public UUID id() {
        return id;
    }

    public String recipientEmail() {
        return recipientEmail;
    }

    public URI confirmationUrl() {
        return confirmationUrl;
    }

    public VerificationEmailDeliveryPurpose purpose() {
        return purpose;
    }

    public DeliveryStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Optional<Instant> sentAt() {
        return Optional.ofNullable(sentAt);
    }

    @Override
    public String toString() {
        return "VerificationEmailDelivery[id=%s, purpose=%s, status=%s]"
                .formatted(id, purpose, status);
    }

    private void validateState() {
        if (status != DeliveryStatus.PENDING) {
            throw new InvalidVerificationEmailDeliveryException(
                    "A newly created verification email delivery must be pending"
            );
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new InvalidVerificationEmailDeliveryException(
                    "Delivery expiration time must be after creation time"
            );
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new InvalidVerificationEmailDeliveryException(
                    "Delivery update time must not be before creation time"
            );
        }
        if (sentAt != null) {
            throw new InvalidVerificationEmailDeliveryException(
                    "A pending verification email delivery must not have a sent time"
            );
        }
    }

    private static String requireEmail(String value) {
        requireValue(value, "Recipient email must not be null");
        if (value.isBlank()) {
            throw new InvalidVerificationEmailDeliveryException("Recipient email must not be blank");
        }
        if (value.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidVerificationEmailDeliveryException(
                    "Recipient email must not contain more than 254 characters"
            );
        }
        return value;
    }

    private static URI requireConfirmationUrl(URI value) {
        requireValue(value, "Confirmation URL must not be null");
        if (!value.isAbsolute()) {
            throw new InvalidVerificationEmailDeliveryException("Confirmation URL must be absolute");
        }
        if (!"http".equalsIgnoreCase(value.getScheme()) && !"https".equalsIgnoreCase(value.getScheme())) {
            throw new InvalidVerificationEmailDeliveryException(
                    "Confirmation URL must use HTTP or HTTPS"
            );
        }
        if (value.getHost() == null || value.getHost().isBlank()) {
            throw new InvalidVerificationEmailDeliveryException("Confirmation URL must contain a host");
        }
        return value;
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new InvalidVerificationEmailDeliveryException(message);
        }
        return value;
    }
}
