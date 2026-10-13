package io.github.edtechdevelopment.notifications.infrastructure.messaging.email;

import io.github.edtechdevelopment.notifications.application.exception.PermanentEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.exception.TemporaryEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.port.out.email.InvitationEmailSender;
import io.github.edtechdevelopment.notifications.domain.delivery.model.InvitationEmailDelivery;
import io.github.edtechdevelopment.notifications.infrastructure.configuration.NotificationMailProperties;

import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SpringMailInvitationEmailSender implements InvitationEmailSender {
    private final JavaMailSender mail;
    private final String from;
    public SpringMailInvitationEmailSender(JavaMailSender mail, NotificationMailProperties properties) {
        this.mail = mail;
        this.from = properties.fromAddress();
    }
    @Override
    public void send(InvitationEmailDelivery delivery) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(delivery.recipientEmail());
        message.setSubject("Приглашение к преподавателю в EdTech");
        message.setText("Преподаватель " + delivery.teacherDisplayName() + " приглашает вас стать его учеником.\n\n"
                + "Откройте приглашение: " + delivery.invitationUrl() + "\n\n"
                + "Приглашение действует до " + delivery.expiresAt() + ".\n"
                + "Если у вас ещё нет аккаунта, зарегистрируйтесь и подтвердите адрес, на который пришло письмо.\n"
                + "После регистрации приглашение нужно принять отдельно.");
        try {
            mail.send(message);
        }
        catch (MailParseException | MailPreparationException exception) {
            throw new PermanentEmailDeliveryException("Invitation email could not be prepared", exception);
        } catch (MailException exception) {
            throw new TemporaryEmailDeliveryException("Invitation email could not be delivered", exception);
        }
    }
}
