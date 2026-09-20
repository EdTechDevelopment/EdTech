package io.github.edtechdevelopment.notifications.api.command;

import io.github.edtechdevelopment.notifications.api.model.VerificationEmailPurpose;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SendVerificationEmailCommandTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T10:05:00Z");

    @Test
    void createsVerificationEmailCommand() {
        URI confirmationUrl = URI.create("http://frontend.example:3000/verify-email?token=raw-token");

        SendVerificationEmailCommand command = new SendVerificationEmailCommand(
                "anna@example.com",
                confirmationUrl,
                VerificationEmailPurpose.REGISTRATION,
                EXPIRES_AT
        );

        assertEquals("anna@example.com", command.recipientEmail());
        assertEquals(confirmationUrl, command.confirmationUrl());
        assertEquals(VerificationEmailPurpose.REGISTRATION, command.purpose());
        assertEquals(EXPIRES_AT, command.expiresAt());
    }

    @Test
    void protectsSensitivePayloadInStringRepresentation() {
        SendVerificationEmailCommand command = new SendVerificationEmailCommand(
                "anna@example.com",
                URI.create("http://frontend.example:3000/verify-email?token=secret-token"),
                VerificationEmailPurpose.REGISTRATION,
                EXPIRES_AT
        );

        assertEquals("SendVerificationEmailCommand[PROTECTED]", command.toString());
    }

    @Test
    void rejectsBlankEmailAndInvalidConfirmationUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SendVerificationEmailCommand(
                        " ",
                        URI.create("http://frontend.example:3000/verify-email"),
                        VerificationEmailPurpose.REGISTRATION,
                        EXPIRES_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SendVerificationEmailCommand(
                        "anna@example.com",
                        URI.create("/verify-email?token=raw-token"),
                        VerificationEmailPurpose.REGISTRATION,
                        EXPIRES_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SendVerificationEmailCommand(
                        "anna@example.com",
                        URI.create("ftp://frontend.example/verify-email?token=raw-token"),
                        VerificationEmailPurpose.REGISTRATION,
                        EXPIRES_AT
                )
        );
    }
}
