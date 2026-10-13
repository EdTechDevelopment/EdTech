package io.github.edtechdevelopment.tutoring.application.invitation.port.out;

import io.github.edtechdevelopment.tutoring.application.invitation.model.notification.StudentInvitationNotification;

public interface InvitationNotificationSender {

    void enqueue(StudentInvitationNotification notification);
}
