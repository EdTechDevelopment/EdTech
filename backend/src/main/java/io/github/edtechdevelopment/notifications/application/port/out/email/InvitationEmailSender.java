package io.github.edtechdevelopment.notifications.application.port.out.email;

import io.github.edtechdevelopment.notifications.domain.delivery.model.InvitationEmailDelivery;

public interface InvitationEmailSender {
    void send(InvitationEmailDelivery delivery);
}
