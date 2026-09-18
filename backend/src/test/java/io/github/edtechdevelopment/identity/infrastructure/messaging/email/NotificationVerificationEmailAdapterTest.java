package io.github.edtechdevelopment.identity.infrastructure.messaging.email;

import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.notifications.api.NotificationGateway;
import io.github.edtechdevelopment.notifications.api.command.SendVerificationEmailCommand;
import io.github.edtechdevelopment.notifications.api.model.VerificationEmailPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationVerificationEmailAdapterTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T10:05:00Z");
    private static final String RAW_TOKEN = "url-safe_verification-token";

    private NotificationGateway notificationGateway;
    private NotificationVerificationEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        notificationGateway = mock(NotificationGateway.class);
        adapter = new NotificationVerificationEmailAdapter(
                notificationGateway,
                new IdentityNotificationProperties(
                        URI.create("http://frontend.example:3000"),
                        "/verify-email"
                )
        );
    }

    @Test
    void enqueuesRegistrationVerificationEmail() {
        adapter.sendVerificationEmail(
                new Email("anna@example.com"),
                RAW_TOKEN,
                VerificationPurpose.REGISTRATION,
                EXPIRES_AT
        );

        SendVerificationEmailCommand command = captureCommand();
        assertEquals("anna@example.com", command.recipientEmail());
        assertEquals(
                URI.create("http://frontend.example:3000/verify-email?token=" + RAW_TOKEN),
                command.confirmationUrl()
        );
        assertEquals(VerificationEmailPurpose.REGISTRATION, command.purpose());
        assertEquals(EXPIRES_AT, command.expiresAt());
    }

    @Test
    void mapsEmailChangePurpose() {
        adapter.sendVerificationEmail(
                new Email("new-address@example.com"),
                RAW_TOKEN,
                VerificationPurpose.EMAIL_CHANGE,
                EXPIRES_AT
        );

        SendVerificationEmailCommand command = captureCommand();
        assertEquals(VerificationEmailPurpose.EMAIL_CHANGE, command.purpose());
        assertEquals(EXPIRES_AT, command.expiresAt());
    }

    private SendVerificationEmailCommand captureCommand() {
        ArgumentCaptor<SendVerificationEmailCommand> commandCaptor =
                ArgumentCaptor.forClass(SendVerificationEmailCommand.class);
        verify(notificationGateway).enqueue(commandCaptor.capture());
        return commandCaptor.getValue();
    }
}
