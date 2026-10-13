package io.github.edtechdevelopment.notifications.application.port.out.email;

import java.net.URI;
import java.util.UUID;

public interface InvitationLinkFactory {
    URI create(UUID invitationId, String recipientEmail);
}
