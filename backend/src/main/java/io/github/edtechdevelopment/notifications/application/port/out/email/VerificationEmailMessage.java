package io.github.edtechdevelopment.notifications.application.port.out.email;

import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDeliveryPurpose;

import java.net.URI;
import java.util.Objects;

public record VerificationEmailMessage(
        String recipientEmail,
        URI confirmationUrl,
        VerificationEmailDeliveryPurpose purpose
) {

    public VerificationEmailMessage {
        Objects.requireNonNull(recipientEmail, "Recipient email must not be null");
        Objects.requireNonNull(confirmationUrl, "Confirmation URL must not be null");
        Objects.requireNonNull(purpose, "Verification email purpose must not be null");
    }
}
