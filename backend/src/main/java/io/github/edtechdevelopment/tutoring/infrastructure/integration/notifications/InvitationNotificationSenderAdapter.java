package io.github.edtechdevelopment.tutoring.infrastructure.integration.notifications;

import io.github.edtechdevelopment.notifications.api.InvitationNotificationGateway;
import io.github.edtechdevelopment.notifications.api.command.SendStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.notification.StudentInvitationNotification;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.InvitationNotificationSender;

import org.springframework.stereotype.Component;

@Component
public class InvitationNotificationSenderAdapter implements InvitationNotificationSender {
    private final InvitationNotificationGateway notifications;
    public InvitationNotificationSenderAdapter(InvitationNotificationGateway notifications) {
        this.notifications = notifications;
    }

    @Override
    public void enqueue(StudentInvitationNotification message) {
        notifications.enqueue(new SendStudentInvitationCommand(message.invitationId(), message.studentEmail().value(),
                message.teacherDisplayName(), message.expiresAt(), message.deduplicationKey()));
    }
}
