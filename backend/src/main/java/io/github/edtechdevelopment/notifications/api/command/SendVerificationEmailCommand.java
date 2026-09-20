package io.github.edtechdevelopment.notifications.api.command;

import io.github.edtechdevelopment.notifications.api.model.VerificationEmailPurpose;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record SendVerificationEmailCommand(
        String recipientEmail,
        URI confirmationUrl,
        VerificationEmailPurpose purpose,
        Instant expiresAt
) {

    public SendVerificationEmailCommand {
        Objects.requireNonNull(recipientEmail, "Recipient email must not be null");
        Objects.requireNonNull(confirmationUrl, "Confirmation URL must not be null");
        Objects.requireNonNull(purpose, "Verification email purpose must not be null");
        Objects.requireNonNull(expiresAt, "Verification expiration time must not be null");

        if (recipientEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email must not be blank");
        }
        if (!confirmationUrl.isAbsolute()) {
            throw new IllegalArgumentException("Confirmation URL must be absolute");
        }
        if (!confirmationUrl.getScheme().equalsIgnoreCase("http")
                && !confirmationUrl.getScheme().equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Confirmation URL must use HTTP or HTTPS");
        }
        if (confirmationUrl.getHost() == null || confirmationUrl.getHost().isBlank()) {
            throw new IllegalArgumentException("Confirmation URL must contain a host");
        }
    }

    @Override
    public String toString() {
        return "SendVerificationEmailCommand[PROTECTED]";
    }
}
