package io.github.edtechdevelopment.notifications.api;

import io.github.edtechdevelopment.notifications.api.command.SendVerificationEmailCommand;

public interface NotificationGateway {

    void enqueue(SendVerificationEmailCommand command);
}
