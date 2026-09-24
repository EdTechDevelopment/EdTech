package io.github.edtechdevelopment.notifications.infrastructure.messaging.email;

import io.github.edtechdevelopment.notifications.application.exception.PermanentEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.exception.TemporaryEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailMessage;
import io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailSender;
import io.github.edtechdevelopment.notifications.infrastructure.configuration.NotificationMailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

public final class SpringMailVerificationEmailSender implements VerificationEmailSender {

    private static final String REGISTRATION_SUBJECT = "Подтвердите email";
    private static final String EMAIL_CHANGE_SUBJECT = "Подтвердите новый email";

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SpringMailVerificationEmailSender(
            JavaMailSender mailSender,
            NotificationMailProperties properties
    ) {
        this.mailSender = Objects.requireNonNull(mailSender, "Java mail sender must not be null");
        Objects.requireNonNull(properties, "Notification mail properties must not be null");
        this.fromAddress = properties.fromAddress();
    }

    @Override
    public void send(VerificationEmailMessage message) {
        Objects.requireNonNull(message, "Verification email message must not be null");

        SimpleMailMessage mailMessage = createMailMessage(message);
        try {
            mailSender.send(mailMessage);
        } catch (MailParseException | MailPreparationException exception) {
            throw new PermanentEmailDeliveryException(
                    "Verification email could not be prepared",
                    exception
            );
        } catch (MailException exception) {
            throw new TemporaryEmailDeliveryException(
                    "Verification email could not be delivered",
                    exception
            );
        }
    }

    private SimpleMailMessage createMailMessage(VerificationEmailMessage message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(fromAddress);
        mailMessage.setTo(message.recipientEmail());
        mailMessage.setSubject(subjectFor(message));
        mailMessage.setText(textFor(message));
        return mailMessage;
    }

    private static String subjectFor(VerificationEmailMessage message) {
        return switch (message.purpose()) {
            case REGISTRATION -> REGISTRATION_SUBJECT;
            case EMAIL_CHANGE -> EMAIL_CHANGE_SUBJECT;
        };
    }

    private static String textFor(VerificationEmailMessage message) {
        String action = switch (message.purpose()) {
            case REGISTRATION -> "Чтобы завершить регистрацию в EdTech, перейдите по ссылке:";
            case EMAIL_CHANGE -> "Чтобы подтвердить новый адрес электронной почты, перейдите по ссылке:";
        };
        String ignoredAction = switch (message.purpose()) {
            case REGISTRATION -> "Если вы не регистрировались, просто проигнорируйте это письмо.";
            case EMAIL_CHANGE -> "Если вы не запрашивали изменение email, просто проигнорируйте это письмо.";
        };

        return """
                %s

                %s

                %s
                """.formatted(action, message.confirmationUrl(), ignoredAction);
    }
}
