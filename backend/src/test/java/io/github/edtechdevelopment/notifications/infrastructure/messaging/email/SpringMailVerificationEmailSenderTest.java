package io.github.edtechdevelopment.notifications.infrastructure.messaging.email;

import io.github.edtechdevelopment.notifications.application.exception.PermanentEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.exception.TemporaryEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailMessage;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDeliveryPurpose;
import io.github.edtechdevelopment.notifications.infrastructure.configuration.NotificationMailProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpringMailVerificationEmailSenderTest {

    private static final String RECIPIENT_EMAIL = "anna@example.com";
    private static final String FROM_ADDRESS = "no-reply@edtech.local";
    private static final URI CONFIRMATION_URL = URI.create(
            "http://frontend.example:3000/verify-email?token=sensitive-token"
    );

    @Test
    void sendsRegistrationEmail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SpringMailVerificationEmailSender sender = createSender(mailSender);

        sender.send(message(VerificationEmailDeliveryPurpose.REGISTRATION));

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        SimpleMailMessage mail = mailCaptor.getValue();

        assertAll(
                () -> assertEquals(FROM_ADDRESS, mail.getFrom()),
                () -> assertEquals(RECIPIENT_EMAIL, mail.getTo()[0]),
                () -> assertEquals("Подтвердите email", mail.getSubject()),
                () -> assertTrue(mail.getText().contains("завершить регистрацию")),
                () -> assertTrue(mail.getText().contains(CONFIRMATION_URL.toString()))
        );
    }

    @Test
    void sendsEmailChangeConfirmation() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SpringMailVerificationEmailSender sender = createSender(mailSender);

        sender.send(message(VerificationEmailDeliveryPurpose.EMAIL_CHANGE));

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        SimpleMailMessage mail = mailCaptor.getValue();

        assertAll(
                () -> assertEquals("Подтвердите новый email", mail.getSubject()),
                () -> assertTrue(mail.getText().contains("подтвердить новый адрес")),
                () -> assertTrue(mail.getText().contains(CONFIRMATION_URL.toString()))
        );
    }

    @Test
    void convertsMailParseFailureToPermanentDeliveryFailure() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MailParseException mailFailure = new MailParseException("Invalid mail message");
        doThrow(mailFailure).when(mailSender).send(any(SimpleMailMessage.class));
        SpringMailVerificationEmailSender sender = createSender(mailSender);

        PermanentEmailDeliveryException thrown = assertThrows(
                PermanentEmailDeliveryException.class,
                () -> sender.send(message(VerificationEmailDeliveryPurpose.REGISTRATION))
        );

        assertSame(mailFailure, thrown.getCause());
    }

    @Test
    void convertsTransportFailureToTemporaryDeliveryFailure() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MailSendException mailFailure = new MailSendException("SMTP is unavailable");
        doThrow(mailFailure).when(mailSender).send(any(SimpleMailMessage.class));
        SpringMailVerificationEmailSender sender = createSender(mailSender);

        TemporaryEmailDeliveryException thrown = assertThrows(
                TemporaryEmailDeliveryException.class,
                () -> sender.send(message(VerificationEmailDeliveryPurpose.REGISTRATION))
        );

        assertSame(mailFailure, thrown.getCause());
    }

    private static SpringMailVerificationEmailSender createSender(JavaMailSender mailSender) {
        return new SpringMailVerificationEmailSender(
                mailSender,
                new NotificationMailProperties(FROM_ADDRESS)
        );
    }

    private static VerificationEmailMessage message(VerificationEmailDeliveryPurpose purpose) {
        return new VerificationEmailMessage(
                RECIPIENT_EMAIL,
                CONFIRMATION_URL,
                purpose
        );
    }
}
