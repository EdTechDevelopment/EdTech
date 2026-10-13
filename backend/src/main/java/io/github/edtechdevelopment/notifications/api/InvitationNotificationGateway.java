package io.github.edtechdevelopment.notifications.api;

import io.github.edtechdevelopment.notifications.api.command.SendStudentInvitationCommand;

public interface InvitationNotificationGateway {
    void enqueue(SendStudentInvitationCommand command);
}
