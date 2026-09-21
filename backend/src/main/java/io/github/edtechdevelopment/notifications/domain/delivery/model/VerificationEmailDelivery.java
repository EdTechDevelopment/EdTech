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
    private URI confirmationUrl;
    private final VerificationEmailDeliveryPurpose purpose;
    private DeliveryStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant sentAt;

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
        this.confirmationUrl = confirmationUrl;
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

    public static VerificationEmailDelivery reconstitute(
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
        return new VerificationEmailDelivery(
                id,
                recipientEmail,
                confirmationUrl,
                purpose,
                status,
                expiresAt,
                createdAt,
                updatedAt,
                sentAt
        );
    }

    public UUID id() {
        return id;
    }

    public String recipientEmail() {
        return recipientEmail;
    }

    public Optional<URI> confirmationUrl() {
        return Optional.ofNullable(confirmationUrl);
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

    public void startProcessing(Instant startedAt) {
        requireStatus(DeliveryStatus.PENDING, "Only a pending delivery can start processing");
        validateTransitionTime(startedAt);
        requireBeforeExpiration(startedAt, "An expired delivery cannot start processing");

        status = DeliveryStatus.PROCESSING;
        updatedAt = startedAt;
    }

    public void markSent(Instant sentAt) {
        requireStatus(DeliveryStatus.PROCESSING, "Only a processing delivery can be marked as sent");
        validateTransitionTime(sentAt);
        requireBeforeExpiration(sentAt, "An expired delivery cannot be marked as sent");

        status = DeliveryStatus.SENT;
        this.sentAt = sentAt;
        updatedAt = sentAt;
        confirmationUrl = null;
    }

    public void returnToPending(Instant returnedAt) {
        requireStatus(DeliveryStatus.PROCESSING, "Only a processing delivery can return to pending");
        validateTransitionTime(returnedAt);
        requireBeforeExpiration(returnedAt, "An expired delivery cannot return to pending");

        status = DeliveryStatus.PENDING;
        updatedAt = returnedAt;
    }

    public void markFailed(Instant failedAt) {
        requireStatus(DeliveryStatus.PROCESSING, "Only a processing delivery can be marked as failed");
        validateTransitionTime(failedAt);
        requireBeforeExpiration(failedAt, "An expired delivery cannot be marked as failed");

        status = DeliveryStatus.FAILED;
        updatedAt = failedAt;
        confirmationUrl = null;
    }

    public void markExpired(Instant expiredAt) {
        requireExpirableStatus();
        validateTransitionTime(expiredAt);
        if (expiredAt.isBefore(expiresAt)) {
            throw new InvalidVerificationEmailDeliveryException(
                    "A delivery cannot be marked as expired before its expiration time"
            );
        }

        status = DeliveryStatus.EXPIRED;
        updatedAt = expiredAt;
        confirmationUrl = null;
    }

    @Override
    public String toString() {
        return "VerificationEmailDelivery[id=%s, purpose=%s, status=%s]"
                .formatted(id, purpose, status);
    }

    private void validateState() {
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

        if (confirmationUrl != null) {
            validateConfirmationUrl(confirmationUrl);
        }

        switch (status) {
            case PENDING, PROCESSING -> validateActiveState();
            case SENT -> validateSentState();
            case FAILED -> validateFailedState();
            case EXPIRED -> validateExpiredState();
        }
    }

    private void validateActiveState() {
        if (confirmationUrl == null) {
            throw new InvalidVerificationEmailDeliveryException(
                    "A pending or processing delivery must contain a confirmation URL"
            );
        }
        if (sentAt != null) {
            throw new InvalidVerificationEmailDeliveryException(
                    "A pending or processing delivery must not have a sent time"
            );
        }
        requireUpdateBeforeExpiration();
    }

    private void validateSentState() {
        requireMissingConfirmationUrl("A sent delivery must not contain a confirmation URL");
        if (sentAt == null) {
            throw new InvalidVerificationEmailDeliveryException("A sent delivery must have a sent time");
        }
        if (!sentAt.equals(updatedAt)) {
            throw new InvalidVerificationEmailDeliveryException(
                    "Sent time must be equal to the last delivery update time"
            );
        }
        requireUpdateBeforeExpiration();
    }

    private void validateFailedState() {
        requireMissingConfirmationUrl("A failed delivery must not contain a confirmation URL");
        requireMissingSentTime("A failed delivery must not have a sent time");
        requireUpdateBeforeExpiration();
    }

    private void validateExpiredState() {
        requireMissingConfirmationUrl("An expired delivery must not contain a confirmation URL");
        requireMissingSentTime("An expired delivery must not have a sent time");
        if (updatedAt.isBefore(expiresAt)) {
            throw new InvalidVerificationEmailDeliveryException(
                    "An expired delivery update time must not be before its expiration time"
            );
        }
    }

    private void requireMissingConfirmationUrl(String message) {
        if (confirmationUrl != null) {
            throw new InvalidVerificationEmailDeliveryException(message);
        }
    }

    private void requireMissingSentTime(String message) {
        if (sentAt != null) {
            throw new InvalidVerificationEmailDeliveryException(message);
        }
    }

    private void requireUpdateBeforeExpiration() {
        if (!updatedAt.isBefore(expiresAt)) {
            throw new InvalidVerificationEmailDeliveryException(
                    "A non-expired delivery update time must be before its expiration time"
            );
        }
    }

    private void requireStatus(DeliveryStatus expectedStatus, String message) {
        if (status != expectedStatus) {
            throw new InvalidVerificationEmailDeliveryException(message);
        }
    }

    private void requireExpirableStatus() {
        if (status != DeliveryStatus.PENDING && status != DeliveryStatus.PROCESSING) {
            throw new InvalidVerificationEmailDeliveryException(
                    "Only a pending or processing delivery can be marked as expired"
            );
        }
    }

    private void validateTransitionTime(Instant transitionAt) {
        requireValue(transitionAt, "Delivery transition time must not be null");
        if (transitionAt.isBefore(updatedAt)) {
            throw new InvalidVerificationEmailDeliveryException(
                    "Delivery transition time must not be before the previous update time"
            );
        }
    }

    private void requireBeforeExpiration(Instant transitionAt, String message) {
        if (!transitionAt.isBefore(expiresAt)) {
            throw new InvalidVerificationEmailDeliveryException(message);
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

    private static void validateConfirmationUrl(URI value) {
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
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new InvalidVerificationEmailDeliveryException(message);
        }
        return value;
    }
}
