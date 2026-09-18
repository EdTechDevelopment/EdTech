package io.github.edtechdevelopment.identity.infrastructure.messaging.email;

import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.notifications.api.NotificationGateway;
import io.github.edtechdevelopment.notifications.api.command.SendVerificationEmailCommand;
import io.github.edtechdevelopment.notifications.api.model.VerificationEmailPurpose;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public final class NotificationVerificationEmailAdapter implements VerificationEmailSender {

    private static final String TOKEN_QUERY_PARAMETER = "token";

    private final NotificationGateway notificationGateway;
    private final IdentityNotificationProperties properties;

    public NotificationVerificationEmailAdapter(
            NotificationGateway notificationGateway,
            IdentityNotificationProperties properties
    ) {
        this.notificationGateway = Objects.requireNonNull(
                notificationGateway,
                "Notification gateway must not be null"
        );
        this.properties = Objects.requireNonNull(properties, "Identity notification properties must not be null");
    }

    @Override
    public void sendVerificationEmail(
            Email recipient,
            String rawToken,
            VerificationPurpose purpose,
            Instant expiresAt
    ) {
        Objects.requireNonNull(recipient, "Recipient email must not be null");
        Objects.requireNonNull(rawToken, "Raw verification token must not be null");
        Objects.requireNonNull(purpose, "Verification purpose must not be null");
        Objects.requireNonNull(expiresAt, "Verification expiration time must not be null");

        if (rawToken.isBlank()) {
            throw new IllegalArgumentException("Raw verification token must not be blank");
        }

        SendVerificationEmailCommand command = new SendVerificationEmailCommand(
                recipient.value(),
                buildConfirmationUrl(rawToken),
                toNotificationPurpose(purpose),
                expiresAt
        );
        notificationGateway.enqueue(command);
    }

    private URI buildConfirmationUrl(String rawToken) {
        return UriComponentsBuilder.fromUri(properties.frontendBaseUrl())
                .replacePath(properties.emailVerificationPath())
                .queryParam(TOKEN_QUERY_PARAMETER, rawToken)
                .build()
                .encode()
                .toUri();
    }

    private static VerificationEmailPurpose toNotificationPurpose(VerificationPurpose purpose) {
        return switch (purpose) {
            case REGISTRATION -> VerificationEmailPurpose.REGISTRATION;
            case EMAIL_CHANGE -> VerificationEmailPurpose.EMAIL_CHANGE;
        };
    }
}
