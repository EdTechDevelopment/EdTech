package io.github.edtechdevelopment.notifications.application.port.out.persistence;

import io.github.edtechdevelopment.notifications.application.model.ClaimedInvitationEmail;
import io.github.edtechdevelopment.notifications.domain.delivery.model.DeliveryStatus;
import io.github.edtechdevelopment.notifications.domain.delivery.model.InvitationEmailDelivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InvitationEmailDeliveryRepository {
    void enqueue(InvitationEmailDelivery delivery);
    List<ClaimedInvitationEmail> claimPending(Instant now, Instant staleBefore, int limit);
    boolean complete(UUID id, UUID claimId, DeliveryStatus status, Instant now);
}
